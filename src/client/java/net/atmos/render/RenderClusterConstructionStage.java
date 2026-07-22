package net.atmos.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RenderCluster Construction — Chapter 9 Stage 5 Phase 5 (Appendix L, Appendix P).
 *
 * Assembly-only stage per Appendix L §10: collects already-finalized producer
 * outputs and constructs immutable RenderClusters. Owns none of the 13
 * RenderCluster fields itself (Appendix P §2/§6) and performs no simulation,
 * scoring, or composition of its own.
 *
 * Per Appendix P §11, construction is authorized only when every required
 * producer has supplied valid data; otherwise the candidate is skipped
 * entirely — never defaulted, never fabricated (Appendix Y §10).
 */
public final class RenderClusterConstructionStage {

    private RenderClusterConstructionStage() {}

    /** Attempts one RenderCluster assembly. Empty when any required producer is unavailable. */
    public static Optional<RenderCluster> attemptConstruct(
            Vec3 position,
            Optional<Vec3> direction,
            Optional<Float> width,
            Optional<Float> length,
            Optional<Float> alpha,
            Optional<RenderColor> color,
            Optional<Float> definition,
            float exposureScale,
            Optional<Float> fadeDistance,
            RenderCluster.Role role,
            Optional<Float> animationPhase,
            Optional<Integer> lodLevel,
            Optional<Float> sunReach
    ) {
        if (direction.isEmpty() || width.isEmpty() || length.isEmpty() || alpha.isEmpty()
                || color.isEmpty() || definition.isEmpty() || fadeDistance.isEmpty()
                || animationPhase.isEmpty() || lodLevel.isEmpty() || sunReach.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RenderCluster(
                position, direction.get(), width.get(), length.get(),
                alpha.get(), color.get(), definition.get(), exposureScale,
                fadeDistance.get(), role, animationPhase.get(), lodLevel.get(), sunReach.get()
        ));
    }

    /** Publishes only successfully constructed clusters — Appendix L §5 (immutable, disposable, per-frame). */
    public static List<RenderCluster> publishAll(List<Optional<RenderCluster>> attempts) {
        List<RenderCluster> result = new ArrayList<>(attempts.size());
        for (Optional<RenderCluster> attempt : attempts) {
            attempt.ifPresent(result::add);
        }
        return List.copyOf(result);
    }
}