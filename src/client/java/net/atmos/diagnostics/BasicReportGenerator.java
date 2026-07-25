package net.atmos.diagnostics;

/**
 * Factual report generator rendering the End-to-End Data Flow Funnel.
 * Avoids all speculation and recommendations.
 */
@SuppressWarnings("unused")
public final class BasicReportGenerator {

    private BasicReportGenerator() {}

    public static String generate(RingBuffer<FrameSnapshot> history, DiagnosticContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ATMOS DIAGNOSTIC REPORT ===\n\n");

        int count = history.getCount();
        if (count == 0) {
            sb.append("No frames captured.\n");
            return sb.toString();
        }

        FrameSnapshot[] snapshots = new FrameSnapshot[count];
        history.snapshot(snapshots);
        FrameSnapshot latest = snapshots[count - 1];

        String biomeStr = latest.biome() != null && latest.biome().unwrapKey().isPresent()
                ? latest.biome().unwrapKey().get().location().toString() : "unknown";

        sb.append("1. Session Metadata\n");
        sb.append("Mode: ").append(ctx.mode()).append("\n");
        sb.append("Frame: ").append(latest.frameNumber()).append("\n");
        sb.append("Biome: ").append(biomeStr).append("\n\n");

        sb.append("2. End-to-End Data Flow Audit (The Funnel)\n");
        int[] e = latest.pipelineEvents(); // Fixed: changed from eventCounts() to pipelineEvents()
        sb.append("  [Cluster Builder]\n");
        sb.append("    Generated: ").append(e[DiagnosticEvent.CLUSTER_GENERATED.ordinal()]).append("\n\n");

        sb.append("  [Composition Engine]\n");
        sb.append("    Accepted:  ").append(e[DiagnosticEvent.COMPOSITION_ACCEPTED.ordinal()]).append("\n");
        sb.append("    Rejected:  ").append(e[DiagnosticEvent.COMPOSITION_REJECTED.ordinal()]).append("\n\n");

        sb.append("  [RenderCluster Construction]\n");
        sb.append("    Succeeded: ").append(e[DiagnosticEvent.RENDER_CLUSTER_ACCEPTED.ordinal()]).append("\n");
        sb.append("    Failed:    ").append(e[DiagnosticEvent.RENDER_CLUSTER_REJECTED.ordinal()]).append("\n\n");

        sb.append("  [Geometry Generation]\n");
        sb.append("    Quads:     ").append(e[DiagnosticEvent.QUAD_GENERATED.ordinal()]).append("\n");
        sb.append("    Vertices:  ").append(e[DiagnosticEvent.VERTEX_SUBMITTED.ordinal()]).append("\n\n");

        sb.append("3. Rejection Reasons (Production Audit)\n");
        RejectionReason[] reasons = RejectionReason.values();
        for (int i = 0; i < latest.rejectionReasons().length; i++) {
            if (latest.rejectionReasons()[i] > 0) {
                sb.append(String.format(" - %-30s : %d\n", reasons[i].name(), latest.rejectionReasons()[i]));
            }
        }
        sb.append("\n");

        sb.append("4. Pipeline Stage Timings\n");
        long totalTime = 0;
        PipelineStage[] stages = PipelineStage.values();
        for (int i = 0; i < latest.stageTimingsNs().length; i++) {
            long t = latest.stageTimingsNs()[i];
            totalTime += t;
            String name = (i < stages.length) ? stages[i].name() : "STAGE_" + i;
            sb.append(String.format(" - %-30s : %8d ns\n", name, t));
        }
        sb.append(String.format("Total Pipeline Time: %d ns (%.2f ms)\n\n", totalTime, totalTime / 1_000_000.0));

        sb.append("5. Validation Checklist\n");
        int anomalies = sum(latest.anomalyCounts());
        int warnings = sum(latest.warningCounts());

        sb.append(anomalies > 0 ? "[FAILED] " : "[PASS]   ").append("Pipeline Integrity\n");
        sb.append(warnings > 0  ? "[WARN]   " : "[PASS]   ").append("Performance Thresholds\n");
        sb.append("Last Anomaly: ").append(latest.lastAnomaly() != null ? latest.lastAnomaly().name() : "None").append("\n\n");

        sb.append("6. Conclusion\n");
        sb.append("Data represents empirical pipeline metrics at the time of snapshot.\n");

        return sb.toString();
    }

    private static int sum(int[] arr) {
        int total = 0;
        for (int i : arr) total += i;
        return total;
    }
}