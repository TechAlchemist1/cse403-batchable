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
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.RestaurantService;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.service.internal.RestaurantBatchingManager;
import com.batchable.backend.service.internal.RestaurantBatchingManager.Batches;
import com.batchable.backend.service.internal.RestaurantBatchingManager.ReadyBatch;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

/**
 * Unit tests for RestaurantBatchingManager using Mockito.
 *
 * This test class verifies: - Delegation of order operations (add, remove, update) to the batching
 * algorithm. - Handling of active, ready, and tentative batches during removal and update. -
 * Registration and invocation of batch change and activation listeners. - Correct behaviour of
 * checkExpiredBatches in various scenarios (mixed cooked/uncooked, only expired, full flow). -
 * Driver availability logic (getReadyDrivers). - Batch assignment and activation. - Delay of
 * remaining ready batches. - Constructor behaviour with null or custom Batches. - Publisher refresh
 * calls after batch processing.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantBatchingManagerTest {

  @Mock
  private OrderWebSocketPublisher publisher;
  @Mock
  private BatchingAlgorithm batchingAlgorithm;
  @Mock
  private RouteService routeService;
  @Mock
  private DbOrderService dbOrderService;
  @Mock
  private RestaurantService restaurantService;
  @Mock
  private DriverService driverService;

  @Captor
  private ArgumentCaptor<Instant> instantCaptor;

  private static final long RESTAURANT_ID = 1L;
  private static final String ADDRESS = "123 Main St";
  private static final long ADDITIONAL_COOK_TIME_SEC = 180;
  private static final long UPDATE_MILLIS = 60000;

  /** Creates an order with the given parameters, including an optional batchId. */
  private Order createOrder(long id, State state, Instant cookedTime, Instant deliveryTime,
      Long batchId) {
    return new Order(id, RESTAURANT_ID, "dest" + id, "[]", Instant.now(), deliveryTime, cookedTime,
        state, false, batchId);
  }

  /** Convenience overload when batchId is null. */
  private Order createOrder(long id, State state, Instant cookedTime, Instant deliveryTime) {
    return createOrder(id, state, cookedTime, deliveryTime, null);
  }

  /** Creates a tentative batch with orders sorted by delivery time (ascending). */
  private TentativeBatch createTentativeBatch(List<Order> orders, Instant expiration) {
    List<Order> sortedOrders = new ArrayList<>(orders);
    sortedOrders.sort(Comparator.comparing(o -> o.deliveryTime));
    return new TentativeBatch(sortedOrders, expiration);
  }

  /** Creates a RouteDirectionsResponse with the given polyline and duration. */
  private RouteDirectionsResponse createRouteResponse(String polyline, int durationSeconds) {
    RouteDirectionsResponse response = new RouteDirectionsResponse();
    response.setPolyline(polyline);
    response.setDurationSeconds(durationSeconds);
    return response;
  }

  /**
   * Verifies that an order has been delayed correctly: - deliveryTime and cookedTime are both
   * increased by ADDITIONAL_COOK_TIME_SEC.
   */
  private void verifyOrderDelayed(Order order, Instant originalDelivery, Instant originalCooked) {
    verify(dbOrderService).updateOrderDeliveryTime(eq(order.id), instantCaptor.capture());
    assertEquals(originalDelivery.plusSeconds(ADDITIONAL_COOK_TIME_SEC), instantCaptor.getValue());

    verify(dbOrderService).updateOrderCookedTime(eq(order.id), instantCaptor.capture());
    assertEquals(originalCooked.plusSeconds(ADDITIONAL_COOK_TIME_SEC), instantCaptor.getValue());
  }

  // --- Delegation tests for addOrder ---

  /** Verifies that addOrder delegates to the batching algorithm. */
  @Test
  void addOrder_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order = mock(Order.class);
    mgr.addOrder(order);
    verify(batchingAlgorithm).addOrder(emptyBatches.getTentativeBatches(), order, ADDRESS);
  }

  // --- Tests for removeOrder ---

  /**
   * When order is in an active batch, removeOrder should emit a batch change and not touch the
   * algorithm.
   */
  @Test
  void removeOrder_whenOrderInActiveBatch_emitsChangeAndDoesNotDelegateToAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order =
        createOrder(42L, State.DRIVING, Instant.now(), Instant.now().plusSeconds(300), 100L);
    when(dbOrderService.getOrder(42L)).thenReturn(order);

    Consumer<Long> changeListener = mock(Consumer.class);
    mgr.onBatchChange(changeListener);

    mgr.removeOrder(42L);

    verify(changeListener).accept(100L);
    verify(batchingAlgorithm, never()).removeOrder(anyList(), anyLong(), anyString());
  }

  /** When order is in a ready batch, removeOrder should remove it from that batch. */
  @Test
  void removeOrder_whenOrderInReadyBatch_removesFromReadyBatch() {
    Instant now = Instant.now();
    Order order = createOrder(42L, State.COOKED, now, now.plusSeconds(300));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(new ArrayList<>(List.of(order))));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    when(dbOrderService.getOrder(42L)).thenReturn(order);

    mgr.removeOrder(42L);

    assertTrue(mgr.getBatches().getReadyBatches().peek().getBatch().isEmpty());
    verify(batchingAlgorithm, never()).removeOrder(anyList(), anyLong(), anyString());
  }

  /** When order is in a tentative batch, removeOrder delegates to the algorithm. */
  @Test
  void removeOrder_whenOrderInTentativeBatch_delegatesToAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order = createOrder(42L, State.COOKING, Instant.now(), Instant.now().plusSeconds(300));
    when(dbOrderService.getOrder(42L)).thenReturn(order);

    mgr.removeOrder(42L);

    verify(batchingAlgorithm).removeOrder(emptyBatches.getTentativeBatches(), 42L, ADDRESS);
  }

  // --- Tests for updateOrder ---

  /** When order is in an active batch, updateOrder emits a batch change. */
  @Test
  void updateOrder_whenOrderInActiveBatch_emitsChange() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order =
        createOrder(42L, State.DRIVING, Instant.now(), Instant.now().plusSeconds(300), 100L);
    when(dbOrderService.getOrder(42L)).thenReturn(order);

    Consumer<Long> changeListener = mock(Consumer.class);
    mgr.onBatchChange(changeListener);

    mgr.updateOrder(42L, false);

    verify(changeListener).accept(100L);
    verify(batchingAlgorithm, never()).rebatchOrder(anyList(), any(), anyString());
    verify(batchingAlgorithm, never()).updateOrderInplace(anyList(), anyLong());
  }

  /** When order is in a ready batch, updateOrder updates it in place. */
  @Test
  void updateOrder_whenOrderInReadyBatch_updatesInPlace() {
    Instant now = Instant.now();
    Order originalOrder = createOrder(42L, State.COOKED, now, now.plusSeconds(300));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(new ArrayList<>(List.of(originalOrder))));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Order updatedOrder = createOrder(42L, State.COOKED, now, now.plusSeconds(350));
    when(dbOrderService.getOrder(42L)).thenReturn(updatedOrder);

    mgr.updateOrder(42L, false);

    assertEquals(updatedOrder, mgr.getBatches().getReadyBatches().peek().getBatch().get(0));
    verify(batchingAlgorithm, never()).rebatchOrder(anyList(), any(), anyString());
    verify(batchingAlgorithm, never()).updateOrderInplace(anyList(), anyLong());
  }

  /** When order is in a tentative batch and rebatchIfTentative is true, it rebatches. */
  @Test
  void updateOrder_whenOrderInTentativeAndRebatchTrue_rebatches() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order = createOrder(42L, State.COOKING, Instant.now(), Instant.now().plusSeconds(300));
    when(dbOrderService.getOrder(42L)).thenReturn(order);

    mgr.updateOrder(42L, true);

    verify(batchingAlgorithm).rebatchOrder(emptyBatches.getTentativeBatches(), order, ADDRESS);
  }

  /** When order is in a tentative batch and rebatchIfTentative is false, it updates in place. */
  @Test
  void updateOrder_whenOrderInTentativeAndRebatchFalse_updatesInPlace() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order = createOrder(42L, State.COOKING, Instant.now(), Instant.now().plusSeconds(300));
    when(dbOrderService.getOrder(42L)).thenReturn(order);

    mgr.updateOrder(42L, false);

    verify(batchingAlgorithm).updateOrderInplace(emptyBatches.getTentativeBatches(), 42L);
  }

  // --- rebatchTentativeOrder delegation ---

  /** Verifies that rebatchTentativeOrder delegates to the algorithm. */
  @Test
  void rebatchTentativeOrder_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Order order = mock(Order.class);
    mgr.rebatchTentativeOrder(order);
    verify(batchingAlgorithm).rebatchOrder(emptyBatches.getTentativeBatches(), order, ADDRESS);
  }

  // --- updateTentativeOrderInplace delegation ---

  /** Verifies that updateTentativeOrderInplace delegates to the algorithm. */
  @Test
  void updateTentativeOrderInplace_delegatesToBatchingAlgorithm() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    mgr.updateTentativeOrderInplace(42L);
    verify(batchingAlgorithm).updateOrderInplace(emptyBatches.getTentativeBatches(), 42L);
  }

  // --- getReadyDrivers tests ---

  /** Verifies that getReadyDrivers returns only available drivers, up to the requested maximum. */
  @Test
  void getReadyDrivers_returnsOnlyAvailableDrivers_upToMax() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

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

  /** Verifies that getReadyDrivers returns fewer if not enough drivers are available. */
  @Test
  void getReadyDrivers_returnsFewerIfNotEnoughAvailable() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    Driver d1 = new Driver(1L, RESTAURANT_ID, "Alice", "111-111-1111", true);
    Driver d2 = new Driver(2L, RESTAURANT_ID, "Bob", "222-222-2222", true);

    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d1, d2));
    when(driverService.isAvailable(1L)).thenReturn(true);
    when(driverService.isAvailable(2L)).thenReturn(false);

    Queue<Driver> readyDrivers = mgr.getReadyDrivers(5);

    assertEquals(1, readyDrivers.size());
    assertTrue(readyDrivers.contains(d1));
  }

  /** Verifies that getReadyDrivers throws for negative max and allows zero. */
  @Test
  void getReadyDrivers_throwsIfMaxNonPositive() {
    Batches emptyBatches = new Batches();
    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, emptyBatches);

    assertDoesNotThrow(() -> mgr.getReadyDrivers(0));
    assertThrows(IllegalArgumentException.class, () -> mgr.getReadyDrivers(-1));
  }

  // --- checkExpiredBatches tests ---

  /** Moves an expired batch with all cooked orders to ready, then assigns it to a driver. */
  @Test
  void checkExpiredBatches_movesExpiredCookedBatchToReady() {
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
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Driver driver = new Driver(10L, RESTAURANT_ID, "D", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(driver));
    when(driverService.isAvailable(10L)).thenReturn(true);

    RouteDirectionsResponse routeResp = createRouteResponse("poly", 300);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(dbOrderService.createBatch(any(Batch.class))).thenReturn(100L);
    Batch createdBatch = new Batch(100L, driver.id, "poly", now, now.plusSeconds(300));
    when(dbOrderService.getBatch(100L)).thenReturn(createdBatch);

    when(dbOrderService.getOrder(1L)).thenReturn(order1);
    when(dbOrderService.getOrder(2L)).thenReturn(order2);

    mgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = mgr.getBatches();
    assertTrue(result.getTentativeBatches().isEmpty());
    assertTrue(result.getReadyBatches().isEmpty()); // batch was assigned
    assertEquals(1, result.getActiveBatches().size());
    assertEquals(createdBatch, result.getActiveBatches().get(0));

    verify(dbOrderService, never()).updateOrderDeliveryTime(anyLong(), any());
    verify(dbOrderService, never()).updateOrderCookedTime(anyLong(), any());
    verify(batchingAlgorithm, never()).addOrder(anyList(), any(), anyString());
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /**
   * Expired batch with mixed cooked/uncooked: uncooked are delayed and re-added, cooked become
   * ready.
   */
  @Test
  void checkExpiredBatches_handlesMixedCookedAndUncooked() {
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
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Order updatedUncooked =
        createOrder(2L, State.DRIVING, uncooked.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(dbOrderService.getOrder(2L)).thenReturn(updatedUncooked);
    when(dbOrderService.getOrder(1L)).thenReturn(cooked);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = spyMgr.getBatches();
    assertTrue(result.getTentativeBatches().isEmpty());
    assertEquals(1, result.getReadyBatches().size());
    assertEquals(List.of(cooked), result.getReadyBatches().peek().getBatch());

    verifyOrderDelayed(uncooked, uncooked.deliveryTime, uncooked.cookedTime);
    verify(dbOrderService).getOrder(2L);
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updatedUncooked,
        ADDRESS);
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Expired batch with all uncooked: all are delayed and re-added, no ready batch remains. */
  @Test
  void checkExpiredBatches_handlesAllUncooked() {
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
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Order updated1 =
        createOrder(1L, State.DRIVING, uncooked1.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked1.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    Order updated2 =
        createOrder(2L, State.DELIVERED, uncooked2.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked2.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(dbOrderService.getOrder(1L)).thenReturn(updated1);
    when(dbOrderService.getOrder(2L)).thenReturn(updated2);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = spyMgr.getBatches();
    verifyOrderDelayed(uncooked1, uncooked1.deliveryTime, uncooked1.cookedTime);
    verifyOrderDelayed(uncooked2, uncooked2.deliveryTime, uncooked2.cookedTime);
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updated1, ADDRESS);
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updated2, ADDRESS);
    assertTrue(result.getReadyBatches().isEmpty());
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Only expired batches are processed; future batches remain untouched. */
  @Test
  void checkExpiredBatches_onlyProcessesExpiredBatches() {
    Instant now = Instant.now();
    Instant expired = now.minusSeconds(10);
    Instant future = now.plusSeconds(30);

    Order expiredOrder = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(1)),
        now.plus(Duration.ofMinutes(10)));
    Order futureOrder = createOrder(2L, State.COOKING, now.plus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(20)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(futureOrder), future));
    tentative.add(createTentativeBatch(List.of(expiredOrder), expired));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    when(dbOrderService.getOrder(1L)).thenReturn(expiredOrder);

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = spyMgr.getBatches();
    assertEquals(1, result.getTentativeBatches().size());
    assertEquals(futureOrder, result.getTentativeBatches().get(0).getBatch().get(0));
    assertEquals(0, result.getActiveBatches().size());
    assertEquals(1, result.getReadyBatches().size());
    assertEquals(expiredOrder, result.getReadyBatches().peek().getBatch().get(0));
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Assigns ready batches to available drivers, activates them, and notifies listeners. */
  @Test
  void assignReadyBatchesToDrivers_assignsToAvailableDrivers() {
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
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Driver d1 = new Driver(10L, RESTAURANT_ID, "D1", "", true);
    Driver d2 = new Driver(11L, RESTAURANT_ID, "D2", "", true);

    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d1, d2));
    when(driverService.isAvailable(10L)).thenReturn(true);
    when(driverService.isAvailable(11L)).thenReturn(true);

    RouteDirectionsResponse routeResp = createRouteResponse("polyline", 600);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(dbOrderService.createBatch(any(Batch.class))).thenReturn(100L, 101L);

    Batch batch1 = new Batch(100L, d1.id, "poly1", now, now.plusSeconds(600));
    Batch batch2 = new Batch(101L, d2.id, "poly2", now, now.plusSeconds(600));
    when(dbOrderService.getBatch(100L)).thenReturn(batch1);
    when(dbOrderService.getBatch(101L)).thenReturn(batch2);

    when(dbOrderService.getOrder(1L)).thenReturn(o1);
    when(dbOrderService.getOrder(2L)).thenReturn(o2);

    Consumer<Long> becomeActiveListener = mock(Consumer.class);
    mgr.onBatchBecomeActive(becomeActiveListener);
    Consumer<Long> changeListener = mock(Consumer.class);
    mgr.onBatchChange(changeListener);

    mgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = mgr.getBatches();
    assertTrue(result.getReadyBatches().isEmpty());
    assertEquals(2, result.getActiveBatches().size());

    verify(dbOrderService).setOrderBatchId(1L, 100L);
    verify(dbOrderService).advanceOrderState(1L);
    verify(dbOrderService).setOrderBatchId(2L, 101L);
    verify(dbOrderService).advanceOrderState(2L);
    verify(dbOrderService, times(2)).getOrder(anyLong());
    verify(dbOrderService, times(2)).createBatch(any(Batch.class));
    verify(becomeActiveListener, times(2)).accept(anyLong());
    verify(changeListener, never()).accept(anyLong()); // change listener not called during
                                                       // activation
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** When no drivers are available, ready batches are left and not assigned. */
  @Test
  void assignReadyBatchesToDrivers_noDriversLeavesBatches() {
    Instant now = Instant.now();
    Order o1 = createOrder(1L, State.COOKED, now, now.plus(Duration.ofMinutes(10)));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(List.of(o1)));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    when(dbOrderService.getOrder(1L)).thenReturn(o1);

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = spyMgr.getBatches();
    assertEquals(1, result.getReadyBatches().size());
    assertTrue(result.getActiveBatches().isEmpty());

    verify(dbOrderService, never()).setOrderBatchId(anyLong(), anyLong());
    verify(dbOrderService, never()).createBatch(any());
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Unassigned ready batches have their delivery times delayed. */
  @Test
  void delayRemainingReadyBatches_updatesDeliveryTimes() {
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
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Order updatedOrder =
        createOrder(1L, State.COOKED, o1.cookedTime, originalDelivery.plusMillis(UPDATE_MILLIS));
    when(dbOrderService.getOrder(1L)).thenReturn(updatedOrder);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    verify(dbOrderService).updateOrderDeliveryTime(eq(1L), instantCaptor.capture());
    assertEquals(originalDelivery.plusMillis(UPDATE_MILLIS), instantCaptor.getValue());

    Batches result = spyMgr.getBatches();
    assertEquals(updatedOrder, result.getReadyBatches().peek().getBatch().get(0));
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Batch become‑active listener is invoked when a batch is activated. */
  @Test
  void onBatchBecomeActive_listenerInvokedWhenBatchActivated() {
    Instant now = Instant.now();
    Order o = createOrder(1L, State.COOKED, now, now.plus(Duration.ofMinutes(5)));

    List<TentativeBatch> tentative = new ArrayList<>();
    Queue<ReadyBatch> ready = new LinkedList<>();
    ready.add(new ReadyBatch(List.of(o)));
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Driver d = new Driver(10L, RESTAURANT_ID, "D", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(d));
    when(driverService.isAvailable(10L)).thenReturn(true);

    RouteDirectionsResponse routeResp = createRouteResponse("poly", 120);
    when(routeService.getRouteDirections(eq(ADDRESS), anyList(), eq(false))).thenReturn(routeResp);
    when(dbOrderService.createBatch(any(Batch.class))).thenReturn(100L);
    when(dbOrderService.getBatch(100L))
        .thenReturn(new Batch(100L, d.id, "poly", now, now.plusSeconds(120)));

    when(dbOrderService.getOrder(1L)).thenReturn(o);

    Consumer<Long> listener = mock(Consumer.class);
    mgr.onBatchBecomeActive(listener);

    mgr.checkExpiredBatches(UPDATE_MILLIS);

    verify(listener).accept(100L);
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** RemoveUncookedOrders handles all order states and delays them appropriately. */
  @Test
  void removeUncookedOrders_handlesAllOrderStates() {
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
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Order updatedDelivered =
        createOrder(1L, State.DELIVERED, delivered.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            delivered.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(dbOrderService.getOrder(1L)).thenReturn(updatedDelivered);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    verify(dbOrderService).updateOrderDeliveryTime(eq(1L), any(Instant.class));
    verify(dbOrderService).updateOrderCookedTime(eq(1L), any(Instant.class));
    verify(batchingAlgorithm).addOrder(customBatches.getTentativeBatches(), updatedDelivered,
        ADDRESS);
    assertTrue(spyMgr.getBatches().getReadyBatches().isEmpty());
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** delayOrder updates both cooked and delivery times by the additional time. */
  @Test
  void delayOrder_updatesBothTimes() {
    Order order = createOrder(1L, State.DRIVING, Instant.parse("2025-01-01T10:00:00Z"),
        Instant.parse("2025-01-01T10:30:00Z"));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(order), Instant.now().minusSeconds(10)));
    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Order updatedOrder =
        createOrder(1L, State.DRIVING, order.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            order.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(dbOrderService.getOrder(1L)).thenReturn(updatedOrder);

    RestaurantBatchingManager spyMgr = spy(mgr);
    doReturn(new LinkedList<>()).when(spyMgr).getReadyDrivers(anyInt());

    spyMgr.checkExpiredBatches(UPDATE_MILLIS);

    verify(dbOrderService).updateOrderDeliveryTime(eq(1L), instantCaptor.capture());
    assertEquals(order.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
        instantCaptor.getValue());

    verify(dbOrderService).updateOrderCookedTime(eq(1L), instantCaptor.capture());
    assertEquals(order.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC), instantCaptor.getValue());
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Full end‑to‑end test with multiple batches, expired and future, and driver assignment. */
  @Test
  void checkExpiredBatches_fullFlow() {
    Instant now = Instant.now();

    Order cooked1 = createOrder(1L, State.COOKED, now.minus(Duration.ofMinutes(5)),
        now.plus(Duration.ofMinutes(10)));
    Order uncooked1 = createOrder(2L, State.DRIVING, now.plus(Duration.ofMinutes(2)),
        now.plus(Duration.ofMinutes(15)));
    Order cooked2 = createOrder(3L, State.COOKING, now.plus(Duration.ofMinutes(1)),
        now.plus(Duration.ofMinutes(20)));

    List<TentativeBatch> tentative = new ArrayList<>();
    tentative.add(createTentativeBatch(List.of(cooked2), now.plusSeconds(30)));
    tentative.add(
        createTentativeBatch(new ArrayList<>(List.of(uncooked1, cooked1)), now.minusSeconds(10)));

    Queue<ReadyBatch> ready = new LinkedList<>();
    List<Batch> active = new ArrayList<>();
    Batches customBatches = new Batches(tentative, ready, active);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);

    Driver driver = new Driver(100L, RESTAURANT_ID, "Driver", "", true);
    when(restaurantService.getRestaurantDrivers(RESTAURANT_ID)).thenReturn(List.of(driver));
    when(driverService.isAvailable(100L)).thenReturn(true);

    Order updatedUncooked =
        createOrder(2L, State.DRIVING, uncooked1.cookedTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC),
            uncooked1.deliveryTime.plusSeconds(ADDITIONAL_COOK_TIME_SEC));
    when(dbOrderService.getOrder(2L)).thenReturn(updatedUncooked);

    RouteDirectionsResponse routeResp = createRouteResponse("poly", 300);
    when(routeService.getRouteDirections(eq(ADDRESS), eq(List.of(cooked1.destination)), eq(false)))
        .thenReturn(routeResp);
    when(dbOrderService.createBatch(any(Batch.class))).thenReturn(200L);
    Batch createdBatch = new Batch(200L, driver.id, "poly", now, now.plusSeconds(300));
    when(dbOrderService.getBatch(200L)).thenReturn(createdBatch);

    when(dbOrderService.getOrder(1L)).thenReturn(cooked1);

    doAnswer(invocation -> {
      List<TentativeBatch> list = invocation.getArgument(0);
      Order order = invocation.getArgument(1);
      list.add(new TentativeBatch(List.of(order), Instant.now().plusSeconds(1000)));
      return null;
    }).when(batchingAlgorithm).addOrder(anyList(), any(Order.class), anyString());

    mgr.checkExpiredBatches(UPDATE_MILLIS);

    Batches result = mgr.getBatches();
    assertEquals(2, result.getTentativeBatches().size());
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

    verify(dbOrderService).setOrderBatchId(1L, 200L);
    verify(dbOrderService).advanceOrderState(1L);
    verify(dbOrderService).getOrder(1L);
    verify(dbOrderService).updateOrderDeliveryTime(eq(2L), any());
    verify(dbOrderService).updateOrderCookedTime(eq(2L), any());
    verify(publisher).refreshOrderData(RESTAURANT_ID);
  }

  /** Constructor creates empty Batches when null is passed. */
  @Test
  void constructor_createsEmptyBatchesWhenNullPassed() {
    RestaurantBatchingManager mgr = new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher,
        batchingAlgorithm, routeService, dbOrderService, driverService, restaurantService, null);
    Batches b = mgr.getBatches();
    assertTrue(b.getTentativeBatches().isEmpty());
    assertTrue(b.getReadyBatches().isEmpty());
    assertTrue(b.getActiveBatches().isEmpty());
  }

  /** Constructor uses the provided Batches instance. */
  @Test
  void constructor_usesProvidedBatches() {
    List<TentativeBatch> tb = new ArrayList<>();
    List<ReadyBatch> rb = new ArrayList<>();
    List<Batch> ab = new ArrayList<>();
    Batches customBatches = new Batches(tb, rb, ab);

    RestaurantBatchingManager mgr =
        new RestaurantBatchingManager(RESTAURANT_ID, ADDRESS, publisher, batchingAlgorithm,
            routeService, dbOrderService, driverService, restaurantService, customBatches);
    assertSame(customBatches, mgr.getBatches());
  }
}
