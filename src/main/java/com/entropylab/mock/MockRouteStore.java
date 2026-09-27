package com.entropylab.mock;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.RoutePatternMatcher;
import com.entropylab.core.SchemaInitializer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory store for MockRoutes, backed by SQLite via MockRouteDao.
 * Loads all routes on construction and synchronizes writes with the database.
 */
public class MockRouteStore {

    private final MockRouteDao dao;
    private final List<MockRoute> routes;

    public MockRouteStore() {
        this(new MockRouteDao());
    }

    public MockRouteStore(MockRouteDao dao) {
        this.dao = dao;
        this.routes = new CopyOnWriteArrayList<>(dao.getAll());
    }

    /**
     * Persists a new route via DAO and adds it to the in-memory list.
     *
     * @param route The MockRoute to add.
     * @return The added MockRoute with its generated ID assigned.
     */
    public MockRoute addRoute(MockRoute route) {
        MockRoute inserted = dao.insert(route);
        routes.add(inserted);
        return inserted;
    }

    /**
     * Persists route updates via DAO and updates the in-memory copy.
     *
     * @param route The MockRoute to update.
     * @return true if updated in DAO, false otherwise.
     */
    public boolean updateRoute(MockRoute route) {
        boolean updated = dao.update(route);
        if (updated) {
            for (int i = 0; i < routes.size(); i++) {
                if (routes.get(i).getId() == route.getId()) {
                    routes.set(i, route);
                    break;
                }
            }
        }
        return updated;
    }

    /**
     * Deletes a route via DAO and removes it from the in-memory list.
     *
     * @param id The id of the route to remove.
     * @return true if deleted from DAO, false otherwise.
     */
    public boolean removeRoute(int id) {
        boolean deleted = dao.delete(id);
        if (deleted) {
            routes.removeIf(r -> r.getId() == id);
        }
        return deleted;
    }

    /**
     * Returns an unmodifiable view of all routes currently in memory.
     *
     * @return List of all MockRoute instances.
     */
    public List<MockRoute> getAllRoutes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Finds the matching ENABLED route for a given path using RoutePatternMatcher.
     * If multiple routes match, the longest match wins.
     *
     * @param path The incoming request path.
     * @return Matching enabled MockRoute, or null if none match.
     */
    public MockRoute findMatchingRoute(String path) {
        if (path == null) {
            return null;
        }

        String lookupPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        List<MockRoute> enabledRoutes = routes.stream()
                .filter(MockRoute::isEnabled)
                .toList();

        // Check exact match against incoming path (supporting exact full path or query matches)
        for (MockRoute r : enabledRoutes) {
            if (path.equals(r.getRoutePattern())) {
                return r;
            }
        }

        List<String> patterns = enabledRoutes.stream()
                .map(MockRoute::getRoutePattern)
                .toList();

        String bestPattern = RoutePatternMatcher.findLongestMatch(patterns, lookupPath);
        if (bestPattern != null) {
            return enabledRoutes.stream()
                    .filter(r -> bestPattern.equals(r.getRoutePattern()))
                    .findFirst()
                    .orElse(null);
        }

        // Also check if any pattern without "/*" acts as prefix (e.g. "/mock/api" for "/mock/api/users")
        MockRoute bestPrefix = null;
        int longestLen = -1;
        for (MockRoute r : enabledRoutes) {
            String pat = r.getRoutePattern();
            if (!pat.endsWith("/*") && (lookupPath.equals(pat) || lookupPath.startsWith(pat + "/"))) {
                if (pat.length() > longestLen) {
                    longestLen = pat.length();
                    bestPrefix = r;
                }
            }
        }
        return bestPrefix;
    }

    /**
     * Manual console test proving add, reload from DB on restart, and pattern matching.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  MockRouteStore Test Suite (M6.3)");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        MockRouteDao dao = new MockRouteDao(dbManager);
        MockRouteStore store1 = new MockRouteStore(dao);

        System.out.println("\n[STEP 1] Adding 3 test MockRoutes to store1...");
        MockRoute routeA = store1.addRoute(new MockRoute("/api/users/*", "mocks/users.json", true, MockSource.MANUAL));
        System.out.println("  Added Route A: " + routeA);

        MockRoute routeB = store1.addRoute(new MockRoute("/api/users/profile/*", "mocks/profile.json", true, MockSource.MANUAL));
        System.out.println("  Added Route B (longer pattern): " + routeB);

        MockRoute routeC = store1.addRoute(new MockRoute("/api/disabled/*", "mocks/disabled.json", false, MockSource.AUTO_SNAPSHOT));
        System.out.println("  Added Route C (disabled): " + routeC);

        try {
            // 2. Test matching on store1
            System.out.println("\n[STEP 2] Testing pattern matching on store1...");

            // Test A: Should match /api/users/*
            MockRoute matchA = store1.findMatchingRoute("/api/users/123");
            System.out.println("  /api/users/123 -> " + matchA);
            if (matchA == null || matchA.getId() != routeA.getId()) {
                throw new AssertionError("Expected match with Route A, got: " + matchA);
            }

            // Test B: Should match /api/users/profile/* (longest match wins!)
            MockRoute matchB = store1.findMatchingRoute("/api/users/profile/details");
            System.out.println("  /api/users/profile/details -> " + matchB);
            if (matchB == null || matchB.getId() != routeB.getId()) {
                throw new AssertionError("Expected match with Route B (longest match), got: " + matchB);
            }

            // Test C: Disabled route should not match
            MockRoute matchC = store1.findMatchingRoute("/api/disabled/endpoint");
            System.out.println("  /api/disabled/endpoint -> " + matchC);
            if (matchC != null) {
                throw new AssertionError("Disabled route should not match, but got: " + matchC);
            }

            // Test D: Unmatched path
            MockRoute matchD = store1.findMatchingRoute("/completely/unrelated");
            System.out.println("  /completely/unrelated -> " + matchD);
            if (matchD != null) {
                throw new AssertionError("Expected null for unrelated path, got: " + matchD);
            }
            System.out.println("  Result: PASS (Matching rules, longest-match-wins, and disabled route exclusion verified)");

            // 3. Simulate App Restart: Create store2 and verify reload from SQLite DB
            System.out.println("\n[STEP 3] Simulating app restart with new MockRouteStore(dao)...");
            MockRouteStore store2 = new MockRouteStore(dao);
            System.out.println("  store2 loaded " + store2.getAllRoutes().size() + " routes from DB");

            boolean hasA = store2.getAllRoutes().stream().anyMatch(r -> r.getId() == routeA.getId());
            boolean hasB = store2.getAllRoutes().stream().anyMatch(r -> r.getId() == routeB.getId());
            boolean hasC = store2.getAllRoutes().stream().anyMatch(r -> r.getId() == routeC.getId());

            if (!hasA || !hasB || !hasC) {
                throw new AssertionError("Not all routes were reloaded from DB into store2!");
            }

            // Test matching on store2 after restart
            MockRoute reloadMatch = store2.findMatchingRoute("/api/users/profile/avatar");
            if (reloadMatch == null || reloadMatch.getId() != routeB.getId()) {
                throw new AssertionError("store2 failed matching after reload!");
            }
            System.out.println("  Result: PASS (Routes reloaded cleanly from SQLite on restart)");

            // 4. Test updateRoute
            System.out.println("\n[STEP 4] Testing updateRoute...");
            routeA.setFilePath("mocks/users-updated.json");
            boolean updated = store2.updateRoute(routeA);
            if (!updated || !"mocks/users-updated.json".equals(store2.findMatchingRoute("/api/users/123").getFilePath())) {
                throw new AssertionError("updateRoute failed to update store2!");
            }
            System.out.println("  Result: PASS (Updated filePath in memory and DB)");

            // 5. Verify AppContext registration
            System.out.println("\n[STEP 5] Verifying AppContext registration...");
            MockRouteStore appContextStore = AppContext.getMockRouteStore();
            if (appContextStore == null) {
                throw new AssertionError("AppContext.getMockRouteStore() returned null!");
            }
            System.out.println("  Result: PASS (AppContext singleton properly registered and accessible)");

        } finally {
            // Clean up test routes
            store1.removeRoute(routeA.getId());
            store1.removeRoute(routeB.getId());
            store1.removeRoute(routeC.getId());
            System.out.println("\n[CLEANUP] Removed test routes from store and DB.");
        }

        System.out.println("\n==================================================");
        System.out.println("  All MockRouteStore M6.3 tests PASSED!");
        System.out.println("==================================================");
    }
}
