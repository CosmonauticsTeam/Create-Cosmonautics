package dev.devce.rocketnautics.client.ui.imgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SputnikCustomTheme {

    private static final Logger LOGGER = LoggerFactory.getLogger(SputnikCustomTheme.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SputnikCustomTheme INSTANCE;

    public String activeTheme = "NEW";
    public boolean autoSave = true;

    public float nodeRounding = 6.0f;
    public float nodeHeaderHeight = 24.0f;
    public float windowRounding = 6.0f;
    public float frameRounding = 3.0f;
    public float grabRounding = 3.0f;
    public float popupRounding = 4.0f;
    public float tabRounding = 4.0f;
    public float scrollbarRounding = 4.0f;

    public float windowBorderSize = 1.0f;
    public float frameBorderSize = 1.0f;
    public float nodeBorderThickness = 1.0f;
    public float selectionOutlineThickness = 1.5f;

    public float wireThickness = 2.2f;
    public boolean wireShadow = true;
    public float wireShadowThickness = 3.8f;

    public float gridSize = 28.0f;
    public int gridMajorStep = 4;

    public float[] canvasBg = {0.115f, 0.115f, 0.120f, 1.0f};
    public float[] menuBarBg = {0.140f, 0.140f, 0.145f, 1.0f};

    public float[] gridLineColor = {1.0f, 1.0f, 1.0f, 0.09f};
    public float[] gridMajorLineColor = {1.0f, 1.0f, 1.0f, 0.22f};

    public float[] nodeBodyBg = {0.155f, 0.155f, 0.160f, 0.98f};
    public float[] nodeBodyBgHovered = {0.180f, 0.180f, 0.185f, 0.98f};
    public float[] nodeHeaderDefault = {0.145f, 0.375f, 0.520f, 1.0f};
    public boolean useCategoryColors = true;

    public float[] nodeBorderColor = {0.08f, 0.08f, 0.08f, 1.0f};
    public float[] nodeHoveredBorderColor = {0.55f, 0.55f, 0.58f, 0.85f};
    public float[] nodeSelectedBorderColor = {1.0f, 1.0f, 1.0f, 1.0f};
    public float[] nodeSelectedGlowColor = {0.92f, 0.50f, 0.15f, 0.65f};
    public float[] nodeTitleColor = {1.0f, 1.0f, 1.0f, 1.0f};

    public float[] wireColor = {0.70f, 0.72f, 0.75f, 1.0f};
    public boolean wireUsePinColor = true;
    public float[] wireHoveredColor = {1.0f, 1.0f, 1.0f, 1.0f};

    public float[] boxSelectBg = {0.20f, 0.40f, 0.70f, 0.18f};
    public float[] boxSelectBorder = {0.35f, 0.65f, 1.0f, 0.85f};

    public float[] buttonColor = {0.18f, 0.18f, 0.20f, 0.90f};
    public float[] buttonHoveredColor = {0.24f, 0.24f, 0.26f, 1.00f};
    public float[] buttonActiveColor = {0.30f, 0.30f, 0.34f, 1.00f};

    public float[] frameBgColor = {0.10f, 0.10f, 0.11f, 1.00f};
    public float[] frameBgHoveredColor = {0.14f, 0.14f, 0.15f, 1.00f};
    public float[] frameBgActiveColor = {0.20f, 0.35f, 0.55f, 1.00f};

    public float[] textColor = {0.90f, 0.90f, 0.90f, 1.0f};

    public boolean rainbowEnabled = false;
    public float rainbowSpeed = 1.0f;
    public float rainbowSaturation = 0.85f;
    public float rainbowBrightness = 0.95f;

    public boolean rainbowHeaders = true;
    public boolean rainbowSelection = true;
    public boolean rainbowWires = false;
    public boolean rainbowGrid = false;

    public boolean wirePulseParticles = true;
    public float wirePulseSpeed = 1.0f;
    public boolean breathingGlow = true;

    private transient boolean isDirty = false;
    private transient long lastDirtyTime = 0;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (INSTANCE != null && INSTANCE.isDirty) {
                INSTANCE.saveToFile();
            }
        }, "Cosmonautics-SputnikTheme-Saver"));
    }

    public static synchronized SputnikCustomTheme get() {
        if (INSTANCE == null) {
            INSTANCE = loadFromFile();
        }
        return INSTANCE;
    }

    public void markDirty() {
        this.isDirty = true;
        this.lastDirtyTime = System.currentTimeMillis();
        if (autoSave) {
            saveToFile();
        }
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void resetToDefaults() {
        nodeRounding = 6.0f;
        nodeHeaderHeight = 24.0f;
        windowRounding = 6.0f;
        frameRounding = 3.0f;
        grabRounding = 3.0f;
        popupRounding = 4.0f;
        tabRounding = 4.0f;
        scrollbarRounding = 4.0f;

        windowBorderSize = 1.0f;
        frameBorderSize = 1.0f;
        nodeBorderThickness = 1.0f;
        selectionOutlineThickness = 1.5f;

        wireThickness = 2.2f;
        wireShadow = true;
        wireShadowThickness = 3.8f;

        gridSize = 28.0f;
        gridMajorStep = 4;

        setCol(canvasBg, 0.115f, 0.115f, 0.120f, 1.0f);
        setCol(menuBarBg, 0.140f, 0.140f, 0.145f, 1.0f);

        setCol(gridLineColor, 1.0f, 1.0f, 1.0f, 0.09f);
        setCol(gridMajorLineColor, 1.0f, 1.0f, 1.0f, 0.22f);

        setCol(nodeBodyBg, 0.155f, 0.155f, 0.160f, 0.98f);
        setCol(nodeBodyBgHovered, 0.180f, 0.180f, 0.185f, 0.98f);
        setCol(nodeHeaderDefault, 0.145f, 0.375f, 0.520f, 1.0f);
        useCategoryColors = true;

        setCol(nodeBorderColor, 0.08f, 0.08f, 0.08f, 1.0f);
        setCol(nodeHoveredBorderColor, 0.55f, 0.55f, 0.58f, 0.85f);
        setCol(nodeSelectedBorderColor, 1.0f, 1.0f, 1.0f, 1.0f);
        setCol(nodeSelectedGlowColor, 0.92f, 0.50f, 0.15f, 0.65f);
        setCol(nodeTitleColor, 1.0f, 1.0f, 1.0f, 1.0f);

        setCol(wireColor, 0.70f, 0.72f, 0.75f, 1.0f);
        wireUsePinColor = true;
        setCol(wireHoveredColor, 1.0f, 1.0f, 1.0f, 1.0f);

        setCol(boxSelectBg, 0.20f, 0.40f, 0.70f, 0.18f);
        setCol(boxSelectBorder, 0.35f, 0.65f, 1.0f, 0.85f);

        setCol(buttonColor, 0.18f, 0.18f, 0.20f, 0.90f);
        setCol(buttonHoveredColor, 0.24f, 0.24f, 0.26f, 1.00f);
        setCol(buttonActiveColor, 0.30f, 0.30f, 0.34f, 1.00f);

        setCol(frameBgColor, 0.10f, 0.10f, 0.11f, 1.00f);
        setCol(frameBgHoveredColor, 0.14f, 0.14f, 0.15f, 1.00f);
        setCol(frameBgActiveColor, 0.20f, 0.35f, 0.55f, 1.00f);

        setCol(textColor, 0.90f, 0.90f, 0.90f, 1.0f);

        rainbowEnabled = false;
        rainbowSpeed = 1.0f;
        rainbowSaturation = 0.85f;
        rainbowBrightness = 0.95f;
        rainbowHeaders = true;
        rainbowSelection = true;
        rainbowWires = false;
        rainbowGrid = false;
        wirePulseParticles = true;
        wirePulseSpeed = 1.0f;
        breathingGlow = true;

        markDirty();
    }

    public void applyPresetModern() {
        resetToDefaults();
    }

    public void applyPresetCyberpunk() {
        nodeRounding = 4.0f;
        nodeHeaderHeight = 28.0f;
        setCol(canvasBg, 0.04f, 0.04f, 0.06f, 1.0f);
        setCol(menuBarBg, 0.07f, 0.06f, 0.10f, 1.0f);
        setCol(gridLineColor, 0.12f, 0.08f, 0.16f, 0.70f);
        setCol(gridMajorLineColor, 0.22f, 0.10f, 0.28f, 0.85f);
        setCol(nodeBodyBg, 0.08f, 0.07f, 0.12f, 0.99f);
        setCol(nodeBodyBgHovered, 0.12f, 0.10f, 0.18f, 0.99f);
        setCol(nodeHeaderDefault, 0.20f, 0.08f, 0.28f, 1.0f);
        setCol(nodeBorderColor, 0.18f, 0.08f, 0.24f, 1.0f);
        setCol(nodeHoveredBorderColor, 0.0f, 0.90f, 1.0f, 0.90f);
        setCol(nodeSelectedBorderColor, 1.0f, 0.10f, 0.60f, 1.0f);
        setCol(nodeSelectedGlowColor, 1.0f, 0.20f, 0.70f, 0.85f);
        setCol(wireColor, 0.0f, 0.85f, 0.95f, 1.0f);
        setCol(wireHoveredColor, 1.0f, 0.20f, 0.70f, 1.0f);
        setCol(boxSelectBg, 1.0f, 0.10f, 0.60f, 0.20f);
        setCol(boxSelectBorder, 0.0f, 0.90f, 1.0f, 0.90f);
        setCol(buttonColor, 0.35f, 0.08f, 0.45f, 0.90f);
        setCol(buttonHoveredColor, 0.55f, 0.12f, 0.70f, 1.0f);
        setCol(buttonActiveColor, 0.80f, 0.15f, 0.90f, 1.0f);
        wirePulseParticles = true;
        wirePulseSpeed = 1.5f;
        breathingGlow = true;
        markDirty();
    }

    public void applyPresetDeepSpace() {
        nodeRounding = 8.0f;
        nodeHeaderHeight = 28.0f;
        setCol(canvasBg, 0.02f, 0.02f, 0.03f, 1.0f);
        setCol(menuBarBg, 0.05f, 0.05f, 0.07f, 1.0f);
        setCol(gridLineColor, 0.06f, 0.07f, 0.09f, 0.60f);
        setCol(gridMajorLineColor, 0.10f, 0.12f, 0.16f, 0.75f);
        setCol(nodeBodyBg, 0.07f, 0.08f, 0.10f, 0.99f);
        setCol(nodeBodyBgHovered, 0.11f, 0.12f, 0.15f, 0.99f);
        setCol(nodeHeaderDefault, 0.12f, 0.15f, 0.20f, 1.0f);
        setCol(nodeBorderColor, 0.06f, 0.07f, 0.09f, 1.0f);
        setCol(nodeHoveredBorderColor, 0.30f, 0.50f, 0.80f, 0.85f);
        setCol(nodeSelectedBorderColor, 0.25f, 0.60f, 1.0f, 1.0f);
        setCol(nodeSelectedGlowColor, 0.35f, 0.75f, 1.0f, 0.80f);
        setCol(wireColor, 0.55f, 0.65f, 0.78f, 1.0f);
        setCol(wireHoveredColor, 0.40f, 0.80f, 1.0f, 1.0f);
        setCol(buttonColor, 0.10f, 0.18f, 0.30f, 0.90f);
        setCol(buttonHoveredColor, 0.16f, 0.30f, 0.50f, 1.0f);
        setCol(buttonActiveColor, 0.22f, 0.45f, 0.75f, 1.0f);
        rainbowEnabled = false;
        breathingGlow = true;
        markDirty();
    }

    public void applyPresetSolarFlare() {
        nodeRounding = 6.0f;
        nodeHeaderHeight = 28.0f;
        setCol(canvasBg, 0.08f, 0.07f, 0.06f, 1.0f);
        setCol(menuBarBg, 0.12f, 0.10f, 0.08f, 1.0f);
        setCol(gridLineColor, 0.14f, 0.11f, 0.08f, 0.60f);
        setCol(gridMajorLineColor, 0.22f, 0.16f, 0.10f, 0.75f);
        setCol(nodeBodyBg, 0.14f, 0.12f, 0.10f, 0.99f);
        setCol(nodeBodyBgHovered, 0.18f, 0.15f, 0.12f, 0.99f);
        setCol(nodeHeaderDefault, 0.25f, 0.18f, 0.10f, 1.0f);
        setCol(nodeBorderColor, 0.12f, 0.09f, 0.06f, 1.0f);
        setCol(nodeHoveredBorderColor, 0.85f, 0.60f, 0.20f, 0.85f);
        setCol(nodeSelectedBorderColor, 1.0f, 0.55f, 0.10f, 1.0f);
        setCol(nodeSelectedGlowColor, 1.0f, 0.75f, 0.20f, 0.80f);
        setCol(wireColor, 0.85f, 0.70f, 0.45f, 1.0f);
        setCol(wireHoveredColor, 1.0f, 0.85f, 0.30f, 1.0f);
        setCol(boxSelectBg, 1.0f, 0.55f, 0.10f, 0.18f);
        setCol(boxSelectBorder, 1.0f, 0.70f, 0.20f, 0.85f);
        setCol(buttonColor, 0.32f, 0.20f, 0.08f, 0.90f);
        setCol(buttonHoveredColor, 0.48f, 0.28f, 0.10f, 1.0f);
        setCol(buttonActiveColor, 0.70f, 0.40f, 0.12f, 1.0f);
        markDirty();
    }

    public void applyPresetBlueprint() {
        nodeRounding = 2.0f;
        nodeHeaderHeight = 26.0f;
        setCol(canvasBg, 0.05f, 0.11f, 0.19f, 1.0f);
        setCol(menuBarBg, 0.07f, 0.15f, 0.25f, 1.0f);
        setCol(gridLineColor, 0.12f, 0.22f, 0.36f, 0.65f);
        setCol(gridMajorLineColor, 0.20f, 0.36f, 0.58f, 0.85f);
        setCol(nodeBodyBg, 0.08f, 0.16f, 0.26f, 0.99f);
        setCol(nodeBodyBgHovered, 0.12f, 0.22f, 0.35f, 0.99f);
        setCol(nodeHeaderDefault, 0.14f, 0.28f, 0.45f, 1.0f);
        setCol(nodeBorderColor, 0.18f, 0.32f, 0.50f, 1.0f);
        setCol(nodeHoveredBorderColor, 0.50f, 0.80f, 1.0f, 0.90f);
        setCol(nodeSelectedBorderColor, 0.40f, 0.85f, 1.0f, 1.0f);
        setCol(nodeSelectedGlowColor, 0.60f, 0.90f, 1.0f, 0.80f);
        setCol(wireColor, 0.65f, 0.85f, 1.0f, 1.0f);
        setCol(wireHoveredColor, 1.0f, 1.0f, 1.0f, 1.0f);
        setCol(boxSelectBg, 0.20f, 0.50f, 0.85f, 0.25f);
        setCol(boxSelectBorder, 0.50f, 0.85f, 1.0f, 0.90f);
        setCol(buttonColor, 0.12f, 0.26f, 0.44f, 0.90f);
        setCol(buttonHoveredColor, 0.18f, 0.38f, 0.62f, 1.0f);
        setCol(buttonActiveColor, 0.25f, 0.50f, 0.80f, 1.0f);
        markDirty();
    }

    public void applyAccentColor(float r, float g, float b) {
        setCol(nodeSelectedBorderColor, r, g, b, 1.0f);
        setCol(nodeSelectedGlowColor, r, g, b, 0.80f);
        setCol(wireHoveredColor, r, g, b, 1.0f);
        setCol(boxSelectBorder, r, g, b, 0.88f);
        setCol(boxSelectBg, r, g, b, 0.20f);
        setCol(buttonHoveredColor, Math.min(1.0f, r * 0.7f), Math.min(1.0f, g * 0.7f), Math.min(1.0f, b * 0.7f), 1.0f);
        setCol(buttonActiveColor, r, g, b, 1.0f);
        markDirty();
    }

    private static void setCol(float[] arr, float r, float g, float b, float a) {
        if (arr.length >= 4) {
            arr[0] = r;
            arr[1] = g;
            arr[2] = b;
            arr[3] = a;
        }
    }

    public void applyToImGuiStyle() {
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(windowRounding);
        style.setFrameRounding(frameRounding);
        style.setGrabRounding(grabRounding);
        style.setPopupRounding(popupRounding);
        style.setTabRounding(tabRounding);
        style.setScrollbarRounding(scrollbarRounding);

        style.setWindowBorderSize(windowBorderSize);
        style.setFrameBorderSize(frameBorderSize);

        style.setColor(ImGuiCol.Button, buttonColor[0], buttonColor[1], buttonColor[2], buttonColor[3]);
        style.setColor(ImGuiCol.ButtonHovered, buttonHoveredColor[0], buttonHoveredColor[1], buttonHoveredColor[2], buttonHoveredColor[3]);
        style.setColor(ImGuiCol.ButtonActive, buttonActiveColor[0], buttonActiveColor[1], buttonActiveColor[2], buttonActiveColor[3]);

        style.setColor(ImGuiCol.FrameBg, frameBgColor[0], frameBgColor[1], frameBgColor[2], frameBgColor[3]);
        style.setColor(ImGuiCol.FrameBgHovered, frameBgHoveredColor[0], frameBgHoveredColor[1], frameBgHoveredColor[2], frameBgHoveredColor[3]);
        style.setColor(ImGuiCol.FrameBgActive, frameBgActiveColor[0], frameBgActiveColor[1], frameBgActiveColor[2], frameBgActiveColor[3]);

        style.setColor(ImGuiCol.Text, textColor[0], textColor[1], textColor[2], textColor[3]);
    }

    public static int toImColor(float[] col) {
        if (col == null || col.length < 4) return 0xFFFFFFFF;
        return ImColor.rgba(
                Math.max(0, Math.min(255, (int) (col[0] * 255.0f))),
                Math.max(0, Math.min(255, (int) (col[1] * 255.0f))),
                Math.max(0, Math.min(255, (int) (col[2] * 255.0f))),
                Math.max(0, Math.min(255, (int) (col[3] * 255.0f)))
        );
    }

    public static int toImColorWithAlpha(float[] col, float alphaMultiplier) {
        if (col == null || col.length < 4) return 0xFFFFFFFF;
        int a = Math.max(0, Math.min(255, (int) (col[3] * alphaMultiplier * 255.0f)));
        return ImColor.rgba(
                Math.max(0, Math.min(255, (int) (col[0] * 255.0f))),
                Math.max(0, Math.min(255, (int) (col[1] * 255.0f))),
                Math.max(0, Math.min(255, (int) (col[2] * 255.0f))),
                a
        );
    }

    public int getRainbowColor(float phaseOffset, float alpha) {
        float timeSec = (System.currentTimeMillis() % 10000000L) / 1000.0f;
        float hue = ((timeSec * 0.25f * rainbowSpeed) + phaseOffset) % 1.0f;
        if (hue < 0.0f) hue += 1.0f;

        float[] rgb = hsvToRgb(hue, rainbowSaturation, rainbowBrightness);
        return ImColor.rgba(
                (int) (rgb[0] * 255.0f),
                (int) (rgb[1] * 255.0f),
                (int) (rgb[2] * 255.0f),
                Math.max(0, Math.min(255, (int) (alpha * 255.0f)))
        );
    }

    public float getBreathingFactor() {
        if (!breathingGlow) return 1.0f;
        float timeSec = (System.currentTimeMillis() % 10000000L) / 1000.0f;
        return 0.70f + 0.30f * (float) Math.sin(timeSec * 3.5f * rainbowSpeed);
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        int i = (int) Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);

        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            case 5 -> { r = v; g = p; b = q; }
        }
        return new float[]{r, g, b};
    }

    public static Path getConfigFilePath() {
        Path baseDir;
        try {
            baseDir = FMLPaths.CONFIGDIR.get();
        } catch (Throwable t) {
            baseDir = Path.of("config");
        }
        return baseDir.resolve("rocketnautics").resolve("sputnik_custom_theme.json");
    }

    public synchronized void saveToFile() {
        try {
            Path file = getConfigFilePath();
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Path tempFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Path backupFile = file.resolveSibling(file.getFileName().toString() + ".bak");

            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }

            if (Files.exists(file)) {
                try {
                    Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }
            }

            try {
                Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception moveEx) {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }

            this.isDirty = false;
        } catch (Throwable t) {
            LOGGER.error("[Cosmonautics] Failed to persist sputnik custom theme", t);
        }
    }

    private static SputnikCustomTheme loadFromFile() {
        Path file = getConfigFilePath();
        Path backupFile = file.resolveSibling(file.getFileName().toString() + ".bak");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                SputnikCustomTheme theme = GSON.fromJson(reader, SputnikCustomTheme.class);
                if (theme != null) {
                    theme.isDirty = false;
                    return theme;
                }
            } catch (Throwable t) {
                LOGGER.warn("[Cosmonautics] Primary sputnik theme config corrupted, trying backup: {}", t.getMessage());
            }
        }

        if (Files.exists(backupFile)) {
            try (Reader reader = Files.newBufferedReader(backupFile, StandardCharsets.UTF_8)) {
                SputnikCustomTheme theme = GSON.fromJson(reader, SputnikCustomTheme.class);
                if (theme != null) {
                    LOGGER.info("[Cosmonautics] Successfully restored sputnik custom theme from backup!");
                    theme.isDirty = false;
                    theme.saveToFile();
                    return theme;
                }
            } catch (Throwable t) {
                LOGGER.error("[Cosmonautics] Backup sputnik theme config also failed", t);
            }
        }

        SputnikCustomTheme defaultTheme = new SputnikCustomTheme();
        defaultTheme.saveToFile();
        return defaultTheme;
    }
}
