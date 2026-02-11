package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Restaurant;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RestaurantDAO {
    private final Connection conn;

    public RestaurantDAO(Connection conn) {
        this.conn = conn;
    }

    public long createRestaurant(String name, String location) throws SQLException {
        final String sql = "INSERT INTO Restaurant(name, location) VALUES (?, ?) RETURNING id;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, location);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    public Optional<Restaurant> getRestaurant(long id) throws SQLException {
        final String sql = "SELECT id, name, location FROM Restaurant WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Restaurant(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("location")
                ));
            }
        }
    }

    public List<Restaurant> listRestaurants() throws SQLException {
        final String sql = "SELECT id, name, location FROM Restaurant ORDER BY id;";
        List<Restaurant> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Restaurant(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("location")
                ));
            }
        }
        return out;
    }
}
