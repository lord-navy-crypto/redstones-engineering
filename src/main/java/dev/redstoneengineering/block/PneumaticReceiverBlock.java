package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Pneumatic BACK input -> isolated vanilla redstone FRONT output. The receiver is a terminal, not a pneumatic bridge. */
public class PneumaticReceiverBlock extends PassiveDirectionalSignalBlock {
    public PneumaticReceiverBlock(Properties properties) { super(properties); }

    @Override public MapCodec<PneumaticReceiverBlock> codec() {
        return RedstoneEngineering.PNEUMATIC_RECEIVER_CODEC.value();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "PNEUMATIC IN", inputSide(state), EngineeringDomain.PNEUMATIC,
                        PortKind.CONVERTER, PortDirection.INPUT, false, "pressure"
                ),
                new EngineeringPort(
                        "REDSTONE OUT", outputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.OUTPUT, true, "signal"
                )
        );
    }

    private static PneumaticObservationSupport.Observation inputObservation(
            Level level, BlockPos pos, BlockState state
    ) {
        Direction input = state.getValue(DirectionalSignalBlock.FACING).getOpposite();
        return PneumaticObservationSupport.observe(level, pos.relative(input));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        PneumaticObservationSupport.Observation pressure = inputObservation(level, pos, state);
        if (side == inputSide(state)) {
            return Optional.of(new EngineeringPortSnapshot(
                    descriptor.get(), pressure.pressure(), 0.0, 100.0, pressure.quality()
            ));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                descriptor.get(), state.getValue(OUTPUT), pressure.quality()
        ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == outputSide(state);
    }

    @Override
    protected int computeOutput(Level level, BlockPos pos, BlockState state) {
        return Math.min(15, (PneumaticNetwork.pressure(level, inputPos(pos, state)) * 15) / 100);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                PneumaticObservationSupport.Observation pressure = inputObservation(level, pos, state);
                player.displayClientMessage(Component.literal(
                        "Pneumatic receiver pressure=" + pressure.pressure()
                                + "/100 quality=" + pressure.quality()
                                + " output=" + outputValue(level, pos, state) + "/15 | BACK=PNEUMATIC FRONT=REDSTONE"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
