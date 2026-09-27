package com.entropylab.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Utility for safely parsing and pretty-printing JSON strings.
 * Attempts to parse the input as a JSON object or array; if successful,
 * returns an indented, human-readable string. If parsing fails or the input
 * is not a JSON object/array, returns the original raw string unchanged.
 */
public final class JsonPrettyPrinter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private JsonPrettyPrinter() {
        // Prevent instantiation
    }

    /**
     * Attempts to parse the raw string as JSON and returns a pretty-printed version.
     * If parsing fails or the string is blank or non-JSON, returns the original raw string unchanged.
     *
     * @param raw The raw string (e.g. HTTP request or response body).
     * @return Formatted JSON string if valid JSON, otherwise the original raw string.
     */
    public static String tryPrettyPrint(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }

        String trimmed = raw.trim();
        // Only attempt pretty printing on JSON objects or arrays
        if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
              (trimmed.startsWith("[") && trimmed.endsWith("]")))) {
            return raw;
        }

        try {
            Object jsonNode = OBJECT_MAPPER.readValue(trimmed, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            // Malformed JSON: return original raw string unchanged
            return raw;
        }
    }
}
