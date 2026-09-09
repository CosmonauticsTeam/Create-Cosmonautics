package dev.devce.rocketnautics.content.blocks.mfd;

import dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui.MFDFocusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
public class MFDInputManager {

    private static final Set<String> ACTIVE_KEYS = ConcurrentHashMap.newKeySet();
    private static BlockPos activeFocusPos = null;

    public static void setFocusedPos(BlockPos pos) {
        activeFocusPos = pos;
        if (pos == null) {
            ACTIVE_KEYS.clear();
        }
    }

    public static BlockPos getFocusedPos() {
        return activeFocusPos;
    }

    public static boolean isFocused(BlockPos pos) {
        return activeFocusPos != null && activeFocusPos.equals(pos);
    }

    public static void onKeyDown(int keyCode, int scanCode) {
        String keyName = getKeyName(keyCode);
        if (keyName != null) {
            ACTIVE_KEYS.add(keyName.toLowerCase());
        }
    }

    public static void onKeyUp(int keyCode, int scanCode) {
        String keyName = getKeyName(keyCode);
        if (keyName != null) {
            ACTIVE_KEYS.remove(keyName.toLowerCase());
        }
    }

    public static boolean isKeyDown(String key) {
        if (key == null) return false;
        return ACTIVE_KEYS.contains(key.toLowerCase());
    }

    public static void clear() {
        ACTIVE_KEYS.clear();
        activeFocusPos = null;
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof MFDFocusScreen) {
            if (event.getName().equals(ResourceLocation.withDefaultNamespace("crosshair"))) {
                event.setCanceled(true);
            }
        }
    }

    public static String getKeyName(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_W -> "w";
            case GLFW.GLFW_KEY_A -> "a";
            case GLFW.GLFW_KEY_S -> "s";
            case GLFW.GLFW_KEY_D -> "d";
            case GLFW.GLFW_KEY_SPACE -> "space";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "alt";
            case GLFW.GLFW_KEY_ENTER -> "enter";
            case GLFW.GLFW_KEY_TAB -> "tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "backspace";
            case GLFW.GLFW_KEY_UP -> "up";
            case GLFW.GLFW_KEY_DOWN -> "down";
            case GLFW.GLFW_KEY_LEFT -> "left";
            case GLFW.GLFW_KEY_RIGHT -> "right";
            case GLFW.GLFW_KEY_Z -> "z";
            case GLFW.GLFW_KEY_X -> "x";
            case GLFW.GLFW_KEY_C -> "c";
            case GLFW.GLFW_KEY_V -> "v";
            case GLFW.GLFW_KEY_E -> "e";
            case GLFW.GLFW_KEY_Q -> "q";
            case GLFW.GLFW_KEY_R -> "r";
            case GLFW.GLFW_KEY_F -> "f";
            case GLFW.GLFW_KEY_0 -> "0";
            case GLFW.GLFW_KEY_1 -> "1";
            case GLFW.GLFW_KEY_2 -> "2";
            case GLFW.GLFW_KEY_3 -> "3";
            case GLFW.GLFW_KEY_4 -> "4";
            case GLFW.GLFW_KEY_5 -> "5";
            case GLFW.GLFW_KEY_6 -> "6";
            case GLFW.GLFW_KEY_7 -> "7";
            case GLFW.GLFW_KEY_8 -> "8";
            case GLFW.GLFW_KEY_9 -> "9";
            default -> {
                String str = GLFW.glfwGetKeyName(keyCode, 0);
                yield str != null ? str : "key_" + keyCode;
            }
        };
    }
}
