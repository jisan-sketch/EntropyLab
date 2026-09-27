package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import javafx.application.Platform;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M7.3 Wire Proxy Control.
 */
public class ProxyControlWiringVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  ProxyControl Wiring Verification Test (M7.3)");
        System.out.println("==================================================");

        // Bootstrap backend environment
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

        int testPort = 8088;
        HttpClient client = HttpClient.newHttpClient();

        final ProxyControlView[] viewRef = new ProxyControlView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new ProxyControlView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        ProxyControlView view = viewRef[0];

        // 1. Initial State Check
        System.out.println("\n[STEP 1] Checking initial stopped state...");
        if (!"Status: Stopped".equals(view.getStatusLabel().getText())) {
            throw new AssertionError("Expected 'Status: Stopped', got: " + view.getStatusLabel().getText());
        }
        if (view.getStartButton().isDisable()) {
            throw new AssertionError("Start button should be enabled initially!");
        }
        if (!view.getStopButton().isDisable()) {
            throw new AssertionError("Stop button should be disabled initially!");
        }
        if (view.getPortField().isDisable()) {
            throw new AssertionError("Port field should be enabled initially!");
        }
        System.out.println("  Result: PASS (Initial stopped UI state verified)");

        // 2. Test Invalid Port Validation
        System.out.println("\n[STEP 2] Testing invalid port validation (< 1024)...");
        CountDownLatch invalidLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getPortField().setText("500");
            view.onStartClicked();
            invalidLatch.countDown();
        });
        invalidLatch.await(2, TimeUnit.SECONDS);

        if (!view.getErrorLabel().isVisible() || !view.getErrorLabel().getText().contains("1024")) {
            throw new AssertionError("Expected validation error for port 500!");
        }
        if (AppContext.getProxyServer().isRunning()) {
            throw new AssertionError("ProxyServer should NOT start with invalid port!");
        }
        System.out.println("  Result: PASS (Port validation correctly prevented startup)");

        // 3. Start Proxy via Button Action
        System.out.println("\n[STEP 3] Starting proxy on port " + testPort + " via Start Proxy action...");
        CountDownLatch startLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getPortField().setText(String.valueOf(testPort));
            view.onStartClicked();
        });

        // Wait up to 5 seconds for background task to start proxy
        boolean running = false;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            if (AppContext.getProxyServer().isRunning()) {
                running = true;
                break;
            }
        }
        if (!running) {
            throw new AssertionError("ProxyServer failed to start within timeout!");
        }
        // Small sleep to allow Platform.runLater to finish updating UI
        Thread.sleep(200);

        System.out.println("  Server isRunning: " + AppContext.getProxyServer().isRunning() + " on port " + AppContext.getProxyServer().getPort());
        System.out.println("  Status Label: " + view.getStatusLabel().getText());
        if (!view.getStatusLabel().getText().contains("Running on port " + testPort)) {
            throw new AssertionError("Expected status label 'Status: Running on port " + testPort + "', got: " + view.getStatusLabel().getText());
        }
        if (!view.getStartButton().isDisable()) {
            throw new AssertionError("Start button should be disabled while running!");
        }
        if (view.getStopButton().isDisable()) {
            throw new AssertionError("Stop button should be enabled while running!");
        }
        if (!view.getPortField().isDisable()) {
            throw new AssertionError("Port field should be disabled while running!");
        }
        System.out.println("  Result: PASS (Server started on background Task and UI controls updated correctly)");

        // 4. Live HTTP Request verification while proxy is running
        System.out.println("\n[STEP 4] Sending live HTTP request to http://localhost:" + testPort + "/unregistered/test");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + "/unregistered/test"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Response status: " + resp.statusCode() + " body: " + resp.body());
        if (resp.statusCode() != 404 || !resp.body().contains("No route configured for this path")) {
            throw new AssertionError("Expected proxy 404 response on port " + testPort);
        }
        System.out.println("  Result: PASS (Embedded proxy server actively handling traffic on port " + testPort + ")");

        // 5. Stop Proxy via Button Action
        System.out.println("\n[STEP 5] Stopping proxy via Stop Proxy action...");
        Platform.runLater(() -> view.onStopClicked());

        boolean stopped = false;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            if (!AppContext.getProxyServer().isRunning()) {
                stopped = true;
                break;
            }
        }
        if (!stopped) {
            throw new AssertionError("ProxyServer failed to stop within timeout!");
        }
        Thread.sleep(200);

        System.out.println("  Status Label: " + view.getStatusLabel().getText());
        if (!"Status: Stopped".equals(view.getStatusLabel().getText())) {
            throw new AssertionError("Expected status label 'Status: Stopped', got: " + view.getStatusLabel().getText());
        }
        if (view.getStartButton().isDisable()) {
            throw new AssertionError("Start button should be enabled when stopped!");
        }
        if (!view.getStopButton().isDisable()) {
            throw new AssertionError("Stop button should be disabled when stopped!");
        }
        if (view.getPortField().isDisable()) {
            throw new AssertionError("Port field should be enabled when stopped!");
        }
        System.out.println("  Result: PASS (Proxy server stopped, executor shutdown, and UI updated)");

        // 6. Verify server is no longer accepting connections
        System.out.println("\n[STEP 6] Confirming connection refused to http://localhost:" + testPort + "...");
        try {
            client.send(req, HttpResponse.BodyHandlers.ofString());
            throw new AssertionError("Expected ConnectException since server is stopped!");
        } catch (IOException e) {
            System.out.println("  Caught expected exception: " + e.getMessage());
            System.out.println("  Result: PASS (Server cleanly closed and port released)");
        }

        System.out.println("\n==================================================");
        System.out.println("  All M7.3 Wire Proxy Control tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
