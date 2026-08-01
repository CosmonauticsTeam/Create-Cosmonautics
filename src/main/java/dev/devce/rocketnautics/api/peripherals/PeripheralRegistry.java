package dev.devce.rocketnautics.api.peripherals;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;

/**
 * Thread-safe global registry for position-independent block/entity peripherals.
 * Peripherals register on load and unregister on unload/destruction.
 */
public class PeripheralRegistry {
    private static final Map<Level, Map<UUID, IPeripheral>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * Registers a peripheral in the given level.
     *
     * @param level      The level containing the peripheral.
     * @param peripheral The peripheral instance.
     */
    public static void register(Level level, IPeripheral peripheral) {
        if (level == null || peripheral == null) return;
        REGISTRY.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).put(peripheral.getUniqueId(), peripheral);
    }

    /**
     * Unregisters a peripheral from the given level.
     *
     * @param level      The level containing the peripheral.
     * @param peripheral The peripheral instance.
     */
    public static void unregister(Level level, IPeripheral peripheral) {
        if (level == null || peripheral == null) return;
        Map<UUID, IPeripheral> map = REGISTRY.get(level);
        if (map != null) {
            map.remove(peripheral.getUniqueId());
            if (map.isEmpty()) {
                REGISTRY.remove(level);
            }
        }
    }

    /**
     * Retrieves all active peripherals registered in the given level.
     *
     * @param level The level to query.
     * @return An unmodifiable collection of active peripherals.
     */
    public static Collection<IPeripheral> getPeripherals(Level level) {
        Map<UUID, IPeripheral> map = REGISTRY.get(level);
        return map != null ? Collections.unmodifiableCollection(map.values()) : Collections.emptyList();
    }

    /**
     * Finds a registered peripheral by its unique ID in the given level.
     *
     * @param level The level containing the peripheral.
     * @param id    The unique UUID of the peripheral.
     * @return The peripheral instance, or null if not registered.
     */
    public static IPeripheral getPeripheral(Level level, UUID id) {
        Map<UUID, IPeripheral> map = REGISTRY.get(level);
        return map != null ? map.get(id) : null;
    }

    /**
     * Finds a registered peripheral by its integer peripheral ID in the given level.
     *
     * @param level The level containing the peripheral.
     * @param id    The integer ID of the peripheral.
     * @return The peripheral instance, or null if not registered.
     */
    public static IPeripheral getPeripheralById(Level level, int id) {
        Map<UUID, IPeripheral> map = REGISTRY.get(level);
        if (map != null) {
            for (IPeripheral p : map.values()) {
                if (p.getPeripheralId() == id) {
                    return p;
                }
            }
        }
        return null;
    }
}
