package dev.redstoneengineering.integration;

import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.robotics.RobotMission;
import net.minecraft.core.BlockPos;

/** Immutable correlation evidence carried beside a Robotics mission across the Operations boundary. */
public record OperationTransportBinding(
        long missionId,
        long outputId,
        long jobId,
        BlockPos source,
        BlockPos target,
        int units
) {
    public OperationTransportBinding {
        if (missionId < 0) throw new IllegalArgumentException("missionId must be non-negative");
        if (outputId < 0) throw new IllegalArgumentException("outputId must be non-negative");
        if (jobId < 0) throw new IllegalArgumentException("jobId must be non-negative");
        if (source == null) throw new IllegalArgumentException("source is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
    }

    /**
     * Stable cross-domain correlation key for one Operations output assigned to one Robotics mission.
     *
     * This is trace metadata only; it is not a security token and never grants motion or material-flow
     * authority.
     */
    public String correlationKey() {
        return "ops-m" + missionId + "-o" + outputId + "-j" + jobId;
    }

    public String payloadId() {
        return correlationKey() + "-payload";
    }

    public String loadTransferId() {
        return correlationKey() + "-load";
    }

    public String unloadTransferId() {
        return correlationKey() + "-unload";
    }

    public boolean matches(OperationOutputSnapshot output, OperationTransportDemand demand) {
        return output != null
                && demand != null
                && output.outputId() == outputId
                && output.jobId() == jobId
                && demand.missionId() == missionId
                && demand.outputId() == outputId
                && output.source().equals(source)
                && demand.source().equals(source)
                && demand.target().equals(target)
                && output.units() == units
                && demand.units() == units;
    }

    public boolean matches(RobotMission mission) {
        return mission != null
                && mission.missionId() == missionId
                && mission.type() == RobotMission.MissionType.TRANSFER
                && mission.source() != null
                && mission.source().equals(source)
                && mission.target().equals(target)
                && mission.payloadUnits() == units;
    }
}

