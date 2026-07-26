package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketNauticsClient;
import dev.devce.rocketnautics.client.render.JetpackLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import net.minecraft.client.resources.PlayerSkin;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Event subscriber for mod-bus client-side events.
 * Handles key mapping registration and entity rendering layers.
 */
public class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RocketNauticsClient.JETPACK_TOGGLE);
        event.register(RocketNauticsClient.DAMPENERS_TOGGLE);
        event.register(RocketNauticsClient.ALIGNMENT_TOGGLE);
    }

    @SubscribeEvent
    public static void onRegisterShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(
            new net.minecraft.client.renderer.ShaderInstance(
                event.getResourceProvider(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.devce.rocketnautics.RocketNautics.MODID, "exhaust"),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR
            ),
            shader -> dev.devce.rocketnautics.client.render.ExhaustRenderer.exhaustShader = shader
        );
        event.registerShader(
            new net.minecraft.client.renderer.ShaderInstance(
                event.getResourceProvider(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.devce.rocketnautics.RocketNautics.MODID, "rcs"),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR
            ),
            shader -> dev.devce.rocketnautics.client.render.ExhaustRenderer.rcsShader = shader
        );
        event.registerShader(
            new net.minecraft.client.renderer.ShaderInstance(
                event.getResourceProvider(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.devce.rocketnautics.RocketNautics.MODID, "reentry"),
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR
            ),
            shader -> dev.devce.rocketnautics.client.render.ReentryClientRenderer.reentryShader = shader
        );
    }

    /**
     * Adds the jetpack rendering layer to player models.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        // Iterate through all player skin models (slim and normal)
        for (PlayerSkin.Model model : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(model);
            if (renderer != null) {
                renderer.addLayer(new JetpackLayer<>(renderer));
            }
        }
    }
}
