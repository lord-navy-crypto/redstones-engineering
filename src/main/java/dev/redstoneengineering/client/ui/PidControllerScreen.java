package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.CommissioningStatus;
import dev.redstoneengineering.diagnostics.PneumaticClosedLoopWitness;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceTrend;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;
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

        int commissioningY = topPos + 198;
        addConfigureWidget(Button.builder(Component.literal("Capture acceptance"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_CAPTURE_ACCEPTANCE))
                .bounds(leftPos + 18, commissioningY, 140, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Reset runtime + trend"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_RESET_RUNTIME_TREND))
                .bounds(leftPos + 162, commissioningY, 140, 20).build());
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
        labelValue(graphics, "Output / actuator target",
                menu.controlOutput() + " / " + menu.actuatorTarget(), 146);
        labelValue(graphics, "System score", menu.available() ? menu.score() + " / 100" : "N/A", 161);
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
        labelValue(graphics, "Kp / Ki divisor / Kd",
                menu.proportionalGain() + " / " + menu.integralDivisor() + " / " + menu.derivativeGain(), 139);
        labelValue(graphics, "D smoothing / slew ↑ / ↓",
                menu.derivativeSmoothing() + " / " + menu.riseLimit() + " / " + menu.fallLimit(), 157);
        safeText(graphics,
                "Derivative is on measured PV to avoid setpoint derivative kick; rise/fall slew limits are levels per 2t control cycle.",
                16, 176, MUTED);
        safeText(graphics,
                "Commissioning actions below capture/reset evidence. Physical RX/TX orientation lives only on Route.",
                16, 190, INFO);
        safeText(graphics, "Preset selection and commissioning actions remain server-authoritative.", 16, 226, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Controller",
                menu.controllerStatus().name() + " • score " + menu.controllerScore(),
                statusColor(menu.controllerStatus()), 80);
        labelValue(graphics, "Rise / settle",
                metricTicks(menu.rise90Ticks()) + " / " + metricTicks(menu.settlingTicks()), 96);
        labelValue(graphics, "Overshoot / saturation",
                menu.overshoot() + " / " + menu.saturationEvents(), 112);
        statusLine(graphics, "Actuator slew",
                (menu.slewActive() ? "LIMITING • target " : "TRACKING • target ")
                        + menu.actuatorTarget() + " • episodes " + menu.slewEvents(),
                menu.slewActive() ? WARN : GOOD, 128);

        if (!menu.plantDetected()) {
            statusLine(graphics, "Plant witness", "NONE • explicit cylinder feedback not detected", MUTED, 148);
            safeText(graphics,
                    "Generic PID commissioning remains controller-only until PROCESS VALUE is directly wired to a formal cylinder FEEDBACK port.",
                    16, 167, MUTED);
            statusLine(graphics, "System verdict",
                    menu.status().name() + " • score " + menu.score(), statusColor(menu.status()), 194);
            statusLine(graphics, "Inhibit",
                    menu.inhibited() ? "ACTIVE • OUTPUT FORCED LOW" : "CLEAR",
                    menu.inhibited() ? BAD : GOOD, 210);
            return;
        }

        String plantState = menu.plantReady() ? menu.plantStatus().name() : "WARMING";
        statusLine(graphics, "Pneumatic plant",
                plantState + " • penalty " + menu.plantPenalty() + " • n=" + menu.plantSamples(),
                menu.plantReady() ? statusColor(menu.plantStatus()) : WARN, 148);
        labelValue(graphics, "Position / target / stall",
                menu.plantPosition() + " / " + menu.plantTarget() + " / " + menu.plantStallTicks() + "t", 164);
        labelValue(graphics, "Actuator / supply pressure", menu.plantPressure() + " / " + menu.plantSupply(), 180);
        labelValue(graphics, "Loss obs / line / restrict",
                menu.plantObservedLoss() + " / " + menu.plantLineLoss() + " / " + menu.plantRestrictionLoss(), 196);
        statusLine(graphics, "Likely cause",
                diagnosisLabel(menu.plantDiagnosis()), diagnosisColor(menu.plantDiagnosis()), 212);
        safeText(graphics,
                "System " + menu.status().name() + " • score " + menu.score(),
                218, 228, statusColor(menu.status()));
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = 38;
        int y = 80;
        int width = 260;
        int height = 58;

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
        graphics.drawString(font, "SP", 45, 144, SP_COLOR, false);
        graphics.drawString(font, "PV", 74, 144, PV_COLOR, false);
        graphics.drawString(font, "OUT", 103, 144, OUT_COLOR, false);
        graphics.drawString(font, "newest →", 240, 144, MUTED, false);
        safeText(graphics, menu.trendCount() + "/32 authoritative samples • 2t/sample • transient", 16, 157, MUTED);

        statusBadge(graphics, "EVIDENCE " + menu.historyCount() + " / 8", menu.historyCount() >= 8 ? WARN : INFO, 16, 174);
        if (menu.historyCount() == 0) {
            safeText(graphics, "No retained acceptance capture yet. Use Configure → Capture acceptance.", 112, 177, MUTED);
        } else {
            String latest = "#" + menu.latestSequence() + " • " + menu.latestAcceptanceStatus().name()
                    + " • score " + menu.latestAcceptanceScore();
            safeText(graphics, latest, 112, 177, acceptanceColor(menu.latestAcceptanceStatus()));
            AcceptanceEvidenceTrend trend = menu.comparisonTrend();
            if (trend != null) {
                safeText(graphics,
                        "Compared with previous: " + trend.name()
                                + " • Δscore " + signed(menu.scoreDelta())
                                + " • Δissues " + signed(menu.topologyIssueDelta()),
                        16, 197, comparisonColor(trend));
            } else {
                safeText(graphics, "Baseline capture established; capture again after a change to compare.", 16, 197, INFO);
            }
        }
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
        return "SYSTEM " + menu.status().name();
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
            case 0 -> "Gentle P-only response with slow 1-level/cycle actuator motion.";
            case 1 -> "PI control with slow actuator motion for steady, inertia-dominated plants.";
            case 2 -> "Balanced PID with 2-level/cycle actuator command slew and PV derivative.";
            case 3 -> "Aggressive PID with faster 3-level/cycle actuator command slew.";
            default -> "Unknown tuning preset.";
        };
    }

    private static String metricTicks(int ticks) {
        return ticks > 0 ? ticks + "t" : "—";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String diagnosisLabel(PneumaticClosedLoopWitness.Diagnosis diagnosis) {
        return switch (diagnosis) {
            case NO_WITNESS -> "NO WITNESS";
            case COLLECTING_EVIDENCE -> "COLLECTING EVIDENCE";
            case NOMINAL -> "NOMINAL";
            case NO_SUPPLY -> "NO SUPPLY • check source / isolation";
            case RESTRICTION -> "RESTRICTION • check valve / path command";
            case LOW_ACTUATOR_PRESSURE -> "LOW ACTUATOR PRESSURE";
            case STALLED -> "ACTUATOR STALL • pressure path not primary suspect";
        };
    }

    private static int diagnosisColor(PneumaticClosedLoopWitness.Diagnosis diagnosis) {
        return switch (diagnosis) {
            case NOMINAL -> GOOD;
            case COLLECTING_EVIDENCE, LOW_ACTUATOR_PRESSURE -> WARN;
            case NO_SUPPLY, RESTRICTION, STALLED -> BAD;
            case NO_WITNESS -> MUTED;
        };
    }

    private static int statusColor(CommissioningStatus status) {
        return switch (status) {
            case PASS -> GOOD;
            case MARGINAL, RUNNING -> WARN;
            case FAIL -> BAD;
            case IDLE, UNAVAILABLE -> MUTED;
        };
    }

    private static int acceptanceColor(EngineeringAcceptanceStatus status) {
        return switch (status) {
            case PASS -> GOOD;
            case MARGINAL -> WARN;
            case FAIL -> BAD;
            case NOT_READY -> MUTED;
        };
    }

    private static int comparisonColor(AcceptanceEvidenceTrend trend) {
        return switch (trend) {
            case IMPROVED -> GOOD;
            case SAME -> INFO;
            case REGRESSED -> BAD;
            case INCOMPARABLE -> WARN;
        };
    }
}
