package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelTimeAccess;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(LevelTimeAccess.class)
public interface LevelTimeAccessMixin {

    @ModifyExpressionValue(method = "getTimeOfDay", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelTimeAccess;dayTime()J"))
    private long overrideTimeOfDay(long original) {
        // this only needs to affect server side, since we're overriding the client level's time of day directly.
        /*
        AI helped explain to me the codebase

        An extra statement checking if access is an instance of SchematicLevel in order to prevent crashes
        due to SchematicLevel's implementation of ServerLevelAccessor.
         */

        if (this instanceof ServerLevelAccessor access) {
            if (access instanceof SchematicLevel) {
                return original;
            }
            MinecraftServer server = access.getLevel().getServer();

            // as it turns out, this method gets called very early.
            if (DeepSpaceData.tooSoon(server)) return original;
            DeepSpaceData data = DeepSpaceData.getInstance(server);
            AtomicLong ret = new AtomicLong(original);
            DeepSpaceHelper.checkAndOverrideLevelTime(data.getUniverse(), data.getUniverseTime(), access.getLevel(), ret::set);
            return ret.get();
        }
        return original;
    }
}
