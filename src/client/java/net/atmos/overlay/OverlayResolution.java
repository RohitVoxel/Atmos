package net.atmos.overlay;

public enum OverlayResolution {
    LOW(16),
    MEDIUM(32),
    HIGH(64);

    private final int pixels;

    OverlayResolution(int pixels) { this.pixels = pixels; }

    public int pixels() { return pixels; }

    public String folder() { return pixels + "x" + pixels; }

    public static OverlayResolution fromPixels(int pixels) {
        for (OverlayResolution r : values()) {
            if (r.pixels == pixels) return r;
        }
        return MEDIUM;
    }
}
