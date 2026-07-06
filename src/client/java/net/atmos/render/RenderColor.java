package net.atmos.render;

/**
 * Immutable float-channel color, shared across future rendering systems
 * (fog, sky, clouds, auroras, shafts, etc.).
 *
 * Preserves the existing [0,1] float-channel convention already used
 * throughout the project (FogState.red/green/blue, SkyColorController's
 * packed-channel math) rather than introducing a packed-int or a new
 * representation. Extracted from RenderCluster's former nested Color
 * record — color is a reusable rendering concept, not something
 * RenderCluster should own.
 *
 * No numeric range validation is enforced here. RenderCluster's own
 * constructor previously performed no range check on its nested Color
 * record either — this refactor preserves that existing behavior exactly
 * rather than introducing new validation.
 */
public record RenderColor(float red, float green, float blue) {}