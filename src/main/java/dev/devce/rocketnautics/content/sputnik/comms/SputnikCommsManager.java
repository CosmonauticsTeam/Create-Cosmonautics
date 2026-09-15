package dev.devce.rocketnautics.content.sputnik.comms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages inter-satellite wireless communication channels across active Sputnik satellites.
 */
public final class SputnikCommsManager {

    public static class CommsPacket {
        public final int senderSputnikId;
        public final int channel;
        public final double data;
        public final long gameTime;

        public CommsPacket(int senderSputnikId, int channel, double data, long gameTime) {
            this.senderSputnikId = senderSputnikId;
            this.channel = channel;
            this.data = data;
            this.gameTime = gameTime;
        }
    }

    // channel -> latest CommsPacket
    private static final Map<Integer, CommsPacket> CHANNEL_PACKETS = new ConcurrentHashMap<>();

    private SputnikCommsManager() {}

    /**
     * Broadcasts a packet on a given channel from a specific satellite.
     */
    public static void send(int senderSputnikId, int channel, double data, long gameTime) {
        if (channel <= 0) return;
        CHANNEL_PACKETS.put(channel, new CommsPacket(senderSputnikId, channel, data, gameTime));
    }

    /**
     * Receives the latest packet on a channel, filtering out packets sent by the caller satellite itself.
     * Requires heartbeat (packet must be within the last 2 ticks).
     */
    public static CommsPacket receive(int mySputnikId, int channel, long currentGameTime) {
        if (channel <= 0) return null;
        CommsPacket packet = CHANNEL_PACKETS.get(channel);
        if (packet == null) return null;

        // Filter out own transmissions
        if (mySputnikId > 0 && packet.senderSputnikId == mySputnikId) {
            return null;
        }

        // Heartbeat check: expire if older than 2 ticks
        if (currentGameTime > 0 && packet.gameTime > 0 && (currentGameTime - packet.gameTime) > 2) {
            return null;
        }

        return packet;
    }

    /**
     * Peeks at the latest packet on a channel for UI / status display without filtering.
     */
    public static CommsPacket peek(int channel) {
        return CHANNEL_PACKETS.get(channel);
    }

    /**
     * Clears all packets transmitted by a satellite when it is destroyed or removed.
     */
    public static void onSputnikRemoved(int sputnikId) {
        if (sputnikId <= 0) return;
        List<Integer> toRemove = new ArrayList<>();
        for (var entry : CHANNEL_PACKETS.entrySet()) {
            if (entry.getValue().senderSputnikId == sputnikId) {
                toRemove.add(entry.getKey());
            }
        }
        for (int ch : toRemove) {
            CHANNEL_PACKETS.remove(ch);
        }
    }
}
