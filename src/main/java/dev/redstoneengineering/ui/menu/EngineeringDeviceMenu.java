package dev.redstoneengineering.ui.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Shared no-inventory menu base for RSE engineering instruments and controllers. */
public abstract class EngineeringDeviceMenu extends AbstractContainerMenu {
    protected final Inventory playerInventory;
    protected final Level level;
    protected final BlockPos blockPos;
    private final Block expectedBlock;

    protected EngineeringDeviceMenu(
            MenuType<?> type,
            int containerId,
            Inventory playerInventory,
            BlockPos blockPos,
            Block expectedBlock
    ) {
        super(type, containerId);
        this.playerInventory = playerInventory;
        this.level = playerInventory.player.level();
        this.blockPos = blockPos;
        this.expectedBlock = expectedBlock;
    }

    protected DataSlot trackedInt() {
        DataSlot slot = DataSlot.standalone();
        addDataSlot(slot);
        return slot;
    }

    public BlockPos blockPos() {
        return blockPos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(blockPos).is(expectedBlock)) return false;
        double dx = player.getX() - (blockPos.getX() + 0.5D);
        double dy = player.getY() - (blockPos.getY() + 0.5D);
        double dz = player.getZ() - (blockPos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
        super.broadcastChanges();
    }

    protected abstract void refreshAuthoritativeSnapshot();
}
