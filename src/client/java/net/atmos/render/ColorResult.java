package net.atmos.render;

/** Explainable output of Color Producer — Appendix ZB Blocker 2. */
public record ColorResult(
        RenderColor color,
        float luminance
) {
    public ColorResult {
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
    }
}