package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.PidTelemetryHistory;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Level-2 engineering workbench for PID commissioning and tuning-preset selection. */
public final class PidControllerScreen extends EngineeringScreen<PidControllerMenu> {
    private final EngineeringChartRenderer.Series setpointSeries;
    private final EngineeringChartRenderer.Series processSeries;
    private final EngineeringChartRenderer.Series outputSeries;
    private final EngineeringChartRenderer.Series errorSeries;
    private final EngineeringChartRenderer.Series saturationSeries;

    public PidControllerScreen(PidControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.setpointSeries = new PidSeries(0);
        this.processSeries = new PidSeries(1);
        this.outputSeries = new PidSeries(2);
        this.errorSeries = new PidSeries(3);
        this.saturationSeries = new PidSeries(4);
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
        labelValue(graphics, "Control output (OUT)", menu.controlOutput() + " / 15", 146);
        labelValue(graphics, "Telemetry", menu.telemetryCount() + " / " + PidTelemetryHistory.DISPLAY_SAMPLES
                + " • " + menu.telemetryTimeSpanTicks() + " gt", 161);
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
        labelValue(graphics, "Anti-windup events", Integer.toString(menu.saturationEvents()), 144);
        labelValue(graphics, "Window mean |error|", hundredths(menu.telemetryMeanAbsError100()), 160);
        labelValue(graphics, "Steady-state |error|", steadyStateError(), 176);
    }

    private void renderHistory(GuiGraphics graphics) {
        graphics.drawString(font, "SP", 16, 80, ACCENT, false);
        graphics.drawString(font, "PV", 40, 80, INFO, false);
        graphics.drawString(font, "OUT", 64, 80, GOOD, false);
        graphics.drawString(font, "SAT", 96, 80, BAD, false);
        graphics.drawString(font, "EVIDENCE " + menu.historyCount() + " / 8", 218, 80, MUTED, false);

        int chartX = 16;
        int chartWidth = 288;
        EngineeringChartRenderer.drawFrame(graphics, chartX, 89, chartWidth, 43, 0, 15, true);
        EngineeringChartRenderer.drawWaveform(graphics, setpointSeries, chartX, 89, chartWidth, 43, 0, 15, ACCENT);
        EngineeringChartRenderer.drawWaveform(graphics, processSeries, chartX, 89, chartWidth, 43, 0, 15, INFO);
        EngineeringChartRenderer.drawWaveform(graphics, outputSeries, chartX, 89, chartWidth, 43, 0, 15, GOOD);
        EngineeringChartRenderer.drawLimitHitMarkers(graphics, saturationSeries, chartX, 89, chartWidth, 43, 0, 15, BAD);
        EngineeringChartRenderer.drawGameTimeAxis(graphics, font, processSeries, chartX, 134, chartWidth);

        graphics.drawString(font, "ERROR (SP − PV)", 16, 147, TEXT, false);
        graphics.drawString(font, "Shift+FRONT captures acceptance evidence", 156, 147, MUTED, false);
        EngineeringChartRenderer.drawFrame(graphics, chartX, 157, chartWidth, 25, -15, 15, true);
        graphics.fill(chartX + 1, 169, chartX + chartWidth - 1, 170, BORDER);
        EngineeringChartRenderer.drawWaveform(graphics, errorSeries, chartX, 157, chartWidth, 25, -15, 15, WARN);
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

    private String steadyStateError() {
        if (menu.stepActive() || menu.settlingTicks() <= 0 || menu.telemetryRecentAbsError100() < 0) return "N/A";
        return hundredths(menu.telemetryRecentAbsError100());
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
        return ticks > 0 ? ticks + " ticks" : "N/A";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String hundredths(int value100) {
        if (value100 < 0) return "N/A";
        return (value100 / 100) + "." + String.format("%02d", value100 % 100);
    }

    private static int statusColor(CommissioningStatus status) {
        return switch (status) {
            case PASS -> GOOD;
            case MARGINAL, RUNNING -> WARN;
            case FAIL -> BAD;
            case IDLE, UNAVAILABLE -> MUTED;
        };
    }

    private final class PidSeries implements EngineeringChartRenderer.Series {
        private final int kind;

        private PidSeries(int kind) {
            this.kind = kind;
        }

        @Override
        public int size() {
            return PidTelemetryHistory.DISPLAY_SAMPLES;
        }

        @Override
        public int valueAt(int slot) {
            return switch (kind) {
                case 0 -> menu.telemetrySetpoint(slot);
                case 1 -> menu.telemetryProcessValue(slot);
                case 2 -> menu.telemetryOutput(slot);
                case 3 -> menu.telemetryError(slot);
                case 4 -> menu.telemetrySaturated(slot) ? menu.telemetryOutput(slot) : -1;
                default -> -1;
            };
        }

        @Override
        public long gameTimeAt(int slot) {
            return menu.telemetryGameTime(slot);
        }
    }
}
