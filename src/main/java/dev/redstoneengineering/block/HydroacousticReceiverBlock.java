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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Hydroacoustic BACK input -> isolated vanilla redstone FRONT output. */
public class HydroacousticReceiverBlock extends PassiveDirectionalSignalBlock {
    public HydroacousticReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<HydroacousticReceiverBlock> codec() {
        return RedstoneEngineering.HYDROACOUSTIC_RECEIVER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("PRESSURE WAVE IN", inputSide(state), EngineeringDomain.HYDROACOUSTIC,
                        PortKind.SENSOR, PortDirection.INPUT, false, "amplitude"),
                new EngineeringPort("REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal")
        );
    }

    private static PortQuality packetQuality(InformationRuntime.Snapshot packet) {
        return packet.valid() && packet.qualityPercent() > 0 && packet.value() > 0
                ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "hydro", pos);
        PortQuality quality = packetQuality(packet);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(), Math.max(0, Math.min(15, packet.value())), 0.0, 15.0, quality));
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
        InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "hydro", pos);
        return packetQuality(packet) == PortQuality.VALID
                ? Math.min(15, packet.value()) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 4);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "hydro", pos);
        if (packet.valid() && packet.qualityPercent() > 0 && packet.value() > 0) {
            int next = Math.max(0, packet.value() - 1);
            if (next == 0) {
                InformationRuntime.clear(level, "hydro", pos);
            } else {
                InformationRuntime.write(level, "hydro", pos, next,
                        packet.selector(), true,
                        Math.max(0, packet.qualityPercent() - 5));
            }
        } else if (packet.ageTicks() >= 0) {
            InformationRuntime.clear(level, "hydro", pos);
        }
        updateOutput(level, pos, state, outputValue(level, pos, state));
        level.scheduleTick(pos, this, 4);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "hydro", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                InformationRuntime.Snapshot packet = InformationRuntime.snapshot(level, "hydro", pos);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Hydroacoustic A=" + packet.value()
                                + " f=" + packet.selector()), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
