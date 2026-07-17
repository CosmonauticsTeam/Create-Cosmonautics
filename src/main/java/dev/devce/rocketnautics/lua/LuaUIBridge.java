package dev.devce.rocketnautics.lua;

import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.WElement;
import dev.devce.websnodelib.api.elements.*;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers a `ui` global table into a Lua Globals environment.
 * Lua scripts running inside a WNode can use this to add interactive UI
 * elements (buttons, sliders, labels, etc.) to the node, and read back
 * values from those elements every tick.
 *
 * <p>Usage example in a Lua node script:
 * <pre>
 *   -- Create a slider and button on the node
 *   local s = ui.slider("Gain", 0, 10, 80)
 *   local b = ui.button("Reset", 60)
 *   addElement(s)
 *   addElement(b)
 *
 *   -- Every tick:
 *   local gain = s.getValue()
 *   if b.wasClicked() then
 *       gain = 1.0
 *   end
 *   output("signal", input("raw") * gain)
 * </pre>
 * </p>
 *
 * <p>Elements are identified by the Lua table proxy object.  The bridge keeps
 * a registry of proxy → element so that the same element is reused across
 * ticks (no layout thrashing).</p>
 */
public class LuaUIBridge {

    /** A thin wrapper so Lua code can call addElement(proxy) */
    public static class LuaElement {
        public final LuaTable proxy;
        public final WElement element;

        public LuaElement(LuaTable proxy, WElement element) {
            this.proxy = proxy;
            this.element = element;
        }
    }

    /**
     * Injects a `ui` global and an `addElement` / `removeElements` global
     * into the given Lua globals instance.
     *
     * @param globals  The Lua globals to inject into.
     * @param node     The WNode that owns this script (used for element management).
     * @param registry Shared registry mapping proxy LuaTable → LuaElement, persisted
     *                 across ticks by the caller so elements survive recompilation.
     */
    public static void inject(Globals globals, WNode node, ConcurrentHashMap<LuaTable, LuaElement> registry) {

        LuaTable ui = new LuaTable();

        // ── ui.label(text [, colorHex]) ─────────────────────────────────
        ui.set("label", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String text  = args.arg(1).tojstring();
                int    color = args.narg() >= 2 ? args.arg(2).toint() : 0xFFFFFFFF;
                WLabel el    = new WLabel(text, color);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("label"));

                proxy.set("setText", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        el.setText(arg.tojstring());
                        return LuaValue.NIL;
                    }
                });
                proxy.set("getText", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        return LuaValue.valueOf(text);
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.button(label, width) ─────────────────────────────────────
        ui.set("button", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String label = args.arg(1).tojstring();
                int    w     = args.narg() >= 2 ? args.arg(2).toint() : 80;

                final boolean[] clicked = {false};
                WButton el = new WButton(label, w, () -> clicked[0] = true);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("button"));
                proxy.set("wasClicked", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        boolean c = clicked[0];
                        clicked[0] = false;          // consume the event
                        return LuaValue.valueOf(c);
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.checkbox(label) ──────────────────────────────────────────
        ui.set("checkbox", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String label = args.arg(1).tojstring();
                WCheckbox el = new WCheckbox(label);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("checkbox"));
                proxy.set("isChecked", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        return LuaValue.valueOf(el.isChecked());
                    }
                });
                proxy.set("setChecked", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        el.setChecked(arg.toboolean());
                        return LuaValue.NIL;
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.slider(label, min, max, width) ───────────────────────────
        ui.set("slider", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String label  = args.arg(1).tojstring();
                double min    = args.narg() >= 2 ? args.arg(2).todouble() : 0.0;
                double max    = args.narg() >= 3 ? args.arg(3).todouble() : 1.0;
                int    w      = args.narg() >= 4 ? args.arg(4).toint()    : 80;
                WSlider el    = new WSlider(label, min, max, w);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("slider"));
                proxy.set("getValue", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        return LuaValue.valueOf(el.getValue());
                    }
                });
                proxy.set("setValue", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        el.setValue(arg.todouble());
                        return LuaValue.NIL;
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.textfield(width) ─────────────────────────────────────────
        ui.set("textfield", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                int w      = args.narg() >= 1 ? args.arg(1).toint() : 80;
                WTextField el = new WTextField(w);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("textfield"));
                proxy.set("getValue", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        return LuaValue.valueOf(el.getValue());
                    }
                });
                proxy.set("setValue", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        el.setValue(arg.tojstring());
                        return LuaValue.NIL;
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.image(namespace, path, width, height) ────────────────────
        ui.set("image", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String ns   = args.arg(1).tojstring();
                String path = args.arg(2).tojstring();
                int    w    = args.narg() >= 3 ? args.arg(3).toint() : 64;
                int    h    = args.narg() >= 4 ? args.arg(4).toint() : 64;

                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(ns, path);
                WImage el = new WImage(loc, w, h);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("image"));
                // Images are static; proxy mostly serves as handle for addElement
                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.itempicker() ─────────────────────────────────────────────
        ui.set("itempicker", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                WItemPicker el = new WItemPicker();

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("itempicker"));
                proxy.set("getItem", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        if (el.getStack().isEmpty()) return LuaValue.NIL;
                        // Return the item registry name as a string
                        net.minecraft.world.item.Item item = el.getStack().getItem();
                        net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item> key =
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getResourceKey(item).orElse(null);
                        if (key == null) return LuaValue.NIL;
                        return LuaValue.valueOf(key.location().toString());
                    }
                });
                proxy.set("setItem", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        if (arg.isnil()) {
                            el.setStack(net.minecraft.world.item.ItemStack.EMPTY);
                            return LuaValue.NIL;
                        }
                        String name = arg.tojstring();
                        net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(name);
                        if (loc != null) {
                            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(loc);
                            if (item != net.minecraft.world.item.Items.AIR) {
                                el.setStack(new net.minecraft.world.item.ItemStack(item));
                            } else {
                                el.setStack(net.minecraft.world.item.ItemStack.EMPTY);
                            }
                        }
                        return LuaValue.NIL;
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        // ── ui.viewport(width, height) ──────────────────────────────────
        ui.set("viewport", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                int w = args.narg() >= 1 ? args.arg(1).toint() : 80;
                int h = args.narg() >= 2 ? args.arg(2).toint() : 80;
                WViewport3D el = new WViewport3D(w, h);

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("viewport"));
                proxy.set("setZoom", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        el.setZoom((float) arg.todouble());
                        return LuaValue.NIL;
                    }
                });
                proxy.set("setRotation", new ThreeArgFunction() {
                    @Override public LuaValue call(LuaValue rx, LuaValue ry, LuaValue rz) {
                        el.setGlobalRotation((float) rx.todouble(), (float) ry.todouble(), (float) rz.todouble());
                        return LuaValue.NIL;
                    }
                });
                proxy.set("addItem", new VarArgFunction() {
                    @Override public Varargs invoke(Varargs a) {
                        // addItem(namespace, path [, px, py, pz [, rx, ry, rz [, scale]]])
                        String ns   = a.arg(1).tojstring();
                        String path = a.arg(2).tojstring();
                        float  px   = a.narg() >= 3 ? (float)a.arg(3).todouble() : 0f;
                        float  py   = a.narg() >= 4 ? (float)a.arg(4).todouble() : 0f;
                        float  pz   = a.narg() >= 5 ? (float)a.arg(5).todouble() : 0f;
                        float  rrx  = a.narg() >= 6 ? (float)a.arg(6).todouble() : 0f;
                        float  rry  = a.narg() >= 7 ? (float)a.arg(7).todouble() : 0f;
                        float  rrz  = a.narg() >= 8 ? (float)a.arg(8).todouble() : 0f;
                        float  sc   = a.narg() >= 9 ? (float)a.arg(9).todouble() : 1f;

                        try {
                            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .get(ResourceLocation.fromNamespaceAndPath(ns, path));
                            el.addModel(
                                new net.minecraft.world.item.ItemStack(item),
                                new org.joml.Vector3f(px, py, pz),
                                new org.joml.Vector3f(rrx, rry, rrz),
                                sc
                            );
                        } catch (Exception ignore) {}
                        return LuaValue.NIL;
                    }
                });
                proxy.set("clear", new ZeroArgFunction() {
                    @Override public LuaValue call() {
                        el.clear();
                        return LuaValue.NIL;
                    }
                });

                registry.put(proxy, new LuaElement(proxy, el));
                return proxy;
            }
        });

        globals.set("ui", ui);

        // ── ui.gif(namespace, path, width, height) ──────────────────────
        // Note: WGif is client-only (it loads textures), so we only create it on the client side.
        ui.set("gif", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String ns   = args.arg(1).tojstring();
                String path = args.arg(2).tojstring();
                int    w    = args.narg() >= 3 ? args.arg(3).toint() : 64;
                int    h    = args.narg() >= 4 ? args.arg(4).toint() : 64;

                LuaTable proxy = new LuaTable();
                proxy.set("__type", LuaValue.valueOf("gif"));

                if (!net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer()) {
                    WGif el = new WGif(ResourceLocation.fromNamespaceAndPath(ns, path), w, h);
                    registry.put(proxy, new LuaElement(proxy, el));
                }
                return proxy;
            }
        });

        // ── addElement(proxy) ────────────────────────────────────────────
        // Called from Lua to attach an element to the node for this tick.
        // We track which elements are "desired" and reconcile with the node's
        // element list once before rendering (handled by the evaluator wrapper).
        globals.set("addElement", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                if (!(arg instanceof LuaTable proxy)) return LuaValue.NIL;
                LuaElement le = registry.get(proxy);
                if (le == null) return LuaValue.NIL;
                // Check if already on the node
                if (!node.getElements().contains(le.element)) {
                    node.addElement(le.element);
                }
                return LuaValue.NIL;
            }
        });

        // ── removeElement(proxy) ─────────────────────────────────────────
        globals.set("removeElement", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                if (!(arg instanceof LuaTable proxy)) return LuaValue.NIL;
                LuaElement le = registry.get(proxy);
                if (le == null) return LuaValue.NIL;
                node.getElements().remove(le.element);
                node.updateLayout();
                return LuaValue.NIL;
            }
        });
    }
}
