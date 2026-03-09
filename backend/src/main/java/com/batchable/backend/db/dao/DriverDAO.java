package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Driver;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Data Access Object for Driver table.
 *
 * Responsible ONLY for direct database interaction. No business logic should live here.
 */
@Repository
public class DriverDAO {

  // Spring-managed connection pool source
  private final DataSource dataSource;

  // Thread-safety lock:
  // - multiple readers can proceed together
  // - writers are exclusive
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
  private final Lock readLock = rwLock.readLock();
  private final Lock writeLock = rwLock.writeLock();

  /**
   * Constructor injection of DataSource. Spring will provide this automatically.
   */
  public DriverDAO(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Creates a new driver row and returns the generated ID.
   */
  public long createDriver(long restaurantId, String name, String phoneNumber, boolean onShift)
      throws SQLException {

    final String sql = "INSERT INTO Driver(restaurant_id, name, phone_number, on_shift) "
        + "VALUES (?, ?, ?, ?) RETURNING id;";

    writeLock.lock();
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, restaurantId);
        ps.setString(2, name);
        ps.setString(3, phoneNumber);
        ps.setBoolean(4, onShift);

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
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Retrieves a driver by ID. Returns Optional.empty() if not found.
   */
  public Optional<Driver> getDriver(long driverId) throws SQLException {

    final String sql =
        "SELECT id, restaurant_id, name, phone_number, on_shift " + "FROM Driver WHERE id = ?;";

    readLock.lock();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next())
          return Optional.empty();

        return Optional.of(new Driver(rs.getLong("id"), rs.getLong("restaurant_id"),
            rs.getString("name"), rs.getString("phone_number"), rs.getBoolean("on_shift")));
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Retrieves a driver by token. Returns Optional.empty() if not found.
   */
  public Optional<Driver> getDriverByToken(String token) throws SQLException {
    final String sql = "SELECT id, restaurant_id, name, phone_number, on_shift "
        + "FROM Driver WHERE token = ?::uuid;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, token);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next())
          return Optional.empty();

        return Optional.of(new Driver(rs.getLong("id"), rs.getLong("restaurant_id"),
            rs.getString("name"), rs.getString("phone_number"), rs.getBoolean("on_shift")));
      }
    }
  }

  /**
   * Retrieves the token belonging to the Driver specified by the given driverId Returns
   * Optional.empty() if not found.
   */
  public Optional<String> getDriverToken(long driverId) throws SQLException {

    final String sql = "SELECT token FROM Driver WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, driverId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next())
          return Optional.empty();
        return Optional.ofNullable(rs.getString("token"));
      }
    }
  }

  /**
   * Updates only the on_shift flag for a driver.
   */
  public void setDriverShift(long driverId, boolean onShift) throws SQLException {

    final String sql = "UPDATE Driver SET on_shift = ? WHERE id = ?;";

    writeLock.lock();
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setBoolean(1, onShift);
        ps.setLong(2, driverId);
        ps.executeUpdate();
        conn.commit();
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Lists all drivers for a given restaurant. If onShiftOnly is true, filters to only on-shift
   * drivers.
   */

  public List<Driver> listDriversForRestaurant(long restaurantId, boolean onShiftOnly)
      throws SQLException {

    final String sql = "SELECT id, restaurant_id, name, phone_number, on_shift "
        + "FROM Driver WHERE restaurant_id = ? " + (onShiftOnly ? "AND on_shift = TRUE " : "")
        + "ORDER BY id;";

    List<Driver> out = new ArrayList<>();

    readLock.lock();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, restaurantId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new Driver(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("name"),
              rs.getString("phone_number"), rs.getBoolean("on_shift")));
        }
      }
    } finally {
      readLock.unlock();
    }

    return out;
  }

  /**
   * Updates mutable driver fields (NOT id, NOT restaurant_id, NOT on_shift).
   */
  public boolean updateDriver(long driverId, String name, String phoneNumber) throws SQLException {

    final String sql = "UPDATE Driver SET name = ?, phone_number = ? WHERE id = ?;";

    writeLock.lock();
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, name);
        ps.setString(2, phoneNumber);
        ps.setLong(3, driverId);

        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Deletes a driver row by id. Returns true if a row was deleted.
   */
  public boolean deleteDriver(long driverId) throws SQLException {

    final String sql = "DELETE FROM Driver WHERE id = ?;";

    writeLock.lock();
    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, driverId);
        boolean deleted = ps.executeUpdate() == 1;
        conn.commit();
        return deleted;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Returns true if any driver for the restaurant is currently on shift.
   */
  public boolean hasOnShiftDrivers(long restaurantId) throws SQLException {

    final String sql =
        "SELECT 1 FROM Driver WHERE restaurant_id = ? " + "AND on_shift = TRUE LIMIT 1;";

    readLock.lock();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, restaurantId);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    } finally {
      readLock.unlock();
    }
  }
}
