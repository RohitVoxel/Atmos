package net.atmos.pes;

import net.atmos.cellgrid.CellCoord;

/**
 * Read-only, zero-allocation view over a {@link PESHistoryBuffer}.
 * Evaluators depend on this interface rather than the concrete buffer so
 * they can never call {@code push()}/{@code reset()} — buffer mutation
 * remains exclusively owned by {@link PerceptualEvaluationSystem} per
 * §12.10.
 */
public interface PESHistoryView {

    int size();

    /** 0 = oldest retained entry, {@code size() - 1} = most recently pushed entry. */
    PESHistoryEntry get(int indexFromOldest);

    /** Current recurrence count of {@code anchor} within the retained window; 0 if untracked or null. */
    int heroAnchorCount(CellCoord anchor);

    /** Highest recurrence count among all tracked Hero anchors; 0 if none retained. */
    int mostFrequentHeroAnchorCount();

    /** Count of retained entries whose {@code heroAnchor()} is non-null. */
    int heroBearingEntryCount();
}