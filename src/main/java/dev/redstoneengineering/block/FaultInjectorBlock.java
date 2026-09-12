package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
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
 * Controlled reliability-test fault injector.
 * BACK=signal input, RIGHT=ARM, FRONT=faulted output.
 * Modes: 0 STUCK_LOW, 1 STUCK_HIGH, 2 BIAS_PLUS_4, 3 BIAS_MINUS_4.
 */
public class FaultInjectorBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 3);
    private static final String KEY = "fault_injector";
    private static final String[] MODE_LABELS = {"STUCK LOW", "STUCK HIGH", "BIAS +4", "BIAS -4"};
    private static final int RUNTIME_SIZE = 5;

    public FaultInjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, 0));
    }

    @Override
    public MapCodec<FaultInjectorBlock> codec() {
        return EngineeringSystemsModule.FAULT_INJECTOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE);
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state) || side == rightOf(front) || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("SIGNAL IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("FAULT ARM", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "arm"),
                new EngineeringPort("FAULTED OUT", front, EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "faulted_signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        Direction front = outputSide(state);
        int value = side == front ? state.getValue(OUTPUT) : readInputFrom(level, pos, side);
        PortQuality quality = side == front && active(level, pos) ? PortQuality.FAULT : PortQuality.VALID;
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, quality));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        Direction front = outputSide(state);
        int input = readBackInput(level, pos, state);
        boolean armed = readInputFrom(level, pos, rightOf(front)) > 0;
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (armed && runtime[0] == 0) runtime[1]++;
        runtime[0] = armed ? 1 : 0;

        int output = input;
        if (armed) {
            output = switch (state.getValue(MODE)) {
                case 0 -> 0;
                case 1 -> 15;
                case 2 -> Math.min(15, input + 4);
                default -> Math.max(0, input - 4);
            };
            if (output != input) runtime[2]++;
        }
        runtime[3] = input;
        runtime[4] = output;
        return output;
    }

    public static boolean active(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > 0 && runtime[0] != 0;
    }

    public static int activationCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 2 ? 0 : runtime[1];
    }

    public static int lastInput(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 4 ? 0 : runtime[3];
    }

    public static int lastOutput(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 5 ? 0 : runtime[4];
    }

    public static String modeLabel(BlockState state) {
        return modeLabelFor(state.getValue(MODE));
    }

    public static String modeLabelFor(int mode) {
        return MODE_LABELS[Math.floorMod(mode, MODE_LABELS.length)];
    }

    public boolean adjustMode(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int next = Math.floorMod(state.getValue(MODE) + delta, 4);
        BlockState updated = state.setValue(MODE, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
        return true;
    }

    public void cycleMode(Level level, BlockPos pos) {
        adjustMode(level, pos, 1);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                RuntimeIntStore.remove(level, KEY, pos);
                player.displayClientMessage(Component.literal("Fault injector diagnostics reset"), true);
            } else {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
