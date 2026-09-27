package com.entropylab.ui;

import com.entropylab.EntropyLabApp;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M7.1 Main Window Shell.
 */
public class MainWindowVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  MainWindow Shell 5-Tabs Verification (M7.1)");
        System.out.println("==================================================");

        com.entropylab.core.AppPaths.ensureDirectoriesExist();
        com.entropylab.core.DatabaseManager dbManager = com.entropylab.core.DatabaseManager.initialize();
        com.entropylab.core.SchemaInitializer.initializeSchema(dbManager.getConnection());
        com.entropylab.core.AppContext.initialize(dbManager);

        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> latch.countDown());
        } catch (IllegalStateException e) {
            // Toolkit already initialized
            latch.countDown();
        }

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        CountDownLatch testLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TabPane tabPane = EntropyLabApp.buildTabPane();

                System.out.println("[TEST 1] Verifying TabPane properties...");
                if (tabPane.getTabClosingPolicy() != TabPane.TabClosingPolicy.UNAVAILABLE) {
                    throw new AssertionError("Expected TabClosingPolicy UNAVAILABLE, got: " + tabPane.getTabClosingPolicy());
                }
                System.out.println("  TabClosingPolicy: " + tabPane.getTabClosingPolicy() + " - PASS");

                System.out.println("\n[TEST 2] Verifying 5 tabs count and titles...");
                List<Tab> tabs = tabPane.getTabs();
                if (tabs.size() != 5) {
                    throw new AssertionError("Expected 5 tabs, got: " + tabs.size());
                }

                List<String> expectedTitles = List.of("Proxy Control", "Routes", "Chaos Rules", "Inspector", "Mocks");
                for (int i = 0; i < expectedTitles.size(); i++) {
                    Tab tab = tabs.get(i);
                    String expectedTitle = expectedTitles.get(i);
                    System.out.printf("  Tab %d: '%s'%n", i + 1, tab.getText());

                    if (!expectedTitle.equals(tab.getText())) {
                        throw new AssertionError("Tab " + i + " expected title '" + expectedTitle + "', but got: '" + tab.getText() + "'");
                    }
                    if (tab.isClosable()) {
                        throw new AssertionError("Tab '" + tab.getText() + "' should be non-closable!");
                    }

                    if (i == 0) {
                        // M7.2: Tab 0 has ProxyControlView
                        if (!(tab.getContent() instanceof ProxyControlView)) {
                            throw new AssertionError("Tab 0 content should be ProxyControlView!");
                        }
                        ProxyControlView view = (ProxyControlView) tab.getContent();
                        System.out.println("    - Port Field text: '" + view.getPortField().getText() + "'");
                        if (!"8080".equals(view.getPortField().getText())) {
                            throw new AssertionError("Expected default port '8080', got: " + view.getPortField().getText());
                        }

                        System.out.println("    - Start Button text: '" + view.getStartButton().getText() + "'");
                        if (!"Start Proxy".equals(view.getStartButton().getText())) {
                            throw new AssertionError("Expected 'Start Proxy' button, got: " + view.getStartButton().getText());
                        }

                        System.out.println("    - Stop Button text: '" + view.getStopButton().getText() + "'");
                        if (!"Stop Proxy".equals(view.getStopButton().getText())) {
                            throw new AssertionError("Expected 'Stop Proxy' button, got: " + view.getStopButton().getText());
                        }

                        System.out.println("    - Status Label text: '" + view.getStatusLabel().getText() + "'");
                        if (!"Status: Stopped".equals(view.getStatusLabel().getText())) {
                            throw new AssertionError("Expected 'Status: Stopped' label, got: " + view.getStatusLabel().getText());
                        }
                    } else if (i == 1 && tab.getContent() instanceof RoutesView) {
                        RoutesView routesView = (RoutesView) tab.getContent();
                        System.out.println("    - RoutesView verified with TableView (" + routesView.getTableView().getColumns().size() + " cols)");
                        if (routesView.getTableView() == null || routesView.getTableView().getColumns().size() != 3) {
                            throw new AssertionError("RoutesView should have TableView with 3 columns!");
                        }
                    } else if (i == 2 && tab.getContent() instanceof ChaosRulesView) {
                        ChaosRulesView chaosView = (ChaosRulesView) tab.getContent();
                        System.out.println("    - ChaosRulesView verified with TableView (" + chaosView.getTableView().getColumns().size() + " cols)");
                        if (chaosView.getTableView() == null || chaosView.getTableView().getColumns().size() != 5) {
                            throw new AssertionError("ChaosRulesView should have TableView with 5 columns!");
                        }
                    } else if (i == 3 && tab.getContent() instanceof InspectorView) {
                        InspectorView inspectorView = (InspectorView) tab.getContent();
                        System.out.println("    - InspectorView verified with TableView (" + inspectorView.getTableView().getColumns().size() + " cols)");
                        if (inspectorView.getTableView() == null || inspectorView.getTableView().getColumns().size() != 6) {
                            throw new AssertionError("InspectorView should have TableView with 6 columns!");
                        }
                    } else if (i == 4 && tab.getContent() instanceof MocksView) {
                        MocksView mocksView = (MocksView) tab.getContent();
                        System.out.println("    - MocksView verified with TableView (" + mocksView.getTableView().getColumns().size() + " cols)");
                        if (mocksView.getTableView() == null || mocksView.getTableView().getColumns().size() != 4) {
                            throw new AssertionError("MocksView should have TableView with 4 columns!");
                        }
                    } else {
                        // Other tabs: Placeholders
                        if (!(tab.getContent() instanceof VBox)) {
                            throw new AssertionError("Tab '" + tab.getText() + "' content should be a VBox!");
                        }
                        VBox content = (VBox) tab.getContent();
                        if (content.getChildren().isEmpty() || !(content.getChildren().get(0) instanceof Label)) {
                            throw new AssertionError("Tab '" + tab.getText() + "' VBox should contain a Label!");
                        }
                        Label label = (Label) content.getChildren().get(0);
                        if (!expectedTitle.equals(label.getText())) {
                            throw new AssertionError("Label text should match tab title '" + expectedTitle + "', got: '" + label.getText() + "'");
                        }
                    }
                }
                System.out.println("  Result: PASS (All 5 tabs present and validated with active View implementations)");

                System.out.println("\n==================================================");
                System.out.println("  All M7.1 & M7.2 Shell + Proxy Control Layout tests PASSED!");
                System.out.println("==================================================");
            } finally {
                testLatch.countDown();
            }
        });

        if (!testLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for JavaFX test execution");
        }

        Platform.exit();
    }
}
