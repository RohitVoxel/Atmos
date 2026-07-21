package net.atmos.aps;

/** APS operating state vocabulary — Chapter 16 §16.10. Transition logic belongs to a later stage. */
public enum PerformanceState {
    EXCELLENT,
    NORMAL,
    BUSY,
    HEAVY,
    CRITICAL,
    RECOVERY
}