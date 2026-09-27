package com.entropylab.mock;

/**
 * Indicates how a mock route was created.
 */
public enum MockSource {
    /**
     * Created manually by the user.
     */
    MANUAL,

    /**
     * Automatically generated from a recorded request/response snapshot.
     */
    AUTO_SNAPSHOT;

    /**
     * Safely parses a string into a MockSource, defaulting to MANUAL if null or unrecognized.
     *
     * @param value String name to parse.
     * @return Corresponding MockSource or MANUAL.
     */
    public static MockSource fromString(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        try {
            return MockSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MANUAL;
        }
    }
}
