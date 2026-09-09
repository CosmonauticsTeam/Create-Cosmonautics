package dev.devce.rocketnautics.lua;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaSandboxTest {
    @Test
    void initializesBit32WithoutExposingPackageApis() {
        Globals globals = LuaSandbox.createSandboxedGlobals();

        assertEquals(1, globals.get("bit32").get("band").call(LuaValue.valueOf(3), LuaValue.valueOf(1)).checkint());
        assertTrue(globals.get("package").isnil());
        assertTrue(globals.get("require").isnil());
        assertTrue(globals.get("os").isnil());
        assertTrue(globals.get("io").isnil());
        assertTrue(globals.get("luajava").isnil());
        assertTrue(globals.get("debug").isnil());
        assertTrue(globals.get("string").get("dump").isnil());
    }
}
