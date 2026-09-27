package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.mock.MockFileNaming;
import com.entropylab.mock.MockRoute;
import com.entropylab.mock.MockSource;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * View for the "Inspector" tab.
 * Displays real-time intercepted HTTP traffic directly bound to InspectorTableModel
 * and an exchange detail panel in a SplitPane with an Auto-Mock Snapshot action.
 */
public class InspectorView extends VBox {

    private final TableView<RequestLog> tableView;
    private final Button refreshButton;
    private final SplitPane splitPane;

    // Detail Panel Controls
    private final Label targetUrlLabel;
    private final Label latencyLabel;
    private final Label outcomeLabel;
    private final Button saveAsMockButton;
    private final TextArea requestHeadersArea;
    private final TextArea requestBodyArea;
    private final TextArea responseHeadersArea;
    private final TextArea responseBodyArea;

    private boolean initialLoadDone = false;
    private RequestLog currentSelectedLog = null;

    private static Alert lastShownAlert = null;

    private static java.util.function.Consumer<Alert> alertHandler = alert -> {
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
            System.out.println("[InspectorView] Alert display skipped: " + e.getMessage());
        }
    };

    public static void setAlertHandler(java.util.function.Consumer<Alert> handler) {
        alertHandler = handler;
    }

    public static java.util.function.Consumer<Alert> getAlertHandler() {
        return alertHandler;
    }

    public static Alert getLastShownAlert() {
        return lastShownAlert;
    }

    public static void clearLastShownAlert() {
        lastShownAlert = null;
    }

    private void showInformationAlert(Alert alert) {
        lastShownAlert = alert;
        if (Platform.isFxApplicationThread()) {
            if (alertHandler != null) {
                alertHandler.accept(alert);
            }
        } else {
            Platform.runLater(() -> {
                if (alertHandler != null) {
                    alertHandler.accept(alert);
                }
            });
        }
    }

    public InspectorView() {
        setSpacing(12);
        setPadding(new Insets(20));
        setAlignment(Pos.TOP_LEFT);
        setStyle("-fx-background-color: #f8fafc;");

        // Header Section
        VBox headerBox = new VBox(4);
        Label titleLabel = new Label("Traffic Inspector");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitleLabel = new Label("Live interception stream and captured HTTP request/response logs.");
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748b;");
        headerBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Toolbar with Refresh button
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        refreshButton = new Button("Refresh");
        refreshButton.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-text-fill: #0f172a; " +
                "-fx-font-size: 13px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 7 16; " +
                "-fx-background-radius: 6px; " +
                "-fx-border-color: #cbd5e1; " +
                "-fx-border-radius: 6px; " +
                "-fx-cursor: hand;"
        );
        toolbar.getChildren().add(refreshButton);

        // 1. Table Setup directly bound to InspectorTableModel's ObservableList
        tableView = new TableView<>();
        if (AppContext.getInspectorTableModel() != null) {
            tableView.setItems(AppContext.getInspectorTableModel().getLogEntries());
        }
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setMinHeight(180);
        tableView.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px;"
        );

        TableColumn<RequestLog, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTimestamp() != null ? cell.getValue().getTimestamp() : ""));
        timeCol.setMinWidth(150);

        TableColumn<RequestLog, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMethod() != null ? cell.getValue().getMethod() : ""));
        methodCol.setMaxWidth(90);
        methodCol.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<RequestLog, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPath() != null ? cell.getValue().getPath() : ""));
        pathCol.setMinWidth(220);

        TableColumn<RequestLog, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getResponseStatus() != null ? String.valueOf(cell.getValue().getResponseStatus()) : "-"
        ));
        statusCol.setMaxWidth(100);
        statusCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<RequestLog, String> latencyCol = new TableColumn<>("Latency (ms)");
        latencyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLatencyMs() + " ms"));
        latencyCol.setMaxWidth(120);
        latencyCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<RequestLog, String> outcomeCol = new TableColumn<>("Outcome Type");
        outcomeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getOutcomeType() != null ? cell.getValue().getOutcomeType().name() : "-"
        ));
        outcomeCol.setMaxWidth(140);
        outcomeCol.setStyle("-fx-alignment: CENTER;");

        tableView.getColumns().addAll(timeCol, methodCol, pathCol, statusCol, latencyCol, outcomeCol);
        tableView.setPlaceholder(new Label("No requests recorded yet. Traffic will appear here in real time."));

        // 2. Detail Panel Setup (SplitPane below TableView)
        VBox detailBox = new VBox(10);
        detailBox.setPadding(new Insets(12));
        detailBox.setMinHeight(200);
        detailBox.setStyle(
                "-fx-background-color: #ffffff; " +
                "-fx-border-color: #e2e8f0; " +
                "-fx-border-radius: 6px; " +
                "-fx-background-radius: 6px;"
        );

        // Metadata summary row (Target URL, Latency, Outcome, Save as Mock Button)
        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(6, 10, 6, 10));
        metaRow.setStyle(
                "-fx-background-color: #f1f5f9; " +
                "-fx-background-radius: 6px;"
        );

        targetUrlLabel = new Label("Target URL: https://api.example.com/v1/users");
        targetUrlLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");

        latencyLabel = new Label("Latency: 45 ms");
        latencyLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        outcomeLabel = new Label("Outcome: FORWARDED");
        outcomeLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");

        Region metaSpacer = new Region();
        HBox.setHgrow(metaSpacer, Priority.ALWAYS);

        saveAsMockButton = new Button("Save as Offline Mock");
        saveAsMockButton.setStyle(
                "-fx-background-color: #6366f1; " +
                "-fx-text-fill: #ffffff; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 12; " +
                "-fx-background-radius: 6px; " +
                "-fx-cursor: hand;"
        );
        saveAsMockButton.setVisible(false);
        saveAsMockButton.setDisable(true);

        metaRow.getChildren().addAll(targetUrlLabel, latencyLabel, outcomeLabel, metaSpacer, saveAsMockButton);

        // Split columns for Request and Response
        HBox columnsBox = new HBox(12);
        VBox.setVgrow(columnsBox, Priority.ALWAYS);

        // Left: Request Section
        VBox requestSection = new VBox(6);
        HBox.setHgrow(requestSection, Priority.ALWAYS);

        Label reqHeadersTitle = new Label("Request Headers");
        reqHeadersTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        requestHeadersArea = new TextArea("Host: api.example.com\nUser-Agent: curl/7.88.1\nAccept: application/json");
        requestHeadersArea.setEditable(false);
        requestHeadersArea.setPrefRowCount(4);
        requestHeadersArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px;");

        Label reqBodyTitle = new Label("Request Body");
        reqBodyTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        requestBodyArea = new TextArea("{\n  \"action\": \"create_user\",\n  \"username\": \"jdoe\"\n}");
        requestBodyArea.setEditable(false);
        requestBodyArea.setPrefRowCount(6);
        VBox.setVgrow(requestBodyArea, Priority.ALWAYS);
        requestBodyArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px;");

        requestSection.getChildren().addAll(reqHeadersTitle, requestHeadersArea, reqBodyTitle, requestBodyArea);

        // Right: Response Section
        VBox responseSection = new VBox(6);
        HBox.setHgrow(responseSection, Priority.ALWAYS);

        Label resHeadersTitle = new Label("Response Headers");
        resHeadersTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        responseHeadersArea = new TextArea("HTTP/1.1 200 OK\nContent-Type: application/json\nContent-Length: 48");
        responseHeadersArea.setEditable(false);
        responseHeadersArea.setPrefRowCount(4);
        responseHeadersArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px;");

        Label resBodyTitle = new Label("Response Body");
        resBodyTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        responseBodyArea = new TextArea("{\n  \"status\": \"success\",\n  \"userId\": 1042\n}");
        responseBodyArea.setEditable(false);
        responseBodyArea.setPrefRowCount(6);
        VBox.setVgrow(responseBodyArea, Priority.ALWAYS);
        responseBodyArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11px;");

        responseSection.getChildren().addAll(resHeadersTitle, responseHeadersArea, resBodyTitle, responseBodyArea);

        columnsBox.getChildren().addAll(requestSection, responseSection);
        detailBox.getChildren().addAll(metaRow, columnsBox);

        // SplitPane container
        splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.getItems().addAll(tableView, detailBox);
        splitPane.setDividerPositions(0.45);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        getChildren().addAll(headerBox, toolbar, splitPane);

        wireEvents();
    }

    private void wireEvents() {
        refreshButton.setOnAction(e -> refreshHistoricalLogs());

        // Auto-trigger once when this tab/view is first added to a Scene
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !initialLoadDone) {
                initialLoadDone = true;
                refreshHistoricalLogs();
            }
        });

        // M10.4: Selection listener on TableView to fetch full details asynchronously
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getId() != null) {
                loadLogDetails(newVal.getId());
            } else {
                clearDetails();
            }
        });

        // M11.4: Save as Offline Mock Button Action
        saveAsMockButton.setOnAction(e -> handleSaveAsOfflineMock());
    }

    /**
     * Fetches the full RequestLog from SQLite on a background thread and populates the detail panel.
     */
    public void loadLogDetails(int logId) {
        Thread worker = new Thread(() -> {
            try {
                if (AppContext.getRequestLogDao() != null) {
                    RequestLog fullLog = AppContext.getRequestLogDao().getLogById(logId);
                    if (fullLog != null) {
                        Platform.runLater(() -> populateDetails(fullLog));
                    }
                }
            } catch (Exception ex) {
                System.err.println("[InspectorView] Failed to fetch log details for id=" + logId + ": " + ex.getMessage());
            }
        }, "inspector-detail-fetcher");
        worker.setDaemon(true);
        worker.start();
    }

    private void populateDetails(RequestLog log) {
        currentSelectedLog = log;
        targetUrlLabel.setText("Target URL: " + (log.getTargetUrl() != null && !log.getTargetUrl().isEmpty() ? log.getTargetUrl() : "-"));
        latencyLabel.setText("Latency: " + log.getLatencyMs() + " ms");
        outcomeLabel.setText("Outcome: " + (log.getOutcomeType() != null ? log.getOutcomeType().name() : "-"));

        requestHeadersArea.setText(formatHeaders(log.getRequestHeaders()));
        requestBodyArea.setText(JsonPrettyPrinter.tryPrettyPrint(log.getRequestBodyAsString() != null ? log.getRequestBodyAsString() : ""));

        responseHeadersArea.setText(formatHeaders(log.getResponseHeaders()));
        responseBodyArea.setText(JsonPrettyPrinter.tryPrettyPrint(log.getResponseBodyAsString() != null ? log.getResponseBodyAsString() : ""));

        // M11.4: Enable Save as Offline Mock button only when outcome == FORWARDED and response body is non-empty
        boolean canSaveAsMock = log.getOutcomeType() == OutcomeType.FORWARDED &&
                log.getResponseBodyBytes() != null &&
                log.getResponseBodyBytes().length > 0;
        saveAsMockButton.setVisible(canSaveAsMock);
        saveAsMockButton.setDisable(!canSaveAsMock);
    }

    public void clearDetails() {
        currentSelectedLog = null;
        targetUrlLabel.setText("Target URL: -");
        latencyLabel.setText("Latency: -");
        outcomeLabel.setText("Outcome: -");
        saveAsMockButton.setVisible(false);
        saveAsMockButton.setDisable(true);
        requestHeadersArea.clear();
        requestBodyArea.clear();
        responseHeadersArea.clear();
        responseBodyArea.clear();
    }

    /**
     * Saves the currently selected log's response body to a file in AppPaths.getMocksDir()
     * and registers an exact-match MockRoute with source AUTO_SNAPSHOT in MockRouteStore.
     */
    public void handleSaveAsOfflineMock() {
        if (currentSelectedLog == null) {
            return;
        }

        byte[] bodyBytes = currentSelectedLog.getResponseBodyBytes();
        if (bodyBytes == null || bodyBytes.length == 0 || currentSelectedLog.getOutcomeType() != OutcomeType.FORWARDED) {
            return;
        }

        try {
            AppPaths.ensureDirectoriesExist();
            String routePath = currentSelectedLog.getPath();
            String fileName = MockFileNaming.generateFileName(routePath);
            Path targetFile = AppPaths.getMocksDir().resolve(fileName);
            Files.write(targetFile, bodyBytes);
            System.out.println("[InspectorView] Saved snapshot response body to: " + targetFile.toAbsolutePath());

            // routePattern is log.getPath() EXACTLY AS-IS (do not generalize to a wildcard)
            MockRoute newMockRoute = new MockRoute(
                    routePath,
                    targetFile.toAbsolutePath().toString(),
                    true,
                    MockSource.AUTO_SNAPSHOT
            );

            if (AppContext.getMockRouteStore() != null) {
                AppContext.getMockRouteStore().addRoute(newMockRoute);
                System.out.println("[InspectorView] Added AUTO_SNAPSHOT MockRoute for: " + routePath);
            }

            // M11.5: Refresh MocksView table immediately if active
            MocksView.refreshAllViews();

            // M11.5: Show confirmation alert
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Offline Mock Saved");
            alert.setHeaderText(null);
            alert.setContentText("Saved as offline mock — this route will now be served locally.");
            showInformationAlert(alert);

        } catch (Exception ex) {
            System.err.println("[InspectorView] Failed to save offline mock: " + ex.getMessage());
            AlertHelper.showError("Error", "Failed to save offline mock: " + ex.getMessage());
        }
    }

    private String formatHeaders(java.util.Map<String, java.util.List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue())).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Loads historical logs from SQLite on a background thread and replaces the table contents.
     */
    public void refreshHistoricalLogs() {
        refreshButton.setDisable(true);

        Thread fetchThread = new Thread(() -> {
            try {
                if (AppContext.getRequestLogDao() != null && AppContext.getInspectorTableModel() != null) {
                    List<RequestLog> fetchedList = AppContext.getRequestLogDao().getRecentLogs(100, 0);

                    // This fully replaces the list contents (simplest correct approach,
                    // since the DB already contains every entry including live ones).
                    Platform.runLater(() -> {
                        AppContext.getInspectorTableModel().replaceAll(fetchedList);
                        refreshButton.setDisable(false);
                    });
                } else {
                    Platform.runLater(() -> refreshButton.setDisable(false));
                }
            } catch (Exception e) {
                System.err.println("[InspectorView] Failed to load historical logs: " + e.getMessage());
                Platform.runLater(() -> {
                    refreshButton.setDisable(false);
                    AlertHelper.showError("Error", "Failed to load historical logs: " + e.getMessage());
                });
            }
        }, "inspector-log-refresher");

        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    public TableView<RequestLog> getTableView() {
        return tableView;
    }

    public Button getRefreshButton() {
        return refreshButton;
    }

    public SplitPane getSplitPane() {
        return splitPane;
    }

    public Label getTargetUrlLabel() {
        return targetUrlLabel;
    }

    public Label getLatencyLabel() {
        return latencyLabel;
    }

    public Label getOutcomeLabel() {
        return outcomeLabel;
    }

    public Button getSaveAsMockButton() {
        return saveAsMockButton;
    }

    public RequestLog getCurrentSelectedLog() {
        return currentSelectedLog;
    }

    public TextArea getRequestHeadersArea() {
        return requestHeadersArea;
    }

    public TextArea getRequestBodyArea() {
        return requestBodyArea;
    }

    public TextArea getResponseHeadersArea() {
        return responseHeadersArea;
    }

    public TextArea getResponseBodyArea() {
        return responseBodyArea;
    }
}
