package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.logging.RequestLog;
import com.entropylab.logging.RequestLoggedListener;
import javafx.application.Platform;

/**
 * Bridge between backend request log events and the UI InspectorTableModel.
 * Listens for new log persistence events on background worker threads, fetches the
 * full RequestLog entity from the database, and safely pushes it onto the JavaFX
 * Application Thread via Platform.runLater().
 */
public class UiUpdateBridge implements RequestLoggedListener {

    @Override
    public void onRequestLogged(int newLogId) {
        // Fetch full RequestLog from database on the background/calling thread
        RequestLog log = AppContext.getRequestLogDao().getLogById(newLogId);
        if (log == null) {
            System.err.println("[UiUpdateBridge] Could not find log with ID=" + newLogId);
            return;
        }

        try {
            Platform.runLater(() -> {
                AppContext.getInspectorTableModel().addNewestEntry(log);
                System.out.println("[UiUpdateBridge] Log ID=" + log.getId() + " added to InspectorTableModel. New list size: " + AppContext.getInspectorTableModel().size());
            });
        } catch (IllegalStateException e) {
            // Headless / non-toolkit fallback (e.g. testing)
            AppContext.getInspectorTableModel().addNewestEntry(log);
            System.out.println("[UiUpdateBridge] (Direct fallback) Log ID=" + log.getId() + " added to InspectorTableModel. New list size: " + AppContext.getInspectorTableModel().size());
        }
    }

    /**
     * Manual console test proving proxy request triggers event bridge,
     * Platform.runLater executes safely, and InspectorTableModel grows by 1 entry.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  UiUpdateBridge Test Suite (M4.5)");
        System.out.println("==================================================");

        // Start JavaFX runtime for Platform.runLater
        try {
            Platform.startup(() -> System.out.println("[JavaFX] Toolkit started for UiUpdateBridge test."));
        } catch (IllegalStateException ignored) {
            // Toolkit already running
        }

        // Bootstrap App environment
        com.entropylab.core.AppPaths.ensureDirectoriesExist();
        com.entropylab.core.DatabaseManager dbManager = com.entropylab.core.DatabaseManager.initialize();
        com.entropylab.core.SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // Register UiUpdateBridge
        UiUpdateBridge bridge = new UiUpdateBridge();
        AppContext.getRequestLogEventDispatcher().addListener(bridge);

        InspectorTableModel model = AppContext.getInspectorTableModel();
        int initialSize = model.size();
        System.out.println("[TEST] Initial InspectorTableModel size: " + initialSize);

        com.entropylab.proxy.ProxyServer proxyServer = AppContext.getProxyServer();
        int testPort = 8085;
        proxyServer.start(testPort);

        try {
            // Send request to proxy
            System.out.println("[TEST] Sending test request to proxy on port " + testPort + "...");
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + testPort + "/test/ui/bridge"))
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            System.out.println("[TEST] Response received, status: " + response.statusCode());

            // Allow Platform.runLater to execute on JavaFX application thread
            Thread.sleep(600);

            int newSize = model.size();
            System.out.println("[TEST] Model size after request: " + newSize);
            if (newSize != initialSize + 1) {
                throw new AssertionError("Expected model size to grow by 1 (from " + initialSize + " to " + (initialSize + 1) + "), but got: " + newSize);
            }

            RequestLog latestLog = model.getLogEntries().get(0);
            System.out.println("[TEST] Latest log in model: ID=" + latestLog.getId() + " path=" + latestLog.getPath() + " outcome=" + latestLog.getOutcomeType());
            if (!latestLog.getPath().equals("/test/ui/bridge")) {
                throw new AssertionError("Expected latest log path /test/ui/bridge, got: " + latestLog.getPath());
            }

            System.out.println("  Result: PASS (InspectorTableModel grew by 1 entry via Platform.runLater without threading exceptions)");
        } finally {
            proxyServer.stop();
            Platform.exit();
        }

        System.out.println("\n==================================================");
        System.out.println("  All UiUpdateBridge M4.5 tests PASSED!");
        System.out.println("==================================================");
    }
}
