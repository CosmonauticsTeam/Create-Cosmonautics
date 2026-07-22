package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.RocketNauticsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.LongSupplier;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$onSetScreen(Screen screen, CallbackInfo ci) {
        if (RocketNauticsClient.seamlessTransitionTicks > 0) {
            if (screen instanceof ReceivingLevelScreen) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void rocketnautics$onTick(CallbackInfo ci) {
        if (RocketNauticsClient.seamlessTransitionTicks > 0) {
            RocketNauticsClient.seamlessTransitionTicks--;
        }
    }
}
