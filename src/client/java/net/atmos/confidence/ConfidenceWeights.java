package net.atmos.confidence;

/**
 * Centralized, authoritative location for every Confidence System (Chapter 4)
 * tuning value.
 *
 * Added during the Confidence System Final Cleanup pass, prior to Cluster
 * Builder approval. Before this file existed, each tier's weight constants
 * (and soft floors) lived privately inside their respective evaluator
 * classes (TierAEvaluator, TierBEvaluator, TierCEvaluator). That made
 * future balancing require hunting across three files. This is a pure
 * relocation — no numerical value below differs from its original
 * evaluator-local definition.
 *
 * Rule going forward: no Confidence evaluator class may declare its own
 * tuning constant. Every weight or floor used by Tier A, Tier B, or Tier C
 * belongs here, and only here.
 *
 * Explicitly NOT centralized here, by design:
 *   - TierCEvaluator.MAX_PRESENTABLE_DISTANCE and FRUSTUM_PROBE_HALF_EXTENT
 *     remain local to TierCEvaluator. They are not "Confidence weights" —
 *     they are geometric/render-distance defaults that Tier C must not
 *     permanently own at all (see TierCEvaluator's class doc). Moving them
 *     here would misrepresent them as belonging to Confidence tuning when
 *     their real future home is renderer configuration / quality settings.
 *
 * Tuning status: values below are still the original conservative defaults
 * (near-equal splits). Relocating them here does not constitute a tuning
 * pass — Tier A's weight balance in particular still awaits Rohit's
 * explicit approval, as previously flagged.
 */
public final class ConfidenceWeights {

    private ConfidenceWeights() {}

    // --- Tier A — Atmospheric Possibility ---
    // See TierAEvaluator's class doc for why only humidity and thermal
    // energy are used, and why storm-related fields were excluded rather
    // than merely deferred.
    public static final float TIER_A_WEIGHT_HUMIDITY = 0.5f;
    public static final float TIER_A_WEIGHT_THERMAL  = 0.5f;

    // --- Tier B — Local Opportunity ---
    public static final float TIER_B_WEIGHT_TERRAIN_OPENNESS = 0.5f;
    public static final float TIER_B_WEIGHT_SKY_EXPOSURE     = 0.5f;

    // Soft floor applied to the binary sky-exposure signal (level.canSeeSky
    // is inherently true/false) so Tier B never hard-collapses to a literal
    // zero under a single leaf block. See TierBEvaluator's class doc.
    public static final float TIER_B_SKY_EXPOSURE_FLOOR = 0.15f;

    // --- Tier C — Geometric Presentation ---
    public static final float TIER_C_WEIGHT_DISTANCE  = 0.34f;
    public static final float TIER_C_WEIGHT_ALIGNMENT = 0.33f;
    public static final float TIER_C_WEIGHT_FRUSTUM   = 0.33f;

    // Soft floors preventing hard binary collapse — same rationale as
    // TIER_B_SKY_EXPOSURE_FLOOR. See TierCEvaluator's class doc.
    public static final float TIER_C_ALIGNMENT_FLOOR = 0.02f;
    public static final float TIER_C_FRUSTUM_FLOOR   = 0.05f;
}