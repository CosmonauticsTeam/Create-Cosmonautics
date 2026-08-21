package dev.devce.rocketnautics.content.blocks.energy_tank;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.devce.rocketnautics.content.energy.CustomEnergyStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EnergyTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int CAPACITY_PER_BLOCK = 250000; // 250k FE
    public static final int TRANSFER_PER_BLOCK = 5000;   // 5k FE/t
    public static final int MAX_CLUSTER_SIZE = 512;

    public final CustomEnergyStorage energyStorage;
    private final ClusterEnergyStorage clusterStorage;

    private int clusterSize = 1;
    private int clusterTotalEnergy = 0;
    private int clusterTotalCapacity = CAPACITY_PER_BLOCK;
    private int clusterMaxTransfer = TRANSFER_PER_BLOCK;

    private int syncCooldown = 0;
    private int lastSyncedEnergy = 0;
    private int clusterScanCooldown = 0;

    public EnergyTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.energyStorage = new CustomEnergyStorage(CAPACITY_PER_BLOCK, TRANSFER_PER_BLOCK);
        this.clusterStorage = new ClusterEnergyStorage(this);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) return;

        clusterScanCooldown++;
        if (clusterScanCooldown >= 20) {
            clusterScanCooldown = 0;
            updateClusterStats();
        }

        syncCooldown++;
        boolean energyChanged = Math.abs(energyStorage.getEnergyStored() - lastSyncedEnergy) >= 100;
        if (syncCooldown >= 10 || energyChanged) {
            syncCooldown = 0;
            lastSyncedEnergy = energyStorage.getEnergyStored();
            updateClusterStats();
            sendData();
        }
    }

    public List<EnergyTankBlockEntity> getConnectedCluster() {
        List<EnergyTankBlockEntity> cluster = new ArrayList<>();
        if (level == null) return cluster;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(worldPosition);
        visited.add(worldPosition);

        while (!queue.isEmpty() && cluster.size() < MAX_CLUSTER_SIZE) {
            BlockPos current = queue.poll();
            BlockEntity be = level.getBlockEntity(current);
            if (be instanceof EnergyTankBlockEntity tankBE && !tankBE.isRemoved()) {
                cluster.add(tankBE);

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        BlockEntity neighborBE = level.getBlockEntity(neighbor);
                        if (neighborBE instanceof EnergyTankBlockEntity) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return cluster;
    }

    public void updateClusterStats() {
        if (level == null || level.isClientSide) return;
        List<EnergyTankBlockEntity> cluster = getConnectedCluster();
        int totalEnergy = 0;
        for (EnergyTankBlockEntity tank : cluster) {
            totalEnergy += tank.energyStorage.getEnergyStored();
        }
        this.clusterSize = cluster.size();
        this.clusterTotalEnergy = totalEnergy;
        this.clusterTotalCapacity = clusterSize * CAPACITY_PER_BLOCK;
        this.clusterMaxTransfer = clusterSize * TRANSFER_PER_BLOCK;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return clusterStorage;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.rocketnautics.energy_tank").withStyle(ChatFormatting.GOLD)));

        if (clusterSize > 1) {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.rocketnautics.goggles.tank_size").withStyle(ChatFormatting.GRAY))
                    .append(": ")
                    .append(Component.literal(clusterSize + " blocks")
                            .withStyle(ChatFormatting.AQUA)));
        }

        int stored = clusterSize > 1 ? clusterTotalEnergy : energyStorage.getEnergyStored();
        int max = clusterSize > 1 ? clusterTotalCapacity : energyStorage.getMaxEnergyStored();
        int pct = max > 0 ? Math.round(((float) stored / max) * 100.0f) : 0;

        ChatFormatting color = pct >= 50 ? ChatFormatting.GREEN : (pct >= 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);

        tooltip.add(Component.literal("  ")
                .append(Component.translatable("gui.rocketnautics.goggles.energy_stored").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(Component.literal(stored + " / " + max + " FE").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + pct + "%)").withStyle(color)));

        tooltip.add(Component.literal("  ")
                .append(Component.translatable("gui.rocketnautics.goggles.wire_max_rate").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(Component.literal((clusterSize > 1 ? clusterMaxTransfer : TRANSFER_PER_BLOCK) + " FE/t").withStyle(ChatFormatting.DARK_AQUA)));

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("ClusterSize", clusterSize);
        tag.putInt("ClusterEnergy", clusterTotalEnergy);
        tag.putInt("ClusterCapacity", clusterTotalCapacity);
        tag.putInt("ClusterTransfer", clusterMaxTransfer);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }
        if (tag.contains("ClusterSize")) {
            clusterSize = tag.getInt("ClusterSize");
            clusterTotalEnergy = tag.getInt("ClusterEnergy");
            clusterTotalCapacity = tag.getInt("ClusterCapacity");
            clusterMaxTransfer = tag.getInt("ClusterTransfer");
        }
    }

    private static class ClusterEnergyStorage implements IEnergyStorage {
        private final EnergyTankBlockEntity host;

        public ClusterEnergyStorage(EnergyTankBlockEntity host) {
            this.host = host;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            List<EnergyTankBlockEntity> cluster = host.getConnectedCluster();
            if (cluster.isEmpty()) return 0;

            int totalReceived = 0;
            int remaining = Math.min(maxReceive, cluster.size() * TRANSFER_PER_BLOCK);

            for (EnergyTankBlockEntity tank : cluster) {
                if (remaining <= 0) break;
                int received = tank.energyStorage.receiveEnergy(remaining, simulate);
                totalReceived += received;
                remaining -= received;
                if (!simulate && received > 0) {
                    tank.setChanged();
                }
            }

            return totalReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            List<EnergyTankBlockEntity> cluster = host.getConnectedCluster();
            if (cluster.isEmpty()) return 0;

            int totalExtracted = 0;
            int remaining = Math.min(maxExtract, cluster.size() * TRANSFER_PER_BLOCK);

            for (EnergyTankBlockEntity tank : cluster) {
                if (remaining <= 0) break;
                int extracted = tank.energyStorage.extractEnergy(remaining, simulate);
                totalExtracted += extracted;
                remaining -= extracted;
                if (!simulate && extracted > 0) {
                    tank.setChanged();
                }
            }

            return totalExtracted;
        }

        @Override
        public int getEnergyStored() {
            List<EnergyTankBlockEntity> cluster = host.getConnectedCluster();
            int total = 0;
            for (EnergyTankBlockEntity tank : cluster) {
                total += tank.energyStorage.getEnergyStored();
            }
            return total;
        }

        @Override
        public int getMaxEnergyStored() {
            List<EnergyTankBlockEntity> cluster = host.getConnectedCluster();
            return cluster.size() * CAPACITY_PER_BLOCK;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
