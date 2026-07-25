package net.atmos.diagnostics;

/**
 * Pure observer facade.
 * Validates primitives only. Completely decoupled from Atmos internals and production objects.
 */
public final class DiagnosticValidator {

    private DiagnosticValidator() {}

    public static void validateFogState(float start, float end, float r, float g, float b, float a) {
        if (!DiagnosticManager.isActive() || DiagnosticManager.MODE == DiagnosticMode.LIGHT) return;

        ValidationResult distRes = StateValidator.validateDistances(start, end);
        if (!distRes.isValid()) {
            DiagnosticHooks.captureAnomaly(PipelineStage.ENVIRONMENTAL_STATE, AnomalyType.STATE_INVALID_DISTANCE);
        }

        ValidationResult colRes = ColorValidator.validate(r, g, b, a);
        if (!colRes.isValid()) {
            DiagnosticHooks.captureAnomaly(PipelineStage.ENVIRONMENTAL_STATE, AnomalyType.COLOR_OUT_OF_BOUNDS);
        }
    }

    @SuppressWarnings("unused")
    public static void validateGeometryData(float[] vertices, float[] normals, float[] uvs) {
        if (!DiagnosticManager.isActive() || DiagnosticManager.MODE == DiagnosticMode.LIGHT) return;

        ValidationResult res = GeometryValidator.validateQuad(vertices, normals, uvs);
        if (!res.isValid()) {
            DiagnosticHooks.captureAnomaly(PipelineStage.GEOMETRY_GENERATION, AnomalyType.GEOMETRY_DEGENERATE);
        }
    }
}