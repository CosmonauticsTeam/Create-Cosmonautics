package dev.devce.rocketnautics.content.solar;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.SableSubLevelLightingHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles solar magnification hazard when looking at the Sun with a spyglass in Deep Space.
 */
@EventBusSubscriber(modid = RocketNautics.MODID)
public class SolarBurnHandler {

    private static int soundCooldown = 0;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.isScoping()) return;
        if (!DeepSpaceHelper.isDeepSpace(player.level())) return;

        Vec3 look = player.getViewVector(1.0f);
        Vec3 sunDir;
        if (player.level().isClientSide) {
            float sx = SableSubLevelLightingHandler.getSunX();
            float sy = SableSubLevelLightingHandler.getSunY();
            float sz = SableSubLevelLightingHandler.getSunZ();
            sunDir = new Vec3(sx, sy, sz).normalize();
        } else {
            sunDir = new Vec3(0.577, 0.707, 0.408).normalize();
        }

        if (look.dot(sunDir) > 0.982) {
            // Concentrated solar radiation ignites and damages the player
            player.igniteForSeconds(4);

            if (player.level().isClientSide) {
                if (player == net.minecraft.client.Minecraft.getInstance().player) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.devce.rocketnautics.network.SolarBurnPayload());
                }

                Vec3 eyePos = player.getEyePosition();
                Vec3 pPos = eyePos.add(look.scale(0.3));
                player.level().addParticle(ParticleTypes.LAVA, pPos.x, pPos.y, pPos.z, 0, 0.05, 0);
                player.level().addParticle(ParticleTypes.FLAME, pPos.x, pPos.y, pPos.z, 0, 0.05, 0);
                player.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pPos.x, pPos.y, pPos.z, 0, 0.08, 0);

                if (soundCooldown <= 0) {
                    player.level().playLocalSound(
                        pPos.x, pPos.y, pPos.z,
                        SoundEvents.FIRECHARGE_USE,
                        SoundSource.PLAYERS,
                        1.2f, 0.8f, false
                    );
                    player.displayClientMessage(
                        Component.literal("Direct solar magnification hazard!"),
                        true
                    );
                    soundCooldown = 15;
                } else {
                    soundCooldown--;
                }
            } else {
                player.hurt(player.damageSources().inFire(), 3.0f);
            }
        }
    }
}
