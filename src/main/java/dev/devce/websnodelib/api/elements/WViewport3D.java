package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class WViewport3D extends WElement {
    private float zoom = 1.0f;
    private float rotationX = 0f;
    private float rotationY = 0f;
    private float rotationZ = 0f;

    public record ModelEntry(ItemStack stack, Vector3f offset, Vector3f rotation, float scale) {}
    private final List<ModelEntry> models = new ArrayList<>();

    public WViewport3D(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
    }

    public void setGlobalRotation(float rx, float ry, float rz) {
        this.rotationX = rx;
        this.rotationY = ry;
        this.rotationZ = rz;
    }

    public void addModel(ItemStack stack, Vector3f offset, Vector3f rotation, float scale) {
        models.add(new ModelEntry(stack, offset, rotation, scale));
    }

    public void clear() {
        models.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        graphics.fill(x, y, x + width, y + height, 0x77000000);
        graphics.renderOutline(x, y, width, height, 0xFF445566);
    }
}
