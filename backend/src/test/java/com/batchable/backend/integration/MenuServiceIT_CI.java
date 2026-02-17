package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.batchable.backend.db.PostgresTestBase;
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
 * MenuService integration tests (real Postgres via Testcontainers + real DAOs).
 *
 * Assumes migration created:
 *  - Restaurant
 *  - menu_item  (lowercase, unquoted)
 */
public class MenuServiceIT_CI extends PostgresTestBase {

  private MenuService menuService;
  private MenuItemDAO menuItemDAO;
  private RestaurantDAO restaurantDAO;

  @BeforeEach
  void setUp() throws Exception {
    menuItemDAO = new MenuItemDAO(conn);
    restaurantDAO = new RestaurantDAO(conn);
    menuService = new MenuService(menuItemDAO, restaurantDAO);
    cleanDb();
  }

  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      // Order matters if FKs exist
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE menu_item RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE restaurant RESTART IDENTITY CASCADE;");
    } catch (Exception ignored) {
      // If some tables aren't present yet, delete the missing TRUNCATE lines.
    }
  }

  private long insertRestaurant(String name, String location) throws Exception {
    return restaurantDAO.createRestaurant(name, location);
  }

  private static MenuItem menuItem(long id, long restaurantId, String name) {
    return new MenuItem(id, restaurantId, name);
  }

  private static long countMenuItemsForRestaurant(long restaurantId) throws Exception {
    final String sql = "SELECT COUNT(*) AS c FROM menu_item WHERE restaurant_id = ?;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, restaurantId);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        return rs.getLong("c");
      }
    }
  }

  private static long countAllMenuItems() throws Exception {
    try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM menu_item;");
         ResultSet rs = ps.executeQuery()) {
      assertTrue(rs.next());
      return rs.getLong("c");
    }
  }

  // -------- createMenuItem --------

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

  @Test
  void createMenuItem_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(null));
  }

  @Test
  void createMenuItem_nonPositiveRestaurantId_throwsIAE() {
    MenuItem m = menuItem(0, 0, "Burger");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(m));
  }

  @Test
  void createMenuItem_blankName_throwsIAE() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    MenuItem m = menuItem(0, rid, "   ");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(m));
  }

  @Test
  void createMenuItem_positiveId_throwsISE() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    MenuItem m = menuItem(123, rid, "Burger");
    assertThrows(IllegalStateException.class, () -> menuService.createMenuItem(m));
  }

  @Test
  void createMenuItem_missingRestaurant_throwsIAE_andDoesNotInsert() throws Exception {
    long missingRid = 9999;

    assertThrows(
        IllegalArgumentException.class,
        () -> menuService.createMenuItem(menuItem(0, missingRid, "Burger")));

    assertEquals(0L, countAllMenuItems());
  }

  @Test
  void createMenuItem_duplicateNameSameRestaurant_throwsISE_andDoesNotInsertSecond() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    long firstId = menuService.createMenuItem(menuItem(0, rid, "Burger"));
    assertTrue(firstId > 0);

    assertThrows(
        IllegalStateException.class,
        () -> menuService.createMenuItem(menuItem(0, rid, "Burger")));

    assertEquals(1L, countMenuItemsForRestaurant(rid));
  }

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

  @Test
  void removeMenuItem_happyPath_deletes() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = menuService.createMenuItem(menuItem(0, rid, "Burger"));

    menuService.removeMenuItem(id);

    assertTrue(menuItemDAO.getMenuItem(id).isEmpty());
    assertEquals(0L, countMenuItemsForRestaurant(rid));
  }

  @Test
  void removeMenuItem_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(0));
  }

  @Test
  void removeMenuItem_missing_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(9999));
  }
}
