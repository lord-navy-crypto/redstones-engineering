package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.List;
import java.util.Optional;

/**
 * Sampled-data boundary: VALUE is captured only on the configured TRIGGER edge and then held.
 * Capture age/count are transient evidence, not additional physics or persistent history.
 */
public class SampleHoldBlock extends DirectionalSignalBlock {
    public static final IntegerProperty TRIGGER_MODE = IntegerProperty.create("trigger_mode", 0, 2);
    private static final String KEY = "redstone_sample_hold";
    private static final int RUNTIME_SIZE = 5;
    private static final int HELD_SLOT = 0;
    private static final int TRIGGER_STATE_SLOT = 1;
    private static final int INITIALIZED_SLOT = 2;
    private static final int CAPTURE_COUNT = 3;
    private static final int LAST_CAPTURE_TICK = 4;

    public SampleHoldBlock(Properties properties) {
        super(properties); registerDefaultState(defaultBlockState().setValue(TRIGGER_MODE, 0));
    }
    @Override public MapCodec<SampleHoldBlock> codec() { return RedstoneEngineering.SAMPLE_HOLD_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(TRIGGER_MODE);
    }
    @Override protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state) || side == outputSide(state) || side == leftOf(facing) || side == rightOf(facing);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(
                new EngineeringPort("VALUE IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("HELD OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal"),
                new EngineeringPort("TRIGGER", leftOf(facing), EngineeringDomain.REDSTONE,
                        PortKind.TRIGGER, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("RESET", rightOf(facing), EngineeringDomain.REDSTONE,
                        PortKind.RESET, PortDirection.INPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction facing = state.getValue(FACING);
        int value;
        if (side == outputSide(state)) value = state.getValue(OUTPUT);
        else if (side == inputSide(state)) value = readBackInput(level, pos, state);
        else if (side == leftOf(facing) || side == rightOf(facing)) value = readInputFrom(level, pos, side);
        else return Optional.empty();
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, PortQuality.VALID));
    }

    private int[] runtime(Level level, BlockPos pos, BlockState state, boolean triggerNow) {
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (rt[INITIALIZED_SLOT] == 0) {
            rt[HELD_SLOT] = state.getValue(OUTPUT);
            // Seed the observed trigger level to avoid a false edge after reload or first runtime allocation.
            rt[TRIGGER_STATE_SLOT] = triggerNow ? 1 : 0;
            rt[INITIALIZED_SLOT] = 1;
        }
        return rt;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        boolean triggerNow = readInputFrom(level, pos, leftOf(facing)) > 0;
        boolean resetNow = readInputFrom(level, pos, rightOf(facing)) > 0;
        int[] rt = runtime(level, pos, state, triggerNow);
        boolean triggerBefore = rt[TRIGGER_STATE_SLOT] == 1;
        boolean rising = !triggerBefore && triggerNow;
        boolean falling = triggerBefore && !triggerNow;
        boolean sample = switch (state.getValue(TRIGGER_MODE)) {
            case 0 -> rising; case 1 -> falling; case 2 -> rising || falling; default -> rising;
        };

        if (resetNow) {
            rt[HELD_SLOT] = 0;
        } else if (sample) {
            rt[HELD_SLOT] = readBackInput(level, pos, state);
            rt[CAPTURE_COUNT]++;
            rt[LAST_CAPTURE_TICK] = boundedTick(level.getGameTime());
        }
        rt[TRIGGER_STATE_SLOT] = triggerNow ? 1 : 0;
        updateOutput(level, pos, state, rt[HELD_SLOT]);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    public static int captureCount(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length < RUNTIME_SIZE ? 0 : Math.max(0, rt[CAPTURE_COUNT]);
    }

    public static int sampleAgeTicks(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        if (rt == null || rt.length < RUNTIME_SIZE || rt[CAPTURE_COUNT] <= 0) return -1;
        long age = Math.max(0L, level.getGameTime() - Integer.toUnsignedLong(rt[LAST_CAPTURE_TICK]));
        return (int) Math.min(Integer.MAX_VALUE, age);
    }

    private static int boundedTick(long tick) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, tick));
    }

    public boolean adjustTriggerMode(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int mode = Math.floorMod(state.getValue(TRIGGER_MODE) + delta, 3);
        level.setBlock(pos, state.setValue(TRIGGER_MODE, mode), Block.UPDATE_CLIENTS);
        return true;
    }

    public boolean clearHeldValue(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        Direction facing = state.getValue(FACING);
        boolean triggerNow = readInputFrom(level, pos, leftOf(facing)) > 0;
        int[] rt = runtime(level, pos, state, triggerNow);
        rt[HELD_SLOT] = 0;
        rt[TRIGGER_STATE_SLOT] = triggerNow ? 1 : 0;
        rt[INITIALIZED_SLOT] = 1;
        updateOutput(level, pos, state, 0);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                clearHeldValue(level, pos);
                player.displayClientMessage(Component.literal(
                        "Sample & Hold | held value cleared | captures=" + captureCount(level, pos)
                                + " | lastCaptureAge=" + sampleAgeTicks(level, pos) + "t"), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static String modeName(int mode) {
        return switch (mode) { case 0 -> "RISING"; case 1 -> "FALLING"; case 2 -> "BOTH"; default -> "RISING"; };
    }
}