package net.atmos.overlay;

public enum OverlayType {
    FROST(false),
    SNOW(false),
    WET(false),
    AUTUMN(false),
    DUST(false),
    POLLEN(false);

    private final boolean supportsUnderside;

    OverlayType(boolean supportsUnderside) {
        this.supportsUnderside = supportsUnderside;
    }

    public boolean supportsUnderside() {
        return supportsUnderside;
    }
}