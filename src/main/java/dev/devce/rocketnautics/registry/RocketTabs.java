package dev.devce.rocketnautics.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.items.CreditsBookItem;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class RocketTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RocketNautics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RESOURCE_TAB = CREATIVE_MODE_TABS.register("resource_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rocketnautics.tab.resource"))
                    .icon(() -> new ItemStack(RocketBlocks.TITANIUM_BLOCK.get()))
                    .displayItems(new RegistrateDisplayItemsGenerator(RocketTabs.RESOURCE_TAB))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WORLD_TAB = CREATIVE_MODE_TABS.register("world_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.rocketnautics.tab.world"))
                    .icon(() -> new ItemStack(RocketBlocks.TITANIUM_ORE.get()))
                    .displayItems(new RegistrateDisplayItemsGenerator(RocketTabs.WORLD_TAB))
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }

    private static class RegistrateDisplayItemsGenerator implements CreativeModeTab.DisplayItemsGenerator {
        private final DeferredHolder<CreativeModeTab, CreativeModeTab> tabFilter;

        public RegistrateDisplayItemsGenerator(DeferredHolder<CreativeModeTab, CreativeModeTab> tabFilter) {
            this.tabFilter = tabFilter;
        }

        private static Predicate<Item> makeExclusionPredicate() {
            Set<Item> exclusions = new ReferenceOpenHashSet<>();

            // allows us to hide items, such as partial sequenced assembly items.
            List<ItemProviderEntry<?, ?>> simpleExclusions = List.of();

            for (ItemProviderEntry<?, ?> entry : simpleExclusions) {
                exclusions.add(entry.asItem());
            }

            return exclusions::contains;
        }

        private static Function<Item, ItemStack> makeStackFunc() {
            Map<Item, Function<Item, ItemStack>> factories = new Reference2ReferenceOpenHashMap<>();

            // allows us to modify the stack shown in the creative menu for an item, e.g. making backtanks start full.
            Map<ItemProviderEntry<?, ?>, Function<Item, ItemStack>> simpleFactories = Map.of();

            simpleFactories.forEach((entry, factory) -> {
                factories.put(entry.asItem(), factory);
            });

            return item -> {
                Function<Item, ItemStack> factory = factories.get(item);
                if (factory != null) {
                    return factory.apply(item);
                }
                return new ItemStack(item);
            };
        }

        private static Function<Item, CreativeModeTab.TabVisibility> makeVisibilityFunc() {
            Map<Item, CreativeModeTab.TabVisibility> visibilities = new Reference2ObjectOpenHashMap<>();

            // allows us to control whether items show up in the tab, in the search tab, or both.
            Map<ItemProviderEntry<?, ?>, CreativeModeTab.TabVisibility> simpleVisibilities = Map.of();

            simpleVisibilities.forEach((entry, factory) -> {
                visibilities.put(entry.asItem(), factory);
            });

            return item -> {
                CreativeModeTab.TabVisibility visibility = visibilities.get(item);
                if (visibility != null) {
                    return visibility;
                }
                return CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
            };
        }

        private static final List<String> RESOURCE_TAB_ORDER = List.of(
            "credits_book",
            "music_disc_space",
            "space_helmet",
            "jetpack",
            "copper_leg_thrusters",
            "copper_anchor_boots",
            "sputnik",
            "combustion_engine_controller",
            "launch_pad_core",
            "combustion_engine",
            "combustion_engine_valve",
            "combustion_engine_shaft",
            "rocket_thruster",
            "vector_thruster",
            "booster_thruster",
            "creative_thruster",
            "thruster_valve",
            "copper_nozzle",
            "titanium_nozzle",
            "separator_block",
            "separator_charge_block",
            "separator_shaft_block",
            "engine_pipes_expander",
            "expandable_pipe",
            "hose_anchor",
            "titanium_casing",
            "raw_titanium",
            "crushed_raw_titanium",
            "titanium_ingot",
            "titanium_nugget",
            "titanium_sheet",
            "titanium_alloy",
            "titanium_alloy_nugget",
            "titanium_alloy_sheet",
            "titanium_block"
        );

        @Override
        public void accept(@NotNull CreativeModeTab.ItemDisplayParameters parameters, @NotNull CreativeModeTab.Output output) {
            Predicate<Item> exclusionPredicate = makeExclusionPredicate();
            Function<Item, ItemStack> stackFunc = makeStackFunc();
            Function<Item, CreativeModeTab.TabVisibility> visibilityFunc = makeVisibilityFunc();

            List<Item> items = new LinkedList<>();
            items.addAll(collectBlocks(exclusionPredicate));
            items.addAll(collectItems(exclusionPredicate));

            items.sort(java.util.Comparator.comparingInt(item -> {
                String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
                int idx = RESOURCE_TAB_ORDER.indexOf(path);
                return idx != -1 ? idx : Integer.MAX_VALUE;
            }));

            outputAll(output, items, stackFunc, visibilityFunc);
        }

        private List<Item> collectBlocks(Predicate<Item> exclusionPredicate) {
            List<Item> items = new ReferenceArrayList<>();
            for (RegistryEntry<Block, Block> entry : RocketNautics.getRegistrate().getAll(Registries.BLOCK)) {
                if (!CreateRegistrate.isInCreativeTab(entry, tabFilter))
                    continue;
                Item item = entry.get().asItem();
                if (item == Items.AIR)
                    continue;
                if (!exclusionPredicate.test(item))
                    items.add(item);
            }
            items = new ReferenceArrayList<>(new ReferenceLinkedOpenHashSet<>(items));
            return items;
        }

        private List<Item> collectItems(Predicate<Item> exclusionPredicate) {
            List<Item> items = new ReferenceArrayList<>();
            for (RegistryEntry<Item, Item> entry : RocketNautics.getRegistrate().getAll(Registries.ITEM)) {
                if (!CreateRegistrate.isInCreativeTab(entry, tabFilter))
                    continue;
                Item item = entry.get();
                if (item instanceof BlockItem)
                    continue;
                if (!exclusionPredicate.test(item))
                    items.add(item);
            }
            return items;
        }

        private static void outputAll(CreativeModeTab.Output output, List<Item> items, Function<Item, ItemStack> stackFunc, Function<Item, CreativeModeTab.TabVisibility> visibilityFunc) {
            for (Item item : items) {
                output.accept(stackFunc.apply(item), visibilityFunc.apply(item));
            }
        }
    }
}
