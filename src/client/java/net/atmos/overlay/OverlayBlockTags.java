package net.atmos.overlay;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Extension points for material classification that vanilla has no tag
 * for. Future mods (or Atmos itself) add block registrations here via a
 * data pack, never via code changes.
 */
public final class OverlayBlockTags {

    private OverlayBlockTags() {}

    public static final TagKey<Block> METAL =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("atmos", "metal"));

    public static final TagKey<Block> MAN_MADE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("atmos", "man_made"));
}