package net.atmos.diagnostics;

public record ValidationResult(
        boolean isValid,
        String message
) {
    public static final ValidationResult VALID = new ValidationResult(true, "VALID");

    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }
}