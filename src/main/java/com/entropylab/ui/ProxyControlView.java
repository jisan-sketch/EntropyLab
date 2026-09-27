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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * View and controller for the "Proxy Control" tab.
 * Allows configuring port, starting/stopping the embedded reverse proxy,
 * and displays real-time server operational status.
 */
public class ProxyControlView extends VBox {

    private final TextField portField;
    private final Button startButton;
    private final Button stopButton;
    private final Label statusLabel;
    private final Label errorLabel;

    public ProxyControlView() {
        setSpacing(24);
        setPadding(new Insets(32));
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: #f8fafc;");

        // Header Section
        VBox headerBox = new VBox(6);
        Label titleLabel = new Label("Reverse Proxy Server");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Configure and control the embedded reverse proxy engine.");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Control Card Container
        VBox card = new VBox(20);
        card.setPadding(new Insets(24));
        card.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-background-radius: 8px; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 8px; " +
                "-fx-border-width: 1px;"
        );
        card.setMaxWidth(600);

        // Port Configuration Row
        VBox portBox = new VBox(8);
        Label portLabel = new Label("Listening Port");
        portLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #334155;");

        portField = new TextField("8080");
        portField.setPromptText("e.g. 8080");
        portField.setMaxWidth(200);
        portField.setStyle(
                "-fx-font-size: 14px; " +
                "-fx-padding: 8 12; " +
                "-fx-background-radius: 6px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 6px;"
        );

        Label portHint = new Label("Port number between 1 and 65535.");
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
        startButton.setStyle(
                "-fx-background-color: #2563eb; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 9 20; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );

        stopButton = new Button("Stop Proxy");
        stopButton.setStyle(
                "-fx-background-color: #fee2e2; " +
                "-fx-text-fill: #dc2626; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 9 20; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand; " +
                "-fx-border-color: #fca5a5; " +
                "-fx-border-radius: 6px;"
        );

        buttonRow.getChildren().addAll(startButton, stopButton);

        // Status Indicator Row
        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPadding(new Insets(12, 16, 12, 16));
        statusRow.setStyle(
                "-fx-background-color: #f8fafc; " +
                "-fx-background-radius: 6px; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 6px;"
        );

        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        statusRow.getChildren().add(statusLabel);

        card.getChildren().addAll(
                portBox,
                new Separator(),
                buttonRow,
                statusRow
        );

        getChildren().addAll(headerBox, card);

        // Wire event handlers
        wireEvents();

        // Sync initial state with AppContext.getProxyServer()
        syncInitialState();
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
        startButton.setDisable(true);
        stopButton.setDisable(false);
        portField.setDisable(true);
        clearError();
        com.entropylab.EntropyLabApp.updateTitle(true, port);
    }

    public void setStoppedState() {
        statusLabel.setText("Status: Stopped");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
        startButton.setDisable(false);
        stopButton.setDisable(true);
        portField.setDisable(false);
        com.entropylab.EntropyLabApp.updateTitle(false, 0);
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
