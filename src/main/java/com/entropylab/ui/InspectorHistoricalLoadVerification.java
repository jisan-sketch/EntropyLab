package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.routes.ProxyRoute;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M10.2 Historical Load / Refresh.
 */
public class InspectorHistoricalLoadVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  Inspector Historical Load Verification (M10.2)");
        System.out.println("==================================================");

        // 1. Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // Register UiUpdateBridge listener with dispatcher
        AppContext.getRequestLogEventDispatcher().addListener(new UiUpdateBridge());

        // Clear in-memory model
        AppContext.getInspectorTableModel().clear();

        // 2. Insert 5 historical logs directly to DB (simulating previous sessions)
        System.out.println("\n[SETUP] Inserting 5 historical logs into SQLite...");
        for (int i = 1; i <= 5; i++) {
            RequestLog log = new RequestLog(
                    Instant.now().minusSeconds(60 - i * 10).toString(),
                    "GET",
                    "/historical/item-" + i
            );
            log.setResponseStatus(200);
            log.setLatencyMs(i * 15);
            log.setOutcomeType(OutcomeType.FORWARDED);
            AppContext.getRequestLogDao().insertLog(log);
        }
        System.out.println("  Historical logs inserted into DB successfully.");

        // 3. Initialize JavaFX Toolkit
        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        // 4. Instantiate InspectorView
        final InspectorView[] viewRef = new InspectorView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new InspectorView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        InspectorView view = viewRef[0];

        // 5. Trigger Historical Load via Refresh button
        System.out.println("\n[STEP 1] Triggering historical load via Refresh button...");
        Platform.runLater(() -> view.getRefreshButton().fire());

        // Wait up to 4 seconds for background thread to query DB and update model
        boolean loaded = false;
        for (int i = 0; i < 40; i++) {
            Thread.sleep(100);
            if (view.getTableView().getItems().size() >= 5) {
                loaded = true;
                break;
            }
        }
        if (!loaded) {
            throw new AssertionError("Historical logs failed to load into TableView! Current size: " + view.getTableView().getItems().size());
        }

        System.out.println("  TableView loaded count: " + view.getTableView().getItems().size());
        RequestLog topLog = view.getTableView().getItems().get(0);
        System.out.println("  Newest item (index 0): Path=" + topLog.getPath() + " ID=" + topLog.getId());
        if (!topLog.getPath().contains("historical")) {
            throw new AssertionError("Expected top log to be historical log, got: " + topLog.getPath());
        }
        System.out.println("  Result: PASS (Historical logs successfully populated newest-first from background query)");

        // 6. Test that real-time live-append still works without breaking
        System.out.println("\n[STEP 2] Testing real-time live-append on top of historical logs...");
        int targetPort = 9098;
        HttpServer targetServer = HttpServer.create(new InetSocketAddress(targetPort), 0);
        targetServer.createContext("/", exchange -> {
            byte[] resp = "{\"live\": true}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        targetServer.start();

        int proxyPort = 8095;
        AppContext.getProxyRouteStore().addRoute(new ProxyRoute("/live/*", "http://localhost:" + targetPort, true));
        AppContext.getProxyServer().start(proxyPort);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest liveReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + proxyPort + "/live/test-traffic"))
                .GET()
                .build();
        HttpResponse<String> liveResp = client.send(liveReq, HttpResponse.BodyHandlers.ofString());
        if (liveResp.statusCode() != 200) {
            throw new AssertionError("Live request failed with status: " + liveResp.statusCode());
        }

        // Wait for live event bridge update
        Thread.sleep(300);

        System.out.println("  TableView size after live request: " + view.getTableView().getItems().size());
        RequestLog liveTop = view.getTableView().getItems().get(0);
        System.out.println("  Index 0 item: Path=" + liveTop.getPath() + " Status=" + liveTop.getResponseStatus());
        if (!"/live/test-traffic".equals(liveTop.getPath())) {
            throw new AssertionError("Expected live request at index 0, but found: " + liveTop.getPath());
        }
        System.out.println("  Result: PASS (Live request appended to top without breaking historical list)");

        // 7. Click Refresh again and verify all entries (historical + live) persist and reload
        System.out.println("\n[STEP 3] Triggering Refresh again to verify full replacement reload...");
        Platform.runLater(() -> view.getRefreshButton().fire());
        Thread.sleep(500);

        RequestLog refreshedTop = view.getTableView().getItems().get(0);
        System.out.println("  Refreshed top item: Path=" + refreshedTop.getPath() + " Status=" + refreshedTop.getResponseStatus());
        if (!"/live/test-traffic".equals(refreshedTop.getPath())) {
            throw new AssertionError("Expected live request to remain at index 0 after refresh, got: " + refreshedTop.getPath());
        }
        System.out.println("  Result: PASS (Full reload successfully replaced list maintaining newest-first order)");

        // Cleanup
        AppContext.getProxyServer().stop();
        targetServer.stop(0);

        System.out.println("\n==================================================");
        System.out.println("  All M10.2 Historical Load tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
