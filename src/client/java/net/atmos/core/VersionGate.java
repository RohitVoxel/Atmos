package net.atmos.core;

/**
 * Batch 1 Phase 3 — needsUpdate() guard via cheap version comparison.
 *
 * A producer (CellGrid, OverlayManager, etc.) exposes a monotonically
 * increasing structural version. A consumer (ClusterBuilder, Composition,
 * etc.) holds one VersionGate per upstream dependency and only recomputes
 * when that version has changed since last observed.
 */
public final class VersionGate {

    private long lastSeenVersion = -1L;

    /** Returns true (and records the new version) only if version changed. */
    public boolean changed(long currentVersion) {
        if (currentVersion != lastSeenVersion) {
            lastSeenVersion = currentVersion;
            return true;
        }
        return false;
    }

    public void reset() {
        lastSeenVersion = -1L;
    }
}