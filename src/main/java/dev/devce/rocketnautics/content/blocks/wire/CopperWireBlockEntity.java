package dev.devce.rocketnautics.content.blocks.wire;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CopperWireBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public static final int WIRE_CAPACITY = 2000;
    public static final int WIRE_TRANSFER_RATE = 1000;

    private final WireEnergyStorage energyStorage;
    private int lastTransferredEnergy = 0;
    private int currentTransferRate = 0;
    private int syncCooldown = 0;

    public CopperWireBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.energyStorage = new WireEnergyStorage();
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    public void tick() {
        if (level == null) return;

        if (!level.isClientSide) {
            int previousTransferred = lastTransferredEnergy;
            lastTransferredEnergy = currentTransferRate;
            currentTransferRate = 0;

            if (energyStorage.getEnergyStored() > 0) {
                pushBufferToConsumers();
            }

            syncCooldown++;
            if (syncCooldown >= 5 || previousTransferred != lastTransferredEnergy) {
                syncCooldown = 0;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    private void pushBufferToConsumers() {
        Set<BlockPos> visited = new HashSet<>();
        visited.add(worldPosition);
        List<ConsumerTarget> consumers = new ArrayList<>();
        findConsumers(worldPosition, visited, consumers, 64);

        if (consumers.isEmpty()) return;

        int toDistribute = Math.min(energyStorage.getEnergyStored(), WIRE_TRANSFER_RATE);
        int perConsumer = Math.max(1, toDistribute / consumers.size());

        for (ConsumerTarget target : consumers) {
            if (energyStorage.getEnergyStored() <= 0) break;
            int accepted = target.storage.receiveEnergy(Math.min(energyStorage.getEnergyStored(), perConsumer), false);
            if (accepted > 0) {
                energyStorage.extractInternal(accepted);
                currentTransferRate += accepted;
            }
        }
    }

    public int routeEnergy(int maxReceive, boolean simulate) {
        if (level == null || maxReceive <= 0) return 0;

        Set<BlockPos> visited = new HashSet<>();
        visited.add(worldPosition);
        List<ConsumerTarget> consumers = new ArrayList<>();
        findConsumers(worldPosition, visited, consumers, 64);

        if (consumers.isEmpty()) {
            return energyStorage.receiveInternal(maxReceive, simulate);
        }

        int remaining = Math.min(maxReceive, WIRE_TRANSFER_RATE);
        int totalAccepted = 0;

        int perConsumer = Math.max(1, remaining / consumers.size());
        for (ConsumerTarget target : consumers) {
            if (remaining <= 0) break;
            int amount = Math.min(remaining, perConsumer);
            int accepted = target.storage.receiveEnergy(amount, simulate);
            if (accepted > 0) {
                remaining -= accepted;
                totalAccepted += accepted;
            }
        }

        if (remaining > 0) {
            for (ConsumerTarget target : consumers) {
                if (remaining <= 0) break;
                int accepted = target.storage.receiveEnergy(remaining, simulate);
                if (accepted > 0) {
                    remaining -= accepted;
                    totalAccepted += accepted;
                }
            }
        }

        if (!simulate) {
            currentTransferRate += totalAccepted;
        }

        return totalAccepted;
    }

    private void findConsumers(BlockPos current, Set<BlockPos> visited, List<ConsumerTarget> consumers, int maxDepth) {
        if (maxDepth <= 0 || level == null) return;

        BlockState state = level.getBlockState(current);
        if (!(state.getBlock() instanceof CopperWireBlock)) return;

        for (Direction dir : Direction.values()) {
            if (!state.getValue(CopperWireBlock.PROPERTY_BY_DIRECTION.get(dir))) continue;

            BlockPos neighborPos = current.relative(dir);
            if (visited.contains(neighborPos)) continue;

            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof CopperWireBlock) {
                visited.add(neighborPos);
                findConsumers(neighborPos, visited, consumers, maxDepth - 1);
            } else {
                visited.add(neighborPos);
                IEnergyStorage neighborStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, dir.getOpposite());
                if (neighborStorage != null && neighborStorage.canReceive()) {
                    consumers.add(new ConsumerTarget(neighborStorage, neighborPos, dir.getOpposite()));
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.rocketnautics.copper_wire").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  ")
                .append(Component.translatable("gui.rocketnautics.goggles.wire_transfer").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(Component.literal(lastTransferredEnergy + " FE/t").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("  ")
                .append(Component.translatable("gui.rocketnautics.goggles.wire_max_rate").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(Component.literal(WIRE_TRANSFER_RATE + " FE/t").withStyle(ChatFormatting.DARK_AQUA)));
        return true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Transferred", lastTransferredEnergy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergy(tag.getInt("Energy"));
        lastTransferredEnergy = tag.getInt("Transferred");
    }

    private static class ConsumerTarget {
        final IEnergyStorage storage;
        final BlockPos pos;
        final Direction side;

        ConsumerTarget(IEnergyStorage storage, BlockPos pos, Direction side) {
            this.storage = storage;
            this.pos = pos;
            this.side = side;
        }
    }

    private class WireEnergyStorage implements IEnergyStorage {

        private int energy = 0;

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return routeEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return WIRE_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(WIRE_CAPACITY, energy));
        }

        int receiveInternal(int maxReceive, boolean simulate) {
            int accepted = Math.min(maxReceive, Math.min(WIRE_CAPACITY - energy, WIRE_TRANSFER_RATE));
            if (!simulate) {
                energy += accepted;
                setChanged();
            }
            return accepted;
        }

        void extractInternal(int amount) {
            energy = Math.max(0, energy - amount);
            setChanged();
        }
    }
}
