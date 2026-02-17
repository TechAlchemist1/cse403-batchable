package com.batchable.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.MenuItem;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MenuService mock-based unit tests.
 *
 * These tests validate:
 *  - input validation
 *  - domain invariants (restaurant exists, no duplicates)
 *  - correct DAO interactions
 *  - exception wrapping behavior on SQLException
 */
@ExtendWith(MockitoExtension.class)
public class MenuServiceTest {

  @Mock private MenuItemDAO menuItemDAO;
  @Mock private RestaurantDAO restaurantDAO;

  private MenuService menuService;

  @BeforeEach
  void setUp() {
    menuService = new MenuService(menuItemDAO, restaurantDAO);
  }

  // ---- createMenuItem ----

  @Test
  void createMenuItem_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(null));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  @Test
  void createMenuItem_restaurantIdNonPositive_throwsIAE() {
    MenuItem mi = new MenuItem(0, 0, "Burger");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(mi));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  @Test
  void createMenuItem_blankName_throwsIAE() {
    MenuItem mi = new MenuItem(0, 1, "   ");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(mi));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  @Test
  void createMenuItem_positiveId_throwsISE() {
    MenuItem mi = new MenuItem(123, 1, "Burger");
    assertThrows(IllegalStateException.class, () -> menuService.createMenuItem(mi));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  @Test
  void createMenuItem_restaurantDoesNotExist_throwsIAE_andDoesNotInsert() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(false);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(mi));
    assertTrue(ex.getMessage().contains("Restaurant does not exist"));

    verify(restaurantDAO).restaurantExists(7L);
    verifyNoInteractions(menuItemDAO);
  }

  @Test
  void createMenuItem_duplicateName_throwsISE_andDoesNotInsert() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(true);
    when(menuItemDAO.menuItemExistsForRestaurantByName(7L, "Burger")).thenReturn(true);

    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> menuService.createMenuItem(mi));
    assertTrue(ex.getMessage().contains("Duplicate menu item"));

    verify(restaurantDAO).restaurantExists(7L);
    verify(menuItemDAO).menuItemExistsForRestaurantByName(7L, "Burger");
    verify(menuItemDAO, never()).createMenuItem(anyLong(), anyString());
  }

  @Test
  void createMenuItem_happyPath_returnsGeneratedId_andCallsDAOs() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(true);
    when(menuItemDAO.menuItemExistsForRestaurantByName(7L, "Burger")).thenReturn(false);
    when(menuItemDAO.createMenuItem(7L, "Burger")).thenReturn(99L);

    long id = menuService.createMenuItem(mi);
    assertEquals(99L, id);

    verify(restaurantDAO).restaurantExists(7L);
    verify(menuItemDAO).menuItemExistsForRestaurantByName(7L, "Burger");
    verify(menuItemDAO).createMenuItem(7L, "Burger");
    verifyNoMoreInteractions(menuItemDAO, restaurantDAO);
  }

  @Test
  void createMenuItem_sqlException_wrappedInRuntimeException() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(true);
    when(menuItemDAO.menuItemExistsForRestaurantByName(7L, "Burger")).thenReturn(false);
    when(menuItemDAO.createMenuItem(7L, "Burger")).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> menuService.createMenuItem(mi));
    assertTrue(ex.getMessage().contains("Failed to create menu item"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(restaurantDAO).restaurantExists(7L);
    verify(menuItemDAO).menuItemExistsForRestaurantByName(7L, "Burger");
    verify(menuItemDAO).createMenuItem(7L, "Burger");
  }

  // ---- removeMenuItem ----

  @Test
  void removeMenuItem_nonPositiveId_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(0));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  @Test
  void removeMenuItem_missing_throwsIAE_andDoesNotDelete() throws Exception {
    when(menuItemDAO.getMenuItem(5L)).thenReturn(Optional.empty());

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(5L));
    assertTrue(ex.getMessage().contains("Menu item does not exist"));

    verify(menuItemDAO).getMenuItem(5L);
    verify(menuItemDAO, never()).deleteMenuItem(anyLong());
    verifyNoInteractions(restaurantDAO);
  }

  @Test
  void removeMenuItem_deleteReturnsFalse_throwsIAE() throws Exception {
    when(menuItemDAO.getMenuItem(5L)).thenReturn(Optional.of(new MenuItem(5L, 7L, "Burger")));
    when(menuItemDAO.deleteMenuItem(5L)).thenReturn(false);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(5L));
    assertTrue(ex.getMessage().contains("Menu item does not exist"));

    verify(menuItemDAO).getMenuItem(5L);
    verify(menuItemDAO).deleteMenuItem(5L);
    verifyNoInteractions(restaurantDAO);
  }

  @Test
  void removeMenuItem_happyPath_deletesOnce() throws Exception {
    when(menuItemDAO.getMenuItem(5L)).thenReturn(Optional.of(new MenuItem(5L, 7L, "Burger")));
    when(menuItemDAO.deleteMenuItem(5L)).thenReturn(true);

    assertDoesNotThrow(() -> menuService.removeMenuItem(5L));

    verify(menuItemDAO).getMenuItem(5L);
    verify(menuItemDAO).deleteMenuItem(5L);
    verifyNoInteractions(restaurantDAO);
  }

  @Test
  void removeMenuItem_sqlException_wrappedInRuntimeException() throws Exception {
    when(menuItemDAO.getMenuItem(5L)).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> menuService.removeMenuItem(5L));
    assertTrue(ex.getMessage().contains("Failed to remove menu item"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(menuItemDAO).getMenuItem(5L);
    verifyNoInteractions(restaurantDAO);
  }
}
