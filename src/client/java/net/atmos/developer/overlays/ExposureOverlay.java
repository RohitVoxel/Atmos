package net.atmos.developer.overlays;

import net.atmos.developer.DvfDataAccess;
import net.atmos.exposure.ExposureStateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumSet;

public final class ExposureOverlay implements DvfOverlay {

    private static final StringBuilder BUILDER = new StringBuilder(64);

    @Override
    public EnumSet<OverlayCapability> capabilities() {
        return EnumSet.of(OverlayCapability.HUD);
    }

    @Override
    public int renderHud(GuiGraphics graphics, int startY) {
        ExposureStateSnapshot snapshot = DvfDataAccess.getExposureSnapshot();
        if (snapshot == null) {
            graphics.drawString(Minecraft.getInstance().font, "§cUnavailable: No Exposure Snapshot Published", 10, startY, 0xFFFFFF, true);
            return startY + 10;
        }

        BUILDER.setLength(0);
        BUILDER.append("Exposure Scale: ").append(Math.round(snapshot.exposureScale() * 1000f) / 1000f);
        graphics.drawString(Minecraft.getInstance().font, BUILDER.toString(), 10, startY, 0xFFFFFF, true);
        startY += 10;

        graphics.drawString(Minecraft.getInstance().font, "Snapshot Version: " + snapshot.version(), 10, startY, 0xFFFFFF, true);
        return startY + 10;
    }
}