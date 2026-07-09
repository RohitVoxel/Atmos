package net.atmos.director;

/**
 * The five atmospheric pacing states of the Atmosphere Director, per
 * Chapter 11 §11.11 ("Atmospheric States").
 *
 * Pure data type. No transition rule between phases exists anywhere in
 * the codebase yet — Chapter 11 §11.17-§11.20 ("State Transitions,"
 * "Transition Rules") describes confidence-threshold-driven pacing logic
 * that is explicitly out of scope for this Foundation stage. Stage 1
 * establishes only the vocabulary of possible phases; {@link
 * AtmosphereDirector} does not yet decide when or why to move between
 * them — see that class's doc for the Stage 1 phase-behavior boundary.
 */
public enum DirectorPhase {
    /** Chapter 11 §11.12 — entering a newly encountered environment. */
    ESTABLISHING,
    /** Chapter 11 §11.13 — atmospheric quality is improving. */
    BUILDING,
    /** Chapter 11 §11.14 — rare alignment of multiple atmospheric systems. */
    PEAK,
    /** Chapter 11 §11.15 — a Peak or Building moment fading naturally. */
    RESOLVING,
    /** Chapter 11 §11.16 — the quiescent reference state. */
    CALM
}