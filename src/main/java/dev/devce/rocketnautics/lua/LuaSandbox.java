package dev.devce.rocketnautics.lua;

import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

/**
 * Hardened, sandboxed Lua environment.
 * Prevents sandbox escapes, RCE via package.loaded/os/io/luajava/debug,
 * filesystem access via dofile/loadfile, and bytecode exploitation via string.dump.
 */
public final class LuaSandbox {

    private LuaSandbox() {}

    /**
     * Creates a fully sandboxed and isolated Lua Globals instance.
     */
    public static Globals createSandboxedGlobals() {
        Globals globals = new Globals();

        // 1. Load only safe standard libraries
        globals.load(new JseBaseLib());
        // Bit32Lib registers itself in package.loaded during initialization.
        globals.load(new PackageLib());
        globals.load(new Bit32Lib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());

        // 2. Install parser and compiler
        LoadState.install(globals);
        LuaC.install(globals);

        // 3. Remove dangerous I/O and reflection APIs from global scope
        globals.set("dofile", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
        globals.set("module", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("package", LuaValue.NIL);
        globals.set("os", LuaValue.NIL);
        globals.set("io", LuaValue.NIL);
        globals.set("luajava", LuaValue.NIL);
        globals.set("debug", LuaValue.NIL);

        // 4. Disable string.dump to prevent dumping and loading unsafe bytecode
        LuaValue stringLib = globals.get("string");
        if (stringLib.istable()) {
            stringLib.set("dump", LuaValue.NIL);
        }

        return globals;
    }
}
