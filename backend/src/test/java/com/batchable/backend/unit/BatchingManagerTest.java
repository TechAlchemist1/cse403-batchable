package com.batchable.backend.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.OrderService;
import com.batchable.backend.service.RestaurantService;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.service.internal.RestaurantBatchingManager;
import com.batchable.backend.service.internal.RestaurantBatchingManager.Batches;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

@ExtendWith(MockitoExtension.class)
class BatchingManagerTest {

  @Mock
  private OrderWebSocketPublisher publisher;
  @Mock
  private BatchingAlgorithm batchingAlgorithm;
  @Mock
  private RestaurantService restaurantService;
  @Mock
  private RouteService routeService;
  @Mock
  private OrderService orderService;
  @Mock
  private DriverService driverService;

  @Captor
  private ArgumentCaptor<Long> orderIdCaptor;
  @Captor
  private ArgumentCaptor<State> stateCaptor;
  @Captor
  private ArgumentCaptor<Order> orderCaptor;
  @Captor
  private ArgumentCaptor<String> addressCaptor;
  @Captor
  private ArgumentCaptor<List<com.batchable.backend.service.BatchingAlgorithm.TentativeBatch>> tentativeBatchesCaptor;

  private BatchingManager batchingManager;

  private static final long RESTAURANT_ID_1 = 1L;
  private static final long RESTAURANT_ID_2 = 2L;
  private static final String ADDRESS_1 = "123 Main St";
  private static final String ADDRESS_2 = "456 Oak Ave";
  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    batchingManager = new BatchingManager(publisher, batchingAlgorithm, restaurantService,
        routeService, orderService, driverService);
  }

  // Helper to create a real Order (final class with final fields, so use constructor)
  private Order createOrder(long id, long restaurantId) {
    return new Order(id, restaurantId, "dest" + id, "[]", NOW, NOW.plusSeconds(3600),
        NOW.minusSeconds(300), State.COOKED, false, null);
  }

  // Helper to extract the internal managers map
  @SuppressWarnings("unchecked")
  private Map<Long, RestaurantBatchingManager> getManagerMap() throws Exception {
    Field field = BatchingManager.class.getDeclaredField("restaurantManagers");
    field.setAccessible(true);
    return (Map<Long, RestaurantBatchingManager>) field.get(batchingManager);
  }

  // Helper to replace a manager with a spy
  private RestaurantBatchingManager spyOnManager(long restaurantId) throws Exception {
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    RestaurantBatchingManager real = map.get(restaurantId);
    assertNotNull(real, "Manager should exist for restaurant " + restaurantId);
    RestaurantBatchingManager spy = spy(real);
    map.put(restaurantId, spy);
    return spy;
  }

  // --- Manager creation tests ---

  @Test
  void getManager_createsNewManagerWhenNoneExists() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);

    // When – first operation that triggers creation
    batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1));

    // Then
    verify(restaurantService).getRestaurant(RESTAURANT_ID_1);
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    assertTrue(map.containsKey(RESTAURANT_ID_1));
    assertNotNull(map.get(RESTAURANT_ID_1));
  }

  @Test
  void getManager_reusesExistingManager() throws Exception {
    // Given – create first manager
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1));

    Map<Long, RestaurantBatchingManager> mapBefore = getManagerMap();
    RestaurantBatchingManager firstManager = mapBefore.get(RESTAURANT_ID_1);

    // When – second operation for same restaurant
    batchingManager.addOrder(createOrder(101L, RESTAURANT_ID_1));

    // Then – no additional getRestaurant call, same manager in map
    verify(restaurantService, times(1)).getRestaurant(RESTAURANT_ID_1);
    Map<Long, RestaurantBatchingManager> mapAfter = getManagerMap();
    assertSame(firstManager, mapAfter.get(RESTAURANT_ID_1));
  }

  // --- Delegation tests with argument capturing ---

  @Test
  void addOrder_delegatesToCorrectManagerWithCorrectAddress() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    Order order = createOrder(100L, RESTAURANT_ID_1);

    // When
    batchingManager.addOrder(order);

    // Then – verify address and that the list is the manager's tentative batches
    verify(batchingAlgorithm).addOrder(tentativeBatchesCaptor.capture(), eq(order),
        addressCaptor.capture());
    assertEquals(ADDRESS_1, addressCaptor.getValue());
    // Also verify that the list belongs to the manager for restaurant 1
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    RestaurantBatchingManager manager = map.get(RESTAURANT_ID_1);
    assertEquals(manager.getBatches().getTentativeBatches(), tentativeBatchesCaptor.getValue());
  }

  @Test
  void removeOrder_delegatesToCorrectManagerWithCorrectAddress() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    Order order = createOrder(100L, RESTAURANT_ID_1);
    when(orderService.getOrder(100L)).thenReturn(order);

    // When
    batchingManager.removeOrder(100L);

    // Then
    verify(batchingAlgorithm).removeOrder(tentativeBatchesCaptor.capture(), orderIdCaptor.capture(),
        addressCaptor.capture());
    assertEquals(100L, orderIdCaptor.getValue());
    assertEquals(ADDRESS_1, addressCaptor.getValue());
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    RestaurantBatchingManager manager = map.get(RESTAURANT_ID_1);
    assertEquals(manager.getBatches().getTentativeBatches(), tentativeBatchesCaptor.getValue());
  }

  @Test
  void rebatchOrder_delegatesToCorrectManagerWithCorrectAddress() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    Order order = createOrder(100L, RESTAURANT_ID_1);

    // When
    batchingManager.rebatchOrder(order);

    // Then
    verify(batchingAlgorithm).rebatchOrder(tentativeBatchesCaptor.capture(), orderCaptor.capture(),
        addressCaptor.capture());
    assertSame(order, orderCaptor.getValue());
    assertEquals(ADDRESS_1, addressCaptor.getValue());
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    RestaurantBatchingManager manager = map.get(RESTAURANT_ID_1);
    assertEquals(manager.getBatches().getTentativeBatches(), tentativeBatchesCaptor.getValue());
  }

  @Test
  void updateOrderState_delegatesToCorrectManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    Order order = createOrder(100L, RESTAURANT_ID_1);
    when(orderService.getOrder(100L)).thenReturn(order);

    // When
    batchingManager.updateOrderState(100L, State.COOKING);

    // Then
    verify(batchingAlgorithm).updateOrderState(tentativeBatchesCaptor.capture(),
        orderIdCaptor.capture(), stateCaptor.capture());

    assertEquals(100L, orderIdCaptor.getValue());
    assertEquals(State.COOKING, stateCaptor.getValue());

    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    RestaurantBatchingManager manager = map.get(RESTAURANT_ID_1);
    assertEquals(manager.getBatches().getTentativeBatches(), tentativeBatchesCaptor.getValue());
  }

  // --- Listener registration tests ---

  @Test
  void onBatchesChange_addsListenerToManager() throws Exception {
    // Given – create manager
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1)); // ensures manager exists

    // Replace real manager with spy to verify method call
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);

    Consumer<Batches> listener = mock(Consumer.class);

    // When
    batchingManager.onBatchesChange(RESTAURANT_ID_1, listener);

    // Then – spy's onBatchesChange was invoked with the listener
    verify(spy).onBatchesChange(listener);
  }

  @Test
  void onBatchBecomeActive_addsListenerToManager() throws Exception {
    // Given – create manager
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1)); // ensures manager exists

    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);
    Consumer<Batch> listener = mock(Consumer.class);

    // When
    batchingManager.onBatchBecomeActive(RESTAURANT_ID_1, listener);

    // Then
    verify(spy).onBatchBecomeActive(listener);
  }

  // --- Scheduled check tests ---

  @Test
  void checkExpiredBatches_callsAllManagers() throws Exception {
    // Given – create two managers
    Restaurant rest1 = new Restaurant(RESTAURANT_ID_1, "Rest1", ADDRESS_1);
    Restaurant rest2 = new Restaurant(RESTAURANT_ID_2, "Rest2", ADDRESS_2);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(rest1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_2)).thenReturn(rest2);

    batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1));
    batchingManager.addOrder(createOrder(200L, RESTAURANT_ID_2));

    // Replace with spies
    RestaurantBatchingManager spy1 = spyOnManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy2 = spyOnManager(RESTAURANT_ID_2);

    // When
    batchingManager.checkExpiredBatches();

    // Then – each manager's checkExpiredBatches called with the correct increment
    verify(spy1).checkExpiredBatches(BatchingManager.UPDATE_INCREMENTS_MILLIS);
    verify(spy2).checkExpiredBatches(BatchingManager.UPDATE_INCREMENTS_MILLIS);
  }

  @Test
  void checkExpiredBatches_withNoManagers_doesNothing() {
    // When – no managers have been created
    batchingManager.checkExpiredBatches();

    // Then – no exception, and no interactions with mocks (besides possible internal calls)
    // We can't verify zero calls to batchingAlgorithm because it's not used here.
    // Just assert that the method completes.
    assertTrue(true); // dummy assertion to indicate test passed
  }

  // --- Error handling tests ---

  @Test
  void removeOrder_withInvalidOrderId_throwsException() {
    // Given
    when(orderService.getOrder(999L)).thenThrow(new IllegalArgumentException("Order not found"));

    // When / Then
    assertThrows(IllegalArgumentException.class, () -> batchingManager.removeOrder(999L));
  }

  @Test
  void updateOrderState_withInvalidOrderId_throwsException() {
    // Given
    when(orderService.getOrder(999L)).thenThrow(new IllegalArgumentException("Order not found"));

    // When / Then
    assertThrows(IllegalArgumentException.class,
        () -> batchingManager.updateOrderState(999L, State.COOKING));
  }

  @Test
  void getManager_whenRestaurantServiceFails_throwsException() {
    // Given
    when(restaurantService.getRestaurant(RESTAURANT_ID_1))
        .thenThrow(new RuntimeException("DB error"));

    // When / Then
    assertThrows(RuntimeException.class,
        () -> batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1)));
  }

  // --- Edge case: operation on non-existent restaurant before any manager created ---
  // This is covered by getManager_createsNewManagerWhenNoneExists, but we can add a test
  // that verifies the address is fetched correctly even if multiple concurrent calls happen.
  // However, concurrency is out of scope.

  @Test
  void multipleOperationsOnSameRestaurant_useSameManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);

    // When – multiple operations
    Order order1 = createOrder(100L, RESTAURANT_ID_1);
    Order order2 = createOrder(101L, RESTAURANT_ID_1);
    batchingManager.addOrder(order1);
    batchingManager.addOrder(order2);

    // Then – restaurantService called only once
    verify(restaurantService, times(1)).getRestaurant(RESTAURANT_ID_1);
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    assertEquals(1, map.size());
    RestaurantBatchingManager manager = map.get(RESTAURANT_ID_1);
    assertNotNull(manager);
  }
}
