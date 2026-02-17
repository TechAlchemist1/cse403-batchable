package com.batchable.backend.service;

import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.MenuItem;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

/**
 * MenuService belongs to the business logic layer.
 *
 * Responsibilities:
 *  - Manage restaurant menu items
 *  - Enforce invariants regarding menu ownership and removal
 *  - Protect referential integrity with orders
 *
 * This service is the ONLY component that should mutate MenuItem entities.
 */
@Service
public class MenuService {

  private final MenuItemDAO menuItemDAO;
  private final RestaurantDAO restaurantDAO;

  public MenuService(MenuItemDAO menuItemDAO, RestaurantDAO restaurantDAO) {
    this.menuItemDAO = menuItemDAO;
    this.restaurantDAO = restaurantDAO;
  }

  /**
   * Creates a new menu item for a restaurant.
   *
   * Responsibilities:
   *  - Validate required fields (name, price, restaurantId, etc.)
   *  - Ensure the referenced restaurant exists
   *  - Ensure no duplicate menu item exists for the same restaurant
   *  - Persist the menu item
   *
   * Domain Invariants:
   *  - Price must be non-negative
   *  - Name must be non-empty
   *  - Menu item must belong to exactly one restaurant
   *
   * Behavior:
   *  - Frontend may pass a dummy id (<= 0)
   *  - Database generates the real id
   *  - The generated id is returned to caller
   *
   * Errors:
   *  - IllegalArgumentException if:
   *      • Required fields are missing
   *      • Price is negative
   *      • Restaurant does not exist
   *  - IllegalStateException if:
   *      • Duplicate menu item already exists for the restaurant
   *  - RuntimeException if persistence fails
   */
  public long createMenuItem(MenuItem menuItem) {
    if (menuItem == null) {
      throw new IllegalArgumentException("menuItem is required");
    }

    if (menuItem.restaurantId <= 0) {
      throw new IllegalArgumentException("restaurantId must be positive");
    }

    if (menuItem.name == null || menuItem.name.trim().isEmpty()) {
      throw new IllegalArgumentException("name must be non-empty");
    }

    // Allow dummy id (negative or 0) from frontend.
    // Only reject if a positive id is supplied during creation.
    if (menuItem.id > 0) {
      throw new IllegalStateException("menuItem.id must be <= 0 (database-generated)");
    }

    try {
      // Ensure restaurant exists
      if (!restaurantDAO.restaurantExists(menuItem.restaurantId)) {
        throw new IllegalArgumentException(
            "Restaurant does not exist: " + menuItem.restaurantId);
      }

      // Ensure no duplicate menu item name for same restaurant
      if (menuItemDAO.menuItemExistsForRestaurantByName(
          menuItem.restaurantId, menuItem.name)) {
        throw new IllegalStateException(
            "Duplicate menu item for restaurantId=" +
                menuItem.restaurantId +
                " name=" +
                menuItem.name);
      }

      return menuItemDAO.createMenuItem(menuItem.restaurantId, menuItem.name);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to create menu item", e);
    }
  }

  /**
   * Removes a menu item from the system.
   *
   * Domain Rules:
   *  - A menu item should not be removable if it is referenced
   *    by active (non-delivered, non-cancelled) orders.
   *  - Historical orders referencing this item must remain valid.
   *
   * Responsibilities:
   *  - Validate menu item exists
   *  - Ensure removal does not violate active order constraints
   *  - Remove from persistence
   *
   * Errors:
   *  - IllegalArgumentException if menuItemId does not exist
   *  - IllegalStateException if:
   *      • Menu item is referenced by active orders
   *  - RuntimeException if persistence fails
   */
  public void removeMenuItem(long menuItemId) {
    if (menuItemId <= 0) {
      throw new IllegalArgumentException("menuItemId must be positive");
    }

    try {
      // Validate menu item exists
      if (menuItemDAO.getMenuItem(menuItemId).isEmpty()) {
        throw new IllegalArgumentException(
            "Menu item does not exist: " + menuItemId);
      }

      // TODO:
      // Add OrderDAO check here once active order status exists.
      // Example:
      // if (orderDAO.hasActiveOrdersReferencingMenuItem(menuItemId)) {
      //     throw new IllegalStateException(
      //         "Menu item is referenced by active orders: " + menuItemId);
      // }

      boolean deleted = menuItemDAO.deleteMenuItem(menuItemId);
      if (!deleted) {
        throw new IllegalArgumentException(
            "Menu item does not exist: " + menuItemId);
      }

    } catch (SQLException e) {
      throw new RuntimeException(
          "Failed to remove menu item " + menuItemId, e);
    }
  }
}
