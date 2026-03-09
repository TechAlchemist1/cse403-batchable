package com.batchable.backend.db.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.batchable.backend.db.PostgresTestBase;
import com.batchable.backend.db.TestDataSource;
import com.batchable.backend.db.models.Batch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * DAO tests are DB-backed: they verify SQL actually works against Postgres.
 *
 * Assumes you already have a PostgresTestBase that provides:
 *  - a Connection field named `conn`
 *  - a clean DB per test (or you can TRUNCATE in @BeforeEach)
 *
 * If your base class name/methods differ, adjust accordingly.
 */
public class BatchDAOTest extends PostgresTestBase {

  private BatchDAO batchDAO;

  @BeforeEach
  void setUp() throws Exception {
    batchDAO = new BatchDAO(new TestDataSource(conn));

    // If your PostgresTestBase does NOT auto-clean tables between tests,
    // uncomment and adjust these TRUNCATEs to match your schema.
    //
     try (var st = conn.createStatement()) {
       st.execute("TRUNCATE TABLE \"Order\" RESTART IDENTITY CASCADE;");
       st.execute("TRUNCATE TABLE Batch RESTART IDENTITY CASCADE;");
       st.execute("TRUNCATE TABLE Driver RESTART IDENTITY CASCADE;");
       st.execute("TRUNCATE TABLE Restaurant RESTART IDENTITY CASCADE;");
    }
  }

  // --- helpers ---

  private long insertRestaurant(String name) throws Exception {
    TestDataSource ds = new TestDataSource(conn);
    RestaurantDAO restaurantDAO = new RestaurantDAO(ds);
    return restaurantDAO.createRestaurant(name, "somewhere");
  }

  private long insertDriver(long restaurantId, String name) throws Exception {
    TestDataSource ds = new TestDataSource(conn);
    DriverDAO driverDAO = new DriverDAO(ds);
    return driverDAO.createDriver(restaurantId, name, "206-555-0101", false);
  }

  // --- tests ---

  @Test
  void createBatch_returnsId_and_persistsRow() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    Instant dispatch = Instant.parse("2026-01-01T10:00:00Z");
    Instant expectedDone = Instant.parse("2026-01-01T10:30:00Z");
    String route = "polyline-abc";

    long batchId = batchDAO.createBatch(driverId, route, dispatch, expectedDone);

    assertTrue(batchId > 0);

    Optional<Batch> got = batchDAO.getBatch(batchId);
    assertTrue(got.isPresent());
    assertEquals(batchId, got.get().id);
    assertEquals(driverId, got.get().driverId);
    assertEquals(route, got.get().route);
    assertEquals(dispatch, got.get().dispatchTime);
    assertEquals(expectedDone, got.get().completionTime);
  }

  @Test
  void getBatch_missing_returnsEmpty() throws Exception {
    Optional<Batch> got = batchDAO.getBatch(999999);
    assertTrue(got.isEmpty());
  }

  @Test
  void listBatchesForDriver_returnsInAscendingIdOrder() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    long b1 =
        batchDAO.createBatch(
            driverId,
            "r1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:10:00Z"));
    long b2 =
        batchDAO.createBatch(
            driverId,
            "r2",
            Instant.parse("2026-01-01T11:00:00Z"),
            Instant.parse("2026-01-01T11:10:00Z"));
    long b3 =
        batchDAO.createBatch(
            driverId,
            "r3",
            Instant.parse("2026-01-01T12:00:00Z"),
            Instant.parse("2026-01-01T12:10:00Z"));

    List<Batch> batches = batchDAO.listBatchesForDriver(driverId);
    assertEquals(3, batches.size());

    assertEquals(b1, batches.get(0).id);
    assertEquals(b2, batches.get(1).id);
    assertEquals(b3, batches.get(2).id);
  }

  @Test
  void getBatchForDriver_returnsMostRecentByIdDesc() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    long older =
        batchDAO.createBatch(
            driverId,
            "old",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:10:00Z"));
    long newer =
        batchDAO.createBatch(
            driverId,
            "new",
            Instant.parse("2026-01-01T11:00:00Z"),
            Instant.parse("2026-01-01T11:10:00Z"));

    Optional<Batch> got = batchDAO.getBatchForDriver(driverId);
    assertTrue(got.isPresent());
    assertEquals(newer, got.get().id);
    assertNotEquals(older, got.get().id);
    assertEquals("new", got.get().route);
  }

  @Test
  void getBatchForDriver_missing_returnsEmpty() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    Optional<Batch> got = batchDAO.getBatchForDriver(driverId);
    assertTrue(got.isEmpty());
  }

  @Test
  void updateBatch_updatesFields_and_returnsTrue() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    long batchId =
        batchDAO.createBatch(
            driverId,
            "r1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:10:00Z"));

    boolean ok =
        batchDAO.updateBatch(
            batchId,
            "updated-route",
            Instant.parse("2026-01-01T12:00:00Z"),
            Instant.parse("2026-01-01T12:45:00Z"));
    assertTrue(ok);

    Batch got = batchDAO.getBatch(batchId).orElseThrow();
    assertEquals("updated-route", got.route);
    assertEquals(Instant.parse("2026-01-01T12:00:00Z"), got.dispatchTime);
    assertEquals(Instant.parse("2026-01-01T12:45:00Z"), got.completionTime);
  }

  @Test
  void updateBatch_missing_returnsFalse() throws Exception {
    boolean ok =
        batchDAO.updateBatch(
            999999,
            "x",
            Instant.parse("2026-01-01T12:00:00Z"),
            Instant.parse("2026-01-01T12:45:00Z"));
    assertFalse(ok);
  }

  @Test
  void updateBatchDriver_changesDriverId() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driver1 = insertDriver(restaurantId, "D1");
    long driver2 = insertDriver(restaurantId, "D2");

    long batchId =
        batchDAO.createBatch(
            driver1,
            "r1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:10:00Z"));

    boolean ok = batchDAO.updateBatchDriver(batchId, driver2);
    assertTrue(ok);

    Batch got = batchDAO.getBatch(batchId).orElseThrow();
    assertEquals(driver2, got.driverId);
  }

  @Test
  void updateBatchDriver_missing_returnsFalse() throws Exception {
    boolean ok = batchDAO.updateBatchDriver(999999, 123);
    assertFalse(ok);
  }

  @Test
  void deleteBatch_removesRow_and_returnsTrue() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    long batchId =
        batchDAO.createBatch(
            driverId,
            "r1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:10:00Z"));

    assertTrue(batchDAO.batchExists(batchId));

    boolean deleted = batchDAO.deleteBatch(batchId);
    assertTrue(deleted);

    assertFalse(batchDAO.batchExists(batchId));
    assertTrue(batchDAO.getBatch(batchId).isEmpty());
  }

  @Test
  void deleteBatch_missing_returnsFalse() throws Exception {
    boolean deleted = batchDAO.deleteBatch(999999);
    assertFalse(deleted);
  }

  @Test
  void batchExists_trueWhenPresent_falseWhenMissing() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driverId = insertDriver(restaurantId, "D1");

    long batchId =
        batchDAO.createBatch(
            driverId,
            "r1",
            Instant.parse("2026-01-01T10:00:00Z"),
            Instant.parse("2026-01-01T10:10:00Z"));

    assertTrue(batchDAO.batchExists(batchId));
    assertFalse(batchDAO.batchExists(999999));
  }

  @Test
  void batchExistsForDriver_trueWhenAnyBatchExists_forDriver() throws Exception {
    long restaurantId = insertRestaurant("R1");
    long driver1 = insertDriver(restaurantId, "D1");
    long driver2 = insertDriver(restaurantId, "D2");

    assertFalse(batchDAO.batchExistsForDriver(driver1));
    assertFalse(batchDAO.batchExistsForDriver(driver2));

    batchDAO.createBatch(
        driver1,
        "r1",
        Instant.parse("2026-01-01T10:00:00Z"),
        Instant.parse("2026-01-01T10:10:00Z"));

    assertTrue(batchDAO.batchExistsForDriver(driver1));
    assertFalse(batchDAO.batchExistsForDriver(driver2));
  }
}
