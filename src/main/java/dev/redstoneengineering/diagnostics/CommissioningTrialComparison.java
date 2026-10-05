package dev.redstoneengineering.diagnostics;

import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceTrend;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;

import java.util.Objects;

/**
 * Deterministic baseline-versus-candidate commissioning trial comparison.
 *
 * This is evidence interpretation only. It consumes frozen records and delegates the dynamic
 * robustness rule to the established ClosedLoopCommissioning comparison.
 */
public record CommissioningTrialComparison(
        long baselineSequence,
        long candidateSequence,
        int scoreDelta,
        int settlingDeltaTicks,
        int overshootDelta,
        int saturationDelta,
        int topologyIssueDelta,
        AcceptanceEvidenceTrend trend,
        boolean robust
) {
    public CommissioningTrialComparison {
        if (baselineSequence < 1 || candidateSequence < 1) {
            throw new IllegalArgumentException("record sequences must be >= 1");
        }
        Objects.requireNonNull(trend, "trend");
    }

    public static CommissioningTrialComparison between(
            CommissioningTrialRecord baseline,
            CommissioningTrialRecord candidate
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");

        int scoreDelta = candidate.commissioning().score() - baseline.commissioning().score();
        int settlingDelta = candidate.commissioning().settlingTicks() - baseline.commissioning().settlingTicks();
        int overshootDelta = candidate.commissioning().overshoot() - baseline.commissioning().overshoot();
        int saturationDelta = candidate.commissioning().saturationEvents() - baseline.commissioning().saturationEvents();
        int issueDelta = candidate.acceptance().topologyIssues() - baseline.acceptance().topologyIssues();

        AcceptanceEvidenceTrend trend;
        if (!baseline.acceptance().ready() || !candidate.acceptance().ready()) {
            trend = AcceptanceEvidenceTrend.INCOMPARABLE;
        } else if (issueDelta < 0) {
            trend = AcceptanceEvidenceTrend.IMPROVED;
        } else if (issueDelta > 0) {
            trend = AcceptanceEvidenceTrend.REGRESSED;
        } else {
            int severityDelta = severity(candidate.acceptance().status()) - severity(baseline.acceptance().status());
            if (severityDelta < 0) trend = AcceptanceEvidenceTrend.IMPROVED;
            else if (severityDelta > 0) trend = AcceptanceEvidenceTrend.REGRESSED;
            else if (scoreDelta > 0) trend = AcceptanceEvidenceTrend.IMPROVED;
            else if (scoreDelta < 0) trend = AcceptanceEvidenceTrend.REGRESSED;
            else if (settlingDelta < 0 || overshootDelta < 0 || saturationDelta < 0) trend = AcceptanceEvidenceTrend.IMPROVED;
            else if (settlingDelta > 0 || overshootDelta > 0 || saturationDelta > 0) trend = AcceptanceEvidenceTrend.REGRESSED;
            else trend = AcceptanceEvidenceTrend.SAME;
        }

        CommissioningComparison dynamic = ClosedLoopCommissioning.compare(
                baseline.commissioning(), candidate.commissioning());
        boolean robust = trend != AcceptanceEvidenceTrend.INCOMPARABLE
                && candidate.acceptance().status() != EngineeringAcceptanceStatus.FAIL
                && candidate.acceptance().status() != EngineeringAcceptanceStatus.NOT_READY
                && issueDelta <= 0
                && dynamic.robust();

        return new CommissioningTrialComparison(
                baseline.sequence(),
                candidate.sequence(),
                scoreDelta,
                settlingDelta,
                overshootDelta,
                saturationDelta,
                issueDelta,
                trend,
                robust
        );
    }

    private static int severity(EngineeringAcceptanceStatus status) {
        return switch (status) {
            case PASS -> 0;
            case MARGINAL -> 1;
            case FAIL -> 2;
            case NOT_READY -> 3;
        };
    }

    public String compact() {
        return "#" + baselineSequence + "→#" + candidateSequence
                + " " + trend
                + " robust=" + robust
                + " Δscore=" + signed(scoreDelta)
                + " Δsettle=" + signed(settlingDeltaTicks)
                + " Δovershoot=" + signed(overshootDelta)
                + " Δsat=" + signed(saturationDelta)
                + " Δissues=" + signed(topologyIssueDelta);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
