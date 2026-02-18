package com.batchable.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.websocket.OrderWebSocketPublisher;
import java.sql.SQLException;
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
 * OrderService mock-based unit tests (Mockito).
 *
 * Covers:
 *  - createOrder validations + default state behavior
 *  - advanceOrderState transitions + delivered timestamp behavior (no exact time assert)
 *  - updateOrderCookedTime invariants
 *  - remake/remove restrictions
 *  - getOrder/getBatch/getBatchOrders
 *  - setOrderBatchId invariants and DAO interactions
 *  - SQLException wrapping and websocket refresh behavior
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

  @Mock private OrderDAO orderDAO;
  @Mock private BatchDAO batchDAO;
  @Mock private OrderWebSocketPublisher publisher;
  @Mock private BatchingManager mockBatchingManager;

  private OrderService service;

  @BeforeEach
  void setUp() {
    service = new OrderService(orderDAO, batchDAO, publisher, mockBatchingManager);
  }

  // ---- helpers ----

  private static Order order(
      long id,
      long restaurantId,
      String destination,
      String itemJson,
      Instant initial,
      Instant delivery,
      Instant cooked,
      Order.State state,
      boolean highPriority,
      Long batchId) {
    return new Order(id, restaurantId, destination, itemJson, initial, delivery, cooked, state, highPriority, batchId);
  }

  private static Batch batch(long id, long driverId) {
    return new Batch(id, driverId, "poly", Instant.now(), Instant.now().plusSeconds(60));
  }

  // ---- createOrder ----

  @Test
  void createOrder_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(null));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void createOrder_missingRestaurantId_throwsIAE() {
    Order o = order(0, 0, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void createOrder_blankDestination_throwsIAE() {
    Order o = order(0, 1, "   ", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void createOrder_nullInitialTime_throwsIAE() {
    Order o = order(0, 1, "Dest", "[]", null, null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalArgumentException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void createOrder_positiveId_throwsISE() {
    Order o = order(5, 1, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    assertThrows(IllegalStateException.class, () -> service.createOrder(o));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void createOrder_happyPath_setsStateCooking_returnsId_andRefreshes() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(0, 7, "Seattle", "[\"Burger\"]", t0, null, null, Order.State.DRIVING, false, 123L);

    when(orderDAO.createOrder(
            eq(7L),
            eq("Seattle"),
            eq("[\"Burger\"]"),
            eq(t0),
            eq(null),
            eq(null),
            eq(Order.State.COOKING), // defaulted by service
            eq(false),
            isNull()))
        .thenReturn(99L);

    long id = service.createOrder(o);
    assertEquals(99L, id);

    verify(orderDAO).createOrder(
        7L, "Seattle", "[\"Burger\"]", t0, null, null, Order.State.COOKING, false, null);
    verify(publisher).refreshOrderData(7L);
    verifyNoInteractions(batchDAO);
  }

  @Test
  void createOrder_sqlException_wrapped_andNoRefresh() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(0, 7, "Seattle", "[]", t0, null, null, Order.State.COOKING, true, null);

    when(orderDAO.createOrder(anyLong(), anyString(), anyString(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createOrder(o));
    assertTrue(ex.getMessage().contains("Failed to create order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- getOrder ----

  @Test
  void getOrder_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.getOrder(0));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void getOrder_missing_throwsIAE() throws Exception {
    when(orderDAO.getOrder(5L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getOrder(5L));
  }

  @Test
  void getOrder_happyPath_returns() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    assertSame(o, service.getOrder(5L));
  }

  @Test
  void getOrder_sqlException_wrapped() throws Exception {
    when(orderDAO.getOrder(5L)).thenThrow(new SQLException("boom"));
    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getOrder(5L));
    assertTrue(ex.getMessage().contains("Failed to retrieve order"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- advanceOrderState ----

  @Test
  void advanceOrderState_delivered_throwsISE() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.advanceOrderState(5L, true));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(batchDAO, publisher);
  }

  @Test
  void advanceOrderState_cooking_to_cooked_updatesState_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderState(5L, Order.State.COOKED)).thenReturn(true);

    service.advanceOrderState(5L, true);

    verify(orderDAO).updateOrderState(5L, Order.State.COOKED);
    verify(orderDAO, never()).updateOrderDeliveryTime(anyLong(), any());
    verify(publisher).refreshOrderData(7L);
  }

  @Test
  void advanceOrderState_driving_to_delivered_setsDeliveryTime_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DRIVING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderState(5L, Order.State.DELIVERED)).thenReturn(true);

    service.advanceOrderState(5L, true);

    verify(orderDAO).updateOrderState(5L, Order.State.DELIVERED);

    ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
    verify(orderDAO).updateOrderDeliveryTime(eq(5L), cap.capture());
    assertNotNull(cap.getValue()); // don't assert exact instant

    verify(publisher).refreshOrderData(7L);
  }

  @Test
  void advanceOrderState_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderState(anyLong(), any())).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.advanceOrderState(5L, true));
    assertTrue(ex.getMessage().contains("Failed to advance order state"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- updateOrderCookedTime ----

  @Test
  void updateOrderCookedTime_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateOrderCookedTime(1L, null, true));
    verifyNoInteractions(orderDAO, batchDAO, publisher);
  }

  @Test
  void updateOrderCookedTime_delivered_throwsISE() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.updateOrderCookedTime(5L, t0.plusSeconds(60), true));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  @Test
  void updateOrderCookedTime_beforeInitial_throwsIAE() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalArgumentException.class, () -> service.updateOrderCookedTime(5L, t0.minusSeconds(1), true));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  @Test
  void updateOrderCookedTime_happyPath_updates_andRefreshes() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderCookedTime(5L, cooked)).thenReturn(true);

    service.updateOrderCookedTime(5L, cooked, true);

    verify(orderDAO).updateOrderCookedTime(5L, cooked);
    verify(publisher).refreshOrderData(7L);
  }

  @Test
  void updateOrderCookedTime_sqlException_wrapped_andNoRefresh() throws Exception {
    Instant t0 = Instant.parse("2026-02-16T00:00:00Z");
    Instant cooked = t0.plusSeconds(600);
    Order o = order(5, 7, "Dest", "[]", t0, null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.updateOrderCookedTime(anyLong(), any())).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.updateOrderCookedTime(5L, cooked, true));
    assertTrue(ex.getMessage().contains("Failed to update cooked time"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- remakeOrder ----

  @Test
  void remakeOrder_delivered_throwsISE() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.remakeOrder(5L, true));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  @Test
  void remakeOrder_happyPath_callsDao_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DRIVING, false, 10L);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.remakeOrder(5L, Order.State.COOKING, true)).thenReturn(true);

    service.remakeOrder(5L, true);

    verify(orderDAO).remakeOrder(5L, Order.State.COOKING, true);
    verify(publisher).refreshOrderData(7L);
  }

  @Test
  void remakeOrder_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.remakeOrder(anyLong(), any(), anyBoolean())).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.remakeOrder(5L, true));
    assertTrue(ex.getMessage().contains("Failed to remake order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }

  // ---- removeOrder ----

  @Test
  void removeOrder_delivered_throwsISE() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.removeOrder(5L));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(publisher, batchDAO);
  }

  @Test
  void removeOrder_happyPath_deletes_andRefreshes() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKING, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(orderDAO.deleteOrder(5L)).thenReturn(true);

    service.removeOrder(5L);

    verify(orderDAO).deleteOrder(5L);
    verify(publisher).refreshOrderData(7L);
  }

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

  @Test
  void getBatch_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.getBatch(0));
    verifyNoInteractions(batchDAO, orderDAO, publisher);
  }

  @Test
  void getBatch_missing_throwsIAE() throws Exception {
    when(batchDAO.getBatch(9L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getBatch(9L));
  }

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

  @Test
  void setOrderBatchId_delivered_throwsISE() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.DELIVERED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));

    assertThrows(IllegalStateException.class, () -> service.setOrderBatchId(5L, 10L));

    verify(orderDAO).getOrder(5L);
    verifyNoMoreInteractions(orderDAO);
    verifyNoInteractions(batchDAO, publisher);
  }

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

  @Test
  void setOrderBatchId_updateReturnsFalse_throwsIAE_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(batchDAO.getBatch(10L)).thenReturn(Optional.of(batch(10, 1)));
    when(orderDAO.updateOrderBatchId(5L, 10L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.setOrderBatchId(5L, 10L));

    verify(publisher, never()).refreshOrderData(7L);
  }

  @Test
  void setOrderBatchId_sqlException_wrapped_andNoRefresh() throws Exception {
    Order o = order(5, 7, "Dest", "[]", Instant.now(), null, null, Order.State.COOKED, true, null);
    when(orderDAO.getOrder(5L)).thenReturn(Optional.of(o));
    when(batchDAO.getBatch(10L)).thenReturn(Optional.of(batch(10, 1)));
    when(orderDAO.updateOrderBatchId(anyLong(), anyLong())).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.setOrderBatchId(5L, 10L));
    assertTrue(ex.getMessage().contains("Failed to assign order"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(publisher, never()).refreshOrderData(7L);
  }
}
