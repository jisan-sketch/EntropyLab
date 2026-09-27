package com.entropylab.ui;

import com.entropylab.chaos.ChaosRule;
import javafx.application.Platform;
import javafx.scene.control.TableColumn;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verification test for M9.1 Chaos Rules Tab Layout.
 */
public class ChaosRulesViewLayoutVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Chaos Rules Tab Layout Verification (M9.1)");
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

        final ChaosRulesView[] viewRef = new ChaosRulesView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new ChaosRulesView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        ChaosRulesView view = viewRef[0];

        // 1. Verify TableView columns
        System.out.println("\n[STEP 1] Verifying TableView columns and structure...");
        if (view.getTableView() == null) {
            throw new AssertionError("TableView is null!");
        }
        if (view.getTableView().getColumns().size() != 5) {
            throw new AssertionError("Expected 5 columns, got: " + view.getTableView().getColumns().size());
        }
        TableColumn<ChaosRule, ?> col0 = view.getTableView().getColumns().get(0);
        TableColumn<ChaosRule, ?> col1 = view.getTableView().getColumns().get(1);
        TableColumn<ChaosRule, ?> col2 = view.getTableView().getColumns().get(2);
        TableColumn<ChaosRule, ?> col3 = view.getTableView().getColumns().get(3);
        TableColumn<ChaosRule, ?> col4 = view.getTableView().getColumns().get(4);

        System.out.println("  Col 0: " + col0.getText());
        System.out.println("  Col 1: " + col1.getText());
        System.out.println("  Col 2: " + col2.getText());
        System.out.println("  Col 3: " + col3.getText());
        System.out.println("  Col 4: " + col4.getText());

        if (!"Route Pattern".equals(col0.getText())) throw new AssertionError("Col 0 title mismatch");
        if (!"Latency (ms)".equals(col1.getText())) throw new AssertionError("Col 1 title mismatch");
        if (!"Status Override".equals(col2.getText())) throw new AssertionError("Col 2 title mismatch");
        if (!"Reset Enabled".equals(col3.getText())) throw new AssertionError("Col 3 title mismatch");
        if (!"Enabled".equals(col4.getText())) throw new AssertionError("Col 4 title mismatch");

        if (view.getRulesList().size() != 0) {
            throw new AssertionError("Initial rules list should be empty!");
        }
        System.out.println("  Result: PASS (TableView columns and initial empty state verified)");

        // 2. Verify Form Controls
        System.out.println("\n[STEP 2] Verifying form input controls...");
        if (view.getRoutePatternField() == null || view.getLatencyField() == null || view.getStatusOverrideField() == null) {
            throw new AssertionError("TextFields missing!");
        }
        if (!"0".equals(view.getLatencyField().getText())) {
            throw new AssertionError("Latency should default to '0'!");
        }
        if (view.getResetEnabledCheckBox() == null || view.getResetEnabledCheckBox().isSelected()) {
            throw new AssertionError("Reset Enabled CheckBox should default to false!");
        }
        if (view.getEnabledCheckBox() == null || !view.getEnabledCheckBox().isSelected()) {
            throw new AssertionError("Enabled CheckBox should default to true!");
        }
        if (view.getAddRuleButton() == null || view.getDeleteSelectedButton() == null) {
            throw new AssertionError("Action buttons missing!");
        }
        System.out.println("  Result: PASS (Form input controls verified)");

        // 3. Add Rule 1 (Latency injection)
        System.out.println("\n[STEP 3] Adding Rule 1 (/api/slow/*, latency=2500ms)...");
        CountDownLatch addLatch1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/slow/*");
            view.getLatencyField().setText("2500");
            view.handleAddRule();
            addLatch1.countDown();
        });
        addLatch1.await(2, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 1) {
            throw new AssertionError("Expected 1 rule in list, got: " + view.getRulesList().size());
        }
        ChaosRule rule1 = view.getRulesList().get(0);
        if (!"/api/slow/*".equals(rule1.getRoutePattern()) || rule1.getLatencyMs() != 2500 ||
            rule1.getStatusOverrideCode() != null || rule1.isConnectionResetEnabled() || !rule1.isEnabled()) {
            throw new AssertionError("Rule 1 properties mismatch: " + rule1);
        }
        if (!view.getRoutePatternField().getText().isEmpty() || !"0".equals(view.getLatencyField().getText())) {
            throw new AssertionError("Form fields should be reset after adding rule!");
        }
        System.out.println("  Result: PASS (Rule 1 added successfully)");

        // 4. Add Rule 2 (Status Override)
        System.out.println("\n[STEP 4] Adding Rule 2 (/api/error/*, status=503)...");
        CountDownLatch addLatch2 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/error/*");
            view.getStatusOverrideField().setText("503");
            view.handleAddRule();
            addLatch2.countDown();
        });
        addLatch2.await(2, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 2) {
            throw new AssertionError("Expected 2 rules in list, got: " + view.getRulesList().size());
        }
        ChaosRule rule2 = view.getRulesList().get(1);
        if (rule2.getStatusOverrideCode() == null || rule2.getStatusOverrideCode() != 503) {
            throw new AssertionError("Rule 2 status override mismatch: " + rule2);
        }
        System.out.println("  Result: PASS (Rule 2 added successfully)");

        // 5. Add Rule 3 (Connection Reset, Disabled)
        System.out.println("\n[STEP 5] Adding Rule 3 (/api/drop/*, reset=true, enabled=false)...");
        CountDownLatch addLatch3 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/api/drop/*");
            view.getResetEnabledCheckBox().setSelected(true);
            view.getEnabledCheckBox().setSelected(false);
            view.handleAddRule();
            addLatch3.countDown();
        });
        addLatch3.await(2, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 3) {
            throw new AssertionError("Expected 3 rules in list, got: " + view.getRulesList().size());
        }
        ChaosRule rule3 = view.getRulesList().get(2);
        if (!rule3.isConnectionResetEnabled() || rule3.isEnabled()) {
            throw new AssertionError("Rule 3 reset / enabled mismatch: " + rule3);
        }
        System.out.println("  Result: PASS (Rule 3 added successfully)");

        // 6. Test Validations
        System.out.println("\n[STEP 6] Testing input validations...");
        CountDownLatch valLatch1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().clear();
            view.handleAddRule();
            valLatch1.countDown();
        });
        valLatch1.await(2, TimeUnit.SECONDS);
        if (view.getRulesList().size() != 3 || !view.getErrorLabel().isVisible()) {
            throw new AssertionError("Validation should catch empty pattern!");
        }

        CountDownLatch valLatch2 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/test");
            view.getLatencyField().setText("invalid");
            view.handleAddRule();
            valLatch2.countDown();
        });
        valLatch2.await(2, TimeUnit.SECONDS);
        if (view.getRulesList().size() != 3 || !view.getErrorLabel().getText().contains("integer")) {
            throw new AssertionError("Validation should catch non-integer latency!");
        }

        CountDownLatch valLatch3 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getRoutePatternField().setText("/test");
            view.getLatencyField().setText("0");
            view.getStatusOverrideField().setText("999");
            view.handleAddRule();
            valLatch3.countDown();
        });
        valLatch3.await(2, TimeUnit.SECONDS);
        if (view.getRulesList().size() != 3 || !view.getErrorLabel().getText().contains("100-599")) {
            throw new AssertionError("Validation should catch invalid HTTP status code!");
        }
        System.out.println("  Result: PASS (All validation checks rejected invalid input correctly)");

        // 7. Delete Selected Rule
        System.out.println("\n[STEP 7] Selecting Rule 2 and clicking 'Delete Selected'...");
        CountDownLatch delLatch1 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(1);
            view.handleDeleteSelected();
            delLatch1.countDown();
        });
        delLatch1.await(2, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 2) {
            throw new AssertionError("Expected 2 rules after deletion, got: " + view.getRulesList().size());
        }
        if ("/api/error/*".equals(view.getRulesList().get(0).getRoutePattern()) ||
            "/api/error/*".equals(view.getRulesList().get(1).getRoutePattern())) {
            throw new AssertionError("Rule 2 was not deleted!");
        }
        System.out.println("  Result: PASS (Selected rule deleted)");

        // 8. Delete with no selection
        System.out.println("\n[STEP 8] Clicking 'Delete Selected' without selection...");
        CountDownLatch delLatch2 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().clearSelection();
            view.handleDeleteSelected();
            delLatch2.countDown();
        });
        delLatch2.await(2, TimeUnit.SECONDS);
        if (view.getRulesList().size() != 2 || !view.getErrorLabel().isVisible()) {
            throw new AssertionError("Error should be displayed when deleting with no selection!");
        }
        System.out.println("  Result: PASS (Handled deletion without selection)");

        // 9. Clean up remaining rules
        System.out.println("\n[STEP 9] Deleting remaining rules...");
        CountDownLatch delLatch3 = new CountDownLatch(1);
        Platform.runLater(() -> {
            view.getTableView().getSelectionModel().select(0);
            view.handleDeleteSelected();
            view.getTableView().getSelectionModel().select(0);
            view.handleDeleteSelected();
            delLatch3.countDown();
        });
        delLatch3.await(2, TimeUnit.SECONDS);

        if (view.getRulesList().size() != 0) {
            throw new AssertionError("Table should be empty after deleting all rules!");
        }
        System.out.println("  Result: PASS (All rules cleared)");

        System.out.println("\n==================================================");
        System.out.println("  All M9.1 Chaos Rules Tab Layout tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
