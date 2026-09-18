package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.core.signal.SignalMath;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
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

import java.util.Optional;

/** Real-time series redstone signal conditioner with independently configurable RX/TX faces. */
public class SignalConditionerBlock extends DirectionalSignalBlock {
    private static final String RUNTIME_KEY = "signal_conditioner";
    private static final int LIMITING_ACTIVE = 0;
    private static final int LIMITING_EPISODES = 1;
    private static final int LAST_LIMIT_TICK = 2;
    private static final int RUNTIME_SIZE = 3;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 4);
    public static final IntegerProperty PARAM = IntegerProperty.create("param", 0, 15);

    public SignalConditionerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MODE, 0).setValue(PARAM, 2));
    }

    @Override
    public MapCodec<SignalConditionerBlock> codec() {
        return RedstoneEngineering.SIGNAL_CONDITIONER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, PARAM);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int input = readBackInput(level, pos, state);
        int output = calculate(input, state.getValue(OUTPUT), state.getValue(MODE), state.getValue(PARAM));
        boolean limiting = limitingActive(level, pos, state);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        if (limiting && runtime[LIMITING_ACTIVE] == 0) {
            runtime[LIMITING_EPISODES]++;
            runtime[LAST_LIMIT_TICK] = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        }
        runtime[LIMITING_ACTIVE] = limiting ? 1 : 0;
        updateOutput(level, pos, state, output);
    }

    private static int calculate(int input, int previousOutput, int mode, int param) {
        return switch (mode) {
            case 0 -> SignalMath.gain(input, Math.max(1, Math.min(4, param)));
            case 1 -> SignalMath.offset(input, Math.min(10, param) - 5);
            case 2 -> Math.min(input, Math.max(1, param));
            case 3 -> SignalMath.threshold(input, Math.max(1, param));
            case 4 -> Math.abs(input - previousOutput) >= Math.max(1, Math.min(4, param)) ? input : previousOutput;
            default -> input;
        };
    }

    /**
     * True only when a static transfer mode is hitting the physical 0..15 boundary.
     * Threshold HIGH and deadband hold are intentional transfer semantics, not saturation faults.
     */
    public static boolean limitingActive(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SignalConditionerBlock conditioner)) return false;
        int input = conditioner.readBackInput(level, pos, state);
        int mode = state.getValue(MODE);
        int param = state.getValue(PARAM);
        return switch (mode) {
            case 0 -> input * Math.max(1, Math.min(4, param)) > 15;
            case 1 -> {
                int raw = input + (Math.min(10, param) - 5);
                yield raw < 0 || raw > 15;
            }
            case 2 -> input > Math.max(1, param);
            default -> false;
        };
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPortSnapshot> base = super.engineeringSnapshot(level, pos, state, side);
        if (base.isEmpty() || side != outputSide(state) || !limitingActive(level, pos, state)) return base;
        EngineeringPortSnapshot snapshot = base.get();
        return Optional.of(new EngineeringPortSnapshot(
                snapshot.port(), snapshot.value(), snapshot.minimum(), snapshot.maximum(), PortQuality.SATURATED));
    }

    public static int limitingEpisodes(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE ? 0 : Math.max(0, runtime[LIMITING_EPISODES]);
    }

    public static int lastLimitingAgeTicks(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        if (runtime == null || runtime.length < RUNTIME_SIZE || runtime[LIMITING_EPISODES] <= 0) return -1;
        long age = Math.max(0L, level.getGameTime() - Integer.toUnsignedLong(runtime[LAST_LIMIT_TICK]));
        return (int) Math.min(Integer.MAX_VALUE, age);
    }

    public static int inspectInput(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof SignalConditionerBlock conditioner)) return 0;
        return conditioner.readBackInput(level, pos, state);
    }

    public static Direction inputDirection(BlockState state) {
        return seriesInputSide(state);
    }

    public static Direction outputDirection(BlockState state) {
        return seriesOutputSide(state);
    }

    /** Applies a bounded, server-authoritative configuration action. */
    public static boolean applyConfigurationAction(Level level, BlockPos pos, int action) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SignalConditionerBlock conditioner)) return false;

        if (action == SignalConditionerMenu.BUTTON_ROTATE_LEFT) {
            return rotateWholeRoute(level, pos, false);
        }
        if (action == SignalConditionerMenu.BUTTON_ROTATE_RIGHT) {
            return rotateWholeRoute(level, pos, true);
        }

        int mode = state.getValue(MODE);
        int param = state.getValue(PARAM);
        BlockState next;
        switch (action) {
            case SignalConditionerMenu.BUTTON_MODE_PREVIOUS -> {
                int nextMode = (mode + 4) % 5;
                next = state.setValue(MODE, nextMode).setValue(PARAM, defaultParam(nextMode));
            }
            case SignalConditionerMenu.BUTTON_MODE_NEXT -> {
                int nextMode = (mode + 1) % 5;
                next = state.setValue(MODE, nextMode).setValue(PARAM, defaultParam(nextMode));
            }
            case SignalConditionerMenu.BUTTON_PARAM_DECREASE ->
                    next = state.setValue(PARAM, cycleParam(mode, param, -1));
            case SignalConditionerMenu.BUTTON_PARAM_INCREASE ->
                    next = state.setValue(PARAM, cycleParam(mode, param, 1));
            default -> {
                return false;
            }
        }

        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(pos, conditioner);
        level.updateNeighborsAt(pos.relative(inputDirection(next)), conditioner);
        level.updateNeighborsAt(pos.relative(outputDirection(next)), conditioner);
        level.scheduleTick(pos, conditioner, 1);
        return true;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                applyConfigurationAction(level, pos, SignalConditionerMenu.BUTTON_PARAM_INCREASE);
                BlockState next = level.getBlockState(pos);
                int input = inspectInput(level, pos, next);
                int output = calculate(input, next.getValue(OUTPUT), next.getValue(MODE), next.getValue(PARAM));
                player.displayClientMessage(Component.literal(
                        "Conditioner quick-adjust | " + modeName(next.getValue(MODE))
                                + " " + parameterText(next.getValue(MODE), next.getValue(PARAM))
                                + " | " + inputDirection(next).getName().toUpperCase() + " IN=" + input
                                + " → OUT≈" + output + " " + outputDirection(next).getName().toUpperCase()
                                + " | limiting=" + (limitingActive(level, pos, next) ? "YES" : "NO")
                                + " | normal right-click opens Engineering UI"), true);
            } else {
                serverPlayer.openMenu(
                        new SimpleMenuProvider(
                                (containerId, inventory, ignored) -> new SignalConditionerMenu(containerId, inventory, pos),
                                Component.translatable("block.redstoneengineering.signal_conditioner")),
                        data -> data.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int defaultParam(int mode) {
        return switch (mode) {
            case 0 -> 2;
            case 1 -> 5;
            case 2 -> 10;
            case 3 -> 8;
            case 4 -> 2;
            default -> 1;
        };
    }

    private static int cycleParam(int mode, int param, int delta) {
        int min = mode == 1 ? 0 : 1;
        int max = switch (mode) {
            case 0, 4 -> 4;
            case 1 -> 10;
            case 2, 3 -> 15;
            default -> 15;
        };
        int next = param + Integer.signum(delta);
        if (next > max) return min;
        if (next < min) return max;
        return next;
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "SCALE";
            case 1 -> "OFFSET";
            case 2 -> "CLAMP";
            case 3 -> "THRESHOLD";
            case 4 -> "DEADBAND";
            default -> "UNKNOWN";
        };
    }

    private static String parameterText(int mode, int param) {
        return switch (mode) {
            case 0 -> "scale=x" + Math.max(1, Math.min(4, param));
            case 1 -> "offset=" + (Math.min(10, param) - 5);
            case 2 -> "max=" + Math.max(1, param);
            case 3 -> "threshold=" + Math.max(1, param);
            case 4 -> "band=" + Math.max(1, Math.min(4, param));
            default -> "";
        };
    }
}
