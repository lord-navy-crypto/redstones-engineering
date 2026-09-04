package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Level-2 engineering workbench for PID commissioning and tuning-preset selection. */
public final class PidControllerScreen extends EngineeringScreen<PidControllerMenu> {
    public PidControllerScreen(PidControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 108;
        addConfigureWidget(Button.builder(
                Component.literal("◀ Preset"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_TUNING_PREVIOUS)
        ).bounds(leftPos + 18, y, 92, 20).build());
        addConfigureWidget(Button.builder(
                Component.literal("Preset ▶"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_TUNING_NEXT)
        ).bounds(leftPos + 114, y, 92, 20).build());
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> renderOverview(graphics);
            case PORTS -> renderPorts(graphics);
            case CONFIGURE -> renderConfigure(graphics);
            case DIAGNOSTICS -> renderDiagnostics(graphics);
            case HISTORY -> renderHistory(graphics);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        labelValue(graphics, "Setpoint (SP)", Integer.toString(menu.setpoint()), 82);
        labelValue(graphics, "Process value (PV)", Integer.toString(menu.processValue()), 97);
        labelValue(graphics, "Error", signed(menu.error()), 112);
        labelValue(graphics, "Control output", Integer.toString(menu.controlOutput()), 127);
        statusLine(graphics, "Commissioning", menu.status().name(), statusColor(menu.status()), 142);
        labelValue(graphics, "Score", menu.available() ? menu.score() + " / 100" : "N/A", 157);
        signalBar(graphics, menu.controlOutput(), 180);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "BACK", "SETPOINT • INPUT", GOOD, 81);
        statusLine(graphics, "LEFT", "PROCESS VALUE • INPUT", GOOD, 97);
        statusLine(graphics, "RIGHT", "INHIBIT • INPUT", WARN, 113);
        statusLine(graphics, "FRONT", "CONTROL OUTPUT", GOOD, 129);
        statusLine(graphics, "UP", "MODE SELECT • 0=AUTO", MUTED, 145);
        statusLine(graphics, "DOWN", "MANUAL OUTPUT", MUTED, 161);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Tuning preset", tuningName(menu.tuning()), 84);
        graphics.drawString(font, "The UI selects the existing bounded preset only.", 16, 146, MUTED, false);
        graphics.drawString(font, "PID physics and runtime state remain server-owned.", 16, 161, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        labelValue(graphics, "Rise to 90%", menu.rise90Ticks() + " ticks", 81);
        labelValue(graphics, "Settling time", menu.settlingTicks() + " ticks", 97);
        labelValue(graphics, "Overshoot", Integer.toString(menu.overshoot()), 113);
        labelValue(graphics, "Saturation events", Integer.toString(menu.saturationEvents()), 129);
        labelValue(graphics, "Mode transfers", Integer.toString(menu.modeTransfers()), 145);
        statusLine(graphics, "Operating mode", menu.manualMode() ? "MANUAL" : "AUTO", menu.manualMode() ? WARN : GOOD, 161);
        statusLine(graphics, "Inhibit", menu.inhibited() ? "ACTIVE" : "CLEAR", menu.inhibited() ? BAD : GOOD, 177);
    }

    private void renderHistory(GuiGraphics graphics) {
        labelValue(graphics, "Captured runs", menu.historyCount() + " / 8", 84);
        graphics.drawString(font, "Shift + FRONT: capture authoritative acceptance evidence", 16, 110, TEXT, false);
        graphics.drawString(font, "Shift + other face: reset PID runtime", 16, 128, WARN, false);
        graphics.drawString(font, "History remains bounded and transient in Alpha 1.0.20.", 16, 154, MUTED, false);
    }

    private static String tuningName(int tuning) {
        return switch (tuning) {
            case 0 -> "P-GENTLE";
            case 1 -> "PI";
            case 2 -> "PID-BALANCED";
            case 3 -> "PID-AGGRESSIVE";
            default -> "UNKNOWN";
        };
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static int statusColor(CommissioningStatus status) {
        return switch (status) {
            case PASS -> GOOD;
            case MARGINAL, RUNNING -> WARN;
            case FAIL -> BAD;
            case IDLE, UNAVAILABLE -> MUTED;
        };
    }
}
