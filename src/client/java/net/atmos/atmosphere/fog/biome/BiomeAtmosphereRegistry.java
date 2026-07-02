package net.atmos.atmosphere.fog.biome;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

public final class BiomeAtmosphereRegistry {

    private BiomeAtmosphereRegistry() {}

    //                                                           start    end      R      G      B     open  cRet  wSens  hum
    private static final BiomeAtmosphere DEFAULT     = wrap( 14.0f, 112.0f, 0.72f, 0.78f, 0.84f, 0.60f, 0.50f, 0.60f, 0.35f);
    private static final BiomeAtmosphere FOREST      = wrap( 12.0f,  72.0f, 0.60f, 0.67f, 0.61f, 0.30f, 0.30f, 0.50f, 0.55f);
    private static final BiomeAtmosphere DARK_FOREST = wrap( 10.0f,  80.0f, 0.55f, 0.62f, 0.58f, 0.35f, 0.40f, 0.60f, 0.50f);
    private static final BiomeAtmosphere DESERT      = wrap( 32.0f, 180.0f, 0.88f, 0.79f, 0.56f, 1.00f, 0.80f, 0.20f, 0.05f);
    private static final BiomeAtmosphere SAVANNA     = wrap( 28.0f, 160.0f, 0.82f, 0.76f, 0.52f, 1.00f, 0.70f, 0.50f, 0.20f);
    private static final BiomeAtmosphere SWAMP       = wrap(  6.0f,  52.0f, 0.48f, 0.55f, 0.47f, 0.10f, 0.10f, 0.90f, 0.95f);
    private static final BiomeAtmosphere SNOWY       = wrap( 20.0f, 148.0f, 0.87f, 0.91f, 0.96f, 0.80f, 0.70f, 0.60f, 0.30f);
    private static final BiomeAtmosphere TAIGA       = wrap( 10.0f,  80.0f, 0.60f, 0.67f, 0.71f, 0.40f, 0.40f, 0.60f, 0.50f);
    private static final BiomeAtmosphere OCEAN       = wrap( 28.0f, 200.0f, 0.50f, 0.64f, 0.79f, 1.00f, 0.90f, 0.40f, 0.70f);
    private static final BiomeAtmosphere JUNGLE      = wrap(  8.0f,  60.0f, 0.40f, 0.57f, 0.43f, 0.10f, 0.10f, 1.00f, 1.00f);
    private static final BiomeAtmosphere BADLANDS    = wrap( 24.0f, 160.0f, 0.82f, 0.54f, 0.40f, 0.90f, 0.80f, 0.20f, 0.08f);
    private static final BiomeAtmosphere MUSHROOM    = wrap( 10.0f,  72.0f, 0.68f, 0.64f, 0.70f, 0.30f, 0.30f, 0.40f, 0.60f);
    private static final BiomeAtmosphere DEEP_DARK   = wrap(  4.0f,  32.0f, 0.28f, 0.30f, 0.36f, 0.00f, 0.20f, 0.00f, 0.65f);
    private static final BiomeAtmosphere NETHER      = wrap(  4.0f,  40.0f, 0.60f, 0.20f, 0.16f, 0.00f, 0.00f, 0.00f, 0.00f);
    private static final BiomeAtmosphere END         = wrap( 24.0f, 180.0f, 0.72f, 0.68f, 0.80f, 0.60f, 0.50f, 0.10f, 0.00f);

    // Mountain — thin crisp air, very long visibility, maximum exposure.
    // HeightFogModifier expands distances further at altitude on top of this.
    private static final BiomeAtmosphere MOUNTAIN    = wrap( 36.0f, 220.0f, 0.78f, 0.84f, 0.94f, 0.95f, 0.80f, 0.70f, 0.12f);

    // River — flowing cool air, slight mist, narrow enclosed feel.
    // Moderate humidity from constant water surface. Fog pools along the channel.
    // Long-axis openness is high but lateral enclosure from banks keeps fog close.
    private static final BiomeAtmosphere RIVER       = wrap( 10.0f,  80.0f, 0.58f, 0.68f, 0.76f, 0.50f, 0.40f, 0.60f, 0.65f);

    // Beach — open salt air, bright pale haze, long visibility.
    // High openness: exposed coastal strip. High humidity from sea spray
    // but low fog density — salt air resists condensation.
    private static final BiomeAtmosphere BEACH       = wrap( 24.0f, 180.0f, 0.80f, 0.82f, 0.86f, 0.90f, 0.70f, 0.40f, 0.55f);

    public static BiomeAtmosphere of(Holder<Biome> holder) {
        // --- Tier 1: Dimension overrides ---
        if (holder.is(BiomeTags.IS_NETHER)) return NETHER;
        if (holder.is(BiomeTags.IS_END))    return END;

        // --- Tier 2: Strong structural tags ---
        // Ordered by specificity. More specific discriminators before broader ones.
        if (holder.is(BiomeTags.IS_BADLANDS))                                      return BADLANDS;
        if (holder.is(BiomeTags.IS_JUNGLE))                                        return JUNGLE;
        if (holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_DEEP_OCEAN))   return OCEAN;

        // Mountain: IS_MOUNTAIN (windswept variants) then ConventionalBiomeTags
        // (peaks, slopes, all modded mountains tagged by mod authors).
        if (holder.is(BiomeTags.IS_MOUNTAIN))                                      return MOUNTAIN;
        if (holder.is(ConventionalBiomeTags.IS_MOUNTAIN))                          return MOUNTAIN;

        if (holder.is(BiomeTags.IS_TAIGA))                                         return TAIGA;
        if (holder.is(BiomeTags.HAS_WOODLAND_MANSION))                             return DARK_FOREST;
        if (holder.is(BiomeTags.IS_FOREST))                                        return FOREST;

        // IS_BEACH before IS_RIVER — some coastal biomes may satisfy both.
        if (holder.is(BiomeTags.IS_BEACH))                                         return BEACH;
        if (holder.is(BiomeTags.IS_RIVER))                                         return RIVER;

        if (holder.is(ConventionalBiomeTags.IS_SWAMP))                             return SWAMP;
        if (holder.is(ConventionalBiomeTags.IS_SAVANNA))                           return SAVANNA;
        if (holder.is(BiomeTags.HAS_ANCIENT_CITY))                                 return DEEP_DARK;
        if (holder.is(ConventionalBiomeTags.IS_MUSHROOM))                          return MUSHROOM;

        // --- Tier 3: Climate matrix (temperature × precipitation) ---
        //
        // Without downfall access, temperature is used as a secondary humidity
        // signal within hasPrecipitation=true. This is an approximation: in both
        // vanilla and most modded content, warmer wet biomes tend toward higher
        // humidity. The bands below reflect this correlation:
        //
        //   temp < 0.15               → frozen,  precip irrelevant
        //   temp 0.15–0.50  + wet     → cool coniferous (taiga-like)
        //   temp 0.15–0.50  + dry     → cold open (snowy plains-like)
        //   temp 0.50–0.70  + wet     → temperate forest
        //   temp 0.50–0.70  + dry     → open plains / meadow
        //   temp 0.70–0.90  + wet     → warm humid (vanilla swamp zone —
        //                               untagged modded biomes here are likely
        //                               wetlands, floodplains, fens; FOREST
        //                               chosen over SWAMP to avoid extreme
        //                               enclosure for potentially open biomes)
        //   temp 0.70–0.90  + dry     → open warm plains
        //   temp 0.90–1.40  + wet     → tropical / jungle-dense
        //   temp 0.90–1.40  + dry     → savanna / open hot
        //   temp 1.40–1.80  + wet     → hot tropical
        //   temp 1.40–1.80  + dry     → arid / desert
        //   temp > 1.80               → extreme heat, desert
        //
        // Lush Caves and Dripstone Caves intentionally excluded — their
        // underground atmosphere is handled entirely by CaveFogModifier
        // (Y-level based). A biome profile would stack incorrectly.
        float   temp      = holder.value().getBaseTemperature();
        boolean hasPrecip = holder.value().hasPrecipitation();

        if (temp < 0.15f) return SNOWY;
        if (temp < 0.50f) return hasPrecip ? TAIGA  : SNOWY;
        if (temp < 0.90f) return hasPrecip ? FOREST : DEFAULT;
        if (temp < 1.40f) return hasPrecip ? JUNGLE : SAVANNA;
        if (temp < 1.80f) return hasPrecip ? JUNGLE : DESERT;

        return DESERT;
    }

    private static BiomeAtmosphere wrap(float start, float end,
                                        float r, float g, float b,
                                        float openness,
                                        float contrastRetention,
                                        float weatherSensitivity,
                                        float humidity) {
        return BiomeAtmosphere.of(
                BiomeTraits.of(start, end, r, g, b,
                        openness, contrastRetention, weatherSensitivity, humidity)
        );
    }
}