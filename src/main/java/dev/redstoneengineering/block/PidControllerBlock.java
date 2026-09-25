package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.ClosedLoopCommissioning;
import dev.redstoneengineering.diagnostics.CommissioningSnapshot;
import dev.redstoneengineering.diagnostics.PidTelemetryStore;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceComparison;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceRecord;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceStore;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptance;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.PidActuatorLogic;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Discrete PID controller for Minecraft-scale plants.
 *
 * Ports:
 * BACK=setpoint, LEFT=process value, RIGHT=INHIBIT, FRONT=control output,
 * UP=mode select (0=AUTO, >0=MANUAL), DOWN=manual output.
 *
 * The controller keeps its engineering boundary at 0..15 while retaining
 * internal integral/derivative state and a controller bias for bumpless
 * manual→auto transfer. AUTO requires real setpoint and process evidence;
 * missing/unknown required inputs fail safe instead of becoming fabricated zeroes.
 */
public class PidControllerBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TUNING = IntegerProperty.create("tuning", 0, 3);

    public static final int CONTROL_CYCLE_TICKS = 2;
    public static final int MIN_OUT = 0;
    public static final int MAX_OUT = 15;
    public static final int DEADBAND_LEVELS = 1;
    public static final int INTEGRAL_MIN = -180;
    public static final int INTEGRAL_MAX = 180;

    public static final int MIN_KP = EngineeringDeviceParameters.PidParameters.MIN_KP;
    public static final int MAX_KP = EngineeringDeviceParameters.PidParameters.MAX_KP;
    public static final int MIN_KI_DIVISOR = EngineeringDeviceParameters.PidParameters.MIN_KI_DIVISOR;
    public static final int MAX_KI_DIVISOR = EngineeringDeviceParameters.PidParameters.MAX_KI_DIVISOR;
    public static final int MIN_KD = EngineeringDeviceParameters.PidParameters.MIN_KD;
    public static final int MAX_KD = EngineeringDeviceParameters.PidParameters.MAX_KD;
    public static final int MIN_DERIVATIVE_SMOOTHING = EngineeringDeviceParameters.PidParameters.MIN_DERIVATIVE_SMOOTHING;
    public static final int MAX_DERIVATIVE_SMOOTHING = EngineeringDeviceParameters.PidParameters.MAX_DERIVATIVE_SMOOTHING;
    public static final int MIN_RISE_LIMIT = EngineeringDeviceParameters.PidParameters.MIN_RISE_LIMIT;
    public static final int MAX_RISE_LIMIT = EngineeringDeviceParameters.PidParameters.MAX_RISE_LIMIT;
    public static final int MIN_FALL_LIMIT = EngineeringDeviceParameters.PidParameters.MIN_FALL_LIMIT;
    public static final int MAX_FALL_LIMIT = EngineeringDeviceParameters.PidParameters.MAX_FALL_LIMIT;

    private static final String KEY = "pid";
    private static final int AUTO_MODE = 0;
    private static final int MANUAL_MODE = 1;
    private static final int DEADBAND = DEADBAND_LEVELS;

    /**
     * Kp, Ki divisor, Kd, derivative smoothing, max rise / control-cycle, max fall / control-cycle.
     * The final two terms approximate actuator-command dynamics without pretending to simulate
     * a particular motor, valve or drive.
     */
    private static final int[][] PRESETS = {
            {1, 0, 0, 2, 1, 1},
            {2, 24, 0, 2, 1, 1},
            {2, 18, 1, 3, 2, 2},
            {3, 14, 2, 4, 3, 3}
    };

    private static final int RUNTIME_SIZE = 25;
    private static final int ACTUATOR_TARGET_SLOT = 22;
    private static final int SLEW_ACTIVE_SLOT = 23;
    private static final int SLEW_EPISODES_SLOT = 24;

    public PidControllerBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(TUNING, 2));
    }

    @Override
    public MapCodec<PidControllerBlock> codec() {
        return RedstoneEngineering.PID_CONTROLLER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(TUNING);
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return side == inputSide(state)
                || side == outputSide(state)
                || side == leftOf(outputSide(state))
                || side == rightOf(outputSide(state))
                || side == Direction.UP
                || side == Direction.DOWN;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("SETPOINT IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "setpoint"),
                new EngineeringPort("PROCESS VALUE IN", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.FEEDBACK, PortDirection.INPUT, true, "process_value"),
                new EngineeringPort("INHIBIT IN", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.INPUT, true, "inhibit"),
                new EngineeringPort("CONTROL OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.OUTPUT, true, "control_output"),
                new EngineeringPort("MODE SELECT", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "mode"),
                new EngineeringPort("MANUAL OUTPUT IN", Direction.DOWN, EngineeringDomain.REDSTONE,
                        PortKind.AUXILIARY, PortDirection.INPUT, true, "manual_output")
        );
    }

    private RedstoneObservationSupport.Observation observe(Level level, BlockPos pos, Direction side) {
        return RedstoneObservationSupport.observe(level, pos, side);
    }

    private static boolean usable(RedstoneObservationSupport.Observation observation) {
        return observation.valid();
    }

    private static PortQuality requiredQuality(RedstoneObservationSupport.Observation... observations) {
        for (RedstoneObservationSupport.Observation observation : observations) {
            if (observation.quality() == PortQuality.STALE) return PortQuality.STALE;
        }
        for (RedstoneObservationSupport.Observation observation : observations) {
            if (!observation.valid()) return observation.quality();
        }
        return PortQuality.VALID;
    }

    private PortQuality controlOutputQuality(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        RedstoneObservationSupport.Observation inhibit = observe(level, pos, rightOf(front));
        if (inhibit.quality() == PortQuality.STALE) return PortQuality.STALE;
        if (inhibit.valid() && inhibit.value() > 0) return PortQuality.VALID;

        RedstoneObservationSupport.Observation mode = observe(level, pos, Direction.UP);
        if (mode.quality() == PortQuality.STALE) return PortQuality.STALE;
        boolean manual = mode.valid() && mode.value() > 0;
        if (manual) return requiredQuality(observe(level, pos, Direction.DOWN));
        return requiredQuality(
                observe(level, pos, inputSide(state)),
                observe(level, pos, leftOf(front))
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        if (side == front) {
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), state.getValue(OUTPUT), controlOutputQuality(level, pos, state)));
        }
        RedstoneObservationSupport.Observation observation = observe(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), observation.value(), observation.quality()));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        RedstoneObservationSupport.Observation setpointObservation = observe(level, pos, inputSide(state));
        RedstoneObservationSupport.Observation processObservation = observe(level, pos, leftOf(front));
        RedstoneObservationSupport.Observation inhibitObservation = observe(level, pos, rightOf(front));
        RedstoneObservationSupport.Observation modeObservation = observe(level, pos, Direction.UP);
        RedstoneObservationSupport.Observation manualObservation = observe(level, pos, Direction.DOWN);

        int setpoint = usable(setpointObservation) ? setpointObservation.value() : 0;
        int process = usable(processObservation) ? processObservation.value() : 0;
        int inhibit = usable(inhibitObservation) ? inhibitObservation.value() : 0;
        int requestedMode = usable(modeObservation) && modeObservation.value() > 0 ? MANUAL_MODE : AUTO_MODE;
        int manualOutput = clamp(usable(manualObservation) ? manualObservation.value() : 0, MIN_OUT, MAX_OUT);
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        if (rt[21] == 0) {
            rt[20] = 8;
            rt[17] = AUTO_MODE;
            rt[3] = state.getValue(OUTPUT);
            rt[ACTUATOR_TARGET_SLOT] = rt[3];
            if (usable(processObservation)) rt[6] = process;
            rt[21] = 1;
        }

        // Unknown safety/mode coverage fails safe and must not mutate controller history as a fake zero sample.
        if (inhibitObservation.quality() == PortQuality.STALE || modeObservation.quality() == PortQuality.STALE) {
            forceFailSafe(rt);
            return 0;
        }

        rt[4] = inhibit > 0 ? 1 : 0;
        if (rt[4] != 0) {
            forceFailSafe(rt);
            if (usable(processObservation)) rt[6] = process;
            return usable(setpointObservation) && usable(processObservation)
                    ? recordTelemetry(level, pos, setpoint, process, 0) : 0;
        }

        // Required signal evidence is mode-dependent. Do not commit mode transfer against fabricated zeroes.
        if (requestedMode == MANUAL_MODE && !usable(manualObservation)) {
            forceFailSafe(rt);
            return 0;
        }
        if (requestedMode == AUTO_MODE && (!usable(setpointObservation) || !usable(processObservation))) {
            forceFailSafe(rt);
            return 0;
        }

        int rawError = setpoint - process;
        int controlError = Math.abs(rawError) <= DEADBAND ? 0 : rawError;
        EngineeringDeviceParameters.PidParameters parameters = configuredParameters(level, pos, state);
        int kp = parameters.kp();
        int kiDiv = parameters.kiDivisor();
        int kd = parameters.kd();
        int dSmooth = parameters.derivativeSmoothing();
        int riseLimit = parameters.riseLimit();
        int fallLimit = parameters.fallLimit();

        if (requestedMode != rt[17]) {
            if (rt[17] == MANUAL_MODE && requestedMode == AUTO_MODE) {
                rt[1] = controlError;
                rt[2] = 0;
                int iTerm = kiDiv == 0 ? 0 : rt[0] / kiDiv;
                rt[20] = clamp(rt[3] - kp * controlError - iTerm, -45, 45);
            }
            rt[17] = requestedMode;
            rt[19]++;
        }
        rt[18] = manualOutput;

        if (requestedMode == MANUAL_MODE) {
            rt[1] = controlError;
            rt[2] = 0;
            int out = applyActuatorDynamics(rt, manualOutput, riseLimit, fallLimit);
            rt[3] = out;
            if (usable(processObservation)) rt[6] = process;
            if (usable(setpointObservation) && usable(processObservation)) {
                updateStepDiagnostics(level, rt, setpoint, process, rawError);
                return recordTelemetry(level, pos, setpoint, process, out);
            }
            return out;
        }

        // Derivative-on-measurement avoids a control spike caused only by a setpoint step.
        rt[2] = PidActuatorLogic.filteredMeasurementDerivative(rt[6], process, rt[2], dSmooth);
        rt[1] = controlError;

        int candidateIntegral = clamp(rt[0] + controlError, INTEGRAL_MIN, INTEGRAL_MAX);
        int pTerm = kp * controlError;
        int iTerm = kiDiv == 0 ? 0 : candidateIntegral / kiDiv;
        int dTerm = -kd * rt[2];
        int unsat = rt[20] + pTerm + iTerm + dTerm;
        int actuatorTarget = clamp(unsat, MIN_OUT, MAX_OUT);
        int out = applyActuatorDynamics(rt, actuatorTarget, riseLimit, fallLimit);

        boolean saturatedHigh = unsat > MAX_OUT && controlError > 0;
        boolean saturatedLow = unsat < MIN_OUT && controlError < 0;
        boolean rateLimitedAgainstError = rt[SLEW_ACTIVE_SLOT] != 0
                && ((actuatorTarget > out && controlError > 0)
                || (actuatorTarget < out && controlError < 0));
        if (!saturatedHigh && !saturatedLow && !rateLimitedAgainstError) {
            rt[0] = candidateIntegral;
        } else if (saturatedHigh || saturatedLow) {
            rt[5]++;
        }

        rt[3] = out;
        rt[6] = process;
        updateStepDiagnostics(level, rt, setpoint, process, rawError);
        return recordTelemetry(level, pos, setpoint, process, out);
    }

    private static int applyActuatorDynamics(int[] rt, int target, int riseLimit, int fallLimit) {
        PidActuatorLogic.SlewResult result = PidActuatorLogic.slew(
                rt[3], target, riseLimit, fallLimit);
        boolean wasLimited = rt[SLEW_ACTIVE_SLOT] != 0;
        rt[ACTUATOR_TARGET_SLOT] = clamp(target, MIN_OUT, MAX_OUT);
        rt[SLEW_ACTIVE_SLOT] = result.limited() ? 1 : 0;
        if (result.limited() && !wasLimited && rt[SLEW_EPISODES_SLOT] < Integer.MAX_VALUE) {
            rt[SLEW_EPISODES_SLOT]++;
        }
        return result.output();
    }

    private static void forceFailSafe(int[] rt) {
        rt[3] = 0;
        rt[ACTUATOR_TARGET_SLOT] = 0;
        rt[SLEW_ACTIVE_SLOT] = 0;
    }

    public static int actuatorTarget(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0
                : clamp(rt[ACTUATOR_TARGET_SLOT], MIN_OUT, MAX_OUT);
    }

    public static boolean slewLimitActive(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt != null && rt.length >= RUNTIME_SIZE && rt[SLEW_ACTIVE_SLOT] != 0;
    }

    public static int slewLimitEvents(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0
                : Math.max(0, rt[SLEW_EPISODES_SLOT]);
    }

    public static EngineeringDeviceParameters.PidParameters presetParameters(BlockState state) {
        int[] p = PRESETS[Math.max(0, Math.min(PRESETS.length - 1, state.getValue(TUNING)))];
        return new EngineeringDeviceParameters.PidParameters(p[0], p[1], p[2], p[3], p[4], p[5]);
    }

    public static EngineeringDeviceParameters.PidParameters configuredParameters(Level level, BlockPos pos, BlockState state) {
        EngineeringDeviceParameters.PidParameters fallback = presetParameters(state);
        if (level instanceof ServerLevel serverLevel) {
            return EngineeringDeviceParameters.get(serverLevel).pidParameters(serverLevel, pos, fallback);
        }
        return fallback;
    }

    public static int riseLimit(BlockState state) {
        return presetParameters(state).riseLimit();
    }

    public static int fallLimit(BlockState state) {
        return presetParameters(state).fallLimit();
    }

    public static int riseLimit(Level level, BlockPos pos, BlockState state) {
        return configuredParameters(level, pos, state).riseLimit();
    }

    public static int fallLimit(Level level, BlockPos pos, BlockState state) {
        return configuredParameters(level, pos, state).fallLimit();
    }

    public static boolean adjustParameter(ServerLevel level, BlockPos pos, int parameter, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PidControllerBlock)) return false;
        EngineeringDeviceParameters.PidParameters current = configuredParameters(level, pos, state);
        EngineeringDeviceParameters.PidParameters next = switch (parameter) {
            case 0 -> new EngineeringDeviceParameters.PidParameters(current.kp() + delta, current.kiDivisor(), current.kd(), current.derivativeSmoothing(), current.riseLimit(), current.fallLimit());
            case 1 -> new EngineeringDeviceParameters.PidParameters(current.kp(), current.kiDivisor() + delta, current.kd(), current.derivativeSmoothing(), current.riseLimit(), current.fallLimit());
            case 2 -> new EngineeringDeviceParameters.PidParameters(current.kp(), current.kiDivisor(), current.kd() + delta, current.derivativeSmoothing(), current.riseLimit(), current.fallLimit());
            case 3 -> new EngineeringDeviceParameters.PidParameters(current.kp(), current.kiDivisor(), current.kd(), current.derivativeSmoothing() + delta, current.riseLimit(), current.fallLimit());
            case 4 -> new EngineeringDeviceParameters.PidParameters(current.kp(), current.kiDivisor(), current.kd(), current.derivativeSmoothing(), current.riseLimit() + delta, current.fallLimit());
            case 5 -> new EngineeringDeviceParameters.PidParameters(current.kp(), current.kiDivisor(), current.kd(), current.derivativeSmoothing(), current.riseLimit(), current.fallLimit() + delta);
            default -> current;
        };
        boolean changed = EngineeringDeviceParameters.get(level).setPidParameters(level, pos, next);
        if (changed) level.scheduleTick(pos, state.getBlock(), 1);
        return changed;
    }

    public static boolean loadPreset(ServerLevel level, BlockPos pos, int presetIndex) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PidControllerBlock)) return false;
        int bounded = Math.max(0, Math.min(PRESETS.length - 1, presetIndex));
        BlockState nextState = state.setValue(TUNING, bounded);
        level.setBlock(pos, nextState, Block.UPDATE_CLIENTS);
        boolean changed = EngineeringDeviceParameters.get(level)
                .setPidParameters(level, pos, presetParameters(nextState));
        level.scheduleTick(pos, nextState.getBlock(), 1);
        return changed || bounded != state.getValue(TUNING);
    }

    private static int recordTelemetry(Level level, BlockPos pos, int setpoint, int process, int output) {
        PidTelemetryStore.record(level, pos, setpoint, process, output);
        return output;
    }

    private static void updateStepDiagnostics(Level level, int[] rt, int setpoint, int process, int error) {
        int nowTick = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        if (Math.abs(setpoint - rt[7]) >= 2) {
            rt[7] = setpoint;
            rt[8] = nowTick;
            rt[9] = process;
            rt[10] = 0;
            rt[11] = 0;
            rt[12] = 1;
            rt[14] = 0;
            rt[15] = process;
            rt[16] = 0;
        }
        if (rt[12] == 0) return;

        int elapsed = Math.max(0, nowTick - rt[8]);
        int base = rt[15];
        int step = setpoint - base;
        rt[9] = step >= 0 ? Math.max(rt[9], process) : Math.min(rt[9], process);
        int overshoot = step >= 0 ? Math.max(0, process - setpoint) : Math.max(0, setpoint - process);
        rt[14] = Math.max(rt[14], overshoot);

        if (rt[10] == 0 && Math.abs(step) >= 2) {
            int progressed = step >= 0 ? process - base : base - process;
            if (progressed * 10 >= Math.abs(step) * 9) rt[10] = elapsed;
        }
        if (Math.abs(error) <= 1) rt[16]++;
        else rt[16] = 0;

        if (rt[11] == 0 && rt[16] >= 5) {
            rt[11] = elapsed;
            rt[12] = 0;
        }
        if (elapsed > 600) rt[12] = 0;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) {
        super.onPlace(s, l, p, o, m);
        if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, CONTROL_CYCLE_TICKS);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            RuntimeIntStore.remove(level, KEY, pos);
            PidTelemetryStore.clear(level, pos);
            AcceptanceEvidenceStore.clear(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removePidParameters(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        updateOutput(l, p, s, outputValue(l, p, s));
        l.scheduleTick(p, this, CONTROL_CYCLE_TICKS);
    }

    /** Applies only the existing bounded tuning-preset selection on the logical server. */
    public static boolean applyTuningAction(Level level, BlockPos pos, int action) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PidControllerBlock)) return false;

        int current = state.getValue(TUNING);
        int next = switch (action) {
            case PidControllerMenu.BUTTON_TUNING_PREVIOUS -> (current + 3) % 4;
            case PidControllerMenu.BUTTON_TUNING_NEXT -> (current + 1) % 4;
            default -> -1;
        };
        if (next < 0) return false;

        if (level instanceof ServerLevel serverLevel) {
            return loadPreset(serverLevel, pos, next);
        }
        level.setBlock(pos, state.setValue(TUNING, next), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Shared server-authoritative commissioning reset used by HMI and Shift shortcut. */
    public static boolean resetRuntimeAndTrend(Level l, BlockPos p) {
        if (l.isClientSide) return false;
        BlockState state = l.getBlockState(p);
        if (!(state.getBlock() instanceof PidControllerBlock controller)) return false;
        RuntimeIntStore.remove(l, KEY, p);
        PidTelemetryStore.clear(l, p);
        controller.updateOutput(l, p, state, 0);
        return true;
    }

    /**
     * Shared explicit acceptance capture. Retained acceptance history is observational evidence;
     * capture never mutates plant, controller, topology, or tuning state.
     */
    public static AcceptanceEvidenceRecord captureAcceptanceEvidence(Level level, BlockPos pos) {
        if (level.isClientSide) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PidControllerBlock)) return null;
        TopologyVisualizationSnapshot topology = EngineeringTopologyView.inspect(level, pos, state);
        CommissioningSnapshot commissioning = ClosedLoopCommissioning.inspectPid(level, pos);
        EngineeringAcceptanceSnapshot acceptance = EngineeringAcceptance.evaluate(topology, commissioning);
        return AcceptanceEvidenceStore.capture(
                level, pos, level.getGameTime(), state.getValue(TUNING), acceptance);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown() && h.getDirection() == outputSide(s)) {
                AcceptanceEvidenceRecord record = captureAcceptanceEvidence(l, p);
                if (record != null) {
                    AcceptanceEvidenceComparison comparison = AcceptanceEvidenceStore.compareLatestToPrevious(l, p).orElse(null);
                    String message = "Captured acceptance " + record.compact();
                    if (comparison != null) message += " | " + comparison.compact();
                    pl.displayClientMessage(Component.literal(message), true);
                }
            } else if (pl.isShiftKeyDown()) {
                if (resetRuntimeAndTrend(l, p)) {
                    pl.displayClientMessage(Component.literal(
                            "PID runtime + trend reset | Shift+FRONT captures acceptance evidence"), true);
                }
            } else if (pl instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (containerId, inventory, ignored) ->
                                        new PidControllerMenu(containerId, inventory, p),
                                Component.translatable("block.redstoneengineering.pid_controller")
                        ),
                        data -> data.writeBlockPos(p)
                );
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
