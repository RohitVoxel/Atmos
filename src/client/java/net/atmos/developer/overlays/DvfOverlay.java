package net.atmos.developer.overlays;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumSet;

public interface DvfOverlay {

    /** The capabilities supported by this overlay. */
    EnumSet<OverlayCapability> capabilities();

    default int renderHud(GuiGraphics graphics, int startY) {
        return startY;
    }

    default void renderWorld(PoseStack poseStack, WorldRenderContext context) {
    }
}