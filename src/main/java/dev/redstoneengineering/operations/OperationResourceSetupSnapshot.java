package dev.redstoneengineering.operations;

import dev.redstoneengineering.core.port.PortQuality;

/** Immutable setup/changeover evidence for one production resource. */
public record OperationResourceSetupSnapshot(
        String resourceId,
        String configuredProcessId,
        String targetProcessId,
        State state,
        PortQuality evidenceQuality,
        boolean faultActive
) {
    public enum State {
        READY,
        CHANGEOVER_IN_PROGRESS,
        UNCONFIGURED,
        FAULTED
    }

    public OperationResourceSetupSnapshot {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must be non-blank");
        }
        resourceId = resourceId.trim();
        configuredProcessId = normalizeOptional(configuredProcessId);
        targetProcessId = normalizeOptional(targetProcessId);
        if (state == null) state = State.UNCONFIGURED;
        if (evidenceQuality == null) evidenceQuality = PortQuality.NO_SIGNAL;
        if (state == State.READY && configuredProcessId == null) {
            throw new IllegalArgumentException("READY setup requires configuredProcessId");
        }
        if (state == State.CHANGEOVER_IN_PROGRESS && targetProcessId == null) {
            throw new IllegalArgumentException("changeover requires targetProcessId");
        }
    }

    public boolean readyFor(String processId) {
        return !faultActive
                && evidenceQuality == PortQuality.VALID
                && state == State.READY
                && configuredProcessId != null
                && processId != null
                && configuredProcessId.equals(processId.trim());
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
