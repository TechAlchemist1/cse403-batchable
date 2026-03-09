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
 * Responsible ONLY for direct database interaction. No business logic should live here.
 */
@Repository
public class BatchDAO {

  // Spring-managed connection pool source
  private final DataSource dataSource;

  /**
   * Constructor injection of DataSource. Spring will provide this automatically.
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
  public long createBatch(long driverId, String routePolyline, Instant dispatchTime,
      Instant completionTime) throws SQLException {

    final String sql =
        "INSERT INTO Batch(driver_id, route, dispatch_time, completion_time, finished) "
            + "VALUES (?, ?, ?, ?, ?) RETURNING id;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, driverId);
        ps.setString(2, routePolyline);
        ps.setTimestamp(3, ts(dispatchTime));
        ps.setTimestamp(4, ts(completionTime));
        ps.setBoolean(5, false); // new batches start unfinished

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

  /** Retrieves a batch by ID. Returns Optional.empty() if not found. */
  public Optional<Batch> getBatch(long batchId) throws SQLException {
    final String sql = "SELECT id, driver_id, route, dispatch_time, completion_time, finished "
        + "FROM Batch WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, batchId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(mapBatch(rs));
      }
    }
  }

  /**
   * Removes all unfinished batches from the database. This is a destructive cleanup operation.
   *
   * This method operates strictly at the persistence layer: No business logic is applied No events
   * are emitted No external systems (e.g., Twilio) are notified
   *
   * Callers are responsible for coordinating any higher-level effects (such as notifying drivers or
   * refreshing in-memory state).
   */
  public void removeAllUnfinishedBatches() throws SQLException {

    final String sql = "DELETE FROM Batch WHERE finished = false;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.executeUpdate();
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Returns the current batch assigned to the driver if applicable, otherwise null. Uses invariant
   * that drivers have at most one unfinished batch at a time.
   */
  public Optional<Batch> getBatchForDriver(long driverId) throws SQLException {
    final String sql = "SELECT id, driver_id, route, dispatch_time, completion_time, finished "
        + "FROM Batch WHERE driver_id = ? AND finished = ? ORDER BY id DESC LIMIT 1;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);
      ps.setBoolean(2, false);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(mapBatch(rs));
      }
    }
  }

  /** Lists all batches for a driver ordered by id. */
  public List<Batch> listBatchesForDriver(long driverId) throws SQLException {
    final String sql = "SELECT id, driver_id, route, dispatch_time, completion_time, finished "
        + "FROM Batch WHERE driver_id = ? ORDER BY id;";

    List<Batch> out = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapBatch(rs));
        }
      }

      return out;
    }
  }

  /**
   * Updates mutable batch fields (route, dispatch_time, completion_time). Returns true iff one row
   * updated.
   */
  public boolean updateBatch(long batchId, String routePolyline, Instant dispatchTime,
      Instant completionTime) throws SQLException {

    final String sql =
        "UPDATE Batch SET route = ?, dispatch_time = ?, completion_time = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, routePolyline);
        ps.setTimestamp(2, ts(dispatchTime));
        ps.setTimestamp(3, ts(completionTime));
        ps.setLong(4, batchId);

        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /** Marks a batch as finished (sets finished = true). Returns true iff one row updated. */
  public boolean markBatchFinished(long batchId) throws SQLException {
    final String sql = "UPDATE Batch SET finished = true WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, batchId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /** Sets the finished flag explicitly. Returns true iff one row updated. */
  public boolean setBatchFinished(long batchId, boolean finished) throws SQLException {
    final String sql = "UPDATE Batch SET finished = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setBoolean(1, finished);
        ps.setLong(2, batchId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /** Reassigns a batch to a different driver. Returns true iff one row updated. */
  public boolean updateBatchDriver(long batchId, long driverId) throws SQLException {
    final String sql = "UPDATE Batch SET driver_id = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, driverId);
        ps.setLong(2, batchId);

        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /** Deletes a batch row by id. Returns true iff a row was deleted. */
  public boolean deleteBatch(long batchId) throws SQLException {
    final String sql = "DELETE FROM Batch WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, batchId);
        boolean deleted = ps.executeUpdate() == 1;
        conn.commit();
        return deleted;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
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
    return new Batch(rs.getLong("id"), rs.getLong("driver_id"), rs.getString("route"),
        instant(rs, "dispatch_time"), instant(rs, "completion_time"), rs.getBoolean("finished"));
  }
}
