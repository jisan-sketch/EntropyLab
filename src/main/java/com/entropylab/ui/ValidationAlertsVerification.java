package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.routes.ProxyRoute;
import com.entropylab.chaos.ChaosRule;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verification test for M12.1:
 * Input validation and JavaFX Alert error dialogs for:
 * 1. Proxy Control (port 1-65535)
 * 2. Routes form (pattern, target URL http/https)
 * 3. Chaos Rules form (pattern, non-negative latency, status 100-599)
 */
public class ValidationAlertsVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Input Validation & Alert Dialogs (M12.1)       ");
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
                // ==========================================
                // TEST 1: Proxy Control Port Validation
                // ==========================================
                System.out.println("\n[TEST 1] Testing Proxy Control Port Validation...");
                ProxyControlView proxyView = new ProxyControlView();

                // 1.1 Non-numeric port
                proxyView.getPortField().setText("invalid_port");
                capturedAlert.set(null);
                proxyView.onStartClicked();

                assertAlertShown(capturedAlert.get(), "Port must be a valid integer between 1 and 65535.", Alert.AlertType.ERROR);
                if (AppContext.getProxyServer() != null && AppContext.getProxyServer().isRunning()) {
                    throw new AssertionError("Proxy should not be running after invalid port input!");
                }
                System.out.println("  Non-numeric port ('invalid_port') rejected with Alert - PASS");

                // 1.2 Zero port (out of range 1-65535)
                proxyView.getPortField().setText("0");
                capturedAlert.set(null);
                proxyView.onStartClicked();

                assertAlertShown(capturedAlert.get(), "Port must be a valid integer between 1 and 65535.", Alert.AlertType.ERROR);
                System.out.println("  Port 0 rejected with Alert - PASS");

                // 1.3 Negative port
                proxyView.getPortField().setText("-8080");
                capturedAlert.set(null);
                proxyView.onStartClicked();

                assertAlertShown(capturedAlert.get(), "Port must be a valid integer between 1 and 65535.", Alert.AlertType.ERROR);
                System.out.println("  Negative port (-8080) rejected with Alert - PASS");

                // 1.4 Port exceeding 65535
                proxyView.getPortField().setText("70000");
                capturedAlert.set(null);
                proxyView.onStartClicked();

                assertAlertShown(capturedAlert.get(), "Port must be a valid integer between 1 and 65535.", Alert.AlertType.ERROR);
                System.out.println("  Port 70000 rejected with Alert - PASS");

                // ==========================================
                // TEST 2: Routes Form Validation
                // ==========================================
                System.out.println("\n[TEST 2] Testing Routes Form Validation...");
                RoutesView routesView = new RoutesView();
                int initialRouteCount = routesView.getRoutesList().size();

                // 2.1 Empty Route Pattern
                routesView.getRoutePatternField().setText("");
                routesView.getTargetUrlField().setText("https://api.github.com");
                capturedAlert.set(null);
                routesView.handleAddRoute();

                assertAlertShown(capturedAlert.get(), "Route Pattern cannot be empty.", Alert.AlertType.ERROR);
                if (routesView.getRoutesList().size() != initialRouteCount) {
                    throw new AssertionError("Route count changed on empty route pattern!");
                }
                System.out.println("  Empty route pattern rejected with Alert - PASS");

                // 2.2 Empty Target Base URL
                routesView.getRoutePatternField().setText("/api/users");
                routesView.getTargetUrlField().setText("");
                capturedAlert.set(null);
                routesView.handleAddRoute();

                assertAlertShown(capturedAlert.get(), "Target Base URL cannot be empty.", Alert.AlertType.ERROR);
                if (routesView.getRoutesList().size() != initialRouteCount) {
                    throw new AssertionError("Route count changed on empty target URL!");
                }
                System.out.println("  Empty target URL rejected with Alert - PASS");

                // 2.3 Target URL missing http:// or https://
                routesView.getRoutePatternField().setText("/api/users");
                routesView.getTargetUrlField().setText("api.github.com");
                capturedAlert.set(null);
                routesView.handleAddRoute();

                assertAlertShown(capturedAlert.get(), "Target Base URL must start with \"http://\" or \"https://\".", Alert.AlertType.ERROR);
                System.out.println("  Missing protocol ('api.github.com') rejected with Alert - PASS");

                routesView.getTargetUrlField().setText("ftp://ftp.example.com");
                capturedAlert.set(null);
                routesView.handleAddRoute();

                assertAlertShown(capturedAlert.get(), "Target Base URL must start with \"http://\" or \"https://\".", Alert.AlertType.ERROR);
                System.out.println("  Unsupported protocol ('ftp://...') rejected with Alert - PASS");

                // 2.4 Valid Route Input
                routesView.getRoutePatternField().setText("/valid/test/path");
                routesView.getTargetUrlField().setText("https://httpbin.org");
                capturedAlert.set(null);
                routesView.handleAddRoute();

                if (capturedAlert.get() != null) {
                    throw new AssertionError("Unexpected error alert on valid route input: " + capturedAlert.get().getContentText());
                }
                boolean routeFound = false;
                for (ProxyRoute r : routesView.getRoutesList()) {
                    if ("/valid/test/path".equals(r.getRoutePattern())) {
                        routeFound = true;
                        break;
                    }
                }
                if (!routeFound) {
                    throw new AssertionError("Valid route was not added to store!");
                }
                System.out.println("  Valid route successfully accepted without Alert - PASS");

                // ==========================================
                // TEST 3: Chaos Rules Form Validation
                // ==========================================
                System.out.println("\n[TEST 3] Testing Chaos Rules Form Validation...");
                ChaosRulesView chaosView = new ChaosRulesView();
                int initialRuleCount = chaosView.getRulesList().size();

                // 3.1 Empty Route Pattern
                chaosView.getRoutePatternField().setText("");
                chaosView.getLatencyField().setText("0");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                assertAlertShown(capturedAlert.get(), "Route Pattern cannot be empty.", Alert.AlertType.ERROR);
                if (chaosView.getRulesList().size() != initialRuleCount) {
                    throw new AssertionError("Rule count changed on empty route pattern!");
                }
                System.out.println("  Empty chaos rule pattern rejected with Alert - PASS");

                // 3.2 Negative Latency
                chaosView.getRoutePatternField().setText("/api/chaos");
                chaosView.getLatencyField().setText("-500");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                assertAlertShown(capturedAlert.get(), "Latency (ms) cannot be negative.", Alert.AlertType.ERROR);
                System.out.println("  Negative latency (-500) rejected with Alert - PASS");

                // 3.3 Non-integer Latency
                chaosView.getLatencyField().setText("five_hundred");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                assertAlertShown(capturedAlert.get(), "Latency must be a valid non-negative integer.", Alert.AlertType.ERROR);
                System.out.println("  Non-integer latency rejected with Alert - PASS");

                // 3.4 Invalid Status Override (too low < 100)
                chaosView.getLatencyField().setText("0");
                chaosView.getStatusOverrideField().setText("50");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                assertAlertShown(capturedAlert.get(), "Status Override must be a valid HTTP status code (100-599).", Alert.AlertType.ERROR);
                System.out.println("  Status override < 100 (50) rejected with Alert - PASS");

                // 3.5 Invalid Status Override (too high > 599)
                chaosView.getStatusOverrideField().setText("700");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                assertAlertShown(capturedAlert.get(), "Status Override must be a valid HTTP status code (100-599).", Alert.AlertType.ERROR);
                System.out.println("  Status override > 599 (700) rejected with Alert - PASS");

                // 3.6 Non-integer Status Override
                chaosView.getStatusOverrideField().setText("ERROR_CODE");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                assertAlertShown(capturedAlert.get(), "Status Override must be a valid integer code (100-599).", Alert.AlertType.ERROR);
                System.out.println("  Non-integer status override rejected with Alert - PASS");

                // 3.7 Valid Chaos Rule Input
                chaosView.getRoutePatternField().setText("/valid/chaos/test");
                chaosView.getLatencyField().setText("350");
                chaosView.getStatusOverrideField().setText("503");
                capturedAlert.set(null);
                chaosView.handleAddRule();

                if (capturedAlert.get() != null) {
                    throw new AssertionError("Unexpected error alert on valid chaos rule input: " + capturedAlert.get().getContentText());
                }
                boolean ruleFound = false;
                for (ChaosRule r : chaosView.getRulesList()) {
                    if ("/valid/chaos/test".equals(r.getRoutePattern()) && r.getLatencyMs() == 350) {
                        ruleFound = true;
                        break;
                    }
                }
                if (!ruleFound) {
                    throw new AssertionError("Valid chaos rule was not added to store!");
                }
                System.out.println("  Valid chaos rule successfully accepted without Alert - PASS");

                testLatch.countDown();

            } catch (Throwable t) {
                testError.set(t);
                testLatch.countDown();
            }
        });

        if (!testLatch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Validation verification timed out after 10 seconds!");
        }

        if (testError.get() != null) {
            testError.get().printStackTrace();
            throw new AssertionError("M12.1 Verification failed: " + testError.get().getMessage(), testError.get());
        }

        System.out.println("\n==================================================");
        System.out.println("  All M12.1 Input Validation tests PASSED!       ");
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
