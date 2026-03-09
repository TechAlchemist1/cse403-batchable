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
 * Service responsible for coordinating order batching across all restaurants.
 *
 * Responsibilities: - Maintains one RestaurantBatchingManager per restaurant. - Routes order events
 * to the appropriate manager. - Periodically checks for expired tentative batches.
 *
 * This class does not implement batching logic itself; it delegates that responsibility to
 * RestaurantBatchingManager instances.
 */
@Service
public class BatchingManager {

  /** Handles sending SMS notifications related to batches. */
  private final TwilioManager twilioManager;

  /** Provides database access for orders. */
  private final DbOrderService dbOrderService;

  /** Publishes server-sent events to connected clients. */
  private final SsePublisher publisher;

  /** Algorithm implementation used for computing batches. */
  private final BatchingAlgorithm batchingAlgorithm;

  /** Service for retrieving restaurant information. */
  private final RestaurantService restaurantService;

  /** Service responsible for route calculations. */
  private final RouteService routeService;

  /** Service responsible for driver management. */
  private final DriverService driverService;

  /**
   * Thread-safe map of restaurant ID to its corresponding batching manager.
   */
  private final Map<Long, RestaurantBatchingManager> restaurantManagers = new ConcurrentHashMap<>();

  /** Interval (in milliseconds) between batch expiration checks. */
  public static final long UPDATE_INCREMENTS_MILLIS = 5000;

  /**
   * Constructs the batching manager service.
   *
   * @param publisher publisher used to send SSE updates
   * @param batchingAlgorithm algorithm used to compute optimal batches
   * @param restaurantService service used to retrieve restaurant data
   * @param routeService service used to compute routes for batches
   * @param dbOrderService service used to access order data
   * @param driverService service used to manage drivers
   * @param twilioManager manager used to send SMS notifications
   */
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

  /**
   * Initializes batching managers after the service is constructed.
   *
   * This method: - Clears unfinished batches from previous runs. - Creates a batching manager for
   * each restaurant currently in the system.
   */
  @PostConstruct
  private void initialize() {
    dbOrderService.removeAllUnfinishedBatches();

    List<Restaurant> restaurants = restaurantService.getAllRestaurants();
    for (Restaurant restaurant : restaurants) {
      addManager(restaurant.id);
    }
  }

  /**
   * Retrieves the batching manager for a given restaurant.
   *
   * @param restaurantId the ID of the restaurant
   * @return the RestaurantBatchingManager responsible for the restaurant
   * @throws IllegalArgumentException if the manager does not exist
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
   * Creates and registers a batching manager for a restaurant.
   *
   * Thread-safe: uses putIfAbsent to prevent duplicate managers.
   *
   * @param restaurantId the ID of the restaurant
   * @throws IllegalArgumentException if a manager for this restaurant already exists
   */
  public void addManager(long restaurantId) {
    Restaurant restaurant = restaurantService.getRestaurant(restaurantId);
    String address = restaurant.location;

    RestaurantBatchingManager newManager =
        new RestaurantBatchingManager(restaurantId, address, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, twilioManager, null);

    RestaurantBatchingManager existing = restaurantManagers.putIfAbsent(restaurantId, newManager);
    if (existing != null) {
      throw new IllegalArgumentException("Cannot add RestaurantBatchingManager for id "
          + restaurantId + " because it already exists.");
    }
  }

  /**
   * Updates the address associated with a restaurant manager.
   *
   * @param restaurant the restaurant with the updated address
   * @throws IllegalArgumentException if the manager does not exist
   */
  public void updateManagerAddress(Restaurant restaurant) {
    getManager(restaurant.id).setRestaurantAddress(restaurant.location);
  }

  /**
   * Removes the batching manager for a restaurant.
   *
   * @param restaurantId the ID of the restaurant
   * @throws IllegalArgumentException if the manager does not exist
   */
  public void removeManager(long restaurantId) {
    RestaurantBatchingManager removed = restaurantManagers.remove(restaurantId);
    if (removed == null) {
      throw new IllegalArgumentException("Cannot remove RestaurantBatchingManager for id "
          + restaurantId + " because it does not exist.");
    }

    removed.shutdown();
  }

  /**
   * Adds a new order to the batching system.
   *
   * The order is routed to the batching manager responsible for the corresponding restaurant.
   *
   * @param order the order to add
   * @throws IllegalArgumentException if the restaurant manager does not exist
   */
  public void addOrder(Order order) {
    getManager(order.restaurantId).addOrder(order);
  }

  /**
   * Removes an order from the batching system.
   *
   * @param order the order to remove
   * @throws IllegalArgumentException if the restaurant manager does not exist
   */
  public void removeOrder(Order order) {
    getManager(order.restaurantId).removeOrder(order);
  }

  /**
   * Updates an order and optionally triggers re-batching.
   *
   * @param orderId the ID of the order to update
   * @param rebatchIfTentative whether re-batching should occur if the batch containing the order is
   *        still tentative
   * @throws IllegalArgumentException if the restaurant manager does not exist
   */
  public void updateOrder(Long orderId, boolean rebatchIfTentative) {
    Order order = dbOrderService.getOrder(orderId);
    getManager(order.restaurantId).updateOrder(orderId, rebatchIfTentative);
  }

  /**
   * Periodic scheduled task that checks all restaurant managers for expired tentative batches.
   *
   * A snapshot of the managers is taken to avoid issues if managers are added or removed during
   * iteration.
   *
   * @return void
   */
  @Scheduled(fixedDelay = UPDATE_INCREMENTS_MILLIS)
  public void checkExpiredBatches() {
    List<RestaurantBatchingManager> managers = new ArrayList<>(restaurantManagers.values());
    for (RestaurantBatchingManager manager : managers) {
      manager.checkExpiredBatches(UPDATE_INCREMENTS_MILLIS);
    }
  }
}
