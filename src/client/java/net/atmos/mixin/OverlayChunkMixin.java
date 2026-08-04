package net.atmos.mixin;

import net.atmos.core.AtmosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class OverlayChunkMixin {

    @Shadow public abstract Level getLevel();

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void atmos$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
                                       CallbackInfoReturnable<BlockState> cir) {
        if (getLevel() != Minecraft.getInstance().level) return;
        AtmosClient.getOverlayChunkSurfaceCache().markDirty(pos);
    }
}