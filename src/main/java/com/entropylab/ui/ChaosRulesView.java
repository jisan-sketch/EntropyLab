package com.entropylab.ui;

import com.entropylab.chaos.ChaosRule;
import com.entropylab.core.AppContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * View and controller for the "Chaos Rules" tab.
 * Displays configured chaos rules from ChaosRuleStore in a TableView
 * and provides forms to add, edit, and delete chaos rules.
 */
public class ChaosRulesView extends VBox {

    private final TableView<ChaosRule> tableView;
    private final ObservableList<ChaosRule> rulesList;

    private final TextField routePatternField;
    private final TextField latencyField;
    private final TextField statusOverrideField;
    private final CheckBox resetEnabledCheckBox;
    private final CheckBox enabledCheckBox;
    private final Button addRuleButton;
    private final Button saveChangesButton;
    private final Button deleteSelectedButton;
    private final Button clearFormButton;
    private final Label errorLabel;
    private final Label formTitle;

    private boolean isRefreshing = false;
    private Integer selectedRuleId = null;

    public ChaosRulesView() {
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: #f8fafc;");

        // Header Section
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("Chaos Rules");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Simulate latency injection, HTTP status overrides, and abrupt connection resets.");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Table Setup
        rulesList = FXCollections.observableArrayList();
        tableView = new TableView<>(rulesList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px;"
        );

        TableColumn<ChaosRule, String> patternCol = new TableColumn<>("Route Pattern");
        patternCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoutePattern()));
        patternCol.setMinWidth(180);

        TableColumn<ChaosRule, String> latencyCol = new TableColumn<>("Latency (ms)");
        latencyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLatencyMs() + " ms"));
        latencyCol.setMaxWidth(130);
        latencyCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<ChaosRule, String> statusCol = new TableColumn<>("Status Override");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getStatusOverrideCode() != null ? String.valueOf(cell.getValue().getStatusOverrideCode()) : "-"
        ));
        statusCol.setMaxWidth(130);
        statusCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<ChaosRule, String> resetCol = new TableColumn<>("Reset Enabled");
        resetCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isConnectionResetEnabled() ? "Yes" : "No"));
        resetCol.setMaxWidth(120);
        resetCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<ChaosRule, String> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isEnabled() ? "Yes" : "No"));
        enabledCol.setMaxWidth(100);
        enabledCol.setStyle("-fx-alignment: CENTER;");

        tableView.getColumns().addAll(patternCol, latencyCol, statusCol, resetCol, enabledCol);
        tableView.setPlaceholder(new Label("No chaos rules configured yet. Add one below."));

        // Form Card Container
        VBox formCard = new VBox(12);
        formCard.setPadding(new Insets(16));
        formCard.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 8px; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 8px; " +
                "-fx-border-width: 1px;"
        );

        formTitle = new Label("Add New Chaos Rule");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // Form Input Fields Row
        HBox inputsRow = new HBox(12);
        inputsRow.setAlignment(Pos.CENTER_LEFT);

        VBox patternBox = new VBox(4);
        Label patternLabel = new Label("Route Pattern");
        patternLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        routePatternField = new TextField();
        routePatternField.setPromptText("e.g. /api/slow/*");
        routePatternField.setPrefWidth(200);
        routePatternField.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-padding: 7 10; " +
                "-fx-background-radius: 5px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 5px;"
        );
        patternBox.getChildren().addAll(patternLabel, routePatternField);
        HBox.setHgrow(patternBox, Priority.ALWAYS);

        VBox latencyBox = new VBox(4);
        Label latencyLabel = new Label("Latency (ms)");
        latencyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        latencyField = new TextField("0");
        latencyField.setPromptText("0");
        latencyField.setPrefWidth(110);
        latencyField.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-padding: 7 10; " +
                "-fx-background-radius: 5px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 5px;"
        );
        latencyBox.getChildren().addAll(latencyLabel, latencyField);

        VBox statusBox = new VBox(4);
        Label statusLabel = new Label("Status Override");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        statusOverrideField = new TextField();
        statusOverrideField.setPromptText("e.g. 500 (optional)");
        statusOverrideField.setPrefWidth(140);
        statusOverrideField.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-padding: 7 10; " +
                "-fx-background-radius: 5px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 5px;"
        );
        statusBox.getChildren().addAll(statusLabel, statusOverrideField);

        VBox resetBox = new VBox(4);
        resetBox.setAlignment(Pos.BOTTOM_LEFT);
        resetEnabledCheckBox = new CheckBox("Reset Enabled");
        resetEnabledCheckBox.setSelected(false);
        resetEnabledCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 0 0 8 0;");
        resetBox.getChildren().add(resetEnabledCheckBox);

        VBox enabledBox = new VBox(4);
        enabledBox.setAlignment(Pos.BOTTOM_LEFT);
        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        enabledCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 0 0 8 0;");
        enabledBox.getChildren().add(enabledCheckBox);

        inputsRow.getChildren().addAll(patternBox, latencyBox, statusBox, resetBox, enabledBox);

        // Buttons and Error Row
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        addRuleButton = new Button("Add Rule");
        addRuleButton.setStyle(
                "-fx-background-color: #2563eb; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 18; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );

        saveChangesButton = new Button("Save Changes");
        saveChangesButton.setDisable(true);
        saveChangesButton.setStyle(
                "-fx-background-color: #059669; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 18; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );

        deleteSelectedButton = new Button("Delete Selected");
        deleteSelectedButton.setStyle(
                "-fx-background-color: #fee2e2; " +
                "-fx-text-fill: #dc2626; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 18; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: #fca5a5; " +
                "-fx-border-radius: 6px;"
        );

        clearFormButton = new Button("Clear");
        clearFormButton.setStyle(
                "-fx-background-color: #f1f5f9; " +
                "-fx-text-fill: #475569; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 14; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 6px;"
        );

        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        actionsRow.getChildren().addAll(addRuleButton, saveChangesButton, deleteSelectedButton, clearFormButton, errorLabel);

        formCard.getChildren().addAll(formTitle, inputsRow, actionsRow);

        getChildren().addAll(headerBox, tableView, formCard);

        wireEvents();
        refreshFromStore();
    }

    private void wireEvents() {
        addRuleButton.setOnAction(e -> handleAddRule());
        saveChangesButton.setOnAction(e -> handleSaveChanges());
        deleteSelectedButton.setOnAction(e -> handleDeleteSelected());
        clearFormButton.setOnAction(e -> handleClearForm());

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (isRefreshing) return;
            if (newVal != null) {
                populateForm(newVal);
            } else {
                handleClearForm();
            }
        });
    }

    public void refreshFromStore() {
        isRefreshing = true;
        try {
            rulesList.clear();
            if (AppContext.getChaosRuleStore() != null) {
                List<ChaosRule> allRules = AppContext.getChaosRuleStore().getAllRules();
                rulesList.addAll(allRules);
            }
        } finally {
            isRefreshing = false;
        }
    }

    public void handleAddRule() {
        clearError();
        String pattern = routePatternField.getText() != null ? routePatternField.getText().trim() : "";
        if (pattern.isEmpty()) {
            String errorMsg = "Route Pattern cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        int latencyMs = 0;
        String latencyText = latencyField.getText() != null ? latencyField.getText().trim() : "";
        if (!latencyText.isEmpty()) {
            try {
                latencyMs = Integer.parseInt(latencyText);
                if (latencyMs < 0) {
                    String errorMsg = "Latency (ms) cannot be negative.";
                    showError(errorMsg);
                    AlertHelper.showError("Validation Error", errorMsg);
                    return;
                }
            } catch (NumberFormatException ex) {
                String errorMsg = "Latency must be a valid non-negative integer.";
                showError(errorMsg);
                AlertHelper.showError("Validation Error", errorMsg);
                return;
            }
        }

        Integer statusOverrideCode = null;
        String statusText = statusOverrideField.getText() != null ? statusOverrideField.getText().trim() : "";
        if (!statusText.isEmpty()) {
            try {
                int code = Integer.parseInt(statusText);
                if (code < 100 || code > 599) {
                    String errorMsg = "Status Override must be a valid HTTP status code (100-599).";
                    showError(errorMsg);
                    AlertHelper.showError("Validation Error", errorMsg);
                    return;
                }
                statusOverrideCode = code;
            } catch (NumberFormatException ex) {
                String errorMsg = "Status Override must be a valid integer code (100-599).";
                showError(errorMsg);
                AlertHelper.showError("Validation Error", errorMsg);
                return;
            }
        }

        boolean resetEnabled = resetEnabledCheckBox.isSelected();
        if (resetEnabled && statusOverrideCode != null) {
            System.err.println("[ChaosRulesView] Warning: Both connection reset and status override are enabled. Reset takes priority.");
        }

        ChaosRule rule = new ChaosRule(
                pattern,
                latencyMs,
                statusOverrideCode,
                resetEnabled,
                enabledCheckBox.isSelected()
        );

        try {
            if (AppContext.getChaosRuleStore() != null) {
                AppContext.getChaosRuleStore().addRule(rule);
            } else {
                rulesList.add(rule);
            }

            refreshFromStore();
            handleClearForm();
        } catch (Exception ex) {
            String msg = "Failed to add chaos rule: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleSaveChanges() {
        clearError();
        if (selectedRuleId == null) {
            String errorMsg = "Please select a chaos rule from the table to edit.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        String pattern = routePatternField.getText() != null ? routePatternField.getText().trim() : "";
        if (pattern.isEmpty()) {
            String errorMsg = "Route Pattern cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        int latencyMs = 0;
        String latencyText = latencyField.getText() != null ? latencyField.getText().trim() : "";
        if (!latencyText.isEmpty()) {
            try {
                latencyMs = Integer.parseInt(latencyText);
                if (latencyMs < 0) {
                    String errorMsg = "Latency (ms) cannot be negative.";
                    showError(errorMsg);
                    AlertHelper.showError("Validation Error", errorMsg);
                    return;
                }
            } catch (NumberFormatException ex) {
                String errorMsg = "Latency must be a valid non-negative integer.";
                showError(errorMsg);
                AlertHelper.showError("Validation Error", errorMsg);
                return;
            }
        }

        Integer statusOverrideCode = null;
        String statusText = statusOverrideField.getText() != null ? statusOverrideField.getText().trim() : "";
        if (!statusText.isEmpty()) {
            try {
                int code = Integer.parseInt(statusText);
                if (code < 100 || code > 599) {
                    String errorMsg = "Status Override must be a valid HTTP status code (100-599).";
                    showError(errorMsg);
                    AlertHelper.showError("Validation Error", errorMsg);
                    return;
                }
                statusOverrideCode = code;
            } catch (NumberFormatException ex) {
                String errorMsg = "Status Override must be a valid integer code (100-599).";
                showError(errorMsg);
                AlertHelper.showError("Validation Error", errorMsg);
                return;
            }
        }

        boolean resetEnabled = resetEnabledCheckBox.isSelected();
        if (resetEnabled && statusOverrideCode != null) {
            System.err.println("[ChaosRulesView] Warning: Both connection reset and status override are enabled. Reset takes priority.");
        }

        try {
            ChaosRule updated = new ChaosRule(
                    selectedRuleId,
                    pattern,
                    latencyMs,
                    statusOverrideCode,
                    resetEnabled,
                    enabledCheckBox.isSelected()
            );

            if (AppContext.getChaosRuleStore() != null) {
                AppContext.getChaosRuleStore().updateRule(updated);
            }

            refreshFromStore();
            handleClearForm();
        } catch (Exception ex) {
            String msg = "Failed to save chaos rule changes: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleDeleteSelected() {
        clearError();
        ChaosRule selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                if (AppContext.getChaosRuleStore() != null) {
                    AppContext.getChaosRuleStore().removeRule(selected.getId());
                } else {
                    rulesList.remove(selected);
                }
                refreshFromStore();
                handleClearForm();
            } catch (Exception ex) {
                String msg = "Failed to delete chaos rule: " + ex.getMessage();
                showError(msg);
                AlertHelper.showError("Error", msg);
            }
        } else {
            String msg = "Please select a chaos rule from the table to delete.";
            showError(msg);
            AlertHelper.showError("Selection Required", msg);
        }
    }

    private void populateForm(ChaosRule rule) {
        selectedRuleId = rule.getId();
        routePatternField.setText(rule.getRoutePattern());
        latencyField.setText(String.valueOf(rule.getLatencyMs()));
        statusOverrideField.setText(rule.getStatusOverrideCode() != null ? String.valueOf(rule.getStatusOverrideCode()) : "");
        resetEnabledCheckBox.setSelected(rule.isConnectionResetEnabled());
        enabledCheckBox.setSelected(rule.isEnabled());
        saveChangesButton.setDisable(false);
        formTitle.setText("Edit Chaos Rule (ID: " + rule.getId() + ")");
        clearError();
    }

    public void handleClearForm() {
        selectedRuleId = null;
        tableView.getSelectionModel().clearSelection();
        routePatternField.clear();
        latencyField.setText("0");
        statusOverrideField.clear();
        resetEnabledCheckBox.setSelected(false);
        enabledCheckBox.setSelected(true);
        saveChangesButton.setDisable(true);
        formTitle.setText("Add New Chaos Rule");
        clearError();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public TableView<ChaosRule> getTableView() {
        return tableView;
    }

    public ObservableList<ChaosRule> getRulesList() {
        return rulesList;
    }

    public TextField getRoutePatternField() {
        return routePatternField;
    }

    public TextField getLatencyField() {
        return latencyField;
    }

    public TextField getStatusOverrideField() {
        return statusOverrideField;
    }

    public CheckBox getResetEnabledCheckBox() {
        return resetEnabledCheckBox;
    }

    public CheckBox getEnabledCheckBox() {
        return enabledCheckBox;
    }

    public Button getAddRuleButton() {
        return addRuleButton;
    }

    public Button getSaveChangesButton() {
        return saveChangesButton;
    }

    public Button getDeleteSelectedButton() {
        return deleteSelectedButton;
    }

    public Button getClearFormButton() {
        return clearFormButton;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }

    public Integer getSelectedRuleId() {
        return selectedRuleId;
    }
}
