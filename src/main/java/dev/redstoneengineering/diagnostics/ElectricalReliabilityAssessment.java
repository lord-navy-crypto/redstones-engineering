package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventRecord;
import dev.redstoneengineering.diagnostics.events.SystemEventScope;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Read-only reliability projection over authoritative electrical protection evidence.
 *
 * <p>This deliberately reports evidence metrics rather than MTBF/MTTR. The transient event timeline
 * does not yet provide durable operating exposure or maintenance semantics, so claiming those formal
 * reliability measures would overstate what the evidence can support.</p>
 */
public final class ElectricalReliabilityAssessment {
    private ElectricalReliabilityAssessment() {}

    public record Snapshot(
            int tripCount,
            int recoveryCount,
            int repeatTripCount,
            int activeTripCount,
            long electricalDowntimeTicks,
            long lastTripAgeTicks,
            long lastRecoveryDurationTicks
    ) {
        public boolean hasElectricalEvidence() { return tripCount > 0 || recoveryCount > 0; }
        public boolean protectionActive() { return activeTripCount > 0; }
    }

    public static Snapshot inspect(Level level, SystemEventScope scope) {
        if (level == null || scope == null) return new Snapshot(0, 0, 0, 0, 0L, -1L, -1L);

        Map<BlockPos, Long> activeTrips = new HashMap<>();
        Set<BlockPos> previouslyTripped = new HashSet<>();
        int trips = 0;
        int recoveries = 0;
        int repeats = 0;
        long completedDowntime = 0L;
        long lastTripTick = -1L;
        long lastRecoveryDuration = -1L;

        for (SystemEventRecord event : SystemEventTimeline.within(level, scope)) {
            if (event.kind() == SystemEventKind.ELECTRICAL_TRIP) {
                trips++;
                if (!previouslyTripped.add(event.source())) repeats++;
                activeTrips.putIfAbsent(event.source(), event.tick());
                lastTripTick = Math.max(lastTripTick, event.tick());
            } else if (event.kind() == SystemEventKind.ELECTRICAL_READY) {
                Long start = activeTrips.remove(event.source());
                if (start != null && event.tick() >= start) {
                    long duration = event.tick() - start;
                    completedDowntime += duration;
                    lastRecoveryDuration = duration;
                    recoveries++;
                }
            }
        }

        long now = level.getGameTime();
        long downtime = completedDowntime;
        for (long start : activeTrips.values()) downtime += Math.max(0L, now - start);
        long lastTripAge = lastTripTick < 0 ? -1L : Math.max(0L, now - lastTripTick);

        return new Snapshot(
                trips,
                recoveries,
                repeats,
                activeTrips.size(),
                downtime,
                lastTripAge,
                lastRecoveryDuration
        );
    }
}
