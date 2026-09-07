package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

/** Converts level transitions into bounded redstone event pulses with inspectable transient evidence. */
public class EdgeDetectorBlock extends DirectionalSignalBlock {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    private static final String KEY = "redstone_edge_detector";
    private static final int RUNTIME_SIZE = 5;
    private static final int LAST = 0;
    private static final int REMAINING = 1;
    private static final int INITIALIZED = 2;
    private static final int EDGE_COUNT = 3;
    private static final int LAST_EDGE_TICK = 4;

    public EdgeDetectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, 0));
    }

    @Override public MapCodec<EdgeDetectorBlock> codec() { return RedstoneEngineering.EDGE_DETECTOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(MODE);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean now = readBackInput(level, pos, state) > 0;
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (rt[INITIALIZED] == 0) {
            rt[LAST] = now ? 1 : 0;
            rt[REMAINING] = 0;
            rt[INITIALIZED] = 1;
            updateOutput(level, pos, state, 0);
            return;
        }

        boolean last = rt[LAST] == 1;
        int remaining = rt[REMAINING];
        boolean edge = switch (state.getValue(MODE)) {
            case 0 -> !last && now;
            case 1 -> last && !now;
            case 2 -> last != now;
            default -> false;
        };
        if (edge) {
            remaining = 2;
            rt[EDGE_COUNT]++;
            rt[LAST_EDGE_TICK] = boundedTick(level.getGameTime());
        }

        updateOutput(level, pos, state, remaining > 0 ? 15 : 0);
        rt[LAST] = now ? 1 : 0;
        rt[REMAINING] = Math.max(0, remaining - 1);
        if (remaining > 0) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Read-only diagnostic accessors must never initialize the detector or create a false edge. */
    public static int lastInput(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : rt[LAST];
    }
    public static int pulseRemaining(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[REMAINING]);
    }
    public static boolean initialized(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt != null && rt.length >= RUNTIME_SIZE && rt[INITIALIZED] == 1;
    }
    public static int edgeCount(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[EDGE_COUNT]);
    }
    public static int lastEdgeAgeTicks(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        if (rt == null || rt.length < RUNTIME_SIZE || rt[EDGE_COUNT] <= 0) return -1;
        long age = Math.max(0L, level.getGameTime() - Integer.toUnsignedLong(rt[LAST_EDGE_TICK]));
        return (int) Math.min(Integer.MAX_VALUE, age);
    }

    private static int boundedTick(long tick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tick));
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            BlockState next = state.setValue(MODE, (state.getValue(MODE) + 1) % 3);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            player.displayClientMessage(Component.literal(
                    "Edge Detector | mode=" + modeName(next.getValue(MODE))
                            + " | edges=" + edgeCount(level, pos)
                            + " | lastEdgeAge=" + lastEdgeAgeTicks(level, pos) + "t"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static String modeName(int mode) {
        return switch (mode) { case 0 -> "RISING"; case 1 -> "FALLING"; case 2 -> "BOTH"; default -> "RISING"; };
    }
}
