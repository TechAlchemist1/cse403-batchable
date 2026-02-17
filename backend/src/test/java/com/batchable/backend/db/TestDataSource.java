package com.batchable.backend.db;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Minimal DataSource for DAO unit/integration tests.
 * Wraps the existing Connection from PostgresTestBase.
 *
 * NOTE: DAOs will use try-with-resources and call conn.close().
 * That would close the shared test connection, so we return a "non-closing"
 * wrapper Connection that ignores close().
 */
public final class TestDataSource implements DataSource {

  private final Connection conn;

  public TestDataSource(Connection conn) {
    this.conn = Objects.requireNonNull(conn);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return new NonClosingConnection(conn);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return getConnection();
  }

  // --- unused in our tests ---
  @Override public PrintWriter getLogWriter() { throw new UnsupportedOperationException(); }
  @Override public void setLogWriter(PrintWriter out) { throw new UnsupportedOperationException(); }
  @Override public void setLoginTimeout(int seconds) { throw new UnsupportedOperationException(); }
  @Override public int getLoginTimeout() { throw new UnsupportedOperationException(); }
  @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
  @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
  @Override public boolean isWrapperFor(Class<?> iface) { return false; }
}
