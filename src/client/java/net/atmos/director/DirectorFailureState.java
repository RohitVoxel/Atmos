package net.atmos.director;

/**
 * Immutable Stage 9 (Appendix U) failure-handling snapshot.
 *
 * weatherStableTime — accumulated seconds since the last detected raw
 * rain/thunder change, clamped to [0, WEATHER_STABILITY_TIME] (§U.6-U.8).
 * weatherStable      — true once weatherStableTime reaches
 *                       WEATHER_STABILITY_TIME (§U.9).
 * travelScale        — 0.50 during Fast Travel Mode (§U.11-U.12), else
 *                       1.00. Affects only Director Memory decay
 *                       (§U.13-U.14) — no other Stage 1-8 mathematics.
 */
public record DirectorFailureState(
        float weatherStableTime,
        boolean weatherStable,
        float travelScale
) {
    public DirectorFailureState {
        if (!Float.isFinite(weatherStableTime) || weatherStableTime < 0f) {
            throw new IllegalArgumentException(
                    "weatherStableTime must be non-negative and finite, got " + weatherStableTime);
        }
        if (!Float.isFinite(travelScale) || travelScale <= 0f) {
            throw new IllegalArgumentException(
                    "travelScale must be positive and finite, got " + travelScale);
        }
    }
}