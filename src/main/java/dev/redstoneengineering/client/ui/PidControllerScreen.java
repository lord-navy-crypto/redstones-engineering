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
        int y = topPos + 111;
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
        statusBadge(graphics, operatingState(), operatingColor(), 16, 80);
        statusBadge(graphics, commissioningState(), statusColor(menu.status()), 112, 80);
        labelValue(graphics, "Setpoint (SP)", menu.setpoint() + " / 15", 101);
        labelValue(graphics, "Process value (PV)", menu.processValue() + " / 15", 116);
        labelValue(graphics, "Error (SP − PV)", signed(menu.error()), 131);
        labelValue(graphics, "Control output", menu.controlOutput() + " / 15", 146);
        labelValue(graphics, "Acceptance score", menu.available() ? menu.score() + " / 100" : "N/A", 161);
        signalBar(graphics, menu.controlOutput(), 177);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "BACK", "SETPOINT • INPUT 0..15", GOOD, 80);
        statusLine(graphics, "LEFT", "PROCESS VALUE • INPUT 0..15", GOOD, 96);
        statusLine(graphics, "RIGHT", "INHIBIT • >0 FORCES OUTPUT 0", WARN, 112);
        statusLine(graphics, "FRONT", "CONTROL OUTPUT • 0..15", GOOD, 128);
        statusLine(graphics, "UP", "MODE SELECT • 0=AUTO, >0=MANUAL", INFO, 144);
        statusLine(graphics, "DOWN", "MANUAL OUTPUT • 0..15", INFO, 160);
        graphics.drawString(font, "All six faces have explicit engineering meaning.", 16, 178, MUTED, false);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Tuning preset", tuningName(menu.tuning()), 82);
        graphics.drawString(font, tuningDescription(menu.tuning()), 16, 98, TEXT, false);
        graphics.drawString(font, tuningProfile(menu.tuning()), 16, 150, INFO, false);
        graphics.drawString(font, "Preset selection is bounded; controller physics stays server-owned.", 16, 169, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Telemetry", menu.available() ? "AVAILABLE" : "WAITING FOR RUN", menu.available() ? GOOD : MUTED, 80);
        labelValue(graphics, "Rise to 90%", metricTicks(menu.rise90Ticks()), 96);
        labelValue(graphics, "Settling time", metricTicks(menu.settlingTicks()), 112);
        labelValue(graphics, "Overshoot", Integer.toString(menu.overshoot()), 128);
        labelValue(graphics, "Saturation events", Integer.toString(menu.saturationEvents()), 144);
        labelValue(graphics, "Mode transfers", Integer.toString(menu.modeTransfers()), 160);
        statusLine(graphics, "Inhibit", menu.inhibited() ? "ACTIVE • OUTPUT FORCED LOW" : "CLEAR", menu.inhibited() ? BAD : GOOD, 176);
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "EVIDENCE " + menu.historyCount() + " / 8", menu.historyCount() >= 8 ? WARN : INFO, 16, 82);
        graphics.drawString(font, "Shift + FRONT", 16, 107, TEXT, false);
        graphics.drawString(font, "Capture the current authoritative acceptance evidence.", 112, 107, INFO, false);
        graphics.drawString(font, "Shift + other face", 16, 126, TEXT, false);
        graphics.drawString(font, "Reset transient PID runtime state.", 112, 126, WARN, false);
        sectionRule(graphics, 146);
        graphics.drawString(font, "History is bounded to 8 runs and remains transient in Alpha 1.0.20.", 16, 157, MUTED, false);
        graphics.drawString(font, "Use captured runs to compare commissioning results, not to drive physics.", 16, 174, MUTED, false);
    }

    private String operatingState() {
        if (menu.inhibited()) return "INHIBITED";
        return menu.manualMode() ? "MANUAL" : "AUTO";
    }

    private int operatingColor() {
        if (menu.inhibited()) return BAD;
        return menu.manualMode() ? WARN : GOOD;
    }

    private String commissioningState() {
        if (!menu.available()) return "COMMISSIONING N/A";
        return "COMMISSIONING " + menu.status().name();
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

    private static String tuningDescription(int tuning) {
        return switch (tuning) {
            case 0 -> "Gentle proportional-only response for simple, stable plants.";
            case 1 -> "Adds slow integral correction to remove persistent steady-state error.";
            case 2 -> "Balanced P + I + D preset for general closed-loop commissioning.";
            case 3 -> "Higher proportional/derivative action for faster, more demanding plants.";
            default -> "Unknown tuning preset.";
        };
    }

    private static String tuningProfile(int tuning) {
        return switch (tuning) {
            case 0 -> "Profile: Kp=1 • integral OFF • derivative OFF";
            case 1 -> "Profile: Kp=2 • integral divisor=24 • derivative OFF";
            case 2 -> "Profile: Kp=2 • integral divisor=18 • Kd=1 • D smoothing=3";
            case 3 -> "Profile: Kp=3 • integral divisor=14 • Kd=2 • D smoothing=4";
            default -> "Profile unavailable";
        };
    }

    private static String metricTicks(int ticks) {
        return ticks > 0 ? ticks + " ticks" : "—";
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
