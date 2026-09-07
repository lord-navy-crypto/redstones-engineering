package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.TransmissionTopology.SignalMedium;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.physics.SerialNetwork;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unified same-medium signal junction/riser.
 *
 * <p>The historical registry id remains {@code redstone_cable_junction} for save compatibility,
 * but the device now provides the one explicit vertical-transition point shared by RSE cable-like
 * information media. It auto-resolves from adjacent cable identities. Mixed media produce
 * {@link SignalMedium#MISMATCH}; no network is allowed to cross that state. Domain conversion
 * remains the responsibility of dedicated converters/transducers.</p>
 */
public class RedstoneCableJunctionBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public static final EnumProperty<SignalMedium> MEDIUM = EnumProperty.create("medium", SignalMedium.class);
    private static final String KEY = "redstone_junction";

    public RedstoneCableJunctionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MEDIUM, SignalMedium.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MEDIUM);
    }

    @Override protected int maxConnections() { return 6; }
    @Override public MapCodec<RedstoneCableJunctionBlock> codec() { return RedstoneEngineering.REDSTONE_CABLE_JUNCTION_CODEC.value(); }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        SignalMedium medium = TransmissionTopology.inferJunctionMedium(level, pos);
        return TransmissionTopology.junctionNeighborMatches(level, pos, direction, neighbor, medium);
    }

    private BlockState refreshMedium(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return state;
        SignalMedium inferred = TransmissionTopology.inferJunctionMedium(level, pos);
        if (state.getValue(MEDIUM) != inferred) {
            state = state.setValue(MEDIUM, inferred);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        refreshConnections(level, pos, state);
        return level.getBlockState(pos);
    }

    private static EngineeringDomain domain(SignalMedium medium) {
        return switch (medium) {
            case REDSTONE -> EngineeringDomain.REDSTONE;
            case INSTRUMENT -> EngineeringDomain.INSTRUMENT_BUS;
            case DATA_BUS_8 -> EngineeringDomain.DATA_BUS_8;
            case SERIAL -> EngineeringDomain.SERIAL_DATA;
            case DIFFERENTIAL -> EngineeringDomain.DIFFERENTIAL_DATA;
            case NONE, MISMATCH -> null;
        };
    }

    private static String payload(SignalMedium medium) {
        return switch (medium) {
            case REDSTONE -> "signal";
            case INSTRUMENT -> "channel";
            case DATA_BUS_8, SERIAL -> "byte";
            case DIFFERENTIAL -> "bit";
            case NONE, MISMATCH -> "";
        };
    }

    private void refreshAdjacentNetworks(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.getBlock() instanceof RedstoneSignalCableBlock) {
                RedstoneCableNetwork.recompute(server, neighborPos);
            } else if (neighbor.getBlock() instanceof EightBitDataBusBlock bus) {
                server.scheduleTick(neighborPos, bus, 1);
            } else if (neighbor.getBlock() instanceof SerialDataLineBlock line) {
                server.scheduleTick(neighborPos, line, 1);
            } else if (neighbor.getBlock() instanceof DifferentialDataPairBlock pair) {
                server.scheduleTick(neighborPos, pair, 1);
            }
        }
    }

    public static void setPower(Level level, BlockPos pos, int power) {
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = Math.max(0, Math.min(15, power));
    }

    public static int power(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0];
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        SignalMedium medium = state.getValue(MEDIUM);
        EngineeringDomain domain = domain(medium);
        if (domain == null) return List.of();
        List<EngineeringPort> ports = new ArrayList<>();
        PortKind kind = medium == SignalMedium.REDSTONE ? PortKind.REDSTONE_ANALOG : PortKind.BUS;
        for (Direction direction : Direction.values()) {
            if (!connected(state, direction)) continue;
            ports.add(new EngineeringPort(
                    "SIGNAL JUNCTION " + direction.getName().toUpperCase(), direction,
                    domain, kind, PortDirection.BIDIRECTIONAL, false, payload(medium)));
        }
        return List.copyOf(ports);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (state.getValue(MEDIUM) == SignalMedium.REDSTONE) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), power(level, pos), PortQuality.VALID));
        }
        return Optional.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        refreshMedium(level, pos);
        refreshAdjacentNetworks(level, pos);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean moved
    ) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moved);
        refreshMedium(level, pos);
        refreshAdjacentNetworks(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean removed = !state.is(newState.getBlock());
        if (removed) RuntimeIntStore.remove(level, KEY, pos);
        super.onRemove(state, level, pos, newState, moved);
        if (removed && level instanceof ServerLevel server) {
            RedstoneCableNetwork.recomputeAround(server, pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (neighbor.getBlock() instanceof EightBitDataBusBlock bus) server.scheduleTick(neighborPos, bus, 1);
                else if (neighbor.getBlock() instanceof SerialDataLineBlock line) server.scheduleTick(neighborPos, line, 1);
                else if (neighbor.getBlock() instanceof DifferentialDataPairBlock pair) server.scheduleTick(neighborPos, pair, 1);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                SignalMedium medium = state.getValue(MEDIUM);
                String status = medium == SignalMedium.MISMATCH
                        ? "MISMATCH — mixed media blocked; use a dedicated converter"
                        : medium == SignalMedium.NONE ? "NO MEDIUM" : "medium=" + medium.getSerializedName();
                if (medium == SignalMedium.REDSTONE) status += " signal=" + power(level, pos) + "/15";
                player.displayClientMessage(Component.literal(
                        "Signal Junction Point | " + status + " | ports=" + connectionCount(state)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
