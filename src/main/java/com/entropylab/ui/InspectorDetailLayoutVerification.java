package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import javafx.application.Platform;
import javafx.geometry.Orientation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Headless verification test for M10.3 Detail Panel Layout.
 */
public class InspectorDetailLayoutVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Inspector Detail Panel Layout Verification (M10.3)");
        System.out.println("==================================================");

        // Bootstrap backend environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        CountDownLatch fxStartupLatch = new CountDownLatch(1);
        try {
            Platform.startup(() -> fxStartupLatch.countDown());
        } catch (IllegalStateException e) {
            fxStartupLatch.countDown();
        }
        if (!fxStartupLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Failed to initialize JavaFX toolkit");
        }

        final InspectorView[] viewRef = new InspectorView[1];
        CountDownLatch initLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            viewRef[0] = new InspectorView();
            initLatch.countDown();
        });
        initLatch.await(5, TimeUnit.SECONDS);
        InspectorView view = viewRef[0];

        // 1. Verify SplitPane structure
        System.out.println("\n[STEP 1] Verifying SplitPane layout...");
        if (view.getSplitPane() == null) {
            throw new AssertionError("SplitPane is null!");
        }
        if (view.getSplitPane().getOrientation() != Orientation.VERTICAL) {
            throw new AssertionError("Expected vertical SplitPane orientation!");
        }
        if (view.getSplitPane().getItems().size() != 2) {
            throw new AssertionError("SplitPane should contain 2 items (table on top, detail below), found: " + view.getSplitPane().getItems().size());
        }
        if (view.getSplitPane().getItems().get(0) != view.getTableView()) {
            throw new AssertionError("Top item of SplitPane must be the TableView!");
        }
        System.out.println("  Result: PASS (Vertical SplitPane with TableView on top verified)");

        // 2. Verify Detail Panel Labels & TextAreas
        System.out.println("\n[STEP 2] Verifying Detail Panel components & placeholders...");
        if (view.getTargetUrlLabel() == null || !view.getTargetUrlLabel().getText().contains("Target URL")) {
            throw new AssertionError("Target URL label missing or lacks placeholder!");
        }
        System.out.println("  Target URL Label: '" + view.getTargetUrlLabel().getText() + "'");

        if (view.getLatencyLabel() == null || !view.getLatencyLabel().getText().contains("Latency")) {
            throw new AssertionError("Latency label missing or lacks placeholder!");
        }
        System.out.println("  Latency Label: '" + view.getLatencyLabel().getText() + "'");

        if (view.getOutcomeLabel() == null || !view.getOutcomeLabel().getText().contains("Outcome")) {
            throw new AssertionError("Outcome label missing or lacks placeholder!");
        }
        System.out.println("  Outcome Label: '" + view.getOutcomeLabel().getText() + "'");

        // Verify TextAreas
        if (view.getRequestHeadersArea() == null || view.getRequestHeadersArea().isEditable() ||
                !view.getRequestHeadersArea().getText().contains("Host")) {
            throw new AssertionError("Request Headers TextArea must be read-only with placeholder text!");
        }
        System.out.println("  Request Headers TextArea: readOnly=true, length=" + view.getRequestHeadersArea().getText().length());

        if (view.getRequestBodyArea() == null || view.getRequestBodyArea().isEditable() ||
                !view.getRequestBodyArea().getText().contains("action")) {
            throw new AssertionError("Request Body TextArea must be read-only with placeholder text!");
        }
        System.out.println("  Request Body TextArea: readOnly=true, length=" + view.getRequestBodyArea().getText().length());

        if (view.getResponseHeadersArea() == null || view.getResponseHeadersArea().isEditable() ||
                !view.getResponseHeadersArea().getText().contains("HTTP")) {
            throw new AssertionError("Response Headers TextArea must be read-only with placeholder text!");
        }
        System.out.println("  Response Headers TextArea: readOnly=true, length=" + view.getResponseHeadersArea().getText().length());

        if (view.getResponseBodyArea() == null || view.getResponseBodyArea().isEditable() ||
                !view.getResponseBodyArea().getText().contains("status")) {
            throw new AssertionError("Response Body TextArea must be read-only with placeholder text!");
        }
        System.out.println("  Response Body TextArea: readOnly=true, length=" + view.getResponseBodyArea().getText().length());

        System.out.println("  Result: PASS (All detail panel TextAreas and Labels verified with static placeholders)");

        // 3. Verify TableView still has 6 columns
        System.out.println("\n[STEP 3] Verifying TableView columns preserved...");
        if (view.getTableView().getColumns().size() != 6) {
            throw new AssertionError("Expected 6 columns in TableView!");
        }
        System.out.println("  Result: PASS (TableView 6 columns intact)");

        System.out.println("\n==================================================");
        System.out.println("  All M10.3 Detail Panel Layout tests PASSED!");
        System.out.println("==================================================");

        Platform.exit();
    }
}
