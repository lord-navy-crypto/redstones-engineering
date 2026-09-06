package dev.redstoneengineering.diagnostics.lifecycle;

import java.util.List;

/**
 * Machine-readable audit of persistence semantics for systems-level RSE state.
 *
 * <p>This class documents current behavior; it does not itself serialize or mutate runtime state.
 * Safety-sensitive runtime such as a live sequence step or PID integrator is not automatically made
 * durable merely because a durable store is technically possible.</p>
 */
public final class RuntimePersistenceContract {
    public enum StorageClass {
        BLOCK_STATE_DURABLE,
        LEVEL_RUNTIME_TRANSIENT,
        BLOCK_RUNTIME_TRANSIENT
    }

    public enum FutureDirection {
        KEEP_CURRENT,
        CONSIDER_DURABLE_RECORDER,
        SPLIT_ROLLING_AND_LIFETIME_METRICS
    }

    public record Entry(
            String owner,
            String state,
            StorageClass currentStorage,
            boolean survivesChunkUnloadInSession,
            boolean survivesServerRestart,
            boolean clearedOnBlockRemoval,
            FutureDirection futureDirection,
            String rationale
    ) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry(
                    "SequenceController",
                    "current step / edge memory / transition counters",
                    StorageClass.BLOCK_RUNTIME_TRANSIENT,
                    true, false, true,
                    FutureDirection.KEEP_CURRENT,
                    "Fail-to-idle after restart is safer than silently resuming an actuator sequence."
            ),
            new Entry(
                    "AlarmProcessor",
                    "latched / acknowledged / first-out severity / counters",
                    StorageClass.BLOCK_RUNTIME_TRANSIENT,
                    true, false, true,
                    FutureDirection.CONSIDER_DURABLE_RECORDER,
                    "Live alarm logic is transient today; durable incident evidence should be added explicitly rather than hidden in BlockState."
            ),
            new Entry(
                    "OperationsMonitor",
                    "rolling throughput / queue / downtime / constraint counters",
                    StorageClass.BLOCK_RUNTIME_TRANSIENT,
                    true, false, true,
                    FutureDirection.SPLIT_ROLLING_AND_LIFETIME_METRICS,
                    "Rolling windows may reset safely; future lifetime production records need separate durable semantics."
            ),
            new Entry(
                    "FaultInjector",
                    "configured fault mode",
                    StorageClass.BLOCK_STATE_DURABLE,
                    true, true, true,
                    FutureDirection.KEEP_CURRENT,
                    "Small bounded player configuration belongs in BlockState and already survives normal saves."
            ),
            new Entry(
                    "FaultInjector",
                    "armed/activation/sample diagnostic runtime",
                    StorageClass.BLOCK_RUNTIME_TRANSIENT,
                    true, false, true,
                    FutureDirection.KEEP_CURRENT,
                    "Derived reliability-test runtime must not become durable machine configuration."
            ),
            new Entry(
                    "SystemEventTimeline",
                    "bounded event evidence",
                    StorageClass.LEVEL_RUNTIME_TRANSIENT,
                    true, false, false,
                    FutureDirection.CONSIDER_DURABLE_RECORDER,
                    "Historical evidence intentionally remains after a source block is removed, but currently disappears on server restart."
            ),
            new Entry(
                    "AcceptanceEvidenceStore",
                    "explicit PID commissioning captures",
                    StorageClass.LEVEL_RUNTIME_TRANSIENT,
                    true, false, true,
                    FutureDirection.CONSIDER_DURABLE_RECORDER,
                    "Explicitly captured engineering evidence is a strong candidate for a future dedicated recorder or bounded SavedData store."
            ),
            new Entry(
                    "PidController",
                    "tuning preset",
                    StorageClass.BLOCK_STATE_DURABLE,
                    true, true, true,
                    FutureDirection.KEEP_CURRENT,
                    "Small bounded operator configuration is already represented durably without high-cardinality state."
            ),
            new Entry(
                    "PidController",
                    "integral/derivative/mode-transfer/step-response runtime",
                    StorageClass.BLOCK_RUNTIME_TRANSIENT,
                    true, false, true,
                    FutureDirection.KEEP_CURRENT,
                    "Controller dynamic state should reinitialize after a restart rather than resuming with stale hidden energy."
            )
    );

    private RuntimePersistenceContract() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry find(String owner, String stateContains) {
        return ENTRIES.stream()
                .filter(entry -> entry.owner().equals(owner) && entry.state().contains(stateContains))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No persistence contract for " + owner + " / " + stateContains));
    }
}
