package com.batchable.backend.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.model.dto.RouteDirectionsResponse;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.BatchingAlgorithm.TentativeBatch;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.OrderService;
import com.batchable.backend.service.RestaurantService;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.service.internal.RestaurantBatchingManager;
import com.batchable.backend.service.internal.RestaurantBatchingManager.Batches;
import com.batchable.backend.service.internal.RestaurantBatchingManager.ReadyBatch;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

@ExtendWith(MockitoExtension.class)
class RestaurantBatchingManagerTest {

  @Mock
  private OrderWebSocketPublisher publisher;
  @Mock
  private BatchingAlgorithm batchingAlgorithm;
  @Mock
  private RouteService routeService;
  @Mock
  private OrderService orderService;
  @Mock
  private RestaurantService restaurantService;
  @Mock
  private DriverService driverService;

  @Captor
  private ArgumentCaptor<Instant> instantCaptor;

  private long nextOrderId = 1L;
  private static final long RESTAURANT_ID = 1L;
  private static final String ADDRESS = "123 Main St";
  private static final long ADDITIONAL_COOK_TIME_SEC = 180;
  private static final long UPDATE_MILLIS = 60000;

  // Helper to create an order
  private Order createOrder(long id, State state, Instant cookedTime, Instant deliveryTime) {
    return new Order(id, RESTAURANT_ID, "dest" + id, "[]", Instant.now(), deliveryTime, cookedTime,
        state, false, null);
  }

  // Helper to create a tentative batch with orders sorted by delivery time (ascending)
  private TentativeBatch createTentativeBatch(List<Order> orders, Instant expiration) {
    List<Order> sortedOrders = new ArrayList<>(orders);
    sortedOrders.sort(Comparator.comparing(o -> o.deliveryTime));
    return new TentativeBatch(sortedOrders, expiration);
  }

  // Helper to create a RouteDirectionsResponse
  private RouteDirectionsResponse createRouteResponse(String polyline, int durationSeconds) {
    RouteDirectionsResponse response = new RouteDirectionsResponse();
    response.setPolyline(polyline);
    response.setDurationSeconds(durationSeconds);
    return response;
  }

  // Helper to verify order delay
  private void verifyOrderDelayed(Order order, Instant originalDelivery, Instant originalCooked) {
    verify(orderService).updateOrderDeliveryTime(eq(order.id), instantCaptor.capture());
    assertEquals(originalDelivery.plusSeconds(ADDITIONAL_COOK_TIME_SEC), instantCaptor.getValue());

    verify(orderService).updateOrderCookedTime(eq(order.id), instantCaptor.capture());
    assertEquals(originalCooked.plusSeconds(ADDITIONAL_COOK_TIME_SEC), instantCaptor.getValue());
  }

  // --- Delegation tests ---
  @Test
  void addOrder_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    Order order = mock(Order.class);
    mgr.addOrder(order);
    verify(batchingAlgorithm).addOrder(emptyBatches.getTentativeBatches(), order, ADDRESS);
  }

  @Test
  void removeOrder_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    mgr.removeOrder(42L);
    verify(batchingAlgorithm).removeOrder(emptyBatches.getTentativeBatches(), 42L, ADDRESS);
  }

  @Test
  void rebatchOrder_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    Order order = mock(Order.class);
    mgr.rebatchOrder(order);
    verify(batchingAlgorithm).rebatchOrder(emptyBatches.getTentativeBatches(), order, ADDRESS);
  }

  @Test
  void updateOrderState_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    mgr.updateOrderState(10L, State.COOKING);
    verify(batchingAlgorithm).updateOrderState(emptyBatches.getTentativeBatches(), 10L,
        State.COOKING);
  }

  // --- getReadyDrivers tests ---
  @Test
  void getReadyDrivers_returnsOnlyAvailableDrivers_upToMax() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    Driver d1 = new Driver(1L, RESTAURANT_ID, "Alice", "111-111-1111", true);
    Driver d2 = new Driver(2L, RESTAURANT_ID, "Bob", "222-222-2222", true);
    Driver d3 = new Driver(3L, RESTAURANT_ID, "Charlie", "333-333-3333", true);

    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d1, d2, d3));
    when(driverService.isAvailable(1L)).thenReturn(true);
    when(driverService.isAvailable(2L)).thenReturn(false);
    when(driverService.isAvailable(3L)).thenReturn(true);

    Queue<Driver> readyDrivers = mgr.getReadyDrivers(2);

    assertEquals(2, readyDrivers.size());
    assertTrue(readyDrivers.contains(d1));
    assertTrue(readyDrivers.contains(d3));
    assertFalse(readyDrivers.contains(d2));
  }

  @Test
  void getReadyDrivers_returnsFewerIfNotEnoughAvailable() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    Driver d1 = new Driver(1L, RESTAURANT_ID, "Alice", "111-111-1111", true);
    Driver d2 = new Driver(2L, RESTAURANT_ID, "Bob", "222-222-2222", true);

    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d1, d2));
    when(driverService.isAvailable(1L)).thenReturn(true);
    when(driverService.isAvailable(2L)).thenReturn(false);

    Queue<Driver> readyDrivers = mgr.getReadyDrivers(5);

    assertEquals(1, readyDrivers.size());
    assertTrue(readyDrivers.contains(d1));
  }

  @Test
  void getReadyDrivers_throwsIfMaxNonPositive() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, emptyBatches);

    assertDoesNotThrow(() -> mgr.getReadyDrivers(0));
    assertThrows(IllegalArgumentException.class, () -> mgr.getReadyDrivers(-1));
  }

  // --- Tests that require controlled batches ---

  @Test
  void checkExpiredBatches_movesExpiredCookedBatchToReady() {
    // Given: all orders are COOKED (ready)
    Instant now = Instant.now();
    Instant expiration = now.minusSeconds(10);
    Order order1 = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(20)));
    Order order2 = createOrder(2L, State.COOKED, now.minus(Duration.ofMinutes(3)),
        now.plus(Duration.ofMinutes(22)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(order2, order1), expiration));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    // Set up a driver so that the ready batch gets assigned and delay does NOT run
    Driver driver = new Driver(10L, RESTAURANT_ID, "D", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(driver));
    when(driverService.isAvailable(10L)).thenReturn(true);

    // Mock route and batch creation for the ready batch (both orders in one batch)
    RouteDirectionsResponse routeResp = createRouteResponse("poly", 300);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(orderService.createBatch(any(Batch.class))).thenReturn(100L);
    Batch createdBatch = new Batch(100L, driver.id, "poly", now, now.plusSeconds(300));
    when(orderService.getBatch(100L)).thenReturn(createdBatch);

    // Stub getOrder for orders after batch assignment (called by updateOrdersWithBatchId)
    when(orderService.getOrder(1L)).thenReturn(order1);
    when(orderService.getOrder(2L)).thenReturn(order2);

    // When
    mgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = mgr.getBatches();
    assertTrue(result.getTentativeBatches().isEmpty());
    assertTrue(result.getReadyBatches().isEmpty()); // batch was assigned, not left in ready
    assertEquals(1, result.getActiveBatches().size());
    assertEquals(createdBatch, result.getActiveBatches().get(0));

    verify(orderService, never()).updateOrderDeliveryTime(anyLong(), any());
    verify(orderService, never()).updateOrderCookedTime(anyLong(), any());
    verify(batchingAlgorithm, never()).addOrder(anyList(), any(), anyString());
  }

  @Test
  void checkExpiredBatches_handlesMixedCookedAndUncooked() {
    // Given: one COOKED, one not COOKED
    Instant now = Instant.now();
    Instant expiration = now.minusSeconds(5);
    Order cooked = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(2)),
        now.plus(Duration.ofMinutes(15)));
    Order uncooked = createOrder(2L, State.DRIVING, now.plus(Duration.ofMinutes(3)),
        now.plus(Duration.ofMinutes(18)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(new ArrayList<>(List.of(cooked, uncooked)), expiration));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Order updatedUncooked =
        createOrder(2L, State.DRIVING, uncooked.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(orderService.getOrder(2L)).thenReturn(updatedUncooked);
    // Cooked order will be in ready batch and may be delayed if no drivers
    when(orderService.getOrder(1L)).thenReturn(cooked);

    // Stub getReadyDrivers to empty so that ready batch is not assigned (delay runs)
    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = spyMgr.getBatches();
    assertTrue(result.getTentativeBatches().isEmpty());
    assertEquals(1, result.getReadyBatches().size());
    assertEquals(List.of(cooked), result.getReadyBatches().peek().getBatch());

    verifyOrderDelayed(uncooked, uncooked.deliveryTime, uncooked.cookedTime);
    verify(orderService).getOrder(2L);
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updatedUncooked,
        ADDRESS);
  }

  @Test
  void checkExpiredBatches_handlesAllUncooked() {
    // Given: all orders are not COOKED
    Instant now = Instant.now();
    Instant expiration = now.minusSeconds(5);
    Order uncooked1 = createOrder(1L, State.DRIVING, now.plus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(25)));
    Order uncooked2 = createOrder(2L, State.DELIVERED, now.plus(Duration.ofMinutes(2)),
        now.plus(Duration.ofMinutes(22)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(uncooked1, uncooked2), expiration));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Order updated1 =
        createOrder(1L, State.DRIVING, uncooked1.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked1.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    Order updated2 =
        createOrder(2L, State.DELIVERED, uncooked2.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked2.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(orderService.getOrder(1L)).thenReturn(updated1);
    when(orderService.getOrder(2L)).thenReturn(updated2);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = spyMgr.getBatches();
    verifyOrderDelayed(uncooked1, uncooked1.deliveryTime, uncooked1.cookedTime);
    verifyOrderDelayed(uncooked2, uncooked2.deliveryTime, uncooked2.cookedTime);
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updated1, ADDRESS);
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updated2, ADDRESS);
    assertTrue(result.getReadyBatches().isEmpty());
  }

  @Test
  void checkExpiredBatches_onlyProcessesExpiredBatches() {
    // Given: one expired, one future batch – added in descending order (future first, expired last)
    Instant now = Instant.now();
    Instant expired = now.minusSeconds(10);
    Instant future = now.plusSeconds(30);

    Order expiredOrder = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(1)),
        now.plus(Duration.ofMinutes(10)));
    Order futureOrder = createOrder(2L, State.COOKING, now.plus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(20)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(futureOrder), future)); // later expiration first
    tentative.add(createTentativeBatch(List.of(expiredOrder), expired)); // earlier expiration last
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // Stub getOrder for expired order (will be in ready batch and delayed)
    when(orderService.getOrder(1L)).thenReturn(expiredOrder);

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = spyMgr.getBatches();
    assertEquals(1, result.getTentativeBatches().size());
    assertEquals(futureOrder, result.getTentativeBatches().get(0).getBatch().get(0));
    assertEquals(0, result.getActiveBatches().size());
    assertEquals(1, result.getReadyBatches().size());
    assertEquals(expiredOrder, result.getReadyBatches().peek().getBatch().get(0));
  }

  @Test
  void assignReadyBatchesToDrivers_assignsToAvailableDrivers() {
    // Given: orders in ready batch are COOKED
    Instant now = Instant.now();
    Order o1 = createOrder(1L, State.COOKED, now, now.plus(Duration.ofMinutes(10)));
    Order o2 = createOrder(2L, State.COOKED, now, now.plus(Duration.ofMinutes(12)));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(List.of(o1)));
    ready.add(new ReadyBatch(List.of(o2)));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Driver d1 = new Driver(10L, RESTAURANT_ID, "D1", "", true);
    Driver d2 = new Driver(11L, RESTAURANT_ID, "D2", "", true);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>(List.of(d1, d2))).when(spyMgr).getReadyDrivers(anyInt());

    RouteDirectionsResponse routeResp = createRouteResponse("polyline", 600);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(orderService.createBatch(any(Batch.class))).thenReturn(100L, 101L);

    Batch batch1 = new Batch(100L, d1.id, "poly1", now, now.plusSeconds(600));
    Batch batch2 = new Batch(101L, d2.id, "poly2", now, now.plusSeconds(600));
    when(orderService.getBatch(100L)).thenReturn(batch1);
    when(orderService.getBatch(101L)).thenReturn(batch2);

    // Stub getOrder for orders after batch assignment
    when(orderService.getOrder(1L)).thenReturn(o1);
    when(orderService.getOrder(2L)).thenReturn(o2);

    Consumer<Batch> becomeActiveListener = mock(Consumer.class);
    spyMgr.onBatchBecomeActive(becomeActiveListener);
    Consumer<Batches> changeListener = mock(Consumer.class);
    spyMgr.onBatchesChange(changeListener);

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = spyMgr.getBatches();
    assertTrue(result.getReadyBatches().isEmpty());
    assertEquals(2, result.getActiveBatches().size());

    verify(orderService).setOrderBatchId(1L, 100L);
    verify(orderService).setOrderBatchId(2L, 101L);
    verify(orderService, times(2)).getOrder(anyLong());
    verify(orderService, times(2)).createBatch(any(Batch.class));
    verify(becomeActiveListener, times(2)).accept(any(Batch.class));
    verify(changeListener, times(2)).accept(any(Batches.class));
  }

  @Test
  void assignReadyBatchesToDrivers_noDriversLeavesBatches() {
    // Given: order in ready batch is COOKED
    Instant now = Instant.now();
    Order o1 = createOrder(1L, State.COOKED, now, now.plus(Duration.ofMinutes(10)));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(List.of(o1)));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // Stub getOrder for order (will be delayed)
    when(orderService.getOrder(1L)).thenReturn(o1);

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = spyMgr.getBatches();
    assertEquals(1, result.getReadyBatches().size());
    assertTrue(result.getActiveBatches().isEmpty());

    verify(orderService, never()).setOrderBatchId(anyLong(), anyLong());
    verify(orderService, never()).createBatch(any());
  }

  @Test
  void delayRemainingReadyBatches_updatesDeliveryTimes() {
    // Given: order in ready batch is COOKED
    Instant originalDelivery = Instant.now().plusSeconds(300);
    Instant cookedTime = Instant.now();
    Order o1 = createOrder(1L, State.COOKED, cookedTime, originalDelivery);

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(List.of(o1)));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Order updatedOrder =
        createOrder(1L, State.COOKED, o1.cookedTime, originalDelivery.plusMillis(UPDATE_MILLIS));
    when(orderService.getOrder(1L)).thenReturn(updatedOrder);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    verify(orderService).updateOrderDeliveryTime(eq(1L), instantCaptor.capture());
    assertEquals(originalDelivery.plusMillis(UPDATE_MILLIS), instantCaptor.getValue());

    Batches result = spyMgr.getBatches();
    assertEquals(updatedOrder, result.getReadyBatches().peek().getBatch().get(0));
  }

  @Test
  void onBatchesChange_listenerInvokedWhenBatchBecomesActive() {
    // To trigger a change, we need a batch to become active (driver available)
    Instant now = Instant.now();
    Order o = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(1)),
        now.plus(Duration.ofMinutes(5)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(o), now.minusSeconds(10))); // expired
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    // Provide a driver so that ready batch gets assigned
    Driver d = new Driver(10L, RESTAURANT_ID, "D", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d));
    when(driverService.isAvailable(10L)).thenReturn(true);

    // Mock route and batch creation
    RouteDirectionsResponse routeResp = createRouteResponse("poly", 120);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(orderService.createBatch(any(Batch.class))).thenReturn(100L);
    when(orderService.getBatch(100L))
        .thenReturn(new Batch(100L, d.id, "poly", now, now.plusSeconds(120)));

    // Stub getOrder for the order after assignment
    when(orderService.getOrder(1L)).thenReturn(o);

    Consumer<Batches> listener = mock(Consumer.class);
    mgr.onBatchesChange(listener);

    // When
    mgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    verify(listener).accept(any(Batches.class));
  }

  @Test
  void onBatchBecomeActive_listenerInvokedWhenBatchActivated() {
    // Given
    Instant now = Instant.now();
    Order o = createOrder(1L, State.COOKED, now, now.plus(Duration.ofMinutes(5)));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(List.of(o)));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Driver d = new Driver(10L, RESTAURANT_ID, "D", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d));
    when(driverService.isAvailable(10L)).thenReturn(true);

    RouteDirectionsResponse routeResp = createRouteResponse("poly", 120);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(orderService.createBatch(any(Batch.class))).thenReturn(100L);
    when(orderService.getBatch(100L))
        .thenReturn(new Batch(100L, d.id, "poly", now, now.plusSeconds(120)));

    // Stub getOrder for order after assignment
    when(orderService.getOrder(1L)).thenReturn(o);

    Consumer<Batch> listener = mock(Consumer.class);
    mgr.onBatchBecomeActive(listener);

    // When
    mgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    verify(listener).accept(any(Batch.class));
  }

  @Test
  void removeUncookedOrders_handlesAllOrderStates() {
    // Given
    Instant now = Instant.now();
    Order delivered = createOrder(1L, State.DELIVERED, now.plus(Duration.ofMinutes(2)),
        now.plus(Duration.ofMinutes(10)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(new ArrayList<>(List.of(delivered)), now.minusSeconds(5)));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Order updatedDelivered =
        createOrder(1L, State.DELIVERED, delivered.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            delivered.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(orderService.getOrder(1L)).thenReturn(updatedDelivered);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    verify(orderService).updateOrderDeliveryTime(eq(1L), any());
    verify(orderService).updateOrderCookedTime(eq(1L), any());
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updatedDelivered,
        ADDRESS);
    assertTrue(spyMgr.getBatches().getReadyBatches().isEmpty());
  }

  @Test
  void delayOrder_updatesBothTimes() {
    // Given
    Order order = createOrder(1L, State.DRIVING, Instant.parse("2025-01-01T10:00:00Z"),
        Instant.parse("2025-01-01T10:30:00Z"));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(order), Instant.now().minusSeconds(10)));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Order updatedOrder =
        createOrder(1L, State.DRIVING, order.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            order.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(orderService.getOrder(1L)).thenReturn(updatedOrder);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    // When
    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    verify(orderService).updateOrderDeliveryTime(eq(1L), instantCaptor.capture());
    assertEquals(order.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
        instantCaptor.getValue());

    verify(orderService).updateOrderCookedTime(eq(1L), instantCaptor.capture());
    assertEquals(order.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC), instantCaptor.getValue());
  }

  @Test
  void checkExpiredBatches_fullFlow() {
    // Given: two tentative batches – one expired (mixed), one future – added in descending order
    Instant now = Instant.now();

    Order cooked1 = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(10)));
    Order uncooked1 = createOrder(2L, State.DRIVING, now.plus(Duration.ofMinutes(2)),
        now.plus(Duration.ofMinutes(15)));
    Order cooked2 = createOrder(3L, State.COOKING, now.plus(Duration.ofMinutes(1)),
        now.plus(Duration.ofMinutes(20))); // stays in tentative

    List<TentativeBatch> tentative = new ArrayList<>();
    // Future batch first (later expiration)
    tentative.add(createTentativeBatch(List.of(cooked2), now.plusSeconds(30)));
    // Expired batch second (earlier expiration) – must be last for correct descending order
    tentative.add(
        createTentativeBatch(new ArrayList<>(List.of(uncooked1, cooked1)), now.minusSeconds(10)));

    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);

    Driver driver = new Driver(100L, RESTAURANT_ID, "Driver", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(driver));
    when(driverService.isAvailable(100L)).thenReturn(true);

    Order updatedUncooked =
        createOrder(2L, State.DRIVING, uncooked1.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked1.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(orderService.getOrder(2L)).thenReturn(updatedUncooked);

    RouteDirectionsResponse routeResp = createRouteResponse("poly", 300);
    when(routeService.getRouteDirections(eq(ADDRESS), eq(List.of(cooked1.destination)), eq(false)))
        .thenReturn(routeResp);
    when(orderService.createBatch(any(Batch.class))).thenReturn(200L);
    Batch createdBatch = new Batch(200L, driver.id, "poly", now, now.plusSeconds(300));
    when(orderService.getBatch(200L)).thenReturn(createdBatch);

    // Stub getOrder for cooked1 after assignment
    when(orderService.getOrder(1L)).thenReturn(cooked1);

    // Simulate batchingAlgorithm.addOrder actually adding the order to the list
    doAnswer(invocation -> {
      List<TentativeBatch> list = invocation.getArgument(0);
      Order order = invocation.getArgument(1);
      // Add the order as a new tentative batch with a far future expiration so it's not immediately
      // expired
      list.add(new TentativeBatch(List.of(order), Instant.now().plusSeconds(1000)));
      return null;
    }).when(batchingAlgorithm).addOrder(anyList(), any(Order.class), anyString());

    // When
    mgr.checkExpiredBatches(UPDATE_MILLIS);

    // Then
    Batches result = mgr.getBatches();
    assertEquals(2, result.getTentativeBatches().size()); // future + re-added uncooked
    assertTrue(result.getReadyBatches().isEmpty());
    assertEquals(1, result.getActiveBatches().size());
    assertEquals(createdBatch, result.getActiveBatches().get(0));

    List<TentativeBatch> resultTentative = result.getTentativeBatches();
    boolean foundUncooked =
        resultTentative.stream().flatMap(tb -> tb.getBatch().stream()).anyMatch(o -> o.id == 2L);
    assertTrue(foundUncooked);
    boolean foundCooked2 =
        resultTentative.stream().flatMap(tb -> tb.getBatch().stream()).anyMatch(o -> o.id == 3L);
    assertTrue(foundCooked2);

    verify(orderService).setOrderBatchId(1L, 200L);
    verify(orderService).getOrder(1L);
    verify(orderService).updateOrderDeliveryTime(eq(2L), any());
    verify(orderService).updateOrderCookedTime(eq(2L), any());
  }

  @Test
  void constructor_createsEmptyBatchesWhenNullPassed() {
    RestaurantBatchingManager mgr = new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher,
        batchingAlgorithm, routeService, orderService, driverService, restaurantService, null);
    Batches b = mgr.getBatches();
    assertTrue(b.getTentativeBatches().isEmpty());
    assertTrue(b.getReadyBatches().isEmpty());
    assertTrue(b.getActiveBatches().isEmpty());
  }

  @Test
  void constructor_usesProvidedBatches() {
    List<TentativeBatch> tb = new ArrayList<>();
    List<ReadyBatch> rb = new ArrayList<>();
    List<Batch> ab = new ArrayList<>();
    Batches customBatches = new Batches(tb, rb, ab);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, orderService, driverService, restaurantService, customBatches);
    assertSame(customBatches, mgr.getBatches());
  }
}
