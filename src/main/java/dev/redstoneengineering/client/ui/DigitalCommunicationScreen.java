package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 hosts the digital communication medium/converter HMI. */
public final class DigitalCommunicationScreen extends LdlibEngineeringHostScreen<DigitalCommunicationMenu> {
    public DigitalCommunicationScreen(DigitalCommunicationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
