package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalRedstoneEndpointBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneCableTerminalBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
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
 * Generic server-authoritative HMI snapshot for any RSE block exposing EngineeringPortProvider.
 * Dedicated instruments may keep their richer menus; this menu provides a complete fallback
 * instead of a blank or three-number-only screen.
 */
public final class UniversalFieldDeviceMenu extends EngineeringDeviceMenu {
    public static final int FACE_COUNT = Direction.values().length;
    public static final int BUTTON_ROTATE_LEFT = 100;
    public static final int BUTTON_ROTATE_RIGHT = 101;

    private final DataSlot facing = trackedInt();
    private final DataSlot declaredPortMask = trackedInt();
    private final DataSlot inputMask = trackedInt();
    private final DataSlot outputMask = trackedInt();
    private final DataSlot bidirectionalMask = trackedInt();
    private final DataSlot seriesRotatable = trackedInt();
    private final DataSlot[] domains = trackedInts(FACE_COUNT);
    private final DataSlot[] kinds = trackedInts(FACE_COUNT);
    private final DataSlot[] values = trackedInts(FACE_COUNT);
    private final DataSlot[] minimums = trackedInts(FACE_COUNT);
    private final DataSlot[] maximums = trackedInts(FACE_COUNT);
    private final DataSlot[] qualities = trackedInts(FACE_COUNT);

    public UniversalFieldDeviceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public UniversalFieldDeviceMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.UNIVERSAL_FIELD_DEVICE.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        facing.set(directionOrdinal(state));
        seriesRotatable.set(isRotatable(block) ? 1 : 0);
        declaredPortMask.set(0);
        inputMask.set(0);
        outputMask.set(0);
        bidirectionalMask.set(0);
        for (int i = 0; i < FACE_COUNT; i++) {
            domains[i].set(-1);
            kinds[i].set(-1);
            values[i].set(0);
            minimums[i].set(0);
            maximums[i].set(0);
            qualities[i].set(-1);
        }
        if (!(block instanceof EngineeringPortProvider provider)) return;

        int declared = 0;
        int inputs = 0;
        int outputs = 0;
        int bidirectional = 0;
        for (Direction side : Direction.values()) {
            var descriptor = provider.engineeringPort(state, side);
            if (descriptor.isEmpty()) continue;
            int index = side.ordinal();
            declared |= 1 << index;
            PortDirection direction = descriptor.get().direction();
            if (direction == PortDirection.INPUT) inputs |= 1 << index;
            if (direction == PortDirection.OUTPUT) outputs |= 1 << index;
            if (direction == PortDirection.BIDIRECTIONAL) bidirectional |= 1 << index;
            domains[index].set(descriptor.get().domain().ordinal());
            kinds[index].set(descriptor.get().kind().ordinal());
            var snapshot = provider.engineeringSnapshot(level, blockPos, state, side);
            if (snapshot.isPresent()) {
                values[index].set(syncNumber(snapshot.get().value()));
                minimums[index].set(syncNumber(snapshot.get().minimum()));
                maximums[index].set(syncNumber(snapshot.get().maximum()));
                qualities[index].set(snapshot.get().quality().ordinal());
            } else {
                qualities[index].set(PortQuality.NO_SIGNAL.ordinal());
            }
        }
        declaredPortMask.set(declared);
        inputMask.set(inputs);
        outputMask.set(outputs);
        bidirectionalMask.set(bidirectional);
    }

    private static boolean isRotatable(Block block) {
        return block instanceof DirectionalSignalBlock
                || block instanceof DirectionalDomainBlock
                || block instanceof DirectionalRedstoneEndpointBlock
                || block instanceof SignalProbeBlock
                || block instanceof RedstoneCableTerminalBlock;
    }

    private static int syncNumber(double value) {
        long rounded = Math.round(value);
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
    }

    private static int directionOrdinal(BlockState state) {
        if (state.hasProperty(DirectionalSignalBlock.FACING)) return state.getValue(DirectionalSignalBlock.FACING).ordinal();
        if (state.hasProperty(DirectionalDomainBlock.FACING)) return state.getValue(DirectionalDomainBlock.FACING).ordinal();
        if (state.hasProperty(DirectionalRedstoneEndpointBlock.FACING)) return state.getValue(DirectionalRedstoneEndpointBlock.FACING).ordinal();
        if (state.hasProperty(SignalProbeBlock.FACING)) return state.getValue(SignalProbeBlock.FACING).ordinal();
        if (state.hasProperty(RedstoneCableTerminalBlock.FACING)) return state.getValue(RedstoneCableTerminalBlock.FACING).ordinal();
        return -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = switch (id) {
            case BUTTON_ROTATE_LEFT -> rotate(false);
            case BUTTON_ROTATE_RIGHT -> rotate(true);
            default -> false;
        };
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private boolean rotate(boolean clockwise) {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        if (block instanceof DirectionalSignalBlock) {
            return DirectionalSignalBlock.rotateSeriesAxis(level, blockPos, clockwise);
        }
        if (block instanceof DirectionalDomainBlock) {
            return DirectionalDomainBlock.rotateSeriesAxis(level, blockPos, clockwise);
        }
        if (block instanceof DirectionalRedstoneEndpointBlock) {
            return DirectionalRedstoneEndpointBlock.rotateOutput(level, blockPos, clockwise);
        }
        if (block instanceof SignalProbeBlock) {
            return SignalProbeBlock.rotateMeasurementAxis(level, blockPos, clockwise);
        }
        if (block instanceof RedstoneCableTerminalBlock) {
            return RedstoneCableTerminalBlock.rotateInterface(level, blockPos, clockwise);
        }
        return false;
    }

    public int facingOrdinal() { return facing.get(); }
    public int declaredPortMask() { return declaredPortMask.get(); }
    public boolean hasPort(Direction side) { return (declaredPortMask.get() & (1 << side.ordinal())) != 0; }
    public boolean isInput(Direction side) { return (inputMask.get() & (1 << side.ordinal())) != 0; }
    public boolean isOutput(Direction side) { return (outputMask.get() & (1 << side.ordinal())) != 0; }
    public boolean isBidirectional(Direction side) { return (bidirectionalMask.get() & (1 << side.ordinal())) != 0; }
    public int value(Direction side) { return values[side.ordinal()].get(); }
    public int minimum(Direction side) { return minimums[side.ordinal()].get(); }
    public int maximum(Direction side) { return maximums[side.ordinal()].get(); }

    public EngineeringDomain domain(Direction side) {
        int ordinal = domains[side.ordinal()].get();
        EngineeringDomain[] all = EngineeringDomain.values();
        return ordinal < 0 || ordinal >= all.length ? EngineeringDomain.GENERIC : all[ordinal];
    }

    public PortKind portKind(Direction side) {
        int ordinal = kinds[side.ordinal()].get();
        PortKind[] all = PortKind.values();
        return ordinal < 0 || ordinal >= all.length ? PortKind.AUXILIARY : all[ordinal];
    }

    public PortQuality quality(Direction side) {
        int ordinal = qualities[side.ordinal()].get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public boolean rotatableSeriesAxis() {
        return seriesRotatable.get() != 0;
    }
}
