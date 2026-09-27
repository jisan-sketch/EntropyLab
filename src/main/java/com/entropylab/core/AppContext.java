package com.entropylab.core;

import com.entropylab.chaos.ChaosRuleDao;
import com.entropylab.chaos.ChaosRuleStore;
import com.entropylab.logging.RequestLogDao;
import com.entropylab.logging.RequestLogEventDispatcher;
import com.entropylab.mock.MockRouteDao;
import com.entropylab.mock.MockRouteStore;
import com.entropylab.proxy.ProxyServer;
import com.entropylab.routes.ProxyRouteStore;
import com.entropylab.ui.InspectorTableModel;

/**
 * Central singleton service registry for EntropyLab.
 *
 * Every future shared service (stores, DAOs, dispatchers, the proxy server itself) MUST be
 * registered here and retrieved via AppContext.getX() everywhere in the app — never instantiate
 * a second copy of any of these elsewhere.
 */
public final class AppContext {

    private static DatabaseManager databaseManager;
    private static ProxyRouteStore proxyRouteStore;
    private static ProxyServer proxyServer;
    private static RequestLogDao requestLogDao;
    private static RequestLogEventDispatcher requestLogEventDispatcher;
    private static InspectorTableModel inspectorTableModel;
    private static ChaosRuleStore chaosRuleStore;
    private static MockRouteStore mockRouteStore;

    private AppContext() {
        // Static registry; prevent instantiation
    }

    /**
     * Initializes the AppContext registry with shared services after database setup.
     */
    public static synchronized void initialize() {
        databaseManager = DatabaseManager.getInstance();
        proxyRouteStore = new ProxyRouteStore();
        proxyServer = new ProxyServer();
        requestLogDao = new RequestLogDao(databaseManager);
        requestLogEventDispatcher = new RequestLogEventDispatcher();
        inspectorTableModel = new InspectorTableModel();
        chaosRuleStore = new ChaosRuleStore(new ChaosRuleDao(databaseManager));
        mockRouteStore = new MockRouteStore(new MockRouteDao(databaseManager));
        System.out.println("[AppContext] Initialized shared service registry.");
    }

    /**
     * Overload to initialize AppContext with an existing DatabaseManager instance.
     *
     * @param dbManager The active DatabaseManager instance.
     */
    public static synchronized void initialize(DatabaseManager dbManager) {
        databaseManager = dbManager;
        proxyRouteStore = new ProxyRouteStore();
        proxyServer = new ProxyServer();
        requestLogDao = new RequestLogDao(databaseManager);
        requestLogEventDispatcher = new RequestLogEventDispatcher();
        inspectorTableModel = new InspectorTableModel();
        chaosRuleStore = new ChaosRuleStore(new ChaosRuleDao(databaseManager));
        mockRouteStore = new MockRouteStore(new MockRouteDao(databaseManager));
        System.out.println("[AppContext] Initialized shared service registry with provided DatabaseManager.");
    }

    /**
     * Retrieves the shared DatabaseManager singleton.
     *
     * @return DatabaseManager instance.
     */
    public static synchronized DatabaseManager getDatabaseManager() {
        if (databaseManager == null) {
            databaseManager = DatabaseManager.getInstance();
        }
        return databaseManager;
    }

    /**
     * Retrieves the shared ProxyRouteStore singleton.
     *
     * @return ProxyRouteStore instance.
     */
    public static synchronized ProxyRouteStore getProxyRouteStore() {
        if (proxyRouteStore == null) {
            proxyRouteStore = new ProxyRouteStore();
        }
        return proxyRouteStore;
    }

    /**
     * Retrieves the shared ProxyServer singleton.
     *
     * @return ProxyServer instance.
     */
    public static synchronized ProxyServer getProxyServer() {
        if (proxyServer == null) {
            proxyServer = new ProxyServer();
        }
        return proxyServer;
    }

    /**
     * Retrieves the shared RequestLogDao singleton.
     *
     * @return RequestLogDao instance.
     */
    public static synchronized RequestLogDao getRequestLogDao() {
        if (requestLogDao == null) {
            requestLogDao = new RequestLogDao(getDatabaseManager());
        }
        return requestLogDao;
    }

    /**
     * Retrieves the shared RequestLogEventDispatcher singleton.
     *
     * @return RequestLogEventDispatcher instance.
     */
    public static synchronized RequestLogEventDispatcher getRequestLogEventDispatcher() {
        if (requestLogEventDispatcher == null) {
            requestLogEventDispatcher = new RequestLogEventDispatcher();
        }
        return requestLogEventDispatcher;
    }

    /**
     * Retrieves the shared InspectorTableModel singleton.
     *
     * @return InspectorTableModel instance.
     */
    public static synchronized InspectorTableModel getInspectorTableModel() {
        if (inspectorTableModel == null) {
            inspectorTableModel = new InspectorTableModel();
        }
        return inspectorTableModel;
    }

    /**
     * Retrieves the shared ChaosRuleStore singleton.
     *
     * @return ChaosRuleStore instance.
     */
    public static synchronized ChaosRuleStore getChaosRuleStore() {
        if (chaosRuleStore == null) {
            chaosRuleStore = new ChaosRuleStore(new ChaosRuleDao(getDatabaseManager()));
        }
        return chaosRuleStore;
    }

    /**
     * Retrieves the shared MockRouteStore singleton.
     *
     * @return MockRouteStore instance.
     */
    public static synchronized MockRouteStore getMockRouteStore() {
        if (mockRouteStore == null) {
            mockRouteStore = new MockRouteStore(new MockRouteDao(getDatabaseManager()));
        }
        return mockRouteStore;
    }
}
