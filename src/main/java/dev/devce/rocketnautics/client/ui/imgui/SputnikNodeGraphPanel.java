package dev.devce.rocketnautics.client.ui.imgui;

import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.content.sputnik.model.SputnikGraph;
import dev.devce.rocketnautics.content.sputnik.model.SputnikLink;
import dev.devce.rocketnautics.content.sputnik.model.SputnikNode;
import dev.devce.rocketnautics.content.sputnik.model.SputnikPin;
import dev.devce.rocketnautics.content.sputnik.network.SputnikSaveGraphPayload;
import dev.devce.rocketnautics.content.sputnik.node.INodeHandler;
import dev.devce.rocketnautics.content.sputnik.node.SputnikNodeRegistry;
import dev.devce.rocketnautics.content.sputnik.storage.SputnikStorageManager;
import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImDouble;
import imgui.type.ImString;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.*;

public final class SputnikNodeGraphPanel {

    public enum ThemeMode {
        NEW("New (Modern)"),
        OLDSCHOOL("Oldschool (Classic)"),
        CUSTOM("Custom Design");

        private final String label;

        ThemeMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static ThemeMode themeMode = initThemeMode();

    private static ThemeMode initThemeMode() {
        try {
            String saved = SputnikCustomTheme.get().activeTheme;
            if ("OLDSCHOOL".equalsIgnoreCase(saved)) return ThemeMode.OLDSCHOOL;
            if ("CUSTOM".equalsIgnoreCase(saved)) return ThemeMode.CUSTOM;
            return ThemeMode.NEW;
        } catch (Throwable t) {
            return ThemeMode.NEW;
        }
    }

    private static int draggingNodeId = -1;
    private static float lastMouseX = 0.0f;
    private static float lastMouseY = 0.0f;

    private static final Set<Integer> selectedNodeIds = new LinkedHashSet<>();
    private static int hoveredNodeId = -1;
    private static int contextMenuNodeId = -1;

    private static boolean isBoxSelecting = false;
    private static float boxSelectStartX = 0.0f;
    private static float boxSelectStartY = 0.0f;

    private static int dragFromNodeId = -1;
    private static String dragFromPinId = null;
    private static float dragStartX = 0.0f;
    private static float dragStartY = 0.0f;
    private static int dragPinColor = 0xFFFFFFFF;

    private static long lastSavedTime = 0;
    private static int currentSputnikId = 0;

    private SputnikNodeGraphPanel() {
    }

    public static void render(SputnikGraph graph, int sputnikId, BlockPos pos) {
        render(graph, sputnikId, pos, null);
    }

    public static void render(SputnikGraph graph, int sputnikId, BlockPos pos, SputnikBlockEntity blockEntity) {
        if (graph == null) return;
        currentSputnikId = sputnikId;

        graph.evaluate(blockEntity);

        ImGuiIO io = ImGui.getIO();

        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(io.getDisplaySizeX(), io.getDisplaySizeY(), ImGuiCond.Always);

        int windowFlags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoCollapse;

        if (themeMode == ThemeMode.CUSTOM) {
            SputnikCustomTheme custom = SputnikCustomTheme.get();
            custom.applyToImGuiStyle();
            int cBg = (custom.rainbowEnabled && custom.rainbowGrid)
                    ? custom.getRainbowColor(0.0f, custom.canvasBg[3])
                    : SputnikCustomTheme.toImColor(custom.canvasBg);
            int mBg = SputnikCustomTheme.toImColor(custom.menuBarBg);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, cBg);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.MenuBarBg, mBg);
        } else if (themeMode == ThemeMode.OLDSCHOOL) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, 0.07f, 0.07f, 0.07f, 1.0f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.MenuBarBg, 0.09f, 0.09f, 0.09f, 1.0f);
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, 0.115f, 0.115f, 0.120f, 1.0f);
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.MenuBarBg, 0.140f, 0.140f, 0.145f, 1.0f);
        }

        float zoom = graph.getZoom();
        if (zoom <= 0.05f) {
            zoom = 1.0f;
            graph.setZoom(1.0f);
        }

        if (ImGui.begin("##SputnikGraphCanvas", windowFlags)) {

            renderMenuBar(graph, sputnikId, pos, io);

            ImDrawList drawList = ImGui.getWindowDrawList();
            float canvasX = ImGui.getWindowPosX();
            float canvasY = ImGui.getWindowPosY();
            float canvasW = ImGui.getWindowWidth();
            float canvasH = ImGui.getWindowHeight();

            handlePanning(graph, io);

            handleMouseWheelZoom(graph, canvasX, canvasY, io);
            zoom = graph.getZoom();

            drawGrid(drawList, canvasX, canvasY, canvasW, canvasH, graph.getPanX(), graph.getPanY(), zoom);

            float originX = canvasX + graph.getPanX();
            float originY = canvasY + graph.getPanY();

            updateMultiNodeDragging(graph, io, zoom);

            ImGui.setWindowFontScale(zoom);

            drawLinks(drawList, graph, originX, originY, io, zoom);

            drawPendingLink(drawList, graph, originX, originY, io, zoom);

            drawNodes(drawList, graph, originX, originY, io, zoom);

            ImGui.setWindowFontScale(1.0f);

            handleBoxSelection(drawList, graph, originX, originY, io, zoom);

            handleNodeContextMenu(graph);

            handleCanvasContextMenu(graph, originX, originY, io, zoom);

            if (!io.getWantTextInput()) {
                if (ImGui.isKeyPressed(GLFW.GLFW_KEY_X) || ImGui.isKeyPressed(GLFW.GLFW_KEY_DELETE)) {
                    deleteSelected(graph);
                }
            }
        }
        ImGui.end();
        ImGui.popStyleColor(2);

        SputnikCustomThemeEditor.render();
    }

    private static void renderMenuBar(SputnikGraph graph, int sputnikId, BlockPos pos, ImGuiIO io) {
        if (!ImGui.beginMenuBar()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSingleplayer = mc.getSingleplayerServer() != null;

        if (ImGui.beginMenu("File")) {
            if (ImGui.menuItem("Save Graph", "Ctrl+S")) {
                saveGraphToServer(graph, sputnikId, pos);
                lastSavedTime = System.currentTimeMillis();
            }

            if (isSingleplayer) {
                if (ImGui.menuItem("Open Satellite Folder")) {
                    openSatelliteFolder(mc.getSingleplayerServer(), sputnikId);
                }
            } else {
                ImGui.textDisabled("Open Folder (Singleplayer Only)");
            }

            if (ImGui.menuItem("Copy Graph JSON to Clipboard")) {
                byte[] bytes = SputnikStorageManager.serializeToGzip(graph);
                SputnikGraph copy = SputnikStorageManager.deserializeFromGzip(bytes);
                ImGui.setClipboardText(SputnikStorageManager.serializeToGzip(copy).length + " bytes GZIP");
            }

            ImGui.separator();
            if (ImGui.menuItem("Close & Save", "Esc")) {
                saveGraphToServer(graph, sputnikId, pos);
                if (mc.screen != null) {
                    mc.screen.onClose();
                }
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Edit")) {
            boolean hasSelection = !selectedNodeIds.isEmpty();
            if (ImGui.menuItem("Duplicate", "Shift+D", false, hasSelection)) {
                duplicateSelected(graph);
            }
            if (ImGui.menuItem("Delete", "X / Del", false, hasSelection)) {
                deleteSelected(graph);
            }
            if (ImGui.menuItem("Select All", "Ctrl+A")) {
                selectAll(graph);
            }
            if (ImGui.menuItem("Disconnect All Wires", null, false, hasSelection)) {
                disconnectSelectedWires(graph);
            }
            ImGui.separator();
            if (ImGui.menuItem("Reset Default Graph")) {
                resetToDefaultGraph(graph);
            }
            if (ImGui.menuItem("Clear All Nodes")) {
                graph.getNodes().clear();
                graph.getLinks().clear();
                selectedNodeIds.clear();
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Add")) {
            Map<String, List<INodeHandler>> categories = SputnikNodeRegistry.getByCategory();
            for (Map.Entry<String, List<INodeHandler>> entry : categories.entrySet()) {
                if (ImGui.beginMenu(entry.getKey())) {
                    for (INodeHandler h : entry.getValue()) {
                        if (ImGui.menuItem(h.getTitle())) {
                            float spawnX = (140.0f - graph.getPanX()) / Math.max(0.05f, graph.getZoom());
                            float spawnY = (140.0f - graph.getPanY()) / Math.max(0.05f, graph.getZoom());
                            SputnikNode node = new SputnikNode(graph.allocateNodeId(), h.getTypeId(), spawnX, spawnY);
                            graph.addNode(node);
                            selectedNodeIds.clear();
                            selectedNodeIds.add(node.getId());
                        }
                    }
                    ImGui.endMenu();
                }
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Satellite")) {
            ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "Satellite sp" + sputnikId);
            if (pos != null) {
                ImGui.text("BlockPos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            }
            ImGui.separator();
            if (ImGui.menuItem("Force Server Sync")) {
                saveGraphToServer(graph, sputnikId, pos);
                lastSavedTime = System.currentTimeMillis();
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("View")) {
            if (ImGui.menuItem("Center View", "Home")) {
                graph.setPanX(0.0f);
                graph.setPanY(0.0f);
            }
            String zoomLabel = String.format(Locale.ROOT, "Reset Zoom (%.0f%%)", graph.getZoom() * 100.0f);
            if (ImGui.menuItem(zoomLabel, "1:1")) {
                graph.setZoom(1.0f);
            }
            if (ImGui.menuItem("Zoom In (+15%)", "+ / =")) {
                graph.setZoom(Math.min(2.5f, graph.getZoom() * 1.15f));
            }
            if (ImGui.menuItem("Zoom Out (-15%)", "-")) {
                graph.setZoom(Math.max(0.3f, graph.getZoom() / 1.15f));
            }
            ImGui.separator();
            if (ImGui.beginMenu("Setup")) {
                if (ImGui.menuItem("Custom Design...", null, SputnikCustomThemeEditor.isOpen())) {
                    SputnikCustomThemeEditor.toggle();
                }
                ImGui.endMenu();
            }
            if (ImGui.menuItem("Custom Design Setup...", null, SputnikCustomThemeEditor.isOpen())) {
                SputnikCustomThemeEditor.toggle();
            }
            ImGui.endMenu();
        }

        if (ImGui.beginMenu("Appearance")) {
            if (ImGui.menuItem("Modern (Blender)", null, themeMode == ThemeMode.NEW)) {
                themeMode = ThemeMode.NEW;
                SputnikCustomTheme.get().activeTheme = "NEW";
                SputnikCustomTheme.get().markDirty();
            }
            if (ImGui.menuItem("Oldschool (Classic)", null, themeMode == ThemeMode.OLDSCHOOL)) {
                themeMode = ThemeMode.OLDSCHOOL;
                SputnikCustomTheme.get().activeTheme = "OLDSCHOOL";
                SputnikCustomTheme.get().markDirty();
            }
            if (ImGui.menuItem("Custom Design", null, themeMode == ThemeMode.CUSTOM)) {
                themeMode = ThemeMode.CUSTOM;
                SputnikCustomTheme.get().activeTheme = "CUSTOM";
                SputnikCustomTheme.get().markDirty();
            }
            ImGui.separator();
            if (ImGui.menuItem("Configure Custom Design...", null, SputnikCustomThemeEditor.isOpen())) {
                SputnikCustomThemeEditor.toggle();
            }
            ImGui.endMenu();
        }

        ImGui.sameLine(io.getDisplaySizeX() - 170.0f);
        ImGui.textDisabled(String.format(Locale.ROOT, "Zoom: %.0f%%", graph.getZoom() * 100.0f));

        if (System.currentTimeMillis() - lastSavedTime < 2500) {
            ImGui.sameLine(io.getDisplaySizeX() - 80.0f);
            ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, "[SAVED]");
        }

        ImGui.endMenuBar();
    }

    private static void openSatelliteFolder(net.minecraft.server.MinecraftServer server, int sputnikId) {
        if (server == null) return;
        try {
            Path dir = SputnikStorageManager.getSatelliteDir(server, sputnikId);
            Util.getPlatform().openFile(dir.toFile());
        } catch (Exception ignored) {
        }
    }

    public static void saveGraphToServer(SputnikGraph graph, int sputnikId, BlockPos pos) {
        if (pos == null || graph == null) return;
        byte[] bytes = SputnikStorageManager.serializeToGzip(graph);
        PacketDistributor.sendToServer(new SputnikSaveGraphPayload(pos, sputnikId, bytes));
    }

    public static void duplicateSelected(SputnikGraph graph) {
        if (graph == null) return;

        Set<Integer> targets = new HashSet<>(selectedNodeIds);
        if (targets.isEmpty() && hoveredNodeId != -1) {
            targets.add(hoveredNodeId);
        }
        if (targets.isEmpty()) return;

        Map<Integer, SputnikNode> oldToNew = new HashMap<>();
        Set<Integer> newSelection = new LinkedHashSet<>();

        for (int id : targets) {
            SputnikNode src = graph.findNode(id);
            if (src != null) {
                SputnikNode copy = new SputnikNode(graph.allocateNodeId(), src.getTypeId(), src.getX() + 35.0f, src.getY() + 35.0f);
                copy.setTitle(src.getTitle());
                copy.setWidth(src.getWidth());
                copy.setHeight(src.getHeight());
                copy.setCustomNumber(src.getCustomNumber());
                copy.setCustomString(src.getCustomString());
                graph.addNode(copy);
                oldToNew.put(src.getId(), copy);
                newSelection.add(copy.getId());
            }
        }

        for (SputnikLink link : new ArrayList<>(graph.getLinks())) {
            if (oldToNew.containsKey(link.getFromNodeId()) && oldToNew.containsKey(link.getToNodeId())) {
                graph.addLink(oldToNew.get(link.getFromNodeId()).getId(), link.getFromPinId(),
                        oldToNew.get(link.getToNodeId()).getId(), link.getToPinId());
            }
        }

        selectedNodeIds.clear();
        selectedNodeIds.addAll(newSelection);
    }

    public static void deleteSelected(SputnikGraph graph) {
        if (graph == null) return;
        Set<Integer> targets = new HashSet<>(selectedNodeIds);
        if (targets.isEmpty() && hoveredNodeId != -1) {
            targets.add(hoveredNodeId);
        }
        for (int id : targets) {
            graph.removeNode(id);
        }
        selectedNodeIds.clear();
        draggingNodeId = -1;
    }

    public static void selectAll(SputnikGraph graph) {
        if (graph == null) return;
        selectedNodeIds.clear();
        for (SputnikNode n : graph.getNodes()) {
            selectedNodeIds.add(n.getId());
        }
    }

    private static void disconnectSelectedWires(SputnikGraph graph) {
        if (graph == null || selectedNodeIds.isEmpty()) return;
        graph.getLinks().removeIf(l -> selectedNodeIds.contains(l.getFromNodeId()) || selectedNodeIds.contains(l.getToNodeId()));
    }

    private static void resetToDefaultGraph(SputnikGraph graph) {
        graph.getNodes().clear();
        graph.getLinks().clear();
        SputnikGraph def = SputnikGraph.createDefault();
        for (SputnikNode n : def.getNodes()) graph.addNode(n);
        for (SputnikLink l : def.getLinks()) graph.getLinks().add(l);
        selectedNodeIds.clear();
    }

    private static void handlePanning(SputnikGraph graph, ImGuiIO io) {
        if (draggingNodeId != -1 || dragFromNodeId != -1 || isBoxSelecting) return;

        if (ImGui.isWindowHovered() && !ImGui.isAnyItemActive()) {
            if (ImGui.isMouseDragging(ImGuiMouseButton.Middle, 0.0f)
                    || ImGui.isMouseDragging(ImGuiMouseButton.Right, 0.0f)) {
                graph.setPanX(graph.getPanX() + io.getMouseDeltaX());
                graph.setPanY(graph.getPanY() + io.getMouseDeltaY());
            }
        }
    }

    private static void handleMouseWheelZoom(SputnikGraph graph, float canvasX, float canvasY, ImGuiIO io) {
        float wheel = io.getMouseWheel();
        if (Math.abs(wheel) < 0.01f) return;

        float mx = io.getMousePosX();
        float my = io.getMousePosY();
        float zoom = graph.getZoom();
        if (zoom <= 0.05f) zoom = 1.0f;
        float originX = canvasX + graph.getPanX();
        float originY = canvasY + graph.getPanY();

        if (!io.getKeyCtrl()) {
            for (SputnikNode node : graph.getNodes()) {
                if (!"constant_number".equals(node.getTypeId())) continue;

                float nx = originX + node.getX() * zoom;
                float ny = originY + node.getY() * zoom;
                float headerH = ((themeMode == ThemeMode.OLDSCHOOL) ? 18.0f : 24.0f) * zoom;
                if (isWidgetArea(node, nx, ny, headerH, mx, my, zoom)) {
                    double step = io.getKeyShift() ? 0.1 : 1.0;
                    node.setCustomNumber(node.getCustomNumber() + wheel * step);
                    return;
                }
            }
        }

        float zoomFactor = wheel > 0 ? 1.12f : (1.0f / 1.12f);
        float newZoom = Math.max(0.3f, Math.min(2.5f, zoom * zoomFactor));
        if (Math.abs(newZoom - zoom) < 0.001f) return;

        float panX = graph.getPanX();
        float panY = graph.getPanY();

        float gx = (mx - canvasX - panX) / zoom;
        float gy = (my - canvasY - panY) / zoom;

        float newPanX = mx - canvasX - gx * newZoom;
        float newPanY = my - canvasY - gy * newZoom;

        graph.setPanX(newPanX);
        graph.setPanY(newPanY);
        graph.setZoom(newZoom);
    }

    private static void handleBoxSelection(ImDrawList drawList, SputnikGraph graph, float originX, float originY, ImGuiIO io, float zoom) {
        float mx = io.getMousePosX();
        float my = io.getMousePosY();

        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            if (my > 28.0f && !isMouseOverAnyNode(graph, originX, originY, mx, my, zoom) && draggingNodeId == -1 && dragFromNodeId == -1) {
                isBoxSelecting = true;
                boxSelectStartX = mx;
                boxSelectStartY = my;
                if (!io.getKeyShift()) {
                    selectedNodeIds.clear();
                }
            }
        }

        if (isBoxSelecting) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                float minX = Math.min(boxSelectStartX, mx);
                float maxX = Math.max(boxSelectStartX, mx);
                float minY = Math.min(boxSelectStartY, my);
                float maxY = Math.max(boxSelectStartY, my);

                if (themeMode == ThemeMode.CUSTOM) {
                    SputnikCustomTheme custom = SputnikCustomTheme.get();
                    int fillCol = SputnikCustomTheme.toImColor(custom.boxSelectBg);
                    int borderCol = SputnikCustomTheme.toImColor(custom.boxSelectBorder);
                    drawList.addRectFilled(minX, minY, maxX, maxY, fillCol);
                    drawList.addRect(minX, minY, maxX, maxY, borderCol, 0.0f, 0, 1.5f);
                } else if (themeMode == ThemeMode.OLDSCHOOL) {
                    drawList.addRectFilled(minX, minY, maxX, maxY, ImColor.rgba(0, 255, 136, 51)); // 0x3300FF88
                    drawList.addRect(minX, minY, maxX, maxY, ImColor.rgba(0, 255, 136, 255), 0.0f, 0, 1.0f); // 0xFF00FF88
                } else {
                    drawList.addRectFilled(minX, minY, maxX, maxY, ImColor.rgba(255, 255, 255, 20));
                    drawList.addRect(minX, minY, maxX, maxY, ImColor.rgba(255, 255, 255, 175), 0.0f, 0, 1.0f);
                }

                for (SputnikNode node : graph.getNodes()) {
                    float nx = originX + node.getX() * zoom;
                    float ny = originY + node.getY() * zoom;
                    float nw = node.getWidth() * zoom;
                    float nh = node.getHeight() * zoom;

                    boolean intersects = !(nx > maxX || nx + nw < minX || ny > maxY || ny + nh < minY);
                    if (intersects) {
                        selectedNodeIds.add(node.getId());
                    }
                }
            } else {
                isBoxSelecting = false;
            }
        }
    }

    private static void updateMultiNodeDragging(SputnikGraph graph, ImGuiIO io, float zoom) {
        float mx = io.getMousePosX();
        float my = io.getMousePosY();

        if (draggingNodeId != -1) {
            if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                float deltaX = (mx - lastMouseX) / zoom;
                float deltaY = (my - lastMouseY) / zoom;

                for (int id : selectedNodeIds) {
                    SputnikNode node = graph.findNode(id);
                    if (node != null) {
                        node.setX(node.getX() + deltaX);
                        node.setY(node.getY() + deltaY);
                    }
                }
            } else {
                draggingNodeId = -1;
            }
        }

        lastMouseX = mx;
        lastMouseY = my;
    }

    private static void drawGrid(ImDrawList drawList, float x, float y, float w, float h, float sx, float sy, float zoom) {
        if (themeMode == ThemeMode.CUSTOM) {
            SputnikCustomTheme custom = SputnikCustomTheme.get();
            float gridSize = Math.max(8.0f, custom.gridSize * zoom);
            int step = Math.max(1, custom.gridMajorStep);

            int gridCol = (custom.rainbowEnabled && custom.rainbowGrid)
                    ? custom.getRainbowColor(0.0f, custom.gridLineColor[3])
                    : SputnikCustomTheme.toImColor(custom.gridLineColor);

            int gridMajorCol = (custom.rainbowEnabled && custom.rainbowGrid)
                    ? custom.getRainbowColor(0.2f, custom.gridMajorLineColor[3])
                    : SputnikCustomTheme.toImColor(custom.gridMajorLineColor);

            int countX = 0;
            for (float gx = (sx % gridSize); gx < w; gx += gridSize) {
                drawList.addLine(x + gx, y, x + gx, y + h, (countX % step == 0) ? gridMajorCol : gridCol, 1.0f);
                countX++;
            }
            int countY = 0;
            for (float gy = (sy % gridSize); gy < h; gy += gridSize) {
                drawList.addLine(x, y + gy, x + w, y + gy, (countY % step == 0) ? gridMajorCol : gridCol, 1.0f);
                countY++;
            }
        } else if (themeMode == ThemeMode.OLDSCHOOL) {
            float gridSize = 20.0f * zoom;
            int gridCol = ImColor.rgba(255, 255, 255, 18); // 0x12FFFFFF
            for (float gx = (sx % gridSize); gx < w; gx += gridSize) {
                drawList.addLine(x + gx, y, x + gx, y + h, gridCol, 1.0f);
            }
            for (float gy = (sy % gridSize); gy < h; gy += gridSize) {
                drawList.addLine(x, y + gy, x + w, y + gy, gridCol, 1.0f);
            }
        } else {
            float gridSize = 28.0f * zoom;
            int dotCol = ImColor.rgba(255, 255, 255, 22);
            int dotMajorCol = ImColor.rgba(255, 255, 255, 55);

            int countX = 0;
            for (float gx = (sx % gridSize); gx < w; gx += gridSize) {
                int countY = 0;
                for (float gy = (sy % gridSize); gy < h; gy += gridSize) {
                    boolean isMajor = (countX % 4 == 0) && (countY % 4 == 0);
                    drawList.addCircleFilled(x + gx, y + gy, (isMajor ? 1.6f : 1.1f) * Math.max(0.5f, zoom), isMajor ? dotMajorCol : dotCol);
                    countY++;
                }
                countX++;
            }
        }
    }

    private static void drawLinks(ImDrawList drawList, SputnikGraph graph, float originX, float originY, ImGuiIO io, float zoom) {
        float mx = io.getMousePosX();
        float my = io.getMousePosY();
        SputnikLink linkToCut = null;

        List<SputnikLink> links = new ArrayList<>(graph.getLinks());
        for (SputnikLink link : links) {
            SputnikNode fromNode = graph.findNode(link.getFromNodeId());
            SputnikNode toNode = graph.findNode(link.getToNodeId());
            if (fromNode == null || toNode == null) continue;

            SputnikPin fromPin = fromNode.findPin(link.getFromPinId());
            int linkColor = fromPin != null ? fromPin.getType().getColor() : ImColor.rgb(180, 190, 200);

            ImVec2 p1 = getPinPos(fromNode, link.getFromPinId(), originX, originY, zoom);
            ImVec2 p2 = getPinPos(toNode, link.getToPinId(), originX, originY, zoom);

            float dist = Math.max(35.0f * zoom, Math.abs(p2.x - p1.x) * 0.5f);

            boolean wireHovered = isMouseNearCubicBezier(mx, my, p1.x, p1.y, p1.x + dist, p1.y, p2.x - dist, p2.y, p2.x, p2.y, zoom);

            if (themeMode == ThemeMode.CUSTOM) {
                SputnikCustomTheme custom = SputnikCustomTheme.get();
                int baseCol = custom.wireUsePinColor ? linkColor : SputnikCustomTheme.toImColor(custom.wireColor);

                if (custom.rainbowEnabled && custom.rainbowWires) {
                    baseCol = custom.getRainbowColor((fromNode.getId() + toNode.getId()) * 0.1f, 1.0f);
                }

                int finalColor = wireHovered ? SputnikCustomTheme.toImColor(custom.wireHoveredColor) : baseCol;
                float thickness = Math.max(1.0f, (wireHovered ? (custom.wireThickness + 1.0f) : custom.wireThickness) * zoom);

                if (custom.wireShadow) {
                    drawList.addBezierCubic(p1.x, p1.y + 2.0f * zoom, p1.x + dist, p1.y + 2.0f * zoom, p2.x - dist, p2.y + 2.0f * zoom, p2.x, p2.y + 2.0f * zoom, ImColor.rgba(0, 0, 0, 75), Math.max(1.0f, custom.wireShadowThickness * zoom));
                }

                drawList.addBezierCubic(p1.x, p1.y, p1.x + dist, p1.y, p2.x - dist, p2.y, p2.x, p2.y, finalColor, thickness);

                if (custom.wirePulseParticles) {
                    float speed = 0.0025f * custom.wirePulseSpeed;
                    float time = (System.currentTimeMillis() % 10000000L) * speed;
                    float cycle = 3.0f;
                    float t = (time - (fromNode.getId() * 0.53f)) % cycle;
                    if (t < 0) t += cycle;
                    if (t >= 0 && t <= 1.0f) {
                        float pulsePos = t * t * (3.0f - 2.0f * t);
                        float inv = 1.0f - pulsePos;
                        float px = inv * inv * inv * p1.x + 3 * inv * inv * pulsePos * (p1.x + dist) + 3 * inv * pulsePos * pulsePos * (p2.x - dist) + pulsePos * pulsePos * pulsePos * p2.x;
                        float py = inv * inv * inv * p1.y + 3 * inv * inv * pulsePos * p1.y + 3 * inv * pulsePos * pulsePos * p2.y + pulsePos * pulsePos * pulsePos * p2.y;

                        int sparkCol = (custom.rainbowEnabled && custom.rainbowWires)
                                ? custom.getRainbowColor(t, 0.9f)
                                : (wireHovered ? ImColor.rgba(255, 255, 255, 240) : finalColor);
                        drawList.addCircleFilled(px, py, Math.max(1.5f, 3.5f * zoom), sparkCol);
                        drawList.addCircleFilled(px, py, Math.max(1.0f, 1.5f * zoom), ImColor.rgba(255, 255, 255, 240));
                    }
                }
            } else if (themeMode == ThemeMode.OLDSCHOOL) {
                int wireColor = wireHovered ? ImColor.rgba(120, 255, 180, 240) : ImColor.rgba(0, 255, 136, 170); // 0xAA00FF88
                float thickness = Math.max(1.0f, (wireHovered ? 2.5f : 1.5f) * zoom);
                drawList.addBezierCubic(p1.x, p1.y, p1.x + dist, p1.y, p2.x - dist, p2.y, p2.x, p2.y, wireColor, thickness);

                // Animated glowing pulse dot traveling along the curve
                float speed = 0.003f;
                float time = (System.currentTimeMillis() % 1000000) * speed;
                float cycle = 3.5f;
                float t = (time - (fromNode.getId() * 0.73f)) % cycle;
                if (t < 0) t += cycle;
                if (t >= 0 && t <= 1.0f) {
                    float pulsePos = t * t * (3.0f - 2.0f * t);
                    float inv = 1.0f - pulsePos;
                    float px = inv * inv * inv * p1.x + 3 * inv * inv * pulsePos * (p1.x + dist) + 3 * inv * pulsePos * pulsePos * (p2.x - dist) + pulsePos * pulsePos * pulsePos * p2.x;
                    float py = inv * inv * inv * p1.y + 3 * inv * inv * pulsePos * p1.y + 3 * inv * pulsePos * pulsePos * p2.y + pulsePos * pulsePos * pulsePos * p2.y;
                    drawList.addCircleFilled(px, py, Math.max(1.5f, 3.5f * zoom), ImColor.rgba(0, 255, 136, 200));
                    drawList.addCircleFilled(px, py, Math.max(1.0f, 1.5f * zoom), ImColor.rgba(255, 255, 255, 240));
                }
            } else {
                drawList.addBezierCubic(p1.x, p1.y + 2.0f * zoom, p1.x + dist, p1.y + 2.0f * zoom, p2.x - dist, p2.y + 2.0f * zoom, p2.x, p2.y + 2.0f * zoom, ImColor.rgba(0, 0, 0, 65), Math.max(1.5f, 3.8f * zoom));

                int finalColor = wireHovered ? ImColor.rgb(255, 255, 255) : linkColor;
                float thickness = Math.max(1.0f, (wireHovered ? 3.0f : 2.2f) * zoom);
                drawList.addBezierCubic(p1.x, p1.y, p1.x + dist, p1.y, p2.x - dist, p2.y, p2.x, p2.y, finalColor, thickness);
            }

            if (wireHovered && ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
                linkToCut = link;
            }
        }

        if (linkToCut != null) {
            graph.getLinks().remove(linkToCut);
        }
    }

    private static boolean isMouseNearCubicBezier(float mx, float my, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, float zoom) {
        float thresh = Math.max(25.0f, 49.0f * zoom * zoom);
        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            float inv = 1.0f - t;
            float bx = inv * inv * inv * x1 + 3 * inv * inv * t * x2 + 3 * inv * t * t * x3 + t * t * t * x4;
            float by = inv * inv * inv * y1 + 3 * inv * inv * t * y2 + 3 * inv * t * t * y3 + t * t * t * y4;
            if ((mx - bx) * (mx - bx) + (my - by) * (my - by) <= thresh) {
                return true;
            }
        }
        return false;
    }

    private static void drawPendingLink(ImDrawList drawList, SputnikGraph graph, float originX, float originY, ImGuiIO io, float zoom) {
        if (dragFromNodeId == -1) return;

        float mx = io.getMousePosX();
        float my = io.getMousePosY();
        float dist = Math.max(30.0f * zoom, Math.abs(mx - dragStartX) * 0.5f);

        if (themeMode == ThemeMode.CUSTOM) {
            SputnikCustomTheme custom = SputnikCustomTheme.get();
            int linkCol = (custom.rainbowEnabled && custom.rainbowWires) ? custom.getRainbowColor(0.5f, 1.0f) : dragPinColor;
            if (custom.wireShadow) {
                drawList.addBezierCubic(dragStartX, dragStartY + 2.0f * zoom, dragStartX + dist, dragStartY + 2.0f * zoom, mx - dist, my + 2.0f * zoom, mx, my + 2.0f * zoom, ImColor.rgba(0, 0, 0, 75), Math.max(1.0f, custom.wireShadowThickness * zoom));
            }
            drawList.addBezierCubic(dragStartX, dragStartY, dragStartX + dist, dragStartY, mx - dist, my, mx, my, linkCol, Math.max(1.0f, custom.wireThickness * zoom));
        } else if (themeMode == ThemeMode.OLDSCHOOL) {
            drawList.addBezierCubic(dragStartX, dragStartY, dragStartX + dist, dragStartY, mx - dist, my, mx, my, ImColor.rgba(255, 255, 255, 170), Math.max(1.0f, 1.5f * zoom));
        } else {
            drawList.addBezierCubic(dragStartX, dragStartY + 2.0f * zoom, dragStartX + dist, dragStartY + 2.0f * zoom, mx - dist, my + 2.0f * zoom, mx, my + 2.0f * zoom, ImColor.rgba(0, 0, 0, 75), Math.max(1.5f, 4.5f * zoom));
            drawList.addBezierCubic(dragStartX, dragStartY, dragStartX + dist, dragStartY, mx - dist, my, mx, my, dragPinColor, Math.max(1.0f, 2.6f * zoom));
        }

        if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            checkLinkDrop(graph, originX, originY, mx, my, zoom);
            dragFromNodeId = -1;
            dragFromPinId = null;
        }
    }

    private static void drawNodes(ImDrawList drawList, SputnikGraph graph, float originX, float originY, ImGuiIO io, float zoom) {
        float mouseX = io.getMousePosX();
        float mouseY = io.getMousePosY();

        int bodyBg = ImColor.rgba(38, 38, 40, 252);
        int borderCol = ImColor.rgba(20, 20, 22, 255);

        List<SputnikNode> nodes = graph.getNodes();
        int nodeToBringToFront = -1;
        hoveredNodeId = -1;
        int nodeToDeleteWithMMB = -1;

        for (int i = nodes.size() - 1; i >= 0; i--) {
            SputnikNode node = nodes.get(i);
            float nx = originX + node.getX() * zoom;
            float ny = originY + node.getY() * zoom;
            float nw = node.getWidth() * zoom;
            float nh = node.getHeight() * zoom;
            float headerH = ((themeMode == ThemeMode.OLDSCHOOL) ? 18.0f : ((themeMode == ThemeMode.CUSTOM) ? SputnikCustomTheme.get().nodeHeaderHeight : 24.0f)) * zoom;
            float cornerRadius = ((themeMode == ThemeMode.OLDSCHOOL) ? 0.0f : ((themeMode == ThemeMode.CUSTOM) ? SputnikCustomTheme.get().nodeRounding : 6.0f)) * zoom;

            boolean hovered = mouseX >= nx && mouseX <= nx + nw && mouseY >= ny && mouseY <= ny + nh;
            if (hovered && hoveredNodeId == -1) {
                hoveredNodeId = node.getId();
            }

            boolean pinInteracted = handlePinInteractions(graph, node, originX, originY, mouseX, mouseY, zoom);
            boolean widgetInteracted = isWidgetArea(node, nx, ny, headerH, mouseX, mouseY, zoom);

            if (hovered) {
                if (ImGui.isMouseClicked(ImGuiMouseButton.Left) || ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                    if (io.getKeyShift()) {
                        if (selectedNodeIds.contains(node.getId())) {
                            selectedNodeIds.remove(node.getId());
                        } else {
                            selectedNodeIds.add(node.getId());
                        }
                    } else if (!selectedNodeIds.contains(node.getId())) {
                        selectedNodeIds.clear();
                        selectedNodeIds.add(node.getId());
                    }
                }

                if (!pinInteracted && !widgetInteracted) {
                    if (ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
                        nodeToDeleteWithMMB = node.getId();
                    } else if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
                        contextMenuNodeId = node.getId();
                        ImGui.openPopup("NodeContextMenu");
                    } else if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && draggingNodeId == -1 && dragFromNodeId == -1) {
                        draggingNodeId = node.getId();
                        lastMouseX = mouseX;
                        lastMouseY = mouseY;
                        nodeToBringToFront = i;
                    }
                }
            }

            boolean isSelected = selectedNodeIds.contains(node.getId());

            if (themeMode == ThemeMode.CUSTOM) {
                SputnikCustomTheme custom = SputnikCustomTheme.get();
                drawList.addRectFilled(nx + 3.0f * zoom, ny + 4.0f * zoom, nx + nw + 3.0f * zoom, ny + nh + 5.0f * zoom, ImColor.rgba(0, 0, 0, 85), cornerRadius + 2.0f * zoom);

                int bodyCol = hovered ? SputnikCustomTheme.toImColor(custom.nodeBodyBgHovered) : SputnikCustomTheme.toImColor(custom.nodeBodyBg);
                drawList.addRectFilled(nx, ny + headerH, nx + nw, ny + nh, bodyCol, cornerRadius, ImDrawFlags.RoundCornersBottom);

                int headerCol;
                if (custom.rainbowEnabled && custom.rainbowHeaders) {
                    float phase = (node.getId() * 0.15f) + (nx * 0.001f);
                    headerCol = custom.getRainbowColor(phase, 1.0f);
                } else if (custom.useCategoryColors) {
                    headerCol = getHeaderColor(node.getTypeId());
                } else {
                    headerCol = SputnikCustomTheme.toImColor(custom.nodeHeaderDefault);
                }

                drawList.addRectFilled(nx, ny, nx + nw, ny + headerH, headerCol, cornerRadius, ImDrawFlags.RoundCornersTop);
                drawList.addLine(nx, ny + headerH, nx + nw, ny + headerH, ImColor.rgba(20, 22, 26, 180), 1.0f);

                int titleCol = SputnikCustomTheme.toImColor(custom.nodeTitleColor);
                drawList.addText(nx + 12.0f * zoom, ny + (headerH * 0.5f) - 7.0f * zoom, titleCol, node.getTitle());

                int customBorderCol = hovered ? SputnikCustomTheme.toImColor(custom.nodeHoveredBorderColor) : SputnikCustomTheme.toImColor(custom.nodeBorderColor);
                drawList.addRect(nx, ny, nx + nw, ny + nh, customBorderCol, cornerRadius, ImDrawFlags.RoundCornersAll, Math.max(1.0f, custom.nodeBorderThickness * zoom));

                if (isSelected) {
                    float breath = custom.getBreathingFactor();
                    int selCol;
                    int glowCol;
                    if (custom.rainbowEnabled && custom.rainbowSelection) {
                        float phase = (node.getId() * 0.2f);
                        selCol = custom.getRainbowColor(phase, 1.0f);
                        glowCol = custom.getRainbowColor(phase + 0.1f, 0.8f * breath);
                    } else {
                        selCol = SputnikCustomTheme.toImColor(custom.nodeSelectedBorderColor);
                        glowCol = SputnikCustomTheme.toImColorWithAlpha(custom.nodeSelectedGlowColor, breath);
                    }

                    float glowSpread = (custom.selectionOutlineThickness * 1.2f) * breath * zoom;
                    drawList.addRect(nx - glowSpread, ny - glowSpread, nx + nw + glowSpread, ny + nh + glowSpread,
                            glowCol, cornerRadius + glowSpread, ImDrawFlags.RoundCornersAll, Math.max(1.0f, custom.selectionOutlineThickness * zoom));
                    drawList.addRect(nx - 1.0f * zoom, ny - 1.0f * zoom, nx + nw + 1.0f * zoom, ny + nh + 1.0f * zoom,
                            selCol, cornerRadius + 1.0f * zoom, ImDrawFlags.RoundCornersAll, Math.max(1.0f, 1.2f * zoom));
                } else if (hovered) {
                    drawList.addRect(nx - 1.5f * zoom, ny - 1.5f * zoom, nx + nw + 1.5f * zoom, ny + nh + 1.5f * zoom,
                            customBorderCol, cornerRadius + 1.5f * zoom, ImDrawFlags.RoundCornersAll, Math.max(1.0f, (custom.nodeBorderThickness + 0.6f) * zoom));
                }
            } else if (themeMode == ThemeMode.OLDSCHOOL) {
                // Sharp 2px offset shadow
                drawList.addRectFilled(nx + 2.0f * zoom, ny + 2.0f * zoom, nx + nw + 2.0f * zoom, ny + nh + 2.0f * zoom, ImColor.rgba(0, 0, 0, 85));

                // Sharp body
                int bodyBgOld = hovered ? ImColor.rgba(37, 37, 37, 238) : ImColor.rgba(26, 26, 26, 221);
                drawList.addRectFilled(nx, ny + headerH, nx + nw, ny + nh, bodyBgOld);

                // Tinted header + 1px category line + category colored title
                int catColor = getCategoryColor(node.getTypeId());
                int headerBgOld = (catColor & 0x00FFFFFF) | 0x33000000;
                drawList.addRectFilled(nx, ny, nx + nw, ny + headerH, headerBgOld);
                drawList.addLine(nx, ny + headerH - 1.0f, nx + nw, ny + headerH - 1.0f, catColor, 1.0f);
                drawList.addText(nx + 6.0f * zoom, ny + 2.0f * zoom, catColor, node.getTitle());

                // Outline
                int borderColOld = isSelected ? ImColor.rgb(255, 255, 255) : (hovered ? ImColor.rgb(170, 170, 170) : ImColor.rgb(68, 68, 68));
                drawList.addRect(nx, ny, nx + nw, ny + nh, borderColOld, 0.0f, 0, 1.0f);
            } else {
                drawList.addRectFilled(nx, ny + 3.0f * zoom, nx + nw, ny + nh + 6.0f * zoom, ImColor.rgba(0, 0, 0, 95), 8.0f * zoom);
                drawList.addRectFilled(nx, ny + 1.0f * zoom, nx + nw, ny + nh + 3.0f * zoom, ImColor.rgba(0, 0, 0, 55), 7.0f * zoom);

                int bodyCol = hovered ? ImColor.rgba(44, 44, 47, 252) : bodyBg;
                drawList.addRectFilled(nx, ny + headerH, nx + nw, ny + nh, bodyCol, cornerRadius, ImDrawFlags.RoundCornersBottom);

                int headerCol = getHeaderColor(node.getTypeId());
                drawList.addRectFilled(nx, ny, nx + nw, ny + headerH, headerCol, cornerRadius, ImDrawFlags.RoundCornersTop);
                drawList.addLine(nx, ny + headerH, nx + nw, ny + headerH, ImColor.rgba(18, 18, 20, 220), 1.0f);
                drawList.addText(nx + 8.0f * zoom, ny + 4.0f * zoom, 0xFFFFFFFF, "v  " + node.getTitle());

                drawList.addRect(nx, ny, nx + nw, ny + nh, borderCol, cornerRadius, ImDrawFlags.RoundCornersAll, 1.0f);

                if (isSelected) {
                    drawList.addRect(nx - 2.5f * zoom, ny - 2.5f * zoom, nx + nw + 2.5f * zoom, ny + nh + 2.5f * zoom,
                            ImColor.rgba(235, 125, 35, 160), cornerRadius + 2.5f * zoom, ImDrawFlags.RoundCornersAll, Math.max(1.0f, 2.0f * zoom));
                    drawList.addRect(nx - 1.0f * zoom, ny - 1.0f * zoom, nx + nw + 1.0f * zoom, ny + nh + 1.0f * zoom,
                            ImColor.rgb(255, 255, 255), cornerRadius + 1.0f * zoom, ImDrawFlags.RoundCornersAll, Math.max(1.0f, 1.4f * zoom));
                } else if (hovered) {
                    drawList.addRect(nx - 1.0f * zoom, ny - 1.0f * zoom, nx + nw + 1.0f * zoom, ny + nh + 1.0f * zoom,
                            ImColor.rgba(160, 160, 165, 220), cornerRadius + 1.0f * zoom, ImDrawFlags.RoundCornersAll, Math.max(1.0f, 1.2f * zoom));
                }
            }

            drawNodeContent(drawList, node, nx, ny, nw, nh, headerH, zoom);

            renderPins(drawList, graph, node, originX, originY, mouseX, mouseY, zoom);
        }

        if (nodeToDeleteWithMMB != -1) {
            if (selectedNodeIds.contains(nodeToDeleteWithMMB)) {
                deleteSelected(graph);
            } else {
                graph.removeNode(nodeToDeleteWithMMB);
            }
            if (hoveredNodeId == nodeToDeleteWithMMB) hoveredNodeId = -1;
            if (draggingNodeId == nodeToDeleteWithMMB) draggingNodeId = -1;
        }

        if (nodeToBringToFront != -1 && nodeToBringToFront < nodes.size()) {
            SputnikNode front = nodes.remove(nodeToBringToFront);
            nodes.add(front);
        }
    }

    private static boolean isWidgetArea(SputnikNode node, float nx, float ny, float headerH, float mouseX, float mouseY, float zoom) {
        String typeId = node.getTypeId();
        boolean isOld = (themeMode == ThemeMode.OLDSCHOOL);
        if ("constant_number".equals(typeId) || "constant_string".equals(typeId) || "constant_boolean".equals(typeId)) {
            float boxX = nx + 10.0f * zoom;
            float boxY = ny + headerH + (isOld ? 8.0f : 12.0f) * zoom;
            float boxW = (node.getWidth() - 28.0f) * zoom;
            float boxH = 26.0f * zoom;
            return mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
        }
        if ("sputnik_link".equals(typeId) || "satellite_comms".equals(typeId)) {
            float boxX = nx + 34.0f * zoom;
            float boxY = ny + headerH + (isOld ? 4.0f : 6.0f) * zoom;
            float boxW = 55.0f * zoom;
            float boxH = 20.0f * zoom;
            return mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
        }
        if (isEngineNode(typeId)) {
            float boxX = nx + 32.0f * zoom;
            float boxY = ny + headerH + (isOld ? 4.0f : 6.0f) * zoom;
            float boxW = 46.0f * zoom;
            float boxH = 20.0f * zoom;
            return mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
        }
        return false;
    }

    private static boolean isEngineNode(String typeId) {
        if ("engine_ignition".equals(typeId) || "engine_thrust".equals(typeId) || "engine_vector".equals(typeId) || "gyrodyne_control".equals(typeId)) {
            return true;
        }
        INodeHandler handler = SputnikNodeRegistry.get(typeId);
        return handler != null && "Actuators".equals(handler.getCategory());
    }

    private static void drawNodeContent(ImDrawList drawList, SputnikNode node, float nx, float ny, float nw, float nh, float headerH, float zoom) {
        String typeId = node.getTypeId();
        boolean isOld = (themeMode == ThemeMode.OLDSCHOOL);
        if (isOld) {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 0.0f);
        } else if (themeMode == ThemeMode.CUSTOM) {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, SputnikCustomTheme.get().frameRounding * zoom);
        } else {
            ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.FrameRounding, 3.0f * zoom);
        }

        try {
            if ("constant_number".equals(typeId)) {
                float boxX = nx + 10.0f * zoom;
                float boxY = ny + headerH + (isOld ? 8.0f : 12.0f) * zoom;
                float boxW = nw - 28.0f * zoom;

                if (zoom >= 0.55f) {
                    ImGui.setCursorScreenPos(boxX, boxY);
                    ImGui.pushItemWidth(boxW);
                    ImDouble val = new ImDouble(node.getCustomNumber());
                    if (ImGui.inputDouble("##num_" + node.getId(), val, 0.0, 0.0, "%.3f")) {
                        node.setCustomNumber(val.get());
                    }
                    ImGui.popItemWidth();
                } else {
                    float boxH = 22.0f * zoom;
                    drawList.addRectFilled(boxX, boxY, boxX + boxW, boxY + boxH, ImColor.rgba(15, 18, 22, 220));
                    drawList.addRect(boxX, boxY, boxX + boxW, boxY + boxH, ImColor.rgba(50, 60, 70, 200));
                    drawList.addText(boxX + 4.0f * zoom, boxY + 3.0f * zoom, 0xFFFFFFFF, String.format(Locale.ROOT, "%.3f", node.getCustomNumber()));
                }
            } else if ("constant_string".equals(typeId)) {
                float boxX = nx + 10.0f * zoom;
                float boxY = ny + headerH + (isOld ? 8.0f : 12.0f) * zoom;
                float boxW = nw - 28.0f * zoom;

                if (zoom >= 0.55f) {
                    ImGui.setCursorScreenPos(boxX, boxY);
                    ImGui.pushItemWidth(boxW);
                    ImString imStr = new ImString(node.getCustomString(), 256);
                    if (ImGui.inputText("##str_" + node.getId(), imStr)) {
                        node.setCustomString(imStr.get());
                    }
                    ImGui.popItemWidth();
                } else {
                    float boxH = 22.0f * zoom;
                    drawList.addRectFilled(boxX, boxY, boxX + boxW, boxY + boxH, ImColor.rgba(15, 18, 22, 220));
                    drawList.addRect(boxX, boxY, boxX + boxW, boxY + boxH, ImColor.rgba(50, 60, 70, 200));
                    drawList.addText(boxX + 4.0f * zoom, boxY + 3.0f * zoom, 0xFFFFFFFF, node.getCustomString());
                }
            } else if ("constant_boolean".equals(typeId)) {
                float boxX = nx + 10.0f * zoom;
                float boxY = ny + headerH + (isOld ? 8.0f : 12.0f) * zoom;
                float boxW = nw - 28.0f * zoom;
                float boxH = 26.0f * zoom;

                boolean b = node.getCustomNumber() > 0.5;
                int col = b ? ImColor.rgb(40, 130, 60) : ImColor.rgb(130, 40, 40);

                if (zoom >= 0.55f) {
                    ImGui.setCursorScreenPos(boxX, boxY);
                    ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, col);
                    if (ImGui.button((b ? "TRUE" : "FALSE") + "##bool_" + node.getId(), boxW, boxH)) {
                        node.setCustomNumber(b ? 0.0 : 1.0);
                    }
                    ImGui.popStyleColor();
                } else {
                    drawList.addRectFilled(boxX, boxY, boxX + boxW, boxY + boxH, col);
                    drawList.addText(boxX + 4.0f * zoom, boxY + 3.0f * zoom, 0xFFFFFFFF, b ? "TRUE" : "FALSE");
                }
            } else if ("display".equals(typeId) || "display_bridge".equals(typeId) || "visualizer".equals(typeId)) {
                float lcdX = nx + 34.0f * zoom;
                float lcdY = ny + headerH + (isOld ? 8.0f : 13.0f) * zoom;
                float lcdW = nw - 46.0f * zoom;
                float lcdH = 26.0f * zoom;

                float rounding = isOld ? 0.0f : 4.0f * zoom;
                drawList.addRectFilled(lcdX, lcdY, lcdX + lcdW, lcdY + lcdH, ImColor.rgba(10, 16, 14, 255), rounding);
                drawList.addRect(lcdX, lcdY, lcdX + lcdW, lcdY + lcdH, ImColor.rgba(25, 55, 45, 255), rounding);

                String val = node.getCustomString();
                if (val == null || val.isEmpty() || "---".equals(val)) {
                    drawList.addText(lcdX + 8.0f * zoom, lcdY + 5.0f * zoom, ImColor.rgba(110, 125, 120, 180), "[ NO INPUT ]");
                } else {
                    drawList.addText(lcdX + 8.0f * zoom, lcdY + 5.0f * zoom, ImColor.rgb(65, 240, 130), val);
                }
            } else if ("sputnik_link".equals(typeId)) {
                float topY = ny + headerH + (isOld ? 4.0f : 6.0f) * zoom;

                // Draw "ID:" label
                drawList.addText(nx + 12.0f * zoom, topY + 2.0f * zoom, ImColor.rgb(180, 190, 200), "ID:");

                float inputX = nx + 34.0f * zoom;
                float inputW = 55.0f * zoom;
                if (zoom >= 0.55f) {
                    ImGui.setCursorScreenPos(inputX, topY);
                    ImGui.pushItemWidth(inputW);
                    imgui.type.ImInt linkId = new imgui.type.ImInt((int) Math.round(node.getCustomNumber()));
                    if (ImGui.inputInt("##link_" + node.getId(), linkId, 0, 0)) {
                        node.setCustomNumber(Math.max(1, linkId.get()));
                    }
                    ImGui.popItemWidth();
                } else {
                    drawList.addText(inputX + 2.0f * zoom, topY + 2.0f * zoom, 0xFFFFFFFF, "#" + (int) Math.round(node.getCustomNumber()));
                }

                // Draw status badge on the right
                int id = (int) Math.round(node.getCustomNumber());
                boolean isReceiver = dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.isReceiver(id);
                int sig = isReceiver
                        ? dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.getTransmittedSignal(id)
                        : dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.getReceivedSignal(id);
                String status = (isReceiver ? "RX: " : "TX: ") + sig + "/15";

                float badgeX = nx + 98.0f * zoom;
                float badgeW = nw - 108.0f * zoom;
                float rounding = isOld ? 0.0f : 4.0f * zoom;
                drawList.addRectFilled(badgeX, topY, badgeX + badgeW, topY + 20.0f * zoom,
                        isReceiver ? ImColor.rgba(20, 60, 35, 200) : ImColor.rgba(65, 45, 15, 200), rounding);
                drawList.addRect(badgeX, topY, badgeX + badgeW, topY + 20.0f * zoom,
                        isReceiver ? ImColor.rgba(50, 180, 90, 220) : ImColor.rgba(200, 140, 40, 220), rounding);
                drawList.addText(badgeX + 6.0f * zoom, topY + 2.0f * zoom,
                        isReceiver ? ImColor.rgb(100, 240, 140) : ImColor.rgb(255, 200, 80), status);

                // Subtle divider line between settings and pins
                float divY = topY + 24.0f * zoom;
                drawList.addLine(nx + 8.0f * zoom, divY, nx + nw - 8.0f * zoom, divY, ImColor.rgba(255, 255, 255, 30), 1.0f);
            } else if ("satellite_comms".equals(typeId)) {
                float topY = ny + headerH + (isOld ? 4.0f : 6.0f) * zoom;

                drawList.addText(nx + 10.0f * zoom, topY + 2.0f * zoom, ImColor.rgb(180, 190, 200), "CH:");

                float inputX = nx + 32.0f * zoom;
                float inputW = 50.0f * zoom;
                if (zoom >= 0.55f) {
                    ImGui.setCursorScreenPos(inputX, topY);
                    ImGui.pushItemWidth(inputW);
                    imgui.type.ImInt chVal = new imgui.type.ImInt((int) Math.round(node.getCustomNumber()));
                    if (ImGui.inputInt("##ch_" + node.getId(), chVal, 0, 0)) {
                        node.setCustomNumber(Math.max(1, chVal.get()));
                    }
                    ImGui.popItemWidth();
                } else {
                    drawList.addText(inputX + 2.0f * zoom, topY + 2.0f * zoom, 0xFFFFFFFF, "#" + (int) Math.round(node.getCustomNumber()));
                }

                int ch = (int) Math.round(node.getCustomNumber());
                var packet = dev.devce.rocketnautics.content.sputnik.comms.SputnikCommsManager.peek(ch);
                String status;
                int badgeBg;
                int badgeBorder;
                int textColor;
                if (packet != null) {
                    if (packet.senderSputnikId == currentSputnikId) {
                        status = String.format(Locale.ROOT, "TX: %.1f", packet.data);
                        badgeBg = ImColor.rgba(20, 45, 65, 200);
                        badgeBorder = ImColor.rgba(40, 120, 180, 220);
                        textColor = ImColor.rgb(95, 185, 255);
                    } else {
                        status = String.format(Locale.ROOT, "RX #%d: %.1f", packet.senderSputnikId, packet.data);
                        badgeBg = ImColor.rgba(20, 60, 35, 200);
                        badgeBorder = ImColor.rgba(50, 180, 90, 220);
                        textColor = ImColor.rgb(100, 240, 140);
                    }
                } else {
                    status = "IDLE";
                    badgeBg = ImColor.rgba(40, 42, 48, 180);
                    badgeBorder = ImColor.rgba(70, 75, 85, 200);
                    textColor = ImColor.rgb(150, 155, 165);
                }

                float badgeX = nx + 88.0f * zoom;
                float badgeW = nw - 98.0f * zoom;
                float rounding = isOld ? 0.0f : 4.0f * zoom;
                drawList.addRectFilled(badgeX, topY, badgeX + badgeW, topY + 20.0f * zoom, badgeBg, rounding);
                drawList.addRect(badgeX, topY, badgeX + badgeW, topY + 20.0f * zoom, badgeBorder, rounding);
                drawList.addText(badgeX + 6.0f * zoom, topY + 2.0f * zoom, textColor, status);

                float divY = topY + 24.0f * zoom;
                drawList.addLine(nx + 8.0f * zoom, divY, nx + nw - 8.0f * zoom, divY, ImColor.rgba(255, 255, 255, 30), 1.0f);
            } else if (isEngineNode(typeId)) {
                float topY = ny + headerH + (isOld ? 4.0f : 6.0f) * zoom;

                drawList.addText(nx + 12.0f * zoom, topY + 2.0f * zoom, ImColor.rgb(180, 190, 200), "ID:");

                float inputX = nx + 32.0f * zoom;
                float inputW = 46.0f * zoom;
                if (zoom >= 0.55f) {
                    ImGui.setCursorScreenPos(inputX, topY);
                    ImGui.pushItemWidth(inputW);
                    imgui.type.ImInt engId = new imgui.type.ImInt((int) Math.round(node.getCustomNumber()));
                    if (ImGui.inputInt("##eng_" + node.getId(), engId, 0, 0)) {
                        node.setCustomNumber(Math.max(0, engId.get()));
                    }
                    ImGui.popItemWidth();
                } else {
                    drawList.addText(inputX + 2.0f * zoom, topY + 2.0f * zoom, 0xFFFFFFFF, "#" + (int) Math.round(node.getCustomNumber()));
                }

                int id = (int) Math.round(node.getCustomNumber());
                String status = "gyrodyne_control".equals(typeId) ? ("GYRO #" + id) : ("ENG #" + id);
                int badgeBg = ImColor.rgba(40, 42, 48, 180);
                int badgeBorder = ImColor.rgba(70, 75, 85, 200);
                int textColor = ImColor.rgb(150, 155, 165);

                Minecraft mc = Minecraft.getInstance();
                if (mc.getSingleplayerServer() != null) {
                    for (net.minecraft.server.level.ServerLevel sl : mc.getSingleplayerServer().getAllLevels()) {
                        dev.devce.rocketnautics.api.peripherals.IPeripheral p =
                                dev.devce.rocketnautics.api.peripherals.PeripheralRegistry.getPeripheralById(sl, id);
                        if (p != null) {
                            String type = p.getPeripheralType().toUpperCase(Locale.ROOT);
                            boolean active = p.readValue("active") > 0.5;
                            status = type + (active ? " [ON]" : " [OFF]");
                            badgeBg = active ? ImColor.rgba(20, 60, 35, 200) : ImColor.rgba(50, 35, 20, 200);
                            badgeBorder = active ? ImColor.rgba(50, 180, 90, 220) : ImColor.rgba(180, 100, 40, 220);
                            textColor = active ? ImColor.rgb(100, 240, 140) : ImColor.rgb(240, 160, 80);
                            break;
                        }
                    }
                } else if (mc.level != null) {
                    dev.devce.rocketnautics.api.peripherals.IPeripheral p =
                            dev.devce.rocketnautics.api.peripherals.PeripheralRegistry.getPeripheralById(mc.level, id);
                    if (p != null) {
                        String type = p.getPeripheralType().toUpperCase(Locale.ROOT);
                        boolean active = p.readValue("active") > 0.5;
                        status = type + (active ? " [ON]" : " [OFF]");
                        badgeBg = active ? ImColor.rgba(20, 60, 35, 200) : ImColor.rgba(50, 35, 20, 200);
                        badgeBorder = active ? ImColor.rgba(50, 180, 90, 220) : ImColor.rgba(180, 100, 40, 220);
                        textColor = active ? ImColor.rgb(100, 240, 140) : ImColor.rgb(240, 160, 80);
                    }
                }

                float badgeX = nx + 84.0f * zoom;
                float badgeW = nw - 92.0f * zoom;
                float rounding = isOld ? 0.0f : 4.0f * zoom;
                drawList.addRectFilled(badgeX, topY, badgeX + badgeW, topY + 20.0f * zoom, badgeBg, rounding);
                drawList.addRect(badgeX, topY, badgeX + badgeW, topY + 20.0f * zoom, badgeBorder, rounding);

                float maxTextW = badgeW - 10.0f * zoom;
                String displayStatus = status;
                if (status.length() * 7.2f * zoom > maxTextW) {
                    if (status.endsWith(" [ON]") || status.endsWith(" [OFF]")) {
                        String suffix = status.substring(status.length() - 6);
                        int avail = Math.max(1, (int) Math.floor((maxTextW - 6 * 7.2f * zoom - 3 * 7.2f * zoom) / (7.2f * zoom)));
                        displayStatus = status.substring(0, Math.min(avail, status.length() - 6)) + "..." + suffix;
                    } else {
                        int maxChars = Math.max(4, (int) Math.floor((maxTextW - 3 * 7.2f * zoom) / (7.2f * zoom)));
                        displayStatus = status.substring(0, Math.min(maxChars, status.length())) + "...";
                    }
                }
                drawList.addText(badgeX + 5.0f * zoom, topY + 2.0f * zoom, textColor, displayStatus);

                float divY = topY + 24.0f * zoom;
                drawList.addLine(nx + 8.0f * zoom, divY, nx + nw - 8.0f * zoom, divY, ImColor.rgba(255, 255, 255, 30), 1.0f);
            }
        } finally {
            ImGui.popStyleVar();
        }
    }

    private static float getPinPosX(SputnikNode node, SputnikPin pin, boolean isInput, float originX, float zoom) {
        float nx = originX + node.getX() * zoom;
        float nw = node.getWidth() * zoom;
        if (themeMode == ThemeMode.OLDSCHOOL) {
            return isInput ? nx : (nx + nw);
        }
        String typeId = node.getTypeId();
        if ("constant_number".equals(typeId) || "constant_string".equals(typeId) || "constant_boolean".equals(typeId)) {
            return nx + nw;
        }
        if ("display".equals(typeId) || "display_bridge".equals(typeId) || "visualizer".equals(typeId)) {
            return nx;
        }
        return isInput ? nx : (nx + nw);
    }

    private static float getPinPosY(SputnikNode node, int pinIndex, float originY, float zoom) {
        float ny = originY + node.getY() * zoom;
        float headerH = ((themeMode == ThemeMode.OLDSCHOOL) ? 18.0f : ((themeMode == ThemeMode.CUSTOM) ? SputnikCustomTheme.get().nodeHeaderHeight : 24.0f)) * zoom;
        String typeId = node.getTypeId();
        if (themeMode == ThemeMode.OLDSCHOOL) {
            if ("constant_number".equals(typeId) || "constant_string".equals(typeId) || "constant_boolean".equals(typeId)
                    || "display".equals(typeId) || "display_bridge".equals(typeId) || "visualizer".equals(typeId)) {
                return ny + headerH + 20.0f * zoom;
            }
            if ("sputnik_link".equals(typeId) || "satellite_comms".equals(typeId) || isEngineNode(typeId)) {
                return ny + headerH + (34.0f + (pinIndex * 18.0f)) * zoom;
            }
            return ny + headerH + (14.0f + (pinIndex * 18.0f)) * zoom;
        }

        if ("constant_number".equals(typeId) || "constant_string".equals(typeId) || "constant_boolean".equals(typeId)
                || "display".equals(typeId) || "display_bridge".equals(typeId) || "visualizer".equals(typeId)) {
            return ny + headerH + 24.0f * zoom;
        }
        if ("sputnik_link".equals(typeId) || "satellite_comms".equals(typeId) || isEngineNode(typeId)) {
            return ny + headerH + (36.0f + (pinIndex * 22.0f)) * zoom;
        }
        return ny + headerH + (16.0f + (pinIndex * 22.0f)) * zoom;
    }

    private static boolean handlePinInteractions(SputnikGraph graph, SputnikNode node, float originX, float originY, float mouseX, float mouseY, float zoom) {
        boolean interacted = false;
        float hitDistSq = Math.max(36.0f, 64.0f * zoom * zoom);

        List<SputnikPin> inputs = node.getInputs();
        for (int p = 0; p < inputs.size(); p++) {
            SputnikPin pin = inputs.get(p);
            float pinX = getPinPosX(node, pin, true, originX, zoom);
            float pinY = getPinPosY(node, p, originY, zoom);

            boolean pinHovered = (mouseX - pinX) * (mouseX - pinX) + (mouseY - pinY) * (mouseY - pinY) <= hitDistSq;
            if (pinHovered) {
                interacted = true;
                if (ImGui.isMouseClicked(ImGuiMouseButton.Right) || ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
                    graph.removeLinksTo(node.getId(), pin.getId());
                }
            }
        }

        List<SputnikPin> outputs = node.getOutputs();
        for (int p = 0; p < outputs.size(); p++) {
            SputnikPin pin = outputs.get(p);
            float pinX = getPinPosX(node, pin, false, originX, zoom);
            float pinY = getPinPosY(node, p, originY, zoom);

            boolean pinHovered = (mouseX - pinX) * (mouseX - pinX) + (mouseY - pinY) * (mouseY - pinY) <= hitDistSq;
            if (pinHovered) {
                interacted = true;
                if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                    dragFromNodeId = node.getId();
                    dragFromPinId = pin.getId();
                    dragStartX = pinX;
                    dragStartY = pinY;
                    dragPinColor = pin.getType().getColor();
                }
                if (ImGui.isMouseClicked(ImGuiMouseButton.Right) || ImGui.isMouseClicked(ImGuiMouseButton.Middle)) {
                    graph.removeLinksFrom(node.getId(), pin.getId());
                }
            }
        }

        return interacted;
    }

    private static void renderPins(ImDrawList drawList, SputnikGraph graph, SputnikNode node, float originX, float originY, float mouseX, float mouseY, float zoom) {
        String typeId = node.getTypeId();
        boolean isConstant = "constant_number".equals(typeId) || "constant_string".equals(typeId) || "constant_boolean".equals(typeId);
        boolean isDisplay = "display".equals(typeId) || "display_bridge".equals(typeId) || "visualizer".equals(typeId);
        boolean isOld = (themeMode == ThemeMode.OLDSCHOOL);
        float hitDistSq = Math.max(36.0f, 64.0f * zoom * zoom);

        List<SputnikPin> inputs = node.getInputs();
        for (int p = 0; p < inputs.size(); p++) {
            SputnikPin pin = inputs.get(p);
            float pinX = getPinPosX(node, pin, true, originX, zoom);
            float pinY = getPinPosY(node, p, originY, zoom);

            boolean pinHovered = (mouseX - pinX) * (mouseX - pinX) + (mouseY - pinY) * (mouseY - pinY) <= hitDistSq;
            boolean isConnected = isInputConnected(graph, node.getId(), pin.getId());
            int pinColor = pin.getType().getColor();

            if (isOld) {
                float pinSize = 6.0f * zoom;
                float pinLeft = pinX - 3.0f * zoom;
                float pinTop = pinY - 3.0f * zoom;

                if (pinHovered) {
                    drawList.addRectFilled(pinLeft - 1.0f * zoom, pinTop - 1.0f * zoom, pinLeft + pinSize + 1.0f * zoom, pinTop + pinSize + 1.0f * zoom, ImColor.rgba(255, 255, 255, 60));
                }

                int fillCol = isConnected ? pinColor : ((pinColor & 0x00FFFFFF) | 0x44000000);
                drawList.addRectFilled(pinLeft, pinTop, pinLeft + pinSize, pinTop + pinSize, fillCol);
                int outCol = pinHovered ? ImColor.rgb(255, 255, 255) : pinColor;
                drawList.addRect(pinLeft, pinTop, pinLeft + pinSize, pinTop + pinSize, outCol, 0.0f, 0, 1.0f);

                if (!isDisplay) {
                    drawList.addText(pinX + 8.0f * zoom, pinY - 7.0f * zoom, 0xFFCAD1D9, pin.getName());
                }
            } else {
                float radius = (pinHovered ? 5.5f : 4.2f) * Math.max(0.6f, Math.min(1.4f, zoom));
                if (isConnected) {
                    drawList.addCircleFilled(pinX, pinY, radius, pinColor);
                    drawList.addCircle(pinX, pinY, radius, ImColor.rgba(18, 20, 24, 255), 16, 1.2f);
                } else {
                    drawList.addCircleFilled(pinX, pinY, radius, ImColor.rgba(36, 36, 38, 255));
                    drawList.addCircle(pinX, pinY, radius, pinColor, 16, 1.6f);
                }

                if (pinHovered) {
                    drawList.addCircle(pinX, pinY, radius + 2.0f * zoom, ImColor.rgba(255, 255, 255, 180), 16, 1.2f);
                }

                if (!isDisplay) {
                    drawList.addText(pinX + 8.0f * zoom, pinY - 7.0f * zoom, 0xFFCAD1D9, pin.getName());
                }
            }
        }

        List<SputnikPin> outputs = node.getOutputs();
        for (int p = 0; p < outputs.size(); p++) {
            SputnikPin pin = outputs.get(p);
            float pinX = getPinPosX(node, pin, false, originX, zoom);
            float pinY = getPinPosY(node, p, originY, zoom);

            boolean pinHovered = (mouseX - pinX) * (mouseX - pinX) + (mouseY - pinY) * (mouseY - pinY) <= hitDistSq;
            boolean isConnected = isOutputConnected(graph, node.getId(), pin.getId());
            int pinColor = pin.getType().getColor();

            if (isOld) {
                float pinSize = 6.0f * zoom;
                float pinLeft = pinX - 3.0f * zoom;
                float pinTop = pinY - 3.0f * zoom;

                if (pinHovered) {
                    drawList.addRectFilled(pinLeft - 1.0f * zoom, pinTop - 1.0f * zoom, pinLeft + pinSize + 1.0f * zoom, pinTop + pinSize + 1.0f * zoom, ImColor.rgba(255, 255, 255, 60));
                }

                int fillCol = isConnected ? pinColor : ((pinColor & 0x00FFFFFF) | 0x44000000);
                drawList.addRectFilled(pinLeft, pinTop, pinLeft + pinSize, pinTop + pinSize, fillCol);
                int outCol = pinHovered ? ImColor.rgb(255, 255, 255) : pinColor;
                drawList.addRect(pinLeft, pinTop, pinLeft + pinSize, pinTop + pinSize, outCol, 0.0f, 0, 1.0f);

                if (!isConstant) {
                    float tw = pin.getName().length() * 7.2f * zoom;
                    drawList.addText(pinX - 8.0f * zoom - tw, pinY - 7.0f * zoom, 0xFFCAD1D9, pin.getName());
                }
            } else {
                float radius = (pinHovered ? 5.5f : 4.2f) * Math.max(0.6f, Math.min(1.4f, zoom));
                if (isConnected) {
                    drawList.addCircleFilled(pinX, pinY, radius, pinColor);
                    drawList.addCircle(pinX, pinY, radius, ImColor.rgba(18, 20, 24, 255), 16, 1.2f);
                } else {
                    drawList.addCircleFilled(pinX, pinY, radius, ImColor.rgba(36, 36, 38, 255));
                    drawList.addCircle(pinX, pinY, radius, pinColor, 16, 1.6f);
                }

                if (pinHovered) {
                    drawList.addCircle(pinX, pinY, radius + 2.0f * zoom, ImColor.rgba(255, 255, 255, 180), 16, 1.2f);
                }

                if (!isConstant) {
                    float tw = ImGui.calcTextSize(pin.getName()).x;
                    drawList.addText(pinX - 9.0f * zoom - tw, pinY - 7.0f * zoom, 0xFFCAD1D9, pin.getName());
                }
            }
        }
    }

    private static boolean isInputConnected(SputnikGraph graph, int nodeId, String pinId) {
        for (SputnikLink l : graph.getLinks()) {
            if (l.getToNodeId() == nodeId && l.getToPinId().equals(pinId)) return true;
        }
        return false;
    }

    private static boolean isOutputConnected(SputnikGraph graph, int nodeId, String pinId) {
        for (SputnikLink l : graph.getLinks()) {
            if (l.getFromNodeId() == nodeId && l.getFromPinId().equals(pinId)) return true;
        }
        return false;
    }

    private static void checkLinkDrop(SputnikGraph graph, float originX, float originY, float mx, float my, float zoom) {
        if (dragFromNodeId == -1 || dragFromPinId == null) return;

        float hitDistSq = Math.max(36.0f, 64.0f * zoom * zoom);
        for (SputnikNode targetNode : graph.getNodes()) {
            if (targetNode.getId() == dragFromNodeId) continue;

            for (SputnikPin pin : targetNode.getInputs()) {
                ImVec2 pos = getPinPos(targetNode, pin.getId(), originX, originY, zoom);
                float distSq = (mx - pos.x) * (mx - pos.x) + (my - pos.y) * (my - pos.y);
                if (distSq <= hitDistSq) {
                    graph.addLink(dragFromNodeId, dragFromPinId, targetNode.getId(), pin.getId());
                    return;
                }
            }
        }
    }

    private static ImVec2 getPinPos(SputnikNode node, String pinId, float originX, float originY, float zoom) {
        List<SputnikPin> inputs = node.getInputs();
        for (int p = 0; p < inputs.size(); p++) {
            SputnikPin pin = inputs.get(p);
            if (pin.getId().equals(pinId)) {
                return new ImVec2(getPinPosX(node, pin, true, originX, zoom), getPinPosY(node, p, originY, zoom));
            }
        }

        List<SputnikPin> outputs = node.getOutputs();
        for (int p = 0; p < outputs.size(); p++) {
            SputnikPin pin = outputs.get(p);
            if (pin.getId().equals(pinId)) {
                return new ImVec2(getPinPosX(node, pin, false, originX, zoom), getPinPosY(node, p, originY, zoom));
            }
        }

        return new ImVec2(originX + node.getX() * zoom, originY + node.getY() * zoom);
    }

    private static void handleNodeContextMenu(SputnikGraph graph) {
        if (ImGui.beginPopup("NodeContextMenu")) {
            SputnikNode node = graph.findNode(contextMenuNodeId);
            if (node != null) {
                ImGui.textColored(0.4f, 0.8f, 1.0f, 1.0f, node.getTitle());
                ImGui.separator();

                if (ImGui.menuItem("Duplicate (Shift+D)")) {
                    duplicateSelected(graph);
                }
                if (ImGui.menuItem("Disconnect Wires")) {
                    disconnectSelectedWires(graph);
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete Node (X / Del)")) {
                    deleteSelected(graph);
                }
            }
            ImGui.endPopup();
        }
    }

    private static void handleCanvasContextMenu(SputnikGraph graph, float originX, float originY, ImGuiIO io, float zoom) {
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right) && !isMouseOverAnyNode(graph, originX, originY, io.getMousePosX(), io.getMousePosY(), zoom) && !isBoxSelecting) {
            ImGui.openPopup("CanvasContextMenu");
        }

        if (ImGui.beginPopup("CanvasContextMenu")) {
            float spawnX = (io.getMousePosX() - originX) / zoom;
            float spawnY = (io.getMousePosY() - originY) / zoom;

            ImGui.textColored(0.4f, 0.8f, 1.0f, 1.0f, "Create: Cosmonautics Nodes");
            ImGui.separator();

            Map<String, List<INodeHandler>> categories = SputnikNodeRegistry.getByCategory();
            for (Map.Entry<String, List<INodeHandler>> entry : categories.entrySet()) {
                String category = entry.getKey();
                List<INodeHandler> handlers = entry.getValue();

                if (ImGui.beginMenu(category)) {
                    for (INodeHandler handler : handlers) {
                        if (ImGui.menuItem("+ " + handler.getTitle())) {
                            SputnikNode node = new SputnikNode(graph.allocateNodeId(), handler.getTypeId(), spawnX, spawnY);
                            graph.addNode(node);
                            selectedNodeIds.clear();
                            selectedNodeIds.add(node.getId());
                        }
                    }
                    ImGui.endMenu();
                }
            }

            ImGui.separator();
            if (ImGui.menuItem("Reset Default Graph")) {
                resetToDefaultGraph(graph);
            }
            if (ImGui.menuItem("Center View")) {
                graph.setPanX(0.0f);
                graph.setPanY(0.0f);
            }
            String zoomLabel = String.format(Locale.ROOT, "Reset Zoom (%.0f%%)", graph.getZoom() * 100.0f);
            if (ImGui.menuItem(zoomLabel)) {
                graph.setZoom(1.0f);
            }
            if (ImGui.menuItem("Clear All Nodes")) {
                graph.getNodes().clear();
                graph.getLinks().clear();
                selectedNodeIds.clear();
            }

            ImGui.endPopup();
        }
    }

    private static boolean isMouseOverAnyNode(SputnikGraph graph, float originX, float originY, float mx, float my, float zoom) {
        for (SputnikNode n : graph.getNodes()) {
            float nx = originX + n.getX() * zoom;
            float ny = originY + n.getY() * zoom;
            float nw = n.getWidth() * zoom;
            float nh = n.getHeight() * zoom;
            if (mx >= nx && mx <= nx + nw && my >= ny && my <= ny + nh) {
                return true;
            }
        }
        return false;
    }

    private static int getHeaderColor(String typeId) {
        INodeHandler handler = SputnikNodeRegistry.get(typeId);
        if (handler != null) {
            String cat = handler.getCategory();
            if (cat != null) {
                switch (cat) {
                    case "Sensors":
                        return ImColor.rgb(29, 96, 133);
                    case "Actuators":
                        return ImColor.rgb(125, 77, 35);
                    case "Math":
                        return ImColor.rgb(34, 107, 145);
                    case "Logic":
                        return ImColor.rgb(30, 122, 92);
                    case "Communication":
                        return ImColor.rgb(135, 60, 92);
                    case "Display":
                        return ImColor.rgb(53, 69, 107);
                    case "Constants":
                        return ImColor.rgb(60, 63, 135);
                }
            }
            return handler.getHeaderColor();
        }
        return ImColor.rgb(42, 50, 62);
    }

    private static int getCategoryColor(String typeId) {
        if (themeMode == ThemeMode.OLDSCHOOL) {
            INodeHandler handler = SputnikNodeRegistry.get(typeId);
            if (handler != null) {
                String cat = handler.getCategory();
                if (cat != null) {
                    switch (cat) {
                        case "Sensors":
                            return ImColor.rgb(0, 170, 255); // 0xFF00AAFF
                        case "Actuators":
                            return ImColor.rgb(255, 170, 0); // 0xFFFFAA00
                        case "Math":
                            return ImColor.rgb(0, 255, 136); // 0xFF00FF88
                        case "Logic":
                            return ImColor.rgb(255, 85, 85); // 0xFFFF5555
                        case "Communication":
                            return ImColor.rgb(170, 0, 255); // 0xFFAA00FF
                        case "Display":
                            return ImColor.rgb(255, 255, 0); // 0xFFFFFF00
                        case "Constants":
                            return ImColor.rgb(0, 170, 170); // 0xFF00AAAA
                    }
                }
                return ImColor.rgb(0, 255, 136);
            }
            return ImColor.rgb(0, 255, 136);
        }
        return getHeaderColor(typeId);
    }
}
