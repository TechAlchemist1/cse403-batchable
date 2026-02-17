package com.batchable.backend.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.service.BatchingAlgorithm;
import com.batchable.backend.service.BatchingAlgorithm.TentativeBatch;
import com.batchable.backend.service.OrderService;
import com.batchable.backend.service.RouteService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BatchingAlgorithmTest {
  private RouteService mockRouteService;
  private OrderService mockOrderService;
  private BatchingAlgorithm batchingAlgorithm;
  private int SECONDS_TO_HAND_DELIVER;
  private int CEIL_MINS_TO_HAND_DELIVER;
  private long ORDER_ID;
  private String restaurantAddress;

  @BeforeEach
  void setUp() {
    mockRouteService = mock(RouteService.class);
    mockOrderService = mock(OrderService.class);
    batchingAlgorithm = new BatchingAlgorithm(mockRouteService, mockOrderService);
    SECONDS_TO_HAND_DELIVER = batchingAlgorithm.getSecondsToHandDeliver();
    CEIL_MINS_TO_HAND_DELIVER = (SECONDS_TO_HAND_DELIVER + 59) / 60;
    ORDER_ID = 1;
    restaurantAddress = "Restaurant A";
  }

  private Order getOrder(Instant initialTime, Instant cookedTime, Instant deliveryTime) {
    return new Order(ORDER_ID++, -1L, "", "", initialTime, deliveryTime, cookedTime, State.COOKING,
        false, -1L);
  }

  private Instant futureMinutes(int min) {
    return Instant.now().plus(Duration.ofMinutes(min));
  }

  // Helper
  private Instant getLastAllowedCookedTime(Order firstOrder, String restaurantAddress) {
    int firstDeliverySeconds =
        mockRouteService.getSecondsBetween(restaurantAddress, firstOrder.destination)
            + SECONDS_TO_HAND_DELIVER;
    Instant lastAllowedCookTime =
        firstOrder.deliveryTime.minus(Duration.ofSeconds(firstDeliverySeconds - 1));
    assertFalse(lastAllowedCookTime.isBefore(firstOrder.cookedTime));
    return lastAllowedCookTime;
  }

  // Helper
  void checkInvariants(List<TentativeBatch> batches, boolean checkEdges) {
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
          // assertEquals(getLastAllowedCookedTime(o, restaurantAddress), ct);
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

  @Test
  void testAddSingleOrderCreatesNewBatch() throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(120);
    Order order = getOrder(futureMinutes(0), futureMinutes(5),
        futureMinutes(5 + 2 + CEIL_MINS_TO_HAND_DELIVER));
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



  // Helper / Template
  void ordersUniform(List<Integer> initialTimes, List<Integer> cookedTimes,
      List<Integer> deliveredTimes, List<List<Integer>> expectedAnswerInds, int uniformTime)
      throws Exception {

    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(uniformTime);

    assertEquals(initialTimes.size(), cookedTimes.size(),
        "Mismatch: initialTimes and cookedTimes lists must be the same length");
    assertEquals(cookedTimes.size(), deliveredTimes.size(),
        "Mismatch: cookedTimes and deliveredTimes lists must be the same length");

    int n = initialTimes.size();

    List<TentativeBatch> batches = new ArrayList<>();
    List<Order> orders = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      Order order = getOrder(futureMinutes(initialTimes.get(i)), futureMinutes(cookedTimes.get(i)),
          futureMinutes(deliveredTimes.get(i)));
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
      assertDoesNotThrow(
        () -> batchingAlgorithm.updateOrderState(batches, order.id, State.COOKED));
      assertDoesNotThrow(
          () -> batchingAlgorithm.removeOrder(batches, order.id, restaurantAddress));
      assertThrows(IllegalArgumentException.class, 
        () -> batchingAlgorithm.removeOrder(batches, order.id, restaurantAddress));
      assertThrows(IllegalArgumentException.class, 
        () -> batchingAlgorithm.rebatchOrder(batches, order, restaurantAddress));
      assertThrows(IllegalArgumentException.class,
        () -> batchingAlgorithm.updateOrderState(batches, order.id, State.DELIVERED));
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


  @Test
  void twoOrders() throws Exception {
    int uniformTravelTime = 2 * 60; // 2 minutes in seconds
    int t = uniformTravelTime / 60 + CEIL_MINS_TO_HAND_DELIVER;
    List<Integer> initialTimes;
    List<Integer> cookedTimes;
    List<Integer> deliveredTimes;
    List<List<Integer>> expectedAnswerInds;

    initialTimes = List.of(0, 1); // minutes from now
    cookedTimes = List.of(5, 6); // when cooked
    deliveredTimes = List.of(6 + 1 * t, 6 + 2 * t); // delivery times
    expectedAnswerInds = List.of(List.of(0, 1));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    initialTimes = List.of(0, 1); // minutes from now
    cookedTimes = List.of(5, 6); // when cooked
    deliveredTimes = List.of(6 + 2 * t, 6 + t); // delivery times
    expectedAnswerInds = List.of(List.of(1, 0));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    initialTimes = List.of(0, 1); // minutes from now
    cookedTimes = List.of(5, 6); // when cooked
    deliveredTimes = List.of(6 + 2 * t - 1, 6 + t); // delivery times
    expectedAnswerInds = List.of(List.of(0), List.of(1));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    initialTimes = List.of(0, 1); // minutes from now
    cookedTimes = List.of(5, 6); // when cooked
    deliveredTimes = List.of(6 + 1 * t, 6 + 2 * t - 1); // delivery times
    expectedAnswerInds = List.of(List.of(1), List.of(0));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);
  }

  @Test
  void threeOrders() throws Exception {
    int uniformTravelTime = 2 * 60; // 2 minutes in seconds
    int t = uniformTravelTime / 60 + CEIL_MINS_TO_HAND_DELIVER; // minutes per leg
                                                                // (including
                                                                // hand‑deliver)

    List<Integer> initialTimes; // not used by algorithm, set to 0 for all orders
    List<Integer> cookedTimes;
    List<Integer> deliveredTimes;
    List<List<Integer>> expectedAnswerInds;

    // ---- Case 1: All three in one batch, ascending delivery order ----
    // cooked: 5,6,7 ; delivery: 7+t, 7+2t, 7+3t
    initialTimes = List.of(0, 0, 0);
    cookedTimes = List.of(5, 6, 7);
    deliveredTimes = List.of(7 + 1 * t, 7 + 2 * t, 7 + 3 * t);
    expectedAnswerInds = List.of(List.of(0, 1, 2));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    // ---- Case 2: All three in one batch, descending delivery order (batch sorted by delivery
    // time) ----
    // cooked: 5,6,7 ; delivery: 7+3t, 7+2t, 7+t
    deliveredTimes = List.of(7 + 3 * t, 7 + 2 * t, 7 + 1 * t);
    expectedAnswerInds = List.of(List.of(2, 1, 0)); // after sorting by delivery time: order2 (7+t),
                                                    // order1 (7+2t), order0 (7+3t)
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    // ---- Case 3: Two batches – first two together, third alone ----
    // cooked: 5,6,8 ; delivery: order0=7+t, order1=7+2t, order2=8+t
    // order0.latestAllowed = 7, order1.cooked=6 ≤7 (fits), order2.cooked=8 >7 → separate batch
    cookedTimes = List.of(5, 6, 8);
    deliveredTimes = List.of(7 + 1 * t, 7 + 2 * t, 8 + 1 * t);
    expectedAnswerInds = List.of(List.of(2), List.of(0, 1));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    // ---- Case 4: Three separate batches – each cooked after previous batch's latestAllowed ----
    // cooked: 5,7,9 ; delivery: 5+t, 7+t, 9+t
    cookedTimes = List.of(5, 7, 9);
    deliveredTimes = List.of(5 + 1 * t, 7 + 1 * t, 9 + 1 * t);
    expectedAnswerInds = List.of(List.of(2), List.of(1), List.of(0));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    // ---- Case 5: Two batches – first alone, second and third together ----
    // cooked: 5,8,9 ; delivery: order0=5+t, order1=9+t, order2=9+2t
    // order0.latestAllowed=5, order1.cooked=8 >5 → new batch with latestAllowed=9 (since
    // delivery1-t =9)
    // order2.cooked=9 ≤9, and canFollow(order1,order2) holds
    cookedTimes = List.of(5, 8, 9);
    deliveredTimes = List.of(5 + 1 * t, 9 + 1 * t, 9 + 2 * t);
    expectedAnswerInds = List.of(List.of(1, 2), List.of(0));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    // ---- Case 6: Two batches – orders 0 and 2 together, order1 alone ----
    // cooked: 5,7,6 ; delivery: order0=6+t, order1=7+t, order2=6+2t
    // order0.latestAllowed=6, order1.cooked=7 >6 → new batch, order2.cooked=6 ≤6 and
    // canFollow(order0,order2) holds
    cookedTimes = List.of(5, 7, 6);
    deliveredTimes = List.of(6 + 1 * t, 7 + 1 * t, 6 + 2 * t);
    expectedAnswerInds = List.of(List.of(1), List.of(0, 2));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    // ---- Case 7: Three separate batches due to hand‑deliver constraint ----
    cookedTimes = List.of(5, 5, 5);
    deliveredTimes = List.of(5 + 1 * t, 5 + 1 * t + 4, 5 + 1 * t + 3);
    expectedAnswerInds = List.of(List.of(1), List.of(2), List.of(0));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);
  }

  @Test
  void testAddOrdersCreatesMultipleBatchesWhenNecessary() throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(360);

    List<TentativeBatch> batches = new ArrayList<>();
    Order order1 = getOrder(futureMinutes(0), futureMinutes(1),
        futureMinutes(1 + 6 + CEIL_MINS_TO_HAND_DELIVER));
    Order order2 = getOrder(futureMinutes(5), futureMinutes(6),
        futureMinutes(6 + 6 + CEIL_MINS_TO_HAND_DELIVER));
    Order order3 = getOrder(futureMinutes(10), futureMinutes(11),
        futureMinutes(11 + 6 + CEIL_MINS_TO_HAND_DELIVER));

    batchingAlgorithm.addOrder(batches, order1, restaurantAddress);
    batchingAlgorithm.addOrder(batches, order2, restaurantAddress);
    batchingAlgorithm.addOrder(batches, order3, restaurantAddress);

    assertEquals(3, batches.size());
  }

  @Test
  void testOrderCannotFollowPreviousDueToDeliveryTime() throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenReturn(10);

    List<TentativeBatch> batches = new ArrayList<>();
    Order order1 =
        getOrder(futureMinutes(0), futureMinutes(0), futureMinutes(1 + CEIL_MINS_TO_HAND_DELIVER));
    Order order2 =
        getOrder(futureMinutes(0), futureMinutes(0), futureMinutes(1 + CEIL_MINS_TO_HAND_DELIVER)); // same
                                                                                                    // delivery
    // time

    batchingAlgorithm.addOrder(batches, order1, restaurantAddress);
    batchingAlgorithm.addOrder(batches, order2, restaurantAddress);

    assertEquals(2, batches.size(),
        "Orders with conflicting delivery times should be in separate batches");
  }


  @Test
  void tenOrdersAllInOneBatch() throws Exception {
    int uniformTravelTime = 2 * 60; // 2 minutes in seconds
    int t = uniformTravelTime / 60 + CEIL_MINS_TO_HAND_DELIVER; // ≈7 minutes

    List<Integer> initialTimes = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
    List<Integer> deliveredTimes = List.of(14 + 1 * t, 14 + 2 * t, 14 + 3 * t, 14 + 4 * t,
        14 + 5 * t, 14 + 6 * t, 14 + 7 * t, 14 + 8 * t, 14 + 9 * t, 14 + 10 * t);
    List<List<Integer>> expectedAnswerInds = List.of(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    initialTimes = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    cookedTimes = List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
    deliveredTimes = List.of(14 + 5 * t, 14 + 7 * t, 14 + 2 * t, 14 + 8 * t, 14 + 1 * t, 14 + 4 * t,
        14 + 9 * t, 14 + 6 * t, 14 + 10 * t, 14 + 3 * t);
    expectedAnswerInds = List.of(List.of(4, 2, 9, 5, 0, 7, 1, 3, 6, 8));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);

    initialTimes = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    cookedTimes = List.of(5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
    deliveredTimes = List.of(14 + 10 * t, 14 + 9 * t, 14 + 8 * t, 14 + 7 * t, 14 + 6 * t,
        14 + 5 * t, 14 + 4 * t, 14 + 3 * t, 14 + 2 * t, 14 + 1 * t);
    expectedAnswerInds = List.of(List.of(9, 8, 7, 6, 5, 4, 3, 2, 1, 0));
    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);
  }

  @Test
  void tenOrdersTwoBatchesEvenOdd() throws Exception {
    int uniformTravelTime = 2 * 60;
    int t = uniformTravelTime / 60 + CEIL_MINS_TO_HAND_DELIVER; // ≈7

    // Even indices: 0,2,4,6,8 (cooked 5‑9, deliveries 10+t … 10+5t)
    // Odd indices: 1,3,5,7,9 (cooked 11‑15, deliveries 16+t … 16+5t)
    List<Integer> initialTimes = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 11, 6, 12, 7, 13, 8, 14, 9, 15);
    List<Integer> deliveredTimes = List.of(10 + 1 * t, 16 + 1 * t, 10 + 2 * t, 16 + 2 * t,
        10 + 3 * t, 16 + 3 * t, 10 + 4 * t, 16 + 4 * t, 10 + 5 * t, 16 + 5 * t);
    // Expected: first batch (latestAllowed=16) contains odds, second batch (latestAllowed=10)
    // contains evens
    List<List<Integer>> expectedAnswerInds = List.of(List.of(1, 3, 5, 7, 9), // odds in delivery
                                                                             // order
        List.of(0, 2, 4, 6, 8) // evens in delivery order
    );

    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);
  }

  @Test
  void tenOrdersThreeBatchesByCookedTime() throws Exception {
    int uniformTravelTime = 2 * 60;
    int t = uniformTravelTime / 60 + CEIL_MINS_TO_HAND_DELIVER; // ≈7

    // Group A (indices 0‑1): cooked 5‑6, deliveries 7+t, 7+2t
    // Group B (indices 2‑4): cooked 8‑10, deliveries 11+t, 11+2t, 11+3t
    // Group C (indices 5‑9): cooked 12‑16, deliveries 17+t … 17+5t
    List<Integer> initialTimes = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 6, 8, 9, 10, 12, 13, 14, 15, 16);
    List<Integer> deliveredTimes = List.of(7 + 1 * t, 7 + 2 * t, 11 + 1 * t, 11 + 2 * t, 11 + 3 * t,
        17 + 1 * t, 17 + 2 * t, 17 + 3 * t, 17 + 4 * t, 17 + 5 * t);
    // Expected batches sorted by latestAllowed descending:
    // Batch C (latest=17) first, then B (latest=11), then A (latest=7)
    List<List<Integer>> expectedAnswerInds = List.of(List.of(5, 6, 7, 8, 9), // group C
        List.of(2, 3, 4), // group B
        List.of(0, 1) // group A
    );

    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);
  }

  @Test
  void fiftyOrdersTenBatchesReordered() throws Exception {
    int uniformTravelTime = 2 * 60; // 2 minutes in seconds
    int t = uniformTravelTime / 60 + CEIL_MINS_TO_HAND_DELIVER; // minutes per leg (≈7)

    List<Integer> initialTimes = new ArrayList<>();
    List<Integer> cookedTimes = new ArrayList<>();
    List<Integer> deliveredTimes = new ArrayList<>();

    // Create 10 batches of 5 orders each, with increasing latestAllowedCookedTime.
    // Add batches in order of increasing L (batch0 smallest L, batch9 largest L).
    // This forces reordering because each new batch has larger L and will be inserted at the front.
    for (int batch = 0; batch < 10; batch++) {
      int L = 10 + 2 * batch; // latestAllowedCookedTime for this batch (minutes)
      for (int j = 0; j < 5; j++) {
        initialTimes.add(0);
        // cooked times: L-5 .. L-1 (all <= L)
        cookedTimes.add(L - 5 + j);
        // delivery times: L + (j+1)*t (first delivery at L+t, last at L+5t)
        deliveredTimes.add(L + (j + 1) * t);
      }
    }

    // Expected batches in descending L order (batch9 first, batch0 last).
    // Within each batch, orders appear in delivery order (j=0..4).
    List<List<Integer>> expectedAnswerInds = new ArrayList<>();
    for (int batch = 9; batch >= 0; batch--) {
      List<Integer> batchIndices = new ArrayList<>();
      int startIdx = batch * 5;
      for (int j = 0; j < 5; j++) {
        batchIndices.add(startIdx + j);
      }
      expectedAnswerInds.add(batchIndices);
    }

    ordersUniform(initialTimes, cookedTimes, deliveredTimes, expectedAnswerInds, uniformTravelTime);
  }


  // Helper to create an order with a specific destination
  private Order getOrder(Instant initialTime, Instant cookedTime, Instant deliveryTime,
      String destination) {
    Order order = new Order(ORDER_ID++, -1L, destination, "", initialTime, deliveryTime, cookedTime,
        State.COOKING, false, -1L);
    return order;
  }

  /**
   * Sets up the mock to return specific travel times for given origin→destination pairs. The map
   * keys are strings like "origin→destination" and values are seconds (excluding hand‑deliver
   * overhead).
   */
  private void mockTravelTimes(Map<String, Integer> travelTimeMap) throws Exception {
    when(mockRouteService.getSecondsBetween(anyString(), anyString())).thenAnswer(invocation -> {
      String from = invocation.getArgument(0);
      String to = invocation.getArgument(1);
      String key = from + "→" + to;
      if (travelTimeMap.containsKey(key)) {
        System.out.println("GETTING " + key + " " + "time: " + travelTimeMap.get(key));
        return travelTimeMap.get(key);
      }
      throw new IllegalArgumentException("Unexpected route: " + key);
    });
  }

  // A flexible test method similar to ordersUniform but accepting a travel time map and a list of
  // destinations
  void ordersNonUniform(List<Integer> initialTimes, List<Integer> cookedTimes,
      List<Integer> deliveredTimes, List<String> destinations, Map<String, Integer> travelTimeMap,
      List<List<Integer>> expectedAnswerInds) throws Exception {

    assertEquals(initialTimes.size(), cookedTimes.size(),
        "Mismatch: initialTimes and cookedTimes sizes");
    assertEquals(cookedTimes.size(), deliveredTimes.size(),
        "Mismatch: cookedTimes and deliveredTimes sizes");
    assertEquals(deliveredTimes.size(), destinations.size(),
        "Mismatch: deliveredTimes and destinations sizes");

    mockTravelTimes(travelTimeMap);

    int n = initialTimes.size();
    List<TentativeBatch> batches = new ArrayList<>();
    List<Order> orders = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      Order order = getOrder(futureMinutes(initialTimes.get(i)), futureMinutes(cookedTimes.get(i)),
          futureMinutes(deliveredTimes.get(i)), destinations.get(i));
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
  // Non‑uniform travel time tests
  // ---------------------------------------------------------------------

  @Test
  void twoOrdersDifferentTravelTimesStillBatch() throws Exception {
    // Two orders, both cooked at 5 min. Destinations: A and B.
    // Travel times: Restaurant→A = 2 min, Restaurant→B = 3 min
    // delivery windows wide enough to allow both in one batch.
    int tA = 2 * 60; // 120 s
    int tB = 3 * 60; // 180 s
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", tA);
    travelTimes.put(restaurantAddress + "→DestB", tB);
    travelTimes.put("DestA→DestB", 1 * 60); // 1 min between destinations

    List<Integer> initialTimes = List.of(0, 0);
    List<Integer> cookedTimes = List.of(5, 5);
    List<Integer> deliveredTimes = List.of(5 + tA / 60 + CEIL_MINS_TO_HAND_DELIVER, // delivery
                                                                                    // A =
                                                                                    // cooked +
                                                                                    // travelA+hand
                                                                                    // + buffer
        5 + tA / 60 + CEIL_MINS_TO_HAND_DELIVER + 1 + CEIL_MINS_TO_HAND_DELIVER // delivery B =
                                                                                // later
    );
    List<String> destinations = List.of("DestA", "DestB");
    List<List<Integer>> expected = List.of(List.of(0, 1)); // both in one batch

    ordersNonUniform(initialTimes, cookedTimes, deliveredTimes, destinations, travelTimes,
        expected);
  }

  @Test
  void twoOrdersDifferentTravelTimesSeparateBatches() throws Exception {
    // Travel to B is very long, causing the batch's latestAllowedCookedTime to be too early for
    // order1.
    int tA = 2 * 60;
    int tB = 15 * 60; // 15 minutes
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", tA);
    travelTimes.put(restaurantAddress + "→DestB", tB);
    // Not needed for this scenario because they won't be in same batch.

    List<Integer> initialTimes = List.of(0, 0);
    List<Integer> cookedTimes = List.of(5, 7);
    List<Integer> deliveredTimes = List.of(5 + tA / 60 + CEIL_MINS_TO_HAND_DELIVER, // delivery A
                                                                                    // exactly at
                                                                                    // earliest
                                                                                    // possible
        7 + tB / 60 + CEIL_MINS_TO_HAND_DELIVER // delivery B exactly at earliest possible
    );
    List<String> destinations = List.of("DestA", "DestB");
    List<List<Integer>> expected = List.of(List.of(1), List.of(0)); // separate batches

    ordersNonUniform(initialTimes, cookedTimes, deliveredTimes, destinations, travelTimes,
        expected);
  }

  @Test
  void threeOrdersMixedTravelTimesReordering() throws Exception {
    // Orders with different travel times that cause reordering of batches.
    // Order0: dest A (short travel), cooked 5, delivery 10
    // Order1: dest B (medium travel), cooked 6, delivery 14
    // Order2: dest C (long travel), cooked 7, delivery 18
    // Travel times: R→A=2min, R→B=5min, R→C=10min
    // When added in order 0,1,2, they should form a single batch because latestAllowed for batch0
    // is computed from first order.
    // But after adding order2, if travel times cause constraints, may split.
    // We'll design so they fit together.
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 2 * 60);
    travelTimes.put(restaurantAddress + "→DestB", 5 * 60);
    travelTimes.put(restaurantAddress + "→DestC", 10 * 60);
    // Inter-destination times for the sequence A→B, B→C
    travelTimes.put("DestA→DestB", 1 * 60);
    travelTimes.put("DestB→DestC", 2 * 60);

    List<Integer> initialTimes = List.of(0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 6, 7);
    List<Integer> deliveredTimes = List.of(14, 20, 27);
    List<String> destinations = List.of("DestA", "DestB", "DestC");
    List<List<Integer>> expected = List.of(List.of(0, 1, 2)); // all together

    ordersNonUniform(initialTimes, cookedTimes, deliveredTimes, destinations, travelTimes,
        expected);
  }

  @Test
  void threeOrdersMixedTravelTimesReordering2() throws Exception {
    // Orders with different travel times that cause reordering of batches.
    // Order0: dest A (short travel), cooked 5, delivery 10
    // Order1: dest B (medium travel), cooked 6, delivery 14
    // Order2: dest C (long travel), cooked 7, delivery 18
    // Travel times: R→A=2min, R→B=5min, R→C=10min
    // When added in order 0,1,2, they should form a single batch because latestAllowed for batch0
    // is computed from first order.
    // But after adding order2, if travel times cause constraints, may split.
    // We'll design so they fit together.
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 2 * 60);
    travelTimes.put(restaurantAddress + "→DestC", 10 * 60);
    travelTimes.put(restaurantAddress + "→DestB", 5 * 60);
    // Inter-destination times for the sequence A→B, B→C
    travelTimes.put("DestA→DestC", 3 * 60);
    travelTimes.put("DestA→DestB", 1 * 60);
    travelTimes.put("DestB→DestC", 2 * 60);

    List<Integer> initialTimes = List.of(0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 7, 6);
    List<Integer> deliveredTimes = List.of(14, 27, 20);
    List<String> destinations = List.of("DestA", "DestC", "DestB");
    List<List<Integer>> expected = List.of(List.of(0, 2, 1)); // all together

    ordersNonUniform(initialTimes, cookedTimes, deliveredTimes, destinations, travelTimes,
        expected);
  }

  @Test
  void threeOrdersTravelTimesCauseSplit() throws Exception {
    // Similar but order2 has too tight delivery, cannot follow order1.
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 2 * 60);
    travelTimes.put(restaurantAddress + "→DestB", 5 * 60);
    travelTimes.put(restaurantAddress + "→DestC", 10 * 60);
    travelTimes.put("DestA→DestB", 1 * 60);
    travelTimes.put("DestB→DestC", 2 * 60);

    List<Integer> initialTimes = List.of(0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 6, 7);
    List<Integer> deliveredTimes = List.of(14, 20, 22); // too early for after B
    List<String> destinations = List.of("DestA", "DestB", "DestC");
    // Expected: order0 and order1 together, order2 separate
    List<List<Integer>> expected = List.of(List.of(2), List.of(0, 1));

    ordersNonUniform(initialTimes, cookedTimes, deliveredTimes, destinations, travelTimes,
        expected);
  }

  @Test
  void threeOrdersTravelTimesCauseSplit2() throws Exception {
    // Similar but order2 has too tight delivery, cannot follow order1.
    Map<String, Integer> travelTimes = new HashMap<>();
    travelTimes.put(restaurantAddress + "→DestA", 2 * 60);
    travelTimes.put(restaurantAddress + "→DestB", 5 * 60);
    travelTimes.put(restaurantAddress + "→DestC", 20 * 60);
    travelTimes.put("DestA→DestB", 1 * 60);
    travelTimes.put("DestB→DestC", 2 * 60);

    List<Integer> initialTimes = List.of(0, 0, 0);
    List<Integer> cookedTimes = List.of(5, 6, 7);
    List<Integer> deliveredTimes = List.of(18, 30, 34); // too early for after B
    List<String> destinations = List.of("DestA", "DestB", "DestC");
    // Expected: order0 and order1 together, order2 separate
    List<List<Integer>> expected = List.of(List.of(0, 1), List.of(2));

    ordersNonUniform(initialTimes, cookedTimes, deliveredTimes, destinations, travelTimes,
        expected);
  }
}
