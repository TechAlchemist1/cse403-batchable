package com.batchable.backend.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.Driver;
import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.db.models.Order;
import com.batchable.backend.db.models.Restaurant;
import com.batchable.backend.service.BatchingManager;
import com.batchable.backend.service.RestaurantService;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for RestaurantService using Mockito.
 *
 * This test class verifies: - Input validation and uniqueness checks when creating a restaurant. -
 * Update logic: existence, name uniqueness (excluding self), and field updates. - Retrieval of a
 * restaurant by ID. - Deletion rules: cannot delete if there are active orders or on‑shift drivers.
 * - Retrieval of related entities (orders, drivers, menu items) with validation. - Proper wrapping
 * of SQLException into RuntimeException.
 */
@ExtendWith(MockitoExtension.class)
public class RestaurantServiceTest {

  @Mock
  private RestaurantDAO restaurantDAO;
  @Mock
  private OrderDAO orderDAO;
  @Mock
  private DriverDAO driverDAO;
  @Mock
  private MenuItemDAO menuItemDAO;
  @Mock
  private BatchingManager mockBatchingManager;

  private RestaurantService service;

  @BeforeEach
  void setUp() {
    service = new RestaurantService(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  // ---- helpers ----
  private static Restaurant restaurant(long id, String name, String location) {
    return new Restaurant(id, name, location);
  }

  // ---- createRestaurant ----

  /** Verifies that passing null to createRestaurant throws IllegalArgumentException. */
  @Test
  void createRestaurant_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.createRestaurant(null));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that a restaurant with a blank name is rejected. */
  @Test
  void createRestaurant_blankName_throwsIAE() {
    Restaurant r = restaurant(0, "   ", "Seattle");
    assertThrows(IllegalArgumentException.class, () -> service.createRestaurant(r));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that a restaurant with a blank location is rejected. */
  @Test
  void createRestaurant_blankLocation_throwsIAE() {
    Restaurant r = restaurant(0, "R1", "   ");
    assertThrows(IllegalArgumentException.class, () -> service.createRestaurant(r));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /**
   * Verifies that a restaurant with a pre‑assigned positive ID is rejected (must be
   * database‑generated).
   */
  @Test
  void createRestaurant_positiveId_throwsISE() {
    Restaurant r = restaurant(10, "R1", "Seattle");
    assertThrows(IllegalStateException.class, () -> service.createRestaurant(r));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Ensures that a duplicate restaurant name causes an exception and no insert is attempted. */
  @Test
  void createRestaurant_duplicateName_throwsISE_andDoesNotCreate() throws Exception {
    Restaurant r = restaurant(0, "R1", "Seattle");
    when(restaurantDAO.restaurantExistsByName("R1")).thenReturn(true);

    assertThrows(IllegalStateException.class, () -> service.createRestaurant(r));

    verify(restaurantDAO).restaurantExistsByName("R1");
    verify(restaurantDAO, never()).createRestaurant(anyString(), anyString());
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that a valid restaurant is created and the generated ID is returned. */
  @Test
  void createRestaurant_happyPath_returnsId() throws Exception {
    Restaurant r = restaurant(0, "R1", "Seattle");
    when(restaurantDAO.restaurantExistsByName("R1")).thenReturn(false);
    when(restaurantDAO.createRestaurant("R1", "Seattle")).thenReturn(42L);

    long id = service.createRestaurant(r);
    assertEquals(42L, id);

    verify(restaurantDAO).restaurantExistsByName("R1");
    verify(restaurantDAO).createRestaurant("R1", "Seattle");
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that a SQLException from the DAO is wrapped in a RuntimeException. */
  @Test
  void createRestaurant_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(0, "R1", "Seattle");
    when(restaurantDAO.restaurantExistsByName("R1")).thenReturn(false);
    when(restaurantDAO.createRestaurant(anyString(), anyString()))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.createRestaurant(r));
    assertTrue(ex.getMessage().contains("Failed to create restaurant"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- updateRestaurant ----

  /** Verifies that updateRestaurant rejects a non‑positive ID. */
  @Test
  void updateRestaurant_nonPositiveId_throwsIAE() {
    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(restaurant(0, "R", "L")));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that updateRestaurant rejects a null restaurant object. */
  @Test
  void updateRestaurant_nullRestaurant_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateRestaurant(null));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that updateRestaurant rejects a blank name. */
  @Test
  void updateRestaurant_blankName_throwsIAE() {
    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(restaurant(0, " ", "Seattle")));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that updateRestaurant rejects a blank location. */
  @Test
  void updateRestaurant_blankLocation_throwsIAE() {
    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(restaurant(0, "R1", " ")));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Ensures that updateRestaurant throws if the restaurant does not exist. */
  @Test
  void updateRestaurant_missingRestaurant_throwsIAE_andDoesNotUpdate() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(restaurant(5L, "R1", "Seattle")));

    verify(restaurantDAO).restaurantExists(5L);
    verify(restaurantDAO, never()).updateRestaurant(anyLong(), anyString(), anyString());
  }

  /** Ensures that updating to a name already used by another restaurant is rejected. */
  @Test
  void updateRestaurant_nameConflict_throwsISE_andDoesNotUpdate() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(true);

    assertThrows(IllegalStateException.class,
        () -> service.updateRestaurant(restaurant(5L, "R2", "Seattle")));

    verify(restaurantDAO).restaurantExists(5L);
    verify(restaurantDAO).restaurantExistsByNameExcludingId(5L, "R2");
    verify(restaurantDAO, never()).updateRestaurant(anyLong(), anyString(), anyString());
  }

  /** Verifies that if updateRestaurant returns false (no rows affected), an exception is thrown. */
  @Test
  void updateRestaurant_updateReturnsFalse_throwsIAE() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(false);
    when(restaurantDAO.updateRestaurant(5L, "R2", "Seattle")).thenReturn(false);

    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(restaurant(5L, "R2", "Seattle")));
  }

  /** Verifies that a valid update succeeds and calls the DAO. */
  @Test
  void updateRestaurant_happyPath_callsDao() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(false);
    when(restaurantDAO.updateRestaurant(5L, "R2", "Seattle")).thenReturn(true);

    service.updateRestaurant(restaurant(5L, "R2", "Seattle"));

    verify(restaurantDAO).updateRestaurant(5L, "R2", "Seattle");
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that a SQLException from updateRestaurant is wrapped. */
  @Test
  void updateRestaurant_sqlException_wrapped() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(false);
    when(restaurantDAO.updateRestaurant(anyLong(), anyString(), anyString()))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> service.updateRestaurant(restaurant(5L, "R2", "Seattle")));
    assertTrue(ex.getMessage().contains("Failed to update restaurant"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- getRestaurant ----

  /** Verifies that getRestaurant rejects a non‑positive ID. */
  @Test
  void getRestaurant_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.getRestaurant(0));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that getRestaurant throws when the restaurant does not exist. */
  @Test
  void getRestaurant_missing_throwsIAE() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getRestaurant(5L));
  }

  /** Verifies that getRestaurant returns the expected restaurant when found. */
  @Test
  void getRestaurant_happyPath_returns() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));

    Restaurant got = service.getRestaurant(5L);
    assertSame(r, got);
  }

  /** Verifies that a SQLException from getRestaurant is wrapped. */
  @Test
  void getRestaurant_sqlException_wrapped() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getRestaurant(5L));
    assertTrue(ex.getMessage().contains("Failed to retrieve restaurant"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- removeRestaurant ----

  /** Verifies that removeRestaurant rejects a non‑positive ID. */
  @Test
  void removeRestaurant_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.removeRestaurant(0));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  /** Verifies that removeRestaurant throws when the restaurant does not exist. */
  @Test
  void removeRestaurant_missing_throwsIAE() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.removeRestaurant(5L));

    verify(restaurantDAO).restaurantExists(5L);
    verifyNoMoreInteractions(restaurantDAO);
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

  /** Ensures that deletion is blocked if there are active orders. */
  @Test
  void removeRestaurant_activeOrders_throwsISE_andDoesNotDelete() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(true);

    assertThrows(IllegalStateException.class, () -> service.removeRestaurant(5L));

    verify(restaurantDAO).restaurantExists(5L);
    verify(orderDAO).hasActiveOrdersForRestaurant(5L);
    verify(driverDAO, never()).hasOnShiftDrivers(anyLong());
    verify(restaurantDAO, never()).deleteRestaurant(anyLong());
  }

  /** Ensures that deletion is blocked if there are on‑shift drivers (even if no active orders). */
  @Test
  void removeRestaurant_hasOnShiftDrivers_throwsISE_andDoesNotDelete() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(false);
    when(driverDAO.hasOnShiftDrivers(5L)).thenReturn(true);

    assertThrows(IllegalStateException.class, () -> service.removeRestaurant(5L));

    verify(restaurantDAO).restaurantExists(5L);
    verify(orderDAO).hasActiveOrdersForRestaurant(5L);
    verify(driverDAO).hasOnShiftDrivers(5L);
    verify(restaurantDAO, never()).deleteRestaurant(anyLong());
  }

  /** Verifies that if deleteRestaurant returns false, an exception is thrown. */
  @Test
  void removeRestaurant_deleteReturnsFalse_throwsIAE() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(false);
    when(driverDAO.hasOnShiftDrivers(5L)).thenReturn(false);
    when(restaurantDAO.deleteRestaurant(5L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.removeRestaurant(5L));
  }

  /** Verifies that a restaurant can be deleted when conditions are met. */
  @Test
  void removeRestaurant_happyPath_deletes() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(false);
    when(driverDAO.hasOnShiftDrivers(5L)).thenReturn(false);
    when(restaurantDAO.deleteRestaurant(5L)).thenReturn(true);

    service.removeRestaurant(5L);

    verify(restaurantDAO).deleteRestaurant(5L);
  }

  /** Verifies that a SQLException from deleteRestaurant is wrapped. */
  @Test
  void removeRestaurant_sqlException_wrapped() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(false);
    when(driverDAO.hasOnShiftDrivers(5L)).thenReturn(false);
    when(restaurantDAO.deleteRestaurant(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.removeRestaurant(5L));
    assertTrue(ex.getMessage().contains("Failed to remove restaurant"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- getRestaurantOrders / Drivers / MenuItems (validate restaurant exists via getRestaurant)
  // ----

  /** Verifies that getRestaurantOrders validates the restaurant and returns open orders. */
  @Test
  void getRestaurantOrders_validatesRestaurant_thenListsOpenOrders() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));

    Order o1 = mock(Order.class);
    Order o2 = mock(Order.class);
    when(orderDAO.listOpenOrdersForRestaurant(5L)).thenReturn(List.of(o1, o2));

    List<Order> out = service.getRestaurantOrders(5L);
    assertEquals(2, out.size());

    verify(restaurantDAO).getRestaurant(5L);
    verify(orderDAO).listOpenOrdersForRestaurant(5L);
  }

  /** Verifies that getRestaurantDrivers validates the restaurant and returns all drivers. */
  @Test
  void getRestaurantDrivers_validatesRestaurant_thenListsDrivers() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));

    Driver d1 = mock(Driver.class);
    when(driverDAO.listDriversForRestaurant(5L, false)).thenReturn(List.of(d1));

    List<Driver> out = service.getRestaurantDrivers(5L);
    assertEquals(1, out.size());

    verify(restaurantDAO).getRestaurant(5L);
    verify(driverDAO).listDriversForRestaurant(5L, false);
  }

  /** Verifies that getRestaurantMenuItems validates the restaurant and returns menu items. */
  @Test
  void getRestaurantMenuItems_validatesRestaurant_thenListsMenuItems() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));

    MenuItem m1 = mock(MenuItem.class);
    MenuItem m2 = mock(MenuItem.class);
    when(menuItemDAO.listMenuItems(5L)).thenReturn(List.of(m1, m2));

    List<MenuItem> out = service.getRestaurantMenuItems(5L);
    assertEquals(2, out.size());

    verify(restaurantDAO).getRestaurant(5L);
    verify(menuItemDAO).listMenuItems(5L);
  }

  /** Ensures that getRestaurantOrders throws if the restaurant does not exist. */
  @Test
  void getRestaurantOrders_missingRestaurant_throwsIAE_andDoesNotList() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getRestaurantOrders(5L));

    verify(orderDAO, never()).listOpenOrdersForRestaurant(anyLong());
  }

  /** Ensures that getRestaurantDrivers throws if the restaurant does not exist. */
  @Test
  void getRestaurantDrivers_missingRestaurant_throwsIAE_andDoesNotList() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getRestaurantDrivers(5L));

    verify(driverDAO, never()).listDriversForRestaurant(anyLong(), anyBoolean());
  }

  /** Ensures that getRestaurantMenuItems throws if the restaurant does not exist. */
  @Test
  void getRestaurantMenuItems_missingRestaurant_throwsIAE_andDoesNotList() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getRestaurantMenuItems(5L));

    verify(menuItemDAO, never()).listMenuItems(anyLong());
  }

  /** Verifies that a SQLException from listOpenOrdersForRestaurant is wrapped. */
  @Test
  void getRestaurantOrders_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));
    when(orderDAO.listOpenOrdersForRestaurant(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getRestaurantOrders(5L));
    assertTrue(ex.getMessage().contains("Failed to list restaurant orders"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  /** Verifies that a SQLException from listDriversForRestaurant is wrapped. */
  @Test
  void getRestaurantDrivers_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));
    when(driverDAO.listDriversForRestaurant(5L, false)).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getRestaurantDrivers(5L));
    assertTrue(ex.getMessage().contains("Failed to list restaurant drivers"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  /** Verifies that a SQLException from listMenuItems is wrapped. */
  @Test
  void getRestaurantMenuItems_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));
    when(menuItemDAO.listMenuItems(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getRestaurantMenuItems(5L));
    assertTrue(ex.getMessage().contains("Failed to list restaurant menu items"));
    assertTrue(ex.getCause() instanceof SQLException);
  }
}
