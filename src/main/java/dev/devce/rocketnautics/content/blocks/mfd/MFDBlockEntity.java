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

    public void cycleProgram(int delta) {
        setProgramIndex(this.programIndex + delta);
    }

    public boolean hasCartridge() {
        return (activeCartridgeId != null && !activeCartridgeId.isEmpty()) || !insertedCartridge.isEmpty();
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
        sendData();
        notifyUpdate();
        return ejected;
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
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && level.isClientSide) {
            canvas.close();
        }
    }
}
