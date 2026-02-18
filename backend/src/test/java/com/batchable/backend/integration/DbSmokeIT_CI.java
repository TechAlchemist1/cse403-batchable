package com.batchable.backend.integration;

import com.batchable.backend.db.PostgresTestBase;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A smoke test that verifies the database is reachable and that Flyway migrations have been applied
 * successfully. Extends PostgresTestBase to get a live connection.
 */
public class DbSmokeIT_CI extends PostgresTestBase {

  /**
   * Executes a simple "SELECT 1" query to confirm the database connection works and the test
   * environment is correctly initialised.
   *
   * @throws Exception if any JDBC operation fails
   */
  @Test
  void migrationsRan_canQueryDatabase() throws Exception {
    try (Statement st = conn.createStatement()) {
      ResultSet rs = st.executeQuery("SELECT 1");
      assertTrue(rs.next(), "Expected at least one row from SELECT 1");
      assertEquals(1, rs.getInt(1), "SELECT 1 should return 1");
    }
  }
}
