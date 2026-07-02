package net.atmos.atmosphere;

/**
 * Velocity-damped float drifter.
 *
 * Uses real inertia: value accelerates toward target, decelerates on arrival,
 * and can overshoot slightly before settling. This is how physical quantities
 * (pressure, humidity, temperature) actually behave.
 *
 * Parameters:
 *   accelStrength   — how hard the value accelerates toward target.
 *   dampingRate     — exponential velocity decay per second (frame-rate independent).
 *
 * Tuning guide:
 *   accel=0.4, damp=2.5  — sluggish, heavy inertia (humidity mass)
 *   accel=0.8, damp=3.0  — medium inertia (storm energy)
 *   accel=1.5, damp=4.0  — responsive but smooth (thermal)
 *   accel=2.0, damp=5.0  — fast-settling (night depth)
 */
public final class AtmosphereDrifter {

    private final float accelStrength;
    private final float dampingRate;

    private float value;
    private float velocity;

    public AtmosphereDrifter(float initial, float accelStrength, float dampingRate) {
        this.value         = initial;
        this.velocity      = 0f;
        this.accelStrength = accelStrength;
        this.dampingRate   = dampingRate;
    }

    public float advance(float target, float deltaSec) {
        float diff = target - value;

        float accel = diff * accelStrength;
        velocity += accel * deltaSec;

        // Exact solution to the damping ODE — frame-rate independent.
        velocity *= (float) Math.exp(-dampingRate * deltaSec);

        value += velocity * deltaSec;

        if (Math.abs(diff) < 1e-4f && Math.abs(velocity) < 1e-4f) {
            value    = target;
            velocity = 0f;
        }

        return value;
    }

    public float get()         { return value;    }
    public float getVelocity() { return velocity; }

    /** Hard reset — use on world load to prevent startup drift spikes. */
    public void snap(float v) {
        value    = v;
        velocity = 0f;
    }
}