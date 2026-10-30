package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.client.ui.ldlib.LdlibEngineeringHostScreen;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** LDLib2 hosts the complete four-channel Logic Analyzer HMI. */
public final class LogicAnalyzerScreen extends LdlibEngineeringHostScreen<LogicAnalyzerMenu> {
    public LogicAnalyzerScreen(LogicAnalyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
