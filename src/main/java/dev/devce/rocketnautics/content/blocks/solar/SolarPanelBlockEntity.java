package dev.devce.rocketnautics.content.blocks.solar;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.SableSubLevelLightingHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.*;

/**
 * BlockEntity for the Solar Panel.
 * Generates Forge Energy based on sunlight incidence angle.
 * Adjacent coplanar solar panels automatically unite into a single shared FE solar array.
 */
public class SolarPanelBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public static final int MAX_SPACE_GENERATION = 160; // Max FE/t in Deep Space at optimal 90 deg angle
    public static final int MAX_OVERWORLD_GENERATION = 60; // Max FE/t in Overworld during clear midday
    public static final int CAPACITY_PER_PANEL = 10000;
    public static final int TRANSFER_PER_PANEL = 200;

    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(CAPACITY_PER_PANEL, TRANSFER_PER_PANEL);

    // Multiblock / Array state
    @Nullable
    private BlockPos controllerPos = null;
    private int connectedPanelsCount = 1;
    private boolean connectivityDirty = true;

    // Generation stats
    private int currentGeneration = 0;
    private float efficiency = 0.0f;
    private int totalArrayGeneration = 0;
    private float avgArrayEfficiency = 0.0f;

    public SolarPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick() {
        if (level == null) return;

        if (!level.isClientSide) {
            if (connectivityDirty) {
                updateConnectivity();
                connectivityDirty = false;
            }

            // 1. Calculate local panel generation
            int generated = computeGeneration();
            this.currentGeneration = generated;

            // 2. Feed generated energy into the controller
            SolarPanelBlockEntity controller = getController();
            if (controller != null) {
                if (generated > 0) {
                    controller.energyStorage.receiveEnergyInternal(generated);
                }
            }

            // 3. If controller, aggregate array statistics and push energy
            if (isController()) {
                aggregateArrayStats();
            }

            // 4. Any panel in the array with an adjacent consumer can push energy from the shared buffer
            pushEnergy();

            // 5. Periodic sync
            if (level.getGameTime() % 10 == 0) {
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public boolean isController() {
        return controllerPos == null || controllerPos.equals(worldPosition);
    }

    @Nullable
    public SolarPanelBlockEntity getController() {
        if (isController()) return this;
        if (level != null && controllerPos != null) {
            BlockEntity be = level.getBlockEntity(controllerPos);
            if (be instanceof SolarPanelBlockEntity solarBe && !solarBe.isRemoved()) {
                return solarBe;
            }
        }
        return this;
    }

    public static List<Direction> getPerpendicularDirections(Direction facing) {
        return switch (facing.getAxis()) {
            case Y -> List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
            case Z -> List.of(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST);
            case X -> List.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH);
        };
    }

    /**
     * Traverses adjacent coplanar solar panels and forms a unified network with a deterministic controller.
     */
    public void updateConnectivity() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBlock)) return;
        Direction facing = state.getValue(SolarPanelBlock.FACING);

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        List<SolarPanelBlockEntity> members = new ArrayList<>();

        queue.add(worldPosition);
        visited.add(worldPosition);

        int totalEnergyStored = 0;

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.poll();
            BlockEntity be = level.getBlockEntity(currentPos);
            if (be instanceof SolarPanelBlockEntity solarBe && !solarBe.isRemoved()) {
                BlockState beState = solarBe.getBlockState();
                if (beState.getBlock() instanceof SolarPanelBlock && beState.getValue(SolarPanelBlock.FACING) == facing) {
                    members.add(solarBe);
                    totalEnergyStored += solarBe.energyStorage.getEnergyStored();

                    for (Direction dir : getPerpendicularDirections(facing)) {
                        BlockPos neighborPos = currentPos.relative(dir);
                        if (!visited.contains(neighborPos)) {
                            visited.add(neighborPos);
                            queue.add(neighborPos);
                        }
                    }
                }
            }
        }

        if (members.isEmpty()) return;

        // Find member with minimum BlockPos to serve as the master controller
        SolarPanelBlockEntity controller = members.stream()
                .min(Comparator.comparingInt((SolarPanelBlockEntity b) -> b.worldPosition.getX())
                        .thenComparingInt(b -> b.worldPosition.getY())
                        .thenComparingInt(b -> b.worldPosition.getZ()))
                .orElse(this);

        int memberCount = members.size();
        controller.energyStorage.setCapacity(memberCount * CAPACITY_PER_PANEL);
        controller.energyStorage.setMaxExtract(memberCount * TRANSFER_PER_PANEL);
        controller.energyStorage.setEnergy(Math.min(controller.energyStorage.getMaxEnergyStored(), totalEnergyStored));

        for (SolarPanelBlockEntity member : members) {
            member.controllerPos = controller.worldPosition;
            member.connectedPanelsCount = memberCount;
            if (member != controller) {
                member.energyStorage.setEnergy(0);
            }
            member.setChanged();
            level.sendBlockUpdated(member.worldPosition, member.getBlockState(), member.getBlockState(), 3);
        }
    }

    /**
     * Splits remaining connected panels and rebuilds connectivity when this panel is removed.
     */
    public void destroyConnectivity() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBlock)) return;
        Direction facing = state.getValue(SolarPanelBlock.FACING);

        List<SolarPanelBlockEntity> neighborsToUpdate = new ArrayList<>();
        for (Direction dir : getPerpendicularDirections(facing)) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockEntity be = level.getBlockEntity(neighborPos);
            if (be instanceof SolarPanelBlockEntity solarBe && !solarBe.isRemoved()) {
                neighborsToUpdate.add(solarBe);
            }
        }

        for (SolarPanelBlockEntity neighbor : neighborsToUpdate) {
            neighbor.controllerPos = null;
            neighbor.connectedPanelsCount = 1;
            neighbor.updateConnectivity();
        }
    }

    private int arrayStoredEnergy = 0;
    private int arrayMaxEnergy = CAPACITY_PER_PANEL;

    private void aggregateArrayStats() {
        if (level == null || !isController()) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBlock)) return;
        Direction facing = state.getValue(SolarPanelBlock.FACING);

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        List<SolarPanelBlockEntity> members = new ArrayList<>();

        queue.add(worldPosition);
        visited.add(worldPosition);

        int sumGen = 0;
        float sumEff = 0.0f;
        int count = 0;

        while (!queue.isEmpty()) {
            BlockPos currentPos = queue.poll();
            BlockEntity be = level.getBlockEntity(currentPos);
            if (be instanceof SolarPanelBlockEntity solarBe && !solarBe.isRemoved()) {
                BlockState beState = solarBe.getBlockState();
                if (beState.getBlock() instanceof SolarPanelBlock && beState.getValue(SolarPanelBlock.FACING) == facing) {
                    members.add(solarBe);
                    sumGen += solarBe.currentGeneration;
                    sumEff += solarBe.efficiency;
                    count++;

                    for (Direction dir : getPerpendicularDirections(facing)) {
                        BlockPos neighborPos = currentPos.relative(dir);
                        if (!visited.contains(neighborPos)) {
                            visited.add(neighborPos);
                            queue.add(neighborPos);
                        }
                    }
                }
            }
        }

        this.totalArrayGeneration = sumGen;
        this.avgArrayEfficiency = count > 0 ? (sumEff / count) : 0.0f;
        this.connectedPanelsCount = count;
        this.arrayStoredEnergy = energyStorage.getEnergyStored();
        this.arrayMaxEnergy = energyStorage.getMaxEnergyStored();

        for (SolarPanelBlockEntity member : members) {
            member.totalArrayGeneration = this.totalArrayGeneration;
            member.avgArrayEfficiency = this.avgArrayEfficiency;
            member.connectedPanelsCount = this.connectedPanelsCount;
            member.arrayStoredEnergy = this.arrayStoredEnergy;
            member.arrayMaxEnergy = this.arrayMaxEnergy;
        }
    }

    private int computeGeneration() {
        if (level == null) return 0;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBlock)) return 0;
        Direction facing = state.getValue(SolarPanelBlock.FACING);

        // Check if facing top is obstructed by a solid block
        BlockPos frontPos = worldPosition.relative(facing);
        if (level.getBlockState(frontPos).isSolidRender(level, frontPos)) {
            this.efficiency = 0.0f;
            return 0;
        }

        if (DeepSpaceHelper.isDeepSpace(level)) {
            Vec3 panelNormal = getPanelWorldNormal(facing);
            Vec3 sunDir = getSunDirection();
            double dot = panelNormal.dot(sunDir);

            if (dot > 0.0) {
                this.efficiency = (float) Math.min(1.0, dot);
                return (int) Math.round(MAX_SPACE_GENERATION * efficiency);
            } else {
                this.efficiency = 0.0f;
                return 0;
            }
        } else {
            if (!level.canSeeSky(frontPos)) {
                this.efficiency = 0.0f;
                return 0;
            }

            float sunAngle = level.getSunAngle(1.0f);
            double sunFactor = Math.max(0.0, Math.cos(sunAngle));
            if (level.isRaining()) sunFactor *= 0.35;
            if (level.isThundering()) sunFactor *= 0.15;

            double orientFactor = (facing == Direction.UP) ? 1.0 : (facing == Direction.DOWN ? 0.0 : 0.6);
            double totalEfficiency = sunFactor * orientFactor;

            this.efficiency = (float) Math.min(1.0, totalEfficiency);
            return (int) Math.round(MAX_OVERWORLD_GENERATION * efficiency);
        }
    }

    private Vec3 getPanelWorldNormal(Direction facing) {
        if (level != null) {
            SubLevel subLevel = Sable.HELPER.getContaining(level, worldPosition);
            if (subLevel != null) {
                Quaterniond rot = subLevel.logicalPose().orientation();
                Vector3d localNorm = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                Vector3d worldNorm = rot.transform(localNorm, new Vector3d());
                return new Vec3(worldNorm.x, worldNorm.y, worldNorm.z);
            }
        }
        return new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private Vec3 getSunDirection() {
        if (level != null && level.isClientSide) {
            return new Vec3(
                    SableSubLevelLightingHandler.getSunX(),
                    SableSubLevelLightingHandler.getSunY(),
                    SableSubLevelLightingHandler.getSunZ()
            ).normalize();
        }

        if (DeepSpaceHandler.hasReceivedPosition() && DeepSpaceHandler.getUniverse() != null) {
            try {
                org.orekit.time.AbsoluteDate renderDate = DeepSpaceHandler.getRenderDate(0.0f);
                Vector3D shipPos = DeepSpaceHandler.getReceivedPosition().getPosition(renderDate);
                org.orekit.frames.Frame shipFrame = DeepSpaceHandler.getReceivedPosition().getFrame();

                return DeepSpaceHandler.getUniverse().getPlanets().stream()
                        .filter(p -> p.extras() != null && p.extras().star())
                        .map(sol -> {
                            try {
                                Vector3D dir = sol.posInMyFrame(renderDate, shipPos, shipFrame).negate();
                                return new Vec3(dir.getX(), dir.getY(), dir.getZ()).normalize();
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(new Vec3(0.577, 0.707, 0.408).normalize());
            } catch (Exception ignored) {}
        }
        return new Vec3(0.577, 0.707, 0.408).normalize();
    }

    private void pushEnergy() {
        SolarPanelBlockEntity controller = getController();
        if (controller == null || controller.energyStorage.getEnergyStored() <= 0 || level == null) return;
        Direction facing = getBlockState().getValue(SolarPanelBlock.FACING);

        // Push energy to neighbor non-solar blocks on all sides except the front active surface
        for (Direction d : Direction.values()) {
            if (d == facing) continue;
            BlockPos targetPos = worldPosition.relative(d);
            BlockEntity targetBe = level.getBlockEntity(targetPos);
            if (targetBe instanceof SolarPanelBlockEntity) continue; // Don't push to internal array members

            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, d.getOpposite());
            if (target != null && target.canReceive()) {
                int toExtract = Math.min(controller.energyStorage.getEnergyStored(), TRANSFER_PER_PANEL);
                int simulated = target.receiveEnergy(toExtract, true);
                if (simulated > 0) {
                    int drained = controller.energyStorage.extractEnergy(simulated, false);
                    target.receiveEnergy(drained, false);
                    if (controller.energyStorage.getEnergyStored() <= 0) break;
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean isArray = this.connectedPanelsCount > 1;

        if (isArray) {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("block.rocketnautics.solar_panel.array", this.connectedPanelsCount)
                            .withStyle(ChatFormatting.GOLD)));
        } else {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable(getBlockState().getBlock().getDescriptionId())
                            .withStyle(ChatFormatting.GOLD)));
        }

        int output = isArray ? this.totalArrayGeneration : this.currentGeneration;
        float eff = isArray ? this.avgArrayEfficiency : this.efficiency;

        Direction facing = getBlockState().getValue(SolarPanelBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        boolean isObstructed = level != null && level.getBlockState(frontPos).isSolidRender(level, frontPos);

        if (isObstructed) {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Obstructed").withStyle(ChatFormatting.RED)));
        } else if (output > 0) {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Generating").withStyle(ChatFormatting.GREEN)));
            int pct = Math.round(eff * 100.0f);
            ChatFormatting effColor = pct >= 75 ? ChatFormatting.GREEN : (pct >= 35 ? ChatFormatting.YELLOW : ChatFormatting.GOLD);
            tooltip.add(Component.literal("  Efficiency: ")
                    .append(Component.literal(pct + "%").withStyle(effColor)));
            tooltip.add(Component.literal(isArray ? "  Array Output: " : "  Output: ")
                    .append(Component.literal(output + " FE/t").withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Inactive (No Sunlight)").withStyle(ChatFormatting.RED)));
            tooltip.add(Component.literal("  Efficiency: ")
                    .append(Component.literal("0%").withStyle(ChatFormatting.GRAY)));
        }

        int stored = isArray ? this.arrayStoredEnergy : this.energyStorage.getEnergyStored();
        int max = isArray ? this.arrayMaxEnergy : this.energyStorage.getMaxEnergyStored();

        tooltip.add(Component.literal("  Stored: ")
                .append(Component.literal(stored + " / " + max + " FE").withStyle(ChatFormatting.WHITE)));

        return true;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        SolarPanelBlockEntity controller = getController();
        return controller != null ? controller.energyStorage : this.energyStorage;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("MaxEnergy", energyStorage.getMaxEnergyStored());
        tag.putInt("Generation", currentGeneration);
        tag.putFloat("Efficiency", efficiency);
        tag.putInt("ConnectedPanels", connectedPanelsCount);
        tag.putInt("ArrayGen", totalArrayGeneration);
        tag.putFloat("ArrayEff", avgArrayEfficiency);
        tag.putInt("ArrayStored", arrayStoredEnergy);
        tag.putInt("ArrayMax", arrayMaxEnergy);
        if (controllerPos != null) {
            tag.putLong("ControllerPos", controllerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergy(tag.getInt("Energy"));
        if (tag.contains("MaxEnergy")) {
            energyStorage.setCapacity(tag.getInt("MaxEnergy"));
        }
        this.currentGeneration = tag.getInt("Generation");
        this.efficiency = tag.getFloat("Efficiency");
        this.connectedPanelsCount = tag.getInt("ConnectedPanels");
        this.totalArrayGeneration = tag.getInt("ArrayGen");
        this.avgArrayEfficiency = tag.getFloat("ArrayEff");
        this.arrayStoredEnergy = tag.getInt("ArrayStored");
        this.arrayMaxEnergy = tag.getInt("ArrayMax");
        if (tag.contains("ControllerPos")) {
            this.controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
        } else {
            this.controllerPos = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static class CustomEnergyStorage extends EnergyStorage {
        public CustomEnergyStorage(int capacity, int maxTransfer) {
            super(capacity, 0, maxTransfer);
        }

        public int receiveEnergyInternal(int maxReceive) {
            int energyReceived = Math.min(capacity - energy, maxReceive);
            energy += energyReceived;
            return energyReceived;
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public void setMaxExtract(int maxExtract) {
            this.maxExtract = maxExtract;
        }
    }
}
