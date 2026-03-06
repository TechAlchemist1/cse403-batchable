package com.batchable.backend.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Order.State;
import com.batchable.backend.service.DbOrderService;
import com.batchable.backend.service.DriverService;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for DriverService using Mockito.
 *
 * Verifies validation rules, exception handling, and correct delegation to DAOs for all public
 * methods of DriverService.
 */
public class DriverServiceTest {

  @Mock
  private DriverDAO driverDAO;
  @Mock
  private BatchDAO batchDAO;
  @Mock
  private DbOrderService dbOrderService;

  private DriverService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new DriverService(driverDAO, batchDAO, dbOrderService);
  }

  // ---------------- createDriver ----------------

  /** Verifies that a driver is created with off‑shift forced and the correct ID returned. */
  @Test
  void createDriver_happyPath_returnsId_andForcesOffShift() throws Exception {
    Driver d =
        new Driver(/* id= */0, /* restaurantId= */10, "Alice", "206-555-0101", /* onShift= */true);

    when(driverDAO.createDriver(eq(10L), eq("Alice"), eq("206-555-0101"), eq(true)))
        .thenReturn(123L);

    long id = service.createDriver(d);

    assertEquals(123L, id);
    verify(driverDAO).createDriver(10L, "Alice", "206-555-0101", true);
    verifyNoMoreInteractions(driverDAO);
    verifyNoInteractions(batchDAO);
  }

  /** Ensures that dummy (non‑positive) IDs are accepted. */
  @Test
  void createDriver_allowsDummyIdNegativeOrZero() throws Exception {
    Driver d = new Driver(/* id= */-1, /* restaurantId= */10, "Alice", "206-555-0101",
        /* onShift= */false);

    when(driverDAO.createDriver(anyLong(), anyString(), anyString(), eq(false))).thenReturn(5L);

    assertEquals(5L, service.createDriver(d));
  }

  /** Verifies that a driver with a pre‑assigned positive ID is rejected. */
  @Test
  void createDriver_rejectsPositiveId() {
    Driver d =
        new Driver(/* id= */7, /* restaurantId= */10, "Alice", "206-555-0101", /* onShift= */false);

    assertThrows(IllegalStateException.class, () -> service.createDriver(d));
    verifyNoInteractions(driverDAO, batchDAO);
  }

  /** Tests all validation checks for createDriver: null, restaurantId, name, phone. */
  @Test
  void createDriver_validations() {
    assertThrows(IllegalArgumentException.class, () -> service.createDriver(null));

    Driver badRestaurant = new Driver(0, 0, "A", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> service.createDriver(badRestaurant));

    Driver badName = new Driver(0, 1, "   ", "206-555-0101", false);
    assertThrows(IllegalArgumentException.class, () -> service.createDriver(badName));

    Driver badPhone = new Driver(0, 1, "A", "nope", false);
    assertThrows(IllegalArgumentException.class, () -> service.createDriver(badPhone));

    verifyNoInteractions(driverDAO, batchDAO);
  }

  /** Verifies that SQLException from DAO is wrapped in a RuntimeException. */
  @Test
  void createDriver_wrapsSqlException() throws Exception {
    Driver d = new Driver(0, 10, "Alice", "206-555-0101", false);

    when(driverDAO.createDriver(anyLong(), anyString(), anyString(), eq(false)))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createDriver(d));
    assertTrue(ex.getMessage().contains("Failed to create driver"));
  }

  // ---------------- updateDriver ----------------

  /** Verifies that updateDriver retrieves the existing driver and updates name/phone. */
  @Test
  void updateDriver_happyPath_callsUpdate() throws Exception {
    Driver d =
        new Driver(/* id= */50, /* restaurantId= */10, "Bob", "206-555-2222", /* onShift= */false);

    when(driverDAO.getDriver(50L)).thenReturn(Optional.of(d));
    when(driverDAO.updateDriver(50L, "Bob", "206-555-2222")).thenReturn(true);

    service.updateDriver(d);

    InOrder inOrder = inOrder(driverDAO);
    inOrder.verify(driverDAO).getDriver(50L);
    inOrder.verify(driverDAO).updateDriver(50L, "Bob", "206-555-2222");

  }

  /** Tests that updateDriver rejects null, non‑positive ID, blank name, or invalid phone. */
  @Test
  void updateDriver_rejectsNullOrNonPositiveId_orBadFields() {
    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(null));

    Driver badId = new Driver(0, 1, "Bob", "206-555-2222", false);
    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(badId));

    Driver badName = new Driver(1, 1, " ", "206-555-2222", false);
    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(badName));

    Driver badPhone = new Driver(1, 1, "Bob", "abc", false);
    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(badPhone));

    verifyNoInteractions(driverDAO, batchDAO);
  }

  /** Ensures updateDriver throws if the driver does not exist before update. */
  @Test
  void updateDriver_throwsIfDriverMissing_beforeUpdate() throws Exception {
    Driver d = new Driver(99, 10, "Bob", "206-555-2222", false);
    when(driverDAO.getDriver(99L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(d));
    verify(driverDAO).getDriver(99L);
    verify(driverDAO, never()).updateDriver(anyLong(), anyString(), anyString());
    verifyNoInteractions(batchDAO);
  }

  /** Ensures updateDriver throws if the DAO update returns false (indicating no rows affected). */
  @Test
  void updateDriver_throwsIfUpdateReturnsFalse() throws Exception {
    Driver d = new Driver(99, 10, "Bob", "206-555-2222", false);
    when(driverDAO.getDriver(99L)).thenReturn(Optional.of(d));
    when(driverDAO.updateDriver(99L, "Bob", "206-555-2222")).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(d));
  }

  // ---------------- updateDriverOnShift ----------------

  /** Verifies that setting shift to the current value does nothing. */
  @Test
  void updateDriverOnShift_noOpIfAlreadySameShift() throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", true);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));

    service.updateDriverOnShift(5L, true);

    verify(driverDAO).getDriver(5L);
    verify(driverDAO, never()).setDriverShift(anyLong(), anyBoolean());
    verifyNoInteractions(batchDAO);
  }

  /** Verifies that turning a driver on shift calls setDriverShift without checking batches. */
  @Test
  void updateDriverOnShift_turnOnShift_callsSetShift() throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", false);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));

    service.updateDriverOnShift(5L, true);

    verify(driverDAO).setDriverShift(5L, true);
    verify(batchDAO, never()).batchExistsForDriver(anyLong());
  }

  /**
   * Ensures that turning a driver off shift is blocked if they have an active batch, and the batch
   * ID is included in the exception message.
   */
  @Test
  void updateDriverOnShift_blockTurningOffIfBatchExists_includesBatchIdIfPresent()
      throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", true);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));
    when(batchDAO.batchExistsForDriver(5L)).thenReturn(true);
    when(batchDAO.getBatchForDriver(5L))
        .thenReturn(Optional.of(new Batch(777L, 5L, "poly", Instant.now(), Instant.now(), false)));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.updateDriverOnShift(5L, false));

    assertTrue(ex.getMessage().contains("batchId=777"));
    verify(driverDAO, never()).setDriverShift(anyLong(), anyBoolean());
  }

  /**
   * Verifies that if a batch exists but its ID cannot be retrieved, the exception says
   * "batchId=unknown".
   */
  @Test
  void updateDriverOnShift_blockTurningOffIfBatchExists_unknownBatchIdIfMissing() throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", true);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));
    when(batchDAO.batchExistsForDriver(5L)).thenReturn(true);
    when(batchDAO.getBatchForDriver(5L)).thenReturn(Optional.empty());

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.updateDriverOnShift(5L, false));

    assertTrue(ex.getMessage().contains("batchId=unknown"));
    verify(driverDAO, never()).setDriverShift(anyLong(), anyBoolean());
  }

  /** Tests validation of driver ID and existence for updateDriverOnShift. */
  @Test
  void updateDriverOnShift_validatesId_andDriverExists() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> service.updateDriverOnShift(0L, true));

    when(driverDAO.getDriver(5L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.updateDriverOnShift(5L, true));
  }

  // ---------------- getDriver ----------------

  /** Verifies that getDriver returns the driver when found. */
  @Test
  void getDriver_happyPath() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));

    Driver got = service.getDriver(1L);
    assertEquals(1L, got.id);
  }

  /** Verifies that getDriver throws when the driver is not found. */
  @Test
  void getDriver_missing_throws() throws Exception {
    when(driverDAO.getDriver(1L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getDriver(1L));
  }

  // ---------------- removeDriver ----------------

  /** Ensures removeDriver is blocked if the driver is on shift. */
  @Test
  void removeDriver_blocksIfOnShift() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));

    assertThrows(IllegalStateException.class, () -> service.removeDriver(1L));

    verify(driverDAO).getDriver(1L);
    verify(driverDAO, never()).deleteDriver(anyLong());
    verifyNoInteractions(batchDAO);
  }

  /** Ensures removeDriver is blocked if the driver has an active batch, even if off shift. */
  @Test
  void removeDriver_blocksIfBatchExists() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));
    when(batchDAO.batchExistsForDriver(1L)).thenReturn(true);
    when(batchDAO.getBatchForDriver(1L))
        .thenReturn(Optional.of(new Batch(9L, 1L, "poly", Instant.now(), Instant.now(), false)));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.removeDriver(1L));
    assertTrue(ex.getMessage().contains("batchId=9"));

    verify(driverDAO, never()).deleteDriver(anyLong());
  }

  /** Verifies successful deletion when off shift and no batch exists. */
  @Test
  void removeDriver_happyPath_deletes() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));
    when(batchDAO.batchExistsForDriver(1L)).thenReturn(false);
    when(driverDAO.deleteDriver(1L)).thenReturn(true);

    service.removeDriver(1L);

    verify(driverDAO).deleteDriver(1L);
  }

  /** Verifies that if deleteDriver returns false, an IllegalArgumentException is thrown. */
  @Test
  void removeDriver_deleteReturnsFalse_throwsNotFound() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));
    when(batchDAO.batchExistsForDriver(1L)).thenReturn(false);
    when(driverDAO.deleteDriver(1L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.removeDriver(1L));
  }

  // ---------------- getDriverBatch ----------------

  /**
   * Verifies that getDriverBatch first checks driver existence, then returns the batch from DAO.
   */
  @Test
  void getDriverBatch_requiresDriverExists_thenReturnsDaoBatch() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));

    Batch b = new Batch(3L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(b));

    Optional<Batch> got = service.getDriverBatch(1L);
    assertTrue(got.isPresent());
    assertEquals(3L, got.get().id);

    verify(driverDAO).getDriver(1L);
    verify(batchDAO).getBatchForDriver(1L);
  }

  // ---------------- getDriverByToken ----------------

  /** Verifies that a driver can be retrieved by a valid UUID token. */
  @Test
  void getDriverByToken_happyPath() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriverByToken("valid-token")).thenReturn(Optional.of(d));

    Driver got = service.getDriverByToken("valid-token");

    assertEquals(1L, got.id);
    verify(driverDAO).getDriverByToken("valid-token");
  }

  /** Rejects null or empty token. */
  @Test
  void getDriverByToken_invalidToken_throws() {
    assertThrows(IllegalArgumentException.class, () -> service.getDriverByToken(null));
    assertThrows(IllegalArgumentException.class, () -> service.getDriverByToken(""));
    verifyNoInteractions(driverDAO);
  }

  /** Throws when token does not exist. */
  @Test
  void getDriverByToken_notFound_throws() throws Exception {
    when(driverDAO.getDriverByToken("unknown")).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getDriverByToken("unknown"));
  }

  /** Wraps SQLException in RuntimeException. */
  @Test
  void getDriverByToken_sqlException_wrapped() throws Exception {
    when(driverDAO.getDriverByToken("token")).thenThrow(new SQLException("db error"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getDriverByToken("token"));
    assertTrue(ex.getMessage().contains("Failed to get driver"));
  }

  // ---------------- handleReturn ----------------

  /** Happy path: driver has a batch, no current order, marks batch finished. */
  @Test
  void handleReturn_happyPath_marksBatchFinished() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriverByToken("token")).thenReturn(Optional.of(driver));

    Batch batch = new Batch(100L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));

    // No current order (all delivered)
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));
    
    when(dbOrderService.getBatchOrders(100L)).thenReturn(List.of()); // empty means none pending

    service.handleReturn("token");

    verify(batchDAO).markBatchFinished(100L);
  }

  /** Throws if driver still has an undelivered order. */
  @Test
  void handleReturn_driverHasCurrentOrder_throws() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriverByToken("token")).thenReturn(Optional.of(driver));

    Batch batch = new Batch(100L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));

    Order pendingOrder = new Order(5, 10, "", "", null, null, null, State.DRIVING, false, 100L);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));

    when(dbOrderService.getBatchOrders(100L)).thenReturn(List.of(pendingOrder));

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> service.handleReturn("token"));
    assertTrue(ex.getMessage().contains("still has orders to deliver"));
    verify(batchDAO, never()).markBatchFinished(anyLong());
  }

  /** Throws if driver has no batch at all. */
  @Test
  void handleReturn_driverNoBatch_throws() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriverByToken("token")).thenReturn(Optional.of(driver));
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.handleReturn("token"));
  }

  /** Wraps SQLException from markBatchFinished. */
  @Test
  void handleReturn_markBatchFinishedThrowsSQLException() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriverByToken("token")).thenReturn(Optional.of(driver));
    // Also stub getDriver for the id check inside getDriverBatch and getCurrentOrderToDeliver
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));

    Batch batch = new Batch(100L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));
    when(dbOrderService.getBatchOrders(100L)).thenReturn(List.of());
    doThrow(new SQLException("mark failed")).when(batchDAO).markBatchFinished(100L);

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.handleReturn("token"));
    assertTrue(ex.getMessage().contains("Failed to mark batch finished"));
  }

  // ---------------- getCurrentOrderToDeliver ----------------

  /** Returns first undelivered order when batch has mixed states. */
  @Test
  void getCurrentOrderToDeliver_returnsFirstUndelivered() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));

    Batch batch = new Batch(100L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));

    Order delivered = new Order(1, 10, "addr", "[]", Instant.now(), null, null,
        Order.State.DELIVERED, false, 100L);
    Order cooking =
        new Order(2, 10, "addr", "[]", Instant.now(), null, null, Order.State.COOKING, false, 100L);
    Order driving =
        new Order(3, 10, "addr", "[]", Instant.now(), null, null, Order.State.DRIVING, false, 100L);

    when(dbOrderService.getBatchOrders(100L)).thenReturn(List.of(delivered, cooking, driving));

    Order result = service.getCurrentOrderToDeliver(1L);

    assertEquals(cooking, result);
  }

  /** Returns null if all orders are delivered. */
  @Test
  void getCurrentOrderToDeliver_allDelivered_returnsNull() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));

    Batch batch = new Batch(100L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));

    Order delivered1 = new Order(1, 10, "addr", "[]", Instant.now(), null, null,
        Order.State.DELIVERED, false, 100L);
    Order delivered2 = new Order(2, 10, "addr", "[]", Instant.now(), null, null,
        Order.State.DELIVERED, false, 100L);
    when(dbOrderService.getBatchOrders(100L)).thenReturn(List.of(delivered1, delivered2));

    assertNull(service.getCurrentOrderToDeliver(1L));
  }

  /** Returns null when batch has no orders (edge case). */
  @Test
  void getCurrentOrderToDeliver_emptyBatch_returnsNull() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));

    Batch batch = new Batch(100L, 1L, "poly", Instant.now(), Instant.now(), false);
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(batch));
    when(dbOrderService.getBatchOrders(100L)).thenReturn(List.of());

    assertNull(service.getCurrentOrderToDeliver(1L));
  }

  /** Throws if driver has no batch. */
  @Test
  void getCurrentOrderToDeliver_driverNoBatch_throws() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getCurrentOrderToDeliver(1L));
  }

  // ---------------- isAvailable ----------------

  /** Available when on shift and no batch. */
  @Test
  void isAvailable_onShiftAndNoBatch_true() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.empty());

    assertTrue(service.isAvailable(1L));
  }

  /** Not available when off shift. */
  @Test
  void isAvailable_offShift_false() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));
    // batch existence doesn't matter because off shift already disqualifies
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.empty());

    assertFalse(service.isAvailable(1L));
  }

  /** Not available when on shift but has a batch. */
  @Test
  void isAvailable_onShiftButHasBatch_false() throws Exception {
    Driver driver = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(driver));
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(new Batch(2, 1, "", null, null, false)));

    assertFalse(service.isAvailable(1L));
  }

  /** Propagates exception if driver not found. */
  @Test
  void isAvailable_driverNotFound_throws() throws Exception {
    when(driverDAO.getDriver(99L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.isAvailable(99L));
  }
}
