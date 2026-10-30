package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for the QuartzTiming engineering HMI. Server authority remains in the existing menu/block model. */
public final class QuartzTimingScreen extends LdlibEngineeringHostScreen<QuartzTimingMenu> {
    public QuartzTimingScreen(QuartzTimingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
