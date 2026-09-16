package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.EdgeDetectorBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.LapisNoiseSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.instrument.InstrumentNetwork;
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

/** Places and automatically evaluates deterministic validation benches without owning device physics. */
public final class RseValidationSelfTestService {
    private RseValidationSelfTestService() {}

    public enum Verdict { WAIT, PASS, FAIL }

    public record Evaluation(Verdict verdict, String detail) {
        public Evaluation {
            if (verdict == null) throw new IllegalArgumentException("verdict required");
            detail = detail == null ? "" : detail;
        }
    }

    private record Definition(int settleTicks, BlockPos stimulus, BlockPos dut, BlockPos observe) {}

    private static final BlockPos WAIT_POWER = new BlockPos(8, 2, 2);
    private static final BlockPos PASS_POWER = new BlockPos(9, 2, 2);
    private static final BlockPos FAIL_POWER = new BlockPos(10, 2, 2);
    private static final BlockPos RETEST_BUTTON = new BlockPos(1, 1, 7);
    private static final int AUTO_INTERVAL_TICKS = 4;
    private static final int SELFTEST_WIDTH = 13;
    private static final int SELFTEST_HEIGHT = 4;
    private static final int SELFTEST_DEPTH = 9;

    private static final Map<String, Definition> TESTS;
    static {
        LinkedHashMap<String, Definition> tests = new LinkedHashMap<>();
        tests.put("01_basic/reference_source", def(2, 3,1,4, 3,1,4, 4,1,4));
        tests.put("01_basic/signal_probe", def(2, 3,1,4, 4,1,4, 4,1,4));
        tests.put("01_basic/signal_analyzer_tap", def(8, 3,1,4, 4,1,4, 4,1,4));
        tests.put("01_basic/signal_analyzer_inline", def(8, 3,1,4, 4,1,4, 6,1,4));
        tests.put("01_basic/analog_indicator", def(2, 3,1,4, 5,1,4, 5,1,4));
        tests.put("01_basic/signal_conditioner_gain", def(4, 3,1,4, 4,1,4, 5,1,4));
        tests.put("01_basic/directional_io", def(4, 2,1,3, 3,1,3, 4,1,3));
        tests.put("01_basic/instrument_bus", def(8, 2,1,4, 3,1,4, 5,1,4));
        tests.put("02_signal/conditioner_saturation", def(4, 3,1,4, 4,1,4, 5,1,4));
        tests.put("02_signal/precision_filter", def(12, 3,1,4, 4,1,4, 5,1,4));
        tests.put("02_signal/sample_hold", def(6, 3,1,4, 4,1,4, 5,1,4));
        tests.put("02_signal/edge_detector", def(4, 3,1,4, 4,1,4, 5,1,4));
        tests.put("02_signal/pulse_shaper", def(6, 2,1,4, 4,1,4, 5,1,4));
        tests.put("02_signal/pwm_control", def(20, 3,1,4, 4,1,4, 5,1,4));
        tests.put("02_signal/noise_vs_filter", def(12, 2,1,3, 3,1,5, 5,1,5));
        tests.put("02_signal/quantizer_scaler", def(10, 2,1,4, 4,1,4, 6,1,4));
        TESTS = Map.copyOf(tests);
    }

    private static Definition def(int settle, int sx,int sy,int sz, int dx,int dy,int dz, int ox,int oy,int oz) {
        return new Definition(settle, new BlockPos(sx,sy,sz), new BlockPos(dx,dy,dz), new BlockPos(ox,oy,oz));
    }

    public static RseValidationFactoryService.Result list(ServerLevel level) {
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE self-tests (WAIT=yellow, PASS=green, FAIL=red; automatic after placement):"));
        for (String id : TESTS.keySet()) lines.add(Component.literal(" - " + id));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result place(ServerLevel level, BlockPos origin, String testId) {
        String id = normalize(testId);
        Definition definition = TESTS.get(id);
        if (level == null || origin == null) return RseValidationFactoryService.Result.fail("Self-test place failed: level/origin missing");
        if (level.getServer().overworld() != level) return RseValidationFactoryService.Result.fail("Automatic self-tests currently require the overworld validation world.");
        if (definition == null) return RseValidationFactoryService.Result.fail("Unknown self-test: " + id);

        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                RedstoneEngineering.MOD_ID, "validation/selftest/" + id);
        StructureTemplate template = level.getStructureManager().get(resource).orElse(null);
        if (template == null) return RseValidationFactoryService.Result.fail("Missing self-test structure: " + resource);
        boolean placed = template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        if (!placed) return RseValidationFactoryService.Result.fail("Failed to place self-test: " + id);

        RseValidationSelfTestSavedData.get(level).put(id, origin, level.getGameTime());
        primeDynamicStimulus(level, origin, id);
        updatePanel(level, origin, Verdict.WAIT);
        return RseValidationFactoryService.Result.ok(
                "Placed self-test: " + id,
                "Automatic self-test armed: yellow WAIT will change to green PASS or red FAIL.",
                "Press the stone RETEST button on the bench to rebuild and run it again. /check remains a debug fallback."
        );
    }

    public static RseValidationFactoryService.Result check(ServerLevel level, String testId) {
        String id = normalize(testId);
        Definition definition = TESTS.get(id);
        if (level == null) return RseValidationFactoryService.Result.fail("Self-test check failed: server level missing");
        if (definition == null) return RseValidationFactoryService.Result.fail("Unknown self-test: " + id);
        RseValidationSelfTestSavedData.Placement placement = RseValidationSelfTestSavedData.get(level).placement(id);
        if (placement == null) return RseValidationFactoryService.Result.fail("Self-test has not been placed: " + id);

        Evaluation evaluation = evaluateWithSettle(level, placement, definition);
        updatePanel(level, placement.origin(), evaluation.verdict());
        return result(id, evaluation);
    }

    /** Called from the NeoForge server post-tick hook; commands are not required for normal validation. */
    public static void tickAll(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() % AUTO_INTERVAL_TICKS != 0) return;
        tickAutomatic(level);
    }

    /** Advances every loaded validation-owned bench and drives its physical WAIT/PASS/FAIL panel. */
    public static void tickAutomatic(ServerLevel level) {
        if (level == null) return;
        RseValidationSelfTestSavedData data = RseValidationSelfTestSavedData.get(level);
        for (RseValidationSelfTestSavedData.Placement snapshot : data.placements()) {
            Definition definition = TESTS.get(snapshot.testId());
            if (definition == null) continue;
            BlockPos origin = snapshot.origin();
            if (!level.hasChunkAt(origin) || !level.hasChunkAt(origin.offset(SELFTEST_WIDTH - 1, 0, SELFTEST_DEPTH - 1))) {
                continue;
            }

            RseValidationSelfTestSavedData.Placement placement = data.placement(snapshot.testId());
            if (placement == null) continue;
            boolean pressed = retestButtonPressed(level, origin);
            if (pressed && !placement.retestPressed()) {
                if (!rebuildForRetest(level, placement)) {
                    updatePanel(level, origin, Verdict.FAIL);
                }
                continue;
            }
            if (!pressed && placement.retestPressed()) {
                placement = data.setRetestPressed(placement.testId(), false);
                if (placement == null) continue;
            }

            Evaluation evaluation = evaluateWithSettle(level, placement, definition);
            updatePanel(level, origin, evaluation.verdict());
        }
    }

    private static boolean retestButtonPressed(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin.offset(RETEST_BUTTON));
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    private static boolean rebuildForRetest(ServerLevel level, RseValidationSelfTestSavedData.Placement placement) {
        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath(
                RedstoneEngineering.MOD_ID, "validation/selftest/" + placement.testId());
        StructureTemplate template = level.getStructureManager().get(resource).orElse(null);
        if (template == null) return false;

        BlockPos origin = placement.origin();
        for (int x = 0; x < SELFTEST_WIDTH; x++) {
            for (int y = 0; y < SELFTEST_HEIGHT; y++) {
                for (int z = 0; z < SELFTEST_DEPTH; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        boolean placed = template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        if (!placed) return false;

        RseValidationSelfTestSavedData.get(level).resetForRetest(placement.testId(), level.getGameTime(), true);
        primeDynamicStimulus(level, origin, placement.testId());
        updatePanel(level, origin, Verdict.WAIT);
        return true;
    }

    private static Evaluation evaluateWithSettle(
            ServerLevel level,
            RseValidationSelfTestSavedData.Placement placement,
            Definition definition
    ) {
        long age = Math.max(0L, level.getGameTime() - placement.placedTick());
        if (age < definition.settleTicks()) {
            return new Evaluation(Verdict.WAIT,
                    "settling " + age + "/" + definition.settleTicks() + " ticks");
        }
        return evaluate(level, placement, definition);
    }

    private static RseValidationFactoryService.Result result(String id, Evaluation evaluation) {
        String lamp = switch (evaluation.verdict()) {
            case WAIT -> "YELLOW WAIT";
            case PASS -> "GREEN PASS";
            case FAIL -> "RED FAIL";
        };
        return new RseValidationFactoryService.Result(
                evaluation.verdict() != Verdict.FAIL,
                List.of(Component.literal(id + " -> " + lamp + " | " + evaluation.detail()))
        );
    }

    private static Evaluation evaluate(ServerLevel level, RseValidationSelfTestSavedData.Placement placement, Definition definition) {
        String id = placement.testId();
        BlockPos origin = placement.origin();
        return switch (id) {
            case "01_basic/reference_source" -> evaluateReferenceSource(level, origin.offset(definition.dut()));
            case "01_basic/signal_probe" -> evaluateProbe(level, origin.offset(definition.dut()), 9);
            case "01_basic/signal_analyzer_tap" -> evaluateAnalyzer(level, origin.offset(definition.dut()), 7, false);
            case "01_basic/signal_analyzer_inline" -> evaluateAnalyzerInline(level, origin.offset(definition.dut()), origin.offset(definition.observe()), 7);
            case "01_basic/analog_indicator" -> evaluateIndicator(level, origin.offset(definition.observe()), 11);
            case "01_basic/signal_conditioner_gain" -> evaluateConditioner(level, origin.offset(definition.dut()), origin.offset(definition.observe()), 12, PortQuality.VALID);
            case "01_basic/directional_io" -> evaluateDirectional(level, origin);
            case "01_basic/instrument_bus" -> evaluateInstrumentBus(level, origin.offset(definition.observe()), 5);
            case "02_signal/conditioner_saturation" -> evaluateConditioner(level, origin.offset(definition.dut()), origin.offset(definition.observe()), 15, PortQuality.SATURATED);
            case "02_signal/precision_filter" -> evaluatePrecisionFilter(level, origin.offset(definition.dut()), origin.offset(definition.observe()), 7);
            case "02_signal/sample_hold" -> evaluateSampleHold(level, origin, definition);
            case "02_signal/edge_detector" -> evaluateEdge(level, origin, definition);
            case "02_signal/pulse_shaper" -> evaluatePulse(level, origin, definition);
            case "02_signal/pwm_control" -> evaluatePwm(level, origin, definition);
            case "02_signal/noise_vs_filter" -> evaluateNoiseFilter(level, placement);
            case "02_signal/quantizer_scaler" -> evaluateQuantizerRoundTrip(level, origin);
            default -> new Evaluation(Verdict.FAIL, "no evaluator registered");
        };
    }

    private static Evaluation evaluateReferenceSource(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock provider)) return fail("reference source missing");
        EngineeringPortSnapshot snapshot = provider.engineeringSnapshot(level, pos, state, Direction.EAST).orElse(null);
        return exact(snapshot, 7, PortQuality.VALID, "source EAST output");
    }

    private static Evaluation evaluateProbe(ServerLevel level, BlockPos pos, int expected) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalProbeBlock probe)) return fail("signal probe missing");
        EngineeringPortSnapshot snapshot = probe.engineeringSnapshot(level, pos, state, Direction.WEST).orElse(null);
        return exact(snapshot, expected, PortQuality.VALID, "probe TEST measurement");
    }

    private static Evaluation evaluateAnalyzer(ServerLevel level, BlockPos pos, int expected, boolean inline) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalAnalyzerBlock)) return fail("signal analyzer missing");
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, pos);
        if (snapshot.totalSamples() < 2) return waitFor("analyzer samples=" + snapshot.totalSamples());
        if (snapshot.raw() != expected) return fail("analyzer raw=" + snapshot.raw() + " expected=" + expected);
        if (inline && snapshot.output() != expected) return fail("inline output=" + snapshot.output() + " expected=" + expected);
        if (!inline && snapshot.output() != 0) return fail("TAP unexpectedly drives output=" + snapshot.output());
        return pass("raw=" + snapshot.raw() + " samples=" + snapshot.totalSamples());
    }

    private static Evaluation evaluateAnalyzerInline(ServerLevel level, BlockPos analyzerPos, BlockPos indicatorPos, int expected) {
        Evaluation analyzer = evaluateAnalyzer(level, analyzerPos, expected, true);
        if (analyzer.verdict() != Verdict.PASS) return analyzer;
        Evaluation indicator = evaluateIndicator(level, indicatorPos, expected);
        return indicator.verdict() == Verdict.PASS
                ? pass("INLINE raw/output/downstream=" + expected)
                : indicator;
    }

    private static Evaluation evaluateIndicator(ServerLevel level, BlockPos pos, int expected) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AnalogIndicatorBlock indicator)) return fail("analog indicator missing");
        AnalogIndicatorBlock.InputObservation observation = indicator.inputObservation(level, pos, state);
        if (observation.quality() == PortQuality.STALE) return waitFor("indicator evidence STALE");
        if (observation.quality() != PortQuality.VALID) return fail("indicator quality=" + observation.quality());
        if (observation.value() != expected) return fail("indicator=" + observation.value() + " expected=" + expected);
        return pass("indicator=" + observation.value() + " quality=VALID");
    }

    private static Evaluation evaluateConditioner(ServerLevel level, BlockPos conditionerPos, BlockPos indicatorPos,
                                                    int expected, PortQuality expectedQuality) {
        BlockState state = level.getBlockState(conditionerPos);
        if (!(state.getBlock() instanceof SignalConditionerBlock conditioner)) return fail("signal conditioner missing");
        EngineeringPortSnapshot output = conditioner.engineeringSnapshot(level, conditionerPos, state, Direction.EAST).orElse(null);
        Evaluation direct = exact(output, expected, expectedQuality, "conditioner output");
        if (direct.verdict() != Verdict.PASS) return direct;
        Evaluation indicator = evaluateIndicator(level, indicatorPos, expected);
        return indicator.verdict() == Verdict.PASS
                ? pass("conditioner=" + expected + " quality=" + expectedQuality + " downstream=" + expected)
                : indicator;
    }

    private static Evaluation evaluateDirectional(ServerLevel level, BlockPos origin) {
        Evaluation correct = evaluateIndicator(level, origin.offset(4,1,3), 8);
        if (correct.verdict() != Verdict.PASS) return correct;
        BlockState wrong = level.getBlockState(origin.offset(4,1,5));
        if (!(wrong.getBlock() instanceof SignalConditionerBlock) || !wrong.hasProperty(SignalConditionerBlock.OUTPUT)) {
            return fail("wrong-face conditioner missing");
        }
        int wrongOutput = wrong.getValue(SignalConditionerBlock.OUTPUT);
        return wrongOutput == 0
                ? pass("correct face=8; wrong face rejected with output=0")
                : fail("wrong face leaked output=" + wrongOutput);
    }

    private static Evaluation evaluateInstrumentBus(ServerLevel level, BlockPos cablePos, int expected) {
        InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, cablePos);
        if (snapshot.qualityForMask(1) == PortQuality.STALE) return waitFor("instrument bus STALE");
        if (snapshot.qualityForMask(1) != PortQuality.VALID) return fail("instrument bus quality=" + snapshot.qualityForMask(1));
        return snapshot.valid(0) && snapshot.valueOr(0, -1) == expected
                ? pass("channel A=" + expected + " integrity=" + snapshot.integrity())
                : fail("channel A=" + snapshot.valueOr(0, -1) + " expected=" + expected);
    }

    private static Evaluation evaluatePrecisionFilter(ServerLevel level, BlockPos filterPos, BlockPos indicatorPos, int expected) {
        BlockState state = level.getBlockState(filterPos);
        if (!(state.getBlock() instanceof PrecisionFilterBlock) || !state.hasProperty(PrecisionFilterBlock.OUTPUT)) {
            return fail("precision filter missing");
        }
        if (!PrecisionFilterBlock.settled(level, filterPos, state)) {
            return waitFor("precision filter lag=" + PrecisionFilterBlock.lag(level, filterPos, state));
        }
        if (state.getValue(PrecisionFilterBlock.OUTPUT) != expected) {
            return fail("precision filter output=" + state.getValue(PrecisionFilterBlock.OUTPUT) + " expected=" + expected);
        }
        return evaluateIndicator(level, indicatorPos, expected);
    }

    private static Evaluation evaluateSampleHold(ServerLevel level, BlockPos origin, Definition definition) {
        BlockPos dut = origin.offset(definition.dut());
        BlockState state = level.getBlockState(dut);
        if (!(state.getBlock() instanceof SampleHoldBlock) || !state.hasProperty(SampleHoldBlock.OUTPUT)) return fail("sample/hold missing");
        if (SampleHoldBlock.captureCount(level, dut) <= 0) {
            BlockPos trigger = dut.relative(Direction.NORTH);
            if (!level.getBlockState(trigger).is(Blocks.REDSTONE_BLOCK)) {
                level.setBlock(trigger, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                return waitFor("trigger edge injected; automatic runner is waiting for capture");
            }
            return waitFor("waiting for sample capture");
        }
        int output = state.getValue(SampleHoldBlock.OUTPUT);
        return output == 6 ? pass("captures=" + SampleHoldBlock.captureCount(level, dut) + " held=6")
                : fail("held=" + output + " expected=6");
    }

    private static Evaluation evaluateEdge(ServerLevel level, BlockPos origin, Definition definition) {
        BlockPos dut = origin.offset(definition.dut());
        BlockState state = level.getBlockState(dut);
        if (!(state.getBlock() instanceof EdgeDetectorBlock)) return fail("edge detector missing");
        if (!EdgeDetectorBlock.initialized(level, dut)) return waitFor("edge detector not initialized");
        if (EdgeDetectorBlock.edgeCount(level, dut) > 0) {
            return pass("edges=" + EdgeDetectorBlock.edgeCount(level, dut) + " lastAge=" + EdgeDetectorBlock.lastEdgeAgeTicks(level, dut) + "t");
        }
        if (setReferencePower(level, origin.offset(definition.stimulus()), 7)) {
            return waitFor("rising edge injected; automatic runner is waiting for evidence");
        }
        return fail("no rising edge recorded after stimulus");
    }

    private static Evaluation evaluatePulse(ServerLevel level, BlockPos origin, Definition definition) {
        BlockPos analyzerPos = origin.offset(definition.observe());
        BlockState analyzerState = level.getBlockState(analyzerPos);
        if (!(analyzerState.getBlock() instanceof SignalAnalyzerBlock)) return fail("pulse observer analyzer missing");
        SignalAnalyzerBlock.UiSnapshot snapshot = SignalAnalyzerBlock.uiSnapshot(level, analyzerPos);
        if (snapshot.totalSamples() >= 3 && snapshot.lifeMax() == 15 && snapshot.changes() > 0) {
            return pass("observer saw 0→15 pulse; changes=" + snapshot.changes());
        }
        if (setReferencePower(level, origin.offset(definition.stimulus()), 7)) {
            return waitFor("pulse stimulus injected; automatic runner is waiting for analyzer samples");
        }
        return snapshot.totalSamples() < 3
                ? waitFor("pulse observer samples=" + snapshot.totalSamples())
                : fail("pulse observer never saw HIGH; max=" + snapshot.lifeMax());
    }

    private static Evaluation evaluatePwm(ServerLevel level, BlockPos origin, Definition definition) {
        BlockPos dut = origin.offset(definition.dut());
        BlockState state = level.getBlockState(dut);
        if (!(state.getBlock() instanceof PwmControllerBlock pwm)) return fail("PWM controller missing");
        PwmControllerBlock.PwmAssessment assessment = pwm.assessment(level, dut, state);
        if (assessment.command() != 8 || assessment.inhibited()) return fail("PWM command/inhibit mismatch");
        BlockPos analyzerPos = origin.offset(definition.observe());
        if (!(level.getBlockState(analyzerPos).getBlock() instanceof SignalAnalyzerBlock)) return fail("PWM observer analyzer missing");
        SignalAnalyzerBlock.UiSnapshot observer = SignalAnalyzerBlock.uiSnapshot(level, analyzerPos);
        if (observer.totalSamples() < 8) return waitFor("PWM observer samples=" + observer.totalSamples());
        if (observer.lifeMin() == 0 && observer.lifeMax() == 15 && observer.changes() > 0) {
            return pass("PWM requested=" + assessment.requestedDutyPermille()/10.0 + "% realized=" + assessment.effectiveDutyPermille()/10.0 + "%");
        }
        return fail("PWM observer range=" + observer.lifeMin() + ".." + observer.lifeMax() + " changes=" + observer.changes());
    }

    private static Evaluation evaluateNoiseFilter(ServerLevel level, RseValidationSelfTestSavedData.Placement placement) {
        BlockPos origin = placement.origin();
        BlockPos rawSourcePos = origin.offset(2,1,3);
        BlockPos filterPos = origin.offset(3,1,5);
        BlockState rawSourceState = level.getBlockState(rawSourcePos);
        if (!(rawSourceState.getBlock() instanceof LapisNoiseSourceBlock)) return fail("raw noise source missing");
        if (!(level.getBlockState(filterPos).getBlock() instanceof LapisLowPassFilterBlock)) return fail("low-pass filter missing");
        if (!LapisNoiseSourceBlock.sampleInitialized(level, rawSourcePos) || !LapisLowPassFilterBlock.runtimePresent(level, filterPos)) {
            return waitFor("noise/filter runtime not initialized");
        }
        int raw = LapisNoiseSourceBlock.currentValue(level, rawSourcePos, rawSourceState);
        LapisLowPassFilterBlock.FilterState filtered = LapisLowPassFilterBlock.filterState(level, filterPos);
        if (!filtered.valid() || filtered.quality() != PortQuality.VALID) return fail("filtered quality=" + filtered.quality());
        RseValidationSelfTestSavedData.Placement evidence = RseValidationSelfTestSavedData.get(level)
                .observeNoise(placement.testId(), raw, filtered.output());
        if (evidence == null || evidence.checkCount() < 6) {
            return waitFor("collecting automatic noise window " + (evidence == null ? 0 : evidence.checkCount()) + "/6");
        }
        return evidence.filteredPeakToPeak() < evidence.rawPeakToPeak()
                ? pass("raw P-P=" + evidence.rawPeakToPeak() + " filtered P-P=" + evidence.filteredPeakToPeak())
                : fail("filter did not reduce sampled P-P: raw=" + evidence.rawPeakToPeak() + " filtered=" + evidence.filteredPeakToPeak());
    }

    private static Evaluation evaluateQuantizerRoundTrip(ServerLevel level, BlockPos origin) {
        BlockPos scalerPos = origin.offset(3,1,4);
        BlockPos quantizerPos = origin.offset(5,1,4);
        BlockPos indicatorPos = origin.offset(6,1,4);
        if (!(level.getBlockState(scalerPos).getBlock() instanceof RedstoneToLapisScalerBlock)) return fail("scaler missing");
        BlockState quantizerState = level.getBlockState(quantizerPos);
        if (!(quantizerState.getBlock() instanceof LapisToRedstoneQuantizerBlock)
                || !quantizerState.hasProperty(LapisToRedstoneQuantizerBlock.POWER)) return fail("quantizer missing");
        PortQuality scalerQuality = RedstoneToLapisScalerBlock.outputQuality(level, scalerPos);
        PortQuality quantizerQuality = LapisToRedstoneQuantizerBlock.outputQuality(level, quantizerPos);
        if (scalerQuality == PortQuality.STALE || quantizerQuality == PortQuality.STALE) return waitFor("converter runtime STALE");
        if (scalerQuality != PortQuality.VALID || quantizerQuality != PortQuality.VALID) {
            return fail("converter quality scaler=" + scalerQuality + " quantizer=" + quantizerQuality);
        }
        int output = quantizerState.getValue(LapisToRedstoneQuantizerBlock.POWER);
        if (Math.abs(output - 9) > 1) return fail("round-trip output=" + output + " expected≈9");
        Evaluation indicator = evaluateIndicator(level, indicatorPos, output);
        return indicator.verdict() == Verdict.PASS ? pass("round-trip 9→" + output + " within ±1") : indicator;
    }

    private static Evaluation exact(EngineeringPortSnapshot snapshot, int expected, PortQuality expectedQuality, String label) {
        if (snapshot == null) return fail(label + " snapshot missing");
        if (snapshot.quality() == PortQuality.STALE) return waitFor(label + " STALE");
        if (snapshot.quality() != expectedQuality) return fail(label + " quality=" + snapshot.quality() + " expected=" + expectedQuality);
        int value = (int) Math.round(snapshot.value());
        return value == expected ? pass(label + "=" + value + " quality=" + snapshot.quality())
                : fail(label + "=" + value + " expected=" + expected);
    }

    private static void primeDynamicStimulus(ServerLevel level, BlockPos origin, String id) {
        if (id.equals("02_signal/edge_detector") || id.equals("02_signal/pulse_shaper")) {
            Definition definition = TESTS.get(id);
            setReferencePower(level, origin.offset(definition.stimulus()), 0);
        }
    }

    private static boolean setReferencePower(ServerLevel level, BlockPos pos, int power) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneReferenceSourceBlock source)
                || !state.hasProperty(RedstoneReferenceSourceBlock.POWER)) return false;
        int bounded = Math.max(0, Math.min(15, power));
        if (state.getValue(RedstoneReferenceSourceBlock.POWER) == bounded) return false;
        level.setBlock(pos, state.setValue(RedstoneReferenceSourceBlock.POWER, bounded), 3);
        level.updateNeighborsAt(pos, source);
        level.updateNeighborsAt(pos.relative(state.getValue(RedstoneReferenceSourceBlock.FACING)), source);
        return true;
    }

    public static void updatePanel(ServerLevel level, BlockPos origin, Verdict verdict) {
        setPower(level, origin.offset(WAIT_POWER), verdict == Verdict.WAIT);
        setPower(level, origin.offset(PASS_POWER), verdict == Verdict.PASS);
        setPower(level, origin.offset(FAIL_POWER), verdict == Verdict.FAIL);
    }

    private static void setPower(ServerLevel level, BlockPos pos, boolean powered) {
        BlockState desired = powered ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (level.getBlockState(pos) == desired) return;
        level.setBlock(pos, desired, 3);
        level.updateNeighborsAt(pos, powered ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Evaluation waitFor(String detail) { return new Evaluation(Verdict.WAIT, detail); }
    private static Evaluation pass(String detail) { return new Evaluation(Verdict.PASS, detail); }
    private static Evaluation fail(String detail) { return new Evaluation(Verdict.FAIL, detail); }
}
