package net.atmos.developer;

import net.atmos.atmosphere.EnvironmentalState;
import net.atmos.cellgrid.AtmosCell;
import net.atmos.cellgrid.CellGrid;
import net.atmos.core.AtmosClient;
import net.atmos.exposure.ExposureStateManager;
import net.atmos.exposure.ExposureStateSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class DvfDataAccess {

    private static final List<CellDebugView> CELL_VIEWS = new ArrayList<>(512);
    private static final List<CellDebugView> UNMODIFIABLE_CELL_VIEWS = Collections.unmodifiableList(CELL_VIEWS);
    private static long snapshotFrame = -1L;

    private DvfDataAccess() {}

    public static EnvironmentalState getEnvironmentalState() {
        return AtmosClient.getFogManager().getEnvState();
    }

    public static CellGrid getCellGrid() {
        return AtmosClient.getCellGrid();
    }

    public static float getEnvHumidityMass() {
        return getEnvironmentalState().getHumidityMass();
    }

    public static float getEnvThermalEnergy() {
        return getEnvironmentalState().getThermalEnergy();
    }

    public static float getEnvStormEnergy() {
        return getEnvironmentalState().getStormEnergy();
    }

    public static float getEnvNightDepth() {
        return getEnvironmentalState().getNightDepth();
    }

    public static long getCurrentCellTick() {
        return getCellGrid().currentTick();
    }

    public static int getPendingMemoryWrites() {
        return getCellGrid().memoryDiagnostics().pendingWrites();
    }

    public static ExposureStateSnapshot getExposureSnapshot() {
        return ExposureStateManager.get();
    }

    public static void ensureSnapshot(long frameId) {
        if (snapshotFrame == frameId) {
            return;
        }
        snapshotFrame = frameId;
        refreshSnapshot();
    }

    private static void refreshSnapshot() {
        CELL_VIEWS.clear();
        for (AtmosCell cell : getCellGrid().getActiveCells()) {
            CELL_VIEWS.add(new CellDebugView(
                    cell.coord().centerWorldX(CellGrid.CELL_SIZE),
                    cell.coord().centerWorldY(CellGrid.CELL_SIZE),
                    cell.coord().centerWorldZ(CellGrid.CELL_SIZE),
                    cell.humidityMemory(),
                    cell.skyExposed()
            ));
        }
    }

    public static Collection<AtmosCell> getActiveCells() {
        return getCellGrid().getActiveCells();
    }

    public static List<CellDebugView> getActiveCellViews() {
        return UNMODIFIABLE_CELL_VIEWS;
    }
}