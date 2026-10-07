package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.LapisLowPassMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for the formula-first Lapis low-pass filter HMI. */
public final class LapisLowPassScreen extends LdlibEngineeringHostScreen<LapisLowPassMenu> {
    public LapisLowPassScreen(LapisLowPassMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
