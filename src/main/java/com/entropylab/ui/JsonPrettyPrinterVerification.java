package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import javafx.application.Platform;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M10.5 — JSON Pretty-Print Helper.
 * Tests unit-level pretty-printing and UI integration in InspectorView.
 */
public class JsonPrettyPrinterVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   JSON Pretty-Printer Verification (M10.5)");
        System.out.println("==================================================");

        // -------------------------------------------------------------
        // PART 1: Unit Tests for JsonPrettyPrinter.tryPrettyPrint
        // -------------------------------------------------------------
        System.out.println("\n[PART 1] Running unit tests on JsonPrettyPrinter...");

        // 1. null / empty / blank
        if (JsonPrettyPrinter.tryPrettyPrint(null) != null) {
            throw new AssertionError("Expected null for null input");
        }
        if (!"".equals(JsonPrettyPrinter.tryPrettyPrint(""))) {
            throw new AssertionError("Expected empty string for empty input");
        }
        if (!"   ".equals(JsonPrettyPrinter.tryPrettyPrint("   "))) {
            throw new AssertionError("Expected blank string for blank input");
        }
        System.out.println("  1. Null / empty / blank handling: PASS");

        // 2. Compact JSON Object formatting
        String compactJson = "{\"name\":\"Alice\",\"age\":30,\"roles\":[\"admin\",\"user\"],\"active\":true}";
        String prettyJson = JsonPrettyPrinter.tryPrettyPrint(compactJson);
        if (!prettyJson.contains("\n") || !prettyJson.contains("  \"name\" : \"Alice\"")) {
            throw new AssertionError("Failed to format compact JSON object! Output:\n" + prettyJson);
        }
        System.out.println("  2. Compact JSON Object pretty-printing: PASS");

        // 3. Compact JSON Array formatting
        String compactArray = "[{\"id\":1,\"status\":\"OK\"},{\"id\":2,\"status\":\"FAIL\"}]";
        String prettyArray = JsonPrettyPrinter.tryPrettyPrint(compactArray);
        if (!prettyArray.contains("\n") || !prettyArray.contains("  \"id\" : 1")) {
            throw new AssertionError("Failed to format compact JSON array! Output:\n" + prettyArray);
        }
        System.out.println("  3. Compact JSON Array pretty-printing: PASS");

        // 4. Non-JSON plain text (must remain untouched)
        String plainText = "Hello, world! This is raw non-JSON text.";
        String returnedPlainText = JsonPrettyPrinter.tryPrettyPrint(plainText);
        if (!plainText.equals(returnedPlainText)) {
            throw new AssertionError("Plain text was modified! Got: " + returnedPlainText);
        }
        System.out.println("  4. Plain text passthrough without change: PASS");

        // 5. URL-encoded form data (must remain untouched)
        String formEncoded = "username=testuser&password=secret%21&action=login";
        String returnedForm = JsonPrettyPrinter.tryPrettyPrint(formEncoded);
        if (!formEncoded.equals(returnedForm)) {
            throw new AssertionError("Form-encoded string was modified! Got: " + returnedForm);
        }
        System.out.println("  5. Form-encoded body passthrough: PASS");

        // 6. Malformed JSON with braces (must remain untouched)
        String malformedJson = "{\"title\": \"Broken\", \"missingClosingQuote: 123";
        String returnedMalformed = JsonPrettyPrinter.tryPrettyPrint(malformedJson);
        if (!malformedJson.equals(returnedMalformed)) {
            throw new AssertionError("Malformed JSON was modified! Got: " + returnedMalformed);
        }
        System.out.println("  6. Malformed JSON passthrough: PASS");

        // 7. XML / HTML string
        String xmlContent = "<response><error code=\"500\">Internal Error</error></response>";
        String returnedXml = JsonPrettyPrinter.tryPrettyPrint(xmlContent);
        if (!xmlContent.equals(returnedXml)) {
            throw new AssertionError("XML was modified! Got: " + returnedXml);
        }
        System.out.println("  7. XML/HTML passthrough: PASS");

        // -------------------------------------------------------------
        // PART 2: UI Integration in InspectorView Detail Panel
        // -------------------------------------------------------------
        System.out.println("\n[PART 2] Testing InspectorView Detail Panel Integration...");

        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        final InspectorView[] viewRef = new InspectorView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new InspectorView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        InspectorView view = viewRef[0];

        // Seed Log A: Minified JSON request and response
        RequestLog jsonLog = new RequestLog();
        jsonLog.setTimestamp("2026-09-26T12:30:00.000Z");
        jsonLog.setMethod("POST");
        jsonLog.setPath("/api/v1/metrics");
        jsonLog.setTargetUrl("http://metrics.service.internal/v1/metrics");
        jsonLog.setRequestHeaders(Map.of("Content-Type", List.of("application/json")));
        jsonLog.setRequestBodyBytes("{\"metric\":\"cpu_load\",\"value\":92.4,\"host\":{\"name\":\"node-42\",\"region\":\"us-east\"}}".getBytes(StandardCharsets.UTF_8));
        jsonLog.setResponseStatus(200);
        jsonLog.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        jsonLog.setResponseBodyBytes("{\"status\":\"recorded\",\"retentionDays\":30}".getBytes(StandardCharsets.UTF_8));
        jsonLog.setLatencyMs(42);
        jsonLog.setOutcomeType(OutcomeType.FORWARDED);
        int jsonLogId = AppContext.getRequestLogDao().insertLog(jsonLog);
        System.out.println("  Seeded JSON Log ID: " + jsonLogId);

        // Seed Log B: Non-JSON plain text / URL-encoded request and response
        RequestLog nonJsonLog = new RequestLog();
        nonJsonLog.setTimestamp("2026-09-26T12:31:00.000Z");
        nonJsonLog.setMethod("POST");
        nonJsonLog.setPath("/api/v1/legacy");
        nonJsonLog.setTargetUrl("http://legacy.service.internal/v1/legacy");
        nonJsonLog.setRequestHeaders(Map.of("Content-Type", List.of("application/x-www-form-urlencoded")));
        String rawReqBody = "user=admin&cmd=ping&count=4";
        nonJsonLog.setRequestBodyBytes(rawReqBody.getBytes(StandardCharsets.UTF_8));
        nonJsonLog.setResponseStatus(200);
        nonJsonLog.setResponseHeaders(Map.of("Content-Type", List.of("text/plain")));
        String rawResBody = "PING reply: 4 packets transmitted, 4 received, 0% packet loss";
        nonJsonLog.setResponseBodyBytes(rawResBody.getBytes(StandardCharsets.UTF_8));
        nonJsonLog.setLatencyMs(15);
        nonJsonLog.setOutcomeType(OutcomeType.FORWARDED);
        int nonJsonLogId = AppContext.getRequestLogDao().insertLog(nonJsonLog);
        System.out.println("  Seeded Non-JSON Log ID: " + nonJsonLogId);

        // Refresh Inspector Table
        Platform.runLater(() -> view.refreshHistoricalLogs());

        // Wait for table to load
        long startTime = System.currentTimeMillis();
        while (view.getTableView().getItems().size() < 2) {
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new AssertionError("Timeout waiting for TableView items");
            }
            Thread.sleep(50);
        }

        // Find index of jsonLog and nonJsonLog
        int jsonIndex = -1;
        int nonJsonIndex = -1;
        for (int i = 0; i < view.getTableView().getItems().size(); i++) {
            RequestLog r = view.getTableView().getItems().get(i);
            if (r.getId() != null && r.getId() == jsonLogId) {
                jsonIndex = i;
            } else if (r.getId() != null && r.getId() == nonJsonLogId) {
                nonJsonIndex = i;
            }
        }

        // Select JSON Log
        System.out.println("\n  Testing selection of JSON Log...");
        final int selJson = jsonIndex;
        Platform.runLater(() -> view.getTableView().getSelectionModel().select(selJson));

        startTime = System.currentTimeMillis();
        while (!view.getRequestBodyArea().getText().contains("  \"metric\" : \"cpu_load\"")) {
            if (System.currentTimeMillis() - startTime > 4000) {
                throw new AssertionError("Timeout waiting for pretty-printed JSON request body! Got:\n" + view.getRequestBodyArea().getText());
            }
            Thread.sleep(50);
        }
        System.out.println("  Verified Request Body: formatted and indented with newlines");

        if (!view.getResponseBodyArea().getText().contains("  \"status\" : \"recorded\"")) {
            throw new AssertionError("Response body was not pretty-printed! Got:\n" + view.getResponseBodyArea().getText());
        }
        System.out.println("  Verified Response Body: formatted and indented with newlines");

        // Select Non-JSON Log
        System.out.println("\n  Testing selection of Non-JSON Log...");
        final int selNonJson = nonJsonIndex;
        Platform.runLater(() -> view.getTableView().getSelectionModel().select(selNonJson));

        startTime = System.currentTimeMillis();
        while (!view.getRequestBodyArea().getText().equals(rawReqBody)) {
            if (System.currentTimeMillis() - startTime > 4000) {
                throw new AssertionError("Timeout waiting for non-JSON request body! Got:\n" + view.getRequestBodyArea().getText());
            }
            Thread.sleep(50);
        }
        System.out.println("  Verified Non-JSON Request Body: unchanged raw content ('" + rawReqBody + "')");

        if (!view.getResponseBodyArea().getText().equals(rawResBody)) {
            throw new AssertionError("Non-JSON response body was unexpectedly altered! Got:\n" + view.getResponseBodyArea().getText());
        }
        System.out.println("  Verified Non-JSON Response Body: unchanged raw content ('" + rawResBody + "')");

        System.out.println("\n==================================================");
        System.out.println("   All M10.5 JSON Pretty-Printer tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
