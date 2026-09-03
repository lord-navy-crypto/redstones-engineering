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
 * BACK=setpoint, LEFT=process value, RIGHT=INHIBIT, FRONT=control output.
 * Tuning is intentionally preset-based so the block teaches tuning without becoming a GUI-only magic controller.
 */
public class PidControllerBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty TUNING = IntegerProperty.create("tuning", 0, 3);
    private static final String KEY = "pid";
    // Kp numerator, Ki divisor, Kd numerator, derivative smoothing divisor.
    private static final int[][] PRESETS = {
            {1, 0, 0, 2},   // P: gentle
            {2, 24, 0, 2},  // PI: slow plant / zero steady-state error
            {2, 18, 1, 3},  // PID: balanced default
            {3, 14, 2, 4}   // PID+: aggressive, more derivative damping
    };

    public PidControllerBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(TUNING, 2));
    }

    @Override public MapCodec<PidControllerBlock> codec() { return RedstoneEngineering.PID_CONTROLLER_CODEC.value(); }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(TUNING);
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        return super.isEngineeringPort(state, side)
                || side == leftOf(outputSide(state))
                || side == rightOf(outputSide(state));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        int setpoint = readBackInput(level, pos, state);
        int process = readInputFrom(level, pos, leftOf(front));
        int inhibit = readInputFrom(level, pos, rightOf(front));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, 17);

        if (inhibit > 0) {
            rt[4] = 1;
            return 0;
        }
        rt[4] = 0;

        int error = setpoint - process;
        int[] k = PRESETS[state.getValue(TUNING)];
        int kp = k[0], kiDiv = k[1], kd = k[2], dSmooth = k[3];

        int rawDerivative = error - rt[1];
        rt[2] += (rawDerivative - rt[2]) / Math.max(1, dSmooth); // low-pass derivative
        rt[1] = error;

        // Candidate integral. Commit only when not driving farther into saturation (anti-windup).
        int candidateIntegral = clamp(rt[0] + error, -180, 180);
        int pTerm = kp * error;
        int iTerm = kiDiv == 0 ? 0 : candidateIntegral / kiDiv;
        int dTerm = kd * rt[2];
        int unsat = 8 + pTerm + iTerm + dTerm;
        int out = clamp(unsat, 0, 15);

        boolean saturatedHigh = unsat > 15 && error > 0;
        boolean saturatedLow = unsat < 0 && error < 0;
        if (!saturatedHigh && !saturatedLow) rt[0] = candidateIntegral;
        else rt[5]++;

        rt[3] = out;
        // Passive process-response test: a meaningful setpoint step starts a measurement window.
        int nowTick = (int)Math.min(Integer.MAX_VALUE, level.getGameTime());
        if (Math.abs(setpoint - rt[7]) >= 2) {
            rt[7] = setpoint; rt[8] = nowTick; rt[9] = process; rt[10] = 0; rt[11] = 0; rt[12] = 1; rt[14] = 0; rt[15] = process; rt[16] = 0;
        }
        if (rt[12] != 0) {
            int elapsed = Math.max(0, nowTick - rt[8]);
            int base = rt[15], step = setpoint - base;
            rt[9] = step >= 0 ? Math.max(rt[9], process) : Math.min(rt[9], process);
            int overshoot = step >= 0 ? Math.max(0, process - setpoint) : Math.max(0, setpoint - process);
            rt[14] = Math.max(rt[14], overshoot);
            if (rt[10] == 0 && Math.abs(step) >= 2) {
                int progressed = step >= 0 ? process - base : base - process;
                if (progressed * 10 >= Math.abs(step) * 9) rt[10] = elapsed;
            }
            if (Math.abs(error) <= 1) rt[16]++; else rt[16] = 0;
            if (rt[11] == 0 && rt[16] >= 5) { rt[11] = elapsed; rt[12] = 0; }
            if (elapsed > 600) rt[12] = 0;
        }
        rt[6] = process;
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

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
                pl.displayClientMessage(Component.literal("PID runtime reset (integral/derivative cleared)"), true);
            } else {
                int next = (s.getValue(TUNING) + 1) % 4;
                BlockState ns = s.setValue(TUNING, next);
                l.setBlock(p, ns, Block.UPDATE_CLIENTS);
                int[] rt = RuntimeIntStore.get(l, KEY, p, 17);
                String name = switch (next) { case 0 -> "P-GENTLE"; case 1 -> "PI"; case 2 -> "PID-BALANCED"; default -> "PID-AGGRESSIVE"; };
                pl.displayClientMessage(Component.literal("PID " + name + " | out=" + outputValue(l,p,ns) + " err=" + rt[1] + " I=" + rt[0] + " dFilt=" + rt[2] + " sat=" + rt[5] + " | step rise90="+rt[10]+"t settle="+rt[11]+"t overshoot="+rt[14]+" PV | "+(rt[12]!=0?"TESTING":"IDLE")+" | RIGHT=inhibit"), true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
