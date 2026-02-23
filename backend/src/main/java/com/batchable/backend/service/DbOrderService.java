package com.batchable.backend.service;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.websocket.OrderWebSocketPublisher;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * OrderService is part of the business logic layer.
 *
 * Responsibilities: - Enforce valid order lifecycle transitions - Protect domain invariants -
 * Coordinate persistence and higher-level system behavior
 *
 * This service should be the ONLY component allowed to mutate Order state. Controllers must call
 * this service instead of modifying Orders directly.
 */
@Service
public class DbOrderService {

  private final OrderDAO orderDAO;
  private final BatchDAO batchDAO;
  private final OrderWebSocketPublisher publisher;

  public DbOrderService(OrderDAO orderDAO, BatchDAO batchDAO, OrderWebSocketPublisher publisher) {
    this.orderDAO = orderDAO;
    this.batchDAO = batchDAO;
    this.publisher = publisher;
  }

  /**
   * Creates a new order in the system.
   *
   * Responsibilities: - Validate required fields (restaurant, items, timestamps, etc.) - Ensure
   * order does not already exist - Initialize default state COOKING - Persist to database
   *
   * Errors: - IllegalArgumentException if required fields are missing or invalid -
   * IllegalStateException if order ID already exists - RuntimeException (or custom
   * DataAccessException) if persistence fails
   */
  public long createOrder(Order order) {
    Instant now = Instant.now();
    if (order == null) {
      throw new IllegalArgumentException("order is required");
    }

    if (order.restaurantId <= 0) {
      throw new IllegalArgumentException("restaurantId is required");
    }

    if (order.destination == null || order.destination.trim().isEmpty()) {
      throw new IllegalArgumentException("destination is required");
    }

    if (order.cookedTime == null) {
      throw new IllegalArgumentException("cookedTime is required");
    }

    if (order.deliveryTime == null) {
      throw new IllegalArgumentException("deliveryTime is required");
    }

    if (now.isAfter(order.cookedTime) || order.cookedTime.isAfter(order.deliveryTime)) {
      throw new IllegalArgumentException("Must have initialTime <= cookedTime <= deliveryTime");
    }

    // Allow frontend dummy id (negative or 0)
    if (order.id > 0) {
      throw new IllegalStateException("order.id must be <= 0 (database-generated)");
    }

    try {
      // batch ids initialized to null then filled in later
      long id = orderDAO.createOrder(order.restaurantId, order.destination, order.itemNamesJson,
          now, order.deliveryTime, order.cookedTime, Order.State.COOKING, order.highPriority, null);

      publisher.refreshOrderData(order.restaurantId);
      return id;

    } catch (SQLException e) {
      throw new RuntimeException("Failed to create order", e);
    }
  }

  /**
   * Advances the state of the specified order by exactly one valid step in the lifecycle. E.g.
   * COOKING -> COOKED
   *
   * Responsibilities: - Retrieve order from persistence layer - Determine the next valid state -
   * Validate transition is allowed - Persist updated state - Trigger any necessary downstream
   * effects (e.g., batching, notifications)
   *
   * Errors: - IllegalArgumentException if orderId does not exist - IllegalStateException if: •
   * Order is already DELIVERED • No valid next state exists - RuntimeException if persistence fails
   */
  public void advanceOrderState(long orderId) {
    Order order = getOrder(orderId);

    if (order.state == Order.State.DELIVERED) {
      throw new IllegalStateException("Order is already DELIVERED");
    }

    Order.State next = determineNextState(order.state);

    if (next == null) {
      throw new IllegalStateException("No valid next state exists");
    }

    if (next == Order.State.COOKED) {
      updateOrderCookedTime(orderId, Instant.now());
    } else if (next == Order.State.DELIVERED) {
      updateOrderDeliveryTime(orderId, Instant.now());
    }

    try {
      orderDAO.updateOrderState(orderId, next);

      // Push update to frontend via WebSocket
      publisher.refreshOrderData(order.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to advance order state", e);
    }
  }

  private Order.State determineNextState(Order.State current) {
    switch (current) {
      case COOKING:
        return Order.State.COOKED;
      case COOKED:
        return Order.State.DRIVING;
      case DRIVING:
        return Order.State.DELIVERED;
      default:
        return null;
    }
  }

  /**
   * Updates the cooked time of an order.
   *
   * Domain Invariants: - cookedTime must not be null - cookedTime must not be before
   * order.initialTime - cookedTime must not be in the far past relative to now
   *
   * Responsibilities: - Retrieve order - Validate temporal constraints - Persist update -
   * Potentially trigger batching recalculation if READY state depends on it
   *
   * Errors: - IllegalArgumentException if: • orderId does not exist • cookedTime is null •
   * cookedTime < order.initialTime - IllegalStateException if: • Order is already DELIVERED -
   * RuntimeException if persistence fails
   */
  public void updateOrderCookedTime(long orderId, Instant cookedTime) {
    if (cookedTime == null) {
      throw new IllegalArgumentException("cookedTime cannot be null");
    }

    Order order = getOrder(orderId);

    if (order.state.getRank() >= Order.State.COOKED.getRank()) {
      throw new IllegalStateException(
          "Cannot update cooked time after order state has passed COOKED. Order id " + order.id);
    }

    if (cookedTime.isBefore(order.initialTime)) {
      throw new IllegalArgumentException("cookedTime cannot be before initialTime");
    }

    try {
      orderDAO.updateOrderCookedTime(orderId, cookedTime);

      // Push update to frontend via WebSocket
      publisher.refreshOrderData(order.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update cooked time", e);
    }
  }

  /**
   * Updates the delivery time of an order.
   *
   * Domain Invariants: - deliveryTime must not be null - deliveryTime must not be before
   * order.initialTime - deliveryTime must not be in the far past relative to now
   *
   * Responsibilities: - Retrieve order - Validate temporal constraints - Persist update -
   * Potentially trigger batching recalculation if READY state depends on it
   *
   * Errors: - IllegalArgumentException if: • orderId does not exist • deliveryTime is null •
   * deliveryTime < order.cookedTime - RuntimeException if persistence fails
   */
  public void updateOrderDeliveryTime(long orderId, Instant deliveryTime) {
    if (deliveryTime == null) {
      throw new IllegalArgumentException("deliveryTime cannot be null");
    }

    Order order = getOrder(orderId);

    if (deliveryTime.isBefore(order.cookedTime)) {
      throw new IllegalArgumentException("deliveryTime cannot be before cookedTime");
    }

    if (order.state.getRank() >= Order.State.DELIVERED.getRank()) {
      throw new IllegalStateException(
          "Cannot update cooked time after order state has passed DELIVERED. Order id " + order.id);
    }

    try {
      orderDAO.updateOrderDeliveryTime(orderId, deliveryTime);

      // Push update to frontend via WebSocket
      publisher.refreshOrderData(order.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update cooked time", e);
    }
  }



  /**
   * Resets the specified order to behave as if it were newly created.
   *
   * Behavior: - Reset lifecycle state to initial COOKING - Clear delivery/batch assignment - Mark
   * highPriority = true - Preserve identity and restaurant association
   *
   * Use case: - Order was incorrect or rejected and must be remade
   *
   * Responsibilities: - Validate order exists - Ensure order is eligible for remake (e.g.,
   * DELIVERED may not be allowed) - Reset state fields consistently - Persist update - Trigger
   * re-batching logic
   *
   * Errors: - IllegalArgumentException if orderId does not exist - IllegalStateException if: •
   * Order has been permanently finalized - RuntimeException if persistence fails
   */
  public void remakeOrder(long orderId) {
    Order order = getOrder(orderId);

    if (order.state == Order.State.DELIVERED) {
      throw new IllegalStateException("Delivered orders cannot be remade");
    }
    
    Instant newInitialTime = Instant.now();
    Instant newCookedTime = newInitialTime.plus(Duration.between(order.initialTime, order.cookedTime));
    Instant newDeliveryTime = newInitialTime.plus(Duration.between(order.initialTime, order.deliveryTime));

    try {
      orderDAO.remakeOrder(orderId, Order.State.COOKING, newInitialTime, 
                           newDeliveryTime, newCookedTime, true);
      // Push update to frontend via WebSocket
      publisher.refreshOrderData(order.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to remake order", e);
    }
  }

  /**
   * Retrieves the order by ID.
   *
   * Errors: - IllegalArgumentException if orderId does not exist
   */
  public Order getOrder(long orderId) {
    if (orderId <= 0) {
      throw new IllegalArgumentException("orderId must be positive");
    }

    try {
      return orderDAO.getOrder(orderId)
          .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve order", e);
    }
  }

  /**
   * Returns the Batch corresponding to the given batchId.
   *
   * Errors: - IllegalArgumentException if batchId does not exist
   */
  public Batch getBatch(long batchId) {
    if (batchId <= 0) {
      throw new IllegalArgumentException("batchId must be positive");
    }

    try {
      return batchDAO.getBatch(batchId)
          .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
    } catch (SQLException e) {
      throw new RuntimeException("Failed to retrieve batch", e);
    }
  }

  /**
   * Creates a new batch in the system.
   *
   * Responsibilities: - Validate required fields - Ensure batch does not already exist - Persist to
   * database
   *
   * Errors: - IllegalArgumentException if required fields are missing or invalid -
   * IllegalStateException if batch ID already exists - RuntimeException (or custom
   * DataAccessException) if persistence fails
   */
  public long createBatch(Batch batch) {
    if (batch == null) {
      throw new IllegalArgumentException("batch is required");
    }

    if (batch.driverId <= 0) {
      throw new IllegalArgumentException("driverId is required");
    }

    if (batch.route == null || batch.route.trim().isEmpty()) {
      throw new IllegalArgumentException("route is required");
    }

    if (batch.dispatchTime == null) {
      throw new IllegalArgumentException("initialTime is required");
    }

    if (batch.expectedCompletionTime == null) {
      throw new IllegalArgumentException("completionTime is required");
    }

    if (batch.expectedCompletionTime.isBefore(batch.dispatchTime)) {
      throw new IllegalArgumentException("completionTime must not be before dispatchTime");
    }

    // Allow frontend dummy id (negative or 0)
    if (batch.id > 0) {
      throw new IllegalStateException("batch.id must be <= 0 (database-generated)");
    }

    try {
      // batch ids initialized to null then filled in later
      long id = batchDAO.createBatch(batch.driverId, batch.route, batch.dispatchTime,
          batch.expectedCompletionTime);
      return id;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create batch", e);
    }
  }

  /**
   * Returns all orders belonging to a specific batch.
   *
   * Errors: - IllegalArgumentException if batchId does not exist
   */
  public List<Order> getBatchOrders(long batchId) {
    getBatch(batchId); // validate exists

    try {
      List<Order> orders = orderDAO.listOrdersInBatch(batchId);
      return orders;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to list batch orders", e);
    }
  }

  /**
   * Removes an order from the system.
   *
   * Domain Rules: - Delivered orders may not be removable - Removing an order may require batch
   * recalculation
   *
   * Responsibilities: - Validate order exists - Ensure removal is allowed - Remove from persistence
   * - Trigger downstream updates if batch was affected
   *
   * Errors: - IllegalArgumentException if orderId does not exist - IllegalStateException if: •
   * Order is already DELIVERED • Order is in a finalized state - RuntimeException if persistence
   * fails
   */
  public void removeOrder(long orderId) {
    Order order = getOrder(orderId);

    if (order.state == Order.State.DELIVERED) {
      throw new IllegalStateException("Delivered orders cannot be removed");
    }

    try {
      orderDAO.deleteOrder(orderId);

      // Push update to frontend via WebSocket
      publisher.refreshOrderData(order.restaurantId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to remove order", e);
    }
  }

  /**
   * Assigns an order to a batch by setting its batchId.* Domain Invariants: orderId must correspond
   * to an existing order batchId must correspond to an existing batch An order may belong to at
   * most one batch at a time
   *
   * Behavior: Updates the order’s batchId to the specified batch Overwrites any existing batch
   * assignment
   *
   * Responsibilities: Retrieve the order Validate the target batch exists Update the order’s batch
   * association Persist the change Notify downstream consumers (e.g., frontend, batching observers)
   *
   * Errors: IllegalArgumentException if: • orderId does not exist • batchId does not exist
   * IllegalStateException if: • Order is already DELIVERED • Order is in a state that forbids
   * reassignment RuntimeException if persistence fails
   *
   * @param orderId the id of the order to assign
   * @param batchId the id of the batch to associate with the order
   */
  public void setOrderBatchId(long orderId, long batchId) {
    // Validate order exists + fetch current state
    Order order = getOrder(orderId);

    // Domain rule: no changes if delivered
    if (order.state == Order.State.DELIVERED) {
      throw new IllegalStateException("Delivered orders cannot be reassigned to a batch");
    } else if (order.state == Order.State.DRIVING) {
      throw new IllegalStateException("Driving orders cannot be reassigned to a batch");
    }

    // Validate batch exists
    getBatch(batchId);

    try {
      boolean ok = orderDAO.updateOrderBatchId(orderId, batchId);
      if (!ok)
        throw new IllegalArgumentException("Order not found: " + orderId);


      // Push update to frontend via WebSocket
      publisher.refreshOrderData(order.restaurantId);

    } catch (SQLException e) {
      throw new RuntimeException("Failed to assign order " + orderId + " to batch " + batchId, e);
    }
  }
}
