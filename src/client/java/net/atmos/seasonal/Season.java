package net.atmos.seasonal;

/** Phase 1 — the four named seasons layered onto the existing continuous seasonal cycle. */
public enum Season {
    SPRING, SUMMER, AUTUMN, WINTER;

    public Season next() {
        return values()[(ordinal() + 1) % 4];
    }
}