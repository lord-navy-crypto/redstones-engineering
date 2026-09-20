package dev.redstoneengineering.validation;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystPiezoPickupBlock;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystTunedResonatorBlock;
import dev.redstoneengineering.block.AirCompressorBlock;
import dev.redstoneengineering.block.AirReservoirBlock;
import dev.redstoneengineering.block.AnalogIndicatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalDomainSourceBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.block.LapisPrecisionSourceBlock;
import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.PidControllerBlock;
import dev.redstoneengineering.block.PneumaticCylinderBlock;
import dev.redstoneengineering.block.PneumaticFlowMeterBlock;
import dev.redstoneengineering.block.PneumaticReceiverBlock;
import dev.redstoneengineering.block.PressureRegulatorBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzToRedstoneReceiverBlock;
import dev.redstoneengineering.block.QuartzTriggeredLapisSamplerBlock;
import dev.redstoneengineering.block.RedstoneAmethystExciterBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.block.ServoPositionSensorBlock;
import dev.redstoneengineering.core.diagnostic.CoreMediaDiagnostics;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.RseLiveDiagnostics;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.physics.PrecisionObservationSupport;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * V2 integrated commissioning bench.
 *
 * <p>This bench is deliberately stricter than the original demo. It validates real domain
 * boundaries, including insulated-redstone terminals, requires sustained evidence before a
 * stage turns green, and requires dynamic witnesses for clock edges, PID action, servo motion,
 * feedback return, Amethyst excitation and piezo conversion.</p>
 *
 * <p>Floating status blocks are diagnostic-only: yellow=WAIT, lime=PASS, red=FAIL. They are
 * placed two blocks above their associated stage so they cannot become accidental electrical
 * inputs. Their state is derived from observer-only validation and never feeds the plant.</p>
 */
public final class RseIntegratedDemoService {
    private RseIntegratedDemoService() {}

    public enum Verdict { WAIT, PASS, FAIL }
    public record StageResult(int stage, String name, Verdict verdict, String detail) {}

    private static final int STAGE_COUNT = 15;
    private static final int AUTO_INTERVAL_TICKS = 4;
    private static final int PASS_CONFIRM_SAMPLES = 8;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final UUID owner;
        private final BlockPos origin;
        private final Verdict[] displayed = new Verdict[STAGE_COUNT + 1];
        private final Verdict[] announced = new Verdict[STAGE_COUNT + 1];
        private final int[] passStreak = new int[STAGE_COUNT + 1];

        private int lastServoPosition;
        private boolean pidResponded;
        private boolean servoMoved;
        private boolean feedbackRoundTripSeen;
        private boolean amethystDriveSeen;
        private boolean resonancePathSeen;
        private boolean piezoSeen;
        private int lastCylinderPosition;
        private boolean cylinderMoved;
        private boolean reservoirCharged;
        private boolean flowSeen;
        private boolean pneumaticReceiverSeen;
        private final String runId;
        private String lastCommand = "/rsevalidation demo place";
        private final ArrayDeque<String> runLog = new ArrayDeque<>();

        private Session(UUID owner, BlockPos origin) {
            this.owner = owner;
            this.origin = origin.immutable();
            this.runId = owner.toString().substring(0, 8) + "@" + origin.toShortString();
            for (int i = 1; i <= STAGE_COUNT; i++) {
                displayed[i] = Verdict.WAIT;
                announced[i] = Verdict.WAIT;
            }
        }
    }

    private static BlockPos p(int x, int y, int z) { return new BlockPos(x, y, z); }
    private static BlockPos at(Session s, BlockPos offset) { return s.origin.offset(offset); }

    // Precision + sampling chain.
    private static final BlockPos LAPIS_SOURCE = p(0, 0, 0);
    private static final BlockPos LAPIS_TRACE_A = p(1, 0, 0);
    private static final BlockPos LAPIS_FILTER = p(2, 0, 0);
    private static final BlockPos LAPIS_TRACE_B = p(3, 0, 0);
    private static final BlockPos SAMPLER = p(4, 0, 0);
    private static final BlockPos LAPIS_TRACE_C = p(5, 0, 0);
    private static final BlockPos QUANTIZER = p(6, 0, 0);

    // Legal insulated-redstone boundary for setpoint distribution.
    private static final BlockPos SETPOINT_TX_TERMINAL = p(7, 0, 0);
    private static final BlockPos SETPOINT_CABLE = p(8, 0, 0);
    private static final BlockPos SETPOINT_PID_TERMINAL = p(9, 0, 0);
    private static final BlockPos PID = p(10, 0, 0);

    // PID control output -> legal cable boundary -> servo command.
    private static final BlockPos CONTROL_TX_TERMINAL = p(11, 0, 0);
    private static final BlockPos CONTROL_CABLE_A = p(12, 0, 0);
    private static final BlockPos CONTROL_CABLE_B = p(13, 0, 0);
    private static final BlockPos CONTROL_CABLE_C = p(13, 0, -1);
    private static final BlockPos CONTROL_RX_TERMINAL = p(12, 0, -1);
    private static final BlockPos SERVO = p(11, 0, -1);
    private static final BlockPos POSITION_SENSOR = p(10, 0, -1);

    // Quartz timing branch and a real edge-event witness.
    private static final BlockPos QUARTZ_OSC = p(4, 0, -4);
    private static final BlockPos QUARTZ_TRACE_A = p(4, 0, -3);
    private static final BlockPos QUARTZ_DIVIDER = p(4, 0, -2);
    private static final BlockPos QUARTZ_TRACE_B = p(4, 0, -1);
    private static final BlockPos QUARTZ_EDGE_RX = p(5, 0, -1);
    private static final BlockPos QUARTZ_EDGE_INDICATOR = p(6, 0, -1);

    // Same setpoint cable drives an electromechanical Amethyst branch.
    private static final BlockPos AMETHYST_BRANCH_TERMINAL = p(8, 0, 1);
    private static final BlockPos AMETHYST_EXCITER = p(8, 0, 2);
    private static final BlockPos AMETHYST_TRACE_A = p(9, 0, 2);
    private static final BlockPos AMETHYST_TUNED = p(10, 0, 2);
    private static final BlockPos AMETHYST_TRACE_B = p(11, 0, 2);
    private static final BlockPos AMETHYST_FILTER = p(12, 0, 2);
    private static final BlockPos AMETHYST_TRACE_C = p(13, 0, 2);
    private static final BlockPos PIEZO = p(14, 0, 2);
    private static final BlockPos PIEZO_INDICATOR = p(15, 0, 2);
    private static final BlockPos SPECTRUM = p(11, 0, 4);

    // Pneumatic instrumentation + visibly translating linear actuator branch.
    private static final BlockPos PNEU_COMMAND_SOURCE = p(19, 0, 0);
    private static final BlockPos PNEU_COMPRESSOR = p(19, 1, 0);
    private static final BlockPos PNEU_PIPE_UP = p(19, 2, 0);
    private static final BlockPos PNEU_PIPE_A = p(20, 2, 0);
    private static final BlockPos PNEU_RESERVOIR = p(20, 2, 1);
    private static final BlockPos PNEU_REGULATOR = p(21, 2, 0);
    private static final BlockPos PNEU_FLOW_METER = p(22, 2, 0);
    private static final BlockPos PNEU_PIPE_B = p(23, 2, 0);
    private static final BlockPos PNEU_PRESSURE_RX = p(23, 2, 1);
    private static final BlockPos PNEU_PRESSURE_DISPLAY = p(23, 2, 2);
    private static final BlockPos PNEU_CYLINDER = p(24, 2, 0);
    private static final BlockPos PNEU_POSITION_DISPLAY = p(25, 2, 0);

    // Diagnostic status blocks float above the plant so they never touch an engineering port.
    private static final BlockPos[] LAMPS = {
            null,
            p(0, 2, 0),   // S1 source + first trace
            p(2, 2, 0),   // S2 filter
            p(5, 2, -1),  // S3 quartz edge receiver
            p(4, 2, 0),   // S4 sampler
            p(6, 2, 0),   // S5 quantizer + setpoint transport
            p(10, 2, 0),  // S6 PID
            p(11, 2, -1), // S7 servo + direct feedback
            p(8, 2, 2),   // S8 redstone -> Amethyst exciter
            p(12, 2, 2),  // S9 tuned/filter resonance path
            p(14, 2, 2),  // S10 piezo conversion
            p(19, 4, 0),  // S11 compressor
            p(20, 4, 1),  // S12 reservoir/regulator
            p(22, 4, 0),  // S13 flow meter
            p(23, 4, 1),  // S14 pneumatic receiver
            p(24, 4, 0)   // S15 translating cylinder
    };
    private static final BlockPos OVERALL_LAMP = p(27, 4, 0);

    private static final Map<String, BlockPos> NODES = nodeOffsets();
    private static Map<String, BlockPos> nodeOffsets() {
        LinkedHashMap<String, BlockPos> n = new LinkedHashMap<>();
        n.put("lapis_source", LAPIS_SOURCE);
        n.put("lapis_trace_a", LAPIS_TRACE_A);
        n.put("lapis_filter", LAPIS_FILTER);
        n.put("lapis_trace_b", LAPIS_TRACE_B);
        n.put("sampler", SAMPLER);
        n.put("lapis_trace_c", LAPIS_TRACE_C);
        n.put("quantizer", QUANTIZER);
        n.put("setpoint_tx_terminal", SETPOINT_TX_TERMINAL);
        n.put("setpoint_cable", SETPOINT_CABLE);
        n.put("setpoint_pid_terminal", SETPOINT_PID_TERMINAL);
        n.put("pid", PID);
        n.put("control_tx_terminal", CONTROL_TX_TERMINAL);
        n.put("control_cable_a", CONTROL_CABLE_A);
        n.put("control_cable_b", CONTROL_CABLE_B);
        n.put("control_cable_c", CONTROL_CABLE_C);
        n.put("control_rx_terminal", CONTROL_RX_TERMINAL);
        n.put("servo", SERVO);
        n.put("position_sensor", POSITION_SENSOR);
        n.put("quartz_osc", QUARTZ_OSC);
        n.put("quartz_trace_a", QUARTZ_TRACE_A);
        n.put("quartz_divider", QUARTZ_DIVIDER);
        n.put("quartz_trace_b", QUARTZ_TRACE_B);
        n.put("quartz_edge_rx", QUARTZ_EDGE_RX);
        n.put("quartz_edge_indicator", QUARTZ_EDGE_INDICATOR);
        n.put("amethyst_branch_terminal", AMETHYST_BRANCH_TERMINAL);
        n.put("amethyst_exciter", AMETHYST_EXCITER);
        n.put("amethyst_trace_a", AMETHYST_TRACE_A);
        n.put("amethyst_tuned", AMETHYST_TUNED);
        n.put("amethyst_trace_b", AMETHYST_TRACE_B);
        n.put("amethyst_filter", AMETHYST_FILTER);
        n.put("amethyst_trace_c", AMETHYST_TRACE_C);
        n.put("piezo", PIEZO);
        n.put("piezo_indicator", PIEZO_INDICATOR);
        n.put("spectrum", SPECTRUM);
        n.put("pneu_command_source", PNEU_COMMAND_SOURCE);
        n.put("pneu_compressor", PNEU_COMPRESSOR);
        n.put("pneu_pipe_up", PNEU_PIPE_UP);
        n.put("pneu_pipe_a", PNEU_PIPE_A);
        n.put("pneu_reservoir", PNEU_RESERVOIR);
        n.put("pneu_regulator", PNEU_REGULATOR);
        n.put("pneu_flow_meter", PNEU_FLOW_METER);
        n.put("pneu_pipe_b", PNEU_PIPE_B);
        n.put("pneu_pressure_rx", PNEU_PRESSURE_RX);
        n.put("pneu_pressure_display", PNEU_PRESSURE_DISPLAY);
        n.put("pneu_cylinder", PNEU_CYLINDER);
        n.put("pneu_position_display", PNEU_POSITION_DISPLAY);
        return Map.copyOf(n);
    }

    public static RseValidationFactoryService.Result place(ServerPlayer player, BlockPos origin) {
        if (player == null) return RseValidationFactoryService.Result.fail("Integrated demo requires a player.");
        ServerLevel level = player.serverLevel();
        if (level.getServer().overworld() != level) {
            return RseValidationFactoryService.Result.fail("Integrated demo currently requires the overworld.");
        }
        if (origin == null) return RseValidationFactoryService.Result.fail("Integrated demo origin missing.");

        clearAndFloor(level, origin);

        // Lapis source -> filter -> Quartz-triggered sample -> quantizer.
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

        // Quantizer -> Vanilla/engineering REDSTONE -> input terminal -> one-node insulated cable.
        set(level, origin, SETPOINT_TX_TERMINAL, terminal(Direction.WEST, false));
        set(level, origin, SETPOINT_CABLE, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, SETPOINT_PID_TERMINAL, terminal(Direction.EAST, true));

        // PID faces EAST: WEST=setpoint, NORTH=process, EAST=control.
        set(level, origin, PID, RedstoneEngineering.PID_CONTROLLER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(PidControllerBlock.TUNING, 1));

        // PID output crosses a second legal terminal/cable boundary to the Servo command port.
        set(level, origin, CONTROL_TX_TERMINAL, terminal(Direction.WEST, false));
        set(level, origin, CONTROL_CABLE_A, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, CONTROL_CABLE_B, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, CONTROL_CABLE_C, RedstoneEngineering.REDSTONE_SIGNAL_CABLE.get().defaultBlockState());
        set(level, origin, CONTROL_RX_TERMINAL, terminal(Direction.WEST, true));

        // Servo faces WEST into a position sensor. Medium load makes inertia visible while preserving control authority.
        set(level, origin, SERVO, RedstoneEngineering.SERVO_ACTUATOR.get().defaultBlockState()
                .setValue(ServoActuatorBlock.FACING, Direction.WEST)
                .setValue(ServoActuatorBlock.SLEW, 2)
                .setValue(ServoActuatorBlock.LOAD, 2));
        set(level, origin, POSITION_SENSOR, RedstoneEngineering.SERVO_POSITION_SENSOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.SOUTH)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.EAST));

        // Quartz oscillator -> /2 -> sampler, plus a real edge receiver + visible pulse history indicator.
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
        set(level, origin, QUARTZ_EDGE_RX, RedstoneEngineering.QUARTZ_TO_REDSTONE_RECEIVER.get().defaultBlockState()
                .setValue(QuartzToRedstoneReceiverBlock.FACING, Direction.EAST)
                .setValue(QuartzToRedstoneReceiverBlock.INPUT_FACING, Direction.WEST)
                .setValue(QuartzToRedstoneReceiverBlock.POWER, 0));
        set(level, origin, QUARTZ_EDGE_INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        // Branch the same setpoint cable into a Redstone -> Amethyst electromechanical transducer.
        set(level, origin, AMETHYST_BRANCH_TERMINAL, terminal(Direction.SOUTH, true));
        set(level, origin, AMETHYST_EXCITER, RedstoneEngineering.REDSTONE_AMETHYST_EXCITER.get().defaultBlockState()
                .setValue(RedstoneAmethystExciterBlock.FACING, Direction.EAST)
                .setValue(RedstoneAmethystExciterBlock.INPUT_FACING, Direction.NORTH)
                .setValue(RedstoneAmethystExciterBlock.FREQUENCY, 8));
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
                .setValue(AmethystPiezoPickupBlock.INPUT_FACING, Direction.WEST)
                .setValue(AmethystPiezoPickupBlock.POWER, 0));
        set(level, origin, PIEZO_INDICATOR, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));
        set(level, origin, SPECTRUM, RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER.get().defaultBlockState());

        // Instrumented pneumatic test cell. The compressor command enters on DOWN and compressed air
        // leaves UP, then the solved pneumatic path turns horizontal through storage/regulation/metrology.
        set(level, origin, PNEU_COMMAND_SOURCE, Blocks.REDSTONE_BLOCK.defaultBlockState());
        set(level, origin, PNEU_COMPRESSOR, RedstoneEngineering.AIR_COMPRESSOR.get().defaultBlockState()
                .setValue(AirCompressorBlock.RESPONSE_MODE, 1));
        set(level, origin, PNEU_PIPE_UP, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        set(level, origin, PNEU_PIPE_A, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        set(level, origin, PNEU_RESERVOIR, RedstoneEngineering.AIR_RESERVOIR.get().defaultBlockState());
        set(level, origin, PNEU_REGULATOR, RedstoneEngineering.PRESSURE_REGULATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST)
                .setValue(PressureRegulatorBlock.SETPOINT, 8)
                .setValue(PressureRegulatorBlock.RESPONSE_MODE, 1));
        set(level, origin, PNEU_FLOW_METER, RedstoneEngineering.PNEUMATIC_FLOW_METER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST));
        set(level, origin, PNEU_PIPE_B, RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState());
        set(level, origin, PNEU_PRESSURE_RX, RedstoneEngineering.PNEUMATIC_RECEIVER.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.SOUTH)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.NORTH)
                .setValue(PneumaticReceiverBlock.RANGE_MODE, 2));
        set(level, origin, PNEU_PRESSURE_DISPLAY, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.SOUTH));
        set(level, origin, PNEU_CYLINDER, RedstoneEngineering.PNEUMATIC_CYLINDER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(DirectionalDomainBlock.INPUT_FACING, Direction.WEST));
        set(level, origin, PNEU_POSITION_DISPLAY, RedstoneEngineering.ANALOG_INDICATOR.get().defaultBlockState()
                .setValue(DirectionalRedstoneEndpointBlock.FACING, Direction.EAST));

        DomainNetwork.recomputeLapis(level, origin.offset(LAPIS_SOURCE));
        DomainNetwork.recomputeQuartz(level, origin.offset(QUARTZ_OSC));
        RedstoneCableNetwork.recompute(level, origin.offset(SETPOINT_TX_TERMINAL));
        RedstoneCableNetwork.recompute(level, origin.offset(CONTROL_TX_TERMINAL));
        PneumaticNetwork.recompute(level, origin.offset(PNEU_COMPRESSOR));

        Session session = new Session(player.getUUID(), origin);
        session.lastServoPosition = ServoActuatorBlock.position(level, origin.offset(SERVO));
        session.lastCylinderPosition = PneumaticCylinderBlock.position(level, origin.offset(PNEU_CYLINDER));
        SESSIONS.put(player.getUUID(), session);
        paintAllWait(level, session);
        installBeaconLights(level, session);
        appendRunLog(session, level.getGameTime(), "RUN START | integrated commissioning bench placed");
        publishValidationRun(level, session);

        return RseValidationFactoryService.Result.ok(
                "Placed RSE integrated commissioning bench V3.",
                "FIXED: all insulated Redstone links now use legal Cable Terminals.",
                "Main loop: Lapis -> filter -> Quartz sample -> quantizer -> terminal/cable -> PID -> terminal/cable -> loaded Servo -> direct position feedback.",
                "Integrated resonance loop: same setpoint cable -> Redstone-Amethyst Exciter -> tuned resonator -> frequency filter -> piezo -> Redstone display.",
                "Quartz proof: divided clock also drives Quartz->Redstone edge receiver; stage 3 requires real observed edges.",
                "Floating status blocks: YELLOW=waiting for evidence, LIME=sustained PASS, RED=real failure.",
                "Servo starts at MEDIUM mechanical load; its shaft animation mirrors authoritative loaded position dynamics.",
                "Linear actuator cell: compressor -> reservoir/regulator -> flow meter -> pressure transducer -> visibly translating pneumatic cylinder.",
                "A stage needs " + PASS_CONFIRM_SAMPLES + " consecutive valid checks before its lamp turns green.",
                "Commands: /rsevalidation demo status | stage <1..15> | setpoint <0..100> | load <0..3> | nodes | node <id> | retest"
        );
    }

    private static BlockState terminal(Direction vanillaSide, boolean outputMode) {
        return RedstoneEngineering.REDSTONE_CABLE_TERMINAL.get().defaultBlockState()
                .setValue(RedstoneCableTerminalBlock.FACING, vanillaSide)
                .setValue(RedstoneCableTerminalBlock.OUTPUT_MODE, outputMode)
                .setValue(RedstoneCableTerminalBlock.POWER, 0);
    }

    public static RseValidationFactoryService.Result retest(ServerPlayer player) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session. Run /rsevalidation demo place.");
        return place(player, s.origin);
    }

    public static RseValidationFactoryService.Result setpoint(ServerPlayer player, int value) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        int bounded = Math.max(0, Math.min(100, value));
        ServerLevel level = player.serverLevel();
        BlockPos sourcePos = at(s, LAPIS_SOURCE);
        BlockState source = level.getBlockState(sourcePos);
        if (!(source.getBlock() instanceof LapisPrecisionSourceBlock)) {
            return RseValidationFactoryService.Result.fail("Lapis source missing at " + sourcePos.toShortString());
        }
        level.setBlock(sourcePos, source.setValue(LapisPrecisionSourceBlock.VALUE, bounded), Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeLapis(level, sourcePos);
        resetDynamicEvidence(level, s);
        s.lastCommand = "/rsevalidation demo setpoint " + bounded;
        appendRunLog(s, level.getGameTime(), "COMMAND | setpoint=" + bounded + "/100 | dynamic evidence re-armed");
        publishValidationRun(level, s);
        return RseValidationFactoryService.Result.ok(
                "Integrated demo setpoint changed to " + bounded + "/100.",
                "All stages re-armed. Use a value different from the current settled point to prove PID/Servo motion again.",
                "Amethyst is no longer a separate impulse test: the same setpoint now drives the Redstone-Amethyst exciter continuously."
        );
    }

    public static RseValidationFactoryService.Result load(ServerPlayer player, int profile) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        int bounded = Math.max(0, Math.min(3, profile));
        ServerLevel level = player.serverLevel();
        BlockPos servoPos = at(s, SERVO);
        BlockState servo = level.getBlockState(servoPos);
        if (!(servo.getBlock() instanceof ServoActuatorBlock block)) {
            return RseValidationFactoryService.Result.fail("Servo actuator missing.");
        }
        BlockState next = servo.setValue(ServoActuatorBlock.LOAD, bounded);
        level.setBlock(servoPos, next, Block.UPDATE_CLIENTS);
        level.scheduleTick(servoPos, block, 1);
        s.pidResponded = false;
        s.servoMoved = false;
        s.feedbackRoundTripSeen = false;
        s.lastServoPosition = ServoActuatorBlock.position(level, servoPos);
        resetStages(level, s, 6, 7);
        s.lastCommand = "/rsevalidation demo load " + bounded;
        appendRunLog(s, level.getGameTime(), "COMMAND | servo load=" + bounded + " (" + ServoActuatorBlock.loadName(next) + ") | S6-S7 re-armed");
        publishValidationRun(level, s);
        return RseValidationFactoryService.Result.ok(
                "Servo mechanical load profile=" + bounded + " (" + ServoActuatorBlock.loadName(next) + ").",
                "accelerationPeriod=" + ServoActuatorBlock.accelerationPeriod(next) + "t"
                        + " effectiveMaxSpeed=" + ServoActuatorBlock.effectiveMaxSpeed(next) + "/step.",
                "Stages 6-7 re-armed so the loaded closed-loop response must be proven again."
        );
    }

    /** Compatibility command: the V2 Amethyst path is continuous, so this re-arms its proof stages. */
    public static RseValidationFactoryService.Result excite(ServerPlayer player) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        s.amethystDriveSeen = false;
        s.resonancePathSeen = false;
        s.piezoSeen = false;
        resetStages(player.serverLevel(), s, 8, 10);
        s.lastCommand = "/rsevalidation demo excite";
        appendRunLog(s, player.serverLevel().getGameTime(), "COMMAND | Amethyst proof stages S8-S10 re-armed");
        publishValidationRun(player.serverLevel(), s);
        return RseValidationFactoryService.Result.ok(
                "Amethyst stages re-armed.",
                "V2 uses a continuous Redstone-Amethyst exciter driven by the real setpoint branch; no fake/manual resonance impulse is injected.",
                "If the setpoint is zero, choose a non-zero setpoint to prove transduction."
        );
    }

    public static RseValidationFactoryService.Result nodes(ServerPlayer player) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE integrated demo node ids (" + NODES.size() + "):"));
        for (String id : NODES.keySet()) lines.add(Component.literal(" - " + id));
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result node(ServerPlayer player, String nodeId) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        String id = nodeId == null ? "" : nodeId.trim().toLowerCase(Locale.ROOT);
        BlockPos offset = NODES.get(id);
        if (offset == null) return RseValidationFactoryService.Result.fail("Unknown demo node: " + id, "Run /rsevalidation demo nodes.");

        ServerLevel level = player.serverLevel();
        BlockPos pos = at(s, offset);
        BlockState state = level.getBlockState(pos);
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[RSE DEMO NODE] " + id + " @ " + pos.toShortString()
                + " | block=" + BuiltInRegistries.BLOCK.getKey(state.getBlock())));

        if (state.getBlock() instanceof EngineeringPortProvider provider) {
            for (var port : provider.engineeringPorts(state)) {
                EngineeringPortSnapshot snap = provider.engineeringSnapshot(level, pos, state, port.side()).orElse(null);
                lines.add(Component.literal(" " + port.label()
                        + " side=" + port.side().getName().toUpperCase(Locale.ROOT)
                        + " domain=" + port.domain()
                        + " dir=" + port.direction()
                        + (snap == null ? " snapshot=MISSING"
                        : " value=" + String.format(Locale.ROOT, "%.2f", snap.value())
                        + " range=[" + String.format(Locale.ROOT, "%.2f", snap.minimum())
                        + "," + String.format(Locale.ROOT, "%.2f", snap.maximum()) + "]"
                        + " quality=" + snap.quality())));
            }
        } else {
            lines.add(Component.literal(" block does not expose EngineeringPortProvider diagnostics"));
        }
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result status(ServerPlayer player) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        updateWitnesses(player.serverLevel(), s);
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.literal("RSE integrated commissioning status @ " + s.origin.toShortString()));
        for (int i = 1; i <= STAGE_COUNT; i++) {
            StageResult raw = evaluate(player.serverLevel(), s, i);
            lines.add(statusComponent(s, raw));
        }
        lines.add(Component.literal("Overall lamp=" + overallVerdict(s)
                + " | green requires every stage to be stably PASS."));
        s.lastCommand = "/rsevalidation demo status";
        publishValidationRun(player.serverLevel(), s);
        return new RseValidationFactoryService.Result(true, lines);
    }

    public static RseValidationFactoryService.Result stage(ServerPlayer player, int stage) {
        Session s = session(player);
        if (s == null) return RseValidationFactoryService.Result.fail("No integrated demo session.");
        if (stage < 1 || stage > STAGE_COUNT) return RseValidationFactoryService.Result.fail("Stage must be 1.." + STAGE_COUNT);
        updateWitnesses(player.serverLevel(), s);
        return new RseValidationFactoryService.Result(true, List.of(statusComponent(s, evaluate(player.serverLevel(), s, stage))));
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level.getGameTime() % AUTO_INTERVAL_TICKS != 0) return;

        for (Session s : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.owner);
            if (player == null || player.serverLevel() != level) continue;
            updateWitnesses(level, s);

            for (int stage = 1; stage <= STAGE_COUNT; stage++) {
                StageResult raw = evaluate(level, s, stage);
                Verdict stable = stabilize(s, stage, raw.verdict());
                paintLamp(level, at(s, LAMPS[stage]), stable);
                if (s.announced[stage] != stable && stable != Verdict.WAIT) {
                    Verdict previous = s.announced[stage];
                    s.announced[stage] = stable;
                    appendRunLog(s, level.getGameTime(),
                            String.format(Locale.ROOT, "S%02d %s | %s | raw=%s | %s",
                                    stage, stable, raw.name(), raw.verdict(), raw.detail()));
                    player.sendSystemMessage(statusComponent(s, raw));
                }
            }
            paintLamp(level, at(s, OVERALL_LAMP), overallVerdict(s));
            publishValidationRun(level, s);
        }
    }

    private static Verdict stabilize(Session s, int stage, Verdict raw) {
        Verdict next;
        if (raw == Verdict.FAIL) {
            s.passStreak[stage] = 0;
            next = Verdict.FAIL;
        } else if (raw == Verdict.WAIT) {
            s.passStreak[stage] = 0;
            next = Verdict.WAIT;
        } else {
            s.passStreak[stage] = Math.min(PASS_CONFIRM_SAMPLES, s.passStreak[stage] + 1);
            next = s.passStreak[stage] >= PASS_CONFIRM_SAMPLES ? Verdict.PASS : Verdict.WAIT;
        }
        s.displayed[stage] = next;
        return next;
    }

    private static void updateWitnesses(ServerLevel level, Session s) {
        BlockPos pidPos = at(s, PID);
        BlockState pidState = level.getBlockState(pidPos);
        if (pidState.getBlock() instanceof PidControllerBlock) {
            int out = pidState.getValue(DirectionalSignalBlock.OUTPUT);
            if (out > 0 || PidControllerBlock.actuatorTarget(level, pidPos) > 0) s.pidResponded = true;
        }

        BlockPos servoPos = at(s, SERVO);
        int position = ServoActuatorBlock.position(level, servoPos);
        if (position != s.lastServoPosition) s.servoMoved = true;
        s.lastServoPosition = position;

        BlockState sensorState = level.getBlockState(at(s, POSITION_SENSOR));
        if (sensorState.getBlock() instanceof ServoPositionSensorBlock sensor
                && pidState.getBlock() instanceof PidControllerBlock pid) {
            EngineeringPortSnapshot sensorOut = sensor.engineeringSnapshot(
                    level, at(s, POSITION_SENSOR), sensorState, Direction.SOUTH).orElse(null);
            EngineeringPortSnapshot pidPv = pid.engineeringSnapshot(
                    level, pidPos, pidState, Direction.NORTH).orElse(null);
            if (sensorOut != null && pidPv != null
                    && sensorOut.quality() == PortQuality.VALID && pidPv.quality() == PortQuality.VALID
                    && Math.round(sensorOut.value()) == Math.round(pidPv.value())) {
                s.feedbackRoundTripSeen = true;
            }
        }

        BlockPos exciterPos = at(s, AMETHYST_EXCITER);
        if (RedstoneAmethystExciterBlock.inputQuality(level, exciterPos) == PortQuality.VALID
                && RedstoneAmethystExciterBlock.actualAmplitude(level, exciterPos) > 0) {
            s.amethystDriveSeen = true;
        }

        if (AmethystResonanceDustBlock.status(level, at(s, AMETHYST_TRACE_C))
                == AmethystResonanceDustBlock.ResonanceStatus.ACTIVE) {
            s.resonancePathSeen = true;
        }

        BlockState pickup = level.getBlockState(at(s, PIEZO));
        if (pickup.getBlock() instanceof AmethystPiezoPickupBlock
                && pickup.getValue(AmethystPiezoPickupBlock.POWER) > 0
                && AmethystPiezoPickupBlock.outputQuality(level, at(s, PIEZO)) == PortQuality.VALID) {
            s.piezoSeen = true;
        }

        int cylinderPosition = PneumaticCylinderBlock.position(level, at(s, PNEU_CYLINDER));
        if (cylinderPosition != s.lastCylinderPosition) s.cylinderMoved = true;
        s.lastCylinderPosition = cylinderPosition;
        if (AirReservoirBlock.storedPressure(level, at(s, PNEU_RESERVOIR)) > 0) s.reservoirCharged = true;
        if (PneumaticFlowMeterBlock.flowProxy(level, at(s, PNEU_FLOW_METER)) > 0
                && PneumaticFlowMeterBlock.measurement(level, at(s, PNEU_FLOW_METER)).sampleCount() > 0) {
            s.flowSeen = true;
        }
        BlockState pressureRx = level.getBlockState(at(s, PNEU_PRESSURE_RX));
        if (pressureRx.getBlock() instanceof PneumaticReceiverBlock
                && pressureRx.getValue(DirectionalSignalBlock.OUTPUT) > 0
                && AnalogIndicatorBlock.retainedMaximum(level, at(s, PNEU_PRESSURE_DISPLAY)) > 0) {
            s.pneumaticReceiverSeen = true;
        }
    }

    private static StageResult evaluate(ServerLevel level, Session s, int stage) {
        return switch (stage) {
            case 1 -> stage1(level, s);
            case 2 -> stage2(level, s);
            case 3 -> stage3(level, s);
            case 4 -> stage4(level, s);
            case 5 -> stage5(level, s);
            case 6 -> stage6(level, s);
            case 7 -> stage7(level, s);
            case 8 -> stage8(level, s);
            case 9 -> stage9(level, s);
            case 10 -> stage10(level, s);
            case 11 -> stage11(level, s);
            case 12 -> stage12(level, s);
            case 13 -> stage13(level, s);
            case 14 -> stage14(level, s);
            case 15 -> stage15(level, s);
            default -> fail(stage, "unknown", "unregistered stage");
        };
    }

    private static StageResult stage1(ServerLevel level, Session s) {
        BlockState source = level.getBlockState(at(s, LAPIS_SOURCE));
        if (!(source.getBlock() instanceof LapisPrecisionSourceBlock)) return fail(1, "Lapis source transport", "source missing");
        int configured = source.getValue(LapisPrecisionSourceBlock.VALUE);
        var trace = PrecisionObservationSupport.lapis(level, at(s, LAPIS_TRACE_A));
        if (trace.quality() == PortQuality.STALE || trace.quality() == PortQuality.NO_SIGNAL) {
            return waitFor(1, "Lapis source transport", "trace quality=" + trace.quality());
        }
        if (!trace.valid() || trace.value() != configured) {
            return fail(1, "Lapis source transport", "configured=" + configured + " trace=" + trace.value() + " quality=" + trace.quality());
        }
        return pass(1, "Lapis source transport", configured + "/100 reached first Lapis trace with VALID evidence");
    }

    private static StageResult stage2(ServerLevel level, Session s) {
        BlockPos filterPos = at(s, LAPIS_FILTER);
        if (!(level.getBlockState(filterPos).getBlock() instanceof LapisLowPassFilterBlock)) return fail(2, "Lapis low-pass", "filter missing");
        var state = LapisLowPassFilterBlock.filterState(level, filterPos);
        var downstream = PrecisionObservationSupport.lapis(level, at(s, LAPIS_TRACE_B));
        if (state.quality() == PortQuality.STALE || downstream.quality() == PortQuality.STALE) {
            return waitFor(2, "Lapis low-pass", "filter=" + state.quality() + " trace=" + downstream.quality());
        }
        if (state.quality() != PortQuality.VALID || !downstream.valid()) {
            return fail(2, "Lapis low-pass", "filter=" + state.quality() + " trace=" + downstream.quality());
        }
        if (downstream.value() != state.output()) {
            return fail(2, "Lapis low-pass", "runtime output=" + state.output() + " downstream=" + downstream.value());
        }
        return pass(2, "Lapis low-pass", "output=" + state.output() + "/100 propagated downstream");
    }

    private static StageResult stage3(ServerLevel level, Session s) {
        var clock = PrecisionObservationSupport.quartz(level, at(s, QUARTZ_TRACE_B));
        BlockPos rxPos = at(s, QUARTZ_EDGE_RX);
        BlockState rx = level.getBlockState(rxPos);
        if (!(rx.getBlock() instanceof QuartzToRedstoneReceiverBlock)) return fail(3, "Quartz clock/event proof", "edge receiver missing");
        if (clock.quality() == PortQuality.STALE || clock.quality() == PortQuality.NO_SIGNAL
                || QuartzToRedstoneReceiverBlock.inputQuality(level, rxPos) == PortQuality.STALE) {
            return waitFor(3, "Quartz clock/event proof", "clock=" + clock.quality() + " edges=" + QuartzToRedstoneReceiverBlock.edgeCount(level, rxPos));
        }
        if (!clock.valid() || clock.periodTicks() != 16
                || QuartzToRedstoneReceiverBlock.inputQuality(level, rxPos) != PortQuality.VALID
                || QuartzToRedstoneReceiverBlock.observedPeriod(level, rxPos) != 16) {
            return fail(3, "Quartz clock/event proof", "tracePeriod=" + clock.periodTicks()
                    + " receiverPeriod=" + QuartzToRedstoneReceiverBlock.observedPeriod(level, rxPos)
                    + " quality=" + QuartzToRedstoneReceiverBlock.inputQuality(level, rxPos));
        }
        int edges = QuartzToRedstoneReceiverBlock.edgeCount(level, rxPos);
        int pulseMax = AnalogIndicatorBlock.retainedMaximum(level, at(s, QUARTZ_EDGE_INDICATOR));
        if (edges < 2 || pulseMax < 15) {
            return waitFor(3, "Quartz clock/event proof", "need real edges; edges=" + edges + " indicatorMax=" + pulseMax);
        }
        return pass(3, "Quartz clock/event proof", "16t clock produced " + edges + " rising edges; visible receiver reached 15/15");
    }

    private static StageResult stage4(ServerLevel level, Session s) {
        BlockPos pos = at(s, SAMPLER);
        if (!(level.getBlockState(pos).getBlock() instanceof QuartzTriggeredLapisSamplerBlock)) return fail(4, "Quartz/Lapis sampler", "sampler missing");
        PortQuality q = QuartzTriggeredLapisSamplerBlock.heldQuality(level, pos);
        int captures = QuartzTriggeredLapisSamplerBlock.acceptedCaptures(level, pos);
        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, pos);
        var downstream = PrecisionObservationSupport.lapis(level, at(s, LAPIS_TRACE_C));
        if (captures < 2 || q == PortQuality.STALE || q == PortQuality.NO_SIGNAL || downstream.quality() == PortQuality.STALE) {
            return waitFor(4, "Quartz/Lapis sampler", "captures=" + captures + " held=" + held + " quality=" + q);
        }
        if (q != PortQuality.VALID || !downstream.valid() || downstream.value() != held) {
            return fail(4, "Quartz/Lapis sampler", "held=" + held + " downstream=" + downstream.value()
                    + " heldQuality=" + q + " downstreamQuality=" + downstream.quality());
        }
        return pass(4, "Quartz/Lapis sampler", "real clock captures=" + captures + " held=" + held + "/100 propagated");
    }

    private static StageResult stage5(ServerLevel level, Session s) {
        BlockPos qPos = at(s, QUANTIZER);
        BlockState qState = level.getBlockState(qPos);
        if (!(qState.getBlock() instanceof LapisToRedstoneQuantizerBlock)) return fail(5, "Quantizer + setpoint transport", "quantizer missing");
        PortQuality q = LapisToRedstoneQuantizerBlock.outputQuality(level, qPos);
        if (q == PortQuality.STALE || q == PortQuality.NO_SIGNAL) return waitFor(5, "Quantizer + setpoint transport", "quantizer quality=" + q);
        if (q != PortQuality.VALID) return fail(5, "Quantizer + setpoint transport", "quantizer quality=" + q);

        int held = QuartzTriggeredLapisSamplerBlock.heldValue(level, at(s, SAMPLER));
        int expected = CoreMediaDiagnostics.redstoneFromLapis(held);
        int quantized = qState.getValue(LapisToRedstoneQuantizerBlock.POWER);
        if (quantized != expected) return fail(5, "Quantizer + setpoint transport", "quantized=" + quantized + " expected=" + expected);

        BlockPos cablePos = at(s, SETPOINT_CABLE);
        RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(level, cablePos);
        BlockState outTerm = level.getBlockState(at(s, SETPOINT_PID_TERMINAL));
        if (evidence.quality() == PortQuality.STALE) return waitFor(5, "Quantizer + setpoint transport", "cable source evidence STALE");
        if (evidence.sourceCount() != 1 || evidence.quality() != PortQuality.VALID) {
            return fail(5, "Quantizer + setpoint transport", "cable sources=" + evidence.sourceCount() + " quality=" + evidence.quality());
        }
        int delivered = outTerm.getBlock() instanceof RedstoneCableTerminalBlock
                ? outTerm.getValue(RedstoneCableTerminalBlock.POWER) : -1;
        if (delivered != quantized) {
            return fail(5, "Quantizer + setpoint transport", "quantizer=" + quantized + " terminalDelivered=" + delivered);
        }
        return pass(5, "Quantizer + setpoint transport", held + "/100 -> " + quantized + "/15; legal terminal/cable delivered same code");
    }

    private static StageResult stage6(ServerLevel level, Session s) {
        BlockPos pos = at(s, PID);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PidControllerBlock pid)) return fail(6, "PID control", "PID missing");
        EngineeringPortSnapshot sp = pid.engineeringSnapshot(level, pos, state, Direction.WEST).orElse(null);
        EngineeringPortSnapshot pv = pid.engineeringSnapshot(level, pos, state, Direction.NORTH).orElse(null);
        EngineeringPortSnapshot out = pid.engineeringSnapshot(level, pos, state, Direction.EAST).orElse(null);
        if (sp == null || pv == null || out == null) return fail(6, "PID control", "required engineering snapshot missing");
        if (sp.quality() == PortQuality.STALE || pv.quality() == PortQuality.STALE || out.quality() == PortQuality.STALE) {
            return waitFor(6, "PID control", "SP=" + sp.quality() + " PV=" + pv.quality() + " OUT=" + out.quality());
        }
        if (sp.quality() != PortQuality.VALID || pv.quality() != PortQuality.VALID || out.quality() != PortQuality.VALID) {
            return fail(6, "PID control", "SP=" + sp.quality() + " PV=" + pv.quality() + " OUT=" + out.quality());
        }
        if (!s.pidResponded) {
            return waitFor(6, "PID control", "valid inputs established; waiting for observed controller response");
        }

        BlockState tx = level.getBlockState(at(s, CONTROL_TX_TERMINAL));
        BlockState rx = level.getBlockState(at(s, CONTROL_RX_TERMINAL));
        int pidOut = state.getValue(DirectionalSignalBlock.OUTPUT);
        int terminalInput = tx.getBlock() instanceof RedstoneCableTerminalBlock ? tx.getValue(RedstoneCableTerminalBlock.POWER) : -1;
        int delivered = rx.getBlock() instanceof RedstoneCableTerminalBlock ? rx.getValue(RedstoneCableTerminalBlock.POWER) : -1;
        RedstoneCableNetwork.SourceEvidence evidence = RedstoneCableNetwork.sourceEvidence(level, at(s, CONTROL_CABLE_A));
        RedstoneCableNetwork.PathEvidence path = RedstoneCableNetwork.pathEvidence(level, at(s, CONTROL_RX_TERMINAL));
        if (terminalInput != pidOut) {
            return fail(6, "PID control", "PID OUT=" + pidOut + " control TX terminal=" + terminalInput);
        }
        if (evidence.quality() != PortQuality.VALID || evidence.sourceCount() != 1) {
            return fail(6, "PID control", "control cable sources=" + evidence.sourceCount() + " quality=" + evidence.quality());
        }
        if (delivered < 0 || delivered > pidOut) {
            return fail(6, "PID control", "impossible control transport: PID=" + pidOut + " delivered=" + delivered);
        }
        return pass(6, "PID control", "SP=" + Math.round(sp.value()) + " PV=" + Math.round(pv.value())
                + " OUT=" + pidOut + " delivered=" + delivered
                + " lineLoss=" + path.attenuationLoss()
                + " target=" + PidControllerBlock.actuatorTarget(level, pos)
                + " responseWitness=YES");
    }

    private static StageResult stage7(ServerLevel level, Session s) {
        BlockPos servoPos = at(s, SERVO);
        BlockState servoState = level.getBlockState(servoPos);
        BlockPos sensorPos = at(s, POSITION_SENSOR);
        BlockState sensorState = level.getBlockState(sensorPos);
        BlockState pidState = level.getBlockState(at(s, PID));
        if (!(servoState.getBlock() instanceof ServoActuatorBlock servo)) return fail(7, "Servo closed-loop feedback", "servo missing");
        if (!(sensorState.getBlock() instanceof ServoPositionSensorBlock sensor)) return fail(7, "Servo closed-loop feedback", "sensor missing");
        if (!(pidState.getBlock() instanceof PidControllerBlock pid)) return fail(7, "Servo closed-loop feedback", "PID missing");

        EngineeringPortSnapshot command = servo.engineeringSnapshot(level, servoPos, servoState, Direction.EAST).orElse(null);
        EngineeringPortSnapshot sensorOut = sensor.engineeringSnapshot(level, sensorPos, sensorState, Direction.SOUTH).orElse(null);
        EngineeringPortSnapshot pidPv = pid.engineeringSnapshot(level, at(s, PID), pidState, Direction.NORTH).orElse(null);
        EngineeringPortSnapshot pidSp = pid.engineeringSnapshot(level, at(s, PID), pidState, Direction.WEST).orElse(null);
        if (command == null || sensorOut == null || pidPv == null || pidSp == null) return fail(7, "Servo closed-loop feedback", "snapshot missing");
        if (command.quality() == PortQuality.STALE || sensorOut.quality() == PortQuality.STALE || pidPv.quality() == PortQuality.STALE) {
            return waitFor(7, "Servo closed-loop feedback", "CMD=" + command.quality() + " SENSOR=" + sensorOut.quality() + " PID-PV=" + pidPv.quality());
        }
        if (command.quality() != PortQuality.VALID || sensorOut.quality() != PortQuality.VALID
                || pidPv.quality() != PortQuality.VALID || pidSp.quality() != PortQuality.VALID) {
            return fail(7, "Servo closed-loop feedback", "CMD=" + command.quality() + " SENSOR=" + sensorOut.quality()
                    + " PID-PV=" + pidPv.quality() + " PID-SP=" + pidSp.quality());
        }
        if (!s.servoMoved) return waitFor(7, "Servo closed-loop feedback", "valid command path; waiting for real Servo position change");
        if (!s.feedbackRoundTripSeen) return waitFor(7, "Servo closed-loop feedback", "Servo moved; waiting for sensor value to arrive at PID process input");

        int position = ServoActuatorBlock.position(level, servoPos);
        int target = (int) Math.round(pidSp.value());
        int sensorReading = (int) Math.round(sensorOut.value());
        int pidReading = (int) Math.round(pidPv.value());
        int load = ServoActuatorBlock.loadIndex(servoState);
        int accelPeriod = ServoActuatorBlock.accelerationPeriod(servoState);
        int loadDelay = ServoActuatorBlock.loadDelayTicks(level, servoPos);
        int motionSamples = ServoActuatorBlock.motionSamples(level, servoPos);
        int pidOut = pidState.getValue(DirectionalSignalBlock.OUTPUT);
        if (motionSamples <= 0) {
            return waitFor(7, "Servo closed-loop feedback", "no authoritative mechanical motion samples yet");
        }
        if (accelPeriod > 1 && loadDelay <= 0) {
            return waitFor(7, "Servo closed-loop feedback", "loaded actuator has not yet demonstrated inertia delay");
        }
        if (sensorReading != pidReading) {
            return fail(7, "Servo closed-loop feedback", "sensorOut=" + sensorReading + " PID-PV=" + pidReading);
        }
        if (Math.abs(position - target) > 2) {
            if (pidOut >= 15 && command.value() < target && position >= Math.round(command.value())
                    && motionSamples >= 4) {
                return fail(7, "Servo closed-loop feedback", "control authority exhausted: target=" + target
                        + " PID=15 deliveredCommand=" + Math.round(command.value())
                        + " position=" + position + " (transmission loss limits reachable position)");
            }
            return waitFor(7, "Servo closed-loop feedback", "position=" + position + " target≈" + target
                    + " still settling | load=" + ServoActuatorBlock.loadName(servoState)
                    + " velocity=" + ServoActuatorBlock.velocity(level, servoPos));
        }
        return pass(7, "Servo closed-loop feedback", "moved=YES position=" + position + " target=" + target
                + " sensor=" + sensorReading
                + " load=" + ServoActuatorBlock.loadName(servoState)
                + " accelPeriod=" + accelPeriod + "t loadDelay=" + loadDelay + "t"
                + " motionSamples=" + motionSamples
                + " feedback returned to PID");
    }

    private static StageResult stage8(ServerLevel level, Session s) {
        BlockPos pos = at(s, AMETHYST_EXCITER);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneAmethystExciterBlock)) return fail(8, "Redstone -> Amethyst drive", "exciter missing");
        PortQuality quality = RedstoneAmethystExciterBlock.inputQuality(level, pos);
        int target = RedstoneAmethystExciterBlock.targetAmplitude(level, pos);
        int actual = RedstoneAmethystExciterBlock.actualAmplitude(level, pos);
        BlockState branch = level.getBlockState(at(s, AMETHYST_BRANCH_TERMINAL));
        int delivered = branch.getBlock() instanceof RedstoneCableTerminalBlock
                ? branch.getValue(RedstoneCableTerminalBlock.POWER) : -1;
        if (quality == PortQuality.STALE) return waitFor(8, "Redstone -> Amethyst drive", "input quality STALE");
        if (quality != PortQuality.VALID) return fail(8, "Redstone -> Amethyst drive", "input quality=" + quality);
        if (target != delivered) return fail(8, "Redstone -> Amethyst drive", "target=" + target + " branch terminal=" + delivered);
        if (target <= 0) return waitFor(8, "Redstone -> Amethyst drive", "valid commanded zero cannot prove active transduction");
        if (!s.amethystDriveSeen || actual <= 0) {
            return waitFor(8, "Redstone -> Amethyst drive", "target=" + target + " actual=" + actual + " waiting for finite attack");
        }
        return pass(8, "Redstone -> Amethyst drive", "same setpoint branch drove f=8 resonance targetA=" + target + " actualA=" + actual);
    }

    private static StageResult stage9(ServerLevel level, Session s) {
        BlockPos[] traces = {at(s, AMETHYST_TRACE_A), at(s, AMETHYST_TRACE_B), at(s, AMETHYST_TRACE_C)};
        for (int i = 0; i < traces.length; i++) {
            var status = AmethystResonanceDustBlock.status(level, traces[i]);
            if (status == AmethystResonanceDustBlock.ResonanceStatus.FREQUENCY_CONFLICT) {
                return fail(9, "Amethyst tuned/filter path", "frequency conflict at trace " + (i + 1));
            }
            if (status == AmethystResonanceDustBlock.ResonanceStatus.STALE) {
                return waitFor(9, "Amethyst tuned/filter path", "trace " + (i + 1) + " STALE");
            }
            if (status != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE) {
                return waitFor(9, "Amethyst tuned/filter path", "trace " + (i + 1) + " idle");
            }
            if (AmethystResonanceDustBlock.frequency(level, traces[i]) != 8
                    || AmethystResonanceDustBlock.amplitude(level, traces[i]) <= 0) {
                return fail(9, "Amethyst tuned/filter path", "trace " + (i + 1)
                        + " f=" + AmethystResonanceDustBlock.frequency(level, traces[i])
                        + " A=" + AmethystResonanceDustBlock.amplitude(level, traces[i]));
            }
        }
        if (!s.resonancePathSeen) return waitFor(9, "Amethyst tuned/filter path", "waiting for end-to-end resonance witness");
        return pass(9, "Amethyst tuned/filter path", "f=8 remained ACTIVE through input trace, tuned resonator, exact filter and output trace");
    }

    private static StageResult stage10(ServerLevel level, Session s) {
        BlockPos pos = at(s, PIEZO);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AmethystPiezoPickupBlock)) return fail(10, "Amethyst -> Redstone piezo", "pickup missing");
        var in = AmethystPiezoPickupBlock.inputObservation(level, pos, state);
        PortQuality outQ = AmethystPiezoPickupBlock.outputQuality(level, pos);
        int out = state.getValue(AmethystPiezoPickupBlock.POWER);
        if (in.quality() == PortQuality.STALE || outQ == PortQuality.STALE) {
            return waitFor(10, "Amethyst -> Redstone piezo", "input=" + in.quality() + " output=" + outQ);
        }
        if (in.quality() != PortQuality.VALID || outQ != PortQuality.VALID) {
            return fail(10, "Amethyst -> Redstone piezo", "input=" + in.quality() + " output=" + outQ);
        }
        if (in.amplitude() <= 0 || out <= 0) return waitFor(10, "Amethyst -> Redstone piezo", "waiting for non-zero physical envelope");
        if (Math.abs(out - in.amplitude()) > 1) {
            return fail(10, "Amethyst -> Redstone piezo", "resonance A=" + in.amplitude() + " rectified=" + out + " mismatch");
        }
        if (!s.piezoSeen) return waitFor(10, "Amethyst -> Redstone piezo", "waiting for retained conversion witness");
        int displayMax = AnalogIndicatorBlock.retainedMaximum(level, at(s, PIEZO_INDICATOR));
        if (displayMax <= 0) return waitFor(10, "Amethyst -> Redstone piezo", "pickup valid; waiting for visible Redstone display witness");
        return pass(10, "Amethyst -> Redstone piezo", "f=" + in.frequency() + " A=" + in.amplitude()
                + " -> Redstone=" + out + "/15; displayMax=" + displayMax);
    }

    private static StageResult stage11(ServerLevel level, Session s) {
        BlockPos pos = at(s, PNEU_COMPRESSOR);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AirCompressorBlock)) return fail(11, "Compressor pressure source", "compressor missing");
        var command = AirCompressorBlock.commandObservation(level, pos);
        if (command.quality() == PortQuality.STALE) return waitFor(11, "Compressor pressure source", "command STALE");
        if (command.quality() != PortQuality.VALID || command.value() != 15) {
            return fail(11, "Compressor pressure source", "command=" + command.value() + " quality=" + command.quality());
        }
        if (!AirCompressorBlock.initialized(level, pos) || AirCompressorBlock.startCount(level, pos) <= 0) {
            return waitFor(11, "Compressor pressure source", "waiting for authoritative compressor start");
        }
        int actual = AirCompressorBlock.actualPressure(level, pos);
        var outlet = PneumaticObservationSupport.observe(level, at(s, PNEU_PIPE_UP));
        if (actual < 90 || !outlet.valid() || outlet.pressure() <= 0) {
            return waitFor(11, "Compressor pressure source", "actual=" + actual + "/100 outlet=" + outlet.pressure() + " " + outlet.quality());
        }
        return pass(11, "Compressor pressure source", "command=15/15 target=100 actual=" + actual
                + " outlet=" + outlet.pressure() + " starts=" + AirCompressorBlock.startCount(level, pos));
    }

    private static StageResult stage12(ServerLevel level, Session s) {
        BlockPos regulatorPos = at(s, PNEU_REGULATOR);
        BlockState regulatorState = level.getBlockState(regulatorPos);
        if (!(regulatorState.getBlock() instanceof PressureRegulatorBlock)) return fail(12, "Reservoir + pressure regulation", "regulator missing");
        if (!(level.getBlockState(at(s, PNEU_RESERVOIR)).getBlock() instanceof AirReservoirBlock)) {
            return fail(12, "Reservoir + pressure regulation", "reservoir missing");
        }
        int stored = AirReservoirBlock.storedPressure(level, at(s, PNEU_RESERVOIR));
        int regulated = PressureRegulatorBlock.actualRegulatedPressure(level, regulatorPos);
        int setpoint = PressureRegulatorBlock.setpointPressure(regulatorState);
        var downstream = PneumaticObservationSupport.observe(level, at(s, PNEU_FLOW_METER));
        if (!s.reservoirCharged || stored <= 0 || regulated <= 0 || !downstream.valid()) {
            return waitFor(12, "Reservoir + pressure regulation", "stored=" + stored + " regulated=" + regulated
                    + " downstream=" + downstream.quality());
        }
        if (regulated > setpoint) return fail(12, "Reservoir + pressure regulation", "regulated=" + regulated + " exceeds setpoint=" + setpoint);
        if (regulated < setpoint - 10) {
            return waitFor(12, "Reservoir + pressure regulation", "diaphragm settling: actual=" + regulated + " target=" + setpoint);
        }
        return pass(12, "Reservoir + pressure regulation", "reservoir=" + stored + "/100 regulator="
                + regulated + "/100 setpoint=" + setpoint + "/100 downstream=" + downstream.pressure());
    }

    private static StageResult stage13(ServerLevel level, Session s) {
        BlockPos pos = at(s, PNEU_FLOW_METER);
        if (!(level.getBlockState(pos).getBlock() instanceof PneumaticFlowMeterBlock)) return fail(13, "Pneumatic flow metrology", "flow meter missing");
        int pin = PneumaticFlowMeterBlock.inletPressure(level, pos);
        int pout = PneumaticFlowMeterBlock.outletPressure(level, pos);
        int dp = PneumaticFlowMeterBlock.pressureDrop(level, pos);
        int flow = PneumaticFlowMeterBlock.flowProxy(level, pos);
        var measurement = PneumaticFlowMeterBlock.measurement(level, pos);
        if (!s.flowSeen || measurement.sampleCount() < 2 || flow <= 0) {
            return waitFor(13, "Pneumatic flow metrology", "Pin/Pout=" + pin + "/" + pout
                    + " dP=" + dp + " flow≈" + flow + " samples=" + measurement.sampleCount());
        }
        if (pin <= 0 || pout <= 0 || dp <= 0 || pin <= pout) {
            return fail(13, "Pneumatic flow metrology", "nonphysical metering evidence Pin/Pout=" + pin + "/" + pout + " dP=" + dp);
        }
        return pass(13, "Pneumatic flow metrology", "Pin/Pout=" + pin + "/" + pout
                + " dP=" + dp + " flow≈" + flow + " samples=" + measurement.sampleCount());
    }

    private static StageResult stage14(ServerLevel level, Session s) {
        BlockPos pos = at(s, PNEU_PRESSURE_RX);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PneumaticReceiverBlock receiver)) return fail(14, "Pneumatic pressure transduction", "receiver missing");
        EngineeringPortSnapshot input = receiver.engineeringSnapshot(level, pos, state, Direction.NORTH).orElse(null);
        EngineeringPortSnapshot output = receiver.engineeringSnapshot(level, pos, state, Direction.SOUTH).orElse(null);
        if (input == null || output == null) return fail(14, "Pneumatic pressure transduction", "snapshot missing");
        if (input.quality() == PortQuality.STALE || output.quality() == PortQuality.STALE) {
            return waitFor(14, "Pneumatic pressure transduction", "IN=" + input.quality() + " OUT=" + output.quality());
        }
        if (input.quality() != PortQuality.VALID || output.quality() != PortQuality.VALID) {
            return fail(14, "Pneumatic pressure transduction", "IN=" + input.quality() + " OUT=" + output.quality());
        }
        int pressure = (int) Math.round(input.value());
        int expected = PneumaticReceiverBlock.scaledOutput(pressure, PneumaticReceiverBlock.fullScalePressure(state));
        int actual = state.getValue(DirectionalSignalBlock.OUTPUT);
        BlockState displayState = level.getBlockState(at(s, PNEU_PRESSURE_DISPLAY));
        int displayed = displayState.getBlock() instanceof AnalogIndicatorBlock ? displayState.getValue(AnalogIndicatorBlock.LEVEL) : -1;
        if (actual != expected || displayed != actual) {
            return fail(14, "Pneumatic pressure transduction", "pressure=" + pressure + " expected="
                    + expected + " receiver=" + actual + " display=" + displayed);
        }
        if (!s.pneumaticReceiverSeen || actual <= 0) return waitFor(14, "Pneumatic pressure transduction", "waiting for non-zero pressure/display witness");
        return pass(14, "Pneumatic pressure transduction", pressure + "/100 -> Redstone " + actual + "/15; display=" + displayed);
    }

    private static StageResult stage15(ServerLevel level, Session s) {
        BlockPos pos = at(s, PNEU_CYLINDER);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PneumaticCylinderBlock cylinder)) return fail(15, "Visible pneumatic linear actuator", "cylinder missing");
        EngineeringPortSnapshot pressureIn = cylinder.engineeringSnapshot(level, pos, state, Direction.WEST).orElse(null);
        EngineeringPortSnapshot feedback = cylinder.engineeringSnapshot(level, pos, state, Direction.EAST).orElse(null);
        if (pressureIn == null || feedback == null) return fail(15, "Visible pneumatic linear actuator", "snapshot missing");
        if (pressureIn.quality() == PortQuality.STALE) return waitFor(15, "Visible pneumatic linear actuator", "pressure evidence STALE");
        if (pressureIn.quality() != PortQuality.VALID || feedback.quality() != PortQuality.VALID) {
            return fail(15, "Visible pneumatic linear actuator", "pressure=" + pressureIn.quality() + " feedback=" + feedback.quality());
        }
        int position = PneumaticCylinderBlock.position(level, pos);
        int target = PneumaticCylinderBlock.target(level, pos);
        int pressure = PneumaticCylinderBlock.pressure(level, pos);
        int travel = PneumaticCylinderBlock.travel(level, pos);
        int samples = PneumaticCylinderBlock.samples(level, pos);
        BlockState displayState = level.getBlockState(at(s, PNEU_POSITION_DISPLAY));
        int displayed = displayState.getBlock() instanceof AnalogIndicatorBlock ? displayState.getValue(AnalogIndicatorBlock.LEVEL) : -1;
        var path = PneumaticNetwork.actuatorPathEvidence(level, pos);
        if (!s.cylinderMoved || travel <= 0 || samples <= 0) {
            return waitFor(15, "Visible pneumatic linear actuator", "pressure=" + pressure + " position=" + position
                    + " target=" + target + " travel=" + travel + " samples=" + samples);
        }
        if (displayed != position) return fail(15, "Visible pneumatic linear actuator", "position=" + position + " display=" + displayed);
        if (Math.abs(position - target) > 1) {
            return waitFor(15, "Visible pneumatic linear actuator", "rod moving position=" + position
                    + " target=" + target + " velocity=" + PneumaticCylinderBlock.velocity(level, pos));
        }
        if (path.supplyPressure() <= 0 || path.pathEdges() <= 0 || path.actuatorPressure() <= 0
                || path.actuatorPressure() > pressure || pressure - path.actuatorPressure() > 2) {
            return fail(15, "Visible pneumatic linear actuator", "bad path evidence supply=" + path.supplyPressure()
                    + " actuatorNode=" + path.actuatorPressure() + " inlet=" + pressure
                    + " edges=" + path.pathEdges() + " observedLoss=" + path.observedLoss());
        }
        return pass(15, "Visible pneumatic linear actuator", "rod settled at " + position + "/15 target=" + target
                + " pressure=" + pressure + "/100 | pathLoss=" + path.observedLoss()
                + " | travel=" + travel + " display=" + displayed);
    }

    private static StageResult pass(int stage, String name, String detail) { return new StageResult(stage, name, Verdict.PASS, detail); }
    private static StageResult fail(int stage, String name, String detail) { return new StageResult(stage, name, Verdict.FAIL, detail); }
    private static StageResult waitFor(int stage, String name, String detail) { return new StageResult(stage, name, Verdict.WAIT, detail); }

    private static Component statusComponent(Session s, StageResult raw) {
        Verdict lamp = s.displayed[raw.stage()];
        ChatFormatting color = switch (lamp) {
            case PASS -> ChatFormatting.GREEN;
            case FAIL -> ChatFormatting.RED;
            case WAIT -> ChatFormatting.YELLOW;
        };
        return Component.literal("[RSE DEMO][LAMP=" + lamp + "][RAW=" + raw.verdict() + "][S" + raw.stage() + "] "
                + raw.name() + " | confirm=" + s.passStreak[raw.stage()] + "/" + PASS_CONFIRM_SAMPLES
                + " | " + raw.detail()).withStyle(color);
    }

    private static Session session(ServerPlayer player) { return player == null ? null : SESSIONS.get(player.getUUID()); }

    private static void appendRunLog(Session s, long tick, String message) {
        while (s.runLog.size() >= 120) s.runLog.removeFirst();
        s.runLog.addLast("t=" + tick + " | " + message);
    }

    private static List<String> feedbackLines(ServerLevel level, Session s) {
        ArrayList<String> lines = new ArrayList<>();
        BlockState source = level.getBlockState(at(s, LAPIS_SOURCE));
        int setpoint = source.getBlock() instanceof LapisPrecisionSourceBlock
                ? source.getValue(LapisPrecisionSourceBlock.VALUE) : -1;
        BlockState servo = level.getBlockState(at(s, SERVO));
        String load = servo.getBlock() instanceof ServoActuatorBlock ? ServoActuatorBlock.loadName(servo) : "MISSING";
        lines.add("RUN " + s.runId + " | origin=" + s.origin.toShortString()
                + " | setpoint=" + setpoint + "/100 | servoLoad=" + load);
        lines.add("OVERALL " + overallVerdict(s) + " | PASS requires all 15 stages stable for "
                + PASS_CONFIRM_SAMPLES + " consecutive checks");
        for (int i = 1; i <= STAGE_COUNT; i++) {
            StageResult raw = evaluate(level, s, i);
            lines.add(String.format(Locale.ROOT,
                    "S%02d %-4s | raw=%-4s | confirm=%d/%d | %s | %s",
                    i, s.displayed[i], raw.verdict(), s.passStreak[i], PASS_CONFIRM_SAMPLES,
                    raw.name(), raw.detail()));
        }
        return List.copyOf(lines);
    }

    private static void publishValidationRun(ServerLevel level, Session s) {
        RseLiveDiagnostics.publishValidationRun(
                s.runId,
                s.lastCommand,
                overallVerdict(s).name(),
                feedbackLines(level, s),
                List.copyOf(s.runLog),
                level.getGameTime()
        );
    }

    private static Verdict overallVerdict(Session s) {
        boolean all = true;
        for (int i = 1; i <= STAGE_COUNT; i++) {
            if (s.displayed[i] == Verdict.FAIL) return Verdict.FAIL;
            if (s.displayed[i] != Verdict.PASS) all = false;
        }
        return all ? Verdict.PASS : Verdict.WAIT;
    }

    private static void resetDynamicEvidence(ServerLevel level, Session s) {
        s.pidResponded = false;
        s.servoMoved = false;
        s.feedbackRoundTripSeen = false;
        s.amethystDriveSeen = false;
        s.resonancePathSeen = false;
        s.piezoSeen = false;
        s.cylinderMoved = false;
        s.reservoirCharged = false;
        s.flowSeen = false;
        s.pneumaticReceiverSeen = false;
        s.lastServoPosition = ServoActuatorBlock.position(level, at(s, SERVO));
        s.lastCylinderPosition = PneumaticCylinderBlock.position(level, at(s, PNEU_CYLINDER));
        resetStages(level, s, 1, STAGE_COUNT);
    }

    private static void resetStages(ServerLevel level, Session s, int first, int last) {
        for (int i = Math.max(1, first); i <= Math.min(STAGE_COUNT, last); i++) {
            s.displayed[i] = Verdict.WAIT;
            s.announced[i] = Verdict.WAIT;
            s.passStreak[i] = 0;
            paintLamp(level, at(s, LAMPS[i]), Verdict.WAIT);
        }
        paintLamp(level, at(s, OVERALL_LAMP), Verdict.WAIT);
    }

    private static void paintAllWait(ServerLevel level, Session s) { resetStages(level, s, 1, STAGE_COUNT); }

    /** Constant light sources sit above the colored status glass and never touch signal wiring. */
    private static void installBeaconLights(ServerLevel level, Session s) {
        for (int i = 1; i <= STAGE_COUNT; i++) {
            level.setBlock(at(s, LAMPS[i]).above(), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(at(s, OVERALL_LAMP).above(), Blocks.SEA_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void paintLamp(ServerLevel level, BlockPos pos, Verdict verdict) {
        BlockState target = switch (verdict) {
            case PASS -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case FAIL -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case WAIT -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
        };
        if (!level.getBlockState(pos).is(target.getBlock())) {
            level.setBlock(pos, target, Block.UPDATE_CLIENTS);
        }
    }

    private static void set(ServerLevel level, BlockPos origin, BlockPos offset, BlockState state) {
        level.setBlock(origin.offset(offset), state, Block.UPDATE_ALL);
    }

    private static void clearAndFloor(ServerLevel level, BlockPos origin) {
        for (int x = -1; x <= 28; x++) {
            for (int z = -5; z <= 6; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = 0; y <= 5; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }
}
