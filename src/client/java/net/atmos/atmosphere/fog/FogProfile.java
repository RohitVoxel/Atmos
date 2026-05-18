package net.atmos.atmosphere.fog;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * Immutable fog parameters for a biome type.
 * openness: 0.0 = enclosed/dense (jungle, swamp), 1.0 = open/airy (desert, ocean).
 * Used by FogManager to scale depth layering per biome character.
 */
public record FogProfile(float start, float end, float red, float green, float blue, float openness) {

    private static final FogProfile DEFAULT  = new FogProfile( 8.0f,  96.0f, 0.72f, 0.78f, 0.84f, 0.5f);
    private static final FogProfile FOREST   = new FogProfile(12.0f,  72.0f, 0.60f, 0.67f, 0.61f, 0.3f);
    private static final FogProfile DESERT   = new FogProfile(24.0f, 140.0f, 0.88f, 0.79f, 0.56f, 0.9f);
    private static final FogProfile SWAMP    = new FogProfile( 6.0f,  52.0f, 0.48f, 0.55f, 0.47f, 0.1f);
    private static final FogProfile SNOWY    = new FogProfile(16.0f, 120.0f, 0.87f, 0.91f, 0.94f, 0.7f);
    private static final FogProfile TAIGA    = new FogProfile(10.0f,  80.0f, 0.60f, 0.67f, 0.71f, 0.4f);
    private static final FogProfile OCEAN    = new FogProfile(20.0f, 160.0f, 0.50f, 0.64f, 0.79f, 1.0f);
    private static final FogProfile JUNGLE   = new FogProfile( 8.0f,  60.0f, 0.40f, 0.57f, 0.43f, 0.1f);
    private static final FogProfile BADLANDS = new FogProfile(18.0f, 120.0f, 0.82f, 0.54f, 0.40f, 0.8f);
    private static final FogProfile MUSHROOM = new FogProfile(10.0f,  72.0f, 0.68f, 0.64f, 0.70f, 0.3f);
    private static final FogProfile NETHER   = new FogProfile( 4.0f,  40.0f, 0.60f, 0.20f, 0.16f, 0.0f);
    private static final FogProfile END      = new FogProfile(24.0f, 180.0f, 0.72f, 0.68f, 0.80f, 0.6f);

    public static FogProfile of(Holder<Biome> holder) {
        if (holder.is(BiomeTags.IS_NETHER))                                      return NETHER;
        if (holder.is(BiomeTags.IS_END))                                         return END;
        if (holder.is(BiomeTags.IS_BADLANDS))                                    return BADLANDS;
        if (holder.is(BiomeTags.IS_JUNGLE))                                      return JUNGLE;
        if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_DEEP_OCEAN)) return OCEAN;
        if (holder.is(BiomeTags.IS_TAIGA))                                       return TAIGA;
        if (holder.is(BiomeTags.IS_FOREST))                                      return FOREST;
        if (holder.is(BiomeTags.HAS_SWAMP_HUT))                                  return SWAMP;

        float temp = holder.value().getBaseTemperature();
        if (temp < 0.15f) return SNOWY;
        if (temp > 1.8f)  return DESERT;

        return DEFAULT;
    }
}