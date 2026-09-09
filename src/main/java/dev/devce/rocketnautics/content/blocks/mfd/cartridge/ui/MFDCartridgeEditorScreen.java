package dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui;

import dev.devce.rocketnautics.client.ui.imgui.ImGuiManager;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager.CartridgeMetadata;
import dev.devce.rocketnautics.lua.LuaSandbox;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTabBarFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

public class MFDCartridgeEditorScreen extends Screen {

    private static class OpenTab {
        final Path path;
        final ImString content;
        String lastSavedContent;
        boolean isDirty;

        OpenTab(Path path, String text) {
            this.path = path;
            this.content = new ImString(text, 250000);
            this.lastSavedContent = text;
            this.isDirty = false;
        }

        String getFileName() {
            return path.getFileName().toString();
        }
    }

    private static class LogEntry {
        final String time;
        final String level;
        final String message;
        final int color;

        LogEntry(String time, String level, String message, int color) {
            this.time = time;
            this.level = level;
            this.message = message;
            this.color = color;
        }
    }

    private final String cartridgeId;
    private final InteractionHand hand;
    private final Path cartridgeDir;

    private final List<OpenTab> openTabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private final List<Path> cartridgeFiles = new ArrayList<>();

    private CartridgeMetadata metadata;
    private final ImString metaTitle = new ImString(128);
    private final ImString metaAuthor = new ImString(128);
    private final ImString metaVersion = new ImString(64);
    private final ImString metaDescription = new ImString(2048);

    private final List<LogEntry> terminalLogs = new ArrayList<>();
    private final ImString replInput = new ImString(2048);
    private final ImString newFileNameInput = new ImString(128);
    private boolean openNewFileModal = false;

    private long lastSavedNotificationTime = 0;
    private boolean autoScrollLogs = true;

    public MFDCartridgeEditorScreen(String cartridgeId, InteractionHand hand) {
        super(Component.literal("MFD Cartridge Studio - " + cartridgeId));
        this.cartridgeId = (cartridgeId != null && !cartridgeId.isEmpty()) ? cartridgeId : "default";
        this.hand = hand;
        this.cartridgeDir = CartridgeManager.getCartridgeDir(this.cartridgeId);

        this.metadata = CartridgeManager.getMetadata(this.cartridgeId);
        this.metaTitle.set(metadata.title != null ? metadata.title : this.cartridgeId);
        this.metaAuthor.set(metadata.author != null ? metadata.author : "Anonymous");
        this.metaVersion.set(metadata.version != null ? metadata.version : "1.0.0");
        this.metaDescription.set(metadata.description != null ? metadata.description : "");

        log("INFO", "Initialized MFD Cartridge Studio for [" + this.cartridgeId + "]", ImColor.rgb(100, 255, 140));
        log("INFO", "Location: " + cartridgeDir.toAbsolutePath(), ImColor.rgb(160, 160, 160));

        refreshFiles();

        Path mainLua = cartridgeDir.resolve("main.lua");
        if (Files.exists(mainLua)) {
            openFileInTab(mainLua);
        } else if (!cartridgeFiles.isEmpty()) {
            openFileInTab(cartridgeFiles.get(0));
        }
    }

    private void log(String level, String msg, int color) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        terminalLogs.add(new LogEntry(time, level, msg, color));
        if (terminalLogs.size() > 300) {
            terminalLogs.remove(0);
        }
    }

    private void refreshFiles() {
        cartridgeFiles.clear();
        if (Files.exists(cartridgeDir)) {
            try (Stream<Path> stream = Files.walk(cartridgeDir, 3)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> !p.getFileName().toString().equals("metadata.json"))
                      .forEach(cartridgeFiles::add);
            } catch (IOException e) {
                log("ERROR", "Failed to refresh file list: " + e.getMessage(), ImColor.rgb(255, 90, 90));
            }
        }
    }

    private void openFileInTab(Path path) {
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).path.equals(path)) {
                activeTabIndex = i;
                return;
            }
        }
        try {
            String content = Files.exists(path) ? Files.readString(path) : "";
            OpenTab tab = new OpenTab(path, content);
            openTabs.add(tab);
            activeTabIndex = openTabs.size() - 1;
            log("INFO", "Opened " + path.getFileName(), ImColor.rgb(120, 200, 255));
        } catch (IOException e) {
            log("ERROR", "Failed to open " + path.getFileName() + ": " + e.getMessage(), ImColor.rgb(255, 90, 90));
        }
    }

    private void saveCurrentTab() {
        if (activeTabIndex >= 0 && activeTabIndex < openTabs.size()) {
            OpenTab tab = openTabs.get(activeTabIndex);
            try {
                Files.createDirectories(tab.path.getParent());
                String code = tab.content.get();
                Files.writeString(tab.path, code);
                tab.lastSavedContent = code;
                tab.isDirty = false;
                lastSavedNotificationTime = System.currentTimeMillis();
                log("INFO", "Saved " + tab.getFileName(), ImColor.rgb(100, 255, 140));
            } catch (IOException e) {
                log("ERROR", "Failed to save " + tab.getFileName() + ": " + e.getMessage(), ImColor.rgb(255, 90, 90));
            }
        }
    }

    private void saveAll() {
        for (OpenTab tab : openTabs) {
            if (tab.isDirty) {
                try {
                    Files.createDirectories(tab.path.getParent());
                    String code = tab.content.get();
                    Files.writeString(tab.path, code);
                    tab.lastSavedContent = code;
                    tab.isDirty = false;
                } catch (IOException e) {
                    log("ERROR", "Failed to save " + tab.getFileName() + ": " + e.getMessage(), ImColor.rgb(255, 90, 90));
                }
            }
        }
        saveMetadataChanges();
        lastSavedNotificationTime = System.currentTimeMillis();
        log("INFO", "Saved all files and metadata successfully.", ImColor.rgb(100, 255, 140));
    }

    private void saveMetadataChanges() {
        metadata.title = metaTitle.get().trim();
        metadata.author = metaAuthor.get().trim();
        metadata.version = metaVersion.get().trim();
        metadata.description = metaDescription.get().trim();
        CartridgeManager.saveMetadata(cartridgeId, metadata);
        lastSavedNotificationTime = System.currentTimeMillis();
        log("INFO", "Updated cartridge metadata.", ImColor.rgb(100, 255, 140));
    }

    private void createNewFile(String name) {
        String clean = name.trim();
        if (clean.isEmpty()) return;
        if (!clean.endsWith(".lua")) clean += ".lua";
        Path target = cartridgeDir.resolve(clean);
        if (Files.exists(target)) {
            log("WARN", "File already exists: " + clean, ImColor.rgb(255, 200, 80));
            openFileInTab(target);
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "local M = {}\n\nfunction M.init()\nend\n\nfunction M.update()\nend\n\nreturn M\n");
            refreshFiles();
            openFileInTab(target);
            log("INFO", "Created new script: " + clean, ImColor.rgb(100, 255, 140));
        } catch (IOException e) {
            log("ERROR", "Failed to create file: " + e.getMessage(), ImColor.rgb(255, 90, 90));
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
            openTabs.removeIf(t -> t.path.equals(path));
            if (activeTabIndex >= openTabs.size()) {
                activeTabIndex = Math.max(0, openTabs.size() - 1);
            }
            refreshFiles();
            log("INFO", "Deleted file: " + path.getFileName(), ImColor.rgb(255, 150, 100));
        } catch (IOException e) {
            log("ERROR", "Failed to delete file: " + e.getMessage(), ImColor.rgb(255, 90, 90));
        }
    }

    private void validateSyntax(OpenTab tab) {
        if (tab == null) return;
        try {
            Globals globals = LuaSandbox.createSandboxedGlobals();
            globals.load(tab.content.get());
            log("INFO", "[Syntax OK] " + tab.getFileName() + " compiled without errors.", ImColor.rgb(100, 255, 140));
        } catch (LuaError e) {
            log("ERROR", "[Syntax Error] " + tab.getFileName() + ": " + e.getMessage(), ImColor.rgb(255, 90, 90));
        } catch (Exception e) {
            log("ERROR", "[Validation Error] " + e.getMessage(), ImColor.rgb(255, 90, 90));
        }
    }

    private void runRepl(String expr) {
        String clean = expr.trim();
        if (clean.isEmpty()) return;
        log("INPUT", "> " + clean, ImColor.rgb(200, 200, 200));
        try {
            Globals globals = LuaSandbox.createSandboxedGlobals();
            LuaValue chunk;
            try {
                chunk = globals.load("return " + clean);
            } catch (Exception ignored) {
                chunk = globals.load(clean);
            }
            LuaValue result = chunk.call();
            log("RESULT", "= " + (result.isnil() ? "nil" : result.tojstring()), ImColor.rgb(120, 220, 255));
        } catch (LuaError e) {
            log("ERROR", e.getMessage(), ImColor.rgb(255, 90, 90));
        } catch (Exception e) {
            log("ERROR", e.getMessage(), ImColor.rgb(255, 90, 90));
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ImGuiManager.getInstance().renderScreen(this::renderStudio);
    }

    private void renderStudio() {
        ImGuiIO io = ImGui.getIO();
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always);
        ImGui.setNextWindowSize(io.getDisplaySizeX(), io.getDisplaySizeY(), ImGuiCond.Always);

        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoBringToFrontOnFocus;

        if (ImGui.begin("MFDCartridgeStudioWindow", flags)) {
            renderMenuBar(io);

            float leftPanelWidth = 230.0f;
            float totalHeight = ImGui.getContentRegionAvailY();

            ImGui.beginChild("FileExplorerPanel", leftPanelWidth, totalHeight, true);
            renderFileExplorer();
            ImGui.endChild();

            ImGui.sameLine();

            ImGui.beginChild("CenterEditorArea", 0.0f, totalHeight, false);
            renderMainWorkspace();
            ImGui.endChild();

            renderModals();
        }
        ImGui.end();
    }

    private void renderMenuBar(ImGuiIO io) {
        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Save Script", "Ctrl+S", false, !openTabs.isEmpty())) {
                    saveCurrentTab();
                }
                if (ImGui.menuItem("Save All", null, false, !openTabs.isEmpty())) {
                    saveAll();
                }
                ImGui.separator();
                if (ImGui.menuItem("New Lua Script...")) {
                    newFileNameInput.set("new_script.lua");
                    openNewFileModal = true;
                }
                ImGui.separator();
                if (ImGui.menuItem("Close Studio", "Esc")) {
                    this.onClose();
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Project")) {
                if (ImGui.menuItem("Refresh Files")) {
                    refreshFiles();
                    log("INFO", "File list refreshed.", ImColor.rgb(180, 180, 180));
                }
                if (ImGui.menuItem("Open In File Explorer")) {
                    Util.getPlatform().openFile(cartridgeDir.toFile());
                }
                ImGui.endMenu();
            }

            if (ImGui.beginMenu("Lua")) {
                if (ImGui.menuItem("Check Syntax", "F5", false, !openTabs.isEmpty())) {
                    if (activeTabIndex >= 0 && activeTabIndex < openTabs.size()) {
                        validateSyntax(openTabs.get(activeTabIndex));
                    }
                }
                if (ImGui.menuItem("Clear Console Output")) {
                    terminalLogs.clear();
                }
                ImGui.endMenu();
            }

            if (System.currentTimeMillis() - lastSavedNotificationTime < 2500) {
                ImGui.sameLine(io.getDisplaySizeX() - 260.0f);
                ImGui.textColored(0.35f, 1.0f, 0.45f, 1.0f, "[SAVED TO DISK]");
            }

            ImGui.sameLine(io.getDisplaySizeX() - 150.0f);
            ImGui.textDisabled("Cartridge: " + cartridgeId);

            ImGui.endMenuBar();
        }
    }

    private void renderFileExplorer() {
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "PROJECT EXPLORER");
        ImGui.textDisabled(cartridgeId + "/");
        ImGui.separator();

        if (ImGui.button("+ New", 70.0f, 22.0f)) {
            newFileNameInput.set("script.lua");
            openNewFileModal = true;
        }
        ImGui.sameLine();
        if (ImGui.button("Refresh", 70.0f, 22.0f)) {
            refreshFiles();
        }
        ImGui.sameLine();
        if (ImGui.button("Dir", 60.0f, 22.0f)) {
            Util.getPlatform().openFile(cartridgeDir.toFile());
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.beginChild("FilesListScroll", 0.0f, 0.0f, false);
        for (Path file : cartridgeFiles) {
            String fileName = file.getFileName().toString();
            boolean isSelected = (!openTabs.isEmpty() && activeTabIndex >= 0
                    && activeTabIndex < openTabs.size() && openTabs.get(activeTabIndex).path.equals(file));

            if (ImGui.selectable(fileName + "##fileitem", isSelected)) {
                openFileInTab(file);
            }

            if (ImGui.beginPopupContextItem("file_ctx_" + fileName)) {
                if (ImGui.menuItem("Open In Editor")) {
                    openFileInTab(file);
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete File")) {
                    deleteFile(file);
                }
                ImGui.endPopup();
            }
        }
        ImGui.endChild();
    }

    private void renderMainWorkspace() {
        if (ImGui.beginTabBar("WorkspaceTabBar", ImGuiTabBarFlags.None)) {

            if (ImGui.beginTabItem("Code Editor")) {
                renderCodeEditorView();
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Cartridge Properties")) {
                renderMetadataView();
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }
    }

    private void renderCodeEditorView() {
        float totalHeight = ImGui.getContentRegionAvailY();
        float bottomConsoleHeight = Math.max(160.0f, totalHeight * 0.32f);
        float editorHeight = totalHeight - bottomConsoleHeight - 12.0f;

        if (openTabs.isEmpty()) {
            ImGui.beginChild("EmptyEditorPlaceholder", 0.0f, editorHeight, true);
            ImGui.spacing();
            ImGui.textDisabled("No script open. Select a file from the explorer or click '+ New' to create one.");
            ImGui.endChild();
        } else {
            if (ImGui.beginTabBar("OpenTabsBar", ImGuiTabBarFlags.Reorderable | ImGuiTabBarFlags.AutoSelectNewTabs)) {
                for (int i = 0; i < openTabs.size(); i++) {
                    OpenTab tab = openTabs.get(i);
                    String label = tab.getFileName() + (tab.isDirty ? " *###tab_" : "###tab_") + tab.path.toString();
                    ImBoolean keepOpen = new ImBoolean(true);

                    if (ImGui.beginTabItem(label, keepOpen)) {
                        activeTabIndex = i;

                        ImGui.beginChild("EditorContainer", 0.0f, editorHeight - 34.0f, false);
                        int inputFlags = ImGuiInputTextFlags.AllowTabInput;
                        if (ImGui.inputTextMultiline("##source_code", tab.content, -1.0f, -1.0f, inputFlags)) {
                            tab.isDirty = !tab.content.get().equals(tab.lastSavedContent);
                        }
                        ImGui.endChild();

                        renderEditorStatusBar(tab);

                        ImGui.endTabItem();
                    }

                    if (!keepOpen.get()) {
                        if (tab.isDirty) {
                            saveCurrentTab();
                        }
                        openTabs.remove(i);
                        if (activeTabIndex >= openTabs.size()) {
                            activeTabIndex = Math.max(0, openTabs.size() - 1);
                        }
                        break;
                    }
                }
                ImGui.endTabBar();
            }
        }

        ImGui.separator();
        ImGui.beginChild("BottomConsoleChild", 0.0f, 0.0f, true);
        renderConsolePanel();
        ImGui.endChild();
    }

    private void renderEditorStatusBar(OpenTab tab) {
        ImGui.separator();
        int lineCount = tab.content.get().split("\n", -1).length;
        int charCount = tab.content.get().length();

        ImGui.textDisabled("Lines: " + lineCount + " | Chars: " + charCount);
        ImGui.sameLine();
        if (tab.isDirty) {
            ImGui.textColored(1.0f, 0.7f, 0.2f, 1.0f, "[MODIFIED]");
        } else {
            ImGui.textColored(0.4f, 0.9f, 0.4f, 1.0f, "[CLEAN]");
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 180.0f);
        if (ImGui.button("Check Syntax", 95.0f, 20.0f)) {
            validateSyntax(tab);
        }
        ImGui.sameLine();
        if (ImGui.button("Save (Ctrl+S)", 95.0f, 20.0f)) {
            saveCurrentTab();
        }
    }

    private void renderConsolePanel() {
        if (ImGui.beginTabBar("ConsoleTabs", ImGuiTabBarFlags.None)) {

            if (ImGui.beginTabItem("Console Log")) {
                if (ImGui.button("Clear Output", 90.0f, 20.0f)) {
                    terminalLogs.clear();
                }
                ImGui.sameLine();
                ImGui.checkbox("Auto-scroll", autoScrollLogs);

                ImGui.separator();
                ImGui.beginChild("LogTextScrolling", 0.0f, 0.0f, false);
                for (LogEntry entry : terminalLogs) {
                    ImGui.textDisabled("[" + entry.time + "]");
                    ImGui.sameLine();
                    ImGui.textColored((entry.color >> 16 & 0xFF) / 255.0f,
                                      (entry.color >> 8 & 0xFF) / 255.0f,
                                      (entry.color & 0xFF) / 255.0f,
                                      1.0f,
                                      "[" + entry.level + "]");
                    ImGui.sameLine();
                    ImGui.textUnformatted(entry.message);
                }
                if (autoScrollLogs && ImGui.getScrollY() >= ImGui.getScrollMaxY() - 20.0f) {
                    ImGui.setScrollHereY(1.0f);
                }
                ImGui.endChild();

                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Lua REPL")) {
                ImGui.textDisabled("Execute test expressions directly in the sandboxed Lua environment:");

                boolean execute = false;
                ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 85.0f);
                if (ImGui.inputText("##repl_input", replInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    execute = true;
                }
                ImGui.popItemWidth();

                ImGui.sameLine();
                if (ImGui.button("Execute", 75.0f, 22.0f)) {
                    execute = true;
                }

                if (execute) {
                    runRepl(replInput.get());
                    replInput.set("");
                }

                ImGui.separator();
                ImGui.beginChild("ReplOutputScroll", 0.0f, 0.0f, false);
                for (LogEntry entry : terminalLogs) {
                    if ("INPUT".equals(entry.level) || "RESULT".equals(entry.level) || "ERROR".equals(entry.level)) {
                        ImGui.textColored((entry.color >> 16 & 0xFF) / 255.0f,
                                          (entry.color >> 8 & 0xFF) / 255.0f,
                                          (entry.color & 0xFF) / 255.0f,
                                          1.0f,
                                          entry.message);
                    }
                }
                ImGui.setScrollHereY(1.0f);
                ImGui.endChild();

                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }
    }

    private void renderMetadataView() {
        ImGui.spacing();
        ImGui.textColored(0.4f, 0.85f, 1.0f, 1.0f, "CARTRIDGE MANIFEST & CONFIGURATION");
        ImGui.textDisabled("Edit package properties stored in metadata.json:");
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.text("Package Title:");
        ImGui.inputText("##meta_title", metaTitle);

        ImGui.spacing();
        ImGui.text("Author:");
        ImGui.inputText("##meta_author", metaAuthor);

        ImGui.spacing();
        ImGui.text("Version:");
        ImGui.inputText("##meta_version", metaVersion);

        ImGui.spacing();
        ImGui.text("Description:");
        ImGui.inputTextMultiline("##meta_desc", metaDescription, -1.0f, 120.0f, 0);

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        if (ImGui.button("Save Metadata Changes", 200.0f, 30.0f)) {
            saveMetadataChanges();
        }
    }

    private void renderModals() {
        if (openNewFileModal) {
            ImGui.openPopup("Create New Script##NewFileModal");
            openNewFileModal = false;
        }

        if (ImGui.beginPopupModal("Create New Script##NewFileModal", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Enter file name for new Lua script:");
            ImGui.spacing();

            boolean create = false;
            if (ImGui.inputText("##new_file_name", newFileNameInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
                create = true;
            }

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            if (ImGui.button("Create", 120.0f, 26.0f) || create) {
                createNewFile(newFileNameInput.get());
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120.0f, 26.0f)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    @Override
    public void onClose() {
        saveAll();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), xpos, ypos);
            ImGuiManager.getInstance().onMouseMove(mc.getWindow().getWindow(), xpos[0], ypos[0]);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            ImGuiManager.getInstance().onMouseClick(mc.getWindow().getWindow(), button, GLFW.GLFW_PRESS, 0);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            ImGuiManager.getInstance().onMouseClick(mc.getWindow().getWindow(), button, GLFW.GLFW_RELEASE, 0);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ImGuiManager.getInstance().onMouseScroll(scrollX, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || hasControlDown();
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            saveCurrentTab();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            if (activeTabIndex >= 0 && activeTabIndex < openTabs.size()) {
                validateSyntax(openTabs.get(activeTabIndex));
            }
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            ImGuiManager.getInstance().onKey(mc.getWindow().getWindow(), keyCode, scanCode, GLFW.GLFW_PRESS, modifiers);
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            ImGuiManager.getInstance().onKey(mc.getWindow().getWindow(), keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            ImGuiManager.getInstance().onChar(mc.getWindow().getWindow(), codePoint);
        }
        return true;
    }
}
