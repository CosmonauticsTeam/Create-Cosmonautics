package dev.devce.rocketnautics.client.ui.imgui;

import dev.devce.rocketnautics.content.sputnik.model.SputnikGraph;
import dev.devce.rocketnautics.content.sputnik.network.SputnikSaveGraphPayload;
import dev.devce.rocketnautics.content.sputnik.storage.SputnikStorageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class SputnikImGuiScreen extends Screen {

    private final BlockPos pos;
    private final int sputnikId;
    private final SputnikGraph graph;

    public SputnikImGuiScreen(BlockPos pos, int sputnikId, SputnikGraph graph) {
        super(Component.literal("Sputnik Flight Computer"));
        this.pos = pos;
        this.sputnikId = sputnikId;
        this.graph = graph != null ? graph : SputnikGraph.createDefault();
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getSputnikId() {
        return sputnikId;
    }

    public SputnikGraph getGraph() {
        return graph;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderMenuBackground(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ImGuiManager.getInstance().renderScreen(() -> {
            dev.devce.rocketnautics.content.blocks.SputnikBlockEntity be = null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && pos != null && mc.level.getBlockEntity(pos) instanceof dev.devce.rocketnautics.content.blocks.SputnikBlockEntity sbe) {
                be = sbe;
            }
            SputnikNodeGraphPanel.render(graph, sputnikId, pos, be);
        });
    }

    @Override
    public void onClose() {
        if (pos != null && graph != null) {
            byte[] bytes = SputnikStorageManager.serializeToGzip(graph);
            PacketDistributor.sendToServer(new SputnikSaveGraphPayload(pos, sputnikId, bytes));
        }
        if (SputnikCustomTheme.get().isDirty()) {
            SputnikCustomTheme.get().saveToFile();
        }
        super.onClose();
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

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 || hasShiftDown();
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || hasControlDown();

        if (shift && keyCode == GLFW.GLFW_KEY_D) {
            SputnikNodeGraphPanel.duplicateSelected(graph);
            return true;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            SputnikNodeGraphPanel.saveGraphToServer(graph, sputnikId, pos);
            return true;
        }

        boolean wantTextInput = ImGuiManager.getInstance().isActive() && imgui.ImGui.getIO().getWantTextInput();
        if (!wantTextInput) {
            if (keyCode == GLFW.GLFW_KEY_X || keyCode == GLFW.GLFW_KEY_DELETE) {
                SputnikNodeGraphPanel.deleteSelected(graph);
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                SputnikNodeGraphPanel.selectAll(graph);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                graph.setZoom(Math.min(2.5f, graph.getZoom() * 1.15f));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                graph.setZoom(Math.max(0.3f, graph.getZoom() / 1.15f));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_HOME) {
                graph.setPanX(0.0f);
                graph.setPanY(0.0f);
                graph.setZoom(1.0f);
                return true;
            }
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
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() != null) {
            ImGuiManager.getInstance().onChar(mc.getWindow().getWindow(), codePoint);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
