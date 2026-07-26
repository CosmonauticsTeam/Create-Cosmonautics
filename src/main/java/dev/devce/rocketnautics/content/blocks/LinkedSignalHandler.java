package dev.devce.rocketnautics.content.blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Real Bridge to Create's Wireless Redstone Network.
 * Improved with automatic cleanup for ghost signals and Sable coordinates projection.
 */
public class LinkedSignalHandler {

    private static final Map<Level, Map<LinkKey, VirtualLink>> links = new HashMap<>();
    private static long tickCounter = 0;

    public static void setSignal(Level level, ItemStack s1, ItemStack s2, BlockPos pos, double strength) {
        if (level.isClientSide) return;
        
        LinkKey key = new LinkKey(Couple.create(Frequency.of(s1), Frequency.of(s2)), pos, true);
        Map<LinkKey, VirtualLink> worldLinks = links.computeIfAbsent(level, k -> new HashMap<>());
        
        VirtualLink link = worldLinks.computeIfAbsent(key, k -> {
            VirtualLink newLink = new VirtualLink(level, key.couple, pos, true);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newLink);
            return newLink;
        });
        
        link.currentStrength = (int) Math.max(0, Math.min(15, strength));
        link.lastUpdateTick = tickCounter;
        Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, link);
    }

    public static double getSignal(Level level, ItemStack s1, ItemStack s2, BlockPos pos) {
        if (level.isClientSide) return 0;
        
        LinkKey key = new LinkKey(Couple.create(Frequency.of(s1), Frequency.of(s2)), pos, false);
        Map<LinkKey, VirtualLink> worldLinks = links.computeIfAbsent(level, k -> new HashMap<>());
        
        VirtualLink link = worldLinks.computeIfAbsent(key, k -> {
            VirtualLink newLink = new VirtualLink(level, key.couple, pos, false);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, newLink);
            return newLink;
        });
        
        link.lastUpdateTick = tickCounter;
        return link.receivedStrength;
    }

    public static void tick(Level level) {
        if (level.isClientSide) return;
        tickCounter++;
        
        Map<LinkKey, VirtualLink> worldLinks = links.get(level);
        if (worldLinks == null) return;
        
        Iterator<Map.Entry<LinkKey, VirtualLink>> it = worldLinks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LinkKey, VirtualLink> entry = it.next();
            VirtualLink link = entry.getValue();
            
            // If the link hasn't been updated for 10 ticks, it's considered dead
            if (tickCounter - link.lastUpdateTick > 10) {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
                it.remove();
            }
        }
    }

    public static void onWorldUnload(Level level) {
        links.remove(level);
    }

    private record LinkKey(Couple<Frequency> couple, BlockPos pos, boolean isTransmitter) {}

    private static class VirtualLink implements IRedstoneLinkable {
        private final Level level;
        private final Couple<Frequency> key;
        private final BlockPos pos;
        private final boolean isTransmitter;
        
        long lastUpdateTick = 0;
        int currentStrength = 0;
        int receivedStrength = 0;

        public VirtualLink(Level level, Couple<Frequency> key, BlockPos pos, boolean isTransmitter) {
            this.level = level;
            this.key = key;
            this.pos = pos;
            this.isTransmitter = isTransmitter;
        }

        @Override
        public int getTransmittedStrength() {
            return isTransmitter ? currentStrength : 0;
        }

        @Override
        public void setReceivedStrength(int power) {
            this.receivedStrength = power;
        }

        @Override
        public boolean isListening() {
            return !isTransmitter;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            // Project the block coordinates to actual world coordinates if inside a moving Sable sublevel.
            // This prevents Create from calculating 20M+ block distance attenuation.
            if (level != null) {
                dev.ryanhcode.sable.sublevel.SubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos);
                if (subLevel != null) {
                    org.joml.Vector3d realPos = new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    subLevel.logicalPose().transformPosition(realPos);
                    return new BlockPos((int) realPos.x, (int) realPos.y, (int) realPos.z);
                }
            }
            return pos;
        }
    }
}
