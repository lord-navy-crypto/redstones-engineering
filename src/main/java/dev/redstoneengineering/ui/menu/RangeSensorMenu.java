package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.RangeSensorBlock;
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
 * Server-authoritative Range Sensor HMI model.
 *
 * <p>The menu deliberately synchronizes the retained scan status separately from distance so a
 * complete CLEAR scan at distance zero remains VALID evidence instead of being collapsed into
 * "missing signal". Configuration buttons mutate bounded BlockState only on the logical server.
 * The client merely sends intent.</p>
 */
public final class RangeSensorMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_MODE_PREVIOUS = 0;
    public static final int BUTTON_MODE_NEXT = 1;
    public static final int BUTTON_RANGE_PREVIOUS = 2;
    public static final int BUTTON_RANGE_NEXT = 3;
    public static final int BUTTON_RESPONSE_PREVIOUS = 4;
    public static final int BUTTON_RESPONSE_NEXT = 5;
    public static final int BUTTON_ROTATE_LEFT = 6;
    public static final int BUTTON_ROTATE_RIGHT = 7;

    private final DataSlot distance = trackedInt();
    private final DataSlot scanStatus = trackedInt();
    private final DataSlot scannedCells = trackedInt();
    private final DataSlot configuredRange = trackedInt();
    private final DataSlot detectMode = trackedInt();
    private final DataSlot rangeMode = trackedInt();
    private final DataSlot responseMode = trackedInt();
    private final DataSlot output = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot evidenceValid = trackedInt();

    public RangeSensorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public RangeSensorMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.RANGE_SENSOR.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof RangeSensorBlock)) {
            distance.set(0);
            scanStatus.set(RangeSensorBlock.ScanStatus.UNINITIALIZED.ordinal());
            scannedCells.set(0);
            configuredRange.set(0);
            detectMode.set(0);
            rangeMode.set(0);
            responseMode.set(0);
            output.set(0);
            facing.set(-1);
            evidenceValid.set(0);
            return;
        }

        RangeSensorBlock.ScanResult scan = RangeSensorBlock.lastScan(level, blockPos, state);
        distance.set(scan.distance());
        scanStatus.set(scan.status().ordinal());
        scannedCells.set(scan.scannedCells());
        configuredRange.set(scan.configuredRange());
        detectMode.set(state.getValue(RangeSensorBlock.MODE));
        rangeMode.set(state.getValue(RangeSensorBlock.RANGE_MODE));
        responseMode.set(state.getValue(RangeSensorBlock.RESPONSE));
        output.set(state.getValue(RangeSensorBlock.OUTPUT));
        facing.set(RangeSensorBlock.sensingSide(state).ordinal());
        evidenceValid.set(scan.complete() ? 1 : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof RangeSensorBlock sensor)) return false;

        BlockState next = state;
        switch (id) {
            case BUTTON_MODE_PREVIOUS -> next = state.setValue(RangeSensorBlock.MODE,
                    Math.floorMod(state.getValue(RangeSensorBlock.MODE) - 1, 3));
            case BUTTON_MODE_NEXT -> next = state.setValue(RangeSensorBlock.MODE,
                    (state.getValue(RangeSensorBlock.MODE) + 1) % 3);
            case BUTTON_RANGE_PREVIOUS -> next = state.setValue(RangeSensorBlock.RANGE_MODE,
                    Math.floorMod(state.getValue(RangeSensorBlock.RANGE_MODE) - 1, 3));
            case BUTTON_RANGE_NEXT -> next = state.setValue(RangeSensorBlock.RANGE_MODE,
                    (state.getValue(RangeSensorBlock.RANGE_MODE) + 1) % 3);
            case BUTTON_RESPONSE_PREVIOUS -> next = state.setValue(RangeSensorBlock.RESPONSE,
                    Math.floorMod(state.getValue(RangeSensorBlock.RESPONSE) - 1, 4));
            case BUTTON_RESPONSE_NEXT -> next = state.setValue(RangeSensorBlock.RESPONSE,
                    (state.getValue(RangeSensorBlock.RESPONSE) + 1) % 4);
            case BUTTON_ROTATE_LEFT -> next = state.setValue(RangeSensorBlock.FACING,
                    state.getValue(RangeSensorBlock.FACING).getCounterClockWise());
            case BUTTON_ROTATE_RIGHT -> next = state.setValue(RangeSensorBlock.FACING,
                    state.getValue(RangeSensorBlock.FACING).getClockWise());
            default -> {
                return false;
            }
        }

        if (next == state) return false;
        level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
        // One bounded notification fans out to all adjacent redstone consumers without per-face storms.
        level.updateNeighborsAt(blockPos, sensor);
        level.scheduleTick(blockPos, sensor, 1);
        refreshAuthoritativeSnapshot();
        broadcastChanges();
        return true;
    }

    public int distance() { return distance.get(); }
    public int scanStatusOrdinal() { return scanStatus.get(); }
    public int scannedCells() { return scannedCells.get(); }
    public int configuredRange() { return configuredRange.get(); }
    public int detectMode() { return detectMode.get(); }
    public int rangeMode() { return rangeMode.get(); }
    public int responseMode() { return responseMode.get(); }
    public int output() { return output.get(); }
    public int facingOrdinal() { return facing.get(); }
    public boolean evidenceValid() { return evidenceValid.get() != 0; }

    public Direction sensingDirection() {
        int ordinal = facing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }

    public Direction outputDirection() {
        return sensingDirection().getOpposite();
    }
}
