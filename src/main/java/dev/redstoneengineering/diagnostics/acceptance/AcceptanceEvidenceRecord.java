package dev.redstoneengineering.diagnostics.acceptance;

import java.util.Objects;

/**
 * Immutable, explicitly captured engineering acceptance evidence.
 *
 * A record freezes the already-authoritative acceptance snapshot plus capture metadata. It does
 * not own, replay, or mutate topology, controller, sampling, or machine physics.
 */
public record AcceptanceEvidenceRecord(
        long sequence,
        long gameTick,
        int tuningPreset,
        EngineeringAcceptanceSnapshot acceptance
) {
    public AcceptanceEvidenceRecord {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be >= 1");
        if (gameTick < 0) throw new IllegalArgumentException("gameTick must be >= 0");
        if (tuningPreset < 0 || tuningPreset > 3) throw new IllegalArgumentException("tuningPreset must be 0..3");
        Objects.requireNonNull(acceptance, "acceptance");
    }

    public String compact() {
        return "#" + sequence
                + " " + acceptance.status()
                + " score=" + acceptance.commissioningScore()
                + " issues=" + acceptance.topologyIssues()
                + " tuning=" + tuningPreset
                + " tick=" + gameTick;
    }
}
