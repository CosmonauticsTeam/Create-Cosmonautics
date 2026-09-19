#version 450

const float INFINITY = 1.0 / 0.0;
    const float PI = 3.14159265359;

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

vec3 voxelize(vec3 p, float voxelSz) {
    if (voxelSz <= 0.0) return p;
    return floor(p / voxelSz) * voxelSz + voxelSz * 0.5;
}

float densityAtP(int shape, vec3 pDensitySample, float rAtmosphere, float rPlanet, float densityFalloff, float voxelSz, mat4 invTransform) {
    vec3 pLocal = (invTransform * vec4(pDensitySample, 1.0)).xyz;
    pLocal = voxelize(pLocal, voxelSz);

    float distanceToSurface = 0;

    if (shape == 0) distanceToSurface = length(pLocal) - rPlanet;
    if (shape == 1) distanceToSurface = sdBox(pLocal, vec3(rPlanet));

    float height = distanceToSurface / (rAtmosphere - rPlanet);

    float localDensity = exp(densityFalloff * -height) / (rAtmosphere - rPlanet);

    return max(0.0, localDensity);
}

//return distance to first intersection, then distance to second from first

vec2 raySphere(float r, mat4 invTransform, vec3 rO, vec3 rD) {
    vec3 irO = (invTransform * vec4(rO, 1.0)).xyz;
    vec3 irD = (invTransform * vec4(rD, 0.0)).xyz;

    float a = dot(irD, irD);
    float b = 2.0 * dot(irO, irD);
    float c = dot(irO, irO) - r * r;

    float discriminant = b * b - 4.0 * a * c;

    if (discriminant >= 0.0) {
        float sqrtD = sqrt(discriminant);

        float x1 = (-b - sqrtD) / (2.0 * a);
        float x2 = (-b + sqrtD) / (2.0 * a);

        if (x2 >= 0.0) {
            x1 = max(x1, 0.0);
            return vec2(x1, x2 - x1);
        }
    }

    return vec2(INFINITY, 0.0);
}

vec2 rayCube(float hSz, mat4 invTransform, vec3 rO, vec3 rD) {
    vec3 irO = (invTransform * vec4(rO, 1.0)).xyz;
    vec3 irD = (invTransform * vec4(rD, 0.0)).xyz;

    vec3 invDir = 1.0 / irD;

    vec3 t1 = (-hSz - irO) * invDir;
    vec3 t2 = ( hSz - irO) * invDir;

    vec3 tMin = min(t1, t2);
    vec3 tMax = max(t1, t2);

    float dstNear = max(max(tMin.x, tMin.y), tMin.z);
    float dstFar  = min(min(tMax.x, tMax.y), tMax.z);

    if (dstFar < max(dstNear, 0.0))
    return vec2(INFINITY, 0.0);

    dstNear = max(dstNear, 0.0);

    return vec2(dstNear, dstFar - dstNear);
}

vec2 rayShape(int shape, float sz, mat4 invTransform, vec3 rO, vec3 rD) {
    if (shape == 0) return raySphere(sz, invTransform, rO, rD);
    if (shape == 1) return rayCube(sz, invTransform, rO, rD);

    return vec2(0);
}