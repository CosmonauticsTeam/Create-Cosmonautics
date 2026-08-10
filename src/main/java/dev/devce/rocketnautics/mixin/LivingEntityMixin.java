package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.FreeMotionHandler;
import dev.devce.rocketnautics.content.orbit.universe.PlanetDimensionData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements FreeMotionEntity {

    @Override
    public boolean is6DOFEnabled() { return this.rocketnautics$6DOFEnabled; }

    @Override
    public void set6DOFEnabled(boolean enabled) { rocketnautics$6DOFEnabled = enabled; }

    @Unique
    private boolean rocketnautics$6DOFEnabled;

    @Override
    public boolean isAmbulant() { return this.rocketnautics$ambulant; }

    @Override
    public void setAmbulant(boolean ambulant) { rocketnautics$ambulant = ambulant; }

    @Unique
    private boolean rocketnautics$ambulant;

    @Override
    public float getMovementAcceleration() { return rocketnautics$movementAcceleration; }

    @Override
    public void setMovementAcceleration(float accel) { rocketnautics$movementAcceleration = accel; }

    @Unique
    private float rocketnautics$movementAcceleration = (float)RocketConfig.SERVER.jetpackThrust.getAsDouble();

    @Override
    public float getDampenerForce() { return rocketnautics$dampenerForce; }

    @Override
    public void setDampenerForce(float force) { rocketnautics$dampenerForce = force; }

    @Unique
    private float rocketnautics$dampenerForce;

    @Override
    public Quaternionf getOrientation() { return rocketnautics$orientation; }

    @Override
    public void setOrientation(Quaternionf quat) { rocketnautics$orientation = quat; }

    @Unique
    private Quaternionf rocketnautics$orientation = new Quaternionf();

    public LivingEntityMixin(EntityType<?> p_19870_, Level p_19871_) {
        super(p_19870_, p_19871_);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", ordinal = 3))
    private void rocketnautics$adjustDragByPressure(LivingEntity instance, double x, double y, double z, Operation<Void> original, @Local(ordinal = 1) Vec3 vec35, @Local(ordinal = 1) float f3, @Local(ordinal = 1) double d2) {
        int limit = RocketConfig.SERVER.entitySpeedLimit.get();
        // note that delta movement is in m/t not m/s, so we multiply our speed squared by a correction factor of 400 (20 * 20)
        if (!instance.onGround() && 400 * (x * x + y * y + z * z) <= limit * limit) {
            double pressure;
            if (DeepSpaceHelper.isDeepSpace(level())) {
                pressure = 0;
            } else {
                pressure = DeepSpaceHelper.getDataForDimension(instance.level()).map(PlanetDimensionData::entityDragMultiplier).orElse(PlanetDimensionData.EMPTY_BEZIER).evaluateFunction(instance.getY());
            }
            if (pressure != 1) {
                double drag1 = 1 - (1 - f3) * pressure;
                double drag2 = 1 - 0.02 * pressure;
                original.call(instance, vec35.x * drag1, this instanceof FlyingAnimal ? d2 * drag1 : d2 * drag2, vec35.z * drag1);
                return;
            }
        }
        original.call(instance, x, y, z);
    }

    @Shadow
    public abstract SoundEvent getFallDamageSound(int distance);

    @Shadow
    public abstract float getSpeed();

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$6DOFMovement(Vec3 motionIn, CallbackInfo ci) {
        if (FreeMotionHandler.apply6DOFPhysics(motionIn.toVector3f(), (LivingEntity)(Object)this)) ci.cancel();
    }
}
