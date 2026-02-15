package com.batchable.backend.service;

import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
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

  public RestaurantService() {}

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
   * Errors:
   *  - IllegalArgumentException if required fields are missing or invalid
   *  - IllegalStateException if restaurant already exists
   *  - RuntimeException if persistence fails
   */
  public void createRestaurant(Restaurant restaurant) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
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
   *  - IllegalArgumentException if restaurantId does not exist
   *  - IllegalArgumentException if updated fields are invalid
   *  - RuntimeException if persistence fails
   */
  public void updateRestaurant(long restaurantId, Restaurant restaurant) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Retrieves a restaurant by ID.
   *
   * Errors:
   *  - IllegalArgumentException if restaurantId does not exist
   */
  public Restaurant getRestaurant(long restaurantId) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
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
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
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
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
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
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
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
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
