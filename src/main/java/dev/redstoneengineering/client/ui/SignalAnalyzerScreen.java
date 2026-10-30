package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 hosts the complete metrology/calibration Signal Analyzer HMI. */
public final class SignalAnalyzerScreen extends LdlibEngineeringHostScreen<SignalAnalyzerMenu> {
    public SignalAnalyzerScreen(SignalAnalyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
