package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class MFDAudioEngine {

    private static final Map<Long, ActiveSound> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BUFFER_CACHE = new ConcurrentHashMap<>();

    private record ActiveSound(int sourceId, String absPath) {}

    public static void play(BlockPos mfdPos, Path filePath, float volume, float pitch, boolean loop) {
        if (mfdPos == null || filePath == null || !Files.exists(filePath)) return;

        long key = mfdPos.asLong();
        String absPath = filePath.toAbsolutePath().toString();

        ActiveSound current = ACTIVE.get(key);
        if (current != null) {
            int state = AL10.alGetSourcei(current.sourceId(), AL10.AL_SOURCE_STATE);
            if (current.absPath().equals(absPath)
                    && (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED)) {
                AL10.alSourcef(current.sourceId(), AL10.AL_GAIN,  clamp(volume, 0f, 2f));
                AL10.alSourcef(current.sourceId(), AL10.AL_PITCH, clamp(pitch, 0.1f, 4f));
                return;
            }
            releaseSource(key);
        }

        int bufferId = getOrLoadBuffer(filePath, absPath);
        if (bufferId < 0) return;

        int sourceId = AL10.alGenSources();
        if (AL10.alGetError() != AL10.AL_NO_ERROR) return;

        AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
        AL10.alSourcef(sourceId, AL10.AL_GAIN,  clamp(volume, 0f, 2f));
        AL10.alSourcef(sourceId, AL10.AL_PITCH, clamp(pitch, 0.1f, 4f));
        AL10.alSourcei(sourceId, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSource3f(sourceId, AL10.AL_POSITION,
                mfdPos.getX() + 0.5f,
                mfdPos.getY() + 0.5f,
                mfdPos.getZ() + 0.5f);
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR,     1.2f);
        AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 2.0f);
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE,       32.0f);

        AL10.alSourcePlay(sourceId);
        ACTIVE.put(key, new ActiveSound(sourceId, absPath));
    }

    public static void stop(BlockPos mfdPos) {
        if (mfdPos != null) releaseSource(mfdPos.asLong());
    }

    public static boolean isPlaying(BlockPos mfdPos) {
        if (mfdPos == null) return false;
        ActiveSound as = ACTIVE.get(mfdPos.asLong());
        if (as == null) return false;
        int state = AL10.alGetSourcei(as.sourceId(), AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING;
    }

    public static void tick() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.level.Level level = mc.level;

        ACTIVE.entrySet().removeIf(entry -> {
            int sourceId = entry.getValue().sourceId();
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_STOPPED) {
                AL10.alDeleteSources(sourceId);
                return true;
            }

            if (level != null) {
                BlockPos pos = BlockPos.of(entry.getKey());
                if (!level.isLoaded(pos)) {
                    AL10.alSourceStop(sourceId);
                    AL10.alDeleteSources(sourceId);
                    return true;
                }
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity mfd) || be.isRemoved() || !mfd.hasCartridge()) {
                    AL10.alSourceStop(sourceId);
                    AL10.alDeleteSources(sourceId);
                    return true;
                }
            }
            return false;
        });
    }

    public static void stopAll() {
        Long[] keys = ACTIVE.keySet().toArray(new Long[0]);
        for (long key : keys) releaseSource(key);
        ACTIVE.clear();
        for (int buf : BUFFER_CACHE.values()) AL10.alDeleteBuffers(buf);
        BUFFER_CACHE.clear();
    }

    private static void releaseSource(long key) {
        ActiveSound as = ACTIVE.remove(key);
        if (as == null) return;
        AL10.alSourceStop(as.sourceId());
        AL10.alDeleteSources(as.sourceId());
    }

    private static int getOrLoadBuffer(Path filePath, String absPath) {
        Integer cached = BUFFER_CACHE.get(absPath);
        if (cached != null) return cached;

        String name = filePath.getFileName().toString().toLowerCase();
        try {
            int bufferId;
            if (name.endsWith(".ogg")) {
                bufferId = decodeOgg(filePath);
            } else if (name.endsWith(".wav")) {
                bufferId = decodeWav(filePath);
            } else {
                RocketNautics.LOGGER.warn("[MFD Audio] Unsupported format '{}'. Use .ogg or .wav", name);
                return -1;
            }
            if (bufferId >= 0) BUFFER_CACHE.put(absPath, bufferId);
            return bufferId;
        } catch (Exception e) {
            RocketNautics.LOGGER.warn("[MFD Audio] Failed to load '{}': {}", filePath.getFileName(), e.getMessage());
            return -1;
        }
    }

    private static int decodeOgg(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer fileData = MemoryUtil.memAlloc(bytes.length);
        try {
            fileData.put(bytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer errorBuf = stack.mallocInt(1);
                long vorbis = STBVorbis.stb_vorbis_open_memory(fileData, errorBuf, null);
                if (vorbis == 0L)
                    throw new IOException("STBVorbis open error: " + errorBuf.get(0));

                STBVorbisInfo info = STBVorbisInfo.malloc(stack);
                STBVorbis.stb_vorbis_get_info(vorbis, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();

                int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(vorbis);
                if (totalSamples <= 0) {
                    STBVorbis.stb_vorbis_close(vorbis);
                    throw new IOException("OGG samples 0");
                }

                ShortBuffer pcm;
                if (channels == 1) {
                    pcm = BufferUtils.createShortBuffer(totalSamples);
                    STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbis, channels, pcm);
                } else {
                    ShortBuffer interleaved = BufferUtils.createShortBuffer(totalSamples * channels);
                    STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbis, channels, interleaved);
                    pcm = BufferUtils.createShortBuffer(totalSamples);
                    for (int i = 0; i < totalSamples; i++) {
                        int sum = 0;
                        for (int c = 0; c < channels; c++) {
                            sum += interleaved.get(i * channels + c);
                        }
                        pcm.put((short) (sum / channels));
                    }
                    pcm.flip();
                }
                STBVorbis.stb_vorbis_close(vorbis);

                int bufferId = AL10.alGenBuffers();
                AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcm, sampleRate);
                return bufferId;
            }
        } finally {
            MemoryUtil.memFree(fileData);
        }
    }

    private static int decodeWav(Path path) throws Exception {
        AudioInputStream rawStream = AudioSystem.getAudioInputStream(path.toFile());
        AudioFormat srcFmt = rawStream.getFormat();
        int channels = srcFmt.getChannels();
        float sampleRate = srcFmt.getSampleRate();

        AudioFormat pcmFmt = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false);

        AudioInputStream pcmStream;
        try {
            pcmStream = AudioSystem.getAudioInputStream(pcmFmt, rawStream);
        } catch (IllegalArgumentException e) {
            pcmStream = rawStream;
        }

        byte[] rawBytes = pcmStream.readAllBytes();
        pcmStream.close();

        int totalSamples = rawBytes.length / (channels * 2);
        ShortBuffer pcm = BufferUtils.createShortBuffer(totalSamples);

        if (channels == 1) {
            for (int i = 0; i < totalSamples; i++) {
                short s = (short) ((rawBytes[i * 2] & 0xFF) | (rawBytes[i * 2 + 1] << 8));
                pcm.put(s);
            }
        } else {
            for (int i = 0; i < totalSamples; i++) {
                int sum = 0;
                for (int c = 0; c < channels; c++) {
                    int offset = (i * channels + c) * 2;
                    short s = (short) ((rawBytes[offset] & 0xFF) | (rawBytes[offset + 1] << 8));
                    sum += s;
                }
                pcm.put((short) (sum / channels));
            }
        }
        pcm.flip();

        int bufferId = AL10.alGenBuffers();
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcm, (int) sampleRate);
        return bufferId;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

