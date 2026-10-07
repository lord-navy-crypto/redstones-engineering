package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 hosts the optical engineering / commissioning HMI. */
public final class OpticalSystemScreen extends LdlibEngineeringHostScreen<OpticalSystemMenu> {
    public OpticalSystemScreen(OpticalSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
