package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Client-facing shell for the registry-backed Redstone Encyclopedia. */
public final class RedstoneEncyclopediaMenu extends AbstractContainerMenu {
    public RedstoneEncyclopediaMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf ignored) {
        this(containerId, inventory);
    }

    public RedstoneEncyclopediaMenu(int containerId, Inventory inventory) {
        super(EngineeringUiRegistration.REDSTONE_ENCYCLOPEDIA.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
