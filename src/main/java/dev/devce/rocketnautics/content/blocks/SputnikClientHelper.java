package dev.devce.rocketnautics.content.blocks;

import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import java.util.function.Supplier;

public class SputnikClientHelper {
    public static Supplier<Boolean> hasReceivedPosition = () -> false;
    public static Supplier<DeepSpacePosition> getReceivedPosition = () -> null;
    public static Supplier<UniverseDefinition> getUniverse = () -> null;
}
