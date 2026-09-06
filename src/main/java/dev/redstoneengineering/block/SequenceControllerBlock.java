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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/**
 * Four-step finite-state sequence controller.
 * BACK=RUN/ENABLE, LEFT=ADVANCE edge, RIGHT=RESET, UP=HOLD, FRONT=step code 0..4.
 *
 * <p>The controller deliberately does not become a general computer: it is a small,
 * inspectable industrial sequence primitive that composes with vanilla redstone.</p>
 */
public class SequenceControllerBlock extends PassiveDirectionalSignalBlock {
    private static final String KEY = "sequence_controller";
    // [step, prevAdvance, prevRun, transitions, completedCycles, resets, holdTicks]
    private static final int RUNTIME_SIZE = 7;

    public SequenceControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SequenceControllerBlock> codec() {
        return EngineeringSystemsModule.SEQUENCE_CONTROLLER_CODEC;
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction front = outputSide(state);
        return side == inputSide(state)
                || side == leftOf(front)
                || side == rightOf(front)
                || side == Direction.UP
                || side == front;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction front = outputSide(state);
        return List.of(
                new EngineeringPort("RUN / ENABLE", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "enable"),
                new EngineeringPort("ADVANCE", leftOf(front), EngineeringDomain.REDSTONE,
                        PortKind.TRIGGER, PortDirection.INPUT, true, "advance"),
                new EngineeringPort("RESET", rightOf(front), EngineeringDomain.REDSTONE,
                        PortKind.RESET, PortDirection.INPUT, true, "reset"),
                new EngineeringPort("HOLD", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "hold"),
                new EngineeringPort("STEP CODE", front, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.OUTPUT, true, "step")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int value = side == outputSide(state) ? state.getValue(OUTPUT) : readInputFrom(level, pos, side);
        PortQuality quality = side == outputSide(state) && value == 0 ? PortQuality.NO_SIGNAL : PortQuality.VALID;
        return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, quality));
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        Direction front = outputSide(state);
        int run = readBackInput(level, pos, state);
        int advance = readInputFrom(level, pos, leftOf(front));
        int reset = readInputFrom(level, pos, rightOf(front));
        int hold = readInputFrom(level, pos, Direction.UP);

        if (reset > 0) {
            if (runtime[0] != 0) {
                runtime[0] = 0;
                runtime[3]++;
                runtime[5]++;
            }
            runtime[1] = advance > 0 ? 1 : 0;
            runtime[2] = run > 0 ? 1 : 0;
            return 0;
        }

        if (run <= 0) {
            if (runtime[0] != 0) {
                runtime[0] = 0;
                runtime[3]++;
            }
            runtime[1] = advance > 0 ? 1 : 0;
            runtime[2] = 0;
            return 0;
        }

        if (runtime[2] == 0 && runtime[0] == 0) {
            runtime[0] = 1;
            runtime[3]++;
        }
        runtime[2] = 1;

        if (hold > 0) {
            runtime[6]++;
            runtime[1] = advance > 0 ? 1 : 0;
            return runtime[0];
        }

        boolean risingAdvance = advance > 0 && runtime[1] == 0;
        if (risingAdvance && runtime[0] > 0) {
            if (runtime[0] < 4) {
                runtime[0]++;
            } else {
                runtime[0] = 0;
                runtime[4]++;
            }
            runtime[3]++;
        }
        runtime[1] = advance > 0 ? 1 : 0;
        return runtime[0];
    }

    public static int step(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length == 0 ? 0 : runtime[0];
    }

    public static int completedCycles(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 5 ? 0 : runtime[4];
    }

    public static int transitions(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < 4 ? 0 : runtime[3];
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
                int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
                runtime[0] = 0;
                runtime[1] = 0;
                runtime[2] = 0;
                runtime[5]++;
                updateOutput(level, pos, state, 0);
                player.displayClientMessage(Component.literal("Sequence reset | waiting for RUN rising edge"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
