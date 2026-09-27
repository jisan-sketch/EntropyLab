package com.entropylab.ui;

import com.entropylab.chaos.ChaosRule;
import com.entropylab.chaos.ChaosRuleDao;
import com.entropylab.chaos.ChaosRuleStore;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.routes.ProxyRoute;
import com.entropylab.routes.ProxyRouteStore;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verification test for M9.2 Wire Chaos Rules Tab to ChaosRuleStore.
 * Tests live proxy traffic mutation without restart and SQLite persistence.
 */
public class ChaosRulesViewWiringVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println(" Chaos Rules Tab Wiring Verification Test (M9.2)");
        System.out.println("==================================================");

        // 1. Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // Clear existing routes and rules
        ChaosRuleStore chaosStore = AppContext.getChaosRuleStore();
        for (ChaosRule r : chaosStore.getAllRules()) {
            chaosStore.removeRule(r.getId());
        }

        ProxyRouteStore routeStore = AppContext.getProxyRouteStore();
        for (ProxyRoute r : routeStore.getAllRoutes()) {
            routeStore.removeRoute(r.getId());
        }

        // Start mock upstream target server on port 9096
        int targetPort = 9096;
        HttpServer targetServer = HttpServer.create(new InetSocketAddress(targetPort), 0);
        targetServer.createContext("/", exchange -> {
            byte[] resp = "{\"data\": \"upstream-ok\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        targetServer.start();
        System.out.println("[SETUP] Target upstream server started on port " + targetPort);

        // Add base proxy route: /api/* -> targetPort
        routeStore.addRoute(new ProxyRoute("/api/*", "http://localhost:" + targetPort, true));

        // Start live ProxyServer on port 8093
        int proxyPort = 8093;
        AppContext.getProxyServer().start(proxyPort);
        System.out.println("[SETUP] ProxyServer started on port " + proxyPort);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + proxyPort + "/api/test"))
                .GET()
                .build();

        // Check normal traffic before chaos
        HttpResponse<String> normalResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (normalResp.statusCode() != 200 || !normalResp.body().contains("upstream-ok")) {
            throw new AssertionError("Normal traffic failed before chaos injection: " + normalResp.statusCode());
        }
        System.out.println("[SETUP] Normal forwarding verified (Status: 200 OK)");

        // 2. Initialize JavaFX & ChaosRulesView
        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        final ChaosRulesView[] viewRef = new ChaosRulesView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new ChaosRulesView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        ChaosRulesView view = viewRef[0];

        if (view.getRulesList().size() != 0) {
            throw new AssertionError("Expected initial rules list to be empty!");
        }
        System.out.println("\n[STEP 1] ChaosRulesView loaded empty state from store - PASS");

        // 3. Add Latency Rule via GUI
        System.out.println("\n[STEP 2] Adding Latency rule (/api/*, 500ms) via GUI...");
        CountDownLatch addLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/*");
            view.getLatencyField().setText("500");
            view.handleAddRule();
            addLatch.countDown();
        });
        addLatch.await(3, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 1 || AppContext.getChaosRuleStore().getAllRules().size() != 1) {
            throw new AssertionError("Rule was not added to list/store!");
        }

        long start = System.currentTimeMillis();
        HttpResponse<String> latencyResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  Status: " + latencyResp.statusCode() + " elapsed: " + elapsed + "ms");
        if (elapsed < 450) {
            throw new AssertionError("Expected latency >= 450ms, but request took: " + elapsed + "ms");
        }
        if (latencyResp.statusCode() != 200) {
            throw new AssertionError("Expected 200 OK with latency, got: " + latencyResp.statusCode());
        }
        System.out.println("  Result: PASS (Live proxy immediately reflected latency rule without restart)");

        // 4. Edit Rule via GUI: Status Override 503
        System.out.println("\n[STEP 3] Editing rule via GUI to Status Override 503 (0 latency)...");
        CountDownLatch editLatch1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.getLatencyField().setText("0");
            view.getStatusOverrideField().setText("503");
            view.handleSaveChanges();
            editLatch1.countDown();
        });
        editLatch1.await(3, TimeUnit.SECONDS);

        HttpResponse<String> overrideResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Status: " + overrideResp.statusCode() + " Body: " + overrideResp.body());
        if (overrideResp.statusCode() != 503 || !overrideResp.body().contains("Simulated failure")) {
            throw new AssertionError("Expected 503 status override response, got: " + overrideResp.statusCode());
        }
        System.out.println("  Result: PASS (Live proxy immediately reflected status override rule)");

        // 5. Edit Rule via GUI: Connection Reset
        System.out.println("\n[STEP 4] Editing rule via GUI to Connection Reset Enabled...");
        CountDownLatch editLatch2 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.getStatusOverrideField().clear();
            view.getResetEnabledCheckBox().setSelected(true);
            view.handleSaveChanges();
            editLatch2.countDown();
        });
        editLatch2.await(3, TimeUnit.SECONDS);

        boolean connectionResetCaught = false;
        try {
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            connectionResetCaught = true;
            System.out.println("  Caught expected reset exception: " + e);
        }
        if (!connectionResetCaught) {
            throw new AssertionError("Expected IOException from abrupt connection reset!");
        }
        System.out.println("  Result: PASS (Live proxy immediately triggered abrupt connection reset)");

        // 6. Delete Rule via GUI
        System.out.println("\n[STEP 5] Deleting chaos rule via GUI...");
        CountDownLatch delLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.handleDeleteSelected();
            delLatch.countDown();
        });
        delLatch.await(3, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 0 || AppContext.getChaosRuleStore().getAllRules().size() != 0) {
            throw new AssertionError("Rule was not deleted!");
        }

        HttpResponse<String> postDelResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (postDelResp.statusCode() != 200 || !postDelResp.body().contains("upstream-ok")) {
            throw new AssertionError("Expected normal 200 OK after deleting chaos rule, got: " + postDelResp.statusCode());
        }
        System.out.println("  Result: PASS (Rule deleted from store and UI; normal proxying restored)");

        // 7. Test SQLite persistence across restart
        System.out.println("\n[STEP 6] Testing persistence across app restart...");
        CountDownLatch persistLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/persist/chaos/*");
            view.getLatencyField().setText("1200");
            view.getStatusOverrideField().setText("418");
            view.handleAddRule();
            persistLatch.countDown();
        });
        persistLatch.await(3, TimeUnit.SECONDS);

        ChaosRuleStore restartedStore = new ChaosRuleStore(new ChaosRuleDao(dbManager));
        List<ChaosRule> reloaded = restartedStore.getAllRules();
        System.out.println("  Reloaded rules count: " + reloaded.size());
        boolean found = reloaded.stream().anyMatch(r ->
                "/persist/chaos/*".equals(r.getRoutePattern()) &&
                r.getLatencyMs() == 1200 &&
                r.getStatusOverrideCode() != null &&
                r.getStatusOverrideCode() == 418
        );
        if (!found) {
            throw new AssertionError("Persisted chaos rule not found after restart simulation!");
        }
        System.out.println("  Result: PASS (Chaos rule persisted to SQLite and reloaded correctly)");

        // Cleanup
        for (ChaosRule r : restartedStore.getAllRules()) {
            restartedStore.removeRule(r.getId());
        }
        for (ProxyRoute r : routeStore.getAllRoutes()) {
            routeStore.removeRoute(r.getId());
        }
        AppContext.getProxyServer().stop();
        targetServer.stop(0);

        System.out.println("\n==================================================");
        System.out.println("  All M9.2 Chaos Rules Tab Wiring tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
