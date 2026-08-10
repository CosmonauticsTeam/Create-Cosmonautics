package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.api.FreeMotionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    private final boolean wasOnGround = true;

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique
    private boolean transitioned = true;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$freeLook(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (!(player instanceof FreeMotionEntity fme)) return;
        Quaternionf q = fme.getOrientation();

        double dx = this.accumulatedDX;
        double dy = this.accumulatedDY;

        this.accumulatedDX = 0;
        this.accumulatedDY = 0;

        double sensitivity = mc.options.sensitivity().get();

        double dYaw = dx * sensitivity * 0.01;
        double dPitch = dy * sensitivity * 0.01;

        if (fme.is6DOFEnabled()) {
            // set 6DOF orientation to follow the mouse
            q.rotateYXZ((float)-dYaw, (float)-dPitch, 0.0f).normalize();

            Vector3d forward = new Vector3d(0, 0, -1);
            q.transform(forward);

            double yaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
            double pitch = -Math.toDegrees(Math.asin(forward.y));

            // set vanilla orientation in the background
            player.setYRot((float)yaw);
            player.setXRot((float)pitch);

            transitioned = false;
        } else {
            // restore vanilla camera
            double yaw = player.getYRot() + Math.toDegrees(dYaw);
            double pitch = Math.clamp(player.getXRot() + Math.toDegrees(dPitch), -90.0f, 90.0f); // clamp pitch to vanilla limits

            player.setYRot((float)yaw);
            player.setXRot((float)pitch);

            Quaternionf target = new Quaternionf()
                    .rotationYXZ(
                            (float)Math.toRadians(180.0f - yaw),
                            (float)Math.toRadians(-pitch),
                            0.0f
                    ).normalize();

            float dot = Math.clamp(q.normalize().dot(target), -1.0f, 1.0f);

            if (!transitioned && Math.abs(dot) < 0.999f) {
                q.slerp(target, 0.1f);
            } else {
                q.set(target);
                transitioned = true;
            }
        }

        ci.cancel();
    }
}
