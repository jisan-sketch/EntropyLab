package com.entropylab.routes;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.RoutePatternMatcher;
import com.entropylab.core.SchemaInitializer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory store for ProxyRoutes, backed by SQLite via ProxyRouteDao.
 * Loads all routes on construction and writes through to DAO on all mutations.
 */
public class ProxyRouteStore {

    private final ProxyRouteDao dao;
    private final List<ProxyRoute> routes;

    public ProxyRouteStore() {
        this(new ProxyRouteDao());
    }

    public ProxyRouteStore(ProxyRouteDao dao) {
        this.dao = dao;
        this.routes = new CopyOnWriteArrayList<>(dao.getAll());
    }

    /**
     * Persists a new route via DAO and adds it to the in-memory list.
     *
     * @param route The ProxyRoute to add.
     * @return The added ProxyRoute with generated ID assigned.
     */
    public ProxyRoute addRoute(ProxyRoute route) {
        ProxyRoute inserted = dao.insert(route);
        routes.add(inserted);
        return inserted;
    }

    /**
     * Persists route updates via DAO and updates the in-memory copy.
     *
     * @param route The ProxyRoute to update.
     * @return true if updated in DAO, false otherwise.
     */
    public boolean updateRoute(ProxyRoute route) {
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
     * @return List of all ProxyRoute instances.
     */
    public List<ProxyRoute> getAllRoutes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Finds the matching ENABLED route for a given path with the longest match.
     * Returns null if no enabled route matches.
     *
     * @param path The incoming request path.
     * @return Matching enabled ProxyRoute or null.
     */
    public ProxyRoute findMatchingRoute(String path) {
        if (path == null) {
            return null;
        }

        String lookupPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        List<ProxyRoute> enabledRoutes = routes.stream()
                .filter(ProxyRoute::isEnabled)
                .toList();

        List<String> patterns = enabledRoutes.stream()
                .map(ProxyRoute::getRoutePattern)
                .toList();

        String bestPattern = RoutePatternMatcher.findLongestMatch(patterns, lookupPath);
        if (bestPattern != null) {
            return enabledRoutes.stream()
                    .filter(r -> bestPattern.equals(r.getRoutePattern()))
                    .findFirst()
                    .orElse(null);
        }

        // Also check if any exact pattern without "/*" acts as prefix (e.g. "/github" for "/github/users")
        ProxyRoute bestPrefix = null;
        int longestLen = -1;
        for (ProxyRoute r : enabledRoutes) {
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
     * Manual console test verifying ProxyRouteStore persistence, reload simulation,
     * and findMatchingRoute resolution.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ProxyRouteStore Manual Test Suite");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        ProxyRouteStore store = new ProxyRouteStore();

        // Clean slate for test
        for (ProxyRoute r : store.getAllRoutes()) {
            store.removeRoute(r.getId());
        }
        System.out.println("[STEP 1] Store reset. Initial routes: " + store.getAllRoutes().size());

        // Add 3 test routes
        ProxyRoute r1 = store.addRoute(new ProxyRoute("/api/*", "https://api.example.com", true));
        ProxyRoute r2 = store.addRoute(new ProxyRoute("/api/v1/*", "https://v1.example.com", true));
        ProxyRoute r3 = store.addRoute(new ProxyRoute("/disabled/*", "https://disabled.example.com", false));
        System.out.println("[STEP 2] Added 3 routes: ");
        System.out.println("  - " + r1);
        System.out.println("  - " + r2);
        System.out.println("  - " + r3);

        // Simulate app restart by constructing a new ProxyRouteStore instance
        System.out.println("[STEP 3] Simulating app restart (new store instance from DB)...");
        ProxyRouteStore restartedStore = new ProxyRouteStore();
        List<ProxyRoute> reloaded = restartedStore.getAllRoutes();
        System.out.println("  Reloaded route count: " + reloaded.size() + " (expected: 3)");
        if (reloaded.size() != 3) {
            throw new AssertionError("Routes failed to reload from DB on restart simulation!");
        }

        // Test route matching with longest match
        System.out.println("[STEP 4] Testing findMatchingRoute resolution:");
        ProxyRoute match1 = restartedStore.findMatchingRoute("/api/v1/users");
        System.out.println("  /api/v1/users -> " + (match1 != null ? match1.getRoutePattern() : "null") + " (expected: /api/v1/*)");
        if (match1 == null || !"/api/v1/*".equals(match1.getRoutePattern())) {
            throw new AssertionError("Expected longest match /api/v1/* for /api/v1/users");
        }

        ProxyRoute match2 = restartedStore.findMatchingRoute("/api/general");
        System.out.println("  /api/general  -> " + (match2 != null ? match2.getRoutePattern() : "null") + " (expected: /api/*)");
        if (match2 == null || !"/api/*".equals(match2.getRoutePattern())) {
            throw new AssertionError("Expected /api/* for /api/general");
        }

        ProxyRoute match3 = restartedStore.findMatchingRoute("/disabled/test");
        System.out.println("  /disabled/test -> " + (match3 != null ? match3.getRoutePattern() : "null") + " (expected: null, disabled)");
        if (match3 != null) {
            throw new AssertionError("Expected null for disabled route /disabled/*");
        }

        ProxyRoute match4 = restartedStore.findMatchingRoute("/unmapped");
        System.out.println("  /unmapped     -> " + (match4 != null ? match4.getRoutePattern() : "null") + " (expected: null)");
        if (match4 != null) {
            throw new AssertionError("Expected null for unmapped path");
        }

        // Cleanup test routes
        restartedStore.removeRoute(r1.getId());
        restartedStore.removeRoute(r2.getId());
        restartedStore.removeRoute(r3.getId());
        System.out.println("[STEP 5] Cleaned up test routes. Remaining: " + restartedStore.getAllRoutes().size());

        System.out.println("==================================================");
        System.out.println("  All ProxyRouteStore tests PASSED!");
        System.out.println("==================================================");
    }
}
