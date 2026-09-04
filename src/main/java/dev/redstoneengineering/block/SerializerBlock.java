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
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Converts one 8-bit bus word into a framed serial payload. */
public class SerializerBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public SerializerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SerializerBlock> codec() {
        return RedstoneEngineering.SERIALIZER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "BYTE IN",
                        inputSide(state),
                        EngineeringDomain.DATA_BUS_8,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        false,
                        "byte"
                ),
                new EngineeringPort(
                        "SERIAL OUT",
                        outputSide(state),
                        EngineeringDomain.SERIAL_DATA,
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
            BlockPos input = inputPos(pos, state);
            boolean valid = DataBusNetwork.valid(level, input);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    DataBusNetwork.sample(level, input),
                    0.0,
                    255.0,
                    valid ? PortQuality.VALID : PortQuality.NO_SIGNAL
            ));
        }
        boolean valid = InformationRuntime.valid(level, "serial", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                InformationRuntime.value(level, "serial", pos) & 0xFF,
                0.0,
                255.0,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        BlockPos output = outputPos(pos, state);
        int value = DataBusNetwork.sample(level, input);
        boolean valid = DataBusNetwork.valid(level, input);
        InformationRuntime.write(level, "serial", pos, value, 8, valid, valid ? 100 : 0);
        if (level.getBlockState(output).getBlock() instanceof SerialDataLineBlock) {
            SerialNetwork.recompute(level, output);
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
            InformationRuntime.clear(level, "serial", pos);
            BlockPos output = outputPos(pos, state);
            BlockState outputState = level.getBlockState(output);
            if (outputState.getBlock() instanceof SerialDataLineBlock line) {
                serverLevel.scheduleTick(output, line, 1);
            }
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
                        "Serializer framed byte=" + (InformationRuntime.value(level, "serial", pos) & 0xFF)
                                + " @ 8t/word valid=" + InformationRuntime.valid(level, "serial", pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
