package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for the RadioLink engineering HMI. Server authority remains in the existing menu/block model. */
public final class RadioLinkScreen extends LdlibEngineeringHostScreen<RadioLinkMenu> {
    public RadioLinkScreen(RadioLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
