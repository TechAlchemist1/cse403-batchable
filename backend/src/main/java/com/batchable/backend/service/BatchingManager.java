package com.batchable.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.batchable.backend.EventSource.SsePublisher;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.internal.RestaurantBatchingManager;
import com.batchable.backend.twilio.TwilioManager;
import jakarta.annotation.PostConstruct;

/**
 * Service that coordinates order batching for all restaurants.
 *
 * Responsibilities: - Maintains a batching manager per restaurant. - Routes incoming orders to the
 * appropriate restaurant manager. - Exposes listener registration APIs for batch updates and
 * activation. - Periodically checks all restaurants for expired tentative batches.
 *
 * The actual batching logic is handled by RestaurantBatchingManager instances.
 */
@Service
public class BatchingManager {

  private final TwilioManager twilioManager;
  private final DbOrderService dbOrderService;
  private final SsePublisher publisher;
  private final BatchingAlgorithm batchingAlgorithm;
  private final RestaurantService restaurantService;
  private final RouteService routeService;
  private final DriverService driverService;

  // Thread-safe map of restaurant ID to its batching manager
  private final Map<Long, RestaurantBatchingManager> restaurantManagers = new ConcurrentHashMap<>();

  public static final long UPDATE_INCREMENTS_MILLIS = 5000;

  public BatchingManager(SsePublisher publisher, BatchingAlgorithm batchingAlgorithm,
      RestaurantService restaurantService, RouteService routeService, DbOrderService dbOrderService,
      DriverService driverService, TwilioManager twilioManager) {
    this.publisher = publisher;
    this.batchingAlgorithm = batchingAlgorithm;
    this.restaurantService = restaurantService;
    this.routeService = routeService;
    this.dbOrderService = dbOrderService;
    this.driverService = driverService;
    this.twilioManager = twilioManager;
  }

  @PostConstruct
  private void initialize() {
    dbOrderService.removeAllUnfinishedBatches();
    List<Restaurant> restaurants = restaurantService.getAllRestaurants();
    for (Restaurant restaurant : restaurants) {
      addManager(restaurant.id);
    }
  }

  /**
   * Retrieves the batching manager for a restaurant, throwing an exception if it does not exist.
   * Thread-safe: atomic get from ConcurrentHashMap.
   */
  private RestaurantBatchingManager getManager(long restaurantId) {
    RestaurantBatchingManager manager = restaurantManagers.get(restaurantId);
    if (manager == null) {
      throw new IllegalArgumentException("Cannot get RestaurantBatchingManager for id "
          + restaurantId + " because it does not exist.");
    }
    return manager;
  }

  /**
   * Adds a new restaurant manager for the given restaurant ID. Thread-safe: uses putIfAbsent.
   */
  public void addManager(long restaurantId) {
    Restaurant restaurant = restaurantService.getRestaurant(restaurantId);
    String address = restaurant.location;
    RestaurantBatchingManager newManager = new RestaurantBatchingManager(restaurantId, address,
        publisher, batchingAlgorithm, routeService, dbOrderService, driverService,
        restaurantService, twilioManager, null);
    RestaurantBatchingManager existing = restaurantManagers.putIfAbsent(restaurantId, newManager);
    if (existing != null) {
      throw new IllegalArgumentException("Cannot add RestaurantBatchingManager for id "
          + restaurantId + " because it already exists.");
    }
  }

  /**
   * Updates the address of an existing restaurant manager.
   */
  public void updateManagerAddress(Restaurant restaurant) {
    getManager(restaurant.id).setRestaurantAddress(restaurant.location);
  }

  /**
   * Removes the restaurant manager for the given restaurant ID. Thread-safe: atomic remove.
   */
  public void removeManager(long restaurantId) {
    RestaurantBatchingManager removed = restaurantManagers.remove(restaurantId);
    if (removed == null) {
      throw new IllegalArgumentException("Cannot remove RestaurantBatchingManager for id "
          + restaurantId + " because it does not exist.");
    }
    removed.shutdown();
  }

  public void addOrder(Order order) {
    getManager(order.restaurantId).addOrder(order);
  }

  public void removeOrder(Order order) {
    getManager(order.restaurantId).removeOrder(order);
  }

  public void updateOrder(Long orderId, boolean rebatchIfTentative) {
    Order order = dbOrderService.getOrder(orderId);
    getManager(order.restaurantId).updateOrder(orderId, rebatchIfTentative);
  }

  /**
   * Scheduled task that checks for expired tentative batches in all restaurants.
   * Iterates over a snapshot of managers to avoid calling on removed ones.
   */
  @Scheduled(fixedDelay = UPDATE_INCREMENTS_MILLIS)
  public void checkExpiredBatches() {
    // Take a snapshot to avoid ConcurrentModification and ensure we don't
    // call checkExpiredBatches on a manager that was removed during iteration.
    List<RestaurantBatchingManager> managers = new ArrayList<>(restaurantManagers.values());
    for (RestaurantBatchingManager manager : managers) {
      manager.checkExpiredBatches(UPDATE_INCREMENTS_MILLIS);
    }
  }
}