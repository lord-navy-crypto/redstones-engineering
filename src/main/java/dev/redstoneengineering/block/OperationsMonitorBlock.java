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
import net.minecraft.world.phys.BlockHitResult;

/**
 * IOE monitor with explicit ports:
 * DOWN=machine running, UP=completed-cycle pulse, horizontal sides=queue/WIP proxy (0..15).
 * Measures throughput, utilization, downtime, cycle time, queue behavior and a derived
 * operations state without automatically changing the plant.
 */
public class OperationsMonitorBlock extends Block {
    private static final String KEY = "ops_monitor";
    private static final int RUNTIME_SIZE = 26;
    private static final int WINDOW_TICKS = 1200; // 60 s at 20 TPS

    private enum SystemState {
        NOMINAL,
        CONGESTED,
        NOISY,
        UNSTABLE,
        OVERLOADED,
        SAFETY_LIMITED,
        FAILED
    }

    public OperationsMonitorBlock(Properties p) {
        super(p);
    }

    @Override
    public MapCodec<OperationsMonitorBlock> codec() {
        return RedstoneEngineering.OPERATIONS_MONITOR_CODEC.value();
    }

    private int signal(Level l, BlockPos p, Direction d) {
        return Math.max(0, Math.min(15, l.getSignal(p.relative(d), d)));
    }

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
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            queue = Math.max(queue, signal(l, p, d));
        }
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

        if (run == 0) {
            r[11]++;
            if (r[0] == 1) r[12]++;
            r[18]++;
            r[19] = Math.max(r[19], r[18]);
        } else {
            r[18] = 0;
        }

        // IOE proxies. These are measurements, not automatic control actions.
        if (run == 1 && queue == 0) r[20]++;       // starved
        if (run == 0 && queue > 0) r[21]++;        // blocked/fault proxy
        if (run == 1 && queue >= 10) r[22]++;      // highQueueRun

        r[0] = run;
        r[1] = cycle;
        r[3]++;
        if (run == 1) r[4]++;
        r[13] = queue;
        r[14] += queue;
        r[15] = Math.max(r[15], queue);

        SystemState state = classifySystemState(run, queue, r);
        r[25] = state.ordinal();

        if (r[3] >= WINDOW_TICKS) {
            r[5] = r[2];
            r[6] = r[4];
            r[16] = r[14] / Math.max(1, r[3]);
            r[17] = r[15];
            r[2] = 0;
            r[3] = 0;
            r[4] = 0;
            r[14] = 0;
            r[15] = 0;
            r[23] = 0;
            r[24] = 0;
        }

        l.scheduleTick(p, this, 1);
    }

    private static SystemState classifySystemState(int run, int queue, int[] r) {
        // A sustained stopped machine with queued work is treated as a failed state.
        if (run == 0 && queue > 0 && r[18] >= 600) return SystemState.FAILED;
        // A shorter stopped-with-WIP condition is a blocking/safety-limited proxy.
        if (run == 0 && queue > 0) return SystemState.SAFETY_LIMITED;
        if (run == 1 && queue >= 13) return SystemState.OVERLOADED;
        if (run == 1 && queue >= 9) return SystemState.CONGESTED;
        // High queue variation and frequent run-state toggles act as noise/instability proxies.
        if (r[23] >= 120) return SystemState.NOISY;
        if (r[24] >= 12) return SystemState.UNSTABLE;
        return SystemState.NOMINAL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide) {
            if (pl.isShiftKeyDown()) {
                RuntimeIntStore.remove(l, KEY, p);
                pl.displayClientMessage(Component.literal("Operations monitor statistics reset"), true);
            } else {
                int[] r = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
                double currentU = r[3] == 0 ? 0 : 100.0 * r[4] / r[3];
                double lastU = 100.0 * r[6] / WINDOW_TICKS;
                double downtimeSec = r[11] / 20.0;
                SystemState state = SystemState.values()[Math.max(0, Math.min(SystemState.values().length - 1, r[25]))];

                pl.displayClientMessage(Component.literal(
                        "Operations state=" + state
                                + " | throughput last60s=" + r[5] + " cycles/min"
                                + " | util=" + String.format("%.1f", currentU) + "%"
                                + " last60s=" + String.format("%.1f", lastU) + "%"
                                + " | cycle last/avg/max=" + r[8] + "/" + r[9] + "/" + r[10] + "t"
                                + " | downtime=" + String.format("%.1f", downtimeSec) + "s"
                                + " events=" + r[12]
                                + " longest=" + r[19] + "t"
                                + " | queue now/avg60/max60=" + r[13] + "/" + r[16] + "/" + r[17]
                                + " | starved=" + r[20]
                                + " blocked/fault=" + r[21]
                                + " highQueueRun=" + r[22]
                                + " | ports DOWN=RUN UP=CYCLE HORIZ=QUEUE"), true);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
