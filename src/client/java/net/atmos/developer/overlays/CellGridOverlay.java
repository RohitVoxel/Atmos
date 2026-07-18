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
import java.util.List;

public final class CellGridOverlay implements DvfOverlay {

    @Override
    public EnumSet<OverlayCapability> capabilities() {
        return EnumSet.of(OverlayCapability.HUD, OverlayCapability.WORLD);
    }

    @Override
    public int renderHud(GuiGraphics graphics, int startY) {
        graphics.drawString(Minecraft.getInstance().font, "Active Cells: " + DvfDataAccess.getActiveCellViews().size(), 10, startY, 0xFFFFFF, true);
        startY += 10;
        graphics.drawString(Minecraft.getInstance().font, "Current Tick: " + DvfDataAccess.getCurrentCellTick(), 10, startY, 0xFFFFFF, true);
        return startY + 10;
    }

    @Override
    public void renderWorld(PoseStack poseStack, WorldRenderContext context) {
        if (context.consumers() == null) return;

        VertexConsumer buffer = context.consumers().getBuffer(RenderType.lines());
        List<CellDebugView> cells = DvfDataAccess.getActiveCellViews();
        int halfSize = 8;

        for (CellDebugView cell : cells) {
            OverlayRenderUtil.drawAABB(buffer, poseStack.last(),
                    cell.centerX() - halfSize, cell.centerY() - halfSize, cell.centerZ() - halfSize,
                    cell.centerX() + halfSize, cell.centerY() + halfSize, cell.centerZ() + halfSize,
                    0.0f, 1.0f, 1.0f, 0.4f);
        }
    }
}