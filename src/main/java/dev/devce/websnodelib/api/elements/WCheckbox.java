package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;

public class WCheckbox extends WElement {
    private boolean checked;
    private String label;

    public WCheckbox(String label) {
        this.label = label;
        this.width = 100;
        this.height = 12;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        
        // Box
        graphics.fill(x, y + 1, x + 10, y + 11, 0xFF1A1A1A);
        graphics.renderOutline(x, y + 1, 10, 10, hovered ? 0xFF00FF88 : 0xFF444444);
        
        if (checked) {
            graphics.fill(x + 2, y + 3, x + 8, y + 9, 0xFF00FF88);
        }
        
        // Label
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, label, x + 15, y + 2, 0xFFCCCCCC);
    }

    @Override
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 0 && mouseX <= 10 && mouseY >= 1 && mouseY <= 11) {
            checked = !checked;
            return true;
        }
        return false;
    }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putBoolean("checked", checked);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("checked")) {
            this.checked = tag.getBoolean("checked");
        }
    }
}
