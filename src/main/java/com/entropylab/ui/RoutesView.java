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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * View and controller for the "Routes" tab.
 * Allows viewing, adding, editing, and deleting upstream proxy routes.
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

    private final Label formTitle;
    private final Label errorLabel;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label patternLabel;
    private final Label targetLabel;
    private final VBox formCard;
    private final Label routesCountBadge;

    private Integer selectedRouteId = null;
    private boolean isRefreshing = false;

    public RoutesView() {
        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);

        // Header Section with colorful squircle icon
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label headerIcon = new Label("🔀");
        headerIcon.setStyle(
                "-fx-background-color: #cffafe; " +
                "-fx-text-fill: #0891b2; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 12; " +
                "-fx-background-radius: 8px;"
        );

        VBox titleBox = new VBox(4);
        titleLabel = new Label("Proxy Routes");
        subtitleLabel = new Label("Manage upstream routing rules and target service mappings.");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        routesCountBadge = new Label("0 Routes");
        routesCountBadge.setStyle(
                "-fx-background-color: #e0f2fe; " +
                "-fx-text-fill: #0369a1; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 10; " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: #bae6fd; " +
                "-fx-border-radius: 12px;"
        );

        headerBox.getChildren().addAll(headerIcon, titleBox, spacer, routesCountBadge);

        // Table Setup
        routesList = FXCollections.observableArrayList();
        tableView = new TableView<>(routesList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<ProxyRoute, String> patternCol = new TableColumn<>("Route Pattern");
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

        TableColumn<ProxyRoute, String> targetCol = new TableColumn<>("Target Base URL");
        targetCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTargetBaseUrl()));
        targetCol.setMinWidth(280);
        targetCol.setCellFactory(col -> new TableCell<>() {
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
                            ? "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-text-fill: #5eead4; -fx-background-color: #132f38; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #0d948844; -fx-border-radius: 4px;"
                            : "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-text-fill: #0f766e; -fx-background-color: #f0fdfa; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #99f6e4; -fx-border-radius: 4px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<ProxyRoute, String> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isEnabled() ? "Yes" : "No"));
        enabledCol.setMaxWidth(110);
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

        tableView.getColumns().addAll(patternCol, targetCol, enabledCol);
        tableView.setPlaceholder(new Label("No proxy routes configured yet. Add one below."));

        // Form Card Container
        formCard = new VBox(14);
        formCard.setPadding(new Insets(18));

        formTitle = new Label("Add New Route");

        // Form Input Fields
        HBox inputsRow = new HBox(12);
        inputsRow.setAlignment(Pos.CENTER_LEFT);

        VBox patternBox = new VBox(4);
        patternLabel = new Label("Route Pattern");
        routePatternField = new TextField();
        routePatternField.setPromptText("e.g. /api/*");
        routePatternField.setPrefWidth(220);
        patternBox.getChildren().addAll(patternLabel, routePatternField);
        HBox.setHgrow(patternBox, Priority.ALWAYS);

        VBox targetBox = new VBox(4);
        targetLabel = new Label("Target Base URL");
        targetUrlField = new TextField();
        targetUrlField.setPromptText("e.g. http://localhost:8000");
        targetUrlField.setPrefWidth(300);
        targetBox.getChildren().addAll(targetLabel, targetUrlField);
        HBox.setHgrow(targetBox, Priority.ALWAYS);

        VBox enabledBox = new VBox(4);
        enabledBox.setAlignment(Pos.BOTTOM_LEFT);
        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        enabledCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 0 0 8 0;");
        enabledBox.getChildren().add(enabledCheckBox);

        inputsRow.getChildren().addAll(patternBox, targetBox, enabledBox);

        // Buttons and Error Row
        HBox actionsRow = new HBox(10);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        addRouteButton = new Button("Add Route");
        saveChangesButton = new Button("Save Changes");
        saveChangesButton.setDisable(true);
        deleteSelectedButton = new Button("Delete Selected");
        deleteSelectedButton.setDisable(true);
        clearFormButton = new Button("Clear");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        actionsRow.getChildren().addAll(addRouteButton, saveChangesButton, deleteSelectedButton, clearFormButton, errorLabel);

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
        formCard.setStyle(ThemeManager.getCardStyle("#06b6d4"));
        formTitle.setStyle(ThemeManager.getFormTitleStyle());
        patternLabel.setStyle(ThemeManager.getFormLabelStyle());
        targetLabel.setStyle(ThemeManager.getFormLabelStyle());

        routePatternField.setStyle(ThemeManager.getFieldStyle());
        targetUrlField.setStyle(ThemeManager.getFieldStyle());

        enabledCheckBox.setStyle(
                dark
                        ? "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9; -fx-padding: 0 0 8 0;"
                        : "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 0 0 8 0;"
        );

        addRouteButton.setStyle(ThemeManager.getPrimaryButtonStyle());
        saveChangesButton.setStyle(ThemeManager.getSuccessButtonStyle());
        deleteSelectedButton.setStyle(ThemeManager.getDangerButtonStyle());
        clearFormButton.setStyle(ThemeManager.getSecondaryButtonStyle());

        if (dark) {
            routesCountBadge.setStyle(
                    "-fx-background-color: #132f38; -fx-text-fill: #38bdf8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #0369a1; -fx-border-radius: 12px;"
            );
        } else {
            routesCountBadge.setStyle(
                    "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #bae6fd; -fx-border-radius: 12px;"
            );
        }

        tableView.refresh();
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
            routesCountBadge.setText(routesList.size() + (routesList.size() == 1 ? " Route" : " Routes"));
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
            String msg = "Failed to update route: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleDeleteSelected() {
        clearError();
        ProxyRoute selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null && selectedRouteId != null) {
            final int idToFind = selectedRouteId;
            selected = routesList.stream().filter(r -> r.getId() == idToFind).findFirst().orElse(null);
        }

        if (selected == null) {
            return;
        }

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
    }

    public void handleClearForm() {
        selectedRouteId = null;
        routePatternField.clear();
        targetUrlField.clear();
        enabledCheckBox.setSelected(true);

        formTitle.setText("Add New Route");
        addRouteButton.setDisable(false);
        saveChangesButton.setDisable(true);
        deleteSelectedButton.setDisable(true);
        clearError();

        if (tableView.getSelectionModel().getSelectedItem() != null) {
            tableView.getSelectionModel().clearSelection();
        }
    }

    private void populateForm(ProxyRoute route) {
        selectedRouteId = route.getId();
        routePatternField.setText(route.getRoutePattern());
        targetUrlField.setText(route.getTargetBaseUrl());
        enabledCheckBox.setSelected(route.isEnabled());

        formTitle.setText("Edit Route (ID: " + route.getId() + ")");
        addRouteButton.setDisable(true);
        saveChangesButton.setDisable(false);
        deleteSelectedButton.setDisable(false);
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
}
