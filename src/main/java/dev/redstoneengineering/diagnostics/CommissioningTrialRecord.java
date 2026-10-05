package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;

import java.util.Objects;

/**
 * Immutable, explicitly captured system-level commissioning trial evidence.
 *
 * The record freezes already-authoritative controller/system commissioning and acceptance
 * projections. It never owns, replays, or mutates controller, plant, topology, or routing state.
 */
public record CommissioningTrialRecord(
        long sequence,
        long gameTick,
        int tuningPreset,
        CommissioningSnapshot commissioning,
        EngineeringAcceptanceSnapshot acceptance
) {
    public CommissioningTrialRecord {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be >= 1");
        if (gameTick < 0) throw new IllegalArgumentException("gameTick must be >= 0");
        if (tuningPreset < 0 || tuningPreset > 3) throw new IllegalArgumentException("tuningPreset must be 0..3");
        Objects.requireNonNull(commissioning, "commissioning");
        Objects.requireNonNull(acceptance, "acceptance");
    }

    public String compact() {
        return "#" + sequence
                + " " + acceptance.status()
                + " score=" + commissioning.score()
                + " settle=" + commissioning.settlingTicks()
                + " overshoot=" + commissioning.overshoot()
                + " sat=" + commissioning.saturationEvents()
                + " issues=" + acceptance.topologyIssues()
                + " tuning=" + tuningPreset;
    }
}
