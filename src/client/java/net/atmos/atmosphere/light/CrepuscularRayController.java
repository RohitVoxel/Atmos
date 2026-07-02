package net.atmos.atmosphere.light;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.atmosphere.fog.FogContext;
import net.atmos.atmosphere.fog.FogMath;
import net.minecraft.core.BlockPos;

/**
 * Computes crepuscular ray ("god ray") visibility once per frame.
 *
 * Architecturally separate from FogPipeline by design (Forest Spec Task 1):
 * this class only reads EnvironmentalState/FogContext/the supplied openness
 * value and produces a rendering-trigger signal (intensity + color). It
 * never mutates FogState or EnvironmentalState — CrepuscularRayRenderer is
 * the only consumer.
 *
 * Trigger conditions are multiplicative, matching the spec exactly:
 * any single factor at zero silences rays entirely. No idle/ambient floor.
 *
 *   skyGate        — NEW (review fix). cameraY surface threshold (mirrors
 *                    CaveFogModifier) plus a cached canSeeSky() check
 *                    (mirrors CanopyMoistureModifier's movement-cache
 *                    pattern, but intentionally a separate, private cache —
 *                    sharing CanopyMoistureModifier's cache would mean this
 *                    rendering-trigger class reaching into a FogModifier's
 *                    internals, crossing the "atmospheric simulation ≠
 *                    rendering" boundary the project instructions call out
 *                    explicitly). Without this, rays could trigger indoors
 *                    or underground purely from biome/weather state with no
 *                    awareness of the player's actual local sky exposure.
 *   horizonFactor  — now FogMath.horizonFactor(sunHeight, sinAngle), the
 *                    same dawn/dusk/horizon-mask formula DaylightFogModifier
 *                    and SkyColorController use, extracted as an additive
 *                    FogMath helper (review Fix 3) rather than duplicated
 *                    inline a fourth time.
 *   hazeFactor     — rays need something to scatter against. Rises with
 *                    humidityMass/stormEnergy from a clear-air floor, then
 *                    collapses again at very high stormEnergy — a full
 *                    downpour has no shafts, just grey compression.
 *   enclosureFactor — (1 - openness), where openness is now supplied by the
 *                    caller (FogManager.getFogOpenness()) rather than looked
 *                    up directly from BiomeAtmosphereRegistry (review Fix 2).
 *                    That getter already returns FogInterpolator's
 *                    hold-time + hysteresis-blended value — the same one
 *                    fog start/end/color already use at biome borders — so
 *                    ray intensity no longer snaps on the raw candidate
 *                    biome the way a direct registry lookup would.
 *
 * Color (visual improvement pass):
 * Previously a hardcoded warm-gold range that did not respond to
 * atmospheric state. Now derives from the current fog color passed by
 * the caller (FogManager.getFogRed/Green/Blue — the same pipeline-smoothed
 * values the fog system renders). Fog color already encodes the atmospheric
 * moment: dawn warmth, storm grey, rain desaturation, midday neutral-white.
 * Rays inherit that state and push it toward the characteristic warm-bright
 * quality of sunlight shafts.
 *
 * Implementation: boost fog luminance toward a target brightness level
 * (rays are brighter than ambient atmosphere), then blend 60% fixed
 * warm-sunlight base with 40% boosted fog color. The fixed base prevents
 * strongly biome-tinted fog (e.g. forest green, badlands amber) from
 * greenifying or over-reddening sun shafts, which are lit by the sun
 * rather than the biome's ground-level air.
 *
 * No internal drifter/smoothing: intensity is fully recomputed from
 * EnvironmentalState (and the now-already-blended openness input) every
 * frame. Adding a second smoothing layer here would just add lag on top of
 * blending that's already happening upstream.
 *
 * Update cadence: called once per frame from AtmosClient's
 * WorldRenderEvents.START handler, in the same place skyContext and
 * FOG_MANAGER are advanced — no separate frame-update guard needed since
 * there is exactly one call site per frame.
 */
public final class CrepuscularRayController {

    // Mirrors CaveFogModifier.CAVE_SURFACE_THRESHOLD. Deliberately a
    // separate constant (not a shared reference) — same value, different
    // system, kept decoupled per the simulation/rendering separation rule.
    // Below this Y, skip even the canSeeSky() lookup: cheap early-out for
    // the common "definitely underground" case before paying for a world
    // query.
    private static final float RAY_SURFACE_THRESHOLD = 50f;

    // Mirrors CanopyMoistureModifier's CACHE_MOVE_THRESHOLD exactly, but as
    // this class's own private cache state — see class doc.
    private static final float CACHE_MOVE_THRESHOLD = 3.0f;

    // Haze rises to full strength by this combined humidity/storm value —
    // well below saturation, so dawn mist (humidity ~0.5-0.7) already
    // qualifies without needing active rain.
    private static final float HAZE_RISE_PEAK = 0.35f;

    // Above this stormEnergy, haze stops helping and starts hurting —
    // heavy rain/thunder has no visible shafts, just grey compression.
    private static final float HAZE_COLLAPSE_AT = 0.85f;

    // Hard cap so this never reads as a constant overlay, even at the
    // theoretical maximum of all factors at once.
    private static final float MAX_INTENSITY = 0.55f;

    // Fog luminance target for ray brightness boost.
    // Rays should be significantly brighter than the ambient fog they scatter
    // through — 0.88 is roughly "sunlit" while average clear fog sits ~0.65.
    private static final float RAY_TARGET_LUMINANCE = 0.88f;

    // Maximum boost applied to fog color when deriving ray color.
    // Caps at 2.2× to prevent extremely dark storm fog from being
    // aggressively over-brightened into unrealistic white.
    private static final float RAY_BOOST_MAX = 2.2f;

    // Blend fraction: how much of the boosted fog color (vs. fixed warm-
    // sunlight base) contributes to the final ray color.
    // 0.40 = 40% fog influence. Enough to track atmospheric state (storm
    // grey, dawn orange) without letting biome-tinted fog (forest green,
    // badlands red) make sun shafts read as the wrong color.
    private static final float FOG_BLEND_FRACTION = 0.40f;

    private boolean cachedCanSeeSky = true;
    private int     cacheX          = Integer.MIN_VALUE;
    private int     cacheY          = Integer.MIN_VALUE;
    private int     cacheZ          = Integer.MIN_VALUE;

    /** 0..MAX_INTENSITY. Renderer should treat <= 0.01 as "do not render." */
    public float intensity = 0f;

    public float red   = 1.00f;
    public float green = 0.92f;
    public float blue  = 0.78f;

    /** cos(sunAngle) — exposed for renderer geometry; same convention as EnvironmentalState. */
    public float sunHeight = 0f;

    /** sin(sunAngle) — exposed for renderer geometry. */
    public float sinAngle = 0f;

    /**
     * @param openness FogManager.getFogOpenness() — the already
     *                 hysteresis-blended value, not a raw biome lookup.
     * @param fogR     FogManager.getFogRed()   — current pipeline fog color.
     * @param fogG     FogManager.getFogGreen() — current pipeline fog color.
     * @param fogB     FogManager.getFogBlue()  — current pipeline fog color.
     *                 Used to derive ray color that tracks atmospheric state
     *                 (dawn warmth, storm grey, rain desaturation) rather
     *                 than a fixed warm-gold that never adapts.
     */
    public void update(FogContext ctx, EnvironmentalState env, float openness,
                       float fogR, float fogG, float fogB) {
        // Sun direction fields are computed first, before any early return,
        // so sunHeight/sinAngle stay current every frame even on frames
        // where the rest of update() exits early (underground, no sky,
        // etc.) — guards against stale values if future code ever reads
        // them independent of isVisible(). Math is unchanged from before;
        // only the position moved.
        float angle = ctx.sunAngle();
        sunHeight = (float) Math.cos(angle);
        sinAngle  = (float) Math.sin(angle);

        // --- Cheapest gate first: definitely-underground early-out ---
        float y = ctx.cameraY();
        if (y < RAY_SURFACE_THRESHOLD) {
            intensity = 0f;
            return;
        }

        // --- Horizon factor (shared FogMath helper — review Fix 3) ---
        float horizonFactor = FogMath.horizonFactor(sunHeight, sinAngle);
        if (horizonFactor < 0.001f) {
            intensity = 0f;
            return;
        }

        // --- Sky visibility gate (review fix — indoor/cave correctness) ---
        // Placed after the cheap Y/horizon checks and before the haze
        // calculation: this is the one check that touches the world
        // (level.canSeeSky), so everything cheaper runs first.
        if (!sampleSkyVisibilityCached(ctx)) {
            intensity = 0f;
            return;
        }

        // --- Haze factor: rises with humidity/storm, collapses at full storm ---
        float hazeSignal   = Math.max(env.humidityMass, env.stormEnergy);
        float hazeRise     = FogMath.smoothstep(FogMath.clamp(hazeSignal / HAZE_RISE_PEAK, 0f, 1f));
        float stormExcess  = Math.max(0f, env.stormEnergy - HAZE_COLLAPSE_AT) / (1f - HAZE_COLLAPSE_AT);
        float hazeCollapse = FogMath.clamp(1f - stormExcess, 0f, 1f);
        float hazeFactor   = hazeRise * hazeCollapse;

        if (hazeFactor < 0.001f) {
            intensity = 0f;
            return;
        }

        // --- Enclosure factor: now from the caller-supplied blended openness ---
        float enclosureFactor = 1f - openness;

        float raw = horizonFactor * hazeFactor * enclosureFactor;
        intensity = FogMath.clamp(raw, 0f, 1f) * MAX_INTENSITY;

        if (intensity < 0.01f) return;

        // --- Color: fog-informed, atmosphere-driven warm sunlight ---
        //
        // Fog color already encodes atmospheric state — DaylightFogModifier
        // warms it at dawn/dusk, WeatherFogModifier desaturates it in storms,
        // NightFogModifier darkens it at night. Boosting fog luminance toward
        // RAY_TARGET_LUMINANCE makes rays significantly brighter than the
        // ambient atmosphere they scatter through, which is correct — shafts
        // of direct sunlight are brighter than the haze surrounding them.
        //
        // A fixed warm-sunlight base (60%) is blended with the boosted fog
        // color (40%) so biome-specific fog tints (forest green, badlands
        // amber) don't make sun shafts read as colored. The blend still picks
        // up the dominant atmospheric signal — storm grey, dawn warmth — that
        // the fog encodes, without over-inheriting ground-level biome air color.
        float fogLum = fogR * 0.299f + fogG * 0.587f + fogB * 0.114f;
        float boost  = (fogLum > 0.05f)
                ? Math.min(RAY_BOOST_MAX, RAY_TARGET_LUMINANCE / fogLum)
                : 1.5f;

        float fogBoostedR = Math.min(1f, fogR * boost);
        float fogBoostedG = Math.min(1f, fogG * boost);
        float fogBoostedB = Math.min(1f, fogB * boost);

        // Warm-sunlight base: neutral-warm at midday, gold-warm at horizon.
        // horizonFactor peaks when the sun is near the horizon (dawn/dusk),
        // pushing the base toward the bright gold of low-angle sunlight.
        float sunlightR = FogMath.lerp(0.88f, 1.00f, horizonFactor);
        float sunlightG = FogMath.lerp(0.83f, 0.91f, horizonFactor);
        float sunlightB = FogMath.lerp(0.68f, 0.74f, horizonFactor);

        // Blend fixed base with boosted fog color.
        float blendedR = FogMath.lerp(sunlightR, fogBoostedR, FOG_BLEND_FRACTION);
        float blendedG = FogMath.lerp(sunlightG, fogBoostedG, FOG_BLEND_FRACTION);
        float blendedB = FogMath.lerp(sunlightB, fogBoostedB, FOG_BLEND_FRACTION);

        // Haze attenuates blue more than red/green — thick scattering absorbs
        // short wavelengths. Keeps dawn/mist rays warm rather than blue-white.
        red   = FogMath.clamp(blendedR, 0f, 1f);
        green = FogMath.clamp(blendedG, 0f, 1f);
        blue  = FogMath.clamp(blendedB * (1f - hazeFactor * 0.18f), 0f, 1f);
    }

    public boolean isVisible() {
        return intensity > 0.01f;
    }

    /**
     * Clears the cached sky-visibility position so a stale cache from a
     * previous dimension/session can't leak across a reset boundary.
     * Mirrors the reset() pattern FogManager and SkyColorController already
     * use, called from the same two lifecycle points in AtmosClient
     * (disconnect, dimension change).
     *
     * Pure lifecycle cleanup — does not touch intensity/red/green/blue/
     * sunHeight/sinAngle, which are already fully recomputed every frame by
     * update() regardless of cache state.
     */
    public void reset() {
        cacheX = Integer.MIN_VALUE;
        cacheY = Integer.MIN_VALUE;
        cacheZ = Integer.MIN_VALUE;
        cachedCanSeeSky = true;
    }

    /**
     * Returns whether the camera has a direct line to the sky.
     * Cached by block position — invalidates when the player moves
     * >= CACHE_MOVE_THRESHOLD blocks in any axis. Own private cache,
     * deliberately not shared with CanopyMoistureModifier — see class doc.
     */
    private boolean sampleSkyVisibilityCached(FogContext ctx) {
        BlockPos pos = ctx.camera().getBlockPosition();

        int   dx     = pos.getX() - cacheX;
        int   dy     = pos.getY() - cacheY;
        int   dz     = pos.getZ() - cacheZ;
        float distSq = dx * dx + dy * dy + dz * dz;

        if (cacheX != Integer.MIN_VALUE
                && distSq < CACHE_MOVE_THRESHOLD * CACHE_MOVE_THRESHOLD) {
            return cachedCanSeeSky;
        }

        cachedCanSeeSky = ctx.level().canSeeSky(pos);
        cacheX = pos.getX();
        cacheY = pos.getY();
        cacheZ = pos.getZ();
        return cachedCanSeeSky;
    }
}