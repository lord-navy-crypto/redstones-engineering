package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.SignalConditionerBlock;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
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

    private final DataSlot mode = trackedInt();
    private final DataSlot parameter = trackedInt();
    private final DataSlot input = trackedInt();
    private final DataSlot output = trackedInt();

    public SignalConditionerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public SignalConditionerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(
                EngineeringUiRegistration.SIGNAL_CONDITIONER.get(),
                containerId,
                inventory,
                pos,
                RedstoneEngineering.SIGNAL_CONDITIONER.get()
        );
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof SignalConditionerBlock)) return;
        mode.set(state.getValue(SignalConditionerBlock.MODE));
        parameter.set(state.getValue(SignalConditionerBlock.PARAM));
        input.set(SignalConditionerBlock.inspectInput(level, blockPos, state));
        output.set(state.getValue(DirectionalSignalBlock.OUTPUT));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = SignalConditionerBlock.applyConfigurationAction(level, blockPos, id);
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int mode() {
        return mode.get();
    }

    public int parameter() {
        return parameter.get();
    }

    public int input() {
        return input.get();
    }

    public int output() {
        return output.get();
    }
}
