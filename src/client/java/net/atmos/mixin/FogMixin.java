package net.atmos.mixin;

import net.atmos.core.AtmosClient;
import net.atmos.atmosphere.fog.FogManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.systems.RenderSystem;

@Mixin(FogRenderer.class)
abstract class FogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void atmos$setupFog(
            Camera camera, FogMode fogMode, float renderDistance,
            boolean isFoggy, float partialTick, CallbackInfo ci
    ) {
        if (fogMode != FogMode.FOG_TERRAIN) return;
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        if (RenderSystem.getShaderFogEnd() < 8.0f) return;

        FogManager fog = AtmosClient.getFogManager();
        fog.update(camera, level);

        float start = Math.max(2.0f, fog.getFogStart());
        float end   = Math.max(start + 1.0f, fog.getFogEnd());

        RenderSystem.setShaderFogStart(start);
        RenderSystem.setShaderFogEnd(end);
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void atmos$setupColor(
            Camera camera, float partialTick, ClientLevel level,
            int renderDistance, float darkenWorldAmount, CallbackInfo ci
    ) {
        if (level == null) return;

        final float ATMOS_COLOR_BLEND = 0.16f;

        FogManager fog = AtmosClient.getFogManager();

        float[] vanillaFog = RenderSystem.getShaderFogColor();
        float vanillaR = vanillaFog[0];
        float vanillaG = vanillaFog[1];
        float vanillaB = vanillaFog[2];

        RenderSystem.setShaderFogColor(
                lerp(vanillaR, fog.getFogRed(),   ATMOS_COLOR_BLEND),
                lerp(vanillaG, fog.getFogGreen(), ATMOS_COLOR_BLEND),
                lerp(vanillaB, fog.getFogBlue(),  ATMOS_COLOR_BLEND),
                1.0f
        );
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

