package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 hosts the reliability/safe-state engineering HMI. */
public final class ReliabilitySystemScreen extends LdlibEngineeringHostScreen<ReliabilitySystemMenu> {
    public ReliabilitySystemScreen(ReliabilitySystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
