package net.atmos.aps;

/** Bottleneck classification vocabulary — Appendix D §2. NONE added as the neutral/default value. */
public enum PerformanceBottleneck {
    NONE,
    CLUSTERS,
    RAYS,
    SIMULATION,
    MEMORY
}