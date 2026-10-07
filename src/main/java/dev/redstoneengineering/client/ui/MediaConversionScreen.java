package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.MediaConversionMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Thin host for the LDLib2 Media Conversion engineering HMI. */
public final class MediaConversionScreen extends LdlibEngineeringHostScreen<MediaConversionMenu> {
    public MediaConversionScreen(MediaConversionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
