package net.atmos.diagnostics;

/**
 * Formats the automated Root Cause Analysis trace chain per frame.
 */
@SuppressWarnings("unused")
public final class FullReportGenerator {

    private FullReportGenerator() {}

    public static String generateFull(FullDiagnosticContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("=================================================================\n");
        sb.append("                 ATMOS FULL DIAGNOSTIC REPORT                    \n");
        sb.append("=================================================================\n\n");

        sb.append("1. ENVIRONMENTAL STATE\n");
        sb.append(String.format("Frame: %d | Tick: %d | TimeOfDay: %.2f\n", ctx.frameNumber, ctx.tickNumber, ctx.timeOfDay));
        sb.append(String.format("Weather: Rain=%.2f, Thunder=%.2f\n", ctx.rainIntensity, ctx.thunderIntensity));
        sb.append(String.format("Camera: Pos=[%.1f, %.1f, %.1f] Dir=%s\n\n", ctx.camX, ctx.camY, ctx.camZ, ctx.cameraDir));

        if (ctx.airLog != null) {
            sb.append(String.format(
                    "Air Foundation: Active=%b Pressure=%.2f Density=%.2f Stability=%.2f Turbulence=%.2f Aerosol=%.2f\n\n",
                    ctx.airLog.active(), ctx.airLog.pressure(), ctx.airLog.density(),
                    ctx.airLog.stability(), ctx.airLog.turbulence(), ctx.airLog.aerosolDensity()));
        }

        if (ctx.seasonalLog != null) {
            sb.append(String.format(
                    "Season: %s -> %s (progress=%.2f strength=%.2f) Temp=%.2f Humid=%.2f Daylight=%.2f Wind=%.2f Weather=%.2f\n\n",
                    ctx.seasonalLog.season(), ctx.seasonalLog.nextSeason(),
                    ctx.seasonalLog.seasonProgress(), ctx.seasonalLog.seasonStrength(),
                    ctx.seasonalLog.temperatureInfluence(), ctx.seasonalLog.humidityInfluence(),
                    ctx.seasonalLog.daylightInfluence(), ctx.seasonalLog.windTendency(), ctx.seasonalLog.weatherTendency()));
        }

        sb.append("2. DIRECTOR STATE\n");
        if (ctx.directorLog != null) {
            sb.append(String.format("Phase: %s -> %s | Speed: %.2f | Reason: %s\n", ctx.directorLog.prevTarget(), ctx.directorLog.currTarget(), ctx.directorLog.transSpeed(), ctx.directorLog.changeReason()));
            sb.append(String.format("Influence: Weather Stable=%.2f, TierA=%.2f, Memory=%.2f\n", ctx.directorLog.weatherInf(), ctx.directorLog.tierA(), ctx.directorLog.mem()));
        }
        sb.append("\n");

        sb.append("3. ROOT CAUSE ANALYSIS (Cluster Trace)\n");
        for (FullDiagnosticContext.CandidateLog c : ctx.candidates) {
            String pId = ctx.persistentIdMap.getOrDefault(c.id(), c.id());
            sb.append("Cluster #").append(pId).append("\n");
            sb.append("  ↓ Generated (Reason: ").append(c.genReason()).append(")\n");

            FullDiagnosticContext.SunReachLog sr = ctx.sunReachLogs.get(c.id());
            if (sr != null) {
                sb.append(String.format("  ↓ SunReach: Vis=%.2f, Final=%.2f (Hit %s)\n", sr.sunVis(), sr.finalSunlight(), sr.hitBlock()));
            } else {
                sb.append("  ↓ SunReach: Not Evaluated\n");
            }

            FullDiagnosticContext.ConfidenceLog conf = ctx.confidenceLogs.get(c.id());
            if (conf != null) {
                sb.append(String.format("  ↓ Confidence: Final=%.2f\n", conf.finalConf()));
            } else {
                sb.append("  ↓ Confidence: Not Evaluated\n");
            }

            FullDiagnosticContext.CompositionLog comp = ctx.compositionLogs.get(c.id());
            if (comp != null) {
                if ("Rejected".equals(comp.status())) {
                    sb.append("  ↓ Composition: REJECTED (").append(comp.exactReason()).append(")\n\n");
                    continue;
                }
                sb.append("  ↓ Composition: Accepted (").append(comp.exactReason()).append(")\n");
            } else {
                sb.append("  ↓ Composition: Skipped (No Data)\n\n");
                continue;
            }

            FullDiagnosticContext.ExposureLog exp = ctx.exposureLogs.get(c.id());
            if (exp != null) sb.append(String.format("  ↓ Exposure: Target=%.2f, Current=%.2f\n", exp.targetExposure(), exp.currentExposure()));

            FullDiagnosticContext.RenderClusterLog rcc = ctx.rccLogs.get(c.id());
            if (rcc != null) {
                if (rcc.failReason() != null) {
                    sb.append("  ↓ RenderCluster: FAILED (").append(rcc.failReason()).append(")\n\n");
                    continue;
                }
                sb.append(String.format("  ↓ RenderCluster: Built (Alpha=%.2f, Width=%.2f, LOD=%d)\n", rcc.alpha(), rcc.width(), rcc.lod()));
            } else {
                sb.append("  ↓ RenderCluster: Skipped/Failed to Init\n\n");
                continue;
            }

            FullDiagnosticContext.GeometryLog geom = ctx.geometryLogs.get(c.id());
            if (geom != null) {
                if (geom.skipped()) {
                    sb.append("  ↓ Geometry: SKIPPED (").append(geom.skipReason()).append(")\n\n");
                    continue;
                }
                sb.append(String.format("  ↓ Geometry: Generated %d quads, %d vertices\n", geom.quads(), geom.vertices()));
            }

            if (ctx.rendererLog.cullReasons.containsKey(c.id())) {
                sb.append("  ↓ Renderer: CULLED (").append(ctx.rendererLog.cullReasons.get(c.id())).append(")\n\n");
            } else {
                sb.append("  ↓ Renderer: Submitted & Rendered\n\n");
            }
        }

        sb.append("4. RENDERER SUMMARY\n");
        sb.append(String.format("Submitted: %d | Rendered: %d | Culled: %d | Vertices: %d | DrawCalls: %d\n\n",
                ctx.rendererLog.submitted, ctx.rendererLog.rendered, ctx.rendererLog.culled, ctx.rendererLog.totalVertices, ctx.rendererLog.drawCalls));

        sb.append("5. TEMPORAL STABILITY (Frame Diff)\n");
        PersistentShaftTracker.FrameDiffLog diff = PersistentShaftTracker.getLastDiff();
        if (diff != null) {
            sb.append(String.format("Created: %d | Removed: %d | Persisted: %d\n", diff.created(), diff.removed(), diff.persisted()));
            sb.append(String.format("Instability Detected: Alpha=%b, Width=%b, Exp=%b, Conf=%b, LOD=%b, Dir=%b\n\n",
                    diff.alphaChg(), diff.widthChg(), diff.expChg(), diff.confChg(), diff.lodChg(), diff.dirChg()));
        }

        sb.append("=================================================================\n");
        return sb.toString();
    }
}