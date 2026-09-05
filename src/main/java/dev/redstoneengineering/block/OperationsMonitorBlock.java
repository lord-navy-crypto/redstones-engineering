package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Observer-only IOE monitor with explicit ports:
 * DOWN=machine running, UP=completed-cycle pulse, horizontal sides=queue/WIP proxy (0..15).
 */
public class OperationsMonitorBlock extends Block implements EngineeringPortProvider {
    private static final String KEY = "ops_monitor";
    private static final int RUNTIME_SIZE = 26;
    private static final int WINDOW_TICKS = 1200;

    public enum SystemState { NOMINAL, CONGESTED, NOISY, UNSTABLE, OVERLOADED, SAFETY_LIMITED, FAILED }

    public OperationsMonitorBlock(Properties p) { super(p); }
    @Override public MapCodec<OperationsMonitorBlock> codec() { return RedstoneEngineering.OPERATIONS_MONITOR_CODEC.value(); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        ports.add(new EngineeringPort("MACHINE RUNNING", Direction.DOWN, EngineeringDomain.REDSTONE,
                PortKind.MEASUREMENT, PortDirection.INPUT, true, "run"));
        ports.add(new EngineeringPort("CYCLE PULSE", Direction.UP, EngineeringDomain.REDSTONE,
                PortKind.TRIGGER, PortDirection.INPUT, true, "cycle"));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            ports.add(new EngineeringPort("QUEUE / WIP", side, EngineeringDomain.REDSTONE,
                    PortKind.MEASUREMENT, PortDirection.INPUT, true, "queue"));
        }
        return List.copyOf(ports);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), signal(level, pos, side), PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null;
    }

    private static int signal(Level l, BlockPos p, Direction d) { return Math.max(0, Math.min(15, l.getSignal(p.relative(d), d))); }

    @Override
    protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) {
        super.onPlace(s, l, p, o, m);
        if (l instanceof ServerLevel sl) {
            int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
            r[7] = (int) Math.min(Integer.MAX_VALUE, sl.getGameTime());
            sl.scheduleTick(p, this, 1);
        }
    }

    @Override
    protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource rnd) {
        int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
        int run = signal(l, p, Direction.DOWN) > 0 ? 1 : 0;
        int cycle = signal(l, p, Direction.UP) > 0 ? 1 : 0;
        int queue = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) queue = Math.max(queue, signal(l, p, d));
        int gt = (int) Math.min(Integer.MAX_VALUE, l.getGameTime());

        if (cycle == 1 && r[1] == 0) {
            r[2]++;
            if (r[7] > 0) {
                int ct = Math.max(1, gt - r[7]);
                r[8] = ct;
                r[9] = r[9] == 0 ? ct : (r[9] * 7 + ct) / 8;
                r[10] = Math.max(r[10], ct);
            }
            r[7] = gt;
        }
        if (r[3] > 0 && run != r[0]) r[24]++;
        if (r[3] > 0) r[23] += Math.abs(queue - r[13]);
        if (run == 0) { r[11]++; if (r[0] == 1) r[12]++; r[18]++; r[19] = Math.max(r[19], r[18]); }
        else r[18] = 0;
        if (run == 1 && queue == 0) r[20]++;
        if (run == 0 && queue > 0) r[21]++;
        if (run == 1 && queue >= 10) r[22]++;

        r[0] = run; r[1] = cycle; r[3]++; if (run == 1) r[4]++;
        r[13] = queue; r[14] += queue; r[15] = Math.max(r[15], queue);
        r[25] = classifySystemState(run, queue, r).ordinal();

        if (r[3] >= WINDOW_TICKS) {
            r[5] = r[2]; r[6] = r[4]; r[16] = r[14] / Math.max(1, r[3]); r[17] = r[15];
            r[2] = 0; r[3] = 0; r[4] = 0; r[14] = 0; r[15] = 0; r[23] = 0; r[24] = 0;
        }
        l.scheduleTick(p, this, 1);
    }

    private static SystemState classifySystemState(int run, int queue, int[] r) {
        if (run == 0 && queue > 0 && r[18] >= 600) return SystemState.FAILED;
        if (run == 0 && queue > 0) return SystemState.SAFETY_LIMITED;
        if (run == 1 && queue >= 13) return SystemState.OVERLOADED;
        if (run == 1 && queue >= 9) return SystemState.CONGESTED;
        if (r[23] >= 120) return SystemState.NOISY;
        if (r[24] >= 12) return SystemState.UNSTABLE;
        return SystemState.NOMINAL;
    }

    private static int runtime(Level level, BlockPos pos, int index) {
        int[] r = RuntimeIntStore.peek(level, KEY, pos);
        return r == null || r.length <= index ? 0 : r[index];
    }
    public static boolean running(Level level, BlockPos pos) { return runtime(level,pos,0) != 0; }
    public static int cyclesCurrentWindow(Level level, BlockPos pos) { return runtime(level,pos,2); }
    public static int throughputLastWindow(Level level, BlockPos pos) { return runtime(level,pos,5); }
    public static int queueNow(Level level, BlockPos pos) { return runtime(level,pos,13); }
    public static int downtimeTicks(Level level, BlockPos pos) { return runtime(level,pos,11); }
    public static int stateOrdinal(Level level, BlockPos pos) { return runtime(level,pos,25); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide && pl instanceof ServerPlayer serverPlayer) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Operations monitor statistics reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, p);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
