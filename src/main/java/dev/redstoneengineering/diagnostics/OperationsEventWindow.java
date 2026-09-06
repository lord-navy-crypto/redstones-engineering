package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.events.FirstOutAnalysis;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Bounded player-facing event strip projected from the server-authoritative plant timeline.
 *
 * <p>This class owns no process or alarm state. It only narrows the existing operations dashboard
 * scope to the most recent evidence that can be rendered legibly in the Operations Monitor UI.</p>
 */
public record OperationsEventWindow(
        List<SystemEventRecord> events,
        Optional<FirstOutAnalysis.Snapshot> firstOut
) {
    public static final int MAX_VISIBLE_EVENTS = 8;

    public OperationsEventWindow {
        events = List.copyOf(events);
        firstOut = firstOut == null ? Optional.empty() : firstOut;
    }

    public static OperationsEventWindow inspect(Level level, OperationsDashboardSnapshot dashboard) {
        if (level == null || dashboard == null) return new OperationsEventWindow(List.of(), Optional.empty());
        return new OperationsEventWindow(
                SystemEventTimeline.recentWithin(level, dashboard.eventScope(), MAX_VISIBLE_EVENTS),
                dashboard.firstOut()
        );
    }

    /** Index inside the bounded oldest-to-newest visible strip, or -1 when first-out is outside the tail. */
    public int firstOutIndex() {
        if (firstOut.isEmpty()) return -1;
        SystemEventRecord target = firstOut.get().firstOut();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).equals(target)) return i;
        }
        return -1;
    }
}
