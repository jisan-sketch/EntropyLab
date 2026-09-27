package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M11.2 — Wire Mocks Tab to MockRouteStore.
 * Tests store loading, adding manual mocks, live proxy mock interception (bypassing upstream),
 * toggle off/on, editing, and deletion with SQLite persistence.
 */
public class MocksViewWiringVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Mocks Tab Store Wiring Verification (M11.2)");
        System.out.println("==================================================");

        // 1. Bootstrap backend environment
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

        // Clean up existing mock routes for clean test run
        for (MockRoute r : AppContext.getMockRouteStore().getAllRoutes()) {
            AppContext.getMockRouteStore().removeRoute(r.getId());
        }

        // 2. Prepare test mock files on disk
        Path mockFile1 = AppPaths.getMocksDir().resolve("test_user_v1.json");
        Files.writeString(mockFile1, "{\"status\": \"mock_active\", \"data\": \"user_data_123\"}");

        Path mockFile2 = AppPaths.getMocksDir().resolve("test_user_v2.json");
        Files.writeString(mockFile2, "{\"status\": \"mock_updated\", \"data\": \"user_data_456\"}");

        // Pre-seed one route in MockRouteStore before view creation to test initial load
        MockRoute preSeeded = new MockRoute("/api/initial/seed", mockFile1.toAbsolutePath().toString(), true, MockSource.MANUAL);
        AppContext.getMockRouteStore().addRoute(preSeeded);

        final MocksView[] viewRef = new MocksView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new MocksView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        MocksView view = viewRef[0];

        // 3. Verify Initial Store Loading
        System.out.println("\n[STEP 1] Verifying Initial Store Loading...");
        if (view.getTableView().getItems().size() != 1) {
            throw new AssertionError("Expected 1 seeded mock route, found: " + view.getTableView().getItems().size());
        }
        if (!"/api/initial/seed".equals(view.getTableView().getItems().get(0).getRoutePattern())) {
            throw new AssertionError("Unexpected route pattern in table: " + view.getTableView().getItems().get(0).getRoutePattern());
        }
        System.out.println("  Initial store loading verified - PASS");

        // 4. Test Add Mock via Form
        System.out.println("\n[STEP 2] Adding Mock Route via Form...");
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/orders/*");
            view.getFilePathField().setText(mockFile1.toAbsolutePath().toString());
            view.getEnabledCheckBox().setSelected(true);
            view.getAddMockButton().fire();
        });
        Thread.sleep(200);

        if (view.getTableView().getItems().size() != 2) {
            throw new AssertionError("Expected 2 mock routes in TableView! Found: " + view.getTableView().getItems().size());
        }
        if (AppContext.getMockRouteStore().getAllRoutes().size() != 2) {
            throw new AssertionError("Expected 2 mock routes in MockRouteStore! Found: " + AppContext.getMockRouteStore().getAllRoutes().size());
        }

        MockRoute addedRoute = null;
        for (MockRoute r : AppContext.getMockRouteStore().getAllRoutes()) {
            if ("/api/orders/*".equals(r.getRoutePattern())) {
                addedRoute = r;
                break;
            }
        }
        if (addedRoute == null || addedRoute.getSource() != MockSource.MANUAL || !addedRoute.isEnabled()) {
            throw new AssertionError("Added route verification failed in MockRouteStore: " + addedRoute);
        }
        System.out.println("  Added MockRoute: ID=" + addedRoute.getId() + " pattern=" + addedRoute.getRoutePattern() + " source=MANUAL - PASS");

        // 5. Test Live Proxy Mock Interception (bypassing upstream backend)
        System.out.println("\n[STEP 3] Testing Live Proxy Mock Interception...");
        int proxyPort = 18993;
        int upstreamPort = 18994;

        // Upstream backend returns distinct response
        HttpServer upstreamServer = HttpServer.create(new InetSocketAddress("localhost", upstreamPort), 0);
        upstreamServer.createContext("/", exchange -> {
            byte[] responseBytes = "{\"upstream\": \"real_backend\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        upstreamServer.start();

        ProxyServer proxyServer = AppContext.getProxyServer();
        proxyServer.start(proxyPort);

        // Register forwarding route for /api/orders/*
        AppContext.getProxyRouteStore().addRoute(new ProxyRoute("/api/orders/*", "http://localhost:" + upstreamPort, true));

        // Send request to /api/orders/99
        URL testUrl = new URL("http://localhost:" + proxyPort + "/api/orders/99");
        HttpURLConnection conn = (HttpURLConnection) testUrl.openConnection();
        conn.setRequestMethod("GET");
        int responseCode = conn.getResponseCode();
        byte[] respBytes;
        try (InputStream is = conn.getInputStream()) {
            respBytes = is.readAllBytes();
        }
        String body = new String(respBytes, StandardCharsets.UTF_8);
        conn.disconnect();

        System.out.println("  Response code: " + responseCode + ", Body: " + body);
        if (responseCode != 200 || !body.contains("mock_active")) {
            throw new AssertionError("Live proxy did not return mock file contents! Body: " + body);
        }
        System.out.println("  Live proxy returned static mock response, bypassing upstream - PASS");

        // 6. Test Enable/Disable Toggle
        System.out.println("\n[STEP 4] Testing Mock Route Toggle Off/On...");
        final MockRoute targetRoute = addedRoute;
        Platform.runLater(() -> {
            // Find index of targetRoute in table
            int idx = -1;
            for (int i = 0; i < view.getTableView().getItems().size(); i++) {
                if (view.getTableView().getItems().get(i).getId() == targetRoute.getId()) {
                    idx = i;
                    break;
                }
            }
            if (idx != -1) {
                MockRoute r = view.getTableView().getItems().get(idx);
                r.setEnabled(false);
                AppContext.getMockRouteStore().updateRoute(r);
                view.refreshFromStore();
            }
        });
        Thread.sleep(200);

        // Verify disabled in store
        MockRoute inStore = AppContext.getMockRouteStore().findMatchingRoute("/api/orders/99");
        if (inStore != null) {
            throw new AssertionError("MockRoute should not match when disabled!");
        }

        // Send request again — should now hit real upstream backend!
        conn = (HttpURLConnection) testUrl.openConnection();
        conn.setRequestMethod("GET");
        responseCode = conn.getResponseCode();
        try (InputStream is = conn.getInputStream()) {
            respBytes = is.readAllBytes();
        }
        body = new String(respBytes, StandardCharsets.UTF_8);
        conn.disconnect();

        System.out.println("  Disabled mock -> response code: " + responseCode + ", Body: " + body);
        if (!body.contains("real_backend")) {
            throw new AssertionError("Disabled mock should have allowed request to hit upstream! Got: " + body);
        }
        System.out.println("  Disabled mock routed to upstream successfully - PASS");

        // Re-enable mock
        Platform.runLater(() -> {
            targetRoute.setEnabled(true);
            AppContext.getMockRouteStore().updateRoute(targetRoute);
            view.refreshFromStore();
        });
        Thread.sleep(200);

        // 7. Test Edit Flow (Save Changes to use mockFile2)
        System.out.println("\n[STEP 5] Testing Edit Flow (Save Changes)...");
        Platform.runLater(() -> {
            int idx = -1;
            for (int i = 0; i < view.getTableView().getItems().size(); i++) {
                if (view.getTableView().getItems().get(i).getId() == targetRoute.getId()) {
                    idx = i;
                    break;
                }
            }
            view.getTableView().getSelectionModel().select(idx);
        });
        Thread.sleep(100);

        if (!view.getRoutePatternField().getText().equals("/api/orders/*")) {
            throw new AssertionError("Form routePatternField not populated on row selection!");
        }

        // Change file path to mockFile2 and save
        Platform.runLater(() -> {
            view.getFilePathField().setText(mockFile2.toAbsolutePath().toString());
            view.getSaveChangesButton().fire();
        });
        Thread.sleep(200);

        // Verify proxy now returns mockFile2 payload ("mock_updated")
        conn = (HttpURLConnection) testUrl.openConnection();
        conn.setRequestMethod("GET");
        responseCode = conn.getResponseCode();
        try (InputStream is = conn.getInputStream()) {
            respBytes = is.readAllBytes();
        }
        body = new String(respBytes, StandardCharsets.UTF_8);
        conn.disconnect();

        System.out.println("  Updated mock -> Body: " + body);
        if (!body.contains("mock_updated")) {
            throw new AssertionError("Updated mock file not reflected in live proxy! Got: " + body);
        }
        System.out.println("  Edit flow and updated mock response verified - PASS");

        // 8. Test Delete Selected
        System.out.println("\n[STEP 6] Testing Delete Selected...");
        Platform.runLater(() -> {
            int idx = -1;
            for (int i = 0; i < view.getTableView().getItems().size(); i++) {
                if (view.getTableView().getItems().get(i).getId() == targetRoute.getId()) {
                    idx = i;
                    break;
                }
            }
            view.getTableView().getSelectionModel().select(idx);
            view.getDeleteSelectedButton().fire();
        });
        Thread.sleep(200);

        if (view.getTableView().getItems().size() != 1) {
            throw new AssertionError("Expected 1 mock route remaining after delete! Found: " + view.getTableView().getItems().size());
        }
        if (AppContext.getMockRouteStore().getAllRoutes().size() != 1) {
            throw new AssertionError("Deleted route still present in MockRouteStore!");
        }
        System.out.println("  Route deleted from TableView, Store, and SQLite DB - PASS");

        // 9. Cleanup
        proxyServer.stop();
        upstreamServer.stop(0);
        Files.deleteIfExists(mockFile1);
        Files.deleteIfExists(mockFile2);

        System.out.println("\n==================================================");
        System.out.println("   All M11.2 Mocks Tab Wiring tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
