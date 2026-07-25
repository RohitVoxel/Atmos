package net.atmos.diagnostics;

/**
 * Pure functions for validating render geometry at stage boundaries.
 * No side effects. Math is optimized for CPU efficiency.
 */
public final class GeometryValidator {

    // Floating-point tolerance for zero-area validation to account for precision loss
    private static final float AREA_EPSILON = 1e-6f;

    private GeometryValidator() {}

    public static ValidationResult validateQuad(float[] vertices, float[] normals, float[] uvs) {
        if (vertices == null || normals == null || uvs == null) return ValidationResult.invalid("Null arrays");
        if (vertices.length != 12) return ValidationResult.invalid("Invalid quad vertex count");

        for (float v : vertices) if (!Float.isFinite(v)) return ValidationResult.invalid("Non-finite vertex");
        for (float n : normals) if (!Float.isFinite(n)) return ValidationResult.invalid("Non-finite normal");
        for (float u : uvs) if (!Float.isFinite(u)) return ValidationResult.invalid("Non-finite UV");

        if (hasDuplicateVertices(vertices)) return ValidationResult.invalid("Duplicate vertices");
        if (isDegenerate(vertices)) return ValidationResult.invalid("Degenerate quad (zero area)");

        return ValidationResult.VALID;
    }

    private static boolean hasDuplicateVertices(float[] v) {
        return equals(v, 0, 3) || equals(v, 3, 6) || equals(v, 6, 9) || equals(v, 9, 0);
    }

    private static boolean equals(float[] v, int a, int b) {
        return v[a] == v[b] && v[a+1] == v[b+1] && v[a+2] == v[b+2];
    }

    private static boolean isDegenerate(float[] v) {
        return isZeroArea(v, 0, 3, 6) || isZeroArea(v, 0, 6, 9);
    }

    private static boolean isZeroArea(float[] v, int a, int b, int c) {
        float e1x = v[b] - v[a], e1y = v[b+1] - v[a+1], e1z = v[b+2] - v[a+2];
        float e2x = v[c] - v[a], e2y = v[c+1] - v[a+1], e2z = v[c+2] - v[a+2];

        float crossX = e1y * e2z - e1z * e2y;
        float crossY = e1z * e2x - e1x * e2z;
        float crossZ = e1x * e2y - e1y * e2x;

        return (crossX * crossX + crossY * crossY + crossZ * crossZ) < AREA_EPSILON;
    }
}