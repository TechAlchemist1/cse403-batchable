package com.batchable.backend.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import com.batchable.backend.db.PostgresTestBase;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.Comparator;
import java.util.stream.Stream;

public abstract class PostgresTestBase {
  protected static PostgreSQLContainer<?> pg;
  protected static Connection conn;

  @BeforeAll
  static void startDb() throws Exception {
    pg = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("appdb")
        .withUsername("app_user")
        .withPassword("app_password");
    pg.start();

    conn = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    runMigrations(conn);
  }

  @AfterAll
  static void stopDb() throws Exception {
    if (conn != null) conn.close();
    if (pg != null) pg.stop();
  }

  private static void runMigrations(Connection c) throws IOException, SQLException {
    // If your migrations folder is different, change this path
    Path migrationsDir = Paths.get("..", "infra", "postgres", "migrations");

    if (!Files.exists(migrationsDir)) {
      throw new IllegalStateException(
          "Migrations dir not found: " + migrationsDir.toAbsolutePath()
      );
    }

    try (Stream<Path> paths = Files.list(migrationsDir)) {
      paths
          .filter(p -> p.toString().endsWith(".sql"))
          .sorted(Comparator.comparing(Path::getFileName))
          .forEach(p -> {
            try {
              String sql = Files.readString(p);
              try (Statement st = c.createStatement()) {
                st.execute(sql);
              }
            } catch (Exception e) {
              throw new RuntimeException("Failed migration: " + p, e);
            }
          });
    }
  }
}
