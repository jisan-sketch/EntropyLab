package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.routes.ProxyRoute;
import com.entropylab.routes.ProxyRouteStore;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verification test for M8.2 Wire Routes Tab to ProxyRouteStore.
 * Tests live proxy route updates with no restart needed and DB persistence across restarts.
 */
public class RoutesViewWiringVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  Routes Tab Wiring Verification Test (M8.2)");
        System.out.println("==================================================");

        // 1. Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // Reset any leftover routes
        ProxyRouteStore store = AppContext.getProxyRouteStore();
        for (ProxyRoute r : store.getAllRoutes()) {
            store.removeRoute(r.getId());
        }

        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        // 2. Start mock upstream target server on port 9095
        int targetPort = 9095;
        HttpServer targetServer = HttpServer.create(new InetSocketAddress(targetPort), 0);
        targetServer.createContext("/", exchange -> {
            byte[] resp = "{\"message\": \"Hello from target backend\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        targetServer.start();
        System.out.println("[SETUP] Target upstream server started on port " + targetPort);

        // 3. Start live ProxyServer on port 8092
        int proxyPort = 8092;
        AppContext.getProxyServer().start(proxyPort);
        System.out.println("[SETUP] ProxyServer started on port " + proxyPort);

        HttpClient client = HttpClient.newHttpClient();

        // 4. Verify 404 initially before adding route
        System.out.println("\n[STEP 1] Requesting unmapped route before adding to Routes tab...");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + proxyPort + "/api/test"))
                .GET()
                .build();
        HttpResponse<String> initialResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Status: " + initialResp.statusCode() + " Body: " + initialResp.body());
        if (initialResp.statusCode() != 404) {
            throw new AssertionError("Expected 404 for unrouted path, got: " + initialResp.statusCode());
        }
        System.out.println("  Result: PASS (404 received as expected)");

        // 5. Create RoutesView and verify initial loading from store
        final RoutesView[] viewRef = new RoutesView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new RoutesView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        RoutesView view = viewRef[0];

        if (view.getRoutesList().size() != 0) {
            throw new AssertionError("Routes list should be empty initially!");
        }
        System.out.println("\n[STEP 2] RoutesView loaded 0 routes from store - PASS");

        // 6. Add route via GUI form
        System.out.println("\n[STEP 3] Adding route /api/* -> http://localhost:" + targetPort + " via GUI...");
        CountDownLatch addLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/*");
            view.getTargetUrlField().setText("http://localhost:" + targetPort);
            view.getEnabledCheckBox().setSelected(true);
            view.handleAddRoute();
            addLatch.countDown();
        });
        addLatch.await(3, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 1) {
            throw new AssertionError("Expected 1 route in TableView, got: " + view.getRoutesList().size());
        }
        if (AppContext.getProxyRouteStore().getAllRoutes().size() != 1) {
            throw new AssertionError("Expected 1 route in ProxyRouteStore!");
        }
        System.out.println("  Result: PASS (Route persisted to store and visible in TableView)");

        // 7. Verify live proxy immediately handles request with NO restart
        System.out.println("\n[STEP 4] Sending request to running proxy to verify live forwarding...");
        HttpResponse<String> proxyResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Status: " + proxyResp.statusCode() + " Body: " + proxyResp.body());
        if (proxyResp.statusCode() != 200 || !proxyResp.body().contains("Hello from target backend")) {
            throw new AssertionError("Expected 200 OK forwarded response, got: " + proxyResp.statusCode() + " " + proxyResp.body());
        }
        System.out.println("  Result: PASS (Live proxy immediately forwards traffic without restart!)");

        // 8. Test Edit Flow: select row, change enabled to false, click Save Changes
        System.out.println("\n[STEP 5] Testing edit flow: disable route via 'Save Changes'...");
        CountDownLatch editLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            if (view.getSaveChangesButton().isDisable()) {
                throw new AssertionError("Save Changes button should be enabled when row selected!");
            }
            if (!"/api/*".equals(view.getRoutePatternField().getText())) {
                throw new AssertionError("Form route pattern mismatch!");
            }
            view.getEnabledCheckBox().setSelected(false);
            view.handleSaveChanges();
            editLatch.countDown();
        });
        editLatch.await(3, TimeUnit.SECONDS);

        ProxyRoute edited = AppContext.getProxyRouteStore().getAllRoutes().get(0);
        if (edited.isEnabled()) {
            throw new AssertionError("Route should now be disabled in store!");
        }
        System.out.println("  Store route enabled: " + edited.isEnabled());

        // Hit proxy again: should now be 404 because route is disabled
        HttpResponse<String> disabledResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Hit disabled route -> Status: " + disabledResp.statusCode());
        if (disabledResp.statusCode() != 404) {
            throw new AssertionError("Expected 404 for disabled route, got: " + disabledResp.statusCode());
        }
        System.out.println("  Result: PASS (Edit flow saved to store and live proxy immediately reflected disabled state)");

        // 9. Re-enable route via Edit Flow
        System.out.println("\n[STEP 6] Re-enabling route via Edit Flow...");
        CountDownLatch reEnableLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.getEnabledCheckBox().setSelected(true);
            view.handleSaveChanges();
            reEnableLatch.countDown();
        });
        reEnableLatch.await(3, TimeUnit.SECONDS);

        HttpResponse<String> reEnabledResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (reEnabledResp.statusCode() != 200) {
            throw new AssertionError("Expected 200 after re-enabling route!");
        }
        System.out.println("  Result: PASS (Re-enabled route forwards traffic successfully)");

        // 10. Delete route via Delete Selected
        System.out.println("\n[STEP 7] Deleting route via 'Delete Selected'...");
        CountDownLatch delLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.handleDeleteSelected();
            delLatch.countDown();
        });
        delLatch.await(3, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 0 || AppContext.getProxyRouteStore().getAllRoutes().size() != 0) {
            throw new AssertionError("Route was not removed!");
        }
        HttpResponse<String> postDelResp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (postDelResp.statusCode() != 404) {
            throw new AssertionError("Expected 404 after deleting route!");
        }
        System.out.println("  Result: PASS (Route deleted from store and UI, proxy returned 404)");

        // 11. Test restart persistence
        System.out.println("\n[STEP 8] Testing persistence across app restart...");
        CountDownLatch persistLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/persist/*");
            view.getTargetUrlField().setText("http://localhost:" + targetPort);
            view.getEnabledCheckBox().setSelected(true);
            view.handleAddRoute();
            persistLatch.countDown();
        });
        persistLatch.await(3, TimeUnit.SECONDS);

        // Simulate app restart by constructing a new ProxyRouteStore reading SQLite
        ProxyRouteStore newStore = new ProxyRouteStore();
        List<ProxyRoute> reloadedRoutes = newStore.getAllRoutes();
        System.out.println("  Reloaded routes count after restart simulation: " + reloadedRoutes.size());
        boolean found = reloadedRoutes.stream().anyMatch(r -> "/persist/*".equals(r.getRoutePattern()));
        if (!found) {
            throw new AssertionError("Persisted route was not found upon simulated restart!");
        }
        System.out.println("  Result: PASS (Route persisted in DB and reloaded correctly)");

        // Clean up
        for (ProxyRoute r : newStore.getAllRoutes()) {
            newStore.removeRoute(r.getId());
        }
        AppContext.getProxyServer().stop();
        targetServer.stop(0);

        System.out.println("\n==================================================");
        System.out.println("  All M8.2 Routes Tab Wiring tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
