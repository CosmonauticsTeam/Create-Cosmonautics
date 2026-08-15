package dev.devce.rocketnautics.client.render.shader;

import foundry.veil.api.client.render.shader.processor.ShaderPreProcessor;
import io.github.ocelot.glslprocessor.api.GlslInjectionPoint;
import io.github.ocelot.glslprocessor.api.GlslParser;
import io.github.ocelot.glslprocessor.api.GlslSyntaxException;
import io.github.ocelot.glslprocessor.api.node.GlslTree;
import net.minecraft.client.renderer.RenderType;

import java.io.IOException;
import java.util.List;

/**
 * Veil ShaderPreProcessor that injects realistic sun-directional PBR lighting
 * into Minecraft chunk shaders for Sable SubLevel structures.
 *
 * Uses the same mechanism as Sable's SableDynamicDirectionalShadingPreProcessor:
 * targets all rendertype_* vertex shaders and injects GLSL uniforms + code.
 *
 * Injected uniforms (set each frame by SableSubLevelLightingHandler):
 *   - SunDirection: normalized sun direction in world space
 *   - SunEnabled:   1.0 on SubLevel, 0.0 elsewhere
 *   - SunIntensity: star brightness [0..1]
 */
public class SunDirectionalShadingPreProcessor implements ShaderPreProcessor {

    public static final String SUN_DIRECTION_UNIFORM = "SunDirection";
    public static final String SUN_ENABLED_UNIFORM   = "SunEnabled";
    public static final String SUN_INTENSITY_UNIFORM = "SunIntensity";

    // Realistic PBR directional lighting for space sublevels with 3x3 PCF Directional Shadow Mapping:
    // - Shadow map depth comparison for true cast shadows from occluding blocks/pillars
    // - Uses UV2.y (sky light access) for interior occlusion (zero sunlight inside rooms)
    // - Smooth terminator transition with natural ambient starlight
    // - Preserves vertex Color AO gradients without crushing dark side textures
    // - Specular reflection on sunny metal/hull surfaces
    private static final String PBR_LIGHTING_GLSL =
        "float _subLevelLit = SunEnabled;" +
        "if (_subLevelLit > 0.5) {" +
        "  float skyExposure = clamp(float(UV2.y) / 240.0, 0.0, 1.0);" +
        "  vec4 torchSample = minecraft_sample_lightmap(Sampler2, ivec2(UV2.x, 0));" +
        "  vec3 _computedNormal = inverse(NormalMat) * (mat3(ModelViewMat) * Normal);" +
        "  vec3 worldNormal = length(_computedNormal) > 0.01 ? normalize(_computedNormal) : Normal;" +
        "  vec3 sunDir = normalize(SunDirection);" +
        "  float NdotL = dot(worldNormal, sunDir);" +
        "  vec4 viewPos = ModelViewMat * vec4(pos, 1.0);" +
        "  vec4 lightSpace = LightSpaceMat * viewPos;" +
        "  vec3 sc = lightSpace.xyz / lightSpace.w;" +
        "  float shadow = 1.0;" +
        "  if (sc.x >= 0.001 && sc.x <= 0.999 && sc.y >= 0.001 && sc.y <= 0.999 && sc.z >= 0.001 && sc.z <= 0.999) {" +
        "    float bias = max(0.0045 * (1.0 - max(NdotL, 0.0)), 0.0016);" +
        "    float sSum = 0.0;" +
        "    vec2 tSz = vec2(1.0 / 2048.0);" +
        "    for (int ix = -1; ix <= 1; ++ix) {" +
        "      for (int iy = -1; iy <= 1; ++iy) {" +
        "        float d = texture(SunShadowSampler, sc.xy + vec2(ix, iy) * tSz).r;" +
        "        sSum += (sc.z - bias > d) ? 0.0 : 1.0;" +
        "      }" +
        "    }" +
        "    shadow = sSum / 9.0;" +
        "  }" +
        "  float sunDiffuse = clamp(NdotL * 1.3, 0.0, 1.0) * shadow * max(SunIntensity, 0.01);" +
        "  vec3 SUN_COLOR = vec3(1.36, 1.32, 1.25);" +
        "  vec3 SPACE_AMBIENT = vec3(0.008, 0.010, 0.015);" +
        "  vec3 directSun = SUN_COLOR * sunDiffuse;" +
        "  vec3 ambientLight = SPACE_AMBIENT;" +
        "  vec3 viewSun = normalize((ModelViewMat * vec4(sunDir, 0.0)).xyz);" +
        "  vec3 viewNorm = normalize(mat3(ModelViewMat) * Normal);" +
        "  vec3 halfVec = normalize(viewSun + vec3(0.0, 0.0, 1.0));" +
        "  float spec = pow(max(dot(viewNorm, halfVec), 0.0), 32.0) * sunDiffuse * 0.40;" +
        "  float fresnel = pow(clamp(1.0 - abs(viewNorm.z), 0.0, 1.0), 3.0);" +
        "  vec3 rimLight = (SUN_COLOR * max(NdotL, 0.0) * 0.8 + vec3(0.15, 0.25, 0.45) * 0.2) * fresnel * 0.40;" +
        "  vec3 outerLight = ambientLight + directSun + (SUN_COLOR * spec) + rimLight;" +
        "  vec3 finalLight = outerLight + torchSample.rgb;" +
        "  vertexColor.rgb = Color.rgb * mix(minecraft_sample_lightmap(Sampler2, UV2).rgb, finalLight, _subLevelLit);" +
        "}";

    @Override
    public void modify(Context ctx, GlslTree tree) throws GlslSyntaxException, IOException {
        if (!ctx.isVertex()) return;

        if (ctx instanceof MinecraftContext minecraftContext) {
            List<RenderType> renderTypes = RenderType.chunkBufferLayers();
            boolean anyMatches = false;
            for (RenderType renderType : renderTypes) {
                if (minecraftContext.shaderInstance().equals("rendertype_" + renderType.name)) {
                    anyMatches = true;
                    break;
                }
            }
            if (!anyMatches) return;
        } else {
            return;
        }

        // Add NormalMat uniform if not present
        if (tree.field("NormalMat").isEmpty()) {
            tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN,
                GlslParser.parseExpression("uniform mat3 NormalMat;"));
        }

        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN,
            GlslParser.parseExpression("uniform vec3 " + SUN_DIRECTION_UNIFORM + ";"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN,
            GlslParser.parseExpression("uniform float " + SUN_ENABLED_UNIFORM + ";"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN,
            GlslParser.parseExpression("uniform float " + SUN_INTENSITY_UNIFORM + ";"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN,
            GlslParser.parseExpression("uniform sampler2D SunShadowSampler;"));
        tree.getBody().add(GlslInjectionPoint.BEFORE_MAIN,
            GlslParser.parseExpression("uniform mat4 LightSpaceMat;"));

        // Inject PBR code at the end of main()
        var body = tree.mainFunction().orElseThrow().getBody();
        for (var node : GlslParser.parseExpressionList(PBR_LIGHTING_GLSL)) {
            body.add(node);
        }
    }
}