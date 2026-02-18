package com.batchable.backend.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import com.batchable.backend.service.RestaurantService;
import com.batchable.backend.service.RouteService;
import com.batchable.backend.service.internal.RestaurantBatchingManager;
import com.batchable.backend.websocket.OrderWebSocketPublisher;

/**
 * Unit tests for BatchingManager using Mockito.
 *
 * This test class verifies: - Manager creation (addManager) and duplication checks. - Address
 * updates delegate to the correct internal manager. - Manager removal. - Delegation of order
 * operations (add, remove, update) to the appropriate RestaurantBatchingManager instance. -
 * Listener registration (onBatchesChange, onBatchBecomeActive). - Scheduled batch expiration check
 * delegates to all managers. - Error handling for invalid orders or restaurant service failures. -
 * Reuse of the same manager instance for multiple operations on the same restaurant.
 */
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
  private DbOrderService dbOrderService;
  @Mock
  private DriverService driverService;

  @Captor
  private ArgumentCaptor<Long> longCaptor;
  @Captor
  private ArgumentCaptor<Order> orderCaptor;
  @Captor
  private ArgumentCaptor<Boolean> booleanCaptor;

  private BatchingManager batchingManager;

  private static final long RESTAURANT_ID_1 = 1L;
  private static final long RESTAURANT_ID_2 = 2L;
  private static final String ADDRESS_1 = "123 Main St";
  private static final String ADDRESS_2 = "456 Oak Ave";
  private static final Instant NOW = Instant.now();

  @BeforeEach
  void setUp() {
    batchingManager = new BatchingManager(publisher, batchingAlgorithm, restaurantService,
        routeService, dbOrderService, driverService);
  }

  /** Creates a simple Order with the given ID and restaurant ID, other fields default. */
  private Order createOrder(long id, long restaurantId) {
    return new Order(id, restaurantId, "dest" + id, "[]", NOW, NOW.plusSeconds(3600),
        NOW.minusSeconds(300), State.COOKED, false, null);
  }

  /**
   * Uses reflection to access the private restaurantManagers map.
   * 
   * @return the internal map of restaurant ID to RestaurantBatchingManager
   */
  @SuppressWarnings("unchecked")
  private Map<Long, RestaurantBatchingManager> getManagerMap() throws Exception {
    Field field = BatchingManager.class.getDeclaredField("restaurantManagers");
    field.setAccessible(true);
    return (Map<Long, RestaurantBatchingManager>) field.get(batchingManager);
  }

  /**
   * Replaces the real manager for a restaurant with a spy, allowing verification of method calls on
   * that manager.
   * 
   * @param restaurantId the ID of the restaurant whose manager should be spied
   * @return the spy
   */
  private RestaurantBatchingManager spyOnManager(long restaurantId) throws Exception {
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    RestaurantBatchingManager real = map.get(restaurantId);
    assertNotNull(real, "Manager should exist for restaurant " + restaurantId);
    RestaurantBatchingManager spy = spy(real);
    map.put(restaurantId, spy);
    return spy;
  }

  // --- addManager tests ---

  /** Verifies that addManager creates a new manager and stores it in the map. */
  @Test
  void addManager_createsManagerAndStoresIt() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);

    // When
    batchingManager.addManager(RESTAURANT_ID_1);

    // Then
    verify(restaurantService).getRestaurant(RESTAURANT_ID_1);
    Map<Long, RestaurantBatchingManager> map = getManagerMap();
    assertTrue(map.containsKey(RESTAURANT_ID_1));
    assertNotNull(map.get(RESTAURANT_ID_1));
  }

  /** Verifies that adding a manager for a restaurant that already has one throws an exception. */
  @Test
  void addManager_duplicate_throwsException() {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);

    // When / Then
    assertThrows(IllegalArgumentException.class, () -> batchingManager.addManager(RESTAURANT_ID_1));
  }

  // --- updateManagerAddress tests ---

  /** Verifies that updating the address delegates to the correct manager. */
  @Test
  void updateManagerAddress_delegatesToManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);

    // When
    batchingManager.updateManagerAddress(RESTAURANT_ID_1, "New Address");

    // Then
    verify(spy).setRestaurantAddress("New Address");
  }

  /** Verifies that updateManagerAddress throws when the restaurant does not have a manager. */
  @Test
  void updateManagerAddress_nonexistent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> batchingManager.updateManagerAddress(RESTAURANT_ID_1, "New Address"));
  }

  // --- removeManager tests ---

  /** Verifies that removeManager removes the manager from the internal map. */
  @Test
  void removeManager_removesFromMap() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    assertTrue(getManagerMap().containsKey(RESTAURANT_ID_1));

    // When
    batchingManager.removeManager(RESTAURANT_ID_1);

    // Then
    assertFalse(getManagerMap().containsKey(RESTAURANT_ID_1));
  }

  /** Verifies that removeManager throws when the restaurant does not have a manager. */
  @Test
  void removeManager_nonexistent_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> batchingManager.removeManager(RESTAURANT_ID_1));
  }

  // --- Delegation tests ---

  /** Verifies that addOrder delegates to the correct manager. */
  @Test
  void addOrder_delegatesToCorrectManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);
    Order order = createOrder(100L, RESTAURANT_ID_1);

    // When
    batchingManager.addOrder(order);

    // Then
    verify(spy).addOrder(order);
  }

  /** Verifies that removeOrder delegates to the correct manager. */
  @Test
  void removeOrder_delegatesToCorrectManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);
    Order order = createOrder(100L, RESTAURANT_ID_1);
    when(dbOrderService.getOrder(100L)).thenReturn(order);

    // When
    batchingManager.removeOrder(100L);

    // Then
    verify(spy).removeOrder(100L);
  }

  /** Verifies that updateOrder delegates to the correct manager with the correct flag. */
  @Test
  void updateOrder_delegatesToCorrectManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);
    Order order = createOrder(100L, RESTAURANT_ID_1);
    when(dbOrderService.getOrder(100L)).thenReturn(order);

    // When
    batchingManager.updateOrder(100L, true);

    // Then
    verify(spy).updateOrder(100L, true);
  }

  // --- Listener registration tests ---

  /** Verifies that onBatchesChange registers a listener with the correct manager. */
  @Test
  void onBatchesChange_addsListenerToManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);
    Consumer<Long> listener = mock(Consumer.class);

    // When
    batchingManager.onBatchesChange(RESTAURANT_ID_1, listener);

    // Then
    verify(spy).onBatchChange(listener);
  }

  /** Verifies that onBatchBecomeActive registers a listener with the correct manager. */
  @Test
  void onBatchBecomeActive_addsListenerToManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy = spyOnManager(RESTAURANT_ID_1);
    Consumer<Long> listener = mock(Consumer.class);

    // When
    batchingManager.onBatchBecomeActive(RESTAURANT_ID_1, listener);

    // Then
    verify(spy).onBatchBecomeActive(listener);
  }

  // --- Scheduled check tests ---

  /** Verifies that checkExpiredBatches calls the method on all existing managers. */
  @Test
  void checkExpiredBatches_callsAllManagers() throws Exception {
    // Given
    Restaurant rest1 = new Restaurant(RESTAURANT_ID_1, "Rest1", ADDRESS_1);
    Restaurant rest2 = new Restaurant(RESTAURANT_ID_2, "Rest2", ADDRESS_2);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(rest1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_2)).thenReturn(rest2);
    batchingManager.addManager(RESTAURANT_ID_1);
    batchingManager.addManager(RESTAURANT_ID_2);

    RestaurantBatchingManager spy1 = spyOnManager(RESTAURANT_ID_1);
    RestaurantBatchingManager spy2 = spyOnManager(RESTAURANT_ID_2);

    // When
    batchingManager.checkExpiredBatches();

    // Then
    verify(spy1).checkExpiredBatches(BatchingManager.UPDATE_INCREMENTS_MILLIS);
    verify(spy2).checkExpiredBatches(BatchingManager.UPDATE_INCREMENTS_MILLIS);
  }

  /** Verifies that checkExpiredBatches does nothing when no managers exist. */
  @Test
  void checkExpiredBatches_withNoManagers_doesNothing() {
    // When – no managers have been added
    batchingManager.checkExpiredBatches();

    // Then – no exception, method completes
    assertTrue(true);
  }

  // --- Error handling tests ---

  /** Verifies that removeOrder throws when the order does not exist. */
  @Test
  void removeOrder_withInvalidOrderId_throwsException() {
    // Given
    when(dbOrderService.getOrder(999L)).thenThrow(new IllegalArgumentException("Order not found"));

    // When / Then
    assertThrows(IllegalArgumentException.class, () -> batchingManager.removeOrder(999L));
  }

  /** Verifies that updateOrder throws when the order does not exist. */
  @Test
  void updateOrder_withInvalidOrderId_throwsException() {
    // Given
    when(dbOrderService.getOrder(999L)).thenThrow(new IllegalArgumentException("Order not found"));

    // When / Then
    assertThrows(IllegalArgumentException.class, () -> batchingManager.updateOrder(999L, true));
  }

  /** Verifies that addManager propagates exceptions from restaurantService. */
  @Test
  void addManager_whenRestaurantServiceFails_throwsException() {
    // Given
    when(restaurantService.getRestaurant(RESTAURANT_ID_1))
        .thenThrow(new RuntimeException("DB error"));

    // When / Then
    assertThrows(RuntimeException.class, () -> batchingManager.addManager(RESTAURANT_ID_1));
  }

  /** Verifies that any operation on a restaurant without a manager throws an exception. */
  @Test
  void operationOnNonexistentRestaurant_throwsException() {
    // No manager added for RESTAURANT_ID_1
    assertThrows(IllegalArgumentException.class,
        () -> batchingManager.addOrder(createOrder(100L, RESTAURANT_ID_1)));
  }

  // --- Reuse of existing manager ---

  /**
   * Verifies that multiple operations on the same restaurant use the same manager instance (i.e.,
   * no duplicate creation).
   */
  @Test
  void multipleOperationsOnSameRestaurant_useSameManager() throws Exception {
    // Given
    Restaurant restaurant = new Restaurant(RESTAURANT_ID_1, "Test Restaurant", ADDRESS_1);
    when(restaurantService.getRestaurant(RESTAURANT_ID_1)).thenReturn(restaurant);
    batchingManager.addManager(RESTAURANT_ID_1);
    Map<Long, RestaurantBatchingManager> mapBefore = getManagerMap();
    RestaurantBatchingManager firstManager = mapBefore.get(RESTAURANT_ID_1);

    // Create order and stub getOrder
    Order order = createOrder(100L, RESTAURANT_ID_1);
    when(dbOrderService.getOrder(100L)).thenReturn(order);

    // When – additional operations
    batchingManager.addOrder(order);
    batchingManager.updateOrder(100L, false);

    // Then – no additional addManager calls, same manager instance
    verify(restaurantService, times(1)).getRestaurant(RESTAURANT_ID_1);
    Map<Long, RestaurantBatchingManager> mapAfter = getManagerMap();
    assertSame(firstManager, mapAfter.get(RESTAURANT_ID_1));
  }
}
