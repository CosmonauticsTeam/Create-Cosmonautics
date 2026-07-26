package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity;
import dev.devce.rocketnautics.network.VectorThrusterSyncPayload;
import dev.devce.websnodelib.client.ui.WItemSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class VectorThrusterGuiScreen extends Screen {
    private final VectorThrusterBlockEntity blockEntity;
    private final ItemStack[] frequencies1 = new ItemStack[6];
    private final ItemStack[] frequencies2 = new ItemStack[6];
    private static final Direction[] DIRECTIONS = Direction.values();

    public VectorThrusterGuiScreen(VectorThrusterBlockEntity blockEntity) {
        super(Component.literal("Vector Thruster Linked Receiver"));
        this.blockEntity = blockEntity;
        
        // Copy current frequencies from block entity
        for (int i = 0; i < 6; i++) {
            Direction dir = DIRECTIONS[i];
            frequencies1[i] = blockEntity.getFrequencyStack1(dir).copy();
            frequencies2[i] = blockEntity.getFrequencyStack2(dir).copy();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Flat transparent dark background tint instead of blurry shader
        g.fill(0, 0, width, height, 0x80000000);
        
        int w = 220;
        int h = 190;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        
        // Draw main Minecraft style dialog background (gray panel with borders)
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.renderOutline(x, y, w, h, 0xFFFFFFFF);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, 0xFF555555);
        
        // Draw title
        g.drawString(font, title, x + 10, y + 10, 0xFF404040, false);
        
        // Draw 6 sides with slots
        for (int i = 0; i < 6; i++) {
            Direction dir = DIRECTIONS[i];
            int rowY = y + 30 + i * 22;
            
            // Draw side name label (e.g. UP, DOWN...)
            g.drawString(font, dir.getName().toUpperCase() + ":", x + 15, rowY + 5, 0xFF404040, false);
            
            // Render the two frequency slots
            renderSlot(g, x + 120, rowY, frequencies1[i], mx, my);
            renderSlot(g, x + 145, rowY, frequencies2[i], mx, my);
        }
        
        // Render buttons at the bottom
        int saveBtnX = x + w - 75;
        int cancelBtnX = x + 15;
        int btnY = y + h - 22;
        
        // Save button (Green/Gray hover)
        boolean saveHovered = mx >= saveBtnX && mx <= saveBtnX + 60 && my >= btnY && my <= btnY + 16;
        g.fill(saveBtnX, btnY, saveBtnX + 60, btnY + 16, saveHovered ? 0xFF00AA55 : 0xFF707070);
        g.renderOutline(saveBtnX, btnY, 60, 16, 0xFF303030);
        g.drawCenteredString(font, "[ Save ]", saveBtnX + 30, btnY + 4, 0xFFFFFFFF);

        // Cancel button
        boolean cancelHovered = mx >= cancelBtnX && mx <= cancelBtnX + 60 && my >= btnY && my <= btnY + 16;
        g.fill(cancelBtnX, btnY, cancelBtnX + 60, btnY + 16, cancelHovered ? 0xFFCC3333 : 0xFF707070); // Fixed hex typo
        g.renderOutline(cancelBtnX, btnY, 60, 16, 0xFF303030);
        g.drawCenteredString(font, "[ Close ]", cancelBtnX + 30, btnY + 4, 0xFFFFFFFF);
        
        super.render(g, mx, my, pt);
    }
    
    private void renderSlot(GuiGraphics g, int slotX, int slotY, ItemStack stack, int mx, int my) {
        boolean hovered = mx >= slotX && mx <= slotX + 18 && my >= slotY && my <= slotY + 18;
        
        // Slot background and border (classic Minecraft style)
        g.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
        g.renderOutline(slotX, slotY, 18, 18, 0xFF373737);
        g.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, hovered ? 0xFFC6C6C6 : 0xFF8B8B8B);
        
        if (!stack.isEmpty()) {
            g.renderFakeItem(stack, slotX + 1, slotY + 1);
            if (hovered) {
                g.renderTooltip(font, stack, mx, my);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int w = 220;
        int h = 190;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        
        // Click on 6 sides slots
        for (int i = 0; i < 6; i++) {
            int rowY = y + 30 + i * 22;
            
            // Slot 1
            if (mx >= x + 120 && mx <= x + 138 && my >= rowY && my <= rowY + 18) {
                if (button == 1) { // Right click to clear
                    frequencies1[i] = ItemStack.EMPTY;
                } else {
                    int idx = i;
                    minecraft.setScreen(new WItemSelectScreen(this, (stack) -> frequencies1[idx] = stack.copy()));
                }
                return true;
            }
            
            // Slot 2
            if (mx >= x + 145 && mx <= x + 163 && my >= rowY && my <= rowY + 18) {
                if (button == 1) {
                    frequencies2[i] = ItemStack.EMPTY;
                } else {
                    int idx = i;
                    minecraft.setScreen(new WItemSelectScreen(this, (stack) -> frequencies2[idx] = stack.copy()));
                }
                return true;
            }
        }
        
        // Buttons
        int saveBtnX = x + w - 75;
        int cancelBtnX = x + 15;
        int btnY = y + h - 22;
        
        if (mx >= saveBtnX && mx <= saveBtnX + 60 && my >= btnY && my <= btnY + 16) {
            // Save and Sync
            Map<Direction, ItemStack> freqs1 = new HashMap<>();
            Map<Direction, ItemStack> freqs2 = new HashMap<>();
            for (int i = 0; i < 6; i++) {
                Direction dir = DIRECTIONS[i];
                freqs1.put(dir, frequencies1[i]);
                freqs2.put(dir, frequencies2[i]);
            }
            
            // Send payload to server
            PacketDistributor.sendToServer(new VectorThrusterSyncPayload(blockEntity.getBlockPos(), freqs1, freqs2));
            this.onClose();
            return true;
        }
        
        if (mx >= cancelBtnX && mx <= cancelBtnX + 60 && my >= btnY && my <= btnY + 16) {
            this.onClose();
            return true;
        }
        
        return super.mouseClicked(mx, my, button);
    }
}
