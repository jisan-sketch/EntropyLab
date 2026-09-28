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
import javafx.geometry.Orientation;
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
 * and supports deep payload inspection, JSON pretty printing, and offline mock generation.
 */
public class InspectorView extends VBox {

    private final TableView<RequestLog> tableView;
    private final Button refreshButton;

    private final SplitPane splitPane;
    private final VBox detailBox;
    private final HBox metaRow;

    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label liveBadge;

    private final Label targetUrlLabel;
    private final Label latencyLabel;
    private final Label outcomeLabel;
    private final Button saveAsMockButton;

    private final Label reqHeadersTitle;
    private final Label reqBodyTitle;
    private final Label resHeadersTitle;
    private final Label resBodyTitle;

    private final TextArea requestHeadersArea;
    private final TextArea requestBodyArea;
    private final TextArea responseHeadersArea;
    private final TextArea responseBodyArea;

    private RequestLog currentSelectedLog = null;
    private boolean initialLoadDone = false;

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

        // Header Section with colorful satellite squircle
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label headerIcon = new Label("📡");
        headerIcon.setStyle(
                "-fx-background-color: #ede9fe; " +
                "-fx-text-fill: #6d28d9; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 12; " +
                "-fx-background-radius: 8px;"
        );

        VBox titleBox = new VBox(4);
        titleLabel = new Label("Traffic Inspector");
        subtitleLabel = new Label("Live interception stream and captured HTTP request/response logs.");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        liveBadge = new Label("● LIVE STREAM");
        liveBadge.setStyle(
                "-fx-background-color: #dcfce7; " +
                "-fx-text-fill: #15803d; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 4 10; " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: #86efac; " +
                "-fx-border-radius: 12px;"
        );

        headerBox.getChildren().addAll(headerIcon, titleBox, spacer, liveBadge);

        // Toolbar with Refresh button
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        refreshButton = new Button("Refresh");
        toolbar.getChildren().add(refreshButton);

        // 1. Table Setup directly bound to InspectorTableModel's ObservableList
        tableView = new TableView<>();
        if (AppContext.getInspectorTableModel() != null) {
            tableView.setItems(AppContext.getInspectorTableModel().getLogEntries());
        }
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setMinHeight(180);

        TableColumn<RequestLog, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTimestamp() != null ? cell.getValue().getTimestamp() : ""));
        timeCol.setMinWidth(150);
        timeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    boolean isDark = ThemeManager.isDarkMode();
                    setStyle(isDark
                            ? "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #94a3b8;"
                            : "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #64748b;"
                    );
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<RequestLog, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMethod() != null ? cell.getValue().getMethod() : ""));
        methodCol.setMaxWidth(95);
        methodCol.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");
        methodCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String method, boolean empty) {
                super.updateItem(method, empty);
                if (empty || method == null || method.isEmpty()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = createMethodBadge(method);
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<RequestLog, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPath() != null ? cell.getValue().getPath() : ""));
        pathCol.setMinWidth(220);
        pathCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String path, boolean empty) {
                super.updateItem(path, empty);
                if (empty || path == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(path);
                    boolean isDark = ThemeManager.isDarkMode();
                    setStyle(isDark
                            ? "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #f1f5f9;"
                            : "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;"
                    );
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        TableColumn<RequestLog, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getResponseStatus() != null ? String.valueOf(cell.getValue().getResponseStatus()) : "-"
        ));
        statusCol.setMaxWidth(100);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || "-".equals(status)) {
                    setText(status != null ? status : "");
                    setGraphic(null);
                    setAlignment(Pos.CENTER);
                } else {
                    Label badge = createStatusBadge(status);
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        TableColumn<RequestLog, String> latencyCol = new TableColumn<>("Latency (ms)");
        latencyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getLatencyMs() + " ms"));
        latencyCol.setMaxWidth(120);
        latencyCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        latencyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String latency, boolean empty) {
                super.updateItem(latency, empty);
                if (empty || latency == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = createLatencyBadge(latency);
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        TableColumn<RequestLog, String> outcomeCol = new TableColumn<>("Outcome Type");
        outcomeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getOutcomeType() != null ? cell.getValue().getOutcomeType().name() : "-"
        ));
        outcomeCol.setMaxWidth(150);
        outcomeCol.setStyle("-fx-alignment: CENTER;");
        outcomeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String outcome, boolean empty) {
                super.updateItem(outcome, empty);
                if (empty || outcome == null || "-".equals(outcome)) {
                    setText(outcome != null ? outcome : "");
                    setGraphic(null);
                    setAlignment(Pos.CENTER);
                } else {
                    Label badge = createOutcomeBadge(outcome);
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tableView.getColumns().addAll(timeCol, methodCol, pathCol, statusCol, latencyCol, outcomeCol);
        tableView.setPlaceholder(new Label("No requests recorded yet. Traffic will appear here in real time."));

        // 2. Detail Panel Setup (SplitPane below TableView)
        detailBox = new VBox(10);
        detailBox.setPadding(new Insets(14));
        detailBox.setMinHeight(200);

        // Metadata summary row (Target URL, Latency, Outcome, Save as Mock Button)
        metaRow = new HBox(14);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(8, 12, 8, 12));

        targetUrlLabel = new Label("Target URL: https://api.example.com/v1/users");
        latencyLabel = new Label("Latency: 45 ms");
        outcomeLabel = new Label("Outcome: FORWARDED");

        Region metaSpacer = new Region();
        HBox.setHgrow(metaSpacer, Priority.ALWAYS);

        saveAsMockButton = new Button("Save as Offline Mock");
        saveAsMockButton.setVisible(false);
        saveAsMockButton.setDisable(true);

        metaRow.getChildren().addAll(targetUrlLabel, latencyLabel, outcomeLabel, metaSpacer, saveAsMockButton);

        // Split columns for Request and Response
        HBox columnsBox = new HBox(14);
        VBox.setVgrow(columnsBox, Priority.ALWAYS);

        // Left: Request Section
        VBox requestSection = new VBox(6);
        HBox.setHgrow(requestSection, Priority.ALWAYS);

        reqHeadersTitle = new Label("Request Headers");
        requestHeadersArea = new TextArea("Host: api.example.com\nUser-Agent: curl/7.88.1\nAccept: application/json");
        requestHeadersArea.setEditable(false);
        requestHeadersArea.setPrefRowCount(4);

        reqBodyTitle = new Label("Request Body");
        requestBodyArea = new TextArea("{\n  \"action\": \"create_user\",\n  \"username\": \"jdoe\"\n}");
        requestBodyArea.setEditable(false);
        requestBodyArea.setPrefRowCount(6);
        VBox.setVgrow(requestBodyArea, Priority.ALWAYS);

        requestSection.getChildren().addAll(reqHeadersTitle, requestHeadersArea, reqBodyTitle, requestBodyArea);

        // Right: Response Section
        VBox responseSection = new VBox(6);
        HBox.setHgrow(responseSection, Priority.ALWAYS);

        resHeadersTitle = new Label("Response Headers");
        responseHeadersArea = new TextArea("HTTP/1.1 200 OK\nContent-Type: application/json\nContent-Length: 48");
        responseHeadersArea.setEditable(false);
        responseHeadersArea.setPrefRowCount(4);

        resBodyTitle = new Label("Response Body");
        responseBodyArea = new TextArea("{\n  \"status\": \"success\",\n  \"userId\": 1042\n}");
        responseBodyArea.setEditable(false);
        responseBodyArea.setPrefRowCount(6);
        VBox.setVgrow(responseBodyArea, Priority.ALWAYS);

        responseSection.getChildren().addAll(resHeadersTitle, responseHeadersArea, resBodyTitle, responseBodyArea);

        columnsBox.getChildren().addAll(requestSection, responseSection);
        detailBox.getChildren().addAll(metaRow, columnsBox);

        // SplitPane container
        splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(tableView, detailBox);
        splitPane.setDividerPositions(0.45);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        getChildren().addAll(headerBox, toolbar, splitPane);

        applyTheme();
        ThemeManager.addListener(isDark -> applyTheme());

        wireEvents();
    }

    private Label createMethodBadge(String method) {
        Label badge = new Label(method);
        boolean dark = ThemeManager.isDarkMode();
        String m = method.toUpperCase();

        if ("GET".equals(m)) {
            badge.setStyle(dark
                    ? "-fx-background-color: #064e3b; -fx-text-fill: #34d399; -fx-border-color: #059669; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
                    : "-fx-background-color: #ecfdf5; -fx-text-fill: #059669; -fx-border-color: #a7f3d0; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
            );
        } else if ("POST".equals(m)) {
            badge.setStyle(dark
                    ? "-fx-background-color: #1e3a8a; -fx-text-fill: #60a5fa; -fx-border-color: #2563eb; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
                    : "-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; -fx-border-color: #bfdbfe; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
            );
        } else if ("PUT".equals(m) || "PATCH".equals(m)) {
            badge.setStyle(dark
                    ? "-fx-background-color: #78350f; -fx-text-fill: #fbbf24; -fx-border-color: #d97706; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
                    : "-fx-background-color: #fffbeb; -fx-text-fill: #d97706; -fx-border-color: #fde68a; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
            );
        } else if ("DELETE".equals(m)) {
            badge.setStyle(dark
                    ? "-fx-background-color: #881337; -fx-text-fill: #fb7185; -fx-border-color: #e11d48; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
                    : "-fx-background-color: #fff1f2; -fx-text-fill: #e11d48; -fx-border-color: #fecdd3; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
            );
        } else {
            badge.setStyle(dark
                    ? "-fx-background-color: #4c1d95; -fx-text-fill: #c084fc; -fx-border-color: #7c3aed; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
                    : "-fx-background-color: #f5f3ff; -fx-text-fill: #7c3aed; -fx-border-color: #ddd6fe; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 2 8; -fx-font-weight: bold; -fx-font-size: 11px;"
            );
        }
        return badge;
    }

    private Label createStatusBadge(String statusStr) {
        Label badge = new Label(statusStr);
        boolean dark = ThemeManager.isDarkMode();
        try {
            int code = Integer.parseInt(statusStr.trim());
            if (code >= 200 && code < 300) {
                badge.setStyle(dark
                        ? "-fx-background-color: #064e3b; -fx-text-fill: #4ade80; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #059669; -fx-border-radius: 10px;"
                        : "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #86efac; -fx-border-radius: 10px;"
                );
            } else if (code >= 300 && code < 400) {
                badge.setStyle(dark
                        ? "-fx-background-color: #0c4a6e; -fx-text-fill: #38bdf8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #0284c7; -fx-border-radius: 10px;"
                        : "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #bae6fd; -fx-border-radius: 10px;"
                );
            } else if (code >= 400 && code < 500) {
                badge.setStyle(dark
                        ? "-fx-background-color: #78350f; -fx-text-fill: #fbbf24; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #d97706; -fx-border-radius: 10px;"
                        : "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #fde68a; -fx-border-radius: 10px;"
                );
            } else {
                badge.setStyle(dark
                        ? "-fx-background-color: #7f1d1d; -fx-text-fill: #f87171; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #dc2626; -fx-border-radius: 10px;"
                        : "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10px; -fx-border-color: #fecaca; -fx-border-radius: 10px;"
                );
            }
        } catch (Exception e) {
            badge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        }
        return badge;
    }

    private Label createLatencyBadge(String latencyStr) {
        Label badge = new Label(latencyStr);
        boolean dark = ThemeManager.isDarkMode();
        try {
            int ms = Integer.parseInt(latencyStr.replace("ms", "").trim());
            if (ms < 100) {
                badge.setStyle(dark ? "-fx-text-fill: #4ade80; -fx-font-weight: bold;" : "-fx-text-fill: #16a34a; -fx-font-weight: bold;");
            } else if (ms < 500) {
                badge.setStyle(dark ? "-fx-text-fill: #fbbf24; -fx-font-weight: bold;" : "-fx-text-fill: #d97706; -fx-font-weight: bold;");
            } else {
                badge.setStyle(dark ? "-fx-text-fill: #f87171; -fx-font-weight: bold;" : "-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            badge.setStyle("-fx-text-fill: #94a3b8;");
        }
        return badge;
    }

    private Label createOutcomeBadge(String outcome) {
        Label badge = new Label(outcome);
        boolean dark = ThemeManager.isDarkMode();
        if ("FORWARDED".equalsIgnoreCase(outcome)) {
            badge.setStyle(dark
                    ? "-fx-background-color: #064e3b; -fx-text-fill: #4ade80; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #059669; -fx-border-radius: 10px;"
                    : "-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #86efac; -fx-border-radius: 10px;"
            );
        } else if ("MOCKED".equalsIgnoreCase(outcome)) {
            badge.setStyle(dark
                    ? "-fx-background-color: #4c1d95; -fx-text-fill: #c084fc; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #7c3aed; -fx-border-radius: 10px;"
                    : "-fx-background-color: #f3e8ff; -fx-text-fill: #7e22ce; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #d8b4fe; -fx-border-radius: 10px;"
            );
        } else if (outcome != null && outcome.contains("LATENCY")) {
            badge.setStyle(dark
                    ? "-fx-background-color: #78350f; -fx-text-fill: #fbbf24; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #d97706; -fx-border-radius: 10px;"
                    : "-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #fde68a; -fx-border-radius: 10px;"
            );
        } else if (outcome != null && outcome.contains("STATUS")) {
            badge.setStyle(dark
                    ? "-fx-background-color: #881337; -fx-text-fill: #fda4af; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #f43f5e; -fx-border-radius: 10px;"
                    : "-fx-background-color: #ffe4e6; -fx-text-fill: #be123c; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #fecdd3; -fx-border-radius: 10px;"
            );
        } else {
            badge.setStyle(dark
                    ? "-fx-background-color: #7f1d1d; -fx-text-fill: #f87171; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #ef4444; -fx-border-radius: 10px;"
                    : "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-size: 10px; -fx-font-weight: 800; -fx-padding: 2 7; -fx-background-radius: 10px; -fx-border-color: #fecaca; -fx-border-radius: 10px;"
            );
        }
        return badge;
    }

    private void applyTheme() {
        boolean dark = ThemeManager.isDarkMode();
        setStyle(ThemeManager.getViewBackground());

        titleLabel.setStyle(ThemeManager.getTitleStyle());
        subtitleLabel.setStyle(ThemeManager.getSubtitleStyle());
        refreshButton.setStyle(ThemeManager.getSecondaryButtonStyle());

        detailBox.setStyle(ThemeManager.getCardStyle("#8b5cf6"));

        if (dark) {
            metaRow.setStyle(
                    "-fx-background-color: #0f172a; -fx-background-radius: 8px; -fx-border-color: #1e293b; -fx-border-radius: 8px;"
            );
            targetUrlLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #cbd5e1;");
            latencyLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #60a5fa;");
            outcomeLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #4ade80;");

            reqHeadersTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
            reqBodyTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
            resHeadersTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
            resBodyTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #94a3b8;");
        } else {
            metaRow.setStyle(
                    "-fx-background-color: #f1f5f9; -fx-background-radius: 8px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;"
            );
            targetUrlLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #334155;");
            latencyLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");
            outcomeLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");

            reqHeadersTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            reqBodyTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            resHeadersTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
            resBodyTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #475569;");
        }

        saveAsMockButton.setStyle(ThemeManager.getPurpleButtonStyle());

        String codeStyle = ThemeManager.getCodeAreaStyle();
        requestHeadersArea.setStyle(codeStyle);
        requestBodyArea.setStyle(codeStyle);
        responseHeadersArea.setStyle(codeStyle);
        responseBodyArea.setStyle(codeStyle);

        tableView.refresh();
    }

    private void wireEvents() {
        refreshButton.setOnAction(e -> refreshHistoricalLogs());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && !initialLoadDone) {
                initialLoadDone = true;
                refreshHistoricalLogs();
            }
        });

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getId() != null) {
                loadLogDetails(newVal.getId());
            } else {
                clearDetails();
            }
        });

        saveAsMockButton.setOnAction(e -> handleSaveAsOfflineMock());
    }

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

            MocksView.refreshAllViews();

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

    public void refreshHistoricalLogs() {
        refreshButton.setDisable(true);

        Thread fetchThread = new Thread(() -> {
            try {
                if (AppContext.getRequestLogDao() != null && AppContext.getInspectorTableModel() != null) {
                    List<RequestLog> fetchedList = AppContext.getRequestLogDao().getRecentLogs(100, 0);

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
