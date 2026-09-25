package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.world.OperationWorldResourceProvider;
import dev.redstoneengineering.operations.world.OperationWorldResourceSnapshot;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Heartbeat watchdog with configurable timeout. Only an observed input transition resets the timer. */
public class WatchdogBlock extends PassiveDirectionalSignalBlock implements OperationWorldResourceProvider {
    public static final int MIN_TIMEOUT_INDEX = 0;
    public static final int MAX_TIMEOUT_INDEX = 3;
    public static final int DEFAULT_TIMEOUT_INDEX = 1;
    public static final int TIMEOUT_SHORT_TICKS = 20;
    public static final int TIMEOUT_MEDIUM_TICKS = 40;
    public static final int TIMEOUT_LONG_TICKS = 80;
    public static final int TIMEOUT_EXTENDED_TICKS = 160;
    public static final int SAMPLE_TICKS = 2;
    public static final int MAX_AGE_TICKS = 12000;
    public static final int MAX_ALARM_OUTPUT = 15;
    public static final IntegerProperty TIMEOUT =
            IntegerProperty.create("timeout", MIN_TIMEOUT_INDEX, MAX_TIMEOUT_INDEX);
    private static final int[] TIMEOUT_TICKS = {
            TIMEOUT_SHORT_TICKS, TIMEOUT_MEDIUM_TICKS,
            TIMEOUT_LONG_TICKS, TIMEOUT_EXTENDED_TICKS
    };
    private static final String KEY = "watchdog";
    private static final int LAST_VALUE = 0;
    private static final int AGE = 1;
    private static final int TRANSITIONS = 2;
    private static final int TIMEOUTS = 3;
    private static final int SOURCE_SEEN = 4;
    private static final int RUNTIME_SIZE = 5;

    public WatchdogBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(TIMEOUT, DEFAULT_TIMEOUT_INDEX));
    }

    @Override public MapCodec<WatchdogBlock> codec() { return RedstoneEngineering.WATCHDOG_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(TIMEOUT); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("HEARTBEAT IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.TRIGGER, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("TIMEOUT OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "alarm")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            RedstoneObservationSupport.Observation heartbeat = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), heartbeat.value(), heartbeat.quality()));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), state.getValue(OUTPUT), PortQuality.VALID));
    }

    @Override
    public OperationWorldResourceSnapshot operationResourceSnapshot(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        int age = ageTicks(level, pos);
        int timeout = timeoutTicks(state.getValue(TIMEOUT));
        boolean timedOut = runtime != null && runtime.length >= RUNTIME_SIZE && age >= timeout;
        RedstoneObservationSupport.Observation heartbeat =
                RedstoneObservationSupport.observe(level, pos, inputSide(state));
        PortQuality quality = runtime == null || runtime.length < RUNTIME_SIZE
                ? PortQuality.STALE
                : heartbeat.quality();
        return new OperationWorldResourceSnapshot(
                "watchdog:" + pos.asLong(),
                Set.of("heartbeat_monitoring"),
                !timedOut,
                false,
                false,
                timedOut,
                quality,
                Map.of(
                        "heartbeat_age_ticks", (long) age,
                        "timeout_ticks", (long) timeout,
                        "transition_count", (long) transitionCount(level, pos),
                        "timeout_count", (long) timeoutCount(level, pos),
                        "source_seen", runtime != null && runtime.length > SOURCE_SEEN && runtime[SOURCE_SEEN] != 0 ? 1L : 0L
                )
        );
    }

    @Override protected int computeOutput(Level l, BlockPos p, BlockState s) {
        int[] rt = RuntimeIntStore.peek(l, KEY, p);
        int age = rt == null || rt.length <= AGE ? 0 : rt[AGE];
        return age >= timeoutTicks(s.getValue(TIMEOUT)) ? MAX_ALARM_OUTPUT : 0;
    }

    private void sample(ServerLevel l, BlockPos p, BlockState s) {
        int[] rt = RuntimeIntStore.get(l, KEY, p, RUNTIME_SIZE);
        RedstoneObservationSupport.Observation heartbeat = RedstoneObservationSupport.observe(l, p, inputSide(s));
        if (heartbeat.valid()) {
            int now = heartbeat.value();
            if (rt[SOURCE_SEEN] == 0) {
                // A source appearing is only a baseline. It is not a fabricated heartbeat edge.
                rt[LAST_VALUE] = now;
                rt[SOURCE_SEEN] = 1;
                rt[AGE] = Math.min(MAX_AGE_TICKS, rt[AGE] + SAMPLE_TICKS);
            } else if (now != rt[LAST_VALUE]) {
                rt[LAST_VALUE] = now;
                rt[AGE] = 0;
                rt[TRANSITIONS]++;
            } else {
                rt[AGE] = Math.min(MAX_AGE_TICKS, rt[AGE] + SAMPLE_TICKS);
            }
        } else {
            // Unknown/missing coverage cannot masquerade as a LOW transition.
            rt[SOURCE_SEEN] = 0;
            rt[AGE] = Math.min(MAX_AGE_TICKS, rt[AGE] + SAMPLE_TICKS);
        }
        int before = s.getValue(OUTPUT);
        int out = computeOutput(l, p, s);
        if (before == 0 && out > 0) rt[TIMEOUTS]++;
        updateOutput(l, p, s, out);
    }

    public static int boundedTimeoutIndex(int index) {
        return Math.max(MIN_TIMEOUT_INDEX, Math.min(MAX_TIMEOUT_INDEX, index));
    }

    public static int timeoutTicks(int index) {
        return TIMEOUT_TICKS[boundedTimeoutIndex(index)];
    }

    public static String timeoutChoicesText() {
        return TIMEOUT_SHORT_TICKS + " / " + TIMEOUT_MEDIUM_TICKS + " / "
                + TIMEOUT_LONG_TICKS + " / " + TIMEOUT_EXTENDED_TICKS + " ticks";
    }

    public static boolean stepTimeout(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WatchdogBlock watchdog)) return false;
        int current = boundedTimeoutIndex(state.getValue(TIMEOUT));
        int next = MIN_TIMEOUT_INDEX + Math.floorMod(
                current - MIN_TIMEOUT_INDEX + (forward ? 1 : -1),
                MAX_TIMEOUT_INDEX - MIN_TIMEOUT_INDEX + 1);
        level.setBlock(pos, state.setValue(TIMEOUT, next), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, watchdog, 1);
        return true;
    }

    public static boolean timedOut(Level level, BlockPos pos, BlockState state) {
        return ageTicks(level, pos) >= timeoutTicks(state.getValue(TIMEOUT));
    }
    public static int ageTicks(Level level, BlockPos pos) { int[] rt = RuntimeIntStore.peek(level, KEY, pos); return rt == null || rt.length <= AGE ? 0 : rt[AGE]; }
    public static int transitionCount(Level level, BlockPos pos) { int[] rt = RuntimeIntStore.peek(level, KEY, pos); return rt == null || rt.length <= TRANSITIONS ? 0 : rt[TRANSITIONS]; }
    public static int timeoutCount(Level level, BlockPos pos) { int[] rt = RuntimeIntStore.peek(level, KEY, pos); return rt == null || rt.length <= TIMEOUTS ? 0 : rt[TIMEOUTS]; }
    public static boolean sourceSeen(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt != null && rt.length > SOURCE_SEEN && rt[SOURCE_SEEN] != 0;
    }

    public boolean resetDiagnostics(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        RuntimeIntStore.remove(level, KEY, pos);
        updateOutput(level, pos, state, 0);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, SAMPLE_TICKS);
        return true;
    }

    @Override protected void onPlace(BlockState s, Level l, BlockPos p, BlockState o, boolean m) { super.onPlace(s,l,p,o,m); if(l instanceof ServerLevel sl) sl.scheduleTick(p,this,SAMPLE_TICKS); }
    @Override protected void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) { sample(l,p,s); l.scheduleTick(p,this,SAMPLE_TICKS); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected InteractionResult useWithoutItem(BlockState s, Level l, BlockPos p, Player pl, BlockHitResult h) {
        if (!l.isClientSide && pl instanceof ServerPlayer serverPlayer) {
            if (pl.isShiftKeyDown()) {
                resetDiagnostics(l, p);
                pl.displayClientMessage(net.minecraft.network.chat.Component.literal("Watchdog diagnostics reset"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, p);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
