package dev.devce.rocketnautics.compat.computercraft.generic;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import dan200.computercraft.api.peripheral.PeripheralType;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlockEntity;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneMode;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static dev.devce.rocketnautics.RocketNautics.MODID;

public class GyrodyneMethods implements GenericPeripheral {
    @Override
    public @NonNull PeripheralType getType() {
        return PeripheralType.ofAdditional("gyrodyne");
    }

    @Override
    public @NonNull String id() {
        return MODID + ":gyrodyne";
    }

    @LuaFunction(mainThread = true)
    public final String getMode(GyrodyneBlockEntity gyrodyne) {
        return gyrodyne.getMode().getSerializedName();
    }

    @LuaFunction(mainThread = true)
    public final void setMode(GyrodyneBlockEntity gyrodyne, String modeName) throws LuaException {
        String lower = modeName.toLowerCase(Locale.ROOT);
        for (GyrodyneMode mode : GyrodyneMode.values()) {
            if (mode.getSerializedName().equalsIgnoreCase(lower)) {
                gyrodyne.setMode(mode);
                return;
            }
        }
        throw new LuaException("Invalid gyrodyne mode: " + modeName + ". Valid modes: " + String.join(", ", getAvailableModes(gyrodyne)));
    }

    @LuaFunction(mainThread = true)
    public final boolean isActive(GyrodyneBlockEntity gyrodyne) {
        return gyrodyne.isActive();
    }

    @LuaFunction(mainThread = true)
    public final int getId(GyrodyneBlockEntity gyrodyne) {
        return gyrodyne.getPeripheralId();
    }

    @LuaFunction(mainThread = true)
    public final java.util.Map<String, Float> getGimbalTilt(GyrodyneBlockEntity gyrodyne) {
        java.util.Map<String, Float> map = new java.util.HashMap<>();
        map.put("x", gyrodyne.getGimbalTiltX(1.0f));
        map.put("z", gyrodyne.getGimbalTiltZ(1.0f));
        return map;
    }

    @LuaFunction(mainThread = true)
    public final float getRotorSpeed(GyrodyneBlockEntity gyrodyne) {
        return gyrodyne.getRotorSpeed();
    }

    @LuaFunction(mainThread = true)
    public final List<String> getAvailableModes(GyrodyneBlockEntity gyrodyne) {
        return Arrays.stream(GyrodyneMode.values()).map(GyrodyneMode::getSerializedName).toList();
    }
}
