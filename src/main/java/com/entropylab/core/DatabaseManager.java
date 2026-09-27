package com.entropylab.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the shared SQLite database connection in WAL mode for EntropyLab.
 */
public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;

    /**
     * Initializes the singleton DatabaseManager instance and establishes the connection.
     *
     * @return The initialized DatabaseManager singleton instance.
     */
    public static synchronized DatabaseManager initialize() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        instance.connect();
        return instance;
    }

    /**
     * Returns the singleton DatabaseManager instance.
     *
     * @return The DatabaseManager instance.
     * @throws IllegalStateException If initialize() has not been called yet.
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("DatabaseManager has not been initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Connects to SQLite at AppPaths.getDbFilePath() and immediately configures WAL mode.
     */
    public synchronized void connect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    return;
                }
            } catch (SQLException ignored) {
            }
        }

        String dbPath = AppPaths.getDbFilePath();
        String url = "jdbc:sqlite:" + dbPath;

        try {
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode=WAL;")) {
                if (rs.next()) {
                    String mode = rs.getString(1);
                    System.out.println("[DatabaseManager] SQLite journal_mode: " + mode);
                }
            }
            System.out.println("[DatabaseManager] Connected to database: " + dbPath);
        } catch (SQLException e) {
            System.err.println("[DatabaseManager] Database connection failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    /**
     * Returns the shared open Connection.
     *
     * @return Active JDBC connection.
     */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error checking database connection state", e);
        }
        return connection;
    }

    /**
     * Closes the database connection if open.
     */
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("[DatabaseManager] Database connection closed.");
                }
            } catch (SQLException e) {
                System.err.println("[DatabaseManager] Error closing database connection: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }
}
