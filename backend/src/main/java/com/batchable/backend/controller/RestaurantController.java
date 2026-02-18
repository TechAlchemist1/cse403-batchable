package com.batchable.backend.controller;

import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.RestaurantService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/restaurant")
public class RestaurantController {

  private final RestaurantService restaurantService;
  private final BatchingManager batchingManager;

  /**
   * Constructor injection: Spring automatically provides a RestaurantService instance because it is
   * annotated with @Service
   */
  public RestaurantController(RestaurantService restaurantService, BatchingManager batchingManager) {
    this.restaurantService = restaurantService;
    this.batchingManager = batchingManager;
  }

  /**
   * Create a new restaurant.
   *
   * POST /restaurant
   *
   * @param restaurant the Restaurant object to create
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public long createRestaurant(@RequestBody Restaurant restaurant) {
    long id = restaurantService.createRestaurant(restaurant);
    batchingManager.addManager(restaurant.id);
    return id;
  }

  /**
   * Update mutable fields of an existing restaurant.
   *
   * PUT /restaurant/{restaurantId}
   *
   * @param restaurantId the ID of the restaurant to update
   * @param restaurant Restaurant object containing updated fields
   */
  @PutMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateRestaurant(@PathVariable long restaurantId, @RequestBody Restaurant restaurant) {
    restaurantService.updateRestaurant(restaurantId, restaurant);
    batchingManager.updateManagerAddress(restaurantId, restaurantService.getRestaurant(restaurantId).location);
  }

  /**
   * Get a restaurant by ID.
   *
   * GET /restaurant/{restaurantId}
   *
   * @param restaurantId the ID of the restaurant
   * @return the Restaurant object
   */
  @GetMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.OK)
  public Restaurant getRestaurant(@PathVariable long restaurantId) {
    return restaurantService.getRestaurant(restaurantId);
  }

  /**
   * Delete a restaurant by ID.
   *
   * DELETE /restaurant/{restaurantId}
   *
   * @param restaurantId the ID of the restaurant to delete
   */
  @DeleteMapping("/{restaurantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeRestaurant(@PathVariable long restaurantId) {
    restaurantService.removeRestaurant(restaurantId);
    batchingManager.removeManager(restaurantId);
  }

  /**
   * Get all active orders for a restaurant.
   *
   * GET restaurant/{restaurantId}/orders
   *
   * @param restaurantId the restaurant ID
   * @return list of Orders
   */
  @GetMapping("/{restaurantId}/orders")
  @ResponseStatus(HttpStatus.OK)
  public List<Order> getRestaurantOrders(@PathVariable long restaurantId) {
    return restaurantService.getRestaurantOrders(restaurantId);
  }

  /**
   * Get all drivers associated with a restaurant.
   *
   * GET /restaurant/{restaurantId}/drivers
   *
   * @param restaurantId the restaurant ID
   * @return array of Driver objects
   */
  @GetMapping("/{restaurantId}/drivers")
  @ResponseStatus(HttpStatus.OK)
  public List<Driver> getRestaurantDrivers(@PathVariable long restaurantId) {
    return restaurantService.getRestaurantDrivers(restaurantId);
  }

  /**
   * Get all menu items associated with a restaurant.
   *
   * GET /restaurant/{restaurantId}/menu
   *
   * @param restaurantId the restaurant ID
   * @return array of MenuItem objects
   */
  @GetMapping("/{restaurantId}/menu")
  @ResponseStatus(HttpStatus.OK)
  public List<MenuItem> getRestaurantMenuItems(@PathVariable long restaurantId) {
    return restaurantService.getRestaurantMenuItems(restaurantId);
  }
}