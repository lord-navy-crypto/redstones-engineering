package dev.redstoneengineering.core.diagnostic;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure deterministic commissioning classification for Copper Circuit Meter evidence. */
public final class CopperCommissioningAssessment {
    private CopperCommissioningAssessment() {}

    public static CommissioningStatus assess(PortQuality quality, int voltage) {
        if (quality == null) return CommissioningStatus.NOT_READY;
        return switch (quality) {
            case VALID -> voltage > 0 ? CommissioningStatus.PASS : CommissioningStatus.MARGINAL;
            case SATURATED -> CommissioningStatus.MARGINAL;
            case NO_SIGNAL, STALE -> CommissioningStatus.NOT_READY;
            case FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> CommissioningStatus.FAIL;
        };
    }

    /** Severity is operational evidence severity, not a physical safety rating. */
    public static int eventSeverity(CommissioningStatus status) {
        return switch (status) {
            case PASS -> 0;
            case NOT_READY, MARGINAL -> 1;
            case FAIL -> 2;
        };
    }
}
