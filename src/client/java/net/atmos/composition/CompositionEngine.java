package net.atmos.composition;

import net.atmos.cellgrid.CellCoord;
import net.atmos.cluster.Cluster;
import net.atmos.core.CameraSnapshot;
import net.atmos.diagnostics.DiagnosticHooks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Composition Engine (Chapter 10) — Stage 2: Composition Selection.
 *
 * Pipeline position, ownership, and thread ownership are unchanged from
 * Stage 1: this class owns Hero / Secondary / Ambient / Rejected
 * classification (Chapter 2 §19, "Hero Selection | Composition Engine"),
 * reads but never mutates {@link Cluster}, {@link CameraSnapshot}, and
 * {@link net.atmos.atmosphere.EnvironmentalState} via {@link
 * CompositionInputs}, and runs on the Simulation Thread per Appendix L §6.
 *
 * --- Stage 2 algorithm ---
 *
 * 1. Evaluate per-cluster Confidence for every candidate ({@link
 *    ClusterConfidenceEvaluator}), completing Chapter 4 §4's "One
 *    Confidence Per Cluster" requirement that {@link
 *    net.atmos.cluster.ClusterBuilder} explicitly defers.
 * 2. Partition candidates into viable / Rejected by {@link
 *    CompositionWeights#MIN_VIABLE_CONFIDENCE}.
 * 3. Select the single Hero as the viable candidate with the highest Hero
 *    Score ({@link HeroScoreEvaluator}), per Chapter 10 Part 3 ("Rule 1:
 *    Only one Hero"). No Hero is selected if no candidate is viable —
 *    Chapter 10 Part 4 ("Failure Recovery"): "Instead of forcing a Hero
 *    Shaft, Atmos intentionally renders a quieter scene."
 * 4. Rank the remaining viable candidates by Confidence, descending, and
 *    greedily select Secondary candidates (up to {@link
 *    CompositionWeights#MAX_SECONDARY_COUNT}) that satisfy Hard Composition
 *    Rule 4's minimum angular separation from the Hero and from every
 *    already-selected Secondary.
 * 5. Every remaining viable candidate becomes Ambient.
 *
 * Candidates are sorted by {@link Cluster#anchorCoord()} before any
 * selection begins (matching {@link net.atmos.cluster.ClusterBuilder}'s own
 * traversal-order discipline), so identical inputs always produce an
 * identical {@link Composition}.
 *
 * --- Explicitly deferred (see {@link CompositionWeights} for full
 *     per-factor rationale) ---
 *
 * Composition Memory / Hero transitions (Chapter 10 Part 4), Travel
 * Alignment, Sun Reach integration, Temporal Stability, Budget Allocation /
 * APS-driven role caps, Hard Rule 2 (rendered-brightness comparison) and
 * Rule 3 (screen-space center avoidance for Ambient), and any {@link
 * Cluster} → {@link net.atmos.render.RenderCluster} conversion or renderer
 * integration. None of these are implemented, approximated, or
 * placeholder-wired here.
 */
public final class CompositionEngine {

    private CompositionEngine() {}

    private static final Comparator<CellCoord> COORD_ORDER =
            Comparator.comparingInt(CellCoord::x)
                    .thenComparingInt(CellCoord::y)
                    .thenComparingInt(CellCoord::z);

    private static final Comparator<Cluster> ANCHOR_ORDER =
            Comparator.comparing(Cluster::anchorCoord, COORD_ORDER);

    /**
     * Produces a deterministic {@link Composition} from {@code inputs}.
     * Pure function — no mutation of any input, no static state.
     */
    public static Composition compose(CompositionInputs inputs) {
        List<Cluster> candidates = inputs.candidateClusters();
        if (candidates.isEmpty()) {
            return new Composition(null, List.of(), List.of(), List.of());
        }

        CameraSnapshot camera = inputs.camera();

        List<Cluster> ordered = new ArrayList<>(candidates);
        ordered.sort(ANCHOR_ORDER);

        Map<CellCoord, Float> confidenceByAnchor = new HashMap<>();
        for (Cluster cluster : ordered) {
            ClusterConfidenceResult result = ClusterConfidenceEvaluator.evaluate(cluster, camera);
            confidenceByAnchor.put(cluster.anchorCoord(), result.value());

            // FULL DIAGNOSTICS: Accurate, honest confidence value only.
            DiagnosticHooks.recordFullConfidence(
                    cluster.anchorCoord().toString(),
                    result.value()
            );
        }

        List<Cluster> viable   = new ArrayList<>();
        List<Cluster> rejected = new ArrayList<>();
        for (Cluster cluster : ordered) {
            if (confidenceByAnchor.get(cluster.anchorCoord()) >= CompositionWeights.MIN_VIABLE_CONFIDENCE) {
                viable.add(cluster);
            } else {
                rejected.add(cluster);
                DiagnosticHooks.recordFullComposition(
                        cluster.anchorCoord().toString(),
                        "Rejected",
                        "Confidence (" + String.format("%.2f", confidenceByAnchor.get(cluster.anchorCoord())) + ") below threshold"
                );
            }
        }

        if (viable.isEmpty()) {
            return new Composition(null, List.of(), List.of(), List.copyOf(rejected));
        }

        float atmosphericValueMean = averageAtmosphericValue(viable);

        Cluster hero = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (Cluster candidate : viable) {
            float confidence = confidenceByAnchor.get(candidate.anchorCoord());
            HeroScoreResult score =
                    HeroScoreEvaluator.evaluate(candidate, confidence, camera, atmosphericValueMean);
            if (score.value() > bestScore) {
                bestScore = score.value();
                hero = candidate;
            }
        }

        if (hero != null) {
            DiagnosticHooks.recordFullComposition(hero.anchorCoord().toString(), "Accepted", "Selected as Hero");
        }

        List<Cluster> remaining = new ArrayList<>();
        for (Cluster candidate : viable) {
            if (hero != null && !candidate.anchorCoord().equals(hero.anchorCoord())) {
                remaining.add(candidate);
            }
        }
        remaining.sort(
                Comparator.comparing((Cluster c) -> confidenceByAnchor.get(c.anchorCoord()))
                        .reversed()
                        .thenComparing(ANCHOR_ORDER)
        );

        List<Cluster> secondary = new ArrayList<>();
        List<Cluster> ambient   = new ArrayList<>();

        Vec3 cameraPos = camera.position();
        Vec3 heroPos   = hero != null ? hero.centerWorldPos() : cameraPos;

        for (Cluster candidate : remaining) {
            Vec3 candidatePos = candidate.centerWorldPos();
            boolean separated = angularSeparation(cameraPos, heroPos, candidatePos)
                    >= CompositionWeights.MIN_ANGULAR_SEPARATION_RADIANS;

            if (separated) {
                for (Cluster selected : secondary) {
                    if (angularSeparation(cameraPos, selected.centerWorldPos(), candidatePos)
                            < CompositionWeights.MIN_ANGULAR_SEPARATION_RADIANS) {
                        separated = false;
                        break;
                    }
                }
            }

            if (separated && secondary.size() < CompositionWeights.MAX_SECONDARY_COUNT) {
                secondary.add(candidate);
                DiagnosticHooks.recordFullComposition(candidate.anchorCoord().toString(), "Accepted", "Selected as Secondary");
            } else {
                ambient.add(candidate);
                DiagnosticHooks.recordFullComposition(
                        candidate.anchorCoord().toString(),
                        "Accepted",
                        separated ? "Selected as Ambient (Max Secondary Reached)" : "Selected as Ambient (Insufficient Angular Separation)"
                );
            }
        }

        ambient.sort(ANCHOR_ORDER);
        rejected.sort(ANCHOR_ORDER);

        return new Composition(hero, List.copyOf(secondary), List.copyOf(ambient), List.copyOf(rejected));
    }

    private static float averageAtmosphericValue(List<Cluster> clusters) {
        float sum = 0f;
        for (Cluster cluster : clusters) {
            sum += cluster.averageAtmosphericValue();
        }
        return sum / clusters.size();
    }

    /**
     * Angle, in radians, between two world-space points as seen from the
     * camera — enforces Hard Composition Rule 4 (Chapter 10 Part 3).
     */
    private static float angularSeparation(Vec3 cameraPos, Vec3 a, Vec3 b) {
        Vec3 toA = a.subtract(cameraPos);
        Vec3 toB = b.subtract(cameraPos);
        double lenA = toA.length();
        double lenB = toB.length();
        if (lenA < 1.0e-4 || lenB < 1.0e-4) return 0f;

        double cosAngle = toA.dot(toB) / (lenA * lenB);
        cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
        return (float) Math.acos(cosAngle);
    }
}