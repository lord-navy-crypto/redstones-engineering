package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import dev.redstoneengineering.ui.ldlib.SignalConditionerLdUi;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative configuration and live readback for the Signal Conditioner. */
public final class SignalConditionerMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_MODE_PREVIOUS = 0;
    public static final int BUTTON_MODE_NEXT = 1;
    public static final int BUTTON_PARAM_DECREASE = 2;
    public static final int BUTTON_PARAM_INCREASE = 3;
    /** Legacy whole-route controls retained for compatibility. */
    public static final int BUTTON_ROTATE_LEFT = 4;
    public static final int BUTTON_ROTATE_RIGHT = 5;
    public static final int BUTTON_INPUT_LEFT = 6;
    public static final int BUTTON_INPUT_RIGHT = 7;
    public static final int BUTTON_OUTPUT_LEFT = 8;
    public static final int BUTTON_OUTPUT_RIGHT = 9;
    /** Formula parameter value encoded as BASE + (value + 16), supporting signed offset mode. */
    public static final int BUTTON_PARAM_DIRECT_BASE = 5000;
    public static final int BUTTON_PARAM_DIRECT_MAX = 5031;

    private final DataSlot mode = trackedInt();
    private final DataSlot parameter = trackedInt();
    private final DataSlot input = trackedInt();
    private final DataSlot output = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();
    private final DataSlot limiting = trackedInt();

    public SignalConditionerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public SignalConditionerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.SIGNAL_CONDITIONER.get(), containerId, inventory, pos,
                RedstoneEngineering.SIGNAL_CONDITIONER.get());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
        if (this instanceof IModularUIHolderMenu holder) {
            holder.setModularUI(SignalConditionerLdUi.create(this, inventory.player));
        }
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof SignalConditionerBlock)) return;
        mode.set(state.getValue(SignalConditionerBlock.MODE));
        parameter.set(state.getValue(SignalConditionerBlock.PARAM));
        input.set(SignalConditionerBlock.inspectInput(level, blockPos, state));
        output.set(state.getValue(DirectionalSignalBlock.OUTPUT));
        inputFacing.set(DirectionalSignalBlock.seriesInputSide(state).ordinal());
        outputFacing.set(DirectionalSignalBlock.seriesOutputSide(state).ordinal());
        limiting.set(SignalConditionerBlock.limitingActive(level, blockPos, state) ? 1 : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;

        boolean changed;
        if (id >= BUTTON_PARAM_DIRECT_BASE && id <= BUTTON_PARAM_DIRECT_MAX) {
            changed = SignalConditionerBlock.setFormulaParameter(level, blockPos, (id - BUTTON_PARAM_DIRECT_BASE) - 16);
        } else if (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT) {
            changed = DirectionalSignalBlock.rotateSeriesInput(level, blockPos, id == BUTTON_INPUT_RIGHT);
        } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT) {
            changed = DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, id == BUTTON_OUTPUT_RIGHT);
        } else if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            changed = DirectionalSignalBlock.rotateWholeRoute(level, blockPos, id == BUTTON_ROTATE_RIGHT);
        } else {
            changed = SignalConditionerBlock.applyConfigurationAction(level, blockPos, id);
        }
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    /** LDLib2 server-event facade: preserve the existing validated menu mutation path. */
    public boolean cycleModeForward() {
        return clickMenuButton(player, BUTTON_MODE_NEXT);
    }

    public boolean cycleInputForward() {
        return clickMenuButton(player, BUTTON_INPUT_RIGHT);
    }

    public boolean cycleOutputForward() {
        return clickMenuButton(player, BUTTON_OUTPUT_RIGHT);
    }

    public boolean applyVisibleFormulaParameter(int value) {
        if (!validVisibleFormulaParameter(mode(), value)) return false;
        return clickMenuButton(player, BUTTON_PARAM_DIRECT_BASE + value + 16);
    }

    private static boolean validVisibleFormulaParameter(int mode, int value) {
        return switch (mode) {
            case 0, 4 -> value >= 1 && value <= 4;
            case 1 -> value >= -5 && value <= 5;
            case 2, 3 -> value >= 1 && value <= 15;
            default -> false;
        };
    }

    public int mode() { return mode.get(); }
    public int parameter() { return parameter.get(); }
    public int input() { return input.get(); }
    public int output() { return output.get(); }
    public boolean limiting() { return limiting.get() != 0; }
    public boolean hasInputEndpoint() { return inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return outputFacing.get() >= 0; }

    public Direction outputDirection() {
        int ordinal = outputFacing.get();
        return ordinal >= 0 && ordinal < Direction.values().length ? Direction.values()[ordinal] : Direction.NORTH;
    }

    public Direction inputDirection() {
        int ordinal = inputFacing.get();
        return ordinal >= 0 && ordinal < Direction.values().length ? Direction.values()[ordinal] : Direction.SOUTH;
    }
}
