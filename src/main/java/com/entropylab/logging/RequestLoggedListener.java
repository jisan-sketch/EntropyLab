package com.entropylab.logging;

/**
 * Listener interface for receiving notifications when a new RequestLog has been persisted.
 * Keeps the backend decoupled from UI frameworks (no JavaFX dependency).
 */
@FunctionalInterface
public interface RequestLoggedListener {

    /**
     * Invoked when a new request log has been written to persistence.
     *
     * @param newLogId The generated database primary key ID of the new log record.
     */
    void onRequestLogged(int newLogId);
}
