package dev.devce.rocketnautics.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlockEntity;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GyrodynePeripheral implements IPeripheral {
    private final GyrodyneBlockEntity gyrodyne;

    public GyrodynePeripheral(GyrodyneBlockEntity gyrodyne) {
        this.gyrodyne = gyrodyne;
    }

    @NotNull
    @Override
    public String getType() {
        return "gyrodyne";
    }

    @Override
    public Object getTarget() {
        return gyrodyne;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof GyrodynePeripheral && ((GyrodynePeripheral) other).gyrodyne == this.gyrodyne;
    }

    @LuaFunction(mainThread = true)
    public final String getMode() {
        return gyrodyne.getMode().getSerializedName();
    }

    @LuaFunction(mainThread = true)
    public final int getModeIndex() {
        return gyrodyne.getMode().ordinal();
    }

    @LuaFunction(mainThread = true)
    public final void setMode(IArguments args) throws LuaException {
        if (args.count() == 0) {
            throw new LuaException("Expected mode name or index");
        }
        Object first = args.get(0);
        if (first instanceof Number num) {
            int index = num.intValue();
            GyrodyneMode[] modes = GyrodyneMode.values();
            if (index < 0 || index >= modes.length) {
                throw new LuaException("Invalid mode index: " + index + ". Expected 0 to " + (modes.length - 1));
            }
            gyrodyne.setMode(modes[index]);
            return;
        }
        String modeName = args.getString(0).toLowerCase(Locale.ROOT);
        for (GyrodyneMode mode : GyrodyneMode.values()) {
            if (mode.getSerializedName().equalsIgnoreCase(modeName)) {
                gyrodyne.setMode(mode);
                return;
            }
        }
        throw new LuaException("Invalid gyrodyne mode: " + modeName + ". Valid modes: " + String.join(", ", getAvailableModes()));
    }

    @LuaFunction(mainThread = true)
    public final String cycleMode() {
        GyrodyneMode[] modes = GyrodyneMode.values();
        int next = (gyrodyne.getMode().ordinal() + 1) % modes.length;
        gyrodyne.setMode(modes[next]);
        return modes[next].getSerializedName();
    }

    @LuaFunction(mainThread = true)
    public final List<String> getAvailableModes() {
        return Arrays.stream(GyrodyneMode.values()).map(GyrodyneMode::getSerializedName).toList();
    }

    @LuaFunction(mainThread = true)
    public final boolean isActive() {
        return gyrodyne.isActive();
    }

    @LuaFunction(mainThread = true)
    public final int getId() {
        return gyrodyne.getPeripheralId();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Float> getGimbalTilt() {
        Map<String, Float> map = new HashMap<>();
        map.put("x", gyrodyne.getGimbalTiltX(1.0f));
        map.put("z", gyrodyne.getGimbalTiltZ(1.0f));
        return map;
    }

    @LuaFunction(mainThread = true)
    public final float getRotorSpeed() {
        return gyrodyne.getRotorSpeed();
    }

    @LuaFunction(mainThread = true)
    public final void setOff() {
        gyrodyne.setMode(GyrodyneMode.OFF);
    }

    @LuaFunction(mainThread = true)
    public final void setSas() {
        gyrodyne.setMode(GyrodyneMode.SAS);
    }

    @LuaFunction(mainThread = true)
    public final void setHold() {
        gyrodyne.setMode(GyrodyneMode.HOLD);
    }

    @LuaFunction(mainThread = true)
    public final void setPrograde() {
        gyrodyne.setMode(GyrodyneMode.PROGRADE);
    }

    @LuaFunction(mainThread = true)
    public final void setRetrograde() {
        gyrodyne.setMode(GyrodyneMode.RETROGRADE);
    }

    @LuaFunction(mainThread = true)
    public final void setNormal() {
        gyrodyne.setMode(GyrodyneMode.NORMAL);
    }

    @LuaFunction(mainThread = true)
    public final void setAntinormal() {
        gyrodyne.setMode(GyrodyneMode.ANTINORMAL);
    }

    @LuaFunction(mainThread = true)
    public final void setRadialIn() {
        gyrodyne.setMode(GyrodyneMode.RADIAL_IN);
    }

    @LuaFunction(mainThread = true)
    public final void setRadialOut() {
        gyrodyne.setMode(GyrodyneMode.RADIAL_OUT);
    }

    @LuaFunction(mainThread = true)
    public final void setHorizon() {
        gyrodyne.setMode(GyrodyneMode.HORIZON);
    }

    @LuaFunction(mainThread = true)
    public final void setSun() {
        gyrodyne.setMode(GyrodyneMode.SUN);
    }
}
