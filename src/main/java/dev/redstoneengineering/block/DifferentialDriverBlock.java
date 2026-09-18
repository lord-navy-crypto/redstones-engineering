package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Redstone binary input -> recomputable differential-data driver. */
public class DifferentialDriverBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty THRESHOLD = IntegerProperty.create("threshold", 0, 3);
    private static final int[] THRESHOLDS = {1, 4, 8, 12};

    public DifferentialDriverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THRESHOLD, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(THRESHOLD);
    }

    public static int thresholdValue(int index) {
        return THRESHOLDS[Math.max(0, Math.min(THRESHOLDS.length - 1, index))];
    }

    public static boolean stepThreshold(Level level, BlockPos pos, boolean forward) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DifferentialDriverBlock driver)) return false;
        int next = Math.floorMod(state.getValue(THRESHOLD) + (forward ? 1 : -1), THRESHOLDS.length);
        BlockState updated = state.setValue(THRESHOLD, next);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) driver.update(server, pos, updated);
        return true;
    }

    @Override
    public MapCodec<DifferentialDriverBlock> codec() {
        return RedstoneEngineering.DIFFERENTIAL_DRIVER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("REDSTONE BIT IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.INPUT, true, "bit"),
                new EngineeringPort("DIFFERENTIAL OUT", outputSide(state), EngineeringDomain.DIFFERENTIAL_DATA,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "bit")
        );
    }

    private RedstoneObservationSupport.Observation inputObservation(Level level, BlockPos pos, BlockState state) {
        return RedstoneObservationSupport.observe(level, pos, inputSide(state));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        RedstoneObservationSupport.Observation input = inputObservation(level, pos, state);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), input.value() >= thresholdValue(state.getValue(THRESHOLD)) ? 1.0 : 0.0,
                    0.0, 1.0, input.quality()));
        }
        InformationRuntime.Snapshot output = InformationRuntime.snapshot(level, "diff_out", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), output.value() & 1,
                0.0, 1.0, input.quality()));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == inputSide(state);
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        RedstoneObservationSupport.Observation input = inputObservation(level, pos, state);
        int bit = input.value() >= thresholdValue(state.getValue(THRESHOLD)) ? 1 : 0;
        InformationRuntime.Snapshot previous = InformationRuntime.snapshot(level, "diff_out", pos);
        InformationRuntime.write(level, "diff_out", pos, bit, 0, input.valid(), input.valid() ? 100 : 0);
        BlockPos output = outputPos(pos, state);
        if ((previous.value() != bit || previous.valid() != input.valid())
                && level.getBlockState(output).getBlock() instanceof DifferentialDataPairBlock) {
            DifferentialNetwork.recompute(level, output);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) update(serverLevel, pos, state);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (level instanceof ServerLevel serverLevel && neighborPos.equals(inputPos(pos, state))) {
            update(serverLevel, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            InformationRuntime.clear(level, "diff_out", pos);
            BlockPos output = outputPos(pos, state);
            BlockState outputState = level.getBlockState(output);
            if (outputState.getBlock() instanceof DifferentialDataPairBlock pair) {
                serverLevel.scheduleTick(output, pair, 1);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                stepThreshold(level, pos, true);
                BlockState next = level.getBlockState(pos);
                RedstoneObservationSupport.Observation input = inputObservation(level, pos, next);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Differential driver | threshold=" + thresholdValue(next.getValue(THRESHOLD))
                                + "/15 | input=" + input.value() + "/15"
                                + " | bit=" + (input.value() >= thresholdValue(next.getValue(THRESHOLD)) ? 1 : 0)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
