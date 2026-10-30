package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for AmethystSystem HMI. Server authority remains in the existing menu/block model. */
public final class AmethystSystemScreen extends LdlibEngineeringHostScreen<AmethystSystemMenu> {
    public AmethystSystemScreen(AmethystSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
