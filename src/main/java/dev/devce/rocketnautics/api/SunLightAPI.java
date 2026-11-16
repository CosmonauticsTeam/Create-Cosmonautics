package dev.devce.rocketnautics.api;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import net.minecraft.world.phys.Vec3;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.frames.Frame;

import java.util.Optional;

public final class SunLightAPI {
    public static boolean isInSpace(Level level) { return DeepSpaceHelper.isDeepSpace(); }

    // Logic partially AI written, for getting DeepSpacePosition and getting Star Position
    // Vector calculation I searched on Google
    // Logic made by me: use deepspaceposition to calculate sun direction, and calculate points between star and ship pos
    /**
     * Calculates the sun Direction from the specified Block Position.
     * 
     * @param level: The current level loaded.
     * @param blockPos:  The Block Position to use to get the Deep Space Instance from.  
     * @return Optional:  An optional container that may or may not return the non null value, in order to avoid NullPointerExceptions.  
     */
    public static Optional<Vec3> getSunDirection(Level level, BlockPos blockPos) {
        MinecraftServer server = level.getServer();
        if (server == null) return Optional.empty();

        DeepSpaceData data = DeepSpaceData.getInstance(server);
        DeepSpaceInstance instance = data.getInstanceForPos(blockPos.getX(), blockPos.getZ());
        DeepSpacePosition position = instance.getPosition();
        UniverseDefinition universe = data.getUniverse();
        CubePlanet star = universe.getPlanets().stream()
                .filter(p -> p.extras() != null && p.extras().star())
                .findFirst()
                .orElse(null);

        AbsoluteDate now = position.getLocalUniverseTime();
        Frame shipFrame = position.getFrame();
        Vector3D shipPos = position.getCurrentPosition();
        Vector3D starPos = star.posInMyFrame(now, shipPos, shipFrame);
        Vector3D dir = starPos.subtract(shipPos).normalize(); // I kinda knew i had to subtract, AI wrote this line of code for me

        return Optional.of(new Vec3(dir.getX(), dir.getY(), dir.getZ()));  // same for this

    }
}
