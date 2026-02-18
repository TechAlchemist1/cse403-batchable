package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.RestaurantService;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

/**
 * Integration tests for {@link RestaurantService} using a real Postgres database (Testcontainers)
 * and real DAO implementations.
 *
 * Verifies that: - Flyway migrations have been applied and the database schema is correct. - DAOs
 * correctly interact with the database. - RestaurantService enforces domain rules: - Restaurant
 * names must be unique. - Updating a restaurant only changes allowed fields. - Deletion is blocked
 * if there are active orders or on‑shift drivers. - Retrieval methods (orders, drivers, menu items)
 * return correct data.
 *
 * The database is truncated before each test to ensure isolation.
 */
public class RestaurantServiceIT_CI extends PostgresTestBase {
  private DataSource ds;

  private RestaurantDAO restaurantDAO;
  private OrderDAO orderDAO;
  private DriverDAO driverDAO;
  private MenuItemDAO menuItemDAO;
  private RestaurantService restaurantService;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    ds = new TestDataSource(conn);

    restaurantDAO = new RestaurantDAO(ds);
    orderDAO = new OrderDAO(ds);
    driverDAO = new DriverDAO(ds);
    menuItemDAO = new MenuItemDAO(ds);

    restaurantService = new RestaurantService(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
    cleanDb();
  }

  /**
   * Truncates all tables used in the tests, restarting identity sequences. Order of truncation
   * respects foreign key constraints.
   */
  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"menu_item\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    }
  }

  // ---------- helpers ----------

  /**
   * Creates a restaurant using the service and returns its generated ID.
   *
   * @param name restaurant name
   * @param location restaurant location
   * @return generated ID
   */
  private long createRestaurant(String name, String location) {
    return restaurantService.createRestaurant(new Restaurant(0, name, location));
  }

  /**
   * Inserts a driver directly via DAO (bypassing service) with the given shift status.
   *
   * @param restaurantId owning restaurant
   * @param onShift shift status
   * @return generated driver ID
   */
  private long createDriver(long restaurantId, boolean onShift) throws Exception {
    return driverDAO.createDriver(restaurantId, "Driver1", "+1 (206) 555-1234", onShift);
  }

  /**
   * Inserts an order in a non‑delivered state (COOKING) directly via DAO.
   *
   * @param restaurantId owning restaurant
   * @return generated order ID
   */
  private long createActiveOrder(long restaurantId) throws Exception {
    Instant t0 = Instant.now();
    return orderDAO.createOrder(restaurantId, "123 Pike St", "[\"Burger\",\"Fries\"]", t0, null,
        null, Order.State.COOKING, false, null);
  }

  /**
   * Inserts an order in DELIVERED state directly via DAO.
   *
   * @param restaurantId owning restaurant
   * @return generated order ID
   */
  private long createDeliveredOrder(long restaurantId) throws Exception {
    Instant t0 = Instant.now();
    return orderDAO.createOrder(restaurantId, "123 Pike St", "[\"Burger\"]", t0, Instant.now(),
        Instant.now(), Order.State.DELIVERED, false, null);
  }

  // ---------- tests ----------

  /** Verifies that a restaurant can be created and retrieved correctly. */
  @Test
  void createRestaurant_persistsAndGetRestaurantWorks() {
    long id = createRestaurant("R1", "Seattle");
    assertTrue(id > 0);

    Restaurant got = restaurantService.getRestaurant(id);
    assertEquals(id, got.id);
    assertEquals("R1", got.name);
    assertEquals("Seattle", got.location);
  }

  /** Ensures that creating a restaurant with a duplicate name is rejected. */
  @Test
  void createRestaurant_duplicateName_blocked() {
    createRestaurant("R1", "Seattle");
    assertThrows(IllegalStateException.class, () -> createRestaurant("R1", "Bellevue"));
  }

  /**
   * Tests that updating a restaurant works and that the name‑uniqueness constraint is enforced
   * while excluding the current restaurant from the check.
   */
  @Test
  void updateRestaurant_updatesRow_andNameUniquenessExcludingIdWorks() {
    long r1 = createRestaurant("R1", "Seattle");
    long r2 = createRestaurant("R2", "Bellevue");

    // Trying to rename r1 to r2's name should fail
    assertThrows(IllegalStateException.class,
        () -> restaurantService.updateRestaurant(r1, new Restaurant(0, "R2", "Seattle")));

    // Valid update
    restaurantService.updateRestaurant(r1, new Restaurant(0, "R1-new", "Seattle-new"));

    Restaurant got = restaurantService.getRestaurant(r1);
    assertEquals("R1-new", got.name);
    assertEquals("Seattle-new", got.location);

    Restaurant got2 = restaurantService.getRestaurant(r2);
    assertEquals("R2", got2.name);
  }

  /** Ensures that a restaurant cannot be removed while it has active (non‑delivered) orders. */
  @Test
  void removeRestaurant_blocksWhenActiveOrdersExist() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    createActiveOrder(r1);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> restaurantService.removeRestaurant(r1));
    assertTrue(ex.getMessage().toLowerCase().contains("active orders"));

    assertTrue(restaurantDAO.restaurantExists(r1));
  }

  /** Verifies that a restaurant can be removed if only delivered orders exist. */
  @Test
  void removeRestaurant_allowsWhenOnlyDeliveredOrdersExist() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    createDeliveredOrder(r1);

    restaurantService.removeRestaurant(r1);
    assertFalse(restaurantDAO.restaurantExists(r1));
  }

  /** Ensures that a restaurant cannot be removed if any driver is on shift. */
  @Test
  void removeRestaurant_blocksWhenOnShiftDriversExist() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");

    createDriver(r1, true);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> restaurantService.removeRestaurant(r1));
    assertTrue(ex.getMessage().toLowerCase().contains("on shift"));

    assertTrue(restaurantDAO.restaurantExists(r1));
  }

  /**
   * Verifies that a restaurant can be removed when there are no active orders and no drivers on
   * shift (off‑shift drivers are allowed).
   */
  @Test
  void removeRestaurant_succeedsWhenNoActiveOrdersAndNoOnShiftDrivers() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");

    createDriver(r1, false);

    restaurantService.removeRestaurant(r1);
    assertFalse(restaurantDAO.restaurantExists(r1));
  }

  /**
   * Tests that {@link RestaurantService#getRestaurantOrders(long)} returns only non‑delivered
   * orders for the given restaurant.
   */
  @Test
  void getRestaurantOrders_returnsOnlyOpenOrders() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");

    long open1 = createActiveOrder(r1);
    createDeliveredOrder(r1);
    long open2 = createActiveOrder(r1);

    List<Order> open = restaurantService.getRestaurantOrders(r1);
    assertEquals(2, open.size());
    assertEquals(List.of(open1, open2), open.stream().map(o -> o.id).toList());
    assertTrue(open.stream().allMatch(o -> o.restaurantId == r1));
    assertTrue(open.stream().allMatch(o -> o.state != Order.State.DELIVERED));
  }

  /** Verifies that all drivers for a restaurant are returned, regardless of shift status. */
  @Test
  void getRestaurantDrivers_returnsAllDrivers_onAndOffShift() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    long d1 = createDriver(r1, false);
    long d2 = createDriver(r1, true);

    List<Driver> drivers = restaurantService.getRestaurantDrivers(r1);
    assertEquals(2, drivers.size());
    assertEquals(List.of(d1, d2), drivers.stream().map(d -> d.id).toList());
  }

  /** Verifies that all menu items for a restaurant are returned. */
  @Test
  void getRestaurantMenuItems_returnsItems() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    long m1 = menuItemDAO.createMenuItem(r1, "Burger");
    long m2 = menuItemDAO.createMenuItem(r1, "Fries");

    var items = restaurantService.getRestaurantMenuItems(r1);
    assertEquals(2, items.size());
    assertEquals(List.of(m1, m2), items.stream().map(mi -> mi.id).toList());
  }
}
