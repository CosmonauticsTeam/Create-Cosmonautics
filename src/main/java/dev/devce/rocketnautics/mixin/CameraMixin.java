package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.devce.rocketnautics.client.IFreeMotionCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin implements IFreeMotionCamera {
    @Shadow
    private static @Final Vector3f FORWARDS;

    @Shadow
    private static @Final Vector3f UP;

    @Shadow
    private static @Final Vector3f LEFT;

    @Shadow
    private @Final Vector3f forwards;

    @Shadow
    private @Final Vector3f up;

    @Shadow
    private @Final Vector3f left;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Unique
    private float zRot;

    @Shadow
    private @Final Quaternionf rotation;

    @Shadow
    abstract void move(float x, float y, float z);

    @Shadow
    abstract void setPosition(Vec3 pos);

    @Shadow
    abstract void setPosition(double xPos, double yPos, double zPos);

    @Shadow
    abstract void setRotation(float yRot, float xRot);

    @Override
    public float rocketnautics$getZRot() { return this.zRot; }

    @Override
    public void rocketnautics$setZRot(float zRot) { this.zRot = zRot; rotation.rotationZ(zRot); }

    @Override
    public void rocketnautics$setRotation(float yRot, float xRot, float zRot) {
        this.yRot = yRot;
        this.xRot = xRot;
        this.zRot = zRot;

        rotation.rotationYXZ(yRot, xRot, zRot);
    }

    @Override
    public Vector3f rocketnautics$getRotation() {
        return new Vector3f(this.xRot, this.yRot, this.zRot);
    }

    @Inject(method = "setup", at = @At("HEAD"))
    private void initCameraRotations(CallbackInfo ci) {
        this.rocketnautics$setZRot(0.0f);
    }

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = Shift.AFTER, ordinal = 0))
    private void rocketnautics$apply6DOFCamera(BlockGetter level, Entity cameraEntity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!(cameraEntity instanceof FreeMotionEntity fme)) return;

        this.rotation.set(fme.getOrientation());

        FORWARDS.rotate(this.rotation, this.forwards);
        UP.rotate(this.rotation, this.up);
        LEFT.rotate(this.rotation, this.left);
    }
}
