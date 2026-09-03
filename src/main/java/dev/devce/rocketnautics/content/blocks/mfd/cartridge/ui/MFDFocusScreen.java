package dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui;

import dev.devce.rocketnautics.content.blocks.mfd.MFDInputManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MFDFocusScreen extends Screen {

    private final BlockPos mfdPos;
    private long windowHandle;

    public MFDFocusScreen(BlockPos mfdPos) {
        super(Component.literal("MFD Screen Focus"));
        this.mfdPos = mfdPos;
        MFDInputManager.setFocusedPos(mfdPos);
    }

    @Override
    protected void init() {
        super.init();
        windowHandle = minecraft.getWindow().getWindow();
        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        MFDInputManager.onKeyDown(keyCode, scanCode);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        MFDInputManager.onKeyUp(keyCode, scanCode);
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        if (windowHandle != 0) {
            GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
        MFDInputManager.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
