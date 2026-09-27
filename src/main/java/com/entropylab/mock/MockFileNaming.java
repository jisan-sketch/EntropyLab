package com.entropylab.mock;

/**
 * Pure utility class for generating filesystem-safe, unique JSON mock filenames.
 */
public final class MockFileNaming {

    private static long lastTimestamp = 0;

    private MockFileNaming() {
        // Prevent instantiation
    }

    /**
     * Generates a filesystem-safe mock filename based on the route path and current timestamp.
     * Replaces every character that isn't a letter, digit, or underscore with an underscore,
     * trims leading and trailing underscores, and appends an epoch millisecond timestamp and .json.
     *
     * Example: "/github/users/octocat" -> "github_users_octocat_1718000000000.json"
     *
     * Guarantees unique filenames across rapid invocations within the same millisecond.
     *
     * @param routePath The request or route path (e.g. "/api/v1/users", "/items?category=books&limit=10").
     * @return A filesystem-safe, readable filename ending in .json.
     */
    public static String generateFileName(String routePath) {
        String baseName;
        if (routePath == null || routePath.isBlank()) {
            baseName = "mock";
        } else {
            // Replace every character that isn't a letter, digit, or underscore with '_'
            String sanitized = routePath.replaceAll("[^a-zA-Z0-9_]", "_");
            // Collapse multiple consecutive underscores into a single underscore for clean readability
            sanitized = sanitized.replaceAll("_+", "_");
            // Trim leading and trailing underscores
            sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
            baseName = sanitized.isEmpty() ? "root" : sanitized;
        }

        long epochMillis = getUniqueEpochMillis();
        return baseName + "_" + epochMillis + ".json";
    }

    /**
     * Returns a monotonically increasing timestamp to guarantee uniqueness across rapid calls.
     */
    private static synchronized long getUniqueEpochMillis() {
        long now = System.currentTimeMillis();
        if (now <= lastTimestamp) {
            now = lastTimestamp + 1;
        }
        lastTimestamp = now;
        return now;
    }
}
