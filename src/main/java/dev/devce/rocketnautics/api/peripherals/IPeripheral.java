package dev.devce.rocketnautics.api.peripherals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface IPeripheral {
    String getPeripheralType();
    BlockPos getBlockPos();
    Level getLevel();
    boolean isRemoved();
    
    java.util.UUID getUniqueId();

    default int getPeripheralId() { return -1; }

    /**
     * Get a value from the peripheral.
     * @param key The data key (e.g., "altitude", "velocity", "thrust")
     * @return The value, or 0 if not found.
     */
    default double readValue(String key) { return 0; }
    
    /**
     * Write a value to the peripheral.
     * @param key The data key
     * @param value The value to set
     */
    default void writeValue(String key, double value) {}

    /**
     * Write multiple values (e.g., for gimbal pitch/yaw)
     */
    default void writeValues(String key, double... values) {}
}
