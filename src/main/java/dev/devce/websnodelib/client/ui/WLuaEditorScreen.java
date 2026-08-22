package dev.devce.websnodelib.client.ui;

import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.elements.WCodeArea;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Super clean Lua code editor. No bars, no bloat.
 * Auto-compiles on close or when navigating back via breadcrumbs.
 */
public class WLuaEditorScreen extends Screen {

    private final WNode node;
    private final WNodeScreen parentScreen;
    private final WCodeArea codeArea;

    // Static side-channel for code storage
    public static final java.util.Map<java.util.UUID, String> NODE_CODE_MAP = new java.util.HashMap<>();
    public static final java.util.Map<java.util.UUID, Boolean> NODE_COMPILED_MAP = new java.util.HashMap<>();

    private static final int PADDING_TOP = 25;
    private static final int PADDING_SIDE = 0;
    private static final int CONSOLE_H = 60;

    private final List<String> consoleLines = new ArrayList<>();

    public WLuaEditorScreen(WNode node, WNodeScreen parentScreen) {
        super(Component.literal(node.getTitle()));
        this.node = node;
        this.parentScreen = parentScreen;
        this.codeArea = new WCodeArea(0, 0);
        
        String savedCode = node.getCustomData().getString("code");
        if (savedCode.isEmpty()) {
            savedCode = NODE_CODE_MAP.getOrDefault(node.getId(), "");
        }
        
        if (!savedCode.isEmpty()) {
            this.codeArea.setValue(savedCode);
        } else {
            this.codeArea.setValue("-- Lua Script\nlocal alt = input(\"Altitude\")\noutput(\"Double\", alt * 2)");
        }
    }

    private void log(String msg) {
        consoleLines.add(msg);
        if (consoleLines.size() > 5) consoleLines.remove(0);
    }

    @Override
    protected void init() {
        super.init();
        codeArea.setWidth(width);
        codeArea.setHeight(height - PADDING_TOP - CONSOLE_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Full screen background
        graphics.fill(0, 0, width, height, 0xFF0D0D0D);
        
        // Code Editor (No side padding)
        codeArea.render(graphics, 0, PADDING_TOP, mouseX, mouseY, partialTick);

        // Console Area (Pinned to bottom)
        int conY = height - CONSOLE_H;
        graphics.fill(0, conY, width, height, 0xFF090909);
        graphics.fill(0, conY, width, conY + 1, 0xFF1A1A1A);
        for (int i = 0; i < consoleLines.size(); i++) {
            graphics.drawString(font, consoleLines.get(i), 10, conY + 5 + i * 10, 0xFFAAAAAA, false);
        }

        // Run Button
        int runBtnX = width - 60;
        int runBtnY = 5;
        boolean hoverRun = mouseX >= runBtnX && mouseX <= runBtnX + 50 && mouseY >= runBtnY && mouseY <= runBtnY + 15;
        graphics.fill(runBtnX, runBtnY, runBtnX + 50, runBtnY + 15, hoverRun ? 0xFF00FF88 : 0xFF252525);
        graphics.drawString(font, "Run (►)", runBtnX + 5, runBtnY + 4, hoverRun ? 0xFF000000 : 0xFF00FF88, false);

        // Clickable Breadcrumbs
        renderBreadcrumbs(graphics);
    }

    private void renderBreadcrumbs(GuiGraphics graphics) {
        List<String> path = getPathNames();
        int x = 10;
        int y = 10;
        for (int i = 0; i < path.size(); i++) {
            String text = path.get(i);
            boolean isLast = (i == path.size() - 1);
            int color = isLast ? 0xFF00FF88 : 0xFFAAAAAA;
            graphics.drawString(font, text, x, y, color, true);
            x += font.width(text);
            if (!isLast) {
                graphics.drawString(font, " / ", x, y, 0xFFAAAAAA, true);
                x += font.width(" / ");
            }
        }
    }

    private List<String> getPathNames() {
        List<String> names = new ArrayList<>();
        names.add(node.getTitle());
        WNodeScreen p = parentScreen;
        while (p != null) {
            names.add(0, p.getParentScreen() == null ? "~" : p.getTitle().getString());
            p = p.getParentScreen();
        }
        return names;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= 8 && mouseY <= 18) {
            List<Screen> screens = new ArrayList<>();
            screens.add(this);
            WNodeScreen p = parentScreen;
            while (p != null) {
                screens.add(0, p);
                p = p.getParentScreen();
            }

            int bcX = 10;
            for (Screen s : screens) {
                String text = (s instanceof WNodeScreen ns && ns.getParentScreen() == null) ? "~" : 
                             (s instanceof WLuaEditorScreen le ? le.node.getTitle() : ((WNodeScreen)s).getTitle().getString());
                int w = font.width(text);
                if (mouseX >= bcX && mouseX <= bcX + w) {
                    if (s != this) {
                        onClose(); // Compile and save
                        minecraft.setScreen(s);
                    }
                    return true;
                }
                bcX += w + font.width(" / ");
            }
        }

        if (button == 0) {
            int runBtnX = width - 60;
            int runBtnY = 5;
            if (mouseX >= runBtnX && mouseX <= runBtnX + 50 && mouseY >= runBtnY && mouseY <= runBtnY + 15) {
                // Save code
                node.getCustomData().putString("code", codeArea.getValue());
                // Force evaluator to reload and run
                node.evaluate();
                
                // Check errors
                boolean hasErr = node.getCustomData().getBoolean("err") || node.getCustomData().getBoolean("failed");
                String errMsg = node.getCustomData().getString("errMsg");
                
                if (hasErr) {
                    log("§c[Error] " + (errMsg.isEmpty() ? "Compilation failed" : errMsg));
                } else {
                    log("§a[Success] Evaluated successfully.");
                }
                return true;
            }
        }

        if (codeArea.handleMouseClick(mouseX - PADDING_SIDE, mouseY - PADDING_TOP, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (codeArea.handleMouseScrolled(mouseX - PADDING_SIDE, mouseY - PADDING_TOP, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // Ctrl+Enter = compile, Ctrl+R = check syntax
        if (Screen.hasControlDown()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                doCompile();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
                checkSyntax();
                return true;
            }
        }
        return codeArea.handleKeyPress(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return codeArea.handleCharTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    private void checkSyntax() {
        try {
            dev.devce.rocketnautics.lua.LuaSandbox.createSandboxedGlobals().load(codeArea.getValue());
            node.getCustomData().putBoolean("err", false);
            log("§aSyntax OK");
        } catch (org.luaj.vm2.LuaError e) {
            node.getCustomData().putBoolean("err", true);
            log("§c" + e.getMessage());
        }
    }

    private void doCompile() {
        String code = codeArea.getValue();
        NODE_CODE_MAP.put(node.getId(), code);
        node.getCustomData().putString("code", code);
        
        // Update failed state
        try {
            dev.devce.rocketnautics.lua.LuaSandbox.createSandboxedGlobals().load(code);
            node.getCustomData().putBoolean("err", false);
        } catch (Exception e) {
            node.getCustomData().putBoolean("err", true);
        }

        List<String> inNames = new ArrayList<>();
        List<String> outNames = new ArrayList<>();
        Pattern pinPat = Pattern.compile("(input|output)\\(\\s*[\"']([^\"']+)[\"']");
        
        for (String line : code.split("\n")) {
            String codeOnly = line;
            int idx = line.indexOf("--");
            if (idx != -1) codeOnly = line.substring(0, idx);
            Matcher m = pinPat.matcher(codeOnly);
            while (m.find()) {
                String kind = m.group(1), name = m.group(2);
                if (kind.equals("input") && !inNames.contains(name)) inNames.add(name);
                if (kind.equals("output") && !outNames.contains(name)) outNames.add(name);
            }
        }

        node.clearInputs();
        node.clearOutputs();
        for (String n : inNames) node.addInput(n, 0xFFFFFFFF);
        for (String n : outNames) node.addOutput(n, 0xFF00FF88);
        NODE_COMPILED_MAP.put(node.getId(), true);
    }

    @Override
    public void onClose() {
        doCompile();
        minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
