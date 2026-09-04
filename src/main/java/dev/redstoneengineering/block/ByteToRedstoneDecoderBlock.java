package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Byte-to-redstone bridge: saturates 0..255 into the vanilla 0..15 output range. */
public class ByteToRedstoneDecoderBlock extends PassiveDirectionalSignalBlock {
    public ByteToRedstoneDecoderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ByteToRedstoneDecoderBlock> codec() {
        return RedstoneEngineering.BYTE_TO_REDSTONE_DECODER_CODEC.value();
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
                        "REDSTONE OUT",
                        outputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
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
        BlockPos input = inputPos(pos, state);
        boolean saturated = DataBusNetwork.valid(level, input) && DataBusNetwork.sample(level, input) > 15;
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(),
                state.getValue(OUTPUT),
                saturated ? PortQuality.SATURATED : PortQuality.VALID
        ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        BlockPos input = inputPos(pos, state);
        if (!DataBusNetwork.valid(level, input)) return 0;
        return Math.min(15, DataBusNetwork.sample(level, input));
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
                        "Byte decoder output = " + outputValue(level, pos, state) + "/15"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
