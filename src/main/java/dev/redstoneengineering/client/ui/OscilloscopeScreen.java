package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Two-channel scope UI backed by the authoritative 32-sample capture engine. */
public final class OscilloscopeScreen extends EngineeringScreen<OscilloscopeMenu> {
    public OscilloscopeScreen(OscilloscopeMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int x = leftPos + 16, y = topPos + 105, w = 88, gap = 6;
        addConfigureWidget(Button.builder(Component.literal("Arm"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_ARM)).bounds(x, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger mode"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_TRIGGER_MODE)).bounds(x + w + gap, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger source"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_TRIGGER_CHANNEL)).bounds(x + (w + gap) * 2, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Level +"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_TRIGGER_LEVEL)).bounds(x, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor A +"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_CURSOR_A)).bounds(x + w + gap, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor B +"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_CURSOR_B)).bounds(x + (w + gap) * 2, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Clear capture"), b -> sendMenuButton(OscilloscopeMenu.BUTTON_CLEAR)).bounds(x, y + 50, w * 3 + gap * 2, 20).build());
    }

    @Override protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) { case OVERVIEW -> renderOverview(graphics); case PORTS -> renderPorts(graphics); case CONFIGURE -> renderConfigure(graphics); case DIAGNOSTICS -> renderDiagnostics(graphics); case HISTORY -> renderHistory(graphics); }
    }

    private void renderOverview(GuiGraphics graphics) {
        statusBadge(graphics, captureState(), captureColor(), 16, 80);
        statusBadge(graphics, "EVIDENCE " + evidenceConfidence() + "%", evidenceColor(), 205, 80);
        labelValue(graphics, "Capture", menu.sampleCount() + "/32 samples", 99);
        labelValue(graphics, "Trigger", triggerText(), 114);
        labelValue(graphics, "CH A / CH B", value(menu.current(0)) + " / " + value(menu.current(1)), 129);
        graphics.drawString(font, "CH A", 16, 148, INFO, false); miniTrace(graphics, 0, 50, 145, 240, 18, INFO);
        graphics.drawString(font, "CH B", 16, 170, GOOD, false); miniTrace(graphics, 1, 50, 167, 240, 18, GOOD);
        safeText(graphics, relationshipDiagnosis(), 16, 193, relationshipColor());
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "Instrument bus", networkIntegrity(), networkColor(), 82);
        statusLine(graphics, "Interference", interferenceSummary(), interferenceColor(), 103);
        statusLine(graphics, "Shielding", shieldingSummary(), shieldingColor(), 124);
        statusLine(graphics, "CH A / CH B", probeState(0) + " / " + probeState(1), probePairColor(), 145);
        labelValue(graphics, "Cable exposure", menu.exposedCableNodes() + "/" + menu.cableNodes() + " nodes • " + menu.interferenceExposure() + "%", 168);
        labelValue(graphics, "Exposed S/U", menu.shieldedExposedNodes() + " / " + menu.unshieldedExposedNodes(), 185);
        safeText(graphics, "Shielding changes deterministic interference confidence; samples are never randomly perturbed.", 16, 207, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Trigger mode", modeName(menu.triggerMode()), 80);
        labelValue(graphics, "Trigger source", "CH " + (menu.triggerChannel() == 0 ? "A" : "B"), 95);
        labelValue(graphics, "Trigger level", menu.triggerLevel() + " / 15", 110);
        labelValue(graphics, "Cursors", "A=" + menu.cursorA() + " B=" + menu.cursorB(), 125);
        labelValue(graphics, "Cursor Δ", Math.abs(menu.cursorB() - menu.cursorA()) + " samples / " + Math.abs(menu.cursorB() - menu.cursorA()) * OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS + "t", 140);
        safeText(graphics, "All controls are validated on the logical server.", 16, 178, MUTED);
        safeText(graphics, "Trigger/cursor interpretation uses synchronized retained samples only.", 16, 194, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        channelDiagnostics(graphics, 0, "A", 78);
        sectionRule(graphics, 120);
        channelDiagnostics(graphics, 1, "B", 130);
        statusLine(graphics, "Network", networkIntegrity(), networkColor(), 174);
        statusLine(graphics, "Interference", interferenceSummary(), interferenceColor(), 192);
        safeText(graphics, "Confidence " + evidenceConfidence() + "% • " + evidenceClass() + " • shield=" + shieldingSummary(), 16, 211, evidenceColor());
        safeText(graphics, nextAction(), 16, 228, evidenceColor());
    }

    private void channelDiagnostics(GuiGraphics graphics, int channel, String name, int y) {
        graphics.drawString(font, "CH " + name, 16, y, channel == 0 ? INFO : GOOD, false);
        safeText(graphics, "coverage=" + menu.coverage(channel) + "%  min/max/p2p=" + value(menu.minimum(channel)) + "/" + value(menu.maximum(channel)) + "/" + value(menu.peakToPeak(channel)), 52, y, TEXT);
        safeText(graphics, "avg=" + decimal100(menu.average100(channel)) + "  meanStep=" + decimal100(menu.meanStep100(channel)) + "  period≈" + tickValue(menu.periodTicks(channel)) + "  " + channelDiagnosis(channel), 52, y + 16, MUTED);
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = 38, y = 82, width = 260, height = 84, inset = 3, samples = OscilloscopeBlockEntity.DISPLAY_SAMPLES;
        graphics.drawString(font, "0", 17, 165, MUTED, false); graphics.drawString(font, "15", 12, 85, MUTED, false);
        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        EngineeringPlot.horizontalMarker(graphics, menu.triggerLevel(), 0, 15, x + inset, y + inset, width - inset * 2, height - inset * 2, WARN);
        plotChannel(graphics, 0, x + inset, y + inset, width - inset * 2, height - inset * 2, INFO);
        plotChannel(graphics, 1, x + inset, y + inset, width - inset * 2, height - inset * 2, GOOD);
        EngineeringPlot.verticalMarker(graphics, menu.cursorA(), samples, x, y, width, height, WARN);
        EngineeringPlot.verticalMarker(graphics, menu.cursorB(), samples, x, y, width, height, 0xFFE879F9);
        graphics.drawString(font, "A", 269, 86, INFO, false); graphics.drawString(font, "B", 280, 86, GOOD, false); graphics.drawString(font, "T", 291, 86, WARN, false);
        safeText(graphics, "A/B traces • T=trigger level • synchronized 0..15 samples", 16, 173, MUTED);
        safeText(graphics, "Cursor Δ=" + cursorDeltaSamples() + " samples / " + cursorDeltaTicks() + "t • ΔV A/B=" + cursorDeltaValue(0) + "/" + cursorDeltaValue(1), 16, 187, TEXT);
        safeText(graphics, "Capture confidence=" + evidenceConfidence() + "% • interference=" + menu.interferenceConfidence() + "% • shielding=" + menu.shieldingCoverage() + "%", 16, 202, MUTED);
    }

    private void miniTrace(GuiGraphics graphics, int channel, int x, int y, int width, int height, int color) { EngineeringPlot.analogFrame(graphics, x, y, width, height); plotChannel(graphics, channel, x + 2, y + 2, width - 4, height - 4, color); }
    private void plotChannel(GuiGraphics graphics, int channel, int x, int y, int width, int height, int color) { EngineeringPlot.analogTrace(graphics, OscilloscopeBlockEntity.DISPLAY_SAMPLES, slot -> menu.displaySample(channel, slot), 0, 15, x, y, width, height, color); }

    private int evidenceConfidence() {
        int capture = Math.min(menu.coverage(0), menu.coverage(1));
        if (!menu.bounded()) capture = Math.min(capture, 25);
        if (menu.duplicateChannels() > 0) capture = Math.min(capture, 35);
        if (menu.probeCount(0) != 1 || menu.probeCount(1) != 1) capture = Math.min(capture, 50);
        capture = Math.min(capture, menu.interferenceConfidence());
        return Math.max(0, Math.min(100, capture));
    }

    private String evidenceClass() { int confidence = evidenceConfidence(); if (confidence >= 90) return "STRONG"; if (confidence >= 70) return "USABLE"; if (confidence >= 40) return "MARGINAL"; return "INSUFFICIENT"; }
    private int evidenceColor() { int confidence = evidenceConfidence(); if (confidence >= 90) return GOOD; if (confidence >= 70) return INFO; return WARN; }

    private String channelDiagnosis(int channel) {
        if (menu.probeCount(channel) == 0) return "NO PROBE";
        if (menu.probeCount(channel) > 1) return "AMBIGUOUS";
        if (menu.coverage(channel) < 70) return "LOW COVERAGE";
        if (menu.peakToPeak(channel) == 0) return "STEADY";
        if (menu.periodTicks(channel) > 0) return "PERIODIC";
        if (menu.meanStep100(channel) >= 300) return "FAST TRANSIENT";
        return "DYNAMIC";
    }

    private String relationshipDiagnosis() {
        if (evidenceConfidence() < 70) return "CHANNEL RELATIONSHIP • insufficient synchronized evidence";
        int avgDelta = Math.abs(menu.average100(0) - menu.average100(1));
        int p2pDelta = Math.abs(menu.peakToPeak(0) - menu.peakToPeak(1));
        int periodA = menu.periodTicks(0), periodB = menu.periodTicks(1);
        if (periodA > 0 && periodB > 0 && Math.abs(periodA - periodB) <= OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS && avgDelta <= 100 && p2pDelta <= 1) return "CHANNEL RELATIONSHIP • closely tracking";
        if (periodA > 0 && periodB > 0 && Math.abs(periodA - periodB) > OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS * 2) return "CHANNEL RELATIONSHIP • timing mismatch";
        if (avgDelta >= 400) return "CHANNEL RELATIONSHIP • large level offset";
        if (p2pDelta >= 5) return "CHANNEL RELATIONSHIP • amplitude mismatch";
        return "CHANNEL RELATIONSHIP • distinct but comparable";
    }

    private int relationshipColor() { String diagnosis = relationshipDiagnosis(); if (diagnosis.contains("insufficient") || diagnosis.contains("mismatch") || diagnosis.contains("large")) return WARN; return diagnosis.contains("closely") ? GOOD : INFO; }

    private String shieldingSummary() {
        if (!menu.bounded()) return "UNKNOWN • scan truncated";
        if (menu.cableNodes() == 0) return "DIRECT / NO CABLE";
        if (menu.unshieldedCableNodes() == 0) return "FULL • 100%";
        if (menu.shieldedCableNodes() == 0) return "NONE • 0%";
        return "MIXED • " + menu.shieldingCoverage() + "%";
    }

    private String interferenceSummary() {
        if (!menu.bounded()) return "UNKNOWN • scan truncated";
        if (menu.cableNodes() == 0) return "DIRECT • no cable exposure";
        if (menu.exposedCableNodes() == 0) return "CLEAR • confidence 100%";
        if (menu.unshieldedExposedNodes() == 0) return "EXPOSED / SHIELDED • confidence " + menu.interferenceConfidence() + "%";
        if (menu.shieldedExposedNodes() == 0) return "EXPOSED / UNSHIELDED • confidence " + menu.interferenceConfidence() + "%";
        return "EXPOSED / MIXED • confidence " + menu.interferenceConfidence() + "%";
    }

    private int shieldingColor() { if (!menu.bounded()) return WARN; if (menu.cableNodes() == 0 || menu.shieldingCoverage() >= 90) return GOOD; if (menu.shieldingCoverage() >= 60) return INFO; return WARN; }
    private int interferenceColor() { if (!menu.bounded()) return WARN; if (menu.interferenceConfidence() >= 90) return GOOD; if (menu.interferenceConfidence() >= 70) return INFO; return WARN; }
    private int probePairColor() { if (menu.probeCount(0) == 1 && menu.probeCount(1) == 1) return GOOD; return menu.probeCount(0) > 1 || menu.probeCount(1) > 1 ? WARN : MUTED; }

    private String nextAction() {
        if (!menu.bounded()) return "NEXT • reduce/segment the instrument network before trusting capture timing.";
        if (menu.duplicateChannels() > 0) return "NEXT • resolve duplicate probe channel ownership before waveform comparison.";
        if (menu.probeCount(0) != 1 || menu.probeCount(1) != 1) return "NEXT • connect exactly one probe to each compared channel.";
        if (menu.unshieldedExposedNodes() > 0) return "NEXT • shield exposed instrument segments or separate them from energized Redstone/Copper routing.";
        if (menu.exposedCableNodes() > 0) return "NEXT • shielding is containing observed exposure; keep route separation if small differences matter.";
        if (evidenceConfidence() < 70) return "NEXT • acquire a longer valid capture before interpreting waveform differences.";
        if (relationshipDiagnosis().contains("timing mismatch")) return "NEXT • compare source timing/trigger alignment before changing amplitude.";
        if (relationshipDiagnosis().contains("amplitude") || relationshipDiagnosis().contains("level offset")) return "NEXT • inspect conditioning, loading or measurement reference before retuning control.";
        return "NEXT • evidence is coherent; use cursors to quantify the observed channel difference.";
    }

    private int cursorDeltaSamples() { return Math.abs(menu.cursorB() - menu.cursorA()); }
    private int cursorDeltaTicks() { return cursorDeltaSamples() * OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS; }
    private String cursorDeltaValue(int channel) { int a = menu.displaySample(channel, menu.cursorA()); int b = menu.displaySample(channel, menu.cursorB()); return a < 0 || b < 0 ? "N/A" : signed(b - a); }
    private String triggerText() { return modeName(menu.triggerMode()) + " CH " + (menu.triggerChannel() == 0 ? "A" : "B") + " @" + menu.triggerLevel(); }
    private String captureState() { return switch (menu.captureState()) { case 1 -> "ARMED"; case 2 -> "TRIGGERED"; default -> "HOLD"; }; }
    private int captureColor() { return switch (menu.captureState()) { case 1 -> INFO; case 2 -> GOOD; default -> MUTED; }; }
    private String networkIntegrity() { if (!menu.bounded()) return "TRUNCATED"; if (menu.duplicateChannels() > 0) return "AMBIGUOUS • duplicate channel"; if (menu.probeNodes() == 0) return "NO PROBES"; return "OK • bounded scan"; }
    private int networkColor() { if (!menu.bounded() || menu.duplicateChannels() > 0) return WARN; return menu.probeNodes() == 0 ? MUTED : GOOD; }
    private String probeState(int channel) { return switch (menu.probeCount(channel)) { case 0 -> "NO PROBE"; case 1 -> "CONNECTED"; default -> "AMBIGUOUS (" + menu.probeCount(channel) + ")"; }; }
    private static String modeName(int mode) { return switch (mode) { case 0 -> "FREE"; case 1 -> "RISING"; case 2 -> "FALLING"; default -> "?"; }; }
    private static String value(int value) { return value < 0 ? "N/A" : Integer.toString(value); }
    private static String tickValue(int value) { return value < 0 ? "N/A" : value + "t"; }
    private static String decimal100(int value) { if (value < 0) return "N/A"; return (value / 100) + "." + String.format("%02d", value % 100); }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
}
