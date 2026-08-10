package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.api.FreeMotionEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void rocketnautics$rotatePlayerHead(LivingEntity player, float limbSwing, float limbSwingAmmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (((FreeMotionEntity)player).is6DOFEnabled()) {
            PlayerModel<LivingEntity> model = (PlayerModel<LivingEntity>)(Object)this;
            model.getHead().setRotation(-(float)Math.toRadians(45), model.getHead().yRot, model.getHead().zRot);
        }
    }
}
