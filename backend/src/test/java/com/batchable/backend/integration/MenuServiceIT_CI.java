package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.service.MenuService;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for MenuService using real Postgres (Testcontainers) and real DAOs. Verifies: -
 * Validation rules for creating menu items. - Persistence and uniqueness constraints. - Deletion
 * behavior. Assumes migrations have created the Restaurant and menu_item tables.
 */
public class MenuServiceIT_CI extends PostgresTestBase {

  private MenuService menuService;
  private MenuItemDAO menuItemDAO;
  private RestaurantDAO restaurantDAO;

  private TestDataSource ds;

  @BeforeEach
  void setUp() throws Exception {
    // Wrap the existing PostgresTestBase connection in a DataSource for DAOs.
    ds = new TestDataSource(conn);

    menuItemDAO = new MenuItemDAO(ds);
    restaurantDAO = new RestaurantDAO(ds);
    menuService = new MenuService(menuItemDAO, restaurantDAO);

    cleanDb();
  }

  /** Truncates all relevant tables to start each test with a clean database. */
  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      // Order matters if foreign keys exist.
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"menu_item\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    } catch (Exception ignored) {
      // If some tables aren't present yet, delete the missing TRUNCATE lines.
    }
  }

  /** Inserts a restaurant and returns its generated ID. */
  private long insertRestaurant(String name, String location) throws Exception {
    return restaurantDAO.createRestaurant(name, location);
  }

  /** Helper to create a MenuItem instance with the given fields. */
  private static MenuItem menuItem(long id, long restaurantId, String name) {
    return new MenuItem(id, restaurantId, name);
  }

  /** Returns the number of menu items for a specific restaurant. */
  private static long countMenuItemsForRestaurant(long restaurantId) throws Exception {
    final String sql = "SELECT COUNT(*) AS c FROM \"menu_item\" WHERE restaurant_id = ?;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, restaurantId);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        return rs.getLong("c");
      }
    }
  }

  /** Returns the total number of menu items across all restaurants. */
  private static long countAllMenuItems() throws Exception {
    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM \"menu_item\";");
        ResultSet rs = ps.executeQuery()) {
      assertTrue(rs.next());
      return rs.getLong("c");
    }
  }

  // -------- createMenuItem --------

  /** Verifies that a valid menu item is persisted and an ID is returned. */
  @Test
  void createMenuItem_happyPath_persistsAndReturnsId() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    long id = menuService.createMenuItem(menuItem(0, rid, "Burger"));
    assertTrue(id > 0);

    var got = menuItemDAO.getMenuItem(id);
    assertTrue(got.isPresent());
    assertEquals(id, got.get().id);
    assertEquals(rid, got.get().restaurantId);
    assertEquals("Burger", got.get().name);

    assertEquals(1L, countMenuItemsForRestaurant(rid));
  }

  /** Ensures createMenuItem rejects a null input. */
  @Test
  void createMenuItem_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(null));
  }

  /** Ensures createMenuItem rejects a non‑positive restaurant ID. */
  @Test
  void createMenuItem_nonPositiveRestaurantId_throwsIAE() {
    MenuItem m = menuItem(0, 0, "Burger");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(m));
  }

  /** Ensures createMenuItem rejects a blank name. */
  @Test
  void createMenuItem_blankName_throwsIAE() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    MenuItem m = menuItem(0, rid, "   ");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(m));
  }

  /** Ensures createMenuItem rejects a request with a preassigned positive ID. */
  @Test
  void createMenuItem_positiveId_throwsISE() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    MenuItem m = menuItem(123, rid, "Burger");
    assertThrows(IllegalStateException.class, () -> menuService.createMenuItem(m));
  }

  /**
   * Ensures createMenuItem throws when the referenced restaurant does not exist, and no row is
   * inserted.
   */
  @Test
  void createMenuItem_missingRestaurant_throwsIAE_andDoesNotInsert() throws Exception {
    long missingRid = 9999;

    assertThrows(IllegalArgumentException.class,
        () -> menuService.createMenuItem(menuItem(0, missingRid, "Burger")));

    assertEquals(0L, countAllMenuItems());
  }

  /**
   * Ensures that creating a duplicate menu item name for the same restaurant is rejected, and that
   * the second insertion does not affect the database.
   */
  @Test
  void createMenuItem_duplicateNameSameRestaurant_throwsISE_andDoesNotInsertSecond()
      throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    long firstId = menuService.createMenuItem(menuItem(0, rid, "Burger"));
    assertTrue(firstId > 0);

    assertThrows(RuntimeException.class,
        () -> menuService.createMenuItem(menuItem(0, rid, "Burger")));

    assertEquals(1L, countMenuItemsForRestaurant(rid));
  }

  /**
   * Verifies that the same menu item name can be used for different restaurants.
   */
  @Test
  void createMenuItem_sameNameDifferentRestaurant_allowed() throws Exception {
    long r1 = insertRestaurant("R1", "Seattle");
    long r2 = insertRestaurant("R2", "Bellevue");

    long a = menuService.createMenuItem(menuItem(0, r1, "Burger"));
    long b = menuService.createMenuItem(menuItem(0, r2, "Burger"));

    assertTrue(a > 0);
    assertTrue(b > 0);
    assertNotEquals(a, b);

    assertEquals(1L, countMenuItemsForRestaurant(r1));
    assertEquals(1L, countMenuItemsForRestaurant(r2));
  }

  // -------- removeMenuItem --------

  /** Verifies that an existing menu item can be removed. */
  @Test
  void removeMenuItem_happyPath_deletes() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = menuService.createMenuItem(menuItem(0, rid, "Burger"));

    menuService.removeMenuItem(id);

    assertTrue(menuItemDAO.getMenuItem(id).isEmpty());
    assertEquals(0L, countMenuItemsForRestaurant(rid));
  }

  /** Ensures removeMenuItem rejects a non‑positive ID. */
  @Test
  void removeMenuItem_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(0));
  }

  /** Ensures removeMenuItem throws when the menu item does not exist. */
  @Test
  void removeMenuItem_missing_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(9999));
  }
}
