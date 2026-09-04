package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortCompatibility;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lightweight Engineering UI for field devices that need inspection and a few bounded controls,
 * but do not justify a dedicated heavy five-tab instrument implementation.
 */
public final class FieldDeviceMenu extends EngineeringDeviceMenu {
    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_PROBE = 1;
    public static final int KIND_FILTER = 2;
    public static final int KIND_REFERENCE = 3;
    public static final int KIND_TERMINAL = 4;
    public static final int KIND_REDSTONE_CABLE = 5;
    public static final int KIND_REDSTONE_JUNCTION = 6;
    public static final int KIND_INSTRUMENT_CABLE = 7;
    public static final int KIND_DATA_BUS_8 = 8;
    public static final int KIND_ENCODER = 9;
    public static final int KIND_DECODER = 10;
    public static final int KIND_SERIAL_LINE = 11;
    public static final int KIND_SERIALIZER = 12;
    public static final int KIND_DESERIALIZER = 13;
    public static final int KIND_DIFFERENTIAL_PAIR = 14;
    public static final int KIND_DIGITAL_REGENERATOR = 15;

    public static final int BUTTON_PRIMARY_DECREASE = 0;
    public static final int BUTTON_PRIMARY_INCREASE = 1;
    public static final int BUTTON_TOGGLE = 2;
    public static final int BUTTON_PRESET_0 = 3;
    public static final int BUTTON_PRESET_5 = 4;
    public static final int BUTTON_PRESET_10 = 5;
    public static final int BUTTON_PRESET_15 = 6;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot portCount = trackedInt();
    private final DataSlot connectionCount = trackedInt();
    private final DataSlot connectionMask = trackedInt();
    private final DataSlot topologyValid = trackedInt();
    private final DataSlot dataValid = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot driverCount = trackedInt();

    public FieldDeviceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public FieldDeviceMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.FIELD_DEVICE.get(),
                containerId,
                inventory,
                pos,
                inventory.player.level().getBlockState(pos).getBlock()
        );
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();

        int resolvedKind = kindOf(block);
        kind.set(resolvedKind);
        primary.set(0);
        secondary.set(0);
        tertiary.set(0);
        facing.set(-1);
        connectionCount.set(0);
        connectionMask.set(0);
        topologyValid.set(1);
        dataValid.set(1);
        quality.set(100);
        driverCount.set(0);

        if (block instanceof EngineeringPortProvider provider) {
            portCount.set(provider.engineeringPorts(state).size());
        } else {
            portCount.set(0);
        }

        if (block instanceof SignalProbeBlock probe) {
            primary.set(probe.sample(level, blockPos, state));
            secondary.set(state.getValue(SignalProbeBlock.CHANNEL));
            facing.set(state.getValue(SignalProbeBlock.FACING).ordinal());
        } else if (block instanceof PrecisionFilterBlock filter) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            Direction input = output.getOpposite();
            primary.set(providerValue(filter, state, input));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set(state.getValue(PrecisionFilterBlock.RATE));
            facing.set(output.ordinal());
        } else if (block instanceof RedstoneReferenceSourceBlock) {
            primary.set(state.getValue(RedstoneReferenceSourceBlock.POWER));
            facing.set(state.getValue(DirectionalRedstoneEndpointBlock.FACING).ordinal());
        } else if (block instanceof RedstoneCableTerminalBlock terminal) {
            primary.set(state.getValue(RedstoneCableTerminalBlock.POWER));
            secondary.set(terminal.externalInput(level, blockPos, state));
            tertiary.set(state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE) ? 1 : 0);
            facing.set(state.getValue(RedstoneCableTerminalBlock.FACING).ordinal());
        } else if (block instanceof RedstoneSignalCableBlock cable) {
            primary.set(RedstoneSignalCableBlock.power(level, blockPos));
            fillCableTopology(state, cable);
        } else if (block instanceof RedstoneCableJunctionBlock junction) {
            primary.set(RedstoneCableJunctionBlock.power(level, blockPos));
            fillCableTopology(state, junction);
        } else if (block instanceof InstrumentCableBlock cable) {
            fillCableTopology(state, cable);
        } else if (block instanceof EightBitDataBusBlock bus) {
            primary.set(DataBusNetwork.sample(level, blockPos));
            DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(level, blockPos);
            dataValid.set(DataBusNetwork.valid(level, blockPos) ? 1 : 0);
            quality.set(DataBusNetwork.valid(level, blockPos) ? 100 : 0);
            driverCount.set(diagnostics.driverCount());
            fillCompatibleTopology(state, bus);
        } else if (block instanceof RedstoneByteEncoderBlock encoder) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(encoder, state, output.getOpposite()));
            secondary.set(InformationRuntime.value(level, "bus8_out", blockPos) & 0xFF);
            dataValid.set(InformationRuntime.valid(level, "bus8_out", blockPos) ? 1 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof ByteToRedstoneDecoderBlock decoder) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(decoder, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(DataBusNetwork.valid(level, blockPos.relative(output.getOpposite())) ? 1 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof SerialDataLineBlock line) {
            primary.set(InformationRuntime.value(level, "serial", blockPos) & 0xFF);
            secondary.set(Math.max(1, InformationRuntime.aux(level, "serial", blockPos)));
            dataValid.set(InformationRuntime.valid(level, "serial", blockPos) ? 1 : 0);
            quality.set(Math.max(0, Math.min(100, InformationRuntime.quality(level, "serial", blockPos))));
            fillCompatibleTopology(state, line);
        } else if (block instanceof SerializerBlock serializer) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(serializer, state, output.getOpposite()));
            secondary.set(InformationRuntime.value(level, "serial", blockPos) & 0xFF);
            tertiary.set(Math.max(1, InformationRuntime.aux(level, "serial", blockPos)));
            dataValid.set(InformationRuntime.valid(level, "serial", blockPos) ? 1 : 0);
            quality.set(Math.max(0, Math.min(100, InformationRuntime.quality(level, "serial", blockPos))));
            facing.set(output.ordinal());
        } else if (block instanceof DeserializerBlock deserializer) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            BlockPos inputPos = blockPos.relative(output.getOpposite());
            primary.set(InformationRuntime.value(level, "serial", inputPos) & 0xFF);
            secondary.set(InformationRuntime.value(level, "bus8_out", blockPos) & 0xFF);
            tertiary.set(Math.max(1, InformationRuntime.aux(level, "serial", inputPos)));
            dataValid.set(InformationRuntime.valid(level, "bus8_out", blockPos) ? 1 : 0);
            quality.set(Math.max(0, Math.min(100, InformationRuntime.quality(level, "serial", inputPos))));
            facing.set(output.ordinal());
        } else if (block instanceof DifferentialDataPairBlock pair) {
            primary.set(InformationRuntime.value(level, "diff", blockPos) & 1);
            dataValid.set(InformationRuntime.valid(level, "diff", blockPos) ? 1 : 0);
            quality.set(Math.max(0, Math.min(100, InformationRuntime.quality(level, "diff", blockPos))));
            fillCompatibleTopology(state, pair);
        } else if (block instanceof DigitalRegeneratorBlock regenerator) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            BlockPos inputPos = blockPos.relative(output.getOpposite());
            primary.set(Math.max(0, Math.min(100, InformationRuntime.quality(level, "serial", inputPos))));
            secondary.set(InformationRuntime.value(level, "serial", blockPos) & 0xFF);
            tertiary.set(state.getValue(DigitalRegeneratorBlock.THRESHOLD));
            dataValid.set(InformationRuntime.valid(level, "serial", blockPos) ? 1 : 0);
            quality.set(Math.max(0, Math.min(100, InformationRuntime.quality(level, "serial", blockPos))));
            facing.set(output.ordinal());
        }
    }

    private int providerValue(EngineeringPortProvider provider, BlockState state, Direction side) {
        return provider.engineeringSnapshot(level, blockPos, state, side)
                .map(snapshot -> (int) Math.round(snapshot.value()))
                .orElse(0);
    }

    private void fillCableTopology(BlockState state, ConnectedCableBlock cable) {
        connectionCount.set(ConnectedCableBlock.connectionCount(state));
        topologyValid.set(cable.topologyValid(state) ? 1 : 0);
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (ConnectedCableBlock.connected(state, direction)) mask |= 1 << direction.ordinal();
        }
        connectionMask.set(mask);
    }

    private void fillCompatibleTopology(BlockState state, EngineeringPortProvider provider) {
        int mask = 0;
        int issues = 0;
        for (Direction direction : Direction.values()) {
            var localPort = provider.engineeringPort(state, direction);
            if (localPort.isEmpty()) continue;
            BlockState neighborState = level.getBlockState(blockPos.relative(direction));
            if (!(neighborState.getBlock() instanceof EngineeringPortProvider neighborProvider)) continue;
            var neighborPort = neighborProvider.engineeringPort(neighborState, direction.getOpposite());
            if (neighborPort.isEmpty()) continue;
            PortCompatibility.Result compatibility = PortCompatibility.evaluate(localPort.get(), neighborPort.get());
            if (compatibility.compatible()) mask |= 1 << direction.ordinal();
            else issues++;
        }
        connectionMask.set(mask);
        connectionCount.set(Integer.bitCount(mask));
        topologyValid.set(issues == 0 ? 1 : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof SignalProbeBlock) {
            int channel = state.getValue(SignalProbeBlock.CHANNEL);
            if (id == BUTTON_PRIMARY_DECREASE) channel = Math.floorMod(channel - 1, 4);
            else if (id == BUTTON_PRIMARY_INCREASE) channel = (channel + 1) % 4;
            else return false;
            level.setBlock(blockPos, state.setValue(SignalProbeBlock.CHANNEL, channel), Block.UPDATE_CLIENTS);
            changed = true;
        } else if (block instanceof PrecisionFilterBlock filter) {
            int rate = state.getValue(PrecisionFilterBlock.RATE);
            if (id == BUTTON_PRIMARY_DECREASE) rate = rate <= 1 ? 4 : rate - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) rate = rate >= 4 ? 1 : rate + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(PrecisionFilterBlock.RATE, rate), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, filter, 1);
            changed = true;
        } else if (block instanceof RedstoneReferenceSourceBlock source) {
            int value = state.getValue(RedstoneReferenceSourceBlock.POWER);
            if (id == BUTTON_PRIMARY_DECREASE) value = value <= 0 ? 15 : value - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) value = value >= 15 ? 0 : value + 1;
            else if (id == BUTTON_PRESET_0) value = 0;
            else if (id == BUTTON_PRESET_5) value = 5;
            else if (id == BUTTON_PRESET_10) value = 10;
            else if (id == BUTTON_PRESET_15) value = 15;
            else return false;
            BlockState next = state.setValue(RedstoneReferenceSourceBlock.POWER, value);
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            Direction front = next.getValue(DirectionalRedstoneEndpointBlock.FACING);
            level.updateNeighborsAt(blockPos, source);
            level.updateNeighborsAt(blockPos.relative(front), source);
            changed = true;
        } else if (block instanceof RedstoneCableTerminalBlock terminal) {
            if (id != BUTTON_TOGGLE) return false;
            BlockState next = state.setValue(
                    RedstoneCableTerminalBlock.OUTPUT_MODE,
                    !state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE)
            );
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                RedstoneCableNetwork.recompute(server, blockPos);
            }
            level.updateNeighborsAt(blockPos, terminal);
            level.updateNeighborsAt(blockPos.relative(terminal.vanillaSide(next)), terminal);
            changed = true;
        } else if (block instanceof DigitalRegeneratorBlock regenerator) {
            int threshold = state.getValue(DigitalRegeneratorBlock.THRESHOLD);
            if (id == BUTTON_PRIMARY_DECREASE) threshold = Math.floorMod(threshold - 1, 3);
            else if (id == BUTTON_PRIMARY_INCREASE) threshold = (threshold + 1) % 3;
            else return false;
            level.setBlock(blockPos, state.setValue(DigitalRegeneratorBlock.THRESHOLD, threshold), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, regenerator, 1);
            changed = true;
        }

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private static int kindOf(Block block) {
        if (block instanceof SignalProbeBlock) return KIND_PROBE;
        if (block instanceof PrecisionFilterBlock) return KIND_FILTER;
        if (block instanceof RedstoneReferenceSourceBlock) return KIND_REFERENCE;
        if (block instanceof RedstoneCableTerminalBlock) return KIND_TERMINAL;
        if (block instanceof RedstoneSignalCableBlock) return KIND_REDSTONE_CABLE;
        if (block instanceof RedstoneCableJunctionBlock) return KIND_REDSTONE_JUNCTION;
        if (block instanceof InstrumentCableBlock) return KIND_INSTRUMENT_CABLE;
        if (block instanceof EightBitDataBusBlock) return KIND_DATA_BUS_8;
        if (block instanceof RedstoneByteEncoderBlock) return KIND_ENCODER;
        if (block instanceof ByteToRedstoneDecoderBlock) return KIND_DECODER;
        if (block instanceof SerialDataLineBlock) return KIND_SERIAL_LINE;
        if (block instanceof SerializerBlock) return KIND_SERIALIZER;
        if (block instanceof DeserializerBlock) return KIND_DESERIALIZER;
        if (block instanceof DifferentialDataPairBlock) return KIND_DIFFERENTIAL_PAIR;
        if (block instanceof DigitalRegeneratorBlock) return KIND_DIGITAL_REGENERATOR;
        return KIND_UNKNOWN;
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int facingOrdinal() { return facing.get(); }
    public int portCount() { return portCount.get(); }
    public int connectionCount() { return connectionCount.get(); }
    public int connectionMask() { return connectionMask.get(); }
    public boolean topologyValid() { return topologyValid.get() != 0; }
    public boolean dataValid() { return dataValid.get() != 0; }
    public int qualityPercent() { return quality.get(); }
    public int driverCount() { return driverCount.get(); }
}
