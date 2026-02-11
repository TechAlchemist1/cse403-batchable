package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Order;


import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OrderDAO {
    private final Connection conn;

    public OrderDAO(Connection conn) {
        this.conn = conn;
    }

    private static Timestamp ts(Instant i) {
        return (i == null) ? null : Timestamp.from(i);
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return (t == null) ? null : t.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        return (o == null) ? null : rs.getLong(col);
    }

    public long createOrder(
            long restaurantId,
            String destination,
            String itemNamesJson,         // e.g. ["Burger","Fries"]
            Instant initialTime,
            Instant deliveryTime,
            Instant cookedTime,
            Order.State state,
            boolean highPriority,
            Long batchId                  // nullable
    ) throws SQLException {
        final String sql =
                "INSERT INTO \"Order\"(" +
                " restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time," +
                " state, high_priority, batch_id" +
                ") VALUES (" +
                " ?, ?, ?::json, ?, ?, ?, ?::order_state, ?, ?" +
                ") RETURNING id;";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            ps.setString(2, destination);
            ps.setString(3, itemNamesJson);
            ps.setTimestamp(4, ts(initialTime));
            ps.setTimestamp(5, ts(deliveryTime));
            ps.setTimestamp(6, ts(cookedTime));
            ps.setString(7, state.name());
            ps.setBoolean(8, highPriority);

            if (batchId == null) ps.setNull(9, Types.BIGINT);
            else ps.setLong(9, batchId);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    public Optional<Order> getOrder(long orderId) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time, " +
                "       state, high_priority, batch_id " +
                "FROM \"Order\" WHERE id = ?;";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapOrder(rs));
            }
        }
    }

    public void updateOrderState(long orderId, Order.State newState) throws SQLException {
        final String sql = "UPDATE \"Order\" SET state = ?::order_state WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newState.name());
            ps.setLong(2, orderId);
            ps.executeUpdate();
        }
    }

    public void assignOrderToBatch(long orderId, long batchId) throws SQLException {
        final String sql = "UPDATE \"Order\" SET batch_id = ? WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, batchId);
            ps.setLong(2, orderId);
            ps.executeUpdate();
        }
    }

    public void unassignOrderFromBatch(long orderId) throws SQLException {
        final String sql = "UPDATE \"Order\" SET batch_id = NULL WHERE id = ?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }

    public List<Order> listOrdersInBatch(long batchId) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time, " +
                "       state, high_priority, batch_id " +
                "FROM \"Order\" WHERE batch_id = ? ORDER BY id;";

        List<Order> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, batchId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapOrder(rs));
                }
            }
        }
        return out;
    }

    public List<Order> listOpenOrdersForRestaurant(long restaurantId) throws SQLException {
        final String sql =
                "SELECT id, restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time, " +
                "       state, high_priority, batch_id " +
                "FROM \"Order\" " +
                "WHERE restaurant_id = ? AND state <> 'DELIVERED' " +
                "ORDER BY id;";

        List<Order> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, restaurantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapOrder(rs));
                }
            }
        }
        return out;
    }

    private static Order mapOrder(ResultSet rs) throws SQLException {
        return new Order(
                rs.getLong("id"),
                rs.getLong("restaurant_id"),
                rs.getString("destination"),
                rs.getString("item_names"),
                instant(rs, "initial_time"),
                instant(rs, "delivery_time"),
                instant(rs, "cooked_time"),
                Order.State.valueOf(rs.getString("state")),
                rs.getBoolean("high_priority"),
                nullableLong(rs, "batch_id")
        );
    }
}
