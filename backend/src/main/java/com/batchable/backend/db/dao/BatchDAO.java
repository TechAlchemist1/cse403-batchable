package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Batch;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Batch table.
 *
 * Responsible ONLY for direct database interaction.
 * No business logic should live here.
 */
@Repository
public class BatchDAO {

  // Spring-managed connection pool source
  private final DataSource dataSource;

  /**
   * Constructor injection of DataSource.
   * Spring will provide this automatically.
   */
  public BatchDAO(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** Convert Instant -> SQL Timestamp (null-safe). */
  private static Timestamp ts(Instant i) {
    return (i == null) ? null : Timestamp.from(i);
  }

  /** Convert SQL Timestamp -> Instant (null-safe). */
  private static Instant instant(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return (t == null) ? null : t.toInstant();
  }

  /** Creates a new batch row and returns the generated ID. */
  public long createBatch(long driverId, String routePolyline, Instant dispatchTime, Instant expectedCompletionTime)
      throws SQLException {

    final String sql =
        "INSERT INTO Batch(driver_id, route, dispatch_time, expected_completion_time) " +
        "VALUES (?, ?, ?, ?) RETURNING id;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

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

  /** Retrieves a batch by ID. Returns Optional.empty() if not found. */
  public Optional<Batch> getBatch(long batchId) throws SQLException {
    final String sql =
        "SELECT id, driver_id, route, dispatch_time, expected_completion_time " +
        "FROM Batch WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, batchId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(mapBatch(rs));
      }
    }
  }

  /** Returns the most recent batch for a driver (by highest id). */
  public Optional<Batch> getBatchForDriver(long driverId) throws SQLException {
    final String sql =
        "SELECT id, driver_id, route, dispatch_time, expected_completion_time " +
        "FROM Batch WHERE driver_id = ? ORDER BY id DESC LIMIT 1;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(mapBatch(rs));
      }
    }
  }

  /** Lists all batches for a driver ordered by id. */
  public List<Batch> listBatchesForDriver(long driverId) throws SQLException {
    final String sql =
        "SELECT id, driver_id, route, dispatch_time, expected_completion_time " +
        "FROM Batch WHERE driver_id = ? ORDER BY id;";

    List<Batch> out = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapBatch(rs));
        }
      }
    }

    return out;
  }

  /** Updates mutable batch fields (NOT id). Returns true iff one row updated. */
  public boolean updateBatch(long batchId, String routePolyline, Instant dispatchTime, Instant expectedCompletionTime)
      throws SQLException {

    final String sql =
        "UPDATE Batch SET route = ?, dispatch_time = ?, expected_completion_time = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, routePolyline);
      ps.setTimestamp(2, ts(dispatchTime));
      ps.setTimestamp(3, ts(expectedCompletionTime));
      ps.setLong(4, batchId);

      return ps.executeUpdate() == 1;
    }
  }

  /** Reassigns a batch to a different driver. Returns true iff one row updated. */
  public boolean updateBatchDriver(long batchId, long driverId) throws SQLException {
    final String sql = "UPDATE Batch SET driver_id = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);
      ps.setLong(2, batchId);

      return ps.executeUpdate() == 1;
    }
  }

  /** Deletes a batch row by id. Returns true iff a row was deleted. */
  public boolean deleteBatch(long batchId) throws SQLException {
    final String sql = "DELETE FROM Batch WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, batchId);
      return ps.executeUpdate() == 1;
    }
  }

  /** Returns true if a batch exists for the given id. */
  public boolean batchExists(long batchId) throws SQLException {
    final String sql = "SELECT 1 FROM Batch WHERE id = ? LIMIT 1;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, batchId);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /** Returns true if any batch exists for the given driver id. */
  public boolean batchExistsForDriver(long driverId) throws SQLException {
    final String sql = "SELECT 1 FROM Batch WHERE driver_id = ? LIMIT 1;";

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /** Maps the current ResultSet row to a Batch model object. */
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
