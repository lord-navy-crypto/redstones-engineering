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
import dev.redstoneengineering.physics.RadioKernel;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * Four-channel low-bandwidth transmitter. UP is the radio antenna face; the other
 * five faces accept a bounded redstone payload and the strongest valid input is transmitted.
 */
public class RadioTransmitterBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);
    private static final Direction[] PAYLOAD_SIDES = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public record PayloadObservation(int value, PortQuality quality) {
        public boolean valid() {
            return quality == PortQuality.VALID || quality == PortQuality.SATURATED;
        }
    }

    public RadioTransmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHANNEL, 0));
    }

    @Override
    public MapCodec<RadioTransmitterBlock> codec() {
        return RedstoneEngineering.RADIO_TRANSMITTER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHANNEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                redstoneInput(Direction.DOWN),
                redstoneInput(Direction.NORTH),
                redstoneInput(Direction.SOUTH),
                redstoneInput(Direction.WEST),
                redstoneInput(Direction.EAST),
                new EngineeringPort("RADIO ANTENNA", Direction.UP, EngineeringDomain.RADIO_DATA,
                        PortKind.BUS, PortDirection.OUTPUT, false, "signal")
        );
    }

    private static EngineeringPort redstoneInput(Direction side) {
        return new EngineeringPort("PAYLOAD IN", side, EngineeringDomain.REDSTONE,
                PortKind.CONVERTER, PortDirection.INPUT, true, "signal");
    }

    public static PayloadObservation payloadObservation(Level level, BlockPos pos) {
        int best = 0;
        boolean hasValidSource = false;
        boolean hasStaleSource = false;
        for (Direction side : PAYLOAD_SIDES) {
            RedstoneObservationSupport.Observation observation =
                    RedstoneObservationSupport.observe(level, pos, side);
            if (observation.valid()) {
                hasValidSource = true;
                best = Math.max(best, observation.value());
            } else if (observation.quality() == PortQuality.STALE) {
                hasStaleSource = true;
            }
        }
        return new PayloadObservation(
                best,
                hasValidSource ? PortQuality.VALID
                        : hasStaleSource ? PortQuality.STALE : PortQuality.NO_SIGNAL
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.UP) {
            PayloadObservation payload = payloadObservation(level, pos);
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), payload.value(), 0.0, 15.0, payload.quality()));
        }
        RedstoneObservationSupport.Observation input = RedstoneObservationSupport.observe(level, pos, side);
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), input.value(), input.quality()));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() != Direction.UP;
    }

    private static boolean isPayloadNeighbor(BlockPos pos, BlockPos neighborPos) {
        for (Direction side : PAYLOAD_SIDES) {
            if (pos.relative(side).equals(neighborPos)) return true;
        }
        return false;
    }

    private static void refreshTransmitter(Level level, BlockPos pos, BlockState state) {
        PayloadObservation payload = payloadObservation(level, pos);
        if (payload.valid()) {
            RadioKernel.updateTransmitter(level, pos, state.getValue(CHANNEL), payload.value());
        } else {
            RadioKernel.removeTransmitter(level, pos);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshTransmitter(level, pos, state);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston
    ) {
        if (isPayloadNeighbor(pos, neighborPos)) {
            refreshTransmitter(level, pos, state);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RadioKernel.removeTransmitter(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
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
                refreshTransmitter(level, pos, next);
                PayloadObservation payload = payloadObservation(level, pos);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Radio TX channel=" + channel + " payload=" + payload.value()
                                + "/15 quality=" + payload.quality()
                                + " range=" + RadioKernel.RANGE), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
