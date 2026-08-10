package dev.devce.rocketnautics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.api.FreeMotionEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$rotatePlayer(AbstractClientPlayer player, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        if(!(player instanceof FreeMotionEntity fme)) return;
        if(!fme.is6DOFEnabled()) return;

        Quaternionf quat = new Quaternionf(fme.getOrientation()).rotateX(-(float)Math.PI/2);
        poseStack.mulPose(quat);
        poseStack.translate(0, -1,  0);
        Quaternionf invQuat = new Quaternionf(quat).invert();
        Vector3f offset = new Vector3f(0, 0.3f, 0);
        offset = invQuat.transform(offset);

        poseStack.translate(offset.x, offset.y, offset.z);

        ci.cancel();
    }
}
