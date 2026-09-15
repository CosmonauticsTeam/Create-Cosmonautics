#version 450

#include rocketnautics:common_math
#include veil:space_helper

uniform sampler2D sDiffuse;
uniform sampler2D sDiffuseDepth;
uniform sampler2D sOpticalDepth;

uniform int shape; // 0 - sphere, 1 - cube

uniform float rPlanet;
uniform float rAtmosphere;

uniform vec3 sunDir;

uniform float intensity;

uniform float densityFalloff;

uniform vec3 scatteringCoefficients;

uniform mat4 transform;
uniform mat4 invTransform;

vec3 planetOrigin = transform[3].xyz;
float scale = (length(transform[0]) + length(transform[1]) + length(transform[2])) / 3;

uniform float voxelSz;

uniform int numInScatteringPoints;

uniform int depthTest;

in vec2 texCoord;
out vec4 fragColor;

float sampleOpticalDepth(vec3 rO, vec3 rD) {
    float height = length(rO - planetOrigin) - rPlanet;
    float altitude = clamp(height / (rAtmosphere - rPlanet), 0.0, 1.0);

    float mu = dot(normalize(rO - planetOrigin), rD);
    float uvX = 0.5 - 0.5 * mu;

    return texture(sOpticalDepth, vec2(uvX, altitude)).r * (rAtmosphere - rPlanet);
}

vec3 light(vec3 rO, vec3 rD, float rL, vec4 colorIn, mat4 invTransform) {
    if (rL <= 0.0) return colorIn.rgb;

    float stepSz = voxelSz > 0.0 ? voxelSz * scale : rL / float(max(numInScatteringPoints, 1));

    int steps = int(ceil(rL / stepSz));
    steps = max(steps, 1);
    steps = min(steps, numInScatteringPoints);

    vec3 inScatteredLight = vec3(0.0);
    float viewRayOpticalDepth = 0.0;

    for (int i = 0; i < steps; i++) {
        float t0 = float(i) / float(steps);
        float t1 = float(i + 1) / float(steps);

        float ds = rL * (t1 - t0);
        float t = (t0 + t1) * 0.5;

        vec3 p = rO + rD * (t * rL);

        float localDensity = densityAtP(shape, p, rAtmosphere, rPlanet, densityFalloff, voxelSz, invTransform);
        if (localDensity <= 0.0) continue;

        float sunRayOpticalDepth = sampleOpticalDepth(p, sunDir);
        viewRayOpticalDepth += localDensity * ds;

        vec3 transmittance = exp( -(sunRayOpticalDepth + viewRayOpticalDepth) * scatteringCoefficients);

        inScatteredLight += localDensity * transmittance * ds;
    }

    inScatteredLight *= scatteringCoefficients * intensity;

    vec3 inTransmittance = exp(-viewRayOpticalDepth * scatteringCoefficients);
    return colorIn.rgb * inTransmittance + inScatteredLight;
}

void main() {
    vec4 col = texture(sDiffuse, texCoord);
    float depth = texture(sDiffuseDepth, texCoord).r;

    vec3 rO = screenToWorldSpace(texCoord, 0).xyz;
    vec3 rD = viewDirFromUv(texCoord);

    fragColor = col;

    vec3 terrainPos = screenToWorldSpace(texCoord, depth).xyz;
    float dstToTerrain = length(terrainPos - rO) * scale;

    float dstToSurface = min(rayShape(shape, rPlanet, invTransform, rO, rD).x, depthTest == 1 ? dstToTerrain : INFINITY);
    vec2 hitInfo = rayShape(shape, rAtmosphere, invTransform, rO, rD);

    float dstToAtmo = hitInfo.x;
    float dstThruAtmo = min(hitInfo.y, dstToSurface - dstToAtmo);

    if (dstThruAtmo > 0) {
        const float epsilon = 0.0001;
        vec3 pointInAtmo = rO + rD * (dstToAtmo + epsilon);
        vec3 light = light(pointInAtmo, rD, dstThruAtmo - epsilon * 2, col, invTransform);

        fragColor = vec4(light, 1.0);
    }
}