package com.batchable.backend.service;

import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * RestaurantService belongs to the business logic layer.
 *
 * Responsibilities:
 *  - Manage restaurant lifecycle
 *  - Enforce ownership relationships (drivers, menu items, orders)
 *  - Protect referential and domain integrity
 *
 * Restaurant is an aggregate root in the domain model.
 * All mutations affecting a restaurant’s core identity should go through this service.
 */
@Service
public class RestaurantService {

  private final RestaurantDAO restaurantDAO;
  private final OrderDAO orderDAO;
  private final DriverDAO driverDAO;
  private final MenuItemDAO menuItemDAO;

  public RestaurantService(
      RestaurantDAO restaurantDAO,
      OrderDAO orderDAO,
      DriverDAO driverDAO,
      MenuItemDAO menuItemDAO) {
    this.restaurantDAO = restaurantDAO;
    this.orderDAO = orderDAO;
    this.driverDAO = driverDAO;
    this.menuItemDAO = menuItemDAO;
  }

  /**
   * Creates a new restaurant in the system.
   *
   * Responsibilities:
   *  - Validate required fields (name, address, contact info, etc.)
   *  - Ensure restaurant does not already exist
   *  - Persist to database
   *
   * Domain Invariants:
   *  - Restaurant must have a unique identifier
   *  - Required identifying fields must be non-null
   *
   * Behavior:
   *  - Frontend may pass a dummy id (<= 0)
   *  - Database generates the real id
   *  - The generated id is returned to caller
   *
   * Errors:
   *  - IllegalArgumentException if required fields are missing or invalid
   *  - IllegalStateException if restaurant already exists
   *  - RuntimeException if persistence fails
   */
  public long createRestaurant(Restaurant restaurant) {
    if (restaurant == null)
      throw new IllegalArgumentException("restaurant is required");

    if (restaurant.name == null || restaurant.name.trim().isEmpty())
      throw new IllegalArgumentException("name is required");

    if (restaurant.location == null || restaurant.location.trim().isEmpty())
      throw new IllegalArgumentException("location is required");

    // Allow dummy id (negative or 0) from frontend.
    // Only reject if a positive id is supplied during creation.
    if (restaurant.id > 0)
      throw new IllegalStateException("restaurant.id must be <= 0 (database-generated)");
  
    try {
      if (restaurantDAO.restaurantExistsByName(restaurant.name))
        throw new IllegalStateException("Restaurant already exists: " + restaurant.name);

      long restaurantId = restaurantDAO.createRestaurant(restaurant.name, restaurant.location);
      return restaurantId;

    } catch (SQLException e) {
      throw new RuntimeException("Failed to create restaurant", e);
    }
  }

  /**
   * Updates mutable restaurant details.
   *
   * Examples of mutable fields:
   *  - Name
   *  - Location
   *
   * Invariants:
   *  - Cannot change restaurant ID
   *
   * Responsibilities:
   *  - Validate restaurant exists
   *  - Validate updated fields
   *  - Persist changes
   *
   * Errors:
   *  - IllegalArgumentException if updated fields are invalid
   *  - RuntimeException if persistence fails
   */
  public void updateRestaurant(Restaurant restaurant) {
    if (restaurant == null)
      throw new IllegalArgumentException("restaurant data required");

    long restaurantId = restaurant.id;

    if (restaurantId <= 0)
      throw new IllegalArgumentException("restaurantId must be positive");

    if (restaurant.name == null || restaurant.name.trim().isEmpty())
      throw new IllegalArgumentException("name is required");

    if (restaurant.location == null || restaurant.location.trim().isEmpty())
      throw new IllegalArgumentException("location is required");

    try {
      if (!restaurantDAO.restaurantExists(restaurantId))
        throw new IllegalArgumentException("Restaurant not found: " + restaurantId);

      if (restaurantDAO.restaurantExistsByNameExcludingId(
          restaurantId, restaurant.name))
        throw new IllegalStateException("Another restaurant already uses that name");

      boolean ok = restaurantDAO.updateRestaurant(restaurantId, restaurant.name, restaurant.location);
      if (!ok)
        throw new IllegalArgumentException("Restaurant not found: " + restaurantId);
      
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update restaurant", e);
    }
  }

  /**
   * Retrieves a restaurant by ID.
   *
   * Errors:
   *  - IllegalArgumentException if restaurantId does not exist
   */
  public Restaurant getRestaurant(long restaurantId) {
    if (restaurantId <= 0)
      throw new IllegalArgumentException("restaurantId must be positive");

    try {
      return restaurantDAO.getRestaurant(restaurantId)
          .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve restaurant", e);
    }
  }

  /** Returns a list of all restaurants in the database */
  public List<Restaurant> getAllRestaurants() {
    try {
      return restaurantDAO.listRestaurants();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve all restaurants", e);
    }
  }

  /**
   * Removes a restaurant from the system.
   *
   * Domain Rules:
   *  - Restaurant must not have active (non-delivered) orders
   *  - Restaurant must not have drivers currently on shift
   *  - Removal may require cascading deletion or archival of:
   *      • Drivers
   *      • Menu items
   *      • Historical orders
   *
   * Responsibilities:
   *  - Validate restaurant exists
   *  - Ensure removal does not violate domain invariants
   *  - Perform safe deletion or archival
   *
   * Errors:
   *  - IllegalArgumentException if restaurantId does not exist
   *  - IllegalStateException if:
   *      • Active orders exist
   *      • Drivers are currently on shift
   *  - RuntimeException if persistence fails
   */
  public void removeRestaurant(long restaurantId) {
    if (restaurantId <= 0)
      throw new IllegalArgumentException("restaurantId must be positive");

    try {
      if (!restaurantDAO.restaurantExists(restaurantId))
        throw new IllegalArgumentException("Restaurant not found: " + restaurantId);

      if (orderDAO.hasActiveOrdersForRestaurant(restaurantId))
        throw new IllegalStateException("Restaurant has active orders");

      if (driverDAO.hasOnShiftDrivers(restaurantId))
        throw new IllegalStateException("Restaurant has drivers currently on shift");

      boolean ok = restaurantDAO.deleteRestaurant(restaurantId);
      if (!ok)
        throw new IllegalArgumentException("Restaurant not found: " + restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to remove restaurant", e);
    }
  }

  /**
   * Returns all active orders for a given restaurant.
   *
   * Responsibilities:
   *  - Validate restaurant exists (optional depending on architecture)
   *
   * Errors:
   *  - IllegalArgumentException if restaurantId does not exist
   */
  public List<Order> getRestaurantOrders(long restaurantId) {
    getRestaurant(restaurantId); // validate exists

    try {
      return orderDAO.listOpenOrdersForRestaurant(restaurantId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to list restaurant orders", e);
    }
  }

  /**
   * Returns all drivers associated with the given restaurant.
   *
   * Behavior:
   *  - Includes both on-shift and off-shift drivers
   *
   * Responsibilities:
   *  - Validate restaurant exists
   *  - Retrieve associated drivers
   *
   * Errors:
   *  - IllegalArgumentException if restaurantId does not exist
   */
  public List<Driver> getRestaurantDrivers(long restaurantId) {
    getRestaurant(restaurantId);

    try {
      return driverDAO.listDriversForRestaurant(restaurantId, false);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to list restaurant drivers", e);
    }
  }

  /**
   * Returns all menu items associated with the given restaurant.
   *
   * Responsibilities:
   *  - Validate restaurant exists
   *  - Retrieve associated menu items
   *
   * Errors:
   *  - IllegalArgumentException if restaurantId does not exist
   */
  public List<MenuItem> getRestaurantMenuItems(long restaurantId) {
    getRestaurant(restaurantId);

    try {
      return menuItemDAO.listMenuItems(restaurantId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to list restaurant menu items", e);
    }
  }
}