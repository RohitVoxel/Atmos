package net.atmos.core;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publisher for CameraSnapshot, per Appendix F §1.
 *
 * Ownership: Render Thread. Exactly one writer (AtmosClient's
 * WorldRenderEvents.START handler, single call site). Any number of readers
 * from any thread may safely call get() — AtomicReference provides a
 * lock-free, happens-before-safe publish/read without locks.
 *
 * The held reference always points to a fully-constructed, immutable
 * CameraSnapshot or null (before the first frame, or after reset()).
 * Readers never observe a partially-constructed snapshot.
 *
 * Deliberately does NOT read WorldRenderContext.matrixStack(): the PoseStack
 * is not yet constructed at WorldRenderEvents.START (the single call site for
 * publish()) and is only valid later in the frame once actual render passes
 * begin. Atmos does not need it here regardless — camera-relative math is
 * done via Vec3 subtraction per Chapter 9 §3, not pose-matrix transforms. See
 * CameraSnapshot's class doc for the full rationale.
 */
public final class CameraManager {

    private CameraManager() {}

    private static final AtomicReference<CameraSnapshot> CURRENT = new AtomicReference<>(null);
    private static final AtomicLong FRAME_SEQUENCE = new AtomicLong(0L);

    /**
     * Publishes a new CameraSnapshot for the current render frame.
     * Must be called exactly once per frame, from the Render Thread, before
     * any consumer attempts to read the current frame's camera state.
     */
    public static void publish(WorldRenderContext context) {
        Camera camera = context.camera();
        long frame = FRAME_SEQUENCE.incrementAndGet();

        Matrix4f projection = context.projectionMatrix();

        // GameRenderer.getFov(...) is private in 1.21.1 Mojmap and cannot be
        // called from outside the class. Instead, derive FOV analytically
        // from the projection matrix: for a standard symmetric perspective
        // projection, fov = 2 * atan(1 / m11), where m11 is the matrix's
        // [1][1] element. This is exact and avoids reflection entirely.
        float fov = (float) (2.0 * Math.atan2(1.0, projection.m11()));

        // Fabric API 1.21+ replaced WorldRenderContext#tickDelta() with
        // #tickCounter(), exposing Mojang's DeltaTracker.
        DeltaTracker tickCounter = context.tickCounter();
        float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);

        // Camera.getLookVector() returns org.joml.Vector3f in 1.21.1 Mojmap,
        // but CameraSnapshot expects net.minecraft.world.phys.Vec3.
        Vector3f lookJoml = camera.getLookVector();
        Vec3 lookVec = new Vec3(lookJoml.x(), lookJoml.y(), lookJoml.z());

        CameraSnapshot snapshot = new CameraSnapshot(
                camera.getPosition(),
                lookVec,
                projection,
                context.frustum(),
                fov,
                tickDelta,
                frame
        );

        CURRENT.set(snapshot);
    }

    /**
     * Returns the most recently published CameraSnapshot.
     * Returns null before the first frame has been rendered, or immediately
     * after reset(). Callers must null-check.
     */
    public static CameraSnapshot get() {
        return CURRENT.get();
    }

    /**
     * Clears the published snapshot. Called from the same lifecycle points as
     * every other Atmos controller's reset() (disconnect, dimension change)
     * so stale camera state from a previous session/dimension cannot leak
     * into the next one.
     */
    public static void reset() {
        CURRENT.set(null);
    }
}