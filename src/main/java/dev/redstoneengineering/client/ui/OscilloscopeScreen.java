package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 renders the complete instrument workspace. Server authority remains in the existing menu/block model. */
public final class OscilloscopeScreen extends LdlibEngineeringHostScreen<OscilloscopeMenu> {
    public OscilloscopeScreen(OscilloscopeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
