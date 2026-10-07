package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Thin host for the LDLib2 Range Sensor engineering HMI. */
public final class RangeSensorScreen extends LdlibEngineeringHostScreen<RangeSensorMenu> {
    public RangeSensorScreen(RangeSensorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
