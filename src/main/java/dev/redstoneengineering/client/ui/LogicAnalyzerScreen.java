package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Four-channel digital timing UI with real capture, synchronized evidence and channel diagnostics. */
public final class LogicAnalyzerScreen extends EngineeringScreen<LogicAnalyzerMenu> {
    public LogicAnalyzerScreen(LogicAnalyzerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int x = leftPos + 16, y = topPos + 104, w = 88, gap = 6;
        addConfigureWidget(Button.builder(Component.literal("Arm"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_ARM)).bounds(x, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Threshold −"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_THRESHOLD_DECREASE)).bounds(x + w + gap, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Threshold +"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE)).bounds(x + (w + gap) * 2, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger CH"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_TRIGGER_CHANNEL)).bounds(x, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger edge"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_TRIGGER_EDGE)).bounds(x + w + gap, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor A +"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_CURSOR_A)).bounds(x + (w + gap) * 2, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor B +"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_CURSOR_B)).bounds(x, y + 50, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Clear capture"), b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_CLEAR)).bounds(x + w + gap, y + 50, w * 2 + gap, 20).build());
    }

    @Override protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) { case OVERVIEW -> renderOverview(graphics); case PORTS -> renderPorts(graphics); case CONFIGURE -> renderConfigure(graphics); case DIAGNOSTICS -> renderDiagnostics(graphics); case HISTORY -> renderHistory(graphics); }
    }

    private void renderOverview(GuiGraphics graphics) {
        statusBadge(graphics, captureState(), captureColor(), 16, 80);
        statusBadge(graphics, "BUS " + busConfidence() + "%", busConfidenceColor(), 212, 80);
        labelValue(graphics, "Threshold", menu.threshold() + " / 15", 99);
        labelValue(graphics, "Trigger", "CH " + channelName(menu.triggerChannel()) + " " + edgeName(menu.triggerEdge()), 114);
        labelValue(graphics, "Capture", menu.sampleCount() + "/32 samples", 129);
        for (int channel = 0; channel < 4; channel++) {
            int lane = channel, y = 148 + lane * 11;
            graphics.drawString(font, channelName(lane), 16, y, channelColor(lane), false);
            EngineeringPlot.digitalTrace(graphics, LogicAnalyzerBlockEntity.DISPLAY_SAMPLES,
                    slot -> menu.displayState(lane, slot), 38, y - 1, 260, 9, channelColor(lane));
        }
        safeText(graphics, "capture=" + captureCoverage() + "% • interference=" + interferenceSummary(), 16, 197, MUTED);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "Instrument bus", networkIntegrity(), networkColor(), 81);
        statusLine(graphics, "Interference", interferenceSummary(), interferenceColor(), 101);
        statusLine(graphics, "Shielding", shieldingSummary(), shieldingColor(), 121);
        for (int channel = 0; channel < 4; channel++) statusLine(graphics, "CH " + channelName(channel), probeState(channel), probeColor(channel), 143 + channel * 17);
        safeText(graphics, "Exposure " + menu.exposedCableNodes() + "/" + menu.cableNodes() + " nodes • shielded/unshielded exposed "
                + menu.shieldedExposedNodes() + "/" + menu.unshieldedExposedNodes(), 16, 218, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Digital threshold", menu.threshold() + " / 15", 80);
        labelValue(graphics, "Trigger channel", "CH " + channelName(menu.triggerChannel()), 95);
        labelValue(graphics, "Trigger edge", edgeName(menu.triggerEdge()), 110);
        labelValue(graphics, "Cursors", "A=" + menu.cursorA() + " B=" + menu.cursorB(), 125);
        labelValue(graphics, "Cursor Δ", Math.abs(menu.cursorB() - menu.cursorA()) + " samples / "
                + Math.abs(menu.cursorB() - menu.cursorA()) * LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS + "t", 140);
        safeText(graphics, "Threshold and trigger controls never bypass the server capture engine.", 16, 178, MUTED);
        safeText(graphics, "Observe/Log pages analyze only the synchronized 32-sample capture buffer.", 16, 193, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Capture synchronization", captureSyncText(), captureSyncColor(), 76);
        for (int channel = 0; channel < 4; channel++) {
            InstrumentDiagnostics.Summary summary = channelSummary(channel);
            int y = 96 + channel * 27;
            graphics.drawString(font, "CH " + channelName(channel), 16, y, channelColor(channel), false);
            safeText(graphics, InstrumentDiagnostics.digitalDiagnosis(summary) + " • coverage=" + summary.coveragePercent() + "% • transition=" + summary.transitions(), 54, y, diagnosticColor(summary));
            safeText(graphics, "duty=" + menu.duty(channel) + "%  edges ↑" + menu.rising(channel) + " ↓" + menu.falling(channel) + "  longest=" + summary.longestRun(), 54, y + 12, MUTED);
        }
        statusLine(graphics, "Bus interference", interferenceSummary(), interferenceColor(), 206);
        safeText(graphics, nextAction(), 16, 226, busConfidenceColor());
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = 38, y = 80, width = 260, height = 86, samples = LogicAnalyzerBlockEntity.DISPLAY_SAMPLES;
        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        for (int channel = 0; channel < 4; channel++) {
            int lane = channel, laneY = 88 + lane * 19;
            graphics.drawString(font, channelName(lane), 16, laneY, channelColor(lane), false);
            EngineeringPlot.digitalTrace(graphics, samples, slot -> menu.displayState(lane, slot), 42, laneY, 250, 12, channelColor(lane));
        }
        EngineeringPlot.verticalMarker(graphics, menu.cursorA(), samples, 42, y, 250, height, WARN);
        EngineeringPlot.verticalMarker(graphics, menu.cursorB(), samples, 42, y, 250, height, 0xFFE879F9);
        safeText(graphics, "HIGH/LOW timing • gaps mark invalid or missing probe samples", 16, 175, MUTED);
        safeText(graphics, "Cursor Δ=" + Math.abs(menu.cursorB() - menu.cursorA()) + " samples / "
                + Math.abs(menu.cursorB() - menu.cursorA()) * LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS + "t • capture=" + captureCoverage() + "%", 16, 190, TEXT);
        safeText(graphics, "Interference confidence=" + menu.interferenceConfidence() + "% • shielding=" + menu.shieldingCoverage() + "%", 16, 207, MUTED);
    }

    private InstrumentDiagnostics.Summary channelSummary(int channel) {
        return InstrumentDiagnostics.summarize(LogicAnalyzerBlockEntity.DISPLAY_SAMPLES, slot -> menu.displayState(channel, slot), 0, 1);
    }
    private int captureCoverage() {
        int valid = 0, total = 0;
        for (int channel = 0; channel < 4; channel++) {
            InstrumentDiagnostics.Summary summary = channelSummary(channel);
            valid += summary.validSamples(); total += summary.validSamples() + summary.invalidSamples();
        }
        return total == 0 ? 0 : Math.round(valid * 100.0f / total);
    }
    private int busConfidence() {
        int confidence = Math.min(captureCoverage(), menu.interferenceConfidence());
        if (!menu.bounded()) confidence = Math.min(confidence, 25);
        if (menu.duplicateChannels() > 0) confidence = Math.min(confidence, 35);
        return Math.max(0, Math.min(100, confidence));
    }
    private int busConfidenceColor() { return busConfidence() >= 90 ? GOOD : busConfidence() >= 70 ? INFO : WARN; }
    private String captureSyncText() {
        if (!menu.bounded()) return "TRUNCATED TOPOLOGY";
        if (menu.duplicateChannels() > 0) return "AMBIGUOUS CHANNEL MAP";
        if (menu.sampleCount() == 0) return "NO CAPTURE";
        if (captureCoverage() < 75) return "PARTIAL • " + captureCoverage() + "%";
        return captureState() + " • " + captureCoverage() + "% evidence";
    }
    private int captureSyncColor() { if (!menu.bounded() || menu.duplicateChannels() > 0 || captureCoverage() < 75) return WARN; return menu.sampleCount() == 0 ? MUTED : GOOD; }
    private int diagnosticColor(InstrumentDiagnostics.Summary summary) { if (summary.validSamples() == 0 || summary.coveragePercent() < 75) return WARN; return summary.longestRun() <= 2 && summary.transitions() > 8 ? INFO : GOOD; }

    private String shieldingSummary() {
        if (!menu.bounded()) return "UNKNOWN • scan truncated";
        if (menu.cableNodes() == 0) return "DIRECT / NO CABLE";
        if (menu.unshieldedCableNodes() == 0) return "FULL • 100%";
        if (menu.shieldedCableNodes() == 0) return "NONE • 0%";
        return "MIXED • " + menu.shieldingCoverage() + "%";
    }
    private int shieldingColor() { if (!menu.bounded()) return WARN; if (menu.cableNodes() == 0 || menu.shieldingCoverage() >= 90) return GOOD; if (menu.shieldingCoverage() >= 60) return INFO; return WARN; }
    private String interferenceSummary() {
        if (!menu.bounded()) return "UNKNOWN • scan truncated";
        if (menu.cableNodes() == 0) return "DIRECT • no cable exposure";
        if (menu.exposedCableNodes() == 0) return "CLEAR • confidence 100%";
        if (menu.unshieldedExposedNodes() == 0) return "EXPOSED / SHIELDED • " + menu.interferenceConfidence() + "%";
        if (menu.shieldedExposedNodes() == 0) return "EXPOSED / UNSHIELDED • " + menu.interferenceConfidence() + "%";
        return "EXPOSED / MIXED • " + menu.interferenceConfidence() + "%";
    }
    private int interferenceColor() { if (!menu.bounded()) return WARN; if (menu.interferenceConfidence() >= 90) return GOOD; if (menu.interferenceConfidence() >= 70) return INFO; return WARN; }
    private String nextAction() {
        if (!menu.bounded()) return "NEXT • reduce/segment the instrument network before trusting timing.";
        if (menu.duplicateChannels() > 0) return "NEXT • resolve duplicate probe ownership before interpreting states.";
        if (menu.unshieldedExposedNodes() > 0) return "NEXT • shield exposed instrument segments or separate them from energized Redstone/Copper routing.";
        if (menu.exposedCableNodes() > 0) return "NEXT • shielding is containing observed exposure; preserve separation for timing-critical captures.";
        if (captureCoverage() < 75) return "NEXT • acquire a longer valid capture before interpreting timing.";
        return "NEXT • topology and interference evidence are coherent; inspect channel timing.";
    }

    private String captureState() { return switch (menu.captureState()) { case 1 -> "ARMED"; case 2 -> "TRIGGERED"; default -> "HOLD"; }; }
    private int captureColor() { return switch (menu.captureState()) { case 1 -> INFO; case 2 -> GOOD; default -> MUTED; }; }
    private String networkIntegrity() { if (!menu.bounded()) return "TRUNCATED"; if (menu.duplicateChannels() > 0) return "AMBIGUOUS • duplicate channels"; if (menu.probeNodes() == 0) return "NO PROBES"; return "OK • " + menu.validChannels() + "/" + menu.activeChannels() + " valid/active"; }
    private int networkColor() { if (!menu.bounded() || menu.duplicateChannels() > 0) return WARN; return menu.probeNodes() == 0 ? MUTED : GOOD; }
    private String probeState(int channel) { return switch (menu.probeCount(channel)) { case 0 -> "NO PROBE"; case 1 -> "CONNECTED"; default -> "AMBIGUOUS (" + menu.probeCount(channel) + ")"; }; }
    private int probeColor(int channel) { return menu.probeCount(channel) == 1 ? GOOD : menu.probeCount(channel) > 1 ? WARN : MUTED; }
    private static String channelName(int channel) { return SignalProbeBlock.channelName(channel); }
    private static String edgeName(int edge) { return edge == 2 ? "FALLING" : "RISING"; }
    private static int channelColor(int channel) { return switch (channel) { case 0 -> 0xFF66C2FF; case 1 -> 0xFF7DDB8A; case 2 -> 0xFFFFC857; default -> 0xFFE879F9; }; }
}
