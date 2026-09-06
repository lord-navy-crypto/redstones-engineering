package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Two-channel scope UI backed by the authoritative bounded capture engine. */
public final class OscilloscopeScreen extends EngineeringScreen<OscilloscopeMenu> {
    private final EngineeringChartRenderer.Series channelA = new ScopeSeries(0);
    private final EngineeringChartRenderer.Series channelB = new ScopeSeries(1);

    public OscilloscopeScreen(OscilloscopeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int x = leftPos + 16;
        int y = topPos + 105;
        int w = 88;
        int gap = 6;
        addConfigureWidget(Button.builder(Component.literal("Arm"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_ARM)).bounds(x, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger mode"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_TRIGGER_MODE)).bounds(x + w + gap, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger source"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_TRIGGER_CHANNEL)).bounds(x + (w + gap) * 2, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Level +"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_TRIGGER_LEVEL)).bounds(x, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor A +"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_CURSOR_A)).bounds(x + w + gap, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor B +"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_CURSOR_B)).bounds(x + (w + gap) * 2, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Clear capture"),
                b -> sendMenuButton(OscilloscopeMenu.BUTTON_CLEAR)).bounds(x, y + 50, w * 3 + gap * 2, 20).build());
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
        statusBadge(graphics, captureState(), captureColor(), 16, 80);
        labelValue(graphics, "Capture", menu.sampleCount() + "/32 samples", 99);
        labelValue(graphics, "Trigger", triggerText(), 114);
        labelValue(graphics, "CH A / CH B", value(menu.current(0)) + " / " + value(menu.current(1)), 129);
        graphics.drawString(font, "CH A", 16, 148, INFO, false);
        miniTrace(graphics, channelA, 50, 145, 240, 18, INFO);
        graphics.drawString(font, "CH B", 16, 170, GOOD, false);
        miniTrace(graphics, channelB, 50, 167, 240, 18, GOOD);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "Instrument bus", networkIntegrity(), networkColor(), 82);
        statusLine(graphics, "CH A probe", probeState(0), probeColor(0), 103);
        statusLine(graphics, "CH B probe", probeState(1), probeColor(1), 123);
        sectionRule(graphics, 143);
        labelValue(graphics, "Cable nodes", Integer.toString(menu.cableNodes()), 153);
        labelValue(graphics, "Probe nodes", Integer.toString(menu.probeNodes()), 168);
        labelValue(graphics, "Valid / active", menu.validChannels() + " / " + menu.activeChannels(), 183);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Trigger mode", modeName(menu.triggerMode()), 80);
        labelValue(graphics, "Trigger source", "CH " + (menu.triggerChannel() == 0 ? "A" : "B"), 95);
        labelValue(graphics, "Trigger level", menu.triggerLevel() + " / 15", 110);
        labelValue(graphics, "Cursors", "A=" + menu.cursorA() + " B=" + menu.cursorB(), 125);
        labelValue(graphics, "Cursor Δ", Math.abs(menu.cursorB() - menu.cursorA()) + " samples / "
                + menu.cursorDeltaTicks() + "t", 140);
        graphics.drawString(font, "Cursor timing comes from captured server gameTime.", 16, 164, MUTED, false);
        graphics.drawString(font, "All controls are validated on the logical server.", 16, 178, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        channelDiagnostics(graphics, 0, "A", 82);
        sectionRule(graphics, 126);
        channelDiagnostics(graphics, 1, "B", 136);
        statusLine(graphics, "Network", networkIntegrity(), networkColor(), 183);
    }

    private void channelDiagnostics(GuiGraphics graphics, int channel, String name, int y) {
        graphics.drawString(font, "CH " + name, 16, y, channel == 0 ? INFO : GOOD, false);
        graphics.drawString(font,
                "coverage=" + menu.coverage(channel) + "%  min/max/p2p="
                        + value(menu.minimum(channel)) + "/" + value(menu.maximum(channel)) + "/" + value(menu.peakToPeak(channel)),
                52, y, TEXT, false);
        graphics.drawString(font,
                "avg=" + decimal100(menu.average100(channel)) + "  meanStep=" + decimal100(menu.meanStep100(channel))
                        + "  period≈" + tickValue(menu.periodTicks(channel)),
                52, y + 16, MUTED, false);
    }

    private void renderHistory(GuiGraphics graphics) {
        int chartX = 38;
        int chartY = 82;
        int chartWidth = 260;
        int chartHeight = 80;

        EngineeringChartRenderer.drawFrame(graphics, chartX, chartY, chartWidth, chartHeight, 0, 15, true);
        EngineeringChartRenderer.drawWaveform(graphics, channelA, chartX, chartY, chartWidth, chartHeight, 0, 15, INFO);
        EngineeringChartRenderer.drawWaveform(graphics, channelB, chartX, chartY, chartWidth, chartHeight, 0, 15, GOOD);
        EngineeringChartRenderer.drawTimeMarker(
                graphics, channelA, menu.displayGameTime(menu.cursorA()), chartX, chartY, chartWidth, chartHeight, WARN);
        EngineeringChartRenderer.drawTimeMarker(
                graphics, channelA, menu.displayGameTime(menu.cursorB()), chartX, chartY, chartWidth, chartHeight, 0xFFE879F9);

        graphics.drawString(font, "15", 13, chartY - 2, MUTED, false);
        graphics.drawString(font, "10", 13, chartY + 24, MUTED, false);
        graphics.drawString(font, "5", 18, chartY + 50, MUTED, false);
        graphics.drawString(font, "0", 18, chartY + 73, MUTED, false);
        EngineeringChartRenderer.drawGameTimeAxis(graphics, font, channelA, chartX, chartY + chartHeight + 3, chartWidth);
        graphics.drawString(font, "A/B • server gameTime • 0..15", 16, 178, MUTED, false);
        graphics.drawString(font, "Cursor Δ=" + menu.cursorDeltaTicks() + "t", 16, 190, TEXT, false);
    }

    private void miniTrace(
            GuiGraphics graphics,
            EngineeringChartRenderer.Series series,
            int x,
            int y,
            int width,
            int height,
            int color
    ) {
        EngineeringChartRenderer.drawFrame(graphics, x, y, width, height, 0, 15, false);
        EngineeringChartRenderer.drawWaveform(graphics, series, x, y, width, height, 0, 15, color);
    }

    private String triggerText() {
        return modeName(menu.triggerMode()) + " CH " + (menu.triggerChannel() == 0 ? "A" : "B")
                + " @" + menu.triggerLevel();
    }

    private String captureState() {
        return switch (menu.captureState()) {
            case 1 -> "ARMED";
            case 2 -> "TRIGGERED";
            default -> "HOLD";
        };
    }

    private int captureColor() {
        return switch (menu.captureState()) {
            case 1 -> INFO;
            case 2 -> GOOD;
            default -> MUTED;
        };
    }

    private String networkIntegrity() {
        if (!menu.bounded()) return "TRUNCATED";
        if (menu.duplicateChannels() > 0) return "AMBIGUOUS • duplicate channel";
        if (menu.probeNodes() == 0) return "NO PROBES";
        return "OK • bounded scan";
    }

    private int networkColor() {
        if (!menu.bounded() || menu.duplicateChannels() > 0) return WARN;
        return menu.probeNodes() == 0 ? MUTED : GOOD;
    }

    private String probeState(int channel) {
        return switch (menu.probeCount(channel)) {
            case 0 -> "NO PROBE";
            case 1 -> "CONNECTED";
            default -> "AMBIGUOUS (" + menu.probeCount(channel) + ")";
        };
    }

    private int probeColor(int channel) {
        return menu.probeCount(channel) == 1 ? GOOD : menu.probeCount(channel) > 1 ? WARN : MUTED;
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "FREE";
            case 1 -> "RISING";
            case 2 -> "FALLING";
            default -> "?";
        };
    }

    private static String value(int value) {
        return value < 0 ? "N/A" : Integer.toString(value);
    }

    private static String tickValue(int value) {
        return value < 0 ? "N/A" : value + "t";
    }

    private static String decimal100(int value) {
        if (value < 0) return "N/A";
        return (value / 100) + "." + String.format("%02d", value % 100);
    }

    private final class ScopeSeries implements EngineeringChartRenderer.Series {
        private final int channel;

        private ScopeSeries(int channel) {
            this.channel = channel;
        }

        @Override
        public int size() {
            return OscilloscopeBlockEntity.DISPLAY_SAMPLES;
        }

        @Override
        public int valueAt(int slot) {
            return menu.displaySample(channel, slot);
        }

        @Override
        public long gameTimeAt(int slot) {
            return menu.displayGameTime(slot);
        }
    }
}
