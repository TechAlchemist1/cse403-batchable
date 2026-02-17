package com.batchable.backend.service;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * DriverService belongs to the business logic layer.
 *
 * Responsibilities: - Manage driver lifecycle - Enforce invariants around shift status and batching
 * - Coordinate with batching logic when driver availability changes
 *
 * This service is the ONLY component that should modify Driver state.
 */
@Service
public class DriverService {

  public DriverService() {}

  /**
   * Creates a new driver in the system.
   *
   * Responsibilities: - Validate required fields (restaurant association, contact info, etc.) -
   * Ensure driver ID does not already exist - Initialize default state (e.g., onShift = false, no
   * active batch) - Persist to database
   *
   * Errors: - IllegalArgumentException if required fields are missing or invalid -
   * IllegalStateException if driver ID already exists - RuntimeException if persistence fails
   */
  public void createDriver(Driver driver) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Updates mutable driver details.
   *
   * Examples of mutable fields: - Name - Phone number - Vehicle information
   *
   * Invariants: - Cannot change driver ID - Cannot arbitrarily clear active batch assignment
   *
   * Responsibilities: - Validate driver exists - Validate updated fields are valid - Persist
   * changes
   *
   * Errors: - IllegalArgumentException if driver does not exist - IllegalArgumentException if
   * updated fields are invalid - RuntimeException if persistence fails
   */
  public void updateDriver(Driver driver) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Sets whether a driver is currently on shift.
   *
   * Domain Meaning: - onShift = true → Driver is eligible for batching - onShift = false → Driver
   * is unavailable for new batches
   *
   * Responsibilities: - Validate driver exists - Update shift status - If driver goes OFF shift: •
   * Ensure driver has no active batch • Or reassign / recompute affected batches - If driver goes
   * ON shift: • Potentially trigger batch recomputation
   *
   * Errors: - IllegalArgumentException if driverId does not exist - IllegalStateException if: •
   * Attempting to go off shift while actively delivering • Driver is in an inconsistent batch state
   * - RuntimeException if persistence fails
   */
  public void updateDriverOnShift(long driverId, boolean onShift) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Returns whether the given driver is currently available to accept a new batch.
   *
   * A driver is considered available if they are on shift and not already assigned to an active
   * batch.
   *
   * @param driverId the unique identifier of the driver
   * @return true if the driver is on shift and unassigned; false otherwise
   * @throws IllegalArgumentException if no driver exists with the given ID
   */
  public boolean isAvailable(long driverId) {
    Driver driver = getDriver(driverId);
    return driver.onShift && getDriverBatch(driverId) == null;
  }

  /**
   * Retrieves driver by ID.
   *
   * Errors: - IllegalArgumentException if driverId does not exist
   */
  public Driver getDriver(long driverId) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Removes a driver from the system.
   *
   * Domain Rules: - Driver must not currently be assigned to an active batch - Driver should be off
   * shift before removal
   *
   * Responsibilities: - Validate driver exists - Validate removal is allowed - Remove from
   * persistence - Potentially trigger batch recomputation if necessary
   *
   * Errors: - IllegalArgumentException if driverId does not exist - IllegalStateException if: •
   * Driver is on shift • Driver is assigned to an active batch - RuntimeException if persistence
   * fails
   */
  public void removeDriver(long driverId) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /**
   * Returns the active batch currently assigned to this driver.
   *
   * Behavior: - If the driver has an active batch, returns Optional containing that Batch. - If the
   * driver has no assigned batch, returns Optional.empty().
   *
   * Responsibilities: - Validate that the driver exists. - Retrieve current batch assignment
   * without mutating state.
   *
   * Errors: - IllegalArgumentException if driverId does not correspond to an existing driver.
   */
  public Optional<Batch> getDriverBatch(long driverId) {
    // TODO
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
