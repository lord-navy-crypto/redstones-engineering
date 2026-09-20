package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AlarmProcessorBlock;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.SafetyInterlockBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatic factory-acceptance runtime for the modular Validation Plant v1.1.
 *
 * <p>The runtime owns fixture placement, status lamps and validation stimulus only. DUT outputs are
 * never written by the validator: verdicts come from live BlockState, engineeringSnapshot(),
 * analyzer samples, interlock/alarm runtime and servo feedback.</p>
 */
public final class RseValidationPlantService {
    private RseValidationPlantService() {}

    private static final int AUTO_INTERVAL_TICKS = 4;
    private static final long PROCESS_PROPAGATION_TIMEOUT_TICKS = 96L;
    private static final BlockPos CONTROL_OFFSET = new BlockPos(15, 0, 0);
    private static final BlockPos MASTER_RETEST_BUTTON = new BlockPos(2, 1, 10);

    private static final BlockPos LOCAL_WAIT_POWER = new BlockPos(14, 2, 3);
    private static final BlockPos LOCAL_PASS_POWER = new BlockPos(15, 2, 3);
    private static final BlockPos LOCAL_FAIL_POWER = new BlockPos(16, 2, 3);
    private static final BlockPos MASTER_WAIT_POWER = new BlockPos(11, 2, 9);
    private static final BlockPos MASTER_PASS_POWER = new BlockPos(12, 2, 9);
    private static final BlockPos MASTER_FAIL_POWER = new BlockPos(13, 2, 9);

    private static final Map<String, BlockPos> CONTROL_CELL_WAIT = Map.of(
            "A", new BlockPos(2, 2, 5), "B", new BlockPos(6, 2, 5), "C", new BlockPos(10, 2, 5),
            "D", new BlockPos(14, 2, 5), "E", new BlockPos(18, 2, 5), "F", new BlockPos(22, 2, 5));
    private static final Map<String, BlockPos> CONTROL_CELL_PASS = Map.of(
            "A", new BlockPos(3, 2, 5), "B", new BlockPos(7, 2, 5), "C", new BlockPos(11, 2, 5),
            "D", new BlockPos(15, 2, 5), "E", new BlockPos(19, 2, 5), "F", new BlockPos(23, 2, 5));
    private static final Map<String, BlockPos> CONTROL_CELL_FAIL = Map.of(
            "A", new BlockPos(4, 2, 5), "B", new BlockPos(8, 2, 5), "C", new BlockPos(12, 2, 5),
            "D", new BlockPos(16, 2, 5), "E", new BlockPos(20, 2, 5), "F", new BlockPos(24, 2, 5));

    private record ModuleSpec(String id, String cell, BlockPos offset, BlockPos size) {}

    private static final List<ModuleSpec> MODULES = List.of(
            new ModuleSpec("control_room", "", CONTROL_OFFSET, new BlockPos(27, 5, 13)),
            new ModuleSpec("service_spine", "", new BlockPos(0, 0, 13), new BlockPos(57, 5, 3)),
            new ModuleSpec("cell_a_acquisition", "A", new BlockPos(0, 0, 16), new BlockPos(19, 5, 13)),
            new ModuleSpec("cell_b_conditioning", "B", new BlockPos(19, 0, 16), new BlockPos(19, 5, 13)),
            new ModuleSpec("cell_c_instrumentation", "C", new BlockPos(38, 0, 16), new BlockPos(19, 5, 13)),
            new ModuleSpec("cell_d_control", "D", new BlockPos(38, 0, 29), new BlockPos(19, 5, 13)),
            new ModuleSpec("cell_e_safety", "E", new BlockPos(19, 0, 29), new BlockPos(19, 5, 13)),
            new ModuleSpec("cell_f_process", "F", new BlockPos(0, 0, 29), new BlockPos(19, 5, 13))
    );

    private static final Map<String, ModuleSpec> CELLS;
    static {
        LinkedHashMap<String, ModuleSpec> cells = new LinkedHashMap<>();
        for (ModuleSpec module : MODULES) {
            if (!module.cell().isBlank()) cells.put(module.cell(), module);
        }
        CELLS = Map.copyOf(cells);
    }

    public enum Phase {
        PRECHECK,
        BIST,
        STARTUP,
        NOMINAL,
        DISTURBANCE,
        SATURATION,
        SENSOR_FAULT,
        INTERLOCK_TRIP,
        RECOVERY,
        FINAL_RUN,
        ACCEPTANCE
    }

    public static RseValidationFactoryService.Result place(ServerLevel level, BlockPos origin) {
        if (level == null || origin == null) {
            return RseValidationFactoryService.Result.fail("Validation plant place failed: level/origin missing");
        }
        if (level.getServer().overworld() != level) {
            return RseValidationFactoryService.Result.fail("Validation Plant v1.1 currently requires the overworld.");
        }
        RseValidationFactoryService.Result preflight = preflight(level);
        if (!preflight.success()) return preflight;
        RseValidationFactoryService.Result placement = placeModules(level, origin);
        if (!placement.success()) return placement;

        RseValidationPlantSavedData.get(level).place(origin, level.getGameTime());
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return RseValidationFactoryService.Result.ok(
                "Placed RSE Integrated Validation Plant v1.1.",
                "Flow: A acquisition -> B conditioning -> C instrumentation -> D control -> E safety/fault -> F servo process.",
                "Signed cells, color-coded WAIT/PASS/FAIL stations and the service spine make the acceptance result readable in-world.",
                "Central control room starts YELLOW WAIT and runs PRECHECK through ACCEPTANCE automatically."
        );
    }

    public static RseValidationFactoryService.Result status(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Validation plant status failed: server level missing");
        RseValidationPlantSavedData.Placement placement = RseValidationPlantSavedData.get(level).placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Validation Plant v1.1 has not been placed.");

        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = phase == Phase.PRECHECK
                ? precheckCells(level, placement.origin())
                : evaluateCells(level, placement.origin(), phase, age);

        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE Integrated Validation Plant v1.1 | phase=" + phase + " | phaseAge=" + age + "t"));
        for (Map.Entry<String, RseValidationSelfTestService.Evaluation> entry : cells.entrySet()) {
            lines.add(Component.literal("Cell " + entry.getKey() + " -> " + entry.getValue().verdict() + " | " + entry.getValue().detail()));
        }
        lines.add(Component.literal("Origin: " + placement.origin().toShortString()));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result retest(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Validation plant retest failed: server level missing");
        RseValidationPlantSavedData.Placement placement = RseValidationPlantSavedData.get(level).placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Validation Plant v1.1 has not been placed.");
        if (!rebuildForRetest(level, placement.origin(), false)) {
            return RseValidationFactoryService.Result.fail("Validation Plant v1.1 retest rebuild failed.");
        }
        return RseValidationFactoryService.Result.ok("Validation Plant v1.1 rebuilt; automatic acceptance restarted at PRECHECK.");
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() % AUTO_INTERVAL_TICKS != 0) return;

        RseValidationPlantSavedData data = RseValidationPlantSavedData.get(level);
        RseValidationPlantSavedData.Placement placement = data.placement();
        if (placement == null) return;
        BlockPos origin = placement.origin();
        if (!level.hasChunkAt(origin) || !level.hasChunkAt(origin.offset(56, 0, 41))) return;

        boolean pressed = masterRetestPressed(level, origin);
        if (pressed && !placement.retestPressed()) {
            if (!rebuildForRetest(level, origin, true)) {
                updateMasterPanel(level, origin, RseValidationSelfTestService.Verdict.FAIL);
            }
            return;
        }
        if (!pressed && placement.retestPressed()) {
            placement = data.setRetestPressed(false);
            if (placement == null) return;
        }
        tickAcceptance(level, data, placement);
    }

    private static void tickAcceptance(
            ServerLevel level,
            RseValidationPlantSavedData data,
            RseValidationPlantSavedData.Placement placement
    ) {
        Phase phase = phaseOf(placement.phase());
        long phaseAge = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        applyPhaseStimulus(level, placement.origin(), phase, phaseAge);

        if (phaseAge < settleTicks(phase)) {
            updateAllPanels(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
            return;
        }

        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = phase == Phase.PRECHECK
                ? precheckCells(level, placement.origin())
                : evaluateCells(level, placement.origin(), phase, phaseAge);
        updateCellPanels(level, placement.origin(), cells);

        boolean failed = cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.FAIL);
        boolean waiting = cells.values().stream().anyMatch(e -> e.verdict() == RseValidationSelfTestService.Verdict.WAIT);
        if (failed) {
            updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.FAIL);
            return;
        }
        if (waiting) {
            updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
            return;
        }
        if (phase == Phase.ACCEPTANCE) {
            updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.PASS);
            return;
        }

        updateMasterPanel(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
        data.advancePhase(level.getGameTime());
    }

    private static LinkedHashMap<String, RseValidationSelfTestService.Evaluation> precheckCells(
            ServerLevel level, BlockPos origin
    ) {
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> result = new LinkedHashMap<>();
        result.put("A", blockCheck(level, cellOrigin(origin, "A").offset(2, 1, 6), RedstoneReferenceSourceBlock.class, "reference source"));
        result.put("B", blockCheck(level, cellOrigin(origin, "B").offset(3, 1, 6), SignalConditionerBlock.class, "gain conditioner"));
        result.put("C", blockCheck(level, cellOrigin(origin, "C").offset(3, 1, 6), SignalAnalyzerBlock.class, "inline analyzer"));
        result.put("D", blockCheck(level, cellOrigin(origin, "D").offset(12, 1, 4), PwmControllerBlock.class, "PWM branch"));
        result.put("E", blockCheck(level, cellOrigin(origin, "E").offset(9, 1, 9), SafetyInterlockBlock.class, "safety interlock"));
        RseValidationSelfTestService.Evaluation servo = blockCheck(
                level, cellOrigin(origin, "F").offset(14, 1, 6), ServoActuatorBlock.class, "servo actuator");
        if (servo.verdict() != RseValidationSelfTestService.Verdict.PASS) {
            result.put("F", servo);
        } else {
            result.put("F", blockCheck(
                    level, cellOrigin(origin, "F").offset(14, 1, 5), RedstoneReferenceSourceBlock.class, "servo BRAKE test source"));
        }
        return result;
    }

    private static LinkedHashMap<String, RseValidationSelfTestService.Evaluation> evaluateCells(
            ServerLevel level, BlockPos origin, Phase phase, long phaseAge
    ) {
        int source = expectedSource(phase);
        int conditioned = Math.min(15, source * 2);
        boolean saturated = source * 2 > 15;
        boolean faulted = phase == Phase.SENSOR_FAULT;
        boolean tripped = phase == Phase.INTERLOCK_TRIP;
        int processCommand = faulted ? Math.min(15, conditioned + 4) : conditioned;

        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> result = new LinkedHashMap<>();
        result.put("A", evaluateAcquisition(level, cellOrigin(origin, "A"), source));
        result.put("B", evaluateConditioning(level, cellOrigin(origin, "B"), conditioned, saturated));
        result.put("C", evaluateInstrumentation(level, cellOrigin(origin, "C"), conditioned));
        result.put("D", evaluateControl(level, cellOrigin(origin, "D"), conditioned));
        result.put("E", evaluateSafety(level, cellOrigin(origin, "E"), conditioned, faulted, tripped, phase == Phase.ACCEPTANCE));
        result.put("F", evaluateProcess(level, cellOrigin(origin, "F"), processCommand, tripped, phaseAge));
        return result;
    }

    private static RseValidationSelfTestService.Evaluation evaluateAcquisition(
            ServerLevel level, BlockPos origin, int expected
    ) {
        BlockPos sourcePos = origin.offset(2, 1, 6);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (!(sourceState.getBlock() instanceof RedstoneReferenceSourceBlock source)) return fail("reference source missing");
        RseValidationSelfTestService.Evaluation sourceEvidence = exact(
                source.engineeringSnapshot(level, sourcePos, sourceState, Direction.EAST).orElse(null),
                expected, PortQuality.VALID, "source EAST");
        if (sourceEvidence.verdict() != RseValidationSelfTestService.Verdict.PASS) return sourceEvidence;

        BlockPos probePos = origin.offset(3, 1, 5);
        BlockState probeState = level.getBlockState(probePos);
        if (!(probeState.getBlock() instanceof SignalProbeBlock probe)) return fail("signal probe missing");
        return exact(
                probe.engineeringSnapshot(level, probePos, probeState, Direction.SOUTH).orElse(null),
                expected, PortQuality.VALID, "probe tap");
    }

    private static RseValidationSelfTestService.Evaluation evaluateConditioning(
            ServerLevel level, BlockPos origin, int expected, boolean saturated
    ) {
        BlockPos conditionerPos = origin.offset(3, 1, 6);
        BlockState conditionerState = level.getBlockState(conditionerPos);
        if (!(conditionerState.getBlock() instanceof SignalConditionerBlock conditioner)) return fail("signal conditioner missing");
        PortQuality expectedQuality = saturated ? PortQuality.SATURATED : PortQuality.VALID;
        RseValidationSelfTestService.Evaluation conditionerEvidence = exact(
                conditioner.engineeringSnapshot(level, conditionerPos, conditionerState, Direction.EAST).orElse(null),
                expected, expectedQuality, "conditioner x2 output");
        if (conditionerEvidence.verdict() != RseValidationSelfTestService.Verdict.PASS) return conditionerEvidence;

        BlockPos filterPos = origin.offset(4, 1, 6);
        BlockState filterState = level.getBlockState(filterPos);
        if (!(filterState.getBlock() instanceof PrecisionFilterBlock)) return fail("precision filter missing");
        int filtered = filterState.getValue(DirectionalSignalBlock.OUTPUT);
        if (filtered != expected) return fail("precision filter=" + filtered + " expected=" + expected);

        return indicator(level, origin.offset(8, 1, 5), expected, PortQuality.VALID, "conditioned tap");
    }

    private static RseValidationSelfTestService.Evaluation evaluateInstrumentation(
            ServerLevel level, BlockPos origin, int expected
    ) {
        BlockPos analyzerPos = origin.offset(3, 1, 6);
        BlockState analyzerState = level.getBlockState(analyzerPos);
        if (!(analyzerState.getBlock() instanceof SignalAnalyzerBlock)) return fail("inline signal analyzer missing");
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, analyzerPos);
        if (snapshot.totalSamples() < 2) return waitFor("inline analyzer samples=" + snapshot.totalSamples());
        if (snapshot.raw() != expected || snapshot.output() != expected) {
            return fail("inline analyzer raw/output=" + snapshot.raw() + "/" + snapshot.output() + " expected=" + expected);
        }
        return indicator(level, origin.offset(8, 1, 5), expected, PortQuality.VALID, "instrumentation tap");
    }

    private static RseValidationSelfTestService.Evaluation evaluateControl(
            ServerLevel level, BlockPos origin, int mainBusExpected
    ) {
        BlockPos pwmPos = origin.offset(12, 1, 4);
        if (!(level.getBlockState(pwmPos).getBlock() instanceof PwmControllerBlock)) return fail("PWM controller missing");
        BlockPos analyzerPos = origin.offset(11, 1, 4);
        if (!(level.getBlockState(analyzerPos).getBlock() instanceof SignalAnalyzerBlock)) return fail("PWM analyzer missing");
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, analyzerPos);
        if (snapshot.totalSamples() < 4) return waitFor("PWM analyzer samples=" + snapshot.totalSamples());
        if (mainBusExpected < 15 && snapshot.peakToPeak() <= 0) {
            return fail("PWM diagnostic branch shows no switching activity; COMMAND/INHIBIT routing check failed");
        }
        if (snapshot.raw() < 0 || snapshot.raw() > 15) return fail("PWM analyzer raw out of range=" + snapshot.raw());
        return pass("PWM CONTROL: analog command=" + mainBusExpected + "; switching peakToPeak=" + snapshot.peakToPeak());
    }

    private static RseValidationSelfTestService.Evaluation evaluateSafety(
            ServerLevel level,
            BlockPos origin,
            int mainInput,
            boolean faultExpected,
            boolean tripExpected,
            boolean finalAcceptance
    ) {
        BlockPos faultPos = origin.offset(14, 1, 6);
        BlockState faultState = level.getBlockState(faultPos);
        if (!(faultState.getBlock() instanceof FaultInjectorBlock injector)) return fail("fault injector missing");
        int expectedFaultOutput = faultExpected ? Math.min(15, mainInput + 4) : mainInput;
        PortQuality expectedFaultQuality = faultExpected ? PortQuality.FAULT : PortQuality.VALID;
        RseValidationSelfTestService.Evaluation fault = exact(
                injector.engineeringSnapshot(level, faultPos, faultState, Direction.WEST).orElse(null),
                expectedFaultOutput, expectedFaultQuality, "fault-path output");
        if (fault.verdict() != RseValidationSelfTestService.Verdict.PASS) return fault;
        if (FaultInjectorBlock.active(level, faultPos) != faultExpected) {
            return fail("fault active=" + FaultInjectorBlock.active(level, faultPos) + " expected=" + faultExpected);
        }
        if (faultExpected && (FaultInjectorBlock.lastInput(level, faultPos) != mainInput
                || FaultInjectorBlock.lastOutput(level, faultPos) != expectedFaultOutput)) {
            return fail("fault evidence does not match live input/output");
        }

        BlockPos interlockPos = origin.offset(9, 1, 9);
        BlockState interlockState = level.getBlockState(interlockPos);
        if (!(interlockState.getBlock() instanceof SafetyInterlockBlock)) return fail("safety interlock missing");
        int mask = SafetyInterlockBlock.failedMask(level, interlockPos);
        if (mask < 0) return waitFor("interlock runtime not evaluated");
        int expectedMask = tripExpected ? 2 : 0;
        int expectedPermit = tripExpected ? 0 : 15;
        if (mask != expectedMask || interlockState.getValue(DirectionalSignalBlock.OUTPUT) != expectedPermit) {
            return fail("interlock mask/output=" + mask + "/" + interlockState.getValue(DirectionalSignalBlock.OUTPUT)
                    + " expected=" + expectedMask + "/" + expectedPermit);
        }

        BlockPos alarmPos = origin.offset(9, 1, 3);
        BlockState alarmState = level.getBlockState(alarmPos);
        if (!(alarmState.getBlock() instanceof AlarmProcessorBlock)) return fail("alarm processor missing");
        boolean latched = AlarmProcessorBlock.latched(level, alarmPos);
        if (latched != tripExpected) return fail("alarm latched=" + latched + " expected=" + tripExpected);
        if (tripExpected) {
            if (!AlarmProcessorBlock.unacknowledged(level, alarmPos)) return fail("trip alarm must begin unacknowledged");
            if (alarmState.getValue(DirectionalSignalBlock.OUTPUT) != 10) return fail("severity-2 alarm output should be 10");
        } else if (alarmState.getValue(DirectionalSignalBlock.OUTPUT) != 0) {
            return fail("healthy alarm output should be 0");
        }

        if (finalAcceptance) {
            if (FaultInjectorBlock.activationCount(level, faultPos) <= 0) return fail("acceptance missing prior fault-injection evidence");
            if (AlarmProcessorBlock.activationCount(level, alarmPos) <= 0) return fail("acceptance missing prior alarm lifecycle evidence");
        }
        return pass((faultExpected ? "FAULT quality propagated; " : "fault path healthy; ")
                + (tripExpected ? "B-trip mask=2 + alarm latch" : "permit healthy + alarm clear"));
    }

    private static RseValidationSelfTestService.Evaluation evaluateProcess(
            ServerLevel level, BlockPos origin, int expectedCommand, boolean tripExpected, long phaseAge
    ) {
        BlockPos servoPos = origin.offset(14, 1, 6);
        BlockState servoState = level.getBlockState(servoPos);
        if (!(servoState.getBlock() instanceof ServoActuatorBlock)) return fail("SERVO: actuator missing");

        BlockPos brakeSourcePos = origin.offset(14, 1, 5);
        BlockState brakeSourceState = level.getBlockState(brakeSourcePos);
        if (!(brakeSourceState.getBlock() instanceof RedstoneReferenceSourceBlock)) {
            return fail("BRAKE: test source missing");
        }
        int expectedBrakePower = tripExpected ? 15 : 0;
        int brakePower = brakeSourceState.getValue(RedstoneReferenceSourceBlock.POWER);
        if (brakePower != expectedBrakePower) {
            return waitFor("BRAKE source propagating=" + brakePower + " expected=" + expectedBrakePower);
        }

        int command = ServoActuatorBlock.command(level, servoPos);
        int position = ServoActuatorBlock.position(level, servoPos);
        if (command != expectedCommand) {
            if (phaseAge < PROCESS_PROPAGATION_TIMEOUT_TICKS) {
                return waitFor("servo command propagating=" + command + " expected=" + expectedCommand);
            }
            return fail("COMMAND STUCK: servo=" + command + " expected=" + expectedCommand + " age=" + phaseAge + "t");
        }

        boolean expectedBrake = tripExpected;
        boolean braking = ServoActuatorBlock.braking(level, servoPos);
        if (braking != expectedBrake) {
            if (phaseAge < PROCESS_PROPAGATION_TIMEOUT_TICKS) {
                return waitFor("BRAKE state=" + braking + " expected=" + expectedBrake);
            }
            return fail("BRAKE STUCK: state=" + braking + " expected=" + expectedBrake + " age=" + phaseAge + "t");
        }
        if (!tripExpected && Math.abs(position - expectedCommand) > 1) {
            if (phaseAge < PROCESS_PROPAGATION_TIMEOUT_TICKS) {
                return waitFor("POSITION settling=" + position + " target=" + expectedCommand);
            }
            return fail("POSITION STUCK: position=" + position + " target=" + expectedCommand + " age=" + phaseAge + "t");
        }

        BlockPos sensorPos = origin.offset(13, 1, 6);
        BlockState sensorState = level.getBlockState(sensorPos);
        if (!(sensorState.getBlock() instanceof ServoPositionSensorBlock sensor)) return fail("FEEDBACK: position sensor missing");
        PortQuality sourceQuality = ServoPositionSensorBlock.sourceQuality(level, sensorPos, sensorState);
        if (sourceQuality != PortQuality.VALID) {
            return fail("FEEDBACK topology/quality=" + sourceQuality);
        }
        EngineeringPortSnapshot feedback = sensor.engineeringSnapshot(level, sensorPos, sensorState, Direction.WEST).orElse(null);
        if (feedback == null || feedback.quality() == PortQuality.STALE) return waitFor("FEEDBACK awaiting fresh sample");
        if (feedback.quality() != PortQuality.VALID && feedback.quality() != PortQuality.SATURATED) {
            return fail("FEEDBACK quality=" + feedback.quality());
        }
        int feedbackValue = (int) Math.round(feedback.value());
        if (Math.abs(feedbackValue - position) > 1) {
            if (phaseAge < PROCESS_PROPAGATION_TIMEOUT_TICKS) {
                return waitFor("FEEDBACK settling=" + feedbackValue + " position=" + position);
            }
            return fail("FEEDBACK STUCK: value=" + feedbackValue + " position=" + position + " age=" + phaseAge + "t");
        }

        BlockState processLamp = level.getBlockState(origin.offset(16, 1, 7));
        if (!processLamp.hasProperty(BlockStateProperties.LIT) || !processLamp.getValue(BlockStateProperties.LIT)) {
            return fail("COMMAND: process command lamp is not lit");
        }

        BlockPos safetyIndicatorPos = origin.offset(14, 1, 9);
        BlockState safetyState = level.getBlockState(safetyIndicatorPos);
        if (!(safetyState.getBlock() instanceof AnalogIndicatorBlock safetyIndicator)) return fail("PERMIT: indicator missing");
        AnalogIndicatorBlock.InputObservation safety = safetyIndicator.inputObservation(level, safetyIndicatorPos, safetyState);
        if (safety.quality() == PortQuality.STALE) return waitFor("PERMIT stale");
        int expectedPermit = tripExpected ? 0 : 15;
        if (safety.value() != expectedPermit) return fail("PERMIT=" + safety.value() + " expected=" + expectedPermit);

        return pass("SERVO: command/position=" + command + "/" + position
                + "; BRAKE=" + braking
                + "; FEEDBACK=" + feedbackValue
                + "; PERMIT=" + expectedPermit);
    }

    private static RseValidationSelfTestService.Evaluation indicator(
            ServerLevel level, BlockPos pos, int expected, PortQuality expectedQuality, String label
    ) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogIndicatorBlock indicator)) return fail(label + " indicator missing");
        AnalogIndicatorBlock.InputObservation observation = indicator.inputObservation(level, pos, state);
        if (observation.quality() == PortQuality.STALE) return waitFor(label + " STALE");
        if (observation.value() != expected) return fail(label + "=" + observation.value() + " expected=" + expected);
        if (observation.quality() != expectedQuality) {
            return fail(label + " quality=" + observation.quality() + " expected=" + expectedQuality);
        }
        return pass(label + "=" + expected + "/" + expectedQuality);
    }

    private static RseValidationSelfTestService.Evaluation exact(
            EngineeringPortSnapshot snapshot, int expected, PortQuality expectedQuality, String label
    ) {
        if (snapshot == null) return fail(label + " snapshot missing");
        if (snapshot.quality() == PortQuality.STALE) return waitFor(label + " STALE");
        int value = (int) Math.round(snapshot.value());
        if (value != expected) return fail(label + "=" + value + " expected=" + expected);
        if (snapshot.quality() != expectedQuality) {
            return fail(label + " quality=" + snapshot.quality() + " expected=" + expectedQuality);
        }
        return pass(label + "=" + expected + "/" + expectedQuality);
    }

    private static RseValidationSelfTestService.Evaluation blockCheck(
            ServerLevel level, BlockPos pos, Class<?> blockType, String label
    ) {
        return blockType.isInstance(level.getBlockState(pos).getBlock())
                ? pass(label + " present")
                : fail(label + " missing at " + pos.toShortString());
    }

    private static void applyPhaseStimulus(ServerLevel level, BlockPos origin, Phase phase, long phaseAge) {
        BlockPos a = cellOrigin(origin, "A");
        BlockPos e = cellOrigin(origin, "E");
        BlockPos f = cellOrigin(origin, "F");
        setReferencePower(level, a.offset(2, 1, 6), expectedSource(phase));
        setReferencePower(level, e.offset(14, 1, 5), phase == Phase.SENSOR_FAULT ? 15 : 0);
        setReferencePower(level, e.offset(9, 1, 10), phase == Phase.INTERLOCK_TRIP ? 0 : 15);
        setReferencePower(level, e.offset(10, 1, 3), phase == Phase.INTERLOCK_TRIP ? 15 : 0);
        setReferencePower(level, f.offset(14, 1, 5), phase == Phase.INTERLOCK_TRIP ? 15 : 0);

        int ack = 0;
        int reset = 0;
        if (phase == Phase.RECOVERY) {
            if (phaseAge <= 8) ack = 15;
            else if (phaseAge >= 12 && phaseAge <= 20) reset = 15;
        }
        setReferencePower(level, e.offset(9, 1, 4), ack);
        setReferencePower(level, e.offset(9, 1, 2), reset);
    }

    private static void applyBaseline(ServerLevel level, BlockPos origin) {
        BlockPos a = cellOrigin(origin, "A");
        BlockPos e = cellOrigin(origin, "E");
        BlockPos f = cellOrigin(origin, "F");
        setReferencePower(level, a.offset(2, 1, 6), 6);
        setReferencePower(level, e.offset(14, 1, 5), 0);
        setReferencePower(level, e.offset(10, 1, 9), 15);
        setReferencePower(level, e.offset(9, 1, 10), 15);
        setReferencePower(level, e.offset(9, 1, 8), 15);
        setReferencePower(level, e.offset(10, 1, 3), 0);
        setReferencePower(level, e.offset(9, 1, 4), 0);
        setReferencePower(level, e.offset(9, 1, 2), 0);
        setReferencePower(level, f.offset(14, 1, 5), 0);
    }

    private static int expectedSource(Phase phase) {
        return switch (phase) {
            case DISTURBANCE -> 5;
            case SATURATION -> 10;
            default -> 6;
        };
    }

    private static int settleTicks(Phase phase) {
        return switch (phase) {
            case PRECHECK -> 4;
            case BIST, STARTUP, NOMINAL -> 28;
            case DISTURBANCE, SATURATION -> 36;
            case SENSOR_FAULT, INTERLOCK_TRIP -> 32;
            case RECOVERY -> 36;
            case FINAL_RUN, ACCEPTANCE -> 36;
        };
    }

    private static Phase phaseOf(int phase) {
        Phase[] phases = Phase.values();
        return phases[Math.max(0, Math.min(phases.length - 1, phase))];
    }

    private static boolean masterRetestPressed(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin.offset(CONTROL_OFFSET).offset(MASTER_RETEST_BUTTON));
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    private static boolean rebuildForRetest(ServerLevel level, BlockPos origin, boolean pressed) {
        if (!preflight(level).success()) return false;
        for (ModuleSpec module : MODULES) clearModule(level, origin.offset(module.offset()), module.size());
        if (!placeModules(level, origin).success()) return false;
        RseValidationPlantSavedData.get(level).resetForRetest(level.getGameTime(), pressed);
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return true;
    }

    private static void clearModule(ServerLevel level, BlockPos origin, BlockPos size) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static RseValidationFactoryService.Result preflight(ServerLevel level) {
        for (ModuleSpec module : MODULES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    RedstoneEngineering.MOD_ID, "validation/plant/" + module.id());
            if (level.getStructureManager().get(id).isEmpty()) {
                return RseValidationFactoryService.Result.fail("Validation Plant v1.1 preflight missing structure: " + id);
            }
        }
        return RseValidationFactoryService.Result.ok("Validation Plant v1.1 structure preflight passed.");
    }

    private static RseValidationFactoryService.Result placeModules(ServerLevel level, BlockPos origin) {
        for (ModuleSpec module : MODULES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    RedstoneEngineering.MOD_ID, "validation/plant/" + module.id());
            StructureTemplate template = level.getStructureManager().get(id).orElse(null);
            if (template == null) return RseValidationFactoryService.Result.fail("Missing plant module: " + id);
            BlockPos moduleOrigin = origin.offset(module.offset());
            boolean placed = template.placeInWorld(
                    level, moduleOrigin, moduleOrigin, new StructurePlaceSettings(), level.getRandom(), 2);
            if (!placed) return RseValidationFactoryService.Result.fail("Failed to place plant module: " + id);
        }
        return RseValidationFactoryService.Result.ok("Placed eight modular Validation Plant v1.1 structures.");
    }

    private static BlockPos cellOrigin(BlockPos plantOrigin, String cell) {
        ModuleSpec module = CELLS.get(cell);
        if (module == null) throw new IllegalArgumentException("unknown plant cell: " + cell);
        return plantOrigin.offset(module.offset());
    }

    private static boolean setReferencePower(ServerLevel level, BlockPos pos, int power) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock source)
                || !state.hasProperty(RedstoneReferenceSourceBlock.POWER)) return false;
        int bounded = Math.max(0, Math.min(15, power));
        if (state.getValue(RedstoneReferenceSourceBlock.POWER) == bounded) return true;
        BlockState next = state.setValue(RedstoneReferenceSourceBlock.POWER, bounded);
        level.setBlock(pos, next, 3);
        level.updateNeighborsAt(pos, source);
        level.updateNeighborsAt(pos.relative(next.getValue(RedstoneReferenceSourceBlock.FACING)), source);
        return true;
    }

    private static void updateCellPanels(
            ServerLevel level,
            BlockPos origin,
            Map<String, RseValidationSelfTestService.Evaluation> evaluations
    ) {
        BlockPos control = origin.offset(CONTROL_OFFSET);
        for (Map.Entry<String, RseValidationSelfTestService.Evaluation> entry : evaluations.entrySet()) {
            String cell = entry.getKey();
            RseValidationSelfTestService.Verdict verdict = entry.getValue().verdict();
            updatePanel(level, cellOrigin(origin, cell), LOCAL_WAIT_POWER, LOCAL_PASS_POWER, LOCAL_FAIL_POWER, verdict);
            updatePanel(level, control,
                    CONTROL_CELL_WAIT.get(cell), CONTROL_CELL_PASS.get(cell), CONTROL_CELL_FAIL.get(cell), verdict);
        }
    }

    private static void updateAllPanels(ServerLevel level, BlockPos origin, RseValidationSelfTestService.Verdict verdict) {
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> all = new LinkedHashMap<>();
        for (String cell : CELLS.keySet()) {
            all.put(cell, new RseValidationSelfTestService.Evaluation(verdict, "phase transition"));
        }
        updateCellPanels(level, origin, all);
        updateMasterPanel(level, origin, verdict);
    }

    private static void updateMasterPanel(ServerLevel level, BlockPos origin, RseValidationSelfTestService.Verdict verdict) {
        updatePanel(level, origin.offset(CONTROL_OFFSET), MASTER_WAIT_POWER, MASTER_PASS_POWER, MASTER_FAIL_POWER, verdict);
    }

    private static void updatePanel(
            ServerLevel level,
            BlockPos origin,
            BlockPos waitPower,
            BlockPos passPower,
            BlockPos failPower,
            RseValidationSelfTestService.Verdict verdict
    ) {
        setPanelPower(level, origin.offset(waitPower), verdict == RseValidationSelfTestService.Verdict.WAIT);
        setPanelPower(level, origin.offset(passPower), verdict == RseValidationSelfTestService.Verdict.PASS);
        setPanelPower(level, origin.offset(failPower), verdict == RseValidationSelfTestService.Verdict.FAIL);
    }

    private static void setPanelPower(ServerLevel level, BlockPos pos, boolean powered) {
        BlockState desired = powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (level.getBlockState(pos).is(desired.getBlock())) return;
        level.setBlock(pos, desired, 3);
        level.updateNeighborsAt(pos, desired.getBlock());
    }

    private static RseValidationSelfTestService.Evaluation pass(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.PASS, detail);
    }

    private static RseValidationSelfTestService.Evaluation waitFor(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.WAIT, detail);
    }

    private static RseValidationSelfTestService.Evaluation fail(String detail) {
        return new RseValidationSelfTestService.Evaluation(RseValidationSelfTestService.Verdict.FAIL, detail);
    }
}
