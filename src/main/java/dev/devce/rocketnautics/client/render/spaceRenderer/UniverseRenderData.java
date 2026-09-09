package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Mth;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

public class UniverseRenderData {
    public static class FaceDefinition {
        final Vector3D center;
        final Vector3D U;
        final Vector3D V;

        FaceDefinition(Vector3D center, Vector3D U, Vector3D V) {
            this.center = center;
            this.U = U;
            this.V = V;
        }
    }

    private static final Vector3d[][] FACE_CORNERS = {
            /*TOP*/ { new Vector3d(-1, 1, -1), new Vector3d(-1, 1, 1), new Vector3d(1, 1, 1), new Vector3d(1, 1, -1) },
            /*BOTTOM*/ { new Vector3d(-1, -1, -1), new Vector3d(1, -1, -1), new Vector3d(1, -1, 1), new Vector3d(-1, -1, 1) },
            /*NORTH*/ { new Vector3d(1, 1, -1), new Vector3d(1, -1, -1), new Vector3d(-1, -1, -1), new Vector3d(-1, 1, -1) },
            /*SOUTH*/ { new Vector3d(-1, 1, 1), new Vector3d(-1, -1, 1), new Vector3d(1, -1, 1), new Vector3d(1, 1, 1) },
            /*WEST*/ { new Vector3d(-1, 1, -1), new Vector3d(-1, -1, -1), new Vector3d(-1, -1, 1), new Vector3d(-1, 1, 1) },
            /*EAST*/ { new Vector3d(1, 1, 1), new Vector3d(1, -1, 1), new Vector3d(1, -1, -1), new Vector3d(1, 1, -1) }
    };

    public static class CachedVertex {
        final float x, y, z;
        final float u, v;
        CachedVertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    private static class CachedShadowVertex {
        final float ux, uy, uz;
        final float nx, ny, nz;
        final int gx, gy;
        CachedShadowVertex(Vector3D p, int gx, int gy, boolean isSphere) {
            this.ux = (float) p.getX();
            this.uy = (float) p.getY();
            this.uz = (float) p.getZ();
            Vector3D norm = p.normalize();
            this.nx = (float) norm.getX();
            this.ny = (float) norm.getY();
            this.nz = (float) norm.getZ();
            this.gx = gx;
            this.gy = gy;
        }
    }

    public static final CachedVertex[] SPHERE_VERTICES;
    public static final CachedVertex[] CUBE_VERTICES;
    public static final CachedShadowVertex[] SPHERE_SHADOW_VERTICES;
    public static final CachedShadowVertex[] CUBE_SHADOW_VERTICES;
    
    static {
        SPHERE_VERTICES = precomputeVertices(true);
        CUBE_VERTICES = precomputeVertices(false);
        SPHERE_SHADOW_VERTICES = precomputeShadowVertices(true);
        CUBE_SHADOW_VERTICES = precomputeShadowVertices(false);
    }

    private static CachedVertex[] precomputeVertices(boolean isSphere) {
        int G = isSphere ? 16 : 1;
        List<CachedVertex> list = new ArrayList<>();
        for (int face = 0; face < 6; face++) {
            float u0 = 0.0f, u1 = 1.0f, v0 = 0.0f, v1 = 1.0f;
            Vector3d c00 = FACE_CORNERS[face][0];
            Vector3d c01 = FACE_CORNERS[face][1];
            Vector3d c11 = FACE_CORNERS[face][2];
            Vector3d c10 = FACE_CORNERS[face][3];
            for (int gv = 0; gv < G; gv++) {
                for (int gu = 0; gu < G; gu++) {
                    double uA = (double) gu / G;
                    double uB = (double) (gu + 1) / G;
                    double vA = (double) gv / G;
                    double vB = (double) (gv + 1) / G;

                    Vector3d p0 = getBilinearPoint(c00, c01, c11, c10, uA, vA);
                    Vector3d p1 = getBilinearPoint(c00, c01, c11, c10, uA, vB);
                    Vector3d p2 = getBilinearPoint(c00, c01, c11, c10, uB, vB);
                    Vector3d p3 = getBilinearPoint(c00, c01, c11, c10, uB, vA);

                    if (isSphere) {
                        p0.normalize();
                        p1.normalize();
                        p2.normalize();
                        p3.normalize();
                    }

                    float texUA = Mth.lerp((float) uA, u0, u1) / 6.0f + (face / 6.0f);
                    float texUB = Mth.lerp((float) uB, u0, u1) / 6.0f + (face / 6.0f);
                    float texVA = Mth.lerp((float) vA, v0, v1);
                    float texVB = Mth.lerp((float) vB, v0, v1);

                    list.add(new CachedVertex((float)p0.x, (float)p0.y, (float)p0.z, texUA, texVA));
                    list.add(new CachedVertex((float)p1.x, (float)p1.y, (float)p1.z, texUA, texVB));
                    list.add(new CachedVertex((float)p2.x, (float)p2.y, (float)p2.z, texUB, texVB));
                    list.add(new CachedVertex((float)p3.x, (float)p3.y, (float)p3.z, texUB, texVA));
                }
            }
        }
        return list.toArray(new CachedVertex[0]);
    }
    private static CachedShadowVertex[] precomputeShadowVertices(boolean isSphere) {
        int G = 16;
        Vector3D[] unitFacesCenter = {
                new Vector3D(0, 1, 0),
                new Vector3D(0, -1, 0),
                new Vector3D(0, 0, -1),
                new Vector3D(0, 0, 1),
                new Vector3D(-1, 0, 0),
                new Vector3D(1, 0, 0)
        };
        Vector3D[] unitFacesU = {
                new Vector3D(1, 0, 0),
                new Vector3D(1, 0, 0),
                new Vector3D(-1, 0, 0),
                new Vector3D(1, 0, 0),
                new Vector3D(0, 0, 1),
                new Vector3D(0, 0, -1)
        };
        Vector3D[] unitFacesV = {
                new Vector3D(0, 0, 1),
                new Vector3D(0, 0, 1),
                new Vector3D(0, -1, 0),
                new Vector3D(0, -1, 0),
                new Vector3D(0, -1, 0),
                new Vector3D(0, -1, 0)
        };

        List<CachedShadowVertex> list = new ArrayList<>();
        for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
            FaceDefinition face = new FaceDefinition(unitFacesCenter[faceIndex], unitFacesU[faceIndex], unitFacesV[faceIndex]);
            for (int gv = 0; gv < G; gv++) {
                for (int gu = 0; gu < G; gu++) {
                    Vector3D p1 = getPointPrecompute(face, gu, gv, G, 1.0, isSphere);
                    Vector3D p2 = getPointPrecompute(face, gu, gv + 1, G, 1.0, isSphere);
                    Vector3D p3 = getPointPrecompute(face, gu + 1, gv + 1, G, 1.0, isSphere);
                    Vector3D p4 = getPointPrecompute(face, gu + 1, gv, G, 1.0, isSphere);

                    int g1x = gu, g1y = gv;
                    int g2x = gu, g2y = gv + 1;
                    int g3x = gu + 1, g3y = gv + 1;
                    int g4x = gu + 1, g4y = gv;

                    // Bottom face normal swap:
                    if (faceIndex == 1) {
                        Vector3D temp = p2;
                        p2 = p4;
                        p4 = temp;

                        g2x = gu + 1; g2y = gv;
                        g4x = gu;     g4y = gv + 1;
                    }

                    list.add(new CachedShadowVertex(p1, g1x, g1y, isSphere));
                    list.add(new CachedShadowVertex(p2, g2x, g2y, isSphere));
                    list.add(new CachedShadowVertex(p3, g3x, g3y, isSphere));
                    list.add(new CachedShadowVertex(p4, g4x, g4y, isSphere));
                }
            }
        }
        return list.toArray(new CachedShadowVertex[0]);
    }

    private static final double CUBE_SHADOW_INSET = 0.12;

    private static Vector3D getPointPrecompute(FaceDefinition face, int gu, int gv, int G, double shadowSize, boolean isSphere) {
        double u = -1.0 + 2.0 * gu / G;
        double v = -1.0 + 2.0 * gv / G;
        // For cube faces: shrink UV coords inward so shadow doesn't cover the block edge rims
        if (!isSphere) {
            u = u * (1.0 - CUBE_SHADOW_INSET);
            v = v * (1.0 - CUBE_SHADOW_INSET);
        }
        Vector3D p = face.center.add(face.U.scalarMultiply(u)).add(face.V.scalarMultiply(v));
        if (isSphere) {
            return p.normalize().scalarMultiply(shadowSize);
        }
        return p;
    }
    private static Vector3d getBilinearPoint(Vector3d c00, Vector3d c01, Vector3d c11, Vector3d c10, double u, double v) {
        Vector3d p0 = c00.lerp(c10, u, new Vector3d());
        Vector3d p1 = c01.lerp(c11, u, new Vector3d());
        return p0.lerp(p1, v, new Vector3d());
    }
    private static long computeColor(float nx, float ny, float nz, Vector3D L, int gx, int gy) {
        double d = nx * L.getX() + ny * L.getY() + nz * L.getZ();

        // --- Pixelated shadow with Fresnel rim ---
        final double DITHER_HALF = 0.065;
        final double FRESNEL_WIDTH = 0.10;

        int r, g, b, a;

        if (d < -DITHER_HALF) {
            // Full shadow: deep space blue-black
            r = 3; g = 4; b = 16; a = 225;
        } else if (d < DITHER_HALF) {
            // Dither band: checkerboard for sub-pixel terminator sharpness
            boolean ditherOn = ((gx + gy) % 2 == 0);
            if (ditherOn) {
                r = 3; g = 4; b = 16; a = 225;
            } else {
                // Fresnel rim color in transition row
                r = 255; g = 220; b = 140; a = 90;
            }
        } else if (d < DITHER_HALF + FRESNEL_WIDTH) {
            // Fresnel rim glow on the lit side, fades quickly
            double fresnelFactor = 1.0 - (d - DITHER_HALF) / FRESNEL_WIDTH;
            fresnelFactor = fresnelFactor * fresnelFactor;
            r = 255; g = 220; b = 140;
            a = (int) (fresnelFactor * 95);
        } else {
            // Fully lit: transparent
            r = 0; g = 0; b = 0; a = 0;
        }

        return ((long)r << 24) | ((long)g << 16) | ((long)b << 8) | a;
    }

    public static VertexBuffer SPHERE_VBO;
    public static VertexBuffer CUBE_VBO;
    public static VertexBuffer SPHERE_ATM_VBO;
    public static VertexBuffer CUBE_ATM_VBO;

    public static VertexBuffer uploadVBO(UniverseRenderData.CachedVertex[] verts, VertexFormat.Mode mode, VertexFormat format, boolean uv) {
        try {
            var builder = Tesselator.getInstance().begin(mode, format);
            for (UniverseRenderData.CachedVertex v : verts) {
                builder.addVertex(v.x, v.y, v.z).setColor(1f,1f,1f,1f);
                if (uv) builder.setUv(v.u, v.v);
            }
            var buf = builder.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(buf);
            VertexBuffer.unbind();
            return vbo;
        } catch (Exception e) {
            return null;
        }
    }
    public static VertexBuffer uploadShadowVBO(CachedShadowVertex[] verts, float shadowScale, Vector3D L) {
        try {
            var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (CachedShadowVertex sv : verts) {
                long c = computeColor(sv.nx, sv.ny, sv.nz, L, sv.gx, sv.gy);
                builder.addVertex(sv.ux * shadowScale, sv.uy * shadowScale, sv.uz * shadowScale)
                        .setColor((int)((c >> 24) & 255), (int)((c >> 16) & 255), (int)((c >> 8) & 255), (int)(c & 255));
            }
            var buf = builder.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            vbo.bind();
            vbo.upload(buf);
            VertexBuffer.unbind();
            return vbo;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean VBOsBuilt = false;
    public static void buildVBOs() {
        if (VBOsBuilt) return;

        SPHERE_VBO     = uploadVBO(SPHERE_VERTICES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR, true);
        CUBE_VBO       = uploadVBO(CUBE_VERTICES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR, true);
        SPHERE_ATM_VBO = uploadVBO(SPHERE_VERTICES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR, false);
        CUBE_ATM_VBO   = uploadVBO(CUBE_VERTICES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR, false);
        VBOsBuilt = true;
    }

    public static int FULLSCREEN_VAO;

    private static boolean VAOsBuilt = false;
    public static void buildVAOs() {
        if (VAOsBuilt) return;

        FULLSCREEN_VAO = GL30.glGenVertexArrays();
        VAOsBuilt = true;
    }
}
