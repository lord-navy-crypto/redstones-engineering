package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.EdgeDetectorBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.PulseShaperBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Shared server-authoritative HMI model for the redstone series-processing family. */
public final class SignalProcessorMenu extends EngineeringDeviceMenu {
    public static final int KIND_FILTER = 0;
    public static final int KIND_EDGE = 1;
    public static final int KIND_PULSE = 2;

    public static final int BUTTON_PARAMETER_PREVIOUS = 0;
    public static final int BUTTON_PARAMETER_NEXT = 1;
    /** Legacy whole-route actions retained for compatibility. */
    public static final int BUTTON_ROTATE_LEFT = 2;
    public static final int BUTTON_ROTATE_RIGHT = 3;
    public static final int BUTTON_INPUT_LEFT = 4;
    public static final int BUTTON_INPUT_RIGHT = 5;
    public static final int BUTTON_OUTPUT_LEFT = 6;
    public static final int BUTTON_OUTPUT_RIGHT = 7;

    private final DataSlot kind = trackedInt();
    private final DataSlot input = trackedInt();
    private final DataSlot output = trackedInt();
    private final DataSlot parameter = trackedInt();
    private final DataSlot runtimeA = trackedInt();
    private final DataSlot runtimeB = trackedInt();
    private final DataSlot runtimeC = trackedInt();
    private final DataSlot initialized = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();

    public SignalProcessorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public SignalProcessorMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.SIGNAL_PROCESSOR.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        input.set(0);
        output.set(0);
        parameter.set(0);
        runtimeA.set(0);
        runtimeB.set(0);
        runtimeC.set(0);
        initialized.set(1);
        inputFacing.set(-1);
        outputFacing.set(-1);

        if (!(block instanceof DirectionalSignalBlock directional)) {
            kind.set(-1);
            initialized.set(0);
            return;
        }
        Direction in = DirectionalSignalBlock.seriesInputSide(state);
        Direction out = DirectionalSignalBlock.seriesOutputSide(state);
        inputFacing.set(in.ordinal());
        outputFacing.set(out.ordinal());
        if (directional instanceof EngineeringPortProvider provider) {
            input.set(provider.engineeringSnapshot(level, blockPos, state, in)
                    .map(snapshot -> (int) Math.round(snapshot.value())).orElse(0));
        }
        output.set(state.getValue(DirectionalSignalBlock.OUTPUT));

        if (block instanceof PrecisionFilterBlock) {
            kind.set(KIND_FILTER);
            parameter.set(state.getValue(PrecisionFilterBlock.RATE));
            runtimeA.set(PrecisionFilterBlock.lag(level, blockPos, state));
            runtimeB.set(PrecisionFilterBlock.settled(level, blockPos, state) ? 1 : 0);
        } else if (block instanceof EdgeDetectorBlock) {
            kind.set(KIND_EDGE);
            parameter.set(state.getValue(EdgeDetectorBlock.MODE));
            runtimeA.set(EdgeDetectorBlock.pulseRemaining(level, blockPos));
            runtimeB.set(EdgeDetectorBlock.edgeCount(level, blockPos));
            runtimeC.set(EdgeDetectorBlock.lastEdgeAgeTicks(level, blockPos));
            initialized.set(EdgeDetectorBlock.initialized(level, blockPos) ? 1 : 0);
        } else if (block instanceof PulseShaperBlock) {
            kind.set(KIND_PULSE);
            parameter.set(state.getValue(PulseShaperBlock.WIDTH));
            runtimeA.set(PulseShaperBlock.pulseRemaining(level, blockPos));
            runtimeB.set(PulseShaperBlock.lastInput(level, blockPos));
            initialized.set(PulseShaperBlock.initialized(level, blockPos) ? 1 : 0);
        } else {
            kind.set(-1);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();

        boolean routed = switch (id) {
            case BUTTON_ROTATE_LEFT -> DirectionalSignalBlock.rotateWholeRoute(level, blockPos, false);
            case BUTTON_ROTATE_RIGHT -> DirectionalSignalBlock.rotateWholeRoute(level, blockPos, true);
            case BUTTON_INPUT_LEFT -> DirectionalSignalBlock.rotateSeriesInput(level, blockPos, false);
            case BUTTON_INPUT_RIGHT -> DirectionalSignalBlock.rotateSeriesInput(level, blockPos, true);
            case BUTTON_OUTPUT_LEFT -> DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, false);
            case BUTTON_OUTPUT_RIGHT -> DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, true);
            default -> false;
        };
        if (routed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
            return true;
        }
        if (id >= BUTTON_ROTATE_LEFT && id <= BUTTON_OUTPUT_RIGHT) return false;

        BlockState next = state;
        if (block instanceof PrecisionFilterBlock filter) {
            int value = state.getValue(PrecisionFilterBlock.RATE);
            if (id == BUTTON_PARAMETER_PREVIOUS) value = value <= 1 ? 4 : value - 1;
            else if (id == BUTTON_PARAMETER_NEXT) value = value >= 4 ? 1 : value + 1;
            else return false;
            next = state.setValue(PrecisionFilterBlock.RATE, value);
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, filter, 1);
        } else if (block instanceof EdgeDetectorBlock detector) {
            int value = state.getValue(EdgeDetectorBlock.MODE);
            if (id == BUTTON_PARAMETER_PREVIOUS) value = Math.floorMod(value - 1, 3);
            else if (id == BUTTON_PARAMETER_NEXT) value = (value + 1) % 3;
            else return false;
            next = state.setValue(EdgeDetectorBlock.MODE, value);
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, detector, 1);
        } else if (block instanceof PulseShaperBlock shaper) {
            int value = state.getValue(PulseShaperBlock.WIDTH);
            if (id == BUTTON_PARAMETER_PREVIOUS) value = value <= 1 ? 8 : value - 1;
            else if (id == BUTTON_PARAMETER_NEXT) value = value >= 8 ? 1 : value + 1;
            else return false;
            next = state.setValue(PulseShaperBlock.WIDTH, value);
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, shaper, 1);
        } else return false;

        refreshAuthoritativeSnapshot();
        broadcastChanges();
        return next != state;
    }

    public int kind() { return kind.get(); }
    public int input() { return input.get(); }
    public int output() { return output.get(); }
    public int parameter() { return parameter.get(); }
    public int runtimeA() { return runtimeA.get(); }
    public int runtimeB() { return runtimeB.get(); }
    public int runtimeC() { return runtimeC.get(); }
    public boolean initialized() { return initialized.get() != 0; }
    public int facingOrdinal() { return outputFacing.get(); }
    public boolean hasInputEndpoint() { return inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return outputFacing.get() >= 0; }

    public Direction outputDirection() {
        int ordinal = outputFacing.get();
        Direction[] directions = Direction.values();
        return ordinal < 0 || ordinal >= directions.length ? Direction.NORTH : directions[ordinal];
    }

    public Direction inputDirection() {
        int ordinal = inputFacing.get();
        Direction[] directions = Direction.values();
        return ordinal < 0 || ordinal >= directions.length ? Direction.SOUTH : directions[ordinal];
    }
}
