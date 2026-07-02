package net.atmos.cluster;

import net.atmos.cellgrid.CellCoord;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Immutable, deterministic spatial grouping of neighboring Atmos Cells whose
 * atmospheric conditions are sufficiently similar (Chapter 7 — Cluster Builder).
 *
 * Pipeline position:
 *
 *   Environmental State
 *         │
 *         ▼
 *   Confidence System (Tier A / Tier B, camera-independent)
 *         │
 *         ▼
 *   Cell Grid
 *         │
 *         ▼
 *   Cluster Builder  ◄── this record is the output
 *         │
 *         ▼
 *   (future) Composition Engine / Sun Reach / APS-ALSC
 *
 * What this record owns:
 *   - The set of member cell coordinates that were determined to belong
 *     together (Chapter 7 §6 "Output").
 *   - A deterministic spatial anchor identity (anchorCoord) — the
 *     lexicographically smallest member coordinate. Used as a stable
 *     cluster identity across rebuilds when membership is unchanged,
 *     without inventing a separate ID-allocation system.
 *   - Purely geometric/statistical summary data (center, radius, cell
 *     count, average/max atmospheric value) derived mechanically from
 *     its members.
 *
 * What this record deliberately does NOT own (left for future chapters):
 *   - Hero score / composition role            → Composition Engine (Ch. 10)
 *   - Sun Reach values                         → Sun Reach System (Ch. 8)
 *   - Render packets / geometry                → ALSS Renderer (Ch. 9)
 *   - Performance/LOD metadata                 → APS / ALSC (Ch. 16)
 *
 * No placeholder fields exist for those future systems — per the Aetheris
 * "no placeholder logic" rule, they will be added only when the chapters
 * that own them are implemented. Because Cluster is a record, adding
 * fields later is a mechanical, additive change that will not require
 * touching ClusterBuilder's clustering algorithm.
 *
 * Immutability: memberCoords is defensively copied to an unmodifiable
 * List in the compact constructor. No consumer — present or future — can
 * mutate a Cluster after construction (Chapter 7 §"Immutability").
 */
public record Cluster(
        CellCoord anchorCoord,
        List<CellCoord> memberCoords,
        Vec3 centerWorldPos,
        float radius,
        int cellCount,
        float averageAtmosphericValue,
        float maxAtmosphericValue
) {
    public Cluster {
        memberCoords = List.copyOf(memberCoords);
        if (memberCoords.isEmpty()) {
            throw new IllegalArgumentException("Cluster must contain at least one member cell");
        }
        if (cellCount != memberCoords.size()) {
            throw new IllegalArgumentException(
                    "cellCount (" + cellCount + ") must match memberCoords.size() ("
                            + memberCoords.size() + ")");
        }
    }
}