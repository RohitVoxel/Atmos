package net.atmos.diagnostics;

/**
 * Integration boundary. Enforces zero-cost early returns in OFF mode.
 */
@SuppressWarnings("unused")
public final class DiagnosticHooks {
    private DiagnosticHooks() {}

    public static void beginStage(PipelineStage stage) {
        if (!DiagnosticManager.isActive()) return;
        DiagnosticManager.recordStageStart(stage);
    }

    public static void endStage(PipelineStage stage) {
        if (!DiagnosticManager.isActive()) return;
        DiagnosticManager.recordStageEnd(stage);
    }

    public static void recordEventCount(DiagnosticEvent event, int count) {
        if (!DiagnosticManager.isActive()) return;
        DiagnosticManager.incrementEvent(event, count);
    }

    // Restored anomaly hook
    public static void captureAnomaly(PipelineStage stage, AnomalyType type) {
        if (!DiagnosticManager.isActive() || DiagnosticManager.MODE == DiagnosticMode.LIGHT) return;
        DiagnosticManager.recordAnomaly(stage, type);
    }

    // --- FULL MODE HOOKS (Strictly Honest Signatures) ---

    public static void recordFullEnvState(long frame, long tick, float pTick, float wTime, float tod, float rain, float thunder, float sunEl, float fogD, float fR, float fG, float fB, float exp, String biome, double cx, double cy, double cz, String cDir) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        FullDiagnosticContext ctx = DiagnosticManager.getFullContext();
        ctx.frameNumber = frame; ctx.tickNumber = tick; ctx.partialTick = pTick; ctx.worldTime = wTime; ctx.timeOfDay = tod;
        ctx.rainIntensity = rain; ctx.thunderIntensity = thunder; ctx.sunElevation = sunEl;
        ctx.fogDensity = fogD; ctx.fogRed = fR; ctx.fogGreen = fG; ctx.fogBlue = fB; ctx.exposure = exp;
        ctx.biome = biome; ctx.camX = cx; ctx.camY = cy; ctx.camZ = cz; ctx.cameraDir = cDir;
    }

    public static void recordFullCandidate(String id, double x, double y, double z, float w, float l, float d, long seed, String genReason) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().candidates.add(new FullDiagnosticContext.CandidateLog(id, x, y, z, w, l, d, seed, genReason));
    }

    public static void recordFullSunReach(String id, float sunVis, String hitBlock, double hx, double hy, double hz, float hDist, float occPct, float skyVis, float finalSunlight) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().sunReachLogs.put(id, new FullDiagnosticContext.SunReachLog(id, sunVis, hitBlock, hx, hy, hz, hDist, occPct, skyVis, finalSunlight));
    }

    public static void recordFullConfidence(String id, float finalConf) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().confidenceLogs.put(id, new FullDiagnosticContext.ConfidenceLog(id, finalConf));
    }

    public static void recordFullComposition(String id, String status, String reason) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().compositionLogs.put(id, new FullDiagnosticContext.CompositionLog(id, status, reason));
    }

    public static void recordFullExposure(String id, float target, float current, float rate, String reason) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().exposureLogs.put(id, new FullDiagnosticContext.ExposureLog(id, target, current, rate, reason));
    }

    public static void recordFullDirector(String prev, String curr, String desired, float speed, float progress, String reason, float weather, float tierA, float mem) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().directorLog = new FullDiagnosticContext.DirectorLog(prev, curr, desired, speed, progress, reason, weather, tierA, mem);
    }

    public static void recordFullRenderCluster(String id, double x, double y, double z, float w, float l, float a, float r, float g, float b, float exp, String tex, int lod, float fade, float anim, float sunR, String fail) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().rccLogs.put(id, new FullDiagnosticContext.RenderClusterLog(id, x, y, z, w, l, a, r, g, b, exp, tex, lod, fade, anim, sunR, fail));
    }

    public static void recordFullGeometry(String id, int quads, int verts, String bill, double rX, double rY, double rZ, boolean skip, String skipReason) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().geometryLogs.put(id, new FullDiagnosticContext.GeometryLog(id, quads, verts, bill, rX, rY, rZ, skip, skipReason));
    }

    public static void recordFullRendererSubmission(int submitted, int rendered, int skipped, int verts, int draws) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        FullDiagnosticContext.RendererLog rl = DiagnosticManager.getFullContext().rendererLog;
        rl.submitted = submitted; rl.rendered = rendered; rl.skipped = skipped; rl.totalVertices = verts; rl.drawCalls = draws;
    }

    public static void recordFullRendererCull(String id, String reason) {
        if (DiagnosticManager.MODE != DiagnosticMode.FULL) return;
        DiagnosticManager.getFullContext().rendererLog.cullReasons.put(id, reason);
        DiagnosticManager.getFullContext().rendererLog.culled++;
    }
}