package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.routes.ProxyRoute;
import com.entropylab.routes.ProxyRouteStore;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.control.TableColumn;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M10.1 Inspector Table Layout Bound to Live Model.
 */
public class InspectorViewLayoutVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  Inspector Table Layout Verification (M10.1)");
        System.out.println("==================================================");

        // 1. Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // Register UiUpdateBridge listener with dispatcher
        AppContext.getRequestLogEventDispatcher().addListener(new UiUpdateBridge());

        // Clear in-memory inspector model
        AppContext.getInspectorTableModel().clear();

        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        // 2. Instantiate InspectorView on JavaFX thread
        final InspectorView[] viewRef = new InspectorView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new InspectorView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        InspectorView view = viewRef[0];

        // 3. Verify TableView columns and structure
        System.out.println("\n[STEP 1] Verifying TableView columns and properties...");
        if (view.getTableView() == null) {
            throw new AssertionError("TableView is null!");
        }
        if (view.getTableView().getColumns().size() != 6) {
            throw new AssertionError("Expected 6 columns, got: " + view.getTableView().getColumns().size());
        }

        TableColumn<RequestLog, ?> col0 = view.getTableView().getColumns().get(0);
        TableColumn<RequestLog, ?> col1 = view.getTableView().getColumns().get(1);
        TableColumn<RequestLog, ?> col2 = view.getTableView().getColumns().get(2);
        TableColumn<RequestLog, ?> col3 = view.getTableView().getColumns().get(3);
        TableColumn<RequestLog, ?> col4 = view.getTableView().getColumns().get(4);
        TableColumn<RequestLog, ?> col5 = view.getTableView().getColumns().get(5);

        System.out.println("  Col 0: " + col0.getText());
        System.out.println("  Col 1: " + col1.getText());
        System.out.println("  Col 2: " + col2.getText());
        System.out.println("  Col 3: " + col3.getText());
        System.out.println("  Col 4: " + col4.getText());
        System.out.println("  Col 5: " + col5.getText());

        if (!"Timestamp".equals(col0.getText())) throw new AssertionError("Col 0 mismatch");
        if (!"Method".equals(col1.getText())) throw new AssertionError("Col 1 mismatch");
        if (!"Path".equals(col2.getText())) throw new AssertionError("Col 2 mismatch");
        if (!"Status".equals(col3.getText())) throw new AssertionError("Col 3 mismatch");
        if (!"Latency (ms)".equals(col4.getText())) throw new AssertionError("Col 4 mismatch");
        if (!"Outcome Type".equals(col5.getText())) throw new AssertionError("Col 5 mismatch");

        // Verify Refresh button
        if (view.getRefreshButton() == null || !"Refresh".equals(view.getRefreshButton().getText())) {
            throw new AssertionError("Refresh button missing or incorrect label!");
        }
        System.out.println("  Refresh button verified: " + view.getRefreshButton().getText());

        // Verify items bound directly to InspectorTableModel
        if (view.getTableView().getItems() != AppContext.getInspectorTableModel().getLogEntries()) {
            throw new AssertionError("TableView items must be bound directly to AppContext.getInspectorTableModel().getLogEntries()!");
        }
        if (view.getTableView().getItems().size() != 0) {
            throw new AssertionError("Expected initial table size to be 0!");
        }
        System.out.println("  Result: PASS (Columns, Refresh button, and direct model binding verified)");

        // 4. Start upstream target server
        int targetPort = 9097;
        HttpServer targetServer = HttpServer.create(new InetSocketAddress(targetPort), 0);
        targetServer.createContext("/", exchange -> {
            byte[] resp = "{\"status\": \"upstream-live\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        targetServer.start();

        // 5. Add proxy route & start ProxyServer
        ProxyRouteStore routeStore = AppContext.getProxyRouteStore();
        routeStore.addRoute(new ProxyRoute("/api/*", "http://localhost:" + targetPort, true));

        int proxyPort = 8094;
        AppContext.getProxyServer().start(proxyPort);

        HttpClient client = HttpClient.newHttpClient();

        // 6. Proxy live Request 1: GET /api/users
        System.out.println("\n[STEP 2] Sending live request: GET /api/users...");
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + proxyPort + "/api/users"))
                .GET()
                .build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        if (resp1.statusCode() != 200) {
            throw new AssertionError("Expected 200 from live request, got: " + resp1.statusCode());
        }

        // Wait for Platform.runLater in UiUpdateBridge
        Thread.sleep(300);

        System.out.println("  TableView items size: " + view.getTableView().getItems().size());
        if (view.getTableView().getItems().size() != 1) {
            throw new AssertionError("Expected 1 row in TableView after live request, got: " + view.getTableView().getItems().size());
        }
        RequestLog topLog1 = view.getTableView().getItems().get(0);
        System.out.println("  Row 0 -> Method: " + topLog1.getMethod() + " Path: " + topLog1.getPath() +
                " Status: " + topLog1.getResponseStatus() + " Outcome: " + topLog1.getOutcomeType());
        if (!"/api/users".equals(topLog1.getPath()) || !"GET".equals(topLog1.getMethod()) ||
                topLog1.getResponseStatus() != 200 || topLog1.getOutcomeType() != OutcomeType.FORWARDED) {
            throw new AssertionError("Row 0 properties mismatch: " + topLog1);
        }
        System.out.println("  Result: PASS (Live request automatically appeared at top of TableView)");

        // 7. Proxy live Request 2: POST /api/items (verifying newest item appears at top, index 0)
        System.out.println("\n[STEP 3] Sending live request: POST /api/items...");
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + proxyPort + "/api/items"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"item\": 1}"))
                .build();
        client.send(req2, HttpResponse.BodyHandlers.ofString());

        Thread.sleep(300);

        System.out.println("  TableView items size: " + view.getTableView().getItems().size());
        if (view.getTableView().getItems().size() != 2) {
            throw new AssertionError("Expected 2 rows in TableView, got: " + view.getTableView().getItems().size());
        }
        RequestLog topLog2 = view.getTableView().getItems().get(0);
        System.out.println("  Row 0 (newest) -> Method: " + topLog2.getMethod() + " Path: " + topLog2.getPath());
        if (!"/api/items".equals(topLog2.getPath()) || !"POST".equals(topLog2.getMethod())) {
            throw new AssertionError("Expected POST /api/items at index 0, got: " + topLog2);
        }

        RequestLog secondLog = view.getTableView().getItems().get(1);
        System.out.println("  Row 1 (previous) -> Method: " + secondLog.getMethod() + " Path: " + secondLog.getPath());
        if (!"/api/users".equals(secondLog.getPath())) {
            throw new AssertionError("Expected GET /api/users at index 1!");
        }
        System.out.println("  Result: PASS (Newest live request placed at index 0 on live UI table)");

        // 8. Proxy live Request 3: Unmapped route 404
        System.out.println("\n[STEP 4] Sending live request: GET /not/found...");
        HttpRequest req3 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + proxyPort + "/not/found"))
                .GET()
                .build();
        client.send(req3, HttpResponse.BodyHandlers.ofString());

        Thread.sleep(300);

        if (view.getTableView().getItems().size() != 3) {
            throw new AssertionError("Expected 3 rows in TableView!");
        }
        RequestLog topLog3 = view.getTableView().getItems().get(0);
        System.out.println("  Row 0 -> Path: " + topLog3.getPath() + " Status: " + topLog3.getResponseStatus() + " Outcome: " + topLog3.getOutcomeType());
        if (topLog3.getResponseStatus() != 404 || topLog3.getOutcomeType() != OutcomeType.NOT_FOUND) {
            throw new AssertionError("Expected 404 NOT_FOUND for unmapped route!");
        }
        System.out.println("  Result: PASS (404 captured in real time on Inspector table)");

        // Cleanup
        AppContext.getProxyServer().stop();
        targetServer.stop(0);

        System.out.println("\n==================================================");
        System.out.println("  All M10.1 Inspector Table Layout tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
