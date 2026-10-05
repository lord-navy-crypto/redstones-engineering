package dev.redstoneengineering.robotics;

import net.minecraft.core.BlockPos;

/**
 * Immutable observer snapshot for one AMR mission run.
 *
 * Counters are collected only at existing runtime/state-machine boundaries. This record never
 * commands motion, plans routes, changes safety state, or mutates mission execution.
 */
public record RobotMissionTelemetrySnapshot(
        boolean started,
        boolean finished,
        boolean completed,
        long startTick,
        long endTick,
        long durationTicks,
        BlockPos startPos,
        BlockPos finalTarget,
        RobotOperatingState terminalState,
        RobotLocalizationQuality worstLocalization,
        int obstacleWaitEvents,
        int degradedEntries,
        int safeStopEvents,
        int faultEvents,
        int routeRejectEvents,
        int dockHoldEvents,
        int materialHoldEvents,
        int maxRouteWaypoints,
        int motionTicks
) {
    public RobotMissionTelemetrySnapshot {
        startTick = Math.max(-1L, startTick);
        endTick = Math.max(-1L, endTick);
        durationTicks = Math.max(0L, durationTicks);
        if (startPos != null) startPos = startPos.immutable();
        if (finalTarget != null) finalTarget = finalTarget.immutable();
        if (terminalState == null) terminalState = RobotOperatingState.IDLE;
        if (worstLocalization == null) worstLocalization = RobotLocalizationQuality.LOST;
        obstacleWaitEvents = Math.max(0, obstacleWaitEvents);
        degradedEntries = Math.max(0, degradedEntries);
        safeStopEvents = Math.max(0, safeStopEvents);
        faultEvents = Math.max(0, faultEvents);
        routeRejectEvents = Math.max(0, routeRejectEvents);
        dockHoldEvents = Math.max(0, dockHoldEvents);
        materialHoldEvents = Math.max(0, materialHoldEvents);
        maxRouteWaypoints = Math.max(0, maxRouteWaypoints);
        motionTicks = Math.max(0, motionTicks);
        if (!started) {
            finished = false;
            completed = false;
        }
        if (completed) finished = true;
    }

    public boolean comparablePath(RobotMissionTelemetrySnapshot other) {
        return other != null
                && startPos != null
                && finalTarget != null
                && startPos.equals(other.startPos)
                && finalTarget.equals(other.finalTarget);
    }

    public int holdEvents() {
        return obstacleWaitEvents + degradedEntries + safeStopEvents
                + routeRejectEvents + dockHoldEvents + materialHoldEvents;
    }

    public long stationaryTicks() {
        return Math.max(0L, durationTicks - motionTicks);
    }

    public String compact() {
        return (completed ? "COMPLETE" : finished ? "FAILED" : started ? "RUNNING" : "IDLE")
                + " duration=" + durationTicks + "t"
                + " motion=" + motionTicks + "t"
                + " holds=" + holdEvents()
                + " safeStops=" + safeStopEvents
                + " faults=" + faultEvents
                + " localization=" + worstLocalization
                + " waypoints=" + maxRouteWaypoints;
    }
}
