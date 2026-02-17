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
import com.batchable.backend.db.models.Order.State;
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
   * ensures batches with earlier deadlines are at the back of the list for efficient retrieval.
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
   * Finds the index of an order in the batches.
   *
   * @param batches the list of tentative batches
   * @param orderId the id of the order to find
   * @param throwIfMissing whether to throw if order is not found
   * @return an array [batchIndex, orderIndex] or null if not found and throwIfMissing=false
   */
  private int[] findOrder(final List<TentativeBatch> batches, final long orderId,
      boolean throwIfMissing) {
    for (int i = 0; i < batches.size(); i++) {
      TentativeBatch tentativeBatch = batches.get(i);
      List<Order> batch = tentativeBatch.batch;
      for (int j = 0; j < batch.size(); j++) {
        Order order = batch.get(j);
        if (orderId == order.id) {
          return new int[] {i, j};
        }
      }
    }
    if (throwIfMissing) {
      throw new IllegalArgumentException(
          "Given batches do not contain the order specified by id " + orderId + ".");
    }
    return null;
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
    int[] inds = findOrder(batches, orderId, true);
    int i = inds[0];
    int j = inds[1];
    TentativeBatch tentativeBatch = batches.get(i);
    List<Order> batch = tentativeBatch.batch;
    batch.remove(j); // remove the order from the batch

    if (batch.isEmpty()) {
      // If removing this order empties the batch, remove the batch entirely
      batches.remove(i);
    } else if (j == 0) {
      // If the removed order was the first in the batch, recompute latestAllowedCookedTime
      tentativeBatch.latestAllowedCookedTime =
          getLastAllowedCookedTime(batch.get(0), restaurantAddress);

      // Remove and reinsert batch to maintain correct order in the batch list
      batches.remove(i);
      insertBatch(batches, tentativeBatch);
    }
  }

  /**
   * Rebatches an existing order in the batching structure.
   *
   * The order is updated by removing the existing instance and re-adding it, ensuring all batching
   * and delivery constraints are re-evaluated.
   *
   * @param batches the list of tentative batches for a restaurant
   * @param order the updated order
   * @param restaurantAddress the address of the restaurant
   * @throws IllegalArgumentException if the order id is not found
   */
  public void rebatchOrder(final List<TentativeBatch> batches, final Order order,
      String restaurantAddress) {
    removeOrder(batches, order.id, restaurantAddress);
    addOrder(batches, order, restaurantAddress);
  }

  /**
   * Updates the state of an existing order within the tentative batches.
   *
   * @param batches the list of tentative batches for a restaurant
   * @param orderId the id of the order to update
   * @param newState the new state to assign to the order
   *
   * @throws IllegalArgumentException if the order id is not found
   */
  public void updateOrderState(final List<TentativeBatch> batches, final long orderId,
      final State newState) {
    int[] inds = findOrder(batches, orderId, true);
    int i = inds[0];
    int j = inds[1];
    List<Order> batch = batches.get(i).batch;
    Order oldOrder = batch.get(j);
    Order newOrder = new Order(oldOrder.id, oldOrder.restaurantId, oldOrder.destination,
        oldOrder.itemNamesJson, oldOrder.initialTime, oldOrder.deliveryTime, oldOrder.cookedTime,
        newState, oldOrder.highPriority, oldOrder.batchId);
    batch.set(j, newOrder);
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
        continue; // cannot fit in this batch
      }

      int insertionInd = findInsertionIndex(tentativeBatch.batch, order);
      if (!canInsertAt(tentativeBatch.batch, order, insertionInd)) {
        continue; // violates delivery ordering
      }

      tentativeBatch.batch.add(insertionInd, order);

      // Reinsert batch if first order changed to maintain descending order by
      // latestAllowedCookedTime
      if (insertionInd == 0) {
        tentativeBatch.latestAllowedCookedTime = getLastAllowedCookedTime(order, restaurantAddress);
        batches.remove(i);
        insertBatch(batches, tentativeBatch);
      }
      return;
    }
    insertBatch(batches, createNewBatchWithOrder(order, restaurantAddress));
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
