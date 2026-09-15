#version 450

#include rocketnautics:common_math
#include veil:space_helper

uniform int shape;

uniform float rCore;
uniform float rPlasma;

uniform float intensity;

uniform float densityFalloff;

uniform vec3 color;

uniform mat4 transform;
uniform mat4 invTransform;

vec3 starOrigin = transform[3].xyz;
float scale = (length(transform[0]) + length(transform[1]) + length(transform[2])) / 3;

uniform float voxelSz;

uniform int numInScatteringPoints;

in vec2 texCoord;
out vec4 fragColor;

vec3 light(vec3 rO, vec3 rD, float rL, mat4 invTransform) {
    if (rL <= 0.0) return vec3(0);

    float stepSz = voxelSz > 0.0 ? voxelSz * scale : rL / float(max(numInScatteringPoints, 1));

    int steps = int(ceil(rL / stepSz));
    steps = max(steps, 1);
    steps = min(steps, numInScatteringPoints);

    float opticalDepth = 0.0;
    vec3 scatteredLight = vec3(0.0);

    for (int i = 0; i < steps; i++) {
        float t0 = float(i) / float(steps);
        float t1 = float(i + 1) / float(steps);

        float ds = rL * (t1 - t0);
        float t = (t0 + t1) * 0.5;

        vec3 p = rO + rD * (t * rL);

        float localDensity = densityAtP(shape, p, rPlasma, rCore, densityFalloff, voxelSz, invTransform);
        if (localDensity <= 0.0) continue;

        float transmittance = exp(-opticalDepth);

        scatteredLight += color * transmittance * localDensity * ds * intensity;
        opticalDepth += localDensity * ds;
    }

    return scatteredLight;
}

void main() {
    vec3 rO = screenToWorldSpace(texCoord, 0).xyz;
    vec3 rD = viewDirFromUv(texCoord);

    vec2 hitInfo = rayShape(shape, rPlasma, invTransform, rO, rD);

    if (hitInfo.y > 0) {
        const float epsilon = 0.0001;
        vec3 pointInPlasma = rO + rD * (hitInfo.x + epsilon);
        vec3 light = light(pointInPlasma, rD, hitInfo.y - epsilon * 2, invTransform);

        fragColor = clamp(vec4(light, 1.0), 0.0, 1.0);
    }
}