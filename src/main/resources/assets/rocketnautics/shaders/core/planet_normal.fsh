#version 330

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D Sampler0; // Albedo
uniform sampler2D Sampler1; // Normal Map (Object Space)
uniform vec3 LightDir;

void main() {
    vec4 albedo = texture(Sampler0, texCoord);
    vec4 normSample = texture(Sampler1, texCoord);

    vec3 mapNormal = normalize(normSample.rgb * 2.0 - 1.0);

    vec3 light = normalize(LightDir);
    float normalDiffuse = dot(mapNormal, light);

    float shade = 0.3;

    if (normalDiffuse > 0.4) {
        shade = 1.0;
    } else if (normalDiffuse > -0.1) {
        shade = 0.62;
    }

    fragColor = vec4(
            albedo.rgb * shade,
            albedo.a * vertexColor.a
    );
}