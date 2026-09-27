package com.entropylab.mock;

import java.util.Objects;

/**
 * Model representing a static mock route.
 * When enabled, intercepted requests matching routePattern return the static file
 * at filePath directly without contacting upstream backends or applying chaos rules.
 */
public class MockRoute {

    private int id;
    private String routePattern;
    private String filePath;
    private boolean enabled = true;
    private MockSource source = MockSource.MANUAL;

    public MockRoute() {
        this.enabled = true;
        this.source = MockSource.MANUAL;
    }

    public MockRoute(String routePattern, String filePath) {
        this(routePattern, filePath, true, MockSource.MANUAL);
    }

    public MockRoute(String routePattern, String filePath, boolean enabled, MockSource source) {
        this.routePattern = routePattern;
        this.filePath = filePath;
        this.enabled = enabled;
        this.source = source != null ? source : MockSource.MANUAL;
    }

    public MockRoute(int id, String routePattern, String filePath, boolean enabled, MockSource source) {
        this.id = id;
        this.routePattern = routePattern;
        this.filePath = filePath;
        this.enabled = enabled;
        this.source = source != null ? source : MockSource.MANUAL;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MockSource getSource() {
        return source;
    }

    public void setSource(MockSource source) {
        this.source = source != null ? source : MockSource.MANUAL;
    }

    public void setSource(String sourceName) {
        this.source = MockSource.fromString(sourceName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MockRoute mockRoute = (MockRoute) o;
        return id == mockRoute.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MockRoute{" +
                "id=" + id +
                ", routePattern='" + routePattern + '\'' +
                ", filePath='" + filePath + '\'' +
                ", enabled=" + enabled +
                ", source=" + source +
                '}';
    }

    /**
     * Verification test demonstrating compilation and instantiation of MockRoute with all field combinations.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  MockRoute Model Verification (M6.1)");
        System.out.println("==================================================");

        MockRoute defaultRoute = new MockRoute();
        System.out.println("Default: " + defaultRoute);
        assert defaultRoute.isEnabled();
        assert defaultRoute.getSource() == MockSource.MANUAL;

        MockRoute manualRoute = new MockRoute("/api/users/*", "mocks/users.json");
        System.out.println("Manual: " + manualRoute);
        assert manualRoute.isEnabled();
        assert manualRoute.getSource() == MockSource.MANUAL;

        MockRoute snapshotRoute = new MockRoute(1, "/api/snapshot/*", "mocks/snapshot.json", false, MockSource.AUTO_SNAPSHOT);
        System.out.println("Snapshot: " + snapshotRoute);
        assert snapshotRoute.getId() == 1;
        assert !snapshotRoute.isEnabled();
        assert snapshotRoute.getSource() == MockSource.AUTO_SNAPSHOT;

        snapshotRoute.setSource("MANUAL");
        assert snapshotRoute.getSource() == MockSource.MANUAL;

        System.out.println("\nAll MockRoute field combinations verified successfully!");
    }
}
