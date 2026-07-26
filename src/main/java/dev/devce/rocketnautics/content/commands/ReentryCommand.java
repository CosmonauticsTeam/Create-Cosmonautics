package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ReentryCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("reentry")
                .executes(context -> {
                    GlobalSpacePhysicsHandler.reentryDebugEnabled = !GlobalSpacePhysicsHandler.reentryDebugEnabled;
                    boolean active = GlobalSpacePhysicsHandler.reentryDebugEnabled;
                    context.getSource().sendSuccess(() -> Component.literal("Reentry debug mode " + (active ? "ENABLED" : "DISABLED") + " (forcing reentry render on all ships)"), true);
                    return 1;
                })
            )
        );
    }
}
