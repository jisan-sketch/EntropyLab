package com.entropylab.logging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe event dispatcher for request log persistence events.
 * Dispatches notifications to registered RequestLoggedListeners.
 */
public class RequestLogEventDispatcher {

    private final List<RequestLoggedListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener to receive notifications when a request is logged.
     *
     * @param listener The listener to register.
     */
    public void addListener(RequestLoggedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(RequestLoggedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Notifies all registered listeners of a newly persisted request log ID.
     * Exceptions from listeners are caught to prevent disrupting the dispatcher or calling thread.
     *
     * @param newLogId The ID of the newly logged request.
     */
    public void notifyListeners(int newLogId) {
        for (RequestLoggedListener listener : listeners) {
            try {
                listener.onRequestLogged(newLogId);
            } catch (Throwable t) {
                System.err.println("[RequestLogEventDispatcher] Error notifying listener: " + t.getMessage());
            }
        }
    }

    /**
     * Clears all registered listeners. Useful for testing resets.
     */
    public void clearListeners() {
        listeners.clear();
    }

    /**
     * Returns the current number of registered listeners.
     *
     * @return Number of registered listeners.
     */
    public int getListenerCount() {
        return listeners.size();
    }
}
