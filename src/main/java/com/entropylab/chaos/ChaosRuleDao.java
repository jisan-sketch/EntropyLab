package com.entropylab.chaos;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for persisting and querying ChaosRule entities in SQLite.
 */
public class ChaosRuleDao {

    private final DatabaseManager databaseManager;

    public ChaosRuleDao() {
        this(null);
    }

    public ChaosRuleDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection getConnection() {
        if (databaseManager != null) {
            return databaseManager.getConnection();
        }
        return AppContext.getDatabaseManager().getConnection();
    }

    /**
     * Inserts a new ChaosRule into the chaos_rules table.
     * Assigns the generated database primary key ID on the rule object.
     *
     * @param rule The ChaosRule to insert.
     * @return The ChaosRule with its generated id assigned.
     */
    public ChaosRule insert(ChaosRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("ChaosRule must not be null");
        }

        String sql = """
            INSERT INTO chaos_rules (route_pattern, latency_ms, status_override_code, connection_reset_enabled, enabled)
            VALUES (?, ?, ?, ?, ?)
            """;

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, rule.getRoutePattern());
            pstmt.setInt(2, rule.getLatencyMs());

            if (rule.getStatusOverrideCode() != null) {
                pstmt.setInt(3, rule.getStatusOverrideCode());
            } else {
                pstmt.setNull(3, Types.INTEGER);
            }

            pstmt.setInt(4, rule.isConnectionResetEnabled() ? 1 : 0);
            pstmt.setInt(5, rule.isEnabled() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    rule.setId(generatedKeys.getInt(1));
                }
            }
            return rule;
        } catch (SQLException e) {
            System.err.println("[ChaosRuleDao] Failed to insert ChaosRule: " + e.getMessage());
            throw new RuntimeException("Failed to insert ChaosRule: " + rule, e);
        }
    }

    /**
     * Updates an existing ChaosRule in the database.
     *
     * @param rule The ChaosRule containing updated values and a valid ID.
     * @return true if a row was updated, false otherwise.
     */
    public boolean update(ChaosRule rule) {
        if (rule == null || rule.getId() <= 0) {
            throw new IllegalArgumentException("ChaosRule must have a valid ID for update");
        }

        String sql = """
            UPDATE chaos_rules
            SET route_pattern = ?, latency_ms = ?, status_override_code = ?, connection_reset_enabled = ?, enabled = ?
            WHERE id = ?
            """;

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rule.getRoutePattern());
            pstmt.setInt(2, rule.getLatencyMs());

            if (rule.getStatusOverrideCode() != null) {
                pstmt.setInt(3, rule.getStatusOverrideCode());
            } else {
                pstmt.setNull(3, Types.INTEGER);
            }

            pstmt.setInt(4, rule.isConnectionResetEnabled() ? 1 : 0);
            pstmt.setInt(5, rule.isEnabled() ? 1 : 0);
            pstmt.setInt(6, rule.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ChaosRuleDao] Failed to update ChaosRule id=" + rule.getId() + ": " + e.getMessage());
            throw new RuntimeException("Failed to update ChaosRule: " + rule, e);
        }
    }

    /**
     * Deletes a ChaosRule by its ID.
     *
     * @param id Primary key ID of the rule.
     * @return true if a row was deleted, false otherwise.
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM chaos_rules WHERE id = ?";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ChaosRuleDao] Failed to delete ChaosRule id=" + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to delete ChaosRule id=" + id, e);
        }
    }

    /**
     * Retrieves all chaos rules from the database.
     *
     * @return List of all ChaosRule records ordered by id ASC.
     */
    public List<ChaosRule> getAll() {
        String sql = "SELECT id, route_pattern, latency_ms, status_override_code, connection_reset_enabled, enabled FROM chaos_rules ORDER BY id ASC";
        List<ChaosRule> rules = new ArrayList<>();
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rules.add(mapRow(rs));
            }
            return rules;
        } catch (SQLException e) {
            System.err.println("[ChaosRuleDao] Failed to query all ChaosRules: " + e.getMessage());
            throw new RuntimeException("Failed to query all ChaosRules", e);
        }
    }

    /**
     * Retrieves a single ChaosRule by its primary key ID.
     *
     * @param id The rule ID.
     * @return Optional containing the ChaosRule if found, empty otherwise.
     */
    public Optional<ChaosRule> getById(int id) {
        String sql = "SELECT id, route_pattern, latency_ms, status_override_code, connection_reset_enabled, enabled FROM chaos_rules WHERE id = ?";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            System.err.println("[ChaosRuleDao] Failed to query ChaosRule by id=" + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to query ChaosRule by id=" + id, e);
        }
    }

    private ChaosRule mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String routePattern = rs.getString("route_pattern");
        int latencyMs = rs.getInt("latency_ms");
        int statusOverride = rs.getInt("status_override_code");
        Integer statusOverrideCode = rs.wasNull() ? null : statusOverride;
        boolean connectionReset = rs.getInt("connection_reset_enabled") == 1;
        boolean enabled = rs.getInt("enabled") == 1;

        return new ChaosRule(id, routePattern, latencyMs, statusOverrideCode, connectionReset, enabled);
    }

    /**
     * Manual console test proving insert, update, delete, and getAll against SQLite.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ChaosRuleDao Manual Test Suite (M5.2)");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        ChaosRuleDao dao = new ChaosRuleDao(dbManager);

        // 1. Initial count
        List<ChaosRule> initialRules = dao.getAll();
        System.out.println("[STEP 1] Current rules in database: " + initialRules.size());

        // 2. Insert new rule
        System.out.println("\n[STEP 2] Inserting test ChaosRule...");
        ChaosRule newRule = new ChaosRule("/api/test/*", 1500, 503, false, true);
        ChaosRule inserted = dao.insert(newRule);
        System.out.println("  Inserted rule: " + inserted);
        if (inserted.getId() <= 0) {
            throw new AssertionError("Generated ID was not assigned to inserted rule!");
        }

        // 3. Verify getAll and getById
        System.out.println("\n[STEP 3] Verifying getAll() and getById()...");
        List<ChaosRule> allRules = dao.getAll();
        boolean found = allRules.stream().anyMatch(r -> r.getId() == inserted.getId());
        if (!found) {
            throw new AssertionError("Inserted rule not found in getAll()!");
        }

        Optional<ChaosRule> fetched = dao.getById(inserted.getId());
        if (fetched.isEmpty()) {
            throw new AssertionError("getById returned empty for inserted ID: " + inserted.getId());
        }
        ChaosRule rule = fetched.get();
        if (!rule.getRoutePattern().equals("/api/test/*") ||
                rule.getLatencyMs() != 1500 ||
                rule.getStatusOverrideCode() == null ||
                rule.getStatusOverrideCode() != 503 ||
                rule.isConnectionResetEnabled() ||
                !rule.isEnabled()) {
            throw new AssertionError("Retrieved rule attributes did not match inserted values: " + rule);
        }
        System.out.println("  Result: PASS (getAll and getById accurately matched)");

        // 4. Update rule
        System.out.println("\n[STEP 4] Updating ChaosRule...");
        inserted.setRoutePattern("/api/updated/*");
        inserted.setLatencyMs(3000);
        inserted.setStatusOverrideCode(null);
        inserted.setConnectionResetEnabled(true);
        inserted.setEnabled(false);

        boolean updated = dao.update(inserted);
        System.out.println("  Update result: " + updated);
        if (!updated) {
            throw new AssertionError("Update returned false!");
        }

        Optional<ChaosRule> fetchedUpdated = dao.getById(inserted.getId());
        if (fetchedUpdated.isEmpty()) {
            throw new AssertionError("Could not find updated rule by ID!");
        }
        ChaosRule up = fetchedUpdated.get();
        if (!up.getRoutePattern().equals("/api/updated/*") ||
                up.getLatencyMs() != 3000 ||
                up.getStatusOverrideCode() != null ||
                !up.isConnectionResetEnabled() ||
                up.isEnabled()) {
            throw new AssertionError("Updated rule values mismatch: " + up);
        }
        System.out.println("  Result: PASS (Updated values accurately persisted: " + up + ")");

        // 5. Delete rule
        System.out.println("\n[STEP 5] Deleting ChaosRule...");
        boolean deleted = dao.delete(inserted.getId());
        System.out.println("  Delete result: " + deleted);
        if (!deleted) {
            throw new AssertionError("Delete returned false!");
        }

        Optional<ChaosRule> fetchedDeleted = dao.getById(inserted.getId());
        if (fetchedDeleted.isPresent()) {
            throw new AssertionError("Deleted rule still returned by getById!");
        }
        System.out.println("  Result: PASS (Rule deleted successfully)");

        // 6. Verify final count matches initial
        List<ChaosRule> finalRules = dao.getAll();
        System.out.println("\n[STEP 6] Final count matches initial: " + (finalRules.size() == initialRules.size()));
        if (finalRules.size() != initialRules.size()) {
            throw new AssertionError("Final rules count does not match initial!");
        }

        System.out.println("\n==================================================");
        System.out.println("  All ChaosRuleDao M5.2 tests PASSED!");
        System.out.println("==================================================");
    }
}
