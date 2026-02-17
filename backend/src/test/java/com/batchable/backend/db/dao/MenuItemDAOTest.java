package com.batchable.backend.db.dao;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.models.MenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MenuItemDAOTest extends PostgresTestBase {

  private TestDataSource ds;
  private MenuItemDAO menuItemDAO;

  @BeforeEach
  void setUp() throws Exception {
    ds = new TestDataSource(conn);
    menuItemDAO = new MenuItemDAO(ds);
    cleanDb();
  }

  private void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      // Keep tests isolated. If some tables don't exist yet, remove those TRUNCATE lines.
      st.execute("TRUNCATE TABLE \"menu_item\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    } catch (Exception ignored) {
      // If your schema doesn't include some of these tables yet, delete the missing ones.
    }
  }

  private long insertRestaurant(String name, String location) throws Exception {
    final String sql = "INSERT INTO Restaurant(name, location) VALUES (?, ?) RETURNING id;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, name);
      ps.setString(2, location);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        return rs.getLong("id");
      }
    }
  }

  @Test
  void createMenuItem_returnsId_andGetMenuItemWorks() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    long id = menuItemDAO.createMenuItem(rid, "Burger");
    assertTrue(id > 0);

    Optional<MenuItem> got = menuItemDAO.getMenuItem(id);
    assertTrue(got.isPresent());
    MenuItem mi = got.get();

    assertEquals(id, mi.id);
    assertEquals(rid, mi.restaurantId);
    assertEquals("Burger", mi.name);
  }

  @Test
  void getMenuItem_missing_returnsEmpty() throws Exception {
    assertTrue(menuItemDAO.getMenuItem(999999).isEmpty());
  }

  @Test
  void listMenuItems_returnsOnlyThatRestaurantsItems_inIdOrder() throws Exception {
    long r1 = insertRestaurant("R1", "Seattle");
    long r2 = insertRestaurant("R2", "Bellevue");

    long a = menuItemDAO.createMenuItem(r1, "A");
    long b = menuItemDAO.createMenuItem(r1, "B");
    menuItemDAO.createMenuItem(r2, "X");

    List<MenuItem> items = menuItemDAO.listMenuItems(r1);
    assertEquals(2, items.size());
    assertEquals(List.of(a, b), items.stream().map(x -> x.id).toList());
    assertTrue(items.stream().allMatch(x -> x.restaurantId == r1));
  }

  @Test
  void deleteMenuItem_deletes_andReturnsTrue_thenFalseIfRepeated() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    long id = menuItemDAO.createMenuItem(rid, "Burger");
    assertTrue(menuItemDAO.deleteMenuItem(id));

    assertTrue(menuItemDAO.getMenuItem(id).isEmpty());
    assertFalse(menuItemDAO.deleteMenuItem(id));
  }

  @Test
  void menuItemExistsForRestaurantByName_trueOnlyForSameRestaurantAndName() throws Exception {
    long r1 = insertRestaurant("R1", "Seattle");
    long r2 = insertRestaurant("R2", "Bellevue");

    assertFalse(menuItemDAO.menuItemExistsForRestaurantByName(r1, "Burger"));

    menuItemDAO.createMenuItem(r1, "Burger");
    assertTrue(menuItemDAO.menuItemExistsForRestaurantByName(r1, "Burger"));

    // same name, different restaurant => should be false for r2
    assertFalse(menuItemDAO.menuItemExistsForRestaurantByName(r2, "Burger"));
  }

  @Test
  void menuItemExistsForRestaurantByName_isCaseSensitive_byDefault() throws Exception {
    long r1 = insertRestaurant("R1", "Seattle");

    menuItemDAO.createMenuItem(r1, "Burger");

    // Postgres string equality is case-sensitive unless you use CITEXT or lower() checks
    assertFalse(menuItemDAO.menuItemExistsForRestaurantByName(r1, "burger"));
    assertTrue(menuItemDAO.menuItemExistsForRestaurantByName(r1, "Burger"));
  }
}
