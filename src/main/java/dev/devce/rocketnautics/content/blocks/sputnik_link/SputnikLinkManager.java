package dev.devce.rocketnautics.content.blocks.sputnik_link;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages global unique sequential IDs and signal communication for Sputnik Link blocks.
 */
public final class SputnikLinkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SputnikLinkManager.class);
    private static final Object LOCK = new Object();

    // Active block entities on the server
    private static final Map<Integer, SputnikLinkBlockEntity> ACTIVE_LINKS = new ConcurrentHashMap<>();

    public static class TransmittedData {
        public final int sputnikId;
        public int strength;
        public long lastGameTime;

        public TransmittedData(int sputnikId, int strength, long lastGameTime) {
            this.sputnikId = sputnikId;
            this.strength = strength;
            this.lastGameTime = lastGameTime;
        }
    }

    // Cached signals for quick lookup across server & client (linkId -> TransmittedData)
    private static final Map<Integer, TransmittedData> TRANSMITTED_DATA = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> RECEIVED_SIGNALS = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> LINK_MODES = new ConcurrentHashMap<>();

    private SputnikLinkManager() {}

    /**
     * Allocates a persistent unique sequential ID (1, 2, 3...) for newly placed Sputnik Link blocks.
     */
    public static int allocateId(MinecraftServer server) {
        if (server == null) return 1;
        synchronized (LOCK) {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("sputnik");
            try {
                Files.createDirectories(dir);
            } catch (IOException ignored) {}

            Path counterFile = dir.resolve("link_id_counter.dat");
            int currentId = 1;

            if (Files.exists(counterFile)) {
                try {
                    String str = Files.readString(counterFile, StandardCharsets.UTF_8).trim();
                    currentId = Integer.parseInt(str);
                } catch (Exception ignored) {}
            }

            int allocated = currentId;
            try {
                Files.writeString(counterFile, String.valueOf(currentId + 1), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error("[SputnikLinkManager] Failed to update link id counter", e);
            }
            return allocated;
        }
    }

    public static void registerLink(SputnikLinkBlockEntity be) {
        if (be == null || be.getLinkId() <= 0) return;
        ACTIVE_LINKS.put(be.getLinkId(), be);
        LINK_MODES.put(be.getLinkId(), be.isReceiverMode());
    }

    public static void unregisterLink(SputnikLinkBlockEntity be) {
        if (be == null || be.getLinkId() <= 0) return;
        ACTIVE_LINKS.remove(be.getLinkId(), be);
        RECEIVED_SIGNALS.remove(be.getLinkId());
        LINK_MODES.remove(be.getLinkId());
    }

    public static SputnikLinkBlockEntity getLink(int linkId) {
        return ACTIVE_LINKS.get(linkId);
    }

    /**
     * Called by Sputnik graph to send an analog/digital signal to a Sputnik Link in receiver mode.
     */
    public static void setTransmittedSignal(int sputnikId, int linkId, int strength, long gameTime) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped <= 0) {
            TRANSMITTED_DATA.remove(linkId);
        } else {
            TRANSMITTED_DATA.put(linkId, new TransmittedData(sputnikId, clamped, gameTime));
        }

        SputnikLinkBlockEntity be = ACTIVE_LINKS.get(linkId);
        if (be != null && be.isReceiverMode()) {
            be.setEmittedSignal(clamped);
        }
    }

    public static void setTransmittedSignal(int linkId, int strength) {
        setTransmittedSignal(0, linkId, strength, 0L);
    }

    public static int getTransmittedSignal(int linkId, long currentGameTime) {
        TransmittedData data = TRANSMITTED_DATA.get(linkId);
        if (data == null) return 0;
        // If currentGameTime > 0 and data is older than 2 ticks, the satellite has stopped transmitting or is broken
        if (currentGameTime > 0 && data.lastGameTime > 0 && (currentGameTime - data.lastGameTime) > 2) {
            return 0;
        }
        return data.strength;
    }

    public static int getTransmittedSignal(int linkId) {
        TransmittedData data = TRANSMITTED_DATA.get(linkId);
        return data != null ? data.strength : 0;
    }

    /**
     * Called when a Sputnik is removed/broken to immediately cut off all signals transmitted by it.
     */
    public static void onSputnikRemoved(int sputnikId) {
        if (sputnikId <= 0) return;
        List<Integer> affectedLinks = new ArrayList<>();
        for (var entry : TRANSMITTED_DATA.entrySet()) {
            if (entry.getValue().sputnikId == sputnikId) {
                affectedLinks.add(entry.getKey());
            }
        }
        for (int linkId : affectedLinks) {
            TRANSMITTED_DATA.remove(linkId);
            SputnikLinkBlockEntity be = ACTIVE_LINKS.get(linkId);
            if (be != null && be.isReceiverMode()) {
                be.setEmittedSignal(0);
            }
        }
    }

    /**
     * Called by Sputnik Link in transmitter mode to report redstone signal received from the world.
     */
    public static void setReceivedSignal(int linkId, int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        RECEIVED_SIGNALS.put(linkId, clamped);
    }

    /**
     * Called by Sputnik graph to read redstone signal from a Sputnik Link in transmitter mode.
     */
    public static int getReceivedSignal(int linkId) {
        SputnikLinkBlockEntity be = ACTIVE_LINKS.get(linkId);
        if (be != null) {
            return be.getReceivedSignal();
        }
        return RECEIVED_SIGNALS.getOrDefault(linkId, 0);
    }

    public static void updateMode(int linkId, boolean isReceiver) {
        LINK_MODES.put(linkId, isReceiver);
    }

    public static boolean isReceiver(int linkId) {
        SputnikLinkBlockEntity be = ACTIVE_LINKS.get(linkId);
        if (be != null) {
            return be.isReceiverMode();
        }
        return LINK_MODES.getOrDefault(linkId, true);
    }
}
