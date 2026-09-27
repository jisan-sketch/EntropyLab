package com.entropylab.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable data model representing an intercepted HTTP request/response log.
 * Threaded through the entire proxy pipeline: capture -> mock check -> chaos check -> forward -> persist.
 */
public class RequestLog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<String>>> HEADERS_TYPE_REF =
            new TypeReference<>() {};

    // Persisted fields matching the request_logs table
    private Integer id;
    private String timestamp;
    private String method;
    private String path;
    private String targetUrl;
    private Map<String, List<String>> requestHeaders;
    private byte[] requestBodyBytes;
    private Integer responseStatus;
    private Map<String, List<String>> responseHeaders;
    private byte[] responseBodyBytes;
    private long latencyMs;
    private OutcomeType outcomeType;

    // Transient timing field (not persisted to DB)
    private transient long startTimeNanos;

    public RequestLog() {
    }

    public RequestLog(String timestamp, String method, String path) {
        this.timestamp = timestamp;
        this.method = method;
        this.path = path;
    }

    /**
     * Serializes a headers map into a JSON string via Jackson.
     *
     * @param headers Map of header names to list of values.
     * @return JSON string representing the headers map, or "{}" if null/empty.
     */
    public static String serializeHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            System.err.println("[RequestLog] Failed to serialize headers: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * Deserializes a JSON string into a headers map via Jackson.
     *
     * @param json JSON string representing the headers map.
     * @return Map of header names to list of values, or an empty map if null/blank.
     */
    public static Map<String, List<String>> deserializeHeaders(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, HEADERS_TYPE_REF);
        } catch (JsonProcessingException e) {
            System.err.println("[RequestLog] Failed to deserialize headers JSON: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Decodes the request body bytes as a UTF-8 string.
     * Returns an empty string if null.
     */
    public String getRequestBodyAsString() {
        return requestBodyBytes == null ? "" : new String(requestBodyBytes, StandardCharsets.UTF_8);
    }

    /**
     * Decodes the response body bytes as a UTF-8 string.
     * Returns an empty string if null.
     */
    public String getResponseBodyAsString() {
        return responseBodyBytes == null ? "" : new String(responseBodyBytes, StandardCharsets.UTF_8);
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, List<String>> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public byte[] getRequestBodyBytes() {
        return requestBodyBytes;
    }

    public void setRequestBodyBytes(byte[] requestBodyBytes) {
        this.requestBodyBytes = requestBodyBytes;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, List<String>> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public byte[] getResponseBodyBytes() {
        return responseBodyBytes;
    }

    public void setResponseBodyBytes(byte[] responseBodyBytes) {
        this.responseBodyBytes = responseBodyBytes;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public OutcomeType getOutcomeType() {
        return outcomeType;
    }

    public void setOutcomeType(OutcomeType outcomeType) {
        this.outcomeType = outcomeType;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public void setStartTimeNanos(long startTimeNanos) {
        this.startTimeNanos = startTimeNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestLog that = (RequestLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RequestLog{" +
                "id=" + id +
                ", timestamp='" + timestamp + '\'' +
                ", method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                ", responseStatus=" + responseStatus +
                ", latencyMs=" + latencyMs +
                ", outcomeType=" + outcomeType +
                '}';
    }

    /**
     * Manual console test verifying header JSON serialization/deserialization round-trip
     * and string decoding helpers.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RequestLog Manual Test Suite");
        System.out.println("==================================================");

        // 1. Build test headers
        Map<String, List<String>> originalHeaders = new LinkedHashMap<>();
        originalHeaders.put("Content-Type", List.of("application/json"));
        originalHeaders.put("Accept", List.of("text/plain", "application/json"));
        originalHeaders.put("Authorization", List.of("Bearer secret-token-xyz"));

        // 2. Serialize headers
        String json = RequestLog.serializeHeaders(originalHeaders);
        System.out.println("[STEP 1] Serialized headers JSON:\n  " + json);

        // 3. Deserialize headers
        Map<String, List<String>> roundTripHeaders = RequestLog.deserializeHeaders(json);
        System.out.println("[STEP 2] Deserialized headers map: " + roundTripHeaders);

        // 4. Assert equality
        if (!originalHeaders.equals(roundTripHeaders)) {
            throw new AssertionError("Round-trip headers do not match original headers!");
        }
        System.out.println("  Headers round-trip test: PASS");

        // 5. Test empty/null headers
        String emptyJson = RequestLog.serializeHeaders(null);
        Map<String, List<String>> emptyMap = RequestLog.deserializeHeaders(emptyJson);
        if (!emptyMap.isEmpty()) {
            throw new AssertionError("Expected empty map for null headers");
        }
        System.out.println("  Null/empty headers test: PASS");

        // 6. Test body string decoding
        RequestLog log = new RequestLog("2026-09-26T09:45:00Z", "POST", "/api/data");
        log.setRequestBodyBytes("{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8));
        log.setResponseBodyBytes("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
        log.setOutcomeType(OutcomeType.FORWARDED);
        log.setStartTimeNanos(System.nanoTime());
        log.setLatencyMs(42);

        System.out.println("[STEP 3] Request body string: " + log.getRequestBodyAsString());
        System.out.println("[STEP 4] Response body string: " + log.getResponseBodyAsString());
        System.out.println("[STEP 5] OutcomeType: " + log.getOutcomeType());
        System.out.println("[STEP 6] StartTimeNanos: " + log.getStartTimeNanos());
        System.out.println("[STEP 7] LatencyMs: " + log.getLatencyMs());

        if (!"{\"hello\":\"world\"}".equals(log.getRequestBodyAsString())) {
            throw new AssertionError("Request body string mismatch!");
        }
        if (!"{\"status\":\"ok\"}".equals(log.getResponseBodyAsString())) {
            throw new AssertionError("Response body string mismatch!");
        }

        // Test null bodies
        log.setRequestBodyBytes(null);
        log.setResponseBodyBytes(null);
        if (!"".equals(log.getRequestBodyAsString()) || !"".equals(log.getResponseBodyAsString())) {
            throw new AssertionError("Expected empty string for null body bytes");
        }
        System.out.println("  Body string decoding test: PASS");

        System.out.println("==================================================");
        System.out.println("  All RequestLog tests PASSED!");
        System.out.println("==================================================");
    }
}
