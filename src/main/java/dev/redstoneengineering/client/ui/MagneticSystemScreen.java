package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for the MagneticSystem engineering HMI. Server authority remains in the existing menu/block model. */
public final class MagneticSystemScreen extends LdlibEngineeringHostScreen<MagneticSystemMenu> {
    public MagneticSystemScreen(MagneticSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
