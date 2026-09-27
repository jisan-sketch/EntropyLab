package com.entropylab.ui;

import com.entropylab.EntropyLabApp;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verification test for M11.5:
 * Confirmation Alert & Mocks Tab Live Refresh after Auto-Mock Snapshot.
 */
public class InspectorAutoMockConfirmationVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Auto-Mock Confirmation & Live Refresh (M11.5)  ");
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
        CountDownLatch alertLatch = new CountDownLatch(1);

        // Hook alert handler to capture confirmation alert without blocking the test
        InspectorView.setAlertHandler(alert -> {
            capturedAlert.set(alert);
            alertLatch.countDown();
        });

        CountDownLatch testLatch = new CountDownLatch(1);
        AtomicReference<Throwable> testError = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // 1. Build the full TabPane (simulating the complete UI session)
                TabPane tabPane = EntropyLabApp.buildTabPane();
                Tab inspectorTab = tabPane.getTabs().get(3);
                Tab mocksTab = tabPane.getTabs().get(4);

                InspectorView inspectorView = (InspectorView) inspectorTab.getContent();
                MocksView mocksView = (MocksView) mocksTab.getContent();

                int initialMockCount = mocksView.getTableView().getItems().size();
                System.out.println("[STEP 1] UI initialized. Initial mock routes in MocksView table: " + initialMockCount);

                // 2. Insert an eligible request log
                String testRoutePath = "/api/v1/confirmation_test_" + System.currentTimeMillis();
                RequestLog testLog = new RequestLog();
                testLog.setTimestamp("2026-09-26T12:45:00.000Z");
                testLog.setMethod("GET");
                testLog.setPath(testRoutePath);
                testLog.setTargetUrl("https://api.example.com" + testRoutePath);
                testLog.setResponseStatus(200);
                String payload = "{\"status\": \"ok\", \"verified\": true}";
                testLog.setResponseBodyBytes(payload.getBytes(StandardCharsets.UTF_8));
                testLog.setOutcomeType(OutcomeType.FORWARDED);

                int logId = AppContext.getRequestLogDao().insertLog(testLog);
                System.out.println("[STEP 2] Inserted eligible log ID " + logId + " for path: " + testRoutePath);

                // Refresh inspector table
                inspectorView.refreshHistoricalLogs();

                // Wait for log to be populated into TableView
                new Thread(() -> {
                    try {
                        long start = System.currentTimeMillis();
                        while (true) {
                            if (System.currentTimeMillis() - start > 5000) {
                                throw new AssertionError("Timeout waiting for log in InspectorView");
                            }
                            boolean found = false;
                            for (RequestLog r : inspectorView.getTableView().getItems()) {
                                if (r.getId() == logId) {
                                    found = true;
                                    break;
                                }
                            }
                            if (found) break;
                            Thread.sleep(50);
                        }

                        // Select log on FX thread
                        CountDownLatch selectLatch = new CountDownLatch(1);
                        Platform.runLater(() -> {
                            for (RequestLog r : inspectorView.getTableView().getItems()) {
                                if (r.getId() == logId) {
                                    inspectorView.getTableView().getSelectionModel().select(r);
                                    break;
                                }
                            }
                            selectLatch.countDown();
                        });
                        selectLatch.await(3, TimeUnit.SECONDS);

                        // Wait for details to populate
                        long detailStart = System.currentTimeMillis();
                        while (inspectorView.getSaveAsMockButton().isDisabled() || !inspectorView.getSaveAsMockButton().isVisible()) {
                            if (System.currentTimeMillis() - detailStart > 5000) {
                                throw new AssertionError("Timeout waiting for Save as Mock button to become active");
                            }
                            Thread.sleep(50);
                        }
                        System.out.println("[STEP 3] Log selected in InspectorView. 'Save as Offline Mock' button is enabled and visible.");

                        // 3. Click 'Save as Offline Mock'
                        System.out.println("[STEP 4] Firing 'Save as Offline Mock'...");
                        Platform.runLater(() -> inspectorView.getSaveAsMockButton().fire());

                        // 4. Verify Alert was triggered
                        if (!alertLatch.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Confirmation Alert was not shown within 5 seconds!");
                        }

                        Alert alert = capturedAlert.get();
                        if (alert == null) {
                            throw new AssertionError("Captured alert is null!");
                        }

                        System.out.println("[STEP 5] Verifying confirmation Alert details...");
                        if (alert.getAlertType() != Alert.AlertType.INFORMATION) {
                            throw new AssertionError("Expected AlertType.INFORMATION, got: " + alert.getAlertType());
                        }
                        System.out.println("  Alert Type: " + alert.getAlertType() + " - PASS");

                        String expectedMessage = "Saved as offline mock — this route will now be served locally.";
                        if (!expectedMessage.equals(alert.getContentText())) {
                            throw new AssertionError("Alert message mismatch! Expected: '" + expectedMessage + "', got: '" + alert.getContentText() + "'");
                        }
                        System.out.println("  Alert Content Text: \"" + alert.getContentText() + "\" - PASS");

                        // 5. Verify MocksView immediate table refresh
                        System.out.println("[STEP 6] Verifying MocksView immediate live refresh...");
                        Thread.sleep(200);

                        boolean mockFoundInTable = false;
                        for (MockRoute r : mocksView.getTableView().getItems()) {
                            if (testRoutePath.equals(r.getRoutePattern())) {
                                mockFoundInTable = true;
                                if (r.getSource() != MockSource.AUTO_SNAPSHOT) {
                                    throw new AssertionError("Expected source AUTO_SNAPSHOT, got: " + r.getSource());
                                }
                                if (!r.isEnabled()) {
                                    throw new AssertionError("Expected new snapshot mock route to be enabled!");
                                }
                                if (!Files.exists(Path.of(r.getFilePath()))) {
                                    throw new AssertionError("Mock file path does not exist on disk: " + r.getFilePath());
                                }
                                break;
                            }
                        }

                        if (!mockFoundInTable) {
                            throw new AssertionError("New snapshot mock route was not found in MocksView table after immediate refresh!");
                        }
                        System.out.println("  MocksView immediately displays new AUTO_SNAPSHOT route - PASS");

                        // 6. Verify switching to Mocks tab also refreshes from store
                        System.out.println("[STEP 7] Verifying tab selection reload behavior...");
                        CountDownLatch tabSelectLatch = new CountDownLatch(1);
                        Platform.runLater(() -> {
                            tabPane.getSelectionModel().select(mocksTab);
                            if (mocksTab.getOnSelectionChanged() != null) {
                                mocksTab.getOnSelectionChanged().handle(new javafx.event.Event(mocksTab, mocksTab, Tab.SELECTION_CHANGED_EVENT));
                            }
                            tabSelectLatch.countDown();
                        });
                        tabSelectLatch.await(3, TimeUnit.SECONDS);
                        Thread.sleep(150);

                        boolean foundAfterTabSwitch = false;
                        for (MockRoute r : mocksView.getTableView().getItems()) {
                            if (testRoutePath.equals(r.getRoutePattern())) {
                                foundAfterTabSwitch = true;
                                break;
                            }
                        }
                        if (!foundAfterTabSwitch) {
                            throw new AssertionError("New snapshot mock route missing after tab switch refresh!");
                        }
                        System.out.println("  Mocks tab switch refresh verified - PASS");

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
            throw new AssertionError("Verification timed out after 15 seconds!");
        }

        if (testError.get() != null) {
            testError.get().printStackTrace();
            throw new AssertionError("M11.5 Verification failed: " + testError.get().getMessage(), testError.get());
        }

        System.out.println("\n==================================================");
        System.out.println("  All M11.5 Confirmation & Refresh tests PASSED!  ");
        System.out.println("==================================================");

        Platform.exit();
        System.exit(0);
    }
}
