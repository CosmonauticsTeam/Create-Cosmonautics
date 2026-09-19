package dev.devce.rocketnautics.client.ui.imgui;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

public final class SputnikCustomThemeEditor {

    private static boolean open = false;
    private static long lastSavedNotificationTime = 0;
    private static final ImBoolean pOpen = new ImBoolean(false);

    private SputnikCustomThemeEditor() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void setOpen(boolean state) {
        open = state;
        pOpen.set(state);
    }

    public static void toggle() {
        setOpen(!open);
    }

    public static void render() {
        if (!open) return;

        pOpen.set(open);
        ImGui.setNextWindowSize(500.0f, 540.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() - 520.0f, 40.0f, ImGuiCond.FirstUseEver);

        int flags = ImGuiWindowFlags.NoCollapse;
        if (!ImGui.begin("Custom Design Studio##SputnikThemeStudio", pOpen, flags)) {
            ImGui.end();
            open = pOpen.get();
            return;
        }
        open = pOpen.get();

        SputnikCustomTheme theme = SputnikCustomTheme.get();

        renderTopBar(theme);

        ImGui.separator();

        if (ImGui.beginTabBar("ThemeEditorTabs")) {

            if (ImGui.beginTabItem("Quick Setup")) {
                renderQuickSetupTab(theme);
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Animations & Chroma")) {
                renderAnimationsTab(theme);
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Roundings & Borders")) {
                renderGeometryTab(theme);
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Canvas & Nodes")) {
                renderCanvasAndNodesTab(theme);
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Wires & Widgets")) {
                renderWiresAndWidgetsTab(theme);
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.textDisabled("Config location: " + SputnikCustomTheme.getConfigFilePath());

        ImGui.end();
    }

    private static void renderTopBar(SputnikCustomTheme theme) {
        boolean isCustom = (SputnikNodeGraphPanel.themeMode == SputnikNodeGraphPanel.ThemeMode.CUSTOM);

        if (isCustom) {
            ImGui.textColored(0.35f, 1.0f, 0.45f, 1.0f, "[ACTIVE] CUSTOM THEME");
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.20f, 0.50f, 0.85f, 0.95f);
            if (ImGui.button("Activate Custom Theme")) {
                SputnikNodeGraphPanel.themeMode = SputnikNodeGraphPanel.ThemeMode.CUSTOM;
                theme.activeTheme = "CUSTOM";
                theme.markDirty();
            }
            ImGui.popStyleColor();
        }

        ImGui.sameLine();
        if (ImGui.button("Save Now")) {
            theme.saveToFile();
            lastSavedNotificationTime = System.currentTimeMillis();
        }

        ImGui.sameLine();
        if (ImGui.button("Reset Defaults")) {
            theme.resetToDefaults();
            lastSavedNotificationTime = System.currentTimeMillis();
        }

        ImGui.sameLine();
        ImBoolean autoSave = new ImBoolean(theme.autoSave);
        if (ImGui.checkbox("Auto-save", autoSave)) {
            theme.autoSave = autoSave.get();
            theme.markDirty();
        }

        if (System.currentTimeMillis() - lastSavedNotificationTime < 2500) {
            ImGui.sameLine();
            ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, "[SAVED!]");
        }
    }

    private static final float[] quickAccent = {0.961f, 0.549f, 0.118f};

    private static void renderQuickSetupTab(SputnikCustomTheme theme) {
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "One-Click Theme Presets");
        ImGui.textDisabled("Instantly apply clean color schemes & layouts:");
        ImGui.spacing();

        if (ImGui.button("Modern Blender (Default)")) {
            theme.applyPresetModern();
            lastSavedNotificationTime = System.currentTimeMillis();
        }
        ImGui.sameLine();
        if (ImGui.button("Cyberpunk Neon")) {
            theme.applyPresetCyberpunk();
            lastSavedNotificationTime = System.currentTimeMillis();
        }
        ImGui.sameLine();
        if (ImGui.button("Deep Space")) {
            theme.applyPresetDeepSpace();
            lastSavedNotificationTime = System.currentTimeMillis();
        }

        if (ImGui.button("Solar Flare")) {
            theme.applyPresetSolarFlare();
            lastSavedNotificationTime = System.currentTimeMillis();
        }
        ImGui.sameLine();
        if (ImGui.button("Blueprint")) {
            theme.applyPresetBlueprint();
            lastSavedNotificationTime = System.currentTimeMillis();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Global Accent Color");
        ImGui.textDisabled("One color to style selection glows, wires, and buttons:");

        quickAccent[0] = theme.nodeSelectedBorderColor[0];
        quickAccent[1] = theme.nodeSelectedBorderColor[1];
        quickAccent[2] = theme.nodeSelectedBorderColor[2];

        if (ImGui.colorEdit3("Accent Color##quickAcc", quickAccent)) {
            theme.applyAccentColor(quickAccent[0], quickAccent[1], quickAccent[2]);
        }

        ImGui.spacing();
        ImGui.text("Quick Accent Palettes:");
        if (ImGui.button("Cyan")) {
            theme.applyAccentColor(0.0f, 0.85f, 1.0f);
        }
        ImGui.sameLine();
        if (ImGui.button("Amber")) {
            theme.applyAccentColor(1.0f, 0.65f, 0.15f);
        }
        ImGui.sameLine();
        if (ImGui.button("Emerald")) {
            theme.applyAccentColor(0.20f, 0.90f, 0.45f);
        }
        ImGui.sameLine();
        if (ImGui.button("Magenta")) {
            theme.applyAccentColor(1.0f, 0.20f, 0.70f);
        }
        ImGui.sameLine();
        if (ImGui.button("Violet")) {
            theme.applyAccentColor(0.70f, 0.35f, 1.0f);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Corner Style");
        if (ImGui.button("Sharp (0px)")) {
            theme.nodeRounding = 0.0f;
            theme.windowRounding = 0.0f;
            theme.frameRounding = 0.0f;
            theme.markDirty();
        }
        ImGui.sameLine();
        if (ImGui.button("Subtle (4px)")) {
            theme.nodeRounding = 4.0f;
            theme.windowRounding = 4.0f;
            theme.frameRounding = 3.0f;
            theme.markDirty();
        }
        ImGui.sameLine();
        if (ImGui.button("Rounded (8px)")) {
            theme.nodeRounding = 8.0f;
            theme.windowRounding = 8.0f;
            theme.frameRounding = 4.0f;
            theme.markDirty();
        }
        ImGui.sameLine();
        if (ImGui.button("Soft (14px)")) {
            theme.nodeRounding = 14.0f;
            theme.windowRounding = 14.0f;
            theme.frameRounding = 6.0f;
            theme.markDirty();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Quick Effects");
        ImBoolean rainbow = new ImBoolean(theme.rainbowEnabled);
        if (ImGui.checkbox("RGB Rainbow Chroma Mode##quickRainbow", rainbow)) {
            theme.rainbowEnabled = rainbow.get();
            theme.markDirty();
        }
        if (theme.rainbowEnabled) {
            float[] speed = {theme.rainbowSpeed};
            if (ImGui.sliderFloat("Chroma Speed##quickSpeed", speed, 0.1f, 4.0f, "%.2fx")) {
                theme.rainbowSpeed = speed[0];
                theme.markDirty();
            }
        }

        ImBoolean pulse = new ImBoolean(theme.wirePulseParticles);
        if (ImGui.checkbox("Wire Flow Pulse##quickPulse", pulse)) {
            theme.wirePulseParticles = pulse.get();
            theme.markDirty();
        }
    }

    private static void renderAnimationsTab(SputnikCustomTheme theme) {
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Dynamic RGB Chroma / Flowing Colors");
        ImGui.spacing();

        ImBoolean rainbow = new ImBoolean(theme.rainbowEnabled);
        if (ImGui.checkbox("Enable Dynamic Rainbow / Chroma Mode", rainbow)) {
            theme.rainbowEnabled = rainbow.get();
            theme.markDirty();
        }

        if (theme.rainbowEnabled) {
            ImGui.indent();

            float[] speed = {theme.rainbowSpeed};
            if (ImGui.sliderFloat("Speed##rbSpeed", speed, 0.1f, 5.0f, "%.2fx")) {
                theme.rainbowSpeed = speed[0];
                theme.markDirty();
            }

            float[] sat = {theme.rainbowSaturation};
            if (ImGui.sliderFloat("Saturation##rbSat", sat, 0.0f, 1.0f, "%.2f")) {
                theme.rainbowSaturation = sat[0];
                theme.markDirty();
            }

            float[] bright = {theme.rainbowBrightness};
            if (ImGui.sliderFloat("Brightness##rbBri", bright, 0.1f, 1.0f, "%.2f")) {
                theme.rainbowBrightness = bright[0];
                theme.markDirty();
            }

            ImGui.spacing();
            ImGui.text("Elements to animate:");

            ImBoolean rHeaders = new ImBoolean(theme.rainbowHeaders);
            if (ImGui.checkbox("Animate Node Headers (Dynamic Flow)", rHeaders)) {
                theme.rainbowHeaders = rHeaders.get();
                theme.markDirty();
            }

            ImBoolean rSelect = new ImBoolean(theme.rainbowSelection);
            if (ImGui.checkbox("Animate Selection Neon Glow", rSelect)) {
                theme.rainbowSelection = rSelect.get();
                theme.markDirty();
            }

            ImBoolean rWires = new ImBoolean(theme.rainbowWires);
            if (ImGui.checkbox("Animate Wires / Cables Flow", rWires)) {
                theme.rainbowWires = rWires.get();
                theme.markDirty();
            }

            ImBoolean rGrid = new ImBoolean(theme.rainbowGrid);
            if (ImGui.checkbox("Animate Grid Subtle Shift", rGrid)) {
                theme.rainbowGrid = rGrid.get();
                theme.markDirty();
            }

            ImGui.spacing();
            ImGui.text("Live Chroma Swatch:");
            ImDrawList drawList = ImGui.getWindowDrawList();
            float barX = ImGui.getCursorScreenPos().x;
            float barY = ImGui.getCursorScreenPos().y;
            float barW = ImGui.getContentRegionAvail().x;
            float barH = 14.0f;

            int steps = 24;
            float stepW = barW / steps;
            for (int i = 0; i < steps; i++) {
                float phase = (float) i / steps;
                int c = theme.getRainbowColor(phase, 1.0f);
                drawList.addRectFilled(barX + i * stepW, barY, barX + (i + 1) * stepW, barY + barH, c);
            }
            drawList.addRect(barX, barY, barX + barW, barY + barH, ImColor.rgba(255, 255, 255, 120), 2.0f);
            ImGui.dummy(barW, barH + 4.0f);

            ImGui.unindent();
        }

        ImGui.separator();
        ImGui.spacing();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Pulse & Breathing Effects");

        ImBoolean wirePulse = new ImBoolean(theme.wirePulseParticles);
        if (ImGui.checkbox("Traveling Glowing Pulse Along Wires", wirePulse)) {
            theme.wirePulseParticles = wirePulse.get();
            theme.markDirty();
        }

        if (theme.wirePulseParticles) {
            float[] pSpeed = {theme.wirePulseSpeed};
            if (ImGui.sliderFloat("Pulse Travel Speed", pSpeed, 0.2f, 4.0f, "%.2fx")) {
                theme.wirePulseSpeed = pSpeed[0];
                theme.markDirty();
            }
        }

        ImBoolean breathing = new ImBoolean(theme.breathingGlow);
        if (ImGui.checkbox("Breathing Selection Glow (Smooth Sine Wave)", breathing)) {
            theme.breathingGlow = breathing.get();
            theme.markDirty();
        }
    }

    private static void renderGeometryTab(SputnikCustomTheme theme) {
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Node Dimensions & Rounding");

        float[] nodeRound = {theme.nodeRounding};
        if (ImGui.sliderFloat("Node Corner Rounding", nodeRound, 0.0f, 20.0f, "%.1f px")) {
            theme.nodeRounding = nodeRound[0];
            theme.markDirty();
        }

        float[] headerH = {theme.nodeHeaderHeight};
        if (ImGui.sliderFloat("Node Header Height", headerH, 16.0f, 36.0f, "%.1f px")) {
            theme.nodeHeaderHeight = headerH[0];
            theme.markDirty();
        }

        float[] nodeBorder = {theme.nodeBorderThickness};
        if (ImGui.sliderFloat("Node Border Width", nodeBorder, 0.5f, 4.0f, "%.1f px")) {
            theme.nodeBorderThickness = nodeBorder[0];
            theme.markDirty();
        }

        float[] selThick = {theme.selectionOutlineThickness};
        if (ImGui.sliderFloat("Selection Glow Width", selThick, 1.0f, 6.0f, "%.1f px")) {
            theme.selectionOutlineThickness = selThick[0];
            theme.markDirty();
        }

        ImGui.separator();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Wires & Canvas Grid");

        float[] wireThick = {theme.wireThickness};
        if (ImGui.sliderFloat("Wire Thickness", wireThick, 1.0f, 7.0f, "%.1f px")) {
            theme.wireThickness = wireThick[0];
            theme.markDirty();
        }

        ImBoolean shadow = new ImBoolean(theme.wireShadow);
        if (ImGui.checkbox("Wire Drop Shadow", shadow)) {
            theme.wireShadow = shadow.get();
            theme.markDirty();
        }

        float[] gSize = {theme.gridSize};
        if (ImGui.sliderFloat("Grid Cell Size", gSize, 12.0f, 60.0f, "%.0f px")) {
            theme.gridSize = gSize[0];
            theme.markDirty();
        }

        int[] gMajor = {theme.gridMajorStep};
        if (ImGui.sliderInt("Major Grid Interval", gMajor, 2, 10)) {
            theme.gridMajorStep = gMajor[0];
            theme.markDirty();
        }

        ImGui.separator();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "ImGui Window & Widget Roundings");

        float[] winRound = {theme.windowRounding};
        if (ImGui.sliderFloat("Window Rounding", winRound, 0.0f, 20.0f, "%.1f px")) {
            theme.windowRounding = winRound[0];
            theme.markDirty();
        }

        float[] frameRound = {theme.frameRounding};
        if (ImGui.sliderFloat("Frame / Input Rounding", frameRound, 0.0f, 16.0f, "%.1f px")) {
            theme.frameRounding = frameRound[0];
            theme.markDirty();
        }

        float[] grabRound = {theme.grabRounding};
        if (ImGui.sliderFloat("Slider Grab Rounding", grabRound, 0.0f, 16.0f, "%.1f px")) {
            theme.grabRounding = grabRound[0];
            theme.markDirty();
        }

        float[] tabRound = {theme.tabRounding};
        if (ImGui.sliderFloat("Tab Rounding", tabRound, 0.0f, 16.0f, "%.1f px")) {
            theme.tabRounding = tabRound[0];
            theme.markDirty();
        }

        float[] popupRound = {theme.popupRounding};
        if (ImGui.sliderFloat("Popup Rounding", popupRound, 0.0f, 16.0f, "%.1f px")) {
            theme.popupRounding = popupRound[0];
            theme.markDirty();
        }

        float[] scrollRound = {theme.scrollbarRounding};
        if (ImGui.sliderFloat("Scrollbar Rounding", scrollRound, 0.0f, 16.0f, "%.1f px")) {
            theme.scrollbarRounding = scrollRound[0];
            theme.markDirty();
        }

        float[] winBorder = {theme.windowBorderSize};
        if (ImGui.sliderFloat("Window Border Width", winBorder, 0.0f, 3.0f, "%.1f px")) {
            theme.windowBorderSize = winBorder[0];
            theme.markDirty();
        }

        float[] frameBorder = {theme.frameBorderSize};
        if (ImGui.sliderFloat("Frame Border Width", frameBorder, 0.0f, 3.0f, "%.1f px")) {
            theme.frameBorderSize = frameBorder[0];
            theme.markDirty();
        }
    }

    private static void renderCanvasAndNodesTab(SputnikCustomTheme theme) {
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Canvas & Menu Colors");
        if (ImGui.colorEdit4("Canvas Background", theme.canvasBg)) theme.markDirty();
        if (ImGui.colorEdit4("Menu Bar Background", theme.menuBarBg)) theme.markDirty();

        ImGui.separator();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Node Body & Header Colors");

        if (ImGui.colorEdit4("Node Body Normal", theme.nodeBodyBg)) theme.markDirty();
        if (ImGui.colorEdit4("Node Body Hovered", theme.nodeBodyBgHovered)) theme.markDirty();

        ImBoolean useCat = new ImBoolean(theme.useCategoryColors);
        if (ImGui.checkbox("Headers Inherit Category Colors", useCat)) {
            theme.useCategoryColors = useCat.get();
            theme.markDirty();
        }

        if (!theme.useCategoryColors) {
            if (ImGui.colorEdit4("Header Color (Uniform)", theme.nodeHeaderDefault)) theme.markDirty();
        }

        if (ImGui.colorEdit4("Node Border Normal", theme.nodeBorderColor)) theme.markDirty();
        if (ImGui.colorEdit4("Node Border Hovered", theme.nodeHoveredBorderColor)) theme.markDirty();
        if (ImGui.colorEdit4("Selected Outline Border", theme.nodeSelectedBorderColor)) theme.markDirty();
        if (ImGui.colorEdit4("Selected Neon Glow", theme.nodeSelectedGlowColor)) theme.markDirty();
        if (ImGui.colorEdit4("Node Title Text Color", theme.nodeTitleColor)) theme.markDirty();
    }

    private static void renderWiresAndWidgetsTab(SputnikCustomTheme theme) {
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Wires & Selection Box Colors");

        ImBoolean wirePin = new ImBoolean(theme.wireUsePinColor);
        if (ImGui.checkbox("Wires Inherit Pin Data Type Color", wirePin)) {
            theme.wireUsePinColor = wirePin.get();
            theme.markDirty();
        }

        if (!theme.wireUsePinColor) {
            if (ImGui.colorEdit4("Wire Color (Uniform)", theme.wireColor)) theme.markDirty();
        }

        if (ImGui.colorEdit4("Wire Hovered Color", theme.wireHoveredColor)) theme.markDirty();
        if (ImGui.colorEdit4("Box Select Fill", theme.boxSelectBg)) theme.markDirty();
        if (ImGui.colorEdit4("Box Select Border", theme.boxSelectBorder)) theme.markDirty();

        ImGui.separator();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Grid Colors");

        if (ImGui.colorEdit4("Grid Lines", theme.gridLineColor)) theme.markDirty();
        if (ImGui.colorEdit4("Major Grid Lines", theme.gridMajorLineColor)) theme.markDirty();

        ImGui.separator();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Buttons & Frame Widgets");

        if (ImGui.colorEdit4("Button Normal", theme.buttonColor)) theme.markDirty();
        if (ImGui.colorEdit4("Button Hovered", theme.buttonHoveredColor)) theme.markDirty();
        if (ImGui.colorEdit4("Button Active", theme.buttonActiveColor)) theme.markDirty();

        if (ImGui.colorEdit4("Input Frame Normal", theme.frameBgColor)) theme.markDirty();
        if (ImGui.colorEdit4("Input Frame Hovered", theme.frameBgHoveredColor)) theme.markDirty();
        if (ImGui.colorEdit4("Input Frame Active", theme.frameBgActiveColor)) theme.markDirty();

        if (ImGui.colorEdit4("Text Color", theme.textColor)) theme.markDirty();
    }
}
