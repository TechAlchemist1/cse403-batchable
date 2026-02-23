package com.batchable.backend.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.exception.InvalidRouteException;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.BatchingAlgorithm.TentativeBatch;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.RouteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for BatchingAlgorithm using mocked RouteService and DbOrderService. All times are
 * specified in seconds, with constant expressions evaluated.
 */
class BatchingAlgorithmTest {
  @Mock
  private RouteService mockRouteService;
  @Mock
  private DbOrderService mockDbOrderService;
  private BatchingAlgorithm batchingAlgorithm;
  private int SECONDS_TO_HAND_DELIVER;
  private long ORDER_ID;
  private String restaurantAddress;

  @BeforeEach
  void setUp() {
    mockRouteService = mock(RouteService.class);
    mockDbOrderService = mock(DbOrderService.class);
    batchingAlgorithm = new BatchingAlgorithm(mockRouteService, mockDbOrderService);
    SECONDS_TO_HAND_DELIVER = batchingAlgorithm.getSecondsToHandDeliver();
    ORDER_ID = 1;
    restaurantAddress = "Restaurant A";
  }

  /** Creates a new order with the given times. Order IDs are auto‑incremented. */
  private Order getOrder(Instant initialTime, Instant cookedTime, Instant deliveryTime) {
    return new Order(ORDER_ID++, -1L, "", "", initialTime, deliveryTime, cookedTime, State.COOKING,
        false, -1L);
  }

  /** Returns an Instant that is 'seconds' seconds in the future. */
  private Instant futureSeconds(int seconds) {
    return Instant.now().plus(Duration.ofSeconds(seconds));
  }

  /**
   * Computes the last allowed cooked time for the first order in a batch, based on its destination
   * and the hand‑deliver overhead.
   */
  private Instant getLastAllowedCookedTime(Order firstOrder, String restaurantAddress)
      throws InvalidRouteException {
    int firstDeliverySeconds =
        mockRouteService.getSecondsBetween(restaurantAddress, firstOrder.destination)
            + SECONDS_TO_HAND_DELIVER;
    Instant lastAllowedCookTime =
        firstOrder.deliveryTime.minus(Duration.ofSeconds(firstDeliverySeconds - 1));
    assertFalse(lastAllowedCookTime.isBefore(firstOrder.cookedTime));
    return lastAllowedCookTime;
  }

  /**
   * Checks invariants of the list of tentative batches: - No empty batches. - All order IDs are
   * unique. - Batches are sorted by lastAllowedCookedTime descending. - For consecutive orders in a
   * batch, the second order’s delivery time is later than the earliest possible arrival time after
   * the first order. If checkEdges is false, the cross‑batch delivery time constraints are not
   * verified.
   */
  void checkInvariants(List<TentativeBatch> batches, boolean checkEdges)
      throws InvalidRouteException {
    Set<Long> orderIds = new HashSet<Long>();
    int size = 0;
    Instant nextCt = null;
    for (int i = batches.size() - 1; i >= 0; i--) {
      TentativeBatch tb = batches.get(i);
      List<Order> batch = tb.getBatch();
      Instant ct = tb.getLastAllowedCookedTime();
      size += batch.size();
      assertNotEquals(0, batch.size(), "No batch can be empty");
      for (int j = 0; j < batch.size(); j++) {
        Order o = batch.get(j);
        orderIds.add(o.id);
        if (j == 0) {
          assertTrue(Duration.between(getLastAllowedCookedTime(o, restaurantAddress), ct).abs()
              .toMillis() <= 1000);
          assert (o.cookedTime.isBefore(ct));
          if (i != batches.size() - 1) {
            assertFalse(nextCt.isAfter(ct));
          }
          nextCt = ct;
        } else if (checkEdges) {
          Order prev = batch.get(j - 1);
          int deliverySeconds = mockRouteService.getSecondsBetween(prev.destination, o.destination)
              + SECONDS_TO_HAND_DELIVER - 1;
          Instant earliestArrival = prev.deliveryTime.plus(Duration.ofSeconds(deliverySeconds - 1));
          assertTrue(earliestArrival.isBefore(o.deliveryTime));
        }
      }
    }
    assertEquals(size, orderIds.size());
  }

  /** Tests that adding a single order creates a batch containing that order. */
  @Test
  void testAddSingleOrderCreatesNewBatch() throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(120);
    // cooked = 5 min = 300 s; delivery = (5 + 2) min + hand-deliver = 420 + SECONDS_TO_HAND_DELIVER
    // s
    Order order = getOrder(futureSeconds(0), futureSeconds(300),
        futureSeconds(420 + SECONDS_TO_HAND_DELIVER));
    List<TentativeBatch> batches = new ArrayList<>();

    batchingAlgorithm.addOrder(batches, order, restaurantAddress);

    assertEquals(1, batches.size(), "Should create one batch");
    TentativeBatch batch = batches.get(0);
    assertEquals(1, batch.getBatch().size(), "Batch should contain one order");
    assertEquals(order, batch.getBatch().get(0));

    assertDoesNotThrow(() -> batchingAlgorithm.rebatchOrder(batches, order, restaurantAddress));

    assertEquals(1, batches.size(), "Should create one batch");
    batch = batches.get(0);
    assertEquals(1, batch.getBatch().size(), "Batch should contain one order");
    assertEquals(order, batch.getBatch().get(0));
  }

  /**
   * Helper method that runs a complete test scenario with uniform travel times.
   *
   * @param initialSec list of initial times (seconds from now)
   * @param cookedSec list of cooked times (seconds from now)
   * @param deliveredSec list of delivery times (seconds from now)
   * @param expectedAnswerInds list of expected batch contents (order indices)
   * @param uniformTime uniform travel time in seconds between any two addresses
   */
  void ordersUniform(List<Integer> initialSec, List<Integer> cookedSec, List<Integer> deliveredSec,
      List<List<Integer>> expectedAnswerInds, int uniformTime) throws Exception {

    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(uniformTime);

    assertEquals(initialSec.size(), cookedSec.size(),
        "Mismatch: initialSec and cookedSec lists must be the same length");
    assertEquals(cookedSec.size(), deliveredSec.size(),
        "Mismatch: cookedSec and deliveredSec lists must be the same length");

    int n = initialSec.size();

    List<TentativeBatch> batches = new ArrayList<>();
    List<Order> orders = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      Order order = getOrder(futureSeconds(initialSec.get(i)), futureSeconds(cookedSec.get(i)),
          futureSeconds(deliveredSec.get(i)));
      orders.add(order);
      batchingAlgorithm.addOrder(batches, order, restaurantAddress);
      checkInvariants(batches, true);
    }

    assertEquals(expectedAnswerInds.size(), batches.size(),
        "Number of batches does not match expected");

    for (int i = 0; i < batches.size(); i++) {
      TentativeBatch tb = batches.get(i);
      assertNotNull(tb, "Batch at index " + i + " is null");

      List<Order> batch = tb.getBatch();
      assertNotNull(batch, "Order list in batch " + i + " is null");

      List<Integer> expectedOrderInds = expectedAnswerInds.get(i);
      assertEquals(expectedOrderInds.size(), batch.size(), "Batch " + i
          + " size mismatch: expected " + expectedOrderInds.size() + ", got " + batch.size());

      for (int j = 0; j < batch.size(); j++) {
        Order order = batch.get(j);
        assertNotNull(order, "Order at batch " + i + ", index " + j + " is null");

        int expectedInd = expectedOrderInds.get(j);
        assertEquals(orders.get(expectedInd), order, "Order mismatch at batch " + i + ", position "
            + j + ": expected order index " + expectedInd);
      }
    }

    Collections.shuffle(orders);
    int size = orders.size();
    for (Order order : orders) {
      int newSize = 0;
      Order other = new Order(order.id, order.restaurantId, order.destination, order.itemNamesJson,
          order.initialTime, order.deliveryTime, order.cookedTime, State.COOKED, order.highPriority,
          order.batchId);
      when(mockDbOrderService.getOrder(anyLong())).thenReturn(other);
      assertDoesNotThrow(() -> batchingAlgorithm.updateOrderInplace(batches, order.id));
      assertDoesNotThrow(() -> batchingAlgorithm.removeOrder(batches, order.id, restaurantAddress));
      assertThrows(IllegalArgumentException.class,
          () -> batchingAlgorithm.removeOrder(batches, order.id, restaurantAddress));
      assertThrows(IllegalArgumentException.class,
          () -> batchingAlgorithm.rebatchOrder(batches, order, restaurantAddress));
      assertThrows(IllegalArgumentException.class,
          () -> batchingAlgorithm.updateOrderInplace(batches, order.id));
      checkInvariants(batches, true);
      for (TentativeBatch tb : batches) {
        assertNotEquals(0, tb.getBatch().size());
        newSize += tb.getBatch().size();
        for (Order o : tb.getBatch()) {
          assertNotEquals(order, o);
        }
      }
      assertEquals(size - 1, newSize);
      size--;
    }
  }

  /** Tests various two‑order scenarios with uniform travel times. */
  @Test
  void twoOrders() throws Exception {
    int uniformTravelTime = 120; // 2 minutes in seconds
    int t = uniformTravelTime + SECONDS_TO_HAND_DELIVER; // total leg time in seconds

    List<Integer> initialSec = List.of(0, 60); // 0 and 1 minute
    List<Integer> cookedSec = List.of(300, 360); // 5 and 6 minutes
    List<List<Integer>> expectedAnswerInds;

    // Case 1: both together, order0 then order1
    // delivery times in original minutes: (6 + t_min) and (6 + 2*t_min)
    // Converted to seconds: 360 + t_sec and 360 + 2*t_sec
    List<Integer> deliveredSec = List.of(360 + t, 360 + 2 * t);
    expectedAnswerInds = List.of(List.of(0, 1));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // Case 2: both together, order1 then order0
    deliveredSec = List.of(360 + 2 * t, 360 + t);
    expectedAnswerInds = List.of(List.of(1, 0));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // Case 3: separate batches
    deliveredSec = List.of(360 + 2 * t - 60, 360 + t);
    expectedAnswerInds = List.of(List.of(0), List.of(1));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // Case 4: separate batches (reverse)
    deliveredSec = List.of(360 + t, 360 + 2 * t - 60);
    expectedAnswerInds = List.of(List.of(1), List.of(0));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);
  }

  /** Tests various three‑order scenarios with uniform travel times. */
  @Test
  void threeOrders() throws Exception {
    int uniformTravelTime = 120; // 2 minutes in seconds
    int t = uniformTravelTime + SECONDS_TO_HAND_DELIVER; // total leg time in seconds

    List<Integer> initialSec = List.of(0, 0, 0);
    List<Integer> cookedSec;
    List<Integer> deliveredSec;
    List<List<Integer>> expectedAnswerInds;

    // ---- Case 1: All three in one batch, ascending delivery order ----
    cookedSec = List.of(300, 360, 420); // 5,6,7 minutes
    // (7 + 1*t) minutes → seconds = 420 + t
    // (7 + 2*t) minutes → seconds = 420 + 2*t
    // (7 + 3*t) minutes → seconds = 420 + 3*t
    deliveredSec = List.of(420 + t, 420 + 2 * t, 420 + 3 * t);
    expectedAnswerInds = List.of(List.of(0, 1, 2));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // ---- Case 2: All three in one batch, descending delivery order ----
    deliveredSec = List.of(420 + 3 * t, 420 + 2 * t, 420 + t);
    expectedAnswerInds = List.of(List.of(2, 1, 0));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // ---- Case 3: Two batches – first two together, third alone ----
    cookedSec = List.of(300, 360, 480); // 5,6,8 minutes
    // order0: 7 + t → 420 + t
    // order1: 7 + 2t → 420 + 2*t
    // order2: 8 + t → 480 + t
    deliveredSec = List.of(420 + t, 420 + 2 * t, 480 + t);
    expectedAnswerInds = List.of(List.of(2), List.of(0, 1));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // ---- Case 4: Three separate batches – each cooked after previous batch's latestAllowed ----
    cookedSec = List.of(300, 301 + t, 302 + 2 * t); // 5,7,9 minutes
    // (5 + t) → 300 + t
    // (7 + t) → 420 + t
    // (9 + t) → 540 + t
    deliveredSec = List.of(300 + t, 301 + 2 * t, 302 + 3 * t);
    expectedAnswerInds = List.of(List.of(2), List.of(1), List.of(0));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // ---- Case 5: Two batches – first alone, second and third together ----
    cookedSec = List.of(300, 480, 540); // 5,8,9 minutes
    // order0: 5 + t → 300 + t
    // order1: 9 + t → 540 + t
    // order2: 9 + 2t → 540 + 2*t
    deliveredSec = List.of(300 + t, 540 + t, 540 + 2 * t);
    expectedAnswerInds = List.of(List.of(1, 2), List.of(0));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // ---- Case 6: Two batches – orders 0 and 2 together, order1 alone ----
    cookedSec = List.of(300, 420, 360); // 5,7,6 minutes
    // order0: 6 + t → 360 + t
    // order1: 7 + t → 420 + t
    // order2: 6 + 2t → 360 + 2*t
    deliveredSec = List.of(360 + t, 420 + t, 360 + 2 * t);
    expectedAnswerInds = List.of(List.of(1), List.of(0, 2));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // ---- Case 7: Three separate batches due to hand‑deliver constraint ----
    cookedSec = List.of(300, 300, 300); // 5,5,5 minutes
    // (5 + t) → 300 + t
    // (5 + t + 4) → 300 + t + 240
    // (5 + t + 3) → 300 + t + 180
    deliveredSec = List.of(300 + t, 300 + 2 * t - 2, 300 + 2 * t - 3);
    expectedAnswerInds = List.of(List.of(1), List.of(2), List.of(0));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);
  }

  /** Verifies that orders with significantly different cooked times go to separate batches. */
  @Test
  void testAddOrdersCreatesMultipleBatchesWhenNecessary() throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(360);

    List<TentativeBatch> batches = new ArrayList<>();
    // order1: initial 0, cooked 1 min = 60 s, delivery = (1 + 6) min + hand-deliver = 420 +
    // SECONDS_TO_HAND_DELIVER s
    // order2: initial 5 min = 300 s, cooked 6 min = 360 s, delivery = (6 + 6) min + hand-deliver =
    // 720 + SECONDS_TO_HAND_DELIVER s
    // order3: initial 10 min = 600 s, cooked 11 min = 660 s, delivery = (11 + 6) min + hand-deliver
    // = 1020 + SECONDS_TO_HAND_DELIVER s
    Order order1 =
        getOrder(futureSeconds(0), futureSeconds(60), futureSeconds(420 + SECONDS_TO_HAND_DELIVER));
    Order order2 = getOrder(futureSeconds(300), futureSeconds(360),
        futureSeconds(720 + SECONDS_TO_HAND_DELIVER));
    Order order3 = getOrder(futureSeconds(600), futureSeconds(660),
        futureSeconds(1020 + SECONDS_TO_HAND_DELIVER));

    batchingAlgorithm.addOrder(batches, order1, restaurantAddress);
    batchingAlgorithm.addOrder(batches, order2, restaurantAddress);
    batchingAlgorithm.addOrder(batches, order3, restaurantAddress);

    assertEquals(3, batches.size());
  }

  /** Tests that orders with identical delivery times cannot be in the same batch. */
  @Test
  void testOrderCannotFollowPreviousDueToDeliveryTime() throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(10);

    List<TentativeBatch> batches = new ArrayList<>();
    // initial 0, cooked 1 min = 60 s, delivery = (1 + CEIL?) Actually original used
    // CEIL_MINS_TO_HAND_DELIVER.
    // In seconds, base delivery = 60 + SECONDS_TO_HAND_DELIVER
    int baseDelivery = 60 + SECONDS_TO_HAND_DELIVER;
    Order order1 = getOrder(futureSeconds(0), futureSeconds(60), futureSeconds(baseDelivery));
    Order order2 = getOrder(futureSeconds(0), futureSeconds(60), futureSeconds(baseDelivery));

    // updated delivery = 60 + 60 + SECONDS_TO_HAND_DELIVER = 120 + SECONDS_TO_HAND_DELIVER
    int updatedDelivery = 120 + SECONDS_TO_HAND_DELIVER;
    Order updatedOrder1 =
        getOrder(futureSeconds(0), futureSeconds(60), futureSeconds(updatedDelivery));
    Order updatedOrder2 =
        getOrder(futureSeconds(0), futureSeconds(60), futureSeconds(updatedDelivery));

    when(mockDbOrderService.getOrder(anyLong())).thenReturn(updatedOrder1);
    batchingAlgorithm.addOrder(batches, order1, restaurantAddress);
    when(mockDbOrderService.getOrder(anyLong())).thenReturn(updatedOrder2);
    batchingAlgorithm.addOrder(batches, order2, restaurantAddress);

    assertEquals(2, batches.size(),
        "Orders with conflicting delivery times should be in separate batches");
  }

  /** Tests ten orders that all fit into one batch. */
  @Test
  void tenOrdersAllInOneBatch() throws Exception {
    int uniformTravelTime = 120; // 2 minutes in seconds
    int t = uniformTravelTime + SECONDS_TO_HAND_DELIVER; // total leg time in seconds

    List<Integer> initialSec = new ArrayList<>();
    List<Integer> cookedSec = new ArrayList<>();
    List<Integer> deliveredSec = new ArrayList<>();

    // cooked times: 5,6,...,14 minutes → seconds = 300,360,...,840
    // deliveries: 14 + (i+1)*t_min minutes → seconds = 840 + (i+1)*t_sec
    for (int i = 0; i < 10; i++) {
      initialSec.add(0);
      cookedSec.add(300 + i * 60);
      deliveredSec.add(840 + (i + 1) * t);
    }
    List<List<Integer>> expectedAnswerInds = List.of(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);

    // Second permutation
    List<Integer> deliveredPerm = List.of(840 + 5 * t, 840 + 7 * t, 840 + 2 * t, 840 + 8 * t,
        840 + 1 * t, 840 + 4 * t, 840 + 9 * t, 840 + 6 * t, 840 + 10 * t, 840 + 3 * t);
    expectedAnswerInds = List.of(List.of(4, 2, 9, 5, 0, 7, 1, 3, 6, 8));
    ordersUniform(initialSec, cookedSec, deliveredPerm, expectedAnswerInds, uniformTravelTime);

    // Third permutation (reverse order)
    List<Integer> deliveredRev = new ArrayList<>();
    for (int i = 9; i >= 0; i--) {
      deliveredRev.add(840 + (i + 1) * t);
    }
    expectedAnswerInds = List.of(List.of(9, 8, 7, 6, 5, 4, 3, 2, 1, 0));
    ordersUniform(initialSec, cookedSec, deliveredRev, expectedAnswerInds, uniformTravelTime);
  }

  /** Tests ten orders split into two batches by even/odd cooked times. */
  @Test
  void tenOrdersTwoBatchesEvenOdd() throws Exception {
    int uniformTravelTime = 120;
    int t = uniformTravelTime + SECONDS_TO_HAND_DELIVER; // total leg time in seconds

    List<Integer> initialSec = new ArrayList<>();
    List<Integer> cookedSec = new ArrayList<>();
    List<Integer> deliveredSec = new ArrayList<>();

    // Even indices: cooked 5‑9 min (300‑540), deliveries 10+t … 10+5t → 600 + (i+1)*t
    // Odd indices: cooked 11‑15 min (660‑900), deliveries 16+t … 16+5t → 960 + (i+1)*t
    for (int i = 0; i < 5; i++) {
      initialSec.add(0);
      initialSec.add(0);
      cookedSec.add(300 + i * 60); // evens
      cookedSec.add(660 + i * 60); // odds
      deliveredSec.add(600 + (i + 1) * t); // even deliveries
      deliveredSec.add(960 + (i + 1) * t); // odd deliveries
    }

    List<List<Integer>> expectedAnswerInds = List.of(List.of(1, 3, 5, 7, 9), // odds in delivery
                                                                             // order
        List.of(0, 2, 4, 6, 8) // evens in delivery order
    );
    if (t >= 360)
      ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);
  }

  /** Tests ten orders split into three batches based on cooked times. */
  @Test
  void tenOrdersThreeBatchesByCookedTime() throws Exception {
    int uniformTravelTime = 120;
    int t = uniformTravelTime + SECONDS_TO_HAND_DELIVER; // seconds

    List<Integer> initialSec = new ArrayList<>();
    List<Integer> cookedSec = new ArrayList<>();
    List<Integer> deliveredSec = new ArrayList<>();

    // Group A (indices 0‑1): cooked 5‑6 min → 300,360; deliveries 7+t,7+2t → 420+t, 420+2t
    // Group B (indices 2‑4): cooked 8‑10 min → 480,540,600; deliveries 11+t,11+2t,11+3t → 660+t,
    // 660+2t, 660+3t
    // Group C (indices 5‑9): cooked 12‑16 min → 720,780,840,900,960; deliveries 17+t … 17+5t →
    // 1020+t, 1020+2t, 1020+3t, 1020+4t, 1020+5t
    int[][] groups = {{5, 6}, {8, 9, 10}, {12, 13, 14, 15, 16}};
    int[] baseDeliveries = {7, 11, 17};

    for (int g = 0; g < groups.length; g++) {
      for (int c : groups[g]) {
        initialSec.add(0);
        cookedSec.add(c * 60);
        int j = c - groups[g][0]; // 0‑based offset within group
        deliveredSec.add(baseDeliveries[g] * 60 + (j + 1) * t);
      }
    }

    List<List<Integer>> expectedAnswerInds = List.of(List.of(5, 6, 7, 8, 9), // group C
        List.of(2, 3, 4), // group B
        List.of(0, 1) // group A
    );

    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);
  }

  /** Tests a larger scenario with 50 orders forming 10 batches, verifying reordering. */
  @Test
  void fiftyOrdersTenBatchesReordered() throws Exception {
    int uniformTravelTime = 120; // 2 minutes in seconds
    int t = uniformTravelTime + SECONDS_TO_HAND_DELIVER; // seconds

    List<Integer> initialSec = new ArrayList<>();
    List<Integer> cookedSec = new ArrayList<>();
    List<Integer> deliveredSec = new ArrayList<>();

    // Create 10 batches of 5 orders each, with increasing latestAllowedCookedTime L (minutes).
    for (int batch = 0; batch < 10; batch++) {
      int L = 10 + 2 * batch; // latestAllowedCookedTime for this batch (minutes)
      for (int j = 0; j < 5; j++) {
        initialSec.add(0);
        // cooked times: L-5 .. L-1 (all <= L) minutes → seconds = (L-5+j)*60
        cookedSec.add((L - 5 + j) * 60);
        // delivery times: L + (j+1)*t_min minutes → seconds = L*60 + (j+1)*t
        deliveredSec.add(L * 60 + (j + 1) * t);
      }
    }

    // Expected batches in descending L order (batch9 first, batch0 last).
    List<List<Integer>> expectedAnswerInds = new ArrayList<>();
    for (int batch = 9; batch >= 0; batch--) {
      List<Integer> batchIndices = new ArrayList<>();
      int startIdx = batch * 5;
      for (int j = 0; j < 5; j++) {
        batchIndices.add(startIdx + j);
      }
      expectedAnswerInds.add(batchIndices);
    }

    ordersUniform(initialSec, cookedSec, deliveredSec, expectedAnswerInds, uniformTravelTime);
  }

  /** Helper to create an order with a specific destination. */
  private Order getOrder(Instant initialTime, Instant cookedTime, Instant deliveryTime,
      String destination) {
    Order order = new Order(ORDER_ID++, -1L, destination, "", initialTime, deliveryTime, cookedTime,
        State.COOKING, false, -1L);
    return order;
  }

  /**
   * Sets up the mock to return specific travel times for given origin→destination pairs.
   */
  private void mockTravelTimes(Map<String, Integer> travelTimeMap) throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenAnswer(invocation -> {
      String from = invocation.getArgument(0);
      String to = invocation.getArgument(1);
      String key = from + "→" + to;
      if (travelTimeMap.containsKey(key)) {
        return travelTimeMap.get(key);
      }
      throw new IllegalArgumentException("Unexpected route: " + key);
    });
  }

  /**
   * A flexible test method for non‑uniform travel times (all times in seconds).
   */
  void ordersNonUniform(List<Integer> initialSec, List<Integer> cookedSec,
      List<Integer> deliveredSec, List<String> destinations, Map<String, Integer> travelTimeMap,
      List<List<Integer>> expectedAnswerInds) throws Exception {

    assertEquals(initialSec.size(), cookedSec.size(), "Mismatch: initialSec and cookedSec sizes");
    assertEquals(cookedSec.size(), deliveredSec.size(),
        "Mismatch: cookedSec and deliveredSec sizes");
    assertEquals(deliveredSec.size(), destinations.size(),
        "Mismatch: deliveredSec and destinations sizes");

    mockTravelTimes(travelTimeMap);

    int n = initialSec.size();
    List<TentativeBatch> batches = new ArrayList<>();
    List<Order> orders = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      Order order = getOrder(futureSeconds(initialSec.get(i)), futureSeconds(cookedSec.get(i)),
          futureSeconds(deliveredSec.get(i)), destinations.get(i));
      orders.add(order);
      batchingAlgorithm.addOrder(batches, order, restaurantAddress);
      checkInvariants(batches, false);
    }
    assertEquals(expectedAnswerInds.size(), batches.size(),
        "Number of batches does not match expected");

    for (int i = 0; i < batches.size(); i++) {
      TentativeBatch tb = batches.get(i);
      assertNotNull(tb, "Batch at index " + i + " is null");
      List<Order> batch = tb.getBatch();
      assertNotNull(batch, "Order list in batch " + i + " is null");

      List<Integer> expectedOrderInds = expectedAnswerInds.get(i);
      assertEquals(expectedOrderInds.size(), batch.size(), "Batch " + i
          + " size mismatch: expected " + expectedOrderInds.size() + ", got " + batch.size());

      for (int j = 0; j < batch.size(); j++) {
        Order order = batch.get(j);
        assertNotNull(order, "Order at batch " + i + ", index " + j + " is null");
        int expectedInd = expectedOrderInds.get(j);
        assertEquals(orders.get(expectedInd), order, "Order mismatch at batch " + i + ", position "
            + j + ": expected order index " + expectedInd);
      }
    }
    Collections.shuffle(orders);
    int size = orders.size();
    for (Order order : orders) {
      int newSize = 0;
      assertDoesNotThrow(() -> batchingAlgorithm.removeOrder(batches, order.id, restaurantAddress));
      checkInvariants(batches, false);
      for (TentativeBatch tb : batches) {
        assertNotEquals(0, tb.getBatch().size());
        newSize += tb.getBatch().size();
        for (Order o : tb.getBatch()) {
          assertNotEquals(order, o);
        }
      }
      assertEquals(size - 1, newSize);
      size--;
    }
  }

  // ---------------------------------------------------------------------
  // Non‑uniform travel time tests (converted to seconds, using SECONDS_TO_HAND_DELIVER)
  // ---------------------------------------------------------------------

  @Test
  void twoOrdersDifferentTravelTimesStillBatch() throws Exception {
    int tA = 120; // 2 minutes
    int tB = 180; // 3 minutes
    int H = SECONDS_TO_HAND_DELIVER;
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", tA);
    travelTimes.put(restaurantAddress + "→DestB", tB);
    travelTimes.put("DestA→DestB", 60); // 1 minute

    List<Integer> initialSec = List.of(0, 0);
    List<Integer> cookedSec = List.of(300, 300); // both 5 minutes
    // base1 = (5 + tA/60 + CEIL_MINS) minutes → seconds = (5 + tA/60)*60 + H = 5*60 + tA + H = 300
    // + tA + H
    // tA = 120, so base1 = 420 + H
    // base2 = (5 + tA/60 + H/60 + 1 + H/60) minutes = (5 + tA/60 + 1 + 2*H/60) minutes
    // In seconds: 5*60 + tA + 60 + 2*H = 300 + tA + 60 + 2*H = 360 + tA + 2*H
    // Since tA = 120, base2 = 480 + 2*H
    int base1 = 300 + tA + H; // 420 + H
    int base2 = 360 + tA + 2 * H; // 480 + 2*H
    List<Integer> deliveredSec = List.of(base1, base2);
    List<String> destinations = List.of("DestA", "DestB");
    List<List<Integer>> expected = List.of(List.of(0, 1));
    ordersNonUniform(initialSec, cookedSec, deliveredSec, destinations, travelTimes, expected);
  }

  @Test
  void twoOrdersDifferentTravelTimesSeparateBatches() throws Exception {
    int tA = 120;
    int tB = 900; // 15 minutes
    int H = SECONDS_TO_HAND_DELIVER;
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", tA);
    travelTimes.put(restaurantAddress + "→DestB", tB);

    List<Integer> initialSec = List.of(0, 0);
    List<Integer> cookedSec = List.of(300, 420); // 5 and 7 minutes
    // delivery A: (5 + tA/60) min + H → 300 + tA + H
    // delivery B: (7 + tB/60) min + H → 420 + tB + H
    int baseA = 300 + tA + H; // 420 + H
    int baseB = 420 + tB + H; // 1320 + H
    List<Integer> deliveredSec = List.of(baseA, baseB);
    List<String> destinations = List.of("DestA", "DestB");
    List<List<Integer>> expected = List.of(List.of(1), List.of(0));
    ordersNonUniform(initialSec, cookedSec, deliveredSec, destinations, travelTimes, expected);
  }

  @Test
  void threeOrdersMixedTravelTimesReordering() throws Exception {
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 120);
    travelTimes.put(restaurantAddress + "→DestB", 300);
    travelTimes.put(restaurantAddress + "→DestC", 600);
    travelTimes.put("DestA→DestB", 60);
    travelTimes.put("DestB→DestC", 120);

    List<Integer> initialSec = List.of(0, 0, 0);
    List<Integer> cookedSec = List.of(300, 360, 420); // 5,6,7 minutes
    // delivery times from original: 14,20,27 minutes → seconds: 840,1200,1620
    List<Integer> deliveredSec = List.of(840, 1200, 1620);
    List<String> destinations = List.of("DestA", "DestB", "DestC");
    List<List<Integer>> expected = List.of(List.of(0, 1, 2));
    ordersNonUniform(initialSec, cookedSec, deliveredSec, destinations, travelTimes, expected);
  }

  @Test
  void threeOrdersMixedTravelTimesReordering2() throws Exception {
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 120);
    travelTimes.put(restaurantAddress + "→DestC", 600);
    travelTimes.put(restaurantAddress + "→DestB", 300);
    travelTimes.put("DestA→DestC", 180);
    travelTimes.put("DestA→DestB", 60);
    travelTimes.put("DestB→DestC", 120);

    List<Integer> initialSec = List.of(0, 0, 0);
    List<Integer> cookedSec = List.of(300, 420, 360); // 5,7,6 minutes
    // delivery times: 14,27,20 minutes → 840,1620,1200 seconds
    List<Integer> deliveredSec = List.of(840, 1620, 1200);
    List<String> destinations = List.of("DestA", "DestC", "DestB");
    List<List<Integer>> expected = List.of(List.of(0, 2, 1));
    ordersNonUniform(initialSec, cookedSec, deliveredSec, destinations, travelTimes, expected);
  }

  @Test
  void threeOrdersTravelTimesCauseSplit() throws Exception {
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 120);
    travelTimes.put(restaurantAddress + "→DestB", 300);
    travelTimes.put(restaurantAddress + "→DestC", 600);
    travelTimes.put("DestA→DestB", 60);
    travelTimes.put("DestB→DestC", 120);

    List<Integer> initialSec = List.of(0, 0, 0);
    List<Integer> cookedSec = List.of(300, 360, 420); // 5,6,7 minutes
    // delivery times: 14,20,22 minutes → 840,1200,1320 seconds
    List<Integer> deliveredSec = List.of(840, 1200, 1320);
    List<String> destinations = List.of("DestA", "DestB", "DestC");
    List<List<Integer>> expected = List.of(List.of(2), List.of(0, 1));
    ordersNonUniform(initialSec, cookedSec, deliveredSec, destinations, travelTimes, expected);
  }

  @Test
  void threeOrdersTravelTimesCauseSplit2() throws Exception {
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 120);
    travelTimes.put(restaurantAddress + "→DestB", 300);
    travelTimes.put(restaurantAddress + "→DestC", 1200); // 20 minutes
    travelTimes.put("DestA→DestB", 60);
    travelTimes.put("DestB→DestC", 120);

    List<Integer> initialSec = List.of(0, 0, 0);
    List<Integer> cookedSec = List.of(300, 360, 420); // 5,6,7 minutes
    // delivery times: 18,30,34 minutes → 1080,1800,2040 seconds
    // List<Integer> deliveredSec = List.of(1080, 1080 + 60 + SECONDS_TO_HAND_DELIVER, 1080 + 60 +
    // SECONDS_TO_HAND_DELIVER + 119);
    List<Integer> deliveredSec =
        List.of(1080, 1200 + SECONDS_TO_HAND_DELIVER + 419, 1200 + SECONDS_TO_HAND_DELIVER + 420);

    List<String> destinations = List.of("DestA", "DestB", "DestC");
    List<List<Integer>> expected = List.of(List.of(0, 1), List.of(2));
    ordersNonUniform(initialSec, cookedSec, deliveredSec, destinations, travelTimes, expected);
  }
}
