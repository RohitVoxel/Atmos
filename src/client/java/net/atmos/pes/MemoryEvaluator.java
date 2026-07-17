package net.atmos.pes;

import net.atmos.composition.Composition;
import net.atmos.memory.AtmosphericMemorySnapshot;

/**
 * Memory Evaluation — Chapter 12 §12.24.
 *
 * Checks composition density against the persisted Atmospheric Memory
 * channels (Chapter 13) — humidityMemory and stormMemory — rather than
 * the instantaneous EnvironmentalState values already checked by
 * EnvironmentalConsistencyEvaluator (§12.11) and WeatherIdentityEvaluator
 * (§12.13). This catches §12.24's documented failure case: an atmosphere
 * that snaps to "instantly clear" the moment rain stops, discarding the
 * residual humidity/storm influence Atmospheric Memory is designed to
 * preserve.
 *
 * memory is nullable. AtmosphericMemoryState remains unwired into the
 * live game loop (Chapter 13 class docs — "no disk-persistence
 * requirement exists for the global channel"), so no producer is
 * guaranteed to supply a snapshot at any current call site. A null
 * snapshot is not evidence of inconsistency — it is treated identically
 * to CompositionEvaluator's "too few clusters to judge variety
 * meaningfully" case: neutral, consistent.
 *
 * §12.10's consumer list predates Chapter 13's completion and does not
 * name Atmospheric Memory. PES's write-back prohibition ("never owns,
 * modifies, or writes back to... Atmospheric Memory") is unaffected —
 * this evaluator only reads the published immutable snapshot.
 */
public final class MemoryEvaluator {

    private MemoryEvaluator() {}

    public static MemoryEvaluationResult evaluate(AtmosphericMemorySnapshot memory, Composition composition) {
        if (memory == null) {
            return new MemoryEvaluationResult(0f, 0f, 1f, true);
        }

        float densitySignal = PESMath.compositionDensitySignal(composition);

        float humidityDeviation = Math.abs(densitySignal - memory.humidityMemory());
        float humidityScore = PESMath.deviationScore(densitySignal, memory.humidityMemory(),
                PESWeights.MEMORY_EVALUATION_TOLERANCE);

        float stormDeviation = Math.abs(densitySignal - memory.stormMemory());
        float stormScore = PESMath.deviationScore(densitySignal, memory.stormMemory(),
                PESWeights.MEMORY_EVALUATION_TOLERANCE);

        float value = (humidityScore + stormScore) / 2f;

        return new MemoryEvaluationResult(humidityDeviation, stormDeviation, value,
                PESMath.passesCategoryThreshold(value));
    }
}