package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

import java.util.Set;

/** Immutable evidence describing one work resource available to dispatch. */
public record OperationResourceSnapshot(
        String resourceId,
        Set<String> processIds,
        State state,
        PortQuality evidenceQuality
) {
    public enum State {
        AVAILABLE,
        BUSY,
        BLOCKED,
        FAULTED,
        UNAVAILABLE
    }

    public OperationResourceSnapshot {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        if (processIds == null || processIds.isEmpty()) {
            throw new IllegalArgumentException("processIds must not be empty");
        }
        processIds = processIds.stream()
                .map(id -> {
                    if (id == null || id.isBlank()) {
                        throw new IllegalArgumentException("process id must be non-blank");
                    }
                    return id.trim();
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (state == null) state = State.UNAVAILABLE;
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
    }

    public boolean supports(String processId) {
        return processId != null && processIds.contains(processId.trim());
    }

    public boolean dispatchable() {
        return evidenceQuality == PortQuality.VALID && state == State.AVAILABLE;
    }
}
