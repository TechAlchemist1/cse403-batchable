package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.RestaurantService;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Integration tests: RestaurantService + real DAOs + real Postgres (Testcontainers).
 *
 * This proves: - your migrations ran - the DAOs talk to Postgres correctly - RestaurantService
 * enforces its domain rules against real data
 */
public class RestaurantServiceIT_CI extends PostgresTestBase {

  @Mock private BatchingManager mockBatchingManager;

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

    restaurantService =
        new RestaurantService(restaurantDAO, orderDAO, driverDAO, menuItemDAO, mockBatchingManager);

    doNothing().when(mockBatchingManager).addManager(anyLong());
    doNothing().when(mockBatchingManager).updateManagerAddress(anyLong(), anyString());
    doNothing().when(mockBatchingManager).removeManager(anyLong());

    cleanDb();
  }

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

  private long createRestaurant(String name, String location) {
    return restaurantService.createRestaurant(new Restaurant(0, name, location));
  }

  private long createDriver(long restaurantId, boolean onShift) throws Exception {
    return driverDAO.createDriver(restaurantId, "Driver1", "+1 (206) 555-1234", onShift);
  }

  private long createActiveOrder(long restaurantId) throws Exception {
    Instant t0 = Instant.now();
    return orderDAO.createOrder(restaurantId, "123 Pike St", "[\"Burger\",\"Fries\"]", t0, null,
        null, Order.State.COOKING, false, null);
  }

  private long createDeliveredOrder(long restaurantId) throws Exception {
    Instant t0 = Instant.now();
    return orderDAO.createOrder(restaurantId, "123 Pike St", "[\"Burger\"]", t0, Instant.now(),
        Instant.now(), Order.State.DELIVERED, false, null);
  }

  // ---------- tests ----------

  @Test
  void createRestaurant_persistsAndGetRestaurantWorks() {
    long id = createRestaurant("R1", "Seattle");
    assertTrue(id > 0);

    Restaurant got = restaurantService.getRestaurant(id);
    assertEquals(id, got.id);
    assertEquals("R1", got.name);
    assertEquals("Seattle", got.location);
    verify(mockBatchingManager).addManager(id);
  }

  @Test
  void createRestaurant_duplicateName_blocked() {
    long id = createRestaurant("R1", "Seattle");
    verify(mockBatchingManager).addManager(id);
    assertThrows(IllegalStateException.class, () -> createRestaurant("R1", "Bellevue"));
    verify(mockBatchingManager, times(1)).addManager(anyLong()); // only the first call
  }

  @Test
  void updateRestaurant_updatesRow_andNameUniquenessExcludingIdWorks() {
    long r1 = createRestaurant("R1", "Seattle");
    long r2 = createRestaurant("R2", "Bellevue");
    verify(mockBatchingManager).addManager(r1);
    verify(mockBatchingManager).addManager(r2);

    // trying to rename r1 to r2's name should fail
    assertThrows(IllegalStateException.class,
        () -> restaurantService.updateRestaurant(r1, new Restaurant(0, "R2", "Seattle")));
    verify(mockBatchingManager, never()).updateManagerAddress(anyLong(), anyString());

    // valid update
    restaurantService.updateRestaurant(r1, new Restaurant(0, "R1-new", "Seattle-new"));
    verify(mockBatchingManager).updateManagerAddress(r1, "Seattle-new");

    Restaurant got = restaurantService.getRestaurant(r1);
    assertEquals("R1-new", got.name);
    assertEquals("Seattle-new", got.location);

    Restaurant got2 = restaurantService.getRestaurant(r2);
    assertEquals("R2", got2.name);
  }

  @Test
  void removeRestaurant_blocksWhenActiveOrdersExist() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    createActiveOrder(r1);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> restaurantService.removeRestaurant(r1));
    assertTrue(ex.getMessage().toLowerCase().contains("active orders"));

    assertTrue(restaurantDAO.restaurantExists(r1));
    verify(mockBatchingManager, never()).removeManager(r1);
  }

  @Test
  void removeRestaurant_allowsWhenOnlyDeliveredOrdersExist() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    createDeliveredOrder(r1);

    restaurantService.removeRestaurant(r1);
    assertFalse(restaurantDAO.restaurantExists(r1));
    verify(mockBatchingManager).addManager(r1);
    verify(mockBatchingManager).removeManager(r1);
  }

  @Test
  void removeRestaurant_blocksWhenOnShiftDriversExist() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");

    createDriver(r1, true);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> restaurantService.removeRestaurant(r1));
    assertTrue(ex.getMessage().toLowerCase().contains("on shift"));

    assertTrue(restaurantDAO.restaurantExists(r1));
    verify(mockBatchingManager, never()).removeManager(r1);   // <-- added verification
  }

  @Test
  void removeRestaurant_succeedsWhenNoActiveOrdersAndNoOnShiftDrivers() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");

    createDriver(r1, false);

    restaurantService.removeRestaurant(r1);
    assertFalse(restaurantDAO.restaurantExists(r1));
    verify(mockBatchingManager).removeManager(r1);            // <-- added verification
  }

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

  @Test
  void getRestaurantDrivers_returnsAllDrivers_onAndOffShift() throws Exception {
    long r1 = createRestaurant("R1", "Seattle");
    long d1 = createDriver(r1, false);
    long d2 = createDriver(r1, true);

    List<Driver> drivers = restaurantService.getRestaurantDrivers(r1);
    assertEquals(2, drivers.size());
    assertEquals(List.of(d1, d2), drivers.stream().map(d -> d.id).toList());
  }

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