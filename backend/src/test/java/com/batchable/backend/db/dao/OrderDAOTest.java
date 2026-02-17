package com.batchable.backend.db.dao;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.models.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class OrderDAOTest extends PostgresTestBase {

  private TestDataSource ds;
  private OrderDAO orderDAO;

  @BeforeEach
  void setUp() throws Exception {
    ds = new TestDataSource(conn);
    orderDAO = new OrderDAO(ds);
    cleanDb();
  }

  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    }
  }

  private static Instant micros(Instant t) {
    return (t == null) ? null : t.truncatedTo(ChronoUnit.MICROS);
  }

  private static long insertRestaurant(String name) throws Exception {
    try (PreparedStatement ps =
        conn.prepareStatement("INSERT INTO Restaurant(name, location) VALUES (?, ?) RETURNING id;")) {
      ps.setString(1, name);
      ps.setString(2, "Seattle");
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong("id");
      }
    }
  }

  private static long insertDriver(long restaurantId, String name) throws Exception {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "INSERT INTO Driver(restaurant_id, name, phone_number, on_shift) VALUES (?, ?, ?, ?) RETURNING id;")) {
      ps.setLong(1, restaurantId);
      ps.setString(2, name);
      ps.setString(3, "555-555-5555");
      ps.setBoolean(4, true);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong("id");
      }
    }
  }

  private static long insertBatch(long driverId) throws Exception {
    Instant dispatch = micros(Instant.now());
    Instant expected = micros(dispatch.plusSeconds(900));
    try (PreparedStatement ps =
        conn.prepareStatement(
            "INSERT INTO Batch(driver_id, route, dispatch_time, expected_completion_time) " +
                "VALUES (?, ?, ?, ?) RETURNING id;")) {
      ps.setLong(1, driverId);
      ps.setString(2, "encodedpolyline");
      ps.setTimestamp(3, java.sql.Timestamp.from(dispatch));
      ps.setTimestamp(4, java.sql.Timestamp.from(expected));
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong("id");
      }
    }
  }

  @Test
  void createOrder_returnsId_andGetOrderWorks() throws Exception {
    long rid = insertRestaurant("R1");

    Instant t0 = micros(Instant.now());

    long id =
        orderDAO.createOrder(
            rid,
            "123 Main St",
            "[\"Burger\"]",
            t0,
            null,
            null,
            Order.State.COOKING,
            false,
            null);

    assertTrue(id > 0);

    Optional<Order> got = orderDAO.getOrder(id);
    assertTrue(got.isPresent());

    Order o = got.get();
    assertEquals(id, o.id);
    assertEquals(rid, o.restaurantId);
    assertEquals("123 Main St", o.destination);
    assertEquals("[\"Burger\"]", o.itemNamesJson);
    assertEquals(t0, o.initialTime);
    assertNull(o.deliveryTime);
    assertNull(o.cookedTime);
    assertEquals(Order.State.COOKING, o.state);
    assertFalse(o.highPriority);
    assertNull(o.batchId);
  }

  @Test
  void updateOrderState_updatesExactlyOneRow() throws Exception {
    long rid = insertRestaurant("R1");
    Instant t0 = micros(Instant.now());

    long id =
        orderDAO.createOrder(
            rid, "Addr", "[]", t0,
            null, null,
            Order.State.COOKING, false, null);

    assertTrue(orderDAO.updateOrderState(id, Order.State.COOKED));

    Order o = orderDAO.getOrder(id).orElseThrow();
    assertEquals(Order.State.COOKED, o.state);
  }

  @Test
  void updateCookedAndDeliveryTime_updatesExactlyOneRow() throws Exception {
    long rid = insertRestaurant("R1");

    Instant t0 = micros(Instant.now());
    Instant cooked = micros(t0.plusSeconds(600));
    Instant delivered = micros(t0.plusSeconds(1800));

    long id =
        orderDAO.createOrder(
            rid, "Addr", "[]", t0,
            null, null,
            Order.State.COOKING, false, null);

    assertTrue(orderDAO.updateOrderCookedTime(id, cooked));
    assertTrue(orderDAO.updateOrderDeliveryTime(id, delivered));

    Order o = orderDAO.getOrder(id).orElseThrow();
    assertEquals(cooked, o.cookedTime);
    assertEquals(delivered, o.deliveryTime);
  }

  @Test
  void remakeOrder_resetsFields_stateAndPriority_andClearsTimesAndBatch() throws Exception {
    long rid = insertRestaurant("R1");

    Instant t0 = micros(Instant.now());
    Instant cooked = micros(t0.plusSeconds(100));
    Instant delivered = micros(t0.plusSeconds(200));

    long id =
        orderDAO.createOrder(
            rid, "Addr", "[]", t0,
            delivered, cooked,
            Order.State.DELIVERED, false, null);

    assertTrue(orderDAO.remakeOrder(id, Order.State.COOKING, true));

    Order o = orderDAO.getOrder(id).orElseThrow();
    assertEquals(Order.State.COOKING, o.state);
    assertTrue(o.highPriority);
    assertNull(o.cookedTime);
    assertNull(o.deliveryTime);
    assertNull(o.batchId);
  }

  @Test
  void deleteOrder_deletes_andReturnsTrue_thenFalseIfRepeated() throws Exception {
    long rid = insertRestaurant("R1");
    Instant t0 = micros(Instant.now());

    long id =
        orderDAO.createOrder(
            rid, "Addr", "[]", t0,
            null, null,
            Order.State.COOKING, false, null);

    assertTrue(orderDAO.deleteOrder(id));
    assertTrue(orderDAO.getOrder(id).isEmpty());
    assertFalse(orderDAO.deleteOrder(id));
  }

  @Test
  void listOpenOrdersForRestaurant_excludesDelivered() throws Exception {
    long rid = insertRestaurant("R1");
    Instant t0 = micros(Instant.now());

    orderDAO.createOrder(rid, "A", "[]", t0, null, null, Order.State.COOKING, false, null);
    orderDAO.createOrder(rid, "B", "[]", t0, null, null, Order.State.DELIVERED, false, null);

    List<Order> open = orderDAO.listOpenOrdersForRestaurant(rid);

    assertEquals(1, open.size());
    assertEquals(Order.State.COOKING, open.get(0).state);
  }

  @Test
  void updateOrderBatchId_andClearBatchId_work() throws Exception {
    long rid = insertRestaurant("R1");
    long driverId = insertDriver(rid, "D1");
    long batchId = insertBatch(driverId);

    Instant t0 = micros(Instant.now());
    long orderId =
        orderDAO.createOrder(
            rid, "Addr", "[]", t0,
            null, null,
            Order.State.COOKING, false, null);

    assertTrue(orderDAO.updateOrderBatchId(orderId, batchId));

    Order o = orderDAO.getOrder(orderId).orElseThrow();
    assertEquals(batchId, o.batchId);

    assertTrue(orderDAO.clearOrderBatchId(orderId));

    o = orderDAO.getOrder(orderId).orElseThrow();
    assertNull(o.batchId);
  }

  @Test
  void hasActiveOrdersForRestaurant_trueWhenNonDeliveredExists() throws Exception {
    long rid = insertRestaurant("R1");
    Instant t0 = micros(Instant.now());

    assertFalse(orderDAO.hasActiveOrdersForRestaurant(rid));

    orderDAO.createOrder(
        rid, "Addr", "[]", t0,
        null, null,
        Order.State.COOKING, false, null);

    assertTrue(orderDAO.hasActiveOrdersForRestaurant(rid));
  }
}
