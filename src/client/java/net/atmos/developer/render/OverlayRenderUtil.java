package net.atmos.developer.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class OverlayRenderUtil {

    private OverlayRenderUtil() {}

    public static void drawAABB(VertexConsumer buffer, PoseStack.Pose pose, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
        // Bottom Face
        drawLine(buffer, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(buffer, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(buffer, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(buffer, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top Face
        drawLine(buffer, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Pillars
        drawLine(buffer, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(buffer, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    public static void drawLine(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        buffer.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }
}