#version 450

out vec2 texCoord;

void main() {
    vec2 pos = vec2(
            ((gl_VertexID & 1) << 2) - 1.0,
            ((gl_VertexID & 2) << 1) - 1.0
    );

    texCoord = vec2((pos.x + 1.0) * 0.5, (pos.y + 1.0) * 0.5);
    gl_Position = vec4(pos.x, pos.y, 0.0, 1.0);
}