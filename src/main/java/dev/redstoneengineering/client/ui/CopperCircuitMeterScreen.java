package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.CopperCircuitMeterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for the CopperCircuitMeter engineering HMI. Server authority remains in the existing menu/block model. */
public final class CopperCircuitMeterScreen extends LdlibEngineeringHostScreen<CopperCircuitMeterMenu> {
    public CopperCircuitMeterScreen(CopperCircuitMeterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
