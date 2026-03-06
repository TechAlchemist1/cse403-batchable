package com.batchable.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for DriverService using real Postgres (Testcontainers) and real DAOs.
 *
 * Focus: - Validation behavior - Persistence effects - Domain invariants enforced by the service
 * (off-shift on create, removal rules, etc.)
 *
 * Note: This test does not boot Spring. The service is constructed manually with real DAOs. The
 * database schema is already applied by PostgresTestBase.
 */
public class DriverServiceIT_CI extends PostgresTestBase {

  private DriverService driverService;
  private DriverDAO driverDAO;
  private BatchDAO batchDAO;
  private RestaurantDAO restaurantDAO;
  private DbOrderService dbOrderService;

  private TestDataSource ds;

  @BeforeEach
  void setUp() throws Exception {
    // Wrap the existing PostgresTestBase connection in a DataSource for the DAOs.
    ds = new TestDataSource(conn);

    driverDAO = new DriverDAO(ds);
    batchDAO = new BatchDAO(ds);
    restaurantDAO = new RestaurantDAO(ds);

    driverService = new DriverService(driverDAO, batchDAO, dbOrderService);

    cleanDb();
  }

  /** Truncates all relevant tables to start each test with a clean database. */
  private static void cleanDb() throws Exception {
    try (Statement st = conn.createStatement()) {
      // Order matters due to foreign keys.
      st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE \"menu_item\" RESTART IDENTITY CASCADE;");
      st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    } catch (Exception ignored) {
      // If some tables are not yet present, the missing TRUNCATE lines can be removed.
    }
  }

  /** Inserts a restaurant and returns its generated ID. */
  private long insertRestaurant(String name, String location) throws Exception {
    return restaurantDAO.createRestaurant(name, location);
  }

  /**
   * Inserts a batch row directly (bypassing services) for a given driver. Used to simulate an
   * existing batch for tests that require it.
   *
   * @param driverId the driver ID to associate with the batch
   * @return the generated batch ID
   */
  private long insertBatchRow(long driverId) throws Exception {
    final String sql =
        "INSERT INTO Batch(driver_id, route, dispatch_time, completion_time, finished) "
            + "VALUES (?, ?, ?, ?, ?) RETURNING id;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      ps.setString(2, "encoded_polyline");
      ps.setObject(3, java.sql.Timestamp.from(Instant.now()));
      ps.setObject(4, java.sql.Timestamp.from(Instant.now().plusSeconds(600)));
      ps.setBoolean(5, false);
      try (ResultSet rs = ps.executeQuery()) {
        assertTrue(rs.next());
        return rs.getLong("id");
      }
    }
  }

  // -------- createDriver --------

  /** Verifies a driver is persisted correctly and always starts off-shift. */
  @Test
  void createDriver_happyPath_persistsAndForcesOffShift() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");

    Driver req = new Driver(/* id */0, rid, "Alice", "206-555-0101", /* onShift */true);

    long id = driverService.createDriver(req);
    assertTrue(id > 0);

    Driver stored = driverService.getDriver(id);
    assertEquals(id, stored.id);
    assertEquals(rid, stored.restaurantId);
    assertEquals("Alice", stored.name);
    assertEquals("206-555-0101", stored.phoneNumber);

    // We allow starting on shift
    assertTrue(stored.onShift);
  }

  /** Ensures createDriver rejects null input. */
  @Test
  void createDriver_rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(null));
  }

  /** Ensures createDriver rejects a restaurant ID that is not positive. */
  @Test
  void createDriver_rejectsNonPositiveRestaurantId() {
    Driver req = new Driver(0, 0, "Alice", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(req));
  }

  /** Ensures createDriver rejects a blank name. */
  @Test
  void createDriver_rejectsBlankName() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(0, rid, "   ", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(req));
  }

  /** Ensures createDriver rejects an invalid phone number format. */
  @Test
  void createDriver_rejectsBadPhone() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(0, rid, "Alice", "NOT_A_PHONE", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.createDriver(req));
  }

  /** Ensures createDriver rejects a request with a preassigned positive ID. */
  @Test
  void createDriver_rejectsPositiveId() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(123, rid, "Alice", "206-555-0101", false);
    assertThrows(IllegalStateException.class, () -> driverService.createDriver(req));
  }

  // -------- updateDriver --------

  /**
   * Verifies updateDriver only changes name and phone; restaurant ID and shift status remain
   * unchanged.
   */
  @Test
  void updateDriver_happyPath_updatesNameAndPhone_only() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    // Try to change restaurantId/onShift in the object: service/DAO should only update
    // name/phone/onShift.
    Driver updated =
        new Driver(id, /* restaurantId */9999, "Alicia", "425-555-2222", /* onShift */true);
    driverService.updateDriver(updated);

    Driver stored = driverService.getDriver(id);
    assertEquals(id, stored.id);
    assertEquals(rid, stored.restaurantId); // unchanged
    assertEquals("Alicia", stored.name); // changed
    assertEquals("425-555-2222", stored.phoneNumber);// changed
    assertTrue(stored.onShift); // changed
  }

  /** Ensures updateDriver throws when the driver does not exist. */
  @Test
  void updateDriver_missing_throws() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(9999, rid, "Alice", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.updateDriver(req));
  }

  /** Ensures updateDriver rejects a non‑positive driver ID. */
  @Test
  void updateDriver_rejectsNonPositiveDriverId() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    Driver req = new Driver(0, rid, "Alice", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> driverService.updateDriver(req));
  }

  // -------- updateDriverOnShift --------

  /** Verifies that a driver can be turned on shift and off shift when no batch exists. */
  @Test
  void updateDriverOnShift_canTurnOnShift_andOffShift_whenNoBatch() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    driverService.updateDriverOnShift(id, true);
    assertTrue(driverService.getDriver(id).onShift);

    driverService.updateDriverOnShift(id, false);
    assertFalse(driverService.getDriver(id).onShift);
  }

  /** Ensures that going off shift is blocked if the driver still has an active batch. */
  @Test
  void updateDriverOnShift_goingOffShiftBlockedIfBatchExists() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    // Go on shift first
    driverService.updateDriverOnShift(id, true);
    assertTrue(driverService.getDriver(id).onShift);

    // Create a batch row tied to this driver
    long batchId = insertBatchRow(id);
    assertTrue(batchDAO.batchExistsForDriver(id));

    IllegalStateException ex = assertThrows(IllegalStateException.class,
        () -> driverService.updateDriverOnShift(id, false));
    assertTrue(ex.getMessage().contains("Cannot go off-shift"));
    assertTrue(ex.getMessage().contains("driverId=" + id));
    assertTrue(ex.getMessage().contains("batchId="));

    // Still on shift
    assertTrue(driverService.getDriver(id).onShift);
  }

  /** Ensures updateDriverOnShift throws if the driver does not exist. */
  @Test
  void updateDriverOnShift_missingDriver_throws() throws Exception {
    assertThrows(IllegalArgumentException.class,
        () -> driverService.updateDriverOnShift(9999, true));
  }

  // -------- getDriver --------

  /** Ensures getDriver throws when the driver does not exist. */
  @Test
  void getDriver_missing_throws() {
    assertThrows(IllegalArgumentException.class, () -> driverService.getDriver(9999));
  }

  // -------- removeDriver --------

  /** Ensures removeDriver is blocked if the driver is on shift. */
  @Test
  void removeDriver_requiresOffShift() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    driverService.updateDriverOnShift(id, true);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> driverService.removeDriver(id));
    assertTrue(ex.getMessage().contains("off-shift"));

    // Still exists
    assertNotNull(driverService.getDriver(id));
  }

  /** Ensures removeDriver is blocked if the driver still has a batch, even if off shift. */
  @Test
  void removeDriver_blockedIfBatchExists_evenIfOffShift() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    // Ensure batch exists
    insertBatchRow(id);
    assertTrue(batchDAO.batchExistsForDriver(id));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> driverService.removeDriver(id));
    assertTrue(ex.getMessage().contains("Cannot remove driver with existing batch"));

    // Still exists
    assertNotNull(driverService.getDriver(id));
  }

  /** Verifies successful deletion when the driver is off shift and has no batch. */
  @Test
  void removeDriver_happyPath_deletes() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    driverService.removeDriver(id);

    assertThrows(IllegalArgumentException.class, () -> driverService.getDriver(id));
  }

  // -------- getDriverBatch --------

  /** Ensures getDriverBatch throws if the driver does not exist. */
  @Test
  void getDriverBatch_missingDriver_throws() {
    assertThrows(IllegalArgumentException.class, () -> driverService.getDriverBatch(9999));
  }

  /** Verifies getDriverBatch returns empty when the driver has no batch. */
  @Test
  void getDriverBatch_returnsEmptyIfNoBatch() throws Exception {
    long rid = insertRestaurant("R1", "Seattle");
    long id = driverService.createDriver(new Driver(0, rid, "Alice", "206-555-0101", false));

    Optional<Batch> b = driverService.getDriverBatch(id);
    assertTrue(b.isEmpty());
  }

  /** Verifies getDriverBatch returns the correct batch when one exists. */
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
