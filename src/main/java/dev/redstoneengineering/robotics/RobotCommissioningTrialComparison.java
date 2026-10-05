package dev.redstoneengineering.robotics;

import java.util.Objects;

/** Deterministic comparison of two frozen mission runs from the same AMR. */
public record RobotCommissioningTrialComparison(
        long baselineSequence,
        long candidateSequence,
        Trend trend,
        boolean comparable,
        long durationDeltaTicks,
        long stationaryDeltaTicks,
        int obstacleWaitDelta,
        int degradedDelta,
        int safeStopDelta,
        int faultDelta,
        int routeRejectDelta,
        int dockHoldDelta,
        int materialHoldDelta,
        int waypointDelta
) {
    public enum Trend {
        IMPROVED,
        SAME,
        REGRESSED,
        INCOMPARABLE
    }

    public RobotCommissioningTrialComparison {
        if (baselineSequence < 1 || candidateSequence < 1) {
            throw new IllegalArgumentException("record sequences must be >= 1");
        }
        Objects.requireNonNull(trend, "trend");
        if (!comparable && trend != Trend.INCOMPARABLE) {
            throw new IllegalArgumentException("non-comparable trials must use INCOMPARABLE trend");
        }
    }

    public static RobotCommissioningTrialComparison between(
            RobotCommissioningTrialRecord baseline,
            RobotCommissioningTrialRecord candidate
    ) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");
        if (!baseline.robotId().equals(candidate.robotId())) {
            throw new IllegalArgumentException("baseline and candidate must belong to the same robot");
        }

        RobotMissionTelemetrySnapshot before = baseline.telemetry();
        RobotMissionTelemetrySnapshot after = candidate.telemetry();
        boolean comparable = before.comparablePath(after);

        long durationDelta = after.durationTicks() - before.durationTicks();
        long stationaryDelta = after.stationaryTicks() - before.stationaryTicks();
        int obstacleDelta = after.obstacleWaitEvents() - before.obstacleWaitEvents();
        int degradedDelta = after.degradedEntries() - before.degradedEntries();
        int safeStopDelta = after.safeStopEvents() - before.safeStopEvents();
        int faultDelta = after.faultEvents() - before.faultEvents();
        int routeRejectDelta = after.routeRejectEvents() - before.routeRejectEvents();
        int dockHoldDelta = after.dockHoldEvents() - before.dockHoldEvents();
        int materialHoldDelta = after.materialHoldEvents() - before.materialHoldEvents();
        int waypointDelta = after.maxRouteWaypoints() - before.maxRouteWaypoints();

        Trend trend = comparable
                ? classify(before, after, durationDelta, stationaryDelta, obstacleDelta, degradedDelta,
                        safeStopDelta, faultDelta, routeRejectDelta, dockHoldDelta, materialHoldDelta)
                : Trend.INCOMPARABLE;

        return new RobotCommissioningTrialComparison(
                baseline.sequence(), candidate.sequence(), trend, comparable,
                durationDelta, stationaryDelta, obstacleDelta, degradedDelta,
                safeStopDelta, faultDelta, routeRejectDelta, dockHoldDelta, materialHoldDelta, waypointDelta
        );
    }

    private static Trend classify(
            RobotMissionTelemetrySnapshot before,
            RobotMissionTelemetrySnapshot after,
            long durationDelta,
            long stationaryDelta,
            int obstacleDelta,
            int degradedDelta,
            int safeStopDelta,
            int faultDelta,
            int routeRejectDelta,
            int dockHoldDelta,
            int materialHoldDelta
    ) {
        if (before.completed() != after.completed()) {
            return after.completed() ? Trend.IMPROVED : Trend.REGRESSED;
        }

        int localizationDelta = localizationSeverity(after.worstLocalization())
                - localizationSeverity(before.worstLocalization());
        if (faultDelta != 0) return faultDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (safeStopDelta != 0) return safeStopDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (localizationDelta != 0) return localizationDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;

        int hardHoldDelta = routeRejectDelta + dockHoldDelta + materialHoldDelta + degradedDelta;
        if (hardHoldDelta != 0) return hardHoldDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (obstacleDelta != 0) return obstacleDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (stationaryDelta != 0) return stationaryDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        if (durationDelta != 0) return durationDelta < 0 ? Trend.IMPROVED : Trend.REGRESSED;
        return Trend.SAME;
    }

    private static int localizationSeverity(RobotLocalizationQuality quality) {
        return switch (quality) {
            case VALID -> 0;
            case DEGRADED -> 1;
            case STALE -> 2;
            case LOST -> 3;
        };
    }

    public String compact() {
        return "#" + baselineSequence + "→#" + candidateSequence
                + " " + trend
                + " Δduration=" + signed(durationDeltaTicks)
                + "t Δstationary=" + signed(stationaryDeltaTicks)
                + "t Δwait=" + signed(obstacleWaitDelta)
                + " Δdegraded=" + signed(degradedDelta)
                + " ΔsafeStop=" + signed(safeStopDelta)
                + " Δfault=" + signed(faultDelta)
                + " ΔrouteReject=" + signed(routeRejectDelta)
                + " ΔdockHold=" + signed(dockHoldDelta)
                + " ΔmaterialHold=" + signed(materialHoldDelta)
                + " Δwaypoints=" + signed(waypointDelta);
    }

    private static String signed(long value) {
        return value > 0 ? "+" + value : Long.toString(value);
    }
}
