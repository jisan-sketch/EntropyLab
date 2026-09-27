package com.entropylab.logging;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for persisting and querying RequestLog entities in SQLite.
 */
public class RequestLogDao {

    private final DatabaseManager databaseManager;

    public RequestLogDao() {
        this(null);
    }

    public RequestLogDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection getConnection() {
        if (databaseManager != null) {
            return databaseManager.getConnection();
        }
        return AppContext.getDatabaseManager().getConnection();
    }

    /**
     * Inserts a row into request_logs using a prepared statement, serializing headers
     * via RequestLog's helper methods, and assigns the generated id on the log object.
     *
     * @param log The RequestLog entity to insert.
     * @return The generated database id.
     */
    public int insertLog(RequestLog log) {
        if (log == null) {
            throw new IllegalArgumentException("RequestLog must not be null");
        }

        String sql = """
            INSERT INTO request_logs (
                timestamp, method, path, target_url, request_headers, request_body,
                response_status, response_headers, response_body, latency_ms, outcome_type
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, log.getTimestamp());
            pstmt.setString(2, log.getMethod());
            pstmt.setString(3, log.getPath());
            pstmt.setString(4, log.getTargetUrl());
            pstmt.setString(5, RequestLog.serializeHeaders(log.getRequestHeaders()));
            pstmt.setString(6, log.getRequestBodyAsString());

            if (log.getResponseStatus() != null) {
                pstmt.setInt(7, log.getResponseStatus());
            } else {
                pstmt.setNull(7, Types.INTEGER);
            }

            pstmt.setString(8, RequestLog.serializeHeaders(log.getResponseHeaders()));
            pstmt.setString(9, log.getResponseBodyAsString());
            pstmt.setLong(10, log.getLatencyMs());
            pstmt.setString(11, log.getOutcomeType() != null ? log.getOutcomeType().name() : null);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    log.setId(id);
                    return id;
                }
            }
            return -1;
        } catch (SQLException e) {
            System.err.println("[RequestLogDao] Failed to insert RequestLog: " + e.getMessage());
            throw new RuntimeException("Failed to insert RequestLog", e);
        }
    }

    /**
     * Retrieves recent logs ordered by timestamp DESC, supporting pagination via limit and offset.
     *
     * @param limit Maximum number of records to return.
     * @param offset Number of records to skip.
     * @return List of matching RequestLog objects.
     */
    public List<RequestLog> getRecentLogs(int limit, int offset) {
        String sql = """
            SELECT id, timestamp, method, path, target_url, request_headers, request_body,
                   response_status, response_headers, response_body, latency_ms, outcome_type
            FROM request_logs
            ORDER BY timestamp DESC, id DESC
            LIMIT ? OFFSET ?
            """;

        List<RequestLog> logs = new ArrayList<>();
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            pstmt.setInt(2, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapRowToRequestLog(rs));
                }
            }
            return logs;
        } catch (SQLException e) {
            System.err.println("[RequestLogDao] Failed to query recent logs: " + e.getMessage());
            throw new RuntimeException("Failed to query recent logs", e);
        }
    }

    /**
     * Retrieves a full RequestLog by its database primary key.
     *
     * @param id The log record ID.
     * @return Full RequestLog object, or null if not found.
     */
    public RequestLog getLogById(int id) {
        String sql = """
            SELECT id, timestamp, method, path, target_url, request_headers, request_body,
                   response_status, response_headers, response_body, latency_ms, outcome_type
            FROM request_logs
            WHERE id = ?
            """;

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToRequestLog(rs);
                }
            }
            return null;
        } catch (SQLException e) {
            System.err.println("[RequestLogDao] Failed to query log by id=" + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to query log by id", e);
        }
    }

    /**
     * Deletes a log by its ID (useful for test cleanups).
     *
     * @param id Log record ID.
     * @return true if deleted, false otherwise.
     */
    public boolean deleteLog(int id) {
        String sql = "DELETE FROM request_logs WHERE id = ?";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[RequestLogDao] Failed to delete log id=" + id + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves the total count of rows in request_logs table.
     *
     * @return Number of logged requests.
     */
    public int getLogCount() {
        String sql = "SELECT COUNT(*) FROM request_logs";
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("[RequestLogDao] Failed to count request_logs: " + e.getMessage());
            return 0;
        }
    }

    private RequestLog mapRowToRequestLog(ResultSet rs) throws SQLException {
        RequestLog log = new RequestLog();
        log.setId(rs.getInt("id"));
        log.setTimestamp(rs.getString("timestamp"));
        log.setMethod(rs.getString("method"));
        log.setPath(rs.getString("path"));
        log.setTargetUrl(rs.getString("target_url"));

        String reqHeadersJson = rs.getString("request_headers");
        log.setRequestHeaders(RequestLog.deserializeHeaders(reqHeadersJson));

        String reqBody = rs.getString("request_body");
        log.setRequestBodyBytes(reqBody != null ? reqBody.getBytes(StandardCharsets.UTF_8) : null);

        int status = rs.getInt("response_status");
        log.setResponseStatus(rs.wasNull() ? null : status);

        String respHeadersJson = rs.getString("response_headers");
        log.setResponseHeaders(RequestLog.deserializeHeaders(respHeadersJson));

        String respBody = rs.getString("response_body");
        log.setResponseBodyBytes(respBody != null ? respBody.getBytes(StandardCharsets.UTF_8) : null);

        log.setLatencyMs(rs.getLong("latency_ms"));

        String outcomeStr = rs.getString("outcome_type");
        if (outcomeStr != null) {
            try {
                log.setOutcomeType(OutcomeType.valueOf(outcomeStr));
            } catch (IllegalArgumentException ignored) {
                log.setOutcomeType(null);
            }
        }
        return log;
    }

    /**
     * Manual console test verifying insert, getRecentLogs, and getLogById.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RequestLogDao Read Methods Test Suite (M4.2)");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        RequestLogDao dao = AppContext.getRequestLogDao();

        // 1. Create and insert dummy logs with distinct ordered timestamps
        System.out.println("\n[STEP 1] Inserting 3 dummy RequestLog entries...");

        RequestLog log1 = new RequestLog("2026-09-26T10:00:01.000Z", "GET", "/api/items");
        log1.setTargetUrl("https://api.example.com/items");
        log1.setRequestHeaders(Map.of("Accept", List.of("application/json")));
        log1.setRequestBodyBytes(new byte[0]);
        log1.setResponseStatus(200);
        log1.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        log1.setResponseBodyBytes("[{\"id\":1}]".getBytes(StandardCharsets.UTF_8));
        log1.setLatencyMs(45);
        log1.setOutcomeType(OutcomeType.FORWARDED);
        int id1 = dao.insertLog(log1);
        System.out.println("  Inserted log1 with ID=" + id1 + " (timestamp=" + log1.getTimestamp() + ")");

        RequestLog log2 = new RequestLog("2026-09-26T10:00:02.000Z", "POST", "/api/items");
        log2.setTargetUrl("https://api.example.com/items");
        log2.setRequestHeaders(Map.of("Content-Type", List.of("application/json")));
        log2.setRequestBodyBytes("{\"name\":\"widget\"}".getBytes(StandardCharsets.UTF_8));
        log2.setResponseStatus(201);
        log2.setResponseHeaders(Map.of("Location", List.of("/api/items/2")));
        log2.setResponseBodyBytes("{\"id\":2,\"name\":\"widget\"}".getBytes(StandardCharsets.UTF_8));
        log2.setLatencyMs(80);
        log2.setOutcomeType(OutcomeType.FORWARDED);
        int id2 = dao.insertLog(log2);
        System.out.println("  Inserted log2 with ID=" + id2 + " (timestamp=" + log2.getTimestamp() + ")");

        RequestLog log3 = new RequestLog("2026-09-26T10:00:03.000Z", "GET", "/api/unknown");
        log3.setTargetUrl(null);
        log3.setRequestHeaders(Map.of());
        log3.setRequestBodyBytes(new byte[0]);
        log3.setResponseStatus(404);
        log3.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        log3.setResponseBodyBytes("{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8));
        log3.setLatencyMs(2);
        log3.setOutcomeType(OutcomeType.NOT_FOUND);
        int id3 = dao.insertLog(log3);
        System.out.println("  Inserted log3 with ID=" + id3 + " (timestamp=" + log3.getTimestamp() + ")");

        try {
            // 2. Test getRecentLogs(limit, offset) ordering (timestamp DESC)
            System.out.println("\n[STEP 2] Testing getRecentLogs(3, 0) - Expecting DESC timestamp order...");
            List<RequestLog> recentLogs = dao.getRecentLogs(3, 0);
            for (RequestLog r : recentLogs) {
                System.out.printf("   - ID=%d, %s %s, time=%s, outcome=%s, status=%s%n",
                        r.getId(), r.getMethod(), r.getPath(), r.getTimestamp(), r.getOutcomeType(), r.getResponseStatus());
            }

            if (recentLogs.size() < 3) {
                throw new AssertionError("Expected at least 3 logs, got: " + recentLogs.size());
            }

            // Top item must be the most recent
            if (!recentLogs.get(0).getId().equals(id3)) {
                throw new AssertionError("First item in getRecentLogs should be log3 (id=" + id3 + "), but was: " + recentLogs.get(0).getId());
            }
            if (!recentLogs.get(1).getId().equals(id2)) {
                throw new AssertionError("Second item in getRecentLogs should be log2 (id=" + id2 + "), but was: " + recentLogs.get(1).getId());
            }
            if (!recentLogs.get(2).getId().equals(id1)) {
                throw new AssertionError("Third item in getRecentLogs should be log1 (id=" + id1 + "), but was: " + recentLogs.get(2).getId());
            }
            System.out.println("  Result: PASS (Correct DESC timestamp order: log3 -> log2 -> log1)");

            // 3. Test getRecentLogs pagination (limit=1, offset=1)
            System.out.println("\n[STEP 3] Testing pagination getRecentLogs(1, 1)...");
            List<RequestLog> page = dao.getRecentLogs(1, 1);
            if (page.size() != 1 || !page.get(0).getId().equals(id2)) {
                throw new AssertionError("Expected page(1, 1) to return log2 (id=" + id2 + "), got: " + page);
            }
            System.out.println("  Page offset 1 returned log ID=" + page.get(0).getId() + ": PASS");

            // 4. Test getLogById(id)
            System.out.println("\n[STEP 4] Testing getLogById(id)...");
            RequestLog fetchedLog2 = dao.getLogById(id2);
            if (fetchedLog2 == null) {
                throw new AssertionError("getLogById(" + id2 + ") returned null!");
            }
            System.out.println("  Fetched log2: ID=" + fetchedLog2.getId() +
                    ", method=" + fetchedLog2.getMethod() +
                    ", body=" + fetchedLog2.getRequestBodyAsString() +
                    ", status=" + fetchedLog2.getResponseStatus() +
                    ", respBody=" + fetchedLog2.getResponseBodyAsString() +
                    ", outcome=" + fetchedLog2.getOutcomeType());

            if (!"POST".equals(fetchedLog2.getMethod()) ||
                    !"/api/items".equals(fetchedLog2.getPath()) ||
                    fetchedLog2.getResponseStatus() != 201 ||
                    !fetchedLog2.getRequestBodyAsString().contains("widget") ||
                    !fetchedLog2.getResponseBodyAsString().contains("\"id\":2") ||
                    fetchedLog2.getOutcomeType() != OutcomeType.FORWARDED) {
                throw new AssertionError("getLogById did not accurately reconstruct all RequestLog fields!");
            }
            System.out.println("  Result: PASS (All fields accurately reconstructed)");

            // 5. Test getLogById for non-existent ID
            System.out.println("\n[STEP 5] Testing getLogById(-9999)...");
            RequestLog nonExistent = dao.getLogById(-9999);
            if (nonExistent != null) {
                throw new AssertionError("Expected null for non-existent ID, but got: " + nonExistent);
            }
            System.out.println("  Result: PASS (Returned null as expected)");

        } finally {
            // Clean up dummy test logs
            dao.deleteLog(id1);
            dao.deleteLog(id2);
            dao.deleteLog(id3);
            System.out.println("\n[CLEANUP] Deleted test dummy logs (IDs: " + id1 + ", " + id2 + ", " + id3 + ")");
        }

        System.out.println("\n==================================================");
        System.out.println("  All RequestLogDao M4.2 tests PASSED!");
        System.out.println("==================================================");
    }
}
