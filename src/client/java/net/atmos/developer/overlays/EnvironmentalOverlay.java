package net.atmos.developer.overlays;

import net.atmos.developer.DvfDataAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumSet;

public final class EnvironmentalOverlay implements DvfOverlay {

    private static final StringBuilder BUILDER = new StringBuilder(64);

    @Override
    public EnumSet<OverlayCapability> capabilities() {
        return EnumSet.of(OverlayCapability.HUD);
    }

    @Override
    public int renderHud(GuiGraphics graphics, int startY) {
        startY = drawLine(graphics, startY, "Humidity Mass: ", DvfDataAccess.getEnvHumidityMass());
        startY = drawLine(graphics, startY, "Thermal Energy: ", DvfDataAccess.getEnvThermalEnergy());
        startY = drawLine(graphics, startY, "Storm Energy: ", DvfDataAccess.getEnvStormEnergy());
        startY = drawLine(graphics, startY, "Night Depth: ", DvfDataAccess.getEnvNightDepth());
        return startY;
    }

    private int drawLine(GuiGraphics graphics, int y, String prefix, float value) {
        BUILDER.setLength(0);
        BUILDER.append(prefix).append(Math.round(value * 1000f) / 1000f);
        graphics.drawString(Minecraft.getInstance().font, BUILDER.toString(), 10, y, 0xFFFFFF, true);
        return y + 10;
    }
}