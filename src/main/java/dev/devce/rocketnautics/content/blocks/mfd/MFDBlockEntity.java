package dev.devce.rocketnautics.content.blocks.mfd;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.MFDCartridgeItem;
import dev.devce.rocketnautics.content.blocks.mfd.programs.CartridgeProgram;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class MFDBlockEntity extends SmartBlockEntity {
    private final MFDCanvas canvas = new MFDCanvas(64, 64);
    private int programIndex = 0;
    private ItemStack insertedCartridge = ItemStack.EMPTY;
    private CartridgeProgram activeCartridgeProgram = null;
    private String activeCartridgeId = "";

    private int[] externalVideoBuffer = null;
    private boolean externalVideoActive = false;

    public MFDBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public MFDCanvas getCanvas() {
        return canvas;
    }

    public int getProgramIndex() {
        return programIndex;
    }

    public void setProgramIndex(int index) {
        this.programIndex = Math.floorMod(index, Math.max(1, MFDPrograms.getCount()));
        sendData();
        notifyUpdate();
    }

    public void setProgramByName(String name) {
        int idx = MFDPrograms.findIndexByName(name);
        if (idx >= 0) {
            setProgramIndex(idx);
        }
    }

    public String getProgramName() {
        if (hasCartridge() && activeCartridgeId != null && !activeCartridgeId.isEmpty()) {
            return "Cartridge: " + activeCartridgeId;
        }
        MFDProgram p = MFDPrograms.get(programIndex);
        return p != null ? p.getName() : "UNKNOWN";
    }

    public void cycleProgram(int delta) {
        setProgramIndex(this.programIndex + delta);
    }

    public boolean hasCartridge() {
        return (activeCartridgeId != null && !activeCartridgeId.isEmpty()) || !insertedCartridge.isEmpty();
    }

    public String getActiveCartridgeId() {
        return activeCartridgeId != null ? activeCartridgeId : "";
    }

    public ItemStack getInsertedCartridge() {
        return insertedCartridge;
    }

    public void insertCartridge(ItemStack stack) {
        this.insertedCartridge = stack;
        String id = MFDCartridgeItem.getCartridgeId(stack);
        this.activeCartridgeId = id != null ? id : "";
        this.activeCartridgeProgram = !this.activeCartridgeId.isEmpty() ? new CartridgeProgram(this.activeCartridgeId) : null;
        sendData();
        notifyUpdate();
    }

    public ItemStack ejectCartridge() {
        ItemStack ejected = this.insertedCartridge;
        this.insertedCartridge = ItemStack.EMPTY;
        this.activeCartridgeId = "";
        this.activeCartridgeProgram = null;
        if (level != null && level.isClientSide) {
            dev.devce.rocketnautics.client.MFDAudioEngine.stop(worldPosition);
        }
        sendData();
        notifyUpdate();
        return ejected;
    }

    public boolean hasExternalVideo() {
        return externalVideoActive && externalVideoBuffer != null;
    }

    public int[] getExternalVideoBuffer() {
        return externalVideoBuffer;
    }

    public void setExternalVideoBuffer(int[] buffer) {
        if (this.externalVideoBuffer == null || this.externalVideoBuffer.length != 64 * 64) {
            this.externalVideoBuffer = new int[64 * 64];
        }
        System.arraycopy(buffer, 0, this.externalVideoBuffer, 0, Math.min(buffer.length, 64 * 64));
        this.externalVideoActive = true;
        setExternalVideoMode();
        sendData();
        notifyUpdate();
    }

    public void setExternalPixel(int x, int y, int argb) {
        if (x >= 0 && x < 64 && y >= 0 && y < 64) {
            if (this.externalVideoBuffer == null) {
                this.externalVideoBuffer = new int[64 * 64];
            }
            this.externalVideoBuffer[y * 64 + x] = argb;
            this.externalVideoActive = true;
        }
    }

    public int getExternalPixel(int x, int y) {
        if (x >= 0 && x < 64 && y >= 0 && y < 64 && this.externalVideoBuffer != null) {
            return this.externalVideoBuffer[y * 64 + x];
        }
        return 0;
    }

    public void clearExternalVideo(int argb) {
        if (this.externalVideoBuffer == null) {
            this.externalVideoBuffer = new int[64 * 64];
        }
        java.util.Arrays.fill(this.externalVideoBuffer, argb);
        this.externalVideoActive = true;
    }

    public void drawExternalLine(int x0, int y0, int x1, int y1, int argb) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            setExternalPixel(x0, y0, argb);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public void drawExternalRect(int x, int y, int w, int h, int argb) {
        for (int i = 0; i < w; i++) {
            setExternalPixel(x + i, y, argb);
            setExternalPixel(x + i, y + h - 1, argb);
        }
        for (int j = 0; j < h; j++) {
            setExternalPixel(x, y + j, argb);
            setExternalPixel(x + w - 1, y + j, argb);
        }
    }

    public void fillExternalRect(int x, int y, int w, int h, int argb) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                setExternalPixel(x + i, y + j, argb);
            }
        }
    }

    public void drawExternalCircle(int cx, int cy, int r, int argb, boolean filled) {
        int x = 0;
        int y = r;
        int d = 3 - 2 * r;
        while (x <= y) {
            if (filled) {
                for (int xi = cx - x; xi <= cx + x; xi++) {
                    setExternalPixel(xi, cy + y, argb);
                    setExternalPixel(xi, cy - y, argb);
                }
                for (int xi = cx - y; xi <= cx + y; xi++) {
                    setExternalPixel(xi, cy + x, argb);
                    setExternalPixel(xi, cy - x, argb);
                }
            } else {
                setExternalPixel(cx + x, cy + y, argb);
                setExternalPixel(cx - x, cy + y, argb);
                setExternalPixel(cx + x, cy - y, argb);
                setExternalPixel(cx - x, cy - y, argb);
                setExternalPixel(cx + y, cy + x, argb);
                setExternalPixel(cx - y, cy + x, argb);
                setExternalPixel(cx + y, cy - x, argb);
                setExternalPixel(cx - y, cy - x, argb);
            }
            if (d < 0) {
                d += 4 * x + 6;
            } else {
                d += 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
    }

    public void drawExternalString(String text, int x, int y, int argb) {
        if (text == null) return;
        int curX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z') c = (char) (c - 32);
            int charIdx = c - 32;
            if (charIdx < 0 || charIdx >= 64) charIdx = '?' - 32;
            short glyph = MFDCanvas.getGlyph(charIdx);
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    if ((glyph & (1 << (14 - (row * 3 + col)))) != 0) {
                        setExternalPixel(curX + col, y + row, argb);
                    }
                }
            }
            curX += 4;
        }
    }

    public void flushExternalVideo() {
        this.externalVideoActive = true;
        setExternalVideoMode();
        sendData();
        notifyUpdate();
    }

    public void setExternalVideoMode() {
        int idx = MFDPrograms.findIndexByName("VIDEO");
        if (idx >= 0) {
            setProgramIndex(idx);
        }
    }

    public boolean isExternalVideoMode() {
        int idx = MFDPrograms.findIndexByName("VIDEO");
        return idx >= 0 && this.programIndex == idx;
    }

    public void render(float partialTicks) {
        if (hasCartridge()) {
            if (activeCartridgeId != null && !activeCartridgeId.isEmpty()) {
                if (activeCartridgeProgram == null || !activeCartridgeId.equals(activeCartridgeProgram.getName().replace("Cartridge: ", ""))) {
                    activeCartridgeProgram = new CartridgeProgram(activeCartridgeId);
                }
                activeCartridgeProgram.render(canvas, this, partialTicks);
            }
        } else {
            MFDProgram program = MFDPrograms.get(programIndex);
            if (program != null) {
                program.render(canvas, this, partialTicks);
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("ProgramIndex", programIndex);
        tag.putString("CartridgeId", activeCartridgeId != null ? activeCartridgeId : "");
        if (!insertedCartridge.isEmpty()) {
            tag.put("Cartridge", insertedCartridge.save(registries));
        }
        tag.putBoolean("VideoActive", externalVideoActive);
        if (externalVideoActive && externalVideoBuffer != null) {
            tag.putIntArray("VideoBuffer", externalVideoBuffer);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.programIndex = tag.getInt("ProgramIndex");
        this.activeCartridgeId = tag.getString("CartridgeId");
        if (tag.contains("Cartridge")) {
            this.insertedCartridge = ItemStack.parseOptional(registries, tag.getCompound("Cartridge"));
        } else {
            this.insertedCartridge = ItemStack.EMPTY;
        }
        if (!activeCartridgeId.isEmpty()) {
            this.activeCartridgeProgram = new CartridgeProgram(activeCartridgeId);
        } else {
            this.activeCartridgeProgram = null;
        }
        this.externalVideoActive = tag.getBoolean("VideoActive");
        if (tag.contains("VideoBuffer")) {
            this.externalVideoBuffer = tag.getIntArray("VideoBuffer");
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && level.isClientSide) {
            canvas.close();
            dev.devce.rocketnautics.client.MFDAudioEngine.stop(worldPosition);
        }
    }
}
