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
import dev.redstoneengineering.instrument.InstrumentNetwork;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
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
 * The single player-facing RSE Junction Point.
 *
 * <p>The historical registry id remains {@code redstone_cable_junction} for save compatibility,
 * but the device is medium-neutral. It provides exactly one vertical UP/DOWN transition for one
 * physical medium at a time. It never translates or converts a signal. A different medium on the
 * opposite side produces {@link SignalMedium#MISMATCH} and the junction fails closed.</p>
 */
public class RedstoneCableJunctionBlock extends ConnectedCableBlock implements EngineeringPortProvider {
    public static final EnumProperty<SignalMedium> MEDIUM = EnumProperty.create("medium", SignalMedium.class);
    private static final String KEY = "redstone_junction";
    private static final int VALUE = 0;
    private static final int AUX = 1;
    private static final int VALID = 2;
    private static final int QUALITY = 3;
    private static final int RUNTIME_SIZE = 4;

    public RedstoneCableJunctionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MEDIUM, SignalMedium.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MEDIUM);
    }

    @Override protected int maxConnections() { return 2; }
    @Override public MapCodec<RedstoneCableJunctionBlock> codec() { return RedstoneEngineering.REDSTONE_CABLE_JUNCTION_CODEC.value(); }

    @Override
    protected boolean canConnectTo(BlockGetter level, BlockPos pos, Direction direction, BlockState neighbor) {
        if (direction.getAxis() != Direction.Axis.Y) return false;
        SignalMedium medium = TransmissionTopology.inferJunctionMedium(level, pos);
        return TransmissionTopology.junctionNeighborMatches(level, pos, direction, neighbor, medium);
    }

    private BlockState refreshMedium(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return state;
        SignalMedium inferred = TransmissionTopology.inferJunctionMedium(level, pos);
        if (state.getValue(MEDIUM) != inferred) {
            RuntimeIntStore.remove(level, KEY, pos);
            state = state.setValue(MEDIUM, inferred);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        refreshConnections(level, pos, state);
        // MEDIUM is an internal topology epoch. Re-resolve both adjacent cable arms after
        // NONE -> medium, medium -> MISMATCH, or medium swaps even when the state update itself
        // intentionally uses UPDATE_CLIENTS and therefore emits no vanilla neighbor notification.
        refreshAdjacentCableConnections(level, pos);
        return level.getBlockState(pos);
    }

    private static EngineeringDomain domain(SignalMedium medium) {
        return switch (medium) {
            case REDSTONE -> EngineeringDomain.REDSTONE;
            case INSTRUMENT -> EngineeringDomain.INSTRUMENT_BUS;
            case DATA_BUS_8 -> EngineeringDomain.DATA_BUS_8;
            case SERIAL -> EngineeringDomain.SERIAL_DATA;
            case DIFFERENTIAL -> EngineeringDomain.DIFFERENTIAL_DATA;
            case OPTICAL -> EngineeringDomain.OPTICAL;
            case COPPER -> EngineeringDomain.COPPER;
            case NONE, MISMATCH -> null;
        };
    }

    private static String payload(SignalMedium medium) {
        return switch (medium) {
            case REDSTONE -> "signal";
            case INSTRUMENT -> "channel";
            case DATA_BUS_8, SERIAL -> "byte";
            case DIFFERENTIAL -> "bit";
            case OPTICAL -> "intensity/channel";
            case COPPER -> "V-eq";
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
            } else if (neighbor.getBlock() instanceof OpticalFiberBlock) {
                DomainNetwork.recomputeOptical(server, neighborPos);
            } else if (neighbor.getBlock() instanceof CopperWireBlock) {
                DomainNetwork.recomputeCopper(server, neighborPos);
            }
        }
    }

    private static void setCarrier(Level level, BlockPos pos, int value, int aux, boolean valid, PortQuality quality) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[VALUE] = valid ? Math.max(0, value) : 0;
        runtime[AUX] = valid ? Math.max(0, aux) : 0;
        runtime[VALID] = valid ? 1 : 0;
        runtime[QUALITY] = quality.ordinal();
    }

    public static void setPower(Level level, BlockPos pos, int power) {
        setCarrier(level, pos, Math.max(0, Math.min(15, power)), 0, true, PortQuality.VALID);
    }

    public static void setOptical(Level level, BlockPos pos, int intensity, int channel, boolean valid) {
        setCarrier(level, pos,
                Math.max(0, Math.min(15, intensity)),
                Math.max(0, Math.min(15, channel)),
                valid,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL);
    }

    public static void setCopper(Level level, BlockPos pos, int voltage, boolean valid) {
        setCarrier(level, pos,
                Math.max(0, Math.min(15, voltage)), 0, valid,
                valid ? PortQuality.VALID : PortQuality.NO_SIGNAL);
    }

    public static int power(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= VALUE ? 0 : runtime[VALUE];
    }

    public static int auxiliary(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= AUX ? 0 : runtime[AUX];
    }

    public static boolean carrierValid(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length > VALID && runtime[VALID] != 0;
    }

    public static PortQuality carrierQuality(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length <= QUALITY) return PortQuality.NO_SIGNAL;
        PortQuality[] values = PortQuality.values();
        int ordinal = runtime[QUALITY];
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : PortQuality.NO_SIGNAL;
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        SignalMedium medium = state.getValue(MEDIUM);
        EngineeringDomain domain = domain(medium);
        if (domain == null) return List.of();
        List<EngineeringPort> ports = new ArrayList<>();
        PortKind kind = medium == SignalMedium.REDSTONE ? PortKind.REDSTONE_ANALOG : PortKind.BUS;
        for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
            if (!connected(state, direction)) continue;
            ports.add(new EngineeringPort(
                    "JUNCTION " + direction.getName().toUpperCase(), direction,
                    domain, kind, PortDirection.BIDIRECTIONAL, false, payload(medium)));
        }
        return List.copyOf(ports);
    }

    private static EngineeringPortSnapshot informationSnapshot(
            EngineeringPort port,
            InformationRuntime.Snapshot sample,
            double maximum,
            PortQuality quality
    ) {
        return new EngineeringPortSnapshot(port, sample.value(), 0.0, maximum, quality);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        return switch (state.getValue(MEDIUM)) {
            case REDSTONE -> Optional.of(EngineeringPortSnapshot.redstone(
                    port.get(), power(level, pos), RedstoneCableNetwork.sourceEvidence(level, pos).quality()));
            case INSTRUMENT -> {
                InstrumentNetwork.ProbeSnapshot bus = InstrumentNetwork.scan(level, pos);
                yield Optional.of(new EngineeringPortSnapshot(
                        port.get(), bus.validChannelsInMask(0xF), 0.0, 4.0, bus.qualityForMask(0xF)));
            }
            case DATA_BUS_8 -> Optional.of(informationSnapshot(
                    port.get(), InformationRuntime.snapshot(level, "bus8", pos), 255.0,
                    DataBusNetwork.quality(level, pos)));
            case SERIAL -> Optional.of(informationSnapshot(
                    port.get(), InformationRuntime.snapshot(level, "serial", pos), 255.0,
                    SerialNetwork.quality(level, pos)));
            case DIFFERENTIAL -> Optional.of(informationSnapshot(
                    port.get(), InformationRuntime.snapshot(level, "diff", pos), 1.0,
                    DifferentialNetwork.quality(level, pos)));
            case OPTICAL -> Optional.of(new EngineeringPortSnapshot(
                    port.get(), power(level, pos), 0.0, 15.0, carrierQuality(level, pos)));
            case COPPER -> Optional.of(new EngineeringPortSnapshot(
                    port.get(), power(level, pos), 0.0, 15.0, carrierQuality(level, pos)));
            case NONE, MISMATCH -> Optional.empty();
        };
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
        if (removed) {
            RuntimeIntStore.remove(level, KEY, pos);
            RedstoneCableNetwork.removeEvidence(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
        if (removed && level instanceof ServerLevel server) {
            RedstoneCableNetwork.recomputeAround(server, pos);
            DomainNetwork.recomputeOpticalAround(server, pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                BlockState neighbor = level.getBlockState(neighborPos);
                if (neighbor.getBlock() instanceof EightBitDataBusBlock bus) server.scheduleTick(neighborPos, bus, 1);
                else if (neighbor.getBlock() instanceof SerialDataLineBlock line) server.scheduleTick(neighborPos, line, 1);
                else if (neighbor.getBlock() instanceof DifferentialDataPairBlock pair) server.scheduleTick(neighborPos, pair, 1);
                else if (neighbor.getBlock() instanceof CopperWireBlock) DomainNetwork.recomputeCopper(server, neighborPos);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                SignalMedium medium = state.getValue(MEDIUM);
                String status = medium == SignalMedium.MISMATCH
                        ? "MISMATCH — different media blocked"
                        : medium == SignalMedium.NONE ? "NO MEDIUM" : "medium=" + medium.getSerializedName();
                status += " | UP=" + (connected(state, Direction.UP) ? "LINK" : "OPEN")
                        + " DOWN=" + (connected(state, Direction.DOWN) ? "LINK" : "OPEN")
                        + " | ROUTING ONLY — NO CONVERSION";
                player.displayClientMessage(Component.literal("Junction Point | " + status), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
