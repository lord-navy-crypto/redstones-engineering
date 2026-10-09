package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * LDLib2 host for the final lightweight FieldDevice fallback HMI.
 *
 * <p>The 78-kind taxonomy, exact controls, source/medium integrity and synchronized
 * evidence live in EnhancedFieldDeviceLdUi. Server authority remains in FieldDeviceMenu.</p>
 */
public final class EnhancedFieldDeviceScreen extends LdlibEngineeringHostScreen<FieldDeviceMenu> {
    public EnhancedFieldDeviceScreen(FieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
