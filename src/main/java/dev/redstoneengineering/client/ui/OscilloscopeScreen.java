package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Two-channel scope UI backed by the authoritative 32-sample capture engine. */
public final class OscilloscopeScreen extends EngineeringScreen<OscilloscopeMenu> {
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
        miniTrace(graphics, 0, 50, 145, 240, 18, INFO);
        graphics.drawString(font, "CH B", 16, 170, GOOD, false);
        miniTrace(graphics, 1, 50, 167, 240, 18, GOOD);
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
                + Math.abs(menu.cursorB() - menu.cursorA()) * OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS + "t", 140);
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
        int x = 38;
        int y = 82;
        int width = 260;
        int height = 84;
        int inset = 3;
        int samples = OscilloscopeBlockEntity.DISPLAY_SAMPLES;

        graphics.drawString(font, "0", 17, 165, MUTED, false);
        graphics.drawString(font, "15", 12, 85, MUTED, false);
        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        EngineeringPlot.horizontalMarker(graphics, menu.triggerLevel(), 0, 15,
                x + inset, y + inset, width - inset * 2, height - inset * 2, WARN);
        plotChannel(graphics, 0, x + inset, y + inset, width - inset * 2, height - inset * 2, INFO);
        plotChannel(graphics, 1, x + inset, y + inset, width - inset * 2, height - inset * 2, GOOD);
        EngineeringPlot.verticalMarker(graphics, menu.cursorA(), samples, x, y, width, height, WARN);
        EngineeringPlot.verticalMarker(graphics, menu.cursorB(), samples, x, y, width, height, 0xFFE879F9);

        graphics.drawString(font, "A", 269, 86, INFO, false);
        graphics.drawString(font, "B", 280, 86, GOOD, false);
        graphics.drawString(font, "T", 291, 86, WARN, false);
        graphics.drawString(font, "A/B traces • T=trigger level • synchronized 0..15 samples", 16, 173, MUTED, false);
        graphics.drawString(font,
                "Cursor Δ=" + Math.abs(menu.cursorB() - menu.cursorA()) + " samples / "
                        + Math.abs(menu.cursorB() - menu.cursorA()) * OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS + "t",
                16, 187, TEXT, false);
    }

    private void miniTrace(GuiGraphics graphics, int channel, int x, int y, int width, int height, int color) {
        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        plotChannel(graphics, channel, x + 2, y + 2, width - 4, height - 4, color);
    }

    private void plotChannel(GuiGraphics graphics, int channel, int x, int y, int width, int height, int color) {
        EngineeringPlot.analogTrace(
                graphics,
                OscilloscopeBlockEntity.DISPLAY_SAMPLES,
                slot -> menu.displaySample(channel, slot),
                0,
                15,
                x,
                y,
                width,
                height,
                color
        );
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
}
