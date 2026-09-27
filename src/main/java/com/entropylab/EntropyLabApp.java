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

    public static void updateTitle(boolean running, int port) {
        Runnable updateTask = () -> {
            String title = running ? "EntropyLab — Running on :" + port : "EntropyLab — Stopped";
            if (primaryStageInstance != null) {
                primaryStageInstance.setTitle(title);
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
        menuBar.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About EntropyLab");
        aboutItem.setOnAction(e -> showAboutDialog(owner));
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().add(helpMenu);
        return menuBar;
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

        // Set dynamic title
        boolean isRunning = AppContext.getProxyServer() != null && AppContext.getProxyServer().isRunning();
        int runningPort = isRunning ? AppContext.getProxyServer().getPort() : 0;
        updateTitle(isRunning, runningPort);

        VBox root = new VBox();
        MenuBar menuBar = buildMenuBar(primaryStage);
        TabPane tabPane = buildTabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        root.getChildren().addAll(menuBar, tabPane);

        Scene scene = new Scene(root, 1000, 700);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static TabPane buildTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab proxyControlTab = new Tab("Proxy Control");
        proxyControlTab.setClosable(false);
        proxyControlTab.setContent(new ProxyControlView());

        Tab routesTab = new Tab("Routes");
        routesTab.setClosable(false);
        routesTab.setContent(new RoutesView());

        Tab chaosRulesTab = new Tab("Chaos Rules");
        chaosRulesTab.setClosable(false);
        chaosRulesTab.setContent(new ChaosRulesView());

        Tab inspectorTab = new Tab("Inspector");
        inspectorTab.setClosable(false);
        InspectorView inspectorView = new InspectorView();
        inspectorTab.setContent(inspectorView);
        inspectorTab.setOnSelectionChanged(e -> {
            if (inspectorTab.isSelected()) {
                inspectorView.refreshHistoricalLogs();
            }
        });

        Tab mocksTab = new Tab("Mocks");
        mocksTab.setClosable(false);
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
