#version 330

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float u_Time;       // Animates plasma fluidity at game FPS
uniform float u_Intensity;  // Reentry heat level [0.0 - 1.0]

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    float u = texCoord.x;
    float v = texCoord.y;

    float time = u_Time * 4.0;

    // 1. Broad, thick cinematic flame streaks (lower U frequency for wide, smooth plasma tongues)
    vec2 streakCoord = vec2(u * 4.5, v * 1.2 - time * 1.2);
    float streakPattern = noise(streakCoord);

    vec2 waveCoord = vec2(u * 9.0, v * 2.5 - time * 2.5);
    float waveNoise = noise(waveCoord);

    float plasmaNoise = mix(streakPattern, waveNoise, 0.40);

    // 2. Soft turbulent flame boundary fade
    float boundary = 0.98 * u_Intensity + (plasmaNoise - 0.5) * 0.28;
    float alphaFade = smoothstep(1.0, 0.0, v / boundary);

    if (alphaFade <= 0.001) {
        discard;
    }

    float factor = clamp(v / boundary, 0.0, 1.0);

    // 3. Cinematic Real-World Reentry Color Palette (Matching Reference Image):
    // Shock Front (V ~ 0.0)    -> Glowing White-Gold / Incandescent Heat
    // Mid Stream (V ~ 0.3-0.6) -> Radiant Pink / Magenta Plasma
    // Outer Stream (V ~ 0.6-0.8) -> Soft Amethyst Violet
    // Rear Wake (V ~ 0.8-1.0)  -> Deep Ultramarine Blue / Violet
    vec3 colorWhiteGold = vec3(1.0, 0.97, 0.85);
    vec3 colorCreamPeach = vec3(1.0, 0.72, 0.45);
    vec3 colorPinkMagenta = vec3(0.96, 0.38, 0.72);
    vec3 colorAmethyst = vec3(0.62, 0.22, 0.88);
    vec3 colorUltramarine = vec3(0.22, 0.35, 0.95);

    vec3 plasmaColor;
    if (factor < 0.20) {
        plasmaColor = mix(colorWhiteGold, colorCreamPeach, factor / 0.20);
    } else if (factor < 0.55) {
        plasmaColor = mix(colorCreamPeach, colorPinkMagenta, (factor - 0.20) / 0.35);
    } else if (factor < 0.80) {
        plasmaColor = mix(colorPinkMagenta, colorAmethyst, (factor - 0.55) / 0.25);
    } else {
        plasmaColor = mix(colorAmethyst, colorUltramarine, (factor - 0.80) / 0.20);
    }

    // 4. Smooth broad brightness modulation
    plasmaColor *= 0.85 + 0.30 * plasmaNoise;

    // 5. Exponential soft alpha decay for atmospheric glow transparency
    float finalAlpha = pow(alphaFade, 1.35) * 0.92 * u_Intensity * vertexColor.a;

    // 6. Incandescent Shockwave Flare at the leading impact edge (v -> 0)
    float shockFlare = (1.0 - smoothstep(0.0, 0.15, v)) * 0.65 * u_Intensity;
    vec3 finalRGB = mix(plasmaColor, vec3(1.0, 0.98, 0.92), shockFlare);

    fragColor = vec4(finalRGB, clamp(finalAlpha + shockFlare * 0.4, 0.0, 1.0)) * vertexColor;
}
