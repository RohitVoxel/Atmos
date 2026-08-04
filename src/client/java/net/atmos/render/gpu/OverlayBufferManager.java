package net.atmos.render.gpu;

import com.mojang.blaze3d.vertex.*;
import net.atmos.atmosphere.fog.FogMath;
import net.atmos.overlay.OverlaySurfaceQuad;
import net.atmos.overlay.OverlayVisualProfile;
import net.minecraft.core.BlockPos;

import java.util.List;

/** Builds and uploads one retained VertexBuffer from a batch of merged overlay quads. Never runs per-frame — only when a mesh is (re)baked. */
public final class OverlayBufferManager {

    private OverlayBufferManager() {}

    public static VertexBuffer upload(List<OverlaySurfaceQuad> quads, OverlayVisualProfile profile, float depthOffset) {
        if (quads.isEmpty()) return null;

        int bytesPerVertex = DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP.getVertexSize();
        BufferBuilder builder = new BufferBuilder(
                new ByteBufferBuilder(quads.size() * 4 * bytesPerVertex),
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);

        float r = FogMath.clamp(profile.colorTint().red()   * profile.brightness(), 0f, 1f);
        float g = FogMath.clamp(profile.colorTint().green() * profile.brightness(), 0f, 1f);
        float b = FogMath.clamp(profile.colorTint().blue()  * profile.brightness(), 0f, 1f);
        float a = profile.opacity();

        for (OverlaySurfaceQuad quad : quads) {
            emitQuad(builder, quad, r, g, b, a, depthOffset);
        }

        MeshData meshData = builder.buildOrThrow();
        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vertexBuffer.bind();
        vertexBuffer.upload(meshData);
        VertexBuffer.unbind();
        return vertexBuffer;
    }

    private static void emitQuad(BufferBuilder buffer, OverlaySurfaceQuad quad,
                                 float r, float g, float b, float a, float depthOffset) {
        BlockPos o = quad.origin();
        int aExt = quad.extentA();
        int bExt = quad.extentB();
        double ox = o.getX(), oy = o.getY(), oz = o.getZ();
        double x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3;

        switch (quad.face()) {
            case UP -> {
                double py = oy + 1.0 + depthOffset;
                x0 = ox; y0 = py; z0 = oz;
                x1 = ox; y1 = py; z1 = oz + bExt;
                x2 = ox + aExt; y2 = py; z2 = oz + bExt;
                x3 = ox + aExt; y3 = py; z3 = oz;
            }
            case DOWN -> {
                double py = oy - depthOffset;
                x0 = ox; y0 = py; z0 = oz;
                x1 = ox + aExt; y1 = py; z1 = oz;
                x2 = ox + aExt; y2 = py; z2 = oz + bExt;
                x3 = ox; y3 = py; z3 = oz + bExt;
            }
            case NORTH -> {
                double pz = oz - depthOffset;
                x0 = ox; y0 = oy; z0 = pz;
                x1 = ox + aExt; y1 = oy; z1 = pz;
                x2 = ox + aExt; y2 = oy + bExt; z2 = pz;
                x3 = ox; y3 = oy + bExt; z3 = pz;
            }
            case SOUTH -> {
                double pz = oz + 1.0 + depthOffset;
                x0 = ox; y0 = oy; z0 = pz;
                x1 = ox; y1 = oy + bExt; z1 = pz;
                x2 = ox + aExt; y2 = oy + bExt; z2 = pz;
                x3 = ox + aExt; y3 = oy; z3 = pz;
            }
            case WEST -> {
                double px = ox - depthOffset;
                x0 = px; y0 = oy; z0 = oz;
                x1 = px; y1 = oy + bExt; z1 = oz;
                x2 = px; y2 = oy + bExt; z2 = oz + aExt;
                x3 = px; y3 = oy; z3 = oz + aExt;
            }
            case EAST -> {
                double px = ox + 1.0 + depthOffset;
                x0 = px; y0 = oy; z0 = oz;
                x1 = px; y1 = oy; z1 = oz + aExt;
                x2 = px; y2 = oy + bExt; z2 = oz + aExt;
                x3 = px; y3 = oy + bExt; z3 = oz;
            }
            default -> { return; }
        }

        int light = quad.cachedLight();
        buffer.addVertex((float) x0, (float) y0, (float) z0).setUv(0f, 0f).setColor(r, g, b, a).setLight(light);
        buffer.addVertex((float) x1, (float) y1, (float) z1).setUv(aExt, 0f).setColor(r, g, b, a).setLight(light);
        buffer.addVertex((float) x2, (float) y2, (float) z2).setUv(aExt, bExt).setColor(r, g, b, a).setLight(light);
        buffer.addVertex((float) x3, (float) y3, (float) z3).setUv(0f, bExt).setColor(r, g, b, a).setLight(light);
    }
}