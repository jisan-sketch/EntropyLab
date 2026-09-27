package com.entropylab.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.function.Consumer;

/**
 * Shared utility for displaying standardized JavaFX Alerts across views,
 * supporting headless and test execution environments safely without hanging.
 */
public class AlertHelper {

    private static Alert lastAlert = null;

    private static Consumer<Alert> alertHandler = alert -> {
        try {
            boolean hasShowingWindow = javafx.stage.Window.getWindows().stream()
                    .anyMatch(javafx.stage.Window::isShowing);
            if (hasShowingWindow) {
                alert.showAndWait();
            } else {
                alert.show();
                alert.close();
            }
        } catch (Exception e) {
            System.out.println("[AlertHelper] Alert display skipped: " + e.getMessage());
        }
    };

    public static void setAlertHandler(Consumer<Alert> handler) {
        alertHandler = handler;
    }

    public static Consumer<Alert> getAlertHandler() {
        return alertHandler;
    }

    public static Alert getLastAlert() {
        return lastAlert;
    }

    public static void clearLastAlert() {
        lastAlert = null;
    }

    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public static void showAlert(Alert.AlertType type, String title, String message) {
        Runnable showTask = () -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            lastAlert = alert;
            if (alertHandler != null) {
                alertHandler.accept(alert);
            }
        };

        if (Platform.isFxApplicationThread()) {
            showTask.run();
        } else {
            Platform.runLater(showTask);
        }
    }
}
