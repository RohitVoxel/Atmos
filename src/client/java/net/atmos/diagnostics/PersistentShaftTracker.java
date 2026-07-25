package net.atmos.diagnostics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks the lifetime and stability of atmospheric clusters across frames.
 * Uses strict spatial proximity matching to guarantee stable persistent IDs.
 */
@SuppressWarnings("unused")
public final class PersistentShaftTracker {
    private static final Map<String, ShaftRecord> activeShafts = new HashMap<>();
    private static final Map<String, ShaftLifetimeLog> destroyedShafts = new HashMap<>();
    private static long idCounter = 1000;

    private static FrameDiffLog lastDiff = new FrameDiffLog(0, 0, 0, false, false, false, false, false, false, false, false, false, false, false);

    private PersistentShaftTracker() {}

    public static synchronized void trackFrame(long frame, FullDiagnosticContext ctx) {
        int created = 0, removed = 0, persisted = 0;
        boolean alphaChg = false, colorChg = false, widthChg = false, lenChg = false, expChg = false, confChg = false, lodChg = false;

        Map<String, String> matchedIdsThisFrame = new HashMap<>();

        // 1. Spatial Matching (Survives anchor changes)
        for (FullDiagnosticContext.CandidateLog c : ctx.candidates) {
            ShaftRecord bestMatch = null;
            double bestDistSq = 16.0;

            for (ShaftRecord rec : activeShafts.values()) {
                if (matchedIdsThisFrame.containsValue(rec.persistentId)) continue;
                double distSq = getDistSq(c.x(), c.y(), c.z(), rec.lastX, rec.lastY, rec.lastZ);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestMatch = rec;
                }
            }

            if (bestMatch == null) {
                bestMatch = new ShaftRecord("Shaft-" + (++idCounter), frame);
                activeShafts.put(bestMatch.persistentId, bestMatch);
                created++;
            } else {
                persisted++;
            }

            ctx.persistentIdMap.put(c.id(), bestMatch.persistentId);
            matchedIdsThisFrame.put(c.id(), bestMatch.persistentId);
            bestMatch.visibleFrames++;
            bestMatch.lastRawId = c.id();
            bestMatch.lastX = c.x(); bestMatch.lastY = c.y(); bestMatch.lastZ = c.z();

            FullDiagnosticContext.RenderClusterLog rcc = ctx.rccLogs.get(c.id());
            if (rcc != null) {
                if (bestMatch.lastAlpha != -1f && Math.abs(rcc.alpha() - bestMatch.lastAlpha) > 0.01f) alphaChg = true;
                if (bestMatch.lastWidth != -1f && Math.abs(rcc.width() - bestMatch.lastWidth) > 0.01f) widthChg = true;
                if (bestMatch.lastExp != -1f && Math.abs(rcc.exposure() - bestMatch.lastExp) > 0.01f) expChg = true;
                if (bestMatch.lastLod != -1 && rcc.lod() != bestMatch.lastLod) lodChg = true;

                bestMatch.lastAlpha = rcc.alpha(); bestMatch.lastWidth = rcc.width();
                bestMatch.lastExp = rcc.exposure(); bestMatch.lastLod = rcc.lod();
            }

            FullDiagnosticContext.ConfidenceLog conf = ctx.confidenceLogs.get(c.id());
            if (conf != null) {
                if (bestMatch.lastConf != -1f && Math.abs(conf.finalConf() - bestMatch.lastConf) > 0.01f) confChg = true;
                bestMatch.lastConf = conf.finalConf();
            }
        }

        Iterator<Map.Entry<String, ShaftRecord>> it = activeShafts.entrySet().iterator();
        while (it.hasNext()) {
            ShaftRecord r = it.next().getValue();
            if (!matchedIdsThisFrame.containsValue(r.persistentId)) {
                String reason = determineDestructionReason(r.lastRawId, ctx);
                destroyedShafts.put(r.persistentId, new ShaftLifetimeLog(r.persistentId, r.createdFrame, frame - r.createdFrame, r.visibleFrames, r.rejectedFrames, r.culledFrames, frame, reason));
                removed++;
                it.remove();
            }
        }

        boolean dirChg = ctx.directorLog != null && !ctx.directorLog.prevTarget().equals(ctx.directorLog.currTarget());
        lastDiff = new FrameDiffLog(created, removed, persisted, alphaChg, colorChg, widthChg, lenChg, expChg, confChg, lodChg, dirChg, false, false, false);
    }

    private static double getDistSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        return (x1-x2)*(x1-x2) + (y1-y2)*(y1-y2) + (z1-z2)*(z1-z2);
    }

    private static String determineDestructionReason(String rawId, FullDiagnosticContext ctx) {
        if (ctx.compositionLogs.containsKey(rawId) && "Rejected".equals(ctx.compositionLogs.get(rawId).status())) {
            return ctx.compositionLogs.get(rawId).exactReason();
        }
        if (ctx.rccLogs.containsKey(rawId) && ctx.rccLogs.get(rawId).failReason() != null) {
            return ctx.rccLogs.get(rawId).failReason();
        }
        if (ctx.rendererLog.cullReasons.containsKey(rawId)) {
            return "Culled: " + ctx.rendererLog.cullReasons.get(rawId);
        }
        return "Lost to topological shift or grid boundary";
    }

    public static FrameDiffLog getLastDiff() { return lastDiff; }
    public static Map<String, ShaftLifetimeLog> getDestroyed() { return destroyedShafts; }

    private static class ShaftRecord {
        String persistentId, lastRawId;
        long createdFrame, visibleFrames, rejectedFrames, culledFrames;
        double lastX, lastY, lastZ;
        float lastAlpha = -1f, lastWidth = -1f, lastExp = -1f, lastConf = -1f;
        int lastLod = -1;

        ShaftRecord(String id, long frame) { this.persistentId = id; this.createdFrame = frame; }
    }
    public record FrameDiffLog(int created, int removed, int persisted, boolean alphaChg, boolean colorChg, boolean widthChg, boolean lenChg, boolean expChg, boolean confChg, boolean lodChg, boolean dirChg, boolean compChg, boolean geomChg, boolean rendChg) {}
    public record ShaftLifetimeLog(String id, long createdFrame, long lifetime, long framesVisible, long framesRejected, long framesCulled, long destructionFrame, String exactReason) {}
}