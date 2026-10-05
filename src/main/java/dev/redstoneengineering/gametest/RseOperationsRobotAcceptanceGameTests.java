package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.integration.OperationTransportBinding;
import dev.redstoneengineering.integration.OperationsRobotMaterialFlowAcceptance;
import dev.redstoneengineering.integration.OperationsRobotTransportBridge;
import dev.redstoneengineering.operations.OperationOutputSnapshot;
import dev.redstoneengineering.operations.OperationTransportDemand;
import dev.redstoneengineering.robotics.RobotDockSnapshot;
import dev.redstoneengineering.robotics.RobotLocalizationQuality;
import dev.redstoneengineering.robotics.RobotMaterialTransferSnapshot;
import dev.redstoneengineering.robotics.RobotMission;
import dev.redstoneengineering.robotics.RobotMissionTelemetrySnapshot;
import dev.redstoneengineering.robotics.RobotOperatingState;
import dev.redstoneengineering.robotics.RobotPayloadSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Executable contracts for Operations→AMR end-to-end material-flow acceptance. */
public final class RseOperationsRobotAcceptanceGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final BlockPos MARKER = new BlockPos(2, 1, 2);

    private RseOperationsRobotAcceptanceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void nominalCorrelatedMaterialFlowPasses(GameTestHelper helper) {
        Fixture f = fixture();
        OperationsRobotMaterialFlowAcceptance.Snapshot result = inspect(f, f.load(), f.unload(), f.telemetry());
        if (result.verdict() != OperationsRobotMaterialFlowAcceptance.Verdict.PASS
                || result.stage() != OperationsRobotMaterialFlowAcceptance.Stage.COMPLETE
                || !result.accepted()
                || !"END_TO_END_ACCEPTED".equals(result.reason())
                || !result.traceKey().contains("mission=" + f.binding().missionId())) {
            helper.fail("Nominal correlated Operations→AMR material flow must pass with traceable IDs", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void recoveredSafetyHoldIsMarginalNotSilentPass(GameTestHelper helper) {
        Fixture f = fixture();
        RobotMissionTelemetrySnapshot telemetry = telemetry(
                f.target(),
                true,
                RobotOperatingState.COMPLETE,
                RobotLocalizationQuality.VALID,
                0,
                1,
                0
        );
        OperationsRobotMaterialFlowAcceptance.Snapshot result = inspect(f, f.load(), f.unload(), telemetry);
        if (result.verdict() != OperationsRobotMaterialFlowAcceptance.Verdict.MARGINAL
                || result.operationalHolds() != 1
                || !"END_TO_END_ACCEPTED_WITH_RECOVERED_HOLDS".equals(result.reason())) {
            helper.fail("Recovered safety holds must remain visible as marginal acceptance", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void loadTransferCorrelationMismatchFailsClosed(GameTestHelper helper) {
        Fixture f = fixture();
        RobotMaterialTransferSnapshot wrongLoad = transfer(
                "unrelated-transfer",
                f.sourceDock().dockId(),
                f.robotId(),
                f.binding().units()
        );
        OperationsRobotMaterialFlowAcceptance.Snapshot result = inspect(f, wrongLoad, f.unload(), f.telemetry());
        if (result.verdict() != OperationsRobotMaterialFlowAcceptance.Verdict.SAFE_STOP
                || result.stage() != OperationsRobotMaterialFlowAcceptance.Stage.LOAD_TRANSFER
                || !"LOAD_TRANSFER_CORRELATION_MISMATCH".equals(result.reason())) {
            helper.fail("A valid-looking transfer with the wrong Operations correlation id must fail closed", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void unloadQuantityMismatchFailsClosed(GameTestHelper helper) {
        Fixture f = fixture();
        RobotMaterialTransferSnapshot wrongUnload = transfer(
                f.binding().unloadTransferId(),
                f.targetDock().dockId(),
                f.robotId(),
                f.binding().units() - 1
        );
        OperationsRobotMaterialFlowAcceptance.Snapshot result = inspect(f, f.load(), wrongUnload, f.telemetry());
        if (result.verdict() != OperationsRobotMaterialFlowAcceptance.Verdict.SAFE_STOP
                || result.stage() != OperationsRobotMaterialFlowAcceptance.Stage.UNLOAD_TRANSFER
                || !"UNLOAD_TRANSFER_QUANTITY_MISMATCH".equals(result.reason())) {
            helper.fail("Unload evidence cannot pass when its quantity no longer matches the Operations binding", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void terminalRobotFaultOverridesGoodTransferEvidence(GameTestHelper helper) {
        Fixture f = fixture();
        RobotMissionTelemetrySnapshot faulted = telemetry(
                f.target(),
                false,
                RobotOperatingState.FAULT,
                RobotLocalizationQuality.LOST,
                1,
                0,
                1
        );
        OperationsRobotMaterialFlowAcceptance.Snapshot result = inspect(f, f.load(), f.unload(), faulted);
        if (result.verdict() != OperationsRobotMaterialFlowAcceptance.Verdict.FAULT
                || result.stage() != OperationsRobotMaterialFlowAcceptance.Stage.MISSION
                || !"MISSION_TERMINAL_FAULT".equals(result.reason())) {
            helper.fail("Good load/unload evidence must never override a terminal AMR fault", MARKER);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE)
    public static void wrongFinalTargetCannotBeAccepted(GameTestHelper helper) {
        Fixture f = fixture();
        RobotMissionTelemetrySnapshot wrongTarget = telemetry(
                f.target().east(),
                true,
                RobotOperatingState.COMPLETE,
                RobotLocalizationQuality.VALID,
                0,
                0,
                0
        );
        OperationsRobotMaterialFlowAcceptance.Snapshot result = inspect(f, f.load(), f.unload(), wrongTarget);
        if (result.verdict() != OperationsRobotMaterialFlowAcceptance.Verdict.SAFE_STOP
                || result.stage() != OperationsRobotMaterialFlowAcceptance.Stage.MISSION
                || !"MISSION_FINAL_TARGET_MISMATCH".equals(result.reason())) {
            helper.fail("A completed AMR run to the wrong target must not satisfy Operations acceptance", MARKER);
            return;
        }
        helper.succeed();
    }

    private static OperationsRobotMaterialFlowAcceptance.Snapshot inspect(
            Fixture f,
            RobotMaterialTransferSnapshot load,
            RobotMaterialTransferSnapshot unload,
            RobotMissionTelemetrySnapshot telemetry
    ) {
        return OperationsRobotMaterialFlowAcceptance.inspect(
                f.output(),
                f.demand(),
                f.binding(),
                f.mission(),
                f.robotId(),
                f.sourceDock(),
                load,
                f.payload(),
                f.targetDock(),
                unload,
                telemetry
        );
    }

    private static Fixture fixture() {
        BlockPos source = new BlockPos(1, 1, 1);
        BlockPos target = new BlockPos(4, 1, 1);
        String robotId = "robot-a";

        OperationOutputSnapshot output = new OperationOutputSnapshot(
                22, 7, "rse:test_material", 4, source, PortQuality.VALID, true, true, false);
        OperationTransportDemand demand = new OperationTransportDemand(31, 22, source, target, 4, 50);
        OperationsRobotTransportBridge.Decision dispatch = OperationsRobotTransportBridge.evaluate(output, demand);
        if (!dispatch.ready()) throw new IllegalStateException("fixture dispatch must be ready");

        OperationTransportBinding binding = dispatch.binding();
        RobotMission mission = dispatch.mission();
        RobotDockSnapshot sourceDock = dock("source-dock", source, robotId);
        RobotDockSnapshot targetDock = dock("target-dock", target, robotId);
        RobotMaterialTransferSnapshot load = transfer(
                binding.loadTransferId(), sourceDock.dockId(), robotId, binding.units());
        RobotPayloadSnapshot payload = new RobotPayloadSnapshot(
                binding.payloadId(), robotId, binding.units(), PortQuality.VALID, true, false);
        RobotMaterialTransferSnapshot unload = transfer(
                binding.unloadTransferId(), targetDock.dockId(), robotId, binding.units());

        return new Fixture(
                source, target, robotId, output, demand, binding, mission,
                sourceDock, targetDock, load, payload, unload,
                telemetry(target, true, RobotOperatingState.COMPLETE, RobotLocalizationQuality.VALID, 0, 0, 0)
        );
    }

    private static RobotDockSnapshot dock(String dockId, BlockPos position, String robotId) {
        return new RobotDockSnapshot(
                dockId,
                position,
                PortQuality.VALID,
                robotId,
                robotId,
                true,
                true,
                true,
                true,
                false
        );
    }

    private static RobotMaterialTransferSnapshot transfer(
            String transferId,
            String dockId,
            String robotId,
            int units
    ) {
        return new RobotMaterialTransferSnapshot(
                transferId,
                dockId,
                robotId,
                units,
                units,
                PortQuality.VALID,
                true,
                true,
                true,
                false
        );
    }

    private static RobotMissionTelemetrySnapshot telemetry(
            BlockPos target,
            boolean completed,
            RobotOperatingState terminal,
            RobotLocalizationQuality worstLocalization,
            int faultEvents,
            int safeStopEvents,
            int degradedEntries
    ) {
        return new RobotMissionTelemetrySnapshot(
                true,
                true,
                completed,
                100,
                200,
                100,
                new BlockPos(0, 1, 1),
                target,
                terminal,
                worstLocalization,
                0,
                degradedEntries,
                safeStopEvents,
                faultEvents,
                0,
                0,
                0,
                4,
                80
        );
    }

    private record Fixture(
            BlockPos source,
            BlockPos target,
            String robotId,
            OperationOutputSnapshot output,
            OperationTransportDemand demand,
            OperationTransportBinding binding,
            RobotMission mission,
            RobotDockSnapshot sourceDock,
            RobotDockSnapshot targetDock,
            RobotMaterialTransferSnapshot load,
            RobotPayloadSnapshot payload,
            RobotMaterialTransferSnapshot unload,
            RobotMissionTelemetrySnapshot telemetry
    ) {}
}
