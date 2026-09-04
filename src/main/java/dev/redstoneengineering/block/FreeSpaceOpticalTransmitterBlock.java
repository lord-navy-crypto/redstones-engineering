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
import dev.redstoneengineering.physics.FreeSpaceOpticsKernel;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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

/** BACK redstone payload -> FRONT line-of-sight optical beam. */
public class FreeSpaceOpticalTransmitterBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    public FreeSpaceOpticalTransmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHANNEL, 0));
    }

    @Override
    public MapCodec<FreeSpaceOpticalTransmitterBlock> codec() {
        return RedstoneEngineering.FREE_SPACE_OPTICAL_TRANSMITTER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("REDSTONE POWER IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("FREE-SPACE OPTICAL OUT", outputSide(state), EngineeringDomain.OPTICAL,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "power")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int power = inputPower(level, pos, state);
        PortQuality quality = power > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL;
        if (side == inputSide(state)) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), power, PortQuality.VALID));
        }
        return Optional.of(new EngineeringPortSnapshot(port.get(), power, 0.0, 15.0, quality));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == inputSide(state);
    }

    private static int boundedSignal(int signal) {
        return Math.max(0, Math.min(15, signal));
    }

    private int inputPower(Level level, BlockPos pos, BlockState state) {
        Direction input = inputSide(state);
        return boundedSignal(level.getSignal(pos.relative(input), input));
    }

    private void emit(ServerLevel level, BlockPos pos, BlockState state) {
        int power = inputPower(level, pos, state);
        if (power > 0) {
            FreeSpaceOpticsKernel.emit(level, pos, outputSide(state), power, state.getValue(CHANNEL));
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 4);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        emit(level, pos, state);
        level.scheduleTick(pos, this, 4);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston
    ) {
        if (level instanceof ServerLevel serverLevel && neighborPos.equals(inputPos(pos, state))) {
            emit(serverLevel, pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int channel = (state.getValue(CHANNEL) + 1) % 4;
                BlockState next = state.setValue(CHANNEL, channel);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                emit(serverPlayer.serverLevel(), pos, next);
                player.displayClientMessage(Component.literal(
                        "Free-space optical TX channel=" + channel + " range<=48, LOS required"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
