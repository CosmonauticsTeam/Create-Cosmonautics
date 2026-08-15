package dev.devce.rocketnautics.registry;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

public class RocketPartials {

    public static final ModelResourceLocation VECTOR_THRUSTER_NOZZLE_MODEL =
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/vector_thruster_nozzle"), "standalone");

    public static final ModelResourceLocation ENGINE_NOZZLE_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/engine_nozzle"), "standalone");

    public static final ModelResourceLocation ENGINE_PIPES_BASE_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/engine_pipes_base"), "standalone");

    public static final ModelResourceLocation ENGINE_PIPES_CLOSED_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/engine_pipes_closed"), "standalone");
    public static final ModelResourceLocation ENGINE_PIPES_OPEN_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/engine_pipes_open"), "standalone");
    public static final ModelResourceLocation ENGINE_PIPES_FULLFLOW_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/engine_pipes_fullflow"), "standalone");
    public static final ModelResourceLocation ENGINE_PIPES_EXPANDER_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/engine_pipes_expander"), "standalone");

    public static final ModelResourceLocation GYRODYNE_ROTOR_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/gyrodyne_rotor"), "standalone");
    public static final ModelResourceLocation GYRODYNE_RING1_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/gyrodyne_ring1"), "standalone");
    public static final ModelResourceLocation GYRODYNE_RING2_MODEL = 
        new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "block/gyrodyne_ring2"), "standalone");

    public static BakedModel vectorThrusterNozzle;
    public static BakedModel engineNozzle;
    public static BakedModel enginePipesBase;
    public static BakedModel enginePipesClosed;
    public static BakedModel enginePipesOpen;
    public static BakedModel enginePipesFullflow;
    public static BakedModel enginePipesExpander;
    public static BakedModel gyrodyneRotor;
    public static BakedModel gyrodyneRing1;
    public static BakedModel gyrodyneRing2;

    public static void init() {
        
    }
}
