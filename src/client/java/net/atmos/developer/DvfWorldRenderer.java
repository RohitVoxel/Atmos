package net.atmos.developer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DvfWorldRenderer {

    private DvfWorldRenderer() {}

    public static void init() {
        WorldRenderEvents.LAST.register(context -> {
            DvfMode mode = DvfManager.getMode();
            if (mode == DvfMode.OFF) return;

            PoseStack matrixStack = context.matrixStack();
            Vec3 cameraPos = context.camera().getPosition();

            matrixStack.pushPose();
            matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            if (mode == DvfMode.CELL_GRID) {
                renderCellGrid(matrixStack, context);
            } else if (mode == DvfMode.ATMOSPHERIC_MEMORY) {
                renderAtmosphericMemoryHeatmap(matrixStack, context);
            }

            matrixStack.popPose();
        });
    }

    private static void renderCellGrid(PoseStack poseStack, WorldRenderContext context) {
        VertexConsumer buffer = context.consumers().getBuffer(RenderType.lines());
        Matrix4f pose = poseStack.last().pose();
        int halfSize = CellGrid.CELL_SIZE / 2;

        for (AtmosCell cell : DvfDataAccess.getActiveCells()) {
            float cx = cell.coord().centerWorldX(CellGrid.CELL_SIZE);
            float cy = cell.coord().centerWorldY(CellGrid.CELL_SIZE);
            float cz = cell.coord().centerWorldZ(CellGrid.CELL_SIZE);

            drawAABB(pose, buffer, cx - halfSize, cy - halfSize, cz - halfSize,
                    cx + halfSize, cy + halfSize, cz + halfSize,
                    0.0f, 1.0f, 1.0f, 0.4f); // Cyan wireframe
        }
    }

    private static void renderAtmosphericMemoryHeatmap(PoseStack poseStack, WorldRenderContext context) {
        VertexConsumer buffer = context.consumers().getBuffer(RenderType.lines());
        Matrix4f pose = poseStack.last().pose();
        int halfSize = CellGrid.CELL_SIZE / 2;

        for (AtmosCell cell : DvfDataAccess.getActiveCells()) {
            float cx = cell.coord().centerWorldX(CellGrid.CELL_SIZE);
            float cy = cell.coord().centerWorldY(CellGrid.CELL_SIZE);
            float cz = cell.coord().centerWorldZ(CellGrid.CELL_SIZE);

            // Heatmap: Blue (0.0 humidity) to Red (1.0 humidity)
            float humidity = cell.humidityMemory();
            float r = humidity;
            float g = 0.1f;
            float b = 1.0f - humidity;

            drawAABB(pose, buffer, cx - halfSize, cy - halfSize, cz - halfSize,
                    cx + halfSize, cy + halfSize, cz + halfSize,
                    r, g, b, 0.8f);
        }
    }

    private static void drawAABB(Matrix4f pose, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
        // Bottom Face
        drawLine(pose, buffer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(pose, buffer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(pose, buffer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(pose, buffer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top Face
        drawLine(pose, buffer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(pose, buffer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(pose, buffer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(pose, buffer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical Pillars
        drawLine(pose, buffer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(pose, buffer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(pose, buffer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(pose, buffer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void drawLine(Matrix4f pose, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
    }
}