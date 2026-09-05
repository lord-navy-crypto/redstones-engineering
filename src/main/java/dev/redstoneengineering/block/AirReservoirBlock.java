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
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Pressure accumulator. Charges toward line pressure with finite rate and slowly leaks when not replenished. */
public class AirReservoirBlock extends Block implements EngineeringPortProvider {
    public AirReservoirBlock(Properties properties) { super(properties); }

    @Override public MapCodec<AirReservoirBlock> codec() {
        return RedstoneEngineering.AIR_RESERVOIR_CODEC.value();
    }

    private static EngineeringPort port(Direction side) {
        return new EngineeringPort(
                "RESERVOIR " + side.getName().toUpperCase(), side,
                EngineeringDomain.PNEUMATIC, PortKind.AUXILIARY, PortDirection.BIDIRECTIONAL,
                false, "pressure"
        );
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                port(Direction.DOWN), port(Direction.UP),
                port(Direction.NORTH), port(Direction.SOUTH),
                port(Direction.WEST), port(Direction.EAST)
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        int pressure = PneumaticNetwork.pressure(level, pos);
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), pressure, 0.0, 100.0,
                pressure > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL
        ));
    }

    public static int storedPressure(Level level, BlockPos pos) {
        return InformationRuntime.value(level, "air_reservoir", pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server && !state.is(oldState.getBlock())) {
            InformationRuntime.write(server, "air_reservoir", pos, 0, 0, true, 100);
            server.scheduleTick(pos, this, 10);
            PneumaticNetwork.recompute(server, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stored = storedPressure(level, pos);
        int line = InformationRuntime.value(level, "pneumatic", pos);
        int next = stored;
        if (line > stored) next = Math.min(line, stored + 5);
        else if (stored > 0) next = stored - 1;
        InformationRuntime.write(level, "air_reservoir", pos, Math.max(0, Math.min(100, next)), 0, true, 100);
        PneumaticNetwork.recompute(level, pos);
        level.scheduleTick(pos, this, 10);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            InformationRuntime.clear(level, "air_reservoir", pos);
            InformationRuntime.clear(level, "pneumatic", pos);
            PneumaticNetwork.recomputeAround(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int stored = storedPressure(level, pos);
                int line = InformationRuntime.value(level, "pneumatic", pos);
                player.displayClientMessage(Component.literal(
                        "Air reservoir stored=" + stored + "/100 line=" + line + "/100 chargeRate<=5/10t leak=1/10t"
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
