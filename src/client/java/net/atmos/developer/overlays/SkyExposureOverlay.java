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

public final class SkyExposureOverlay implements DvfOverlay {

    @Override
    public EnumSet<OverlayCapability> capabilities() {
        return EnumSet.of(OverlayCapability.HUD, OverlayCapability.WORLD, OverlayCapability.TRANSLUCENT);
    }

    @Override
    public int renderHud(GuiGraphics graphics, int startY) {
        graphics.drawString(Minecraft.getInstance().font, "Visualizing Sky-Exposed Candidate Cells", 10, startY, 0xFFFFFF, true);
        startY += 10;
        graphics.drawString(Minecraft.getInstance().font, "§8Note: This is NOT the final shaft generation algorithm.", 10, startY, 0xFFFFFF, true);
        return startY + 10;
    }

    @Override
    public void renderWorld(PoseStack poseStack, WorldRenderContext context) {
        if (context.consumers() == null) return;

        VertexConsumer buffer = context.consumers().getBuffer(RenderType.lines());
        float width = 1.5f;

        for (CellDebugView cell : DvfDataAccess.getActiveCellViews()) {
            if (cell.skyExposed()) {
                OverlayRenderUtil.drawAABB(buffer, poseStack.last(),
                        cell.centerX() - width, cell.centerY(), cell.centerZ() - width,
                        cell.centerX() + width, cell.centerY() + 30.0f, cell.centerZ() + width,
                        1.0f, 1.0f, 0.0f, 0.6f);
            }
        }
    }
}