#version 450

#include rocketnautics:common_math
#include veil:space_helper

uniform sampler2D sDiffuse;
uniform sampler2D sDiffuseDepth;

uniform vec2 uResolution;
uniform float uThreshold;
uniform float uIntensity;
uniform float uChromaticAbberation;
uniform vec3 uLightPos;
uniform float uRayPhase;

vec2 lightScreenPos = worldToScreenSpace(vec4(uLightPos, 1.0)).xy;

in vec2 texCoord;
out vec4 fragColor;

vec2 pixelateUV(vec2 uv) {
    float aspect = uResolution.x / uResolution.y;

    vec2 p = uv - 0.5;
    p.x *= aspect;

    float pixelSize = 1.0 / uResolution.y;
    p = floor(p / pixelSize) * pixelSize;

    p.x /= aspect;
    return p + 0.5;
}

vec3 brightPass(vec2 uv) {
    vec3 color = texture(sDiffuse, uv).rgb;

    float lum = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    float mask = smoothstep(uThreshold, uThreshold + 0.5, lum);

    return color * mask;
}

vec3 lensGhosts(vec2 uv) {
    vec2 center = vec2(0.5);
    vec2 dir = uv - center;

    vec3 result = vec3(0.0);

    const int GHOSTS = 4;
    for (int i = 0; i < GHOSTS; i++) {
        float t = (float(i) + 0.5) / float(GHOSTS);

        vec2 ghostUV = center - dir * t;

        float separation = uChromaticAbberation * length(dir);

        vec2 chromaDir = normalize(dir + vec2(0.00001));

        vec2 rUV = ghostUV + chromaDir * separation;
        vec2 gUV = ghostUV;
        vec2 bUV = ghostUV - chromaDir * separation;

        float r = brightPass(rUV).r;
        float g = brightPass(gUV).g;
        float b = brightPass(bUV).b;

        vec3 chroma = vec3(r, g, b);
        float weight = 1.0 - t;

        result += chroma * weight;
    }

    return result / float(GHOSTS);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float noise1D(float x) {
    float i = floor(x);
    float f = fract(x);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash11(i);
    float b = hash11(i + 1.0);

    return mix(a, b, f);
}

float angularNoise(float angle, float frequency) {
    float x = angle / (2.0 * PI) * frequency;
    return noise1D(x);
}

float angularNoise(float angle, float frequency, float offset) {
    float x = angle / (2.0 * PI) * frequency + offset;
    return noise1D(x);
}

float rays(vec2 uv, float phase, int rayCount, float sharpness, float noiseAmmount, float falloff) {
    vec2 p = uv - lightScreenPos;
    p.x *= uResolution.x / uResolution.y;

    float radius = length(p);
    float angle = atan(p.y, p.x);

    float rayPattern = pow(max(0.0, cos(angle * float(rayCount) + phase)), sharpness);

    float noise = angularNoise(angle, float(rayCount) * 0.5, uRayPhase);
    noise = mix(1.0 - noiseAmmount, 1.0, noise);

    rayPattern *= noise;

    float radialFalloff = exp(-radius * falloff);
    return rayPattern * radialFalloff;
}

vec3 glow(vec2 uv, float falloff) {
    vec2 p = uv - lightScreenPos;
    p.x *= uResolution.x / uResolution.y;

    float d = length(p);

    float radialFalloff = exp(-d * falloff);

    vec3 color = mix(vec3(0.9922, 0.702, 0.5451), vec3(1.0), radialFalloff);
    return color * radialFalloff;
}

vec3 halo(vec2 uv, float rMin, float width, float intensity) {
    vec2 p = uv - lightScreenPos;
    p.x *= uResolution.x / uResolution.y;

    float d = length(p);

    float t = clamp((d - rMin) / width, 0.0, 1.0);

    float x = (t - 0.5) * 2.0;
    float ring = exp(-x * x * 3.0);

    vec3 cyan = vec3(0.5059, 0.9255, 0.9333);
    vec3 green = vec3(0.8863, 0.851, 0.4941);
    vec3 yellow = vec3(0.9529, 0.6902, 0.6275);
    vec3 red = vec3(0.95, 0.60, 0.45);

    float cyanPos = 0.25;
    float greenPos = 0.5;
    float yellowPos = 0.6;
    float redPos = 0.80;

    float cyanWidth = 0.30;
    float greenWidth = 0.045;
    float yellowWidth = 0.045;
    float redWidth = 0.30;

    float cyanW = exp(-pow((t - cyanPos) / cyanWidth, 2.0));
    float greenW = exp(-pow((t - greenPos) / greenWidth, 2.0));
    float yellowW = exp(-pow((t - yellowPos) / yellowWidth, 2.0));
    float redW = exp(-pow((t - redPos) / redWidth, 2.0));

    float totalW = cyanW + greenW + yellowW + redW;

    vec3 col = (
        cyan * cyanW +
        green * greenW +
        yellow * yellowW +
        red * redW
    ) / max(totalW, 0.0001);

    return col * ring * intensity;
}

void main() {
    vec4 col = texture(sDiffuse, texCoord);

    float depth = texture(sDiffuseDepth, lightScreenPos).r;
    if (depth < 1.0) return;

    vec3 ghosts = lensGhosts(texCoord);

    vec2 pixelUV = pixelateUV(texCoord);

    float rays =
          rays(pixelUV, 0.0, 32, 0.8, 1.5, 10.0) * 0.7
        + rays(pixelUV, 2.0, 16, 1.0, 2.0, 5.0) * 0.9
        + rays(pixelUV, 3.0, 64, 2.0, 1.0, 15.0) * 1.2
        + rays(pixelUV, 2.5, 6, 1.0, 2.0, 4.0) * 0.6
        + rays(pixelUV, 0.5, 64, 1.0, 1.0, 10.0) * 0.5
        + rays(pixelUV, 0.0, 2, 1000.0, 0.0, 9.0) * 5.0;

    vec3 glow = glow(pixelUV, 15.0);

    vec3 halo1 = halo(pixelUV, 0.11, 0.111, 0.2);
    vec3 halo2 = halo(pixelUV, 0.2, 0.3, 0.3);

    fragColor = clamp(
          clamp(vec4(rays) * uIntensity * 0.3 - vec4(brightPass(texCoord), 1.0), 0.0, 1.0)
        + vec4(ghosts, 1.0) * uIntensity
        + vec4(glow, 1.0) * uIntensity * 2
        + vec4(halo1, 1.0) * uIntensity * 0.2
        + vec4(halo2, 1.0) * uIntensity * 0.2,
    0.0, 1.0);
}