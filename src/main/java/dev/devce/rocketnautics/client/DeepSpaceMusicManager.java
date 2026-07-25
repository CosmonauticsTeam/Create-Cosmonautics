package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-side music manager for the Deep Space dimension.
 * Mirrors vanilla's MusicManager approach: picks a random track from a pool,
 * plays it through the MUSIC sound category (respecting the in-game Music slider),
 * then waits a random delay before playing the next one.
 *
 * <p>Tracks are registered in {@link RocketSounds} and listed in {@code sounds.json}
 * with {@code "category": "music"} and {@code "stream": true}.</p>
 *
 * <p>Call {@link #tick(Minecraft)} every client tick from
 * {@link RocketNauticsClientEvents#onClientTick}.</p>
 */
@OnlyIn(Dist.CLIENT)
public class DeepSpaceMusicManager {

    /** The dimension key path for deep space. */
    private static final ResourceLocation DEEP_SPACE_DIM =
            ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "deep_space");

    /**
     * Minimum delay between tracks in ticks (6000 = 5 minutes).
     * Vanilla uses similar values: Nether is 1200–3600, End is 6000–24000.
     */
    private static final int DELAY_MIN_TICKS = 6000;

    /**
     * Maximum delay between tracks in ticks (24000 = 20 minutes).
     */
    private static final int DELAY_MAX_TICKS = 24000;

    /**
     * Pool of tracks to randomly select from when in deep space.
     * Add new {@link RocketSounds} entries here as more music is created.
     */
    private static final List<Supplier<SoundEvent>> TRACKS = List.of(
            RocketSounds.MUSIC_ARCADIA,
            RocketSounds.MUSIC_PALE_DOT,
            RocketSounds.MUSIC_REFLECTING_SATELLITES,
            RocketSounds.MUSIC_SOLAR_SAILS
    );

    /** The currently playing music instance, or {@code null} if nothing is active. */
    @Nullable
    private SoundInstance currentTrack = null;

    /**
     * Ticks remaining before the next track should start.
     * Counts down each tick; a new song plays when this reaches 0.
     * Starts at 0 so the first track begins after a short random delay.
     */
    private int nextSongDelay = 100; // ~5 second initial delay on first entry

    /** Whether the player was in deep space last tick (used to detect dimension changes). */
    private boolean wasInDeepSpace = false;

    /**
     * Called every client tick. Handles starting, stopping, and scheduling music.
     *
     * @param mc The Minecraft instance.
     */
    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        boolean inDeepSpace = DEEP_SPACE_DIM.equals(mc.level.dimension().location());

        // Player just left deep space — don't cut the track, let it finish naturally.
        // New tracks won't be scheduled until the player returns.
        if (!inDeepSpace) {
            if (wasInDeepSpace) {
                nextSongDelay = 100; // short delay for next re-entry
            }
            wasInDeepSpace = false;
            return;
        }

        // Player just entered deep space — reset delay for a quick first song.
        if (!wasInDeepSpace) {
            nextSongDelay = 100;
        }
        wasInDeepSpace = true;

        if (currentTrack != null && mc.getSoundManager().isActive(currentTrack)) {
            // A track is still playing — do nothing, just reset the countdown.
            nextSongDelay = 0;
            return;
        }

        // No track is playing. Count down until the next one.
        currentTrack = null;
        if (nextSongDelay > 0) {
            nextSongDelay--;
            return;
        }

        // Delay elapsed — play a random track.
        playRandomTrack(mc);
    }

    /**
     * Selects and plays a random track from {@link #TRACKS}.
     */
    private void playRandomTrack(Minecraft mc) {
        if (TRACKS.isEmpty()) return;

        RandomSource random = mc.level.getRandom();
        SoundEvent track = TRACKS.get(random.nextInt(TRACKS.size())).get();

        // SimpleSoundInstance with relative=true plays in "head space" (no 3D panning),
        // exactly like vanilla music. Volume 1.0f, pitch 1.0f.
        currentTrack = SimpleSoundInstance.forMusic(track);
        mc.getSoundManager().play(currentTrack);

        // Schedule the next song after a random vanilla-style delay.
        nextSongDelay = Mth.randomBetweenInclusive(random, DELAY_MIN_TICKS, DELAY_MAX_TICKS);

        RocketNautics.LOGGER.debug("[DeepSpaceMusic] Now playing: {}", track.getLocation());
    }

    /**
     * Stops the currently playing track and clears the reference.
     */
    private void stopCurrentTrack(Minecraft mc) {
        if (currentTrack != null) {
            mc.getSoundManager().stop(currentTrack);
            currentTrack = null;
        }
    }

    /**
     * Returns the currently playing {@link SoundInstance}, or {@code null}.
     */
    @Nullable
    public SoundInstance getCurrentTrack() {
        return currentTrack;
    }

    /**
     * Returns the list of registered deep-space music tracks.
     * Add entries here when registering new music in {@link RocketSounds}.
     */
    public static List<Supplier<SoundEvent>> getTracks() {
        return TRACKS;
    }
}
