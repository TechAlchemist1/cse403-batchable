package com.batchable.backend.service.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.batchable.backend.EventSource.SsePublisher;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.exception.InvalidRouteException;
import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.BatchingAlgorithm.TentativeBatch;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.RestaurantService;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.twilio.TwilioManager;
import com.batchable.backend.util.Log;

/**
 * Manages order batching for a single restaurant.
 *
 * Responsibilities: - Holds tentative, ready, and active batches for this restaurant. - Assigns
 * batches to drivers when ready and computes routes/duration. - Emits events when batches change or
 * become active. - Periodically checks for expired tentative batches.
 *
 * All state modifications are serialized on a dedicated single‑thread executor.
 */
public class RestaurantBatchingManager {

  private final long restaurantId;
  private String restaurantAddress;
  private final SsePublisher publisher;
  private final BatchingAlgorithm batchingAlgorithm;
  private final Batches batches;
  private final RouteService routeService;
  private final DbOrderService dbOrderService;
  private final DriverService driverService;
  private final RestaurantService restaurantService;
  private final TwilioManager twilioManager;
  // time to add when an order isn't ready when it should be for batching
  private static final long SECONDS_ADDITIONAL_COOK_TIME = 6;
  private boolean updated = false;

  // Single‑thread executor for this restaurant
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  /**
   * Constructs a batching manager for a single restaurant.
   *
   * @param restaurantId the restaurant ID
   * @param restaurantAddress the restaurant's address (used for routing)
   * @param publisher websocket publisher to notify clients
   * @param batchingAlgorithm algorithm for forming batches
   * @param routeService service for computing route polylines and duration
   * @param dbOrderService service for database order operations
   * @param driverService service for driver availability
   * @param restaurantService service for restaurant data
   * @param twilioManager service for Twilio notifications
   * @param batches initial batches container (may be null)
   */
  public RestaurantBatchingManager(long restaurantId, String restaurantAddress,
      SsePublisher publisher, BatchingAlgorithm batchingAlgorithm, RouteService routeService,
      DbOrderService dbOrderService, DriverService driverService,
      RestaurantService restaurantService, TwilioManager twilioManager, Batches batches) {
    this.restaurantId = restaurantId;
    this.restaurantAddress = restaurantAddress;
    this.publisher = publisher;
    this.batchingAlgorithm = batchingAlgorithm;
    this.routeService = routeService;
    this.dbOrderService = dbOrderService;
    this.driverService = driverService;
    this.restaurantService = restaurantService;
    this.twilioManager = twilioManager;
    this.batches = (batches != null) ? batches : new Batches();

    initializeOrders();
  }

  /**
   * Represents a batch that is ready to be assigned to a driver.
   */
  public static class ReadyBatch {
    private final List<Order> batch;

    /**
     * Creates a ready batch from a list of orders.
     *
     * @param batch the orders that form this batch (will be copied)
     */
    public ReadyBatch(List<Order> batch) {
      this.batch = new ArrayList<Order>(batch);
    }

    /**
     * Returns a defensive copy of the orders in this batch.
     *
     * @return a new list containing the batch orders
     */
    public List<Order> getBatch() {
      return new ArrayList<Order>(batch);
    }
  }

  /**
   * Holds all batches for this restaurant, separated by status. Batches are grouped into three
   * categories: tentativeBatches – batches that are still being formed readyBatches – batches that
   * are ready for delivery (FIFO queue) activeBatches – batches that are currently in progress
   */
  public static class Batches {
    /** List of batches that are still tentative (not yet ready). */
    private final List<TentativeBatch> tentativeBatches;

    /**
     * Queue of batches that are ready to be assigned to drivers. A queue is used to efficiently
     * assign batches in the order they became ready.
     */
    private final Queue<ReadyBatch> readyBatches;

    /** List of batches that are currently active (in progress). */
    private final List<Batch> activeBatches;

    /** Creates an empty Batches container. */
    public Batches() {
      this.tentativeBatches = new ArrayList<TentativeBatch>();
      this.readyBatches = new LinkedList<ReadyBatch>();
      this.activeBatches = new ArrayList<Batch>();
    }

    /**
     * Creates a Batches container with the given lists. The ready batches are stored in a queue.
     *
     * @param tb list of tentative batches (will be copied)
     * @param rb list of ready batches (will be copied into a queue)
     * @param ab list of active batches (will be copied)
     */
    public Batches(List<TentativeBatch> tb, List<ReadyBatch> rb, List<Batch> ab) {
      this.tentativeBatches = new ArrayList<TentativeBatch>(tb);
      this.readyBatches = new LinkedList<ReadyBatch>(rb);
      this.activeBatches = new ArrayList<Batch>(ab);
    }

    /**
     * Creates a Batches container with the given tentative and active lists and a ready queue.
     *
     * @param tb list of tentative batches (will be copied)
     * @param rb queue of ready batches (will be copied into a new queue)
     * @param ab list of active batches (will be copied)
     */
    public Batches(List<TentativeBatch> tb, Queue<ReadyBatch> rb, List<Batch> ab) {
      this.tentativeBatches = new ArrayList<TentativeBatch>(tb);
      this.readyBatches = new LinkedList<ReadyBatch>(rb);
      this.activeBatches = new ArrayList<Batch>(ab);
    }

    /**
     * Returns a defensive copy of the tentative batches list.
     *
     * @return a new list containing all tentative batches
     */
    public List<TentativeBatch> getTentativeBatches() {
      return new ArrayList<TentativeBatch>(tentativeBatches);
    }

    /**
     * Returns a defensive copy of the ready batches queue.
     *
     * @return a new queue containing all ready batches (order preserved)
     */
    public Queue<ReadyBatch> getReadyBatches() {
      return new LinkedList<ReadyBatch>(readyBatches);
    }

    /**
     * Returns a defensive copy of the active batches list.
     *
     * @return a new list containing all active batches
     */
    public List<Batch> getActiveBatches() {
      return new ArrayList<Batch>(activeBatches);
    }
  }

  /**
   * Returns the current batches container for this restaurant. This method is thread‑safe and waits
   * for the executor to produce a consistent snapshot.
   *
   * @return the Batches object holding tentative, ready, and active batches
   * @throws RuntimeException if interrupted or if the executor task fails
   */
  public Batches getBatches() {
    try {
      return executor.submit((Callable<Batches>) () -> new Batches(batches.tentativeBatches,
          batches.readyBatches, batches.activeBatches)).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while getting batches", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Failed to get batches", e.getCause());
    }
  }

  /**
   * Sets the restaurant's address.
   *
   * @param restaurantAddress the new address string
   */
  public void setRestaurantAddress(String restaurantAddress) {
    this.restaurantAddress = restaurantAddress;
  }

  /**
   * Initializes the orders for this restaurant by fetching all orders from the restaurant service,
   * remaking each order via the database order service, and then adding them to the local state.
   *
   * @throws RuntimeException if initialization fails due to interruption or execution error
   */
  private void initializeOrders() {
    try {
      executor.submit(() -> {
        List<Order> orders = restaurantService.getRestaurantOrders(restaurantId);
        for (Order order : orders) {
          dbOrderService.remakeOrder(order.id);
          // Call the private doAddOrder directly (not addOrder) to avoid nested submission
          doAddOrder(dbOrderService.getOrder(order.id));
        }
        publisher.refreshOrderData(restaurantId);
      }).get(); // wait for initialization to finish
    } catch (InterruptedException | ExecutionException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Failed to initialize orders", e);
    }
    publisher.refreshOrderData(restaurantId);
  }

  /**
   * Handles an active batch changing by calling the appropriate dependencies.
   *
   * @param batchId the ID of the batch that changed
   */
  private void handleActiveBatchChange(long batchId) {
    twilioManager.handleBatchChange(batchId);
    updated = true;
  }

  /**
   * Handles a new batch becoming active by calling the appropriate dependencies.
   *
   * @param batchId the ID of the newly active batch
   */
  private void handleNewActiveBatch(long batchId) {
    twilioManager.handleNewBatch(batchId);
    updated = true;
  }

  /**
   * Adds an order to the restaurant's tentative batches. This operation is asynchronous and returns
   * immediately.
   *
   * @param order the order to add
   */
  public void addOrder(Order order) {
    executor.submit(() -> doAddOrder(order));
  }

  /**
   * Internal implementation of addOrder.
   *
   * @param order the order to add
   * @throws IllegalArgumentException if the order is not in COOKING state or its cookedTime is not
   *         in the future
   */
  private void doAddOrder(Order order) {
    if (order.state != State.COOKING || order.cookedTime.isBefore(Instant.now())) {
      throw new IllegalArgumentException("Orders must be COOKING and have a"
          + " cookedTime in the future when being added to the batching algorithm"
          + " for the first time. False for order id " + order.id);
    }
    batchingAlgorithm.addOrder(batches.tentativeBatches, order, restaurantAddress);
  }

  /**
   * Removes an order from restaurant's batches by ID. This operation is asynchronous.
   *
   * @param order the order to remove
   * @throws IllegalArgumentException if the order id is not found in any batch
   */
  public void removeOrder(Order order) {
    executor.submit(() -> doRemoveOrder(order));
  }

  /**
   * Internal implementation of removeOrder.
   *
   * @param order the order to remove
   * @throws IllegalArgumentException if the order id is not found in any batch
   */
  private void doRemoveOrder(Order order) {
    if (order.batchId != null) {
      // in active batch
      handleActiveBatchChange(order.batchId);
    } else if (!findAndUpdateReadyBatchOrder(order.id, true)) {
      batchingAlgorithm.removeOrder(batches.tentativeBatches, order.id, restaurantAddress);
    }
  }

  /**
   * Updates an order across all batching states. This operation is asynchronous.
   *
   * @param orderId the ID of the order to update
   * @param rebatchIfTentative whether to rebatch the order if it is currently part of a tentative
   *        batch
   */
  public void updateOrder(Long orderId, boolean rebatchIfTentative) {
    executor.submit(() -> doUpdateOrder(orderId, rebatchIfTentative));
  }

  /**
   * Internal implementation of updateOrder.
   *
   * @param orderId the ID of the order to update
   * @param rebatchIfTentative whether to rebatch the order if it is in a tentative batch
   */
  private void doUpdateOrder(Long orderId, boolean rebatchIfTentative) {
    Order order = dbOrderService.getOrder(orderId);
    if (order.batchId != null) {
      // in active batch
      handleActiveBatchChange(order.batchId);
    } else if (!findAndUpdateReadyBatchOrder(orderId, false)) {
      if (rebatchIfTentative) {
        batchingAlgorithm.rebatchOrder(batches.tentativeBatches, order, restaurantAddress);
      } else {
        batchingAlgorithm.updateOrderInplace(batches.tentativeBatches, orderId);
      }
    }
  }

  /**
   * Searches for an order in the ready batches and either removes it or updates it in place.
   *
   * @param orderId the ID of the order to find in the ready batches
   * @param delete whether to remove the order from its ready batch; if false, the order is updated
   *        in place
   * @return true if the order was found in a ready batch; false otherwise
   */
  private boolean findAndUpdateReadyBatchOrder(long orderId, boolean delete) {
    for (ReadyBatch readyBatch : batches.readyBatches) {
      List<Order> batch = readyBatch.batch;
      for (int i = 0; i < batch.size(); i++) {
        Order order = batch.get(i);
        if (order.id == orderId) {
          if (delete) {
            batch.remove(i);
          } else {
            // update
            batch.set(i, dbOrderService.getOrder(orderId));
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Rebatches an existing order within the tentative batches. This operation is asynchronous.
   *
   * @param order the updated order
   * @throws IllegalArgumentException if the order id is not found
   */
  public void rebatchTentativeOrder(Order order) {
    executor.submit(
        () -> batchingAlgorithm.rebatchOrder(batches.tentativeBatches, order, restaurantAddress));
  }

  /**
   * Updates an existing order within the tentative batches in place. This operation is
   * asynchronous.
   *
   * @param orderId the id of the order to update
   */
  public void updateTentativeOrderInplace(Long orderId) {
    executor.submit(() -> batchingAlgorithm.updateOrderInplace(batches.tentativeBatches, orderId));
  }

  /**
   * Periodic update entry point. Should be called by a scheduler; the actual work is submitted to
   * the single‑thread executor and runs asynchronously.
   *
   * @param updateMillis how much to delay delivery times for unassigned ready batches
   * @return Future representing completion of the check
   */
  public Future<?> checkExpiredBatches(final long updateMillis) {
    return executor.submit(() -> doCheckExpiredBatches(updateMillis));
  }

  /**
   * Internal implementation of periodic update.
   *
   * @param updateMillis how much to delay delivery times for unassigned ready batches
   */
  private void doCheckExpiredBatches(final long updateMillis) {
    updated = false;
    Instant now = Instant.now();

    List<Order> toBeReAdded = moveExpiredTentativeBatches(now);
    reAddDelayedOrders(toBeReAdded);

    assignReadyBatchesToDrivers();
    delayRemainingReadyBatches(updateMillis);

    removeFinishedBatches();

    if (updated) {
      publisher.refreshOrderData(restaurantId);
      this.updated = false; // reset after publishing
    }
    debugPrintBatches();
  }

  /**
   * Moves expired tentative batches into the ready queue.
   *
   * Invariants: - tentativeBatches is sorted by lastAllowedCookedTime descending - expired batches
   * appear at the end of the list
   *
   * For each expired batch: - Remove orders that are not yet cooked - Delay uncooked orders and
   * collect them to be re-added later - If any cooked orders remain, enqueue them as a ReadyBatch
   *
   * @param now the current time
   * @return orders that were delayed and must be re-added to tentative batches
   */
  private List<Order> moveExpiredTentativeBatches(Instant now) {
    List<TentativeBatch> tentativeBatches = batches.tentativeBatches;
    Queue<ReadyBatch> readyBatches = batches.readyBatches;
    List<Order> toBeReAdded = new ArrayList<>();
    for (int i = tentativeBatches.size() - 1; i >= 0; i--) {
      TentativeBatch tentativeBatch = tentativeBatches.get(i);
      if (now.isBefore(tentativeBatch.getLastAllowedCookedTime())) {
        break; // sorted by expiration
      }
      updated = true;
      tentativeBatches.remove(i);
      List<Order> orders = tentativeBatch.getBatch();
      removeUncookedOrders(orders, toBeReAdded);
      if (!orders.isEmpty()) {
        readyBatches.add(new ReadyBatch(orders));
      }
    }
    return toBeReAdded;
  }

  /**
   * Re-inserts delayed orders back into tentative batching.
   *
   * These orders had their cooked/delivery times pushed forward and must be reconsidered for future
   * batching.
   *
   * @param orders delayed orders to re-add
   */
  private void reAddDelayedOrders(List<Order> orders) {
    for (Order order : orders) {
      batchingAlgorithm.addOrder(batches.tentativeBatches, order, restaurantAddress);
    }
  }

  /**
   * Assigns ready batches to available drivers.
   *
   * Continues assigning while both: - There are ready batches waiting - There are available drivers
   *
   * For each assignment: - Compute route and expected completion time - Persist the batch - Update
   * all orders to reference the new batch - Emit batch activation events
   */
  private void assignReadyBatchesToDrivers() {
    Queue<ReadyBatch> readyBatches = batches.readyBatches;
    Queue<Driver> readyDrivers = getReadyDrivers(readyBatches.size());

    while (!readyDrivers.isEmpty()) {
      ReadyBatch readyBatch = readyBatches.poll();
      Driver driver = readyDrivers.poll();

      Batch batch = createAndPersistBatch(readyBatch, driver);
      batches.activeBatches.add(batch);
      handleNewActiveBatch(batch.id);
    }
  }

  /**
   * Pushes delivery times forward for ready batches that were not assigned to a driver during this
   * update cycle.
   *
   * @param updateMillis amount of time to delay delivery for each order
   * @throws IllegalStateException if an order in a ready batch is not in COOKED state
   */
  private void delayRemainingReadyBatches(long updateMillis) {
    for (ReadyBatch readyBatch : batches.readyBatches) {
      List<Order> orders = readyBatch.batch;

      for (int i = 0; i < orders.size(); i++) {
        Order order = orders.get(i);
        if (order.state != State.COOKED) {
          throw new IllegalStateException(
              "Order id " + order.id + " is in a ready batch with non COOKED state");
        }

        dbOrderService.updateOrderDeliveryTime(order.id,
            millisAfter(order.deliveryTime, updateMillis));

        orders.set(i, dbOrderService.getOrder(order.id));
        updated = true;
      }
    }
  }

  /**
   * Returns a queue of drivers who are currently ready to accept a new batch, up to a specified
   * maximum number.
   *
   * @param maxToGet the maximum number of ready drivers to retrieve; must be nonnegative
   * @return a queue containing up to 'maxToGet' available drivers
   * @throws IllegalArgumentException if maxToGet is less than zero
   */
  public Queue<Driver> getReadyDrivers(int maxToGet) {
    if (maxToGet < 0) {
      throw new IllegalArgumentException("maxToGet must be nonnegative");
    }
    Queue<Driver> readyDrivers = new LinkedList<Driver>();
    if (maxToGet == 0) {
      return readyDrivers;
    }
    List<Driver> allDrivers = restaurantService.getRestaurantDrivers(restaurantId);
    for (Driver d : allDrivers) {
      if (driverService.isAvailable(d.id)) {
        readyDrivers.add(d);
        if (readyDrivers.size() == maxToGet) {
          break;
        }
      }
    }
    return readyDrivers;
  }

  /**
   * Creates a persistent batch for a ready batch and assigned driver.
   *
   * - Computes the delivery route and total duration - Persists the batch - Updates all orders to
   * reference the new batch
   *
   * @param readyBatch the batch of orders ready for dispatch
   * @param driver the assigned driver
   * @return the persisted Batch
   * @throws RuntimeException if the route cannot be computed (wraps InvalidRouteException)
   */
  private Batch createAndPersistBatch(ReadyBatch readyBatch, Driver driver) {
    List<String> stops = new ArrayList<>();
    for (Order order : readyBatch.batch) {
      stops.add(order.destination);
    }
    RouteDirectionsResponse resp;
    try {
      resp = routeService.getRouteDirections(restaurantAddress, stops, false);
    } catch (InvalidRouteException e) {
      // TODO handle more elegantly
      throw new RuntimeException(e);
    }
    Instant dispatchTime = Instant.now();
    Instant expectedCompletionTime = dispatchTime.plusSeconds(resp.getDurationSeconds());
    Long batchId = dbOrderService.createBatch(
        new Batch(-1, driver.id, resp.getPolyline(), dispatchTime, expectedCompletionTime, false));

    updateBatchOrders(readyBatch.batch, batchId);
    return dbOrderService.getBatch(batchId);
  }

  /**
   * Updates each order in the given list to reference the provided batch ID, and advances its state
   * to DRIVING
   *
   * This method persists the batch assignment via OrderService and then re-fetches each order from
   * the database to ensure the in-memory list reflects the latest state after mutation.
   *
   * @param orders the orders to advance in state and associate with the batch
   * @param batchId the ID of the batch to assign to each order
   * @throws IllegalStateException if any order is not in COOKED state before assignment
   */
  private void updateBatchOrders(List<Order> orders, Long batchId) {
    for (int i = 0; i < orders.size(); i++) {
      Order order = orders.get(i);
      dbOrderService.setOrderBatchId(order.id, batchId);
      if (order.state != State.COOKED) {
        throw new IllegalStateException(
            "Order id " + order.id + " has non COOKED state before being added to a batch");
      }
      dbOrderService.advanceOrderState(order.id); // to DRIVING
      orders.set(i, dbOrderService.getOrder(order.id));
    }
  }

  /**
   * Removes orders that are not yet cooked from a batch.
   *
   * For each removed order: - Push cooked and delivery times forward - Re-fetch the updated order -
   * Add it to the list of orders to be re-batched later
   *
   * Iterates in reverse to allow safe removal.
   *
   * @param orders current batch orders (mutated in-place)
   * @param toBeReAdded accumulator for delayed orders
   * @throws IllegalStateException if an order has a state beyond COOKED
   */
  private void removeUncookedOrders(List<Order> orders, List<Order> toBeReAdded) {
    for (int j = orders.size() - 1; j >= 0; j--) {
      Order order = orders.get(j);
      if (order.state != State.COOKING && order.state != State.COOKED) {
        throw new IllegalStateException(
            "Tentatively batched order id " + order.id + " has state beyond COOKED");
      }
      if (order.state != State.COOKED) {
        orders.remove(j);
        delayOrder(order);
        // important to re-get the order after updating
        toBeReAdded.add(dbOrderService.getOrder(order.id));
      }
    }
  }

  /**
   * Pushes both cooked time and delivery time forward for an order that was not ready when
   * expected.
   *
   * @param order the order to delay
   */
  private void delayOrder(Order order) {
    Instant newCookedTime = secondsAfter(Instant.now(), SECONDS_ADDITIONAL_COOK_TIME);
    Duration timeBetween = Duration.between(order.cookedTime, order.deliveryTime);
    Instant newDeliveryTime = newCookedTime.plus(timeBetween);

    dbOrderService.updateOrderCookedTime(order.id, newCookedTime);
    dbOrderService.updateOrderDeliveryTime(order.id, newDeliveryTime);
  }

  /**
   * Removes all batches in batches.activeBatches that have been finished
   */
  private void removeFinishedBatches() {
    List<Batch> activeBatches = batches.activeBatches;
    for (int i = activeBatches.size() - 1; i >= 0; i--) {
      Batch batch = activeBatches.get(i);
      if (dbOrderService.getBatch(batch.id).finished) {
        activeBatches.remove(i);
      }
    }
  }

  /*
   * Returns the Instant 'millis' milliseconds after the given time.
   *
   * @param time the base instant
   * @param millis the number of milliseconds to add
   * @return the new Instant
   */
  private Instant millisAfter(Instant time, long millis) {
    return time.plus(Duration.ofMillis(millis));
  }

  /**
   * Returns the Instant 'seconds' seconds after the given time.
   *
   * @param time the base instant
   * @param seconds the number of seconds to add
   * @return the new Instant
   */
  private Instant secondsAfter(Instant time, long seconds) {
    return millisAfter(time, seconds * 1000);
  }

  /**
   * Prints debug information about all batches to System.out.
   */
  public void debugPrintBatches() {
    System.out.println("\n\n\n");
    Instant now = Instant.now();
    System.out.printf("=== Restaurant %d batch state at %s ===%n", restaurantId, now);

    // ----- Tentative Batches -----
    System.out.printf("--- Tentative Batches (%d) ---%n", batches.tentativeBatches.size());
    int tbIdx = 0;
    for (TentativeBatch tb : batches.tentativeBatches) {
      String lastAllowed = formatMinutes(now, tb.getLastAllowedCookedTime());
      System.out.printf("  Tentative Batch %d (lastAllowedCookedTime=%s min):%n", tbIdx++,
          lastAllowed);
      int orderIdx = 0;
      for (Order order : tb.getBatch()) {
        String cookedMin = formatMinutes(now, order.cookedTime);
        String deliveryMin = formatMinutes(now, order.deliveryTime);
        System.out.printf("    Order[%d] id=%-6d (%s) cooked=%s min, delivery=%s min%n", orderIdx++,
            order.id, order.state.name(), cookedMin, deliveryMin);
      }
    }

    // ----- Ready Batches -----
    System.out.printf("--- Ready Batches (%d) ---%n", batches.readyBatches.size());
    int rbIdx = 0;
    for (ReadyBatch rb : batches.readyBatches) {
      System.out.printf("  Ready Batch %d:%n", rbIdx++);
      int orderIdx = 0;
      for (Order order : rb.getBatch()) {
        String cookedMin = formatMinutes(now, order.cookedTime);
        String deliveryMin = formatMinutes(now, order.deliveryTime);
        System.out.printf("    Order[%d] id=%-6d (%s) cooked=%s min, delivery=%s min%n", orderIdx++,
            order.id, order.state.name(), cookedMin, deliveryMin);
      }
    }

    // ----- Active Batches -----
    System.out.printf("--- Active Batches (%d) ---%n", batches.activeBatches.size());
    int abIdx = 0;
    for (Batch batch : batches.activeBatches) {
      System.out.printf("  Active Batch %d (id=%d):%n", abIdx++, batch.id);
      List<Order> orders = dbOrderService.getBatchOrders(batch.id);
      int orderIdx = 0;
      for (Order order : orders) {
        String cookedMin = formatMinutes(now, order.cookedTime);
        String deliveryMin = formatMinutes(now, order.deliveryTime);
        System.out.printf("    Order[%d] id=%-6d (%s) cooked=%s min, delivery=%s min%n", orderIdx++,
            order.id, order.state.name(), cookedMin, deliveryMin);
      }
    }
  }

  /**
   * Returns minutes from 'now' to 'time' as a formatted string with one decimal place.
   *
   * @param now the reference time
   * @param time the target time, may be null
   * @return formatted string representing minutes difference, or "null" if time is null
   */
  private String formatMinutes(Instant now, Instant time) {
    if (time == null)
      return "null";
    double minutes = (time.toEpochMilli() - now.toEpochMilli()) / (1000.0 * 60.0);
    // Show sign and one decimal
    return String.format("%+.1f", minutes);
  }

  /**
   * Shuts down the internal executor. Should be called when this manager is no longer needed (e.g.,
   * when restaurant is deleted or application shuts down).
   */
  public void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
