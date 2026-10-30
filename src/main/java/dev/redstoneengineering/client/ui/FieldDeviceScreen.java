package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Thin LDLib2 host for the legacy FieldDevice fallback route.
 *
 * <p>The same EnhancedFieldDeviceLdUi tree is attached by FieldDeviceMenu, so
 * fallback and enhanced entry paths share one presentation contract while the
 * existing menu/block model remains server-authoritative.</p>
 */
public final class FieldDeviceScreen extends LdlibEngineeringHostScreen<FieldDeviceMenu> {
    public FieldDeviceScreen(FieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
