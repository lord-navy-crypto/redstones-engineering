package dev.redstoneengineering.diagnostics.events;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * First-out analysis over the bounded system timeline.
 *
 * <p>"First out" means the earliest recorded abnormal event in the latest bounded incident cluster.
 * Later entries are downstream observations in time, not a claim of proven physical causality.</p>
 */
public final class FirstOutAnalysis {
    public static final long INCIDENT_GAP_TICKS = 200L;
    public static final int MAX_DOWNSTREAM_OBSERVATIONS = 16;

    private FirstOutAnalysis() {}

    public record Snapshot(
            SystemEventRecord firstOut,
            List<SystemEventRecord> downstreamObservations,
            long incidentStartTick,
            long incidentEndTick
    ) {
        public Snapshot {
            downstreamObservations = List.copyOf(downstreamObservations);
        }

        public String compact() {
            if (firstOut == null) return "FIRST OUT: none";
            return "FIRST OUT: " + firstOut.code() + " @" + firstOut.source().toShortString()
                    + " | kind=" + firstOut.kind()
                    + " | downstream observations=" + downstreamObservations.size();
        }
    }

    public static Optional<Snapshot> latest(Level level) {
        List<SystemEventRecord> events = SystemEventTimeline.snapshot(level);
        if (events.isEmpty()) return Optional.empty();

        int lastAbnormal = -1;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).abnormal()) {
                lastAbnormal = i;
                break;
            }
        }
        if (lastAbnormal < 0) return Optional.empty();

        int firstAbnormal = lastAbnormal;
        long nextAbnormalTick = events.get(lastAbnormal).tick();
        for (int i = lastAbnormal - 1; i >= 0; i--) {
            SystemEventRecord candidate = events.get(i);
            if (!candidate.abnormal()) continue;
            if (nextAbnormalTick - candidate.tick() > INCIDENT_GAP_TICKS) break;
            firstAbnormal = i;
            nextAbnormalTick = candidate.tick();
        }

        SystemEventRecord firstOut = events.get(firstAbnormal);
        long incidentEnd = events.get(lastAbnormal).tick();
        ArrayList<SystemEventRecord> downstream = new ArrayList<>();
        for (int i = firstAbnormal + 1; i < events.size() && downstream.size() < MAX_DOWNSTREAM_OBSERVATIONS; i++) {
            SystemEventRecord event = events.get(i);
            if (event.tick() > incidentEnd + INCIDENT_GAP_TICKS) break;
            downstream.add(event);
        }
        return Optional.of(new Snapshot(firstOut, downstream, firstOut.tick(), incidentEnd));
    }
}
