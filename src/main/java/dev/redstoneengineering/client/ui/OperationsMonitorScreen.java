package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.OperationsMonitorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for observer-only Operations plant evidence. */
public final class OperationsMonitorScreen extends LdlibEngineeringHostScreen<OperationsMonitorMenu> {
    public OperationsMonitorScreen(OperationsMonitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
