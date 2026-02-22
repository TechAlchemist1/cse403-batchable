package com.batchable.backend.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.RestaurantDAO;
import com.batchable.backend.db.models.MenuItem;
import com.batchable.backend.service.MenuService;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mock-based unit tests for MenuService.
 * 
 * These tests validate: - Input validation (null, blank name, non‑positive restaurant ID, etc.) -
 * Domain invariants (restaurant must exist, no duplicate names within a restaurant) - Correct
 * delegation to MenuItemDAO and RestaurantDAO - Wrapping of SQLException into RuntimeException
 */
@ExtendWith(MockitoExtension.class)
public class MenuServiceTest {

  @Mock
  private MenuItemDAO menuItemDAO;
  @Mock
  private RestaurantDAO restaurantDAO;

  private MenuService menuService;

  @BeforeEach
  void setUp() {
    menuService = new MenuService(menuItemDAO, restaurantDAO);
  }

  // ---- createMenuItem ----

  /** Verifies that passing null to createMenuItem throws IllegalArgumentException. */
  @Test
  void createMenuItem_null_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(null));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  /** Verifies that a MenuItem with a non‑positive restaurant ID is rejected. */
  @Test
  void createMenuItem_restaurantIdNonPositive_throwsIAE() {
    MenuItem mi = new MenuItem(0, 0, "Burger");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(mi));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  /** Verifies that a MenuItem with a blank name is rejected. */
  @Test
  void createMenuItem_blankName_throwsIAE() {
    MenuItem mi = new MenuItem(0, 1, "   ");
    assertThrows(IllegalArgumentException.class, () -> menuService.createMenuItem(mi));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  /**
   * Verifies that a MenuItem with a pre‑assigned positive ID is rejected (must be
   * database‑generated).
   */
  @Test
  void createMenuItem_positiveId_throwsISE() {
    MenuItem mi = new MenuItem(123, 1, "Burger");
    assertThrows(IllegalStateException.class, () -> menuService.createMenuItem(mi));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  /**
   * Ensures that creating a menu item for a non‑existent restaurant is rejected, and that no insert
   * is attempted.
   */
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

  /**
   * Ensures that a duplicate menu item name within the same restaurant is rejected, and that no
   * insert is attempted.
   */
  @Test
  void createMenuItem_duplicateName_throwsISE_andDoesNotInsert() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(true);
    when(menuItemDAO.createMenuItem(7L, "Burger")).thenThrow(SQLException.class);

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> menuService.createMenuItem(mi));
    assertTrue(ex.getMessage().contains("Failed to create menu item"));
    verify(restaurantDAO).restaurantExists(7L);
    verify(menuItemDAO).createMenuItem(7L, "Burger");
  }

  /**
   * Verifies happy‑path creation: all checks pass, DAO is called, and the generated ID is returned.
   */
  @Test
  void createMenuItem_happyPath_returnsGeneratedId_andCallsDAOs() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(true);
    when(menuItemDAO.createMenuItem(7L, "Burger")).thenReturn(99L);

    long id = menuService.createMenuItem(mi);
    assertEquals(99L, id);

    verify(restaurantDAO).restaurantExists(7L);
    verify(menuItemDAO).createMenuItem(7L, "Burger");
    verifyNoMoreInteractions(menuItemDAO, restaurantDAO);
  }

  /**
   * Verifies that a SQLException thrown by the DAO is wrapped in a RuntimeException with an
   * appropriate message.
   */
  @Test
  void createMenuItem_sqlException_wrappedInRuntimeException() throws Exception {
    MenuItem mi = new MenuItem(0, 7, "Burger");

    when(restaurantDAO.restaurantExists(7L)).thenReturn(true);
    when(menuItemDAO.createMenuItem(7L, "Burger")).thenThrow(new SQLException("boom"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> menuService.createMenuItem(mi));
    assertTrue(ex.getMessage().contains("Failed to create menu item"));
    assertTrue(ex.getCause() instanceof SQLException);

    verify(restaurantDAO).restaurantExists(7L);
    verify(menuItemDAO).createMenuItem(7L, "Burger");
  }

  // ---- removeMenuItem ----

  /** Verifies that removeMenuItem rejects a non‑positive ID. */
  @Test
  void removeMenuItem_nonPositiveId_throwsIAE() {
    assertThrows(IllegalArgumentException.class, () -> menuService.removeMenuItem(0));
    verifyNoInteractions(menuItemDAO, restaurantDAO);
  }

  /**
   * Ensures that attempting to remove a non‑existent menu item throws an exception and that
   * deleteMenuItem is never called.
   */
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

  /**
   * Verifies that if deleteMenuItem returns false (meaning no rows were affected), an
   * IllegalArgumentException is thrown.
   */
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

  /** Verifies successful deletion of an existing menu item. */
  @Test
  void removeMenuItem_happyPath_deletesOnce() throws Exception {
    when(menuItemDAO.getMenuItem(5L)).thenReturn(Optional.of(new MenuItem(5L, 7L, "Burger")));
    when(menuItemDAO.deleteMenuItem(5L)).thenReturn(true);

    assertDoesNotThrow(() -> menuService.removeMenuItem(5L));

    verify(menuItemDAO).getMenuItem(5L);
    verify(menuItemDAO).deleteMenuItem(5L);
    verifyNoInteractions(restaurantDAO);
  }

  /**
   * Verifies that a SQLException from getMenuItem is wrapped in a RuntimeException with an
   * appropriate message.
   */
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
