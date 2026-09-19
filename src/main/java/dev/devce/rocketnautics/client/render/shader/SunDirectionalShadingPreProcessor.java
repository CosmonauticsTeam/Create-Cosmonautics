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
 * Veil ShaderPreProcessor that injects stylized, pixelated space directional lighting
 * into Minecraft chunk shaders for Sable SubLevel structures.
 *
 * Features:
 *   - Fast, single-tap pixelated directional shadow mapping snapped to block-texel resolution
 *   - Stylized stepped lighting with crisp terminators
 *   - Atmospheric Fresnel rim lighting on silhouette edges with a signature gap/offset on shadow sides
 *   - Specular highlights and deep space ambient tone
 */
public class SunDirectionalShadingPreProcessor implements ShaderPreProcessor {

    public static final String SUN_DIRECTION_UNIFORM = "SunDirection";
    public static final String SUN_ENABLED_UNIFORM   = "SunEnabled";
    public static final String SUN_INTENSITY_UNIFORM = "SunIntensity";

    private static final String PBR_LIGHTING_GLSL =
        "float _subLevelLit = SunEnabled;" +
        "if (_subLevelLit > 0.5) {" +
        "  float skyExposure = clamp(float(UV2.y) / 240.0, 0.0, 1.0);" +
        "  vec4 torchSample = minecraft_sample_lightmap(Sampler2, ivec2(UV2.x, 0));" +
        "  vec3 viewNorm = normalize(NormalMat * Normal);" +
        "  vec3 sunDir = normalize(SunDirection);" +
        "  vec3 viewSun = normalize(mat3(ModelViewMat) * sunDir);" +
        "  float NdotL = dot(viewNorm, viewSun);" +
        "  vec4 viewPos = ModelViewMat * vec4(pos, 1.0);" +
        "  vec4 lightSpace = LightSpaceMat * viewPos;" +
        "  vec3 sc = lightSpace.xyz / lightSpace.w;" +
        "  float shadow = 1.0;" +
        "  if (sc.x >= 0.001 && sc.x <= 0.999 && sc.y >= 0.001 && sc.y <= 0.999 && sc.z >= 0.001 && sc.z <= 0.999) {" +
        "    const float SHADOW_GRID = 1536.0;" +
        "    vec2 snappedSc = (floor(sc.xy * SHADOW_GRID) + 0.5) / SHADOW_GRID;" +
        "    float bias = max(0.0040 * (1.0 - max(NdotL, 0.0)), 0.0015);" +
        "    float d = texture(SunShadowSampler, snappedSc).r;" +
        "    shadow = (sc.z - bias > d) ? 0.0 : 1.0;" +
        "  }" +
        "  float isLitFace = step(0.02, NdotL);" +
        "  float sunDiffuse = isLitFace * shadow * max(SunIntensity, 0.01);" +
        "  vec3 SUN_COLOR = vec3(1.36, 1.32, 1.25);" +
        "  vec3 SPACE_AMBIENT = vec3(0.012, 0.014, 0.025);" +
        "  vec3 directSun = SUN_COLOR * sunDiffuse * 1.35;" +
        "  vec3 halfVec = normalize(viewSun + vec3(0.0, 0.0, 1.0));" +
        "  float spec = (NdotL > 0.05 && shadow > 0.5) ? step(0.92, dot(viewNorm, halfVec)) * 0.35 : 0.0;" +
        "  float rawFresnel = clamp(1.0 - abs(viewNorm.z), 0.0, 1.0);" +
        "  float fresnelRim = 0.0;" +
        "  if (shadow < 0.5 || NdotL < 0.05) {" +
        "    if (rawFresnel > 0.82) {" +
        "      fresnelRim = 1.0;" +
        "    } else if (rawFresnel > 0.58 && rawFresnel < 0.70) {" +
        "      fresnelRim = 0.45;" +
        "    }" +
        "  } else {" +
        "    if (rawFresnel > 0.75) {" +
        "      fresnelRim = 0.65;" +
        "    }" +
        "  }" +
        "  vec3 RIM_COLOR = vec3(1.15, 0.95, 0.65);" +
        "  vec3 rimLight = RIM_COLOR * fresnelRim * max(SunIntensity, 0.1);" +
        "  vec3 outerLight = SPACE_AMBIENT + directSun + (SUN_COLOR * spec) + rimLight;" +
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

        // Inject stylized lighting code at the end of main()
        var body = tree.mainFunction().orElseThrow().getBody();
        for (var node : GlslParser.parseExpressionList(PBR_LIGHTING_GLSL)) {
            body.add(node);
        }
    }
}