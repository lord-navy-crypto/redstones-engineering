package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalDomainSourceBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.block.QuartzTriggeredLapisSamplerBlock;
import dev.redstoneengineering.core.diagnostic.CoreMediaDiagnostics;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.RseLiveDiagnostics;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compact signal-processing teaching laboratory.
 *
 * <p>The building contains four isolated unit cells and one integrated chain. The unit cells
 * prove the real runtime behavior of the same four devices used in the integrated chain:
 * first-order Lapis low-pass filter, Quartz clock source, rising-edge sample-and-hold, and
 * Lapis-to-Redstone quantizer. Support sources, traces and readouts are fixtures rather than
 * additional signal-processing stages.</p>
 *
 * <p>The lab is intentionally self-stimulating after placement so one command can prove both
 * static topology and dynamic behavior. Unit LPF input steps 20 -> 80; integrated-chain input
 * steps 25 -> 75. Validation is observer-only except for those explicit scheduled stimuli.</p>
 */
public final class RseSignalProcessingLabService {
    private RseSignalProcessingLabService() {}

    public enum Verdict { WAIT, PASS, FAIL }
    public record StageResult(int stage, String name, Verdict verdict, String detail) {}

    private static final int STAGE_COUNT = 9;
    private static final int AUTO_INTERVAL_TICKS = 2;
    private static final int PASS_CONFIRM_SAMPLES = 6;
    private static final int MAX_RUN_LOG = 120;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final UUID owner;
        private final BlockPos origin;
        private final long placedTick;
        private final Verdict[] displayed = new Verdict[STAGE_COUNT + 1];
        private final Verdict[] announced = new Verdict[STAGE_COUNT + 1];
        private final int[] passStreak = new int[STAGE_COUNT + 1];

        private boolean unitLpfStepApplied;
        private boolean unitLpfDynamicSeen;

        private boolean unitOscBaseline;
        private int unitOscPrevious;
        private int unitOscRisingEdges;
        private long unitOscLastRisingTick = -1L;
        private boolean unitOscPeriodWitness;

        private boolean combinedStepApplied;
        private boolean combinedFilterDynamicSeen;

        private boolean combinedOscBaseline;
        private int combinedOscPrevious;
        private int combinedOscRisingEdges;
        private long combinedOscLastRisingTick = -1L;
        private boolean combinedOscPeriodWitness;

        private int lastCombinedCaptureCount;
        private boolean combinedPostStepCaptureSeen;

        private final String runId;
        private String lastCommand = "/rsevalidation signal place";
        private final ArrayDeque<String> runLog = new ArrayDeque<>();

        private Session(UUID owner, BlockPos origin, long placedTick) {
            this.owner = owner;
            this.origin = origin.immutable();
            this.placedTick = placedTick;
            this.runId = "signal-" + owner.toString().substring(0, 8) + "@" + origin.toShortString();
            for (int i = 1; i <= STAGE_COUNT; i++) {
                displayed[i] = Verdict.WAIT;
                announced[i] = Verdict.WAIT;
            }
        }
    }

    private static BlockPos p(int x, int y, int z) { return new BlockPos(x, y, z); }
    private static BlockPos at(Session s, BlockPos offset) { return s.origin.offset(offset); }

    // ---- Four isolated unit cells -------------------------------------------------------------

    // U1: source -> trace -> low-pass -> trace. The source automatically steps 20 -> 80.
    private static final BlockPos U1_SOURCE = p(0, 0, 0);
    private static final BlockPos U1_TRACE_IN = p(1, 0, 0);
    private static final BlockPos U1_FILTER = p(2, 0, 0);
    private static final BlockPos U1_TRACE_OUT = p(3, 0, 0);

    // U2: oscillator -> timing trace. Runtime edge timing is observed directly.
    private static final BlockPos U2_OSC = p(8, 0, 0);
    private static final BlockPos U2_TRACE = p(9, 0, 0);

    // U3: fixed precision source + dedicated oscillator -> sample-and-hold -> trace.
    private static final BlockPos U3_SOURCE = p(12, 0, 0);
    private static final BlockPos U3_TRACE_IN = p(13, 0, 0);
    private static final BlockPos U3_SAMPLER = p(14, 0, 0);
    private static final BlockPos U3_TRACE_OUT = p(15, 0, 0);
    private static final BlockPos U3_CLOCK = p(14, 0, -2);
    private static final BlockPos U3_CLOCK_TRACE = p(14, 0, -1);

    // U4: precision source -> trace -> quantizer -> visible Redstone indicator.
    private static final BlockPos U4_SOURCE = p(18, 0, 0);
    private static final BlockPos U4_TRACE_IN = p(19, 0, 0);
    private static final BlockPos U4_QUANTIZER = p(20, 0, 0);
    private static final BlockPos U4_INDICATOR = p(21, 0, 0);

    // ---- Integrated four-device chain ----------------------------------------------------------

    // Signal path: source -> LPF -> sampler -> quantizer -> readout.
    private static final BlockPos C_SOURCE = p(0, 0, 8);
    private static final BlockPos C_TRACE_A = p(1, 0, 8);
    private static final BlockPos C_FILTER = p(2, 0, 8);
    private static final BlockPos C_TRACE_B = p(3, 0, 8);
    private static final BlockPos C_SAMPLER = p(4, 0, 8);
    private static final BlockPos C_TRACE_C = p(5, 0, 8);
    private static final BlockPos C_QUANTIZER = p(6, 0, 8);
    private static final BlockPos C_INDICATOR = p(7, 0, 8);

    // Clock path enters the sampler's LEFT/NORTH trigger port directly; no divider is used.
    private static final BlockPos C_CLOCK = p(4, 0, 5);
    private static final BlockPos C_CLOCK_TRACE_A = p(4, 0, 6);
    private static final BlockPos C_CLOCK_TRACE_B = p(4, 0, 7);

    private static final BlockPos[] LAMPS = {
            null,
            p(2, 3, 0),   // S1 unit low-pass
            p(8, 3, 0),   // S2 unit oscillator
            p(14, 3, 0),  // S3 unit sampler
            p(20, 3, 0),  // S4 unit quantizer
            p(2, 3, 8),   // S5 integrated low-pass
            p(4, 3, 5),   // S6 integrated clock
            p(4, 3, 8),   // S7 integrated sampler
            p(6, 3, 8),   // S8 integrated quantizer
            p(9, 3, 8)    // S9 end-to-end chain
    };
    private static final BlockPos OVERALL_LAMP = p(11, 3, 8);

    public static RseValidationFactoryService.Result place(ServerPlayer player, BlockPos origin) {
        if (player == null) return RseValidationFactoryService.Result.fail("Signal lab requires a player.");
        if (origin == null) return RseValidationFactoryService.Result.fail("Signal lab origin missing.");
        ServerLevel level = player.serverLevel();
        if (level.getServer().overworld() != level) {
            return RseValidationFactoryService.Result.fail("Signal lab currently requires the overworld.");
        }

        clearAndBuildShell(level, origin);

        // U1 low-pass unit cell.
        set(level, origin, U1_SOURCE, lapisSource(Direction.EAST, 20));
        set(level, origin, U1_TRACE_IN, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, U1_FILTER, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)
                .setValue(LapisLowPassFilterBlock.ALPHA, 1));
        set(level, origin, U1_TRACE_OUT, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());

        // U2 oscillator unit cell: nominal T=8 ticks, J=0.
        set(level, origin, U2_OSC, quartzClock(Direction.EAST));
        set(level, origin, U2_TRACE, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        // U3 sample-and-hold unit cell. Sampler faces EAST, therefore LEFT trigger is NORTH.
        set(level, origin, U3_SOURCE, lapisSource(Direction.EAST, 63));
        set(level, origin, U3_TRACE_IN, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, U3_SAMPLER, sampler(Direction.EAST, Direction.WEST));
        set(level, origin, U3_TRACE_OUT, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, U3_CLOCK, quartzClock(Direction.SOUTH));
        set(level, origin, U3_CLOCK_TRACE, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        // U4 quantizer unit cell. 75/100 should become round(75*15/100)=11/15.
        set(level, origin, U4_SOURCE, lapisSource(Direction.EAST, 75));
        set(level, origin, U4_TRACE_IN, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, U4_QUANTIZER, quantizer(Direction.EAST, Direction.WEST));
        set(level, origin, U4_INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        // Integrated four-device chain. It begins at 25/100 and automatically steps to 75/100.
        set(level, origin, C_SOURCE, lapisSource(Direction.EAST, 25));
        set(level, origin, C_TRACE_A, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, C_FILTER, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)
                .setValue(LapisLowPassFilterBlock.ALPHA, 1));
        set(level, origin, C_TRACE_B, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, C_SAMPLER, sampler(Direction.EAST, Direction.WEST));
        set(level, origin, C_TRACE_C, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, C_QUANTIZER, quantizer(Direction.EAST, Direction.WEST));
        set(level, origin, C_INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));
        set(level, origin, C_CLOCK, quartzClock(Direction.SOUTH));
        set(level, origin, C_CLOCK_TRACE_A, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        set(level, origin, C_CLOCK_TRACE_B, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        // Recompute from every independent source so unit cells cannot accidentally depend on the
        // integrated chain or on a neighboring unit fixture.
        DomainNetwork.recomputeLapis(level, origin.offset(U1_SOURCE));
        DomainNetwork.recomputeQuartz(level, origin.offset(U2_OSC));
        DomainNetwork.recomputeLapis(level, origin.offset(U3_SOURCE));
        DomainNetwork.recomputeQuartz(level, origin.offset(U3_CLOCK));
        DomainNetwork.recomputeLapis(level, origin.offset(U4_SOURCE));
        DomainNetwork.recomputeLapis(level, origin.offset(C_SOURCE));
        DomainNetwork.recomputeQuartz(level, origin.offset(C_CLOCK));

        Session session = new Session(player.getUUID(), origin, level.getGameTime());
        session.lastCombinedCaptureCount = QuartzTriggeredLapisSamplerBlock.acceptedCaptures(
                level, origin.offset(C_SAMPLER));
        SESSIONS.put(player.getUUID(), session);
        paintAllWait(level, session);
        installLampLights(level, session);
        appendRunLog(session, level.getGameTime(),
                "RUN START | 4 unit cells + integrated four-device signal chain placed");
        publishValidationRun(level, session);

        return RseValidationFactoryService.Result.ok(
                "Placed RSE Signal Processing Laboratory.",
                "UNIT CELLS: S1 low-pass | S2 clock oscillator | S3 sample-and-hold | S4 quantizer.",
                "INTEGRATED: S5 LPF | S6 clock | S7 sampler | S8 quantizer | S9 end-to-end.",
                "U1 automatically steps 20 -> 80 to prove a real first-order response.",
                "Integrated chain automatically steps 25 -> 75 to prove filter -> sample -> quantize propagation.",
                "Math: LPF y[k]=0.75y[k-1]+0.25x[k] | clock T=8t | rising-edge hold | q=round(15x/100).",
                "Status: YELLOW=WAIT, LIME=PASS, RED=FAIL. Each PASS requires "
                        + PASS_CONFIRM_SAMPLES + " consecutive checks.",
                "Commands: /rsevalidation signal status | stage <1..9> | retest"
        );
    }

    public static RseValidationFactoryService.Result retest(ServerPlayer player) {
        Session s = session(player);
        if (s == null) {
            return RseValidationFactoryService.Result.fail(
                    "No signal lab session. Run /rsevalidation signal place.");
        }
        return place(player, s.origin);
    }

    public static RseValidationFactoryService.Result status(ServerPlayer player) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No signal lab session.");
        ServerLevel level = player.serverLevel();
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE SIGNAL PROCESSING LAB | " + overall(s)
                + " | origin=" + s.origin.toShortString()));
        for (int i = 1; i <= STAGE_COUNT; i++) {
            StageResult raw = evaluate(level, s, i);
            lines.add(component(new StageResult(i, raw.name(), s.displayed[i],
                    "raw=" + raw.verdict() + " confirm=" + s.passStreak[i] + "/"
                            + PASS_CONFIRM_SAMPLES + " | " + raw.detail())));
        }
        publishValidationRun(level, s);
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result stage(ServerPlayer player, int number) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No signal lab session.");
        if (number < 1 || number > STAGE_COUNT) {
            return RseValidationFactoryService.Result.fail("Signal lab stage must be 1.." + STAGE_COUNT + ".");
        }
        ServerLevel level = player.serverLevel();
        StageResult raw = evaluate(level, s, number);
        publishValidationRun(level, s);
        return new RseValidationFactoryService.Result(true, List.of(component(
                new StageResult(number, raw.name(), s.displayed[number],
                        "raw=" + raw.verdict() + " confirm=" + s.passStreak[number] + "/"
                                + PASS_CONFIRM_SAMPLES + " | " + raw.detail()))));
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null || level.getGameTime() % AUTO_INTERVAL_TICKS != 0L) return;

        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.owner);
            if (player == null || player.serverLevel() != level) continue;

            advanceStimuli(level, s);
            updateDynamicWitnesses(level, s);

            for (int stage = 1; stage <= STAGE_COUNT; stage++) {
                StageResult raw = evaluate(level, s, stage);
                updateDisplayed(level, s, raw);
            }
            paintOverall(level, s);
            publishValidationRun(level, s);
        }
    }

    private static void advanceStimuli(ServerLevel level, Session s) {
        long elapsed = level.getGameTime() - s.placedTick;

        if (!s.unitLpfStepApplied && elapsed >= 20L) {
            if (setSourceValue(level, at(s, U1_SOURCE), 80)) {
                s.unitLpfStepApplied = true;
                appendRunLog(s, level.getGameTime(),
                        "STIMULUS | U1 low-pass source step 20 -> 80");
            }
        }

        if (!s.combinedStepApplied && elapsed >= 40L) {
            if (setSourceValue(level, at(s, C_SOURCE), 75)) {
                s.combinedStepApplied = true;
                appendRunLog(s, level.getGameTime(),
                        "STIMULUS | integrated source step 25 -> 75");
            }
        }
    }

    private static void updateDynamicWitnesses(ServerLevel level, Session s) {
        LapisLowPassFilterBlock.FilterState u1 = LapisLowPassFilterBlock.filterState(level, at(s, U1_FILTER));
        if (s.unitLpfStepApplied && u1.valid() && u1.output() > 20 && u1.output() < 80) {
            s.unitLpfDynamicSeen = true;
        }

        LapisLowPassFilterBlock.FilterState combined = LapisLowPassFilterBlock.filterState(level, at(s, C_FILTER));
        if (s.combinedStepApplied && combined.valid() && combined.output() > 25 && combined.output() < 75) {
            s.combinedFilterDynamicSeen = true;
        }

        observeClock(level, s, at(s, U2_OSC), false);
        observeClock(level, s, at(s, C_CLOCK), true);

        int captures = QuartzTriggeredLapisSamplerBlock.acceptedCaptures(level, at(s, C_SAMPLER));
        if (s.combinedStepApplied && captures > s.lastCombinedCaptureCount) {
            int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(s, C_SAMPLER));
            if (held > 25) s.combinedPostStepCaptureSeen = true;
        }
        s.lastCombinedCaptureCount = captures;
    }

    private static void observeClock(ServerLevel level, Session s, BlockPos pos, boolean combined) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) return;
        int active = state.getValue(QuartzLabOscillatorBlock.ACTIVE) ? 1 : 0;
        long tick = level.getGameTime();

        if (combined) {
            if (!s.combinedOscBaseline) {
                s.combinedOscBaseline = true;
                s.combinedOscPrevious = active;
                return;
            }
            if (active == 1 && s.combinedOscPrevious == 0) {
                s.combinedOscRisingEdges++;
                if (s.combinedOscLastRisingTick >= 0L
                        && tick - s.combinedOscLastRisingTick == 8L) {
                    s.combinedOscPeriodWitness = true;
                }
                s.combinedOscLastRisingTick = tick;
            }
            s.combinedOscPrevious = active;
        } else {
            if (!s.unitOscBaseline) {
                s.unitOscBaseline = true;
                s.unitOscPrevious = active;
                return;
            }
            if (active == 1 && s.unitOscPrevious == 0) {
                s.unitOscRisingEdges++;
                if (s.unitOscLastRisingTick >= 0L
                        && tick - s.unitOscLastRisingTick == 8L) {
                    s.unitOscPeriodWitness = true;
                }
                s.unitOscLastRisingTick = tick;
            }
            s.unitOscPrevious = active;
        }
    }

    private static StageResult evaluate(ServerLevel level, Session s, int stage) {
        return switch (stage) {
            case 1 -> evaluateUnitFilter(level, s);
            case 2 -> evaluateUnitClock(level, s);
            case 3 -> evaluateUnitSampler(level, s);
            case 4 -> evaluateUnitQuantizer(level, s);
            case 5 -> evaluateCombinedFilter(level, s);
            case 6 -> evaluateCombinedClock(level, s);
            case 7 -> evaluateCombinedSampler(level, s);
            case 8 -> evaluateCombinedQuantizer(level, s);
            case 9 -> evaluateEndToEnd(level, s);
            default -> fail(stage, "Unknown", "invalid stage");
        };
    }

    private static StageResult evaluateUnitFilter(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, U1_FILTER));
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock)) {
            return fail(1, "Unit LPF", "low-pass filter missing");
        }
        if (state.getValue(LapisLowPassFilterBlock.ALPHA) != 1) {
            return fail(1, "Unit LPF", "alpha preset changed; expected alpha=0.25");
        }
        if (!(level.getBlockState(at(s, U1_SOURCE)).getBlock() instanceof LapisPrecisionSourceBlock)) {
            return fail(1, "Unit LPF", "precision source missing");
        }

        var f = LapisLowPassFilterBlock.filterState(level, at(s, U1_FILTER));
        if (!s.unitLpfStepApplied) {
            return waitFor(1, "Unit LPF",
                    "baseline=" + f.output() + "/100; waiting for automatic 20->80 step");
        }
        if (!f.valid() || f.quality() != PortQuality.VALID) {
            return waitFor(1, "Unit LPF", "waiting for VALID post-step evidence; quality=" + f.quality());
        }
        if (!s.unitLpfDynamicSeen) {
            return waitFor(1, "Unit LPF",
                    "step applied; waiting to observe an intermediate first-order response");
        }
        if (f.output() < 78 || f.output() > 80) {
            return waitFor(1, "Unit LPF",
                    "dynamic response witnessed; settling toward 80, y=" + f.output());
        }
        return pass(1, "Unit LPF",
                "y[k]=0.75y[k-1]+0.25x[k] | 20->80 step | intermediate response=YES | settled="
                        + f.output() + "/100");
    }

    private static StageResult evaluateUnitClock(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, U2_OSC));
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) {
            return fail(2, "Unit Clock", "Quartz oscillator missing");
        }
        if (state.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX) != 2
                || state.getValue(QuartzLabOscillatorBlock.JITTER) != 0) {
            return fail(2, "Unit Clock", "expected nominal period=8t and jitter=0");
        }
        var evidence = QuartzLabOscillatorBlock.timingEvidence(level, at(s, U2_OSC), state);
        if (!evidence.available()) {
            return waitFor(2, "Unit Clock", "waiting for first realized half-period");
        }
        if (s.unitOscRisingEdges < 3 || !s.unitOscPeriodWitness) {
            return waitFor(2, "Unit Clock",
                    "T=8t half=" + evidence.lastHalfInterval() + "t | observed rising edges="
                            + s.unitOscRisingEdges + " | waiting for repeated 8t spacing");
        }
        if (evidence.nominalPeriod() != 8 || evidence.lastHalfInterval() != 4
                || evidence.lastJitterOffset() != 0) {
            return fail(2, "Unit Clock",
                    "timing evidence inconsistent: nominal=" + evidence.nominalPeriod()
                            + " half=" + evidence.lastHalfInterval()
                            + " offset=" + evidence.lastJitterOffset());
        }
        return pass(2, "Unit Clock",
                "square wave T=8t | half-period=4t | jitter=0 | rising edges="
                        + s.unitOscRisingEdges + " | repeated period witness=YES");
    }

    private static StageResult evaluateUnitSampler(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, U3_SAMPLER));
        if (!(state.getBlock() instanceof QuartzTriggeredLapisSamplerBlock)) {
            return fail(3, "Unit Sample-and-Hold", "sampler missing");
        }
        int captures = QuartzTriggeredLapisSamplerBlock.acceptedCaptures(level, at(s, U3_SAMPLER));
        int rejected = QuartzTriggeredLapisSamplerBlock.rejectedCaptures(level, at(s, U3_SAMPLER));
        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(s, U3_SAMPLER));
        PortQuality quality = QuartzTriggeredLapisSamplerBlock.heldQuality(level, at(s, U3_SAMPLER));

        if (captures < 3 || quality != PortQuality.VALID) {
            return waitFor(3, "Unit Sample-and-Hold",
                    "captures=" + captures + " rejected=" + rejected + " held=" + held
                            + " quality=" + quality);
        }
        if (held != 63) {
            return fail(3, "Unit Sample-and-Hold",
                    "valid capture does not equal fixed 63/100 input; held=" + held);
        }
        if (rejected != 0) {
            return fail(3, "Unit Sample-and-Hold",
                    "clean unit fixture produced rejected captures=" + rejected);
        }
        return pass(3, "Unit Sample-and-Hold",
                "rising-edge captures=" + captures + " | held=63/100 between edges | rejected=0");
    }

    private static StageResult evaluateUnitQuantizer(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, U4_QUANTIZER));
        if (!(state.getBlock() instanceof LapisToRedstoneQuantizerBlock)) {
            return fail(4, "Unit Quantizer", "quantizer missing");
        }
        int power = state.getValue(LapisToRedstoneQuantizerBlock.POWER);
        PortQuality quality = LapisToRedstoneQuantizerBlock.outputQuality(level, at(s, U4_QUANTIZER));
        int indicator = indicatorLevel(level, at(s, U4_INDICATOR));
        int reconstructed = CoreMediaDiagnostics.lapisReconstructedFromRedstone(power);
        int error = CoreMediaDiagnostics.quantizationError(75);

        if (quality != PortQuality.VALID) {
            return waitFor(4, "Unit Quantizer", "waiting for VALID input evidence; quality=" + quality);
        }
        if (power != 11 || indicator != 11) {
            return waitFor(4, "Unit Quantizer",
                    "75/100 expected 11/15; quantizer=" + power + " display=" + indicator);
        }
        return pass(4, "Unit Quantizer",
                "q=round(15x/100) | x=75 -> q=11/15 | reconstructed=" + reconstructed
                        + "/100 | |eq|=" + error + "/100");
    }

    private static StageResult evaluateCombinedFilter(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, C_FILTER));
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock)) {
            return fail(5, "Integrated LPF", "integrated low-pass filter missing");
        }
        var f = LapisLowPassFilterBlock.filterState(level, at(s, C_FILTER));
        if (!s.combinedStepApplied) {
            return waitFor(5, "Integrated LPF",
                    "baseline=" + f.output() + "/100; waiting for automatic 25->75 step");
        }
        if (!f.valid() || f.quality() != PortQuality.VALID) {
            return waitFor(5, "Integrated LPF", "waiting for VALID post-step filter evidence");
        }
        if (!s.combinedFilterDynamicSeen) {
            return waitFor(5, "Integrated LPF", "waiting for intermediate 25<y<75 response");
        }
        if (f.output() < 73 || f.output() > 75) {
            return waitFor(5, "Integrated LPF",
                    "dynamic response witnessed; settling toward 75, y=" + f.output());
        }
        return pass(5, "Integrated LPF",
                "25->75 propagated through alpha=0.25 first-order filter | y=" + f.output());
    }

    private static StageResult evaluateCombinedClock(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, C_CLOCK));
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) {
            return fail(6, "Integrated Clock", "integrated oscillator missing");
        }
        var evidence = QuartzLabOscillatorBlock.timingEvidence(level, at(s, C_CLOCK), state);
        if (!evidence.available() || s.combinedOscRisingEdges < 3 || !s.combinedOscPeriodWitness) {
            return waitFor(6, "Integrated Clock",
                    "T=8t | edges=" + s.combinedOscRisingEdges
                            + " | periodWitness=" + (s.combinedOscPeriodWitness ? "YES" : "NO"));
        }
        if (evidence.nominalPeriod() != 8 || evidence.lastHalfInterval() != 4
                || evidence.lastJitterOffset() != 0) {
            return fail(6, "Integrated Clock", "integrated timing evidence inconsistent");
        }
        PortQuality traceQuality = QuartzTimingLineBlock.quality(level, at(s, C_CLOCK_TRACE_B));
        if (traceQuality != PortQuality.VALID) {
            return waitFor(6, "Integrated Clock", "clock trace quality=" + traceQuality);
        }
        return pass(6, "Integrated Clock",
                "T=8t clock reached sampler trigger | rising edges=" + s.combinedOscRisingEdges);
    }

    private static StageResult evaluateCombinedSampler(ServerLevel level, Session s) {
        if (!(level.getBlockState(at(s, C_SAMPLER)).getBlock()
                instanceof QuartzTriggeredLapisSamplerBlock)) {
            return fail(7, "Integrated Sample-and-Hold", "integrated sampler missing");
        }
        int captures = QuartzTriggeredLapisSamplerBlock.acceptedCaptures(level, at(s, C_SAMPLER));
        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(s, C_SAMPLER));
        PortQuality quality = QuartzTriggeredLapisSamplerBlock.heldQuality(level, at(s, C_SAMPLER));
        int filtered = LapisLowPassFilterBlock.filterState(level, at(s, C_FILTER)).output();

        if (!s.combinedStepApplied || !s.combinedPostStepCaptureSeen) {
            return waitFor(7, "Integrated Sample-and-Hold",
                    "captures=" + captures + " held=" + held
                            + " | waiting for a real post-step rising-edge capture");
        }
        if (quality != PortQuality.VALID) {
            return waitFor(7, "Integrated Sample-and-Hold", "held quality=" + quality);
        }
        if (held < 70 || Math.abs(filtered - held) > 3) {
            return waitFor(7, "Integrated Sample-and-Hold",
                    "filter=" + filtered + " held=" + held + " | waiting for settled sampled value");
        }
        return pass(7, "Integrated Sample-and-Hold",
                "post-step capture=YES | captures=" + captures + " | filter=" + filtered
                        + " -> held=" + held + "/100");
    }

    private static StageResult evaluateCombinedQuantizer(ServerLevel level, Session s) {
        BlockState state = level.getBlockState(at(s, C_QUANTIZER));
        if (!(state.getBlock() instanceof LapisToRedstoneQuantizerBlock)) {
            return fail(8, "Integrated Quantizer", "integrated quantizer missing");
        }
        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(s, C_SAMPLER));
        PortQuality heldQuality = QuartzTriggeredLapisSamplerBlock.heldQuality(level, at(s, C_SAMPLER));
        int expected = CoreMediaDiagnostics.redstoneFromLapis(held);
        int actual = state.getValue(LapisToRedstoneQuantizerBlock.POWER);
        int display = indicatorLevel(level, at(s, C_INDICATOR));
        PortQuality quality = LapisToRedstoneQuantizerBlock.outputQuality(level, at(s, C_QUANTIZER));

        if (!s.combinedPostStepCaptureSeen || heldQuality != PortQuality.VALID
                || quality != PortQuality.VALID) {
            return waitFor(8, "Integrated Quantizer",
                    "waiting for valid post-step sample and quantizer evidence");
        }
        if (actual != expected || display != actual) {
            return waitFor(8, "Integrated Quantizer",
                    "held=" + held + " expected=" + expected
                            + " actual=" + actual + " display=" + display);
        }
        return pass(8, "Integrated Quantizer",
                "held=" + held + "/100 -> round(15x/100)=" + actual
                        + "/15 | display=" + display);
    }

    private static StageResult evaluateEndToEnd(ServerLevel level, Session s) {
        StageResult f = evaluateCombinedFilter(level, s);
        StageResult c = evaluateCombinedClock(level, s);
        StageResult h = evaluateCombinedSampler(level, s);
        StageResult q = evaluateCombinedQuantizer(level, s);

        if (f.verdict() == Verdict.FAIL || c.verdict() == Verdict.FAIL
                || h.verdict() == Verdict.FAIL || q.verdict() == Verdict.FAIL) {
            return fail(9, "End-to-End Four-Device Chain",
                    "one or more integrated stages report a real failure");
        }
        if (f.verdict() != Verdict.PASS || c.verdict() != Verdict.PASS
                || h.verdict() != Verdict.PASS || q.verdict() != Verdict.PASS) {
            return waitFor(9, "End-to-End Four-Device Chain",
                    "waiting for LPF + clock + sampler + quantizer to all become valid");
        }

        int filtered = LapisLowPassFilterBlock.filterState(level, at(s, C_FILTER)).output();
        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(s, C_SAMPLER));
        int code = level.getBlockState(at(s, C_QUANTIZER))
                .getValue(LapisToRedstoneQuantizerBlock.POWER);
        if (code != 11) {
            return waitFor(9, "End-to-End Four-Device Chain",
                    "final code still settling; filtered=" + filtered + " held=" + held + " q=" + code);
        }

        return pass(9, "End-to-End Four-Device Chain",
                "25->75 source | LPF=" + filtered + "/100 | sampled=" + held
                        + "/100 | q=" + code + "/15 | all four real devices contributed");
    }

    private static void updateDisplayed(ServerLevel level, Session s, StageResult raw) {
        int i = raw.stage();
        Verdict next;
        if (raw.verdict() == Verdict.PASS) {
            s.passStreak[i] = Math.min(PASS_CONFIRM_SAMPLES, s.passStreak[i] + 1);
            next = s.passStreak[i] >= PASS_CONFIRM_SAMPLES ? Verdict.PASS : Verdict.WAIT;
        } else if (raw.verdict() == Verdict.FAIL) {
            s.passStreak[i] = 0;
            next = Verdict.FAIL;
        } else {
            s.passStreak[i] = 0;
            next = Verdict.WAIT;
        }
        s.displayed[i] = next;
        paintLamp(level, at(s, LAMPS[i]), next);

        if (next != Verdict.WAIT && next != s.announced[i]) {
            appendRunLog(s, level.getGameTime(),
                    String.format("S%02d %s | %s | raw=%s | %s",
                            i, next, raw.name(), raw.verdict(), raw.detail()));
        }
        s.announced[i] = next;
    }

    private static void paintAllWait(ServerLevel level, Session s) {
        for (int i = 1; i <= STAGE_COUNT; i++) {
            paintLamp(level, at(s, LAMPS[i]), Verdict.WAIT);
        }
        paintLamp(level, at(s, OVERALL_LAMP), Verdict.WAIT);
    }

    private static void installLampLights(ServerLevel level, Session s) {
        for (int i = 1; i <= STAGE_COUNT; i++) {
            BlockPos lamp = at(s, LAMPS[i]);
            level.setBlock(lamp.above(), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(at(s, OVERALL_LAMP).above(), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void paintOverall(ServerLevel level, Session s) {
        Verdict verdict = Verdict.PASS;
        for (int i = 1; i <= STAGE_COUNT; i++) {
            if (s.displayed[i] == Verdict.FAIL) {
                verdict = Verdict.FAIL;
                break;
            }
            if (s.displayed[i] != Verdict.PASS) verdict = Verdict.WAIT;
        }
        paintLamp(level, at(s, OVERALL_LAMP), verdict);
    }

    private static void paintLamp(ServerLevel level, BlockPos pos, Verdict verdict) {
        BlockState lamp = switch (verdict) {
            case PASS -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case FAIL -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case WAIT -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        };
        if (!level.getBlockState(pos).is(lamp.getBlock())) {
            level.setBlock(pos, lamp, Block.UPDATE_CLIENTS);
        }
    }

    private static String overall(Session s) {
        for (int i = 1; i <= STAGE_COUNT; i++) {
            if (s.displayed[i] == Verdict.FAIL) return "FAIL";
        }
        for (int i = 1; i <= STAGE_COUNT; i++) {
            if (s.displayed[i] != Verdict.PASS) return "WAIT";
        }
        return "PASS";
    }

    private static List<String> feedbackLines(ServerLevel level, Session s) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("SIGNAL LAB " + s.runId + " | origin=" + s.origin.toShortString());
        lines.add("OVERALL " + overall(s)
                + " | 4 unit validations + 4 integrated stage validations + 1 end-to-end validation");
        for (int i = 1; i <= STAGE_COUNT; i++) {
            StageResult raw = evaluate(level, s, i);
            lines.add(String.format("S%02d %s | raw=%s | confirm=%d/%d | %s | %s",
                    i, s.displayed[i], raw.verdict(), s.passStreak[i], PASS_CONFIRM_SAMPLES,
                    raw.name(), raw.detail()));
        }
        return List.copyOf(lines);
    }

    private static void publishValidationRun(ServerLevel level, Session s) {
        RseLiveDiagnostics.publishValidationRun(
                s.runId,
                s.lastCommand,
                overall(s),
                feedbackLines(level, s),
                List.copyOf(s.runLog),
                level.getGameTime()
        );
    }

    private static void appendRunLog(Session s, long tick, String line) {
        while (s.runLog.size() >= MAX_RUN_LOG) s.runLog.removeFirst();
        s.runLog.addLast("t=" + tick + " | " + line);
    }

    private static Session session(ServerPlayer player) {
        return player == null ? null : SESSIONS.get(player.getUUID());
    }

    private static Component component(StageResult result) {
        ChatFormatting color = switch (result.verdict()) {
            case PASS -> ChatFormatting.GREEN;
            case FAIL -> ChatFormatting.RED;
            case WAIT -> ChatFormatting.YELLOW;
        };
        return Component.literal("[RSE SIGNAL][" + result.verdict() + "][S"
                + String.format("%02d", result.stage()) + "] " + result.name()
                + " | " + result.detail()).withStyle(color);
    }

    private static StageResult pass(int stage, String name, String detail) {
        return new StageResult(stage, name, Verdict.PASS, detail);
    }

    private static StageResult fail(int stage, String name, String detail) {
        return new StageResult(stage, name, Verdict.FAIL, detail);
    }

    private static StageResult waitFor(int stage, String name, String detail) {
        return new StageResult(stage, name, Verdict.WAIT, detail);
    }

    private static BlockState lapisSource(Direction output, int value) {
        return RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(DirectionalDomainSourceBlock.FACING, output)
                .setValue(LapisPrecisionSourceBlock.VALUE, Math.max(0, Math.min(100, value)));
    }

    private static BlockState quartzClock(Direction output) {
        return RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(DirectionalDomainSourceBlock.FACING, output)
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 2)
                .setValue(QuartzLabOscillatorBlock.JITTER, 0)
                .setValue(QuartzLabOscillatorBlock.ACTIVE, false);
    }

    private static BlockState sampler(Direction output, Direction input) {
        return RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, output)
                .setValue(DirectionalDomainBlock.INPUT_FACING, input);
    }

    private static BlockState quantizer(Direction output, Direction input) {
        return RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get().defaultBlockState()
                .setValue(LapisToRedstoneQuantizerBlock.FACING, output)
                .setValue(LapisToRedstoneQuantizerBlock.INPUT_FACING, input)
                .setValue(LapisToRedstoneQuantizerBlock.POWER, 0);
    }

    private static boolean setSourceValue(ServerLevel level, BlockPos pos, int value) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisPrecisionSourceBlock)) return false;
        int bounded = Math.max(0, Math.min(100, value));
        level.setBlock(pos, state.setValue(LapisPrecisionSourceBlock.VALUE, bounded), Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeLapis(level, pos);
        return true;
    }

    private static int indicatorLevel(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof AnalogIndicatorBlock
                ? state.getValue(AnalogIndicatorBlock.LEVEL) : -1;
    }

    private static void set(ServerLevel level, BlockPos origin, BlockPos offset, BlockState state) {
        level.setBlock(origin.offset(offset), state, Block.UPDATE_ALL);
    }

    private static void clearAndBuildShell(ServerLevel level, BlockPos origin) {
        // 26x17 open laboratory hall. Functional blocks sit at y=0; validation lamps float at y=3.
        for (int x = -2; x <= 23; x++) {
            for (int z = -4; z <= 12; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = 0; y <= 5; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        // Floor zoning: four small unit bays in front and one long integrated bay behind them.
        for (int x = -1; x <= 22; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (int x = -1; x <= 12; x++) {
            for (int z = 5; z <= 10; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.CYAN_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (int x = -1; x <= 22; x++) {
            level.setBlock(origin.offset(x, -1, 4), Blocks.POLISHED_ANDESITE.defaultBlockState(), Block.UPDATE_ALL);
        }

        // A simple glass/quartz shell makes this read as a separate teaching laboratory building
        // without placing conductive or active blocks next to any engineering port.
        for (int x = -2; x <= 23; x++) {
            for (int y = 0; y <= 3; y++) {
                Block wall = y == 0 ? Blocks.QUARTZ_BLOCK : Blocks.GLASS;
                level.setBlock(origin.offset(x, y, -4), wall.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(origin.offset(x, y, 12), wall.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (int z = -3; z <= 11; z++) {
            for (int y = 0; y <= 3; y++) {
                Block wall = y == 0 ? Blocks.QUARTZ_BLOCK : Blocks.GLASS;
                level.setBlock(origin.offset(-2, y, z), wall.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(origin.offset(23, y, z), wall.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (int[] corner : new int[][]{{-2, -4}, {-2, 12}, {23, -4}, {23, 12}}) {
            for (int y = 0; y <= 5; y++) {
                level.setBlock(origin.offset(corner[0], y, corner[1]),
                        Blocks.QUARTZ_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
