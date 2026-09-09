package dev.devce.rocketnautics.server.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class TelemetryHandlers {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
        byte[] bytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
    }

    /**
     * Self-documenting discovery catalog handler at /api/v1
     */
    public static class DiscoveryHandler implements HttpHandler {
        private final byte[] cachedDiscoveryJson;

        public DiscoveryHandler() {
            JsonObject root = new JsonObject();
            root.addProperty("api_name", "Create-Cosmonautics DeepSpace Telemetry & Ephemeris API");
            root.addProperty("version", "1.0.0");
            root.addProperty("status", "ONLINE");
            root.addProperty("units_standard", "SI (meters, seconds, kilograms, Pascals, radians/degrees, Kelvin)");

            JsonObject endpoints = new JsonObject();

            addEndpoint(endpoints, "/api/v1", "GET", "Self-documenting directory listing all available API endpoints, parameters, and SI measurement units.");
            addEndpoint(endpoints, "/api/v1/dump", "GET", "All-in-One complete dump of all celestial bodies, active vessels, time, frames, and constants.", "flat=true (optional, returns dot-notation flat key-value dictionary)");
            addEndpoint(endpoints, "/api/v1/time", "GET", "Current universe time, tick count, Julian date, ISO epoch, and timescale factor.");
            addEndpoint(endpoints, "/api/v1/constants", "GET", "Universal gravitational, astronomical, and physical constants.");
            addEndpoint(endpoints, "/api/v1/frames", "GET", "Complete celestial reference frames tree (FrameTree hierarchy).");
            addEndpoint(endpoints, "/api/v1/bodies", "GET", "List of all celestial bodies (stars, planets, moons) with full orbital and physical properties.");
            addEndpoint(endpoints, "/api/v1/bodies/{id}", "GET", "Full telemetry dataset for a single celestial body (e.g. /api/v1/bodies/earth).");
            addEndpoint(endpoints, "/api/v1/bodies/{id}/position", "GET", "Instantaneous 3D Cartesian coordinates (XYZ) and velocity vectors (VxVyVz).");
            addEndpoint(endpoints, "/api/v1/bodies/{id}/orbit", "GET", "Keplerian orbital elements (semi-major axis, eccentricity, inclination, RAAN, AOP, anomalies, period).");
            addEndpoint(endpoints, "/api/v1/bodies/{id}/physics", "GET", "Gravitational parameters (mu, mass, radius, surface gravity, escape velocity, ROI).");
            addEndpoint(endpoints, "/api/v1/bodies/{id}/rotation", "GET", "Spatial orientation (quaternion WXYZ, rotation rate, day length).");
            addEndpoint(endpoints, "/api/v1/bodies/{id}/atmosphere", "GET", "Atmospheric transition altitude, hazard flags, and aerodynamic drag curve.");
            addEndpoint(endpoints, "/api/v1/vessels", "GET", "List of all active player spacecraft and stations in Deep Space.");
            addEndpoint(endpoints, "/api/v1/vessels/{id}", "GET", "Full orbital and positional telemetry of a specific vessel instance.");
            addEndpoint(endpoints, "/api/v1/vessels/{id}/position", "GET", "Instantaneous position and velocity of a spacecraft in Deep Space.");
            addEndpoint(endpoints, "/api/v1/vessels/{id}/orbit", "GET", "Keplerian orbital elements and apoapsis/periapsis of a spacecraft.");

            root.add("endpoints", endpoints);
            cachedDiscoveryJson = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        }

        private void addEndpoint(JsonObject parent, String path, String method, String description) {
            addEndpoint(parent, path, method, description, null);
        }

        private void addEndpoint(JsonObject parent, String path, String method, String description, String params) {
            JsonObject ep = new JsonObject();
            ep.addProperty("method", method);
            ep.addProperty("description", description);
            if (params != null) {
                ep.addProperty("query_parameters", params);
            }
            parent.add(path, ep);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, cachedDiscoveryJson.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(cachedDiscoveryJson);
            }
        }
    }

    /**
     * Complete dump endpoint: /api/v1/dump (supports ?flat=true)
     */
    public static class DumpHandler implements HttpHandler {
        private final AtomicReference<TelemetrySnapshot> snapshotRef;

        public DumpHandler(AtomicReference<TelemetrySnapshot> snapshotRef) {
            this.snapshotRef = snapshotRef;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            TelemetrySnapshot snapshot = snapshotRef.get();
            if (snapshot == null) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Telemetry snapshot not available yet");
                sendJsonResponse(exchange, 503, err);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            boolean flat = query != null && query.contains("flat=true");
            JsonObject response = flat ? snapshot.toFlatMap() : snapshot.toJson();
            sendJsonResponse(exchange, 200, response);
        }
    }

    /**
     * Time endpoint: /api/v1/time
     */
    public static class TimeHandler implements HttpHandler {
        private final AtomicReference<TelemetrySnapshot> snapshotRef;

        public TimeHandler(AtomicReference<TelemetrySnapshot> snapshotRef) {
            this.snapshotRef = snapshotRef;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            TelemetrySnapshot snapshot = snapshotRef.get();
            if (snapshot == null) {
                sendJsonResponse(exchange, 503, notAvailable());
                return;
            }
            sendJsonResponse(exchange, 200, snapshot.timeToJson());
        }
    }

    /**
     * Constants endpoint: /api/v1/constants
     */
    public static class ConstantsHandler implements HttpHandler {
        private final AtomicReference<TelemetrySnapshot> snapshotRef;

        public ConstantsHandler(AtomicReference<TelemetrySnapshot> snapshotRef) {
            this.snapshotRef = snapshotRef;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            TelemetrySnapshot snapshot = snapshotRef.get();
            if (snapshot == null) {
                sendJsonResponse(exchange, 503, notAvailable());
                return;
            }
            sendJsonResponse(exchange, 200, snapshot.constantsToJson());
        }
    }

    /**
     * Frames endpoint: /api/v1/frames
     */
    public static class FramesHandler implements HttpHandler {
        private final AtomicReference<TelemetrySnapshot> snapshotRef;

        public FramesHandler(AtomicReference<TelemetrySnapshot> snapshotRef) {
            this.snapshotRef = snapshotRef;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            TelemetrySnapshot snapshot = snapshotRef.get();
            if (snapshot == null) {
                sendJsonResponse(exchange, 503, notAvailable());
                return;
            }
            sendJsonResponse(exchange, 200, snapshot.framesToJson());
        }
    }

    /**
     * Celestial Bodies endpoint dispatcher: /api/v1/bodies and /api/v1/bodies/*
     */
    public static class BodiesHandler implements HttpHandler {
        private final AtomicReference<TelemetrySnapshot> snapshotRef;

        public BodiesHandler(AtomicReference<TelemetrySnapshot> snapshotRef) {
            this.snapshotRef = snapshotRef;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            TelemetrySnapshot snapshot = snapshotRef.get();
            if (snapshot == null) {
                sendJsonResponse(exchange, 503, notAvailable());
                return;
            }

            String path = exchange.getRequestURI().getPath();
            // Expected formats:
            // /api/v1/bodies
            // /api/v1/bodies/{id}
            // /api/v1/bodies/{id}/position
            // /api/v1/bodies/{id}/orbit
            // /api/v1/bodies/{id}/physics
            // /api/v1/bodies/{id}/rotation
            // /api/v1/bodies/{id}/atmosphere

            String prefix = "/api/v1/bodies";
            String subPath = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
            if (subPath.startsWith("/")) subPath = subPath.substring(1);

            if (subPath.isEmpty()) {
                sendJsonResponse(exchange, 200, snapshot.bodiesToJson());
                return;
            }

            String[] parts = subPath.split("/");
            String bodyId = parts[0].toLowerCase(Locale.ROOT);
            TelemetrySnapshot.BodyData body = snapshot.bodies.get(bodyId);
            if (body == null) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Celestial body not found");
                err.addProperty("requested_id", bodyId);
                sendJsonResponse(exchange, 404, err);
                return;
            }

            JsonObject fullBodyJson = snapshot.bodyToJson(body);

            if (parts.length == 1) {
                sendJsonResponse(exchange, 200, fullBodyJson);
                return;
            }

            String subTopic = parts[1].toLowerCase(Locale.ROOT);
            switch (subTopic) {
                case "position" -> {
                    JsonObject pos = new JsonObject();
                    pos.addProperty("body", body.name());
                    pos.add("position", fullBodyJson.get("position"));
                    pos.add("velocity", fullBodyJson.get("velocity"));
                    sendJsonResponse(exchange, 200, pos);
                }
                case "orbit" -> {
                    JsonObject orb = new JsonObject();
                    orb.addProperty("body", body.name());
                    orb.add("orbit", fullBodyJson.get("orbit"));
                    sendJsonResponse(exchange, 200, orb);
                }
                case "physics" -> {
                    JsonObject phys = new JsonObject();
                    phys.addProperty("body", body.name());
                    phys.add("physics", fullBodyJson.get("physics"));
                    sendJsonResponse(exchange, 200, phys);
                }
                case "rotation" -> {
                    JsonObject rot = new JsonObject();
                    rot.addProperty("body", body.name());
                    rot.add("rotation", fullBodyJson.get("rotation"));
                    sendJsonResponse(exchange, 200, rot);
                }
                case "atmosphere" -> {
                    JsonObject atmo = new JsonObject();
                    atmo.addProperty("body", body.name());
                    atmo.add("atmosphere", fullBodyJson.get("atmosphere"));
                    sendJsonResponse(exchange, 200, atmo);
                }
                default -> {
                    JsonObject err = new JsonObject();
                    err.addProperty("error", "Unknown subtopic for body: " + subTopic);
                    err.addProperty("valid_subtopics", "position, orbit, physics, rotation, atmosphere");
                    sendJsonResponse(exchange, 404, err);
                }
            }
        }
    }

    /**
     * Active Spacecraft / Vessels endpoint dispatcher: /api/v1/vessels and /api/v1/vessels/*
     */
    public static class VesselsHandler implements HttpHandler {
        private final AtomicReference<TelemetrySnapshot> snapshotRef;

        public VesselsHandler(AtomicReference<TelemetrySnapshot> snapshotRef) {
            this.snapshotRef = snapshotRef;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            TelemetrySnapshot snapshot = snapshotRef.get();
            if (snapshot == null) {
                sendJsonResponse(exchange, 503, notAvailable());
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String prefix = "/api/v1/vessels";
            String subPath = path.startsWith(prefix) ? path.substring(prefix.length()) : "";
            if (subPath.startsWith("/")) subPath = subPath.substring(1);

            if (subPath.isEmpty()) {
                sendJsonResponse(exchange, 200, snapshot.vesselsToJson());
                return;
            }

            String[] parts = subPath.split("/");
            String vesselId = parts[0];
            TelemetrySnapshot.VesselData vessel = snapshot.vessels.get(vesselId);
            if (vessel == null) {
                JsonObject err = new JsonObject();
                err.addProperty("error", "Vessel instance not found");
                err.addProperty("requested_instance_id", vesselId);
                sendJsonResponse(exchange, 404, err);
                return;
            }

            JsonObject fullVesselJson = snapshot.vesselToJson(vessel);

            if (parts.length == 1) {
                sendJsonResponse(exchange, 200, fullVesselJson);
                return;
            }

            String subTopic = parts[1].toLowerCase(Locale.ROOT);
            switch (subTopic) {
                case "position" -> {
                    JsonObject pos = new JsonObject();
                    pos.addProperty("instance_id", vessel.instanceId());
                    pos.add("position", fullVesselJson.get("position"));
                    pos.add("velocity", fullVesselJson.get("velocity"));
                    sendJsonResponse(exchange, 200, pos);
                }
                case "orbit" -> {
                    JsonObject orb = new JsonObject();
                    orb.addProperty("instance_id", vessel.instanceId());
                    orb.add("orbit", fullVesselJson.get("orbit"));
                    sendJsonResponse(exchange, 200, orb);
                }
                case "bounds" -> {
                    JsonObject b = new JsonObject();
                    b.addProperty("instance_id", vessel.instanceId());
                    b.add("bounds", fullVesselJson.get("bounds"));
                    sendJsonResponse(exchange, 200, b);
                }
                default -> {
                    JsonObject err = new JsonObject();
                    err.addProperty("error", "Unknown subtopic for vessel: " + subTopic);
                    err.addProperty("valid_subtopics", "position, orbit, bounds");
                    sendJsonResponse(exchange, 404, err);
                }
            }
        }
    }

    private static JsonObject notAvailable() {
        JsonObject err = new JsonObject();
        err.addProperty("error", "Telemetry snapshot not available yet");
        return err;
    }
}
