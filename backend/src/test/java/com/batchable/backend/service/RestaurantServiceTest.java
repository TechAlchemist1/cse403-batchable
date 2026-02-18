package com.batchable.backend.service;

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
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * RestaurantService mock-based unit tests (Mockito).
 *
 * Covers:
 *  - createRestaurant validations + uniqueness check
 *  - updateRestaurant validations + existence + name uniqueness excluding id
 *  - getRestaurant
 *  - removeRestaurant domain rules (no active orders, no on-shift drivers)
 *  - getRestaurantOrders/Drivers/MenuItems validate restaurant exists
 *  - SQLException wrapping behavior
 */
@ExtendWith(MockitoExtension.class)
public class RestaurantServiceTest {

  @Mock private RestaurantDAO restaurantDAO;
  @Mock private OrderDAO orderDAO;
  @Mock private DriverDAO driverDAO;
  @Mock private MenuItemDAO menuItemDAO;
  @Mock private BatchingManager mockBatchingManager;

  private RestaurantService service;

  @BeforeEach
  void setUp() {
    service = new RestaurantService(restaurantDAO, orderDAO, driverDAO, menuItemDAO, mockBatchingManager);
  }

  // ---- helpers ----
  private static Restaurant restaurant(long id, String name, String location) {
    return new Restaurant(id, name, location);
  }

  // ---- createRestaurant ----

  @Test
  void createRestaurant_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.createRestaurant(null));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void createRestaurant_blankName_throwsIAE() {
    Restaurant r = restaurant(0, "   ", "Seattle");
    assertThrows(IllegalArgumentException.class, () -> service.createRestaurant(r));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void createRestaurant_blankLocation_throwsIAE() {
    Restaurant r = restaurant(0, "R1", "   ");
    assertThrows(IllegalArgumentException.class, () -> service.createRestaurant(r));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void createRestaurant_positiveId_throwsISE() {
    Restaurant r = restaurant(10, "R1", "Seattle");
    assertThrows(IllegalStateException.class, () -> service.createRestaurant(r));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void createRestaurant_duplicateName_throwsISE_andDoesNotCreate() throws Exception {
    Restaurant r = restaurant(0, "R1", "Seattle");
    when(restaurantDAO.restaurantExistsByName("R1")).thenReturn(true);

    assertThrows(IllegalStateException.class, () -> service.createRestaurant(r));

    verify(restaurantDAO).restaurantExistsByName("R1");
    verify(restaurantDAO, never()).createRestaurant(anyString(), anyString());
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

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

  @Test
  void updateRestaurant_nonPositiveId_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateRestaurant(0, restaurant(0, "R", "L")));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void updateRestaurant_nullRestaurant_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateRestaurant(1, null));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void updateRestaurant_blankName_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateRestaurant(1, restaurant(0, " ", "Seattle")));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void updateRestaurant_blankLocation_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.updateRestaurant(1, restaurant(0, "R1", " ")));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void updateRestaurant_missingRestaurant_throwsIAE_andDoesNotUpdate() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(5L, restaurant(0, "R1", "Seattle")));

    verify(restaurantDAO).restaurantExists(5L);
    verify(restaurantDAO, never()).updateRestaurant(anyLong(), anyString(), anyString());
  }

  @Test
  void updateRestaurant_nameConflict_throwsISE_andDoesNotUpdate() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(true);

    assertThrows(IllegalStateException.class,
        () -> service.updateRestaurant(5L, restaurant(0, "R2", "Seattle")));

    verify(restaurantDAO).restaurantExists(5L);
    verify(restaurantDAO).restaurantExistsByNameExcludingId(5L, "R2");
    verify(restaurantDAO, never()).updateRestaurant(anyLong(), anyString(), anyString());
  }

  @Test
  void updateRestaurant_updateReturnsFalse_throwsIAE() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(false);
    when(restaurantDAO.updateRestaurant(5L, "R2", "Seattle")).thenReturn(false);

    assertThrows(IllegalArgumentException.class,
        () -> service.updateRestaurant(5L, restaurant(0, "R2", "Seattle")));
  }

  @Test
  void updateRestaurant_happyPath_callsDao() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(false);
    when(restaurantDAO.updateRestaurant(5L, "R2", "Seattle")).thenReturn(true);

    service.updateRestaurant(5L, restaurant(0, "R2", "Seattle"));

    verify(restaurantDAO).updateRestaurant(5L, "R2", "Seattle");
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void updateRestaurant_sqlException_wrapped() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(restaurantDAO.restaurantExistsByNameExcludingId(5L, "R2")).thenReturn(false);
    when(restaurantDAO.updateRestaurant(anyLong(), anyString(), anyString()))
        .thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class,
            () -> service.updateRestaurant(5L, restaurant(0, "R2", "Seattle")));
    assertTrue(ex.getMessage().contains("Failed to update restaurant"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- getRestaurant ----

  @Test
  void getRestaurant_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.getRestaurant(0));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void getRestaurant_missing_throwsIAE() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.getRestaurant(5L));
  }

  @Test
  void getRestaurant_happyPath_returns() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));

    Restaurant got = service.getRestaurant(5L);
    assertSame(r, got);
  }

  @Test
  void getRestaurant_sqlException_wrapped() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getRestaurant(5L));
    assertTrue(ex.getMessage().contains("Failed to retrieve restaurant"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  // ---- removeRestaurant ----

  @Test
  void removeRestaurant_nonPositive_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> service.removeRestaurant(0));
    verifyNoInteractions(restaurantDAO, orderDAO, driverDAO, menuItemDAO);
  }

  @Test
  void removeRestaurant_missing_throwsIAE() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.removeRestaurant(5L));

    verify(restaurantDAO).restaurantExists(5L);
    verifyNoMoreInteractions(restaurantDAO);
    verifyNoInteractions(orderDAO, driverDAO, menuItemDAO);
  }

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

  @Test
  void removeRestaurant_deleteReturnsFalse_throwsIAE() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(false);
    when(driverDAO.hasOnShiftDrivers(5L)).thenReturn(false);
    when(restaurantDAO.deleteRestaurant(5L)).thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> service.removeRestaurant(5L));
  }

  @Test
  void removeRestaurant_happyPath_deletes() throws Exception {
    when(restaurantDAO.restaurantExists(5L)).thenReturn(true);
    when(orderDAO.hasActiveOrdersForRestaurant(5L)).thenReturn(false);
    when(driverDAO.hasOnShiftDrivers(5L)).thenReturn(false);
    when(restaurantDAO.deleteRestaurant(5L)).thenReturn(true);

    service.removeRestaurant(5L);

    verify(restaurantDAO).deleteRestaurant(5L);
  }

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

  // ---- getRestaurantOrders / Drivers / MenuItems (validate restaurant exists via getRestaurant) ----

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

  @Test
  void getRestaurantOrders_missingRestaurant_throwsIAE_andDoesNotList() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getRestaurantOrders(5L));

    verify(orderDAO, never()).listOpenOrdersForRestaurant(anyLong());
  }

  @Test
  void getRestaurantDrivers_missingRestaurant_throwsIAE_andDoesNotList() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getRestaurantDrivers(5L));

    verify(driverDAO, never()).listDriversForRestaurant(anyLong(), anyBoolean());
  }

  @Test
  void getRestaurantMenuItems_missingRestaurant_throwsIAE_andDoesNotList() throws Exception {
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.empty());

    assertThrows(IllegalArgumentException.class, () -> service.getRestaurantMenuItems(5L));

    verify(menuItemDAO, never()).listMenuItems(anyLong());
  }

  @Test
  void getRestaurantOrders_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));
    when(orderDAO.listOpenOrdersForRestaurant(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getRestaurantOrders(5L));
    assertTrue(ex.getMessage().contains("Failed to list restaurant orders"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  @Test
  void getRestaurantDrivers_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));
    when(driverDAO.listDriversForRestaurant(5L, false)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getRestaurantDrivers(5L));
    assertTrue(ex.getMessage().contains("Failed to list restaurant drivers"));
    assertTrue(ex.getCause() instanceof SQLException);
  }

  @Test
  void getRestaurantMenuItems_sqlException_wrapped() throws Exception {
    Restaurant r = restaurant(5, "R1", "Seattle");
    when(restaurantDAO.getRestaurant(5L)).thenReturn(Optional.of(r));
    when(menuItemDAO.listMenuItems(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getRestaurantMenuItems(5L));
    assertTrue(ex.getMessage().contains("Failed to list restaurant menu items"));
    assertTrue(ex.getCause() instanceof SQLException);
  }
}
