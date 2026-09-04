package dev.redstoneengineering.diagnostics;

/** Lifecycle/result state for a closed-loop commissioning observation. */
public enum CommissioningStatus {
    UNAVAILABLE,
    IDLE,
    RUNNING,
    PASS,
    MARGINAL,
    FAIL
}
