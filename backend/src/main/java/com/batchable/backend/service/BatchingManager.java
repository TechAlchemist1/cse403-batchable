package com.batchable.backend.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.internal.RestaurantBatchingManager;
import com.batchable.backend.service.internal.RestaurantBatchingManager.Batches;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

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

  private final OrderService orderService;

  // Publishes updates to clients when batches change
  private final OrderWebSocketPublisher publisher;

  // Algorithm used to form and update tentative batches
  private final BatchingAlgorithm batchingAlgorithm;

  // Service for retrieving restaurant information
  private final RestaurantService restaurantService;

  // Service for computing routes and durations
  private final RouteService routeService;

  // Service for retrieving driver information
  private final DriverService driverService;

  // Map of restaurant ID to its batching manager
  private final Map<Long, RestaurantBatchingManager> restaurantManagers = new HashMap<>();

  public static final long UPDATE_INCREMENTS_MILLIS = 5000;

  /**
   * Constructs the batching manager.
   *
   * @param publisher websocket publisher
   * @param batchingAlgorithm algorithm for batching orders
   * @param restaurantService service for restaurant data
   * @param routeService service for route calculations
   * @param orderService service for order and batch operations
   */
  public BatchingManager(OrderWebSocketPublisher publisher, BatchingAlgorithm batchingAlgorithm,
      RestaurantService restaurantService, RouteService routeService, OrderService orderService,
      DriverService driverService) {
    this.publisher = publisher;
    this.batchingAlgorithm = batchingAlgorithm;
    this.restaurantService = restaurantService;
    this.routeService = routeService;
    this.orderService = orderService;
    this.driverService = driverService;
  }

  /**
   * Retrieves the batching manager for a restaurant, throwing an exception if it does not exist.
   *
   * @param restaurantId the restaurant's ID
   * @return the batching manager for the restaurant
   * @throws IllegalArgumentException if no batching manager corresponds to the ID
   */
  private RestaurantBatchingManager getManager(long restaurantId) {
    if (!restaurantManagers.containsKey(restaurantId)) {
      throw new IllegalArgumentException("Cannot get RestaurantBatchingManager for id "
          + restaurantId + "because it does not exist.");
    }
    return restaurantManagers.get(restaurantId);
  }

  /**
   * Adds a new restaurant manager for the given restaurant ID. Creates a RestaurantBatchingManager
   * instance using the restaurant's address and stores it in the internal map.
   *
   * @param restaurantId the ID of the restaurant to add
   * @throws IllegalArgumentException if a manager for that restaurant already exists
   */
  public void addManager(long restaurantId) {
    if (restaurantManagers.containsKey(restaurantId)) {
      throw new IllegalArgumentException("Cannot add RestaurantBatchingManager for id "
          + restaurantId + "because it already exists.");
    }
    Restaurant restaurant = restaurantService.getRestaurant(restaurantId);
    String address = restaurant.location;
    restaurantManagers.put(restaurantId,
        new RestaurantBatchingManager(restaurantId, address, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, null));
  }

  /**
   * Updates the address of an existing restaurant manager. The change is propagated to the
   * associated RestaurantBatchingManager.
   *
   * @param restaurantId the ID of the restaurant whose address changed
   * @param restaurantAddress the new address
   * @throws IllegalArgumentException if no manager exists for the given restaurant ID
   */
  public void updateManagerAddress(long restaurantId, String restaurantAddress) {
    if (!restaurantManagers.containsKey(restaurantId)) {
      throw new IllegalArgumentException("Cannot update RestaurantBatchingManager for id "
          + restaurantId + "because it does not exist.");
    }
    restaurantManagers.get(restaurantId).setRestaurantAddress(restaurantAddress);

  }

  /**
   * Removes the restaurant manager for the given restaurant ID.
   *
   * @param restaurantId the ID of the restaurant to remove
   * @throws IllegalArgumentException if no manager exists for the given restaurant ID
   */
  public void removeManager(long restaurantId) {
    if (!restaurantManagers.containsKey(restaurantId)) {
      throw new IllegalArgumentException("Cannot remove RestaurantBatchingManager for id "
          + restaurantId + "because it does not exist.");
    }
    restaurantManagers.remove(restaurantId);
  }

  /**
   * Registers a listener for when batches change for a specific restaurant.
   *
   * @param restaurantId the restaurant ID
   * @param handler callback invoked with the restaurant's batch state
   */
  public void onBatchesChange(long restaurantId, Consumer<Long> handler) {
    getManager(restaurantId).onBatchChange(handler);
  }

  /**
   * Registers a listener for when a batch becomes active for a restaurant.
   *
   * @param restaurantId the restaurant ID
   * @param handler callback invoked with the newly active batch
   */
  public void onBatchBecomeActive(long restaurantId, Consumer<Long> handler) {
    getManager(restaurantId).onBatchBecomeActive(handler);
  }

  /**
   * Adds an order to the appropriate restaurant batching manager.
   *
   * @param order the order to add
   */
  public void addOrder(Order order) {
    long restaurantId = order.restaurantId;
    getManager(restaurantId).addOrder(order);
  }

  /**
   * Removes an order from the appropriate restaurant batching manager by ID.
   *
   * @param orderId the order to add
   * @throws IllegalArgumentException if the order id is not found
   */
  public void removeOrder(Long orderId) {
    Order order = orderService.getOrder(orderId);
    long restaurantId = order.restaurantId;
    getManager(restaurantId).removeOrder(orderId);
  }
  
  /**
   * Updates an existing order and forwards the update to the batching manager for the order's
   * restaurant.
   *
   * If rebatchIfTentative is true and the order is currently part of a tentative batch, the order
   * will be removed and re-added so that batching constraints are re-evaluated. Otherwise, the
   * order is updated without rebatching.
   *
   * @param orderId the ID of the order to update
   * @param rebatchIfTentative whether to rebatch the order if it is currently part of a tentative
   *        batch
   */
  public void updateOrder(Long orderId, boolean rebatchIfTentative) {
    Order order = orderService.getOrder(orderId);
    long restaurantId = order.restaurantId;
    getManager(restaurantId).updateOrder(orderId, rebatchIfTentative);
  }

  /**
   * Scheduled task that checks for expired tentative batches in all restaurants. Delegates
   * expiration handling to the restaurant-specific batching managers.
   */
  @Scheduled(fixedDelay = UPDATE_INCREMENTS_MILLIS)
  public void checkExpiredBatches() {
    for (RestaurantBatchingManager manager : restaurantManagers.values()) {
      manager.checkExpiredBatches(UPDATE_INCREMENTS_MILLIS);
    }
  }
}
