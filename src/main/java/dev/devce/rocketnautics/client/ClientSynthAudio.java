package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.network.PlayAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientSynthAudio {
    private static final ExecutorService PLAY_THREAD_POOL = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "Sputnik-Synth-Audio-Thread");
        thread.setDaemon(true);
        return thread;
    });

    private static final float SAMPLE_RATE = 22050.0f;
    private static final double MAX_SOUND_RANGE = 64.0;

    public static void play(PlayAudioPayload payload) {
        PLAY_THREAD_POOL.submit(() -> {
            try {
                // Calculate distance attenuation on client side
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                
                Vec3 playerPos = mc.player.position();
                Vec3 sourcePos = new Vec3(payload.pos().getX() + 0.5, payload.pos().getY() + 0.5, payload.pos().getZ() + 0.5);
                double distance = playerPos.distanceTo(sourcePos);
                
                if (distance > MAX_SOUND_RANGE) return;
                
                double distanceFactor = 1.0 - (distance / MAX_SOUND_RANGE);
                double finalVolume = payload.volume() * distanceFactor;
                if (finalVolume <= 0.001) return;

                double duration = payload.duration();
                double attack = payload.attack();
                double decay = payload.decay();
                double sustain = payload.sustain();
                double release = payload.release();
                
                double totalTime = duration + release;
                int totalSamples = (int) (totalTime * SAMPLE_RATE);
                if (totalSamples <= 0) return;

                byte[] pcmData = new byte[totalSamples * 2]; // 16-bit mono = 2 bytes per sample
                
                // Parse harmonics if waveform is harmonics
                double[] harmonicAmps = null;
                if (payload.waveform().equalsIgnoreCase("harmonics") && !payload.harmonics().isEmpty()) {
                    try {
                        String[] parts = payload.harmonics().split(",");
                        harmonicAmps = new double[parts.length];
                        for (int i = 0; i < parts.length; i++) {
                            harmonicAmps[i] = Double.parseDouble(parts[i].trim());
                        }
                    } catch (Exception ignored) {}
                }
                
                // Set up custom expression evaluator
                MathEvaluator evaluator = null;
                if (payload.waveform().equalsIgnoreCase("custom") && !payload.formula().isEmpty()) {
                    evaluator = new MathEvaluator(payload.formula());
                }

                double frequency = payload.frequency();
                double endFrequency = payload.endFrequency();
                double dutyCycle = payload.dutyCycle();
                double fmFreq = payload.fmFreq();
                double fmDepth = payload.fmDepth();
                double lfoFreq = payload.lfoFreq();
                double lfoDepth = payload.lfoDepth();
                
                // Phase accumulators to avoid phase jumps during frequency sweeps
                double phase = 0.0;
                double fmPhase = 0.0;
                double lfoPhase = 0.0;

                for (int i = 0; i < totalSamples; i++) {
                    double t = (double) i / SAMPLE_RATE;
                    
                    // 1. Envelope calculation
                    double env = 0.0;
                    if (t < attack) {
                        env = t / Math.max(0.001, attack);
                    } else if (t < attack + decay) {
                        double phaseEnv = (t - attack) / Math.max(0.001, decay);
                        env = 1.0 - (1.0 - sustain) * phaseEnv;
                    } else if (t < duration) {
                        env = sustain;
                    } else if (t < totalTime) {
                        double phaseEnv = (t - duration) / Math.max(0.001, release);
                        env = sustain * (1.0 - phaseEnv);
                    }
                    env = Math.max(0.0, Math.min(1.0, env));
                    
                    // 2. Frequency Glide/Sweep
                    double currentFreq = frequency;
                    if (endFrequency > 0.0) {
                        if (t < duration) {
                            double progress = t / duration;
                            currentFreq = frequency + (endFrequency - frequency) * progress;
                        } else {
                            currentFreq = endFrequency;
                        }
                    }

                    // 3. LFO (Vibrato) Modulation
                    if (lfoFreq > 0.0 && lfoDepth > 0.0) {
                        double lfoMod = Math.sin(lfoPhase) * lfoDepth;
                        currentFreq += lfoMod;
                        lfoPhase += 2.0 * Math.PI * lfoFreq / SAMPLE_RATE;
                        if (lfoPhase > 2.0 * Math.PI) lfoPhase -= 2.0 * Math.PI;
                    }

                    // 4. Update carrier and modulator phases
                    phase += 2.0 * Math.PI * currentFreq / SAMPLE_RATE;
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;

                    fmPhase += 2.0 * Math.PI * fmFreq / SAMPLE_RATE;
                    if (fmPhase > 2.0 * Math.PI) fmPhase -= 2.0 * Math.PI;
                    
                    // 5. Waveform generation
                    double val = 0.0;
                    String wf = payload.waveform().toLowerCase();
                    switch (wf) {
                        case "sine" -> val = Math.sin(phase);
                        case "square" -> val = Math.sin(phase) >= (1.0 - 2.0 * dutyCycle) ? 1.0 : -1.0;
                        case "triangle" -> val = 2.0 * Math.abs(2.0 * (phase / (2.0 * Math.PI) - Math.floor(phase / (2.0 * Math.PI) + 0.5))) - 1.0;
                        case "sawtooth" -> val = 2.0 * (phase / (2.0 * Math.PI) - Math.floor(phase / (2.0 * Math.PI) + 0.5));
                        case "noise" -> val = Math.random() * 2.0 - 1.0;
                        case "fm" -> {
                            double mod = Math.sin(fmPhase) * (fmDepth / Math.max(1.0, fmFreq));
                            val = Math.sin(phase + mod);
                        }
                        case "harmonics" -> {
                            if (harmonicAmps != null) {
                                double sum = 0.0;
                                double maxAmp = 0.0;
                                for (int k = 0; k < harmonicAmps.length; k++) {
                                    double amp = harmonicAmps[k];
                                    sum += amp * Math.sin(phase * (k + 1));
                                    maxAmp += Math.abs(amp);
                                }
                                val = maxAmp > 0.0 ? sum / maxAmp : 0.0;
                            } else {
                                val = Math.sin(phase);
                            }
                        }
                        case "custom" -> {
                            if (evaluator != null) {
                                val = evaluator.evaluate(t, currentFreq, phase);
                            }
                        }
                        default -> val = Math.sin(phase);
                    }
                    
                    // Scale value (-1.0 to 1.0) with envelope and final volume
                    val = val * env * finalVolume;
                    short sampleVal = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, val * 32767.0));
                    
                    // Write 16-bit mono little-endian
                    pcmData[i * 2] = (byte) (sampleVal & 0xFF);
                    pcmData[i * 2 + 1] = (byte) ((sampleVal >> 8) & 0xFF);
                }

                // Play the sound
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();
                line.write(pcmData, 0, pcmData.length);
                line.drain();
                line.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
