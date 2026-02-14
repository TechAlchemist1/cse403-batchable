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
 * BatchingAlgorithm is responsible for grouping orders into delivery batches
 * based on cooked times, delivery times, and estimated travel durations.
 * 
 * Orders are added to the earliest possible batch where they fit according
 * to delivery constraints. Batches are internally sorted by latest allowed
 * cooked time in descending order to efficiently identify the next batch ready
 * for dispatch.
 */
@Service
public class BatchingAlgorithm {

  private final RouteService routeService;
  private final List<TentativeBatch> batches;
  private final int SECONDS_TO_HAND_DELIVER = 360; // seconds to park, walk up, walk back
  private final String restaurantAddress;

  // Cache travel times between origins and destinations, expiring 15 minutes after write
  private final Cache<String, Integer> travelTimeCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(15, TimeUnit.MINUTES)
          .maximumSize(1000)
          .build();

  /**
   * Represents a tentative batch of orders.
   */
  private class TentativeBatch {
    private final List<Order> batch;
    private Instant latestAllowedCookedTime;

    private TentativeBatch(List<Order> batch, Instant latestAllowedCookedTime) {
      this.batch = batch;
      this.latestAllowedCookedTime = latestAllowedCookedTime;
    }
  }

  /** Comparator to sort orders by their delivery time in ascending order. */
  Comparator<Order> byDeliveryTime = Comparator.comparing(o -> o.deliveryTime);

  /**
   * Comparator to sort TentativeBatches by latest allowed cooked time in descending order.
   * This ensures batches that need to be dispatched sooner are at the back of the list for
   * efficient retrieval.
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
    this.batches = new ArrayList<>();
    this.restaurantAddress = "dummy for now"; // TODO: configure real address
  }

  /**
   * Adds an order to the earliest batch where it can fit according to delivery and cook-time
   * constraints, or creates a new batch if necessary.
   * 
   * @param order the order to add
   */
  public void addOrder(final Order order) {
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

      // Reinsert batch if first order changed to maintain descending order by latestAllowedCookedTime
      if (insertionInd == 0) {
        tentativeBatch.latestAllowedCookedTime = getLastAllowedCookedTime(order);
        batches.remove(i);
        insertBatch(tentativeBatch);
      }
      return;
    }
    insertBatch(createNewBatchWithOrder(order));
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
   * Finds the insertion index for a batch in the list of batches.
   * 
   * @param newBatch the batch to insert
   * @return index at which to insert the batch
   */
  private int findBatchInsertionIndex(TentativeBatch newBatch) {
    int idx = Collections.binarySearch(batches, newBatch, byLatestAllowedCookedTime);
    return idx >= 0 ? idx : -idx - 1;
  }

  /** Inserts a batch into the list of batches in the correct order. */
  private void insertBatch(TentativeBatch newBatch) {
    batches.add(findBatchInsertionIndex(newBatch), newBatch);
  }

  /**
   * Checks if an order can be inserted at a specific index in a batch without violating
   * delivery ordering constraints.
   */
  private boolean canInsertAt(List<Order> batch, Order order, int ind) {
    boolean prevOkay = ind == 0 || canFollow(batch.get(ind - 1), order);
    boolean nextOkay = ind == batch.size() || canFollow(order, batch.get(ind));
    return prevOkay && nextOkay;
  }

  /** Creates a new batch containing a single order. */
  private TentativeBatch createNewBatchWithOrder(Order order) {
    List<Order> batch = new ArrayList<Order>();
    batch.add(order);
    Instant latestAllowedCookedTime = getLastAllowedCookedTime(order);
    return new TentativeBatch(batch, latestAllowedCookedTime);
  }

  /**
   * Computes the latest allowed cooked time for an order based on travel time from
   * the restaurant to the delivery location.
   */
  private Instant getLastAllowedCookedTime(Order firstOrder) {
    int firstDeliveryTime = secondsToMakeDelivery(restaurantAddress, firstOrder.destination);
    return firstOrder.cookedTime.minus(Duration.ofSeconds(firstDeliveryTime));
  }

  /**
   * Checks if one order can be delivered after another without violating time constraints.
   */
  private boolean canFollow(Order from, Order to) {
    if (Duration.between(from.deliveryTime, to.deliveryTime).toSeconds() <= SECONDS_TO_HAND_DELIVER) {
      return false;
    }
    int deliverySeconds = secondsToMakeDelivery(from.destination, to.destination);
    Instant earliestArrival = from.cookedTime.plus(Duration.ofSeconds(deliverySeconds));
    return !earliestArrival.isAfter(to.deliveryTime);
  }

  /**
   * Computes the travel time (in seconds) between two locations, using a cache to avoid
   * repeated calls to the RouteService.
   * 
   * @param from origin address
   * @param to   destination address
   * @return travel time in seconds
   * @throws RuntimeException if the underlying Google API call fails
   */
  private int secondsToMakeDelivery(String from, String to) {
    String key = from + "}" + to;
    try {
      return travelTimeCache.get(key, () -> routeService.getSecondsBetween(from, to) + SECONDS_TO_HAND_DELIVER);
    } catch (ExecutionException e) {
      throw new RuntimeException("Google API call failed for route: " + from + " → " + to,
          e.getCause());
    }
  }
}
