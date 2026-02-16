package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Driver;


import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DriverDAO {
    private final Connection conn;

    public DriverDAO(Connection conn) {
        this.conn = conn;
    }

    public long createDriver(long restaurantId, String name, String phoneNumber, boolean onShift) throws SQLException {
        final String sql =
                "INSERT INTO Driver(restaurant_id, name, phone_number, on_shift) " +
                "VALUES (?, ?, ?, ?) RETURNING id;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            ps.setString(2, name);
            ps.setString(3, phoneNumber);
            ps.setBoolean(4, onShift);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    public Optional<Driver> getDriver(long driverId) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, name, phone_number, on_shift " +
                "FROM Driver WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, driverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Driver(
                        rs.getLong("id"),
                        rs.getLong("restaurant_id"),
                        rs.getString("name"),
                        rs.getString("phone_number"),
                        rs.getBoolean("on_shift")
                ));
            }
        }
    }

    public void setDriverShift(long driverId, boolean onShift) throws SQLException {
        final String sql = "UPDATE Driver SET on_shift = ? WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, onShift);
            ps.setLong(2, driverId);
            ps.executeUpdate();
        }
    }

    public List<Driver> listDriversForRestaurant(long restaurantId, boolean onShiftOnly) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, name, phone_number, on_shift " +
                "FROM Driver " +
                "WHERE restaurant_id = ? " +
                (onShiftOnly ? "AND on_shift = TRUE " : "") +
                "ORDER BY id;";

        List<Driver> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Driver(
                            rs.getLong("id"),
                            rs.getLong("restaurant_id"),
                            rs.getString("name"),
                            rs.getString("phone_number"),
                            rs.getBoolean("on_shift")
                    ));
                }
            }
        }
        return out;
    }

     /** Updates mutable driver fields (NOT id, NOT restaurant_id, NOT on_shift). */
    public boolean updateDriver(long driverId, String name, String phoneNumber) throws SQLException {
        final String sql = "UPDATE Driver SET name = ?, phone_number = ? WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phoneNumber);
            ps.setLong(3, driverId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Deletes a driver row by id. Returns true if a row was deleted. */
    public boolean deleteDriver(long driverId) throws SQLException {
        final String sql = "DELETE FROM Driver WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, driverId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean hasOnShiftDrivers(long restaurantId) throws SQLException {
        final String sql =
            "SELECT 1 FROM Driver WHERE restaurant_id = ? AND on_shift = TRUE LIMIT 1;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
            return rs.next();
            }
        }
    }
}
