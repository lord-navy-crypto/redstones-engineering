package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

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
            {1, 0, 0, 2},   // P: gentle
            {2, 24, 0, 2},  // PI: slow plant / zero steady-state error
            {2, 18, 1, 3},  // PID: balanced default
            {3, 14, 2, 4}   // PID+: aggressive, more derivative damping
    };

    /*
     * Runtime layout (transient, intentionally not BlockState):
     *  0 integralState, 1 previousError, 2 filteredDerivative, 3 output,
     *  4 inhibit, 5 saturationEvents, 6 process,
     *  7..16 step-response diagnostics,
     * 17 mode, 18 manualOutput, 19 modeTransfers,
     * 20 controllerBias, 21 initialized.
     */
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
        return super.isEngineeringPort(state, side)
                || side == leftOf(outputSide(state))
                || side == rightOf(outputSide(state))
                || side == Direction.UP
                || side == Direction.DOWN;
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
            rt[20] = 8; // neutral controller bias used by the original alpha controller
            rt[17] = AUTO_MODE;
            rt[21] = 1;
        }

        int rawError = setpoint - process;
        int controlError = Math.abs(rawError) <= DEADBAND ? 0 : rawError;
        int[] k = PRESETS[state.getValue(TUNING)];
        int kp = k[0], kiDiv = k[1], kd = k[2], dSmooth = k[3];

        // Track mode changes before computing the new output. On manual→auto,
        // solve the bias around the previous manual output so the transfer is bumpless.
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
            // Keep derivative history aligned while the operator owns the output.
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

        // Candidate integral. Commit only when not driving farther into saturation (anti-windup).
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
        int overshoot = step >= 0
                ? Math.max(0, process - setpoint)
                : Math.max(0, setpoint - process);
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
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        updateOutput(l, p, s, outputValue(l, p, s));
        l.scheduleTick(p, this, 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                updateOutput(l, p, s, 0);
                pl.displayClientMessage(Component.literal("PID runtime reset (integral/derivative/bias cleared)"), true);
            } else {
                int next = (s.getValue(TUNING) + 1) % 4;
                BlockState ns = s.setValue(TUNING, next);
                l.setBlock(p, ns, Block.UPDATE_CLIENTS);
                int[] rt = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
                String name = switch (next) {
                    case 0 -> "P-GENTLE";
                    case 1 -> "PI";
                    case 2 -> "PID-BALANCED";
                    default -> "PID-AGGRESSIVE";
                };
                String mode = rt[17] == MANUAL_MODE ? "MANUAL" : "AUTO";
                pl.displayClientMessage(Component.literal(
                        "PID " + name + " mode=" + mode
                                + " out=" + outputValue(l, p, ns)
                                + " err=" + rt[1]
                                + " integralState=" + rt[0]
                                + " dFilt=" + rt[2]
                                + " bias=" + rt[20]
                                + " manual=" + rt[18]
                                + " transfers=" + rt[19]
                                + " sat=" + rt[5]
                                + " | step rise90=" + rt[10] + "t settle=" + rt[11] + "t overshoot=" + rt[14]
                                + " | UP=MANUAL/AUTO DOWN=manual RIGHT=inhibit"), true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
