package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RadioKernel;
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
 * Radio receiver: UP is the wireless antenna input and FRONT is the isolated
 * vanilla redstone payload output. Link diagnostics remain transient runtime data.
 */
public class RadioReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    private static final String DIAG_KEY = "radio_rx_diag";
    private static final int DIAG_SIZE = 10;

    public RadioReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHANNEL, 0));
    }

    @Override
    public MapCodec<RadioReceiverBlock> codec() {
        return RedstoneEngineering.RADIO_RECEIVER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("RADIO ANTENNA", Direction.UP, EngineeringDomain.RADIO_DATA,
                        PortKind.BUS, PortDirection.INPUT, false, "signal"),
                new EngineeringPort("REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        RadioKernel.Reception reception = RadioKernel.receivePacket(level, pos, state.getValue(CHANNEL));
        PortQuality quality = reception.collision()
                ? PortQuality.TOPOLOGY_ERROR
                : reception.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
        if (side == Direction.UP) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), reception.value(), 0.0, 15.0, quality));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), state.getValue(OUTPUT), quality));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return RadioKernel.receivePacket(level, pos, state.getValue(CHANNEL)).value();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 5);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var rx = RadioKernel.receivePacket(level, pos, state.getValue(CHANNEL));
        int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);

        int linkQuality = rx.quality();
        int noiseStrength = Math.min(100, rx.interference() * 8 + rx.obstacles() * 2);
        int droppedThisTick = diagnostics[6] != 0 && !rx.valid() ? 1 : 0;

        diagnostics[0]++;
        if (rx.valid()) diagnostics[1]++;
        else if (rx.collision()) diagnostics[3]++;
        else diagnostics[2]++;
        diagnostics[4] += droppedThisTick;
        diagnostics[6] = rx.valid() ? 1 : 0;
        diagnostics[7] = linkQuality;
        diagnostics[8] = noiseStrength;
        diagnostics[9] = state.getValue(CHANNEL);

        updateOutput(level, pos, state, rx.value());
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) RuntimeIntStore.remove(level, DIAG_KEY, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int oldChannel = state.getValue(CHANNEL);
                int channel = (oldChannel + 1) % 4;
                BlockState next = state.setValue(CHANNEL, channel);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                int[] diagnostics = RuntimeIntStore.get(level, DIAG_KEY, pos, DIAG_SIZE);
                if (channel != oldChannel) diagnostics[5]++;
                diagnostics[9] = channel;
                var rx = RadioKernel.receivePacket(level, pos, channel);
                updateOutput(level, pos, next, rx.value());
                player.displayClientMessage(Component.literal(
                        "Radio RX channel=" + channel
                                + " payload=" + rx.value() + "/15"
                                + " quality=" + rx.quality() + "%"
                                + " drivers=" + rx.drivers()
                                + " latency≈" + rx.latencyTicks() + "t"
                                + " | samples=" + diagnostics[0]
                                + " valid=" + diagnostics[1]
                                + " undecodable=" + diagnostics[2]
                                + " collisions=" + diagnostics[3]
                                + " dropouts=" + diagnostics[4]
                                + " handoffs=" + diagnostics[5]
                                + (rx.collision() ? " | COLLISION" : rx.valid() ? " | VALID" : " | UNDECODABLE")), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
