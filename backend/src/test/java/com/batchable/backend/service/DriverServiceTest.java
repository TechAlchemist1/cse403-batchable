package com.batchable.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.models.Batch;
import com.batchable.backend.db.models.Driver;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DriverServiceTest {

  @Mock private DriverDAO driverDAO;
  @Mock private BatchDAO batchDAO;

  private DriverService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    service = new DriverService(driverDAO, batchDAO);
  }

  // ---------------- createDriver ----------------

  @Test
  void createDriver_happyPath_returnsId_andForcesOffShift() throws Exception {
    Driver d = new Driver(/*id=*/0, /*restaurantId=*/10, "Alice", "206-555-0101", /*onShift=*/true);

    when(driverDAO.createDriver(eq(10L), eq("Alice"), eq("206-555-0101"), eq(false)))
        .thenReturn(123L);

    long id = service.createDriver(d);

    assertEquals(123L, id);
    verify(driverDAO).createDriver(10L, "Alice", "206-555-0101", false);
    verifyNoMoreInteractions(driverDAO);
    verifyNoInteractions(batchDAO);
  }

  @Test
  void createDriver_allowsDummyIdNegativeOrZero() throws Exception {
    Driver d = new Driver(/*id=*/-1, /*restaurantId=*/10, "Alice", "206-555-0101", /*onShift=*/false);

    when(driverDAO.createDriver(anyLong(), anyString(), anyString(), eq(false))).thenReturn(5L);

    assertEquals(5L, service.createDriver(d));
  }

  @Test
  void createDriver_rejectsPositiveId() {
    Driver d = new Driver(/*id=*/7, /*restaurantId=*/10, "Alice", "206-555-0101", /*onShift=*/false);

    assertThrows(IllegalStateException.class, () -> service.createDriver(d));
    verifyNoInteractions(driverDAO, batchDAO);
  }

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

  @Test
  void createDriver_wrapsSqlException() throws Exception {
    Driver d = new Driver(0, 10, "Alice", "206-555-0101", false);

    when(driverDAO.createDriver(anyLong(), anyString(), anyString(), eq(false)))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createDriver(d));
    assertTrue(ex.getMessage().contains("Failed to create driver"));
  }

  // ---------------- updateDriver ----------------

  @Test
  void updateDriver_happyPath_callsUpdate() throws Exception {
    Driver d = new Driver(/*id=*/50, /*restaurantId=*/10, "Bob", "206-555-2222", /*onShift=*/false);

    when(driverDAO.getDriver(50L)).thenReturn(Optional.of(d));
    when(driverDAO.updateDriver(50L, "Bob", "206-555-2222")).thenReturn(true);

    service.updateDriver(d);

    InOrder inOrder = inOrder(driverDAO);
    inOrder.verify(driverDAO).getDriver(50L);
    inOrder.verify(driverDAO).updateDriver(50L, "Bob", "206-555-2222");
    verifyNoInteractions(batchDAO);
  }

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

  @Test
  void updateDriver_throwsIfDriverMissing_beforeUpdate() throws Exception {
    Driver d = new Driver(99, 10, "Bob", "206-555-2222", false);
    when(driverDAO.getDriver(99L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(d));
    verify(driverDAO).getDriver(99L);
    verify(driverDAO, never()).updateDriver(anyLong(), anyString(), anyString());
    verifyNoInteractions(batchDAO);
  }

  @Test
  void updateDriver_throwsIfUpdateReturnsFalse() throws Exception {
    Driver d = new Driver(99, 10, "Bob", "206-555-2222", false);
    when(driverDAO.getDriver(99L)).thenReturn(Optional.of(d));
    when(driverDAO.updateDriver(99L, "Bob", "206-555-2222")).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.updateDriver(d));
  }

  // ---------------- updateDriverOnShift ----------------

  @Test
  void updateDriverOnShift_noOpIfAlreadySameShift() throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", true);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));

    service.updateDriverOnShift(5L, true);

    verify(driverDAO).getDriver(5L);
    verify(driverDAO, never()).setDriverShift(anyLong(), anyBoolean());
    verifyNoInteractions(batchDAO);
  }

  @Test
  void updateDriverOnShift_turnOnShift_callsSetShift() throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", false);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));

    service.updateDriverOnShift(5L, true);

    verify(driverDAO).setDriverShift(5L, true);
    verify(batchDAO, never()).batchExistsForDriver(anyLong());
  }

  @Test
  void updateDriverOnShift_blockTurningOffIfBatchExists_includesBatchIdIfPresent() throws Exception {
    Driver existing = new Driver(5, 10, "Bob", "206-555-2222", true);
    when(driverDAO.getDriver(5L)).thenReturn(Optional.of(existing));
    when(batchDAO.batchExistsForDriver(5L)).thenReturn(true);
    when(batchDAO.getBatchForDriver(5L))
        .thenReturn(Optional.of(new Batch(777L, 5L, "poly", Instant.now(), Instant.now())));

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> service.updateDriverOnShift(5L, false));

    assertTrue(ex.getMessage().contains("batchId=777"));
    verify(driverDAO, never()).setDriverShift(anyLong(), anyBoolean());
  }

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

  @Test
  void updateDriverOnShift_validatesId_andDriverExists() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> service.updateDriverOnShift(0L, true));

    when(driverDAO.getDriver(5L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.updateDriverOnShift(5L, true));
  }

  // ---------------- getDriver ----------------

  @Test
  void getDriver_happyPath() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));

    Driver got = service.getDriver(1L);
    assertEquals(1L, got.id);
  }

  @Test
  void getDriver_missing_throws() throws Exception {
    when(driverDAO.getDriver(1L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getDriver(1L));
  }

  // ---------------- removeDriver ----------------

  @Test
  void removeDriver_blocksIfOnShift() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", true);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));

    assertThrows(IllegalStateException.class, () -> service.removeDriver(1L));

    verify(driverDAO).getDriver(1L);
    verify(driverDAO, never()).deleteDriver(anyLong());
    verifyNoInteractions(batchDAO);
  }

  @Test
  void removeDriver_blocksIfBatchExists() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));
    when(batchDAO.batchExistsForDriver(1L)).thenReturn(true);
    when(batchDAO.getBatchForDriver(1L))
        .thenReturn(Optional.of(new Batch(9L, 1L, "poly", Instant.now(), Instant.now())));

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.removeDriver(1L));
    assertTrue(ex.getMessage().contains("batchId=9"));

    verify(driverDAO, never()).deleteDriver(anyLong());
  }

  @Test
  void removeDriver_happyPath_deletes() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));
    when(batchDAO.batchExistsForDriver(1L)).thenReturn(false);
    when(driverDAO.deleteDriver(1L)).thenReturn(true);

    service.removeDriver(1L);

    verify(driverDAO).deleteDriver(1L);
  }

  @Test
  void removeDriver_deleteReturnsFalse_throwsNotFound() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));
    when(batchDAO.batchExistsForDriver(1L)).thenReturn(false);
    when(driverDAO.deleteDriver(1L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.removeDriver(1L));
  }

  // ---------------- getDriverBatch ----------------

  @Test
  void getDriverBatch_requiresDriverExists_thenReturnsDaoBatch() throws Exception {
    Driver d = new Driver(1, 10, "Alice", "206-555-0101", false);
    when(driverDAO.getDriver(1L)).thenReturn(Optional.of(d));

    Batch b = new Batch(3L, 1L, "poly", Instant.now(), Instant.now());
    when(batchDAO.getBatchForDriver(1L)).thenReturn(Optional.of(b));

    Optional<Batch> got = service.getDriverBatch(1L);
    assertTrue(got.isPresent());
    assertEquals(3L, got.get().id);

    verify(driverDAO).getDriver(1L);
    verify(batchDAO).getBatchForDriver(1L);
  }
}
