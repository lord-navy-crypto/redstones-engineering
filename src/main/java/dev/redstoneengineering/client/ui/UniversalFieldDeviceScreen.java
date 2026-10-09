package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Thin host for the LDLib2 Universal Field Device HMI.
 *
 * <p>The synchronized UniversalFieldDeviceMenu remains the authoritative owner
 * of routing, configuration, Pioneer snapshots and six-face port evidence.</p>
 */
public final class UniversalFieldDeviceScreen
        extends LdlibEngineeringHostScreen<UniversalFieldDeviceMenu> {

    public UniversalFieldDeviceScreen(
            UniversalFieldDeviceMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
    }
}
