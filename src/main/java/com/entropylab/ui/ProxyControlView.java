package com.entropylab.ui;

import com.entropylab.core.AppContext;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * View and controller for the "Proxy Control" tab.
 * Allows configuring port, starting/stopping the embedded reverse proxy,
 * and displays real-time server operational status and telemetry summary.
 */
public class ProxyControlView extends VBox {

    private final TextField portField;
    private final Button startButton;
    private final Button stopButton;
    private final Label statusLabel;
    private final Label errorLabel;

    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label portLabel;
    private final Label portHint;
    private final VBox card;
    private final HBox statusRow;
    private final Label statusIcon;

    // Stat chips
    private final Label routesCountLabel;
    private final Label chaosCountLabel;
    private final Label mocksCountLabel;
    private final Label logsCountLabel;
    private final VBox routesCard;
    private final VBox chaosCard;
    private final VBox mocksCard;
    private final VBox logsCard;

    public ProxyControlView() {
        setSpacing(24);
        setPadding(new Insets(28, 32, 28, 32));
        setAlignment(Pos.TOP_LEFT);

        // Header Section with colorful squircle icon
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label headerIcon = new Label("⚡");
        headerIcon.setStyle(
                "-fx-background-color: #dbeafe; " +
                "-fx-text-fill: #1d4ed8; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 12; " +
                "-fx-background-radius: 8px;"
        );

        VBox titleBox = new VBox(4);
        titleLabel = new Label("Reverse Proxy Server");
        subtitleLabel = new Label("Configure and control the embedded reverse proxy engine.");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        headerBox.getChildren().addAll(headerIcon, titleBox);

        // Control Card Container
        card = new VBox(20);
        card.setPadding(new Insets(24));
        card.setMaxWidth(680);

        // Port Configuration Row
        VBox portBox = new VBox(8);
        portLabel = new Label("Listening Port");

        portField = new TextField("8080");
        portField.setPromptText("e.g. 8080");
        portField.setMaxWidth(220);

        portHint = new Label("Port number between 1 and 65535.");
        portHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        portBox.getChildren().addAll(portLabel, portField, portHint, errorLabel);

        // Actions Row (Start / Stop Buttons)
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        startButton = new Button("Start Proxy");
        stopButton = new Button("Stop Proxy");

        buttonRow.getChildren().addAll(startButton, stopButton);

        // Status Indicator Row
        statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPadding(new Insets(14, 18, 14, 18));

        statusIcon = new Label("●");
        statusIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #dc2626;");

        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        statusRow.getChildren().addAll(statusIcon, statusLabel);

        // Quick Stats Dashboard Grid
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(12);
        statsGrid.setVgap(12);

        routesCard = createStatCard("ROUTES", "0", "#3b82f6", "#eff6ff", "#1e3a8a");
        routesCountLabel = (Label) routesCard.getChildren().get(1);

        chaosCard = createStatCard("CHAOS RULES", "0", "#f59e0b", "#fffbeb", "#78350f");
        chaosCountLabel = (Label) chaosCard.getChildren().get(1);

        mocksCard = createStatCard("OFFLINE MOCKS", "0", "#8b5cf6", "#f5f3ff", "#4c1d95");
        mocksCountLabel = (Label) mocksCard.getChildren().get(1);

        logsCard = createStatCard("INTERCEPTED", "0", "#10b981", "#ecfdf5", "#064e3b");
        logsCountLabel = (Label) logsCard.getChildren().get(1);

        statsGrid.add(routesCard, 0, 0);
        statsGrid.add(chaosCard, 1, 0);
        statsGrid.add(mocksCard, 2, 0);
        statsGrid.add(logsCard, 3, 0);

        card.getChildren().addAll(
                portBox,
                new Separator(),
                buttonRow,
                statusRow,
                statsGrid
        );

        getChildren().addAll(headerBox, card);

        // Apply theme styling dynamically
        applyTheme();
        ThemeManager.addListener(isDark -> applyTheme());

        // Wire event handlers
        wireEvents();

        // Sync initial state with AppContext.getProxyServer()
        syncInitialState();

        // Refresh stats
        refreshStats();
    }

    private VBox createStatCard(String title, String initialValue, String accentColor, String lightBg, String darkBg) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setPrefWidth(140);
        box.setAlignment(Pos.CENTER_LEFT);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + accentColor + ";");

        Label valLbl = new Label(initialValue);
        valLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: 800;");

        box.getChildren().addAll(titleLbl, valLbl);
        return box;
    }

    public void refreshStats() {
        try {
            if (AppContext.getProxyRouteStore() != null) {
                routesCountLabel.setText(String.valueOf(AppContext.getProxyRouteStore().getAllRoutes().size()));
            }
            if (AppContext.getChaosRuleStore() != null) {
                chaosCountLabel.setText(String.valueOf(AppContext.getChaosRuleStore().getAllRules().size()));
            }
            if (AppContext.getMockRouteStore() != null) {
                mocksCountLabel.setText(String.valueOf(AppContext.getMockRouteStore().getAllRoutes().size()));
            }
            if (AppContext.getInspectorTableModel() != null) {
                logsCountLabel.setText(String.valueOf(AppContext.getInspectorTableModel().size()));
            }
        } catch (Exception ignored) {
        }
    }

    private void applyTheme() {
        boolean dark = ThemeManager.isDarkMode();
        setStyle(ThemeManager.getViewBackground());

        titleLabel.setStyle(ThemeManager.getTitleStyle());
        subtitleLabel.setStyle(ThemeManager.getSubtitleStyle());
        card.setStyle(ThemeManager.getCardStyle("#3b82f6"));
        portLabel.setStyle(ThemeManager.getFormLabelStyle());
        portField.setStyle(ThemeManager.getFieldStyle());

        startButton.setStyle(ThemeManager.getPrimaryButtonStyle());
        stopButton.setStyle(ThemeManager.getDangerButtonStyle());

        if (dark) {
            statusRow.setStyle(
                    "-fx-background-color: #0f172a; " +
                    "-fx-background-radius: 8px; " +
                    "-fx-border-color: #1e293b; " +
                    "-fx-border-radius: 8px;"
            );
            styleStatCard(routesCard, "#1e293b", "#3b82f6", "#f1f5f9");
            styleStatCard(chaosCard, "#1e293b", "#f59e0b", "#f1f5f9");
            styleStatCard(mocksCard, "#1e293b", "#8b5cf6", "#f1f5f9");
            styleStatCard(logsCard, "#1e293b", "#10b981", "#f1f5f9");
        } else {
            statusRow.setStyle(
                    "-fx-background-color: #f8fafc; " +
                    "-fx-background-radius: 8px; " +
                    "-fx-border-color: #e2e8f0; " +
                    "-fx-border-radius: 8px;"
            );
            styleStatCard(routesCard, "#eff6ff", "#2563eb", "#0f172a");
            styleStatCard(chaosCard, "#fffbeb", "#d97706", "#0f172a");
            styleStatCard(mocksCard, "#f5f3ff", "#7c3aed", "#0f172a");
            styleStatCard(logsCard, "#ecfdf5", "#059669", "#0f172a");
        }
    }

    private void styleStatCard(VBox box, String bg, String titleColor, String valColor) {
        box.setStyle(
                "-fx-background-color: " + bg + "; " +
                "-fx-background-radius: 8px; " +
                "-fx-border-color: " + titleColor + "44; " +
                "-fx-border-radius: 8px;"
        );
        if (box.getChildren().size() > 1 && box.getChildren().get(1) instanceof Label valLbl) {
            valLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: " + valColor + ";");
        }
    }

    private void wireEvents() {
        startButton.setOnAction(e -> onStartClicked());
        stopButton.setOnAction(e -> onStopClicked());
    }

    private void syncInitialState() {
        try {
            if (AppContext.getProxyServer() != null && AppContext.getProxyServer().isRunning()) {
                int runningPort = AppContext.getProxyServer().getPort();
                portField.setText(String.valueOf(runningPort));
                setRunningState(runningPort);
            } else {
                setStoppedState();
            }
        } catch (Exception e) {
            setStoppedState();
        }
    }

    public void onStartClicked() {
        clearError();
        String text = portField.getText() != null ? portField.getText().trim() : "";
        int port;
        try {
            port = Integer.parseInt(text);
            if (port < 1 || port > 65535) {
                String errorMsg = "Port must be a valid integer between 1 and 65535.";
                showError(errorMsg);
                AlertHelper.showError("Invalid Port", errorMsg);
                return;
            }
        } catch (NumberFormatException ex) {
            String errorMsg = "Port must be a valid integer between 1 and 65535.";
            showError(errorMsg);
            AlertHelper.showError("Invalid Port", errorMsg);
            return;
        }

        // Transitional UI state
        startButton.setDisable(true);
        portField.setDisable(true);
        statusLabel.setText("Status: Starting on port " + port + "...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #d97706;");
        statusIcon.setText("●");
        statusIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #d97706;");

        // Execute in background Task to guarantee no UI freezing
        Task<Void> startTask = new Task<>() {
            @Override
            protected Void call() {
                AppContext.getProxyServer().start(port);
                return null;
            }
        };

        startTask.setOnSucceeded(event -> Platform.runLater(() -> setRunningState(port)));

        startTask.setOnFailed(event -> {
            Throwable ex = startTask.getException();
            Platform.runLater(() -> {
                setStoppedState();
                String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                showError("Failed to start proxy: " + msg);
                AlertHelper.showError("Proxy Error", "Failed to start proxy: " + msg);
            });
        });

        Thread starterThread = new Thread(startTask, "proxy-starter-task");
        starterThread.setDaemon(true);
        starterThread.start();
    }

    public void onStopClicked() {
        clearError();

        // Transitional UI state
        stopButton.setDisable(true);
        statusLabel.setText("Status: Stopping...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #d97706;");
        statusIcon.setText("●");
        statusIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #d97706;");

        // Execute in background Task to guarantee no UI freezing
        Task<Void> stopTask = new Task<>() {
            @Override
            protected Void call() {
                AppContext.getProxyServer().stop();
                return null;
            }
        };

        stopTask.setOnSucceeded(event -> Platform.runLater(this::setStoppedState));

        stopTask.setOnFailed(event -> {
            Throwable ex = stopTask.getException();
            Platform.runLater(() -> {
                setStoppedState();
                String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                showError("Failed to stop proxy: " + msg);
                AlertHelper.showError("Proxy Error", "Failed to stop proxy: " + msg);
            });
        });

        Thread stopperThread = new Thread(stopTask, "proxy-stopper-task");
        stopperThread.setDaemon(true);
        stopperThread.start();
    }

    public void setRunningState(int port) {
        statusLabel.setText("Status: Running on port " + port);
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");
        statusIcon.setText("●");
        statusIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #16a34a;");
        startButton.setDisable(true);
        stopButton.setDisable(false);
        portField.setDisable(true);
        clearError();
        com.entropylab.EntropyLabApp.updateTitle(true, port);
        refreshStats();
    }

    public void setStoppedState() {
        statusLabel.setText("Status: Stopped");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        statusIcon.setText("○");
        statusIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #dc2626;");
        startButton.setDisable(false);
        stopButton.setDisable(true);
        portField.setDisable(false);
        clearError();
        com.entropylab.EntropyLabApp.updateTitle(false, 0);
        refreshStats();
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

    public TextField getPortField() {
        return portField;
    }

    public Button getStartButton() {
        return startButton;
    }

    public Button getStopButton() {
        return stopButton;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }
}
