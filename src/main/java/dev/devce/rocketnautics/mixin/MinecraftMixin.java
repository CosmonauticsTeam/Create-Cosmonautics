package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.RocketNauticsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    private long clientTickCount;

    /** The deep space dimension key, cached to avoid allocating on every tick. */
    private static final ResourceLocation DEEP_SPACE_DIM =
            ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "deep_space");
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

    /**
     * Prevents vanilla's MusicManager from playing overworld ambient tracks in the
     * deep_space dimension. When the player is in deep space, we return a silent Music
     * object with an effectively infinite delay, so vanilla's manager never queues
     * anything — our {@link dev.devce.rocketnautics.client.DeepSpaceMusicManager}
     * takes full control instead.
     */
    @Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$suppressVanillaMusicInDeepSpace(CallbackInfoReturnable<Music> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.level != null && DEEP_SPACE_DIM.equals(mc.level.dimension().location())) {
            // Return a no-op Music with an astronomically large delay so vanilla's
            // MusicManager never naturally starts a song. Our DeepSpaceMusicManager
            // plays tracks independently through SoundManager directly.
            cir.setReturnValue(new Music(
                    SoundEvents.MUSIC_GAME,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    true  // replaceCurrentMusic=true: stops any currently-playing vanilla track immediately
            ));
        }
    }
}
