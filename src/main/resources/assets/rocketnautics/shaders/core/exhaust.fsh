#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float u_Time;       // Animates the plume motion
uniform float u_Throttle;   // Current engine throttle level [0.0 - 1.0]

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

    // --- PIXELATION FILTER ---
    // Grids the UV space into pixel steps to give a retro pixelated flame shape.
    // 32.0 pixels wide, 48.0 pixels tall down the plume stream.
    float uPixelSteps = 32.0;
    float vPixelSteps = 48.0;
    
    float steppedU = floor(u * uPixelSteps) / uPixelSteps;
    float steppedV = floor(v * vPixelSteps) / vPixelSteps;

    // Fast scale for flame speed (stepped/quantised time for stop-motion pixel effect)
    float timeSteps = 12.0; // 12 FPS stop-motion animation look for flame
    float steppedTime = floor(u_Time * timeSteps) / timeSteps * 50.0;

    // Generate quantised height threshold using pixelated coordinates
    float noiseVal = heightNoise(steppedU, steppedTime);
    
    // Core boundary (white-hot inner flame) with jagged pixelated height noise edge
    float coreBoundary = 0.52 * u_Throttle + noiseVal;
    
    // Outer plume boundary (colored gas fading to transparent)
    float outerBoundary = 0.95 * u_Throttle + noiseVal * 1.5;

    vec4 finalColor = vec4(0.0);

    // 1. Calculate White-Hot Core
    if (steppedV < coreBoundary) {
        // High intensity core fading slightly towards the boundary
        float coreIntensity = smoothstep(coreBoundary, 0.0, steppedV);
        
        // Pure blinding white center, fading into a yellowish edge
        vec3 coreColor = mix(vec3(1.0, 0.85, 0.5), vec3(1.0, 1.0, 1.0), coreIntensity);
        finalColor = vec4(coreColor, 0.95);
    } 
    // 2. Calculate Outer Plume Shell (Gradients from Hot Orange to Cosmic Violet)
    else if (steppedV < outerBoundary) {
        // Relative position inside the outer shell region
        float shellFactor = (steppedV - coreBoundary) / (outerBoundary - coreBoundary);
        
        // Colors mapping: base (0.0) is fiery orange/yellow, middle is red, tail (1.0) is violet/magenta
        vec3 fieryBase = vec3(1.0, 0.5, 0.1);    // Bright orange
        vec3 violetTip = vec3(0.5, 0.1, 0.9);    // Deep violet
        
        vec3 outerColor = mix(fieryBase, violetTip, shellFactor);
        
        // Soft edge fadeout at the tail of the plume (with stepped gradient)
        float alpha = (1.0 - floor(shellFactor * 8.0) / 8.0) * 0.75 * vertexColor.a;
        
        finalColor = vec4(outerColor, alpha);
    } 
    // 3. Complete transparent zone
    else {
        discard;
    }

    // Additive glow flare near the engine nozzle exit (stepped V)
    float exitGlow = (1.0 - smoothstep(0.0, 0.25, steppedV)) * 0.35 * u_Throttle;
    finalColor.rgb += vec3(1.0, 0.75, 1.0) * exitGlow;
    finalColor.a = clamp(finalColor.a + exitGlow, 0.0, 1.0);

    fragColor = finalColor * vertexColor;
}
