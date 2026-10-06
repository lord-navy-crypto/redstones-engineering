package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.diagnostics.SignalCalibrationTrialComparison;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Metrology-focused analyzer panel with rolling history, synchronization health and explicit TAP/INLINE semantics. */
public final class SignalAnalyzerScreen extends EngineeringScreen<SignalAnalyzerMenu> {
    public SignalAnalyzerScreen(SignalAnalyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int x = leftPos + 16;
        int y = topPos + imageHeight - 116;
        int w = 88;
        int gap = 6;

        addConfigureWidget(Button.builder(Component.literal("Toggle mode"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_MODE_TOGGLE)).bounds(x, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Calibration −"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_CALIBRATION_DECREASE)).bounds(x + w + gap, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Calibration +"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_CALIBRATION_INCREASE)).bounds(x + (w + gap) * 2, y, w, 20).build());

        addConfigureWidget(Button.builder(Component.literal("Reference −"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_REFERENCE_DECREASE)).bounds(x, y + 26, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Reference +"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_REFERENCE_INCREASE)).bounds(x + w + gap, y + 26, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Reset statistics"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_RESET_HISTORY)).bounds(x + (w + gap) * 2, y + 26, w, 20).build());

        addConfigureWidget(Button.builder(Component.literal("Trial baseline"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_TRIAL_BASELINE)).bounds(x, y + 52, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trial candidate"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_TRIAL_CANDIDATE)).bounds(x + w + gap, y + 52, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Clear trial"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_TRIAL_CLEAR)).bounds(x + (w + gap) * 2, y + 52, w, 20).build());
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
        statusBadge(graphics, modeName(), menu.mode() == SignalAnalyzerBlock.TAP ? INFO : GOOD, 16, 80);
        labelValue(graphics, "Raw measurement", menu.raw() + " / 15", 101);
        labelValue(graphics, "Calibrated display", menu.calibrated() + " / 15", 116);
        labelValue(graphics, "Calibration / reference", signed(menu.calibrationOffset()) + " / " + menu.reference(), 131);
        labelValue(graphics, "Measurement evidence", menu.measurementQuality().name() + " • coverage " + menu.coveragePercent() + "%", 146);
        graphics.drawString(font, "ROLLING WINDOW", 16, 164, MUTED, false);
        EngineeringPlot.analogFrame(graphics, 98, 160, 200, 22);
        plotTrace(graphics, 100, 162, 196, 18, INFO);
        InstrumentDiagnostics.Summary summary = summary();
        safeText(graphics, "avg=" + decimal100(menu.average100()) + "  p2p=" + menu.peakToPeak()
                + "  sync=" + InstrumentDiagnostics.freshnessLabel(menu.sampleAgeTicks()), 16, 187, TEXT);
        if (menu.validWindowCount() < menu.windowCount()) {
            safeText(graphics, "measurement coverage=" + menu.validWindowCount() + "/" + menu.windowCount(), 218, 187, WARN);
        }
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "TEST / facing", "MEASUREMENT INPUT • 0..15", GOOD, 82);
        if (menu.mode() == SignalAnalyzerBlock.INLINE) {
            statusLine(graphics, "Opposite face", "RAW PASS-THROUGH OUTPUT • 0..15", GOOD, 103);
        } else {
            statusLine(graphics, "Opposite face", "NO OUTPUT IN TAP MODE", MUTED, 103);
        }
        sectionRule(graphics, 125);
        statusLine(graphics, "Calibration", "DISPLAY ONLY • NEVER CHANGES INLINE OUT", INFO, 136);
        statusLine(graphics, "TAP mode", "NON-INVASIVE", INFO, 157);
        statusLine(graphics, "INLINE mode", "EXPLICIT TWO-PORT BOUNDARY", GOOD, 178);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics,"PIONEER WORKFLOW • INTERNAL REFERENCE CALIBRATION TRIAL",INFO,16,80);
        formulaCard(graphics,"e_ref = mean(clamp(x_raw + b_cal,0,15)) - x_ref",105);
        variableRole(graphics,"MEASURED","x_raw",Integer.toString(menu.raw()),"Redstone",132);
        variableRole(graphics,"ADJUSTABLE","b_cal",signed(menu.calibrationOffset()),"display offset",148);
        variableRole(graphics,"REFERENCE","x_ref",Integer.toString(menu.reference()),"internal 0..15",164);

        String b = menu.trialBaselineSequence() > 0 ? "#" + menu.trialBaselineSequence() : "—";
        String d = menu.trialCandidateSequence() > 0 ? "#" + menu.trialCandidateSequence() : "—";
        SignalCalibrationTrialComparison.Trend trend = menu.trialTrend();
        String trial = trend == null ? "B=" + b + " • C=" + d : "B=" + b + " • C=" + d + " • " + trend.name();
        statusLine(graphics,"Trial",trial,trialColor(trend),178);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        InstrumentDiagnostics.Summary summary = summary();
        statusLine(graphics, "Synchronization",
                InstrumentDiagnostics.freshnessLabel(menu.sampleAgeTicks()) + " • age "
                        + (menu.sampleAgeTicks() < 0 ? "—" : menu.sampleAgeTicks() + "t"),
                freshnessColor(), 78);
        statusLine(graphics, "Window diagnosis", InstrumentDiagnostics.analogDiagnosis(summary), diagnosisColor(summary), 98);
        labelValue(graphics, "Measurement quality", menu.measurementQuality().name(), 114);
        labelValue(graphics, "Window min / max", summary.validSamples() == 0 ? "—" : summary.minimum() + " / " + summary.maximum(), 130);
        labelValue(graphics, "Window avg / span", summary.validSamples() == 0 ? "—" : decimal100(summary.average100()) + " / " + summary.span(), 146);
        labelValue(graphics, "Evidence coverage", menu.coveragePercent() + "% • " + menu.validWindowCount() + "/" + menu.windowCount() + " valid", 162);
        labelValue(graphics, "Lifetime min / max", menu.lifeMin() + " / " + menu.lifeMax(), 178);
        labelValue(graphics, "Changes / edges", menu.changes() + " • ↑" + menu.rising() + " ↓" + menu.falling(), 194);
        labelValue(graphics, "Last / max Δ", menu.lastDelta() + " / " + menu.maxDelta(), 210);
        safeText(graphics, "Stable " + menu.stableAgeTicks() + "t • variation=" + stabilityClass()
                + " • a valid numeric zero remains distinct from an empty aperture.", 16, 231, MUTED);
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = 38;
        int y = 82;
        int width = 260;
        int height = 84;
        InstrumentDiagnostics.Summary summary = summary();

        graphics.drawString(font, "15", 13, 84, MUTED, false);
        graphics.drawString(font, "0", 18, 162, MUTED, false);
        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        plotTrace(graphics, x + 4, y + 4, width - 8, height - 8, INFO);
        if (menu.windowCount() > 0) {
            int meanRounded = Math.max(0, Math.min(15, Math.round(menu.average100() / 100.0f)));
            EngineeringPlot.horizontalMarker(graphics, meanRounded, 0, 15,
                    x + 4, y + 4, width - 8, height - 8, GOOD);
            graphics.drawString(font, "μ", 287, 86, GOOD, false);
        }
        if (summary.validSamples() > 1 && summary.span() > 0) {
            EngineeringPlot.horizontalMarker(graphics, summary.minimum(), 0, 15,
                    x + 4, y + 4, width - 8, height - 8, MUTED);
            EngineeringPlot.horizontalMarker(graphics, summary.maximum(), 0, 15,
                    x + 4, y + 4, width - 8, height - 8, WARN);
        }
        safeText(graphics,
                "window=" + menu.windowCount() + "/16  avg=" + decimal100(menu.average100())
                        + "  p2p=" + menu.peakToPeak() + "  meanStep=" + decimal100(menu.meanStep100()),
                16, 175, TEXT);
        safeText(graphics,
                "samples=" + menu.totalSamples() + "  measurement coverage=" + menu.validWindowCount() + "/" + menu.windowCount()
                        + "  mode switches=" + menu.modeSwitches()
                        + "  calibration switches=" + menu.calibrationSwitches()
                        + "  reference switches=" + menu.referenceSwitches(),
                16, 188, MUTED);

        SignalCalibrationTrialComparison.Trend trend = menu.trialTrend();
        String b = menu.trialBaselineSequence() > 0 ? "#" + menu.trialBaselineSequence() : "—";
        String d = menu.trialCandidateSequence() > 0 ? "#" + menu.trialCandidateSequence() : "—";
        statusBadge(graphics, "CAL TRIAL B=" + b + " • C=" + d, trend == null ? MUTED : trialColor(trend), 16, 208);
        if (trend == null) {
            safeText(graphics,
                    menu.trialBaselineSequence() > 0
                            ? "Baseline frozen. Keep reference/mode/measurement face fixed, collect a fresh 16/16 window, then capture Candidate."
                            : "Capture requires fresh VALID evidence and a complete 16/16 measurement window.",
                    16, 230, INFO);
        } else {
            safeText(graphics,
                    trend.name()
                            + " • Δ|error|=" + decimal100(menu.trialErrorDelta100())
                            + " • Δclip=" + signed(menu.trialClippingDelta())
                            + " • Δspan=" + signed(menu.trialSpanDelta())
                            + " • ΔmeanStep=" + decimal100(menu.trialMeanStepDelta100())
                            + " • Δcal=" + signed(menu.trialCalibrationDelta()),
                    16, 230, trialColor(trend));
        }
        safeText(graphics,
                "Internal RSE reference comparison only; this does not establish external metrological traceability.",
                16, 247, MUTED);
    }

    private void plotTrace(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        EngineeringPlot.analogTrace(
                graphics,
                SignalAnalyzerBlock.DISPLAY_SAMPLES,
                menu::sample,
                0,
                15,
                x,
                y,
                width,
                height,
                color
        );
    }

    private InstrumentDiagnostics.Summary summary() {
        return InstrumentDiagnostics.summarize(
                SignalAnalyzerBlock.DISPLAY_SAMPLES,
                menu::sample,
                0,
                15
        );
    }

    private int freshnessColor() {
        return switch (InstrumentDiagnostics.freshnessSeverity(menu.sampleAgeTicks())) {
            case 0 -> GOOD;
            case 1 -> INFO;
            default -> WARN;
        };
    }

    private int diagnosisColor(InstrumentDiagnostics.Summary summary) {
        if (menu.measurementQuality() != dev.redstoneengineering.core.port.PortQuality.VALID) return WARN;
        if (summary.validSamples() == 0 || menu.coveragePercent() < 75) return WARN;
        return summary.span() >= 8 ? WARN : summary.span() >= 3 ? INFO : GOOD;
    }

    private int trialColor(SignalCalibrationTrialComparison.Trend trend) {
        if (trend == null) return INFO;
        return switch (trend) {
            case IMPROVED -> GOOD;
            case SAME -> INFO;
            case REGRESSED -> BAD;
            case INCOMPARABLE -> WARN;
        };
    }

    private String modeName() {
        return menu.mode() == SignalAnalyzerBlock.INLINE ? "INLINE" : "TAP";
    }

    private String stabilityClass() {
        if (menu.windowCount() < 4) return "WARMUP";
        if (menu.peakToPeak() == 0 && menu.meanStep100() == 0) return "STEADY";
        if (menu.peakToPeak() <= 1 && menu.meanStep100() <= 50) return "STABLE";
        if (menu.peakToPeak() <= 5 && menu.meanStep100() <= 200) return "DYNAMIC";
        return "HIGH VARIATION";
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String decimal100(int value) {
        int abs = Math.abs(value);
        return (value < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
