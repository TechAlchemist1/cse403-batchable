package com.batchable.backend.service.internal;

// import java.net.URLEncoder;
// import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.exception.InvalidRouteException;
import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.twilio.TwilioManager;
import com.batchable.backend.service.BatchingAlgorithm.TentativeBatch;
import com.batchable.backend.util.Log;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.RestaurantService;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

/**
 * Manages order batching for a single restaurant.
 *
 * Responsibilities: - Holds tentative, ready, and active batches for this restaurant. - Assigns
 * batches to drivers when ready and computes routes/duration. - Emits events when batches change or
 * become active. - Periodically checks for expired tentative batches.
 */
public class RestaurantBatchingManager {

  private final long restaurantId;
  private String restaurantAddress;
  private final OrderWebSocketPublisher publisher;
  private final BatchingAlgorithm batchingAlgorithm;
  private final Batches batches;
  private final RouteService routeService;
  private final DbOrderService dbOrderService;
  private final DriverService driverService;
  private final RestaurantService restaurantService;
  private final TwilioManager twilioManager;
  // time to add when an order isn't ready when it should be for batching
  private static final long SECONDS_ADDITIONAL_COOK_TIME = 6;
  private boolean updated = false; // flag for if checkExpiredBatches() made any changes to batches

  /**
   * Constructs a batching manager for a single restaurant.
   *
   * @param restaurantId the restaurant ID
   * @param restaurantAddress the restaurant's address (used for routing)
   * @param publisher websocket publisher to notify clients
   * @param batchingAlgorithm algorithm for forming batches
   * @param routeService service for computing route polylines and duration
   */
  public RestaurantBatchingManager(long restaurantId, String restaurantAddress,
      OrderWebSocketPublisher publisher, BatchingAlgorithm batchingAlgorithm,
      RouteService routeService, DbOrderService dbOrderService, DriverService driverService,
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

    public ReadyBatch(List<Order> batch) {
      this.batch = new ArrayList<Order>(batch);
    }

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
   * Returns the current batches container for this restaurant.
   *
   * @return the Batches object holding tentative, ready, and active batches
   */
  public Batches getBatches() {
    return this.batches;
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
   */
  private void initializeOrders() {
    List<Order> orders = restaurantService.getRestaurantOrders(restaurantId);
    for (Order order : orders) {
      dbOrderService.remakeOrder(order.id);
      addOrder(dbOrderService.getOrder(order.id));
    }
  }

  /** Handles an active batch changing by calling the appropriate dependencies. */
  private void handleActiveBatchChange(long batchId) {
    twilioManager.handleBatchChange(batchId, restaurantAddress);
    updated = true;
  }

  /** Handles a new batch becoming active by calling the appropriate dependencies. */
  private void handleNewActiveBatch(long batchId) {
    twilioManager.handleNewBatch(batchId, restaurantAddress);
    updated = true;
  }

  /**
   * Adds an order to the restaurant's tentative batches.
   *
   * @param order the order to add
   */
  public void addOrder(Order order) {
    batchingAlgorithm.addOrder(batches.tentativeBatches, order, restaurantAddress);
  }

  /**
   * Removes an order from restaurant's batches by ID.
   *
   * @param orderId the id of the order to remove
   * @throws IllegalArgumentException if the order id is not found
   */
  public void removeOrder(Long orderId) {
    Order order = dbOrderService.getOrder(orderId);
    if (order.batchId != null) {
      // in active batch
      handleActiveBatchChange(order.batchId);
    } else if (!findAndUpdateReadyBatchOrder(orderId, true)) {
      batchingAlgorithm.removeOrder(batches.tentativeBatches, orderId, restaurantAddress);
    }
  }

  /**
   * Updates an order across all batching states.
   *
   * If the order is part of an active batch, a batch change event is emitted. Otherwise, the method
   * attempts to update the order within any ready batch. If the order is not found in a ready
   * batch, it is handled as a tentative order: either rebatched (removed and re-added) or updated
   * in place, depending on the rebatchIfTentative flag.
   *
   * @param orderId the ID of the order to update
   * @param rebatchIfTentative whether to rebatch the order if it is currently part of a tentative
   *        batch
   */
  public void updateOrder(Long orderId, boolean rebatchIfTentative) {
    Order order = dbOrderService.getOrder(orderId);
    if (order.batchId != null) {
      // in active batch
      handleActiveBatchChange(order.batchId);
    } else if (!findAndUpdateReadyBatchOrder(orderId, false)) {
      if (rebatchIfTentative) {
        rebatchTentativeOrder(order);
      } else {
        updateTentativeOrderInplace(orderId);
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
   * Rebatches an existing order within the tentative batches.
   *
   * The order is updated by removing the existing instance (by id) and re-adding it, ensuring all
   * batching and delivery constraints are re-evaluated.
   *
   * @param order the updated order
   * @throws IllegalArgumentException if the order id is not found
   */
  public void rebatchTentativeOrder(Order order) {
    batchingAlgorithm.rebatchOrder(batches.tentativeBatches, order, restaurantAddress);
  }

  /**
   * Updates an existing order within the tentative batches in place.
   *
   * @param batches the list of tentative batches for a restaurant
   * @param orderId the id of the order to update
   * @param newState the new state to assign to the order
   *
   * @throws IllegalArgumentException if the order id is not found
   */
  public void updateTentativeOrderInplace(Long orderId) {
    batchingAlgorithm.updateOrderInplace(batches.tentativeBatches, orderId);
  }

  /**
   * Periodic update entry point.
   *
   * High-level flow: 1. Move expired tentative batches into the ready queue. - Orders that are not
   * yet cooked are delayed and re-added to tentative batches. 2. Re-add delayed orders back into
   * tentative batching. 3. Assign as many ready batches as possible to available drivers. 4. Push
   * remaining ready batches forward in time (update delivery times to reflect delay).
   *
   * @param updateMillis how much to delay delivery times for unassigned ready batches
   */
  public void checkExpiredBatches(final long updateMillis) {
    updated = false;
    System.out.println("\n\n\n");
    debugPrintBatches();
    Instant now = Instant.now();

    List<Order> toBeReAdded = moveExpiredTentativeBatches(now);
    reAddDelayedOrders(toBeReAdded);

    assignReadyBatchesToDrivers();
    delayRemainingReadyBatches(updateMillis);

    if (updated) {
      publisher.refreshOrderData(restaurantId);
    }
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
      System.out.println("ready drivers " + readyDrivers.toString());

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

  // returns the Instant 'millis' milliseconds after the given time
  private Instant millisAfter(Instant time, long millis) {
    return time.plus(Duration.ofMillis(millis));
  }

  // returns the Instant 'seconds' seconds after the given time
  private Instant secondsAfter(Instant time, long seconds) {
    return millisAfter(time, seconds * 1000);
  }



  public void debugPrintBatches() {
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
      List<Order> orders = dbOrderService.getBatchOrders(batch.id); // you need this method
      int orderIdx = 0;
      for (Order order : orders) {
        String cookedMin = formatMinutes(now, order.cookedTime);
        String deliveryMin = formatMinutes(now, order.deliveryTime);
        System.out.printf("    Order[%d] id=%-6d (%s) cooked=%s min, delivery=%s min%n", orderIdx++,
            order.id, order.state.name(), cookedMin, deliveryMin);
      }
    }
  }

  /** Returns minutes from 'now' to 'time' as a formatted string with one decimal place. */
  private String formatMinutes(Instant now, Instant time) {
    if (time == null)
      return "null";
    double minutes = (time.toEpochMilli() - now.toEpochMilli()) / (1000.0 * 60.0);
    // Show sign and one decimal
    return String.format("%+.1f", minutes);
  }
}
