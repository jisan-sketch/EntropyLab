package com.entropylab;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.ui.AlertHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.entropylab.ui.ProxyControlView;
import com.entropylab.ui.RoutesView;
import com.entropylab.ui.ChaosRulesView;
import com.entropylab.ui.InspectorView;
import com.entropylab.ui.MocksView;
import com.entropylab.ui.ThemeManager;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class EntropyLabApp extends Application {

    private static Stage primaryStageInstance = null;

    public static Stage getPrimaryStage() {
        return primaryStageInstance;
    }

    public static void setPrimaryStage(Stage stage) {
        primaryStageInstance = stage;
    }

    public static Image loadAppIcon() {
        try {
            InputStream stream = EntropyLabApp.class.getResourceAsStream("/icon.png");
            if (stream != null) {
                return new Image(stream);
            }
            Path localRes = Path.of("src/main/resources/icon.png");
            if (Files.exists(localRes)) {
                return new Image(localRes.toUri().toString());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Label headerProxyStatusBadge = null;

    public static void updateTitle(boolean running, int port) {
        Runnable updateTask = () -> {
            String title = running ? "EntropyLab — Running on :" + port : "EntropyLab — Stopped";
            if (primaryStageInstance != null) {
                primaryStageInstance.setTitle(title);
            }
            if (headerProxyStatusBadge != null) {
                if (running) {
                    headerProxyStatusBadge.setText("● RUNNING :" + port);
                    headerProxyStatusBadge.setStyle(
                            "-fx-background-color: #dcfce7; " +
                            "-fx-text-fill: #15803d; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-padding: 4 10; " +
                            "-fx-background-radius: 12px; " +
                            "-fx-border-color: #86efac; " +
                            "-fx-border-radius: 12px;"
                    );
                } else {
                    headerProxyStatusBadge.setText("○ STOPPED");
                    headerProxyStatusBadge.setStyle(
                            ThemeManager.isDarkMode()
                                    ? "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #334155; -fx-border-radius: 12px;"
                                    : "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12px; -fx-border-color: #e2e8f0; -fx-border-radius: 12px;"
                    );
                }
            }
        };

        if (Platform.isFxApplicationThread()) {
            updateTask.run();
        } else {
            Platform.runLater(updateTask);
        }
    }

    public static void showAboutDialog(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About EntropyLab");
        alert.setHeaderText("EntropyLab\nA local API proxy and chaos engineering studio for developers");
        alert.setContentText("This tool runs an embedded multi-threaded HTTP server that intercepts live API traffic, injects controlled latency and synthetic errors, and streams traffic analysis to an indexed SQLite log via background workers.");
        if (owner != null && owner.getScene() != null) {
            alert.initOwner(owner);
        }
        Image icon = loadAppIcon();
        if (icon != null && alert.getDialogPane().getScene() != null && alert.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            stage.getIcons().add(icon);
        }
        AlertHelper.getAlertHandler().accept(alert);
    }

    public static MenuBar buildMenuBar(Window owner) {
        MenuBar menuBar = new MenuBar();
        updateMenuBarStyle(menuBar);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About EntropyLab");
        aboutItem.setOnAction(e -> showAboutDialog(owner));
        helpMenu.getItems().add(aboutItem);

        Menu viewMenu = new Menu("View");
        MenuItem toggleDarkItem = new MenuItem("Toggle Dark Mode (Ctrl+D)");
        toggleDarkItem.setOnAction(e -> ThemeManager.toggle());
        viewMenu.getItems().add(toggleDarkItem);

        menuBar.getMenus().addAll(helpMenu, viewMenu);

        ThemeManager.addListener(isDark -> updateMenuBarStyle(menuBar));

        return menuBar;
    }

    private static void updateMenuBarStyle(MenuBar menuBar) {
        if (ThemeManager.isDarkMode()) {
            menuBar.setStyle("-fx-background-color: #0b0f19; -fx-border-color: #1e293b; -fx-border-width: 0 0 1 0;");
        } else {
            menuBar.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
        }
    }

    public static javafx.scene.layout.HBox buildAppHeader() {
        javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(14);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new javafx.geometry.Insets(8, 20, 8, 20));

        // Brand Icon + Name
        Label logoBadge = new Label("⚡");
        logoBadge.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #4f46e5, #06b6d4); " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 9; " +
                "-fx-background-radius: 7px;"
        );

        Label brandLabel = new Label("EntropyLab");
        brandLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 800;");

        Label badgeLabel = new Label("STUDIO");
        badgeLabel.setStyle(
                "-fx-background-color: #e0e7ff; " +
                "-fx-text-fill: #4338ca; " +
                "-fx-font-size: 10px; " +
                "-fx-font-weight: 800; " +
                "-fx-padding: 2 6; " +
                "-fx-background-radius: 4px;"
        );

        javafx.scene.layout.HBox brandBox = new javafx.scene.layout.HBox(8, logoBadge, brandLabel, badgeLabel);
        brandBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);

        // Status Badge
        headerProxyStatusBadge = new Label("○ STOPPED");
        headerProxyStatusBadge.setStyle(
                "-fx-background-color: #f1f5f9; " +
                "-fx-text-fill: #64748b; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 10; " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 12px;"
        );

        // Theme Toggle Button
        Button themeBtn = new Button();
        Runnable updateThemeBtn = () -> {
            boolean isDark = ThemeManager.isDarkMode();
            themeBtn.setText(isDark ? "☀️ Light Mode" : "🌙 Dark Mode");
            themeBtn.setStyle(
                    isDark
                            ? "-fx-background-color: #1e293b; -fx-text-fill: #f1f5f9; -fx-border-color: #334155; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-padding: 5 14; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;"
                            : "-fx-background-color: #ffffff; -fx-text-fill: #334155; -fx-border-color: #cbd5e1; -fx-border-radius: 16px; -fx-background-radius: 16px; -fx-padding: 5 14; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 4, 0, 0, 1);"
            );
            if (isDark) {
                header.setStyle("-fx-background-color: #0b0f19; -fx-border-color: #1e293b; -fx-border-width: 0 0 1 0;");
                brandLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #f8fafc;");
            } else {
                header.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
                brandLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
            }
        };
        themeBtn.setOnAction(e -> ThemeManager.toggle());
        ThemeManager.addListener(isDark -> updateThemeBtn.run());

        header.getChildren().addAll(brandBox, spacer, headerProxyStatusBadge, themeBtn);
        return header;
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStageInstance = primaryStage;
        AppPaths.ensureDirectoriesExist();

        try {
            DatabaseManager dbManager = DatabaseManager.initialize();
            System.out.println("[EntropyLabApp] DatabaseManager initialized successfully.");
            SchemaInitializer.initializeSchema(dbManager.getConnection());
            AppContext.initialize();

            // M4.5: Register UiUpdateBridge with RequestLogEventDispatcher
            AppContext.getRequestLogEventDispatcher().addListener(new com.entropylab.ui.UiUpdateBridge());
            System.out.println("[EntropyLabApp] UiUpdateBridge registered with RequestLogEventDispatcher.");
        } catch (Exception e) {
            System.err.println("[EntropyLabApp] Initialization failed: " + e.getMessage());
        }

        // Set application icon
        Image appIcon = loadAppIcon();
        if (appIcon != null) {
            primaryStage.getIcons().add(appIcon);
        }

        VBox root = new VBox();
        MenuBar menuBar = buildMenuBar(primaryStage);
        javafx.scene.layout.HBox appHeader = buildAppHeader();
        TabPane tabPane = buildTabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        root.getChildren().addAll(menuBar, appHeader, tabPane);

        Scene scene = new Scene(root, 1050, 720);
        com.entropylab.ui.ThemeManager.registerScene(scene);

        scene.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == javafx.scene.input.KeyCode.D) {
                com.entropylab.ui.ThemeManager.toggle();
            }
        });

        // Set dynamic title & header status
        boolean isRunning = AppContext.getProxyServer() != null && AppContext.getProxyServer().isRunning();
        int runningPort = isRunning ? AppContext.getProxyServer().getPort() : 0;
        updateTitle(isRunning, runningPort);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static TabPane buildTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab proxyControlTab = new Tab("Proxy Control");
        proxyControlTab.setClosable(false);
        Label proxyIcon = new Label("⚡");
        proxyIcon.setStyle("-fx-text-fill: #3b82f6; -fx-font-weight: bold;");
        proxyControlTab.setGraphic(proxyIcon);
        proxyControlTab.setContent(new ProxyControlView());

        Tab routesTab = new Tab("Routes");
        routesTab.setClosable(false);
        Label routesIcon = new Label("🔀");
        routesIcon.setStyle("-fx-text-fill: #06b6d4; -fx-font-weight: bold;");
        routesTab.setGraphic(routesIcon);
        routesTab.setContent(new RoutesView());

        Tab chaosRulesTab = new Tab("Chaos Rules");
        chaosRulesTab.setClosable(false);
        Label chaosIcon = new Label("🔥");
        chaosIcon.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
        chaosRulesTab.setGraphic(chaosIcon);
        chaosRulesTab.setContent(new ChaosRulesView());

        Tab inspectorTab = new Tab("Inspector");
        inspectorTab.setClosable(false);
        Label inspectorIcon = new Label("📡");
        inspectorIcon.setStyle("-fx-text-fill: #8b5cf6; -fx-font-weight: bold;");
        inspectorTab.setGraphic(inspectorIcon);
        InspectorView inspectorView = new InspectorView();
        inspectorTab.setContent(inspectorView);
        inspectorTab.setOnSelectionChanged(e -> {
            if (inspectorTab.isSelected()) {
                inspectorView.refreshHistoricalLogs();
            }
        });

        Tab mocksTab = new Tab("Mocks");
        mocksTab.setClosable(false);
        Label mocksIcon = new Label("📦");
        mocksIcon.setStyle("-fx-text-fill: #ec4899; -fx-font-weight: bold;");
        mocksTab.setGraphic(mocksIcon);
        MocksView mocksView = new MocksView();
        mocksTab.setContent(mocksView);
        mocksTab.setOnSelectionChanged(e -> {
            if (mocksTab.isSelected()) {
                mocksView.refreshFromStore();
            }
        });

        tabPane.getTabs().addAll(
                proxyControlTab,
                routesTab,
                chaosRulesTab,
                inspectorTab,
                mocksTab
        );
        return tabPane;
    }

    public static Tab createPlaceholderTab(String tabName) {
        Tab tab = new Tab(tabName);
        tab.setClosable(false);

        javafx.scene.layout.VBox placeholderBox = new javafx.scene.layout.VBox();
        placeholderBox.setAlignment(javafx.geometry.Pos.CENTER);
        Label label = new Label(tabName);
        label.setStyle("-fx-font-size: 18px; -fx-text-fill: #777777; -fx-font-weight: bold;");
        placeholderBox.getChildren().add(label);

        tab.setContent(placeholderBox);
        return tab;
    }

    @Override
    public void stop() throws Exception {
        try {
            if (AppContext.getProxyServer() != null && AppContext.getProxyServer().isRunning()) {
                AppContext.getProxyServer().stop();
            }
        } catch (Exception ignored) {
        }
        try {
            DatabaseManager.getInstance().close();
        } catch (Exception ignored) {
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
