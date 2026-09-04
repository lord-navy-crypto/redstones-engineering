package dev.redstoneengineering.diagnostics.acceptance;

import java.util.Objects;

/**
 * Compact read-only presentation of an acceptance snapshot for HUDs, logs, and tests.
 * Formatting never re-evaluates topology, commissioning, or simulation state.
 */
public final class EngineeringAcceptancePresentation {
    private EngineeringAcceptancePresentation() {}

    public static String headline(EngineeringAcceptanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return "Acceptance: " + snapshot.status()
                + " | commissioning=" + snapshot.commissioningStatus()
                + " " + snapshot.commissioningScore() + "/100";
    }

    public static String firstIssueLine(EngineeringAcceptanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.issues().isEmpty()) return "";
        EngineeringAcceptanceIssue issue = snapshot.issues().get(0);
        return "Evidence: " + issue.code()
                + (issue.detail().isBlank() ? "" : " - " + issue.detail());
    }
}
