#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float u_Time;       // Animates the plume motion
uniform float u_Throttle;   // Current engine throttle level [0.0 - 1.0]

// Simple 2D Pseudo-noise function
float noise(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
}

// Multi-layered sine wave fractal for smooth organic gas movement
float heightNoise(float u, float time) {
    float n = sin(u * 12.0 - time * 0.15) * 0.15;
    n += sin(u * 28.0 + time * 0.32) * 0.08;
    n += cos(u * 45.0 - time * 0.45) * 0.04;
    return n;
}

void main() {
    // Normalised coordinate mapping:
    // U (X-axis) wraps around the pyramid sides [0.0 - 1.0]
    // V (Y-axis) runs down the exhaust stream [0.0 - 1.0]
    float u = texCoord.x;
    float v = texCoord.y;

    float time = u_Time * 50.0; // Fast scale for flame speed

    // Generate dynamic height threshold using height noise
    float noiseVal = heightNoise(u, time);
    
    // Core boundary (white-hot inner flame) with jagged height noise edge
    float coreBoundary = 0.55 * u_Throttle + noiseVal;
    
    // Outer plume boundary (colored gas fading to transparent)
    float outerBoundary = 0.95 * u_Throttle + noiseVal * 1.5;

    vec4 finalColor = vec4(0.0);

    // 1. Calculate White-Hot Core
    if (v < coreBoundary) {
        // High intensity core fading slightly towards the boundary
        float coreIntensity = smoothstep(coreBoundary, 0.0, v);
        
        // Pure blinding white center, fading into a yellowish edge
        vec3 coreColor = mix(vec3(1.0, 0.85, 0.5), vec3(1.0, 1.0, 1.0), coreIntensity);
        finalColor = vec4(coreColor, 0.95);
    } 
    // 2. Calculate Outer Plume Shell (Gradients from Hot Orange to Cosmic Violet)
    else if (v < outerBoundary) {
        // Relative position inside the outer shell region
        float shellFactor = (v - coreBoundary) / (outerBoundary - coreBoundary);
        
        // Colors mapping: base (0.0) is fiery orange/yellow, middle is red, tail (1.0) is violet/magenta
        vec3 fieryBase = vec3(1.0, 0.5, 0.1);    // Bright orange
        vec3 violetTip = vec3(0.5, 0.1, 0.9);    // Deep violet
        
        vec3 outerColor = mix(fieryBase, violetTip, shellFactor);
        
        // Soft edge fadeout at the tail of the plume
        float alpha = (1.0 - shellFactor) * 0.75 * vertexColor.a;
        
        finalColor = vec4(outerColor, alpha);
    } 
    // 3. Complete transparent zone
    else {
        discard;
    }

    // Additive glow flare near the engine nozzle exit (V = 0.0)
    float exitGlow = (1.0 - smoothstep(0.0, 0.25, v)) * 0.35 * u_Throttle;
    finalColor.rgb += vec3(1.0, 0.75, 1.0) * exitGlow;
    finalColor.a = clamp(finalColor.a + exitGlow, 0.0, 1.0);

    fragColor = finalColor * vertexColor;
}
