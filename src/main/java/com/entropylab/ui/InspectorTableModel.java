package com.entropylab.ui;

import com.entropylab.core.AppContext;
import com.entropylab.logging.RequestLog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared data model holding the observable collection of RequestLog entries for the Inspector UI.
 * Threaded through the event bridge so the UI TableView can bind directly to this list.
 * Automatically caps total entries to 500 to maintain low memory usage during long-running sessions.
 */
public class InspectorTableModel {

    public static final int MAX_ENTRIES = 500;

    private final ObservableList<RequestLog> logEntries;

    public InspectorTableModel() {
        this.logEntries = FXCollections.observableArrayList();
    }

    /**
     * Inserts a new RequestLog entry at index 0 (top of the inspector list).
     * If the collection exceeds 500 items, removes the oldest (last) entry.
     *
     * @param log The newly captured or completed RequestLog.
     */
    public void addNewestEntry(RequestLog log) {
        if (log == null) {
            return;
        }
        logEntries.add(0, log);
        if (logEntries.size() > MAX_ENTRIES) {
            logEntries.remove(logEntries.size() - 1);
        }
    }

    /**
     * Clears the current collection and repopulates it with the provided list of logs.
     * Capped to MAX_ENTRIES if the input list contains more.
     *
     * @param logs New list of logs to replace current entries with.
     */
    public void replaceAll(List<RequestLog> logs) {
        logEntries.clear();
        if (logs != null && !logs.isEmpty()) {
            int limit = Math.min(logs.size(), MAX_ENTRIES);
            logEntries.addAll(logs.subList(0, limit));
        }
    }

    /**
     * Returns the underlying ObservableList for TableView data binding.
     *
     * @return ObservableList of RequestLog.
     */
    public ObservableList<RequestLog> getLogEntries() {
        return logEntries;
    }

    /**
     * Returns the current number of log entries in the model.
     *
     * @return Current size.
     */
    public int size() {
        return logEntries.size();
    }

    /**
     * Clears all log entries from the model.
     */
    public void clear() {
        logEntries.clear();
    }

    /**
     * Manual console test proving addNewestEntry caps at 500 and replaceAll resets the list.
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  InspectorTableModel Test Suite (M4.4)");
        System.out.println("==================================================");

        InspectorTableModel model = new InspectorTableModel();

        // 1. Initial state
        System.out.println("\n[TEST 1] Verifying initial empty state...");
        if (model.size() != 0) {
            throw new AssertionError("Expected initial size 0, got: " + model.size());
        }
        System.out.println("  Initial size: 0 (PASS)");

        // 2. Add 550 entries and verify capping at MAX_ENTRIES (500)
        System.out.println("\n[TEST 2] Adding 550 entries to test 500-entry capacity capping...");
        for (int i = 0; i < 550; i++) {
            RequestLog log = new RequestLog();
            log.setId(i);
            log.setPath("/item/" + i);
            model.addNewestEntry(log);
        }

        System.out.println("  Model size after 550 insertions: " + model.size());
        if (model.size() != MAX_ENTRIES) {
            throw new AssertionError("Expected size capped at " + MAX_ENTRIES + ", but was: " + model.size());
        }

        // Newest entry (inserted last, index 0) must be ID=549
        RequestLog newest = model.getLogEntries().get(0);
        System.out.println("  Newest entry (index 0): ID=" + newest.getId() + " path=" + newest.getPath());
        if (newest.getId() != 549) {
            throw new AssertionError("Expected newest entry at index 0 to have ID=549, got: " + newest.getId());
        }

        // Oldest retained entry (index 499) must be ID=50 (entries 0..49 were evicted)
        RequestLog oldestRetained = model.getLogEntries().get(MAX_ENTRIES - 1);
        System.out.println("  Oldest entry (index 499): ID=" + oldestRetained.getId() + " path=" + oldestRetained.getPath());
        if (oldestRetained.getId() != 50) {
            throw new AssertionError("Expected oldest entry at index 499 to have ID=50, got: " + oldestRetained.getId());
        }
        System.out.println("  Result: PASS (Capping at 500 and oldest entry eviction verified)");

        // 3. Test replaceAll(List)
        System.out.println("\n[TEST 3] Testing replaceAll with 5 new entries...");
        List<RequestLog> freshList = new ArrayList<>();
        for (int i = 1000; i < 1005; i++) {
            RequestLog log = new RequestLog();
            log.setId(i);
            log.setPath("/fresh/" + i);
            freshList.add(log);
        }

        model.replaceAll(freshList);
        System.out.println("  Model size after replaceAll: " + model.size());
        if (model.size() != 5) {
            throw new AssertionError("Expected size 5 after replaceAll, got: " + model.size());
        }
        if (model.getLogEntries().get(0).getId() != 1000 || model.getLogEntries().get(4).getId() != 1004) {
            throw new AssertionError("replaceAll did not match expected fresh list items!");
        }
        System.out.println("  Result: PASS (replaceAll correctly reset and repopulated list)");

        // 4. Test replaceAll with empty list
        System.out.println("\n[TEST 4] Testing replaceAll with empty list...");
        model.replaceAll(List.of());
        if (model.size() != 0) {
            throw new AssertionError("Expected size 0 after replaceAll(empty), got: " + model.size());
        }
        System.out.println("  Result: PASS (List cleared)");

        System.out.println("\n==================================================");
        System.out.println("  All InspectorTableModel M4.4 tests PASSED!");
        System.out.println("==================================================");
    }
}
