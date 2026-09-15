package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.operations.OperationBufferSnapshot;
import dev.redstoneengineering.operations.world.OperationIndustrialBufferState;
import dev.redstoneengineering.ui.IndustrialBufferUi;
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

/**
 * World-visible finite Operations WIP buffer.
 *
 * <p>The block never owns lot mutation logic. It exposes the persistent
 * {@link OperationIndustrialBufferState} through bounded redstone status and a read-only HMI.</p>
 */
public class IndustrialBufferBlock extends Block implements EngineeringPortProvider {
    public static final int DEFAULT_CAPACITY_UNITS = 64;

    public IndustrialBufferBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<IndustrialBufferBlock> codec() {
        return EngineeringSystemsModule.INDUSTRIAL_BUFFER_CODEC.value();
    }

    public static String bufferId(BlockPos pos) {
        return "buffer:" + pos.asLong();
    }

    public static OperationBufferSnapshot snapshot(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        return OperationIndustrialBufferState.snapshot(server, bufferId(pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!(level instanceof ServerLevel server) || oldState.is(state.getBlock())) return;
        if (OperationIndustrialBufferState.snapshot(server, bufferId(pos)) == null) {
            OperationIndustrialBufferState.create(server, bufferId(pos), pos, DEFAULT_CAPACITY_UNITS);
        }
        notifyProjectedOutputs(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            OperationBufferSnapshot snapshot = OperationIndustrialBufferState.snapshot(server, bufferId(pos));
            if (snapshot != null && snapshot.lots().isEmpty()) {
                OperationIndustrialBufferState.remove(server, bufferId(pos));
            }
            // Non-empty state intentionally remains persisted. Replacing this block at the same
            // position reattaches the deterministic buffer id instead of silently deleting WIP.
            notifyProjectedOutputs(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("WIP LEVEL", Direction.UP, EngineeringDomain.REDSTONE,
                        PortKind.MEASUREMENT, PortDirection.OUTPUT, true, "wip"),
                new EngineeringPort("SPACE PERMIT", Direction.SOUTH, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "permit"),
                new EngineeringPort("FULL", Direction.NORTH, EngineeringDomain.REDSTONE,
                        PortKind.SAFETY, PortDirection.OUTPUT, true, "full")
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        OperationBufferSnapshot snapshot = snapshot(level, pos);
        if (snapshot == null) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), 0, PortQuality.NO_SIGNAL));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(), valueForPhysicalSide(snapshot, side), PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && engineeringPort(state, direction.getOpposite()).isPresent();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction queryDirection) {
        if (!(level instanceof Level world)) return 0;
        Direction physicalSide = queryDirection.getOpposite();
        if (engineeringPort(state, physicalSide).isEmpty()) return 0;
        OperationBufferSnapshot snapshot = snapshot(world, pos);
        return snapshot == null ? 0 : valueForPhysicalSide(snapshot, physicalSide);
    }

    private static int valueForPhysicalSide(OperationBufferSnapshot snapshot, Direction side) {
        return switch (side) {
            case UP -> wipSignal(snapshot);
            case SOUTH -> snapshot.availableUnits() > 0 ? 15 : 0;
            case NORTH -> snapshot.availableUnits() == 0 ? 15 : 0;
            default -> 0;
        };
    }

    public static int wipSignal(OperationBufferSnapshot snapshot) {
        if (snapshot == null || snapshot.usedUnits() <= 0) return 0;
        int scaled = (int) Math.round(snapshot.usedUnits() * 15.0 / snapshot.capacityUnits());
        return Math.max(1, Math.min(15, scaled));
    }

    private static void notifyProjectedOutputs(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        level.updateNeighborsAt(pos.above(), level.getBlockState(pos).getBlock());
        level.updateNeighborsAt(pos.north(), level.getBlockState(pos).getBlock());
        level.updateNeighborsAt(pos.south(), level.getBlockState(pos).getBlock());
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            IndustrialBufferUi.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
