package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.content.physics.SubLevelFuelMassTracker;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.physics.mass.MergedMassTracker;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.util.SableMathUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

@Mixin(value = MergedMassTracker.class, remap = false)
public abstract class MergedMassTrackerMixin {

    @Shadow @Final private MassTracker selfTracker;
    @Shadow @Final private ServerSubLevel subLevel;
    @Shadow private double mass;
    @Shadow @Final private Matrix3d inertiaTensor;
    @Shadow private double inverseMass;
    @Shadow @Final private Matrix3d inverseInertiaTensor;
    @Shadow private @Nullable Vector3d centerOfMass;

    @Shadow protected abstract void uploadData();
    @Shadow protected abstract void setPreviousValues();

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$updateWithFuelMass(float partialPhysicsTick, CallbackInfo ci) {
        if (!RocketConfig.SERVER.fuelMassEnabled.get() || RocketConfig.SERVER.massMultiplier.get() <= 0.0) {
            return;
        }
        if (this.selfTracker.getCenterOfMass() == null) {
            return;
        }

        ci.cancel();

        final Collection<KinematicContraption> contraptions = this.subLevel.getPlot().getContraptions();

        this.mass = this.selfTracker.getMass();
        this.centerOfMass = this.selfTracker.getCenterOfMass().mul(this.mass, new Vector3d());

        for (final KinematicContraption contraption : contraptions) {
            final MassTracker contraptionMassData = contraption.sable$getMassTracker();
            this.mass = this.mass + contraptionMassData.getMass();
            this.centerOfMass.fma(contraptionMassData.getMass(), contraption.sable$getPosition(partialPhysicsTick));
        }

        final List<SubLevelFuelMassTracker.TankFuel> fuels = SubLevelFuelMassTracker.getFuelData(this.subLevel);
        for (final SubLevelFuelMassTracker.TankFuel fuel : fuels) {
            this.mass += fuel.mass();
            this.centerOfMass.fma(fuel.mass(), fuel.centerPos());
        }

        if (this.mass > 0.0) {
            this.centerOfMass.mul(1.0 / this.mass);
        }

        this.inertiaTensor.set(this.selfTracker.getInertiaTensor());
        final Vector3d localShift = this.centerOfMass.sub(this.selfTracker.getCenterOfMass(), new Vector3d());

        if (localShift.lengthSquared() > 0.0) {
            SableMathUtils.fmaInertiaTensor(localShift, this.selfTracker.getMass(), this.inertiaTensor);
        }

        for (final KinematicContraption contraption : contraptions) {
            final MassTracker contraptionMassData = contraption.sable$getMassTracker();

            final Vector3d localPos = contraption.sable$getPosition(partialPhysicsTick).sub(this.centerOfMass, new Vector3d());
            SableMathUtils.fmaInertiaTensor(localPos, contraptionMassData.getMass(), this.inertiaTensor);

            final Quaterniond contraptionOrientation = contraption.sable$getOrientation(partialPhysicsTick);

            final Matrix3d localInertiaTensor = new Matrix3d()
                    .rotateLocal(contraptionOrientation.conjugate(new Quaterniond()))
                    .mulLocal(contraptionMassData.getInertiaTensor())
                    .rotateLocal(contraptionOrientation);

            this.inertiaTensor.add(localInertiaTensor);
        }

        for (final SubLevelFuelMassTracker.TankFuel fuel : fuels) {
            final Vector3d localPos = fuel.centerPos().sub(this.centerOfMass, new Vector3d());
            SableMathUtils.fmaInertiaTensor(localPos, fuel.mass(), this.inertiaTensor);

            double w = fuel.width();
            double h = fuel.height();
            Matrix3d fuelInertia = new Matrix3d();
            fuelInertia.m00 = (w * w + h * h) * fuel.mass() / 12.0;
            fuelInertia.m11 = (w * w + w * w) * fuel.mass() / 12.0;
            fuelInertia.m22 = (w * w + h * h) * fuel.mass() / 12.0;
            this.inertiaTensor.add(fuelInertia);
        }

        this.inverseMass = 1.0 / this.mass;
        this.inertiaTensor.invert(this.inverseInertiaTensor);

        this.uploadData();
        this.setPreviousValues();
    }
}
