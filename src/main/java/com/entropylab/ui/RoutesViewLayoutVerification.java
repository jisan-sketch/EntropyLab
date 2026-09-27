package com.entropylab.ui;

import com.entropylab.routes.ProxyRoute;
import javafx.application.Platform;
import javafx.scene.control.TableColumn;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M8.1 Routes Tab Layout.
 */
public class RoutesViewLayoutVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("     Routes Tab Layout Verification (M8.1)");
        System.out.println("==================================================");

        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        final RoutesView[] viewRef = new RoutesView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new RoutesView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        RoutesView view = viewRef[0];

        // 1. Verify Layout and Columns
        System.out.println("\n[STEP 1] Verifying TableView columns and structure...");
        if (view.getTableView() == null) {
            throw new AssertionError("TableView is null!");
        }
        if (view.getTableView().getColumns().size() != 3) {
            throw new AssertionError("Expected 3 columns, got: " + view.getTableView().getColumns().size());
        }
        TableColumn<ProxyRoute, ?> col0 = view.getTableView().getColumns().get(0);
        TableColumn<ProxyRoute, ?> col1 = view.getTableView().getColumns().get(1);
        TableColumn<ProxyRoute, ?> col2 = view.getTableView().getColumns().get(2);

        System.out.println("  Column 0: " + col0.getText());
        System.out.println("  Column 1: " + col1.getText());
        System.out.println("  Column 2: " + col2.getText());

        if (!"Route Pattern".equals(col0.getText())) throw new AssertionError("Col 0 title mismatch");
        if (!"Target Base URL".equals(col1.getText())) throw new AssertionError("Col 1 title mismatch");
        if (!"Enabled".equals(col2.getText())) throw new AssertionError("Col 2 title mismatch");

        if (view.getRoutesList().size() != 0) {
            throw new AssertionError("Initial routes list should be empty!");
        }
        System.out.println("  Result: PASS (TableView columns and initial empty state verified)");

        // 2. Verify Form Controls
        System.out.println("\n[STEP 2] Verifying form controls...");
        if (view.getRoutePatternField() == null || view.getTargetUrlField() == null) {
            throw new AssertionError("TextFields missing!");
        }
        if (view.getEnabledCheckBox() == null || !view.getEnabledCheckBox().isSelected()) {
            throw new AssertionError("Enabled CheckBox missing or not checked by default!");
        }
        if (view.getAddRouteButton() == null || view.getDeleteSelectedButton() == null) {
            throw new AssertionError("Action buttons missing!");
        }
        System.out.println("  Result: PASS (Form input controls verified)");

        // 3. Add Route 1 via Form
        System.out.println("\n[STEP 3] Adding Route 1 (/api/users/* -> http://localhost:3000)...");
        CountDownLatch addLatch1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/users/*");
            view.getTargetUrlField().setText("http://localhost:3000");
            view.getEnabledCheckBox().setSelected(true);
            view.handleAddRoute();
            addLatch1.countDown();
        });
        addLatch1.await(2, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 1) {
            throw new AssertionError("Expected 1 route in list, got: " + view.getRoutesList().size());
        }
        ProxyRoute route1 = view.getRoutesList().get(0);
        if (!"/api/users/*".equals(route1.getRoutePattern()) ||
            !"http://localhost:3000".equals(route1.getTargetBaseUrl()) ||
            !route1.isEnabled()) {
            throw new AssertionError("Route 1 properties mismatch: " + route1);
        }
        if (!view.getRoutePatternField().getText().isEmpty() || !view.getTargetUrlField().getText().isEmpty()) {
            throw new AssertionError("Input text fields should be cleared after add!");
        }
        System.out.println("  Result: PASS (Route 1 added and displayed in TableView)");

        // 4. Add Route 2 via Form (disabled)
        System.out.println("\n[STEP 4] Adding Route 2 (/v1/orders -> http://localhost:5000, enabled=false)...");
        CountDownLatch addLatch2 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/v1/orders");
            view.getTargetUrlField().setText("http://localhost:5000");
            view.getEnabledCheckBox().setSelected(false);
            view.handleAddRoute();
            addLatch2.countDown();
        });
        addLatch2.await(2, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 2) {
            throw new AssertionError("Expected 2 routes in list, got: " + view.getRoutesList().size());
        }
        ProxyRoute route2 = view.getRoutesList().get(1);
        if (!"/v1/orders".equals(route2.getRoutePattern()) ||
            !"http://localhost:5000".equals(route2.getTargetBaseUrl()) ||
            route2.isEnabled()) {
            throw new AssertionError("Route 2 properties mismatch: " + route2);
        }
        System.out.println("  Result: PASS (Route 2 added with enabled=false)");

        // 5. Validation Check on Empty Add
        System.out.println("\n[STEP 5] Testing validation on empty Add Route click...");
        CountDownLatch valLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().clear();
            view.getTargetUrlField().clear();
            view.handleAddRoute();
            valLatch.countDown();
        });
        valLatch.await(2, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 2) {
            throw new AssertionError("Empty form should not have added any route!");
        }
        if (!view.getErrorLabel().isVisible()) {
            throw new AssertionError("Error label should be visible on invalid input!");
        }
        System.out.println("  Validation error shown: " + view.getErrorLabel().getText());
        System.out.println("  Result: PASS (Validation prevents invalid route entry)");

        // 6. Delete Selected Route
        System.out.println("\n[STEP 6] Selecting Route 1 and clicking 'Delete Selected'...");
        CountDownLatch delLatch1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.handleDeleteSelected();
            delLatch1.countDown();
        });
        delLatch1.await(2, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 1) {
            throw new AssertionError("Expected 1 route remaining, got: " + view.getRoutesList().size());
        }
        if (!"/v1/orders".equals(view.getRoutesList().get(0).getRoutePattern())) {
            throw new AssertionError("Remaining route is not Route 2: " + view.getRoutesList().get(0));
        }
        System.out.println("  Result: PASS (Selected route removed successfully)");

        // 7. Delete Without Selection
        System.out.println("\n[STEP 7] Testing 'Delete Selected' with no selection...");
        CountDownLatch delLatch2 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().clearSelection();
            view.handleDeleteSelected();
            delLatch2.countDown();
        });
        delLatch2.await(2, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 1) {
            throw new AssertionError("Route count should not change when nothing selected!");
        }
        if (!view.getErrorLabel().isVisible() || !view.getErrorLabel().getText().contains("select a route")) {
            throw new AssertionError("Expected prompt to select a route!");
        }
        System.out.println("  Error feedback: " + view.getErrorLabel().getText());
        System.out.println("  Result: PASS (Error feedback shown when no row selected)");

        // 8. Delete Final Route
        System.out.println("\n[STEP 8] Deleting remaining route...");
        CountDownLatch delLatch3 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.handleDeleteSelected();
            delLatch3.countDown();
        });
        delLatch3.await(2, TimeUnit.SECONDS);

        if (view.getRoutesList().size() != 0) {
            throw new AssertionError("Expected empty routes list, got size: " + view.getRoutesList().size());
        }
        System.out.println("  Result: PASS (All routes deleted, table returned to empty state)");

        System.out.println("\n==================================================");
        System.out.println("  All M8.1 Routes Tab Layout tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
