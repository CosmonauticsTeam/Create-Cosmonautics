package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.devce.rocketnautics.RocketConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;

public class MagneticStabilizerBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
    private static final Vector3d temp = new Vector3d();

    public MagneticStabilizerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!getBlockState().getValue(MagneticStabilizerBlock.POWERED)) return;
        double mass = subLevel.getMassTracker().getMass();
        if (mass <= 0) return;
        handle.getAngularVelocity(temp);
        double strength = RocketConfig.SERVER.magneticStabilizerStrength.getAsDouble();
        if (temp.lengthSquared() * mass * mass > timeStep * timeStep * strength * strength) {
            temp.normalize(-timeStep * strength);
            subLevel.logicalPose().orientation().transformInverse(temp);
            handle.applyAngularImpulse(temp);
        } else {
            handle.addLinearAndAngularVelocity(JOMLConversion.ZERO, temp.negate());
        }
    }
}
