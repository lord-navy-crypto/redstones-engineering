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
import dev.redstoneengineering.physics.DataBusDriver;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

/** Explicit scalar-to-byte bridge. Vanilla redstone strength is never silently treated as binary. */
public class RedstoneByteEncoderBlock extends DirectionalDomainBlock implements EngineeringPortProvider, DataBusDriver {
    public RedstoneByteEncoderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<RedstoneByteEncoderBlock> codec() {
        return RedstoneEngineering.REDSTONE_BYTE_ENCODER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "REDSTONE SCALAR IN",
                        inputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        true,
                        "signal"
                ),
                new EngineeringPort(
                        "BYTE OUT",
                        outputSide(state),
                        EngineeringDomain.DATA_BUS_8,
                        PortKind.CONVERTER,
                        PortDirection.OUTPUT,
                        false,
                        "byte"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            int value = Math.max(0, Math.min(15, level.getSignal(inputPos(pos, state), inputSide(state))));
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), value, PortQuality.VALID));
        }
        boolean valid = InformationRuntime.valid(level, "bus8_out", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                InformationRuntime.value(level, "bus8_out", pos) & 0xFF,
                0.0,
                255.0,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == inputSide(state);
    }

    @Override
    public boolean drivesDataBusAt(BlockPos driverPos, BlockState driverState, BlockPos busPos) {
        return outputPos(driverPos, driverState).equals(busPos);
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        int value = Math.max(0, Math.min(15, level.getSignal(inputPos(pos, state), inputSide(state))));
        InformationRuntime.write(level, "bus8_out", pos, value, 0, true, 100);
        BlockPos output = outputPos(pos, state);
        if (level.getBlockState(output).getBlock() instanceof EightBitDataBusBlock) {
            DataBusNetwork.resolve(level, DataBusNetwork.collect(level, output));
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
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (level instanceof ServerLevel serverLevel) update(serverLevel, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            DataBusNetwork.releaseDriver(serverLevel, pos, outputPos(pos, state));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.literal(
                        "Encoder: redstone 0-15 -> byte " + InformationRuntime.value(level, "bus8_out", pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
