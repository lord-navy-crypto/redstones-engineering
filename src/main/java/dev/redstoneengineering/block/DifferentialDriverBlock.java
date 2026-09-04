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
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
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
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Redstone binary input -> recomputable differential-data driver. */
public class DifferentialDriverBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public DifferentialDriverBlock(Properties properties) {
        super(properties);
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

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            int signal = level.getSignal(inputPos(pos, state), inputSide(state));
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), signal > 0 ? 1.0 : 0.0, 0.0, 1.0, PortQuality.VALID));
        }
        boolean valid = InformationRuntime.valid(level, "diff_out", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), InformationRuntime.value(level, "diff_out", pos) & 1,
                0.0, 1.0, valid ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == inputSide(state);
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        int bit = level.getSignal(inputPos(pos, state), inputSide(state)) > 0 ? 1 : 0;
        int oldBit = InformationRuntime.value(level, "diff_out", pos) & 1;
        boolean oldValid = InformationRuntime.valid(level, "diff_out", pos);
        InformationRuntime.write(level, "diff_out", pos, bit, 0, true, 100);
        BlockPos output = outputPos(pos, state);
        if ((oldBit != bit || !oldValid)
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
            FieldDeviceUi.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
