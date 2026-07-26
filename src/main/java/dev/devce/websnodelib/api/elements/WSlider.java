package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class WSlider extends WElement {
    private double value;
    private double min, max;
    private String label;
    private boolean dragging;

    public WSlider(String label, double min, double max, int width) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.width = width;
        this.height = 14;
        this.value = min;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        
        if (dragging) {
            double relX = (mouseX - x) / (double) width;
            value = min + Mth.clamp(relX, 0, 1) * (max - min);
        }

        // Background
        graphics.fill(x, y + 4, x + width, y + 10, 0xFF121212);
        graphics.renderOutline(x, y + 4, width, 6, 0xFF444444);
        
        // Fill
        int fillW = (int) ((value - min) / (max - min) * width);
        graphics.fill(x, y + 4, x + fillW, y + 10, 0xAA00FF88);
        
        // Knob
        graphics.fill(x + fillW - 2, y + 2, x + fillW + 2, y + 12, hovered || dragging ? 0xFFFFFFFF : 0xFF00FF88);
        
        // Label and Value
        String text = String.format("%s: %.2f", label, value);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, x, y - 8, 0xFF888888);
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putDouble("value", value);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("value")) {
            this.value = tag.getDouble("value");
        }
    }
}
