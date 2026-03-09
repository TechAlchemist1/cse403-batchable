package com.batchable.backend.db.dao;

import com.batchable.backend.db.models.Order;

import javax.sql.DataSource;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access object for the Order entity. Provides CRUD and query operations on the "Order" table
 * using plain JDBC. All methods throw SQLException on database errors.
 */
@Repository
public class OrderDAO {

  // Spring-managed connection pool source
  private final DataSource dataSource;

  /**
   * Constructs an OrderDAO with the given DataSource.
   *
   * @param dataSource the Spring-managed DataSource for database connections
   */
  public OrderDAO(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Converts an Instant to a java.sql.Timestamp, handling null.
   *
   * @param i the Instant to convert, may be null
   * @return the corresponding Timestamp, or null if the input was null
   */
  private static Timestamp ts(Instant i) {
    return (i == null) ? null : Timestamp.from(i);
  }

  /**
   * Retrieves an Instant from a ResultSet column, handling null.
   *
   * @param rs the ResultSet to read from
   * @param col the column name
   * @return the Instant value, or null if the column was NULL
   * @throws SQLException if a database access error occurs
   */
  private static Instant instant(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return (t == null) ? null : t.toInstant();
  }

  /**
   * Retrieves a Long from a ResultSet column, handling null.
   *
   * @param rs the ResultSet to read from
   * @param col the column name
   * @return the Long value, or null if the column was NULL
   * @throws SQLException if a database access error occurs
   */
  private static Long nullableLong(ResultSet rs, String col) throws SQLException {
    Object o = rs.getObject(col);
    return (o == null) ? null : rs.getLong(col);
  }

  /**
   * Creates a new order in the database.
   *
   * @param restaurantId ID of the restaurant placing the order
   * @param destination delivery address
   * @param itemNamesJson JSON array of item names, e.g. ["Burger","Fries"]
   * @param initialTime time the order was initially placed
   * @param deliveryTime time the order was delivered (may be null)
   * @param cookedTime time the order finished cooking (may be null)
   * @param state current state of the order
   * @param highPriority whether the order has high priority
   * @param batchId ID of the batch this order belongs to, or null if not batched
   * @return the auto-generated ID of the new order
   * @throws SQLException if a database access error occurs
   */
  public long createOrder(long restaurantId, String destination, String itemNamesJson,
      Instant initialTime, Instant deliveryTime, Instant cookedTime, Order.State state,
      boolean highPriority, Long batchId) throws SQLException {

    final String sql = "INSERT INTO \"Order\"("
        + " restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time,"
        + " state, high_priority, batch_id" + ") VALUES ("
        + " ?, ?, ?::json, ?, ?, ?, ?::order_state, ?, ?" + ") RETURNING id;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, restaurantId);
        ps.setString(2, destination);
        ps.setString(3, itemNamesJson);
        ps.setTimestamp(4, ts(initialTime));
        ps.setTimestamp(5, ts(deliveryTime));
        ps.setTimestamp(6, ts(cookedTime));
        ps.setString(7, state.name());
        ps.setBoolean(8, highPriority);

        if (batchId == null) {
          ps.setNull(9, Types.BIGINT);
        } else {
          ps.setLong(9, batchId);
        }

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

  /**
   * Retrieves an order by its ID.
   *
   * @param orderId the ID of the order to fetch
   * @return an Optional containing the order if found, or empty otherwise
   * @throws SQLException if a database access error occurs
   */
  public Optional<Order> getOrder(long orderId) throws SQLException {

    final String sql =
        "SELECT id, restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time, "
            + "       state, high_priority, batch_id " + "FROM \"Order\" WHERE id = ?;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, orderId);

      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next())
          return Optional.empty();
        return Optional.of(mapOrder(rs));
      }
    }
  }

  /**
   * Updates the state of an existing order.
   *
   * @param orderId the ID of the order to update
   * @param newState the new state to set
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean updateOrderState(long orderId, Order.State newState) throws SQLException {
    final String sql = "UPDATE \"Order\" SET state = ?::order_state WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, newState.name());
        ps.setLong(2, orderId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Updates the cooked time of an existing order.
   *
   * @param orderId the ID of the order to update
   * @param cookedTime the new cooked time (may be null)
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean updateOrderCookedTime(long orderId, Instant cookedTime) throws SQLException {
    final String sql = "UPDATE \"Order\" SET cooked_time = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setTimestamp(1, ts(cookedTime));
        ps.setLong(2, orderId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Updates the delivery time of an existing order.
   *
   * @param orderId the ID of the order to update
   * @param deliveryTime the new delivery time (may be null)
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean updateOrderDeliveryTime(long orderId, Instant deliveryTime) throws SQLException {
    final String sql = "UPDATE \"Order\" SET delivery_time = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setTimestamp(1, ts(deliveryTime));
        ps.setLong(2, orderId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Sets or clears the high priority flag of an order.
   *
   * @param orderId the ID of the order to update
   * @param highPriority the new priority value
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean setOrderHighPriority(long orderId, boolean highPriority) throws SQLException {
    final String sql = "UPDATE \"Order\" SET high_priority = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setBoolean(1, highPriority);
        ps.setLong(2, orderId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Updates an order with new cooking/delivery times, state, and priority, and clears its batch
   * assignment (batch_id = NULL).
   *
   * @param orderId the ID of the order to remake
   * @param resetState the new state (e.g., COOKING)
   * @param newInitialTime the new initial time (may be null)
   * @param newDeliveryTime the new delivery time (may be null)
   * @param newCookedTime the new cooked time (may be null)
   * @param highPriority the new priority flag
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean remakeOrder(long orderId, Order.State resetState, Instant newInitialTime,
      Instant newDeliveryTime, Instant newCookedTime, boolean highPriority) throws SQLException {
    final String sql = "UPDATE \"Order\" " + "SET state = ?::order_state, "
        + "initial_time = ?, cooked_time = ?, delivery_time = ?, "
        + "batch_id = NULL, high_priority = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, resetState.name());
        ps.setTimestamp(2, ts(newInitialTime));
        ps.setTimestamp(3, ts(newCookedTime));
        ps.setTimestamp(4, ts(newDeliveryTime));
        ps.setBoolean(5, highPriority);
        ps.setLong(6, orderId);

        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Deletes an order by its ID.
   *
   * @param orderId the ID of the order to delete
   * @return true if exactly one row was deleted (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean deleteOrder(long orderId) throws SQLException {
    final String sql = "DELETE FROM \"Order\" WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, orderId);
        boolean deleted = ps.executeUpdate() == 1;
        conn.commit();
        return deleted;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Retrieves all orders belonging to a specific batch, ordered by ID.
   *
   * @param batchId the ID of the batch
   * @return a list of orders in that batch (may be empty)
   * @throws SQLException if a database access error occurs
   */
  public List<Order> listOrdersInBatch(long batchId) throws SQLException {

    final String sql =
        "SELECT id, restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time, "
            + "       state, high_priority, batch_id "
            + "FROM \"Order\" WHERE batch_id = ? ORDER BY id;";

    List<Order> out = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, batchId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapOrder(rs));
        }
      }
    }

    return out;
  }

  /**
   * Retrieves all open (not DELIVERED) orders for a specific restaurant, ordered by ID.
   *
   * @param restaurantId the ID of the restaurant
   * @return a list of open orders for that restaurant (may be empty)
   * @throws SQLException if a database access error occurs
   */
  public List<Order> listOpenOrdersForRestaurant(long restaurantId) throws SQLException {

    final String sql =
        "SELECT id, restaurant_id, destination, item_names, initial_time, delivery_time, cooked_time, "
            + "       state, high_priority, batch_id " + "FROM \"Order\" "
            + "WHERE restaurant_id = ? AND state <> 'DELIVERED' " + "ORDER BY id;";

    List<Order> out = new ArrayList<>();

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, restaurantId);

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(mapOrder(rs));
        }
      }
    }

    return out;
  }

  /**
   * Maps the current row of a ResultSet to an Order object.
   *
   * @param rs the ResultSet positioned at a valid row
   * @return the constructed Order
   * @throws SQLException if a database access error occurs
   */
  private static Order mapOrder(ResultSet rs) throws SQLException {
    return new Order(rs.getLong("id"), rs.getLong("restaurant_id"), rs.getString("destination"),
        rs.getString("item_names"), instant(rs, "initial_time"), instant(rs, "delivery_time"),
        instant(rs, "cooked_time"), Order.State.valueOf(rs.getString("state")),
        rs.getBoolean("high_priority"), nullableLong(rs, "batch_id"));
  }

  /**
   * Checks whether a restaurant has any active (not DELIVERED) orders.
   *
   * @param restaurantId the ID of the restaurant
   * @return true if at least one active order exists, false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean hasActiveOrdersForRestaurant(long restaurantId) throws SQLException {
    final String sql =
        "SELECT 1 FROM \"Order\" WHERE restaurant_id = ? AND state <> 'DELIVERED' LIMIT 1;";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setLong(1, restaurantId);

      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /**
   * Assigns an order to a batch by setting its batch_id.
   *
   * @param orderId the ID of the order
   * @param batchId the ID of the batch
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean updateOrderBatchId(long orderId, long batchId) throws SQLException {
    final String sql = "UPDATE \"Order\" SET batch_id = ? WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, batchId);
        ps.setLong(2, orderId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }

  /**
   * Removes an order from its batch by setting batch_id to NULL.
   *
   * @param orderId the ID of the order
   * @return true if exactly one row was updated (order existed), false otherwise
   * @throws SQLException if a database access error occurs
   */
  public boolean clearOrderBatchId(long orderId) throws SQLException {
    final String sql = "UPDATE \"Order\" SET batch_id = NULL WHERE id = ?;";

    try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);

      try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, orderId);
        boolean updated = ps.executeUpdate() == 1;
        conn.commit();
        return updated;
      } catch (SQLException e) {
        conn.rollback();
        throw e;
      }
    }
  }
}
