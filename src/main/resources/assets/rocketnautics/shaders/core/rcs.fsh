#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float u_Time;       // Animates the gas motion
uniform float u_Throttle;   // Current thruster throttle level [0.0 - 1.0]

// Pseudo-random hash for retro pixelated noise
float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float gasNoise(float u, float v, float time) {
    float n = sin(u * 16.0 - time * 0.4) * 0.22;
    n += sin((u + v) * 28.0 + time * 0.75) * 0.14;
    n += (hash2(vec2(floor(u * 24.0), floor((v - time * 0.25) * 32.0))) - 0.5) * 0.15;
    return n;
}

void main() {
    float u = texCoord.x;
    float v = texCoord.y;

    // --- PIXELATION FILTER ---
    float uPixelSteps = 24.0;
    float vPixelSteps = 36.0;
    
    float steppedU = floor(u * uPixelSteps) / uPixelSteps;
    float steppedV = floor(v * vPixelSteps) / vPixelSteps;

    // Continuous time for high FPS smooth pixelated gas puffs
    float smoothTime = u_Time * 10.0;

    float noiseVal = gasNoise(steppedU, steppedV, smoothTime);
    
    // Supersonic shock diamonds (standing wave contraction rings down the gas jet)
    float shockPhase = steppedV * 22.0 - smoothTime * 1.5;
    float shockRing = pow(abs(sin(shockPhase)), 4.0) * (1.0 - steppedV * 0.8);

    // High-contrast outer boundary threshold
    float outerBoundary = 0.95 * u_Throttle + noiseVal * 0.3;

    vec4 finalColor = vec4(0.0);

    if (steppedV < outerBoundary) {
        float gasFactor = steppedV / outerBoundary;
        
        // Supersonic core stream check
        float centerDist = abs(steppedU - 0.5) * 2.0;
        float isCore = step(centerDist, 0.45 - steppedV * 0.3);

        // High contrast color palette:
        // Core center puffs: Pure blinding white -> Electric Icy Cyan -> Cosmic Sapphire -> Deep Space Purple
        vec3 colWhite   = vec3(1.0, 1.0, 1.0);       // Blinding core
        vec3 colCyan    = vec3(0.2, 0.85, 1.0);      // Vibrant electric cyan
        vec3 colSapphire= vec3(0.12, 0.35, 0.9);     // Cosmic sapphire blue
        vec3 colPurple  = vec3(0.35, 0.1, 0.6);      // Outer void purple
        
        float steppedFactor = floor(gasFactor * 5.0) / 5.0;
        vec3 color;
        if (steppedFactor < 0.25) {
            color = mix(colWhite, colCyan, steppedFactor * 4.0);
        } else if (steppedFactor < 0.65) {
            color = mix(colCyan, colSapphire, (steppedFactor - 0.25) * 2.5);
        } else {
            color = mix(colSapphire, colPurple, (steppedFactor - 0.65) * 2.85);
        }

        // Add bright shock diamonds into the core stream
        vec3 shockColor = mix(vec3(0.7, 0.95, 1.0), vec3(1.0, 1.0, 1.0), shockRing);
        color = mix(color, shockColor, shockRing * isCore * u_Throttle * 0.75);
        
        // Quantized alpha stepping for clean retro pixel edges
        float puffAlpha = 1.0 - pow(steppedFactor, 1.4);
        float noiseEdge = step(-0.15, noiseVal);
        float alpha = puffAlpha * noiseEdge * 0.85 * vertexColor.a;
        
        finalColor = vec4(color, alpha);
    } else {
        discard;
    }

    // Additive glow flare near the nozzle exit with intense shock core
    float exitGlow = (1.0 - step(0.18, steppedV)) * 0.65 * u_Throttle;
    finalColor.rgb += vec3(0.65, 0.9, 1.0) * exitGlow;
    finalColor.a = clamp(finalColor.a + exitGlow, 0.0, 1.0);

    fragColor = finalColor * vertexColor;
}

