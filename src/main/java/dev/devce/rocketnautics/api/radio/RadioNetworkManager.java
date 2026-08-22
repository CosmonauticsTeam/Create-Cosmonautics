package dev.devce.rocketnautics.api.radio;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RadioNetworkManager extends SavedData {
    public static final String ID = "rocketnautics_radio_network";

    private final Map<UUID, VirtualRadioNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, Double> lastValues = new ConcurrentHashMap<>();
    private static MinecraftServer serverInstance = null;

    public static void setServer(MinecraftServer server) {
        serverInstance = server;
    }

    public static RadioNetworkManager getInstance(MinecraftServer server) {
        serverInstance = server;
        ServerLevel overworld = server.overworld();
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(
            new Factory<>(RadioNetworkManager::new, RadioNetworkManager::load, null), ID
        );
    }

    public static RadioNetworkManager getClientOrServerInstance() {
        return serverInstance != null ? getInstance(serverInstance) : null;
    }

    public RadioNetworkManager() {}

    public void registerOrUpdateNode(UUID id, double frequency, String script, Long deepSpaceInstanceId, double x, double y, double z, String dimension) {
        VirtualRadioNode node = nodes.computeIfAbsent(id, k -> new VirtualRadioNode(id, frequency));
        node.setFrequency(frequency);
        node.setLuaScript(script);
        
        CompoundTag data = node.getCustomData();
        if (deepSpaceInstanceId != null) {
            data.putLong("dsInstanceId", deepSpaceInstanceId);
        } else {
            data.remove("dsInstanceId");
        }
        data.putDouble("lastX", x);
        data.putDouble("lastY", y);
        data.putDouble("lastZ", z);
        data.putString("lastDim", dimension);
        
        setDirty();
    }

    public void removeNode(UUID id) {
        if (nodes.remove(id) != null) {
            setDirty();
        }
    }

    public VirtualRadioNode getNode(UUID id) {
        return nodes.get(id);
    }

    public double getLastValue(double frequency, double code) {
        return lastValues.getOrDefault(frequency + ":" + code, 0.0);
    }

    /**
     * Sends a radio packet to all virtual nodes matching the frequency.
     */
    public void sendPacket(double frequency, double code, double value) {
        lastValues.put(frequency + ":" + code, value);
        for (VirtualRadioNode node : nodes.values()) {
            if (node.getFrequency() == frequency) {
                executeAutoResponse(node, code, value);
            }
        }
    }

    private void executeAutoResponse(VirtualRadioNode node, double code, double value) {
        String script = node.getLuaScript();
        if (script == null || script.isBlank()) return;

        try {
            Globals globals = dev.devce.rocketnautics.lua.LuaSandbox.createSandboxedGlobals();

            // Injected variables
            globals.set("packetCode", LuaValue.valueOf(code));
            globals.set("packetValue", LuaValue.valueOf(value));

            // reply(replyCode, replyValue)
            globals.set("reply", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue arg1, LuaValue arg2) {
                    double replyCode = arg1.todouble();
                    double replyVal = arg2.todouble();
                    // Send packet back asynchronously on the same frequency
                    sendPacket(node.getFrequency(), replyCode, replyVal);
                    return LuaValue.NIL;
                }
            });

            // getDeepSpacePosition() -> returns x, y, z
            globals.set("getDeepSpacePosition", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (serverInstance != null && node.getCustomData().contains("dsInstanceId")) {
                        long instId = node.getCustomData().getLong("dsInstanceId");
                        var dsData = dev.devce.rocketnautics.content.orbit.DeepSpaceData.getInstance(serverInstance);
                        var inst = dsData.getInstance(instId);
                        if (inst != null && !inst.isCorrupted()) {
                            var pos = inst.getPosition().getCurrentPosition();
                            return varargsOf(new LuaValue[]{
                                LuaValue.valueOf(pos.getX()),
                                LuaValue.valueOf(pos.getY()),
                                LuaValue.valueOf(pos.getZ())
                            });
                        }
                    }
                    return varargsOf(new LuaValue[]{
                        LuaValue.valueOf(node.getCustomData().getDouble("lastX")),
                        LuaValue.valueOf(node.getCustomData().getDouble("lastY")),
                        LuaValue.valueOf(node.getCustomData().getDouble("lastZ"))
                    });
                }
            });

            // getDeepSpaceVelocity() -> returns vx, vy, vz
            globals.set("getDeepSpaceVelocity", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (serverInstance != null && node.getCustomData().contains("dsInstanceId")) {
                        long instId = node.getCustomData().getLong("dsInstanceId");
                        var dsData = dev.devce.rocketnautics.content.orbit.DeepSpaceData.getInstance(serverInstance);
                        var inst = dsData.getInstance(instId);
                        if (inst != null && !inst.isCorrupted()) {
                            var vel = inst.getPosition().getCurrentPVCoords().getVelocity();
                            return varargsOf(new LuaValue[]{
                                LuaValue.valueOf(vel.getX()),
                                LuaValue.valueOf(vel.getY()),
                                LuaValue.valueOf(vel.getZ())
                            });
                        }
                    }
                    return varargsOf(new LuaValue[]{ LuaValue.ZERO, LuaValue.ZERO, LuaValue.ZERO });
                }
            });

            // getDeepSpaceFrame() -> returns string
            globals.set("getDeepSpaceFrame", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    if (serverInstance != null && node.getCustomData().contains("dsInstanceId")) {
                        long instId = node.getCustomData().getLong("dsInstanceId");
                        var dsData = dev.devce.rocketnautics.content.orbit.DeepSpaceData.getInstance(serverInstance);
                        var inst = dsData.getInstance(instId);
                        if (inst != null && !inst.isCorrupted()) {
                            return LuaValue.valueOf(inst.getPosition().getFrame().getName());
                        }
                    }
                    return LuaValue.valueOf(node.getCustomData().getString("lastDim"));
                }
            });

            LuaValue chunk = globals.load(script);
            chunk.call();
        } catch (Throwable t) {
            // Silently catch errors in background scripts to prevent server crashes
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (VirtualRadioNode node : nodes.values()) {
            list.add(node.save());
        }
        tag.put("nodes", list);
        return tag;
    }

    private static RadioNetworkManager load(CompoundTag tag, HolderLookup.Provider provider) {
        RadioNetworkManager manager = new RadioNetworkManager();
        if (tag.contains("nodes")) {
            ListTag list = tag.getList("nodes", 10);
            for (int i = 0; i < list.size(); i++) {
                VirtualRadioNode node = VirtualRadioNode.load(list.getCompound(i));
                manager.nodes.put(node.getId(), node);
            }
        }
        return manager;
    }
}
