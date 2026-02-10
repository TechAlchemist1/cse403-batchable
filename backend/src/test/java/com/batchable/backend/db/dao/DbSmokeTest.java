package com.batchable.backend.db.dao;

import com.batchable.backend.db.PostgresTestBase;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class DbSmokeTest extends PostgresTestBase {

  @Test
  void migrationsRan_canQueryDatabase() throws Exception {
    try (Statement st = conn.createStatement()) {
      ResultSet rs = st.executeQuery("SELECT 1");
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }
}
