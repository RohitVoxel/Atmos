package net.atmos.exposure;

/**
 * Raw, unaggregated exposure-relevant readings — Chapter 14 §14.5
 * (Exposure Sources).
 *
 * Chapter 14 names many exposure sources (Sun Height, Cloud Coverage,
 * Weather, Fog Density, Humidity, Biome, Thermal Energy, Sky Brightness,
 * Ambient Light, Ground Reflection, Hero Shafts, Player Position,
 * Dimension, Atmospheric Memory) and states only that exposure "evaluates
 * the complete atmospheric context" — no combination formula is given
 * anywhere. This record therefore holds only values already owned and
 * published by their existing upstream owner, completely unmodified and
 * uncombined.
 *
 * Fields are limited to sources with an existing, unambiguous
 * EnvironmentalState/AtmosphericMemory accessor. Sources named by §14.5
 * with no current single-value producer — Sun Height, Cloud Coverage,
 * Fog Density, Biome, Hero Shafts, Player Position, Dimension — are
 * absent rather than approximated. nightDepth/skyMoisture are exposed
 * under their own EnvironmentalState names rather than relabeled as
 * "Sun Height"/"Sky Brightness" to avoid misattributing them as literal
 * Guide-named quantities.
 */
public record RawExposureFactors(
        float nightDepth,
        float thermalEnergy,
        float humidityMass,
        float skyMoisture,
        float stormEnergy,
        float humidityMemory,
        float stormMemory
) {}