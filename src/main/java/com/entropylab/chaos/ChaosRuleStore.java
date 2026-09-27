package com.entropylab.chaos;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.RoutePatternMatcher;
import com.entropylab.core.SchemaInitializer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory store for ChaosRules, backed by SQLite via ChaosRuleDao.
 * Loads all rules on construction and synchronizes writes with the database.
 */
public class ChaosRuleStore {

    private final ChaosRuleDao dao;
    private final List<ChaosRule> rules;

    public ChaosRuleStore() {
        this(new ChaosRuleDao());
    }

    public ChaosRuleStore(ChaosRuleDao dao) {
        this.dao = dao;
        this.rules = new CopyOnWriteArrayList<>(dao.getAll());
    }

    /**
     * Persists a new rule via DAO and adds it to the in-memory list.
     *
     * @param rule The ChaosRule to add.
     * @return The added ChaosRule with its generated ID assigned.
     */
    public ChaosRule addRule(ChaosRule rule) {
        ChaosRule inserted = dao.insert(rule);
        rules.add(inserted);
        return inserted;
    }

    /**
     * Persists rule updates via DAO and updates the in-memory copy.
     *
     * @param rule The ChaosRule to update.
     * @return true if updated in DAO, false otherwise.
     */
    public boolean updateRule(ChaosRule rule) {
        boolean updated = dao.update(rule);
        if (updated) {
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getId() == rule.getId()) {
                    rules.set(i, rule);
                    break;
                }
            }
        }
        return updated;
    }

    /**
     * Deletes a rule via DAO and removes it from the in-memory list.
     *
     * @param id The id of the rule to remove.
     * @return true if deleted from DAO, false otherwise.
     */
    public boolean removeRule(int id) {
        boolean deleted = dao.delete(id);
        if (deleted) {
            rules.removeIf(r -> r.getId() == id);
        }
        return deleted;
    }

    /**
     * Returns an unmodifiable view of all rules currently in memory.
     *
     * @return List of all ChaosRule instances.
     */
    public List<ChaosRule> getAllRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Finds the first matching ENABLED rule for a given path using RoutePatternMatcher.
     * If multiple rules match, the longest match wins.
     *
     * @param path The incoming request path.
     * @return Matching enabled ChaosRule, or null if none match.
     */
    public ChaosRule findMatchingRule(String path) {
        if (path == null) {
            return null;
        }

        String lookupPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;

        List<ChaosRule> enabledRules = rules.stream()
                .filter(ChaosRule::isEnabled)
                .toList();

        List<String> patterns = enabledRules.stream()
                .map(ChaosRule::getRoutePattern)
                .toList();

        String bestPattern = RoutePatternMatcher.findLongestMatch(patterns, lookupPath);
        if (bestPattern != null) {
            return enabledRules.stream()
                    .filter(r -> bestPattern.equals(r.getRoutePattern()))
                    .findFirst()
                    .orElse(null);
        }

        // Also check if any pattern without "/*" acts as prefix (e.g. "/github" for "/github/users")
        ChaosRule bestPrefix = null;
        int longestLen = -1;
        for (ChaosRule r : enabledRules) {
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
        System.out.println("  ChaosRuleStore Test Suite (M5.3)");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        ChaosRuleDao dao = new ChaosRuleDao(dbManager);
        ChaosRuleStore store1 = new ChaosRuleStore(dao);

        System.out.println("\n[STEP 1] Adding 3 test ChaosRules to store1...");
        ChaosRule ruleA = store1.addRule(new ChaosRule("/api/slow/*", 2000, null, false, true));
        System.out.println("  Added Rule A: " + ruleA);

        ChaosRule ruleB = store1.addRule(new ChaosRule("/api/slow/heavy/*", 5000, null, false, true));
        System.out.println("  Added Rule B (longer prefix): " + ruleB);

        ChaosRule ruleC = store1.addRule(new ChaosRule("/api/disabled/*", 1000, 500, false, false));
        System.out.println("  Added Rule C (disabled): " + ruleC);

        try {
            // 2. Test matching on store1
            System.out.println("\n[STEP 2] Testing pattern matching on store1...");

            // Test A: Should match /api/slow/*
            ChaosRule matchA = store1.findMatchingRule("/api/slow/item");
            System.out.println("  /api/slow/item -> " + matchA);
            if (matchA == null || matchA.getId() != ruleA.getId()) {
                throw new AssertionError("Expected match with Rule A, got: " + matchA);
            }

            // Test B: Should match /api/slow/heavy/* (longest match wins!)
            ChaosRule matchB = store1.findMatchingRule("/api/slow/heavy/calc");
            System.out.println("  /api/slow/heavy/calc -> " + matchB);
            if (matchB == null || matchB.getId() != ruleB.getId()) {
                throw new AssertionError("Expected match with Rule B (longest match), got: " + matchB);
            }

            // Test C: Disabled rule should not match
            ChaosRule matchC = store1.findMatchingRule("/api/disabled/test");
            System.out.println("  /api/disabled/test -> " + matchC);
            if (matchC != null) {
                throw new AssertionError("Disabled rule should not match, but got: " + matchC);
            }

            // Test D: Unmatched path
            ChaosRule matchD = store1.findMatchingRule("/unrelated/path");
            System.out.println("  /unrelated/path -> " + matchD);
            if (matchD != null) {
                throw new AssertionError("Expected null for unrelated path, got: " + matchD);
            }
            System.out.println("  Result: PASS (Matching rules, longest-match-wins, and disabled rule exclusion verified)");

            // 3. Simulate App Restart: Create store2 and verify reload from SQLite DB
            System.out.println("\n[STEP 3] Simulating app restart with new ChaosRuleStore(dao)...");
            ChaosRuleStore store2 = new ChaosRuleStore(dao);
            System.out.println("  store2 loaded " + store2.getAllRules().size() + " rules from DB");

            boolean hasA = store2.getAllRules().stream().anyMatch(r -> r.getId() == ruleA.getId());
            boolean hasB = store2.getAllRules().stream().anyMatch(r -> r.getId() == ruleB.getId());
            boolean hasC = store2.getAllRules().stream().anyMatch(r -> r.getId() == ruleC.getId());

            if (!hasA || !hasB || !hasC) {
                throw new AssertionError("Not all rules were reloaded from DB into store2!");
            }

            // Test matching on store2 after restart
            ChaosRule reloadMatch = store2.findMatchingRule("/api/slow/heavy/calc");
            if (reloadMatch == null || reloadMatch.getId() != ruleB.getId()) {
                throw new AssertionError("store2 failed matching after reload!");
            }
            System.out.println("  Result: PASS (Rules reloaded cleanly from SQLite on restart)");

            // 4. Test updateRule
            System.out.println("\n[STEP 4] Testing updateRule...");
            ruleA.setLatencyMs(4000);
            boolean updated = store2.updateRule(ruleA);
            if (!updated || store2.findMatchingRule("/api/slow/item").getLatencyMs() != 4000) {
                throw new AssertionError("updateRule failed to update store2!");
            }
            System.out.println("  Result: PASS (Updated latencyMs to 4000 in memory and DB)");

        } finally {
            // Clean up test rules
            store1.removeRule(ruleA.getId());
            store1.removeRule(ruleB.getId());
            store1.removeRule(ruleC.getId());
            System.out.println("\n[CLEANUP] Removed test rules from store and DB.");
        }

        System.out.println("\n==================================================");
        System.out.println("  All ChaosRuleStore M5.3 tests PASSED!");
        System.out.println("==================================================");
    }
}
