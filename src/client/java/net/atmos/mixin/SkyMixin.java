package net.atmos.mixin;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.config.AtmosConfig;
import net.atmos.core.AtmosClient;
import net.atmos.compat.ShaderDetector;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
abstract class SkyMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void atmos$modifySkyColor(Vec3 cameraPos, float partialTick,
                                      CallbackInfoReturnable<Vec3> cir) {
        if (!AtmosConfig.get().fog.skyEnabled) return;
        if (ShaderDetector.hasConflictingRenderer()) return;

        FogContext ctx = AtmosClient.getSkyContext();
        if (ctx == null) return;

        EnvironmentalState env = AtmosClient.getFogManager().getEnvState();

        Vec3 vanilla     = cir.getReturnValue();
        int  r           = (int)(vanilla.x * 255f);
        int  g           = (int)(vanilla.y * 255f);
        int  b           = (int)(vanilla.z * 255f);
        int  packedColor = (r << 16) | (g << 8) | b;

        // deltaSec is captured once per frame at WorldRenderEvents.START.
        // SkyColorController uses it to advance its per-channel drifters at
        // the correct rate — same pattern as FogManager's fog drifters.
        float deltaSec = AtmosClient.getSkyDeltaSec();

        packedColor = AtmosClient.getSkyColorController() .apply(packedColor, ctx, env, deltaSec);
        packedColor = AtmosClient.getSunGlareController()  .apply(packedColor, ctx, env, AtmosClient.getFogManager().getFogEnd());
        packedColor = AtmosClient.getMoonlightController() .apply(packedColor, ctx, env);

        cir.setReturnValue(new Vec3(
                ((packedColor >> 16) & 0xFF) / 255f,
                ((packedColor >>  8) & 0xFF) / 255f,
                ( packedColor        & 0xFF) / 255f
        ));
    }
}