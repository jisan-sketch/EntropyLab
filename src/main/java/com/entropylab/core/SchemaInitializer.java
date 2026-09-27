package com.entropylab.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes SQLite database schema for EntropyLab (proxy_routes, chaos_rules, mock_routes, request_logs).
 */
public class SchemaInitializer {

    private static final String CREATE_PROXY_ROUTES = """
            CREATE TABLE IF NOT EXISTS proxy_routes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                route_pattern TEXT NOT NULL,
                target_base_url TEXT NOT NULL,
                enabled INTEGER DEFAULT 1
            );
            """;

    private static final String CREATE_CHAOS_RULES = """
            CREATE TABLE IF NOT EXISTS chaos_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                route_pattern TEXT NOT NULL,
                latency_ms INTEGER DEFAULT 0,
                status_override_code INTEGER,
                connection_reset_enabled INTEGER DEFAULT 0,
                enabled INTEGER DEFAULT 1
            );
            """;

    private static final String CREATE_MOCK_ROUTES = """
            CREATE TABLE IF NOT EXISTS mock_routes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                route_pattern TEXT NOT NULL,
                file_path TEXT NOT NULL,
                enabled INTEGER DEFAULT 1,
                source TEXT
            );
            """;

    private static final String CREATE_REQUEST_LOGS = """
            CREATE TABLE IF NOT EXISTS request_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                method TEXT NOT NULL,
                path TEXT NOT NULL,
                target_url TEXT,
                request_headers TEXT,
                request_body TEXT,
                response_status INTEGER,
                response_headers TEXT,
                response_body TEXT,
                latency_ms INTEGER,
                outcome_type TEXT
            );
            """;

    /**
     * Initializes the 4 schema tables on the provided connection.
     *
     * @param conn Open JDBC Connection to SQLite database.
     */
    public static void initializeSchema(Connection conn) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection must not be null");
        }

        System.out.println("[SchemaInitializer] Initializing database schema...");
        createTable(conn, "proxy_routes", CREATE_PROXY_ROUTES);
        createTable(conn, "chaos_rules", CREATE_CHAOS_RULES);
        createTable(conn, "mock_routes", CREATE_MOCK_ROUTES);
        createTable(conn, "request_logs", CREATE_REQUEST_LOGS);
        System.out.println("[SchemaInitializer] Database schema initialized successfully.");
    }

    /**
     * Convenience method to initialize the schema using DatabaseManager's shared connection.
     */
    public static void initializeSchema() {
        initializeSchema(DatabaseManager.getInstance().getConnection());
    }

    private static void createTable(Connection conn, String tableName, String ddl) {
        try {
            boolean existed = tableExists(conn, tableName);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
            }
            if (!existed) {
                System.out.println("[SchemaInitializer] Table created: " + tableName);
            } else {
                System.out.println("[SchemaInitializer] Table exists: " + tableName);
            }
        } catch (SQLException e) {
            System.err.println("[SchemaInitializer] Failed to create table " + tableName + ": " + e.getMessage());
            throw new RuntimeException("Schema initialization failed for table: " + tableName, e);
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
