package com.entropylab.ui;

import javafx.application.Platform;
import javafx.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Manages active theme state (Light vs Dark mode), persistence,
 * and dynamic styling for EntropyLab.
 */
public class ThemeManager {

    private static final String PREF_DARK_MODE = "dark_mode_enabled";
    private static final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);

    private static boolean darkMode = false;
    private static final List<Consumer<Boolean>> listeners = new ArrayList<>();
    private static Scene currentScene = null;

    static {
        try {
            darkMode = prefs.getBoolean(PREF_DARK_MODE, false);
        } catch (Exception e) {
            darkMode = false;
        }
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void setDarkMode(boolean dark) {
        if (darkMode != dark) {
            darkMode = dark;
            try {
                prefs.putBoolean(PREF_DARK_MODE, dark);
                prefs.flush();
            } catch (Exception ignored) {
            }
            notifyListeners();
        }
    }

    public static void toggle() {
        setDarkMode(!darkMode);
    }

    public static void registerScene(Scene scene) {
        currentScene = scene;
        applyTheme(scene);
    }

    public static void addListener(Consumer<Boolean> listener) {
        listeners.add(listener);
        // Immediately invoke for initial setup
        listener.accept(darkMode);
    }

    public static void removeListener(Consumer<Boolean> listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        if (Platform.isFxApplicationThread()) {
            if (currentScene != null) {
                applyTheme(currentScene);
            }
            for (Consumer<Boolean> listener : new ArrayList<>(listeners)) {
                listener.accept(darkMode);
            }
        } else {
            Platform.runLater(() -> {
                if (currentScene != null) {
                    applyTheme(currentScene);
                }
                for (Consumer<Boolean> listener : new ArrayList<>(listeners)) {
                    listener.accept(darkMode);
                }
            });
        }
    }

    public static void applyTheme(Scene scene) {
        if (scene == null) return;

        // Ensure stylesheet is loaded
        String cssUrl = ThemeManager.class.getResource("/styles.css") != null
                ? ThemeManager.class.getResource("/styles.css").toExternalForm()
                : null;
        if (cssUrl != null && !scene.getStylesheets().contains(cssUrl)) {
            scene.getStylesheets().add(cssUrl);
        }

        if (scene.getRoot() != null) {
            scene.getRoot().getStyleClass().removeAll("theme-light", "theme-dark");
            scene.getRoot().getStyleClass().add(darkMode ? "theme-dark" : "theme-light");
        }
    }

    // --------------------------------------------------------------------------
    // Dynamic Style Tokens & Helpers
    // --------------------------------------------------------------------------

    public static String getViewBackground() {
        return darkMode ? "-fx-background-color: #0b0f19;" : "-fx-background-color: #f8fafc;";
    }

    public static String getTitleStyle() {
        return darkMode
                ? "-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #f1f5f9;"
                : "-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #0f172a;";
    }

    public static String getSubtitleStyle() {
        return darkMode
                ? "-fx-font-size: 13px; -fx-text-fill: #94a3b8;"
                : "-fx-font-size: 13px; -fx-text-fill: #64748b;";
    }

    public static String getCardStyle() {
        return getCardStyle(null);
    }

    public static String getCardStyle(String topAccentColor) {
        if (darkMode) {
            String border = topAccentColor != null
                    ? "-fx-border-color: " + topAccentColor + " #1e293b #1e293b #1e293b; -fx-border-width: 3 1 1 1;"
                    : "-fx-border-color: #1e293b; -fx-border-width: 1px;";
            return "-fx-background-color: #111827; " +
                   "-fx-background-radius: 10px; " +
                   "-fx-border-radius: 10px; " +
                   border +
                   "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.45), 14, 0, 0, 4);";
        } else {
            String border = topAccentColor != null
                    ? "-fx-border-color: " + topAccentColor + " #e2e8f0 #e2e8f0 #e2e8f0; -fx-border-width: 3 1 1 1;"
                    : "-fx-border-color: #e2e8f0; -fx-border-width: 1px;";
            return "-fx-background-color: #ffffff; " +
                   "-fx-background-radius: 10px; " +
                   "-fx-border-radius: 10px; " +
                   border +
                   "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.04), 12, 0, 0, 3);";
        }
    }

    public static String getFieldStyle() {
        if (darkMode) {
            return "-fx-font-size: 13px; " +
                   "-fx-padding: 8 12; " +
                   "-fx-background-color: #0f172a; " +
                   "-fx-text-fill: #f1f5f9; " +
                   "-fx-prompt-text-fill: #64748b; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-border-color: #334155; " +
                   "-fx-border-radius: 6px;";
        } else {
            return "-fx-font-size: 13px; " +
                   "-fx-padding: 8 12; " +
                   "-fx-background-color: #ffffff; " +
                   "-fx-text-fill: #0f172a; " +
                   "-fx-prompt-text-fill: #94a3b8; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-border-color: #cbd5e1; " +
                   "-fx-border-radius: 6px;";
        }
    }

    public static String getReadOnlyFieldStyle() {
        if (darkMode) {
            return "-fx-font-size: 13px; " +
                   "-fx-padding: 8 12; " +
                   "-fx-background-color: #0b0f19; " +
                   "-fx-text-fill: #94a3b8; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-border-color: #1e293b; " +
                   "-fx-border-radius: 6px;";
        } else {
            return "-fx-font-size: 13px; " +
                   "-fx-padding: 8 12; " +
                   "-fx-background-color: #f8fafc; " +
                   "-fx-text-fill: #475569; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-border-color: #e2e8f0; " +
                   "-fx-border-radius: 6px;";
        }
    }

    public static String getFormLabelStyle() {
        return darkMode
                ? "-fx-font-size: 12px; -fx-text-fill: #94a3b8; -fx-font-weight: bold;"
                : "-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-weight: bold;";
    }

    public static String getFormTitleStyle() {
        return darkMode
                ? "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9;"
                : "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1e293b;";
    }

    public static String getCodeAreaStyle() {
        if (darkMode) {
            return "-fx-font-family: 'Consolas', 'Fira Code', 'JetBrains Mono', 'Courier New', monospace; " +
                   "-fx-font-size: 12px; " +
                   "-fx-text-fill: #38bdf8; " +
                   "-fx-control-inner-background: #090d16; " +
                   "-fx-background-color: #090d16; " +
                   "-fx-border-color: #1e293b; " +
                   "-fx-border-radius: 6px; " +
                   "-fx-background-radius: 6px;";
        } else {
            return "-fx-font-family: 'Consolas', 'Fira Code', 'JetBrains Mono', 'Courier New', monospace; " +
                   "-fx-font-size: 12px; " +
                   "-fx-text-fill: #0369a1; " +
                   "-fx-control-inner-background: #0f172a; " +
                   "-fx-background-color: #0f172a; " +
                   "-fx-border-color: #334155; " +
                   "-fx-border-radius: 6px; " +
                   "-fx-background-radius: 6px;";
        }
    }

    public static String getPrimaryButtonStyle() {
        return "-fx-background-color: linear-gradient(to bottom, #4f46e5, #4338ca); " +
               "-fx-text-fill: #ffffff; " +
               "-fx-font-size: 13px; " +
               "-fx-font-weight: bold; " +
               "-fx-padding: 8 18; " +
               "-fx-background-radius: 6px; " +
               "-fx-cursor: hand; " +
               "-fx-effect: dropshadow(gaussian, rgba(79, 70, 229, 0.35), 8, 0, 0, 2);";
    }

    public static String getSuccessButtonStyle() {
        return "-fx-background-color: linear-gradient(to bottom, #10b981, #059669); " +
               "-fx-text-fill: #ffffff; " +
               "-fx-font-size: 13px; " +
               "-fx-font-weight: bold; " +
               "-fx-padding: 8 18; " +
               "-fx-background-radius: 6px; " +
               "-fx-cursor: hand; " +
               "-fx-effect: dropshadow(gaussian, rgba(16, 185, 129, 0.35), 8, 0, 0, 2);";
    }

    public static String getWarningButtonStyle() {
        return "-fx-background-color: linear-gradient(to bottom, #f59e0b, #d97706); " +
               "-fx-text-fill: #ffffff; " +
               "-fx-font-size: 13px; " +
               "-fx-font-weight: bold; " +
               "-fx-padding: 8 18; " +
               "-fx-background-radius: 6px; " +
               "-fx-cursor: hand; " +
               "-fx-effect: dropshadow(gaussian, rgba(245, 158, 11, 0.35), 8, 0, 0, 2);";
    }

    public static String getPurpleButtonStyle() {
        return "-fx-background-color: linear-gradient(to bottom, #8b5cf6, #7c3aed); " +
               "-fx-text-fill: #ffffff; " +
               "-fx-font-size: 13px; " +
               "-fx-font-weight: bold; " +
               "-fx-padding: 8 18; " +
               "-fx-background-radius: 6px; " +
               "-fx-cursor: hand; " +
               "-fx-effect: dropshadow(gaussian, rgba(139, 92, 246, 0.35), 8, 0, 0, 2);";
    }

    public static String getDangerButtonStyle() {
        if (darkMode) {
            return "-fx-background-color: linear-gradient(to bottom, #7f1d1d, #991b1b); " +
                   "-fx-text-fill: #fecdd3; " +
                   "-fx-font-size: 13px; " +
                   "-fx-font-weight: bold; " +
                   "-fx-padding: 8 18; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-cursor: hand; " +
                   "-fx-border-color: #f43f5e; " +
                   "-fx-border-radius: 6px;";
        } else {
            return "-fx-background-color: #fee2e2; " +
                   "-fx-text-fill: #dc2626; " +
                   "-fx-font-size: 13px; " +
                   "-fx-font-weight: bold; " +
                   "-fx-padding: 8 18; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-cursor: hand; " +
                   "-fx-border-color: #fca5a5; " +
                   "-fx-border-radius: 6px;";
        }
    }

    public static String getSecondaryButtonStyle() {
        if (darkMode) {
            return "-fx-background-color: #1e293b; " +
                   "-fx-text-fill: #cbd5e1; " +
                   "-fx-font-size: 13px; " +
                   "-fx-font-weight: bold; " +
                   "-fx-padding: 8 16; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-border-color: #334155; " +
                   "-fx-border-radius: 6px; " +
                   "-fx-cursor: hand;";
        } else {
            return "-fx-background-color: #ffffff; " +
                   "-fx-text-fill: #475569; " +
                   "-fx-font-size: 13px; " +
                   "-fx-font-weight: bold; " +
                   "-fx-padding: 8 16; " +
                   "-fx-background-radius: 6px; " +
                   "-fx-border-color: #cbd5e1; " +
                   "-fx-border-radius: 6px; " +
                   "-fx-cursor: hand;";
        }
    }
}
