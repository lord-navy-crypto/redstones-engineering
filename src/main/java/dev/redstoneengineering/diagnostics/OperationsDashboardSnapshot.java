package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.events.FirstOutAnalysis;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Read-only plant-scoped operations-console projection over existing monitor and event evidence. */
public record OperationsDashboardSnapshot(
        IndustrialOperationsAssessment.Snapshot operations,
        SystemEventScope eventScope,
        int retainedEvents,
        int recentEvents,
        int recentAbnormalEvents,
        Optional<FirstOutAnalysis.Snapshot> firstOut
) {
    public static final long RECENT_WINDOW_TICKS = 1200L;

    public OperationsDashboardSnapshot {
        firstOut = firstOut == null ? Optional.empty() : firstOut;
    }

    /** Default operations view: 32-block-radius plant scope centered on the monitor. */
    public static OperationsDashboardSnapshot inspect(Level level, BlockPos operationsMonitorPos) {
        return inspect(level, operationsMonitorPos, SystemEventScope.around(operationsMonitorPos));
    }

    public static OperationsDashboardSnapshot inspect(
            Level level,
            BlockPos operationsMonitorPos,
            SystemEventScope scope
    ) {
        long now = level.getGameTime();
        List<SystemEventRecord> events = SystemEventTimeline.within(level, scope);
        int recent = 0;
        int abnormal = 0;
        for (int i = events.size() - 1; i >= 0; i--) {
            SystemEventRecord event = events.get(i);
            if (now - event.tick() > RECENT_WINDOW_TICKS) break;
            recent++;
            if (event.abnormal()) abnormal++;
        }
        return new OperationsDashboardSnapshot(
                IndustrialOperationsAssessment.inspect(level, operationsMonitorPos),
                scope,
                events.size(),
                recent,
                abnormal,
                FirstOutAnalysis.latestWithin(level, scope)
        );
    }

    public String compact() {
        return operations.compact()
                + " | plant radius=" + eventScope.radiusBlocks() + " blocks"
                + " | events recent/retained=" + recentEvents + "/" + retainedEvents
                + " | abnormalRecent=" + recentAbnormalEvents
                + " | " + firstOut.map(FirstOutAnalysis.Snapshot::compact).orElse("FIRST OUT: none");
    }
}
