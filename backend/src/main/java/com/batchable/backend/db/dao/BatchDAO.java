package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Batch;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BatchDAO {
  private final Connection conn;

  public BatchDAO(Connection conn) {
    this.conn = conn;
  }

  private static Timestamp ts(Instant i) {
    return (i == null) ? null : Timestamp.from(i);
  }

  private static Instant instant(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return (t == null) ? null : t.toInstant();
  }

  public long createBatch(long driverId, String routePolyline, Instant dispatchTime, Instant expectedCompletionTime) throws SQLException {
      final String sql =
        "INSERT INTO Batch(driver_id, route, dispatch_time, expected_completion_time) " +
                "VALUES (?, ?, ?, ?) RETURNING id;";
      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, driverId);
        ps.setString(2, routePolyline);
        ps.setTimestamp(3, ts(dispatchTime));
        ps.setTimestamp(4, ts(expectedCompletionTime));
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
            return rs.getLong("id");
        }
    }
  }

  public Optional<Batch> getBatch(long batchId) throws SQLException {
    final String sql =
      "SELECT id, driver_id, route, dispatch_time, expected_completion_time " +
      "FROM Batch WHERE id = ?;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, batchId);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) return Optional.empty();
            return Optional.of(mapBatch(rs));
          }
        }
    }

  public Optional<Batch> getBatchForDriver(long driverId) throws SQLException {
    final String sql =
      "SELECT id, driver_id, route, dispatch_time, expected_completion_time " +
      "FROM Batch WHERE driver_id = ? ORDER BY id DESC LIMIT 1;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(mapBatch(rs));
      }
    }
  }

  public List<Batch> listBatchesForDriver(long driverId) throws SQLException {
    final String sql =
      "SELECT id, driver_id, route, dispatch_time, expected_completion_time " +
      "FROM Batch WHERE driver_id = ? ORDER BY id;";
    List<Batch> out = new ArrayList<>();
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapBatch(rs));
        }
      }
    }
    return out;
  }

  public boolean updateBatch(long batchId, String routePolyline, Instant dispatchTime, Instant expectedCompletionTime)
      throws SQLException {
    final String sql =
      "UPDATE Batch SET route = ?, dispatch_time = ?, expected_completion_time = ? WHERE id = ?;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, routePolyline);
      ps.setTimestamp(2, ts(dispatchTime));
      ps.setTimestamp(3, ts(expectedCompletionTime));
      ps.setLong(4, batchId);
      return ps.executeUpdate() == 1;
    }
  }

  public boolean updateBatchDriver(long batchId, long driverId) throws SQLException {
    final String sql = "UPDATE Batch SET driver_id = ? WHERE id = ?;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      ps.setLong(2, batchId);
      return ps.executeUpdate() == 1;
    }
  }

  public boolean deleteBatch(long batchId) throws SQLException {
    final String sql = "DELETE FROM Batch WHERE id = ?;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, batchId);
      return ps.executeUpdate() == 1;
    }
  }

  public boolean batchExists(long batchId) throws SQLException {
    final String sql = "SELECT 1 FROM Batch WHERE id = ? LIMIT 1;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, batchId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  public boolean batchExistsForDriver(long driverId) throws SQLException {
    final String sql = "SELECT 1 FROM Batch WHERE driver_id = ? LIMIT 1;";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, driverId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  private static Batch mapBatch(ResultSet rs) throws SQLException {
    return new Batch(
      rs.getLong("id"),
      rs.getLong("driver_id"),
      rs.getString("route"),
      instant(rs, "dispatch_time"),
      instant(rs, "expected_completion_time")
    );
  }
}
