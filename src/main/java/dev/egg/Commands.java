package dev.egg;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;
import org.joml.Vector3fc;

import java.util.*;

import static dev.egg.SubLevelWarper.WarpSubLevel;

public class Commands {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event){
        DimensionCommand.register(event.getDispatcher());
    }
    public static class DimensionCommand {
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
            dispatcher.register(net.minecraft.commands.Commands.literal("sable")
                .requires(source -> source.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("dimension_set")
                .then(net.minecraft.commands.Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                .then(net.minecraft.commands.Commands.argument("dimension", DimensionArgument.dimension())
                        .executes(DimensionCommand::executeNoPosition)
                .then(net.minecraft.commands.Commands.argument("position", Vec3Argument.vec3())
                    .executes(DimensionCommand::execute))))));
        }
        private static int executeNoPosition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

            ServerLevel dimension = DimensionArgument.getDimension(ctx, "dimension");

            CommandSourceStack source = ctx.getSource();
            ServerSubLevel sublevel = SubLevelArgumentType.getSingleSubLevel(ctx, "sub_level");

            int warpCount = WarpSubLevel(sublevel, dimension);

            source.sendSuccess(()->Component.literal("Teleported " + warpCount + " sublevels to " + dimension.dimension().location().getPath()), false);
            return Command.SINGLE_SUCCESS;
        }
        private static int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

            ServerLevel dimension = DimensionArgument.getDimension(ctx, "dimension");
            Vec3 position = Vec3Argument.getVec3(ctx, "position");

            CommandSourceStack source = ctx.getSource();
            ServerSubLevel sublevel = SubLevelArgumentType.getSingleSubLevel(ctx, "sub_level");

            int warpCount = WarpSubLevel(sublevel, dimension, new Vector3d(position.x, position.y, position.z));

            source.sendSuccess(()->Component.literal("Teleported " + warpCount + " sublevels to " + dimension.dimension().location().getPath()), false);
            return Command.SINGLE_SUCCESS;
        }
    }
}
