package com.batchable.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.exception.InvalidRouteException;
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
  private final DbOrderService dbOrderService;
  private final int SECONDS_TO_HAND_DELIVER = 6; // seconds to park, walk up, walk back
  private final int PADDING_SECONDS = 60;  // expected upper bound in variability of direct route time estimates

  // Cache travel times between origins and destinations, expiring 15 minutes after write
  private final Cache<String, Integer> travelTimeCache =
      CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).maximumSize(1000).build();

  /**
   * Represents a tentative batch of orders.
   */
  public static class TentativeBatch {
    private final List<Order> batch;
    private Instant latestAllowedCookedTime;

    public TentativeBatch(List<Order> batch, Instant latestAllowedCookedTime) {
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
  public BatchingAlgorithm(RouteService routeService, DbOrderService dbOrderService) {
    this.routeService = routeService;
    this.dbOrderService = dbOrderService;
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
      try {
        tentativeBatch.latestAllowedCookedTime =
            getLastAllowedCookedTime(batch.get(0), restaurantAddress);
      } catch (InvalidRouteException e) {
        throw new IllegalStateException(
            "Order with id " + batch.get(0).id + " no longer has a valid route from the restaurant",
            e);
      }


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
   * Updates an existing order within the tentative batches.
   *
   * @param batches the list of tentative batches for a restaurant
   * @param orderId the id of the order to update
   *
   * @throws IllegalStateException if the order has state that is not COOKING or COOKED
   */
  public void updateOrderInplace(final List<TentativeBatch> batches, final long orderId) {
    Order order = dbOrderService.getOrder(orderId);
    if (order.state != State.COOKING && order.state != State.COOKED) {
      throw new IllegalStateException(
          "Order id " + order.id + " has non COOKED and non COOKING state");
    }

    int[] inds = findOrder(batches, orderId, true);
    int i = inds[0];
    int j = inds[1];
    List<Order> batch = batches.get(i).batch;
    batch.set(j, order);
  }

  /**
   * Adds an order to the earliest batch where it can fit according to delivery and cook-time
   * constraints, or creates a new batch if necessary.
   *
   * @param batches the current list of tentative batches for a single restaurant, sorted by latest
   *        allowed cooked time in descending order
   * @param order the order to insert into the batching structure
   * @param restaurantAddress the address of the restaurant associated with these batches
   *
   * @throws IllegalStateException if initialTime <= cookedTime <= deliveryTime is violated, if the
   *         order is not in the COOKING state or is already past its cooked time when added, or if
   *         the order cannot be routed to a valid delivery location
   */
  public void addOrder(final List<TentativeBatch> batches, final Order order,
      String restaurantAddress) {
    if (order.initialTime.isAfter(order.cookedTime)
        || order.cookedTime.isAfter(order.deliveryTime)) {
      throw new IllegalStateException(
          "Must have initialTime <= cookedTime <= deliveryTime. Order id " + order.id + " failed");
    }

    Instant lastAllowedCookedTime;
    try {
      lastAllowedCookedTime = getLastAllowedCookedTime(order, restaurantAddress);
    } catch (InvalidRouteException e) {
      // Order location is invalid
      // Use invariant that order has already been added to db
      dbOrderService.removeOrder(order.id);
      throw new IllegalStateException(
          "Could not find a route from the restaurant to order id " + order.id, e);
    }
    if (order.cookedTime.isAfter(lastAllowedCookedTime)) {
      Order updatedOrder = fixDeliveryTime(order, lastAllowedCookedTime);
      addOrder(batches, updatedOrder, restaurantAddress);
      return;
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
        tentativeBatch.latestAllowedCookedTime = lastAllowedCookedTime;
        batches.remove(i);
        insertBatch(batches, tentativeBatch);
      }
      return;
    }
    insertBatch(batches, createNewBatchWithOrder(order, lastAllowedCookedTime, restaurantAddress));
  }

  /**
   * Adjusts an order’s delivery time to accommodate a cooked time that exceeds the latest allowed
   * cooked time.
   *
   * The delivery time is pushed back by the excess amount plus a fixed padding, and the updated
   * order is persisted and reloaded from the database.
   *
   * @param order the order to update
   * @param lastAllowedCookedTime the latest permissible cooked time for this order
   * @return the updated order with an adjusted delivery time
   * @throws IllegalArgumentException if lastAllowedCookedTime is after the order’s cookedTime
   */
  private Order fixDeliveryTime(final Order order, final Instant lastAllowedCookedTime) {
    Duration difference = Duration.between(lastAllowedCookedTime, order.cookedTime);
    if (difference.isNegative()) {
      throw new IllegalArgumentException(
          "This method must only be called with a lastAllowedCookedTime earlier than the order's cookedTime");
    }
    Duration paddedDifference = difference.plus(Duration.ofSeconds(PADDING_SECONDS));
    dbOrderService.updateOrderDeliveryTime(order.id, order.deliveryTime.plus(paddedDifference));
    Order updatedOrder = dbOrderService.getOrder(order.id);
    return updatedOrder;
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
   * @param latestAllowedCookedTime the Instant representing the latest time the initial order
   *        should be cooked by to deliver it on time
   * @param restaurantAddress the address of the restaurant preparing the order
   * @return a newly created tentative batch
   */
  private TentativeBatch createNewBatchWithOrder(Order order, Instant latestAllowedCookedTime,
      String restaurantAddress) {
    List<Order> batch = new ArrayList<Order>();
    batch.add(order);
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
  private Instant getLastAllowedCookedTime(Order firstOrder, String restaurantAddress)
      throws InvalidRouteException {
    int firstDeliverySeconds = secondsToMakeDelivery(restaurantAddress, firstOrder.destination);
    Instant lastAllowedCookedTime =
        // We subtract one to make it easier to use whole minutes in tests,
        // alternatively could have just made SECONDS_TO_HAND_DELIVER not divisible by 60
        firstOrder.deliveryTime.minus(Duration.ofSeconds(firstDeliverySeconds - 1));
    return lastAllowedCookedTime;
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
    int deliverySeconds;
    try {
      deliverySeconds = secondsToMakeDelivery(from.destination, to.destination);
    } catch (InvalidRouteException e) {
      // cannot calculate direct route from 'from' to 'to'
      return false;
    }
    // We subtract one to make it easier to use whole minutes in tests,
    // alternatively could have just made SECONDS_TO_HAND_DELIVER not divisible by 60
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
  private int secondsToMakeDelivery(String from, String to) throws InvalidRouteException {
    String key = from + "→" + to;
    Integer travelTime = travelTimeCache.getIfPresent(key);
    if (travelTime == null) {
      travelTime = routeService.getSecondsBetween(from, to) + SECONDS_TO_HAND_DELIVER;
      travelTimeCache.put(key, travelTime);
    }
    return travelTime;
  }

  public int getSecondsToHandDeliver() {
    return SECONDS_TO_HAND_DELIVER;
  }

  public int getPaddingSeconds() {
    return PADDING_SECONDS;
  }
}
