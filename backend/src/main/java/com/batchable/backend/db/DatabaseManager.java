package com.batchable.backend.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.batchable.backend.db.dao.BatchDAO;
import com.batchable.backend.db.dao.DriverDAO;
import com.batchable.backend.db.dao.MenuItemDAO;
import com.batchable.backend.db.dao.OrderDAO;
import com.batchable.backend.db.dao.RestaurantDAO;

public final class DatabaseManager implements AutoCloseable {
    private final Connection conn;

    public final RestaurantDAO restaurants;
    public final DriverDAO drivers;
    public final OrderDAO orders;
    public final BatchDAO batches;
    public final MenuItemDAO menuItems;

    public DatabaseManager() throws SQLException {
        this.conn = DriverManager.getConnection(
                DbConfig.JDBC_URL,
                DbConfig.DB_USER,
                DbConfig.DB_PASSWORD
        );
        this.conn.setAutoCommit(true);

        this.restaurants = new RestaurantDAO(conn);
        this.drivers = new DriverDAO(conn);
        this.orders = new OrderDAO(conn);
        this.batches = new BatchDAO(conn);
        this.menuItems = new MenuItemDAO(conn);
    }

    public Connection rawConnection() {
        return conn;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
