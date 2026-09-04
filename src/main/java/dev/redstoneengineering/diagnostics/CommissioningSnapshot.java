package dev.redstoneengineering.diagnostics;

/**
 * Read-only engineering snapshot of a closed-loop step response.
 * Values are diagnostics only: they never write controller or plant physics.
 */
public record CommissioningSnapshot(
        boolean available,
        int setpoint,
        int processValue,
        int controlOutput,
        int error,
        int rise90Ticks,
        int settlingTicks,
        int overshoot,
        int saturationEvents,
        int stepAgeTicks,
        boolean stepActive,
        boolean manualMode,
        boolean inhibited,
        int modeTransfers,
        int score,
        CommissioningStatus status
) {
    public CommissioningSnapshot {
        if (score < 0 || score > 100) throw new IllegalArgumentException("score must be 0..100");
    }

    public static CommissioningSnapshot unavailable() {
        return new CommissioningSnapshot(false, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                false, false, false, 0, 0, CommissioningStatus.UNAVAILABLE);
    }
}
