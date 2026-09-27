package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.routes.ProxyRoute;
import com.entropylab.proxy.ProxyServer;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M10.4 — Wire Detail Panel to Selection.
 * Tests row selection, background database fetching, detail population,
 * selection clearing, and live proxy traffic selection.
 */
public class InspectorDetailSelectionVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Inspector Detail Panel Selection (M10.4)");
        System.out.println("==================================================");

        // 1. Bootstrap backend environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);
        AppContext.getRequestLogEventDispatcher().addListener(new UiUpdateBridge());

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

        // Clear existing table and prepare fresh state
        AppContext.getInspectorTableModel().clear();

        // 2. Insert two distinct RequestLogs directly into SQLite
        System.out.println("\n[STEP 1] Seeding database with distinct RequestLogs...");
        RequestLog log1 = new RequestLog();
        log1.setTimestamp("2026-09-26T12:00:00.000Z");
        log1.setMethod("POST");
        log1.setPath("/api/v1/orders");
        log1.setTargetUrl("http://orders-backend.internal/orders");
        Map<String, List<String>> reqHeaders1 = new LinkedHashMap<>();
        reqHeaders1.put("Content-Type", List.of("application/json"));
        reqHeaders1.put("X-Customer-Id", List.of("cust-9876"));
        log1.setRequestHeaders(reqHeaders1);
        log1.setRequestBodyBytes("{\"item\": \"Quantum Sensor\", \"qty\": 3}".getBytes(StandardCharsets.UTF_8));
        log1.setResponseStatus(201);
        Map<String, List<String>> resHeaders1 = new LinkedHashMap<>();
        resHeaders1.put("Content-Type", List.of("application/json"));
        resHeaders1.put("X-Order-Id", List.of("ord-5555"));
        log1.setResponseHeaders(resHeaders1);
        log1.setResponseBodyBytes("{\"status\": \"CREATED\", \"orderId\": \"ord-5555\"}".getBytes(StandardCharsets.UTF_8));
        log1.setLatencyMs(88);
        log1.setOutcomeType(OutcomeType.FORWARDED);

        int id1 = AppContext.getRequestLogDao().insertLog(log1);
        System.out.println("  Seeded Log 1 ID: " + id1 + " (" + log1.getMethod() + " " + log1.getPath() + ")");

        RequestLog log2 = new RequestLog();
        log2.setTimestamp("2026-09-26T12:01:00.000Z");
        log2.setMethod("GET");
        log2.setPath("/unmapped/resource");
        log2.setTargetUrl(null);
        Map<String, List<String>> reqHeaders2 = new LinkedHashMap<>();
        reqHeaders2.put("User-Agent", List.of("SyntheticProbe/1.0"));
        log2.setRequestHeaders(reqHeaders2);
        log2.setRequestBodyBytes(null);
        log2.setResponseStatus(404);
        Map<String, List<String>> resHeaders2 = new LinkedHashMap<>();
        resHeaders2.put("Content-Type", List.of("text/plain"));
        log2.setResponseHeaders(resHeaders2);
        log2.setResponseBodyBytes("Route Not Found".getBytes(StandardCharsets.UTF_8));
        log2.setLatencyMs(4);
        log2.setOutcomeType(OutcomeType.NOT_FOUND);

        int id2 = AppContext.getRequestLogDao().insertLog(log2);
        System.out.println("  Seeded Log 2 ID: " + id2 + " (" + log2.getMethod() + " " + log2.getPath() + ")");

        // 3. Load historical logs into InspectorView
        System.out.println("\n[STEP 2] Loading logs into Inspector TableView...");
        Platform.runLater(() -> view.refreshHistoricalLogs());

        // Wait until table has at least 2 rows
        long startTime = System.currentTimeMillis();
        while (view.getTableView().getItems().size() < 2) {
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new AssertionError("Timeout waiting for TableView to populate with historical logs");
            }
            Thread.sleep(50);
        }
        System.out.println("  TableView loaded with " + view.getTableView().getItems().size() + " rows");

        // Find indices of log1 and log2 in table (newest is first, so log2 is at index 0, log1 at index 1)
        int indexLog1 = -1;
        int indexLog2 = -1;
        for (int i = 0; i < view.getTableView().getItems().size(); i++) {
            RequestLog r = view.getTableView().getItems().get(i);
            if (r.getId() != null && r.getId() == id1) {
                indexLog1 = i;
            } else if (r.getId() != null && r.getId() == id2) {
                indexLog2 = i;
            }
        }
        if (indexLog1 == -1 || indexLog2 == -1) {
            throw new AssertionError("Could not locate seeded log rows in TableView!");
        }

        // 4. Select Log 1 row and verify detail fields populated from background fetch
        System.out.println("\n[STEP 3] Selecting Log 1 row (id=" + id1 + ")...");
        final int selIndex1 = indexLog1;
        Platform.runLater(() -> view.getTableView().getSelectionModel().select(selIndex1));

        // Poll detail panel until target URL matches log1
        startTime = System.currentTimeMillis();
        while (!view.getTargetUrlLabel().getText().contains("orders-backend.internal")) {
            if (System.currentTimeMillis() - startTime > 4000) {
                throw new AssertionError("Timeout waiting for Log 1 details to populate! Text: " + view.getTargetUrlLabel().getText());
            }
            Thread.sleep(50);
        }

        System.out.println("  Verified Target URL: " + view.getTargetUrlLabel().getText());
        if (!view.getLatencyLabel().getText().contains("88 ms")) {
            throw new AssertionError("Expected latency '88 ms', got: " + view.getLatencyLabel().getText());
        }
        System.out.println("  Verified Latency: " + view.getLatencyLabel().getText());

        if (!view.getOutcomeLabel().getText().contains("FORWARDED")) {
            throw new AssertionError("Expected outcome 'FORWARDED', got: " + view.getOutcomeLabel().getText());
        }
        System.out.println("  Verified Outcome: " + view.getOutcomeLabel().getText());

        if (!view.getRequestHeadersArea().getText().contains("X-Customer-Id: cust-9876")) {
            throw new AssertionError("Request headers missing expected header! Got:\n" + view.getRequestHeadersArea().getText());
        }
        System.out.println("  Verified Request Headers: contains 'X-Customer-Id: cust-9876'");

        if (!view.getRequestBodyArea().getText().contains("Quantum Sensor")) {
            throw new AssertionError("Request body missing expected content! Got:\n" + view.getRequestBodyArea().getText());
        }
        System.out.println("  Verified Request Body: contains 'Quantum Sensor'");

        if (!view.getResponseHeadersArea().getText().contains("X-Order-Id: ord-5555")) {
            throw new AssertionError("Response headers missing expected header! Got:\n" + view.getResponseHeadersArea().getText());
        }
        System.out.println("  Verified Response Headers: contains 'X-Order-Id: ord-5555'");

        if (!view.getResponseBodyArea().getText().contains("ord-5555")) {
            throw new AssertionError("Response body missing expected content! Got:\n" + view.getResponseBodyArea().getText());
        }
        System.out.println("  Verified Response Body: contains 'ord-5555'");

        // 5. Select Log 2 row and verify detail fields change
        System.out.println("\n[STEP 4] Selecting Log 2 row (id=" + id2 + ")...");
        final int selIndex2 = indexLog2;
        Platform.runLater(() -> view.getTableView().getSelectionModel().select(selIndex2));

        // Poll detail panel until outcome matches NOT_FOUND
        startTime = System.currentTimeMillis();
        while (!view.getOutcomeLabel().getText().contains("NOT_FOUND")) {
            if (System.currentTimeMillis() - startTime > 4000) {
                throw new AssertionError("Timeout waiting for Log 2 details to populate! Outcome: " + view.getOutcomeLabel().getText());
            }
            Thread.sleep(50);
        }

        System.out.println("  Verified Target URL: " + view.getTargetUrlLabel().getText());
        if (!view.getTargetUrlLabel().getText().contains("-")) {
            throw new AssertionError("Expected '-' for unmapped target URL, got: " + view.getTargetUrlLabel().getText());
        }

        if (!view.getLatencyLabel().getText().contains("4 ms")) {
            throw new AssertionError("Expected latency '4 ms', got: " + view.getLatencyLabel().getText());
        }
        System.out.println("  Verified Latency: " + view.getLatencyLabel().getText());

        if (!view.getRequestHeadersArea().getText().contains("User-Agent: SyntheticProbe/1.0")) {
            throw new AssertionError("Expected User-Agent in request headers! Got:\n" + view.getRequestHeadersArea().getText());
        }
        System.out.println("  Verified Request Headers: contains 'User-Agent: SyntheticProbe/1.0'");

        if (!view.getRequestBodyArea().getText().isEmpty()) {
            throw new AssertionError("Expected empty request body for GET! Got:\n" + view.getRequestBodyArea().getText());
        }
        System.out.println("  Verified Request Body: empty");

        if (!view.getResponseBodyArea().getText().contains("Route Not Found")) {
            throw new AssertionError("Expected response body 'Route Not Found'! Got:\n" + view.getResponseBodyArea().getText());
        }
        System.out.println("  Verified Response Body: contains 'Route Not Found'");

        // 6. Test clearSelection() resets fields
        System.out.println("\n[STEP 5] Clearing selection and verifying detail panel resets...");
        CountDownLatch clearLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().clearSelection();
            clearLatch.countDown();
        });
        if (!clearLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timeout waiting for clearSelection to dispatch!");
        }

        startTime = System.currentTimeMillis();
        while (!view.getOutcomeLabel().getText().equals("Outcome: -")) {
            if (System.currentTimeMillis() - startTime > 4000) {
                throw new AssertionError("Timeout waiting for detail panel to reset upon clearSelection! Outcome: " + view.getOutcomeLabel().getText());
            }
            Thread.sleep(50);
        }

        if (!view.getTargetUrlLabel().getText().equals("Target URL: -") ||
                !view.getLatencyLabel().getText().equals("Latency: -") ||
                !view.getOutcomeLabel().getText().equals("Outcome: -") ||
                !view.getRequestHeadersArea().getText().isEmpty() ||
                !view.getRequestBodyArea().getText().isEmpty() ||
                !view.getResponseHeadersArea().getText().isEmpty() ||
                !view.getResponseBodyArea().getText().isEmpty()) {
            throw new AssertionError("Detail panel controls were not completely cleared! Outcome=" + view.getOutcomeLabel().getText()
                    + " Latency=" + view.getLatencyLabel().getText()
                    + " Target=" + view.getTargetUrlLabel().getText());
        }
        System.out.println("  Result: PASS (Selection cleared, all 7 detail fields reset to default empty/dash)");

        // 7. Test live proxy request end-to-end selection
        System.out.println("\n[STEP 6] Testing live proxy request -> selection -> detail panel...");
        int proxyPort = 18991;
        int upstreamPort = 18992;

        HttpServer upstreamServer = HttpServer.create(new InetSocketAddress("localhost", upstreamPort), 0);
        upstreamServer.createContext("/", exchange -> {
            byte[] responseBytes = "{\"upstreamStatus\": \"live_ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Upstream-Server", "MockCluster-01");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        upstreamServer.start();

        ProxyServer proxyServer = AppContext.getProxyServer();
        proxyServer.start(proxyPort);

        ProxyRoute liveRoute = new ProxyRoute("/live/test", "http://localhost:" + upstreamPort, true);
        AppContext.getProxyRouteStore().addRoute(liveRoute);

        // Send request through live proxy
        URL liveUrl = new URL("http://localhost:" + proxyPort + "/live/test");
        HttpURLConnection conn = (HttpURLConnection) liveUrl.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Live-Token", "token-xyz-789");
        byte[] livePayload = "{\"ping\": \"real_traffic_packet\"}".getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(livePayload);
        }

        int respCode = conn.getResponseCode();
        if (respCode != 200) {
            throw new AssertionError("Expected 200 from live proxy, got: " + respCode);
        }
        conn.disconnect();

        // Wait for live event bridge to prepend request into TableView
        startTime = System.currentTimeMillis();
        RequestLog topLog = null;
        while (true) {
            if (!view.getTableView().getItems().isEmpty()) {
                RequestLog cand = view.getTableView().getItems().get(0);
                if ("/live/test".equals(cand.getPath())) {
                    topLog = cand;
                    break;
                }
            }
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new AssertionError("Live request did not appear at top of Inspector TableView!");
            }
            Thread.sleep(50);
        }
        System.out.println("  Live request captured in TableView at index 0 (ID=" + topLog.getId() + ")");

        // Select the live row
        Platform.runLater(() -> view.getTableView().getSelectionModel().select(0));

        // Poll detail panel until live details populate
        startTime = System.currentTimeMillis();
        while (!view.getRequestBodyArea().getText().contains("real_traffic_packet")) {
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new AssertionError("Live request details failed to populate in detail panel! Body text: " + view.getRequestBodyArea().getText());
            }
            Thread.sleep(50);
        }

        System.out.println("  Verified live Target URL: " + view.getTargetUrlLabel().getText());
        if (!view.getTargetUrlLabel().getText().contains("localhost:" + upstreamPort)) {
            throw new AssertionError("Expected live Target URL to contain upstream port, got: " + view.getTargetUrlLabel().getText());
        }

        if (!view.getRequestHeadersArea().getText().toLowerCase().contains("x-live-token: token-xyz-789")) {
            throw new AssertionError("Expected live request headers to contain X-Live-Token! Got:\n" + view.getRequestHeadersArea().getText());
        }
        System.out.println("  Verified live Request Headers: contains 'X-Live-Token: token-xyz-789'");

        if (!view.getResponseHeadersArea().getText().toLowerCase().contains("x-upstream-server: mockcluster-01")) {
            throw new AssertionError("Expected live response headers to contain X-Upstream-Server! Got:\n" + view.getResponseHeadersArea().getText());
        }
        System.out.println("  Verified live Response Headers: contains 'X-Upstream-Server: MockCluster-01'");

        if (!view.getResponseBodyArea().getText().contains("live_ok")) {
            throw new AssertionError("Expected live response body to contain 'live_ok'! Got:\n" + view.getResponseBodyArea().getText());
        }
        System.out.println("  Verified live Response Body: contains 'live_ok'");

        System.out.println("  Result: PASS (Live proxy request selected and detail panel populated end-to-end)");

        // Cleanup
        proxyServer.stop();
        upstreamServer.stop(0);

        System.out.println("\n==================================================");
        System.out.println("   All M10.4 Detail Panel Selection tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
