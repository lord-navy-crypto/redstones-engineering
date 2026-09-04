package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.InstrumentCableBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.RedstoneSignalCableBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
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
}
