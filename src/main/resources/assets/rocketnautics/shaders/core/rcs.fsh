#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float u_Time;       // Animates the gas motion
uniform float u_Throttle;   // Current thruster throttle level [0.0 - 1.0]

// High-contrast sine wave fractal for distinct gas spray puffs
float gasNoise(float u, float time) {
    float n = sin(u * 14.0 - time * 0.35) * 0.25;
    n += sin(u * 28.0 + time * 0.55) * 0.12;
    return n;
}

void main() {
    float u = texCoord.x;
    float v = texCoord.y;

    // --- PIXELATION FILTER ---
    // Lower steps to make pixels bigger and more prominent!
    float uPixelSteps = 16.0;
    float vPixelSteps = 24.0;
    
    float steppedU = floor(u * uPixelSteps) / uPixelSteps;
    float steppedV = floor(v * vPixelSteps) / vPixelSteps;

    // Continuous time for smooth 60+ FPS animation of pixelated gas puffs
    float smoothTime = u_Time * 6.0;

    float noiseVal = gasNoise(steppedU, smoothTime);
    
    // High-contrast threshold gating for distinct gaps between gas puffs
    float outerBoundary = 0.95 * u_Throttle + noiseVal * 1.5;

    vec4 finalColor = vec4(0.0);

    if (steppedV < outerBoundary) {
        float gasFactor = steppedV / outerBoundary;
        
        // High contrast color transition:
        // Core center puffs are hot white, blowing out to deep space cyan/navy
        vec3 gasBase = vec3(1.0, 1.0, 1.0);  // Blinding white core
        vec3 gasTip = vec3(0.1, 0.45, 0.85); // High-contrast cosmic blue
        
        // Apply steps to color interpolation for retro toon-shading feel
        float steppedFactor = floor(gasFactor * 4.0) / 4.0;
        vec3 color = mix(gasBase, gasTip, steppedFactor);
        
        // Sharpen the alpha edges using steps to make noise puffs stand out
        float puffAlpha = 1.0 - steppedFactor;
        float noiseEdge = step(-0.05, noiseVal); // Sharp cutoff for gaps
        float alpha = puffAlpha * noiseEdge * 0.75 * vertexColor.a;
        
        finalColor = vec4(color, alpha);
    } else {
        discard;
    }

    // Additive glow flare near the nozzle exit
    float exitGlow = (1.0 - step(0.2, steppedV)) * 0.35 * u_Throttle;
    finalColor.rgb += vec3(0.6, 0.8, 1.0) * exitGlow;
    finalColor.a = clamp(finalColor.a + exitGlow, 0.0, 1.0);

    fragColor = finalColor * vertexColor;
}
