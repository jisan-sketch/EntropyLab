package com.entropylab.routes;

import java.util.Objects;

/**
 * Model representing a proxy route mapping (route pattern to target base URL).
 */
public class ProxyRoute {

    private int id;
    private String routePattern;
    private String targetBaseUrl;
    private boolean enabled;

    public ProxyRoute() {
        this.enabled = true;
    }

    public ProxyRoute(String routePattern, String targetBaseUrl, boolean enabled) {
        this.routePattern = routePattern;
        this.targetBaseUrl = targetBaseUrl;
        this.enabled = enabled;
    }

    public ProxyRoute(int id, String routePattern, String targetBaseUrl, boolean enabled) {
        this.id = id;
        this.routePattern = routePattern;
        this.targetBaseUrl = targetBaseUrl;
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

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
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
        ProxyRoute that = (ProxyRoute) o;
        return id == that.id &&
                enabled == that.enabled &&
                Objects.equals(routePattern, that.routePattern) &&
                Objects.equals(targetBaseUrl, that.targetBaseUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, routePattern, targetBaseUrl, enabled);
    }

    @Override
    public String toString() {
        return "ProxyRoute{" +
                "id=" + id +
                ", routePattern='" + routePattern + '\'' +
                ", targetBaseUrl='" + targetBaseUrl + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
