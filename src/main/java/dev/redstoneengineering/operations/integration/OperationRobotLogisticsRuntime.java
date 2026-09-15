package dev.redstoneengineering.operations.integration;

import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.robotics.RobotMission;

/**
 * Narrow Operations -> Robotics handoff adapter.
 *
 * <p>It preserves demand identity and intentionally does not select an AMR, route, dock,
 * safety policy, or motion command. Those remain Robotics responsibilities.</p>
 */
public final class OperationRobotLogisticsRuntime {
    private OperationRobotLogisticsRuntime() {}

    public static RobotMission toRobotMission(OperationTransportDemand demand) {
        if (demand == null) throw new IllegalArgumentException("transport demand is required");
        return new RobotMission(
                demand.missionId(),
                RobotMission.MissionType.TRANSFER,
                demand.source(),
                demand.target(),
                demand.priority(),
                demand.units()
        );
    }
}
