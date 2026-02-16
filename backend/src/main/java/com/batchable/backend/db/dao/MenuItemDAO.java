package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.MenuItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MenuItemDAO {
    private final Connection conn;

    public MenuItemDAO(Connection conn) {
        this.conn = conn;
    }

    public long createMenuItem(long restaurantId, String name) throws SQLException {
        final String sql =
                "INSERT INTO MenuItem(restaurant_id, name) VALUES (?, ?) RETURNING id;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    public Optional<MenuItem> getMenuItem(long menuItemId) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, name FROM MenuItem WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, menuItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new MenuItem(
                        rs.getLong("id"),
                        rs.getLong("restaurant_id"),
                        rs.getString("name")
                ));
            }
        }
    }

    public List<MenuItem> listMenuItems(long restaurantId) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, name FROM MenuItem WHERE restaurant_id = ? ORDER BY id;";

        List<MenuItem> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MenuItem(
                            rs.getLong("id"),
                            rs.getLong("restaurant_id"),
                            rs.getString("name")
                    ));
                }
            }
        }
        return out;
    }

    /** Returns true if a row was deleted (menuItemId existed). */
    public boolean deleteMenuItem(long menuItemId) throws SQLException {
        final String sql = "DELETE FROM MenuItem WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, menuItemId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Useful for MenuService "no duplicates" invariant. */
    public boolean menuItemExistsForRestaurantByName(long restaurantId, String name) throws SQLException {
        final String sql =
            "SELECT 1 FROM MenuItem WHERE restaurant_id = ? AND name = ? LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
