package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * View and controller for the "Mocks" tab.
 * Displays configured static mock routes backed by MockRouteStore in a TableView
 * and provides forms to add, edit, toggle, and delete mock routes.
 */
public class MocksView extends VBox {

    private static final java.util.List<java.lang.ref.WeakReference<MocksView>> activeInstances = new java.util.ArrayList<>();

    private final TableView<MockRoute> tableView;
    private final ObservableList<MockRoute> mocksList;

    private final TextField routePatternField;
    private final TextField filePathField;
    private final Button browseButton;
    private final CheckBox enabledCheckBox;
    private final Button addMockButton;
    private final Button saveChangesButton;
    private final Button deleteSelectedButton;
    private final Button clearFormButton;
    private final Label errorLabel;
    private final Label formTitle;

    private boolean isRefreshing = false;
    private Integer selectedMockId = null;

    public MocksView() {
        synchronized (activeInstances) {
            activeInstances.add(new java.lang.ref.WeakReference<>(this));
        }

        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: #f8fafc;");

        // 1. Header Section
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("Mock Routes");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Configure static JSON mock responses that bypass upstream backends.");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // 2. Table Setup
        mocksList = FXCollections.observableArrayList();
        tableView = new TableView<>(mocksList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px;"
        );

        TableColumn<MockRoute, String> patternCol = new TableColumn<>("Route Pattern");
        patternCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getRoutePattern()));
        patternCol.setMinWidth(180);

        TableColumn<MockRoute, String> fileCol = new TableColumn<>("File Path");
        fileCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFilePath()));
        fileCol.setMinWidth(280);

        TableColumn<MockRoute, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getSource() != null ? cell.getValue().getSource().name() : "MANUAL"
        ));
        sourceCol.setMaxWidth(100);
        sourceCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<MockRoute, Void> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setMaxWidth(110);
        enabledCol.setStyle("-fx-alignment: CENTER;");
        enabledCol.setCellFactory(col -> new TableCell<>() {
            private final Button toggleBtn = new Button();

            {
                toggleBtn.setStyle("-fx-cursor: hand; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-background-radius: 4px;");
                toggleBtn.setOnAction(e -> {
                    MockRoute route = getTableView().getItems().get(getIndex());
                    if (route != null) {
                        try {
                            route.setEnabled(!route.isEnabled());
                            if (AppContext.getMockRouteStore() != null) {
                                AppContext.getMockRouteStore().updateRoute(route);
                            }
                            refreshFromStore();
                        } catch (Exception ex) {
                            String msg = "Failed to toggle mock route: " + ex.getMessage();
                            showError(msg);
                            AlertHelper.showError("Error", msg);
                        }
                    }
                });
            }

            private void updateButtonState(boolean enabled) {
                if (enabled) {
                    toggleBtn.setText("Enabled");
                    toggleBtn.setStyle(
                            "-fx-background-color: #dcfce7; " +
                            "-fx-text-fill: #15803d; " +
                            "-fx-border-color: #86efac; " +
                            "-fx-border-radius: 4px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-padding: 3 10; " +
                            "-fx-cursor: hand;"
                    );
                } else {
                    toggleBtn.setText("Disabled");
                    toggleBtn.setStyle(
                            "-fx-background-color: #f1f5f9; " +
                            "-fx-text-fill: #64748b; " +
                            "-fx-border-color: #cbd5e1; " +
                            "-fx-border-radius: 4px; " +
                            "-fx-background-radius: 4px; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-padding: 3 10; " +
                            "-fx-cursor: hand;"
                    );
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    MockRoute route = getTableView().getItems().get(getIndex());
                    updateButtonState(route.isEnabled());
                    setGraphic(toggleBtn);
                }
            }
        });

        tableView.getColumns().addAll(patternCol, fileCol, sourceCol, enabledCol);
        tableView.setPlaceholder(new Label("No mock routes configured yet. Add one below."));

        // 3. Form Card Container
        VBox formCard = new VBox(12);
        formCard.setPadding(new Insets(16));
        formCard.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 8px; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 8px; " +
                "-fx-border-width: 1px;"
        );

        formTitle = new Label("Add Mock Route");
        formTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        // Input Fields Row
        HBox inputsRow = new HBox(12);
        inputsRow.setAlignment(Pos.CENTER_LEFT);

        VBox patternBox = new VBox(4);
        Label patternLabel = new Label("Route Pattern");
        patternLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");
        routePatternField = new TextField();
        routePatternField.setPromptText("/api/v1/resource");
        routePatternField.setPrefWidth(220);
        routePatternField.setStyle("-fx-padding: 6 10; -fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px;");
        patternBox.getChildren().addAll(patternLabel, routePatternField);

        VBox fileBox = new VBox(4);
        HBox.setHgrow(fileBox, Priority.ALWAYS);
        Label fileLabel = new Label("File Path (.json)");
        fileLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;");

        HBox filePickerBox = new HBox(8);
        filePickerBox.setAlignment(Pos.CENTER_LEFT);

        filePathField = new TextField();
        filePathField.setPromptText("Select a .json mock file...");
        filePathField.setEditable(false);
        HBox.setHgrow(filePathField, Priority.ALWAYS);
        filePathField.setStyle("-fx-padding: 6 10; -fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-background-color: #f8fafc;");

        browseButton = new Button("Browse...");
        browseButton.setStyle(
                "-fx-background-color: #f1f5f9; " +
                "-fx-text-fill: #1e293b; " +
                "-fx-font-size: 12px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 6 14; " +
                "-fx-background-radius: 6px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 6px; " +
                "-fx-cursor: hand;"
        );
        filePickerBox.getChildren().addAll(filePathField, browseButton);
        fileBox.getChildren().addAll(fileLabel, filePickerBox);

        VBox enabledBox = new VBox(4);
        enabledBox.setAlignment(Pos.BOTTOM_LEFT);
        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        enabledCheckBox.setStyle("-fx-font-size: 12px; -fx-text-fill: #1e293b; -fx-padding: 6 0 6 0;");
        enabledBox.getChildren().addAll(new Label(""), enabledCheckBox);

        inputsRow.getChildren().addAll(patternBox, fileBox, enabledBox);

        // Action Buttons Row
        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        addMockButton = new Button("Add Mock");
        addMockButton.setStyle(
                "-fx-background-color: #2563eb; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 7 16; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );

        saveChangesButton = new Button("Save Changes");
        saveChangesButton.setStyle(
                "-fx-background-color: #059669; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 7 16; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );
        saveChangesButton.setDisable(true);

        deleteSelectedButton = new Button("Delete Selected");
        deleteSelectedButton.setStyle(
                "-fx-background-color: #ef4444; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 7 16; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );
        deleteSelectedButton.setDisable(true);

        clearFormButton = new Button("Clear");
        clearFormButton.setStyle(
                "-fx-background-color: #f1f5f9; " +
                "-fx-text-fill: #475569; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 7 14; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 6px;"
        );

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px; -fx-font-weight: bold;");

        actionRow.getChildren().addAll(addMockButton, saveChangesButton, deleteSelectedButton, clearFormButton, errorLabel);
        formCard.getChildren().addAll(formTitle, inputsRow, actionRow);

        getChildren().addAll(headerBox, tableView, formCard);

        wireEvents();
        refreshFromStore();
    }

    private void wireEvents() {
        // Browse button FileChooser
        browseButton.setOnAction(e -> {
            clearError();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select JSON Mock File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json")
            );

            try {
                File mocksDir = AppPaths.getMocksDir().toFile();
                if (mocksDir.exists()) {
                    fileChooser.setInitialDirectory(mocksDir);
                }
            } catch (Exception ignored) {
            }

            Window window = getScene() != null ? getScene().getWindow() : null;
            File selectedFile = fileChooser.showOpenDialog(window);
            if (selectedFile != null) {
                filePathField.setText(selectedFile.getAbsolutePath());
            }
        });

        // Add Mock Action
        addMockButton.setOnAction(e -> handleAddMock());

        // Save Changes Action
        saveChangesButton.setOnAction(e -> handleSaveChanges());

        // Delete Selected Action
        deleteSelectedButton.setOnAction(e -> handleDeleteSelected());

        // Clear Form Action
        clearFormButton.setOnAction(e -> handleClearForm());

        // Selection listener to populate form or clear
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (isRefreshing) return;
            if (newVal != null) {
                populateForm(newVal);
            } else {
                handleClearForm();
            }
        });

        // Auto-refresh when added to scene
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                refreshFromStore();
            }
        });
    }

    /**
     * Refreshes all active MocksView instances if any exist.
     */
    public static void refreshAllViews() {
        Runnable refreshTask = () -> {
            synchronized (activeInstances) {
                activeInstances.removeIf(ref -> ref.get() == null);
                for (java.lang.ref.WeakReference<MocksView> ref : activeInstances) {
                    MocksView view = ref.get();
                    if (view != null) {
                        view.refreshFromStore();
                    }
                }
            }
        };

        if (javafx.application.Platform.isFxApplicationThread()) {
            refreshTask.run();
        } else {
            javafx.application.Platform.runLater(refreshTask);
        }
    }

    /**
     * Refreshes the table's items directly from MockRouteStore.
     */
    public void refreshFromStore() {
        if (AppContext.getMockRouteStore() == null) {
            return;
        }

        Runnable task = () -> {
            isRefreshing = true;
            try {
                mocksList.setAll(AppContext.getMockRouteStore().getAllRoutes());
            } finally {
                isRefreshing = false;
            }
        };

        if (javafx.application.Platform.isFxApplicationThread()) {
            task.run();
        } else {
            javafx.application.Platform.runLater(task);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private boolean validateMockInput(String pattern, String filePath) {
        if (pattern.isEmpty()) {
            String errorMsg = "Route Pattern cannot be empty.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            routePatternField.requestFocus();
            return false;
        }

        if (filePath.isEmpty()) {
            String errorMsg = "Please select a JSON mock file.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return false;
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            String errorMsg = "Selected mock file does not exist: " + filePath;
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return false;
        }

        try {
            jsonMapper.readTree(file);
        } catch (Exception ex) {
            String errorMsg = "Selected file is not valid JSON: " + ex.getMessage();
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return false;
        }

        return true;
    }

    public void handleAddMock() {
        clearError();

        String pattern = routePatternField.getText() != null ? routePatternField.getText().trim() : "";
        String filePath = filePathField.getText() != null ? filePathField.getText().trim() : "";
        boolean enabled = enabledCheckBox.isSelected();

        if (!validateMockInput(pattern, filePath)) {
            return;
        }

        try {
            MockRoute newRoute = new MockRoute(pattern, filePath, enabled, MockSource.MANUAL);
            if (AppContext.getMockRouteStore() != null) {
                AppContext.getMockRouteStore().addRoute(newRoute);
            } else {
                mocksList.add(newRoute);
            }

            refreshFromStore();
            handleClearForm();
        } catch (Exception ex) {
            String msg = "Failed to add mock route: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleSaveChanges() {
        clearError();
        if (selectedMockId == null) {
            String errorMsg = "Please select a mock route from the table to edit.";
            showError(errorMsg);
            AlertHelper.showError("Validation Error", errorMsg);
            return;
        }

        String pattern = routePatternField.getText() != null ? routePatternField.getText().trim() : "";
        String filePath = filePathField.getText() != null ? filePathField.getText().trim() : "";
        boolean enabled = enabledCheckBox.isSelected();

        if (!validateMockInput(pattern, filePath)) {
            return;
        }

        try {
            // Preserve original source (e.g. MANUAL or AUTO)
            MockSource source = MockSource.MANUAL;
            if (AppContext.getMockRouteStore() != null) {
                for (MockRoute r : AppContext.getMockRouteStore().getAllRoutes()) {
                    if (r.getId() == selectedMockId) {
                        source = r.getSource();
                        break;
                    }
                }
            }

            MockRoute updated = new MockRoute(selectedMockId, pattern, filePath, enabled, source);
            if (AppContext.getMockRouteStore() != null) {
                AppContext.getMockRouteStore().updateRoute(updated);
            }

            refreshFromStore();
            handleClearForm();
        } catch (Exception ex) {
            String msg = "Failed to save mock route changes: " + ex.getMessage();
            showError(msg);
            AlertHelper.showError("Error", msg);
        }
    }

    public void handleDeleteSelected() {
        clearError();
        MockRoute selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                if (AppContext.getMockRouteStore() != null) {
                    AppContext.getMockRouteStore().removeRoute(selected.getId());
                } else {
                    mocksList.remove(selected);
                }
                refreshFromStore();
                handleClearForm();
            } catch (Exception ex) {
                String msg = "Failed to delete mock route: " + ex.getMessage();
                showError(msg);
                AlertHelper.showError("Error", msg);
            }
        } else {
            String msg = "Please select a mock route from the table to delete.";
            showError(msg);
            AlertHelper.showError("Selection Required", msg);
        }
    }

    private void populateForm(MockRoute route) {
        selectedMockId = route.getId();
        routePatternField.setText(route.getRoutePattern());
        filePathField.setText(route.getFilePath());
        enabledCheckBox.setSelected(route.isEnabled());
        saveChangesButton.setDisable(false);
        deleteSelectedButton.setDisable(false);
        formTitle.setText("Edit Mock Route (ID: " + route.getId() + ")");
        clearError();
    }

    public void handleClearForm() {
        selectedMockId = null;
        tableView.getSelectionModel().clearSelection();
        routePatternField.clear();
        filePathField.clear();
        enabledCheckBox.setSelected(true);
        saveChangesButton.setDisable(true);
        deleteSelectedButton.setDisable(true);
        formTitle.setText("Add Mock Route");
        clearError();
    }

    public void showError(String msg) {
        errorLabel.setText(msg);
    }

    public void clearError() {
        errorLabel.setText("");
    }

    public TableView<MockRoute> getTableView() {
        return tableView;
    }

    public ObservableList<MockRoute> getMocksList() {
        return mocksList;
    }

    public TextField getRoutePatternField() {
        return routePatternField;
    }

    public TextField getFilePathField() {
        return filePathField;
    }

    public Button getBrowseButton() {
        return browseButton;
    }

    public CheckBox getEnabledCheckBox() {
        return enabledCheckBox;
    }

    public Button getAddMockButton() {
        return addMockButton;
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
