package com.batchable.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.batchable.backend.db.models.Order;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;

/**
 * BatchingAlgorithm is responsible for grouping orders into delivery batches based on cooked times,
 * delivery times, and estimated travel durations.
 * 
 * Orders are added to the earliest possible batch where they fit according to delivery constraints.
 * Batches are internally sorted by latest allowed cooked time in descending order to efficiently
 * identify the next batch ready for dispatch.
 */
@Service
public class BatchingAlgorithm {
  private final RouteService routeService;
  private final int SECONDS_TO_HAND_DELIVER = 300; // seconds to park, walk up, walk back

  // Cache travel times between origins and destinations, expiring 15 minutes after write
  private final Cache<String, Integer> travelTimeCache =
      CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(1000).build();

  /**
   * Represents a tentative batch of orders.
   */
  public class TentativeBatch {
    private final List<Order> batch;
    private Instant latestAllowedCookedTime;

    private TentativeBatch(List<Order> batch, Instant latestAllowedCookedTime) {
      this.batch = batch;
      this.latestAllowedCookedTime = latestAllowedCookedTime;
    }

    public Instant getLastAllowedCookedTime() {
      return this.latestAllowedCookedTime;
    }

    public List<Order> getBatch() {
      return this.batch;
    }
  }

  /** Comparator to sort orders by their delivery time in ascending order. */
  Comparator<Order> byDeliveryTime = Comparator.comparing(o -> o.deliveryTime);

  /**
   * Comparator to sort TentativeBatches by latest allowed cooked time in descending order. This
   * ensures batches that need to be dispatched sooner are at the back of the list for efficient
   * retrieval.
   */
  Comparator<TentativeBatch> byLatestAllowedCookedTime =
      Comparator.comparing((TentativeBatch tb) -> tb.latestAllowedCookedTime).reversed();

  /**
   * Constructs a BatchingAlgorithm with the given RouteService.
   * 
   * @param routeService the service used to compute travel durations between addresses
   */
  public BatchingAlgorithm(RouteService routeService) {
    this.routeService = routeService;
  }

  /**
   * Removes an order from the batching structure.
   *
   * If the order is the first order in its batch, the batch’s latestAllowedCookedTime is recomputed
   * and the batch is reinserted to preserve sorted order.
   *
   * If removing the order leaves the batch empty, the batch itself is removed.
   *
   * @param batches the list of tentative batches for a restaurant, sorted by
   *        latestAllowedCookedTime in descending order
   * @param orderId the id of the order to remove
   * @param restaurantAddress the address of the restaurant
   * @throws IllegalArgumentException if the order id is not found
   */
  public void removeOrder(final List<TentativeBatch> batches, final long orderId,
      String restaurantAddress) {

    for (int i = 0; i < batches.size(); i++) {
      TentativeBatch tentativeBatch = batches.get(i);
      List<Order> batch = tentativeBatch.batch;
      for (int j = 0; j < batch.size(); j++) {
        Order order = batch.get(j);

        if (orderId == order.id) {
          batch.remove(j);
          if (batch.isEmpty()) {
            batches.remove(i);
          } else if (j == 0) {
            tentativeBatch.latestAllowedCookedTime =
                getLastAllowedCookedTime(batch.get(0), restaurantAddress);
            batches.remove(i);
            insertBatch(batches, tentativeBatch);
          }
          return;
        }
        
      }
    }
    throw new IllegalArgumentException("Order id not found: " + orderId);
  }

  /**
   * Updates an existing order in the batching structure.
   *
   * The order is updated by removing the existing instance and re-adding it, ensuring all batching
   * and delivery constraints are re-evaluated.
   *
   * @param batches the list of tentative batches for a restaurant
   * @param order the updated order
   * @param restaurantAddress the address of the restaurant
   */
  public void updateOrder(final List<TentativeBatch> batches, final Order order,
      String restaurantAddress) {
    removeOrder(batches, order.id, restaurantAddress);
    addOrder(batches, order, restaurantAddress);
  }

  /**
   * Adds an order to the earliest batch where it can fit according to delivery and cook-time
   * constraints, or creates a new batch if necessary.
   * 
   * @param batches the current list of tentative batches for a single restaurant, sorted by latest
   *        allowed cooked time in descending order
   * @param order the order to insert into the batching structure
   * @param restaurantAddress the address of the restaurant associated with these batches
   */
  public void addOrder(final List<TentativeBatch> batches, final Order order,
      String restaurantAddress) {
    if (order.initialTime.isAfter(order.cookedTime)
        || order.cookedTime.isAfter(order.deliveryTime)) {
      throw new IllegalStateException("Orders must have initialTime < cookedTime < deliveryTime");
    }
    for (int i = 0; i < batches.size(); i++) {
      TentativeBatch tentativeBatch = batches.get(i);

      if (order.cookedTime.isAfter(tentativeBatch.latestAllowedCookedTime)) {
        System.out.println("\nAFTER LATEST!!!");
        System.out.println("first order delivery time " + tentativeBatch.batch.get(0).deliveryTime);
        System.out.println("first order cooked time " + tentativeBatch.batch.get(0).cookedTime);
        System.out.println("latest allowed cooked time " + tentativeBatch.latestAllowedCookedTime);
        System.out.println("curr order cooked time " + order.cookedTime);
        continue; // cannot fit in this batch
      }

      int insertionInd = findInsertionIndex(tentativeBatch.batch, order);
      if (!canInsertAt(tentativeBatch.batch, order, insertionInd)) {
        System.out.println("\nVIOLATES DELIV!!!!");
        System.out.println("couldnt insert in batch " + i + " at index " + insertionInd);
        continue; // violates delivery ordering
      }

      tentativeBatch.batch.add(insertionInd, order);

      // Reinsert batch if first order changed to maintain descending order by
      // latestAllowedCookedTime
      if (insertionInd == 0) {
        System.out.println("\n!!!REINSERTING!\n!");
        tentativeBatch.latestAllowedCookedTime = getLastAllowedCookedTime(order, restaurantAddress);
        batches.remove(i);
        insertBatch(batches, tentativeBatch);
      }
      printBatchSizes(batches);
      return;
    }
    insertBatch(batches, createNewBatchWithOrder(order, restaurantAddress));
    printBatchSizes(batches);

  }

  private void printBatchSizes(List<TentativeBatch> l) {
    System.out.println("\nNUM BATCHES: " + l.size());
    for (int i = 0; i < l.size(); i++) {
      System.out.println("NUM ORDERS IN BATCH " + i + ": " + l.get(i).batch.size());
    }
  }

  /**
   * Finds the insertion index for an order in a sorted batch list by delivery time.
   * 
   * @param batch the list of orders
   * @param order the order to insert
   * @return index at which to insert the order
   */
  private int findInsertionIndex(List<Order> batch, Order order) {
    int idx = Collections.binarySearch(batch, order, byDeliveryTime);
    return idx >= 0 ? idx + 1 : -idx - 1;
  }

  /**
   * Finds the insertion index for a tentative batch in the list of batches.
   *
   * @param batches the list of existing batches
   * @param newBatch the batch to insert
   * @return the index at which the batch should be inserted
   */
  private int findBatchInsertionIndex(final List<TentativeBatch> batches,
      final TentativeBatch newBatch) {
    final int idx = Collections.binarySearch(batches, newBatch, byLatestAllowedCookedTime);
    return idx >= 0 ? idx : -idx - 1;
  }

  /**
   * Inserts a tentative batch into the batch list while preserving sort order.
   *
   * @param batches the list of existing batches
   * @param newBatch the batch to insert
   */
  private void insertBatch(final List<TentativeBatch> batches, final TentativeBatch newBatch) {
    batches.add(findBatchInsertionIndex(batches, newBatch), newBatch);
  }

  /**
   * Determines whether an order can be inserted at a specific position in a batch without violating
   * delivery-time constraints with neighboring orders.
   *
   * @param batch the batch being modified
   * @param order the order to insert
   * @param ind the proposed insertion index
   * @return true if the order can be safely inserted, false otherwise
   */
  private boolean canInsertAt(List<Order> batch, Order order, int ind) {
    boolean prevOkay = ind == 0 || canFollow(batch.get(ind - 1), order);
    boolean nextOkay = ind == batch.size() || canFollow(order, batch.get(ind));
    return prevOkay && nextOkay;
  }

  /**
   * Creates a new batch containing a single order.
   *
   * @param order the initial order in the batch
   * @param restaurantAddress the address of the restaurant preparing the order
   * @return a newly created tentative batch
   */
  private TentativeBatch createNewBatchWithOrder(Order order, String restaurantAddress) {
    List<Order> batch = new ArrayList<Order>();
    batch.add(order);
    final Instant latestAllowedCookedTime = getLastAllowedCookedTime(order, restaurantAddress);
    return new TentativeBatch(batch, latestAllowedCookedTime);
  }

  /**
   * Computes the latest allowable cooked time for an order based on the travel time from the
   * restaurant to the order’s first delivery destination.
   *
   * @param firstOrder the first order in a batch
   * @param restaurantAddress the address of the restaurant
   * @return the latest instant at which the order may be cooked
   */
  private Instant getLastAllowedCookedTime(Order firstOrder, String restaurantAddress) {
    int firstDeliverySeconds = secondsToMakeDelivery(restaurantAddress, firstOrder.destination);
    Instant lastAllowedCookTime =
        firstOrder.deliveryTime.minus(Duration.ofSeconds(firstDeliverySeconds - 1));
    if (lastAllowedCookTime.isBefore(firstOrder.cookedTime)) {
      throw new IllegalStateException(
          "Cannot have cooked time later than the latest allowed cooktime to deliver on time");
    }
    return lastAllowedCookTime;
  }

  /**
   * Determines whether one order can be delivered after another without violating delivery-time
   * constraints.
   *
   * @param from the earlier order in the delivery sequence
   * @param to the later order in the delivery sequence
   * @return true if 'to' can follow 'from', false otherwise
   */
  private boolean canFollow(Order from, Order to) {
    if (Duration.between(from.deliveryTime, to.deliveryTime)
        .toSeconds() < SECONDS_TO_HAND_DELIVER) {
      return false;
    }
    int deliverySeconds = secondsToMakeDelivery(from.destination, to.destination);
    Instant earliestArrival = from.deliveryTime.plus(Duration.ofSeconds(deliverySeconds - 1));
    return earliestArrival.isBefore(to.deliveryTime);
  }

  /**
   * Computes the travel time between two locations, using a cache to avoid repeated calls to the
   * RouteService.
   *
   * @param from the origin address
   * @param to the destination address
   * @return time to make the delivery in seconds, including handoff overhead
   * @throws RuntimeException if the underlying route service call fails
   */
  private int secondsToMakeDelivery(String from, String to) {
    String key = from + "→" + to;
    System.out.println("from: " + from);
    System.out.println("to: " + to);
    try {
      return travelTimeCache.get(key,
          () -> routeService.getSecondsBetween(from, to) + SECONDS_TO_HAND_DELIVER);
    } catch (ExecutionException e) {
      throw new RuntimeException("Google API call failed for route: " + from + " → " + to,
          e.getCause());
    }
  }

  public int getSecondsToHandDeliver() {
    return SECONDS_TO_HAND_DELIVER;
  }
}
