package net.atmos.overlay;

/**
 * Invalidation urgency tiers — Batch 3 §15. Ordinal declaration order still
 * governs OverlayInvalidationQueue's own tier iteration (its documented,
 * unchanged design — Phase 12), but the numeric weight fed into
 * AdaptivePriorityScheduler is an explicit field, per Phase 6, rather than
 * an implicit reliance on .ordinal(). The two currently coincide in value
 * by design intent, not by accident.
 */
public enum InvalidationPriority {
    VISIBLE_NEAR(0),
    LOADED_NEAR(1),
    LOADED_OTHER(2),
    BACKGROUND(3);

    private final int schedulerWeight;

    InvalidationPriority(int schedulerWeight) {
        this.schedulerWeight = schedulerWeight;
    }

    /** Explicit priority number for AdaptivePriorityScheduler.submit() — never derive this via .ordinal(). */
    public int schedulerWeight() {
        return schedulerWeight;
    }
}