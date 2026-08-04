package net.atmos.overlay;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single point of contact between overlay systems and Minecraft block
 * identity. No other overlay class references a concrete Block class.
 */
public final class OverlayMaterialRegistry {

    private OverlayMaterialRegistry() {}

    public static OverlayMaterial classify(BlockState state) {
        if (state.isAir()) return OverlayMaterial.NONE;
        if (!state.getFluidState().isEmpty()) return OverlayMaterial.NONE;
        if (isNonSolidDecoration(state)) return OverlayMaterial.NONE;

        if (state.is(BlockTags.REPLACEABLE)) return OverlayMaterial.NONE;
        if (state.is(BlockTags.LEAVES)) return OverlayMaterial.NONE;
        if (state.is(BlockTags.CROPS)) return OverlayMaterial.NONE;
        if (state.is(BlockTags.FLOWERS)) return OverlayMaterial.NONE;
        if (state.is(BlockTags.WOOL_CARPETS)) return OverlayMaterial.NONE;

        if (state.is(BlockTags.SNOW)) return OverlayMaterial.SNOW;
        if (state.is(BlockTags.SAND)) return OverlayMaterial.SAND;
        if (state.getBlock() instanceof FarmBlock || state.is(BlockTags.DIRT)) return OverlayMaterial.ORGANIC_SOIL;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS)) return OverlayMaterial.WOOD;
        if (state.is(OverlayBlockTags.METAL)) return OverlayMaterial.METAL;
        if (state.is(OverlayBlockTags.MAN_MADE)) return OverlayMaterial.MAN_MADE;
        if (state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.BASE_STONE_OVERWORLD)) return OverlayMaterial.ROCK;

        return OverlayMaterial.DEFAULT;
    }

    /** Vanilla signs are matched via tags rather than instanceof — avoids the
     *  Hanging Sign class hierarchy entirely and reads cleaner besides. */
    private static boolean isNonSolidDecoration(BlockState state) {
        Block block = state.getBlock();
        return block instanceof BushBlock
                || block instanceof VineBlock
                || block instanceof ButtonBlock
                || block instanceof PressurePlateBlock
                || block instanceof BaseRailBlock
                || block instanceof TorchBlock
                || block instanceof AbstractBannerBlock
                || block instanceof LadderBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.ALL_HANGING_SIGNS);
    }

    public static boolean isEligible(BlockState state) {
        return classify(state) != OverlayMaterial.NONE;
    }
}