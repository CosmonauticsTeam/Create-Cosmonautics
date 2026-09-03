package dev.devce.rocketnautics.content.blocks.mfd.programs;

import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import dev.devce.rocketnautics.content.blocks.mfd.MFDInputManager;
import dev.devce.rocketnautics.content.blocks.mfd.MFDProgram;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CartridgeProgram implements MFDProgram {

    private final String cartridgeId;
    private Globals globals;
    private long lastFileModified = -1;
    private String lastError = null;

    private final Map<String, LuaTable> imageTableCache = new ConcurrentHashMap<>();

    public CartridgeProgram(String cartridgeId) {
        this.cartridgeId = cartridgeId;
    }

    @Override
    public String getName() {
        return "Cartridge: " + cartridgeId;
    }

    private LuaTable loadImageTable(String path) {
        return imageTableCache.computeIfAbsent(path, p -> {
            Path file = CartridgeManager.getCartridgeDir(cartridgeId).resolve(p);
            if (!Files.exists(file)) {
                file = CartridgeManager.getCartridgeDir(cartridgeId).resolve("assets").resolve(p);
            }
            if (!Files.exists(file)) return null;

            try (InputStream is = Files.newInputStream(file)) {
                BufferedImage bImg = ImageIO.read(is);
                if (bImg == null) return null;
                int w = bImg.getWidth();
                int h = bImg.getHeight();
                int[] raw = new int[w * h];
                bImg.getRGB(0, 0, w, h, raw, 0, w);

                LuaTable table = new LuaTable();
                table.set("w", LuaValue.valueOf(w));
                table.set("h", LuaValue.valueOf(h));

                LuaTable pixels = new LuaTable();
                for (int i = 0; i < raw.length; i++) {
                    pixels.set(i + 1, LuaValue.valueOf(raw[i]));
                }
                table.set("pixels", pixels);
                return table;
            } catch (IOException e) {
                return null;
            }
        });
    }

    private void ensureInitialized(MFDCanvas canvas, MFDBlockEntity be) {
        Path cartridgeDir = CartridgeManager.getCartridgeDir(cartridgeId);
        Path mainLua = cartridgeDir.resolve("main.lua");
        if (!Files.exists(mainLua)) {
            lastError = "main.lua not found in " + cartridgeId;
            return;
        }

        try {
            long modified = Files.getLastModifiedTime(mainLua).toMillis();
            if (globals == null || modified > lastFileModified) {
                lastFileModified = modified;
                imageTableCache.clear();
                globals = JsePlatform.standardGlobals();

                globals.finder = (filename) -> {
                    String clean = filename.endsWith(".lua") ? filename : filename + ".lua";
                    Path t = cartridgeDir.resolve(clean);
                    if (Files.exists(t)) {
                        try { return Files.newInputStream(t); } catch (IOException ignored) {}
                    }
                    Path a = cartridgeDir.resolve("assets").resolve(clean);
                    if (Files.exists(a)) {
                        try { return Files.newInputStream(a); } catch (IOException ignored) {}
                    }
                    return null;
                };

                bindApi(globals, canvas);

                String script = Files.readString(mainLua);
                LuaValue chunk = globals.load(script, "main.lua");
                chunk.call();

                LuaValue initFunc = globals.get("init");
                if (initFunc.isfunction()) {
                    initFunc.call();
                }
                lastError = null;
            }
        } catch (LuaError | IOException e) {
            lastError = e.getMessage();
        }
    }

    private void bindApi(Globals g, MFDCanvas canvas) {
        LuaValue setPixelFunc = new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue x, LuaValue y, LuaValue col) {
                int px = x.toint();
                int py = y.toint();
                if (px >= 0 && px < 64 && py >= 0 && py < 64) {
                    int c = (int) col.tolong();
                    if ((c & 0xFF000000) == 0) {
                        c |= 0xFF000000;
                    }
                    canvas.setPixelFast(px, py, c);
                }
                return NIL;
            }
        };

        LuaValue clearFunc = new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue col) {
                int c = (int) col.tolong();
                if ((c & 0xFF000000) == 0) {
                    c |= 0xFF000000;
                }
                canvas.clear(c);
                return NIL;
            }
        };

        LuaValue getImageFunc = new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue pathVal) {
                LuaTable t = loadImageTable(pathVal.tojstring());
                return t != null ? t : NIL;
            }
        };

        LuaValue isDownFunc = new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue keyVal) {
                return LuaValue.valueOf(MFDInputManager.isKeyDown(keyVal.tojstring()));
            }
        };

        g.set("setPixel", setPixelFunc);
        g.set("clear", clearFunc);
        g.set("getImage", getImageFunc);
        g.set("isDown", isDownFunc);

        LuaTable input = new LuaTable();
        input.set("isDown", isDownFunc);
        g.set("input", input);
    }

    private void updateInput(Globals g, MFDBlockEntity be) {
        if (g == null || be == null) return;
        LuaValue inputVal = g.get("input");
        if (!inputVal.istable()) return;
        LuaTable input = (LuaTable) inputVal;

        input.set("w", LuaValue.valueOf(MFDInputManager.isKeyDown("w")));
        input.set("s", LuaValue.valueOf(MFDInputManager.isKeyDown("s")));
        input.set("a", LuaValue.valueOf(MFDInputManager.isKeyDown("a")));
        input.set("d", LuaValue.valueOf(MFDInputManager.isKeyDown("d")));
        input.set("space", LuaValue.valueOf(MFDInputManager.isKeyDown("space")));
        input.set("shift", LuaValue.valueOf(MFDInputManager.isKeyDown("shift")));
        input.set("up", LuaValue.valueOf(MFDInputManager.isKeyDown("up")));
        input.set("down", LuaValue.valueOf(MFDInputManager.isKeyDown("down")));
        input.set("left", LuaValue.valueOf(MFDInputManager.isKeyDown("left")));
        input.set("right", LuaValue.valueOf(MFDInputManager.isKeyDown("right")));
        input.set("enter", LuaValue.valueOf(MFDInputManager.isKeyDown("enter")));
        input.set("z", LuaValue.valueOf(MFDInputManager.isKeyDown("z")));
        input.set("x", LuaValue.valueOf(MFDInputManager.isKeyDown("x")));
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        ensureInitialized(canvas, blockEntity);

        if (lastError != null) {
            canvas.clear(0xFF180A0A);
            canvas.drawString("Lua Error:", 2, 4, 0xFFFF4444);
            canvas.drawString(lastError.length() > 20 ? lastError.substring(0, 20) : lastError, 2, 14, 0xFFFFAAAA);
            return;
        }

        if (globals != null) {
            updateInput(globals, blockEntity);
            LuaValue updateFunc = globals.get("update");
            if (updateFunc.isfunction()) {
                try {
                    updateFunc.call();
                } catch (LuaError e) {
                    lastError = e.getMessage();
                }
            }
        }
    }
}
