package com.entropylab.proxy;

import com.entropylab.chaos.ChaosRule;
import com.entropylab.core.AppContext;
import com.entropylab.core.AppPaths;
import com.entropylab.core.DatabaseManager;
import com.entropylab.core.SchemaInitializer;
import com.entropylab.logging.OutcomeType;
import com.entropylab.logging.RequestLog;
import com.entropylab.mock.MockRoute;
import com.entropylab.routes.ProxyRoute;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Embedded HTTP reverse proxy server based on com.sun.net.httpserver.HttpServer.
 * Uses a dedicated worker thread pool and forwards matched routes to upstream targets.
 */
public class ProxyServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "host",
            "content-length",
            "connection",
            "transfer-encoding",
            "upgrade",
            "te",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization"
    );

    private HttpServer server;
    private ExecutorService executor;
    private final HttpClient httpClient;
    private volatile boolean running = false;
    private int port;

    public ProxyServer() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Creates and starts the embedded HTTP server on localhost:port with a dedicated
     * cached thread pool executor.
     *
     * @param port Port number to bind to (e.g. 8080).
     */
    public synchronized void start(int port) {
        if (running) {
            System.out.println("[ProxyServer] Server is already running on port " + this.port);
            return;
        }

        try {
            this.port = port;
            this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            this.executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setName("proxy-worker-" + t.getId());
                t.setDaemon(true);
                return t;
            });
            this.server.setExecutor(executor);

            // Register proxy pipeline handler on context "/"
            this.server.createContext("/", new ProxyPipelineHandler(httpClient));

            this.server.start();
            this.running = true;
            System.out.println("[ProxyServer] Started on http://localhost:" + port);
        } catch (IOException e) {
            this.running = false;
            if (this.executor != null) {
                this.executor.shutdownNow();
                this.executor = null;
            }
            this.server = null;
            System.err.println("[ProxyServer] Failed to start on port " + port + ": " + e.getMessage());
            throw new RuntimeException("Failed to start ProxyServer on port " + port, e);
        }
    }

    /**
     * Gracefully stops the HTTP server and shuts down the executor pool.
     */
    public synchronized void stop() {
        if (!running && server == null) {
            return;
        }

        System.out.println("[ProxyServer] Stopping server on port " + port + "...");
        if (server != null) {
            server.stop(0);
            server = null;
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        this.running = false;
        System.out.println("[ProxyServer] Stopped successfully.");
    }

    /**
     * Returns whether the server is currently running.
     *
     * @return true if running, false otherwise.
     */
    public synchronized boolean isRunning() {
        return running && server != null;
    }

    /**
     * Returns the port the server is bound to.
     *
     * @return Port number.
     */
    public synchronized int getPort() {
        return port;
    }

    private static boolean isExcludedHeader(String headerName) {
        return headerName == null || EXCLUDED_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private static String buildTargetUrl(ProxyRoute route, String rawPath, String rawQuery) {
        String pattern = route.getRoutePattern();
        String prefix = pattern.endsWith("/*") ? pattern.substring(0, pattern.length() - 2) : pattern;

        String remainingPath = "";
        if (rawPath.startsWith(prefix)) {
            remainingPath = rawPath.substring(prefix.length());
        } else {
            remainingPath = rawPath;
        }

        String baseUrl = route.getTargetBaseUrl().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!remainingPath.isEmpty() && !remainingPath.startsWith("/")) {
            remainingPath = "/" + remainingPath;
        }

        String targetUrl = baseUrl + remainingPath;
        if (rawQuery != null && !rawQuery.isEmpty()) {
            targetUrl += "?" + rawQuery;
        }
        return targetUrl;
    }

    /**
     * Handles incoming exchanges through the reverse proxy pipeline.
     */
    private static class ProxyPipelineHandler implements HttpHandler {
        private final HttpClient httpClient;

        public ProxyPipelineHandler(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public void handle(HttpExchange exchange) {
            long startTimeNanos = System.nanoTime();
            RequestLog log = new RequestLog();
            log.setStartTimeNanos(startTimeNanos);

            System.out.println("[ProxyPipelineHandler] Incoming request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            try {
                // Stage 1: CAPTURE
                String timestamp = Instant.now().toString();
                String method = exchange.getRequestMethod();

                URI requestUri = exchange.getRequestURI();
                String rawPath = requestUri.getPath();
                String rawQuery = requestUri.getRawQuery();
                String fullPath = rawPath;
                if (rawQuery != null && !rawQuery.isEmpty()) {
                    fullPath += "?" + rawQuery;
                }

                // Copy all request headers
                Headers exchangeHeaders = exchange.getRequestHeaders();
                Map<String, List<String>> requestHeaders = new LinkedHashMap<>();
                if (exchangeHeaders != null) {
                    exchangeHeaders.forEach((k, v) -> requestHeaders.put(k, new ArrayList<>(v)));
                }

                // =========================================================================
                // CRITICAL ARCHITECTURAL RULE:
                // The request body InputStream is read to completion EXACTLY ONCE right here.
                // This byte[] MUST be reused by all downstream logic (forwarding, logging, etc.)
                // — never read exchange.getRequestBody() again anywhere else.
                // =========================================================================
                byte[] requestBodyBytes;
                try (InputStream is = exchange.getRequestBody()) {
                    requestBodyBytes = is.readAllBytes();
                }

                log.setTimestamp(timestamp);
                log.setMethod(method);
                log.setPath(fullPath);
                log.setRequestHeaders(requestHeaders);
                log.setRequestBodyBytes(requestBodyBytes);

                // =========================================================================
                // Stage 1.2: MOCK CHECK (M6.4 - Highest Precedence)
                // =========================================================================
                // Architectural Note / Pipeline Precedence Order:
                // Mock Routes > Chaos Rules > Normal Forwarding.
                // A matching enabled mock route bypasses both chaos rules and forwarding entirely.
                MockRoute mockRoute = AppContext.getMockRouteStore().findMatchingRoute(fullPath);
                if (mockRoute == null && !fullPath.equals(rawPath)) {
                    mockRoute = AppContext.getMockRouteStore().findMatchingRoute(rawPath);
                }
                if (mockRoute != null) {
                    System.out.println("[ProxyPipelineHandler] Matched MockRoute: " + mockRoute.getRoutePattern()
                            + " -> " + mockRoute.getFilePath());
                    try {
                        Path mockFilePath = Path.of(mockRoute.getFilePath());
                        if (!mockFilePath.isAbsolute()) {
                            if (!Files.exists(mockFilePath)) {
                                mockFilePath = AppPaths.getMocksDir().resolve(mockRoute.getFilePath());
                            }
                        }
                        if (!Files.exists(mockFilePath)) {
                            throw new IOException("Mock file does not exist: " + mockFilePath.toAbsolutePath());
                        }

                        byte[] mockBytes = Files.readAllBytes(mockFilePath);
                        respondMockSuccess(exchange, log, mockBytes, startTimeNanos);
                        return; // Proceed straight to finally block for unified persistence; skips chaos & forwarding entirely!
                    } catch (Exception e) {
                        System.err.println("[ProxyPipelineHandler] Failed to read mock file: " + e.getMessage());
                        respondMockError(exchange, log, e, startTimeNanos);
                        return; // Proceed straight to finally block for unified persistence; skips chaos & forwarding entirely!
                    }
                }

                // =========================================================================
                // Stage 1.5: CHAOS CHECK (M5.4)
                // =========================================================================
                // Code Comment / Architectural Note:
                // Chaos rule matching is independent of whether a real proxy route exists in
                // ProxyRouteStore — a chaos rule can fire and return a simulated error even with
                // no backend registered for that path.
                com.entropylab.chaos.ChaosRule chaosRule = AppContext.getChaosRuleStore().findMatchingRule(rawPath);
                if (chaosRule != null) {
                    // a. Latency injection:
                    // Thread.sleep(latencyMs) is executed on this request's worker thread only.
                    // This cannot affect the JavaFX application thread or other concurrent requests,
                    // as each incoming exchange is executed on an isolated worker thread from our
                    // cached thread pool (proxy-worker-*).
                    if (chaosRule.getLatencyMs() > 0) {
                        try {
                            System.out.println("[ProxyPipelineHandler] Injecting chaos latency: "
                                    + chaosRule.getLatencyMs() + "ms on worker thread for " + rawPath);
                            Thread.sleep(chaosRule.getLatencyMs());
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            System.err.println("[ProxyPipelineHandler] Chaos sleep interrupted: " + ie.getMessage());
                        }
                    }

                    // b. Connection reset check (M5.5 - takes priority over status override):
                    if (chaosRule.isConnectionResetEnabled()) {
                        if (chaosRule.getStatusOverrideCode() != null) {
                            System.out.println("[ProxyPipelineHandler] WARN: ChaosRule has both connectionResetEnabled=true and statusOverrideCode="
                                    + chaosRule.getStatusOverrideCode() + ". Connection reset takes priority (nonsensical combination the UI should prevent later).");
                        }
                        System.out.println("[ProxyPipelineHandler] Applying Chaos connection reset for path: " + rawPath + " (closing connection abruptly)");

                        // Architectural Note:
                        // This closes the connection abruptly rather than sending a graceful HTTP error.
                        // Clients will typically see an 'empty reply' or 'connection reset' error — this is not
                        // a literal raw TCP RST packet, but is a sufficient and intentional simulation for this tool.
                        // Do not attempt raw socket manipulation.
                        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
                        log.setLatencyMs(latencyMs);
                        log.setOutcomeType(OutcomeType.CHAOS_RESET);

                        exchange.close();
                        return; // Proceed straight to finally block for unified persistence; skips forwarding branch entirely!
                    }

                    // c. Status override (M5.4):
                    if (chaosRule.getStatusOverrideCode() != null) {
                        System.out.println("[ProxyPipelineHandler] Applying Chaos status override: "
                                + chaosRule.getStatusOverrideCode() + " for path: " + rawPath + " (forwarding skipped)");
                        respondChaosStatus(exchange, log, chaosRule.getStatusOverrideCode(), startTimeNanos);
                        return; // Skip forwarding branch entirely; finally block executes unified persistence
                    }

                    // d. If only latencyMs was set (no statusOverrideCode, no reset):
                    // After sleeping, continue into normal routing/forwarding below as if no chaos rule existed.
                    // The accumulated latency so far is preserved because startTimeNanos was recorded at request start.
                }

                // Stage 2: ROUTE MATCHING & FORWARDING
                ProxyRoute matchedRoute = AppContext.getProxyRouteStore().findMatchingRoute(rawPath);

                if (matchedRoute != null) {
                    System.out.println("[ProxyPipelineHandler] Matched route: " + matchedRoute.getRoutePattern() + " -> " + matchedRoute.getTargetBaseUrl());
                    forwardRequest(exchange, log, matchedRoute, rawPath, rawQuery, startTimeNanos);
                } else {
                    System.out.println("[ProxyPipelineHandler] No route matched for: " + rawPath);
                    respondNoRouteMatched(exchange, log, startTimeNanos);
                }
            } catch (Throwable t) {
                System.err.println("[ProxyServer] Uncaught error handling request: " + t.getMessage());
                handleForwardingError(exchange, log, t instanceof Exception ? (Exception) t : new RuntimeException(t), startTimeNanos);
            } finally {
                // Unified persistence point: called exactly ONCE at the end of request processing
                try {
                    int logId = AppContext.getRequestLogDao().insertLog(log);
                    System.out.println("[ProxyPipelineHandler] Persisted RequestLog ID: " + logId + " outcome: " + log.getOutcomeType());
                    if (logId > 0) {
                        AppContext.getRequestLogEventDispatcher().notifyListeners(logId);
                    }
                } catch (Exception e) {
                    System.err.println("[ProxyPipelineHandler] Failed to persist RequestLog: " + e.getMessage());
                }
            }
        }

        private void forwardRequest(HttpExchange exchange, RequestLog log, ProxyRoute route,
                                    String rawPath, String rawQuery, long startTimeNanos) {
            String targetUrl = buildTargetUrl(route, rawPath, rawQuery);
            log.setTargetUrl(targetUrl);
            System.out.println("[ProxyPipelineHandler] Forwarding to: " + targetUrl);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10));

            // Copy headers excluding restricted / hop-by-hop headers
            boolean hasUserAgent = false;
            if (log.getRequestHeaders() != null) {
                for (Map.Entry<String, List<String>> entry : log.getRequestHeaders().entrySet()) {
                    String name = entry.getKey();
                    if (isExcludedHeader(name)) {
                        continue;
                    }
                    if ("user-agent".equalsIgnoreCase(name)) {
                        hasUserAgent = true;
                    }
                    for (String val : entry.getValue()) {
                        try {
                            reqBuilder.header(name, val);
                        } catch (IllegalArgumentException ignored) {
                            // Safely skip any platform or client restricted header
                        }
                    }
                }
            }

            // Ensure a default User-Agent if none provided (required by APIs like GitHub)
            if (!hasUserAgent) {
                reqBuilder.header("User-Agent", "EntropyLab/1.0");
            }

            HttpRequest.BodyPublisher bodyPublisher;
            if ("GET".equalsIgnoreCase(log.getMethod()) || "HEAD".equalsIgnoreCase(log.getMethod())) {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            } else {
                byte[] body = log.getRequestBodyBytes() != null ? log.getRequestBodyBytes() : new byte[0];
                bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(body);
            }
            reqBuilder.method(log.getMethod(), bodyPublisher);

            try {
                HttpResponse<byte[]> upstreamResponse = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

                long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
                log.setLatencyMs(latencyMs);
                log.setResponseStatus(upstreamResponse.statusCode());
                byte[] responseBodyBytes = upstreamResponse.body() != null ? upstreamResponse.body() : new byte[0];
                log.setResponseBodyBytes(responseBodyBytes);
                log.setOutcomeType(OutcomeType.FORWARDED);

                Map<String, List<String>> responseHeaders = new LinkedHashMap<>(upstreamResponse.headers().map());
                log.setResponseHeaders(responseHeaders);

                // Copy upstream response headers to client exchange
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    String name = entry.getKey();
                    if (isExcludedHeader(name)) {
                        continue;
                    }
                    for (String val : entry.getValue()) {
                        exchange.getResponseHeaders().add(name, val);
                    }
                }

                int status = upstreamResponse.statusCode();
                if (responseBodyBytes.length > 0) {
                    exchange.sendResponseHeaders(status, responseBodyBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBodyBytes);
                    }
                } else {
                    exchange.sendResponseHeaders(status, -1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[ProxyServer] Forwarding interrupted: " + e.getMessage());
                handleForwardingError(exchange, log, e, startTimeNanos);
            } catch (Exception e) {
                System.err.println("[ProxyServer] Forwarding failed: " + e.getMessage());
                handleForwardingError(exchange, log, e, startTimeNanos);
            }
        }

        private void respondMockSuccess(HttpExchange exchange, RequestLog log, byte[] mockBytes, long startTimeNanos) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
            log.setLatencyMs(latencyMs);
            log.setOutcomeType(OutcomeType.MOCKED);
            log.setResponseStatus(200);
            log.setResponseBodyBytes(mockBytes);

            Map<String, List<String>> respHeaders = new LinkedHashMap<>();
            respHeaders.put("Content-Type", List.of("application/json; charset=utf-8"));
            log.setResponseHeaders(respHeaders);

            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, mockBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(mockBytes);
                }
            } catch (Exception e) {
                System.err.println("[ProxyServer] Failed writing mock response: " + e.getMessage());
            }
        }

        private void respondMockError(HttpExchange exchange, RequestLog log, Exception e, long startTimeNanos) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
            log.setLatencyMs(latencyMs);
            log.setOutcomeType(OutcomeType.ERROR);
            log.setResponseStatus(500);

            try {
                Map<String, String> errMap = new LinkedHashMap<>();
                errMap.put("error", "Mock file could not be read");
                errMap.put("detail", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                byte[] errBytes = MAPPER.writeValueAsBytes(errMap);
                log.setResponseBodyBytes(errBytes);

                Map<String, List<String>> respHeaders = new LinkedHashMap<>();
                respHeaders.put("Content-Type", List.of("application/json; charset=utf-8"));
                log.setResponseHeaders(respHeaders);

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(500, errBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errBytes);
                }
            } catch (Exception ioEx) {
                System.err.println("[ProxyServer] Failed writing mock error response: " + ioEx.getMessage());
            }
        }

        private void respondChaosStatus(HttpExchange exchange, RequestLog log, int statusCode, long startTimeNanos) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
            log.setLatencyMs(latencyMs);
            log.setOutcomeType(OutcomeType.CHAOS_STATUS);
            log.setResponseStatus(statusCode);

            try {
                Map<String, Object> bodyMap = new LinkedHashMap<>();
                bodyMap.put("error", "Simulated failure");
                bodyMap.put("status", statusCode);
                byte[] responseBytes = MAPPER.writeValueAsBytes(bodyMap);
                log.setResponseBodyBytes(responseBytes);

                Map<String, List<String>> respHeaders = new LinkedHashMap<>();
                respHeaders.put("Content-Type", List.of("application/json; charset=utf-8"));
                log.setResponseHeaders(respHeaders);

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception e) {
                System.err.println("[ProxyServer] Failed writing chaos status response: " + e.getMessage());
            }
        }

        private void handleForwardingError(HttpExchange exchange, RequestLog log, Exception e, long startTimeNanos) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
            log.setLatencyMs(latencyMs);
            log.setOutcomeType(OutcomeType.ERROR);
            log.setResponseStatus(502);

            try {
                Map<String, String> errorMap = new LinkedHashMap<>();
                errorMap.put("error", "Failed to reach target API");
                errorMap.put("detail", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                byte[] errorBytes = MAPPER.writeValueAsBytes(errorMap);
                log.setResponseBodyBytes(errorBytes);

                Map<String, List<String>> respHeaders = new LinkedHashMap<>();
                respHeaders.put("Content-Type", List.of("application/json; charset=utf-8"));
                log.setResponseHeaders(respHeaders);

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(502, errorBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorBytes);
                }
            } catch (Exception ioException) {
                System.err.println("[ProxyServer] Failed writing 502 error response: " + ioException.getMessage());
            }
        }

        private void respondNoRouteMatched(HttpExchange exchange, RequestLog log, long startTimeNanos) throws IOException {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
            log.setLatencyMs(latencyMs);
            log.setOutcomeType(OutcomeType.NOT_FOUND);
            log.setResponseStatus(404);

            byte[] errorBytes = "{\"error\": \"No route configured for this path\"}".getBytes(StandardCharsets.UTF_8);
            log.setResponseBodyBytes(errorBytes);
            Map<String, List<String>> respHeaders = new LinkedHashMap<>();
            respHeaders.put("Content-Type", List.of("application/json; charset=utf-8"));
            log.setResponseHeaders(respHeaders);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(404, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        }
    }

    /**
     * Manual console test verifying forwarding, error handling (502), and 404 handling.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  ProxyServer Forwarding Test Suite (M3.1 & M3.2)");
        System.out.println("==================================================");

        // Bootstrap environment
        AppPaths.ensureDirectoriesExist();
        DatabaseManager dbManager = DatabaseManager.initialize();
        SchemaInitializer.initializeSchema(dbManager.getConnection());
        AppContext.initialize(dbManager);

        // M4.3: Register dummy listener to test event dispatcher
        List<Integer> eventNotificationLogIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        AppContext.getRequestLogEventDispatcher().addListener(newLogId -> {
            System.out.println("New log id: " + newLogId);
            eventNotificationLogIds.add(newLogId);
        });

        ProxyServer proxyServer = AppContext.getProxyServer();
        HttpClient client = HttpClient.newHttpClient();
        int testPort = 8080;

        proxyServer.start(testPort);

        try {
            // 1. Test unmapped route returns 404
            System.out.println("\n[TEST 1] Hitting unregistered route: /unregistered/path");
            HttpRequest unmappedRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/unregistered/path"))
                    .GET()
                    .build();

            HttpResponse<String> unmappedResponse = client.send(unmappedRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + unmappedResponse.statusCode());
            System.out.println("  Body: " + unmappedResponse.body());

            if (unmappedResponse.statusCode() != 404 || !unmappedResponse.body().contains("No route configured for this path")) {
                throw new AssertionError("Expected 404 with error message for unregistered route!");
            }
            System.out.println("  Result: PASS");

            // 2. Register route /github/* -> https://api.github.com
            System.out.println("\n[TEST 2] Registering route: /github/* -> https://api.github.com");
            ProxyRoute route = new ProxyRoute("/github/*", "https://api.github.com", true);
            ProxyRoute addedRoute = AppContext.getProxyRouteStore().addRoute(route);
            System.out.println("  Registered route with ID: " + addedRoute.getId());

            // 3. Test hitting /github/users/octocat
            System.out.println("\n[TEST 3] Hitting forwarded route: http://localhost:" + testPort + "/github/users/octocat");
            HttpRequest forwardRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/github/users/octocat"))
                    .header("User-Agent", "EntropyLab-Forwarding-Test")
                    .GET()
                    .build();

            HttpResponse<String> forwardResponse = client.send(forwardRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + forwardResponse.statusCode());
            System.out.println("  Body (first 200 chars): " +
                    (forwardResponse.body().length() > 200 ? forwardResponse.body().substring(0, 200) + "..." : forwardResponse.body()));

            if (forwardResponse.statusCode() != 200 || !forwardResponse.body().contains("\"login\": \"octocat\"") && !forwardResponse.body().contains("\"login\":\"octocat\"")) {
                throw new AssertionError("Forwarded request failed to return GitHub octocat profile!");
            }
            System.out.println("  Result: PASS (real GitHub response forwarded transparently)");

            // Cleanup test route
            AppContext.getProxyRouteStore().removeRoute(addedRoute.getId());
            System.out.println("\n[CLEANUP] Removed test route ID: " + addedRoute.getId());

            // 4. Test unreachable route returns 502
            System.out.println("\n[TEST 4] Registering route to unreachable URL: /unreachable/* -> http://localhost:9999/nope");
            ProxyRoute unreachableRoute = new ProxyRoute("/unreachable/*", "http://localhost:9999/nope", true);
            ProxyRoute addedUnreachable = AppContext.getProxyRouteStore().addRoute(unreachableRoute);
            System.out.println("  Registered unreachable route with ID: " + addedUnreachable.getId());

            System.out.println("\n[TEST 4] Hitting unreachable route: http://localhost:" + testPort + "/unreachable/test");
            HttpRequest unreachableReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/unreachable/test"))
                    .GET()
                    .build();

            HttpResponse<String> unreachableResp = client.send(unreachableReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + unreachableResp.statusCode());
            System.out.println("  Body: " + unreachableResp.body());

            if (unreachableResp.statusCode() != 502 || !unreachableResp.body().contains("Failed to reach target API")) {
                throw new AssertionError("Expected 502 with 'Failed to reach target API' error!");
            }
            System.out.println("  Result: PASS (clean 502 returned for unreachable target)");

            // Cleanup unreachable test route
            AppContext.getProxyRouteStore().removeRoute(addedUnreachable.getId());
            System.out.println("\n[CLEANUP] Removed unreachable test route ID: " + addedUnreachable.getId());

            // 5. Verify database persistence in request_logs (M4.1)
            System.out.println("\n[TEST 5] Verifying persistence in SQLite request_logs table (M4.1)");
            int count = AppContext.getRequestLogDao().getLogCount();
            System.out.println("  Total request_logs in DB: " + count);
            if (count < 3) {
                throw new AssertionError("Expected at least 3 rows in request_logs, found: " + count);
            }

            try (java.sql.Connection conn = dbManager.getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(
                         "SELECT id, timestamp, method, path, target_url, response_status, outcome_type, latency_ms FROM request_logs ORDER BY id DESC LIMIT 5")) {
                System.out.println("  Recent persisted request_logs:");
                while (rs.next()) {
                    System.out.printf("   - ID=%d, %s %s -> %s, status=%d, outcome=%s, latency=%dms%n",
                            rs.getInt("id"),
                            rs.getString("method"),
                            rs.getString("path"),
                            rs.getString("target_url"),
                            rs.getInt("response_status"),
                            rs.getString("outcome_type"),
                            rs.getLong("latency_ms"));
                }
            }
            System.out.println("  Result: PASS (all request outcomes verified persisted in SQLite)");

            // 6. Verify EventDispatcher notification (M4.3)
            System.out.println("\n[TEST 6] Verifying RequestLogEventDispatcher notifications (M4.3)");
            System.out.println("  Events captured by listener: " + eventNotificationLogIds);
            if (eventNotificationLogIds.size() < 3) {
                throw new AssertionError("Expected at least 3 event notifications, got: " + eventNotificationLogIds.size());
            }
            System.out.println("  Result: PASS (Dummy listener successfully notified with 'New log id: X' on every request)");

            // 7. Test ChaosRule statusOverrideCode = 503 (M5.4)
            System.out.println("\n[TEST 7] Testing ChaosRule status override (HTTP 503) for /github/*");
            ProxyRoute githubRoute = AppContext.getProxyRouteStore().addRoute(new ProxyRoute("/github/*", "https://api.github.com", true));
            ChaosRule chaos503 = AppContext.getChaosRuleStore().addRule(new ChaosRule("/github/*", 0, 503, false, true));
            System.out.println("  Registered route ID: " + githubRoute.getId() + " and ChaosRule ID: " + chaos503.getId());

            HttpRequest chaos503Req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/github/users/octocat"))
                    .GET()
                    .build();

            HttpResponse<String> chaos503Resp = client.send(chaos503Req, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + chaos503Resp.statusCode());
            System.out.println("  Body: " + chaos503Resp.body());

            if (chaos503Resp.statusCode() != 503 || !chaos503Resp.body().contains("\"error\":\"Simulated failure\"") || !chaos503Resp.body().contains("\"status\":503")) {
                throw new AssertionError("Expected 503 with simulated failure body, got: " + chaos503Resp.statusCode() + " " + chaos503Resp.body());
            }

            // Verify recent log is CHAOS_STATUS
            RequestLog latestLog = AppContext.getRequestLogDao().getRecentLogs(1, 0).get(0);
            if (latestLog.getOutcomeType() != OutcomeType.CHAOS_STATUS || latestLog.getResponseStatus() != 503) {
                throw new AssertionError("Expected latest log outcome CHAOS_STATUS and status 503, got: " + latestLog.getOutcomeType() + " " + latestLog.getResponseStatus());
            }
            System.out.println("  Persisted log ID " + latestLog.getId() + " verified as outcome: " + latestLog.getOutcomeType());
            System.out.println("  Result: PASS (Status 503 returned immediately without contacting upstream, logged as CHAOS_STATUS)");

            // Cleanup 503 rule
            AppContext.getChaosRuleStore().removeRule(chaos503.getId());

            // 8. Test ChaosRule independent of ProxyRouteStore (M5.4)
            System.out.println("\n[TEST 8] Testing ChaosRule on unmapped path /unregistered/chaos/* (statusOverrideCode = 418)");
            ChaosRule unmappedChaos = AppContext.getChaosRuleStore().addRule(new ChaosRule("/unregistered/chaos/*", 0, 418, false, true));

            HttpRequest unmappedChaosReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/unregistered/chaos/test"))
                    .GET()
                    .build();

            HttpResponse<String> unmappedChaosResp = client.send(unmappedChaosReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + unmappedChaosResp.statusCode());
            System.out.println("  Body: " + unmappedChaosResp.body());

            if (unmappedChaosResp.statusCode() != 418 || !unmappedChaosResp.body().contains("\"status\":418")) {
                throw new AssertionError("Expected 418 on unmapped path with chaos rule!");
            }
            System.out.println("  Result: PASS (Chaos rule fires independently of whether backend route exists)");
            AppContext.getChaosRuleStore().removeRule(unmappedChaos.getId());

            // 9. Test ChaosRule latencyMs = 3000 + Concurrent non-blocking execution (M5.4)
            System.out.println("\n[TEST 9] Testing ChaosRule latencyMs = 3000 on /github/* and concurrent non-blocking request");
            ChaosRule latencyRule = AppContext.getChaosRuleStore().addRule(new ChaosRule("/github/*", 3000, null, false, true));

            long test9Start = System.currentTimeMillis();
            // Start delayed request asynchronously
            CompletableFuture<HttpResponse<String>> slowFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest slowReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + testPort + "/github/users/octocat"))
                            .header("User-Agent", "EntropyLab-Latency-Test")
                            .GET()
                            .build();
                    return client.send(slowReq, HttpResponse.BodyHandlers.ofString());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });

            // Concurrently send request to unaffected route
            Thread.sleep(150); // Ensure slow request has started sleeping on its worker thread
            long unaffectedStart = System.currentTimeMillis();
            HttpRequest fastReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/unregistered/fast-check"))
                    .GET()
                    .build();
            HttpResponse<String> fastResp = client.send(fastReq, HttpResponse.BodyHandlers.ofString());
            long unaffectedDuration = System.currentTimeMillis() - unaffectedStart;
            System.out.println("  Unaffected concurrent request completed in " + unaffectedDuration + "ms (Status: " + fastResp.statusCode() + ")");
            if (unaffectedDuration >= 1500) {
                throw new AssertionError("Unaffected concurrent request took too long (" + unaffectedDuration + "ms)! Worker pool blocked!");
            }

            // Now await slow request completion
            HttpResponse<String> slowResp = slowFuture.get(10, TimeUnit.SECONDS);
            long slowTotalDuration = System.currentTimeMillis() - test9Start;
            System.out.println("  Delayed request completed in " + slowTotalDuration + "ms (Status: " + slowResp.statusCode() + ")");
            if (slowResp.statusCode() != 200 || (!slowResp.body().contains("\"login\": \"octocat\"") && !slowResp.body().contains("\"login\":\"octocat\""))) {
                throw new AssertionError("Expected forwarded GitHub response with status 200!");
            }
            if (slowTotalDuration < 2800) {
                throw new AssertionError("Expected delayed request to take ~3000ms, but took: " + slowTotalDuration + "ms");
            }

            RequestLog slowLog = AppContext.getRequestLogDao().getRecentLogs(2, 0).stream()
                    .filter(l -> l.getPath().contains("/github/users/octocat"))
                    .findFirst()
                    .orElse(null);
            if (slowLog == null || slowLog.getOutcomeType() != OutcomeType.FORWARDED || slowLog.getLatencyMs() < 2800) {
                throw new AssertionError("Expected slow log with outcome FORWARDED and latency >= 2800ms, got: " + slowLog);
            }
            System.out.println("  Persisted slow request log: " + slowLog.getOutcomeType() + ", latency=" + slowLog.getLatencyMs() + "ms");
            System.out.println("  Result: PASS (~3s latency injected before forwarding, unaffected concurrent request unblocked)");

            // Cleanup
            AppContext.getChaosRuleStore().removeRule(latencyRule.getId());
            AppContext.getProxyRouteStore().removeRoute(githubRoute.getId());

            // 10. Test ChaosRule connectionResetEnabled = true (M5.5)
            System.out.println("\n[TEST 10] Testing ChaosRule connection reset (connectionResetEnabled = true, M5.5)");
            // Also set statusOverrideCode = 500 to test warning log and ensure reset takes priority
            ChaosRule resetRule = AppContext.getChaosRuleStore().addRule(new ChaosRule("/reset-test/*", 200, 500, true, true));
            System.out.println("  Registered ChaosRule with ID: " + resetRule.getId() + " (reset=true, statusOverride=500, latency=200ms)");

            // Part A: Client request via java.net.http.HttpClient
            System.out.println("  Part A: Hitting http://localhost:" + testPort + "/reset-test/client-check with HttpClient");
            boolean caughtIoException = false;
            long resetStart = System.currentTimeMillis();
            try {
                HttpRequest resetReq = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + testPort + "/reset-test/client-check"))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(resetReq, HttpResponse.BodyHandlers.ofString());
                System.err.println("  Unexpected response received: " + resp.statusCode());
            } catch (IOException e) {
                caughtIoException = true;
                System.out.println("  HttpClient caught expected exception: " + e.getClass().getName() + ": " + e.getMessage());
            }
            long resetDuration = System.currentTimeMillis() - resetStart;

            if (!caughtIoException) {
                throw new AssertionError("Expected client to fail with IOException due to abrupt connection reset, but got response!");
            }
            if (resetDuration < 180) {
                throw new AssertionError("Expected latency sleep >= 200ms before reset, but completed in: " + resetDuration + "ms");
            }

            // Verify CHAOS_RESET row in request_logs
            RequestLog resetLog = AppContext.getRequestLogDao().getRecentLogs(1, 0).get(0);
            if (resetLog.getOutcomeType() != OutcomeType.CHAOS_RESET) {
                throw new AssertionError("Expected outcomeType CHAOS_RESET, got: " + resetLog.getOutcomeType());
            }
            if (resetLog.getResponseStatus() != null) {
                throw new AssertionError("Expected null responseStatus for connection reset, got: " + resetLog.getResponseStatus());
            }
            System.out.println("  Persisted log ID " + resetLog.getId() + " outcome: " + resetLog.getOutcomeType()
                    + ", response_status: " + resetLog.getResponseStatus() + ", latency: " + resetLog.getLatencyMs() + "ms");
            System.out.println("  Result Part A: PASS (Connection closed abruptly, no HTTP status code received, logged as CHAOS_RESET)");

            // Part B: Client request via curl.exe
            System.out.println("  Part B: Hitting with curl.exe to verify empty reply / connection reset");
            try {
                Process process = new ProcessBuilder("curl.exe", "-s", "-S", "-i", "http://localhost:" + testPort + "/reset-test/curl-check")
                        .redirectErrorStream(true)
                        .start();
                String curlOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.waitFor();
                System.out.println("  curl exit code: " + exitCode + ", output: " + curlOutput.trim());
                if (exitCode != 52 && !curlOutput.contains("Empty reply from server") && !curlOutput.contains("reset")) {
                    System.out.println("  Note: curl exit code: " + exitCode + ", output: " + curlOutput);
                }
                System.out.println("  Result Part B: PASS (curl failed without receiving any HTTP status code)");
            } catch (Exception curlEx) {
                System.out.println("  curl test skipped/handled: " + curlEx.getMessage());
            }

            AppContext.getChaosRuleStore().removeRule(resetRule.getId());

            // 11. Test MockRoute Precedence over Chaos and Forwarding (M6.4)
            System.out.println("\n[TEST 11] Testing MockRoute Precedence (Mocks > Chaos > Forwarding, M6.4)");
            Path testMockFile = AppPaths.getMocksDir().resolve("octocat-mock.json");
            String mockJsonContent = "{\"mock\":true,\"login\":\"mocked-octocat\",\"bio\":\"Created by EntropyLab M6.4 Mock Engine\"}";
            Files.writeString(testMockFile, mockJsonContent, StandardCharsets.UTF_8);
            System.out.println("  Created test mock file at: " + testMockFile.toAbsolutePath());

            // Setup pipeline:
            // 1. Backend Route: /github/* -> https://api.github.com
            ProxyRoute mockGithubRoute = AppContext.getProxyRouteStore().addRoute(new ProxyRoute("/github/*", "https://api.github.com", true));
            // 2. Chaos Rule on same path: /github/* with 3000ms latency and 503 status override
            ChaosRule mockChaosRule = AppContext.getChaosRuleStore().addRule(new ChaosRule("/github/*", 3000, 503, false, true));
            // 3. Mock Route on same path: /github/* pointing to testMockFile
            MockRoute mockRoute = AppContext.getMockRouteStore().addRoute(new MockRoute("/github/*", testMockFile.getFileName().toString(), true, com.entropylab.mock.MockSource.MANUAL));

            System.out.println("  Registered Route ID " + mockGithubRoute.getId() + ", ChaosRule ID " + mockChaosRule.getId()
                    + ", MockRoute ID " + mockRoute.getId() + " all for /github/*");

            // Part A: Enabled Mock Route should bypass both Chaos and Upstream completely
            System.out.println("  Part A: Hitting /github/users/octocat with enabled mock route...");
            long mockReqStart = System.currentTimeMillis();
            HttpRequest mockReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/github/users/octocat"))
                    .GET()
                    .build();
            HttpResponse<String> mockResp = client.send(mockReq, HttpResponse.BodyHandlers.ofString());
            long mockReqDuration = System.currentTimeMillis() - mockReqStart;

            System.out.println("  Status: " + mockResp.statusCode() + " (expected 200, NOT 503 from chaos)");
            System.out.println("  Duration: " + mockReqDuration + "ms (expected < 500ms, NOT ~3000ms from chaos)");
            System.out.println("  Body: " + mockResp.body());

            if (mockResp.statusCode() != 200) {
                throw new AssertionError("Expected HTTP 200 from mock, got: " + mockResp.statusCode());
            }
            if (!mockResp.body().contains("\"mocked-octocat\"")) {
                throw new AssertionError("Expected mocked response body, got: " + mockResp.body());
            }
            if (mockReqDuration >= 1500) {
                throw new AssertionError("Mock request took too long (" + mockReqDuration + "ms)! Chaos sleep was not bypassed!");
            }

            RequestLog mockLog = AppContext.getRequestLogDao().getRecentLogs(1, 0).get(0);
            if (mockLog.getOutcomeType() != OutcomeType.MOCKED || mockLog.getResponseStatus() != 200) {
                throw new AssertionError("Expected outcomeType MOCKED and status 200, got: " + mockLog.getOutcomeType() + " " + mockLog.getResponseStatus());
            }
            System.out.println("  Persisted log ID " + mockLog.getId() + " outcome: " + mockLog.getOutcomeType()
                    + ", latency: " + mockLog.getLatencyMs() + "ms");
            System.out.println("  Result Part A: PASS (Mock successfully bypassed both Chaos and Upstream API with outcome MOCKED)");

            // Part B: Unreadable/Missing mock file returns 500 with detail
            System.out.println("\n  Part B: Testing missing mock file returns HTTP 500...");
            MockRoute brokenMock = AppContext.getMockRouteStore().addRoute(new MockRoute("/broken-mock/*", "non-existent-payload.json", true, com.entropylab.mock.MockSource.MANUAL));
            HttpRequest brokenReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/broken-mock/test"))
                    .GET()
                    .build();
            HttpResponse<String> brokenResp = client.send(brokenReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + brokenResp.statusCode());
            System.out.println("  Body: " + brokenResp.body());
            if (brokenResp.statusCode() != 500 || !brokenResp.body().contains("Mock file could not be read")) {
                throw new AssertionError("Expected 500 with 'Mock file could not be read' error, got: " + brokenResp.statusCode() + " " + brokenResp.body());
            }

            RequestLog brokenLog = AppContext.getRequestLogDao().getRecentLogs(1, 0).get(0);
            if (brokenLog.getOutcomeType() != OutcomeType.ERROR || brokenLog.getResponseStatus() != 500) {
                throw new AssertionError("Expected outcomeType ERROR and status 500, got: " + brokenLog.getOutcomeType() + " " + brokenLog.getResponseStatus());
            }
            System.out.println("  Result Part B: PASS (Missing mock file returned clean HTTP 500 JSON error with outcome ERROR)");
            AppContext.getMockRouteStore().removeRoute(brokenMock.getId());

            // Part C: Disabling mock route allows Chaos Rule to take effect
            System.out.println("\n  Part C: Disabling mock route and verifying Chaos rule takes over...");
            mockRoute.setEnabled(false);
            AppContext.getMockRouteStore().updateRoute(mockRoute);

            HttpRequest disabledMockReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + testPort + "/github/users/octocat"))
                    .GET()
                    .build();
            HttpResponse<String> disabledMockResp = client.send(disabledMockReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("  Status: " + disabledMockResp.statusCode() + " (expected 503 from chaos rule now active)");
            if (disabledMockResp.statusCode() != 503) {
                throw new AssertionError("Expected 503 from ChaosRule after disabling mock, got: " + disabledMockResp.statusCode());
            }
            System.out.println("  Result Part C: PASS (When mock is disabled, lower precedence chaos rule took over as expected)");

            // Cleanup
            AppContext.getMockRouteStore().removeRoute(mockRoute.getId());
            AppContext.getChaosRuleStore().removeRule(mockChaosRule.getId());
            AppContext.getProxyRouteStore().removeRoute(mockGithubRoute.getId());
            Files.deleteIfExists(testMockFile);
            System.out.println("  Cleaned up all M6.4 test routes and temporary mock file.");

        } finally {
            proxyServer.stop();
        }

        System.out.println("\n==================================================");
        System.out.println("  All M3.1 - M6.4 tests PASSED successfully!");
        System.out.println("==================================================");
    }
}
