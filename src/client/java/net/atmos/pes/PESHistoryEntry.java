package net.atmos.pes;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.CellCoord;
import net.atmos.composition.Composition;

/**
 * Lightweight per-frame summary stored in {@link PESHistoryBuffer} (§12.30).
 * Captures only what Stage 1/2 evaluators consume — no exposure level is
 * included; the Exposure Model (Chapter 14) does not exist yet.
 *
 * evaluationSequence is a caller-supplied monotonically increasing counter
 * (e.g. a tick or frame count) — NOT wall-clock time. Mirrors Appendix F
 * 2.0's ExposureStateSnapshot version identifier ("exists solely to
 * support deterministic snapshot ordering... carries no simulation
 * meaning"). A future caller must never source this from
 * System.currentTimeMillis()/nanoTime() — doing so would make
 * PerceptualEvaluationSystem.evaluate() non-reproducible for identical
 * inputs, breaking §12.35's determinism requirement.
 */
public record PESHistoryEntry(
        float humidityMass,
        float stormEnergy,
        float thermalEnergy,
        float nightDepth,
        CellCoord heroAnchor,
        int secondaryCount,
        int ambientCount,
        long evaluationSequence
) {
    public PESHistoryEntry {
        requireFinite("humidityMass", humidityMass);
        requireFinite("stormEnergy", stormEnergy);
        requireFinite("thermalEnergy", thermalEnergy);
        requireFinite("nightDepth", nightDepth);
        if (secondaryCount < 0) {
            throw new IllegalArgumentException("secondaryCount must be non-negative, got " + secondaryCount);
        }
        if (ambientCount < 0) {
            throw new IllegalArgumentException("ambientCount must be non-negative, got " + ambientCount);
        }
    }

    private static void requireFinite(String name, float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got " + value);
        }
    }

    /** Captures the current frame's PES-relevant summary from live EnvironmentalState/Composition. */
    public static PESHistoryEntry capture(EnvironmentalState env, Composition composition, long evaluationSequence) {
        CellCoord heroAnchor = composition.heroCluster() != null
                ? composition.heroCluster().anchorCoord()
                : null;

        return new PESHistoryEntry(
                env.getHumidityMass(),
                env.getStormEnergy(),
                env.getThermalEnergy(),
                env.getNightDepth(),
                heroAnchor,
                composition.secondaryClusters().size(),
                composition.ambientClusters().size(),
                evaluationSequence
        );
    }
}