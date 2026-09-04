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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Recovers the most recent framed serial byte and drives a local 8-bit bus segment. */
public class DeserializerBlock extends DirectionalDomainBlock implements EngineeringPortProvider, DataBusDriver {
    public DeserializerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DeserializerBlock> codec() {
        return RedstoneEngineering.DESERIALIZER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "SERIAL IN",
                        inputSide(state),
                        EngineeringDomain.SERIAL_DATA,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        false,
                        "byte"
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
            BlockPos input = inputPos(pos, state);
            boolean valid = InformationRuntime.valid(level, "serial", input);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    InformationRuntime.value(level, "serial", input) & 0xFF,
                    0.0,
                    255.0,
                    valid ? PortQuality.VALID : PortQuality.NO_SIGNAL
            ));
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

    private void update(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        BlockPos output = outputPos(pos, state);
        int value = InformationRuntime.value(level, "serial", input) & 0xFF;
        boolean valid = InformationRuntime.valid(level, "serial", input);
        InformationRuntime.write(level, "bus8_out", pos, value, 0, valid, valid ? 100 : 0);
        if (level.getBlockState(output).getBlock() instanceof EightBitDataBusBlock) {
            DataBusNetwork.resolve(level, DataBusNetwork.collect(level, output));
        }
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
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
                        "Deserializer byte=" + (InformationRuntime.value(level, "bus8_out", pos) & 0xFF)
                                + " valid=" + InformationRuntime.valid(level, "bus8_out", pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
