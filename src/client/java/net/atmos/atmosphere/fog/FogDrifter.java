package net.atmos.atmosphere.fog;

/**
 * Smooths a single float with velocity damping and asymmetric build/clear speeds.
 * "Build" speed: value moving toward a denser/darker state (fog rolling in).
 * "Clear" speed: value opening up — real atmosphere always lingers on the way out.
 * The velocity term prevents snapping after large jumps. The value accelerates
 * into the change and decelerates near the target — a natural drift rather than
 * the geometric decay you get from a plain lerp.
 */
public final class FogDrifter {

    // How fast velocity accelerates toward the ideal rate per second.
    // 4.0 = firm but organic. Lower = heavier, more inertia.
    private static final float ACCEL = 4.0f;

    private final float buildSpeed; // response rate when value is rising  (fog densifying)
    private final float clearSpeed; // response rate when value is falling  (fog lifting)

    private float current;
    private float velocity = 0f;

    public FogDrifter(float initial, float buildSpeed, float clearSpeed) {
        this.current    = initial;
        this.buildSpeed = buildSpeed;
        this.clearSpeed = clearSpeed;
    }

    /**
     * Advance toward {@code target} by {@code deltaSec} seconds.
     * Returns the updated value.
     */
    public float advance(float target, float deltaSec) {
        float diff = target - current;
        if (Math.abs(diff) < 1e-5f) {
            velocity = 0f;
            current  = target;
            return current;
        }

        float speed    = (diff > 0f) ? buildSpeed : clearSpeed;
        float idealVel = diff * speed;

        // Smoothly accelerate velocity — absorbs large jumps without snapping.
        velocity = FogMath.lerp(velocity, idealVel, Math.min(1f, ACCEL * deltaSec));
        current += velocity * deltaSec;

        // Don't overshoot.
        if (diff > 0f) current = Math.min(current, target);
        else           current = Math.max(current, target);

        return current;
    }

    public float get()             { return current; }
    public void  snap(float value) { current = value; velocity = 0f; }
}