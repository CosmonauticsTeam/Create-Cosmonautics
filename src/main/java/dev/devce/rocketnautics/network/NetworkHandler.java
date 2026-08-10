package dev.devce.rocketnautics.network;

import com.mojang.datafixers.util.Either;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.devce.rocketnautics.api.orbit.ColorPalette;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.FreeMotionHandler;
import dev.devce.rocketnautics.client.SkyHandler;
import dev.devce.rocketnautics.content.items.JetpackItem;
import dev.devce.rocketnautics.content.items.LegThrustersItem;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpaceTextureDefinition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.concurrent.CompletableFuture;

public class NetworkHandler {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(RocketNautics.MODID).versioned("1.0");

        registrar.playToClient(
            FreeMotionSetupPayload.TYPE,
            FreeMotionSetupPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleFreeMotionSetup(context, payload))
        );

        registrar.playToServer(
            FreeMotionPayload.TYPE,
            FreeMotionPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleFreeMotionMovement(context, payload))
        );

        registrar.playToClient(
            FreeMotionSyncPayload.TYPE,
            FreeMotionSyncPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleFreeMotionClientSync(payload))
        );

        registrar.playToServer(
            JetpackTogglePayload.TYPE,
            JetpackTogglePayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> JetpackItem.toggle((ServerPlayer) context.player()))
        );

        registrar.playToServer(
            DampenersTogglePayload.TYPE,
            DampenersTogglePayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> LegThrustersItem.toggle((ServerPlayer) context.player()))
        );

        registrar.playToServer(
            PlanetMapRequestPayload.TYPE,
            PlanetMapRequestPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleMapRequest(context.player(), payload.powerSize()))
        );

        registrar.playToClient(
            PlanetMapPayload.TYPE,
            PlanetMapPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleMapData(payload.powerSize(), payload.centerX(), payload.centerZ(), payload.mapDataPosXPosZ(), payload.mapDataPosXNegZ(), payload.mapDataNegXPosZ(), payload.mapDataNegXNegZ()))
        );

        registrar.playToClient(
            ReentryHeatPayload.TYPE,
            ReentryHeatPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleHeatData(payload.x(), payload.y(), payload.z(), payload.intensity()))
        );

        registrar.playToClient(
            SeamlessTransitionPayload.TYPE,
            SeamlessTransitionPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> handleSeamlessTransition(payload.active()))
        );

        registrar.playToClient(
            DebugLogPayload.TYPE,
            DebugLogPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> dev.devce.rocketnautics.RocketNauticsClient.addLog(payload.message(), payload.color()))
        );

        registrar.playToServer(
                SputnikNodeSyncPayload.TYPE,
                SputnikNodeSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSputnikSync(context.player(), payload.pos(), payload.graphData()))
        );

        registrar.playToServer(
                PlanetRenderRequestPayload.TYPE,
                PlanetRenderRequestPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePlanetRenderRequest(context.player(), payload.ids(), payload.powerScale()))
        );

        registrar.playToClient(
                PlanetRenderPayload.TYPE,
                PlanetRenderPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePlanetRenderData(payload.id(), payload.renderData(), payload.powerSize()))
        );

        registrar.playToClient(
                UniverseDefinitionPayload.TYPE,
                UniverseDefinitionPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handleUniverseDefinition(payload.definition()))
        );

        registrar.playToClient(
                DeepSpacePositionPayload.TYPE,
                DeepSpacePositionPayload.CODEC,
                (payload, context) -> context.enqueueWork(payload::handle)
        );

        registrar.playToClient(
                UniverseTimeSyncPayload.TYPE,
                UniverseTimeSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handleUniverseTime(payload.universeTicks(), payload.serverTickRate()))
        );

        registrar.playToClient(
                PlayAudioPayload.TYPE,
                PlayAudioPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePlayAudio(payload))
        );

        registrar.playToServer(
                LimitWorldBorderPayload.TYPE,
                LimitWorldBorderPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> handleLimitWorldBorder(context.player()))
        );

    }

    private static void handleFreeMotionSetup(IPayloadContext context, FreeMotionSetupPayload payload) {
        if (context.player() instanceof FreeMotionEntity fme) {
            fme.set6DOFEnabled(payload.is6DOFEnabled());
            fme.setAmbulant(payload.isAmbulant());
            fme.setMovementAcceleration(payload.movementAcceleration());
            fme.setDampenerForce(payload.dampenerForce());
        }
    }

    private static void handleFreeMotionMovement(IPayloadContext context, FreeMotionPayload payload) {
        ServerPlayer player = (ServerPlayer) context.player();

        if (!(player instanceof FreeMotionEntity fme)) return;

        fme.setOrientation(payload.orientation());
        player.setDeltaMovement(new Vec3(payload.deltaMovement()));

        for (ServerPlayer target : player.serverLevel().players()) {
            if (target == player) continue;

            PacketDistributor.sendToPlayer(
                    target,
                    new FreeMotionSyncPayload(
                            player.getId(),
                            fme.is6DOFEnabled(),
                            payload.orientation(),
                            FreeMotionHandler.getThrustStrength(player.getId())
                    )
            );
        }
    }

    private static void handleFreeMotionClientSync(FreeMotionSyncPayload payload) {
        Entity entity = Minecraft.getInstance().level.getEntity(payload.entityId());

        if (entity instanceof FreeMotionEntity fme) {
            fme.setOrientation(payload.orientation());
            fme.set6DOFEnabled(payload.freeMotionEnabled());
            FreeMotionHandler.putThrustStrength(payload.entityId(), payload.thrustStrength());
        }
    }

    private static void handleLimitWorldBorder(net.minecraft.world.entity.player.Player player) {
        if (player.getServer() != null) {
            if (player.getServer().isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2)) {
                player.getServer().execute(() -> {
                    player.level().getWorldBorder().setSize(40000);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chat.rocketnautics.world_border_warning.success").withStyle(net.minecraft.ChatFormatting.GREEN));
                });
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("chat.rocketnautics.world_border_warning.no_permission").withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
    }

    private static void handleSputnikSync(net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos, net.minecraft.nbt.CompoundTag graphData) {
        net.minecraft.world.level.Level foundLevel = null;
        if (player.level().getBlockEntity(pos) instanceof dev.devce.rocketnautics.content.blocks.SputnikBlockEntity) {
            foundLevel = player.level();
        } else {
            // Check all levels if not in current player level (e.g. ship in space)
            for (net.minecraft.server.level.ServerLevel serverLevel : player.getServer().getAllLevels()) {
                if (serverLevel.getBlockEntity(pos) instanceof dev.devce.rocketnautics.content.blocks.SputnikBlockEntity) {
                    foundLevel = serverLevel;
                    break;
                }
            }
        }

        if (foundLevel != null && foundLevel.getBlockEntity(pos) instanceof dev.devce.rocketnautics.content.blocks.SputnikBlockEntity sputnik) {
            sputnik.graph.load(graphData);
            sputnik.graph.setContext(sputnik);

            sputnik.setChanged();
            foundLevel.sendBlockUpdated(pos, sputnik.getBlockState(), sputnik.getBlockState(), 3);
/*
            if (dev.devce.rocketnautics.RocketConfig.SERVER.enableEngineDebugLogging.get()) {
                dev.devce.rocketnautics.RocketNautics.LOGGER.info("Sputnik at {} (level {}) SYNCED. Nodes: {}, Connections: {}, Engines Found: {}",
                        pos, foundLevel.dimension().location(), sputnik.graph.getNodes().size(), sputnik.graph.getConnections().size(), sputnik.getEngineCount());
            }
            */
        } else {
            // dev.devce.rocketnautics.RocketNautics.LOGGER.warn("Failed to find Sputnik at {} for sync from player {}", pos, player.getName().getString());
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleSeamlessTransition(boolean active) {
        if (active) {
            dev.devce.rocketnautics.RocketNauticsClient.startSeamlessTransition();
        } else {
            dev.devce.rocketnautics.RocketNauticsClient.endSeamlessTransition();
        }
    }

    private static void handleMapRequest(net.minecraft.world.entity.player.Player rawPlayer, int powerSize) {
        if (!(rawPlayer instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        
        CompletableFuture.runAsync(() -> {
            SkyDataHandler handler = SkyDataHandler.getHandlerForLevel(level);
            PlanetMapPayload payload = handler.getRenderDataAtScaleAndPosition(powerSize, player.getBlockX(), player.getBlockZ());
            
            level.getServer().execute(() -> {
                PacketDistributor.sendToPlayer(player, payload);
            });
        });
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleMapData(int powerSize, int centerX, int centerZ, ColorPalette mapDataPosXPosZ, ColorPalette mapDataPosXNegZ, ColorPalette mapDataNegXPosZ, ColorPalette mapDataNegXNegZ) {
        SkyHandler.updatePlanetTexture(powerSize, centerX, centerZ, mapDataPosXPosZ, mapDataPosXNegZ, mapDataNegXPosZ, mapDataNegXNegZ);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleHeatData(double x, double y, double z, float intensity) {
        dev.devce.rocketnautics.client.HeatClientHandler.updateHeat(x, y, z, intensity);
    }

    private static void handlePlanetRenderRequest(net.minecraft.world.entity.player.Player rawPlayer, int[] ids, int powerSize) {
        if (!(rawPlayer instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        CompletableFuture.runAsync(() -> {
            DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
            UniverseDefinition def = data.getUniverse();
            for (int id : ids) {
                CubePlanet planet = def.getPlanetById(id);
                // computing the render data may take time, so we dispatch in separate packets.
                // would it be better to send a single large packet after loading everything?
                Either<ColorPalette, ResourceLocation> send;
                if (planet == null) {
                    send = Either.right(ResourceLocation.withDefaultNamespace("missingno"));
                } else if (planet.textureDefinition() instanceof DeepSpaceTextureDefinition.ResourceLocationDriven(
                        ResourceLocation texture
                )) {
                    send = Either.right(texture);
                } else {
                    send = Either.left(planet.getRenderData(level.getServer(), powerSize));
                }
                PlanetRenderPayload payload = new PlanetRenderPayload(id, send, powerSize);
                level.getServer().execute(() -> PacketDistributor.sendToPlayer(player, payload));
            }
        });
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handlePlanetRenderData(int id, Either<ColorPalette, ResourceLocation> renderData, int powerSize) {
        DeepSpaceHandler.receiveRenderData(id, renderData, powerSize);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleUniverseDefinition(UniverseDefinition definition) {
        DeepSpaceHandler.receiveUniverse(definition);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleUniverseTime(long universeTicks, float serverTickRate) {
        DeepSpaceHandler.receiveUniverseTime(universeTicks, serverTickRate);
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handlePlayAudio(PlayAudioPayload payload) {
        dev.devce.rocketnautics.client.ClientSynthAudio.play(payload);
    }

    public static void sendPlayAudio(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, double frequency, double endFrequency, double volume, double duration, String waveform, double attack, double decay, double sustain, double release, double dutyCycle, double fmFreq, double fmDepth, double lfoFreq, double lfoDepth, String harmonics, String formula) {
        if (level instanceof ServerLevel serverLevel) {
            PlayAudioPayload payload = new PlayAudioPayload(pos, frequency, endFrequency, volume, duration, waveform, attack, decay, sustain, release, dutyCycle, fmFreq, fmDepth, lfoFreq, lfoDepth, harmonics, formula);
            PacketDistributor.sendToPlayersNear(serverLevel, null, pos.getX(), pos.getY(), pos.getZ(), 64.0, payload);
        }
    }
}
