package net.atmos.render;

/**
 * Minimal deterministic integer hashing used by Renderer Expansion
 * (Appendix G) and the Density Probability Map (Appendix H) to derive
 * reproducible per-shaft variation from a seed.
 *
 * Not a general-purpose utility — deliberately scoped to net.atmos.render,
 * where deterministic procedural variation is required and no existing
 * project utility (FogMath, ConfidenceMath) provides numeric hashing.
 *
 * Uses a SplitMix64-style finalizer: a well-known integer mixing function
 * chosen for guaranteed determinism and good bit dispersion without
 * introducing a PRNG dependency or any mutable state.
 */
final class SeedHash {

    private SeedHash() {}

    /** Deterministically mixes a seed into a well-dispersed 64-bit value. */
    static long mix(long seed) {
        long z = seed;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Deterministically derives a per-index seed from a base cluster seed. */
    static long deriveSeed(long baseSeed, int index) {
        return mix(baseSeed + index * 0x9E3779B97F4A7C15L);
    }

    /** Maps a mixed seed to a deterministic float in [0,1). */
    static float toUnitFloat(long mixedSeed) {
        return ((mixedSeed >>> 40) & 0xFFFFFFL) / (float) (1 << 24);
    }
}