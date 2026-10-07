package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.WorkcellControllerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for synchronized Workcell admission/evidence. */
public final class WorkcellControllerScreen extends LdlibEngineeringHostScreen<WorkcellControllerMenu> {
    public WorkcellControllerScreen(WorkcellControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
