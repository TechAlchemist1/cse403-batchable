package com.batchable.backend.controller;

import com.batchable.backend.db.models.MenuItem;
// DTOs for request/response payloads
import com.batchable.backend.service.MenuService;
// Service layer that contains business logic
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*; // Spring annotations for REST

@RestController
// Marks this class as a REST controller in Spring
// All methods return JSON by default
@RequestMapping("/menu")
// Base URL path for all endpoints in this controller
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

  /**
   * Create a new menu item.
   *
   * POST /menu Body: JSON representing a MenuItem object
   *
   * @param menuItem MenuItem object from request body
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public void createMenuItem(@RequestBody MenuItem menuItem) {
    menuService.createMenuItem(menuItem);
  }


  /**
   * Removes a menu item by ID.
   *
   * @param menuItemId the ID of the MenuItem to delete
   */
  @DeleteMapping("/{menuItemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMenuItem(@PathVariable long menuItemId) {
    menuService.removeMenuItem(menuItemId);
  }
}
