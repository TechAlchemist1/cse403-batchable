package com.batchable.backend.controller;

// DTOs for request/response payloads
import com.batchable.backend.service.MenuService;
// Service layer that contains business logic

import org.springframework.web.bind.annotation.*; // Spring annotations for REST

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/menu")
// Base URL path for all endpoints in this controller
// Example: GET /routes/directions
public class MenuController {

  // Dependency on the service layer
  private final MenuService menuService;

  /**
   * Constructor injection: Spring automatically provides a menuService instance because it is
   * annotated with @Service
   */
  public MenuController(MenuService menuService) {
    this.menuService = menuService;
  }


}
