package com.batchable.backend.service;

import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.model.dto.*;
import org.springframework.stereotype.Service;

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

  public MenuService() {}

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
   * Errors:
   *  - IllegalArgumentException if:
   *      • Required fields are missing
   *      • Price is negative
   *      • Restaurant does not exist
   *  - IllegalStateException if:
   *      • Duplicate menu item already exists for the restaurant
   *  - RuntimeException if persistence fails
   */
  public void createMenuItem(MenuItem menuItem) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
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
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
