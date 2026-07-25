package net.atmos.diagnostics;

public record DiagnosticContext(
        DiagnosticMode mode,
        long sessionStartTimeMs,
        String clientVersion
) {}