package com.entropylab.routes;

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
 * Data Access Object for persisting and querying ProxyRoute entities in SQLite.
 */
public class ProxyRouteDao {

    private Connection getConnection() {
        return AppContext.getDatabaseManager().getConnection();
    }

    /**
     * Inserts a new ProxyRoute into the proxy_routes table.
     * Sets the generated id on the provided route object.
     *
     * @param route The ProxyRoute to insert.
     * @return The ProxyRoute with its generated id assigned.
     */
    public ProxyRoute insert(ProxyRoute route) {
        String sql = "INSERT INTO proxy_routes (route_pattern, target_base_url, enabled) VALUES (?, ?, ?)";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, route.getRoutePattern());
            pstmt.setString(2, route.getTargetBaseUrl());
            pstmt.setInt(3, route.isEnabled() ? 1 : 0);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    route.setId(generatedKeys.getInt(1));
                }
            }
            return route;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert ProxyRoute: " + route, e);
        }
    }

    /**
     * Updates an existing ProxyRoute in the database.
     *
     * @param route The ProxyRoute containing updated fields and valid id.
     * @return true if a row was updated, false otherwise.
     */
    public boolean update(ProxyRoute route) {
        String sql = "UPDATE proxy_routes SET route_pattern = ?, target_base_url = ?, enabled = ? WHERE id = ?";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, route.getRoutePattern());
            pstmt.setString(2, route.getTargetBaseUrl());
            pstmt.setInt(3, route.isEnabled() ? 1 : 0);
            pstmt.setInt(4, route.getId());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update ProxyRoute: " + route, e);
        }
    }

    /**
     * Deletes a ProxyRoute by its id.
     *
     * @param id The id of the route to delete.
     * @return true if a row was deleted, false otherwise.
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM proxy_routes WHERE id = ?";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete ProxyRoute with id: " + id, e);
        }
    }

    /**
     * Retrieves all ProxyRoutes from the database, ordered by id ascending.
     *
     * @return List of all ProxyRoute rows.
     */
    public List<ProxyRoute> getAll() {
        String sql = "SELECT id, route_pattern, target_base_url, enabled FROM proxy_routes ORDER BY id ASC";
        Connection conn = getConnection();
        List<ProxyRoute> routes = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                ProxyRoute route = new ProxyRoute(
                        rs.getInt("id"),
                        rs.getString("route_pattern"),
                        rs.getString("target_base_url"),
                        rs.getInt("enabled") == 1
                );
                routes.add(route);
            }
            return routes;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch all ProxyRoutes", e);
        }
    }

    /**
     * Retrieves a single ProxyRoute by its id.
     *
     * @param id Route id.
     * @return Optional containing the ProxyRoute if found.
     */
    public Optional<ProxyRoute> getById(int id) {
        String sql = "SELECT id, route_pattern, target_base_url, enabled FROM proxy_routes WHERE id = ?";
        Connection conn = getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ProxyRoute(
                            rs.getInt("id"),
                            rs.getString("route_pattern"),
                            rs.getString("target_base_url"),
                            rs.getInt("enabled") == 1
                    ));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch ProxyRoute with id: " + id, e);
        }
    }

    /**
     * Manual console test verifying CRUD operations on ProxyRouteDao.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ProxyRouteDao Manual Test Suite");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        ProxyRouteDao dao = new ProxyRouteDao();

        // 1. Initial listing
        List<ProxyRoute> initialRoutes = dao.getAll();
        System.out.println("[STEP 1] Current routes in database: " + initialRoutes.size());

        // 2. Insert test route
        ProxyRoute newRoute = new ProxyRoute("/github/*", "https://api.github.com", true);
        ProxyRoute inserted = dao.insert(newRoute);
        System.out.println("[STEP 2] Inserted route: " + inserted);
        if (inserted.getId() <= 0) {
            throw new AssertionError("Generated ID was not assigned to inserted route!");
        }

        // 3. Verify route in getAll()
        List<ProxyRoute> afterInsert = dao.getAll();
        boolean foundInserted = afterInsert.stream().anyMatch(r -> r.getId() == inserted.getId());
        System.out.println("[STEP 3] Route found in getAll() list: " + foundInserted + " (total: " + afterInsert.size() + ")");
        if (!foundInserted) {
            throw new AssertionError("Inserted route not found in getAll()!");
        }

        // 4. Update route
        inserted.setTargetBaseUrl("https://api.github.com/v3");
        inserted.setEnabled(false);
        boolean updated = dao.update(inserted);
        System.out.println("[STEP 4] Update route result: " + updated);
        Optional<ProxyRoute> fetchedUpdated = dao.getById(inserted.getId());
        if (fetchedUpdated.isEmpty() ||
                !fetchedUpdated.get().getTargetBaseUrl().equals("https://api.github.com/v3") ||
                fetchedUpdated.get().isEnabled()) {
            throw new AssertionError("Route update was not persisted correctly: " + fetchedUpdated);
        }
        System.out.println("         Persisted updated values: " + fetchedUpdated.get());

        // 5. Delete route
        boolean deleted = dao.delete(inserted.getId());
        System.out.println("[STEP 5] Delete route result: " + deleted);
        Optional<ProxyRoute> fetchedDeleted = dao.getById(inserted.getId());
        if (fetchedDeleted.isPresent()) {
            throw new AssertionError("Deleted route still found in database!");
        }

        // 6. Verify final count matches initial
        List<ProxyRoute> finalRoutes = dao.getAll();
        System.out.println("[STEP 6] Final routes count: " + finalRoutes.size() + " (matches initial: " + (finalRoutes.size() == initialRoutes.size()) + ")");

        System.out.println("==================================================");
        System.out.println("  All ProxyRouteDao CRUD operations PASSED!");
        System.out.println("==================================================");
    }
}
