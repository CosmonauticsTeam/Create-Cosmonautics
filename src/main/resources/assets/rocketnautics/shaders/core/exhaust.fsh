#version 330

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

    // Continuous time for smooth 60+ FPS animation of pixelated flame
    float smoothTime = u_Time * 50.0;

    // Generate quantised height threshold using pixelated coordinates
    float noiseVal = heightNoise(steppedU, smoothTime);
    
    // Core boundary (white-hot inner flame) with jagged pixelated height noise edge
    float coreBoundary = 0.52 * u_Throttle + noiseVal;
    
    // Outer plume boundary (colored gas fading to transparent)
    float outerBoundary = 0.95 * u_Throttle + noiseVal * 1.5;

    vec4 finalColor = vec4(0.0);
    
    // The base engine color comes from the vertexColor (which java sets dynamically!)
    vec3 baseColor = vertexColor.rgb;

    // 1. Calculate White-Hot Core
    if (steppedV < coreBoundary) {
        // High intensity core fading slightly towards the boundary
        float coreIntensity = smoothstep(coreBoundary, 0.0, steppedV);
        
        // Pure blinding white center, fading into a baseColor-blended edge
        vec3 coreColor = mix(mix(baseColor, vec3(1.0), 0.75), vec3(1.0), coreIntensity);
        finalColor = vec4(coreColor, 0.95);
    } 
    // 2. Calculate Outer Plume Shell (Gradients from Base Color to shifted Tail)
    else if (steppedV < outerBoundary) {
        // Relative position inside the outer shell region
        float shellFactor = (steppedV - coreBoundary) / (outerBoundary - coreBoundary);
        
        vec3 fieryBase = baseColor;
        
        // Purple shift at the tail
        vec3 violetTip = mix(baseColor * 0.5, vec3(0.4, 0.1, 0.8), 0.4);
        
        vec3 outerColor = mix(fieryBase, violetTip, shellFactor);
        
        // Soft edge fadeout at the tail of the plume (with stepped gradient)
        float alpha = (1.0 - floor(shellFactor * 8.0) / 8.0) * 0.75;
        
        finalColor = vec4(outerColor, alpha);
    } 
    // 3. Complete transparent zone
    else {
        discard;
    }

    // Additive glow flare near the engine nozzle exit (stepped V)
    float exitGlow = (1.0 - smoothstep(0.0, 0.25, steppedV)) * 0.35 * u_Throttle;
    finalColor.rgb += mix(baseColor, vec3(1.0), 0.5) * exitGlow;
    finalColor.a = clamp(finalColor.a + exitGlow, 0.0, 1.0);

    // Multiply by vertexColor.a to preserve layer opacity passed from Java
    fragColor = finalColor * vec4(1.0, 1.0, 1.0, vertexColor.a);
}
