package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketNautics;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side handler for rendering atmospheric reentry heat effects.
 * Manages a heat map of positions and spawns particles (flame and smoke) 
 * around ships that are undergoing reentry.
 */
@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class HeatClientHandler {
    private static final Map<Vec3, Float> HEAT_MAP = new HashMap<>();

    public static Map<Vec3, Float> getHeatMap() {
        return HEAT_MAP;
    }

    /**
     * Updates or adds a heat source at the specified coordinates.
     */
    public static void updateHeat(double x, double y, double z, float intensity) {
        if (intensity >= 0.99f) {
            dev.devce.rocketnautics.client.render.ReentryClientRenderer.clearCache();
        }
        HEAT_MAP.put(new Vec3(x, y, z), intensity);
    }

    private static final Map<UUID, Vector3d> PREVIOUS_POSITIONS = new HashMap<>();

    /**
     * Ticks the heat sources, decays intensity, and triggers particle spawning.
     */
    @SubscribeEvent
    public static void onClientTick(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide()) return;
        
        ClientLevel level = (ClientLevel) event.getLevel();
        if (level == null) {
            HEAT_MAP.clear();
            PREVIOUS_POSITIONS.clear();
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        // Auto-generate reentry heat for ships traveling extremely fast in the upper atmosphere
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            for (SubLevel sl : container.getAllSubLevels()) {
                if (sl instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel clientShip) {
                    Vector3d currentPos = clientShip.logicalPose().position();
                    
                    // Track velocity via position delta
                    Vector3d prevPos = PREVIOUS_POSITIONS.get(clientShip.getUniqueId());
                    if (prevPos != null) {
                        double deltaDist = currentPos.distance(prevPos);
                        double currentY = currentPos.y();

                        // Altitude check: between 1000 and 5000 blocks
                        if (currentY > 1000 && currentY < 5000) {
                            // In real life sonic boom is ~17 blocks/tick, but in Minecraft engine speeds are lower.
                            // Start effect at 2.5 blocks/tick (50 m/s), max out at 6.0 blocks/tick (120 m/s)
                            if (deltaDist > 2.5) {
                                float speedFactor = (float) ((deltaDist - 2.5) / 3.5);
                                speedFactor = Math.min(1.0f, Math.max(0.0f, speedFactor));
                                
                                // Smoothly fade in/out based on altitude edges (1000-1500 and 4500-5000)
                                float altFactor = 1.0f;
                                if (currentY < 1500) {
                                    altFactor = (float) ((currentY - 1000) / 500.0);
                                } else if (currentY > 4500) {
                                    altFactor = (float) ((5000 - currentY) / 500.0);
                                }
                                
                                float finalIntensity = speedFactor * altFactor;
                                if (finalIntensity > 0.05f) {
                                    updateHeat(currentPos.x(), currentPos.y(), currentPos.z(), finalIntensity);
                                }
                            }
                        }
                    }
                    PREVIOUS_POSITIONS.put(clientShip.getUniqueId(), currentPos);
                }
            }
            
            // Clean up old positions for removed ships
            List<UUID> activeIds = container.getAllSubLevels().stream().map(SubLevel::getUniqueId).toList();
            PREVIOUS_POSITIONS.keySet().retainAll(activeIds);
        }

        // Iterate through active heat sources
        HEAT_MAP.entrySet().removeIf(entry -> {
            Vec3 targetPos = entry.getKey();
            float intensity = entry.getValue();

            // Find the ship (SubLevel) that matches this heat source position
            SubLevel matchingSubLevel = null;
            double minDist = 25.0; 
            
            for (SubLevel sl : container.getAllSubLevels()) {
                Vector3d pos = sl.logicalPose().position();
                double dist = targetPos.distanceToSqr(pos.x, pos.y, pos.z);
                if (dist < minDist) {
                    minDist = dist;
                    matchingSubLevel = sl;
                }
            }

            // If a ship is found, spawn particles and decay intensity
            if (matchingSubLevel != null && intensity > 0.05f) {
                spawnHeatParticles(level, matchingSubLevel, intensity);
                entry.setValue(intensity * 0.95f); // Decay over time
                return false;
            }
            return true; // Remove entry if intensity is too low or ship is gone
        });
    }

    /**
     * Spawns reentry particles (blue flames and smoke) around the ship.
     */
    private static void spawnHeatParticles(ClientLevel level, SubLevel subLevel, float intensity) {
        // Disabled legacy particles in favor of custom 3D shader reentry plasma
    }
}
