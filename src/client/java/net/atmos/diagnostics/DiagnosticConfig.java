package net.atmos.diagnostics;

public record DiagnosticConfig(
        DiagnosticMode mode,
        int historyCapacity,
        boolean detailedLogging
) {
    public static final DiagnosticConfig DEFAULT = new DiagnosticConfig(DiagnosticMode.OFF, 120, false);
}