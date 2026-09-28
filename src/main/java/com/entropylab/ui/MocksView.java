package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import javafx.application.Platform;
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
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * View and controller for the "Mocks" tab.
 * Allows managing offline mock routes, file binding, and manual/auto-snapshot precedence.
 */
public class MocksView extends VBox {

    private static final List<java.lang.ref.WeakReference<MocksView>> activeInstances = new ArrayList<>();

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

    private final Label formTitle;
    private final Label errorLabel;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label patternLabel;
    private final Label fileLabel;
    private final VBox formCard;
    private final Label mocksCountBadge;

    private boolean isRefreshing = false;
    private Integer selectedMockId = null;

    public MocksView() {
        synchronized (activeInstances) {
            activeInstances.add(new java.lang.ref.WeakReference<>(this));
        }

        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);

        // 1. Header Section with colorful box squircle
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label headerIcon = new Label("📦");
        headerIcon.setStyle(
                "-fx-background-color: #fce7f3; " +
                "-fx-text-fill: #be185d; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 12; " +
                "-fx-background-radius: 8px;"
        );

        VBox titleBox = new VBox(4);
        titleLabel = new Label("Mock Routes");
        subtitleLabel = new Label("Configure static JSON mock responses that bypass upstream backends.");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        mocksCountBadge = new Label("0 Mocks");
        mocksCountBadge.setStyle(
                "-fx-background-color: #fce7f3; " +
                "-fx-text-fill: #9d174d; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 10; " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: #fbcfe8; " +
                "-fx-border-radius: 12px;"
        );

        headerBox.getChildren().addAll(headerIcon, titleBox, spacer, mocksCountBadge);

        // 2. Table Setup
        mocksList = FXCollections.observableArrayList();
        tableView = new TableView<>(mocksList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<MockRoute, String> patternCol = new TableColumn<>("Route Pattern");
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
                            ? "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #c084fc; -fx-background-color: #3b076444; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #8b5cf644; -fx-border-radius: 4px;"
                            : "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #7e22ce; -fx-background-color: #faf5ff; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #f3e8ff; -fx-border-radius: 4px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<MockRoute, String> fileCol = new TableColumn<>("File Path");
        fileCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getFilePath()));
        fileCol.setMinWidth(280);
        fileCol.setCellFactory(col -> new TableCell<>() {
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
                            ? "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #cbd5e1; -fx-background-color: #1e293b; -fx-padding: 3 8; -fx-background-radius: 4px;"
                            : "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #475569; -fx-background-color: #f8fafc; -fx-padding: 3 8; -fx-background-radius: 4px; -fx-border-color: #e2e8f0; -fx-border-radius: 4px;"
                    );
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<MockRoute, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getSource() != null ? cell.getValue().getSource().name() : "MANUAL"
        ));
        sourceCol.setMaxWidth(130);
        sourceCol.setStyle("-fx-alignment: CENTER;");
        sourceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label pill = new Label("AUTO_SNAPSHOT".equals(item) ? "📸 Snapshot" : "✍ Manual");
                    boolean isDark = ThemeManager.isDarkMode();
                    if ("AUTO_SNAPSHOT".equals(item)) {
                        pill.setStyle(isDark
                                ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #c084fc; -fx-background-color: #581c8744; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #a855f744; -fx-border-radius: 10px;"
                                : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7e22ce; -fx-background-color: #f3e8ff; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #d8b4fe; -fx-border-radius: 10px;"
                        );
                    } else {
                        pill.setStyle(isDark
                                ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #60a5fa; -fx-background-color: #1e3a8a44; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #3b82f644; -fx-border-radius: 10px;"
                                : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1d4ed8; -fx-background-color: #eff6ff; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #bfdbfe; -fx-border-radius: 10px;"
                        );
                    }
                    setGraphic(pill);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<MockRoute, Void> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setMaxWidth(110);
        enabledCol.setStyle("-fx-alignment: CENTER;");
        enabledCol.setCellFactory(col -> new TableCell<>() {
            private final Button toggleBtn = new Button();

            {
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
                boolean isDark = ThemeManager.isDarkMode();
                if (enabled) {
                    toggleBtn.setText("Enabled");
                    toggleBtn.setStyle(isDark
                            ? "-fx-background-color: #064e3b; -fx-text-fill: #4ade80; -fx-border-color: #059669; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;"
                            : "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-border-color: #86efac; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;"
                    );
                } else {
                    toggleBtn.setText("Disabled");
                    toggleBtn.setStyle(isDark
                            ? "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-border-color: #334155; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;"
                            : "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-border-color: #cbd5e1; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;"
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
        formCard = new VBox(14);
        formCard.setPadding(new Insets(18));

        formTitle = new Label("Add Mock Route");

        // Input Fields Row
        HBox inputsRow = new HBox(12);
        inputsRow.setAlignment(Pos.CENTER_LEFT);

        VBox patternBox = new VBox(4);
        patternLabel = new Label("Route Pattern");
        routePatternField = new TextField();
        routePatternField.setPromptText("/api/v1/resource");
        routePatternField.setPrefWidth(220);
        patternBox.getChildren().addAll(patternLabel, routePatternField);

        VBox fileBox = new VBox(4);
        HBox.setHgrow(fileBox, Priority.ALWAYS);
        fileLabel = new Label("File Path (.json)");

        HBox filePickerBox = new HBox(8);
        filePickerBox.setAlignment(Pos.CENTER_LEFT);

        filePathField = new TextField();
        filePathField.setPromptText("Select a .json mock file...");
        filePathField.setEditable(false);
        HBox.setHgrow(filePathField, Priority.ALWAYS);

        browseButton = new Button("Browse...");
        filePickerBox.getChildren().addAll(filePathField, browseButton);
        fileBox.getChildren().addAll(fileLabel, filePickerBox);

        VBox enabledBox = new VBox(4);
        enabledBox.setAlignment(Pos.BOTTOM_LEFT);
        enabledCheckBox = new CheckBox("Enabled");
        enabledCheckBox.setSelected(true);
        enabledBox.getChildren().add(enabledCheckBox);

        inputsRow.getChildren().addAll(patternBox, fileBox, enabledBox);

        // Action Buttons Row
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        addMockButton = new Button("Add Mock");
        saveChangesButton = new Button("Save Changes");
        saveChangesButton.setDisable(true);
        deleteSelectedButton = new Button("Delete Selected");
        deleteSelectedButton.setDisable(true);
        clearFormButton = new Button("Clear");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px; -fx-font-weight: bold;");

        actionRow.getChildren().addAll(addMockButton, saveChangesButton, deleteSelectedButton, clearFormButton, errorLabel);
        formCard.getChildren().addAll(formTitle, inputsRow, actionRow);

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
        formCard.setStyle(ThemeManager.getCardStyle("#8b5cf6"));
        formTitle.setStyle(ThemeManager.getFormTitleStyle());
        patternLabel.setStyle(ThemeManager.getFormLabelStyle());
        fileLabel.setStyle(ThemeManager.getFormLabelStyle());

        routePatternField.setStyle(ThemeManager.getFieldStyle());
        filePathField.setStyle(ThemeManager.getReadOnlyFieldStyle());
        browseButton.setStyle(ThemeManager.getSecondaryButtonStyle());

        enabledCheckBox.setStyle(
                dark
                        ? "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9; -fx-padding: 0 0 8 0;"
                        : "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155; -fx-padding: 0 0 8 0;"
        );

        addMockButton.setStyle(ThemeManager.getPurpleButtonStyle());
        saveChangesButton.setStyle(ThemeManager.getSuccessButtonStyle());
        deleteSelectedButton.setStyle(ThemeManager.getDangerButtonStyle());
        clearFormButton.setStyle(ThemeManager.getSecondaryButtonStyle());

        if (dark) {
            mocksCountBadge.setStyle(
                    "-fx-background-color: #581c8744; -fx-text-fill: #c084fc; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #8b5cf644; -fx-border-radius: 12px;"
            );
        } else {
            mocksCountBadge.setStyle(
                    "-fx-background-color: #fce7f3; -fx-text-fill: #9d174d; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #fbcfe8; -fx-border-radius: 12px;"
            );
        }

        tableView.refresh();
    }

    private void wireEvents() {
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

        addMockButton.setOnAction(e -> handleAddMock());
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

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                refreshFromStore();
            }
        });
    }

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

        if (Platform.isFxApplicationThread()) {
            refreshTask.run();
        } else {
            Platform.runLater(refreshTask);
        }
    }

    public void refreshFromStore() {
        if (AppContext.getMockRouteStore() == null) {
            return;
        }

        Runnable task = () -> {
            isRefreshing = true;
            try {
                mocksList.setAll(AppContext.getMockRouteStore().getAllRoutes());
                mocksCountBadge.setText(mocksList.size() + (mocksList.size() == 1 ? " Mock" : " Mocks"));
            } finally {
                isRefreshing = false;
            }
        };

        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
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
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
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
