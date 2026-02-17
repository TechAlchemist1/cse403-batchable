package com.batchable.backend.db.dao;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.models.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DriverDAOTest extends PostgresTestBase {

  private TestDataSource ds;
  private DriverDAO driverDAO;

  @BeforeEach
  void setUp() throws Exception {
    ds = new TestDataSource(conn);
    driverDAO = new DriverDAO(ds);
    cleanDb();
  }

  private void cleanDb() throws Exception {
    // keep this aggressive so tests are isolated even if other tables exist
    try (Statement st = conn.createStatement()) {
      // Order matters less with CASCADE, but keep Restaurant last-ish for readability.
      // Quote "Order" if it exists in your schema.
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"menu_item\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    } catch (Exception ignored) {
      // If some tables don't exist in your schema yet, you can delete their TRUNCATE lines above.
      // (But leave Driver + Restaurant at minimum.)
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
  void createDriver_returnsId_andGetDriverWorks() throws Exception {
    long rid = insertRestaurant("A", "Seattle");

    long id = driverDAO.createDriver(rid, "Sam", "206-555-0000", false);
    assertTrue(id > 0);

    Optional<Driver> got = driverDAO.getDriver(id);
    assertTrue(got.isPresent());
    Driver d = got.get();

    assertEquals(id, d.id);
    assertEquals(rid, d.restaurantId);
    assertEquals("Sam", d.name);
    assertEquals("206-555-0000", d.phoneNumber);
    assertFalse(d.onShift);
  }

  @Test
  void getDriver_missing_returnsEmpty() throws Exception {
    assertTrue(driverDAO.getDriver(999999).isEmpty());
  }

  @Test
  void listDriversForRestaurant_returnsAll_andRespectsOnShiftOnly() throws Exception {
    long r1 = insertRestaurant("R1", "Loc1");
    long r2 = insertRestaurant("R2", "Loc2");

    long a = driverDAO.createDriver(r1, "A", "111", false);
    long b = driverDAO.createDriver(r1, "B", "222", true);
    long c = driverDAO.createDriver(r1, "C", "333", true);
    driverDAO.createDriver(r2, "X", "999", true); // other restaurant

    List<Driver> allR1 = driverDAO.listDriversForRestaurant(r1, false);
    assertEquals(3, allR1.size());
    assertEquals(List.of(a, b, c), allR1.stream().map(d -> d.id).toList());

    List<Driver> onShiftR1 = driverDAO.listDriversForRestaurant(r1, true);
    assertEquals(2, onShiftR1.size());
    assertEquals(List.of(b, c), onShiftR1.stream().map(d -> d.id).toList());
    assertTrue(onShiftR1.stream().allMatch(d -> d.onShift));
  }

  @Test
  void setDriverShift_updatesOnShiftFlag() throws Exception {
    long rid = insertRestaurant("A", "Seattle");
    long id = driverDAO.createDriver(rid, "Sam", "206", false);

    Driver d1 = driverDAO.getDriver(id).orElseThrow();
    assertFalse(d1.onShift);

    driverDAO.setDriverShift(id, true);
    Driver d2 = driverDAO.getDriver(id).orElseThrow();
    assertTrue(d2.onShift);

    driverDAO.setDriverShift(id, false);
    Driver d3 = driverDAO.getDriver(id).orElseThrow();
    assertFalse(d3.onShift);
  }

  @Test
  void updateDriver_updatesNameAndPhone_only() throws Exception {
    long rid = insertRestaurant("A", "Seattle");
    long id = driverDAO.createDriver(rid, "Sam", "206", true);

    boolean ok = driverDAO.updateDriver(id, "Samuel", "425");
    assertTrue(ok);

    Driver d = driverDAO.getDriver(id).orElseThrow();
    assertEquals("Samuel", d.name);
    assertEquals("425", d.phoneNumber);
    assertTrue(d.onShift); // unchanged
    assertEquals(rid, d.restaurantId); // unchanged
  }

  @Test
  void updateDriver_missing_returnsFalse() throws Exception {
    boolean ok = driverDAO.updateDriver(123456, "N", "P");
    assertFalse(ok);
  }

  @Test
  void deleteDriver_deletes_andGetReturnsEmpty() throws Exception {
    long rid = insertRestaurant("A", "Seattle");
    long id = driverDAO.createDriver(rid, "Sam", "206", false);

    assertTrue(driverDAO.deleteDriver(id));
    assertTrue(driverDAO.getDriver(id).isEmpty());

    // deleting again should be false
    assertFalse(driverDAO.deleteDriver(id));
  }

  @Test
  void hasOnShiftDrivers_trueWhenAnyOnShift_falseOtherwise() throws Exception {
    long rid = insertRestaurant("A", "Seattle");

    assertFalse(driverDAO.hasOnShiftDrivers(rid));

    driverDAO.createDriver(rid, "A", "111", false);
    assertFalse(driverDAO.hasOnShiftDrivers(rid));

    driverDAO.createDriver(rid, "B", "222", true);
    assertTrue(driverDAO.hasOnShiftDrivers(rid));
  }
}
