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
 * Modular in-world commissioning plant layered above the small self-test yard.
 *
 * <p>The service may mutate only validation-owned stimulus sources, fixture structure blocks and
 * WAIT/PASS/FAIL panel power blocks. DUT outputs and runtime evidence are always observed from the
 * actual Minecraft world.</p>
 */
public final class RseValidationPlantService {
    private RseValidationPlantService() {}

    private static final int AUTO_INTERVAL_TICKS = 4;
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
        for (ModuleSpec module : MODULES) if (!module.cell().isBlank()) cells.put(module.cell(), module);
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
        if (level == null || origin == null) return RseValidationFactoryService.Result.fail("Validation plant place failed: level/origin missing");
        if (level.getServer().overworld() != level) return RseValidationFactoryService.Result.fail("Validation Plant v1 currently requires the overworld.");
        RseValidationFactoryService.Result preflight = preflight(level);
        if (!preflight.success()) return preflight;
        RseValidationFactoryService.Result placed = placeModules(level, origin);
        if (!placed.success()) return placed;

        RseValidationPlantSavedData.get(level).place(origin, level.getGameTime());
        applyBaseline(level, origin);
        updateAllPanels(level, origin, RseValidationSelfTestService.Verdict.WAIT);
        return RseValidationFactoryService.Result.ok(
                "Placed RSE Integrated Validation Plant v1.",
                "Six connected cells: A acquisition -> B conditioning -> C instrumentation -> D control -> E safety/fault -> F servo process.",
                "Central control room starts YELLOW WAIT; the plant acceptance sequence runs automatically.",
                "Use /rsevalidation plant status for evidence or the physical MASTER RETEST button to rebuild and rerun."
        );
    }

    public static RseValidationFactoryService.Result status(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Validation plant status failed: server level missing");
        RseValidationPlantSavedData.Placement placement = RseValidationPlantSavedData.get(level).placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Validation Plant v1 has not been placed.");
        Phase phase = phaseOf(placement.phase());
        long age = Math.max(0L, level.getGameTime() - placement.phaseStartedTick());
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = phase == Phase.PRECHECK
                ? precheckCells(level, placement.origin())
                : evaluateCells(level, placement.origin(), phase);

        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE Integrated Validation Plant v1 | phase=" + phase + " | phaseAge=" + age + "t"));
        for (Map.Entry<String, RseValidationSelfTestService.Evaluation> entry : cells.entrySet()) {
            lines.add(Component.literal("Cell " + entry.getKey() + " -> " + entry.getValue().verdict() + " | " + entry.getValue().detail()));
        }
        lines.add(Component.literal("Plant origin: " + placement.origin().toShortString()));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result retest(ServerLevel level) {
        if (level == null) return RseValidationFactoryService.Result.fail("Validation plant retest failed: server level missing");
        RseValidationPlantSavedData.Placement placement = RseValidationPlantSavedData.get(level).placement();
        if (placement == null) return RseValidationFactoryService.Result.fail("Validation Plant v1 has not been placed.");
        if (!rebuildForRetest(level, placement.origin(), false)) {
            return RseValidationFactoryService.Result.fail("Validation Plant v1 retest rebuild failed.");
        }
        return RseValidationFactoryService.Result.ok("Validation Plant v1 rebuilt; automatic acceptance restarted at PRECHECK.");
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
            if (!rebuildForRetest(level, origin, true)) updateMasterPanel(level, origin, RseValidationSelfTestService.Verdict.FAIL);
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

        int settleTicks = settleTicks(phase);
        if (phaseAge < settleTicks) {
            updateAllPanels(level, placement.origin(), RseValidationSelfTestService.Verdict.WAIT);
            return;
        }

        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> cells = phase == Phase.PRECHECK
                ? precheckCells(level, placement.origin())
                : evaluateCells(level, placement.origin(), phase);
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

    private static LinkedHashMap<String, RseValidationSelfTestService.Evaluation> precheckCells(ServerLevel level, BlockPos origin) {
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> result = new LinkedHashMap<>();
        result.put("A", blockCheck(level, cellOrigin(origin, "A").offset(2, 1, 6), RedstoneReferenceSourceBlock.class, "source present"));
        result.put("B", blockCheck(level, cellOrigin(origin, "B").offset(3, 1, 6), SignalConditionerBlock.class, "conditioner present"));
        result.put("C", blockCheck(level, cellOrigin(origin, "C").offset(3, 1, 6), SignalAnalyzerBlock.class, "inline analyzer present"));
        result.put("D", blockCheck(level, cellOrigin(origin, "D").offset(12, 1, 5), PwmControllerBlock.class, "PWM branch present"));
        result.put("E", blockCheck(level, cellOrigin(origin, "E").offset(9, 1, 9), SafetyInterlockBlock.class, "safety interlock present"));
        result.put("F", blockCheck(level, cellOrigin(origin, "F").offset(14, 1, 6), ServoActuatorBlock.class, "servo actuator present"));
        return result;
    }

    private static LinkedHashMap<String, RseValidationSelfTestService.Evaluation> evaluateCells(ServerLevel level, BlockPos origin, Phase phase) {
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
        result.put("E", evaluateSafety(level, cellOrigin(origin, "E"), conditioned, faulted, tripped));
        result.put("F", evaluateProcess(level, cellOrigin(origin, "F"), processCommand, tripped));
        return result;
    }

    private static RseValidationSelfTestService.Evaluation evaluateAcquisition(ServerLevel level, BlockPos origin, int expected) {
        BlockPos sourcePos = origin.offset(2, 1, 6);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (!(sourceState.getBlock() instanceof RedstoneReferenceSourceBlock source)) return fail("reference source missing");
        RseValidationSelfTestService.Evaluation sourceEvidence = exact(
                source.engineeringSnapshot(level, sourcePos, sourceState, Direction.EAST).orElse(null),
                expected, PortQuality.VALID, "source EAST");
        if (sourceEvidence.verdict() != RseValidationSelfTestService.Verdict.PASS) return sourceEvidence;

        BlockPos probePos = origin.offset(6, 1, 5);
        BlockState probeState = level.getBlockState(probePos);
        if (!(probeState.getBlock() instanceof SignalProbeBlock probe)) return fail("signal probe missing");
        return exact(probe.engineeringSnapshot(level, probePos, probeState, Direction.SOUTH).orElse(null),
                expected, PortQuality.VALID, "probe non-invasive measurement");
    }

    private static RseValidationSelfTestService.Evaluation evaluateConditioning(
            ServerLevel level, BlockPos origin, int expected, boolean saturated
    ) {
        BlockPos conditionerPos = origin.offset(3, 1, 6);
        BlockState conditionerState = level.getBlockState(conditionerPos);
        if (!(conditionerState.getBlock() instanceof SignalConditionerBlock conditioner)) return fail("signal conditioner missing");
        PortQuality expectedQuality = saturated ? PortQuality.SATURATED : PortQuality.VALID;
        RseValidationSelfTestService.Evaluation conditioned = exact(
                conditioner.engineeringSnapshot(level, conditionerPos, conditionerState, Direction.EAST).orElse(null),
                expected, expectedQuality, "conditioner x2 output");
        if (conditioned.verdict() != RseValidationSelfTestService.Verdict.PASS) return conditioned;

        BlockPos filterPos = origin.offset(4, 1, 6);
        BlockState filterState = level.getBlockState(filterPos);
        if (!(filterState.getBlock() instanceof PrecisionFilterBlock)) return fail("precision filter missing");
        int filtered = filterState.getValue(DirectionalSignalBlock.OUTPUT);
        if (filtered != expected) return fail("precision filter=" + filtered + " expected=" + expected);

        return indicator(level, origin.offset(8, 1, 5), expected, expectedQuality, "conditioned process indicator");
    }

    private static RseValidationSelfTestService.Evaluation evaluateInstrumentation(ServerLevel level, BlockPos origin, int expected) {
        BlockPos analyzerPos = origin.offset(3, 1, 6);
        BlockState analyzerState = level.getBlockState(analyzerPos);
        if (!(analyzerState.getBlock() instanceof SignalAnalyzerBlock)) return fail("inline signal analyzer missing");
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, analyzerPos);
        if (snapshot.totalSamples() < 2) return waitFor("inline analyzer samples=" + snapshot.totalSamples());
        if (snapshot.raw() != expected || snapshot.output() != expected) {
            return fail("inline analyzer raw/output=" + snapshot.raw() + "/" + snapshot.output() + " expected=" + expected);
        }
        return indicator(level, origin.offset(8, 1, 5), expected, PortQuality.VALID, "independent process indication");
    }

    private static RseValidationSelfTestService.Evaluation evaluateControl(ServerLevel level, BlockPos origin, int mainBusExpected) {
        BlockPos pwmPos = origin.offset(12, 1, 5);
        BlockState pwmState = level.getBlockState(pwmPos);
        if (!(pwmState.getBlock() instanceof PwmControllerBlock)) return fail("PWM controller missing");
        BlockPos analyzerPos = origin.offset(11, 1, 5);
        BlockState analyzerState = level.getBlockState(analyzerPos);
        if (!(analyzerState.getBlock() instanceof SignalAnalyzerBlock)) return fail("PWM analyzer missing");
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, analyzerPos);
        if (snapshot.totalSamples() < 4) return waitFor("PWM analyzer samples=" + snapshot.totalSamples());
        if (mainBusExpected < 15 && snapshot.peakToPeak() <= 0) return fail("PWM branch shows no switching activity");
        if (snapshot.raw() < 0 || snapshot.raw() > 15) return fail("PWM analyzer raw out of range=" + snapshot.raw());
        return pass("main analog bus=" + mainBusExpected + "; PWM branch sampled with peakToPeak=" + snapshot.peakToPeak());
    }

    private static RseValidationSelfTestService.Evaluation evaluateSafety(
            ServerLevel level, BlockPos origin, int mainInput, boolean faultExpected, boolean tripExpected
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
            return fail("fault injector active=" + FaultInjectorBlock.active(level, faultPos) + " expected=" + faultExpected);
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
            if (!AlarmProcessorBlock.unacknowledged(level, alarmPos)) return fail("trip alarm should be unacknowledged before recovery");
            if (alarmState.getValue(DirectionalSignalBlock.OUTPUT) != 10) return fail("severity-2 alarm output should be 10");
        } else if (alarmState.getValue(DirectionalSignalBlock.OUTPUT) != 0) {
            return fail("healthy alarm output should be 0");
        }
        return pass((faultExpected ? "fault quality propagated; " : "fault path healthy; ")
                + (tripExpected ? "interlock B trip + alarm latch" : "interlock permit + alarm clear"));
    }

    private static RseValidationSelfTestService.Evaluation evaluateProcess(
            ServerLevel level, BlockPos origin, int expectedCommand, boolean tripExpected
    ) {
        BlockPos servoPos = origin.offset(14, 1, 6);
        BlockState servoState = level.getBlockState(servoPos);
        if (!(servoState.getBlock() instanceof ServoActuatorBlock)) return fail("servo actuator missing");
        int command = ServoActuatorBlock.command(level, servoPos);
        int position = ServoActuatorBlock.position(level, servoPos);
        if (command != expectedCommand) return fail("servo command=" + command + " expected=" + expectedCommand);
        if (ServoActuatorBlock.braking(level, servoPos)) return fail("servo unexpectedly braking with valid command path");
        if (Math.abs(position - expectedCommand) > 1) {
            return waitFor("servo settling position=" + position + " target=" + expectedCommand);
        }

        BlockPos sensorPos = origin.offset(13, 1, 6);
        BlockState sensorState = level.getBlockState(sensorPos);
        if (!(sensorState.getBlock() instanceof ServoPositionSensorBlock sensor)) return fail("servo position sensor missing");
        if (ServoPositionSensorBlock.sourceQuality(level, sensorPos, sensorState) != PortQuality.VALID) {
            return fail("servo position sensor topology/quality invalid");
        }
        EngineeringPortSnapshot feedback = sensor.engineeringSnapshot(level, sensorPos, sensorState, Direction.WEST).orElse(null);
        if (feedback == null || feedback.quality() == PortQuality.STALE) return waitFor("servo feedback not yet fresh");
        if (feedback.quality() != PortQuality.VALID && feedback.quality() != PortQuality.SATURATED) {
            return fail("servo feedback quality=" + feedback.quality());
        }

        BlockState processLamp = level.getBlockState(origin.offset(16, 1, 7));
        if (!processLamp.hasProperty(BlockStateProperties.LIT) || !processLamp.getValue(BlockStateProperties.LIT)) {
            return fail("process command lamp is not lit");
        }

        BlockPos safetyIndicatorPos = origin.offset(14, 1, 9);
        BlockState safetyIndicatorState = level.getBlockState(safetyIndicatorPos);
        if (!(safetyIndicatorState.getBlock() instanceof AnalogIndicatorBlock safetyIndicator)) return fail("process safety indicator missing");
        AnalogIndicatorBlock.InputObservation safety = safetyIndicator.inputObservation(level, safetyIndicatorPos, safetyIndicatorState);
        int expectedPermit = tripExpected ? 0 : 15;
        if (safety.quality() == PortQuality.STALE) return waitFor("process safety permit indication stale");
        if (safety.value() != expectedPermit) return fail("process safety permit=" + safety.value() + " expected=" + expectedPermit);
        return pass("servo command/position=" + command + "/" + position + "; feedback=" + Math.round(feedback.value())
                + "; safetyPermit=" + expectedPermit);
    }

    private static RseValidationSelfTestService.Evaluation indicator(
            ServerLevel level, BlockPos pos, int expected, PortQuality expectedQuality, String label
    ) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogIndicatorBlock indicator)) return fail(label + " missing");
        AnalogIndicatorBlock.InputObservation observation = indicator.inputObservation(level, pos, state);
        if (observation.quality() == PortQuality.STALE) return waitFor(label + " STALE");
        if (observation.value() != expected) return fail(label + "=" + observation.value() + " expected=" + expected);
        if (observation.quality() != expectedQuality) return fail(label + " quality=" + observation.quality() + " expected=" + expectedQuality);
        return pass(label + "=" + expected + "/" + expectedQuality);
    }

    private static RseValidationSelfTestService.Evaluation exact(
            EngineeringPortSnapshot snapshot, int expected, PortQuality expectedQuality, String label
    ) {
        if (snapshot == null) return fail(label + " snapshot missing");
        if (snapshot.quality() == PortQuality.STALE) return waitFor(label + " STALE");
        int value = (int) Math.round(snapshot.value());
        if (value != expected) return fail(label + "=" + value + " expected=" + expected);
        if (snapshot.quality() != expectedQuality) return fail(label + " quality=" + snapshot.quality() + " expected=" + expectedQuality);
        return pass(label + "=" + expected + "/" + expectedQuality);
    }

    private static RseValidationSelfTestService.Evaluation blockCheck(
            ServerLevel level, BlockPos pos, Class<?> blockType, String detail
    ) {
        return blockType.isInstance(level.getBlockState(pos).getBlock()) ? pass(detail) : fail(detail + " missing at " + pos.toShortString());
    }

    private static void applyPhaseStimulus(ServerLevel level, BlockPos origin, Phase phase, long phaseAge) {
        int source = expectedSource(phase);
        BlockPos a = cellOrigin(origin, "A");
        BlockPos e = cellOrigin(origin, "E");
        setReferencePower(level, a.offset(2, 1, 6), source);
        setReferencePower(level, e.offset(14, 1, 5), phase == Phase.SENSOR_FAULT ? 15 : 0);
        setReferencePower(level, e.offset(9, 1, 10), phase == Phase.INTERLOCK_TRIP ? 0 : 15);
        setReferencePower(level, e.offset(10, 1, 3), phase == Phase.INTERLOCK_TRIP ? 15 : 0);

        int ack = 0;
        int reset = 0;
        if (phase == Phase.RECOVERY) {
            if (phaseAge < 4) ack = 15;
            else if (phaseAge >= 8 && phaseAge < 12) reset = 15;
        }
        setReferencePower(level, e.offset(9, 1, 4), ack);
        setReferencePower(level, e.offset(9, 1, 2), reset);
    }

    private static void applyBaseline(ServerLevel level, BlockPos origin) {
        BlockPos a = cellOrigin(origin, "A");
        BlockPos e = cellOrigin(origin, "E");
        setReferencePower(level, a.offset(2, 1, 6), 6);
        setReferencePower(level, e.offset(14, 1, 5), 0);
        setReferencePower(level, e.offset(10, 1, 9), 15);
        setReferencePower(level, e.offset(9, 1, 10), 15);
        setReferencePower(level, e.offset(9, 1, 8), 15);
        setReferencePower(level, e.offset(10, 1, 3), 0);
        setReferencePower(level, e.offset(9, 1, 4), 0);
        setReferencePower(level, e.offset(9, 1, 2), 0);
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
            case BIST, STARTUP, NOMINAL -> 24;
            case DISTURBANCE, SATURATION -> 28;
            case SENSOR_FAULT, INTERLOCK_TRIP -> 24;
            case RECOVERY -> 28;
            case FINAL_RUN, ACCEPTANCE -> 28;
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
            ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                    RedstoneEngineering.MOD_ID, "validation/plant/" + module.id());
            if (level.getStructureManager().get(resource).isEmpty()) {
                return RseValidationFactoryService.Result.fail("Validation Plant v1 preflight missing structure: " + resource);
            }
        }
        return RseValidationFactoryService.Result.ok("Validation Plant v1 structure preflight passed.");
    }

    private static RseValidationFactoryService.Result placeModules(ServerLevel level, BlockPos origin) {
        for (ModuleSpec module : MODULES) {
            ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                    RedstoneEngineering.MOD_ID, "validation/plant/" + module.id());
            StructureTemplate template = level.getStructureManager().get(resource).orElse(null);
            if (template == null) return RseValidationFactoryService.Result.fail("Missing plant module: " + resource);
            BlockPos moduleOrigin = origin.offset(module.offset());
            boolean placed = template.placeInWorld(
                    level, moduleOrigin, moduleOrigin, new StructurePlaceSettings(), level.getRandom(), 2);
            if (!placed) return RseValidationFactoryService.Result.fail("Failed to place plant module: " + resource);
        }
        return RseValidationFactoryService.Result.ok("Placed seven modular Validation Plant v1 structures.");
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
            BlockPos local = cellOrigin(origin, cell);
            updatePanel(level, local, LOCAL_WAIT_POWER, LOCAL_PASS_POWER, LOCAL_FAIL_POWER, verdict);
            updatePanel(level, control,
                    CONTROL_CELL_WAIT.get(cell), CONTROL_CELL_PASS.get(cell), CONTROL_CELL_FAIL.get(cell), verdict);
        }
    }

    private static void updateAllPanels(ServerLevel level, BlockPos origin, RseValidationSelfTestService.Verdict verdict) {
        LinkedHashMap<String, RseValidationSelfTestService.Evaluation> all = new LinkedHashMap<>();
        for (String cell : CELLS.keySet()) all.put(cell, new RseValidationSelfTestService.Evaluation(verdict, "phase transition"));
        updateCellPanels(level, origin, all);
        updateMasterPanel(level, origin, verdict);
    }

    private static void updateMasterPanel(ServerLevel level, BlockPos origin, RseValidationSelfTestService.Verdict verdict) {
        updatePanel(level, origin.offset(CONTROL_OFFSET), MASTER_WAIT_POWER, MASTER_PASS_POWER, MASTER_FAIL_POWER, verdict);
    }

    private static void updatePanel(
            ServerLevel level, BlockPos origin,
            BlockPos waitPower, BlockPos passPower, BlockPos failPower,
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
