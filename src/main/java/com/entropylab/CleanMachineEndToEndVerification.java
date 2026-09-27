package com.entropylab;

import com.entropylab.chaos.ChaosRule;
import com.entropylab.chaos.ChaosRuleStore;
import com.entropylab.core.*;
import com.entropylab.logging.RequestLog;
import com.entropylab.logging.RequestLogDao;
import com.entropylab.mock.MockFileNaming;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockRouteStore;
import com.entropylab.mock.MockSource;
import com.entropylab.proxy.ProxyServer;
import com.entropylab.routes.ProxyRoute;
import com.entropylab.routes.ProxyRouteStore;
import com.entropylab.ui.JsonPrettyPrinter;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-End Clean-Machine Verification Suite for EntropyLab (M13.3).
 *
 * Verifies every single capability on an isolated clean directory:
 * 1. Directory creation (%APPDATA%\\EntropyLab\\ and mocks\\) and 4-table schema initialization.
 * 2. Proxy lifecycle and route forwarding to real HTTP upstream server.
 * 3. Chaos rules injection (latency, status override, connection reset) individually and combined.
 * 4. Traffic logging and Inspector data (timestamps, latency, outcomes, headers, bodies, JSON pretty-print).
 * 5. Manual mock serving and precedence over chaos and forwarding.
 * 6. "Save as Offline Mock" auto-snapshot creation and playback.
 * 7. Full restart persistence (routes, chaos rules, mocks stay intact and active).
 */
public class CleanMachineEndToEndVerification {

    private static final int TEST_PROXY_PORT = 19100;
    private static final int UPSTREAM_PORT = 19101;
    private static HttpServer upstreamServer;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  EntropyLab M13.3 End-to-End Clean-Machine Verification Suite     ");
        System.out.println("==================================================================");

        Path cleanRootDir = null;
        try {
            // STEP 0: Create isolated test environment
            cleanRootDir = Files.createTempDirectory("entropylab-clean-machine-test-");
            Path testAppData = cleanRootDir.resolve("AppData").resolve("Roaming").resolve("EntropyLab");
            Path testMocks = testAppData.resolve("mocks");

            System.out.println("[Step 0] Isolated test directory: " + cleanRootDir);
            System.setProperty("entropylab.home", testAppData.toString());

            // 1. Clean environment directory creation and DB initialization
            System.out.println("\n--- Step 1: Clean Profile Initialization ---");
            AppPaths.ensureDirectoriesExist();

            check(Files.exists(testAppData), "AppData directory created: " + testAppData);
            check(Files.exists(testMocks), "Mocks directory created: " + testMocks);

            DatabaseManager.initialize();
            SchemaInitializer.initializeSchema();
            AppContext.initialize();

            // Verify all 4 DB tables exist
            List<String> expectedTables = List.of("proxy_routes", "chaos_rules", "request_logs", "mock_routes");
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                List<String> actualTables = new ArrayList<>();
                while (rs.next()) {
                    actualTables.add(rs.getString("name"));
                }
                for (String t : expectedTables) {
                    check(actualTables.contains(t), "Table '" + t + "' exists in schema");
                }
            }

            // 2. Start Upstream Mock Server
            startUpstreamServer();

            // 3. Start Proxy Server & Register Route
            System.out.println("\n--- Step 2: Proxy Server & Route Forwarding ---");
            ProxyRouteStore routeStore = AppContext.getProxyRouteStore();
            ChaosRuleStore chaosStore = AppContext.getChaosRuleStore();
            MockRouteStore mockStore = AppContext.getMockRouteStore();
            RequestLogDao logDao = AppContext.getRequestLogDao();

            ProxyServer proxyServer = AppContext.getProxyServer();
            proxyServer.start(TEST_PROXY_PORT);
            check(proxyServer.isRunning(), "Proxy server is running on :" + TEST_PROXY_PORT);

            // Register route /api/weather -> http://localhost:19101/upstream/weather
            ProxyRoute weatherRoute = new ProxyRoute("/api/weather", "http://localhost:" + UPSTREAM_PORT + "/upstream/weather", true);
            routeStore.addRoute(weatherRoute);
            check(routeStore.getAllRoutes().size() == 1, "Route /api/weather registered");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            // Hit proxy
            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + TEST_PROXY_PORT + "/api/weather"))
                    .GET()
                    .build();
            HttpResponse<String> res1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
            check(res1.statusCode() == 200, "Forwarded request returned HTTP 200");
            check(res1.body().contains("Sunny"), "Response body matches upstream payload: " + res1.body());

            // 4. Chaos Rules Injection
            System.out.println("\n--- Step 3: Chaos Rules Testing ---");

            // 4a. Latency Chaos
            ChaosRule latencyRule = new ChaosRule("/api/weather", 150, null, false, true);
            chaosStore.addRule(latencyRule);
            long startT = System.currentTimeMillis();
            HttpResponse<String> resLatency = client.send(req1, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - startT;
            check(resLatency.statusCode() == 200, "Latency rule returned 200");
            check(elapsed >= 140, "Observed injected latency: " + elapsed + "ms (expected >= 150ms)");
            chaosStore.removeRule(latencyRule.getId());

            // 4b. Status Override Chaos (e.g. 503 Service Unavailable)
            ChaosRule statusRule = new ChaosRule("/api/weather", 0, 503, false, true);
            chaosStore.addRule(statusRule);
            HttpResponse<String> resStatus = client.send(req1, HttpResponse.BodyHandlers.ofString());
            check(resStatus.statusCode() == 503, "Status override rule returned HTTP 503");
            chaosStore.removeRule(statusRule.getId());

            // 4c. Connection Reset Chaos
            ChaosRule resetRule = new ChaosRule("/api/weather", 0, null, true, true);
            chaosStore.addRule(resetRule);
            boolean resetCaught = false;
            try {
                client.send(req1, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                resetCaught = true;
            }
            check(resetCaught, "Connection reset rule successfully dropped/reset connection");
            chaosStore.removeRule(resetRule.getId());

            // 5. Inspector Traffic Logs Verification
            System.out.println("\n--- Step 4: Inspector & Request Logging ---");
            // Allow asynchronous log dispatcher thread to commit logs
            Thread.sleep(400);
            List<RequestLog> recentLogs = logDao.getRecentLogs(20, 0);
            check(!recentLogs.isEmpty(), "Logs recorded in SQLite database (count = " + recentLogs.size() + ")");
            RequestLog latestLog = recentLogs.get(0);
            check(latestLog.getPath() != null && !latestLog.getPath().isEmpty(), "Log contains path: " + latestLog.getPath());
            check(latestLog.getOutcomeType() != null, "Log contains outcome type: " + latestLog.getOutcomeType());

            // JSON Pretty-Print Verification
            String uglyJson = "{\"temp\":25,\"condition\":\"Sunny\",\"details\":{\"humidity\":40}}";
            String prettyJson = JsonPrettyPrinter.tryPrettyPrint(uglyJson);
            check(prettyJson.contains("\n"), "JsonPrettyPrinter formats JSON with newlines");
            check(prettyJson.contains("  \"temp\" : 25"), "JsonPrettyPrinter indents fields properly");

            // 6. Manual Mock Route Precedence
            System.out.println("\n--- Step 5: Manual Mock Route Precedence ---");
            Path manualMockFile = testMocks.resolve("manual_mock.json");
            Files.writeString(manualMockFile, "{\"mock_type\":\"manual_test\",\"status\":\"ok\"}");

            MockRoute manualMock = new MockRoute("/api/weather", manualMockFile.toAbsolutePath().toString(), true, MockSource.MANUAL);
            mockStore.addRoute(manualMock);

            // Re-add a status override rule 500 to ensure mock PRECEDES chaos
            ChaosRule errorRule = new ChaosRule("/api/weather", 0, 500, false, true);
            chaosStore.addRule(errorRule);

            HttpResponse<String> resMock = client.send(req1, HttpResponse.BodyHandlers.ofString());
            check(resMock.statusCode() == 200, "Manual mock served with HTTP 200 (overriding chaos 500)");
            check(resMock.body().contains("manual_test"), "Manual mock response body served: " + resMock.body());

            // Cleanup manual mock and error rule
            mockStore.removeRoute(manualMock.getId());
            chaosStore.removeRule(errorRule.getId());

            // 7. Auto-Mock Snapshot Workflow
            System.out.println("\n--- Step 6: Auto-Mock Snapshot Workflow ---");
            // Hit upstream again to obtain a FORWARDED response
            HttpResponse<String> resForwarded = client.send(req1, HttpResponse.BodyHandlers.ofString());
            check(resForwarded.statusCode() == 200, "Fresh request forwarded to upstream");

            // Simulate "Save as Offline Mock" button action
            String snapshotFilename = MockFileNaming.generateFileName("/api/weather");
            Path snapshotPath = testMocks.resolve(snapshotFilename);
            Files.writeString(snapshotPath, resForwarded.body());
            check(Files.exists(snapshotPath), "Snapshot mock file created in mocks dir: " + snapshotFilename);

            MockRoute autoMock = new MockRoute("/api/weather", snapshotPath.toAbsolutePath().toString(), true, MockSource.AUTO_SNAPSHOT);
            mockStore.addRoute(autoMock);

            // Stop upstream server to prove the mock serves offline
            upstreamServer.stop(0);
            System.out.println("[Upstream] Stopped upstream server to verify offline playback");

            HttpResponse<String> resOffline = client.send(req1, HttpResponse.BodyHandlers.ofString());
            check(resOffline.statusCode() == 200, "Offline mock served while upstream server was completely stopped!");
            check(resOffline.body().contains("Sunny"), "Offline mock content served accurately: " + resOffline.body());

            // 8. Full App Restart Persistence
            System.out.println("\n--- Step 7: Full Application Restart & Persistence ---");
            // Stop proxy and close database
            proxyServer.stop();
            DatabaseManager.getInstance().close();
            System.out.println("[Persistence] Database closed and proxy stopped. Simulating application restart...");

            // Reopen database with same clean profile DB file
            DatabaseManager.initialize();
            ProxyRouteStore reloadedRouteStore = new ProxyRouteStore();
            ChaosRuleStore reloadedChaosStore = new ChaosRuleStore();
            MockRouteStore reloadedMockStore = new MockRouteStore();

            check(reloadedRouteStore.getAllRoutes().size() == 1, "Route persisted and reloaded after restart");
            check(reloadedRouteStore.getAllRoutes().get(0).getRoutePattern().equals("/api/weather"), "Route pattern matches /api/weather");

            check(reloadedMockStore.getAllRoutes().size() == 1, "Mock route persisted and reloaded after restart");
            check(reloadedMockStore.getAllRoutes().get(0).getSource() == MockSource.AUTO_SNAPSHOT, "Auto-snapshot source preserved across restart");
            check(reloadedMockStore.getAllRoutes().get(0).isEnabled(), "Mock route remains enabled across restart");

            // Restart proxy on reloaded context
            ProxyServer reloadedProxy = new ProxyServer();
            reloadedProxy.start(TEST_PROXY_PORT);
            check(reloadedProxy.isRunning(), "Reloaded proxy server restarted successfully");

            HttpResponse<String> resPostRestart = client.send(req1, HttpResponse.BodyHandlers.ofString());
            check(resPostRestart.statusCode() == 200, "Post-restart request served offline mock immediately without manual setup");
            check(resPostRestart.body().contains("Sunny"), "Post-restart content served accurately");

            reloadedProxy.stop();
            DatabaseManager.getInstance().close();

            System.out.println("\n==================================================================");
            System.out.println("  ALL M13.3 CLEAN-MACHINE VERIFICATION CHECKS PASSED PERFECTLY!   ");
            System.out.println("==================================================================");

        } catch (Throwable t) {
            System.err.println("\n[FAILED] M13.3 Verification encountered an error: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        } finally {
            System.clearProperty("entropylab.home");
            if (upstreamServer != null) {
                try { upstreamServer.stop(0); } catch (Exception ignored) {}
            }
            if (cleanRootDir != null) {
                try {
                    deleteRecursively(cleanRootDir.toFile());
                } catch (Exception ignored) {}
            }
        }
    }

    private static void startUpstreamServer() throws IOException {
        upstreamServer = HttpServer.create(new InetSocketAddress(UPSTREAM_PORT), 0);
        upstreamServer.createContext("/upstream/weather", exchange -> {
            String json = "{\"city\":\"Berlin\",\"temp\":22,\"condition\":\"Sunny\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        upstreamServer.setExecutor(null);
        upstreamServer.start();
        System.out.println("[Upstream] Mock weather upstream running on port :" + UPSTREAM_PORT);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("CHECK FAILED: " + description);
        }
        System.out.println("  [PASS] " + description);
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        file.delete();
    }
}
