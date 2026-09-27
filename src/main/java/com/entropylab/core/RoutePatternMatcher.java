package com.entropylab.core;

import java.util.List;

/**
 * Utility for matching incoming request paths against route patterns.
 * Supports exact matching and prefix wildcard matching (ending with "/*").
 */
public final class RoutePatternMatcher {

    private static final String WILDCARD_SUFFIX = "/*";

    private RoutePatternMatcher() {
        // Utility class; prevent instantiation
    }

    /**
     * Checks if the actual path matches the given route pattern.
     * - If pattern ends with "/*", matches if actualPath starts with the prefix before "/*".
     * - Otherwise, requires an exact match with actualPath.
     *
     * @param pattern    The configured route pattern (e.g. "/api/*" or "/api/users").
     * @param actualPath The incoming actual request path (e.g. "/api/users").
     * @return true if actualPath matches pattern, false otherwise.
     */
    public static boolean matches(String pattern, String actualPath) {
        if (pattern == null || actualPath == null) {
            return false;
        }

        if (pattern.endsWith(WILDCARD_SUFFIX)) {
            String prefix = pattern.substring(0, pattern.length() - WILDCARD_SUFFIX.length());
            return actualPath.startsWith(prefix);
        }

        return pattern.equals(actualPath);
    }

    /**
     * Finds the pattern among the provided list that has the longest matching prefix
     * for the actual path. Returns null if none match.
     *
     * @param patterns   List of candidate route patterns.
     * @param actualPath The incoming actual request path.
     * @return The pattern with the longest matching prefix, or null if none match.
     */
    public static String findLongestMatch(List<String> patterns, String actualPath) {
        if (patterns == null || actualPath == null || patterns.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        int longestPrefixLength = -1;
        boolean bestIsExact = false;

        for (String pattern : patterns) {
            if (pattern == null || !matches(pattern, actualPath)) {
                continue;
            }

            boolean isWildcard = pattern.endsWith(WILDCARD_SUFFIX);
            int prefixLength = isWildcard ? pattern.length() - WILDCARD_SUFFIX.length() : pattern.length();

            if (prefixLength > longestPrefixLength) {
                longestPrefixLength = prefixLength;
                bestMatch = pattern;
                bestIsExact = !isWildcard;
            } else if (prefixLength == longestPrefixLength) {
                // If prefix lengths tie, prioritize the more specific exact match
                if (!isWildcard && !bestIsExact) {
                    bestMatch = pattern;
                    bestIsExact = true;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Manual console test verifying matching scenarios:
     * exact match, prefix wildcard, longest-match-wins, and non-matches.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RoutePatternMatcher Console Test Suite");
        System.out.println("==================================================");

        int passed = 0;
        int total = 0;

        // 1. Exact match tests
        total++;
        boolean t1 = matches("/users", "/users");
        System.out.println("[TEST 1.1] matches(\"/users\", \"/users\") = " + t1 + " (expected: true)");
        if (t1) passed++;

        total++;
        boolean t2 = !matches("/users", "/users/123");
        System.out.println("[TEST 1.2] matches(\"/users\", \"/users/123\") = " + !t2 + " (expected: false)");
        if (t2) passed++;

        total++;
        boolean t3 = !matches("/users", "/other");
        System.out.println("[TEST 1.3] matches(\"/users\", \"/other\") = " + !t3 + " (expected: false)");
        if (t3) passed++;

        // 2. Prefix wildcard tests
        total++;
        boolean t4 = matches("/github/*", "/github/octocat");
        System.out.println("[TEST 2.1] matches(\"/github/*\", \"/github/octocat\") = " + t4 + " (expected: true)");
        if (t4) passed++;

        total++;
        boolean t5 = matches("/github/*", "/github");
        System.out.println("[TEST 2.2] matches(\"/github/*\", \"/github\") = " + t5 + " (expected: true)");
        if (t5) passed++;

        total++;
        boolean t6 = !matches("/github/*", "/gitlab/octocat");
        System.out.println("[TEST 2.3] matches(\"/github/*\", \"/gitlab/octocat\") = " + !t6 + " (expected: false)");
        if (t6) passed++;

        total++;
        boolean t7 = matches("/*", "/any/nested/path");
        System.out.println("[TEST 2.4] matches(\"/*\", \"/any/nested/path\") = " + t7 + " (expected: true)");
        if (t7) passed++;

        // 3. Longest match wins tests
        List<String> patterns = List.of("/api/*", "/api/v1/*", "/api/v1/users");

        total++;
        String m1 = findLongestMatch(patterns, "/api/v1/users");
        boolean t8 = "/api/v1/users".equals(m1);
        System.out.println("[TEST 3.1] findLongestMatch(patterns, \"/api/v1/users\") = \"" + m1 + "\" (expected: \"/api/v1/users\")");
        if (t8) passed++;

        total++;
        String m2 = findLongestMatch(patterns, "/api/v1/posts");
        boolean t9 = "/api/v1/*".equals(m2);
        System.out.println("[TEST 3.2] findLongestMatch(patterns, \"/api/v1/posts\") = \"" + m2 + "\" (expected: \"/api/v1/*\")");
        if (t9) passed++;

        total++;
        String m3 = findLongestMatch(patterns, "/api/status");
        boolean t10 = "/api/*".equals(m3);
        System.out.println("[TEST 3.3] findLongestMatch(patterns, \"/api/status\") = \"" + m3 + "\" (expected: \"/api/*\")");
        if (t10) passed++;

        // 4. Non-matches return false / null
        total++;
        String m4 = findLongestMatch(patterns, "/external/service");
        boolean t11 = (m4 == null);
        System.out.println("[TEST 4.1] findLongestMatch(patterns, \"/external/service\") = " + m4 + " (expected: null)");
        if (t11) passed++;

        total++;
        boolean t12 = !matches(null, "/users") && !matches("/users", null);
        System.out.println("[TEST 4.2] Null handling returns false = " + t12 + " (expected: true)");
        if (t12) passed++;

        total++;
        boolean t13 = (findLongestMatch(null, "/users") == null) && (findLongestMatch(List.of(), "/users") == null);
        System.out.println("[TEST 4.3] Empty/null pattern list returns null = " + t13 + " (expected: true)");
        if (t13) passed++;

        System.out.println("==================================================");
        System.out.println("Result: " + passed + "/" + total + " tests passed.");
        System.out.println("==================================================");

        if (passed != total) {
            throw new AssertionError("Some RoutePatternMatcher tests failed!");
        }
    }
}
