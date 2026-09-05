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
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceComparison;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceRecord;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceStore;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptance;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceSnapshot;
import dev.redstoneengineering.diagnostics.topology.EngineeringTopologyView;
import dev.redstoneengineering.diagnostics.topology.TopologyVisualizationSnapshot;
import dev.redstoneengineering.physics.RuntimeIntStore;
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
 * manual→auto transfer.
 */
public class PidControllerBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TUNING = IntegerProperty.create("tuning", 0, 3);

    private static final String KEY = "pid";
    private static final int AUTO_MODE = 0;
    private static final int MANUAL_MODE = 1;
    private static final int MIN_OUT = 0;
    private static final int MAX_OUT = 15;
    private static final int DEADBAND = 1;

    // Kp numerator, Ki divisor, Kd numerator, derivative smoothing divisor.
    private static final int[][] PRESETS = {
            {1, 0, 0, 2},
            {2, 24, 0, 2},
            {2, 18, 1, 3},
            {3, 14, 2, 4}
    };

    /* Runtime layout: controller state + step-response diagnostics; intentionally not BlockState. */
    private static final int RUNTIME_SIZE = 22;

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

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        int value;
        if (side == front) value = state.getValue(OUTPUT);
        else if (side == inputSide(state)) value = readBackInput(level, pos, state);
        else value = readInputFrom(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, PortQuality.VALID));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        int setpoint = readBackInput(level, pos, state);
        int process = readInputFrom(level, pos, leftOf(front));
        int inhibit = readInputFrom(level, pos, rightOf(front));
        int requestedMode = readInputFrom(level, pos, Direction.UP) > 0 ? MANUAL_MODE : AUTO_MODE;
        int manualOutput = clamp(readInputFrom(level, pos, Direction.DOWN), MIN_OUT, MAX_OUT);
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        if (rt[21] == 0) {
            rt[20] = 8;
            rt[17] = AUTO_MODE;
            rt[21] = 1;
        }

        int rawError = setpoint - process;
        int controlError = Math.abs(rawError) <= DEADBAND ? 0 : rawError;
        int[] k = PRESETS[state.getValue(TUNING)];
        int kp = k[0], kiDiv = k[1], kd = k[2], dSmooth = k[3];

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
        rt[4] = inhibit > 0 ? 1 : 0;

        if (rt[4] != 0) {
            rt[3] = 0;
            rt[6] = process;
            return 0;
        }

        if (requestedMode == MANUAL_MODE) {
            rt[1] = controlError;
            rt[2] = 0;
            rt[3] = manualOutput;
            rt[6] = process;
            updateStepDiagnostics(level, rt, setpoint, process, rawError);
            return manualOutput;
        }

        int rawDerivative = controlError - rt[1];
        rt[2] += (rawDerivative - rt[2]) / Math.max(1, dSmooth);
        rt[1] = controlError;

        // Candidate integral with conditional-commit anti-windup at the 0..15 actuator boundary.
        int candidateIntegral = clamp(rt[0] + controlError, -180, 180);
        int pTerm = kp * controlError;
        int iTerm = kiDiv == 0 ? 0 : candidateIntegral / kiDiv;
        int dTerm = kd * rt[2];
        int unsat = rt[20] + pTerm + iTerm + dTerm;
        int out = clamp(unsat, MIN_OUT, MAX_OUT);

        boolean saturatedHigh = unsat > MAX_OUT && controlError > 0;
        boolean saturatedLow = unsat < MIN_OUT && controlError < 0;
        if (!saturatedHigh && !saturatedLow) {
            rt[0] = candidateIntegral;
        } else {
            rt[5]++;
        }

        rt[3] = out;
        rt[6] = process;
        updateStepDiagnostics(level, rt, setpoint, process, rawError);
        return out;
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
        if (l instanceof ServerLevel sl) sl.scheduleTick(p, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            RuntimeIntStore.remove(level, KEY, pos);
            AcceptanceEvidenceStore.clear(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        updateOutput(l, p, s, outputValue(l, p, s));
        l.scheduleTick(p, this, 2);
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

        level.setBlock(pos, state.setValue(TUNING, next), Block.UPDATE_CLIENTS);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown() && h.getDirection() == outputSide(s)) {
                captureAcceptanceEvidence(s, l, p, pl);
            } else if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                updateOutput(l, p, s, 0);
                pl.displayClientMessage(Component.literal(
                        "PID runtime reset | Shift+FRONT captures acceptance evidence"), true);
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

    private static void captureAcceptanceEvidence(BlockState state, Level level, BlockPos pos, Player player) {
        TopologyVisualizationSnapshot topology = EngineeringTopologyView.inspect(level, pos, state);
        CommissioningSnapshot commissioning = ClosedLoopCommissioning.inspectPid(level, pos);
        EngineeringAcceptanceSnapshot acceptance = EngineeringAcceptance.evaluate(topology, commissioning);
        AcceptanceEvidenceRecord record = AcceptanceEvidenceStore.capture(
                level, pos, level.getGameTime(), state.getValue(TUNING), acceptance);
        AcceptanceEvidenceComparison comparison = AcceptanceEvidenceStore.compareLatestToPrevious(level, pos).orElse(null);

        String message = "Captured acceptance " + record.compact();
        if (comparison != null) message += " | " + comparison.compact();
        player.displayClientMessage(Component.literal(message), true);
    }
}
