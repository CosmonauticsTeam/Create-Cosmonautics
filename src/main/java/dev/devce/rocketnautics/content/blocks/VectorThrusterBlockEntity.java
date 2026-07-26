package dev.devce.rocketnautics.content.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class VectorThrusterBlockEntity extends RocketThrusterBlockEntity {
    
    // Linked Receiver frequencies (two slots per direction)
    private final ItemStack[] frequencies1 = new ItemStack[6];
    private final ItemStack[] frequencies2 = new ItemStack[6];

    @Override
    public double readValue(String key) {
        if (key.equals("thrust")) return getFlow() * 100.0;
        if (key.equals("gimbal_x")) return gimbalX;
        if (key.equals("gimbal_z")) return gimbalZ;
        return 0;
    }
    private static final Direction[] DIRECTIONS = Direction.values();

    private float gimbalX = 0;
    private float gimbalY = 0;
    private float gimbalZ = 0;

    private float prevGimbalX = 0;
    private float prevGimbalY = 0;
    private float prevGimbalZ = 0;

    private float ccGimbalX = 0;
    private float ccGimbalY = 0;
    private float ccGimbalZ = 0;

    public VectorThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        for (int i = 0; i < 6; i++) {
            frequencies1[i] = ItemStack.EMPTY;
            frequencies2[i] = ItemStack.EMPTY;
        }
    }

    public ItemStack getFrequencyStack1(Direction dir) {
        return frequencies1[dir.ordinal()];
    }

    public ItemStack getFrequencyStack2(Direction dir) {
        return frequencies2[dir.ordinal()];
    }

    public void setFrequencyStack1(Direction dir, ItemStack stack) {
        frequencies1[dir.ordinal()] = stack != null ? stack.copy() : ItemStack.EMPTY;
        setChanged();
    }

    public void setFrequencyStack2(Direction dir, ItemStack stack) {
        frequencies2[dir.ordinal()] = stack != null ? stack.copy() : ItemStack.EMPTY;
        setChanged();
    }

    @Override
    public int getWarmupTime() {
        return 10;
    }

    public Vec3 getGimbaledExhaustDirection() {
        Direction nozzle = getThrustDirection();
        return new Vec3(
                nozzle.getStepX() + gimbalX,
                nozzle.getStepY() + gimbalY,
                nozzle.getStepZ() + gimbalZ).normalize();
    }

    private int ccGimbalTimeout = 0;

    public void setComputerGimbal(float x, float y, float z) {
        this.ccGimbalX = Math.max(-1.0f, Math.min(1.0f, x));
        this.ccGimbalY = Math.max(-1.0f, Math.min(1.0f, y));
        this.ccGimbalZ = Math.max(-1.0f, Math.min(1.0f, z));
        this.ccGimbalTimeout = 5;
        setChanged();
    }

    public void updateGimbalAngles() {
        if (level == null)
            return;

        if (ccGimbalTimeout > 0) {
            ccGimbalTimeout--;
        } else {
            ccGimbalX *= 0.8f;
            ccGimbalY *= 0.8f;
            ccGimbalZ *= 0.8f;
            if (Math.abs(ccGimbalX) < 0.001f) ccGimbalX = 0;
            if (Math.abs(ccGimbalY) < 0.001f) ccGimbalY = 0;
            if (Math.abs(ccGimbalZ) < 0.001f) ccGimbalZ = 0;
        }

        Direction nozzle = getThrustDirection();

        float gX = ccGimbalX;
        float gY = ccGimbalY;
        float gZ = ccGimbalZ;

        // Process analog inputs from standard directions & Linked Receivers on specific sides
        for (Direction dir : DIRECTIONS) {
            if (dir.getAxis() != nozzle.getAxis()) {
                // Add wireless signals received on this side
                float strength = 0;
                ItemStack f1 = frequencies1[dir.ordinal()];
                ItemStack f2 = frequencies2[dir.ordinal()];
                if (!f1.isEmpty() && !f2.isEmpty()) {
                    strength = (float) (dev.devce.rocketnautics.content.blocks.LinkedSignalHandler.getSignal(level, f1, f2, worldPosition) * 0.033f);
                }
                
                // Add analog redstone signal
                int signal = level.getSignal(worldPosition.relative(dir), dir.getOpposite());
                strength += signal * 0.033f;

                gX += dir.getStepX() * strength;
                gY += dir.getStepY() * strength;
                gZ += dir.getStepZ() * strength;
            }
        }

        gX = Math.max(-1.0f, Math.min(1.0f, gX));
        gY = Math.max(-1.0f, Math.min(1.0f, gY));
        gZ = Math.max(-1.0f, Math.min(1.0f, gZ));

        if (Math.abs(gimbalX - gX) > 0.001f || Math.abs(gimbalY - gY) > 0.001f || Math.abs(gimbalZ - gZ) > 0.001f) {
            gimbalX = gX;
            gimbalY = gY;
            gimbalZ = gZ;
            if (!level.isClientSide) {
                sendData();
                setChanged();
            }
        }
    }

    @Override
    public void setGimbal(double val1, double val2) {
        float xOffset = (float) (val1 / 180.0);
        float zOffset = (float) (val2 / 180.0);
        setComputerGimbal(xOffset, 0, zOffset);
    }

    @Override
    public void tick() {
        prevGimbalX = gimbalX;
        prevGimbalY = gimbalY;
        prevGimbalZ = gimbalZ;

        updateGimbalAngles();

        super.tick();

        thrust.update(
                getCurrentPower() * 50.0f,
                fuelThrottle,
                getGimbaledExhaustDirection(),
                isActive()
        );
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("GimbalX", gimbalX);
        tag.putFloat("GimbalY", gimbalY);
        tag.putFloat("GimbalZ", gimbalZ);
        tag.putFloat("CCGimbalX", ccGimbalX);
        tag.putFloat("CCGimbalY", ccGimbalY);
        tag.putFloat("CCGimbalZ", ccGimbalZ);
        
        for (int i = 0; i < 6; i++) {
            if (!frequencies1[i].isEmpty()) {
                tag.put("freq1_" + i, frequencies1[i].save(registries));
            }
            if (!frequencies2[i].isEmpty()) {
                tag.put("freq2_" + i, frequencies2[i].save(registries));
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        gimbalX = tag.getFloat("GimbalX");
        gimbalY = tag.getFloat("GimbalY");
        gimbalZ = tag.getFloat("GimbalZ");
        ccGimbalX = tag.getFloat("CCGimbalX");
        ccGimbalY = tag.getFloat("CCGimbalY");
        ccGimbalZ = tag.getFloat("CCGimbalZ");
        
        for (int i = 0; i < 6; i++) {
            if (tag.contains("freq1_" + i)) {
                frequencies1[i] = ItemStack.parse(registries, tag.getCompound("freq1_" + i)).orElse(ItemStack.EMPTY);
            } else {
                frequencies1[i] = ItemStack.EMPTY;
            }
            if (tag.contains("freq2_" + i)) {
                frequencies2[i] = ItemStack.parse(registries, tag.getCompound("freq2_" + i)).orElse(ItemStack.EMPTY);
            } else {
                frequencies2[i] = ItemStack.EMPTY;
            }
        }
    }

    @Override
    public void writeValues(String key, double... values) {
        if ("gimbal".equals(key) && values.length >= 2) {
            setComputerGimbal((float) values[0], 0.0f, (float) values[1]);
        }
    }

    public float getPrevGimbalX() {
        return prevGimbalX;
    }

    public float getPrevGimbalY() {
        return prevGimbalY;
    }

    public float getPrevGimbalZ() {
        return prevGimbalZ;
    }

    public float getGimbalX() {
        return gimbalX;
    }

    public float getGimbalY() {
        return gimbalY;
    }

    public float getGimbalZ() {
        return gimbalZ;
    }

    @Override
    public String getPeripheralType() {
        return "vector_engine";
    }
}
