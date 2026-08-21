package dev.devce.rocketnautics.server.telemetry;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Built-in HTTP Telemetry & Ephemeris Server.
 * Exposes astronomical, celestial mechanics, and spacecraft data as JSON.
 */
public final class TelemetryServer {
    public static final TelemetryServer INSTANCE = new TelemetryServer();

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicReference<TelemetrySnapshot> currentSnapshot = new AtomicReference<>();
    private boolean isRunning = false;
    private long tickCounter = 0;

    private TelemetryServer() {}

    public synchronized void start(MinecraftServer mcServer) {
        if (isRunning) return;

        if (!RocketConfig.SERVER.telemetryServerEnabled.get()) {
            RocketNautics.LOGGER.info("[TelemetryServer] Disabled in configuration.");
            return;
        }

        String bindAddress = RocketConfig.SERVER.telemetryServerBind.get();
        int port = RocketConfig.SERVER.telemetryServerPort.get();
        String authToken = RocketConfig.SERVER.telemetryServerToken.get();

        try {
            InetSocketAddress address = new InetSocketAddress(bindAddress, port);
            server = HttpServer.create(address, 0);

            AtomicInteger threadId = new AtomicInteger(1);
            executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "TelemetryServer-Worker-" + threadId.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });
            server.setExecutor(executor);

            // Register endpoint handlers
            registerContext("/api/v1/dump", new TelemetryHandlers.DumpHandler(currentSnapshot), authToken);
            registerContext("/api/v1/time", new TelemetryHandlers.TimeHandler(currentSnapshot), authToken);
            registerContext("/api/v1/constants", new TelemetryHandlers.ConstantsHandler(currentSnapshot), authToken);
            registerContext("/api/v1/frames", new TelemetryHandlers.FramesHandler(currentSnapshot), authToken);
            registerContext("/api/v1/bodies", new TelemetryHandlers.BodiesHandler(currentSnapshot), authToken);
            registerContext("/api/v1/vessels", new TelemetryHandlers.VesselsHandler(currentSnapshot), authToken);
            registerContext("/api/v1", new TelemetryHandlers.DiscoveryHandler(), authToken);

            server.start();
            isRunning = true;
            RocketNautics.LOGGER.info("[TelemetryServer] HTTP Telemetry server listening on http://{}:{}/api/v1", bindAddress, port);

            // Initial capture
            currentSnapshot.set(TelemetrySnapshot.capture(mcServer));
        } catch (Exception e) {
            RocketNautics.LOGGER.error("[TelemetryServer] Failed to start HTTP server on {}:{}", bindAddress, port, e);
        }
    }

    private void registerContext(String path, com.sun.net.httpserver.HttpHandler handler, String authToken) {
        HttpContext context = server.createContext(path, handler);
        if (authToken != null && !authToken.trim().isEmpty()) {
            context.getFilters().add(new AuthFilter(authToken.trim()));
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        try {
            if (server != null) {
                server.stop(1);
                server = null;
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            isRunning = false;
            RocketNautics.LOGGER.info("[TelemetryServer] HTTP Telemetry server stopped.");
        } catch (Exception e) {
            RocketNautics.LOGGER.error("[TelemetryServer] Error while stopping HTTP server", e);
        }
    }

    public void tick(MinecraftServer mcServer) {
        if (!isRunning) {
            if (RocketConfig.SERVER.telemetryServerEnabled.get()) {
                start(mcServer);
            }
            return;
        }

        if (!RocketConfig.SERVER.telemetryServerEnabled.get()) {
            stop();
            return;
        }

        tickCounter++;
        int interval = Math.max(1, RocketConfig.SERVER.telemetrySnapshotInterval.get());
        if (tickCounter % interval == 0) {
            currentSnapshot.set(TelemetrySnapshot.capture(mcServer));
        }
    }

    public AtomicReference<TelemetrySnapshot> getCurrentSnapshot() {
        return currentSnapshot;
    }

    public boolean isRunning() {
        return isRunning;
    }

    private static class AuthFilter extends Filter {
        private final String expectedToken;

        public AuthFilter(String token) {
            this.expectedToken = token;
        }

        @Override
        public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                chain.doFilter(exchange);
                return;
            }
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ") || !authHeader.substring(7).trim().equals(expectedToken)) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Unauthorized: Valid Bearer token required");
                TelemetryHandlers.sendJsonResponse(exchange, 401, err);
                return;
            }
            chain.doFilter(exchange);
        }

        @Override
        public String description() {
            return "Bearer token authentication filter";
        }
    }
}
