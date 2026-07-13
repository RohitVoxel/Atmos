package net.atmos.pes;

import net.atmos.cellgrid.CellCoord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded ring buffer of {@link PESHistoryEntry} — Chapter 12 §12.30.
 *
 * Fixed-size array, overwrites oldest entries automatically, never grows.
 * Strictly local to PES (§12.30 "Ownership") — no other system may read
 * or write this buffer directly. Evaluators consume it only through the
 * read-only {@link PESHistoryView} interface, which exposes zero-allocation
 * indexed access — {@link #entriesOldestFirst()} remains available for
 * debug/inspection use only and must never be called from the per-frame
 * evaluation path.
 *
 * Hero-anchor recurrence (consumed by PatternRepetitionEvaluator) is
 * maintained incrementally on push/evict rather than rebuilt from scratch
 * each evaluation — this avoids both an O(capacity) rescan and a per-frame
 * HashMap allocation that the prior revision had.
 *
 * {@link #mostFrequentHeroAnchorCount()} is a plain field read (true O(1),
 * not a scan of {@code heroAnchorCounts}). Correctness across evictions is
 * maintained via {@code countFrequency} — a histogram of "how many anchors
 * currently have count N", indexed by N (bounded by capacity). On evict,
 * if the evicted anchor held the current max and no other anchor shares
 * it, the max pointer walks down to the next occupied bucket. This walk is
 * O(1) amortized: total descent across the buffer's lifetime is bounded by
 * total ascent, which is bounded by the number of push() calls.
 */
public final class PESHistoryBuffer implements PESHistoryView {

    private final PESHistoryEntry[] entries;
    private int writeIndex = 0;
    private int size = 0;

    private final Map<CellCoord, Integer> heroAnchorCounts = new HashMap<>();
    private int heroBearingEntryCount = 0;

    // countFrequency[c] = number of distinct anchors currently at count c.
    // Sized capacity+1 since no anchor's count can exceed the buffer capacity.
    private final int[] countFrequency;
    private int currentMaxHeroAnchorCount = 0;

    public PESHistoryBuffer() {
        this(PESWeights.HISTORY_BUFFER_CAPACITY);
    }

    public PESHistoryBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        this.entries = new PESHistoryEntry[capacity];
        this.countFrequency = new int[capacity + 1];
    }

    public void push(PESHistoryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }

        if (size == entries.length) {
            // Buffer full: the slot about to be overwritten holds a real,
            // previously pushed entry (never null at this point) and is
            // leaving the retained window.
            decrementHeroAnchor(entries[writeIndex]);
        }

        entries[writeIndex] = entry;
        incrementHeroAnchor(entry);

        writeIndex = (writeIndex + 1) % entries.length;
        if (size < entries.length) {
            size++;
        }
    }

    @Override
    public int size() {
        return size;
    }

    public int capacity() {
        return entries.length;
    }

    @Override
    public PESHistoryEntry get(int indexFromOldest) {
        if (indexFromOldest < 0 || indexFromOldest >= size) {
            throw new IndexOutOfBoundsException(
                    "index " + indexFromOldest + " out of bounds for size " + size);
        }
        int startIndex = (size < entries.length) ? 0 : writeIndex;
        return entries[(startIndex + indexFromOldest) % entries.length];
    }

    @Override
    public int heroAnchorCount(CellCoord anchor) {
        if (anchor == null) return 0;
        Integer count = heroAnchorCounts.get(anchor);
        return count == null ? 0 : count;
    }

    @Override
    public int mostFrequentHeroAnchorCount() {
        return currentMaxHeroAnchorCount;
    }

    @Override
    public int heroBearingEntryCount() {
        return heroBearingEntryCount;
    }

    /**
     * Materializes an oldest-first copy of every retained entry. Allocates
     * a new list every call — debug/inspection use only. Never call this
     * from the per-frame evaluation path; use the zero-allocation
     * {@link PESHistoryView} accessors instead.
     */
    public List<PESHistoryEntry> entriesOldestFirst() {
        List<PESHistoryEntry> result = new ArrayList<>(size);
        int startIndex = (size < entries.length) ? 0 : writeIndex;
        for (int i = 0; i < size; i++) {
            result.add(entries[(startIndex + i) % entries.length]);
        }
        return List.copyOf(result);
    }

    public void reset() {
        Arrays.fill(entries, null);
        writeIndex = 0;
        size = 0;
        heroAnchorCounts.clear();
        heroBearingEntryCount = 0;
        Arrays.fill(countFrequency, 0);
        currentMaxHeroAnchorCount = 0;
    }

    private void incrementHeroAnchor(PESHistoryEntry entry) {
        CellCoord anchor = entry.heroAnchor();
        if (anchor == null) return;

        Integer current = heroAnchorCounts.get(anchor);
        int oldCount = (current == null) ? 0 : current;
        int newCount = oldCount + 1;
        heroAnchorCounts.put(anchor, newCount);
        heroBearingEntryCount++;

        if (oldCount > 0) countFrequency[oldCount]--;
        countFrequency[newCount]++;
        if (newCount > currentMaxHeroAnchorCount) {
            currentMaxHeroAnchorCount = newCount;
        }
    }

    private void decrementHeroAnchor(PESHistoryEntry entry) {
        CellCoord anchor = entry.heroAnchor();
        if (anchor == null) return;
        Integer current = heroAnchorCounts.get(anchor);
        if (current == null) return;

        int oldCount = current;
        countFrequency[oldCount]--;
        if (oldCount <= 1) {
            heroAnchorCounts.remove(anchor);
        } else {
            int newCount = oldCount - 1;
            heroAnchorCounts.put(anchor, newCount);
            countFrequency[newCount]++;
        }
        heroBearingEntryCount--;

        // Only the bucket that was previously the max can invalidate it —
        // walk down to the next occupied bucket. Amortized O(1): total
        // descent is bounded by total prior ascent (see class doc).
        if (oldCount == currentMaxHeroAnchorCount) {
            while (currentMaxHeroAnchorCount > 0 && countFrequency[currentMaxHeroAnchorCount] == 0) {
                currentMaxHeroAnchorCount--;
            }
        }
    }
}