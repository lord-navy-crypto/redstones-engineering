package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Ambient-air compressor. DOWN is the redstone pressure command; UP is the pneumatic outlet. */
public class AirCompressorBlock extends Block implements EngineeringPortProvider {
    public AirCompressorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AirCompressorBlock> codec() {
        return RedstoneEngineering.AIR_COMPRESSOR_CODEC.value();
    }

    public static RedstoneObservationSupport.Observation commandObservation(Level level, BlockPos pos) {
        return RedstoneObservationSupport.observe(level, pos, Direction.DOWN);
    }

    public static int commandSignal(Level level, BlockPos pos) {
        RedstoneObservationSupport.Observation observation = commandObservation(level, pos);
        return observation.valid() ? observation.value() : 0;
    }

    public static int commandedPressure(Level level, BlockPos pos) {
        return Math.round(commandSignal(level, pos) * 100f / 15f);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("PRESSURE COMMAND", Direction.DOWN, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "signal"),
                new EngineeringPort("COMPRESSED AIR OUT", Direction.UP, EngineeringDomain.PNEUMATIC,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "pressure")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == Direction.DOWN) {
            RedstoneObservationSupport.Observation command = commandObservation(level, pos);
            return Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), command.value(), command.quality()));
        }
        PneumaticObservationSupport.Observation pressure = PneumaticObservationSupport.observe(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), pressure.pressure(), 0.0, 100.0, pressure.quality()));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == Direction.DOWN;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server && !state.is(oldState.getBlock())) {
            PneumaticNetwork.recompute(server, pos);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor,
            BlockPos neighborPos, boolean moved
    ) {
        if (level instanceof ServerLevel server && neighborPos.equals(pos.below())) {
            PneumaticNetwork.recompute(server, pos);
        }
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
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            FieldDeviceUi.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
