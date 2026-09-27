package com.entropylab.mock;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for persisting and querying MockRoute entities in SQLite.
 */
public class MockRouteDao {

    private final DatabaseManager databaseManager;

    public MockRouteDao() {
        this(null);
    }

    public MockRouteDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    private Connection getConnection() {
        if (databaseManager != null) {
            return databaseManager.getConnection();
        }
        return AppContext.getDatabaseManager().getConnection();
    }

    /**
     * Inserts a new MockRoute into the mock_routes table.
     * Assigns the generated database primary key ID on the route object.
     *
     * @param route The MockRoute to insert.
     * @return The MockRoute with its generated id assigned.
     */
    public MockRoute insert(MockRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("MockRoute must not be null");
        }

        String sql = """
            INSERT INTO mock_routes (route_pattern, file_path, enabled, source)
            VALUES (?, ?, ?, ?)
            """;

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, route.getRoutePattern());
            pstmt.setString(2, route.getFilePath());
            pstmt.setInt(3, route.isEnabled() ? 1 : 0);
            pstmt.setString(4, route.getSource() != null ? route.getSource().name() : MockSource.MANUAL.name());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    route.setId(generatedKeys.getInt(1));
                }
            }
            return route;
        } catch (SQLException e) {
            System.err.println("[MockRouteDao] Failed to insert MockRoute: " + e.getMessage());
            throw new RuntimeException("Failed to insert MockRoute: " + route, e);
        }
    }

    /**
     * Updates an existing MockRoute in the database.
     *
     * @param route The MockRoute containing updated values and a valid ID.
     * @return true if a row was updated, false otherwise.
     */
    public boolean update(MockRoute route) {
        if (route == null || route.getId() <= 0) {
            throw new IllegalArgumentException("MockRoute must have a valid ID for update");
        }

        String sql = """
            UPDATE mock_routes
            SET route_pattern = ?, file_path = ?, enabled = ?, source = ?
            WHERE id = ?
            """;

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, route.getRoutePattern());
            pstmt.setString(2, route.getFilePath());
            pstmt.setInt(3, route.isEnabled() ? 1 : 0);
            pstmt.setString(4, route.getSource() != null ? route.getSource().name() : MockSource.MANUAL.name());
            pstmt.setInt(5, route.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MockRouteDao] Failed to update MockRoute: " + e.getMessage());
            throw new RuntimeException("Failed to update MockRoute: " + route, e);
        }
    }

    /**
     * Deletes a MockRoute from the database by its ID.
     *
     * @param id The ID of the MockRoute to delete.
     * @return true if a row was deleted, false otherwise.
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM mock_routes WHERE id = ?";

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MockRouteDao] Failed to delete MockRoute with ID " + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to delete MockRoute ID " + id, e);
        }
    }

    /**
     * Retrieves all MockRoutes from the database ordered by id ASC.
     *
     * @return List of all persisted MockRoutes.
     */
    public List<MockRoute> getAll() {
        String sql = "SELECT id, route_pattern, file_path, enabled, source FROM mock_routes ORDER BY id ASC";
        List<MockRoute> results = new ArrayList<>();

        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                results.add(mapResultSetToMockRoute(rs));
            }
            return results;
        } catch (SQLException e) {
            System.err.println("[MockRouteDao] Failed to query all MockRoutes: " + e.getMessage());
            throw new RuntimeException("Failed to query mock_routes", e);
        }
    }

    /**
     * Retrieves a single MockRoute by ID.
     *
     * @param id The route ID.
     * @return Optional containing the MockRoute if found.
     */
    public Optional<MockRoute> getById(int id) {
        String sql = "SELECT id, route_pattern, file_path, enabled, source FROM mock_routes WHERE id = ?";

        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToMockRoute(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            System.err.println("[MockRouteDao] Failed to query MockRoute ID " + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to query MockRoute ID " + id, e);
        }
    }

    private MockRoute mapResultSetToMockRoute(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String routePattern = rs.getString("route_pattern");
        String filePath = rs.getString("file_path");
        boolean enabled = rs.getInt("enabled") == 1;
        String sourceStr = rs.getString("source");
        MockSource source = MockSource.fromString(sourceStr);

        return new MockRoute(id, routePattern, filePath, enabled, source);
    }

    /**
     * Manual console test proving insert, update, delete, and getAll against SQLite.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  MockRouteDao CRUD Test Suite (M6.2)");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        MockRouteDao dao = new MockRouteDao(dbManager);

        System.out.println("\n[TEST 1] Inserting 2 mock routes...");
        MockRoute route1 = new MockRoute("/api/users/*", "mocks/users.json", true, MockSource.MANUAL);
        dao.insert(route1);
        System.out.println("  Inserted Route 1: " + route1);
        if (route1.getId() <= 0) {
            throw new AssertionError("Route 1 should have generated ID > 0");
        }

        MockRoute route2 = new MockRoute("/api/products/*", "mocks/products.json", false, MockSource.AUTO_SNAPSHOT);
        dao.insert(route2);
        System.out.println("  Inserted Route 2: " + route2);
        if (route2.getId() <= 0) {
            throw new AssertionError("Route 2 should have generated ID > 0");
        }

        System.out.println("\n[TEST 2] Verifying getAll()...");
        List<MockRoute> allRoutes = dao.getAll();
        System.out.println("  Found " + allRoutes.size() + " mock routes in database:");
        for (MockRoute r : allRoutes) {
            System.out.println("   - " + r);
        }
        boolean hasRoute1 = allRoutes.stream().anyMatch(r -> r.getId() == route1.getId());
        boolean hasRoute2 = allRoutes.stream().anyMatch(r -> r.getId() == route2.getId());
        if (!hasRoute1 || !hasRoute2) {
            throw new AssertionError("getAll() should contain both inserted routes!");
        }
        System.out.println("  Result: PASS");

        System.out.println("\n[TEST 3] Updating Route 1...");
        route1.setFilePath("mocks/users-v2.json");
        route1.setEnabled(false);
        boolean updated = dao.update(route1);
        System.out.println("  Update result: " + updated);
        if (!updated) {
            throw new AssertionError("Expected update to succeed");
        }

        Optional<MockRoute> fetchedOpt = dao.getById(route1.getId());
        if (fetchedOpt.isEmpty()) {
            throw new AssertionError("Expected to find updated route by ID");
        }
        MockRoute fetched = fetchedOpt.get();
        System.out.println("  Fetched updated route: " + fetched);
        if (!"mocks/users-v2.json".equals(fetched.getFilePath()) || fetched.isEnabled()) {
            throw new AssertionError("Updated route fields do not match expected values!");
        }
        System.out.println("  Result: PASS");

        System.out.println("\n[TEST 4] Deleting Route 2...");
        boolean deleted = dao.delete(route2.getId());
        System.out.println("  Delete result: " + deleted);
        if (!deleted) {
            throw new AssertionError("Expected delete to succeed");
        }
        Optional<MockRoute> deletedCheck = dao.getById(route2.getId());
        if (deletedCheck.isPresent()) {
            throw new AssertionError("Route 2 should not exist after deletion!");
        }
        System.out.println("  Result: PASS");

        // Cleanup
        dao.delete(route1.getId());
        System.out.println("\n[CLEANUP] Deleted test Route 1.");

        System.out.println("\n==================================================");
        System.out.println("  All MockRouteDao M6.2 CRUD tests PASSED!");
        System.out.println("==================================================");
    }
}
