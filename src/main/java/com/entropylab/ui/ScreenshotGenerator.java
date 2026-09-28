package com.entropylab.ui;

import com.entropylab.EntropyLabApp;
import com.entropylab.chaos.ChaosRule;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import com.entropylab.routes.ProxyRoute;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless/automated screenshot generator for EntropyLab documentation.
 * Produces crisp, production-grade PNG screenshots of key application tabs.
 */
public class ScreenshotGenerator {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Starting ScreenshotGenerator for EntropyLab ===");

        // Set custom isolated home for screenshot generation
        Path tempDir = Files.createTempDirectory("entropylab-screenshots-");
        System.setProperty("entropylab.home", tempDir.toAbsolutePath().toString());

        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // Seed rich data
        seedRealisticData();

        CountDownLatch startupLatch = new CountDownLatch(1);
        try {
            Platform.startup(startupLatch::countDown);
        } catch (IllegalStateException e) {
            startupLatch.countDown();
        }

        if (!startupLatch.await(5, TimeUnit.SECONDS)) {
            System.err.println("Failed to start JavaFX platform");
            System.exit(1);
        }

        CountDownLatch captureLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                EntropyLabApp.setPrimaryStage(stage);

                VBox root = new VBox();
                MenuBar menuBar = EntropyLabApp.buildMenuBar(stage);
                javafx.scene.layout.HBox appHeader = EntropyLabApp.buildAppHeader();
                TabPane tabPane = EntropyLabApp.buildTabPane();
                VBox.setVgrow(tabPane, Priority.ALWAYS);
                root.getChildren().addAll(menuBar, appHeader, tabPane);

                Scene scene = new Scene(root, 1140, 740);
                ThemeManager.registerScene(scene);

                // Simulate running proxy status on port 8080
                EntropyLabApp.updateTitle(true, 8080);

                stage.setScene(scene);
                stage.show();

                // Apply Dark Mode by default
                ThemeManager.setDarkMode(true);

                // Run snapshot sequence on background thread with Platform.runLater steps
                new Thread(() -> {
                    try {
                        Thread.sleep(700);

                        // 1. Capture Inspector Tab (Tab Index 3) in Dark Mode
                        Platform.runLater(() -> {
                            tabPane.getSelectionModel().select(3);
                            Tab inspectorTab = tabPane.getTabs().get(3);
                            InspectorView inspectorView = (InspectorView) inspectorTab.getContent();
                            inspectorView.refreshHistoricalLogs();

                            // Select weather request (index 5) to display rich 200 OK JSON details
                            if (inspectorView.getTableView().getItems().size() > 5) {
                                inspectorView.getTableView().getSelectionModel().select(5);
                            } else if (!inspectorView.getTableView().getItems().isEmpty()) {
                                inspectorView.getTableView().getSelectionModel().select(0);
                            }
                            inspectorView.getTableView().scrollTo(0);
                        });

                        Thread.sleep(500);

                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                saveImage(img, "docs/images/inspector-dark.png");
                                System.out.println("Saved: docs/images/inspector-dark.png");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        Thread.sleep(300);

                        // 2. Capture Chaos Rules Tab (Tab Index 2)
                        Platform.runLater(() -> {
                            tabPane.getSelectionModel().select(2);
                            Tab chaosTab = tabPane.getTabs().get(2);
                            ChaosRulesView chaosView = (ChaosRulesView) chaosTab.getContent();
                            chaosView.refreshFromStore();
                        });

                        Thread.sleep(400);

                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                saveImage(img, "docs/images/chaos-rules-dark.png");
                                System.out.println("Saved: docs/images/chaos-rules-dark.png");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        Thread.sleep(300);

                        // 3. Capture Routes Tab (Tab Index 1)
                        Platform.runLater(() -> {
                            tabPane.getSelectionModel().select(1);
                            Tab routesTab = tabPane.getTabs().get(1);
                            RoutesView routesView = (RoutesView) routesTab.getContent();
                            routesView.refreshFromStore();
                        });

                        Thread.sleep(400);

                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                saveImage(img, "docs/images/routes-dark.png");
                                System.out.println("Saved: docs/images/routes-dark.png");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        Thread.sleep(300);

                        // 4. Capture Mocks Tab (Tab Index 4)
                        Platform.runLater(() -> {
                            tabPane.getSelectionModel().select(4);
                            Tab mocksTab = tabPane.getTabs().get(4);
                            MocksView mocksView = (MocksView) mocksTab.getContent();
                            mocksView.refreshFromStore();
                        });

                        Thread.sleep(400);

                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                saveImage(img, "docs/images/mocks-dark.png");
                                System.out.println("Saved: docs/images/mocks-dark.png");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        Thread.sleep(300);

                        // 5. Capture Proxy Control Tab (Tab Index 0)
                        Platform.runLater(() -> {
                            tabPane.getSelectionModel().select(0);
                        });

                        Thread.sleep(400);

                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                saveImage(img, "docs/images/proxy-control-dark.png");
                                System.out.println("Saved: docs/images/proxy-control-dark.png");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                        Thread.sleep(300);

                        // 5. Capture Inspector in Light Mode
                        Platform.runLater(() -> {
                            ThemeManager.setDarkMode(false);
                            tabPane.getSelectionModel().select(3);
                            Tab inspectorTab = tabPane.getTabs().get(3);
                            InspectorView inspectorView = (InspectorView) inspectorTab.getContent();
                            inspectorView.refreshHistoricalLogs();
                            if (inspectorView.getTableView().getItems().size() > 5) {
                                inspectorView.getTableView().getSelectionModel().select(5);
                            }
                            inspectorView.getTableView().scrollTo(0);
                        });

                        Thread.sleep(600);

                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                saveImage(img, "docs/images/inspector-light.png");
                                System.out.println("Saved: docs/images/inspector-light.png");
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                stage.close();
                                captureLatch.countDown();
                            }
                        });

                    } catch (Exception e) {
                        e.printStackTrace();
                        captureLatch.countDown();
                    }
                }).start();

            } catch (Exception e) {
                e.printStackTrace();
                captureLatch.countDown();
            }
        });

        captureLatch.await(20, TimeUnit.SECONDS);
        Platform.exit();
        System.out.println("=== All screenshots generated successfully! ===");
        System.exit(0);
    }

    private static void saveImage(WritableImage fxImage, String relativePath) throws Exception {
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        PixelReader reader = fxImage.getPixelReader();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }

        File outFile = new File(relativePath);
        outFile.getParentFile().mkdirs();
        ImageIO.write(bufferedImage, "png", outFile);
    }

    private static void seedRealisticData() throws Exception {
        // Seed Proxy Routes
        ProxyRoute route1 = new ProxyRoute("/api/weather/*", "https://api.weatherapi.com", true);
        ProxyRoute route2 = new ProxyRoute("/api/v1/users/*", "http://localhost:3000", true);
        ProxyRoute route3 = new ProxyRoute("/v1/orders", "http://localhost:8081", false);
        AppContext.getProxyRouteStore().addRoute(route1);
        AppContext.getProxyRouteStore().addRoute(route2);
        AppContext.getProxyRouteStore().addRoute(route3);

        // Seed Chaos Rules
        ChaosRule chaos1 = new ChaosRule();
        chaos1.setRoutePattern("/api/weather/*");
        chaos1.setLatencyMs(150);
        chaos1.setStatusOverrideCode(503);
        chaos1.setConnectionResetEnabled(false);
        chaos1.setEnabled(true);

        ChaosRule chaos2 = new ChaosRule();
        chaos2.setRoutePattern("/api/v1/users/stream");
        chaos2.setLatencyMs(0);
        chaos2.setStatusOverrideCode(null);
        chaos2.setConnectionResetEnabled(true);
        chaos2.setEnabled(true);

        ChaosRule chaos3 = new ChaosRule();
        chaos3.setRoutePattern("/v1/orders/checkout");
        chaos3.setLatencyMs(2500);
        chaos3.setStatusOverrideCode(null);
        chaos3.setConnectionResetEnabled(false);
        chaos3.setEnabled(false);

        AppContext.getChaosRuleStore().addRule(chaos1);
        AppContext.getChaosRuleStore().addRule(chaos2);
        AppContext.getChaosRuleStore().addRule(chaos3);

        // Seed Mock Routes
        MockRoute mock1 = new MockRoute();
        mock1.setRoutePattern("/api/weather/current");
        mock1.setFilePath("api_weather_current_20260928_172000.json");
        mock1.setSource(MockSource.AUTO_SNAPSHOT);
        mock1.setEnabled(true);

        MockRoute mock2 = new MockRoute();
        mock2.setRoutePattern("/api/v1/users/profile");
        mock2.setFilePath("users_mock.json");
        mock2.setSource(MockSource.MANUAL);
        mock2.setEnabled(true);

        AppContext.getMockRouteStore().addRoute(mock1);
        AppContext.getMockRouteStore().addRoute(mock2);

        // Seed Rich Request Logs
        String weatherJson = """
                {
                  "location": {
                    "name": "London",
                    "region": "City of London",
                    "country": "United Kingdom",
                    "lat": 51.52,
                    "lon": -0.11,
                    "tz_id": "Europe/London"
                  },
                  "current": {
                    "temp_c": 19.5,
                    "is_day": 1,
                    "condition": {
                      "text": "Partly cloudy",
                      "code": 1003
                    },
                    "wind_kph": 14.8,
                    "humidity": 62,
                    "cloud": 25,
                    "feelslike_c": 19.5
                  }
                }""";

        Map<String, List<String>> reqHeaders = new LinkedHashMap<>();
        reqHeaders.put("Host", List.of("localhost:8080"));
        reqHeaders.put("Accept", List.of("application/json"));
        reqHeaders.put("User-Agent", List.of("EntropyLab/1.0 Client"));

        Map<String, List<String>> resHeaders = new LinkedHashMap<>();
        resHeaders.put("Content-Type", List.of("application/json; charset=utf-8"));
        resHeaders.put("Cache-Control", List.of("public, max-age=60"));
        resHeaders.put("Server", List.of("WeatherAPI-Gateway"));

        RequestLog log1 = new RequestLog();
        log1.setTimestamp("2026-09-28 17:20:12");
        log1.setMethod("GET");
        log1.setPath("/api/weather/current?city=London");
        log1.setTargetUrl("https://api.weatherapi.com/v1/current.json?city=London");
        log1.setResponseStatus(200);
        log1.setLatencyMs(142);
        log1.setOutcomeType(OutcomeType.FORWARDED);
        log1.setRequestHeaders(reqHeaders);
        log1.setRequestBodyBytes(new byte[0]);
        log1.setResponseHeaders(resHeaders);
        log1.setResponseBodyBytes(weatherJson.getBytes(StandardCharsets.UTF_8));

        RequestLog log2 = new RequestLog();
        log2.setTimestamp("2026-09-28 17:21:05");
        log2.setMethod("GET");
        log2.setPath("/api/weather/forecast?days=5");
        log2.setTargetUrl("https://api.weatherapi.com");
        log2.setResponseStatus(503);
        log2.setLatencyMs(150);
        log2.setOutcomeType(OutcomeType.CHAOS_STATUS);
        log2.setRequestHeaders(reqHeaders);
        log2.setRequestBodyBytes(new byte[0]);
        log2.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        log2.setResponseBodyBytes("{\"error\":\"Service Unavailable\",\"chaos\":true}".getBytes(StandardCharsets.UTF_8));

        RequestLog log3 = new RequestLog();
        log3.setTimestamp("2026-09-28 17:21:44");
        log3.setMethod("GET");
        log3.setPath("/api/v1/users/profile");
        log3.setTargetUrl(null);
        log3.setResponseStatus(200);
        log3.setLatencyMs(4);
        log3.setOutcomeType(OutcomeType.MOCKED);
        log3.setRequestHeaders(reqHeaders);
        log3.setRequestBodyBytes(new byte[0]);
        log3.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        log3.setResponseBodyBytes("{\"id\":42,\"name\":\"Alice Developer\",\"role\":\"admin\"}".getBytes(StandardCharsets.UTF_8));

        RequestLog log4 = new RequestLog();
        log4.setTimestamp("2026-09-28 17:22:10");
        log4.setMethod("POST");
        log4.setPath("/api/v1/users/stream");
        log4.setTargetUrl("http://localhost:3000");
        log4.setResponseStatus(0);
        log4.setLatencyMs(2);
        log4.setOutcomeType(OutcomeType.CHAOS_RESET);
        log4.setRequestHeaders(reqHeaders);
        log4.setRequestBodyBytes("{\"connect\":true}".getBytes(StandardCharsets.UTF_8));
        log4.setResponseHeaders(Map.of());
        log4.setResponseBodyBytes(new byte[0]);

        RequestLog log5 = new RequestLog();
        log5.setTimestamp("2026-09-28 17:23:00");
        log5.setMethod("POST");
        log5.setPath("/v1/orders/checkout");
        log5.setTargetUrl("http://localhost:8081");
        log5.setResponseStatus(201);
        log5.setLatencyMs(2514);
        log5.setOutcomeType(OutcomeType.FORWARDED);
        log5.setRequestHeaders(reqHeaders);
        log5.setRequestBodyBytes("{\"item\":\"Book\",\"qty\":1}".getBytes(StandardCharsets.UTF_8));
        log5.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        log5.setResponseBodyBytes("{\"orderId\":\"ORD-9912\",\"status\":\"created\"}".getBytes(StandardCharsets.UTF_8));

        RequestLog log6 = new RequestLog();
        log6.setTimestamp("2026-09-28 17:23:51");
        log6.setMethod("GET");
        log6.setPath("/api/legacy/endpoint");
        log6.setTargetUrl(null);
        log6.setResponseStatus(404);
        log6.setLatencyMs(1);
        log6.setOutcomeType(OutcomeType.NOT_FOUND);
        log6.setRequestHeaders(reqHeaders);
        log6.setRequestBodyBytes(new byte[0]);
        log6.setResponseHeaders(Map.of("Content-Type", List.of("application/json")));
        log6.setResponseBodyBytes("{\"error\":\"No route matched\"}".getBytes(StandardCharsets.UTF_8));

        AppContext.getRequestLogDao().insertLog(log1);
        AppContext.getRequestLogDao().insertLog(log2);
        AppContext.getRequestLogDao().insertLog(log3);
        AppContext.getRequestLogDao().insertLog(log4);
        AppContext.getRequestLogDao().insertLog(log5);
        AppContext.getRequestLogDao().insertLog(log6);
    }
}
