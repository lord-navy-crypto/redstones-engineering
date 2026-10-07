package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for PneumaticSystem HMI. Server authority remains in the existing menu/block model. */
public final class PneumaticSystemScreen extends LdlibEngineeringHostScreen<PneumaticSystemMenu> {
    public PneumaticSystemScreen(PneumaticSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
