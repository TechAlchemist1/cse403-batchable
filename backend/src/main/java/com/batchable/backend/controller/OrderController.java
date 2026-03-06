package com.batchable.backend.controller;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.service.OrderService;

import java.time.Instant;
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
@RequestMapping("/order")
public class OrderController {
  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * Create a new order.
   *
   * POST /order Body: JSON representing an Order object
   *
   * @param order the order to create
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public long createOrder(@RequestBody Order order) {
    long id = orderService.createOrder(order);
    return id;
  }

  /**
   * Advance an order's state by one lifecycle step.
   *
   * PUT /order/{orderId}/advance
   *
   * @param orderId the ID of the order
   */
  @PutMapping("/{orderId}/advance")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void advanceOrderState(@PathVariable long orderId) {
    orderService.advanceOrderState(orderId);
  }

  /**
   * Mark an order as delivered.
   *
   * PUT /order/{orderId}/delivered/{token}
   *
   * @param orderId the ID of the order
   */
  @PutMapping("/{orderId}/delivered/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void markDelivered(@PathVariable long orderId, @PathVariable String token) {
    orderService.markDelivered(orderId, token);
  }

  /**
   * Update the cooked time for an order.
   *
   * PUT /order/{orderId}/cookedTime Body: JSON containing an ISO-8601 timestamp
   *
   * @param orderId the ID of the order
   * @param cookedTime the new cooked time
   */
  @PutMapping("/{orderId}/cookedTime")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateOrderCookedTime(@PathVariable long orderId, @RequestBody Instant cookedTime) {
    orderService.updateOrderCookedTime(orderId, cookedTime);
  }

  /**
   * Remake an order as if newly created.
   *
   * PUT /order/{orderId}/remake
   *
   * @param orderId the ID of the order
   */
  @PutMapping("/{orderId}/remake")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remakeOrder(@PathVariable long orderId) {
    orderService.remakeOrder(orderId);
  }

  /**
   * Get a single order by ID.
   *
   * GET /order/{orderId}
   *
   * @param orderId the ID of the order
   * @return the Order object
   */
  @GetMapping("/{orderId}")
  @ResponseStatus(HttpStatus.OK)
  public Order getOrder(@PathVariable long orderId) {
    return orderService.getOrder(orderId);
  }

  /**
   * Get a single batch by batch ID.
   *
   * GET /order/batch/{batchId}
   *
   * @param batchId the batch ID
   * @return corresponding Batch object
   */
  @GetMapping("/batch/{batchId}")
  @ResponseStatus(HttpStatus.OK)
  public Batch getBatch(@PathVariable long batchId) {
    return orderService.getBatch(batchId);
  }

  /**
   * Get a single batch of orders by batch ID.
   *
   * GET /order/batch/{batchId}/orders
   *
   * @param batchId the batch ID
   * @return list of Orders in that batch
   */
  @GetMapping("/batch/{batchId}/orders")
  @ResponseStatus(HttpStatus.OK)
  public List<Order> getBatchOrders(@PathVariable long batchId) {
    return orderService.getBatchOrders(batchId);
  }

  /**
   * Delete an order.
   *
   * DELETE /order/{orderId}
   *
   * @param orderId the ID of the order
   */
  @DeleteMapping("/{orderId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeOrder(@PathVariable long orderId) {
    orderService.removeOrder(orderId);
  }
}