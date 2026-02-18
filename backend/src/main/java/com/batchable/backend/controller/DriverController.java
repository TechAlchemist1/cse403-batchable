package com.batchable.backend.controller;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
// Service layer that contains business logic
import com.batchable.backend.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/driver")
// Base URL path for all endpoints in this controller
public class DriverController {

  // Dependency on the service layer
  private final DriverService driverService;

  /**
   * Constructor injection: Spring automatically provides a DriverService instance because it is
   * annotated with @Service
   */
  public DriverController(DriverService driverService) {
    this.driverService = driverService;
  }

  /**
   * Create a new driver.
   *
   * POST /driver Body: JSON representing a Driver object
   *
   * @param driver Driver object from request body
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED) // 201 if successful
  public long createDriver(@RequestBody Driver driver) {
   return driverService.createDriver(driver);
  }

  /**
   * Update an existing driver.
   *
   * PUT /driver Body: JSON representing a Driver object
   *
   * @param driver Driver object from request body
   */
  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateDriver(@RequestBody Driver driver) {
    driverService.updateDriver(driver);
  }

  /**
   * Update an existing driver's onShift status.
   *
   * @param driverId the ID of the driver to update
   * @param onShift the new onShift status
   */
  @PutMapping("/{driverId}/shift")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateDriverOnShift(@PathVariable long driverId, @RequestParam boolean onShift) {
    driverService.updateDriverOnShift(driverId, onShift);
  }

  /**
   * Gets a driver by ID.
   *
   * @param driverId the ID of the driver to get
   * @return the Driver object
   */
  @GetMapping("/{driverId}")
  @ResponseStatus(HttpStatus.OK)
  public Driver getDriver(@PathVariable long driverId) {
    return driverService.getDriver(driverId);
  }

  /**
   * Removes a driver by ID.
   *
   * @param driverId the ID of the driver to delete
   */
  @DeleteMapping("/{driverId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeDriver(@PathVariable long driverId) {
    driverService.removeDriver(driverId);
  }

  /**
   * Gets the batch corresponding to a driver by the driver's ID, if applicable.
   *
   * @param driverId the ID of the driver
   * @return the driver's current Batch, if assigned. Otherwise, null.
   */
  @GetMapping("/{driverId}/batch")
  @ResponseStatus(HttpStatus.OK)
  public Batch getDriverBatch(@PathVariable long driverId) {
    return driverService.getDriverBatch(driverId).orElse(null);
  }
}