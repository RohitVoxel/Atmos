package net.atmos.mixin;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.compat.ShaderDetector;
import net.atmos.config.AtmosConfig;
import net.atmos.core.AtmosClient;
import net.atmos.atmosphere.fog.FogMath;
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
        if (!AtmosConfig.get().fog.fogEnabled) return;
        if (ShaderDetector.hasConflictingRenderer()) return;

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

    /**
     * Blends Atmos fog color with vanilla fog color.
     *
     * Blend weight is dynamic — driven by atmospheric conditions from
     * EnvironmentalState plus a dawn/dusk horizon factor.
     *
     * Dawn/dusk factor:
     * During sunrise and sunset, the Atmos fog color carries the warm tinting
     * computed by DaylightFogModifier. Vanilla fog has no awareness of this
     * tinting. Without an elevated weight during the horizon window, vanilla
     * fog dominates and washes out the Atmos-computed dawn/dusk color at
     * exactly the moment it should be most visible — producing the banding
     * artifact between the warm sky and the neutral horizon fog.
     *
     * The factor uses the same horizonMask formula as DaylightFogModifier and
     * SkyColorController so all three systems peak and fade together:
     *   horizonMask = clamp(1 - |cos(sunAngle)| * 3, 0, 1)
     *   dawnFactor  = max(0,  sin(sunAngle)) * horizonMask
     *   duskFactor  = max(0, -sin(sunAngle)) * horizonMask
     *
     * Weight formula:
     *   base        = 0.25  (neutral conditions)
     *   storm       += stormEnergy  * 0.30
     *   night       += nightDepth   * 0.20
     *   humidity    += humidityMass * 0.10
     *   horizon     += horizonFactor * 0.35  (dawn/dusk: Atmos is the authority)
     *   cap         = 0.82
     *
     * At peak clear dawn/dusk: weight ≈ 0.60 — Atmos drives 60% of fog color.
     * Heavy storm at night: weight ≈ 0.80 (cap) — full atmospheric expression.
     * Neutral clear midday: weight ≈ 0.29 — vanilla dominates correctly.
     *
     * Geometry fade (unchanged):
     * Weight scales to zero when fog end is very short — at 2-block range the
     * biome tint reads as a rendering artifact, not atmosphere.
     */
    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void atmos$setupColor(
            Camera camera, float partialTick, ClientLevel level,
            int renderDistance, float darkenWorldAmount, CallbackInfo ci
    ) {
        if (level == null) return;
        if (!AtmosConfig.get().fog.fogEnabled) return;
        if (ShaderDetector.hasConflictingRenderer()) return;

        FogManager         fog       = AtmosClient.getFogManager();
        EnvironmentalState env       = fog.getEnvState();
        float[]            vanillaFog = RenderSystem.getShaderFogColor();

        float atmosR = fog.getFogRed();
        float atmosG = fog.getFogGreen();
        float atmosB = fog.getFogBlue();

        // --- Dawn/dusk horizon factor ---
        // Peaks at 1.0 when the sun is at the horizon (golden hour window).
        // Uses Math.abs(sunHeight) so it is zero at midnight — the same guard
        // that prevents the night-time flickering artifact in DaylightFogModifier
        // and SkyColorController. All three systems share identical horizon logic.
        float sunAngle    = level.getSunAngle(1.0f);
        float sunHeight   = (float) Math.cos(sunAngle);
        float sinAngle    = (float) Math.sin(sunAngle);
        float horizonMask = FogMath.clamp(1f - Math.abs(sunHeight) * 3f, 0f, 1f);
        float dawnFactor  = Math.max(0f,  sinAngle) * horizonMask;
        float duskFactor  = Math.max(0f, -sinAngle) * horizonMask;
        float horizonFactor = FogMath.smoothstep(Math.max(dawnFactor, duskFactor));

        // --- Dynamic blend weight ---
        final float BASE_BLEND = 0.25f;
        final float MAX_BLEND  = 0.82f;

        float dynamicWeight = BASE_BLEND
                + env.stormEnergy  * 0.30f
                + env.nightDepth   * 0.20f
                + env.humidityMass * 0.10f
                + horizonFactor    * 0.35f;
        dynamicWeight = FogMath.clamp(dynamicWeight, BASE_BLEND, MAX_BLEND);

        // --- Geometry fade ---
        // Scale weight toward zero when fog end is very short.
        // Biome tint at 2–12 block range reads as a rendering artifact.
        final float GEOMETRY_FADE_FULL = 48f;
        final float GEOMETRY_FADE_ZERO = 12f;

        float currentFogEnd = RenderSystem.getShaderFogEnd();
        float geometryFade  = FogMath.clamp(
                (currentFogEnd - GEOMETRY_FADE_ZERO) / (GEOMETRY_FADE_FULL - GEOMETRY_FADE_ZERO),
                0f, 1f
        );
        float blendWeight = dynamicWeight * geometryFade;

        if (blendWeight < 0.001f) return;

        RenderSystem.setShaderFogColor(
                FogMath.lerp(vanillaFog[0], atmosR, blendWeight),
                FogMath.lerp(vanillaFog[1], atmosG, blendWeight),
                FogMath.lerp(vanillaFog[2], atmosB, blendWeight),
                1.0f
        );
    }
}