package dev.devce.rocketnautics.client.ui.imgui;

import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ImGuiManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImGuiManager.class);
    private static ImGuiManager instance;

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    private final List<Runnable> renderHooks = new CopyOnWriteArrayList<>();

    private boolean initialized = false;
    private boolean open = false;
    private long windowHandle = 0;

    private ImGuiManager() {}

    public static synchronized boolean hasInstance() {
        return instance != null;
    }

    public static synchronized ImGuiManager getInstance() {
        if (instance == null) {
            instance = new ImGuiManager();
        }
        return instance;
    }

    public synchronized void init(long handle) {
        if (initialized || handle == 0) return;

        try {
            this.windowHandle = handle;
            ImGui.createContext();

            ImGuiIO io = ImGui.getIO();
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);

            setupTheme();

            imGuiGlfw.init(windowHandle, false);
            imGuiGl3.init("#version 150");

            initialized = true;
            LOGGER.info("[Cosmonautics] Dear ImGui successfully initialized!");
        } catch (Throwable t) {
            LOGGER.error("[Cosmonautics] Failed to initialize Dear ImGui", t);
        }
    }

    private void setupTheme() {
        ImGui.styleColorsDark();

        var style = ImGui.getStyle();
        style.setWindowRounding(8.0f);
        style.setFrameRounding(4.0f);
        style.setPopupRounding(4.0f);
        style.setScrollbarRounding(4.0f);
        style.setGrabRounding(4.0f);
        style.setTabRounding(4.0f);

        style.setWindowBorderSize(1.0f);
        style.setFrameBorderSize(1.0f);

        style.setColor(ImGuiCol.WindowBg, 0.07f, 0.09f, 0.14f, 0.94f);
        style.setColor(ImGuiCol.Border, 0.16f, 0.28f, 0.42f, 0.85f);
        style.setColor(ImGuiCol.FrameBg, 0.10f, 0.14f, 0.22f, 1.00f);
        style.setColor(ImGuiCol.FrameBgHovered, 0.15f, 0.22f, 0.35f, 1.00f);
        style.setColor(ImGuiCol.FrameBgActive, 0.20f, 0.32f, 0.50f, 1.00f);

        style.setColor(ImGuiCol.TitleBg, 0.08f, 0.12f, 0.20f, 1.00f);
        style.setColor(ImGuiCol.TitleBgActive, 0.12f, 0.20f, 0.34f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.06f, 0.08f, 0.12f, 0.75f);

        style.setColor(ImGuiCol.Button, 0.14f, 0.25f, 0.42f, 0.90f);
        style.setColor(ImGuiCol.ButtonHovered, 0.20f, 0.38f, 0.65f, 1.00f);
        style.setColor(ImGuiCol.ButtonActive, 0.15f, 0.48f, 0.85f, 1.00f);

        style.setColor(ImGuiCol.SliderGrab, 0.22f, 0.60f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.35f, 0.75f, 1.00f, 1.00f);

        style.setColor(ImGuiCol.Header, 0.14f, 0.24f, 0.38f, 0.80f);
        style.setColor(ImGuiCol.HeaderHovered, 0.22f, 0.36f, 0.58f, 0.90f);
        style.setColor(ImGuiCol.HeaderActive, 0.28f, 0.48f, 0.75f, 1.00f);

        style.setColor(ImGuiCol.Tab, 0.10f, 0.15f, 0.24f, 1.00f);
        style.setColor(ImGuiCol.TabHovered, 0.20f, 0.35f, 0.58f, 1.00f);
        style.setColor(ImGuiCol.TabActive, 0.15f, 0.28f, 0.46f, 1.00f);
    }

    public void addRenderHook(Runnable hook) {
        if (hook != null) {
            renderHooks.add(hook);
        }
    }

    public void removeRenderHook(Runnable hook) {
        if (hook != null) {
            renderHooks.remove(hook);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        updateCursorMode();
    }

    public void toggle() {
        setOpen(!open);
    }

    public void updateCursorMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null && mc.getWindow().getWindow() != 0) {
            long win = mc.getWindow().getWindow();
            if (open && mc.screen == null) {
                GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            } else if (!open && mc.screen == null) {
                GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        }
    }

    public void render() {
        if (!initialized) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null && mc.getWindow().getWindow() != 0) {
                init(mc.getWindow().getWindow());
            }
            if (!initialized) return;
        }

        if (!open) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        try {
            imGuiGlfw.newFrame();
            imGuiGl3.newFrame();
            ImGui.newFrame();

            for (Runnable hook : renderHooks) {
                try {
                    hook.run();
                } catch (Throwable t) {
                    LOGGER.error("[Cosmonautics] Error in ImGui render hook", t);
                }
            }

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } catch (Throwable t) {
            LOGGER.error("[Cosmonautics] Error in ImGuiManager.render()", t);
        }
    }

    public boolean isActive() {
        if (!initialized) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SputnikImGuiScreen || mc.screen instanceof dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui.MFDCartridgeEditorScreen) return true;
        return open && mc.screen == null;
    }

    public void renderScreen(Runnable renderAction) {
        if (!initialized) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null && mc.getWindow().getWindow() != 0) {
                init(mc.getWindow().getWindow());
            }
            if (!initialized) return;
        }

        try {
            imGuiGlfw.newFrame();
            imGuiGl3.newFrame();
            ImGui.newFrame();

            if (renderAction != null) {
                renderAction.run();
            }

            ImGui.render();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } catch (Throwable t) {
            LOGGER.error("[Cosmonautics] Error in ImGuiManager.renderScreen()", t);
        }
    }

    public boolean isWantCaptureMouse() {
        if (!isActive()) return false;
        return ImGui.getIO().getWantCaptureMouse();
    }

    public boolean isWantCaptureKeyboard() {
        if (!isActive()) return false;
        return ImGui.getIO().getWantCaptureKeyboard();
    }

    public void onMouseClick(long window, int button, int action, int mods) {
        if (!isActive()) return;
        imGuiGlfw.mouseButtonCallback(window, button, action, mods);
    }

    public void onMouseMove(long window, double xpos, double ypos) {
        if (!isActive()) return;
        imGuiGlfw.cursorPosCallback(window, xpos, ypos);
    }

    public void onMouseScroll(double xoffset, double yoffset) {
        if (!isActive()) return;
        imGuiGlfw.scrollCallback(this.windowHandle, xoffset, yoffset);
    }

    public void onKey(long window, int key, int scancode, int action, int mods) {
        if (!isActive()) return;
        imGuiGlfw.keyCallback(window, key, scancode, action, mods);
    }

    public void onChar(long window, int codepoint) {
        if (!isActive()) return;
        imGuiGlfw.charCallback(window, codepoint);
    }

    public void destroy() {
        if (!initialized) return;
        try {
            imGuiGl3.shutdown();
            imGuiGlfw.shutdown();
            ImGui.destroyContext();
            initialized = false;
            LOGGER.info("[Cosmonautics] Dear ImGui destroyed.");
        } catch (Throwable t) {
            LOGGER.error("[Cosmonautics] Error disposing ImGui", t);
        }
    }
}
