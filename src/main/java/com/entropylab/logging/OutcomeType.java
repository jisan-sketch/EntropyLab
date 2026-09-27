package com.entropylab.logging;

/**
 * Outcome classifications for intercepted requests in EntropyLab.
 */
public enum OutcomeType {
    /**
     * Successfully proxied and forwarded to the real target URL.
     */
    FORWARDED,

    /**
     * Served directly from a local static mock file.
     */
    MOCKED,

    /**
     * Short-circuited by a chaos rule returning a synthetic status code.
     */
    CHAOS_STATUS,

    /**
     * Short-circuited by a chaos rule hard-closing the connection without headers.
     */
    CHAOS_RESET,

    /**
     * No proxy route or mock matched the requested path.
     */
    NOT_FOUND,

    /**
     * An unexpected failure occurred during forwarding or mock reading.
     */
    ERROR
}
