package dev.redstoneengineering.robotics;

/**
 * Pure runtime bridge that combines dock-transfer admission with authoritative
 * material-transfer evidence before allowing LOADING -> TRANSPORTING.
 */
public final class RobotMaterialFlowRuntime {
    private RobotMaterialFlowRuntime() {}

    public enum Verdict {
        ADVANCE,
        WAIT,
        SAFE_STOP,
        FAULT
    }

    public record Decision(
            RobotOperatingState nextState,
            Verdict verdict,
            String dockReason,
            String materialReason
    ) {
        public Decision {
            if (nextState == null) nextState = RobotOperatingState.SAFE_STOP;
            if (verdict == null) verdict = Verdict.SAFE_STOP;
            if (dockReason == null || dockReason.isBlank()) dockReason = "UNSPECIFIED_DOCK";
            if (materialReason == null || materialReason.isBlank()) materialReason = "UNSPECIFIED_MATERIAL";
        }

        public boolean advancesToTransport() {
            return verdict == Verdict.ADVANCE && nextState == RobotOperatingState.TRANSPORTING;
        }
    }

    public static Decision evaluate(
            RobotOperatingState current,
            RobotDockSnapshot dock,
            RobotMaterialTransferSnapshot transfer,
            String robotId
    ) {
        if (current != RobotOperatingState.LOADING) {
            RobotOperatingState stopped = RobotStateMachine.next(
                    current == null ? RobotOperatingState.FAULT : current,
                    RobotStateMachine.Event.SAFETY_STOP_REQUESTED);
            return new Decision(stopped, Verdict.SAFE_STOP, "STATE_NOT_LOADING", "TRANSFER_NOT_EVALUATED");
        }

        RobotDockAssessment.Snapshot dockAssessment = RobotDockAssessment.inspect(
                dock, robotId, RobotDockAssessment.Phase.TRANSFER);
        switch (dockAssessment.verdict()) {
            case FAULT -> {
                return new Decision(
                        RobotStateMachine.next(current, RobotStateMachine.Event.CRITICAL_FAULT),
                        Verdict.FAULT,
                        dockAssessment.reason(),
                        "TRANSFER_NOT_EVALUATED");
            }
            case SAFE_STOP -> {
                return new Decision(
                        RobotStateMachine.next(current, RobotStateMachine.Event.SAFETY_STOP_REQUESTED),
                        Verdict.SAFE_STOP,
                        dockAssessment.reason(),
                        "TRANSFER_NOT_EVALUATED");
            }
            case WAIT -> {
                return new Decision(current, Verdict.WAIT, dockAssessment.reason(), "TRANSFER_NOT_EVALUATED");
            }
            case PERMIT -> {
                // Continue below: a dock permit is only admission to evaluate
                // transfer evidence, never proof that material moved.
            }
        }

        RobotMaterialTransferAssessment.Snapshot material = RobotMaterialTransferAssessment.inspect(
                transfer, dock.dockId(), robotId);
        return switch (material.verdict()) {
            case FAULT -> new Decision(
                    RobotStateMachine.next(current, RobotStateMachine.Event.CRITICAL_FAULT),
                    Verdict.FAULT,
                    dockAssessment.reason(),
                    material.reason());
            case SAFE_STOP -> new Decision(
                    RobotStateMachine.next(current, RobotStateMachine.Event.SAFETY_STOP_REQUESTED),
                    Verdict.SAFE_STOP,
                    dockAssessment.reason(),
                    material.reason());
            case WAIT -> new Decision(current, Verdict.WAIT, dockAssessment.reason(), material.reason());
            case COMPLETE -> new Decision(
                    RobotStateMachine.next(current, RobotStateMachine.Event.LOAD_COMPLETE),
                    Verdict.ADVANCE,
                    dockAssessment.reason(),
                    material.reason());
        };
    }
}
