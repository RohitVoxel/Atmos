package net.atmos.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data container for FULL diagnostic mode.
 * Captures granular per-frame telemetry for true root-cause analysis without placeholders.
 */
@SuppressWarnings("unused")
public final class FullDiagnosticContext {
    public long frameNumber, tickNumber;
    public float partialTick, worldTime, timeOfDay, rainIntensity, thunderIntensity;
    public float sunElevation, fogDensity, fogRed, fogGreen, fogBlue, exposure;
    public String biome, cameraDir;
    public double camX, camY, camZ;

    public final List<CandidateLog> candidates = new ArrayList<>();
    public final Map<String, SunReachLog> sunReachLogs = new HashMap<>();
    public final Map<String, ConfidenceLog> confidenceLogs = new HashMap<>();
    public final Map<String, CompositionLog> compositionLogs = new HashMap<>();
    public final Map<String, ExposureLog> exposureLogs = new HashMap<>();
    public final Map<String, RenderClusterLog> rccLogs = new HashMap<>();
    public final Map<String, GeometryLog> geometryLogs = new HashMap<>();

    public DirectorLog directorLog;
    public final RendererLog rendererLog = new RendererLog();

    // Maps raw candidate IDs (anchor coords) to spatially-stable Persistent IDs
    public final Map<String, String> persistentIdMap = new HashMap<>();

    public record CandidateLog(String id, double x, double y, double z, float width, float length, float density, long seed, String genReason) {}
    public record SunReachLog(String id, float sunVis, String hitBlock, double hitX, double hitY, double hitZ, float hitDist, float occlusionPct, float skyVis, float finalSunlight) {}
    public record ConfidenceLog(String id, float finalConf) {}
    public record CompositionLog(String id, String status, String exactReason) {}
    public record ExposureLog(String id, float targetExposure, float currentExposure, float adjustmentRate, String limitReason) {}
    public record DirectorLog(String prevTarget, String currTarget, String desiredTarget, float transSpeed, float transProgress, String changeReason, float weatherInf, float tierA, float mem) {}
    public record RenderClusterLog(String id, double x, double y, double z, float width, float length, float alpha, float r, float g, float b, float exposure, String texture, int lod, float fadeDist, float animPhase, float sunReach, String failReason) {}
    public record GeometryLog(String id, int quads, int vertices, String billboardOrient, double relX, double relY, double relZ, boolean skipped, String skipReason) {}

    public static class RendererLog {
        public int submitted, rendered, skipped, culled, totalVertices, drawCalls;
        public final Map<String, String> cullReasons = new HashMap<>();
    }
}