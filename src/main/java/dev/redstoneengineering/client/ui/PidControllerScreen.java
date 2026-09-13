package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Level-2 engineering workbench for PID commissioning, tuning and physical I/O routing. */
public final class PidControllerScreen extends EngineeringScreen<PidControllerMenu> {
    private static final int SP_COLOR = WARN;
    private static final int PV_COLOR = GOOD;
    private static final int OUT_COLOR = INFO;

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

        int routeY = topPos + 137;
        addConfigureWidget(Button.builder(Component.literal("RX ▲"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_INPUT_PREVIOUS))
                .bounds(leftPos + 18, routeY, 62, 20).build());
        addConfigureWidget(Button.builder(Component.literal("RX ▼"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_INPUT_NEXT))
                .bounds(leftPos + 84, routeY, 62, 20).build());
        addConfigureWidget(Button.builder(Component.literal("TX ▲"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_OUTPUT_PREVIOUS))
                .bounds(leftPos + 150, routeY, 62, 20).build());
        addConfigureWidget(Button.builder(Component.literal("TX ▼"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_OUTPUT_NEXT))
                .bounds(leftPos + 216, routeY, 62, 20).build());
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
        Direction tx = menu.outputFacing();
        Direction rx = menu.inputFacing();
        Direction process = tx.getCounterClockWise();
        Direction inhibit = tx.getClockWise();
        statusLine(graphics, face(rx), "SETPOINT • INPUT 0..15", GOOD, 80);
        statusLine(graphics, face(process), "PROCESS VALUE • INPUT 0..15", GOOD, 96);
        statusLine(graphics, face(inhibit), "INHIBIT • >0 FORCES OUTPUT 0", WARN, 112);
        statusLine(graphics, face(tx), "CONTROL OUTPUT • 0..15", GOOD, 128);
        statusLine(graphics, "UP", "MODE SELECT • 0=AUTO, >0=MANUAL", INFO, 144);
        statusLine(graphics, "DOWN", "MANUAL OUTPUT • 0..15", INFO, 160);
        safeText(graphics, "RX/TX routing preserves all six physical port assignments.", 16, 178, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Tuning preset", tuningName(menu.tuning()), 82);
        safeText(graphics, tuningDescription(menu.tuning()), 16, 98, TEXT);
        safeText(graphics,
                "Route: RX=" + face(menu.inputFacing()) + " • TX=" + face(menu.outputFacing())
                        + " • dense PID side ports rotate collision-free",
                16, 164, INFO);
        safeText(graphics, "Preset and route actions are server-authoritative.", 16, 180, MUTED);
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
        int x = 38;
        int y = 80;
        int width = 260;
        int height = 64;

        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        EngineeringPlot.analogTrace(
                graphics, PidControllerMenu.TREND_SAMPLES, menu::trendSetpoint,
                0, 15, x, y, width, height, SP_COLOR);
        EngineeringPlot.analogTrace(
                graphics, PidControllerMenu.TREND_SAMPLES, menu::trendProcessValue,
                0, 15, x, y, width, height, PV_COLOR);
        EngineeringPlot.analogTrace(
                graphics, PidControllerMenu.TREND_SAMPLES, menu::trendControlOutput,
                0, 15, x, y, width, height, OUT_COLOR);

        graphics.drawString(font, "15", 18, y - 3, MUTED, false);
        graphics.drawString(font, "0", 24, y + height - 4, MUTED, false);
        graphics.drawString(font, "SP", 45, 150, SP_COLOR, false);
        graphics.drawString(font, "PV", 74, 150, PV_COLOR, false);
        graphics.drawString(font, "OUT", 103, 150, OUT_COLOR, false);
        graphics.drawString(font, "newest →", 240, 150, MUTED, false);
        safeText(graphics, menu.trendCount() + "/32 authoritative samples • 2t/sample • transient", 16, 163, MUTED);

        statusBadge(graphics, "EVIDENCE " + menu.historyCount() + " / 8", menu.historyCount() >= 8 ? WARN : INFO, 16, 177);
        safeText(graphics, "Shift+TX capture • Shift+other face resets runtime + trend", 112, 180, TEXT);
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

    private static String face(Direction direction) {
        return direction.getName().toUpperCase();
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
