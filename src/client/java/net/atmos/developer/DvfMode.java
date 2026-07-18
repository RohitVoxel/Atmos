package net.atmos.developer;

public enum DvfMode {
    OFF("Off"),
    ENVIRONMENTAL_STATE("Environmental State"),
    CELL_GRID("Cell Grid"),
    CONFIDENCE("Confidence"),
    COMPOSITION("Composition"),
    ATMOSPHERIC_MEMORY("Atmospheric Memory"),
    EXPOSURE("Exposure"),
    SUN_REACH("Sun Reach"),
    SKY_EXPOSURE("Sky Exposure Debug"), // Renamed from Prototype Shafts
    FINAL_OUTPUT("Final Atmos Output");

    private final String displayName;

    DvfMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DvfMode next() {
        DvfMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}