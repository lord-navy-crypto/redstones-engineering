package dev.redstoneengineering.diagnostics.acceptance;

import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only acceptance evaluator joining topology evidence with closed-loop commissioning evidence.
 *
 * The evaluator never samples the world directly and never writes simulation state. Callers must
 * provide snapshots produced by the existing authoritative topology and commissioning contracts.
 */
public final class EngineeringAcceptance {
    public static final String TOPOLOGY_MISMATCH = "TOPOLOGY_MISMATCH";
    public static final String COMMISSIONING_NOT_READY = "COMMISSIONING_NOT_READY";
    public static final String COMMISSIONING_MARGINAL = "COMMISSIONING_MARGINAL";
    public static final String COMMISSIONING_FAIL = "COMMISSIONING_FAIL";

    private EngineeringAcceptance() {}

    public static EngineeringAcceptanceSnapshot evaluate(
            TopologyVisualizationSnapshot topology,
            CommissioningSnapshot commissioning
    ) {
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(commissioning, "commissioning");

        List<EngineeringAcceptanceIssue> issues = new ArrayList<>();
        if (topology.issueCount() > 0) {
            issues.add(new EngineeringAcceptanceIssue(
                    TOPOLOGY_MISMATCH,
                    topology.issueCount() + " domain/direction topology mismatch(es)"
            ));
        }

        CommissioningStatus commissioningStatus = commissioning.status();
        EngineeringAcceptanceStatus status;

        if (topology.issueCount() > 0) {
            status = EngineeringAcceptanceStatus.FAIL;
        } else {
            status = switch (commissioningStatus) {
                case PASS -> EngineeringAcceptanceStatus.PASS;
                case MARGINAL -> EngineeringAcceptanceStatus.MARGINAL;
                case FAIL -> EngineeringAcceptanceStatus.FAIL;
                case UNAVAILABLE, IDLE, RUNNING -> EngineeringAcceptanceStatus.NOT_READY;
            };
        }

        switch (commissioningStatus) {
            case MARGINAL -> issues.add(new EngineeringAcceptanceIssue(
                    COMMISSIONING_MARGINAL,
                    "closed-loop commissioning is marginal; score=" + commissioning.score()
            ));
            case FAIL -> issues.add(new EngineeringAcceptanceIssue(
                    COMMISSIONING_FAIL,
                    "closed-loop commissioning failed; score=" + commissioning.score()
            ));
            case UNAVAILABLE, IDLE, RUNNING -> issues.add(new EngineeringAcceptanceIssue(
                    COMMISSIONING_NOT_READY,
                    "closed-loop commissioning evidence is " + commissioningStatus
            ));
            case PASS -> { }
        }

        return new EngineeringAcceptanceSnapshot(
                status,
                topology.portCount(),
                topology.connectedCount(),
                topology.issueCount(),
                commissioningStatus,
                commissioning.score(),
                issues
        );
    }
}
