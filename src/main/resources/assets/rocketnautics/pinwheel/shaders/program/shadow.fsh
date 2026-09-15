#version 450

in vec4 vertexColor;
in vec2 texCoord;
in vec3 objectPosition;

out vec4 fragColor;

uniform vec3 uLightDir;

void main() {
    vec3 light = normalize(uLightDir);
    float surfaceDiffuse = dot(normalize(objectPosition), light);

    float shadow = smoothstep(-0.3, 0.3, surfaceDiffuse);

    vec4 shadowColor = vec4(0.01, 0.001, 0.02, 0.9);
    fragColor = mix(shadowColor, vec4(0.0), shadow);
}