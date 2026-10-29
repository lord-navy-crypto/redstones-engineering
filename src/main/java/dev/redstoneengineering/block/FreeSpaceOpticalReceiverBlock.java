package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
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

/** Aligned, channel-selective optical BACK input -> FRONT redstone output. */
public class FreeSpaceOpticalReceiverBlock extends PassiveDirectionalSignalBlock {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 3);

    public FreeSpaceOpticalReceiverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHANNEL, 0));
    }

    @Override
    public MapCodec<FreeSpaceOpticalReceiverBlock> codec() {
        return RedstoneEngineering.FREE_SPACE_OPTICAL_RECEIVER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("FREE-SPACE OPTICAL IN", inputSide(state), EngineeringDomain.OPTICAL,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "power"),
                new EngineeringPort("REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal")
        );
    }

    private static PortQuality opticalQuality(
            InformationRuntime.Snapshot packet, BlockState state
    ) {
        return FreeSpaceOpticalChannelSupport.receiveQuality(packet, state.getValue(CHANNEL));
    }

    private static int opticalValue(InformationRuntime.Snapshot packet, BlockState state) {
        return opticalQuality(packet, state) == PortQuality.VALID
                ? Math.max(0, Math.min(15, packet.value())) : 0;
    }

    /**
     * Production fail-closed path used when the optical propagator has positive evidence that
     * line-of-sight coverage became incomplete before this retained endpoint could be revalidated.
     */
    public void invalidateCoverage(ServerLevel level, BlockPos pos, BlockState state, int channel) {
        InformationRuntime.write(level, "free_optical", pos, 0, channel, false, 1);
        updateOutput(level, pos, state, 0);
    }

    /** Changing RX channel discards the previous channel's packet immediately.
     * Switching back cannot resurrect the old carrier. */
    public static boolean setChannel(Level level, BlockPos pos, int channel) {
        if (level.isClientSide || !FreeSpaceOpticalChannelSupport.supportedChannel(channel)) return false;
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof FreeSpaceOpticalReceiverBlock receiver)) return false;
        if (current.getValue(CHANNEL) == channel) return true;
        InformationRuntime.clear(level, "free_optical", pos);
        BlockState updated = current.setValue(CHANNEL, channel);
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        receiver.updateOutput(level, pos, updated, 0);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, receiver, 1);
        return true;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "free_optical", pos);
        PortQuality quality = opticalQuality(packet, state);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), quality == PortQuality.VALID
                            ? FreeSpaceOpticalChannelSupport.boundedPower(packet.value()) : 0,
                    0.0, 15.0, quality));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), quality == PortQuality.VALID ? state.getValue(OUTPUT) : 0, quality));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return opticalValue(InformationRuntime.snapshot(level, "free_optical", pos), state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "free_optical", pos);
        // Only actual line-of-sight emissions renew the packet timestamp.
        // A receiver's own maintenance tick must never make an old packet fresh.
        if (packet.ageTicks() > FreeSpaceOpticalChannelSupport.RETAIN_STALE_TICKS)
            InformationRuntime.clear(level, "free_optical", pos);
        updateOutput(level, pos, state, computeOutput(level, pos, state));
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "free_optical", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int channel = FreeSpaceOpticalChannelSupport.nextChannel(state.getValue(CHANNEL));
                setChannel(level, pos, channel);
                InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "free_optical", pos);
                player.displayClientMessage(Component.literal(
                        "Free-space optical RX channel=" + channel
                                + " power=" + packet.value()
                                + " quality=" + packet.qualityPercent()
                                + "% | fresh=8t timeout; alignment required"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
