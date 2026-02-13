package com.batchable.backend.controller;

// Service layer that contains business logic
import com.batchable.backend.service.OrderService;

import org.springframework.web.bind.annotation.*; // Spring annotations for REST

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/order")
// Base URL path for all endpoints in this controller
// Example: GET /routes/directions
public class OrderController {

  // Dependency on the service layer
  private final OrderService orderService;

  /**
   * Constructor injection: Spring automatically provides a OrderService instance because it is
   * annotated with @Service
   */
  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }


}
