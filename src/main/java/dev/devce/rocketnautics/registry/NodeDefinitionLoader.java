package dev.devce.rocketnautics.registry;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads node definitions from .lua files.
 *
 * Nodes are defined with --@ header comments at the top of the file:
 * <pre>
 *   --@name     Altitude Sensor
 *   --@category Sensors
 *   --@output   m
 *
 *   local alt = getAltitude()
 *   output("m", alt)
 * </pre>
 *
 * Header tags:
 *   --@name <Name>         — display name (required)
 *   --@category <Category> — menu category (default: "Custom")
 *   --@input <pinName>     — input pin (repeatable)
 *   --@output <pinName>    — output pin (repeatable)
 *   --@id <ns:path>        — explicit ResourceLocation (default: derived from file path)
 *   --@color <AARRGGBB>    — hex header color (optional, for WNodeScreen category coloring)
 *
 * Built-in nodes are loaded from assets/rocketnautics/nodes/ inside the jar.
 * Custom nodes are loaded from <gamedir>/nodes/ on disk (created automatically).
 */
public class NodeDefinitionLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeDefinitionLoader.class);

    /** Subdirectory inside gamedir where players drop custom node .lua files. */
    public static final Path CUSTOM_NODES_DIR = FMLPaths.GAMEDIR.get().resolve("nodes");

    /** Resource path inside the jar for built-in node definitions. */
    private static final String BUILTIN_RESOURCE_PREFIX = "/assets/rocketnautics/nodes/";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Loads all built-in nodes from jar resources, then all custom nodes from
     * the gamedir/nodes/ folder. Call once during mod init after RocketNodes.register().
     */
    public static void load() {
        createCustomDir();
        int count = 0;
        count += loadBuiltinNodes();
        count += loadCustomNodes();
        LOGGER.info("[NodeDefinitionLoader] Registered {} data-driven nodes.", count);
    }

    /**
     * Re-scans custom nodes from disk without touching built-ins.
     * Safe to call at any time (e.g. from /nodes reload command).
     */
    public static void reload() {
        LOGGER.info("[NodeDefinitionLoader] Reloading custom nodes from disk...");
        int count = loadCustomNodes();
        LOGGER.info("[NodeDefinitionLoader] Reloaded {} custom nodes.", count);
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    private static void createCustomDir() {
        try {
            Files.createDirectories(CUSTOM_NODES_DIR);
            Path readme = CUSTOM_NODES_DIR.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme,
                    "Place .lua node definition files here to add them to the Sputnik node editor.\n" +
                    "Subdirectories are scanned recursively.\n\n" +
                    "File format (header comments at top of file):\n" +
                    "  --@name     My Custom Node\n" +
                    "  --@category Custom\n" +
                    "  --@input    A\n" +
                    "  --@input    B\n" +
                    "  --@output   Out\n\n" +
                    "  local a = input(\"A\")\n" +
                    "  local b = input(\"B\")\n" +
                    "  output(\"Out\", a + b)\n",
                    StandardCharsets.UTF_8
                );
            }
        } catch (IOException e) {
            LOGGER.error("[NodeDefinitionLoader] Could not create custom nodes directory: {}", e.getMessage());
        }
    }

    private static int loadBuiltinNodes() {
        int count = 0;
        // Walk the jar resource directory
        try {
            URI uri = NodeDefinitionLoader.class.getResource(BUILTIN_RESOURCE_PREFIX).toURI();
            Path resourcePath;
            if (uri.getScheme().equals("jar")) {
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                resourcePath = fs.getPath(BUILTIN_RESOURCE_PREFIX);
            } else {
                resourcePath = Paths.get(uri);
            }

            try (Stream<Path> stream = Files.walk(resourcePath)) {
                for (Path path : (Iterable<Path>) stream::iterator) {
                    if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".lua")) {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        // Derive default ID from path relative to prefix
                        String relative = resourcePath.relativize(path).toString()
                                .replace('\\', '/').replace(".lua", "");
                        if (registerFromContent(content, relative, RocketNautics.MODID)) count++;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[NodeDefinitionLoader] Could not scan built-in nodes: {}", e.getMessage());
            // Fallback: use classloader getResourceAsStream with known paths
            count += loadBuiltinNodesFallback();
        }
        return count;
    }

    /**
     * Fallback for environments where the JAR filesystem walk doesn't work —
     * loads each built-in node by explicit resource name.
     */
    private static int loadBuiltinNodesFallback() {
        // This list mirrors the files in assets/rocketnautics/nodes/
        String[] builtins = {
            "sensors/altitude",
            "sensors/velocity",
            "sensors/attitude",
            "sensors/angular_velocity",
            "sensors/position",
            "sensors/environment",
            "sensors/time",
            "deepspace/orbit_info",
            "deepspace/gravity_info",
            "deepspace/atmosphere_info",
            "deepspace/universe_time",
            "actuators/thruster_control",
            "actuators/vector_control",
            "actuators/rcs_control",
            "actuators/booster_control",
            "actuators/gyrodyne_control",
            "math/math_add",
            "math/math_subtract",
            "math/math_multiply",
            "math/math_divide",
            "math/math_abs",
            "math/math_clamp",
            "math/math_lerp",
            "math/math_mod",
            "math/math_pow",
            "logic/compare",
            "logic/logic_and",
            "logic/logic_or",
            "logic/logic_not",
            "logic/select",
            "logic/timer",
            "logic/memory",
            "display/display",
            "communication/radio",
            "communication/radio_send",
            "communication/radio_receive",
            "audio/synth_basic",
            "audio/synth_custom",
            "audio/melody_player",
        };
        int count = 0;
        for (String relPath : builtins) {
            String resourceName = BUILTIN_RESOURCE_PREFIX + relPath + ".lua";
            try (InputStream is = NodeDefinitionLoader.class.getResourceAsStream(resourceName)) {
                if (is == null) {
                    LOGGER.warn("[NodeDefinitionLoader] Built-in node not found: {}", resourceName);
                    continue;
                }
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (registerFromContent(content, relPath, RocketNautics.MODID)) count++;
            } catch (IOException e) {
                LOGGER.error("[NodeDefinitionLoader] Failed to load {}: {}", resourceName, e.getMessage());
            }
        }
        return count;
    }

    private static int loadCustomNodes() {
        if (!Files.exists(CUSTOM_NODES_DIR)) return 0;
        int count = 0;
        try (Stream<Path> stream = Files.walk(CUSTOM_NODES_DIR)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".lua")) {
                    try {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        // Derive relative path (used as default ID)
                        String relative = CUSTOM_NODES_DIR.relativize(path).toString()
                                .replace('\\', '/').replace(".lua", "");
                        if (registerFromContent(content, relative, "custom")) count++;
                    } catch (IOException e) {
                        LOGGER.error("[NodeDefinitionLoader] Failed to read {}: {}", path, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[NodeDefinitionLoader] Failed to walk custom nodes dir: {}", e.getMessage());
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Parsing & Registration
    // -------------------------------------------------------------------------

    private static boolean registerFromContent(String content, String defaultRelPath, String defaultNamespace) {
        NodeDef def = parse(content, defaultRelPath, defaultNamespace);
        if (def == null) return false;

        // Skip if already registered (built-ins should not be overridden by loader calling twice)
        final String finalCode = content;
        final NodeDef finalDef = def;

        NodeRegistry.register(def.id, def.category, (x, y) -> {
            WNode node = new WNode(finalDef.id, finalDef.name, x, y);
            node.getCustomData().putString("code", finalCode);
            for (String inp : finalDef.inputs)  node.addInput(inp, 0xFFFFFFFF);
            for (String out : finalDef.outputs) node.addOutput(out, 0xFF00FF88);
            if (finalDef.color != 0) node.getCustomData().putInt("categoryColor", finalDef.color);
            RocketNodes.setupLuaEvaluatorPublic(node);
            return node;
        });
        return true;
    }

    /** Parsed node definition from --@ headers. */
    private static class NodeDef {
        ResourceLocation id;
        String name = "";
        String category = "Custom";
        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        int color = 0;
    }

    private static NodeDef parse(String content, String defaultRelPath, String defaultNamespace) {
        NodeDef def = new NodeDef();

        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--@")) break; // Headers must be at the top

            String rest = trimmed.substring(3).trim();
            int space = rest.indexOf(' ');
            if (space < 0) continue;
            String tag = rest.substring(0, space).toLowerCase();
            String value = rest.substring(space + 1).trim();

            switch (tag) {
                case "name"     -> def.name = value;
                case "category" -> def.category = value;
                case "input"    -> def.inputs.add(value);
                case "output"   -> def.outputs.add(value);
                case "color"    -> { try { def.color = (int) Long.parseLong(value.replace("#",""), 16); } catch (NumberFormatException ignored) {} }
                case "id"       -> { try { def.id = ResourceLocation.parse(value); } catch (Exception ignored) {} }
            }
        }

        // Require at least a name
        if (def.name.isEmpty()) return null;

        // Derive ID from file path if not explicitly set
        if (def.id == null) {
            // e.g. "sensors/altitude" → "rocketnautics:altitude"
            String path = defaultRelPath.contains("/")
                    ? defaultRelPath.substring(defaultRelPath.lastIndexOf('/') + 1)
                    : defaultRelPath;
            // sanitize
            path = path.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            def.id = ResourceLocation.fromNamespaceAndPath(defaultNamespace, path);
        }

        return def;
    }
}
