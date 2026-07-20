package net.atmos.seasonal;

/**
 * Deterministic Seed Hash Utility — Appendix X Revision 2.7 §3.
 *
 * Sole owner of world-seed-derived phase offsets for the Seasonal Profile
 * Model's two axes. Uses the MurmurHash3 x64 finalizer (fmix64) — the
 * exact "standardized, cross-platform deterministic hash function"
 * Rev 2.7 §3 names — rather than a substitute algorithm (a prior revision
 * incorrectly used a SplitMix64 finalizer; corrected per review). Pure
 * integer arithmetic only — no {@code Random}, no
 * {@code ThreadLocalRandom}, no {@code SecureRandom}, no platform-
 * dependent behaviour.
 *
 * Self-contained to this package. Deliberately distinct from the
 * unrelated package-private {@code net.atmos.render.SeedHash}, which
 * serves procedural rendering variation — an entirely different concern
 * with its own algorithm; cross-package reuse between simulation and
 * rendering utilities is avoided throughout this codebase.
 */
public final class SeasonalSeedHash {

    private SeasonalSeedHash() {}

    private static final long THERMAL_SALT  = 0x9E3779B97F4A7C15L;
    private static final long MOISTURE_SALT = 0xC2B2AE3D27D4EB4FL;

    public static SeasonalPhaseOffsets derive(long worldSeed) {
        float thermalOffset  = toAngle(fmix64(worldSeed ^ THERMAL_SALT));
        float moistureOffset = toAngle(fmix64(worldSeed ^ MOISTURE_SALT));
        return new SeasonalPhaseOffsets(thermalOffset, moistureOffset);
    }

    /**
     * MurmurHash3 x64 finalizer (fmix64) — the standardized deterministic
     * mixing function named by Appendix X Revision 2.7 §3.
     */
    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xFF51AFD7ED558CCDL;
        k ^= k >>> 33;
        k *= 0xC4CEB9FE1A85EC53L;
        k ^= k >>> 33;
        return k;
    }

    private static float toAngle(long mixed) {
        float unit = ((mixed >>> 40) & 0xFFFFFFL) / (float) (1 << 24);
        return unit * SFSConstants.TWO_PI;
    }
}