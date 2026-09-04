package dev.redstoneengineering.diagnostics.acceptance;

import java.util.Objects;

/** Read-only comparison between two explicitly captured acceptance records. */
public record AcceptanceEvidenceComparison(
        long baselineSequence,
        long candidateSequence,
        int scoreDelta,
        int topologyIssueDelta,
        AcceptanceEvidenceTrend trend
) {
    public AcceptanceEvidenceComparison {
        if (baselineSequence < 1 || candidateSequence < 1) {
            throw new IllegalArgumentException("record sequences must be >= 1");
        }
        Objects.requireNonNull(trend, "trend");
    }

    public static AcceptanceEvidenceComparison between(
            AcceptanceEvidenceRecord baseline,
            AcceptanceEvidenceRecord candidate
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");

        EngineeringAcceptanceSnapshot before = baseline.acceptance();
        EngineeringAcceptanceSnapshot after = candidate.acceptance();
        int scoreDelta = after.commissioningScore() - before.commissioningScore();
        int issueDelta = after.topologyIssues() - before.topologyIssues();

        AcceptanceEvidenceTrend trend;
        if (before.status() == EngineeringAcceptanceStatus.NOT_READY
                || after.status() == EngineeringAcceptanceStatus.NOT_READY) {
            trend = AcceptanceEvidenceTrend.INCOMPARABLE;
        } else if (issueDelta < 0) {
            trend = AcceptanceEvidenceTrend.IMPROVED;
        } else if (issueDelta > 0) {
            trend = AcceptanceEvidenceTrend.REGRESSED;
        } else {
            int severityDelta = severity(after.status()) - severity(before.status());
            if (severityDelta < 0) trend = AcceptanceEvidenceTrend.IMPROVED;
            else if (severityDelta > 0) trend = AcceptanceEvidenceTrend.REGRESSED;
            else if (scoreDelta > 0) trend = AcceptanceEvidenceTrend.IMPROVED;
            else if (scoreDelta < 0) trend = AcceptanceEvidenceTrend.REGRESSED;
            else trend = AcceptanceEvidenceTrend.SAME;
        }

        return new AcceptanceEvidenceComparison(
                baseline.sequence(), candidate.sequence(), scoreDelta, issueDelta, trend);
    }

    public String compact() {
        return "#" + baselineSequence + "→#" + candidateSequence
                + " " + trend
                + " Δscore=" + signed(scoreDelta)
                + " Δissues=" + signed(topologyIssueDelta);
    }

    private static int severity(EngineeringAcceptanceStatus status) {
        return switch (status) {
            case PASS -> 0;
            case MARGINAL -> 1;
            case FAIL -> 2;
            case NOT_READY -> 3;
        };
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
