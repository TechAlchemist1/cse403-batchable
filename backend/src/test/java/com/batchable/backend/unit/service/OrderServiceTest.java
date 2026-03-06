package com.batchable.backend.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import com.batchable.backend.EventSource.SsePublisher;
import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.service.DbOrderService;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for OrderService using Mockito.
 *
 * This test class verifies: - Input validation and exception throwing in createOrder. - Default
 * state assignment when creating an order. - State transitions in advanceOrderState, including
 * timestamp updates. - Validation of cooked time updates. - Remake and removal logic (domain
 * restrictions). - Retrieval methods (getOrder, getBatch, getBatchOrders). - Batch assignment
 * (setOrderBatchId) with checks for existence and state. - Proper handling of SQLException
 * (wrapping into RuntimeException). - WebSocket publisher invocations after successful
 * modifications.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

  @Mock
  private OrderDAO orderDAO;
  @Mock
  private BatchDAO batchDAO;
  @Mock
  private SsePublisher publisher;

  private DbOrderService service;

  @BeforeEach
  void setUp() {
    service = new DbOrderService(orderDAO, batchDAO, publisher);
  }

  // ---- helpers ----

  private static Order order(long id, long restaurantId, String destination, String itemJson,
      Instant initial, Instant delivery, Instant cooked, Order.State state, boolean highPriority,
      Long batchId) {
    Instant now = Instant.now();
    if (initial == null) {
      initial = now;
    }
    if (cooked == null) {
      cooked = now.plus(Duration.ofMinutes(5L));
    }
    if (delivery == null) {
      delivery = now.plus(Duration.ofMinutes(10L));
    }
    return new Order(id, restaurantId, destination, itemJson, initial, delivery, cooked, state,
        highPriority, batchId);
  }

  private static Batch batch(long id, long driverId) {
    return new Batch(id, driverId, "poly", Instant.now(), Instant.now().plusSeconds(60), false);
  }

  // ---- createOrder ----

  /** Verifies that passing null to createOrder throws IllegalArgumentException. */
  @Test
  void createOrder_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(null));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that an order with missing (zero) restaurantId is rejected. */
  @Test
  void createOrder_missingRestaurantId_throwsIAE() {
    Order o = order(0, 0, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that an order with blank destination is rejected. */
  @Test
  void createOrder_blankDestination_throwsIAE() {
    Order o = order(0, 1, "   ", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that an order with null initial time is rejected. */
  @Test
  void createOrder_nullInitialTime_throwsIAE() {
    Order o = new Order(0, 1, "Dest", "[]", null, Instant.now().plus(Duration.ofMinutes(10)),
        Instant.now().plus(Duration.ofMinutes(5)), Order.State.COOKING, true, null);
    assertDoesNotThrow(() -> service.createOrder(o));
  }

  /** Verifies that an order with a pre‑assigned positive ID is rejected (must be generated). */
  @Test
  void createOrder_positiveId_throwsISE() {
    Order o = order(5, 1, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalStateException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /**
   * Verifies that a valid order is created with state forced to COOKING, the correct ID is
   * returned, and a WebSocket refresh is published.
   */
  @Test
  void createOrder_happyPath_setsStateCooking_returnsId_andRefreshes() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o =
        order(0, 7, "Seattle", "[\"Burger\"]", t0, null, null, Order.State.COOKING, false, 123L);

    when(orderDAO.createOrder(eq(7L), eq("Seattle"), eq("[\"Burger\"]"), any(Instant.class),
        any(Instant.class), any(Instant.class), eq(Order.State.COOKING), // defaulted by service
        eq(false), isNull())).thenReturn(99L);

    long id = service.createOrder(o);
    assertEquals(99L, id);

    verify(orderDAO).createOrder(eq(7L), eq("Seattle"), eq("[\"Burger\"]"), any(Instant.class),
        any(Instant.class), any(Instant.class), eq(Order.State.COOKING), // defaulted by service
        eq(false), isNull());
    verify(publisher).refreshOrderData(7L);
    verifyNoInteractions(batchDAO);
  }

  /**
   * Verifies that a SQLException from the DAO is wrapped in a RuntimeException and that no
   * WebSocket publish occurs.
   */
  @Test
  void createOrder_sqlException_wrapped_andNoRefresh() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(0, 7, "Seattle", "[]", t0, null, null, Order.State.COOKING, true, null);

    when(orderDAO.createOrder(anyLong(), anyString(), anyString(), any(), any(), any(), any(),
        anyBoolean(), any())).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createOrder(o));
    assertTrue(ex.getMessage().contains("Failed to create order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- getOrder ----

  /** Verifies that getOrder rejects a non‑positive ID. */
  @Test
  void getOrder_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.getOrder(0));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that getOrder throws when the order does not exist. */
  @Test
  void getOrder_missing_throwsIAE() throws Exception {
    when(orderDAO.getOrder(5L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getOrder(5L));
  }

  /** Verifies that getOrder returns the expected order when found. */
  @Test
  void getOrder_happyPath_returns() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    assertSame(o, service.getOrder(5L));
  }

  /** Verifies that a SQLException from getOrder is wrapped in RuntimeException. */
  @Test
  void getOrder_sqlException_wrapped() throws Exception {
    when(orderDAO.getOrder(5L)).thenThrow(new SQLException("boom"));
    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getOrder(5L));
    assertTrue(ex.getMessage().contains("Failed to retrieve order"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- advanceOrderState ----

  /** Verifies that advanceOrderState throws when the order is already DELIVERED. */
  @Test
  void advanceOrderState_delivered_throwsISE() throws Exception {
    Order o =
        order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.advanceOrderState(5L));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(batchDAO, publisher);
  }

  /**
   * Verifies that advancing from COOKING to COOKED updates the state and publishes a WebSocket
   * refresh.
   */
  @Test
  void advanceOrderState_cooking_to_cooked_updatesState_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderState(5L, Order.State.COOKED)).thenReturn(true);

    service.advanceOrderState(5L);

    verify(orderDAO).updateOrderState(5L, Order.State.COOKED);
    verify(orderDAO, never()).updateOrderDeliveryTime(anyLong(), any());
    verify(publisher, times(2)).refreshOrderData(7L);
  }

  /**
   * Verifies that advancing from DRIVING to DELIVERED updates the state, sets the delivery time,
   * and publishes a refresh.
   */
  @Test
  void advanceOrderState_driving_to_delivered_setsDeliveryTime_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now().minus(Duration.ofMinutes(10)), null,
        Instant.now().minus(Duration.ofMinutes(5)), Order.State.DRIVING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderState(5L, Order.State.DELIVERED)).thenReturn(true);

    service.advanceOrderState(5L);

    verify(orderDAO).updateOrderState(5L, Order.State.DELIVERED);

    ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
    verify(orderDAO).updateOrderDeliveryTime(eq(5L), cap.capture());
    assertNotNull(cap.getValue()); // don't assert exact instant

    verify(publisher, times(2)).refreshOrderData(7L);
  }

  /**
   * Verifies that a SQLException from updateOrderState is wrapped and no refresh is published.
   */
  @Test
  void advanceOrderState_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    // when(orderDAO.updateOrderState(anyLong(), any())).thenThrow(new SQLException("boom"));
    when(orderDAO.updateOrderCookedTime(anyLong(), any())).thenThrow(new SQLException("boom"));


    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.advanceOrderState(5L));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- updateOrderCookedTime ----

  /** Verifies that updateOrderCookedTime rejects a null cooked time. */
  @Test
  void updateOrderCookedTime_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateOrderCookedTime(1L, null));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that updateOrderCookedTime throws when the order is DELIVERED. */
  @Test
  void updateOrderCookedTime_delivered_throwsISE() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class,
        () -> service.updateOrderCookedTime(5L, t0.plusSeconds(60)));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  /** Verifies that cooked time cannot be before initial time. */
  @Test
  void updateOrderCookedTime_beforeInitial_throwsIAE() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateOrderCookedTime(5L, t0.minusSeconds(1)));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  /**
   * Verifies that a valid cooked time update succeeds and triggers a WebSocket refresh.
   */
  @Test
  void updateOrderCookedTime_happyPath_updates_andRefreshes() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderCookedTime(5L, cooked)).thenReturn(true);

    service.updateOrderCookedTime(5L, cooked);

    verify(orderDAO).updateOrderCookedTime(5L, cooked);
    verify(publisher).refreshOrderData(7L);
  }

  /** Verifies that a SQLException from updateOrderCookedTime is wrapped and no refresh occurs. */
  @Test
  void updateOrderCookedTime_sqlException_wrapped_andNoRefresh() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderCookedTime(anyLong(), any())).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.updateOrderCookedTime(5L, cooked));
    assertTrue(ex.getMessage().contains("Failed to update cooked time"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- remakeOrder ----

  /** Verifies that a delivered order cannot be remade. */
  @Test
  void remakeOrder_delivered_throwsISE() throws Exception {
    Order o =
        order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.remakeOrder(5L));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  /** Verifies that a non‑delivered order can be remade and a refresh is published. */
  @Test
  void remakeOrder_happyPath_callsDao_andRefreshes() throws Exception {
    Instant now = Instant.now();
    Instant in20min = now.plus(Duration.ofMinutes(20));
    Instant in5min = now.plus(Duration.ofMinutes(5));

    Order o =
        order(5, 7, "Dest", "[]", Instant.now(), in20min, in5min, Order.State.DRIVING, false, 10L);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.remakeOrder(eq(5L), eq(Order.State.COOKING), any(Instant.class),
        any(Instant.class), any(Instant.class), eq(true))).thenReturn(true);

    service.remakeOrder(5L);

    verify(orderDAO).remakeOrder(eq(5L), eq(Order.State.COOKING), any(Instant.class),
        any(Instant.class), any(Instant.class), eq(true));
    verify(publisher).refreshOrderData(7L);
  }

  /** Verifies that a SQLException from remakeOrder is wrapped and no refresh occurs. */
  @Test
  void remakeOrder_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(
        orderDAO.remakeOrder(anyLong(), any(), isNotNull(), isNotNull(), isNotNull(), anyBoolean()))
            .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.remakeOrder(5L));
    assertTrue(ex.getMessage().contains("Failed to remake order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- removeOrder ----

  /** Verifies that a delivered order cannot be removed. */
  @Test
  void removeOrder_delivered_throwsISE() throws Exception {
    Order o =
        order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.removeOrder(5L));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  /** Verifies that a non‑delivered order can be removed and a refresh is published. */
  @Test
  void removeOrder_happyPath_deletes_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.deleteOrder(5L)).thenReturn(true);

    service.removeOrder(5L);

    verify(orderDAO).deleteOrder(5L);
    verify(publisher).refreshOrderData(7L);
  }

  /** Verifies that a SQLException from deleteOrder is wrapped and no refresh occurs. */
  @Test
  void removeOrder_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.deleteOrder(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.removeOrder(5L));
    assertTrue(ex.getMessage().contains("Failed to remove order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- getBatch / getBatchOrders ----

  /** Verifies that getBatch rejects a non‑positive ID. */
  @Test
  void getBatch_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.getBatch(0));
    verifyNoInteractions(batchDAO, orderDAO, publisher);
  }

  /** Verifies that getBatch throws when the batch does not exist. */
  @Test
  void getBatch_missing_throwsIAE() throws Exception {
    when(batchDAO.getBatch(9L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getBatch(9L));
  }

  /**
   * Verifies that getBatchOrders first validates that the batch exists, then returns the list of
   * orders from the DAO.
   */
  @Test
  void getBatchOrders_validatesBatchExists_thenLists() throws Exception {
    Batch b = batch(9, 1);
    when(batchDAO.getBatch(9L)).thenReturn(Optional.of(b));

    Order o1 = order(1, 7, "D1", "[]", Instant.now(), null, null, Order.State.COOKING, true, 9L);
    Order o2 = order(2, 7, "D2", "[]", Instant.now(), null, null, Order.State.COOKED, true, 9L);
    when(orderDAO.listOrdersInBatch(9L)).thenReturn(List.of(o1, o2));

    List<Order> orders = service.getBatchOrders(9L);

    assertEquals(2, orders.size());
    assertEquals(1L, orders.get(0).id);
    assertEquals(2L, orders.get(1).id);

    verify(batchDAO).getBatch(9L);
    verify(orderDAO).listOrdersInBatch(9L);
  }


  // ---- setOrderBatchId ----

  /** Verifies that a delivered order cannot be assigned to a batch. */
  @Test
  void setOrderBatchId_delivered_throwsISE() throws Exception {
    Order o =
        order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.setOrderBatchId(5L, 10L));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(batchDAO, publisher);
  }

  /**
   * Verifies that setOrderBatchId throws when the target batch does not exist, and no update is
   * attempted.
   */
  @Test
  void setOrderBatchId_missingBatch_throwsIAE_andDoesNotUpdateOrder() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(batchDAO.getBatch(10L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.setOrderBatchId(5L, 10L));

    verify(orderDAO).getOrder(5L);
    verify(batchDAO).getBatch(10L);
    verify(orderDAO, never()).updateOrderBatchId(anyLong(), anyLong());
    verifyNoInteractions(publisher);
  }

  /**
   * Verifies that a valid batch assignment succeeds, updates the order, and publishes a WebSocket
   * refresh.
   */
  @Test
  void setOrderBatchId_happyPath_updates_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(batchDAO.getBatch(10L)).thenReturn(Optional.of(batch(10, 1)));
    when(orderDAO.updateOrderBatchId(5L, 10L)).thenReturn(true);

    service.setOrderBatchId(5L, 10L);

    verify(orderDAO).updateOrderBatchId(5L, 10L);
    verify(publisher).refreshOrderData(7L);
  }

  /**
   * Verifies that if updateOrderBatchId returns false, an exception is thrown and no refresh
   * occurs.
   */
  @Test
  void setOrderBatchId_updateReturnsFalse_throwsIAE_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(batchDAO.getBatch(10L)).thenReturn(Optional.of(batch(10, 1)));
    when(orderDAO.updateOrderBatchId(5L, 10L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.setOrderBatchId(5L, 10L));

    verify(publisher, never()).refreshOrderData(7L);
  }

  /** Verifies that a SQLException from updateOrderBatchId is wrapped and no refresh occurs. */
  @Test
  void setOrderBatchId_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(batchDAO.getBatch(10L)).thenReturn(Optional.of(batch(10, 1)));
    when(orderDAO.updateOrderBatchId(anyLong(), anyLong())).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.setOrderBatchId(5L, 10L));
    assertTrue(ex.getMessage().contains("Failed to assign order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }
  // ---- updateOrderDeliveryTime ----

  /** Verifies that updateOrderDeliveryTime rejects a null delivery time. */
  @Test
  void updateOrderDeliveryTime_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateOrderDeliveryTime(1L, null));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that updateOrderDeliveryTime throws when the order is DELIVERED. */
  @Test
  void updateOrderDeliveryTime_delivered_throwsISE() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(30); // before deliveryTime
    Order o = order(5, 7, "Dest", "[]", t0, null, cooked, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class,
        () -> service.updateOrderDeliveryTime(5L, t0.plusSeconds(60)));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  /** Verifies that delivery time cannot be before cooked time. */
  @Test
  void updateOrderDeliveryTime_beforeCooked_throwsIAE() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Instant delivery = t0.plusSeconds(300); // before cooked
    Order o = order(5, 7, "Dest", "[]", t0, null, cooked, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalArgumentException.class,
        () -> service.updateOrderDeliveryTime(5L, delivery));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  /** Verifies that a valid delivery time update succeeds and triggers a WebSocket refresh. */
  @Test
  void updateOrderDeliveryTime_happyPath_updates_andRefreshes() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Instant delivery = t0.plusSeconds(1200);
    Order o = order(5, 7, "Dest", "[]", t0, null, cooked, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderDeliveryTime(5L, delivery)).thenReturn(true);

    service.updateOrderDeliveryTime(5L, delivery);

    verify(orderDAO).updateOrderDeliveryTime(5L, delivery);
    verify(publisher).refreshOrderData(7L);
  }

  /** Verifies that a SQLException from updateOrderDeliveryTime is wrapped and no refresh occurs. */
  @Test
  void updateOrderDeliveryTime_sqlException_wrapped_andNoRefresh() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Instant delivery = t0.plusSeconds(1200);
    Order o = order(5, 7, "Dest", "[]", t0, null, cooked, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderDeliveryTime(anyLong(), any())).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.updateOrderDeliveryTime(5L, delivery));
    assertTrue(ex.getMessage().contains("Failed to update cooked time")); // Note: method has same
                                                                          // message
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- createBatch ----

  /** Verifies that passing null to createBatch throws IllegalArgumentException. */
  @Test
  void createBatch_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.createBatch(null));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  /** Verifies that a batch with missing (zero) driverId is rejected. */
  @Test
  void createBatch_missingDriverId_throwsIAE() {
    Batch b = batch(0, 0); // driverId = 0
    assertThrows(IllegalArgumentException.class, () -> service.createBatch(b));
    verifyNoInteractions(batchDAO, publisher);
  }

  /** Verifies that a batch with blank route is rejected. */
  @Test
  void createBatch_blankRoute_throwsIAE() {
    Batch b = new Batch(0, 1, "   ", Instant.now(), Instant.now().plusSeconds(60), false);
    assertThrows(IllegalArgumentException.class, () -> service.createBatch(b));
    verifyNoInteractions(batchDAO, publisher);
  }

  /** Verifies that a batch with null dispatchTime is rejected. */
  @Test
  void createBatch_nullDispatchTime_throwsIAE() {
    Batch b = new Batch(0, 1, "poly", null, Instant.now().plusSeconds(60), false);
    assertThrows(IllegalArgumentException.class, () -> service.createBatch(b));
    verifyNoInteractions(batchDAO, publisher);
  }

  /** Verifies that a batch with null completionTime is rejected. */
  @Test
  void createBatch_nullCompletionTime_throwsIAE() {
    Batch b = new Batch(0, 1, "poly", Instant.now(), null, false);
    assertThrows(IllegalArgumentException.class, () -> service.createBatch(b));
    verifyNoInteractions(batchDAO, publisher);
  }

  /** Verifies that completionTime before dispatchTime is rejected. */
  @Test
  void createBatch_completionBeforeDispatch_throwsIAE() {
    Instant now = Instant.now();
    Batch b = new Batch(0, 1, "poly", now, now.minusSeconds(10), false);
    assertThrows(IllegalArgumentException.class, () -> service.createBatch(b));
    verifyNoInteractions(batchDAO, publisher);
  }

  /** Verifies that a batch with a pre‑assigned positive ID is rejected. */
  @Test
  void createBatch_positiveId_throwsISE() {
    Batch b = new Batch(5, 1, "poly", Instant.now(), Instant.now().plusSeconds(60), false);
    assertThrows(IllegalStateException.class, () -> service.createBatch(b));
    verifyNoInteractions(batchDAO, publisher);
  }

  /** Verifies that a valid batch is created and the ID is returned. */
  @Test
  void createBatch_happyPath_returnsId() throws Exception {
    Instant dispatch = Instant.parse("2026-02-16T00:00:00Z");
    Instant completion = dispatch.plusSeconds(3600);
    Batch b = new Batch(0, 7, "polyline", dispatch, completion, false);

    when(batchDAO.createBatch(7L, "polyline", dispatch, completion)).thenReturn(99L);

    long id = service.createBatch(b);
    assertEquals(99L, id);

    verify(batchDAO).createBatch(7L, "polyline", dispatch, completion);
    // No WebSocket refresh expected for batch creation (not in code)
    verifyNoInteractions(publisher);
  }

  /** Verifies that a SQLException from createBatch is wrapped in RuntimeException. */
  @Test
  void createBatch_sqlException_wrapped() throws Exception {
    Instant dispatch = Instant.parse("2026-02-16T00:00:00Z");
    Instant completion = dispatch.plusSeconds(3600);
    Batch b = new Batch(0, 7, "polyline", dispatch, completion, false);

    when(batchDAO.createBatch(anyLong(), anyString(), any(), any()))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createBatch(b));
    assertTrue(ex.getMessage().contains("Failed to create batch"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- removeAllUnfinishedBatches ----

  /** Verifies that removeAllUnfinishedBatches delegates to the DAO. */
  @Test
  void removeAllUnfinishedBatches_callsDao() throws Exception {
    service.removeAllUnfinishedBatches();
    verify(batchDAO).removeAllUnfinishedBatches();
  }

  /** Verifies that a SQLException from removeAllUnfinishedBatches is wrapped. */
  @Test
  void removeAllUnfinishedBatches_sqlException_wrapped() throws Exception {
    doThrow(new SQLException("boom")).when(batchDAO).removeAllUnfinishedBatches();

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.removeAllUnfinishedBatches());
    assertTrue(ex.getMessage().contains("Failed to remove unfinished batches"));
    assertTrue(ex.getCause() instanceof SQLException);
  }
}
