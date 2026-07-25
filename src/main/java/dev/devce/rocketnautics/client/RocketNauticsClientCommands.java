package dev.devce.rocketnautics.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.RocketNauticsClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class RocketNauticsClientCommands {

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("rn")
            .then(Commands.literal("map")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null && dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isDeepSpace(mc.level)) {
                            mc.setScreen(new dev.devce.rocketnautics.client.ui.SystemMapScreen());
                        } else if (mc.player != null) {
                            mc.player.displayClientMessage(
                                Component.literal("Карту можно открыть только в Глубоком Космосе!").withStyle(ChatFormatting.RED), true);
                        }
                    });
                    return 1;
                })
            )
            .then(Commands.literal("debug")
                .executes(context -> {
                    showRenderInfo();
                    return 1;
                })
            )
            .then(Commands.literal("sonic")
                .then(Commands.literal("debug")
                    .executes(context -> {
                        SkyHandler.debugSonicBoom = !SkyHandler.debugSonicBoom;
                        String status = SkyHandler.debugSonicBoom ? "ENABLED" : "DISABLED";
                        ChatFormatting color = SkyHandler.debugSonicBoom ? ChatFormatting.GREEN : ChatFormatting.RED;
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("Sonic Boom Debug: ").append(Component.literal(status).withStyle(color)), true);
                        return 1;
                    })
                )
            );
            
        dispatcher.register(builder);
        
        // Register node library debug commands
        dev.devce.websnodelib.internal.WebsNodeCommands.register(dispatcher);
    }

    private static void showRenderInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean newState = !dev.devce.rocketnautics.RocketConfig.CLIENT.showDebugOverlay.get();
        dev.devce.rocketnautics.RocketConfig.CLIENT.showDebugOverlay.set(newState);
        dev.devce.rocketnautics.RocketConfig.CLIENT.showDebugOverlay.save();
        
        String status = newState ? "ENABLED" : "DISABLED";
        ChatFormatting color = newState ? ChatFormatting.GREEN : ChatFormatting.RED;
        
        mc.player.displayClientMessage(Component.literal("Cosmonautics Debug System: ")
            .append(Component.literal(status).withStyle(color))
            .append(Component.literal(" | Test build. Not done yet.").withStyle(ChatFormatting.GOLD)), true);
    }
}
