package com.batchable.backend.controller;

// DTOs for request/response payloads
import com.batchable.backend.model.dto.*;
// Service layer that contains business logic
import com.batchable.backend.service.RestaurantService;

import org.springframework.web.bind.annotation.*; // Spring annotations for REST

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/restaurant")
// Base URL path for all endpoints in this controller
// Example: GET /routes/directions
public class RestaurantController {

  // Dependency on the service layer
  private final RestaurantService restaurantService;

  /**
   * Constructor injection: Spring automatically provides a RestaurantService instance because it is
   * annotated with @Service
   */
  public RestaurantController(RestaurantService restaurantService) {
    this.restaurantService = restaurantService;
  }
}
