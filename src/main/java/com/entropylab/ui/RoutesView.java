package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.routes.ProxyRoute;
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
 * View and controller for the "Routes" tab.
 * Displays configured proxy routes from ProxyRouteStore in a TableView
 * and provides forms to add, edit, and delete routes.
 */
public class RoutesView extends VBox {

    private final TableView<ProxyRoute> tableView;
    private final ObservableList<ProxyRoute> routesList;

    private final TextField routePatternField;
    private final TextField targetUrlField;
    private final CheckBox enabledCheckBox;
    private final Button addRouteButton;
    private final Button saveChangesButton;
    private final Button deleteSelectedButton;
    private final Button clearFormButton;
    private final Label errorLabel;
    private final Label formTitle;

    private boolean isRefreshing = false;
    private Integer selectedRouteId = null;

    public RoutesView() {
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: #f8fafc;");

        // Header Section
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("Proxy Routes");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Manage upstream routing rules and target service mappings.");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Table Setup
        routesList = FXCollections.observableArrayList();
        tableView = new TableView<>(routesList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px;"
        );

        TableColumn<ProxyRoute, String> patternCol = new TableColumn<>("Route Pattern");
        patternCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoutePattern()));
        patternCol.setMinWidth(180);

        TableColumn<ProxyRoute, String> targetCol = new TableColumn<>("Target Base URL");
        targetCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTargetBaseUrl()));
        targetCol.setMinWidth(280);

        TableColumn<ProxyRoute, String> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isEnabled() ? "Yes" : "No"));
        enabledCol.setMaxWidth(100);
        enabledCol.setStyle("-fx-alignment: CENTER;");

        tableView.getColumns().addAll(patternCol, targetCol, enabledCol);
        tableView.setPlaceholder(new Label("No proxy routes configured yet. Add one below."));

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

        formTitle = new Label("Add New Route");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // Form Input Fields
        HBox inputsRow = new HBox(12);
        inputsRow.setAlignment(Pos.CENTER_LEFT);

        VBox patternBox = new VBox(4);
        Label patternLabel = new Label("Route Pattern");
        patternLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        routePatternField = new TextField();
        routePatternField.setPromptText("e.g. /api/*");
        routePatternField.setPrefWidth(220);
        routePatternField.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-padding: 7 10; " +
                "-fx-background-radius: 5px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 5px;"
        );
        patternBox.getChildren().addAll(patternLabel, routePatternField);
        HBox.setHgrow(patternBox, Priority.ALWAYS);

        VBox targetBox = new VBox(4);
        Label targetLabel = new Label("Target Base URL");
        targetLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        targetUrlField = new TextField();
        targetUrlField.setPromptText("e.g. http://localhost:8000");
        targetUrlField.setPrefWidth(300);
        targetUrlField.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-padding: 7 10; " +
                "-fx-background-radius: 5px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 5px;"
        );
        targetBox.getChildren().addAll(targetLabel, targetUrlField);
        HBox.setHgrow(targetBox, Priority.ALWAYS);

        VBox enabledBox = new VBox(4);
        enabledBox.setAlignment(Pos.BOTTOM_LEFT);
        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        enabledCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 0 0 8 0;");
        enabledBox.getChildren().add(enabledCheckBox);

        inputsRow.getChildren().addAll(patternBox, targetBox, enabledBox);

        // Buttons and Error Row
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        addRouteButton = new Button("Add Route");
        addRouteButton.setStyle(
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

        actionsRow.getChildren().addAll(addRouteButton, saveChangesButton, deleteSelectedButton, clearFormButton, errorLabel);

        formCard.getChildren().addAll(formTitle, inputsRow, actionsRow);

        getChildren().addAll(headerBox, tableView, formCard);

        wireEvents();
        refreshFromStore();
    }

    private void wireEvents() {
        addRouteButton.setOnAction(e -> handleAddRoute());
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
            routesList.clear();
            if (AppContext.getProxyRouteStore() != null) {
                List<ProxyRoute> allRoutes = AppContext.getProxyRouteStore().getAllRoutes();
                routesList.addAll(allRoutes);
            }
        } finally {
            isRefreshing = false;
        }
    }

    public void handleAddRoute() {
        clearError();
        String pattern = routePatternField.getText() != null ? routePatternField.getText().trim() : "";
        String target = targetUrlField.getText() != null ? targetUrlField.getText().trim() : "";

        if (pattern.isEmpty()) {
            String errorMsg = "Route Pattern cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }
        if (target.isEmpty()) {
            String errorMsg = "Target Base URL cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            String errorMsg = "Target Base URL must start with \"http://\" or \"https://\".";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        try {
            ProxyRoute route = new ProxyRoute(pattern, target, enabledCheckBox.isSelected());
            if (AppContext.getProxyRouteStore() != null) {
                AppContext.getProxyRouteStore().addRoute(route);
            } else {
                // Fallback for standalone/unit testing if AppContext not booted
                routesList.add(route);
            }

            refreshFromStore();
            handleClearForm();
        } catch (Exception ex) {
            String msg = "Failed to add route: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleSaveChanges() {
        clearError();
        if (selectedRouteId == null) {
            String errorMsg = "Please select a route from the table to edit.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        String pattern = routePatternField.getText() != null ? routePatternField.getText().trim() : "";
        String target = targetUrlField.getText() != null ? targetUrlField.getText().trim() : "";

        if (pattern.isEmpty()) {
            String errorMsg = "Route Pattern cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }
        if (target.isEmpty()) {
            String errorMsg = "Target Base URL cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            String errorMsg = "Target Base URL must start with \"http://\" or \"https://\".";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        try {
            ProxyRoute updated = new ProxyRoute(selectedRouteId, pattern, target, enabledCheckBox.isSelected());
            if (AppContext.getProxyRouteStore() != null) {
                AppContext.getProxyRouteStore().updateRoute(updated);
            }

            refreshFromStore();
            handleClearForm();
        } catch (Exception ex) {
            String msg = "Failed to save route changes: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleDeleteSelected() {
        clearError();
        ProxyRoute selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                if (AppContext.getProxyRouteStore() != null) {
                    AppContext.getProxyRouteStore().removeRoute(selected.getId());
                } else {
                    routesList.remove(selected);
                }
                refreshFromStore();
                handleClearForm();
            } catch (Exception ex) {
                String msg = "Failed to delete route: " + ex.getMessage();
                showError(msg);
                AlertHelper.showError("Error", msg);
            }
        } else {
            String msg = "Please select a route from the table to delete.";
            showError(msg);
            AlertHelper.showError("Selection Required", msg);
        }
    }

    private void populateForm(ProxyRoute route) {
        selectedRouteId = route.getId();
        routePatternField.setText(route.getRoutePattern());
        targetUrlField.setText(route.getTargetBaseUrl());
        enabledCheckBox.setSelected(route.isEnabled());
        saveChangesButton.setDisable(false);
        formTitle.setText("Edit Route (ID: " + route.getId() + ")");
        clearError();
    }

    public void handleClearForm() {
        selectedRouteId = null;
        tableView.getSelectionModel().clearSelection();
        routePatternField.clear();
        targetUrlField.clear();
        enabledCheckBox.setSelected(true);
        saveChangesButton.setDisable(true);
        formTitle.setText("Add New Route");
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

    public TableView<ProxyRoute> getTableView() {
        return tableView;
    }

    public ObservableList<ProxyRoute> getRoutesList() {
        return routesList;
    }

    public TextField getRoutePatternField() {
        return routePatternField;
    }

    public TextField getTargetUrlField() {
        return targetUrlField;
    }

    public CheckBox getEnabledCheckBox() {
        return enabledCheckBox;
    }

    public Button getAddRouteButton() {
        return addRouteButton;
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

    public Integer getSelectedRouteId() {
        return selectedRouteId;
    }
}
