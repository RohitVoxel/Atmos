package net.atmos.core;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Immutable, per-frame snapshot of camera state.
 *
 * Per Appendix F §1 (Camera Snapshot Contract): owned and published exclusively
 * by the Render Thread via CameraManager. Consumers (Confidence System, future
 * Exposure Model, future PES, any future camera-dependent simulation) must
 * treat this as read-only and never attempt to mutate it.
 *
 * Note on camera-relative rendering: this snapshot intentionally does NOT
 * carry a pose-stack transform (PoseStack.last().pose()). Per Chapter 9 §3
 * (Principle 3), Atmos performs camera-relative conversion via direct Vec3
 * subtraction (worldPosition - camera.position()), not via matrix-stack
 * multiplication. A PoseStack is also not reliably available at
 * WorldRenderEvents.START (the single publish() call site) — it is only
 * constructed later in the frame once actual render passes begin, which is
 * why existing renderers (e.g. CrepuscularRayRenderer) fetch it directly at
 * their own later render hook (AFTER_TRANSLUCENT) rather than relying on a
 * value captured earlier in the frame. If a future system needs a GPU pose
 * transform, it must fetch WorldRenderContext.matrixStack() itself at its own
 * render-time hook — that is a rendering-stage concern, not a per-frame
 * simulation-snapshot concern, and does not belong on this record.
 *
 * Note on immutability: position/lookDirection (Vec3) are genuinely immutable.
 * projectionMatrix (Matrix4f) and frustum (Frustum) are externally-owned
 * Minecraft/JOML types that are mutable by their own API — Atmos never
 * mutates them after capture. This mirrors the existing convention in
 * FogContext, which holds a live Camera/ClientLevel reference under the same
 * "captured once, treated as read-only for the frame" discipline.
 *
 * Field naming note (AUTOPSY cleanup): fovRadians is derived in
 * CameraManager.publish() via 2.0 * Math.atan2(1.0, projection.m11()), which
 * is radians. The field was previously misnamed fovDegrees despite never
 * containing a degree value; no consumer existed yet that depended on the
 * old name, so this is a pure rename with no behavioral change.
 */
public record CameraSnapshot(
        Vec3 position,
        Vec3 lookDirection,
        Matrix4f projectionMatrix,
        Frustum frustum,
        float fovRadians,
        float partialTick,
        long frameSequence
) {}