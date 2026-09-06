package dev.redstoneengineering.diagnostics.events;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bounded chronological evidence trace for the latest incident.
 *
 * <p>This is intentionally an evidence chain, not an automatic causal proof. The first-out event is
 * the earliest abnormal observation in the latest incident cluster; later events are ordered follow-up
 * observations suitable for operator diagnosis and future topology-aware root-cause enrichment.</p>
 */
public final class RootCauseEvidenceTrace {
    public static final int MAX_TRACE_ENTRIES = 12;

    private RootCauseEvidenceTrace() {}

    public record Entry(String role, SystemEventRecord event) {}

    public record Trace(List<Entry> entries) {
        public Trace {
            entries = List.copyOf(entries);
        }

        public String compact() {
            if (entries.isEmpty()) return "ROOT-CAUSE EVIDENCE: none";
            StringBuilder out = new StringBuilder("ROOT-CAUSE EVIDENCE: ");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) out.append(" -> ");
                Entry entry = entries.get(i);
                out.append(entry.role()).append('[').append(entry.event().code()).append(']');
            }
            return out.toString();
        }
    }

    public static Optional<Trace> latest(Level level) {
        Optional<FirstOutAnalysis.Snapshot> first = FirstOutAnalysis.latest(level);
        if (first.isEmpty()) return Optional.empty();
        FirstOutAnalysis.Snapshot incident = first.get();
        ArrayList<Entry> entries = new ArrayList<>();
        entries.add(new Entry("FIRST_OUT", incident.firstOut()));
        for (SystemEventRecord event : incident.downstreamObservations()) {
            if (entries.size() >= MAX_TRACE_ENTRIES) break;
            entries.add(new Entry("FOLLOW_UP", event));
        }
        return Optional.of(new Trace(entries));
    }
}
