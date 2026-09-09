package dev.devce.rocketnautics.content.blocks.sputnik_link;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SputnikLinkBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private int linkId = 0;
    private int emittedSignal = 0;
    private int receivedSignal = 0;
    private int lastEmittedSignal = 0;

    public SputnikLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getLinkId() {
        return linkId;
    }

    public void setLinkId(int linkId) {
        this.linkId = linkId;
        setChanged();
        sendData();
    }

    public boolean isReceiverMode() {
        BlockState state = getBlockState();
        if (state.hasProperty(SputnikLinkBlock.RECEIVER)) {
            return state.getValue(SputnikLinkBlock.RECEIVER);
        }
        return false;
    }

    public int getEmittedSignal() {
        return isReceiverMode() ? emittedSignal : 0;
    }

    public void setEmittedSignal(int signal) {
        this.emittedSignal = Math.max(0, Math.min(15, signal));
        if (emittedSignal != lastEmittedSignal) {
            lastEmittedSignal = emittedSignal;
            if (level != null && !level.isClientSide()) {
                setChanged();
                sendData();
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
    }

    public int getReceivedSignal() {
        return receivedSignal;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        if (!level.isClientSide()) {
            if (linkId <= 0 && level.getServer() != null) {
                linkId = SputnikLinkManager.allocateId(level.getServer());
                setChanged();
                sendData();
            }

            SputnikLinkManager.registerLink(this);

            if (isReceiverMode()) {
                int target = SputnikLinkManager.getTransmittedSignal(linkId, level.getGameTime());
                if (target != emittedSignal) {
                    setEmittedSignal(target);
                }
            } else {
                // Transmitter Mode: read redstone signal from world
                int power = level.getBestNeighborSignal(worldPosition);
                if (power != receivedSignal) {
                    receivedSignal = power;
                    SputnikLinkManager.setReceivedSignal(linkId, power);
                    setChanged();
                    sendData();
                }
            }
        }
    }

    @Override
    public void remove() {
        SputnikLinkManager.unregisterLink(this);
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("     ")
                .append(Component.translatable("block.rocketnautics.sputnik_link").withStyle(ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  ")
                .append(Component.translatable("rocketnautics.goggles.link_id").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(Component.literal("#" + linkId).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));

        boolean isReceiver = isReceiverMode();
        tooltip.add(Component.literal("  ")
                .append(Component.translatable("rocketnautics.goggles.mode").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(isReceiver
                        ? Component.translatable("rocketnautics.goggles.mode.receiver").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("rocketnautics.goggles.mode.transmitter").withStyle(ChatFormatting.GOLD)));

        if (isReceiver) {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("rocketnautics.goggles.output_signal").withStyle(ChatFormatting.GRAY))
                    .append(": ")
                    .append(Component.literal(emittedSignal + " / 15")
                            .withStyle(emittedSignal > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        } else {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("rocketnautics.goggles.input_signal").withStyle(ChatFormatting.GRAY))
                    .append(": ")
                    .append(Component.literal(receivedSignal + " / 15")
                            .withStyle(receivedSignal > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        }

        tooltip.add(Component.literal("  ")
                .append(Component.translatable("rocketnautics.goggles.click_to_toggle").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("LinkId", linkId);
        tag.putInt("EmittedSignal", emittedSignal);
        tag.putInt("ReceivedSignal", receivedSignal);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        linkId = tag.getInt("LinkId");
        emittedSignal = tag.getInt("EmittedSignal");
        receivedSignal = tag.getInt("ReceivedSignal");
    }
}
