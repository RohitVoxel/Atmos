package net.atmos.overlay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable cached environmental snapshot for one exposed block face.
 *
 * packedLight/lightmap are deliberately absent — always sampled live at
 * render time (Atmos Overlay Foundation V2.1 §7).
 *
 * rainfall is a best-effort snapshot captured when this surface was last
 * (re)built — chunk load or a block change touching this exact position.
 * It is NOT frame-accurate: a rain event starting or stopping does not
 * retroactively refresh already-cached surfaces. Consumers needing live
 * rain state must read OverlayManager/EnvironmentalState directly.
 */
public record OverlaySurface(
        BlockPos pos,
        Direction face,
        BlockState blockState,
        boolean skyVisible,
        float exposure,
        float temperature,
        float humidity,
        float rainfall
) {
    public OverlaySurface {
        if (pos == null) throw new IllegalArgumentException("pos must not be null");
        if (face == null) throw new IllegalArgumentException("face must not be null");
        if (blockState == null) throw new IllegalArgumentException("blockState must not be null");
        if (!Float.isFinite(exposure) || exposure < 0f || exposure > 1f) {
            throw new IllegalArgumentException("exposure must be within [0,1], got " + exposure);
        }
    }
}