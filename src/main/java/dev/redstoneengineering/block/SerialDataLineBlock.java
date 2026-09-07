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
import dev.redstoneengineering.physics.SerialNetwork;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Framed serial-data cable. Direct line continuity/branching is planar; vertical
 * transitions require the unified Signal Junction Point.
 */
public class SerialDataLineBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public SerialDataLineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SerialDataLineBlock> codec() {
        return RedstoneEngineering.SERIAL_DATA_LINE_CODEC.value();
    }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos self, Direction direction, BlockState neighbor) {
        return TransmissionTopology.serialPort(level, self, direction, neighbor);
    }

    @Override
    protected int maxConnections() {
        return 6;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        List<EngineeringPort> ports = new ArrayList<>();
        for (Direction side : Direction.values()) {
            // Horizontal faces describe connectable cable capability even when open.
            // UP/DOWN are exposed only after a same-medium junction creates a live arm.
            if (side.getAxis() == Direction.Axis.Y && !connected(state, side)) continue;
            ports.add(new EngineeringPort(
                    "SERIAL DATA",
                    side,
                    EngineeringDomain.SERIAL_DATA,
                    PortKind.BUS,
                    PortDirection.BIDIRECTIONAL,
                    false,
                    "byte"
            ));
        }
        return List.copyOf(ports);
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
        InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "serial", pos);
        return Optional.of(new EngineeringPortSnapshot(
                port.get(),
                snapshot.value() & 0xFF,
                0.0,
                255.0,
                SerialNetwork.quality(level, pos)
        ));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SerialNetwork.recompute(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            SerialNetwork.clearNode(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = pos.relative(direction);
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (neighborState.getBlock() instanceof SerialDataLineBlock line) {
                        serverLevel.scheduleTick(neighborPos, line, 1);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
                InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "serial", pos);
                player.displayClientMessage(Component.literal(
                        "Serial: byte=" + (snapshot.value() & 0xFF)
                                + " period=" + Math.max(1, snapshot.selector()) + "t"
                                + " quality=" + snapshot.qualityPercent() + "%"
                                + " state=" + SerialNetwork.quality(level, pos)
                                + " | ports=" + connectionCount(state)
                                + " | routing=PLANAR; vertical via Signal Junction Point"
                                + " | " + SerialNetwork.diagnostics(level, pos)
                ), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
