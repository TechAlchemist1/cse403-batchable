package com.batchable.backend.service;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.db.models.Restaurant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * DriverService belongs to the business logic layer.
 *
 * This service is the ONLY component that should modify Driver state.
 */
@Service
public class DriverService {

  private final RestaurantService restaurantService;
  private final DbOrderService dbOrderService;
  private final DriverDAO driverDAO;
  private final BatchDAO batchDAO;

  public DriverService(DriverDAO driverDAO, BatchDAO batchDAO, DbOrderService dbOrderService,
      RestaurantService restaurantService) {
    this.driverDAO = driverDAO;
    this.batchDAO = batchDAO;
    this.dbOrderService = dbOrderService;
    this.restaurantService = restaurantService;
  }

  /** Creates a new driver in the system. */
  public long createDriver(Driver driver) {
    if (driver == null)
      throw new IllegalArgumentException("driver is required");
    if (driver.restaurantId <= 0)
      throw new IllegalArgumentException("restaurantId must be positive");
    validateName(driver.name);
    validatePhone(driver.phoneNumber);

    // Allow frontend dummy id (negative or 0).
    // Only reject positive ids (those imply already persisted entity).
    if (driver.id > 0)
      throw new IllegalStateException("driver.id must be <= 0 (db-generated)");

    try {
      return driverDAO.createDriver(driver.restaurantId, driver.name, driver.phoneNumber,
          driver.onShift);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to create driver", e);
    }
  }

  /** Updates mutable driver details (NOT id, NOT restaurant_id). */
  public void updateDriver(Driver driver) {
    if (driver == null)
      throw new IllegalArgumentException("driver is required");
    if (driver.id <= 0)
      throw new IllegalArgumentException("driverId must be positive");
    validateName(driver.name);
    validatePhone(driver.phoneNumber);

    try {
      if (driverDAO.getDriver(driver.id).isEmpty()) {
        throw new IllegalArgumentException("Driver not found: " + driver.id);
      }

      boolean ok = driverDAO.updateDriver(driver.id, driver.name, driver.phoneNumber);
      if (!ok)
        throw new IllegalArgumentException("Driver not found: " + driver.id);
      updateDriverOnShift(driver.id, driver.onShift);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update driver " + driver.id, e);
    }
  }

  /** Sets whether a driver is currently on shift. */
  public void updateDriverOnShift(long driverId, boolean onShift) {
    if (driverId <= 0)
      throw new IllegalArgumentException("driverId must be positive");

    try {
      Driver existing = driverDAO.getDriver(driverId)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

      // Invariant: cannot go OFF shift if they have a batch record (your DAO ties batches to
      // drivers)
      // If you later add a "completed" flag, swap this to only block on active batches.
      if (!onShift && batchDAO.batchExistsForDriver(driverId)) {
        Optional<Batch> latest = batchDAO.getBatchForDriver(driverId);
        throw new IllegalStateException("Cannot go off-shift while assigned to a batch (driverId="
            + driverId + ", batchId=" + (latest.isPresent() ? latest.get().id : "unknown") + ")");
      }

      // No-op allowed
      if (existing.onShift == onShift) {
        return;
      }

      driverDAO.setDriverShift(driverId, onShift);

      // Hook: if you have a batching recompute, trigger it here.
      // batchingService.onDriverAvailabilityChanged(existing.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update shift status for driver " + driverId, e);
    }
  }

  /** Retrieves driver by ID. */
  public Driver getDriver(long driverId) {
    if (driverId <= 0)
      throw new IllegalArgumentException("driverId must be positive");
    try {
      return driverDAO.getDriver(driverId)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get driver " + driverId, e);
    }
  }

  /** Retrieves driver by UUID token. */
  public Driver getDriverByToken(String token) {
    if (token == null || token.isEmpty())
      throw new IllegalArgumentException("driver UUID must be non-null and non-empty");
    try {
      return driverDAO.getDriverByToken(token)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + token));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get driver " + token, e);
    }
  }

  /** Retrieves driver's UUID token. */
  public String getDriverToken(Long driverId) {
    if (driverId <= 0)
      throw new IllegalArgumentException("driver id must be positive");
    try {
      return driverDAO.getDriverToken(driverId)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get driver " + driverId, e);
    }
  }

  /**
   * Gets the data for the webpage for the driver corresponding to the given token.
   *
   * @param token the UUID of the driver
   * @return Map containing the driver, the batch orders, and the batch route link
   */
  public Map<String, Object> getDriverPageData(String token) {
    Driver driver = getDriverByToken(token);
    Optional<List<Order>> orders = getDriverBatchOrders(driver.id);
    Optional<String> routeLink = Optional.empty();
    if (orders.isPresent()) {
      Restaurant restaurant = restaurantService.getRestaurant(driver.restaurantId);
      List<Order> unboxedOrders = orders.orElseThrow();
      routeLink = Optional.of(getRouteLink(unboxedOrders, restaurant.location));
    }
    Map<String, Object> map = new ConcurrentHashMap<>();
    map.put("driver", driver);
    map.put("orders", orders);
    map.put("mapLink", routeLink);
    return map;
  }

  /**
   * Constructs a Google Maps directions link for the remaining (i.e., undelivered) orders in the given list. 
   * Starts at the driver's current location and visits each remaining
   * order destination in list order, then returns to the restaurant
   *
   * @param orders the orders corresponding to stops in the route
   * @param restaurantAddress address of the restaurant these orders are for
   * @return a Google Maps directions URL
   */
  public String getRouteLink(List<Order> orders, String restaurantAddress) {
    orders = orders.stream()
      .filter(order -> order.state != State.DELIVERED)
      .toList();

    StringBuilder linkBuilder = new StringBuilder("https://www.google.com/maps/dir/?api=1");
    linkBuilder.append("&origin=Current+Location");
    linkBuilder.append("&destination=").append(urlEncodeAddress(restaurantAddress));
    if (!orders.isEmpty()) {
      linkBuilder.append("&waypoints=").append(urlEncodeAddress(orders.get(0).destination));
    }
    for (int i = 1; i < orders.size(); i++) {
      linkBuilder.append("|").append(urlEncodeAddress(orders.get(i).destination));
    }
    return linkBuilder.toString();
  }

  /**
   * URL-encodes an address string using UTF-8 so it is safe to include as a query parameter in a
   * Google Maps URL.
   */
  private String urlEncodeAddress(String address) {
    return URLEncoder.encode(address, StandardCharsets.UTF_8);
  }

  /**
   * Handles the return of a driver to the restaurant after finishing their batch by marking their
   * batch as finished
   *
   * @param token the UUID of the driver
   * @throws RuntimeException if the driver's batch cannot be marked finished
   * 
   */
  public void handleReturn(String token) {
    Driver driver = getDriverByToken(token);
    if (getCurrentOrderToDeliver(driver.id) != null) {
      throw new IllegalArgumentException(
          "Driver specified by the given token still has orders to deliver");
    }
    
    Batch batch = getDriverBatch(driver.id).orElseThrow(() -> new IllegalArgumentException(
        "Driver " + driver.id + " does not have an assigned batch"));
    try {
      batchDAO.markBatchFinished(batch.id);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to mark batch finished " + batch.id, e);
    }
  }

  /**
   * Returns the next order in the given driver's batch that still needs to be delivered.
   *
   * Assumes that getBatchOrders(batch.id) returns orders in delivery order. The method scans the
   * batch and returns the first order whose state is not DELIVERED. If all orders have already been
   * delivered, the method returns null.
   *
   * @param driverId the id of the driver we are finding the next order to deliver for
   * @return the next order that should be delivered, or null if the batch is fully delivered
   * @throws IllegalArgumentException if the driver does not have a currently assigned batch
   */
  public Order getCurrentOrderToDeliver(long driverId) {
    Optional<List<Order>> optionalBatchOrders = getDriverBatchOrders(driverId);
    List<Order> batchOrders = optionalBatchOrders.orElseThrow(() -> new IllegalArgumentException(
        "Driver " + driverId + " does not have an assigned batch"));

    // Use the invariant that they are in the order of delivery
    for (Order order : batchOrders) {
      if (order.state != State.DELIVERED) {
        return order;
      }
    }
    return null;
  }

  /**
   * Returns the orders in the driver's batch in delivery order.
   *
   * Assumes that getBatchOrders(batch.id) returns orders in delivery order.
   *
   * @param driverId the id of the driver
   * @return Optional list of the driver's batch orders, or Optional.empty() if the driver does not
   *         have a batch
   */
  public Optional<List<Order>> getDriverBatchOrders(long driverId) {
    Optional<Batch> optionalDriverBatch = getDriverBatch(driverId);
    if (optionalDriverBatch.isEmpty()) {
      return Optional.empty();
    }
    Batch driverBatch = optionalDriverBatch.orElseThrow();
    List<Order> batchOrders = dbOrderService.getBatchOrders(driverBatch.id);
    return Optional.of(batchOrders);
  }

  /** Returns whether the given driver (specified by id) is available to drive a batch */
  public boolean isAvailable(long driverId) {
    Driver driver = getDriver(driverId);
    return driver.onShift && getDriverBatch(driverId).isEmpty();
  }

  /** Removes a driver from the system. */
  public void removeDriver(long driverId) {
    if (driverId <= 0)
      throw new IllegalArgumentException("driverId must be positive");

    try {
      Driver driver = driverDAO.getDriver(driverId)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

      if (driver.onShift) {
        throw new IllegalStateException("Driver must be off-shift before removal: " + driverId);
      }

      // Same note as above: without an "active/completed" concept, any batch record blocks removal.
      if (batchDAO.batchExistsForDriver(driverId)) {
        Optional<Batch> latest = batchDAO.getBatchForDriver(driverId);
        throw new IllegalStateException("Cannot remove driver with existing batch (driverId="
            + driverId + ", batchId=" + (latest.isPresent() ? latest.get().id : "unknown") + ")");
      }

      boolean deleted = driverDAO.deleteDriver(driverId);
      if (!deleted)
        throw new IllegalArgumentException("Driver not found: " + driverId);

      // Hook: batching recompute if needed.
      // batchingService.onDriverAvailabilityChanged(driver.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to remove driver " + driverId, e);
    }
  }

  /** Returns the batch currently assigned to the driver */
  public Optional<Batch> getDriverBatch(long driverId) {
    if (driverId <= 0)
      throw new IllegalArgumentException("driverId must be positive");

    try {
      // Validate driver exists (per contract)
      if (driverDAO.getDriver(driverId).isEmpty()) {
        throw new IllegalArgumentException("Driver not found: " + driverId);
      }

      return batchDAO.getBatchForDriver(driverId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get batch for driver " + driverId, e);
    }
  }

  // ---- helpers ----

  private static void validateName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name is required");
    }
  }

  private static void validatePhone(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
      throw new IllegalArgumentException("phoneNumber is required");
    }
    // permissive: digits + common separators
    String p = phoneNumber.trim();
    if (!p.matches("[0-9+()\\-\\.\\s]{7,20}")) {
      throw new IllegalArgumentException("phoneNumber looks invalid");
    }
  }
}
