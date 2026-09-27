package com.entropylab.ui;

import com.entropylab.EntropyLabApp;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verification test for M12.3: Branding & About Dialog
 * 1. Application Icon in title bar/taskbar
 * 2. About Menu & Dialog with app name, description, and pitch
 * 3. Dynamic window title reflecting proxy state
 */
public class BrandingAndAboutVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Branding & About Dialog Verification (M12.3)   ");
        System.out.println("==================================================");

        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(fxStartupLatch::countDown);
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }

        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX runtime failed to start");
        }

        AtomicReference<Alert> capturedAlert = new AtomicReference<>();
        AlertHelper.setAlertHandler(alert -> capturedAlert.set(alert));

        CountDownLatch testLatch = new CountDownLatch(1);
        AtomicReference<Throwable> testError = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Stage testStage = new Stage();
                testStage.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(), 800, 600));
                EntropyLabApp.setPrimaryStage(testStage);

                // ==========================================
                // TEST 1: Application Icon
                // ==========================================
                System.out.println("\n[TEST 1] Verifying Application Icon...");
                Image appIcon = EntropyLabApp.loadAppIcon();
                if (appIcon == null) {
                    throw new AssertionError("Application icon failed to load!");
                }
                if (appIcon.getWidth() <= 0 || appIcon.getHeight() <= 0) {
                    throw new AssertionError("Application icon has invalid dimensions: " + appIcon.getWidth() + "x" + appIcon.getHeight());
                }
                testStage.getIcons().add(appIcon);
                if (testStage.getIcons().isEmpty()) {
                    throw new AssertionError("Application icon not set on Stage!");
                }
                System.out.println("  Application icon loaded successfully (" + (int) appIcon.getWidth() + "x" + (int) appIcon.getHeight() + ") and attached to Stage - PASS");

                // ==========================================
                // TEST 2: About Menu & Dialog
                // ==========================================
                System.out.println("\n[TEST 2] Verifying About Menu and Dialog...");
                MenuBar menuBar = EntropyLabApp.buildMenuBar(testStage);
                if (menuBar.getMenus().isEmpty()) {
                    throw new AssertionError("MenuBar contains no menus!");
                }

                Menu helpMenu = menuBar.getMenus().get(0);
                if (!"Help".equals(helpMenu.getText())) {
                    throw new AssertionError("Expected Help menu, got: " + helpMenu.getText());
                }

                MenuItem aboutItem = helpMenu.getItems().get(0);
                if (!"About EntropyLab".equals(aboutItem.getText())) {
                    throw new AssertionError("Expected 'About EntropyLab' menu item, got: " + aboutItem.getText());
                }
                System.out.println("  MenuBar Help -> 'About EntropyLab' verified - PASS");

                // Trigger About Dialog
                capturedAlert.set(null);
                aboutItem.fire();

                Alert alert = capturedAlert.get();
                if (alert == null) {
                    throw new AssertionError("About dialog Alert was not shown!");
                }
                if (alert.getAlertType() != Alert.AlertType.INFORMATION) {
                    throw new AssertionError("Expected AlertType.INFORMATION, got: " + alert.getAlertType());
                }
                if (!"About EntropyLab".equals(alert.getTitle())) {
                    throw new AssertionError("Expected dialog title 'About EntropyLab', got: " + alert.getTitle());
                }

                String header = alert.getHeaderText();
                if (header == null || !header.contains("EntropyLab") || !header.contains("A local API proxy and chaos engineering studio for developers")) {
                    throw new AssertionError("Header missing expected name or description! Got: " + header);
                }
                System.out.println("  About Dialog Header: '" + header.replace("\n", " - ") + "' - PASS");

                String pitch = "This tool runs an embedded multi-threaded HTTP server that intercepts live API traffic, injects controlled latency and synthetic errors, and streams traffic analysis to an indexed SQLite log via background workers.";
                if (!pitch.equals(alert.getContentText())) {
                    throw new AssertionError("Pitch text mismatch!\nExpected: " + pitch + "\nActual:   " + alert.getContentText());
                }
                System.out.println("  About Dialog Pitch text matches exact specification - PASS");

                // ==========================================
                // TEST 3: Dynamic Window Title
                // ==========================================
                System.out.println("\n[TEST 3] Verifying Dynamic Window Title Reflecting Proxy State...");
                ProxyControlView proxyView = new ProxyControlView();

                // 3.1 Initial stopped title
                EntropyLabApp.updateTitle(false, 0);
                if (!"EntropyLab — Stopped".equals(testStage.getTitle())) {
                    throw new AssertionError("Expected 'EntropyLab — Stopped', got: '" + testStage.getTitle() + "'");
                }
                System.out.println("  Initial stopped title: '" + testStage.getTitle() + "' - PASS");

                // 3.2 Running title via setRunningState(8080)
                proxyView.setRunningState(8080);
                if (!"EntropyLab — Running on :8080".equals(testStage.getTitle())) {
                    throw new AssertionError("Expected 'EntropyLab — Running on :8080', got: '" + testStage.getTitle() + "'");
                }
                System.out.println("  Running title (:8080): '" + testStage.getTitle() + "' - PASS");

                // 3.3 Stopped title via setStoppedState()
                proxyView.setStoppedState();
                if (!"EntropyLab — Stopped".equals(testStage.getTitle())) {
                    throw new AssertionError("Expected 'EntropyLab — Stopped', got: '" + testStage.getTitle() + "'");
                }
                System.out.println("  Stopped title reset: '" + testStage.getTitle() + "' - PASS");

                // 3.4 Live proxy lifecycle title update
                System.out.println("  Testing dynamic title through live proxy start/stop cycle...");
                proxyView.getPortField().setText("18990");
                proxyView.onStartClicked();

                new Thread(() -> {
                    try {
                        long start = System.currentTimeMillis();
                        while (AppContext.getProxyServer() == null || !AppContext.getProxyServer().isRunning()) {
                            if (System.currentTimeMillis() - start > 5000) {
                                throw new AssertionError("Timeout waiting for proxy to start on port 18990");
                            }
                            Thread.sleep(50);
                        }
                        Thread.sleep(100);

                        CountDownLatch titleLatch = new CountDownLatch(1);
                        Platform.runLater(() -> {
                            if (!"EntropyLab — Running on :18990".equals(testStage.getTitle())) {
                                testError.set(new AssertionError("Expected 'EntropyLab — Running on :18990', got: '" + testStage.getTitle() + "'"));
                            }
                            titleLatch.countDown();
                        });
                        titleLatch.await(3, TimeUnit.SECONDS);

                        if (testError.get() != null) {
                            testLatch.countDown();
                            return;
                        }

                        // Stop proxy
                        Platform.runLater(() -> proxyView.onStopClicked());
                        while (AppContext.getProxyServer() != null && AppContext.getProxyServer().isRunning()) {
                            Thread.sleep(50);
                        }
                        Thread.sleep(100);

                        CountDownLatch stopTitleLatch = new CountDownLatch(1);
                        Platform.runLater(() -> {
                            if (!"EntropyLab — Stopped".equals(testStage.getTitle())) {
                                testError.set(new AssertionError("Expected 'EntropyLab — Stopped' after stop, got: '" + testStage.getTitle() + "'"));
                            }
                            stopTitleLatch.countDown();
                        });
                        stopTitleLatch.await(3, TimeUnit.SECONDS);

                        System.out.println("  Live proxy start/stop title transition verified (:18990 -> Stopped) - PASS");
                        testLatch.countDown();

                    } catch (Throwable t) {
                        testError.set(t);
                        testLatch.countDown();
                    }
                }).start();

            } catch (Throwable t) {
                testError.set(t);
                testLatch.countDown();
            }
        });

        if (!testLatch.await(15, TimeUnit.SECONDS)) {
            throw new AssertionError("Branding & About verification timed out after 15 seconds!");
        }

        if (testError.get() != null) {
            testError.get().printStackTrace();
            throw new AssertionError("M12.3 Verification failed: " + testError.get().getMessage(), testError.get());
        }

        System.out.println("\n==================================================");
        System.out.println("  All M12.3 Branding & About tests PASSED!        ");
        System.out.println("==================================================");

        Platform.exit();
        System.exit(0);
    }
}
