package net.atmos.overlay;

public final class OverlayLevelResolver {

    private OverlayLevelResolver() {}

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    private static final float VISIBILITY_THRESHOLD = 0.005f;
    private static final float SCALE10_VISIBILITY_THRESHOLD = 0.05f;

    /** Legacy 0-1 scale — used by OverlayManager's aggregate contribution values (DebugCommands). */
    public static int levelFor(float value) {
        if (value <= VISIBILITY_THRESHOLD) return 0;
        int level = (int) Math.ceil(value * MAX_LEVEL);
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    /** Atmos overlay scale (0.0 -> 10.0) — used by OverlaySurfaceStateStore's per-surface values. */
    public static int levelForScale10(float value) {
        if (value <= SCALE10_VISIBILITY_THRESHOLD) return 0;
        int level = Math.round(value);
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }
}