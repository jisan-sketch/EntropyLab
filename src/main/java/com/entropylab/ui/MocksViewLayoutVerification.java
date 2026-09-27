package com.entropylab.ui;

import com.entropylab.EntropyLabApp;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M11.1 Mocks Tab Layout.
 */
public class MocksViewLayoutVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Mocks Tab Layout Verification (M11.1)");
        System.out.println("==================================================");

        // Bootstrap backend environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        for (MockRoute r : AppContext.getMockRouteStore().getAllRoutes()) {
            AppContext.getMockRouteStore().removeRoute(r.getId());
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

        final MocksView[] viewRef = new MocksView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new MocksView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        MocksView view = viewRef[0];

        // 1. Verify TableView and Columns
        System.out.println("\n[STEP 1] Verifying TableView and Columns...");
        if (view.getTableView() == null) {
            throw new AssertionError("TableView is null!");
        }
        if (view.getTableView().getColumns().size() != 4) {
            throw new AssertionError("Expected 4 columns, found: " + view.getTableView().getColumns().size());
        }

        String col0 = view.getTableView().getColumns().get(0).getText();
        String col1 = view.getTableView().getColumns().get(1).getText();
        String col2 = view.getTableView().getColumns().get(2).getText();
        String col3 = view.getTableView().getColumns().get(3).getText();

        if (!"Route Pattern".equals(col0) || !"File Path".equals(col1) ||
                !"Source".equals(col2) || !"Enabled".equals(col3)) {
            throw new AssertionError("Column headers mismatch! Found: " + col0 + ", " + col1 + ", " + col2 + ", " + col3);
        }
        System.out.println("  Columns verified: Route Pattern, File Path, Source, Enabled - PASS");

        // 2. Verify Form Controls
        System.out.println("\n[STEP 2] Verifying Form Controls...");
        if (view.getRoutePatternField() == null) throw new AssertionError("routePatternField is null");
        if (view.getFilePathField() == null) throw new AssertionError("filePathField is null");
        if (!view.getFilePathField().isEditable()) {
            System.out.println("  filePathField is read-only as required - PASS");
        } else {
            throw new AssertionError("filePathField must be read-only!");
        }
        if (view.getBrowseButton() == null) throw new AssertionError("browseButton is null");
        if (view.getEnabledCheckBox() == null || !view.getEnabledCheckBox().isSelected()) {
            throw new AssertionError("enabledCheckBox must be present and default to selected");
        }
        if (view.getAddMockButton() == null) throw new AssertionError("addMockButton is null");
        if (view.getDeleteSelectedButton() == null || !view.getDeleteSelectedButton().isDisabled()) {
            throw new AssertionError("deleteSelectedButton must default to disabled");
        }
        System.out.println("  All form controls present and properly configured - PASS");

        // 3. Verify Validation Errors
        System.out.println("\n[STEP 3] Verifying Validation Handling...");
        Platform.runLater(() -> view.getAddMockButton().fire());
        Thread.sleep(100);
        if (!view.getErrorLabel().getText().contains("Route Pattern cannot be empty")) {
            throw new AssertionError("Expected error on empty route pattern! Got: " + view.getErrorLabel().getText());
        }
        System.out.println("  Validated empty pattern rejection - PASS");

        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/v1/users");
            view.getAddMockButton().fire();
        });
        Thread.sleep(100);
        if (!view.getErrorLabel().getText().contains("Please select a JSON mock file")) {
            throw new AssertionError("Expected error on empty file path! Got: " + view.getErrorLabel().getText());
        }
        System.out.println("  Validated empty file path rejection - PASS");

        // 4. Add Rows against Local State
        System.out.println("\n[STEP 4] Adding Mock Routes to Local State...");
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/v1/users");
            view.getFilePathField().setText("C:/mocks/users_mock.json");
            view.getEnabledCheckBox().setSelected(true);
            view.getAddMockButton().fire();
        });
        Thread.sleep(100);

        if (view.getMocksList().size() != 1) {
            throw new AssertionError("Expected 1 mock route in table! Found: " + view.getMocksList().size());
        }
        MockRoute r1 = view.getMocksList().get(0);
        if (!"/api/v1/users".equals(r1.getRoutePattern()) ||
                !"C:/mocks/users_mock.json".equals(r1.getFilePath()) ||
                r1.getSource() != MockSource.MANUAL ||
                !r1.isEnabled()) {
            throw new AssertionError("Row 1 content incorrect: " + r1);
        }
        if (!view.getRoutePatternField().getText().isEmpty() || !view.getFilePathField().getText().isEmpty()) {
            throw new AssertionError("Form fields should clear after adding mock");
        }
        System.out.println("  Row 1 added successfully: " + r1.getRoutePattern() + " -> " + r1.getFilePath() + " - PASS");

        // Add Row 2 (disabled)
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/v1/inventory/*");
            view.getFilePathField().setText("C:/mocks/inventory_mock.json");
            view.getEnabledCheckBox().setSelected(false);
            view.getAddMockButton().fire();
        });
        Thread.sleep(100);

        if (view.getMocksList().size() != 2) {
            throw new AssertionError("Expected 2 mock routes in table! Found: " + view.getMocksList().size());
        }
        MockRoute r2 = view.getMocksList().get(1);
        if (r2.isEnabled()) {
            throw new AssertionError("Row 2 should be disabled");
        }
        System.out.println("  Row 2 added successfully (disabled): " + r2.getRoutePattern() + " - PASS");

        // 5. Toggle Enabled State
        System.out.println("\n[STEP 5] Testing Enable/Disable Toggle...");
        Platform.runLater(() -> {
            r2.setEnabled(true);
            view.getTableView().refresh();
        });
        Thread.sleep(100);
        if (!r2.isEnabled()) {
            throw new AssertionError("Expected Row 2 to toggle to enabled!");
        }
        System.out.println("  Toggle to enabled verified - PASS");

        // 6. Delete Selected Row
        System.out.println("\n[STEP 6] Testing Delete Selected...");
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
        });
        Thread.sleep(100);

        if (view.getDeleteSelectedButton().isDisabled()) {
            throw new AssertionError("Delete button should be enabled when a row is selected!");
        }

        Platform.runLater(() -> view.getDeleteSelectedButton().fire());
        Thread.sleep(100);

        if (view.getMocksList().size() != 1) {
            throw new AssertionError("Expected 1 mock route remaining! Found: " + view.getMocksList().size());
        }
        if (!"/api/v1/inventory/*".equals(view.getMocksList().get(0).getRoutePattern())) {
            throw new AssertionError("Expected Row 2 to remain after deleting Row 1!");
        }
        if (!view.getDeleteSelectedButton().isDisabled()) {
            throw new AssertionError("Delete button should become disabled after row deletion");
        }
        System.out.println("  Row deleted successfully and button state updated - PASS");

        // 7. Verify EntropyLabApp Tab Integration
        System.out.println("\n[STEP 7] Verifying EntropyLabApp Tab Integration...");
        final TabPane[] tabPaneRef = new TabPane[1];
        CountDownLatch tabLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            tabPaneRef[0] = EntropyLabApp.buildTabPane();
            tabLatch.countDown();
        });
        tabLatch.await(5, TimeUnit.SECONDS);

        TabPane tabPane = tabPaneRef[0];
        if (tabPane.getTabs().size() != 5) {
            throw new AssertionError("Expected 5 tabs in EntropyLabApp! Found: " + tabPane.getTabs().size());
        }

        Tab tab5 = tabPane.getTabs().get(4);
        if (!"Mocks".equals(tab5.getText())) {
            throw new AssertionError("Expected 5th tab to be 'Mocks', got: " + tab5.getText());
        }
        if (!(tab5.getContent() instanceof MocksView)) {
            throw new AssertionError("Expected 5th tab content to be MocksView, got: " + tab5.getContent().getClass().getName());
        }
        System.out.println("  EntropyLabApp Tab 5 ('Mocks') integrated with MocksView - PASS");

        System.out.println("\n==================================================");
        System.out.println("   All M11.1 Mocks Tab Layout tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
