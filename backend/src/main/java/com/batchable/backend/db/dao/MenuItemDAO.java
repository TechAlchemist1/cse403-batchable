package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.MenuItem;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MenuItemDAO {

  // Spring-managed connection pool source
  private final DataSource dataSource;

  public MenuItemDAO(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public long createMenuItem(long restaurantId, String name) throws SQLException {
    final String sql = "INSERT INTO \"menu_item\"(restaurant_id, name) VALUES (?, ?) RETURNING id;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, restaurantId);
        ps.setString(2, name);

        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          long id = rs.getLong("id");
          conn.commit();
          return id;
        }
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  public Optional<MenuItem> getMenuItem(long menuItemId) throws SQLException {
    final String sql = "SELECT id, restaurant_id, name FROM \"menu_item\" WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, menuItemId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional
            .of(new MenuItem(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("name")));
      }
    }
  }

  public void updateMenuItem(long menuItemId, long restaurantId, String name) throws SQLException {
    final String sql = "UPDATE \"menu_item\" SET name = ? WHERE id = ? AND restaurant_id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, name);
        ps.setLong(2, menuItemId);
        ps.setLong(3, restaurantId);

        int rows = ps.executeUpdate();
        if (rows == 0) {
          throw new SQLException("No such menu item for this restaurant.");
        }

        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  public List<MenuItem> listMenuItems(long restaurantId) throws SQLException {
    final String sql =
        "SELECT id, restaurant_id, name FROM \"menu_item\" WHERE restaurant_id = ? ORDER BY id;";

    List<MenuItem> out = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, restaurantId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(
              new MenuItem(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("name")));
        }
      }
    }

    return out;
  }

  /** Returns true if a row was deleted (menuItemId existed). */
  public boolean deleteMenuItem(long menuItemId) throws SQLException {
    final String sql = "DELETE FROM \"menu_item\" WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, menuItemId);
        boolean deleted = ps.executeUpdate() == 1;
        conn.commit();
        return deleted;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /** Useful for MenuService "no duplicates" invariant. */
  public boolean menuItemExistsForRestaurantByName(long restaurantId, String name)
      throws SQLException {
    final String sql = "SELECT 1 FROM \"menu_item\" WHERE restaurant_id = ? AND name = ? LIMIT 1;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, restaurantId);
      ps.setString(2, name);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }
}
