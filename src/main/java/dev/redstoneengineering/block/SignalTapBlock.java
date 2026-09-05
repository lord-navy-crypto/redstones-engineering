package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class SignalTapBlock extends DirectionalSignalBlock {
    public SignalTapBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SignalTapBlock> codec() {
        return RedstoneEngineering.SIGNAL_TAP_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(
                new EngineeringPort("SIGNAL IN", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("THROUGH OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.REDSTONE_ANALOG, PortDirection.OUTPUT, true, "signal"),
                new EngineeringPort("NON-INVASIVE TAP", leftOf(facing), EngineeringDomain.REDSTONE,
                        PortKind.TAP, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        int value = side == inputSide(state) ? readInputFrom(level, pos, side) : state.getValue(OUTPUT);
        return Optional.of(EngineeringPortSnapshot.redstone(descriptor.get(), value, PortQuality.VALID));
    }

    @Override
    protected boolean isEngineeringPort(BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        return side == inputSide(state)
                || side == outputSide(state)
                || side == leftOf(facing);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        Direction facing = state.getValue(FACING);
        if (direction == outputSide(state).getOpposite()
                || direction == leftOf(facing).getOpposite()) {
            return state.getValue(OUTPUT);
        }
        return 0;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateOutput(level, pos, state, readBackInput(level, pos, state));
        level.updateNeighborsAt(pos.relative(leftOf(state.getValue(FACING))), this);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(
                    Component.literal(
                            "Signal Tap | IN=" + inputSide(state).getName()
                                    + " | THROUGH=" + outputSide(state).getName()
                                    + " | TAP=" + leftOf(state.getValue(FACING)).getName()
                                    + " | value=" + state.getValue(OUTPUT) + "/15"
                    ),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
