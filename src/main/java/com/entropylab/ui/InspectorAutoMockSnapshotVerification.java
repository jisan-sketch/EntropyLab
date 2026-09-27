package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import com.entropylab.proxy.ProxyServer;
import com.entropylab.routes.ProxyRoute;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Headless verification test for M11.4 — "Save as Offline Mock" Button.
 * Tests button visibility/enablement rules, snapshot file creation in mocks dir,
 * AUTO_SNAPSHOT MockRoute creation with exact pattern, and live proxy replay
 * verifying subsequent identical requests return the local snapshot offline.
 */
public class InspectorAutoMockSnapshotVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Inspector Auto-Mock Snapshot (M11.4)");
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

        // Clean up mock routes for isolated test
        for (MockRoute r : AppContext.getMockRouteStore().getAllRoutes()) {
            AppContext.getMockRouteStore().removeRoute(r.getId());
        }

        final InspectorView[] viewRef = new InspectorView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new InspectorView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        InspectorView view = viewRef[0];

        // 2. Test Button States for Ineligible Logs
        System.out.println("\n[STEP 1] Testing button visibility on initial/ineligible states...");
        if (view.getSaveAsMockButton().isVisible() || !view.getSaveAsMockButton().isDisabled()) {
            throw new AssertionError("Save as Mock button should be hidden & disabled when no row is selected!");
        }
        System.out.println("  Initial unselected state: hidden=true, disabled=true - PASS");

        // Seed Ineligible Log A: NOT_FOUND outcome
        RequestLog logNotFound = new RequestLog();
        logNotFound.setTimestamp("2026-09-26T12:40:00.000Z");
        logNotFound.setMethod("GET");
        logNotFound.setPath("/api/missing");
        logNotFound.setResponseStatus(404);
        logNotFound.setResponseBodyBytes("Not Found".getBytes(StandardCharsets.UTF_8));
        logNotFound.setOutcomeType(OutcomeType.NOT_FOUND);
        int idNotFound = AppContext.getRequestLogDao().insertLog(logNotFound);

        // Seed Ineligible Log B: FORWARDED outcome but empty response body
        RequestLog logEmptyBody = new RequestLog();
        logEmptyBody.setTimestamp("2026-09-26T12:41:00.000Z");
        logEmptyBody.setMethod("DELETE");
        logEmptyBody.setPath("/api/items/1");
        logEmptyBody.setResponseStatus(204);
        logEmptyBody.setResponseBodyBytes(new byte[0]);
        logEmptyBody.setOutcomeType(OutcomeType.FORWARDED);
        int idEmptyBody = AppContext.getRequestLogDao().insertLog(logEmptyBody);

        // Seed Eligible Log C: FORWARDED outcome with JSON response body
        RequestLog logEligible = new RequestLog();
        logEligible.setTimestamp("2026-09-26T12:42:00.000Z");
        logEligible.setMethod("GET");
        logEligible.setPath("/github/users/octocat");
        logEligible.setTargetUrl("https://api.github.com/users/octocat");
        logEligible.setResponseStatus(200);
        String snapshotPayload = "{\"login\": \"octocat\", \"id\": 583231, \"type\": \"User\"}";
        logEligible.setResponseBodyBytes(snapshotPayload.getBytes(StandardCharsets.UTF_8));
        logEligible.setOutcomeType(OutcomeType.FORWARDED);
        int idEligible = AppContext.getRequestLogDao().insertLog(logEligible);

        // Refresh table in view
        Platform.runLater(() -> view.refreshHistoricalLogs());

        long startTime = System.currentTimeMillis();
        while (view.getTableView().getItems().size() < 3) {
            if (System.currentTimeMillis() - startTime > 5000) {
                throw new AssertionError("Timeout waiting for TableView items");
            }
            Thread.sleep(50);
        }

        // Test selecting Ineligible Log A (NOT_FOUND)
        selectLogById(view, idNotFound);
        if (view.getSaveAsMockButton().isVisible() || !view.getSaveAsMockButton().isDisabled()) {
            throw new AssertionError("Save as Mock button should be hidden for NOT_FOUND outcome!");
        }
        System.out.println("  Ineligible Log (NOT_FOUND): hidden=true, disabled=true - PASS");

        // Test selecting Ineligible Log B (empty body)
        selectLogById(view, idEmptyBody);
        if (view.getSaveAsMockButton().isVisible() || !view.getSaveAsMockButton().isDisabled()) {
            throw new AssertionError("Save as Mock button should be hidden for empty response body!");
        }
        System.out.println("  Ineligible Log (Empty Body): hidden=true, disabled=true - PASS");

        // 3. Test Selecting Eligible Log C (FORWARDED with body)
        System.out.println("\n[STEP 2] Testing button activation on eligible log...");
        selectLogById(view, idEligible);
        if (!view.getSaveAsMockButton().isVisible() || view.getSaveAsMockButton().isDisabled()) {
            throw new AssertionError("Save as Mock button should be visible and enabled for FORWARDED log with body!");
        }
        System.out.println("  Eligible Log: visible=true, enabled=true - PASS");

        // 4. Click "Save as Offline Mock" and Verify Snapshot Creation
        System.out.println("\n[STEP 3] Clicking 'Save as Offline Mock' and verifying persistence...");
        CountDownLatch clickLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getSaveAsMockButton().fire();
            clickLatch.countDown();
        });
        clickLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(200);

        // Verify MockRoute in store
        List<MockRoute> routes = AppContext.getMockRouteStore().getAllRoutes();
        if (routes.isEmpty()) {
            throw new AssertionError("No mock routes found in MockRouteStore after clicking Save as Mock!");
        }

        MockRoute createdMock = null;
        for (MockRoute r : routes) {
            if ("/github/users/octocat".equals(r.getRoutePattern())) {
                createdMock = r;
                break;
            }
        }

        if (createdMock == null) {
            throw new AssertionError("MockRoute with pattern '/github/users/octocat' was not found in MockRouteStore!");
        }

        if (createdMock.getSource() != MockSource.AUTO_SNAPSHOT) {
            throw new AssertionError("Expected source AUTO_SNAPSHOT, got: " + createdMock.getSource());
        }
        if (!createdMock.isEnabled()) {
            throw new AssertionError("Expected created MockRoute to be enabled");
        }
        System.out.println("  MockRoute created in store: ID=" + createdMock.getId()
                + " pattern=" + createdMock.getRoutePattern()
                + " source=" + createdMock.getSource()
                + " file=" + createdMock.getFilePath());

        // Verify snapshot file exists and contents match
        Path snapshotFile = Path.of(createdMock.getFilePath());
        if (!Files.exists(snapshotFile)) {
            throw new AssertionError("Snapshot file was not created on disk: " + snapshotFile);
        }
        String fileContent = Files.readString(snapshotFile, StandardCharsets.UTF_8);
        if (!fileContent.equals(snapshotPayload)) {
            throw new AssertionError("Snapshot file content mismatch! Expected:\n" + snapshotPayload + "\nGot:\n" + fileContent);
        }
        System.out.println("  Snapshot file verified on disk with exact payload - PASS");

        // 5. Test Live Proxy: Repeating exact request now instantly returns local snapshot
        System.out.println("\n[STEP 4] Verifying live proxy serves offline snapshot without contacting upstream...");
        int proxyPort = 18995;
        int upstreamPort = 18996;

        AtomicInteger upstreamHitCount = new AtomicInteger(0);
        HttpServer upstreamServer = HttpServer.create(new InetSocketAddress("localhost", upstreamPort), 0);
        upstreamServer.createContext("/", exchange -> {
            upstreamHitCount.incrementAndGet();
            byte[] responseBytes = "{\"network\": \"real_internet_api\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        upstreamServer.start();

        ProxyServer proxyServer = AppContext.getProxyServer();
        proxyServer.start(proxyPort);

        // Register live route forwarding /github/* to upstream
        AppContext.getProxyRouteStore().addRoute(new ProxyRoute("/github/*", "http://localhost:" + upstreamPort, true));

        // Send request to exact route: /github/users/octocat
        URL testUrl = new URL("http://localhost:" + proxyPort + "/github/users/octocat");
        HttpURLConnection conn = (HttpURLConnection) testUrl.openConnection();
        conn.setRequestMethod("GET");
        int responseCode = conn.getResponseCode();
        byte[] respBytes;
        try (InputStream is = conn.getInputStream()) {
            respBytes = is.readAllBytes();
        }
        String returnedBody = new String(respBytes, StandardCharsets.UTF_8);
        conn.disconnect();

        System.out.println("  Proxy response code: " + responseCode);
        System.out.println("  Proxy response body: " + returnedBody);
        System.out.println("  Upstream hit count: " + upstreamHitCount.get());

        if (responseCode != 200 || !returnedBody.contains("octocat") || !returnedBody.contains("583231")) {
            throw new AssertionError("Proxy failed to serve offline snapshot mock! Got: " + returnedBody);
        }
        if (upstreamHitCount.get() != 0) {
            throw new AssertionError("Upstream server was contacted! Mock should have intercepted and bypassed upstream. Hits: " + upstreamHitCount.get());
        }
        System.out.println("  Result: PASS (Offline snapshot returned instantly, upstream hit count = 0)");

        // Cleanup
        proxyServer.stop();
        upstreamServer.stop(0);
        Files.deleteIfExists(snapshotFile);

        System.out.println("\n==================================================");
        System.out.println("   All M11.4 Auto-Mock Snapshot tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }

    private static void selectLogById(InspectorView view, int logId) throws Exception {
        int index = -1;
        for (int i = 0; i < view.getTableView().getItems().size(); i++) {
            if (view.getTableView().getItems().get(i).getId() != null &&
                    view.getTableView().getItems().get(i).getId() == logId) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            throw new AssertionError("Could not find row with logId=" + logId);
        }

        final int selIdx = index;
        Platform.runLater(() -> view.getTableView().getSelectionModel().select(selIdx));

        // Wait for background worker to populate details
        long start = System.currentTimeMillis();
        while (view.getCurrentSelectedLog() == null || view.getCurrentSelectedLog().getId() != logId) {
            if (System.currentTimeMillis() - start > 4000) {
                throw new AssertionError("Timeout waiting for logId=" + logId + " to populate in detail panel!");
            }
            Thread.sleep(50);
        }
        Thread.sleep(50);
    }
}
