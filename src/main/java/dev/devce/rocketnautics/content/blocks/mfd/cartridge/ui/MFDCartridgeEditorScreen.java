package dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui;

import dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager.CartridgeMetadata;
import dev.devce.websnodelib.api.elements.WCodeArea;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

public class MFDCartridgeEditorScreen extends Screen {

    private final String cartridgeId;
    private final InteractionHand hand;
    private final WCodeArea codeArea;

    private Path currentFilePath;
    private final List<Path> cartridgeFiles = new ArrayList<>();
    private final List<Path> openTabs = new ArrayList<>();

    private boolean isMetadataView = false;
    private CartridgeMetadata metadata;
    private int selectedMetaField = -1;

    private int rightPanelWidth = 150;
    private int terminalHeight = 90;
    private boolean isDraggingVerticalSplitter = false;
    private boolean isDraggingHorizontalSplitter = false;

    private static final int TOP_BAR_H = 26;
    private static final int STATUS_BAR_H = 18;
    private static final int SPLITTER_THICKNESS = 3;

    private static final int BG_MAIN = 0xFF0D0D0D;
    private static final int BG_DARK = 0xFF080808;
    private static final int BG_PANEL = 0xFF111111;
    private static final int BG_HEADER = 0xFF141414;
    private static final int BG_ELEM = 0xFF1A1A1A;
    private static final int BG_ELEM_HOVER = 0xFF252525;
    private static final int ACCENT_GREEN = 0xFF00FF88;
    private static final int ACCENT_CYAN = 0xFF00FFCC;
    private static final int ACCENT_ORANGE = 0xFFFFAA00;
    private static final int ACCENT_RED = 0xFFFF5555;
    private static final int BORDER_MUTED = 0xFF1F1F1F;
    private static final int TEXT_MUTED = 0xFF888888;
    private static final int TEXT_MAIN = 0xFFD0D0D0;

    public record TerminalEntry(String time, String level, String message, int color) {}
    private final List<TerminalEntry> terminalLogs = new ArrayList<>();
    private int terminalTab = 0;
    private String terminalInput = "";
    private boolean terminalInputFocused = false;
    private int terminalScrollOffset = 0;

    private boolean isSaved = true;

    public MFDCartridgeEditorScreen(String cartridgeId, InteractionHand hand) {
        super(Component.literal("Rocketnautics IDE - " + cartridgeId));
        this.cartridgeId = cartridgeId;
        this.hand = hand;
        this.codeArea = new WCodeArea(0, 0);

        this.metadata = CartridgeManager.getMetadata(cartridgeId);

        Path dir = CartridgeManager.getCartridgeDir(cartridgeId);
        this.currentFilePath = dir.resolve("main.lua");
        openTabs.add(currentFilePath);

        logTerminal("INFO", "Initialized Console IDE for cartridge: " + cartridgeId, ACCENT_GREEN);
        logTerminal("INFO", "Path: " + dir.toAbsolutePath(), TEXT_MUTED);

        loadFile(currentFilePath);
        refreshFileList();
    }

    private void logTerminal(String level, String msg, int color) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        terminalLogs.add(new TerminalEntry(time, level, msg, color));
        if (terminalLogs.size() > 200) {
            terminalLogs.remove(0);
        }
    }

    private void refreshFileList() {
        cartridgeFiles.clear();
        Path dir = CartridgeManager.getCartridgeDir(cartridgeId);
        if (Files.exists(dir)) {
            try (Stream<Path> stream = Files.walk(dir, 3)) {
                stream.filter(Files::isRegularFile).forEach(cartridgeFiles::add);
            } catch (IOException e) {
                logTerminal("ERROR", "Failed to list files: " + e.getMessage(), ACCENT_RED);
            }
        }
    }

    private void loadFile(Path path) {
        this.isMetadataView = false;
        this.currentFilePath = path;
        if (!openTabs.contains(path)) {
            openTabs.add(path);
        }
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                this.codeArea.setValue(content);
                this.isSaved = true;
                logTerminal("INFO", "Loaded " + path.getFileName() + " (" + content.length() + " bytes)", TEXT_MUTED);
            } catch (IOException e) {
                this.codeArea.setValue("-- Error loading file: " + e.getMessage());
                logTerminal("ERROR", "Load error: " + e.getMessage(), ACCENT_RED);
            }
        }
    }

    private void saveCurrentFile() {
        if (isMetadataView) {
            CartridgeManager.saveMetadata(cartridgeId, metadata);
            isSaved = true;
            logTerminal("SUCCESS", "Saved metadata.json (v" + metadata.version + ")", ACCENT_GREEN);
            return;
        }

        if (currentFilePath != null) {
            try {
                Files.createDirectories(currentFilePath.getParent());
                Files.writeString(currentFilePath, codeArea.getValue());
                isSaved = true;
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                logTerminal("SUCCESS", "Saved " + currentFilePath.getFileName() + " at " + time, ACCENT_GREEN);
            } catch (IOException e) {
                logTerminal("ERROR", "Save failed: " + e.getMessage(), ACCENT_RED);
            }
        }
    }

    private void createNewFile() {
        Path dir = CartridgeManager.getCartridgeDir(cartridgeId);
        int idx = 1;
        Path newFile = dir.resolve("script_" + idx + ".lua");
        while (Files.exists(newFile)) {
            idx++;
            newFile = dir.resolve("script_" + idx + ".lua");
        }
        try {
            Files.writeString(newFile, "local M = {}\n\nfunction M.example()\nend\n\nreturn M\n");
            saveCurrentFile();
            refreshFileList();
            loadFile(newFile);
            logTerminal("INFO", "Created file " + newFile.getFileName(), ACCENT_GREEN);
        } catch (IOException e) {
            logTerminal("ERROR", "File creation failed: " + e.getMessage(), ACCENT_RED);
        }
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
    }

    private void updateLayout() {
        int editorW = Math.max(120, width - rightPanelWidth - SPLITTER_THICKNESS);
        int editorH = Math.max(60, height - TOP_BAR_H - terminalHeight - SPLITTER_THICKNESS - STATUS_BAR_H);
        codeArea.setWidth(editorW);
        codeArea.setHeight(editorH);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int editorW = Math.max(120, width - rightPanelWidth - SPLITTER_THICKNESS);
        int editorH = Math.max(60, height - TOP_BAR_H - terminalHeight - SPLITTER_THICKNESS - STATUS_BAR_H);
        int terminalY = TOP_BAR_H + editorH + SPLITTER_THICKNESS;
        int terminalW = editorW;
        int panelX = width - rightPanelWidth;

        graphics.fill(0, 0, width, height, BG_MAIN);

        graphics.fill(0, 0, width, TOP_BAR_H, BG_HEADER);
        graphics.fill(0, TOP_BAR_H - 1, width, TOP_BAR_H, BORDER_MUTED);

        graphics.drawString(font, "> " + cartridgeId, 10, 8, ACCENT_GREEN, false);

        int tabX = 90;

        int metaTabW = font.width("Metadata") + 22;
        boolean hoverMeta = mouseX >= tabX && mouseX <= tabX + metaTabW && mouseY >= 3 && mouseY <= TOP_BAR_H - 1;
        graphics.fill(tabX, 3, tabX + metaTabW, TOP_BAR_H - 1, isMetadataView ? BG_ELEM : (hoverMeta ? BG_ELEM_HOVER : BG_DARK));
        if (isMetadataView) {
            graphics.fill(tabX, 3, tabX + metaTabW, 4, ACCENT_GREEN);
            graphics.renderOutline(tabX, 3, metaTabW, TOP_BAR_H - 4, ACCENT_GREEN);
        }
        graphics.drawString(font, "⚙ Metadata", tabX + 6, 9, isMetadataView ? ACCENT_GREEN : TEXT_MUTED, false);
        tabX += metaTabW + 4;

        for (Path tabPath : openTabs) {
            String tabName = tabPath.getFileName().toString();
            boolean isActive = !isMetadataView && tabPath.equals(currentFilePath);
            int nameW = font.width(tabName);
            int tabW = nameW + 36;

            int tabBg = isActive ? BG_ELEM : BG_DARK;
            graphics.fill(tabX, 3, tabX + tabW, TOP_BAR_H - 1, tabBg);
            if (isActive) {
                graphics.fill(tabX, 3, tabX + tabW, 4, ACCENT_GREEN);
                graphics.renderOutline(tabX, 3, tabW, TOP_BAR_H - 4, ACCENT_GREEN);
            }

            int tabTextCol = isActive ? 0xFFFFFFFF : TEXT_MUTED;
            graphics.drawString(font, tabName, tabX + 8, 9, tabTextCol, false);

            if (isActive && !isSaved) {
                graphics.drawString(font, "●", tabX + 8 + nameW + 3, 9, ACCENT_ORANGE, false);
            }

            int closeX = tabX + tabW - 14;
            boolean hoverClose = mouseX >= closeX - 2 && mouseX <= closeX + 10 && mouseY >= 7 && mouseY <= 17;
            if (hoverClose) {
                graphics.fill(closeX - 2, 7, closeX + 10, 18, 0x44FF5555);
            }
            graphics.drawString(font, "×", closeX + 1, 8, hoverClose ? ACCENT_RED : 0xFF666666, false);

            tabX += tabW + 4;
        }

        int btnY = 4;
        int btnH = 17;

        int saveW = 50;
        int saveX = width - rightPanelWidth - 130;
        boolean hoverSave = mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(saveX, btnY, saveX + saveW, btnY + btnH, hoverSave ? ACCENT_GREEN : BG_ELEM);
        graphics.renderOutline(saveX, btnY, saveW, btnH, hoverSave ? ACCENT_GREEN : BORDER_MUTED);
        graphics.drawString(font, "Save", saveX + 13, btnY + 5, hoverSave ? 0xFF000000 : ACCENT_GREEN, false);

        int expW = 68;
        int expX = width - rightPanelWidth - 74;
        boolean hoverExp = mouseX >= expX && mouseX <= expX + expW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(expX, btnY, expX + expW, btnY + btnH, hoverExp ? ACCENT_GREEN : BG_ELEM);
        graphics.renderOutline(expX, btnY, expW, btnH, hoverExp ? ACCENT_GREEN : BORDER_MUTED);
        graphics.drawString(font, "Explorer", expX + 11, btnY + 5, hoverExp ? 0xFF000000 : TEXT_MAIN, false);

        if (isMetadataView) {
            renderMetadataEditor(graphics, 0, TOP_BAR_H, editorW, editorH);
        } else {
            codeArea.render(graphics, 0, TOP_BAR_H, mouseX, mouseY, partialTick);
        }

        int hSplitterY = TOP_BAR_H + editorH;
        boolean hoverHSplitter = mouseX < panelX && mouseY >= hSplitterY - 2 && mouseY <= hSplitterY + SPLITTER_THICKNESS + 2;
        graphics.fill(0, hSplitterY, editorW, hSplitterY + SPLITTER_THICKNESS, (hoverHSplitter || isDraggingHorizontalSplitter) ? ACCENT_GREEN : BORDER_MUTED);

        graphics.fill(0, terminalY, terminalW, terminalY + terminalHeight, BG_DARK);

        graphics.fill(0, terminalY, terminalW, terminalY + 16, BG_HEADER);
        graphics.fill(0, terminalY + 15, terminalW, terminalY + 16, BORDER_MUTED);

        boolean actTerm = terminalTab == 0;
        graphics.drawString(font, "> TERMINAL", 10, terminalY + 4, actTerm ? ACCENT_GREEN : TEXT_MUTED, false);
        if (actTerm) graphics.fill(10, terminalY + 14, 68, terminalY + 15, ACCENT_GREEN);

        boolean actProb = terminalTab == 1;
        graphics.drawString(font, "> PROBLEMS", 80, terminalY + 4, actProb ? ACCENT_GREEN : TEXT_MUTED, false);
        if (actProb) graphics.fill(80, terminalY + 14, 138, terminalY + 15, ACCENT_GREEN);

        int clearX = terminalW - 40;
        boolean hoverClear = mouseX >= clearX && mouseX <= clearX + 34 && mouseY >= terminalY + 2 && mouseY <= terminalY + 14;
        graphics.drawString(font, "Clear", clearX, terminalY + 4, hoverClear ? ACCENT_RED : TEXT_MUTED, false);

        int logY = terminalY + 19;
        int maxLogsVisible = Math.max(1, (terminalHeight - 34) / 10);
        int startLogIdx = Math.max(0, terminalLogs.size() - maxLogsVisible - terminalScrollOffset);
        for (int i = startLogIdx; i < terminalLogs.size() && logY < terminalY + terminalHeight - 14; i++) {
            TerminalEntry entry = terminalLogs.get(i);
            graphics.drawString(font, entry.time(), 8, logY, 0xFF444444, false);
            graphics.drawString(font, "[" + entry.level() + "]", 58, logY, entry.color(), false);
            graphics.drawString(font, entry.message(), 108, logY, TEXT_MAIN, false);
            logY += 10;
        }

        int inputBarY = terminalY + terminalHeight - 13;
        graphics.fill(0, inputBarY - 1, terminalW, inputBarY, BORDER_MUTED);
        graphics.drawString(font, "lua>", 8, inputBarY + 3, ACCENT_GREEN, false);
        graphics.drawString(font, terminalInput + (terminalInputFocused && (System.currentTimeMillis() % 1000 < 500) ? "_" : ""), 36, inputBarY + 3, 0xFFFFFFFF, false);

        int vSplitterX = panelX - SPLITTER_THICKNESS;
        boolean hoverVSplitter = mouseX >= vSplitterX - 2 && mouseX <= vSplitterX + SPLITTER_THICKNESS + 2 && mouseY >= TOP_BAR_H;
        graphics.fill(vSplitterX, TOP_BAR_H, panelX, height - STATUS_BAR_H, (hoverVSplitter || isDraggingVerticalSplitter) ? ACCENT_GREEN : BORDER_MUTED);

        graphics.fill(panelX, TOP_BAR_H, width, height - STATUS_BAR_H, BG_PANEL);

        graphics.fill(panelX, TOP_BAR_H, width, TOP_BAR_H + 18, BG_HEADER);
        graphics.fill(panelX, TOP_BAR_H + 17, width, TOP_BAR_H + 18, BORDER_MUTED);
        graphics.drawString(font, "> EXPLORER", panelX + 6, TOP_BAR_H + 5, TEXT_MUTED, false);

        int newBtnX = width - 44;
        int newBtnY = TOP_BAR_H + 2;
        boolean hoverNew = mouseX >= newBtnX && mouseX <= newBtnX + 38 && mouseY >= newBtnY && mouseY <= newBtnY + 13;
        graphics.fill(newBtnX, newBtnY, newBtnX + 38, newBtnY + 13, hoverNew ? ACCENT_GREEN : BG_ELEM);
        graphics.renderOutline(newBtnX, newBtnY, 38, 13, hoverNew ? ACCENT_GREEN : BORDER_MUTED);
        graphics.drawString(font, "+ File", newBtnX + 4, newBtnY + 3, hoverNew ? 0xFF000000 : ACCENT_GREEN, false);

        int itemY = TOP_BAR_H + 24;
        for (Path file : cartridgeFiles) {
            String fname = file.getFileName().toString();
            boolean isCur = !isMetadataView && file.equals(currentFilePath);
            boolean hover = mouseX >= panelX && mouseX <= width && mouseY >= itemY && mouseY <= itemY + 14;

            if (isCur) {
                graphics.fill(panelX, itemY, width, itemY + 14, BG_ELEM);
                graphics.fill(panelX, itemY, panelX + 2, itemY + 14, ACCENT_GREEN);
            } else if (hover) {
                graphics.fill(panelX, itemY, width, itemY + 14, BG_ELEM_HOVER);
            }

            String icon = fname.endsWith(".lua") ? "📄" : (fname.endsWith(".png") ? "🖼" : (fname.endsWith(".json") ? "⚙" : "📁"));
            graphics.drawString(font, icon + " " + fname, panelX + 6, itemY + 3, isCur ? ACCENT_GREEN : TEXT_MAIN, false);

            itemY += 15;
            if (itemY > height - STATUS_BAR_H - 18) break;
        }

        int statY = height - STATUS_BAR_H;
        graphics.fill(0, statY, width, height, BG_DARK);
        graphics.fill(0, statY, width, statY + 1, BORDER_MUTED);

        graphics.drawString(font, "● " + (isSaved ? "Saved" : "Unsaved changes"), 10, statY + 5, isSaved ? ACCENT_GREEN : ACCENT_ORANGE, false);

        String rightStatus = "Lua 5.2  |  UTF-8  |  64x64 Fantasy Console";
        graphics.drawString(font, rightStatus, width - font.width(rightStatus) - 10, statY + 5, TEXT_MUTED, false);
    }

    private void renderMetadataEditor(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, BG_MAIN);

        int startX = x + 24;
        int curY = y + 20;

        graphics.drawString(font, "> Cartridge Metadata [metadata.json]", startX, curY, ACCENT_GREEN, false);
        curY += 24;

        renderMetaField(graphics, "Title:", metadata.title, startX, curY, 220, selectedMetaField == 0);
        curY += 32;

        renderMetaField(graphics, "Author:", metadata.author, startX, curY, 220, selectedMetaField == 1);
        curY += 32;

        renderMetaField(graphics, "Version:", metadata.version, startX, curY, 100, selectedMetaField == 2);
        curY += 32;

        renderMetaField(graphics, "Description:", metadata.description, startX, curY, 320, selectedMetaField == 3);
        curY += 38;

        graphics.drawString(font, "Details appear in the cartridge item tooltip and game browser.", startX, curY, 0xFF444444, false);
    }

    private void renderMetaField(GuiGraphics graphics, String label, String value, int x, int y, int fieldW, boolean focused) {
        graphics.drawString(font, "> " + label, x, y + 4, focused ? ACCENT_GREEN : TEXT_MUTED, false);
        int inputX = x + 85;
        int inputY = y;
        int inputH = 16;

        graphics.fill(inputX, inputY, inputX + fieldW, inputY + inputH, BG_DARK);
        graphics.renderOutline(inputX, inputY, fieldW, inputH, focused ? ACCENT_GREEN : BORDER_MUTED);

        String display = value + (focused && (System.currentTimeMillis() % 1000 < 500) ? "_" : "");
        graphics.drawString(font, display, inputX + 6, inputY + 4, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int editorW = Math.max(120, width - rightPanelWidth - SPLITTER_THICKNESS);
        int editorH = Math.max(60, height - TOP_BAR_H - terminalHeight - SPLITTER_THICKNESS - STATUS_BAR_H);
        int hSplitterY = TOP_BAR_H + editorH;
        int panelX = width - rightPanelWidth;
        int vSplitterX = panelX - SPLITTER_THICKNESS;
        int terminalY = TOP_BAR_H + editorH + SPLITTER_THICKNESS;

        if (mouseX < panelX && mouseY >= hSplitterY - 2 && mouseY <= hSplitterY + SPLITTER_THICKNESS + 2) {
            isDraggingHorizontalSplitter = true;
            return true;
        }

        if (mouseX >= vSplitterX - 2 && mouseX <= vSplitterX + SPLITTER_THICKNESS + 2 && mouseY >= TOP_BAR_H) {
            isDraggingVerticalSplitter = true;
            return true;
        }

        int saveW = 50;
        int saveX = width - rightPanelWidth - 130;
        if (mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= 4 && mouseY <= 21) {
            saveCurrentFile();
            return true;
        }

        int expW = 68;
        int expX = width - rightPanelWidth - 74;
        if (mouseX >= expX && mouseX <= expX + expW && mouseY >= 4 && mouseY <= 21) {
            Path dir = CartridgeManager.getCartridgeDir(cartridgeId);
            Util.getPlatform().openFile(dir.toFile());
            logTerminal("INFO", "Revealed in Explorer: " + dir.getFileName(), TEXT_MUTED);
            return true;
        }

        int tabX = 90;

        int metaTabW = font.width("Metadata") + 22;
        if (mouseY >= 3 && mouseY <= TOP_BAR_H - 1 && mouseX >= tabX && mouseX <= tabX + metaTabW) {
            isMetadataView = true;
            selectedMetaField = -1;
            return true;
        }
        tabX += metaTabW + 4;

        for (int t = 0; t < openTabs.size(); t++) {
            Path tabPath = openTabs.get(t);
            int nameW = font.width(tabPath.getFileName().toString());
            int tabW = nameW + 36;
            if (mouseY >= 3 && mouseY <= TOP_BAR_H - 1 && mouseX >= tabX && mouseX <= tabX + tabW) {
                int closeX = tabX + tabW - 14;
                if (mouseX >= closeX - 2 && mouseX <= closeX + 10) {
                    openTabs.remove(t);
                    if (openTabs.isEmpty()) {
                        openTabs.add(CartridgeManager.getCartridgeDir(cartridgeId).resolve("main.lua"));
                    }
                    if (tabPath.equals(currentFilePath)) {
                        loadFile(openTabs.get(Math.max(0, openTabs.size() - 1)));
                    }
                    return true;
                }
                loadFile(tabPath);
                return true;
            }
            tabX += tabW + 4;
        }

        if (isMetadataView && mouseX < editorW && mouseY >= TOP_BAR_H && mouseY < hSplitterY) {
            int startX = 24 + 85;
            int curY = TOP_BAR_H + 44;
            if (mouseX >= startX && mouseX <= startX + 220 && mouseY >= curY && mouseY <= curY + 16) {
                selectedMetaField = 0;
                return true;
            }
            curY += 32;
            if (mouseX >= startX && mouseX <= startX + 220 && mouseY >= curY && mouseY <= curY + 16) {
                selectedMetaField = 1;
                return true;
            }
            curY += 32;
            if (mouseX >= startX && mouseX <= startX + 100 && mouseY >= curY && mouseY <= curY + 16) {
                selectedMetaField = 2;
                return true;
            }
            curY += 32;
            if (mouseX >= startX && mouseX <= startX + 320 && mouseY >= curY && mouseY <= curY + 16) {
                selectedMetaField = 3;
                return true;
            }
            selectedMetaField = -1;
            return true;
        }

        if (mouseY >= terminalY && mouseY <= terminalY + terminalHeight) {
            if (mouseX >= 10 && mouseX <= 70 && mouseY <= terminalY + 16) {
                terminalTab = 0;
                return true;
            }
            if (mouseX >= 80 && mouseX <= 140 && mouseY <= terminalY + 16) {
                terminalTab = 1;
                return true;
            }
            int clearX = editorW - 40;
            if (mouseX >= clearX && mouseX <= clearX + 34 && mouseY <= terminalY + 16) {
                terminalLogs.clear();
                return true;
            }
            if (mouseY >= terminalY + terminalHeight - 15) {
                terminalInputFocused = true;
                return true;
            }
            terminalInputFocused = false;
        } else {
            terminalInputFocused = false;
        }

        if (mouseX >= panelX && mouseY >= TOP_BAR_H) {
            int newBtnX = width - 44;
            int newBtnY = TOP_BAR_H + 2;
            if (mouseX >= newBtnX && mouseX <= newBtnX + 38 && mouseY >= newBtnY && mouseY <= newBtnY + 13) {
                createNewFile();
                return true;
            }

            int itemY = TOP_BAR_H + 24;
            for (Path file : cartridgeFiles) {
                if (mouseY >= itemY && mouseY <= itemY + 14) {
                    saveCurrentFile();
                    if (file.getFileName().toString().equals("metadata.json")) {
                        isMetadataView = true;
                    } else {
                        loadFile(file);
                    }
                    return true;
                }
                itemY += 15;
            }
            return true;
        }

        if (!isMetadataView && mouseX < editorW && mouseY >= TOP_BAR_H && mouseY < hSplitterY) {
            codeArea.handleMouseClick(mouseX, mouseY - TOP_BAR_H, button);
            isSaved = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingVerticalSplitter = false;
        isDraggingHorizontalSplitter = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingVerticalSplitter) {
            rightPanelWidth = Math.max(100, Math.min(width - 150, width - (int) mouseX));
            updateLayout();
            return true;
        }
        if (isDraggingHorizontalSplitter) {
            terminalHeight = Math.max(40, Math.min(height - 120, height - (int) mouseY - STATUS_BAR_H));
            updateLayout();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int editorW = Math.max(120, width - rightPanelWidth - SPLITTER_THICKNESS);
        int editorH = Math.max(60, height - TOP_BAR_H - terminalHeight - SPLITTER_THICKNESS - STATUS_BAR_H);
        int terminalY = TOP_BAR_H + editorH + SPLITTER_THICKNESS;

        if (mouseY >= terminalY && mouseY <= terminalY + terminalHeight) {
            terminalScrollOffset = Math.max(0, terminalScrollOffset - (int) scrollY);
            return true;
        }

        if (!isMetadataView && mouseX < editorW && mouseY >= TOP_BAR_H && mouseY < terminalY) {
            codeArea.handleMouseScrolled(mouseX, mouseY - TOP_BAR_H, scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            saveCurrentFile();
            return true;
        }

        if (isMetadataView && selectedMetaField >= 0) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                switch (selectedMetaField) {
                    case 0 -> { if (!metadata.title.isEmpty()) metadata.title = metadata.title.substring(0, metadata.title.length() - 1); }
                    case 1 -> { if (!metadata.author.isEmpty()) metadata.author = metadata.author.substring(0, metadata.author.length() - 1); }
                    case 2 -> { if (!metadata.version.isEmpty()) metadata.version = metadata.version.substring(0, metadata.version.length() - 1); }
                    case 3 -> { if (!metadata.description.isEmpty()) metadata.description = metadata.description.substring(0, metadata.description.length() - 1); }
                }
                isSaved = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
                selectedMetaField = (selectedMetaField + 1) % 4;
                return true;
            }
            return true;
        }

        if (terminalInputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!terminalInput.trim().isEmpty()) {
                    logTerminal("LUA", "> " + terminalInput, ACCENT_GREEN);
                    try {
                        Globals g = JsePlatform.standardGlobals();
                        var res = g.load(terminalInput).call();
                        if (!res.isnil()) {
                            logTerminal("RESULT", res.tojstring(), 0xFFFFFF88);
                        }
                    } catch (LuaError e) {
                        logTerminal("ERROR", e.getMessage(), ACCENT_RED);
                    }
                    terminalInput = "";
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !terminalInput.isEmpty()) {
                terminalInput = terminalInput.substring(0, terminalInput.length() - 1);
                return true;
            }
            return true;
        }

        isSaved = false;
        return codeArea.handleKeyPress(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isMetadataView && selectedMetaField >= 0) {
            switch (selectedMetaField) {
                case 0 -> metadata.title += codePoint;
                case 1 -> metadata.author += codePoint;
                case 2 -> metadata.version += codePoint;
                case 3 -> metadata.description += codePoint;
            }
            isSaved = false;
            return true;
        }

        if (terminalInputFocused) {
            terminalInput += codePoint;
            return true;
        }
        isSaved = false;
        return codeArea.handleCharTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        Path assetsDir = CartridgeManager.getCartridgeDir(cartridgeId).resolve("assets");
        try {
            Files.createDirectories(assetsDir);
            for (Path p : paths) {
                if (Files.isRegularFile(p)) {
                    Path dest = assetsDir.resolve(p.getFileName());
                    Files.copy(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logTerminal("INFO", "Imported asset: " + p.getFileName(), ACCENT_GREEN);
                }
            }
            refreshFileList();
        } catch (IOException e) {
            logTerminal("ERROR", "Asset import error: " + e.getMessage(), ACCENT_RED);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
