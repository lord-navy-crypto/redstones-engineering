package dev.redstoneengineering.diagnostics.acceptance;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Small bounded timeline of explicitly captured acceptance evidence.
 *
 * The timeline is intentionally independent of Minecraft world state so its retention and
 * comparison semantics are deterministic and directly testable.
 */
public final class AcceptanceEvidenceTimeline {
    public static final int DEFAULT_CAPACITY = 8;
    private static final int MAX_CAPACITY = 32;

    private final int capacity;
    private final ArrayDeque<AcceptanceEvidenceRecord> records = new ArrayDeque<>();
    private long nextSequence = 1;

    public AcceptanceEvidenceTimeline() {
        this(DEFAULT_CAPACITY);
    }

    public AcceptanceEvidenceTimeline(int capacity) {
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be 1.." + MAX_CAPACITY);
        }
        this.capacity = capacity;
    }

    public AcceptanceEvidenceRecord append(
            long gameTick,
            int tuningPreset,
            EngineeringAcceptanceSnapshot acceptance
    ) {
        Objects.requireNonNull(acceptance, "acceptance");
        AcceptanceEvidenceRecord record = new AcceptanceEvidenceRecord(
                nextSequence++, gameTick, tuningPreset, acceptance);
        records.addLast(record);
        while (records.size() > capacity) records.removeFirst();
        return record;
    }

    public List<AcceptanceEvidenceRecord> records() {
        return List.copyOf(records);
    }

    public Optional<AcceptanceEvidenceRecord> latest() {
        return Optional.ofNullable(records.peekLast());
    }

    public Optional<AcceptanceEvidenceRecord> previous() {
        if (records.size() < 2) return Optional.empty();
        Iterator<AcceptanceEvidenceRecord> descending = records.descendingIterator();
        descending.next();
        return Optional.of(descending.next());
    }

    public Optional<AcceptanceEvidenceComparison> compareLatestToPrevious() {
        Optional<AcceptanceEvidenceRecord> latest = latest();
        Optional<AcceptanceEvidenceRecord> previous = previous();
        if (latest.isEmpty() || previous.isEmpty()) return Optional.empty();
        return Optional.of(AcceptanceEvidenceComparison.between(previous.get(), latest.get()));
    }

    public int size() {
        return records.size();
    }

    public int capacity() {
        return capacity;
    }
}
