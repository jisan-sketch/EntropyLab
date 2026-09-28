package com.entropylab.ui;

import com.entropylab.chaos.ChaosRule;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * View and controller for the "Chaos Rules" tab.
 * Allows managing latency injection, HTTP status overrides, and connection resets.
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

    private final Label formTitle;
    private final Label errorLabel;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label patternLabel;
    private final Label latencyLabel;
    private final Label statusLabel;
    private final VBox formCard;
    private final Label rulesCountBadge;

    private boolean isRefreshing = false;
    private Integer selectedRuleId = null;

    public ChaosRulesView() {
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);

        // Header Section with colorful flame squircle
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label headerIcon = new Label("🔥");
        headerIcon.setStyle(
                "-fx-background-color: #fef3c7; " +
                "-fx-text-fill: #d97706; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 12; " +
                "-fx-background-radius: 8px;"
        );

        VBox titleBox = new VBox(4);
        titleLabel = new Label("Chaos Rules");
        subtitleLabel = new Label("Simulate latency injection, HTTP status overrides, and abrupt connection resets.");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        rulesCountBadge = new Label("0 Rules");
        rulesCountBadge.setStyle(
                "-fx-background-color: #fef3c7; " +
                "-fx-text-fill: #b45309; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 10; " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: #fde68a; " +
                "-fx-border-radius: 12px;"
        );

        headerBox.getChildren().addAll(headerIcon, titleBox, spacer, rulesCountBadge);

        // Table Setup
        rulesList = FXCollections.observableArrayList();
        tableView = new TableView<>(rulesList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<ChaosRule, String> patternCol = new TableColumn<>("Route Pattern");
        patternCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoutePattern()));
        patternCol.setMinWidth(180);
        patternCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    boolean isDark = ThemeManager.isDarkMode();
                    badge.setStyle(isDark
                            ? "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #93c5fd; -fx-background-color: #1e293b; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #3b82f644; -fx-border-radius: 4px;"
                            : "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1d4ed8; -fx-background-color: #eff6ff; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #bfdbfe; -fx-border-radius: 4px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<ChaosRule, String> latencyCol = new TableColumn<>("Latency (ms)");
        latencyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLatencyMs() + " ms"));
        latencyCol.setMaxWidth(130);
        latencyCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        latencyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label("⏱ " + item);
                    boolean isDark = ThemeManager.isDarkMode();
                    badge.setStyle(isDark
                            ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #fbbf24; -fx-background-color: #78350f44; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #f59e0b44; -fx-border-radius: 10px;"
                            : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #b45309; -fx-background-color: #fef3c7; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #fde68a; -fx-border-radius: 10px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        TableColumn<ChaosRule, String> statusCol = new TableColumn<>("Status Override");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getStatusOverrideCode() != null ? String.valueOf(cell.getValue().getStatusOverrideCode()) : "-"
        ));
        statusCol.setMaxWidth(130);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if ("-".equals(item)) {
                    setText("-");
                    setGraphic(null);
                    setAlignment(Pos.CENTER);
                } else {
                    Label badge = new Label("⚡ " + item);
                    boolean isDark = ThemeManager.isDarkMode();
                    badge.setStyle(isDark
                            ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #f87171; -fx-background-color: #7f1d1d44; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #ef444444; -fx-border-radius: 10px;"
                            : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #b91c1c; -fx-background-color: #fee2e2; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #fecaca; -fx-border-radius: 10px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<ChaosRule, String> resetCol = new TableColumn<>("Reset Enabled");
        resetCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isConnectionResetEnabled() ? "Yes" : "No"));
        resetCol.setMaxWidth(120);
        resetCol.setStyle("-fx-alignment: CENTER;");
        resetCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if ("Yes".equals(item)) {
                    Label badge = new Label("💥 Reset");
                    boolean isDark = ThemeManager.isDarkMode();
                    badge.setStyle(isDark
                            ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #fda4af; -fx-background-color: #88133744; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #f43f5e44; -fx-border-radius: 10px;"
                            : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #be123c; -fx-background-color: #ffe4e6; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #fecdd3; -fx-border-radius: 10px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                } else {
                    setText("No");
                    setGraphic(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<ChaosRule, String> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isEnabled() ? "Yes" : "No"));
        enabledCol.setMaxWidth(100);
        enabledCol.setStyle("-fx-alignment: CENTER;");
        enabledCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label pill = new Label("Yes".equals(item) ? "● Active" : "○ Inactive");
                    boolean isDark = ThemeManager.isDarkMode();
                    if ("Yes".equals(item)) {
                        pill.setStyle(isDark
                                ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #4ade80; -fx-background-color: #064e3b; -fx-padding: 3 8; -fx-background-radius: 10px; -fx-border-color: #059669; -fx-border-radius: 10px;"
                                : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #16a34a; -fx-background-color: #dcfce7; -fx-padding: 3 8; -fx-background-radius: 10px; -fx-border-color: #86efac; -fx-border-radius: 10px;"
                        );
                    } else {
                        pill.setStyle(isDark
                                ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; -fx-background-color: #1e293b; -fx-padding: 3 8; -fx-background-radius: 10px; -fx-border-color: #334155; -fx-border-radius: 10px;"
                                : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b; -fx-background-color: #f1f5f9; -fx-padding: 3 8; -fx-background-radius: 10px; -fx-border-color: #cbd5e1; -fx-border-radius: 10px;"
                        );
                    }
                    setGraphic(pill);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tableView.getColumns().addAll(patternCol, latencyCol, statusCol, resetCol, enabledCol);
        tableView.setPlaceholder(new Label("No chaos rules configured yet. Add one below."));

        // Form Card Container
        formCard = new VBox(14);
        formCard.setPadding(new Insets(18));

        formTitle = new Label("Add New Chaos Rule");

        // Form Input Fields Row
        HBox inputsRow = new HBox(12);
        inputsRow.setAlignment(Pos.CENTER_LEFT);

        VBox patternBox = new VBox(4);
        patternLabel = new Label("Route Pattern");
        routePatternField = new TextField();
        routePatternField.setPromptText("e.g. /api/slow/*");
        routePatternField.setPrefWidth(200);
        patternBox.getChildren().addAll(patternLabel, routePatternField);
        HBox.setHgrow(patternBox, Priority.ALWAYS);

        VBox latencyBox = new VBox(4);
        latencyLabel = new Label("Latency (ms)");
        latencyField = new TextField("0");
        latencyField.setPromptText("0");
        latencyField.setPrefWidth(110);
        latencyBox.getChildren().addAll(latencyLabel, latencyField);

        VBox statusBox = new VBox(4);
        statusLabel = new Label("Status Override");
        statusOverrideField = new TextField();
        statusOverrideField.setPromptText("e.g. 500 (optional)");
        statusOverrideField.setPrefWidth(140);
        statusBox.getChildren().addAll(statusLabel, statusOverrideField);

        VBox resetBox = new VBox(4);
        resetBox.setAlignment(Pos.BOTTOM_LEFT);
        resetEnabledCheckBox = new CheckBox("Reset Enabled");
        resetEnabledCheckBox.setSelected(false);
        resetBox.getChildren().add(resetEnabledCheckBox);

        VBox enabledBox = new VBox(4);
        enabledBox.setAlignment(Pos.BOTTOM_LEFT);
        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        enabledBox.getChildren().add(enabledCheckBox);

        inputsRow.getChildren().addAll(patternBox, latencyBox, statusBox, resetBox, enabledBox);

        // Buttons and Error Row
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        addRuleButton = new Button("Add Rule");
        saveChangesButton = new Button("Save Changes");
        saveChangesButton.setDisable(true);
        deleteSelectedButton = new Button("Delete Selected");
        deleteSelectedButton.setDisable(true);
        clearFormButton = new Button("Clear");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        actionsRow.getChildren().addAll(addRuleButton, saveChangesButton, deleteSelectedButton, clearFormButton, errorLabel);

        formCard.getChildren().addAll(formTitle, inputsRow, actionsRow);

        getChildren().addAll(headerBox, tableView, formCard);

        applyTheme();
        ThemeManager.addListener(isDark -> applyTheme());

        wireEvents();
        refreshFromStore();
    }

    private void applyTheme() {
        boolean dark = ThemeManager.isDarkMode();
        setStyle(ThemeManager.getViewBackground());

        titleLabel.setStyle(ThemeManager.getTitleStyle());
        subtitleLabel.setStyle(ThemeManager.getSubtitleStyle());
        formCard.setStyle(ThemeManager.getCardStyle("#f59e0b"));
        formTitle.setStyle(ThemeManager.getFormTitleStyle());
        patternLabel.setStyle(ThemeManager.getFormLabelStyle());
        latencyLabel.setStyle(ThemeManager.getFormLabelStyle());
        statusLabel.setStyle(ThemeManager.getFormLabelStyle());

        routePatternField.setStyle(ThemeManager.getFieldStyle());
        latencyField.setStyle(ThemeManager.getFieldStyle());
        statusOverrideField.setStyle(ThemeManager.getFieldStyle());

        String checkStyle = dark
                ? "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9; -fx-padding: 0 0 8 0;"
                : "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 0 0 8 0;";
        resetEnabledCheckBox.setStyle(checkStyle);
        enabledCheckBox.setStyle(checkStyle);

        addRuleButton.setStyle(ThemeManager.getWarningButtonStyle());
        saveChangesButton.setStyle(ThemeManager.getSuccessButtonStyle());
        deleteSelectedButton.setStyle(ThemeManager.getDangerButtonStyle());
        clearFormButton.setStyle(ThemeManager.getSecondaryButtonStyle());

        if (dark) {
            rulesCountBadge.setStyle(
                    "-fx-background-color: #78350f44; -fx-text-fill: #fbbf24; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #f59e0b44; -fx-border-radius: 12px;"
            );
        } else {
            rulesCountBadge.setStyle(
                    "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #fde68a; -fx-border-radius: 12px;"
            );
        }

        tableView.refresh();
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
            rulesCountBadge.setText(rulesList.size() + (rulesList.size() == 1 ? " Rule" : " Rules"));
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
        deleteSelectedButton.setDisable(false);
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
        deleteSelectedButton.setDisable(true);
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
