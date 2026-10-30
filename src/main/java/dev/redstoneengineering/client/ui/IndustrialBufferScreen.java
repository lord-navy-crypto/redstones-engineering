package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.IndustrialBufferMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 host for read-only Industrial Buffer evidence. */
public final class IndustrialBufferScreen extends LdlibEngineeringHostScreen<IndustrialBufferMenu> {
    public IndustrialBufferScreen(IndustrialBufferMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
