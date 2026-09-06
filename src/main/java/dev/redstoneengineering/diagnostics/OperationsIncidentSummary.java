package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.events.FirstOutAnalysis;
import dev.redstoneengineering.diagnostics.events.RootCauseEvidenceTrace;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Read-only operator-facing summary of the latest plant-scoped incident.
 *
 * <p>This projection does not infer new causality. It exposes the existing first-out incident and
 * bounded root-cause evidence trace in compact values suitable for synchronized UI readback.</p>
 */
public record OperationsIncidentSummary(
        boolean present,
        int firstOutDx,
        int firstOutDy,
        int firstOutDz,
        int downstreamObservations,
        int abnormalDownstreamObservations,
        int evidenceTraceEntries,
        long incidentDurationTicks
) {
    public static OperationsIncidentSummary none() {
        return new OperationsIncidentSummary(false, 0, 0, 0, 0, 0, 0, 0L);
    }

    public static OperationsIncidentSummary inspect(
            Level level,
            BlockPos operationsMonitorPos,
            OperationsDashboardSnapshot dashboard
    ) {
        if (level == null || operationsMonitorPos == null || dashboard == null || dashboard.firstOut().isEmpty()) {
            return none();
        }

        FirstOutAnalysis.Snapshot incident = dashboard.firstOut().get();
        SystemEventRecord first = incident.firstOut();
        if (first == null) return none();

        int abnormalDownstream = 0;
        for (SystemEventRecord event : incident.downstreamObservations()) {
            if (event.abnormal()) abnormalDownstream++;
        }

        int traceEntries = RootCauseEvidenceTrace.latestWithin(level, dashboard.eventScope())
                .map(trace -> trace.entries().size())
                .orElse(0);

        return new OperationsIncidentSummary(
                true,
                first.source().getX() - operationsMonitorPos.getX(),
                first.source().getY() - operationsMonitorPos.getY(),
                first.source().getZ() - operationsMonitorPos.getZ(),
                incident.downstreamObservations().size(),
                abnormalDownstream,
                traceEntries,
                Math.max(0L, incident.incidentEndTick() - incident.incidentStartTick())
        );
    }
}
