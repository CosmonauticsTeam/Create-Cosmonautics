package dev.devce.rocketnautics.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDPrograms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MFDPeripheral implements IPeripheral {
    private final MFDBlockEntity mfd;

    public MFDPeripheral(MFDBlockEntity mfd) {
        this.mfd = mfd;
    }

    @NotNull
    @Override
    public String getType() {
        return "mfd";
    }

    @Override
    public Object getTarget() {
        return mfd;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MFDPeripheral && ((MFDPeripheral) other).mfd == this.mfd;
    }

    @LuaFunction(mainThread = true)
    public final String getProgram() {
        return mfd.getProgramName();
    }

    @LuaFunction(mainThread = true)
    public final String getMode() {
        return mfd.getProgramName();
    }

    @LuaFunction(mainThread = true)
    public final int getProgramIndex() {
        return mfd.getProgramIndex();
    }

    @LuaFunction(mainThread = true)
    public final int getModeIndex() {
        return mfd.getProgramIndex();
    }

    @LuaFunction(mainThread = true)
    public final void setProgram(IArguments args) throws LuaException {
        if (args.count() == 0) {
            throw new LuaException("Expected program name or index");
        }
        Object first = args.get(0);
        if (first instanceof Number num) {
            mfd.setProgramIndex(num.intValue());
            return;
        }
        String name = args.getString(0);
        int idx = MFDPrograms.findIndexByName(name);
        if (idx >= 0) {
            mfd.setProgramIndex(idx);
            return;
        }
        throw new LuaException("Invalid program name: " + name + ". Valid programs: " + String.join(", ", getAvailablePrograms()));
    }

    @LuaFunction(mainThread = true)
    public final void setMode(IArguments args) throws LuaException {
        setProgram(args);
    }

    @LuaFunction(mainThread = true)
    public final List<String> getAvailablePrograms() {
        return MFDPrograms.getNames();
    }

    @LuaFunction(mainThread = true)
    public final List<String> getAvailableModes() {
        return MFDPrograms.getNames();
    }

    @LuaFunction(mainThread = true)
    public final void cycleProgram(IArguments args) throws LuaException {
        int delta = (args.count() > 0) ? (int) args.optLong(0, 1) : 1;
        mfd.cycleProgram(delta);
    }

    @LuaFunction(mainThread = true)
    public final void cycleMode(IArguments args) throws LuaException {
        cycleProgram(args);
    }

    @LuaFunction(mainThread = true)
    public final void setFdai() {
        mfd.setProgramByName("FDAI");
    }

    @LuaFunction(mainThread = true)
    public final void setHorizon() {
        mfd.setProgramByName("HORIZON");
    }

    @LuaFunction(mainThread = true)
    public final void setAltSpeed() {
        mfd.setProgramByName("ALT_SPEED");
    }

    @LuaFunction(mainThread = true)
    public final void setVideo() {
        mfd.setExternalVideoMode();
    }

    @LuaFunction(mainThread = true)
    public final void setExternalVideoMode() {
        mfd.setExternalVideoMode();
    }

    @LuaFunction(mainThread = true)
    public final void setGif() {
        mfd.setProgramByName("GIF");
    }

    @LuaFunction(mainThread = true)
    public final boolean isExternalVideoMode() {
        return mfd.isExternalVideoMode();
    }

    @LuaFunction(mainThread = true)
    public final boolean hasCartridge() {
        return mfd.hasCartridge();
    }

    @LuaFunction(mainThread = true)
    public final String getCartridgeId() {
        return mfd.getActiveCartridgeId();
    }

    @LuaFunction(mainThread = true)
    public final boolean ejectCartridge() {
        return !mfd.ejectCartridge().isEmpty();
    }

    @LuaFunction(mainThread = true)
    public final int getWidth() {
        return 64;
    }

    @LuaFunction(mainThread = true)
    public final int getHeight() {
        return 64;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Integer> getSize() {
        Map<String, Integer> map = new HashMap<>();
        map.put("width", 64);
        map.put("height", 64);
        return map;
    }

    @LuaFunction(mainThread = true)
    public final void clear(IArguments args) throws LuaException {
        int color = (args.count() > 0) ? parseColor(args.optLong(0, 0)) : 0xFF000000;
        mfd.clearExternalVideo(color);
    }

    @LuaFunction(mainThread = true)
    public final void setPixel(int x, int y, long color) {
        mfd.setExternalPixel(x, y, parseColor(color));
    }

    @LuaFunction(mainThread = true)
    public final int getPixel(int x, int y) {
        return mfd.getExternalPixel(x, y);
    }

    @LuaFunction(mainThread = true)
    public final void drawLine(int x0, int y0, int x1, int y1, long color) {
        mfd.drawExternalLine(x0, y0, x1, y1, parseColor(color));
    }

    @LuaFunction(mainThread = true)
    public final void drawRect(int x, int y, int w, int h, long color) {
        mfd.drawExternalRect(x, y, w, h, parseColor(color));
    }

    @LuaFunction(mainThread = true)
    public final void fillRect(int x, int y, int w, int h, long color) {
        mfd.fillExternalRect(x, y, w, h, parseColor(color));
    }

    @LuaFunction(mainThread = true)
    public final void drawCircle(IArguments args) throws LuaException {
        if (args.count() < 4) {
            throw new LuaException("Expected: drawCircle(x, y, radius, color, [filled])");
        }
        int cx = args.getInt(0);
        int cy = args.getInt(1);
        int r = args.getInt(2);
        int color = parseColor(args.getLong(3));
        boolean filled = args.count() >= 5 && args.getBoolean(4);
        mfd.drawExternalCircle(cx, cy, r, color, filled);
    }

    @LuaFunction(mainThread = true)
    public final void drawString(String text, int x, int y, long color) {
        mfd.drawExternalString(text, x, y, parseColor(color));
    }

    @LuaFunction(mainThread = true)
    public final void drawPixels(int startX, int startY, int w, int h, Map<?, ?> pixels) {
        int idx = 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Object val = pixels.get(idx++);
                if (val instanceof Number n) {
                    mfd.setExternalPixel(startX + x, startY + y, parseColor(n.longValue()));
                }
            }
        }
    }

    @LuaFunction(mainThread = true)
    public final void drawFrame(Map<?, ?> table) {
        int[] buffer = new int[64 * 64];
        if (table.size() == 64 && table.containsKey(1) && table.get(1) instanceof Map) {
            for (int y = 0; y < 64; y++) {
                Object rowObj = table.get(y + 1);
                if (rowObj instanceof Map<?, ?> rowMap) {
                    for (int x = 0; x < 64; x++) {
                        Object val = rowMap.get(x + 1);
                        if (val instanceof Number n) {
                            buffer[y * 64 + x] = parseColor(n.longValue());
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i < 64 * 64; i++) {
                Object val = table.get(i + 1);
                if (val instanceof Number n) {
                    buffer[i] = parseColor(n.longValue());
                }
            }
        }
        mfd.setExternalVideoBuffer(buffer);
    }

    @LuaFunction(mainThread = true)
    public final void drawFrameHex(String hex) throws LuaException {
        if (hex == null) return;
        int[] buffer = new int[64 * 64];
        int stride = hex.length() >= 64 * 64 * 8 ? 8 : (hex.length() >= 64 * 64 * 6 ? 6 : 0);
        if (stride > 0) {
            for (int i = 0; i < 64 * 64; i++) {
                int start = i * stride;
                if (start + stride <= hex.length()) {
                    try {
                        long val = Long.parseLong(hex.substring(start, start + stride), 16);
                        buffer[i] = parseColor(val);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            mfd.setExternalVideoBuffer(buffer);
        } else {
            throw new LuaException("Hex string too short. Expected at least " + (64 * 64 * 6) + " hex characters for RRGGBB");
        }
    }

    @LuaFunction(mainThread = true)
    public final void flush() {
        mfd.flushExternalVideo();
    }

    @LuaFunction(mainThread = true)
    public final void update() {
        mfd.flushExternalVideo();
    }

    private int parseColor(long col) {
        int c = (int) col;
        if ((c & 0xFF000000) == 0) {
            c |= 0xFF000000;
        }
        return c;
    }
}
