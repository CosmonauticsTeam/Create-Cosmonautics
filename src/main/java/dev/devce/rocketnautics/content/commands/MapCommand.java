package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.devce.rocketnautics.network.OpenMapPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MapCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
            .then(Commands.literal("map")
                .executes(context -> {
                    if (context.getSource().isPlayer()) {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        if (dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isDeepSpace(player.level())) {
                            PacketDistributor.sendToPlayer(player, new OpenMapPayload());
                        } else {
                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Карту можно открыть только в Глубоком Космосе!"));
                        }
                    }
                    return 1;
                })
            )
        );
    }
}
