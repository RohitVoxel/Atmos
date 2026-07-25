package net.atmos.diagnostics;

/**
 * Pure validation for atmospheric color and alpha channels.
 */
public final class ColorValidator {

    private ColorValidator() {}

    public static ValidationResult validate(float r, float g, float b, float a) {
        if (!Float.isFinite(r) || !Float.isFinite(g) || !Float.isFinite(b) || !Float.isFinite(a)) {
            return ValidationResult.invalid("Non-finite color channel");
        }
        if (r < 0f || r > 1f || g < 0f || g > 1f || b < 0f || b > 1f) {
            return ValidationResult.invalid("Color out of bounds [0,1]");
        }
        if (a < 0f || a > 1f) {
            return ValidationResult.invalid("Alpha out of bounds [0,1]");
        }
        return ValidationResult.VALID;
    }
}