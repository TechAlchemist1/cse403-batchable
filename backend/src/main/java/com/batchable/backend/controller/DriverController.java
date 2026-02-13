package com.batchable.backend.controller;

// Service layer that contains business logic
import com.batchable.backend.service.DriverService;

import org.springframework.web.bind.annotation.*; // Spring annotations for REST

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/driver")
// Base URL path for all endpoints in this controller
// Example: GET /routes/directions
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
}
