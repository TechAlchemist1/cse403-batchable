package com.batchable.backend.service;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * DriverService belongs to the business logic layer.
 *
 * This service is the ONLY component that should modify Driver state.
 */
@Service
public class DriverService {

  private final DbOrderService dbOrderService;
  private final DriverDAO driverDAO;
  private final BatchDAO batchDAO;

  public DriverService(DriverDAO driverDAO, BatchDAO batchDAO, DbOrderService dbOrderService) {
    this.driverDAO = driverDAO;
    this.batchDAO = batchDAO;
    this.dbOrderService = dbOrderService;
  }

  /** Creates a new driver in the system. */
  public long createDriver(Driver driver) {
    if (driver == null) throw new IllegalArgumentException("driver is required");
    if (driver.restaurantId <= 0) throw new IllegalArgumentException("restaurantId must be positive");
    validateName(driver.name);
    validatePhone(driver.phoneNumber);

    // Allow frontend dummy id (negative or 0).
    // Only reject positive ids (those imply already persisted entity).
    if (driver.id > 0) throw new IllegalStateException("driver.id must be <= 0 (db-generated)");

    try {
      return driverDAO.createDriver(
          driver.restaurantId,
          driver.name,
          driver.phoneNumber,
          driver.onShift);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to create driver", e);
    }
  }

  /** Updates mutable driver details (NOT id, NOT restaurant_id). */
  public void updateDriver(Driver driver) {
    if (driver == null) throw new IllegalArgumentException("driver is required");
    if (driver.id <= 0) throw new IllegalArgumentException("driverId must be positive");
    validateName(driver.name);
    validatePhone(driver.phoneNumber);

    try {
      if (driverDAO.getDriver(driver.id).isEmpty()) {
        throw new IllegalArgumentException("Driver not found: " + driver.id);
      }

      boolean ok = driverDAO.updateDriver(driver.id, driver.name, driver.phoneNumber);
      if (!ok) throw new IllegalArgumentException("Driver not found: " + driver.id);
      updateDriverOnShift(driver.id, driver.onShift);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update driver " + driver.id, e);
    }
  }

  /** Sets whether a driver is currently on shift. */
  public void updateDriverOnShift(long driverId, boolean onShift) {
    if (driverId <= 0) throw new IllegalArgumentException("driverId must be positive");

    try {
      Driver existing =
          driverDAO
              .getDriver(driverId)
              .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

      // Invariant: cannot go OFF shift if they have a batch record (your DAO ties batches to drivers)
      // If you later add a "completed" flag, swap this to only block on active batches.
      if (!onShift && batchDAO.batchExistsForDriver(driverId)) {
        Optional<Batch> latest = batchDAO.getBatchForDriver(driverId);
        throw new IllegalStateException(
            "Cannot go off-shift while assigned to a batch (driverId="
                + driverId
                + ", batchId="
                + (latest.isPresent() ? latest.get().id : "unknown")
                + ")");
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
    if (driverId <= 0) throw new IllegalArgumentException("driverId must be positive");
    try {
      return driverDAO
          .getDriver(driverId)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get driver " + driverId, e);
    }
  }

  /** Retrieves driver by UUID token. */
  public Driver getDriverByToken(String token) {
    if (token == null || token.isEmpty()) throw new IllegalArgumentException("driver UUID must be non-null and non-empty");
    try {
      return driverDAO
          .getDriverByToken(token)
          .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + token));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to get driver " + token, e);
    }
  }

  /**
   * Handles the return of a driver to the restaurant after finishing their batch by
   * marking their batch as finished
   *
   * @param token the UUID of the driver
   * @throws RuntimeException if the driver's batch cannot be marked finished
   * 
   */
  public void handleReturn(String token) {
    Driver driver = getDriverByToken(token);
    if (getCurrentOrderToDeliver(driver.id) != null) {
      throw new IllegalArgumentException("Driver specified by the given token still has orders to deliver");
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
    Batch batch = getDriverBatch(driverId).orElseThrow(() -> new IllegalArgumentException(
            "Driver " + driverId + " does not have an assigned batch"));
    List<Order> batchOrders = dbOrderService.getBatchOrders(batch.id);

    // Use the invariant that they are in the order of delivery
    for (Order order : batchOrders) {
      if (order.state != State.DELIVERED) {
        return order;
      }
    }
    return null;
  }

  /** Returns whether the given driver (specified by id) is available to drive a batch */
  public boolean isAvailable(long driverId) {
    Driver driver = getDriver(driverId);
    return driver.onShift && getDriverBatch(driverId).isEmpty();
  }

  /** Removes a driver from the system. */
  public void removeDriver(long driverId) {
    if (driverId <= 0) throw new IllegalArgumentException("driverId must be positive");

    try {
      Driver driver =
          driverDAO
              .getDriver(driverId)
              .orElseThrow(() -> new IllegalArgumentException("Driver not found: " + driverId));

      if (driver.onShift) {
        throw new IllegalStateException("Driver must be off-shift before removal: " + driverId);
      }

      // Same note as above: without an "active/completed" concept, any batch record blocks removal.
      if (batchDAO.batchExistsForDriver(driverId)) {
        Optional<Batch> latest = batchDAO.getBatchForDriver(driverId);
        throw new IllegalStateException(
            "Cannot remove driver with existing batch (driverId="
                + driverId
                + ", batchId="
                + (latest.isPresent() ? latest.get().id : "unknown")
                + ")");
      }

      boolean deleted = driverDAO.deleteDriver(driverId);
      if (!deleted) throw new IllegalArgumentException("Driver not found: " + driverId);

      // Hook: batching recompute if needed.
      // batchingService.onDriverAvailabilityChanged(driver.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to remove driver " + driverId, e);
    }
  }

  /** Returns the batch currently assigned to the driver */
  public Optional<Batch> getDriverBatch(long driverId) {
    if (driverId <= 0) throw new IllegalArgumentException("driverId must be positive");

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
