package net.atmos.diagnostics;

/**
 * Isolated diagnostic clock abstraction.
 * Ensures diagnostic timing does not rely on direct JVM native calls
 * scattered throughout the codebase, allowing for future mocking or scaling.
 */
public final class DiagnosticClock {

    private DiagnosticClock() {}

    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static long nanoTime() {
        return System.nanoTime();
    }
}