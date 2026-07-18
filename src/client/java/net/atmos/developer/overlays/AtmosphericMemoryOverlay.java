package net.atmos.developer.overlays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.atmos.developer.CellDebugView;
import net.atmos.developer.DvfDataAccess;
import net.atmos.developer.render.OverlayRenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

import java.util.EnumSet;

public final class AtmosphericMemoryOverlay implements DvfOverlay {

    @Override
    public EnumSet<OverlayCapability> capabilities() {
        return EnumSet.of(OverlayCapability.HUD, OverlayCapability.WORLD, OverlayCapability.HEATMAP);
    }

    @Override
    public int renderHud(GuiGraphics graphics, int startY) {
        graphics.drawString(Minecraft.getInstance().font, "Active Memory Cells: " + DvfDataAccess.getActiveCellViews().size(), 10, startY, 0xFFFFFF, true);
        startY += 10;
        graphics.drawString(Minecraft.getInstance().font, "Pending Disk Writes: " + DvfDataAccess.getPendingMemoryWrites(), 10, startY, 0xFFFFFF, true);
        return startY + 10;
    }

    @Override
    public void renderWorld(PoseStack poseStack, WorldRenderContext context) {
        if (context.consumers() == null) return;

        VertexConsumer buffer = context.consumers().getBuffer(RenderType.lines());
        int halfSize = 8;

        for (CellDebugView cell : DvfDataAccess.getActiveCellViews()) {
            float humidity = cell.humidity();
            float b = 1.0f - humidity;

            OverlayRenderUtil.drawAABB(buffer, poseStack.last(),
                    cell.centerX() - halfSize, cell.centerY() - halfSize, cell.centerZ() - halfSize,
                    cell.centerX() + halfSize, cell.centerY() + halfSize, cell.centerZ() + halfSize,
                    humidity, 0.1f, b, 0.8f);
        }
    }
}