package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Thin host for the LDLib2 Signal Processor engineering HMI. */
public final class SignalProcessorScreen extends LdlibEngineeringHostScreen<SignalProcessorMenu> {
    public SignalProcessorScreen(SignalProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
