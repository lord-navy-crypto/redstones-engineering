package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.core.port.PortQuality;
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
        int y = topPos + 112;
        int w = 88;
        int gap = 6;
        addConfigureWidget(Button.builder(Component.literal("Toggle mode"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_MODE_TOGGLE)).bounds(x, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Calibration −"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_CALIBRATION_DECREASE)).bounds(x + w + gap, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Calibration +"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_CALIBRATION_INCREASE)).bounds(x + (w + gap) * 2, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Reset statistics"),
                b -> sendMenuButton(SignalAnalyzerMenu.BUTTON_RESET_HISTORY)).bounds(x, y + 26, w * 3 + gap * 2, 20).build());
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
        PortQuality quality = liveQuality();
        statusBadge(graphics,
                evidenceIssue(quality) ? modeName() + " • " + quality.name() : modeName(),
                qualityColor(quality), 16, 80);
        labelValue(graphics, "Raw measurement", menu.raw() + " / 15 • " + quality.name(), 101);
        labelValue(graphics, "Calibrated display", menu.calibrated() + " / 15", 116);
        labelValue(graphics, "Calibration", signed(menu.calibrationOffset()), 131);
        labelValue(graphics, "World output", menu.mode() == SignalAnalyzerBlock.INLINE ? menu.output() + " / 15 RAW" : "DISCONNECTED", 146);
        graphics.drawString(font, "ROLLING WINDOW", 16, 164, MUTED, false);
        EngineeringPlot.analogFrame(graphics, 98, 160, 200, 22);
        plotTrace(graphics, 100, 162, 196, 18, INFO);
        InstrumentDiagnostics.Summary summary = summary();
        safeText(graphics, "avg=" + decimal100(menu.average100()) + "  p2p=" + menu.peakToPeak()
                + "  sync=" + InstrumentDiagnostics.freshnessLabel(menu.sampleAgeTicks()), 16, 187, TEXT);
        if (summary.invalidSamples() > 0) {
            safeText(graphics, "coverage=" + summary.coveragePercent() + "%", 244, 187, WARN);
        }
    }

    private void renderPorts(GuiGraphics graphics) {
        PortQuality quality = liveQuality();
        statusLine(graphics, "TEST / facing",
                "MEASUREMENT INPUT • 0..15 • " + quality.name(), qualityColor(quality), 82);
        if (menu.mode() == SignalAnalyzerBlock.INLINE) {
            statusLine(graphics, "Opposite face",
                    "RAW PASS-THROUGH • " + quality.name(), qualityColor(quality), 103);
        } else {
            statusLine(graphics, "Opposite face", "NO OUTPUT IN TAP MODE", MUTED, 103);
        }
        sectionRule(graphics, 125);
        statusLine(graphics, "Calibration", "DISPLAY ONLY • NEVER CHANGES INLINE OUT", INFO, 136);
        statusLine(graphics, "TAP mode", "NON-INVASIVE", INFO, 157);
        statusLine(graphics, "INLINE mode", "EXPLICIT TWO-PORT BOUNDARY", GOOD, 178);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Mode", modeName(), 80);
        labelValue(graphics, "Calibration offset", signed(menu.calibrationOffset()), 95);
        safeText(graphics,
                menu.mode() == SignalAnalyzerBlock.TAP
                        ? "TAP observes the TEST side without creating a redstone electrical path."
                        : "INLINE reads TEST and reproduces the RAW sample on the opposite face.",
                16, 162, TEXT);
        safeText(graphics, "Calibration changes only the displayed engineering reading; it never repairs source PortQuality.", 16, 177, MUTED);
        safeText(graphics, "Capture statistics and freshness are synchronized readback; bad live evidence becomes a history gap, not a fabricated value.", 16, 192, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        InstrumentDiagnostics.Summary summary = summary();
        PortQuality quality = liveQuality();
        statusLine(graphics, "Synchronization",
                quality.name() + " • " + InstrumentDiagnostics.freshnessLabel(menu.sampleAgeTicks()) + " • age "
                        + (menu.sampleAgeTicks() < 0 ? "—" : menu.sampleAgeTicks() + "t"),
                qualityColor(quality), 78);
        statusLine(graphics, "Window diagnosis", InstrumentDiagnostics.analogDiagnosis(summary), diagnosisColor(summary), 98);
        labelValue(graphics, "Window min / max", summary.validSamples() == 0 ? "—" : summary.minimum() + " / " + summary.maximum(), 118);
        labelValue(graphics, "Window avg / span", summary.validSamples() == 0 ? "—" : decimal100(summary.average100()) + " / " + summary.span(), 134);
        labelValue(graphics, "Evidence coverage", summary.coveragePercent() + "% • " + summary.validSamples() + " valid", 150);
        labelValue(graphics, "Lifetime min / max", menu.lifeMin() + " / " + menu.lifeMax(), 166);
        labelValue(graphics, "Changes / edges", menu.changes() + " • ↑" + menu.rising() + " ↓" + menu.falling(), 182);
        labelValue(graphics, "Last / max Δ", menu.lastDelta() + " / " + menu.maxDelta(), 198);
        safeText(graphics, "Stable " + menu.stableAgeTicks() + "t • variation=" + stabilityClass()
                + " • diagnosis uses synchronized retained samples only.", 16, 219, MUTED);
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
                "samples=" + menu.totalSamples() + "  coverage=" + summary.coveragePercent() + "%  mode switches=" + menu.modeSwitches()
                        + "  calibration switches=" + menu.calibrationSwitches() + "  μ=rounded mean",
                16, 188, MUTED);
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

    private PortQuality liveQuality() {
        PortQuality[] values = PortQuality.values();
        return values[Math.max(0, Math.min(values.length - 1, menu.liveQualityOrdinal()))];
    }

    private static boolean evidenceIssue(PortQuality quality) {
        return quality != PortQuality.VALID && quality != PortQuality.SATURATED;
    }

    private int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> menu.mode() == SignalAnalyzerBlock.TAP ? INFO : GOOD;
            case SATURATED, NO_SIGNAL, STALE -> WARN;
            case FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> BAD;
        };
    }

    private int freshnessColor() {
        return switch (InstrumentDiagnostics.freshnessSeverity(menu.sampleAgeTicks())) {
            case 0 -> GOOD;
            case 1 -> INFO;
            default -> WARN;
        };
    }

    private int diagnosisColor(InstrumentDiagnostics.Summary summary) {
        if (summary.validSamples() == 0 || summary.coveragePercent() < 75) return WARN;
        return summary.span() >= 8 ? WARN : summary.span() >= 3 ? INFO : GOOD;
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
