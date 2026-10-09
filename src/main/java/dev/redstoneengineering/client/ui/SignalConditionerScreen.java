package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 renders the complete engineering HMI. Server authority remains in the existing menu/block model. */
public final class SignalConditionerScreen extends LdlibEngineeringHostScreen<SignalConditionerMenu> {
    public SignalConditionerScreen(SignalConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
