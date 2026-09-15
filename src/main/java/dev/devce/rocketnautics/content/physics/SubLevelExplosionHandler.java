package dev.devce.rocketnautics.content.physics;

import dev.devce.rocketnautics.registry.RocketParticles;
import dev.devce.rocketnautics.registry.RocketSounds;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SubLevelExplosionHandler {

    private record DebrisBlock(BlockPos plotPos, BlockState state, Vector3d worldPos) {}

    public static void sendExplosionParticle(ServerLevel level, double x, double y, double z, float power) {
        if (level == null) return;
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                RocketParticles.TANK_EXPLOSION_CLOUD.get(),
                true, // overrideLimiter = true (bypasses 32-block vanilla limitation)
                x, y, z,
                power, 0.0f, 0.0f,
                1.0f, 0
        );

        for (ServerPlayer player : level.players()) {
            double distSq = player.distanceToSqr(x, y, z);
            if (distSq <= 4096.0 * 4096.0) {
                player.connection.send(packet);
            }
        }
    }

    public static void explodeSubLevel(ServerSubLevel subLevel, float basePower) {
        if (subLevel == null || subLevel.isRemoved()) return;

        ServerLevel level = subLevel.getLevel();
        if (level == null) return;

        ServerLevelPlot plot = subLevel.getPlot();
        var bounds = plot != null ? plot.getBoundingBox() : null;

        Vector3d initialPos = subLevel.logicalPose().position();

        int minX = bounds != null ? bounds.minX() : 0;
        int maxX = bounds != null ? bounds.maxX() : 0;
        int minY = bounds != null ? bounds.minY() : 0;
        int maxY = bounds != null ? bounds.maxY() : 0;
        int minZ = bounds != null ? bounds.minZ() : 0;
        int maxZ = bounds != null ? bounds.maxZ() : 0;

        double sizeX = maxX - minX + 1;
        double sizeY = maxY - minY + 1;
        double sizeZ = maxZ - minZ + 1;
        double shipRadius = Math.max(Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ) * 0.5, 4.0);
        float explosionPower = Math.min(60.0f, Math.max(basePower, (float) shipRadius * 1.6f));

        List<DebrisBlock> shipBlocks = new ArrayList<>();

        if (plot != null) {
            for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
                LevelChunk chunk = chunkHolder.getChunk();
                if (chunk == null) continue;
                for (int sIdx = 0; sIdx < chunk.getSectionsCount(); sIdx++) {
                    LevelChunkSection section = chunk.getSection(sIdx);
                    if (section != null && !section.hasOnlyAir()) {
                        int secY = chunk.getSectionYFromSectionIndex(sIdx);
                        int minChunkX = chunk.getPos().getMinBlockX();
                        int minChunkZ = chunk.getPos().getMinBlockZ();

                        for (int x = 0; x < 16; x++) {
                            for (int y = 0; y < 16; y++) {
                                for (int z = 0; z < 16; z++) {
                                    BlockState state = section.getBlockState(x, y, z);
                                    if (!state.isAir()) {
                                        BlockPos plotPos = new BlockPos(minChunkX + x, secY * 16 + y, minChunkZ + z);
                                        Vector3d localPos = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
                                        Vector3d worldPos = subLevel.logicalPose().transformPosition(new Vector3d(localPos));

                                        shipBlocks.add(new DebrisBlock(plotPos, state, worldPos));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        int totalBlocks = shipBlocks.size();

        // Calculate the true geometric center and vertical span of the rocket
        double sumX = 0, sumY = 0, sumZ = 0;
        double minWorldY = Double.MAX_VALUE, maxWorldY = -Double.MAX_VALUE;
        for (DebrisBlock b : shipBlocks) {
            sumX += b.worldPos.x;
            sumY += b.worldPos.y;
            sumZ += b.worldPos.z;
            if (b.worldPos.y < minWorldY) minWorldY = b.worldPos.y;
            if (b.worldPos.y > maxWorldY) maxWorldY = b.worldPos.y;
        }

        Vector3d shipCenter = totalBlocks > 0
                ? new Vector3d(sumX / totalBlocks, sumY / totalBlocks, sumZ / totalBlocks)
                : initialPos;
        double rocketHeight = totalBlocks > 0 ? (maxWorldY - minWorldY) : 10.0;

        // 1. Physical flying block debris (FallingBlockEntity)
        int maxEntities = Math.min(totalBlocks, Math.min(100, Math.max(30, totalBlocks / 3)));
        List<DebrisBlock> debrisCandidates = new ArrayList<>(shipBlocks);
        Collections.shuffle(debrisCandidates, new java.util.Random(level.random.nextLong()));

        for (int i = 0; i < maxEntities; i++) {
            DebrisBlock debris = debrisCandidates.get(i);
            BlockPos targetPos = BlockPos.containing(debris.worldPos.x, debris.worldPos.y, debris.worldPos.z);
            BlockState prevWorldState = level.getBlockState(targetPos);

            FallingBlockEntity fbe = FallingBlockEntity.fall(level, targetPos, debris.state);
            if (fbe != null) {
                if (!prevWorldState.isAir()) {
                    level.setBlock(targetPos, prevWorldState, 3);
                }
                fbe.setPos(debris.worldPos.x, debris.worldPos.y, debris.worldPos.z);

                Vector3d outDir = new Vector3d(
                        debris.worldPos.x - shipCenter.x,
                        (debris.worldPos.y - shipCenter.y) * 0.5,
                        debris.worldPos.z - shipCenter.z
                );
                if (outDir.lengthSquared() < 1e-4) {
                    outDir = new Vector3d(
                            level.random.nextDouble() - 0.5,
                            level.random.nextDouble() * 0.5 + 0.5,
                            level.random.nextDouble() - 0.5
                    );
                }
                outDir.normalize();

                double speed = 0.6 + level.random.nextDouble() * 1.8;
                double vx = outDir.x * speed + (level.random.nextDouble() - 0.5) * 0.4;
                double vy = Math.max(0.3, outDir.y * speed + 0.4 + level.random.nextDouble() * 0.6);
                double vz = outDir.z * speed + (level.random.nextDouble() - 0.5) * 0.4;

                fbe.setDeltaMovement(new Vec3(vx, vy, vz));
                fbe.dropItem = false;
                fbe.time = 1;
                fbe.setHurtsEntities(4.0f, 60);

                if (level.random.nextFloat() < 0.45f) {
                    fbe.setRemainingFireTicks(100 + level.random.nextInt(120));
                }
            }
        }

        // 2. Clear plot blocks
        for (DebrisBlock block : shipBlocks) {
            level.setBlock(block.plotPos, Blocks.AIR.defaultBlockState(), 3);
        }

        // 3. Play artillery shell explosion sound
        level.playSound(null, shipCenter.x, shipCenter.y, shipCenter.z,
                RocketSounds.TANK_EXPLOSION.get(), SoundSource.BLOCKS,
                16.0f, 0.75f + level.random.nextFloat() * 0.3f);

        // 4. Pure CBC HE Shell Explosions distributed across the WHOLE rocket (long-distance overrideLimiter)
        float visualPower = Math.min(8.5f, Math.max(5.0f, (float) Math.sqrt(shipRadius) * 1.5f));

        // Center of the rocket
        sendExplosionParticle(level, shipCenter.x, shipCenter.y, shipCenter.z, visualPower);

        // Bottom of the rocket (engines & bottom tanks)
        if (rocketHeight > 3.0) {
            sendExplosionParticle(level, shipCenter.x, minWorldY + rocketHeight * 0.15, shipCenter.z, visualPower * 0.9f);
        }

        // Top of the rocket (payload & nosecone)
        if (rocketHeight > 5.0) {
            sendExplosionParticle(level, shipCenter.x, minWorldY + rocketHeight * 0.85, shipCenter.z, visualPower * 0.9f);
        }

        // Staggered HE Shell bursts along the rocket's height
        int extraBursts = Math.min(5, Math.max(2, (int) (shipRadius / 4.0)));
        for (int i = 0; i < extraBursts; i++) {
            double frac = (double) (i + 1) / (extraBursts + 1);
            double burstY = minWorldY + rocketHeight * frac + (level.random.nextDouble() - 0.5) * 1.5;
            double ox = (level.random.nextDouble() - level.random.nextDouble()) * (shipRadius * 0.3);
            double oz = (level.random.nextDouble() - level.random.nextDouble()) * (shipRadius * 0.3);
            float subPower = visualPower * (0.8f + level.random.nextFloat() * 0.2f);

            sendExplosionParticle(level, shipCenter.x + ox, burstY, shipCenter.z + oz, subPower);
        }

        // 5. Blast damage and knockback in the world
        double worldRadius = shipRadius * 3.0;
        AABB worldDamageArea = new AABB(
                shipCenter.x - worldRadius, minWorldY - 10.0, shipCenter.z - worldRadius,
                shipCenter.x + worldRadius, maxWorldY + 10.0, shipCenter.z + worldRadius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, worldDamageArea)) {
            double dist = Math.sqrt(e.distanceToSqr(shipCenter.x, shipCenter.y, shipCenter.z));
            float dmg = (float) Math.max(20.0, (1.0 - (dist / worldRadius)) * (explosionPower * 7.0));
            e.hurt(level.damageSources().explosion(null, null), dmg);

            Vec3 kb = e.position().subtract(shipCenter.x, shipCenter.y, shipCenter.z).normalize()
                    .scale(Math.max(0.5, 3.0 * (1.0 - dist / worldRadius)));
            e.setDeltaMovement(e.getDeltaMovement().add(kb));
        }

        if (bounds != null) {
            AABB plotDamageArea = new AABB(minX - 6, minY - 6, minZ - 6, maxX + 6, maxY + 6, maxZ + 6);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, plotDamageArea)) {
                entity.hurt(level.damageSources().explosion(null, null), explosionPower * 5.0f);
            }
        }

        subLevel.markRemoved();
    }
}
