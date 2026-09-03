package dev.devce.rocketnautics.registry;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static com.simibubi.create.foundation.data.TagGen.tagBlockAndItem;
import static dev.devce.rocketnautics.registry.RocketTags.BlockTags.GENERIC_CARVABLE;

import java.util.Arrays;
import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.foundation.data.BuilderTransformers;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.recipe.CommonMetal;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.DataIngredient;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.RocketSpriteShifts;
import dev.devce.rocketnautics.content.RocketBlockItem;
import dev.devce.rocketnautics.content.blocks.BoosterThrusterBlock;
import dev.devce.rocketnautics.content.blocks.CreativeThrusterBlock;
import dev.devce.rocketnautics.content.blocks.EncasedRCSThrusterBlock;
import dev.devce.rocketnautics.content.blocks.EngineNozzleBlock;
import dev.devce.rocketnautics.content.blocks.EnginePipesBlock;
import dev.devce.rocketnautics.content.blocks.HologramTableBlock;
import dev.devce.rocketnautics.content.blocks.MFDBlock;
import dev.devce.rocketnautics.content.blocks.RCSThrusterBlock;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlock;
import dev.devce.rocketnautics.content.blocks.SputnikBlock;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlock;
import dev.devce.rocketnautics.content.blocks.VectorThrusterBlock;
import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlock;
import dev.devce.rocketnautics.content.blocks.separator.SeparatorBlock;
import dev.devce.rocketnautics.content.blocks.separator.SeparatorChargeBlock;
import dev.devce.rocketnautics.content.blocks.separator.SeparatorShaftBlock;
import dev.devce.rocketnautics.content.blocks.world.MossBlock;
import dev.devce.rocketnautics.content.blocks.world.RockBlock;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.Tags;

public class RocketBlocks {
        private static final SimulatedRegistrate REGISTRATE = RocketNautics.getRegistrate();

        public static final BlockEntry<CasingBlock> TITANIUM_CASING = REGISTRATE.block("titanium_casing", CasingBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(p -> p.mapColor(MapColor.TERRACOTTA_PINK))
                        .transform(BuilderTransformers.casing(() -> RocketSpriteShifts.TITANIUM_CASING)).tag(RocketTags.BlockTags.LIGHT.tag)
                        .register();

        public static final BlockEntry<RocketThrusterBlock> ROCKET_THRUSTER = REGISTRATE.block("rocket_thruster", RocketThrusterBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag)
                        .transform(existingDirectionalModel("rocket_thruster")).item(RocketBlockItem::new).build().register();

        public static final BlockEntry<CreativeThrusterBlock> CREATIVE_THRUSTER = REGISTRATE
                        .block("creative_thruster", CreativeThrusterBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
                        .properties(BlockBehaviour.Properties::noOcclusion).transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag)
                        .transform(existingDirectionalModel("creative_thruster")).item(RocketBlockItem::new).build().register();

        public static final BlockEntry<VectorThrusterBlock> VECTOR_THRUSTER = REGISTRATE.block("vector_thruster", VectorThrusterBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag)
                        .transform(existingDirectionalModel("vector_thruster_base")).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel()).build().register();

        public static final BlockEntry<BoosterThrusterBlock> BOOSTER_THRUSTER = REGISTRATE
                        .block("booster_thruster", BoosterThrusterBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
                        .properties(BlockBehaviour.Properties::noOcclusion).transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag)
                        .transform(existingDirectionalModel("booster_thruster")).item(RocketBlockItem::new).build().register();

        public static final BlockEntry<RCSThrusterBlock> RCS_THRUSTER = REGISTRATE.block("rcs_thruster", RCSThrusterBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly())
                        .tag(RocketTags.BlockTags.THRUSTERS.tag, RocketTags.BlockTags.LIGHT.tag, RocketTags.BlockTags.QUARTER_VOLUME.tag)
                        .transform(existingDirectionalModel("rcs_thruster")).item(RocketBlockItem::new).build().register();

        public static final BlockEntry<EncasedRCSThrusterBlock> BRASS_ENCASED_RCS_THRUSTER = REGISTRATE
                        .block("brass_encased_rcs_thruster",
                                        properties -> new EncasedRCSThrusterBlock(properties, AllBlocks.BRASS_CASING::get))
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag, RocketTags.BlockTags.LIGHT.tag)
                        .transform(encasedDirectionalModel("rcs_thruster_encasement"))
                        .loot((tables, block) -> tables.add(block, rcsEncasedLoot(AllBlocks.BRASS_CASING.get())))
                        .transform(EncasingRegistry.addVariantTo(RCS_THRUSTER)).register();

        public static final BlockEntry<EncasedRCSThrusterBlock> COPPER_ENCASED_RCS_THRUSTER = REGISTRATE
                        .block("copper_encased_rcs_thruster",
                                        properties -> new EncasedRCSThrusterBlock(properties, AllBlocks.COPPER_CASING::get))
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag, RocketTags.BlockTags.LIGHT.tag)
                        .transform(encasedDirectionalModel("rcs_thruster_copper_encasement"))
                        .loot((tables, block) -> tables.add(block, rcsEncasedLoot(AllBlocks.COPPER_CASING.get())))
                        .transform(EncasingRegistry.addVariantTo(RCS_THRUSTER)).register();

        public static final BlockEntry<EncasedRCSThrusterBlock> RAILWAY_ENCASED_RCS_THRUSTER = REGISTRATE
                        .block("railway_encased_rcs_thruster",
                                        properties -> new EncasedRCSThrusterBlock(properties, AllBlocks.RAILWAY_CASING::get))
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).tag(RocketTags.BlockTags.THRUSTERS.tag, RocketTags.BlockTags.LIGHT.tag)
                        .transform(encasedDirectionalModel("rcs_thruster_railway_encasement"))
                        .loot((tables, block) -> tables.add(block, rcsEncasedLoot(AllBlocks.RAILWAY_CASING.get())))
                        .transform(EncasingRegistry.addVariantTo(RCS_THRUSTER)).register();

        public static final BlockEntry<ThrusterMountBlock> THRUSTER_MOUNT = REGISTRATE.block("thruster_mount", ThrusterMountBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).transform(existingDirectionalModel("thruster_mount")).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel()).build().register();

        public static final BlockEntry<EnginePipesBlock> ENGINE_PIPES = REGISTRATE.block("engine_pipes", EnginePipesBlock::new)
                        .initialProperties(() -> Blocks.COPPER_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).transform(existingDirectionalModel("engine_pipes_open")).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel()).build().register();

        public static final BlockEntry<EngineNozzleBlock> ENGINE_NOZZLE = REGISTRATE.block("engine_nozzle", EngineNozzleBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).transform(existingDirectionalModel("engine_nozzle")).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel()).build().register();

        public static final BlockEntry<HoseAnchorBlock> HOSE_ANCHOR = REGISTRATE.block("hose_anchor", HoseAnchorBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).transform(existingDirectionalModel("hose_anchor")).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel()).build().register();

        public static final BlockEntry<dev.devce.rocketnautics.content.blocks.solar.SolarPanelBlock> SOLAR_PANEL = REGISTRATE
                        .block("solar_panel", dev.devce.rocketnautics.content.blocks.solar.SolarPanelBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).item(RocketBlockItem::new)
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 2).pattern("LQL").pattern("CEC")
                                        .pattern("III").define('L', Items.LAPIS_LAZULI).define('Q', Items.QUARTZ)
                                        .define('C', CommonMetal.COPPER.plates).define('E', AllItems.ELECTRON_TUBE)
                                        .define('I', CommonMetal.IRON.plates)
                                        .unlockedBy("has_electron_tube", prov.has(AllItems.ELECTRON_TUBE)).save(prov))
                        .build().register();

        public static final BlockEntry<dev.devce.rocketnautics.content.blocks.ion.IonEngineBlock> ION_ENGINE = REGISTRATE
                        .block("ion_engine", dev.devce.rocketnautics.content.blocks.ion.IonEngineBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly())
                        .tag(RocketTags.BlockTags.THRUSTERS.tag, RocketTags.BlockTags.LIGHT.tag, RocketTags.BlockTags.QUARTER_VOLUME.tag)
                        .item(RocketBlockItem::new)
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get()).pattern(" G ").pattern("TEN")
                                        .pattern(" C ").define('G', CommonMetal.GOLD.plates)
                                        .define('T', RocketTags.MetalTags.TITANIUM.plates).define('E', AllItems.ELECTRON_TUBE)
                                        .define('N', RocketItems.COPPER_NOZZLE).define('C', CommonMetal.COPPER.plates)
                                        .unlockedBy("has_electron_tube", prov.has(AllItems.ELECTRON_TUBE)).save(prov))
                        .build().register();

        public static final BlockEntry<dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock> COPPER_WIRE = REGISTRATE
                        .block("copper_wire", dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock::new)
                        .initialProperties(() -> Blocks.COPPER_BLOCK)
                        .properties(p -> p.noOcclusion().sound(SoundType.COPPER).strength(0.5f)).transform(pickaxeOnly())
                        .blockstate((ctx, prov) -> {
                                prov.getMultipartBuilder(ctx.getEntry()).part()
                                                .modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_core")))
                                                .addModel().end().part()
                                                .modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_north")))
                                                .addModel()
                                                .condition(dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock.NORTH, true).end()
                                                .part()
                                                .modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_south")))
                                                .addModel()
                                                .condition(dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock.SOUTH, true).end()
                                                .part()
                                                .modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_east")))
                                                .addModel()
                                                .condition(dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock.EAST, true).end()
                                                .part()
                                                .modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_west")))
                                                .addModel()
                                                .condition(dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock.WEST, true).end()
                                                .part().modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_up")))
                                                .addModel().condition(dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock.UP, true)
                                                .end().part()
                                                .modelFile(prov.models().getExistingFile(RocketNautics.path("block/copper_wire_down")))
                                                .addModel()
                                                .condition(dev.devce.rocketnautics.content.blocks.wire.CopperWireBlock.DOWN, true).end();
                        }).tag(RocketTags.BlockTags.SUPER_LIGHT.tag).item(RocketBlockItem::new).transform(RocketItems.noGeneratedModel())
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 8).pattern(" C ").pattern("CRC")
                                        .pattern(" C ").define('C', CommonMetal.COPPER.plates).define('R', Items.REDSTONE)
                                        .unlockedBy("has_copper", prov.has(Items.COPPER_INGOT)).save(prov))
                        .build().register();

        public static final BlockEntry<dev.devce.rocketnautics.content.blocks.energy_tank.EnergyTankBlock> ENERGY_TANK = REGISTRATE
                        .block("energy_tank", dev.devce.rocketnautics.content.blocks.energy_tank.EnergyTankBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK)
                        .properties(p -> p.sound(SoundType.COPPER).strength(1.5f).isRedstoneConductor((p1, p2, p3) -> true))
                        .transform(pickaxeOnly())
                        .blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(),
                                        prov.models().getExistingFile(RocketNautics.path("block/energy_tank"))))
                        .item(RocketBlockItem::new).transform(RocketItems.noGeneratedModel())
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get()).pattern(" C ").pattern("CBC")
                                        .pattern(" C ").define('C', CommonMetal.COPPER.plates).define('B', Items.REDSTONE_BLOCK)
                                        .unlockedBy("has_copper", prov.has(Items.COPPER_INGOT)).save(prov))
                        .build().register();

        public static final BlockEntry<SeparatorBlock> SEPARATOR = REGISTRATE.block("separator", SeparatorBlock::new).initialProperties(
                        () -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion).transform(pickaxeOnly()).blockstate((
                                        ctx,
                                        prov) -> allDirectionsMultiPart(prov, prov.getMultipartBuilder(ctx.getEntry()),
                                                        SeparatorBlock.FACING, "separator")
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.NORTH), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Z)).end()
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.SOUTH), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Z)).end()

                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .rotationY(90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.EAST), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.X)).end()
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .rotationY(90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.WEST), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.X)).end()

                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .rotationY(90).rotationX(-90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.UP), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Y)).end()
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .rotationY(90).rotationX(-90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.DOWN), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Y)).end())
                        .tag(RocketTags.BlockTags.SUPER_LIGHT.tag).item(RocketBlockItem::new)
                        .model((ctx, prov) -> prov.blockItem(ctx::getEntry)).build().register();

        public static final BlockEntry<SeparatorChargeBlock> SEPARATOR_CHARGE = REGISTRATE.block("separator_charge",
                        SeparatorChargeBlock::new).initialProperties(() -> Blocks.IRON_BLOCK).properties(
                                        BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).blockstate((ctx,
                                        prov) -> directionalAxisMultiPart(prov, prov.getMultipartBuilder(ctx.getEntry()),
                                                        SeparatorChargeBlock.FACING, SeparatorChargeBlock.AXIS_ALONG_FIRST_COORDINATE,
                                                        "separator_charge")
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .addModel()
                                                                        .condition(SeparatorChargeBlock.LINKS.get(Direction.NORTH), true)
                                                                        .condition(SeparatorChargeBlock.FACING, allBut(Direction.SOUTH))
                                                                        .end().part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .addModel()
                                                                        .condition(SeparatorChargeBlock.LINKS.get(Direction.SOUTH), true)
                                                                        .condition(SeparatorChargeBlock.FACING, allBut(Direction.NORTH))
                                                                        .end()

                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .rotationY(90).addModel()
                                                                        .condition(SeparatorChargeBlock.LINKS.get(Direction.EAST), true)
                                                                        .condition(SeparatorChargeBlock.FACING, allBut(Direction.WEST))
                                                                        .end().part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .rotationY(90).addModel()
                                                                        .condition(SeparatorChargeBlock.LINKS.get(Direction.WEST), true)
                                                                        .condition(SeparatorChargeBlock.FACING, allBut(Direction.EAST))
                                                                        .end()

                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .rotationY(90).rotationX(-90).addModel()
                                                                        .condition(SeparatorChargeBlock.LINKS.get(Direction.UP), true)
                                                                        .condition(SeparatorChargeBlock.FACING, allBut(Direction.DOWN))
                                                                        .end().part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .rotationY(90).rotationX(-90).addModel()
                                                                        .condition(SeparatorChargeBlock.LINKS.get(Direction.DOWN), true)
                                                                        .condition(SeparatorChargeBlock.FACING, allBut(Direction.UP)).end())
                        .tag(RocketTags.BlockTags.SUPER_LIGHT.tag).item(RocketBlockItem::new)
                        .model((ctx, prov) -> prov.blockItem(ctx::getEntry, "_first")).build().register();

        public static final BlockEntry<SeparatorShaftBlock> SEPARATOR_SHAFT = REGISTRATE.block("separator_shaft", SeparatorShaftBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion).transform(
                                        pickaxeOnly())
                        .blockstate((ctx,
                                        prov) -> allDirectionsMultiPart(prov, prov.getMultipartBuilder(ctx.getEntry()),
                                                        SeparatorBlock.FACING, "separator_shaft")
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.NORTH), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Z)).end()
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.SOUTH), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Z)).end()

                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .rotationY(90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.EAST), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.X)).end()
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .rotationY(90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.WEST), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.X)).end()

                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_a")))
                                                                        .rotationY(90).rotationX(-90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.UP), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Y)).end()
                                                                        .part()
                                                                        .modelFile(prov.models().getExistingFile(
                                                                                        RocketNautics.path("block/separator_link_b")))
                                                                        .rotationY(90).rotationX(-90).addModel()
                                                                        .condition(SeparatorBlock.LINKS.get(Direction.DOWN), true)
                                                                        .condition(SeparatorBlock.FACING, allBut(Direction.Axis.Y)).end())
                        .tag(RocketTags.BlockTags.LIGHT.tag).item(RocketBlockItem::new)
                        .model((ctx, prov) -> prov.blockItem(ctx::getEntry, "_item")).build().register();

        public static final BlockEntry<SputnikBlock> SPUTNIK = REGISTRATE.block("sputnik", SputnikBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).transform(existingSimpleModel("sputnik")).tag(RocketTags.BlockTags.SUPER_HEAVY.tag)
                        .item(RocketBlockItem::new)
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get()).pattern("L L").pattern("IBI")
                                        .pattern("L L").define('L', Items.LIGHTNING_ROD).define('I', Items.IRON_INGOT)
                                        .define('B', Items.IRON_BLOCK).unlockedBy("has_iron_block", prov.has(Items.IRON_BLOCK)).save(prov))
                        .build().register();

        public static final BlockEntry<HologramTableBlock> HOLOGRAM_TABLE = REGISTRATE.block("hologram_block", HologramTableBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).transform(pickaxeOnly())
                        .transform(existingSimpleModel("hologram_block"))
                        .tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.HEAVY.tag).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel())
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get()).pattern("   ").pattern("AGA")
                                        .pattern("IBI").define('A', Items.AMETHYST_SHARD).define('G', Items.GLASS)
                                        .define('I', Items.IRON_INGOT).define('B', RocketBlocks.TITANIUM_BLOCK)
                                        .unlockedBy("has_iron_block", prov.has(RocketBlocks.TITANIUM_BLOCK)).save(prov))
                        .build().register();

        public static final BlockEntry<MFDBlock> MFD = REGISTRATE.block("mfd", MFDBlock::new).initialProperties(() -> Blocks.IRON_BLOCK)
                        .transform(pickaxeOnly()).transform(existingDirectionalModel("mfd")).tag(RocketTags.BlockTags.LIGHT.tag)
                        .item(RocketBlockItem::new).build().register();

        public static final BlockEntry<dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlock> GYRODYNE = REGISTRATE
                        .block("gyrodyne", dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlock::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK).properties(BlockBehaviour.Properties::noOcclusion)
                        .transform(pickaxeOnly()).tag(RocketTags.BlockTags.SUPER_HEAVY.tag)
                        .transform(existingDirectionalModel("gyrodyne_base")).item(RocketBlockItem::new)
                        .transform(RocketItems.noGeneratedModel())
                        .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get()).pattern(" P ").pattern("TFT")
                                        .pattern(" C ").define('P', AllItems.PRECISION_MECHANISM)
                                        .define('T', RocketTags.MetalTags.TITANIUM.plates).define('F', AllBlocks.FLYWHEEL)
                                        .define('C', RocketBlocks.TITANIUM_CASING)
                                        .unlockedBy("has_titanium_casing", prov.has(RocketBlocks.TITANIUM_CASING)).save(prov))
                        .build().register();

        static {
                REGISTRATE.setCreativeTab(RocketTabs.WORLD_TAB);
        }

        public static final BlockEntry<Block> TITANIUM_ORE = REGISTRATE.block("titanium_ore", Block::new)
                        .initialProperties(() -> Blocks.IRON_ORE)
                        .properties(p -> p.mapColor(MapColor.TERRACOTTA_PINK).requiresCorrectToolForDrops().sound(SoundType.STONE))
                        .loot((lt, b) -> {
                                HolderLookup.RegistryLookup<Enchantment> enchantmentRegistryLookup = lt.getRegistries()
                                                .lookupOrThrow(Registries.ENCHANTMENT);
                                lt.add(b, lt.createSilkTouchDispatchTable(b, lt.applyExplosionDecay(b, LootItem
                                                .lootTableItem(RocketItems.RAW_TITANIUM.get()).apply(ApplyBonusCount.addOreBonusCount(
                                                                enchantmentRegistryLookup.getOrThrow(Enchantments.FORTUNE))))));
                        }).transform(pickaxeOnly()).tag(BlockTags.NEEDS_DIAMOND_TOOL).tag(Tags.Blocks.ORES)
                        .transform(tagBlockAndItem(
                                        Map.of(RocketTags.MetalTags.TITANIUM.ores.blocks(), RocketTags.MetalTags.TITANIUM.ores.items(),
                                                        Tags.Blocks.ORES_IN_GROUND_STONE, Tags.Items.ORES_IN_GROUND_STONE)))
                        .tag(Tags.Items.ORES).build().register();

        public static final BlockEntry<Block> DEEPSLATE_TITANIUM_ORE = REGISTRATE.block("deepslate_titanium_ore", Block::new)
                        .initialProperties(() -> Blocks.DEEPSLATE_IRON_ORE)
                        .properties(p -> p.mapColor(MapColor.STONE).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE))
                        .loot((lt, b) -> {
                                HolderLookup.RegistryLookup<Enchantment> enchantmentRegistryLookup = lt.getRegistries()
                                                .lookupOrThrow(Registries.ENCHANTMENT);
                                lt.add(b, lt.createSilkTouchDispatchTable(b, lt.applyExplosionDecay(b, LootItem
                                                .lootTableItem(RocketItems.RAW_TITANIUM.get()).apply(ApplyBonusCount.addOreBonusCount(
                                                                enchantmentRegistryLookup.getOrThrow(Enchantments.FORTUNE))))));
                        }).transform(pickaxeOnly()).tag(BlockTags.NEEDS_DIAMOND_TOOL).tag(Tags.Blocks.ORES)
                        .transform(tagBlockAndItem(
                                        Map.of(RocketTags.MetalTags.TITANIUM.ores.blocks(), RocketTags.MetalTags.TITANIUM.ores.items(),
                                                        Tags.Blocks.ORES_IN_GROUND_DEEPSLATE, Tags.Items.ORES_IN_GROUND_DEEPSLATE)))
                        .tag(Tags.Items.ORES).build().register();

        // MOON START

        public static final BlockEntry<RotatedPillarBlock> LUNAR_REGOLITH = REGISTRATE.block("lunar_regolith", RotatedPillarBlock::new)
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE))
                        .loot((tables, block) -> tables.add(block,
                                        tables.createSingleItemTableWithSilkTouch(block, RocketBlocks.LUNAR_SHATTERED_REGOLITH)))
                        .tag(GENERIC_CARVABLE.tag).transform(pickaxeOnly())
                        .recipe((ctx, prov) -> prov.smeltingAndBlasting(
                                        DataIngredient.items((ItemLike) RocketBlocks.LUNAR_SHATTERED_REGOLITH),
                                        RecipeCategory.BUILDING_BLOCKS, ctx::getEntry, 1f))
                        .blockstate((ctx, prov) -> prov.axisBlock(ctx.getEntry())).item().build().register();

        // TODO look into Create's block palette system
        public static final BlockEntry<StairBlock> LUNAR_REGOLITH_STAIRS = REGISTRATE
                        .block("lunar_regolith_stairs", p -> new StairBlock(LUNAR_REGOLITH.getDefaultState(), p))
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .transform(stairs(LUNAR_REGOLITH, "_end", "_side"))
                        .tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.LIGHT.tag).item().build().register();

        public static final BlockEntry<SlabBlock> LUNAR_REGOLITH_SLAB = REGISTRATE.block("lunar_regolith_slab", SlabBlock::new)
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .transform(slab(LUNAR_REGOLITH, "_end", "_side"))
                        .tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.LIGHT.tag).item().build().register();

        public static final BlockEntry<Block> LUNAR_SHATTERED_REGOLITH = REGISTRATE.block("lunar_shattered_regolith", Block::new)
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .item().build().register();

        public static final BlockEntry<StairBlock> LUNAR_SHATTERED_REGOLITH_STAIRS = REGISTRATE
                        .block("lunar_shattered_regolith_stairs", p -> new StairBlock(LUNAR_SHATTERED_REGOLITH.getDefaultState(), p))
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .transform(stairs(LUNAR_SHATTERED_REGOLITH))
                        .tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.LIGHT.tag).item().build().register();

        public static final BlockEntry<SlabBlock> LUNAR_SHATTERED_REGOLITH_SLAB = REGISTRATE
                        .block("lunar_shattered_regolith_slab", SlabBlock::new).initialProperties(() -> Blocks.NETHERRACK)
                        .properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly()).transform(slab(LUNAR_SHATTERED_REGOLITH))
                        .tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.LIGHT.tag).item().build().register();

        public static final BlockEntry<ColoredFallingBlock> LUNAR_LOOSE_REGOLITH = REGISTRATE
                        .block("lunar_loose_regolith", p -> new ColoredFallingBlock(new ColorRGBA(-8356741), p))
                        .initialProperties(() -> Blocks.GRAVEL).properties(p -> p.mapColor(MapColor.STONE)).tag(GENERIC_CARVABLE.tag)
                        .transform(pickaxeOnly()).item().build().register();

        public static final BlockEntry<Block> LUNAR_REGOLITH_BRICK = REGISTRATE.block("lunar_regolith_brick", Block::new)
                        .initialProperties(() -> Blocks.STONE).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .recipe((ctx, prov) -> prov.stonecutting(DataIngredient.items((ItemLike) LUNAR_REGOLITH),
                                        RecipeCategory.BUILDING_BLOCKS, ctx::getEntry))
                        .item().build().register();

        public static final BlockEntry<StairBlock> LUNAR_REGOLITH_BRICK_STAIRS = REGISTRATE
                        .block("lunar_regolith_brick_stairs", p -> new StairBlock(LUNAR_SHATTERED_REGOLITH.getDefaultState(), p))
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .transform(stairs(LUNAR_REGOLITH_BRICK)).tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.LIGHT.tag)
                        .item().build().register();

        public static final BlockEntry<SlabBlock> LUNAR_REGOLITH_BRICK_SLAB = REGISTRATE.block("lunar_regolith_brick_slab", SlabBlock::new)
                        .initialProperties(() -> Blocks.NETHERRACK).properties(p -> p.mapColor(MapColor.STONE)).transform(pickaxeOnly())
                        .transform(slab(LUNAR_REGOLITH_BRICK)).tag(RocketTags.BlockTags.HALF_VOLUME.tag, RocketTags.BlockTags.LIGHT.tag)
                        .item().build().register();

        public static final BlockEntry<RotatedPillarBlock> LUNAR_AGED_BASALT = REGISTRATE
                        .block("lunar_aged_basalt", RotatedPillarBlock::new).initialProperties(() -> Blocks.BASALT).transform(pickaxeOnly())
                        .tag(GENERIC_CARVABLE.tag).blockstate((ctx, prov) -> prov.axisBlock(ctx.getEntry()))
                        .tag(RocketTags.BlockTags.HEAVY.tag).item().build().register();

        public static final BlockEntry<RotatedPillarBlock> LUNAR_ROCK = REGISTRATE.block("lunar_rock", RotatedPillarBlock::new)
                        .initialProperties(() -> Blocks.STONE).properties(p -> p.mapColor(MapColor.STONE))
                        .loot((tables, block) -> tables.add(block,
                                        tables.createSingleItemTableWithSilkTouch(block, RocketBlocks.LUNAR_FRAGMENTED_ROCK)))
                        .tag(GENERIC_CARVABLE.tag).transform(pickaxeOnly()).blockstate((ctx, prov) -> prov.axisBlock(ctx.getEntry())).item()
                        .build().register();

        public static final BlockEntry<ColoredFallingBlock> LUNAR_FRAGMENTED_ROCK = REGISTRATE
                        .block("lunar_fragmented_rock", p -> new ColoredFallingBlock(new ColorRGBA(-8356741), p))
                        .initialProperties(() -> Blocks.GRAVEL).properties(p -> p.mapColor(MapColor.STONE)).tag(GENERIC_CARVABLE.tag)
                        .transform(pickaxeOnly()).item().build().register();

        public static final BlockEntry<MossBlock> LUNAR_MOSS_STIFF = REGISTRATE.block("lunar_moss_stiff", MossBlock::new)
                        .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL).transform(pickaxeOnly()).transform(crossModelAndFlatItem())
                        .build().register();

        public static final BlockEntry<MossBlock> LUNAR_MOSS_SHORT = REGISTRATE.block("lunar_moss_short", MossBlock::new)
                        .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL).transform(pickaxeOnly()).transform(crossModelAndFlatItem())
                        .build().register();

        public static final BlockEntry<MossBlock> LUNAR_MOSS_SCRAGGLY = REGISTRATE.block("lunar_moss_scraggly", MossBlock::new)
                        .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL).transform(pickaxeOnly()).transform(crossModelAndFlatItem())
                        .build().register();

        public static final BlockEntry<RockBlock> LUNAR_ROCK_TALL = REGISTRATE.block("lunar_rock_tall", RockBlock::new)
                        .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL).transform(pickaxeOnly()).transform(crossModelAndFlatItem())
                        .build().register();

        public static final BlockEntry<RockBlock> LUNAR_ROCK_SMOOTH = REGISTRATE.block("lunar_rock_smooth", RockBlock::new)
                        .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL).transform(pickaxeOnly()).transform(crossModelAndFlatItem())
                        .build().register();

        public static final BlockEntry<RockBlock> LUNAR_ROCK_SPIKY = REGISTRATE.block("lunar_rock_spiky", RockBlock::new)
                        .initialProperties(() -> Blocks.DEAD_BRAIN_CORAL).transform(pickaxeOnly()).transform(crossModelAndFlatItem())
                        .build().register();

        // MOON END

        static {
                REGISTRATE.setCreativeTab(RocketTabs.RESOURCE_TAB);
        }

        public static final BlockEntry<Block> RAW_TITANIUM_BLOCK = REGISTRATE.block("raw_titanium_block", Block::new)
                        .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
                        .properties(p -> p.mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops()).transform(pickaxeOnly())
                        .tag(Tags.Blocks.STORAGE_BLOCKS).tag(BlockTags.NEEDS_IRON_TOOL)
                        .transform(RocketTags.tagBlockAndItem(RocketTags.MetalTags.TITANIUM.rawStorageBlocks))
                        .tag(Tags.Items.STORAGE_BLOCKS).build().register();

        public static final BlockEntry<Block> TITANIUM_BLOCK = REGISTRATE.block("titanium_block", Block::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK)
                        .properties(p -> p.mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops()).transform(pickaxeOnly())
                        .tag(BlockTags.NEEDS_IRON_TOOL).tag(Tags.Blocks.STORAGE_BLOCKS).tag(BlockTags.BEACON_BASE_BLOCKS)
                        .transform(RocketTags.tagBlockAndItem(RocketTags.MetalTags.TITANIUM.storageBlocks)).tag(Tags.Items.STORAGE_BLOCKS)
                        .build().register();

        public static final BlockEntry<Block> TITANIUM_ALLOY_BLOCK = REGISTRATE.block("titanium_alloy_block", Block::new)
                        .initialProperties(() -> Blocks.IRON_BLOCK)
                        .properties(p -> p.mapColor(MapColor.COLOR_PINK).requiresCorrectToolForDrops()).transform(pickaxeOnly())
                        .tag(BlockTags.NEEDS_IRON_TOOL).tag(Tags.Blocks.STORAGE_BLOCKS).tag(BlockTags.BEACON_BASE_BLOCKS)
                        .transform(RocketTags.tagBlockAndItem(RocketTags.MetalTags.TITANIUM_ALLOY.storageBlocks))
                        .tag(Tags.Items.STORAGE_BLOCKS).build().register();

        static {
                REGISTRATE.setCreativeTab(null);
        }


        public static void init() {
        }

        private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> existingDirectionalModel(
                        String name) {
                return b -> b.blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
                                prov.models().getExistingFile(RocketNautics.path("block/" + name))));
        }

        private static <T extends DirectionalBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> encasedDirectionalModel(
                        String casingModel) {
                return b -> b.blockstate((ctx, prov) -> {
                        MultiPartBlockStateBuilder builder = prov.getMultipartBuilder(ctx.getEntry());
                        for (Direction direction : Direction.values()) {
                                directionalMultiPart(prov, builder, DirectionalBlock.FACING, direction, "rcs_thruster");
                                directionalMultiPart(prov, builder, DirectionalBlock.FACING, direction, casingModel);
                        }
                });
        }

        private static LootTable.Builder rcsEncasedLoot(Block casing) {
                return LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(RCS_THRUSTER)))
                                .withPool(LootPool.lootPool().add(LootItem.lootTableItem(casing)));
        }

        private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> existingSimpleModel(
                        String name) {
                return b -> b.blockstate((ctx, prov) -> prov.simpleBlock(ctx.getEntry(),
                                prov.models().getExistingFile(RocketNautics.path("block/" + name))));
        }

        private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> existingHorizontalModel(
                        String name) {
                return b -> b.blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
                                prov.models().getExistingFile(RocketNautics.path("block/" + name))));
        }

        private static <T extends Block> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, ItemBuilder<BlockItem, BlockBuilder<T, CreateRegistrate>>> crossModelAndFlatItem() {
                return b -> b.blockstate((ctx, prov) -> {
                        if (ctx.getEntry() instanceof DirectionalBlock) {
                                prov.directionalBlock(ctx.getEntry(),
                                                prov.models().cross(ctx.getName(), prov.blockTexture(ctx.getEntry())).renderType("cutout"));
                        } else {
                                prov.simpleBlock(ctx.getEntry(),
                                                prov.models().cross(ctx.getName(), prov.blockTexture(ctx.getEntry())).renderType("cutout"));
                        }
                }).item().model((ctx, prov) -> prov.generated(ctx::getEntry, RocketNautics.path("block/" + ctx.getName())));
        }

        private static <T extends StairBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> stairs(
                        BlockEntry<? extends Block> parent) {
                return stairs(parent, "", "");
        }

        private static <T extends StairBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> stairs(
                        BlockEntry<? extends Block> parent, String topSuffix, String sideSuffix) {
                return b -> b.recipe((ctx, prov) -> prov.stairs(DataIngredient.items((ItemLike) parent), RecipeCategory.BUILDING_BLOCKS,
                                ctx::getEntry, ctx.getName(), true))
                                .blockstate((ctx, prov) -> prov.stairsBlock(ctx.getEntry(),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath() + sideSuffix),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix)));
        }

        private static <T extends SlabBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> slab(
                        BlockEntry<? extends Block> parent) {
                return slab(parent, "", "");
        }

        private static <T extends SlabBlock> @NonNull NonNullFunction<BlockBuilder<T, CreateRegistrate>, BlockBuilder<T, CreateRegistrate>> slab(
                        BlockEntry<? extends Block> parent, String topSuffix, String sideSuffix) {
                return b -> b.recipe((ctx, prov) -> prov.stairs(DataIngredient.items((ItemLike) parent), RecipeCategory.BUILDING_BLOCKS,
                                ctx::getEntry, ctx.getName(), true))
                                .blockstate((ctx, prov) -> prov.slabBlock(ctx.getEntry(),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath()),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath() + sideSuffix),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix),
                                                RocketNautics.path("block/" + parent.getKey().location().getPath() + topSuffix)));
        }

        private static MultiPartBlockStateBuilder directionalAxisMultiPart(RegistrateBlockstateProvider prov,
                        MultiPartBlockStateBuilder builder, DirectionProperty prop, BooleanProperty axisAlong, String path) {
                for (Direction dir : Direction.values()) {
                        builder.part().modelFile(prov.models().getExistingFile(RocketNautics.path("block/" + path + "_first")))
                                        .rotationX(dir == Direction.DOWN ? 180 : dir.getAxis().isHorizontal() ? 90 : 0)
                                        .rotationY(dir.getAxis().isVertical() ? 0 : (((int) dir.toYRot()) + 180) % 360).addModel()
                                        .condition(prop, dir).condition(axisAlong, true).end();
                        builder.part().modelFile(prov.models().getExistingFile(RocketNautics.path("block/" + path + "_second")))
                                        .rotationX(dir == Direction.DOWN ? 180 : dir.getAxis().isHorizontal() ? 90 : 0)
                                        .rotationY(dir.getAxis().isVertical() ? 0 : (((int) dir.toYRot()) + 180) % 360).addModel()
                                        .condition(prop, dir).condition(axisAlong, false).end();
                }
                return builder;
        }

        private static MultiPartBlockStateBuilder allDirectionsMultiPart(RegistrateBlockstateProvider prov,
                        MultiPartBlockStateBuilder builder, DirectionProperty prop, String path) {
                for (Direction dir : Direction.values()) {
                        directionalMultiPart(prov, builder, prop, dir, path);
                }
                return builder;
        }

        private static MultiPartBlockStateBuilder directionalMultiPart(RegistrateBlockstateProvider prov,
                        MultiPartBlockStateBuilder builder, DirectionProperty prop, Direction dir, String path) {
                return builder.part().modelFile(prov.models().getExistingFile(RocketNautics.path("block/" + path)))
                                .rotationX(dir == Direction.DOWN ? 180 : dir.getAxis().isHorizontal() ? 90 : 0)
                                .rotationY(dir.getAxis().isVertical() ? 0 : (((int) dir.toYRot()) + 180) % 360).addModel()
                                .condition(prop, dir).end();
        }

        private static Direction[] allBut(Direction.Axis axis) {
                return Arrays.stream(Direction.values()).filter(d -> !axis.test(d)).toArray(Direction[]::new);
        }

        private static Direction[] allBut(Direction direction) {
                return Arrays.stream(Direction.values()).filter(d -> direction != d).toArray(Direction[]::new);
        }
}
