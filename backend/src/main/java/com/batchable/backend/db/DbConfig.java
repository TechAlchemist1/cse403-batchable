package com.batchable.backend.db;

public final class DbConfig {
    // For Docker compose mapping 5433 -> 5432
    public static final String JDBC_URL =
            System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5433/appdb");
    public static final String DB_USER =
            System.getenv().getOrDefault("DB_USER", "app_user");
    public static final String DB_PASSWORD =
            System.getenv().getOrDefault("DB_PASSWORD", "app_password");

    private DbConfig() {}
}
