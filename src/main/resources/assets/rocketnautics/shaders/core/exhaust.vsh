#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float u_Time;
uniform float u_Throttle;

// Fast pseudo-random 3D noise hash
float hash3(vec3 p) {
    p = fract(p * vec3(443.897, 441.423, 437.195));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec3 pos = Position;

    // Stepped time for aggressive, crunchy 35Hz geometry twitching
    float timeStep = floor(u_Time * 35.0);

    // Distortion increases down the exhaust stream (UV0.y = 0 at nozzle, 1 at tail)
    float distFactor = pow(UV0.y, 1.1) * max(u_Throttle, 0.3);

    if (distFactor > 0.001) {
        // High spatial frequency ensures different vertices of the same quad get distinct offsets, breaking polygon symmetry!
        float noiseX = hash3(pos * 24.0 + vec3(timeStep * 1.73, 0.0, 0.0)) - 0.5;
        float noiseY = hash3(pos * 24.0 + vec3(0.0, timeStep * 2.31, 0.0)) - 0.5;
        float noiseZ = hash3(pos * 24.0 + vec3(0.0, 0.0, timeStep * 3.17)) - 0.5;

        vec3 jitter = vec3(noiseX, noiseY * 0.3, noiseZ) * 0.16 * distFactor;
        pos += jitter;
    }

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = Color;
    texCoord = UV0;
}

