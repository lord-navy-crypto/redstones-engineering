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
    protected int virtualContentWidth(Section section) {
        return section == Section.CONFIGURE || section == Section.HISTORY ? 1080 : 960;
    }

    @Override
    protected int virtualContentHeight(Section section) {
        return switch (section) {
            case CONFIGURE -> 780;
            case HISTORY -> 700;
            case DIAGNOSTICS -> 620;
            default -> 560;
        };
    }

    @Override
    protected void addDeviceWidgets() {
        int pairGap = 12;
        int pairWidth = Math.min(190, Math.max(132, (imageWidth - 72 - pairGap) / 2));
        int pairTotal = pairWidth * 2 + pairGap;
        int pairX = leftPos + (imageWidth - pairTotal) / 2;

        int tuningY = topPos + 98;
        addConfigureWidget(Button.builder(
                Component.literal("Cycle tuning preset ▶"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_TUNING_NEXT)
        ).bounds(leftPos + (imageWidth - Math.min(320, pairTotal)) / 2, tuningY, Math.min(320, pairTotal), 20).build());

        int routeY = topPos + 128;
        int routeGap = 12;
        int routeWidth = Math.min(190, Math.max(132, (imageWidth - 72 - routeGap) / 2));
        int routeTotal = routeWidth * 2 + routeGap;
        int routeX = leftPos + (imageWidth - routeTotal) / 2;
        addConfigureWidget(Button.builder(Component.literal("Cycle RX ▶"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_INPUT_NEXT))
                .bounds(routeX, routeY, routeWidth, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cycle TX ▶"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_OUTPUT_NEXT))
                .bounds(routeX + routeWidth + routeGap, routeY, routeWidth, 20).build());

        int acceptanceY = topPos + 158;
        addConfigureWidget(Button.builder(Component.literal("Capture acceptance"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_CAPTURE_ACCEPTANCE))
                .bounds(pairX, acceptanceY, pairWidth, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Reset runtime + trend"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_RESET_RUNTIME_TREND))
                .bounds(pairX + pairWidth + pairGap, acceptanceY, pairWidth, 20).build());

        int trialY = topPos + 188;
        int trialGap = 10;
        int trialWidth = Math.min(145, Math.max(104, (imageWidth - 84 - trialGap * 2) / 3));
        int trialTotal = trialWidth * 3 + trialGap * 2;
        int trialX = leftPos + (imageWidth - trialTotal) / 2;
        addConfigureWidget(Button.builder(Component.literal("Trial baseline"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_TRIAL_BASELINE))
                .bounds(trialX, trialY, trialWidth, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trial candidate"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_TRIAL_CANDIDATE))
                .bounds(trialX + trialWidth + trialGap, trialY, trialWidth, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Clear trial"),
                button -> sendMenuButton(PidControllerMenu.BUTTON_TRIAL_CLEAR))
                .bounds(trialX + (trialWidth + trialGap) * 2, trialY, trialWidth, 20).build());
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
        statusBadge(graphics, "PIONEER WORKFLOW • CLOSED-LOOP COMMISSIONING TRIAL", INFO, 16, 80);
        labelValue(graphics, "Tuning preset", tuningName(menu.tuning()), 101);
        formulaCard(graphics, "e[n]=SP[n]-PV[n];  P[n]=Kp·e[n]", 117);
        formulaCard(graphics, "Σe_cand=clamp(Σe[n-1]+e[n],-180,180);  I[n]=KiDiv==0 ? 0 : Σe_cand/KiDiv", 143);
        formulaCard(graphics, "d_f[n]=d_f[n-1]+(Δe-d_f[n-1])/dSmooth;  D[n]=Kd·d_f[n]", 169);
        formulaCard(graphics, "u_raw=bias+P+I+D;  u=clamp(u_raw,0,15); saturation may hold the integral", 195);
        variableRole(graphics, "ADJUSTABLE", "preset", tuningName(menu.tuning()),
                "Kp=" + menu.kp() + " • KiDiv=" + menu.kiDiv() + " • Kd=" + menu.kd()
                        + " • dSmooth=" + menu.derivativeSmoothing() + " • Δt=" + menu.sampleTicks() + "t", 226);
        variableRole(graphics, "MEASURED", "SP / PV / e", menu.setpoint() + " / " + menu.processValue() + " / " + signed(menu.error()),
                "redstone command / feedback / control error", 248);
        variableRole(graphics, "SOLVER", "Σe / d_f", menu.integralState() + " / " + menu.derivativeState(),
                "retained integral / filtered derivative state", 270);
        variableRole(graphics, "DERIVED", "P / I / D", menu.pTerm() + " / " + menu.iTerm() + " / " + menu.dTerm(),
                "latest controller terms", 292);
        variableRole(graphics, "DERIVED", "u_raw → u", menu.unsaturatedOutput() + " → " + menu.controlOutput(),
                menu.antiWindupHolding() ? "SATURATED • integral hold (anti-windup)" : "bounded 0..15", 314);

        String baseline = menu.trialBaselineSequence() > 0 ? "#" + menu.trialBaselineSequence() : "NONE";
        String candidate = menu.trialCandidateSequence() > 0 ? "#" + menu.trialCandidateSequence() : "NONE";
        labelValue(graphics, "Trial baseline / candidate", baseline + " / " + candidate, 344);

        AcceptanceEvidenceTrend trial = menu.trialTrend();
        String verdict = trial == null
                ? (menu.trialBaselineSequence() > 0 ? "BASELINE READY • settle, then candidate" : "START WITH BASELINE")
                : trial.name() + " • " + (menu.trialRobust() ? "ROBUST" : "CHECK");
        statusLine(graphics, "Trial verdict", verdict,
                trial == null ? INFO : (menu.trialRobust() ? GOOD : comparisonColor(trial)), 366);
        safeText(graphics,
                "Captures require settled PASS / MARGINAL / FAIL evidence; detailed deltas are shown on Log.",
                24, 394, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Controller",
                menu.controllerStatus().name() + " • score " + menu.controllerScore(),
                statusColor(menu.controllerStatus()), 80);
        labelValue(graphics, "Rise / settle",
                metricTicks(menu.rise90Ticks()) + " / " + metricTicks(menu.settlingTicks()), 96);
        labelValue(graphics, "Overshoot / saturation",
                menu.overshoot() + " / " + menu.saturationEvents(), 112);

        if (!menu.plantDetected()) {
            statusLine(graphics, "Plant witness", "NONE • explicit cylinder feedback not detected", MUTED, 132);
            safeText(graphics,
                    "Generic PID commissioning remains controller-only until PROCESS VALUE is directly wired to a formal cylinder FEEDBACK port.",
                    16, 151, MUTED);
            statusLine(graphics, "System verdict",
                    menu.status().name() + " • score " + menu.score(), statusColor(menu.status()), 184);
            statusLine(graphics, "Inhibit",
                    menu.inhibited() ? "ACTIVE • OUTPUT FORCED LOW" : "CLEAR",
                    menu.inhibited() ? BAD : GOOD, 200);
            return;
        }

        String plantState = menu.plantReady() ? menu.plantStatus().name() : "WARMING";
        statusLine(graphics, "Pneumatic plant",
                plantState + " • penalty " + menu.plantPenalty() + " • n=" + menu.plantSamples(),
                menu.plantReady() ? statusColor(menu.plantStatus()) : WARN, 132);
        labelValue(graphics, "Position / target / stall",
                menu.plantPosition() + " / " + menu.plantTarget() + " / " + menu.plantStallTicks() + "t", 148);
        labelValue(graphics, "Actuator / supply pressure", menu.plantPressure() + " / " + menu.plantSupply(), 164);
        labelValue(graphics, "Loss obs / line / restrict",
                menu.plantObservedLoss() + " / " + menu.plantLineLoss() + " / " + menu.plantRestrictionLoss(), 180);
        statusLine(graphics, "Likely cause",
                diagnosisLabel(menu.plantDiagnosis()), diagnosisColor(menu.plantDiagnosis()), 196);
        statusLine(graphics, "System verdict",
                menu.status().name() + " • score " + menu.score(), statusColor(menu.status()), 212);
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

        AcceptanceEvidenceTrend trial = menu.trialTrend();
        String trialIds = "B=" + (menu.trialBaselineSequence() > 0 ? "#" + menu.trialBaselineSequence() : "—")
                + " • C=" + (menu.trialCandidateSequence() > 0 ? "#" + menu.trialCandidateSequence() : "—");
        statusBadge(graphics, "TRIAL " + trialIds, trial == null ? MUTED : (menu.trialRobust() ? GOOD : WARN), 16, 220);
        if (trial == null) {
            safeText(graphics, "Explicit trial comparison appears after Baseline and Candidate are both captured.", 144, 223, MUTED);
        } else {
            safeText(graphics,
                    trial.name() + " • " + (menu.trialRobust() ? "ROBUST" : "CHECK")
                            + " • Δscore " + signed(menu.trialScoreDelta())
                            + " • Δsettle " + signed(menu.trialSettlingDelta()) + "t"
                            + " • Δovershoot " + signed(menu.trialOvershootDelta())
                            + " • Δsat " + signed(menu.trialSaturationDelta())
                            + " • Δissues " + signed(menu.trialTopologyIssueDelta()),
                    16, 242, menu.trialRobust() ? GOOD : comparisonColor(trial));
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
            case 0 -> "Gentle proportional-only response for simple, stable plants.";
            case 1 -> "Adds slow integral correction to remove persistent steady-state error.";
            case 2 -> "Balanced P + I + D preset for general closed-loop commissioning.";
            case 3 -> "Higher proportional/derivative action for faster, more demanding plants.";
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
