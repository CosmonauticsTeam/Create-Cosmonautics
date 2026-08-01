package dev.devce.rocketnautics.api.peripherals;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class EngineIdManager extends SavedData {
    public static final String ID = "rocketnautics_engine_ids";
    private int nextId = 0;

    public static EngineIdManager getInstance(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(
            new Factory<>(EngineIdManager::new, EngineIdManager::load, null), ID
        );
    }

    public EngineIdManager() {}

    public synchronized int getNextId() {
        int id = nextId++;
        setDirty();
        return id;
    }

    public static int getNextPeripheralId(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            if (server != null) {
                return getInstance(server).getNextId();
            }
        }
        return 0;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("nextId", nextId);
        return tag;
    }

    private static EngineIdManager load(CompoundTag tag, HolderLookup.Provider provider) {
        EngineIdManager manager = new EngineIdManager();
        manager.nextId = tag.getInt("nextId");
        return manager;
    }
}
