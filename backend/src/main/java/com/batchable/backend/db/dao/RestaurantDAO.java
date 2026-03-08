package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Restaurant;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class RestaurantDAO {

    // Spring-managed connection pool
    private final DataSource dataSource;

    // Thread-safety lock:
    // - multiple readers can proceed together
    // - writers are exclusive
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    public RestaurantDAO(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long createRestaurant(String name, String location) throws SQLException {
        final String sql = "INSERT INTO Restaurant(name, location) VALUES (?, ?) RETURNING id;";

        writeLock.lock();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, location);

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

    public Optional<Restaurant> getRestaurant(long id) throws SQLException {
        final String sql = "SELECT id, name, location FROM Restaurant WHERE id = ?;";

        readLock.lock();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapRestaurant(rs));
            }
        } finally {
            readLock.unlock();
        }
    }

    public List<Restaurant> listRestaurants() throws SQLException {
        final String sql = "SELECT id, name, location FROM Restaurant ORDER BY id;";
        List<Restaurant> out = new ArrayList<>();

        readLock.lock();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(mapRestaurant(rs));
            }
        } finally {
            readLock.unlock();
        }

        return out;
    }

    public boolean updateRestaurant(long restaurantId, String name, String location) throws SQLException {
        final String sql = "UPDATE Restaurant SET name = ?, location = ? WHERE id = ?;";

        writeLock.lock();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, location);
                ps.setLong(3, restaurantId);

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

    public boolean deleteRestaurant(long restaurantId) throws SQLException {
        final String sql = "DELETE FROM Restaurant WHERE id = ?;";

        writeLock.lock();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, restaurantId);
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

    public boolean restaurantExists(long restaurantId) throws SQLException {
        final String sql = "SELECT 1 FROM Restaurant WHERE id = ? LIMIT 1;";

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

    public boolean restaurantExistsByName(String name) throws SQLException {
        final String sql = "SELECT 1 FROM Restaurant WHERE name = ? LIMIT 1;";

        readLock.lock();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } finally {
            readLock.unlock();
        }
    }

    public boolean restaurantExistsByNameExcludingId(long excludedRestaurantId, String name) throws SQLException {
        final String sql = "SELECT 1 FROM Restaurant WHERE name = ? AND id <> ? LIMIT 1;";

        readLock.lock();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setLong(2, excludedRestaurantId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } finally {
            readLock.unlock();
        }
    }

    private static Restaurant mapRestaurant(ResultSet rs) throws SQLException {
        return new Restaurant(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("location")
        );
    }
}