package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystPiezoPickupBlock;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.block.AmethystSpectrumAnalyzerBlock;
import dev.redstoneengineering.block.AmethystTunedResonatorBlock;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalDomainSourceBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzTriggeredLapisSamplerBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.core.diagnostic.CoreMediaDiagnostics;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.PrecisionObservationSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Player-facing four-domain demonstration bench.
 *
 * <p>The builder intentionally uses real RSE blocks and their authoritative runtime physics.
 * It never writes fake pass values into device state. The validation loop is observer-only except
 * for explicit user stimuli such as setpoint changes and Amethyst excitation.</p>
 */
public final class RseFourDomainDemoService {
    private RseFourDomainDemoService() {}

    public enum Verdict { WAIT, PASS, FAIL }

    public record StageResult(int stage, String name, Verdict verdict, String detail) {}

    private static final int STAGE_COUNT = 9;
    private static final int AUTO_INTERVAL_TICKS = 4;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final UUID owner;
        private final BlockPos origin;
        private final Verdict[] announced = new Verdict[STAGE_COUNT + 1];
        private boolean amethystResonanceSeen;
        private boolean piezoSeen;

        private Session(UUID owner, BlockPos origin) {
            this.owner = owner;
            this.origin = origin.immutable();
            for (int i = 1; i <= STAGE_COUNT; i++) announced[i] = Verdict.WAIT;
        }
    }

    // Main sampled-data control chain, y=0, z=0.
    private static final BlockPos LAPIS_SOURCE = p(0, 0, 0);
    private static final BlockPos LAPIS_TRACE_A = p(1, 0, 0);
    private static final BlockPos LAPIS_FILTER = p(2, 0, 0);
    private static final BlockPos LAPIS_TRACE_B = p(3, 0, 0);
    private static final BlockPos SAMPLER = p(4, 0, 0);
    private static final BlockPos LAPIS_TRACE_C = p(5, 0, 0);
    private static final BlockPos QUANTIZER = p(6, 0, 0);
    private static final BlockPos SETPOINT_CABLE = p(7, 0, 0);
    private static final BlockPos PID = p(8, 0, 0);
    private static final BlockPos CONTROL_CABLE = p(9, 0, 0);
    private static final BlockPos SERVO = p(10, 0, 0);
    private static final BlockPos POSITION_SENSOR = p(11, 0, 0);
    private static final BlockPos SETPOINT_INDICATOR = p(7, 0, 1);

    // Feedback path from sensor NORTH output back to PID NORTH process-value input.
    private static final BlockPos FEEDBACK_1 = p(11, 0, -1);
    private static final BlockPos FEEDBACK_2 = p(10, 0, -1);
    private static final BlockPos FEEDBACK_3 = p(9, 0, -1);
    private static final BlockPos FEEDBACK_4 = p(8, 0, -1);

    // Quartz clock branch into sampler NORTH trigger.
    private static final BlockPos QUARTZ_OSC = p(4, 0, -4);
    private static final BlockPos QUARTZ_TRACE_A = p(4, 0, -3);
    private static final BlockPos QUARTZ_DIVIDER = p(4, 0, -2);
    private static final BlockPos QUARTZ_TRACE_B = p(4, 0, -1);

    // Amethyst resonance -> piezo -> Redstone measurement branch.
    private static final BlockPos AMETHYST_SOURCE = p(0, 0, 4);
    private static final BlockPos AMETHYST_TRACE_A = p(1, 0, 4);
    private static final BlockPos AMETHYST_TUNED = p(2, 0, 4);
    private static final BlockPos AMETHYST_TRACE_B = p(3, 0, 4);
    private static final BlockPos AMETHYST_FILTER = p(4, 0, 4);
    private static final BlockPos AMETHYST_TRACE_C = p(5, 0, 4);
    private static final BlockPos PIEZO = p(6, 0, 4);
    private static final BlockPos PIEZO_INDICATOR = p(7, 0, 4);
    private static final BlockPos SPECTRUM = p(4, 0, 6);

    /** Stable node ids for per-block field diagnostics. */
    private static final Map<String, BlockPos> NODES = nodeOffsets();

    private static Map<String, BlockPos> nodeOffsets() {
        LinkedHashMap<String, BlockPos> nodes = new LinkedHashMap<>();
        nodes.put("lapis_source", LAPIS_SOURCE);
        nodes.put("lapis_trace_a", LAPIS_TRACE_A);
        nodes.put("lapis_filter", LAPIS_FILTER);
        nodes.put("lapis_trace_b", LAPIS_TRACE_B);
        nodes.put("sampler", SAMPLER);
        nodes.put("lapis_trace_c", LAPIS_TRACE_C);
        nodes.put("quantizer", QUANTIZER);
        nodes.put("setpoint_cable", SETPOINT_CABLE);
        nodes.put("setpoint_indicator", SETPOINT_INDICATOR);
        nodes.put("pid", PID);
        nodes.put("control_cable", CONTROL_CABLE);
        nodes.put("servo", SERVO);
        nodes.put("position_sensor", POSITION_SENSOR);
        nodes.put("feedback_1", FEEDBACK_1);
        nodes.put("feedback_2", FEEDBACK_2);
        nodes.put("feedback_3", FEEDBACK_3);
        nodes.put("feedback_4", FEEDBACK_4);
        nodes.put("quartz_osc", QUARTZ_OSC);
        nodes.put("quartz_trace_a", QUARTZ_TRACE_A);
        nodes.put("quartz_divider", QUARTZ_DIVIDER);
        nodes.put("quartz_trace_b", QUARTZ_TRACE_B);
        nodes.put("amethyst_source", AMETHYST_SOURCE);
        nodes.put("amethyst_trace_a", AMETHYST_TRACE_A);
        nodes.put("amethyst_tuned", AMETHYST_TUNED);
        nodes.put("amethyst_trace_b", AMETHYST_TRACE_B);
        nodes.put("amethyst_filter", AMETHYST_FILTER);
        nodes.put("amethyst_trace_c", AMETHYST_TRACE_C);
        nodes.put("piezo", PIEZO);
        nodes.put("piezo_indicator", PIEZO_INDICATOR);
        nodes.put("spectrum", SPECTRUM);
        return Map.copyOf(nodes);
    }

    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private static BlockPos at(Session session, BlockPos offset) {
        return session.origin.offset(offset);
    }

    public static RseValidationFactoryService.Result place(ServerPlayer player, BlockPos origin) {
        if (player == null) return RseValidationFactoryService.Result.fail("Four-domain demo requires a player.");
        ServerLevel level = player.serverLevel();
        if (level.getServer().overworld() != level) {
            return RseValidationFactoryService.Result.fail("Four-domain demo currently requires the overworld.");
        }
        if (origin == null) return RseValidationFactoryService.Result.fail("Four-domain demo origin missing.");

        clearAndFloor(level, origin);

        // Lapis: precision source -> trace -> LPF -> trace -> quartz sampler -> trace -> quantizer.
        set(level, origin, LAPIS_SOURCE, RedstoneEngineering.LAPIS_PRECISION_SOURCE.get().defaultBlockState()
                .setValue(DirectionalDomainSourceBlock.FACING, Direction.EAST)
                .setValue(LapisPrecisionSourceBlock.VALUE, 75));
        set(level, origin, LAPIS_TRACE_A, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, LAPIS_FILTER, RedstoneEngineering.LAPIS_LOW_PASS_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)
                .setValue(LapisLowPassFilterBlock.ALPHA, 1));
        set(level, origin, LAPIS_TRACE_B, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, SAMPLER, RedstoneEngineering.QUARTZ_TRIGGERED_LAPIS_SAMPLER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST));
        set(level, origin, LAPIS_TRACE_C, RedstoneEngineering.LAPIS_SIGNAL_LINE.get().defaultBlockState());
        set(level, origin, QUANTIZER, RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER.get().defaultBlockState()
                .setValue(LapisToRedstoneQuantizerBlock.FACING, Direction.EAST)
                .setValue(LapisToRedstoneQuantizerBlock.INPUT_FACING, Direction.WEST));

        // Redstone controller + mechatronic plant.
        set(level, origin, SETPOINT_CABLE, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, PID, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(PidControllerBlock.TUNING, 1));
        set(level, origin, CONTROL_CABLE, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, SERVO, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.EAST)
                .setValue(ServoActuatorBlock.SLEW, 0));
        set(level, origin, POSITION_SENSOR, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.NORTH)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST));

        set(level, origin, FEEDBACK_1, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, FEEDBACK_2, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, FEEDBACK_3, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, FEEDBACK_4, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, SETPOINT_INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.SOUTH));

        // Quartz: 8 tick oscillator -> divide by 2 -> sampler trigger = 16 tick sample cadence.
        set(level, origin, QUARTZ_OSC, RedstoneEngineering.QUARTZ_LAB_OSCILLATOR.get().defaultBlockState()
                .setValue(DirectionalDomainSourceBlock.FACING, Direction.SOUTH)
                .setValue(QuartzLabOscillatorBlock.PERIOD_INDEX, 2)
                .setValue(QuartzLabOscillatorBlock.JITTER, 0)
                .setValue(QuartzLabOscillatorBlock.ACTIVE, false));
        set(level, origin, QUARTZ_TRACE_A, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());
        set(level, origin, QUARTZ_DIVIDER, RedstoneEngineering.QUARTZ_CLOCK_DIVIDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.SOUTH)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.NORTH)
                .setValue(QuartzClockDividerBlock.DIV_INDEX, 0));
        set(level, origin, QUARTZ_TRACE_B, RedstoneEngineering.QUARTZ_TIMING_LINE.get().defaultBlockState());

        // Amethyst: impulse resonance -> tuned response -> exact filter -> piezo pickup -> Redstone indicator.
        set(level, origin, AMETHYST_SOURCE, RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState()
                .setValue(AmethystResonatorBlock.FREQUENCY, 8)
                .setValue(AmethystResonatorBlock.AMPLITUDE, 12));
        set(level, origin, AMETHYST_TRACE_A, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        set(level, origin, AMETHYST_TUNED, RedstoneEngineering.AMETHYST_TUNED_RESONATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)
                .setValue(AmethystTunedResonatorBlock.NATURAL, 8)
                .setValue(AmethystTunedResonatorBlock.Q_INDEX, 2));
        set(level, origin, AMETHYST_TRACE_B, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        set(level, origin, AMETHYST_FILTER, RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)
                .setValue(AmethystFrequencyFilterBlock.TARGET, 8));
        set(level, origin, AMETHYST_TRACE_C, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        set(level, origin, PIEZO, RedstoneEngineering.AMETHYST_PIEZO_PICKUP.get().defaultBlockState()
                .setValue(AmethystPiezoPickupBlock.FACING, Direction.EAST)
                .setValue(AmethystPiezoPickupBlock.INPUT_FACING, Direction.WEST));
        set(level, origin, PIEZO_INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));
        set(level, origin, SPECTRUM, RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER.get().defaultBlockState());

        // Force initial topology discovery without inventing any measurement value.
        DomainNetwork.recomputeLapis(level, origin.offset(LAPIS_SOURCE));
        DomainNetwork.recomputeQuartz(level, origin.offset(QUARTZ_OSC));
        DomainNetwork.recomputeAmethyst(level, origin.offset(AMETHYST_SOURCE));

        Session session = new Session(player.getUUID(), origin);
        SESSIONS.put(player.getUUID(), session);

        // One real impulse proves the Amethyst conversion path; the user can re-run it at any time.
        exciteInternal(level, session);

        return RseValidationFactoryService.Result.ok(
                "Placed RSE four-domain demonstration bench.",
                "Origin: " + origin.toShortString(),
                "Main chain: Lapis -> Quartz-triggered sample -> Redstone PID -> Servo -> position feedback.",
                "Resonance chain: Amethyst source -> tuned resonator -> exact filter -> piezo pickup -> Redstone indicator.",
                "Automatic stage checker armed. PASS/FAIL transitions will appear in your chat.",
                "Per-block diagnostics: /rsevalidation demo nodes | node <id>",
                "Commands: /rsevalidation demo status | stage <1..9> | setpoint <0..100> | excite | retest"
        );
    }

    public static RseValidationFactoryService.Result retest(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session. Run /rsevalidation demo place.");
        return place(player, session.origin);
    }

    public static RseValidationFactoryService.Result setpoint(ServerPlayer player, int value) {
        Session session = session(player);
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session.");
        int bounded = Math.max(0, Math.min(100, value));
        ServerLevel level = player.serverLevel();
        BlockPos pos = at(session, LAPIS_SOURCE);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisPrecisionSourceBlock)) {
            return RseValidationFactoryService.Result.fail("Lapis source missing at " + pos.toShortString());
        }
        level.setBlock(pos, state.setValue(LapisPrecisionSourceBlock.VALUE, bounded), Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeLapis(level, pos);
        resetAnnouncements(session, 1, 7);
        return RseValidationFactoryService.Result.ok(
                "Demo setpoint changed to " + bounded + "/100.",
                "Lapis filter/sample/quantizer/PID/Servo stages will automatically re-evaluate."
        );
    }

    public static RseValidationFactoryService.Result excite(ServerPlayer player) {
        Session session = session(player);
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session.");
        session.amethystResonanceSeen = false;
        session.piezoSeen = false;
        resetAnnouncements(session, 8, 9);
        exciteInternal(player.serverLevel(), session);
        return RseValidationFactoryService.Result.ok(
                "Amethyst impulse injected at f=8, peak A=12.",
                "Stages 8-9 re-armed; watch chat for resonance and piezo conversion PASS/FAIL."
        );
    }

    private static void exciteInternal(ServerLevel level, Session session) {
        BlockPos sourcePos = at(session, AMETHYST_SOURCE);
        BlockState source = level.getBlockState(sourcePos);
        if (!(source.getBlock() instanceof AmethystResonatorBlock)) return;
        AmethystResonatorBlock.excite(level, sourcePos, source);
        level.scheduleTick(sourcePos, source.getBlock(), 2);
        DomainNetwork.recomputeAmethyst(level, sourcePos);
    }

    public static RseValidationFactoryService.Result nodes(ServerPlayer player) {
        Session session = session(player);
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session.");
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE demo node ids (" + NODES.size() + "):"));
        for (String id : NODES.keySet()) lines.add(Component.literal(" - " + id));
        return new RseValidationFactoryService.Result(true, lines);
    }

    /**
     * Observer-only per-block dump. It reports the actual block at the expected coordinate and
     * every engineering port snapshot exposed by that block, so a field failure can be pasted
     * back verbatim without guessing which boundary lost value or quality.
     */
    public static RseValidationFactoryService.Result node(ServerPlayer player, String nodeId) {
        Session session = session(player);
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session.");
        String id = nodeId == null ? "" : nodeId.trim().toLowerCase(java.util.Locale.ROOT);
        BlockPos offset = NODES.get(id);
        if (offset == null) {
            return RseValidationFactoryService.Result.fail(
                    "Unknown demo node: " + id,
                    "Run /rsevalidation demo nodes for valid node ids.");
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = at(session, offset);
        BlockState state = level.getBlockState(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[RSE DEMO NODE] " + id + " @ " + pos.toShortString() + " | block=" + blockId));

        if (state.getBlock() instanceof EngineeringPortProvider provider) {
            var ports = provider.engineeringPorts(state);
            if (ports.isEmpty()) {
                lines.add(Component.literal(" ports=0"));
            } else {
                for (var port : ports) {
                    EngineeringPortSnapshot snapshot =
                            provider.engineeringSnapshot(level, pos, state, port.side()).orElse(null);
                    if (snapshot == null) {
                        lines.add(Component.literal(
                                " " + port.label()
                                        + " side=" + port.side().getName().toUpperCase()
                                        + " domain=" + port.domain()
                                        + " dir=" + port.direction()
                                        + " snapshot=MISSING"));
                    } else {
                        lines.add(Component.literal(
                                " " + port.label()
                                        + " side=" + port.side().getName().toUpperCase()
                                        + " domain=" + port.domain()
                                        + " dir=" + port.direction()
                                        + " value=" + String.format(java.util.Locale.ROOT, "%.2f", snapshot.value())
                                        + " range=[" + String.format(java.util.Locale.ROOT, "%.2f", snapshot.minimum())
                                        + "," + String.format(java.util.Locale.ROOT, "%.2f", snapshot.maximum()) + "]"
                                        + " quality=" + snapshot.quality()));
                    }
                }
            }
        } else {
            lines.add(Component.literal(" block does not expose EngineeringPortProvider diagnostics"));
        }

        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result status(ServerPlayer player) {
        Session session = session(player);
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session.");
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE four-domain demo status @ " + session.origin.toShortString()));
        for (int stage = 1; stage <= STAGE_COUNT; stage++) lines.add(component(evaluate(player.serverLevel(), session, stage)));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result stage(ServerPlayer player, int stage) {
        Session session = session(player);
        if (session == null) return RseValidationFactoryService.Result.fail("No four-domain demo session.");
        if (stage < 1 || stage > STAGE_COUNT) return RseValidationFactoryService.Result.fail("Stage must be 1.." + STAGE_COUNT);
        return new RseValidationFactoryService.Result(true, List.of(component(evaluate(player.serverLevel(), session, stage))));
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() % AUTO_INTERVAL_TICKS != 0) return;

        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.owner);
            if (player == null || player.serverLevel() != level) continue;

            updateHistoricalWitnesses(level, session);
            for (int stage = 1; stage <= STAGE_COUNT; stage++) {
                StageResult result = evaluate(level, session, stage);
                if (result.verdict() == Verdict.WAIT) continue;
                if (session.announced[stage] == result.verdict()) continue;
                session.announced[stage] = result.verdict();
                player.sendSystemMessage(component(result));
            }
        }
    }

    private static void updateHistoricalWitnesses(ServerLevel level, Session session) {
        if (AmethystResonanceDustBlock.status(level, at(session, AMETHYST_TRACE_C))
                == AmethystResonanceDustBlock.ResonanceStatus.ACTIVE) {
            session.amethystResonanceSeen = true;
        }
        BlockState pickup = level.getBlockState(at(session, PIEZO));
        if (pickup.getBlock() instanceof AmethystPiezoPickupBlock
                && pickup.getValue(AmethystPiezoPickupBlock.POWER) > 0
                && AmethystPiezoPickupBlock.outputQuality(level, at(session, PIEZO)) == PortQuality.VALID) {
            session.piezoSeen = true;
        }
    }

    private static StageResult evaluate(ServerLevel level, Session session, int stage) {
        return switch (stage) {
            case 1 -> evaluateLapisSource(level, session);
            case 2 -> evaluateLapisFilter(level, session);
            case 3 -> evaluateQuartz(level, session);
            case 4 -> evaluateSampler(level, session);
            case 5 -> evaluateQuantizer(level, session);
            case 6 -> evaluatePid(level, session);
            case 7 -> evaluateServo(level, session);
            case 8 -> evaluateAmethyst(level, session);
            case 9 -> evaluatePiezo(level, session);
            default -> new StageResult(stage, "unknown", Verdict.FAIL, "unregistered stage");
        };
    }

    private static StageResult evaluateLapisSource(ServerLevel level, Session session) {
        BlockPos pos = at(session, LAPIS_SOURCE);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisPrecisionSourceBlock)) return fail(1, "Lapis source", "block missing");
        int value = state.getValue(LapisPrecisionSourceBlock.VALUE);
        return pass(1, "Lapis source", "value=" + value + "/100 OUT=EAST");
    }

    private static StageResult evaluateLapisFilter(ServerLevel level, Session session) {
        BlockPos pos = at(session, LAPIS_FILTER);
        if (!(level.getBlockState(pos).getBlock() instanceof LapisLowPassFilterBlock)) return fail(2, "Lapis low-pass", "block missing");
        LapisLowPassFilterBlock.FilterState state = LapisLowPassFilterBlock.filterState(level, pos);
        if (state.quality() == PortQuality.STALE || !state.valid()) {
            return waitFor(2, "Lapis low-pass", "quality=" + state.quality() + " output=" + state.output());
        }
        if (state.quality() != PortQuality.VALID) return fail(2, "Lapis low-pass", "quality=" + state.quality());
        return pass(2, "Lapis low-pass", "alpha=0.25 output=" + state.output() + "/100 quality=VALID");
    }

    private static StageResult evaluateQuartz(ServerLevel level, Session session) {
        BlockPos trace = at(session, QUARTZ_TRACE_B);
        var clock = PrecisionObservationSupport.quartz(level, trace);
        if (clock.quality() == PortQuality.STALE || clock.quality() == PortQuality.NO_SIGNAL) {
            return waitFor(3, "Quartz clock", "quality=" + clock.quality() + " period=" + clock.periodTicks());
        }
        if (!clock.valid()) return fail(3, "Quartz clock", "quality=" + clock.quality());
        if (clock.periodTicks() != 16) return fail(3, "Quartz clock", "period=" + clock.periodTicks() + " expected=16");
        return pass(3, "Quartz clock", "8t oscillator /2 -> sample period=16t");
    }

    private static StageResult evaluateSampler(ServerLevel level, Session session) {
        BlockPos pos = at(session, SAMPLER);
        if (!(level.getBlockState(pos).getBlock() instanceof QuartzTriggeredLapisSamplerBlock)) return fail(4, "Quartz/Lapis sampler", "block missing");
        PortQuality quality = QuartzTriggeredLapisSamplerBlock.heldQuality(level, pos);
        int accepted = QuartzTriggeredLapisSamplerBlock.acceptedCaptures(level, pos);
        int value = QuartzTriggeredLapisSamplerBlock.heldValue(level, pos);
        if (accepted <= 0 || quality == PortQuality.STALE || quality == PortQuality.NO_SIGNAL) {
            return waitFor(4, "Quartz/Lapis sampler", "captures=" + accepted + " held=" + value + " quality=" + quality);
        }
        if (quality != PortQuality.VALID) return fail(4, "Quartz/Lapis sampler", "held quality=" + quality);
        return pass(4, "Quartz/Lapis sampler", "captures=" + accepted + " held=" + value + "/100");
    }

    private static StageResult evaluateQuantizer(ServerLevel level, Session session) {
        BlockPos pos = at(session, QUANTIZER);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LapisToRedstoneQuantizerBlock)) return fail(5, "Lapis -> Redstone", "quantizer missing");
        PortQuality quality = LapisToRedstoneQuantizerBlock.outputQuality(level, pos);
        if (quality == PortQuality.STALE || quality == PortQuality.NO_SIGNAL) {
            return waitFor(5, "Lapis -> Redstone", "quality=" + quality + " output=" + state.getValue(LapisToRedstoneQuantizerBlock.POWER));
        }
        if (quality != PortQuality.VALID) return fail(5, "Lapis -> Redstone", "quality=" + quality);
        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(session, SAMPLER));
        int expected = CoreMediaDiagnostics.redstoneFromLapis(held);
        int actual = state.getValue(LapisToRedstoneQuantizerBlock.POWER);
        if (actual != expected) return fail(5, "Lapis -> Redstone", "output=" + actual + " expected=" + expected + " from held=" + held);
        return pass(5, "Lapis -> Redstone", held + "/100 -> " + actual + "/15 quality=VALID");
    }

    private static StageResult evaluatePid(ServerLevel level, Session session) {
        BlockPos pos = at(session, PID);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PidControllerBlock controller)) return fail(6, "PID control", "block missing");
        EngineeringPortSnapshot snapshot = controller.engineeringSnapshot(level, pos, state, Direction.EAST).orElse(null);
        if (snapshot == null) return fail(6, "PID control", "output snapshot missing");
        if (snapshot.quality() == PortQuality.STALE || snapshot.quality() == PortQuality.NO_SIGNAL) {
            return waitFor(6, "PID control", "output=" + state.getValue(DirectionalSignalBlock.OUTPUT) + " quality=" + snapshot.quality());
        }
        if (snapshot.quality() != PortQuality.VALID) return fail(6, "PID control", "quality=" + snapshot.quality());
        return pass(6, "PID control", "output=" + state.getValue(DirectionalSignalBlock.OUTPUT)
                + "/15 target=" + PidControllerBlock.actuatorTarget(level, pos)
                + " slewLimited=" + PidControllerBlock.slewLimitActive(level, pos));
    }

    private static StageResult evaluateServo(ServerLevel level, Session session) {
        BlockPos servoPos = at(session, SERVO);
        BlockPos sensorPos = at(session, POSITION_SENSOR);
        if (!(level.getBlockState(servoPos).getBlock() instanceof ServoActuatorBlock)) return fail(7, "Servo feedback", "servo missing");
        BlockState sensorState = level.getBlockState(sensorPos);
        if (!(sensorState.getBlock() instanceof ServoPositionSensorBlock)) return fail(7, "Servo feedback", "position sensor missing");
        PortQuality sourceQuality = ServoPositionSensorBlock.sourceQuality(level, sensorPos, sensorState);
        if (sourceQuality == PortQuality.STALE || sourceQuality == PortQuality.NO_SIGNAL) {
            return waitFor(7, "Servo feedback", "sensor quality=" + sourceQuality);
        }
        if (sourceQuality != PortQuality.VALID) return fail(7, "Servo feedback", "sensor quality=" + sourceQuality);
        int position = ServoActuatorBlock.position(level, servoPos);
        int setpoint = level.getBlockState(at(session, QUANTIZER)).getValue(LapisToRedstoneQuantizerBlock.POWER);
        if (Math.abs(position - setpoint) > 2) {
            return waitFor(7, "Servo feedback", "position=" + position + " target≈" + setpoint + " moving/settling");
        }
        return pass(7, "Servo feedback", "position=" + position + " target=" + setpoint + " sensor=VALID");
    }

    private static StageResult evaluateAmethyst(ServerLevel level, Session session) {
        BlockPos trace = at(session, AMETHYST_TRACE_C);
        var status = AmethystResonanceDustBlock.status(level, trace);
        if (status == AmethystResonanceDustBlock.ResonanceStatus.FREQUENCY_CONFLICT) {
            return fail(8, "Amethyst resonance", "frequency conflict at filtered trace");
        }
        if (status == AmethystResonanceDustBlock.ResonanceStatus.STALE) {
            return waitFor(8, "Amethyst resonance", "coverage STALE");
        }
        if (status == AmethystResonanceDustBlock.ResonanceStatus.ACTIVE) session.amethystResonanceSeen = true;
        if (!session.amethystResonanceSeen) {
            return waitFor(8, "Amethyst resonance", "idle; run /rsevalidation demo excite");
        }
        return pass(8, "Amethyst resonance", "f=8 passed tuned resonator + exact filter");
    }

    private static StageResult evaluatePiezo(ServerLevel level, Session session) {
        BlockPos pos = at(session, PIEZO);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AmethystPiezoPickupBlock)) return fail(9, "Amethyst piezo conversion", "pickup missing");
        PortQuality quality = AmethystPiezoPickupBlock.outputQuality(level, pos);
        int power = state.getValue(AmethystPiezoPickupBlock.POWER);
        if (power > 0 && quality == PortQuality.VALID) session.piezoSeen = true;
        if (quality == PortQuality.TOPOLOGY_ERROR || quality == PortQuality.FAULT) {
            return fail(9, "Amethyst piezo conversion", "quality=" + quality);
        }
        if (!session.piezoSeen) {
            return waitFor(9, "Amethyst piezo conversion", "waiting for resonance envelope; current=" + power + " quality=" + quality);
        }
        return pass(9, "Amethyst piezo conversion", "resonance amplitude converted to Redstone; last f="
                + AmethystPiezoPickupBlock.lastFrequency(level, pos) + " current=" + power + "/15 quality=" + quality);
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

    private static Component component(StageResult result) {
        ChatFormatting color = switch (result.verdict()) {
            case PASS -> ChatFormatting.GREEN;
            case FAIL -> ChatFormatting.RED;
            case WAIT -> ChatFormatting.YELLOW;
        };
        return Component.literal("[RSE DEMO][" + result.verdict() + "][S" + result.stage() + "] "
                + result.name() + " | " + result.detail()).withStyle(color);
    }

    private static Session session(ServerPlayer player) {
        return player == null ? null : SESSIONS.get(player.getUUID());
    }

    private static void resetAnnouncements(Session session, int first, int last) {
        for (int i = Math.max(1, first); i <= Math.min(STAGE_COUNT, last); i++) {
            session.announced[i] = Verdict.WAIT;
        }
    }

    private static void set(ServerLevel level, BlockPos origin, BlockPos offset, BlockState state) {
        level.setBlock(origin.offset(offset), state, Block.UPDATE_ALL);
    }

    private static void clearAndFloor(ServerLevel level, BlockPos origin) {
        for (int x = -1; x <= 13; x++) {
            for (int z = -5; z <= 7; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = 0; y <= 3; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
