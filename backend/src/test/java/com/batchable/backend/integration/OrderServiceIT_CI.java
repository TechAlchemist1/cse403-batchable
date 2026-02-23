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
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.websocket.OrderWebSocketPublisher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Integration tests for OrderService using real Postgres (Testcontainers) and real DAOs. This test
 * verifies that OrderService correctly: - Persists orders and updates via the database. - Enforces
 * domain rules (lifecycle transitions, invariants). - Publishes WebSocket messages via
 * OrderWebSocketPublisher (with mocked SimpMessagingTemplate). - Delegates to BatchingManager
 * (mocked) where appropriate. The database is cleaned before each test, and all dependencies except
 * the messaging template are real.
 */
public class OrderServiceIT_CI extends PostgresTestBase {

  @Mock
  private BatchingManager mockBatchingManager; // not directly used in these tests (DbOrderService
                                               // doesn't use it)

  private Connection c;

  private RestaurantDAO restaurantDAO;
  private OrderDAO orderDAO;
  private BatchDAO batchDAO;

  private SimpMessagingTemplate messagingTemplate; // mock
  private OrderWebSocketPublisher publisher; // real, but with mocked template
  private DbOrderService service; // the service under test

  private TestDataSource ds;

  @BeforeEach
  void setUp() throws Exception {
    // Initialize mocks and obtain the test database connection.
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
    service = new DbOrderService(orderDAO, batchDAO, publisher);

    // Clean all tables before each test to ensure isolation.
    cleanupTables();
  }

  /** Truncates all relevant tables to start each test with a clean database. */
  private void cleanupTables() throws SQLException {
    try (var st = c.createStatement()) {
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    }
  }

  // ---------- helper inserts (avoid relying on other services) ----------

  /** Creates a restaurant row and returns its ID. */
  private long createRestaurant(String name) throws SQLException {
    return restaurantDAO.createRestaurant(name, "Seattle");
  }

  /** Creates an order row directly with the given state and returns its ID. */
  private long createOrderRow(long restaurantId, Order.State state) throws SQLException {
    return orderDAO.createOrder(restaurantId, "123 Pike St", "[\"Burger\"]", Instant.now(),
        Instant.now().plus(Duration.ofMinutes(20)), Instant.now().plus(Duration.ofMinutes(10)),
        state, false, null);
  }

  /** Creates a driver row (off shift) and returns its ID. */
  private long createDriverRow(long restaurantId) throws SQLException {
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

  /** Creates a batch row for the given driver and returns its ID. */
  private long createBatchRow(long driverId) throws SQLException {
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

  /**
   * Verifies that a valid order is persisted correctly and a WebSocket message is published.
   */
  @Test
  void createOrder_happyPath_persists_andPublishes() throws Exception {
    long rid = createRestaurant("R1");

    Order o = new Order(0L, rid, "123 Pike St", "[\"Burger\",\"Fries\"]", Instant.now(),
        Instant.now().plus(Duration.ofMinutes(13)), Instant.now().plus(Duration.ofMinutes(6)),
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

  /**
   * Tests the full order lifecycle (COOKING → COOKED → DRIVING → DELIVERED). Verifies that the
   * state advances correctly and a WebSocket message is published at each step.
   */
  @Test
  void advanceOrderState_movesAlongLifecycle_andPublishesEachTime() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    service.advanceOrderState(oid);
    assertEquals(Order.State.COOKED, service.getOrder(oid).state);

    service.advanceOrderState(oid);
    assertEquals(Order.State.DRIVING, service.getOrder(oid).state);

    service.advanceOrderState(oid);
    Order delivered = service.getOrder(oid);
    assertEquals(Order.State.DELIVERED, delivered.state);
    assertNotNull(delivered.deliveryTime);

    verify(messagingTemplate, times(5)).convertAndSend("/topic/orders/" + rid, "");
  }

  /**
   * Verifies that updating the cooked time works and triggers a WebSocket notification.
   */
  @Test
  void updateOrderCookedTime_setsCookedTime_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    Instant cooked = service.getOrder(oid).initialTime.plusSeconds(120);
    service.updateOrderCookedTime(oid, cooked);

    assertEquals(cooked, service.getOrder(oid).cookedTime);
    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  /**
   * Verifies that an order can be remade (reset to COOKING, high priority) and that a WebSocket
   * message is published.
   */
  @Test
  void remakeOrder_resetsState_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.DRIVING);

    service.remakeOrder(oid);

    Order got = service.getOrder(oid);
    assertEquals(Order.State.COOKING, got.state);
    assertTrue(got.highPriority);
    assertNotNull(got.cookedTime);
    assertNotNull(got.deliveryTime);
    assertNull(got.batchId);

    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  /**
   * Verifies that an order can be removed (deleted) and that a WebSocket message is published.
   */
  @Test
  void removeOrder_deletes_andPublishes() throws Exception {
    long rid = createRestaurant("R1");
    long oid = createOrderRow(rid, Order.State.COOKING);

    service.removeOrder(oid);

    assertThrows(IllegalArgumentException.class, () -> service.getOrder(oid));
    verify(messagingTemplate, times(1)).convertAndSend("/topic/orders/" + rid, "");
  }

  /**
   * Verifies that an order can be assigned to a batch and that a WebSocket message is published.
   */
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
