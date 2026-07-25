package net.atmos.diagnostics;

/**
 * Pure validation for pipeline state parameters.
 */
public final class StateValidator {

    private StateValidator() {}

    public static ValidationResult validateDistances(float start, float end) {
        if (!Float.isFinite(start) || !Float.isFinite(end)) {
            return ValidationResult.invalid("Non-finite distance values");
        }
        if (start < 0f || end < 0f) {
            return ValidationResult.invalid("Negative distance values");
        }
        if (start > end) {
            return ValidationResult.invalid("Start distance exceeds end distance");
        }
        return ValidationResult.VALID;
    }
}