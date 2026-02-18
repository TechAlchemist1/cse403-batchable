package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.OrderService;
import com.batchable.backend.websocket.OrderWebSocketPublisher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Integration tests for OrderService: - real Postgres via Testcontainers (PostgresTestBase) - real
 * DAOs - real OrderWebSocketPublisher, but with mocked SimpMessagingTemplate so we can verify
 * publishes
 */
public class OrderServiceIT_CI extends PostgresTestBase {
  @Mock
  private BatchingManager mockBatchingManager;
  private Connection c;

  private RestaurantDAO restaurantDAO;
  private OrderDAO orderDAO;
  private BatchDAO batchDAO;

  private SimpMessagingTemplate messagingTemplate; // mock
  private OrderWebSocketPublisher publisher; // real
  private OrderService service; // real

  private TestDataSource ds;

  @BeforeEach
  void setUp() throws Exception {
    // PostgresTestBase provides this per-test
    MockitoAnnotations.openMocks(this);
    c = conn;
    assertNotNull(c, "PostgresTestBase.conn is null — did @BeforeAll run?");

    // Wrap the Connection as a DataSource so DAOs match the new constructor signature.
    ds = new TestDataSource(c);

    restaurantDAO = new RestaurantDAO(ds);
    orderDAO = new OrderDAO(ds);
    batchDAO = new BatchDAO(ds);

    messagingTemplate = mock(SimpMessagingTemplate.class);
    publisher = new OrderWebSocketPublisher(messagingTemplate);

    service = new OrderService(orderDAO, batchDAO, publisher, mockBatchingManager);

    // Optional: keep each test isolated if you want.
    // If you already clean tables elsewhere, remove this.
    cleanupTables();
  }

  private void cleanupTables() throws SQLException {
    // Order depends on Batch and Restaurant. Batch depends on Driver.
    // If your schema uses quoted names like "Order", you must quote them here too.
    // Use TRUNCATE ... CASCADE if your schema supports it.
    try (var st = c.createStatement()) {
      // Match your DAO SQL which uses: Driver, Batch, Restaurant, and "Order"
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    }
  }

  // ---------- helper inserts (avoid relying on other services) ----------

  private long createRestaurant(String name) throws SQLException {
    // Your DAO uses Restaurant in SQL
    return restaurantDAO.createRestaurant(name, "Seattle");
  }

  private long createOrderRow(long restaurantId, Order.State state) throws SQLException {
    return orderDAO.createOrder(restaurantId, "123 Pike St", "[\"Burger\"]", Instant.now(), null,
        null, state, false, null);
  }

  private long createDriverRow(long restaurantId) throws SQLException {
    // Match your DriverDAO SQL: INSERT INTO Driver(...)
    final String sql = "INSERT INTO Driver(restaurant_id, name, phone_number, on_shift) "
        + "VALUES (?, ?, ?, ?) RETURNING id;";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, restaurantId);
      ps.setString(2, "Driver One");
      ps.setString(3, "206-555-0100");
      ps.setBoolean(4, false);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong("id");
      }
    }
  }

  private long createBatchRow(long driverId) throws SQLException {
    // Match your BatchDAO SQL: INSERT INTO Batch(...)
    final String sql =
        "INSERT INTO Batch(driver_id, route, dispatch_time, expected_completion_time) "
            + "VALUES (?, ?, ?, ?) RETURNING id;";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      ps.setString(2, "");

      Instant dispatch = Instant.now();
      Instant expected = dispatch.plusSeconds(600);

      ps.setTimestamp(3, Timestamp.from(dispatch));
      ps.setTimestamp(4, Timestamp.from(expected));

      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getLong("id");
      }
    }
  }

  // ---------- tests ----------

  @Test
  void createOrder_happyPath_persists_andPublishes() throws Exception {
    long rid = createRestaurant("R1");

    Order o = new Order(0L, rid, "123 Pike St", "[\"Burger\",\"Fries\"]", Instant.now(), null, null,
        Order.State.COOKING, false, null);

    long id = service.createOrder(o);
    assertTrue(id > 0);

    Order got = service.getOrder(id);
    assertEquals(rid, got.restaurantId);
    assertEquals("123 Pike St", got.destination);
    assertEquals("[\"Burger\",\"Fries\"]", got.itemNamesJson);
    assertEquals(Order.State.COOKING, got.state);

    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  @Test
  void advanceOrderState_movesAlongLifecycle_andPublishesEachTime() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    service.advanceOrderState(oid, true);
    assertEquals(Order.State.COOKED, service.getOrder(oid).state);

    service.advanceOrderState(oid, true);
    assertEquals(Order.State.DRIVING, service.getOrder(oid).state);

    service.advanceOrderState(oid, true);
    Order delivered = service.getOrder(oid);
    assertEquals(Order.State.DELIVERED, delivered.state);
    assertNotNull(delivered.deliveryTime);

    verify(messagingTemplate, times(3)).convertAndSend("/topic/orders/" + rid, "");
  }

  @Test
  void updateOrderCookedTime_setsCookedTime_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    Instant cooked = service.getOrder(oid).initialTime.plusSeconds(120);
    service.updateOrderCookedTime(oid, cooked, true);

    assertEquals(cooked, service.getOrder(oid).cookedTime);
    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  @Test
  void remakeOrder_resetsState_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.DRIVING);

    service.remakeOrder(oid, true);

    Order got = service.getOrder(oid);
    assertEquals(Order.State.COOKING, got.state);
    assertTrue(got.highPriority);
    assertNull(got.cookedTime);
    assertNull(got.deliveryTime);
    assertNull(got.batchId);

    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  @Test
  void removeOrder_deletes_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    service.removeOrder(oid);

    assertThrows(IllegalArgumentException.class, () -> service.getOrder(oid));
    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  @Test
  void setOrderBatchId_assignsBatch_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    long driverId = createDriverRow(rid);
    long batchId = createBatchRow(driverId);

    service.setOrderBatchId(oid, batchId);

    Order got = service.getOrder(oid);
    assertNotNull(got.batchId);
    assertEquals(batchId, got.batchId.longValue());

    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }
}
