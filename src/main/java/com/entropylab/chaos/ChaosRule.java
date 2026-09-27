package com.entropylab.chaos;

import java.util.Objects;

/**
 * Model representing a chaos engineering rule applied to matched routes.
 * Supports injected latency, HTTP status code override, and abrupt connection reset.
 */
public class ChaosRule {

    private int id;
    private String routePattern;
    private int latencyMs = 0;
    private Integer statusOverrideCode;
    private boolean connectionResetEnabled = false;
    private boolean enabled = true;

    public ChaosRule() {
        this.latencyMs = 0;
        this.connectionResetEnabled = false;
        this.enabled = true;
    }

    public ChaosRule(String routePattern, int latencyMs, Integer statusOverrideCode,
                     boolean connectionResetEnabled, boolean enabled) {
        this.routePattern = routePattern;
        this.latencyMs = latencyMs;
        this.statusOverrideCode = statusOverrideCode;
        this.connectionResetEnabled = connectionResetEnabled;
        this.enabled = enabled;
    }

    public ChaosRule(int id, String routePattern, int latencyMs, Integer statusOverrideCode,
                     boolean connectionResetEnabled, boolean enabled) {
        this.id = id;
        this.routePattern = routePattern;
        this.latencyMs = latencyMs;
        this.statusOverrideCode = statusOverrideCode;
        this.connectionResetEnabled = connectionResetEnabled;
        this.enabled = enabled;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRoutePattern() {
        return routePattern;
    }

    public void setRoutePattern(String routePattern) {
        this.routePattern = routePattern;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Integer getStatusOverrideCode() {
        return statusOverrideCode;
    }

    public void setStatusOverrideCode(Integer statusOverrideCode) {
        this.statusOverrideCode = statusOverrideCode;
    }

    public boolean isConnectionResetEnabled() {
        return connectionResetEnabled;
    }

    public void setConnectionResetEnabled(boolean connectionResetEnabled) {
        this.connectionResetEnabled = connectionResetEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChaosRule chaosRule = (ChaosRule) o;
        return id == chaosRule.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ChaosRule{" +
                "id=" + id +
                ", routePattern='" + routePattern + '\'' +
                ", latencyMs=" + latencyMs +
                ", statusOverrideCode=" + statusOverrideCode +
                ", connectionResetEnabled=" + connectionResetEnabled +
                ", enabled=" + enabled +
                '}';
    }

    /**
     * Manual console test verifying ChaosRule instantiation with all field combinations.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ChaosRule Model Test Suite (M5.1)");
        System.out.println("==================================================");

        // 1. Default constructor defaults
        ChaosRule rule1 = new ChaosRule();
        System.out.println("[TEST 1] Default constructor: " + rule1);
        if (rule1.getLatencyMs() != 0 || rule1.isConnectionResetEnabled() || !rule1.isEnabled() || rule1.getStatusOverrideCode() != null) {
            throw new AssertionError("Default constructor failed to initialize expected defaults!");
        }
        System.out.println("  Result: PASS");

        // 2. Latency-only rule
        ChaosRule rule2 = new ChaosRule("/api/slow/*", 2500, null, false, true);
        System.out.println("\n[TEST 2] Latency-only rule: " + rule2);
        if (rule2.getLatencyMs() != 2500 || rule2.getStatusOverrideCode() != null || !rule2.isEnabled()) {
            throw new AssertionError("Latency-only rule mismatch!");
        }
        System.out.println("  Result: PASS");

        // 3. Status override rule
        ChaosRule rule3 = new ChaosRule("/api/fail/*", 0, 503, false, true);
        System.out.println("\n[TEST 3] Status override rule: " + rule3);
        if (rule3.getStatusOverrideCode() == null || rule3.getStatusOverrideCode() != 503) {
            throw new AssertionError("Status override rule mismatch!");
        }
        System.out.println("  Result: PASS");

        // 4. Connection reset rule
        ChaosRule rule4 = new ChaosRule(42, "/api/drop/*", 500, null, true, true);
        System.out.println("\n[TEST 4] Connection reset rule with ID: " + rule4);
        if (rule4.getId() != 42 || !rule4.isConnectionResetEnabled()) {
            throw new AssertionError("Connection reset rule mismatch!");
        }
        System.out.println("  Result: PASS");

        // 5. Disabled rule with all fields populated
        ChaosRule rule5 = new ChaosRule(99, "/api/disabled/*", 1000, 500, true, false);
        System.out.println("\n[TEST 5] Disabled rule: " + rule5);
        if (rule5.isEnabled() || rule5.getId() != 99) {
            throw new AssertionError("Disabled rule mismatch!");
        }
        System.out.println("  Result: PASS");

        System.out.println("\n==================================================");
        System.out.println("  All ChaosRule M5.1 tests PASSED!");
        System.out.println("==================================================");
    }
}
