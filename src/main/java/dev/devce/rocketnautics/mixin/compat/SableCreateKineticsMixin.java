package dev.devce.rocketnautics.mixin.compat;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SableCommonEvents.class, remap = false)
public class SableCreateKineticsMixin {
    @Inject(method = "handleBlockChange", at = @At("TAIL"))
    private static void rocketnautics$refreshCreateKinetics(ServerLevel level, LevelChunk chunk, int x, int y, int z,
                                                            BlockState oldState, BlockState newState,
                                                            CallbackInfo ci) {
        if (oldState == newState)
            return;
        if (!rocketnautics$mayAffectKinetics(oldState) && !rocketnautics$mayAffectKinetics(newState))
            return;

        BlockPos changedPos = new BlockPos(x, y, z);
        level.getServer().execute(() -> {
            rocketnautics$refreshSableActor(level, changedPos);
            rocketnautics$refreshCreateKineticsAround(level, changedPos);
        });
    }

    @Unique
    private static boolean rocketnautics$mayAffectKinetics(BlockState state) {
        return state.getBlock() instanceof KineticBlock || state.hasBlockEntity();
    }

    @Unique
    private static void rocketnautics$refreshCreateKineticsAround(ServerLevel level, BlockPos changedPos) {
        rocketnautics$refreshCreateKineticsAt(level, changedPos);
        for (Direction direction : Direction.values()) {
            rocketnautics$refreshCreateKineticsAt(level, changedPos.relative(direction));
        }
    }

    @Unique
    private static void rocketnautics$refreshSableActor(ServerLevel level, BlockPos pos) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null)
            return;

        LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null)
            return;

        plot.onBlockChange(pos, level.getBlockState(pos));
    }

    @Unique
    private static void rocketnautics$refreshCreateKineticsAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos))
            return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof KineticBlockEntity kinetic))
            return;

        rocketnautics$refreshCreateKinetics(kinetic);

        if (blockEntity instanceof ExtraKinetics extraKinetics) {
            KineticBlockEntity internalKinetics = extraKinetics.getExtraKinetics();
            if (internalKinetics != null) {
                rocketnautics$refreshCreateKinetics(internalKinetics);
            }
        }
    }

    @Unique
    private static void rocketnautics$refreshCreateKinetics(KineticBlockEntity kinetic) {
        kinetic.warnOfMovement();
        kinetic.clearKineticInformation();
        kinetic.updateSpeed = true;

        if (kinetic instanceof GeneratingKineticBlockEntity generator) {
            generator.updateGeneratedRotation();
        }
    }
}
