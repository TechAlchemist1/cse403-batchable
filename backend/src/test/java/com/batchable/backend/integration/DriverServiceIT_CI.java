package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.service.DriverService;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DriverService integration tests (real Postgres via Testcontainers + real DAOs).
 *
 * Focus:
 *  - validation behavior
 *  - persistence effects
 *  - domain invariants enforced by service (off-shift on create, removal rules, etc.)
 *
 * Notes:
 *  - This does NOT boot Spring. We construct service manually with DAOs.
 *  - Requires schema/migrations already applied by PostgresTestBase.
 */
public class DriverServiceIT_CI extends PostgresTestBase {

  private DriverService driverService;
  private DriverDAO driverDAO;
  private BatchDAO batchDAO;
  private RestaurantDAO restaurantDAO;

  @BeforeEach
  void setUp() throws Exception {
    driverDAO = new DriverDAO(conn);
    batchDAO = new BatchDAO(conn);
    restaurantDAO = new RestaurantDAO(conn);
    driverService = new DriverService(driverDAO, batchDAO);

    cleanDb();
  }

  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      // Order matters due to FKs
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"Menu_Item\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    } catch (Exception ignored) {
      // If some tables aren't in your schema yet, delete the missing TRUNCATE lines.
    }
  }

  private long insertRestaurant(String name, String location) throws Exception {
    return restaurantDAO.createRestaurant(name, location);
  }

  // If your BatchDAO doesn't have a create method yet, we insert directly for tests.
  private long insertBatchRow(long driverId) throws Exception {
    final String sql =
        "INSERT INTO Batch(driver_id, route, dispatch_time, expected_completion_time) " +
        "VALUES (?, ?, ?, ?) RETURNING id;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      ps.setString(2, "encoded_polyline");
      ps.setObject(3, java.sql.Timestamp.from(Instant.now()));
      ps.setObject(4, java.sql.Timestamp.from(Instant.now().plusSeconds(600)));
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        return rs.getLong("id");
      }
    }
  }

  // -------- createDriver --------

  @Test
  void createDriver_happyPath_persistsAndForcesOffShift() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    Driver req = new Driver(/*id*/0, rid, "Alice", "206-555-0101", /*onShift*/true);

    long id = driverService.createDriver(req);
    assertTrue(id > 0);

    Driver stored = driverService.getDriver(id);
    assertEquals(id, stored.id);
    assertEquals(rid, stored.restaurantId);
    assertEquals("Alice", stored.name);
    assertEquals("206-555-0101", stored.phoneNumber);

    // Service invariant: always starts off-shift
    assertFalse(stored.onShift);
  }

  @Test
  void createDriver_rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(null));
  }

  @Test
  void createDriver_rejectsNonPositiveRestaurantId() {
    Driver req = new Driver(0, 0, "Alice", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(req));
  }

  @Test
  void createDriver_rejectsBlankName() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(0, rid, "   ", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(req));
  }

  @Test
  void createDriver_rejectsBadPhone() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(0, rid, "Alice", "NOT_A_PHONE", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(req));
  }

  @Test
  void createDriver_rejectsPositiveId() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(123, rid, "Alice", "206-555-0101", false);
    assertThrows(IllegalStateException.class, () -> driverService.createDriver(req));
  }

  // -------- updateDriver --------

  @Test
  void updateDriver_happyPath_updatesNameAndPhone_only() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    // try to "change" restaurantId/onShift in the object: service/DAO should only update name/phone
    Driver updated = new Driver(id, /*restaurantId*/9999, "Alicia", "425-555-2222", /*onShift*/true);
    driverService.updateDriver(updated);

    Driver stored = driverService.getDriver(id);
    assertEquals(id, stored.id);
    assertEquals(rid, stored.restaurantId);          // unchanged
    assertEquals("Alicia", stored.name);             // changed
    assertEquals("425-555-2222", stored.phoneNumber);// changed
    assertFalse(stored.onShift);                     // unchanged
  }

  @Test
  void updateDriver_missing_throws() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(9999, rid, "Alice", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.updateDriver(req));
  }

  @Test
  void updateDriver_rejectsNonPositiveDriverId() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(0, rid, "Alice", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.updateDriver(req));
  }

  // -------- updateDriverOnShift --------

  @Test
  void updateDriverOnShift_canTurnOnShift_andOffShift_whenNoBatch() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    driverService.updateDriverOnShift(id, true);
    assertTrue(driverService.getDriver(id).onShift);

    driverService.updateDriverOnShift(id, false);
    assertFalse(driverService.getDriver(id).onShift);
  }

  @Test
  void updateDriverOnShift_goingOffShiftBlockedIfBatchExists() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    // go on shift first
    driverService.updateDriverOnShift(id, true);
    assertTrue(driverService.getDriver(id).onShift);

    // create a batch row tied to this driver
    long batchId = insertBatchRow(id);
    assertTrue(batchDAO.batchExistsForDriver(id));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> driverService.updateDriverOnShift(id, false));
    assertTrue(ex.getMessage().contains("Cannot go off-shift"));
    assertTrue(ex.getMessage().contains("driverId=" + id));
    // batch id may or may not be included depending on BatchDAO.getBatchForDriver implementation,
    // but usually it is:
    assertTrue(ex.getMessage().contains("batchId="));

    // still on shift
    assertTrue(driverService.getDriver(id).onShift);
  }

  @Test
  void updateDriverOnShift_missingDriver_throws() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> driverService.updateDriverOnShift(9999, true));
  }

  // -------- getDriver --------

  @Test
  void getDriver_missing_throws() {
    assertThrows(IllegalArgumentException.class, () -> driverService.getDriver(9999));
  }

  // -------- removeDriver --------

  @Test
  void removeDriver_requiresOffShift() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    driverService.updateDriverOnShift(id, true);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> driverService.removeDriver(id));
    assertTrue(ex.getMessage().contains("off-shift"));

    // still exists
    assertNotNull(driverService.getDriver(id));
  }

  @Test
  void removeDriver_blockedIfBatchExists_evenIfOffShift() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    // ensure batch exists
    insertBatchRow(id);
    assertTrue(batchDAO.batchExistsForDriver(id));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> driverService.removeDriver(id));
    assertTrue(ex.getMessage().contains("Cannot remove driver with existing batch"));

    // still exists
    assertNotNull(driverService.getDriver(id));
  }

  @Test
  void removeDriver_happyPath_deletes() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    driverService.removeDriver(id);

    assertThrows(IllegalArgumentException.class, () -> driverService.getDriver(id));
  }

  // -------- getDriverBatch --------

  @Test
  void getDriverBatch_missingDriver_throws() {
    assertThrows(IllegalArgumentException.class, () -> driverService.getDriverBatch(9999));
  }

  @Test
  void getDriverBatch_returnsEmptyIfNoBatch() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    Optional<Batch> b = driverService.getDriverBatch(id);
    assertTrue(b.isEmpty());
  }

  @Test
  void getDriverBatch_returnsBatchIfPresent() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    long batchId = insertBatchRow(id);

    Optional<Batch> b = driverService.getDriverBatch(id);
    assertTrue(b.isPresent());
    assertEquals(batchId, b.get().id);
    assertEquals(id, b.get().driverId);
  }
}
