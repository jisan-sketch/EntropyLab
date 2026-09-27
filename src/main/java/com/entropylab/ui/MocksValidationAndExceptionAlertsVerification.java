package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.mock.MockRoute;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verification test for M12.2:
 * 1. Mocks form validation (route pattern, file exists, file is valid JSON)
 * 2. Generic try/catch-to-alert exception handling across UI views.
 */
public class MocksValidationAndExceptionAlertsVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println(" Mocks Validation & Exception Alerts (M12.2)     ");
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

        // Create temporary test files
        Path validJsonPath = Files.createTempFile("valid_mock_", ".json");
        Files.writeString(validJsonPath, "{\"service\": \"test\", \"active\": true, \"count\": 42}");

        Path invalidJsonPath = Files.createTempFile("invalid_mock_", ".json");
        Files.writeString(invalidJsonPath, "This is plain text and definitely NOT valid JSON content {[:]");

        CountDownLatch testLatch = new CountDownLatch(1);
        AtomicReference<Throwable> testError = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                // ==========================================
                // PART 1: Mocks Form Validation
                // ==========================================
                System.out.println("\n[TEST 1] Testing Mocks Form Validation...");
                MocksView mocksView = new MocksView();
                int initialMockCount = mocksView.getTableView().getItems().size();

                // 1.1 Empty Route Pattern
                mocksView.getRoutePatternField().setText("");
                mocksView.getFilePathField().setText(validJsonPath.toAbsolutePath().toString());
                capturedAlert.set(null);
                mocksView.handleAddMock();

                assertAlertShown(capturedAlert.get(), "Route Pattern cannot be empty.", Alert.AlertType.ERROR);
                if (mocksView.getTableView().getItems().size() != initialMockCount) {
                    throw new AssertionError("Mock count changed on empty route pattern!");
                }
                System.out.println("  Empty route pattern rejected with Alert - PASS");

                // 1.2 Empty File Path
                mocksView.getRoutePatternField().setText("/api/users/*");
                mocksView.getFilePathField().setText("");
                capturedAlert.set(null);
                mocksView.handleAddMock();

                assertAlertShown(capturedAlert.get(), "Please select a JSON mock file.", Alert.AlertType.ERROR);
                if (mocksView.getTableView().getItems().size() != initialMockCount) {
                    throw new AssertionError("Mock count changed on empty file path!");
                }
                System.out.println("  Empty file path rejected with Alert - PASS");

                // 1.3 Non-existent File
                mocksView.getRoutePatternField().setText("/api/users/*");
                String nonExistent = AppPaths.getMocksDir().resolve("non_existent_mock_file_12345.json").toAbsolutePath().toString();
                mocksView.getFilePathField().setText(nonExistent);
                capturedAlert.set(null);
                mocksView.handleAddMock();

                if (capturedAlert.get() == null || !capturedAlert.get().getContentText().contains("Selected mock file does not exist")) {
                    throw new AssertionError("Expected non-existent file alert, got: " + (capturedAlert.get() != null ? capturedAlert.get().getContentText() : "null"));
                }
                System.out.println("  Non-existent file rejected with Alert - PASS");

                // 1.4 Invalid JSON File (unparseable)
                mocksView.getRoutePatternField().setText("/api/users/*");
                mocksView.getFilePathField().setText(invalidJsonPath.toAbsolutePath().toString());
                capturedAlert.set(null);
                mocksView.handleAddMock();

                if (capturedAlert.get() == null || !capturedAlert.get().getContentText().contains("Selected file is not valid JSON")) {
                    throw new AssertionError("Expected invalid JSON alert, got: " + (capturedAlert.get() != null ? capturedAlert.get().getContentText() : "null"));
                }
                System.out.println("  Invalid JSON file rejected with Alert - PASS");

                // 1.5 Valid JSON File
                mocksView.getRoutePatternField().setText("/api/m12_valid_test");
                mocksView.getFilePathField().setText(validJsonPath.toAbsolutePath().toString());
                capturedAlert.set(null);
                mocksView.handleAddMock();

                if (capturedAlert.get() != null) {
                    throw new AssertionError("Unexpected error alert on valid mock input: " + capturedAlert.get().getContentText());
                }
                boolean mockFound = false;
                for (MockRoute r : mocksView.getTableView().getItems()) {
                    if ("/api/m12_valid_test".equals(r.getRoutePattern())) {
                        mockFound = true;
                        break;
                    }
                }
                if (!mockFound) {
                    throw new AssertionError("Valid mock route was not added to store!");
                }
                System.out.println("  Valid JSON mock file accepted without Alert - PASS");

                // ==========================================
                // PART 2: Generic Exception Handling & Alerts
                // ==========================================
                System.out.println("\n[TEST 2] Testing Generic Exception-to-Alert Handling...");

                // 2.1 MocksView: Delete without selection
                capturedAlert.set(null);
                mocksView.getTableView().getSelectionModel().clearSelection();
                mocksView.handleDeleteSelected();
                if (capturedAlert.get() == null || !capturedAlert.get().getContentText().contains("Please select a mock route from the table to delete.")) {
                    throw new AssertionError("Expected selection required alert, got: " + (capturedAlert.get() != null ? capturedAlert.get().getContentText() : "null"));
                }
                System.out.println("  MocksView selection required alert - PASS");

                // 2.2 RoutesView: Delete without selection
                RoutesView routesView = new RoutesView();
                capturedAlert.set(null);
                routesView.getTableView().getSelectionModel().clearSelection();
                routesView.handleDeleteSelected();
                if (capturedAlert.get() == null || !capturedAlert.get().getContentText().contains("Please select a route from the table to delete.")) {
                    throw new AssertionError("Expected selection required alert in RoutesView, got: " + (capturedAlert.get() != null ? capturedAlert.get().getContentText() : "null"));
                }
                System.out.println("  RoutesView selection required alert - PASS");

                // 2.3 ChaosRulesView: Delete without selection
                ChaosRulesView chaosView = new ChaosRulesView();
                capturedAlert.set(null);
                chaosView.getTableView().getSelectionModel().clearSelection();
                chaosView.handleDeleteSelected();
                if (capturedAlert.get() == null || !capturedAlert.get().getContentText().contains("Please select a chaos rule from the table to delete.")) {
                    throw new AssertionError("Expected selection required alert in ChaosRulesView, got: " + (capturedAlert.get() != null ? capturedAlert.get().getContentText() : "null"));
                }
                System.out.println("  ChaosRulesView selection required alert - PASS");

                // 2.4 AlertHelper generic exception capture
                capturedAlert.set(null);
                Exception simulatedDbError = new java.sql.SQLException("Simulated database disk I/O error");
                AlertHelper.showError("Database Error", "Failed to execute database operation: " + simulatedDbError.getMessage());

                if (capturedAlert.get() == null || !capturedAlert.get().getContentText().contains("Simulated database disk I/O error")) {
                    throw new AssertionError("Expected generic simulated error alert, got: " + (capturedAlert.get() != null ? capturedAlert.get().getContentText() : "null"));
                }
                System.out.println("  Generic exception-to-alert pattern verified - PASS");

                testLatch.countDown();

            } catch (Throwable t) {
                testError.set(t);
                testLatch.countDown();
            }
        });

        if (!testLatch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("M12.2 verification timed out after 10 seconds!");
        }

        // Cleanup temporary files
        try {
            Files.deleteIfExists(validJsonPath);
            Files.deleteIfExists(invalidJsonPath);
        } catch (Exception ignored) {
        }

        if (testError.get() != null) {
            testError.get().printStackTrace();
            throw new AssertionError("M12.2 Verification failed: " + testError.get().getMessage(), testError.get());
        }

        System.out.println("\n==================================================");
        System.out.println("  All M12.2 Mocks & Exception tests PASSED!       ");
        System.out.println("==================================================");

        Platform.exit();
        System.exit(0);
    }

    private static void assertAlertShown(Alert alert, String expectedContent, Alert.AlertType expectedType) {
        if (alert == null) {
            throw new AssertionError("Expected Alert to be shown, but no Alert was triggered!");
        }
        if (alert.getAlertType() != expectedType) {
            throw new AssertionError("Expected AlertType " + expectedType + ", got: " + alert.getAlertType());
        }
        if (!expectedContent.equals(alert.getContentText())) {
            throw new AssertionError("Alert message mismatch!\nExpected: " + expectedContent + "\nActual:   " + alert.getContentText());
        }
    }
}
