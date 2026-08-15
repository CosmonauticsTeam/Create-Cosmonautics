#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform sampler2D Sampler0; // Albedo
uniform sampler2D Sampler1; // Normal Map (Object Space)
uniform vec3 LightDir;

void main() {
    vec4 albedo = texture(Sampler0, texCoord);
    vec4 normSample = texture(Sampler1, texCoord);

    // Decode object-space normal map vector directly from RGB [0, 1] to [-1, 1]
    vec3 objectNormal = normalize(normSample.rgb * 2.0 - 1.0);

    // Calculate diffuse dot product with sun direction in object space
    vec3 L = normalize(LightDir);
    float diffuse = dot(objectNormal, L);

    // Shading steps matching index.html '3-step' cel shading:
    float shade = 0.3;
    if (diffuse > 0.4) {
        shade = 1.0;
    } else if (diffuse > -0.1) {
        shade = 0.62;
    }

    fragColor = vec4(albedo.rgb * shade, albedo.a * vertexColor.a);
}
