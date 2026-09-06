package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Four-channel digital timing UI with real capture, edges, pulse metrics and probe integrity. */
public final class LogicAnalyzerScreen extends EngineeringScreen<LogicAnalyzerMenu> {
    public LogicAnalyzerScreen(LogicAnalyzerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int x = leftPos + 16;
        int y = topPos + 104;
        int w = 88;
        int gap = 6;
        addConfigureWidget(Button.builder(Component.literal("Arm"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_ARM)).bounds(x, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Threshold −"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_THRESHOLD_DECREASE)).bounds(x + w + gap, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Threshold +"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_THRESHOLD_INCREASE)).bounds(x + (w + gap) * 2, y, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger CH"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_TRIGGER_CHANNEL)).bounds(x, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Trigger edge"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_TRIGGER_EDGE)).bounds(x + w + gap, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor A +"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_CURSOR_A)).bounds(x + (w + gap) * 2, y + 25, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Cursor B +"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_CURSOR_B)).bounds(x, y + 50, w, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Clear capture"),
                b -> sendMenuButton(LogicAnalyzerMenu.BUTTON_CLEAR)).bounds(x + w + gap, y + 50, w * 2 + gap, 20).build());
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
        labelValue(graphics, "Threshold", menu.threshold() + " / 15", 99);
        labelValue(graphics, "Trigger", "CH " + channelName(menu.triggerChannel()) + " " + edgeName(menu.triggerEdge()), 114);
        labelValue(graphics, "Capture", menu.sampleCount() + "/32 samples", 129);
        for (int channel = 0; channel < 4; channel++) {
            int y = 148 + channel * 11;
            graphics.drawString(font, channelName(channel), 16, y, channelColor(channel), false);
            EngineeringChartRenderer.Series series = series(channel);
            EngineeringChartRenderer.drawDigitalLane(graphics, series, 38, y - 1, 260, 9, 1, channelColor(channel));
            EngineeringChartRenderer.drawDigitalEdgeMarkers(graphics, series, 38, y - 1, 260, 9, 1, GOOD, WARN);
        }
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "Instrument bus", networkIntegrity(), networkColor(), 81);
        for (int channel = 0; channel < 4; channel++) {
            statusLine(graphics, "CH " + channelName(channel), probeState(channel), probeColor(channel), 101 + channel * 20);
        }
        labelValue(graphics, "Cable / probe nodes", menu.cableNodes() + " / " + menu.probeNodes(), 183);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Digital threshold", menu.threshold() + " / 15", 80);
        labelValue(graphics, "Trigger channel", "CH " + channelName(menu.triggerChannel()), 95);
        labelValue(graphics, "Trigger edge", edgeName(menu.triggerEdge()), 110);
        labelValue(graphics, "Cursors", "A=" + menu.cursorA() + " B=" + menu.cursorB(), 125);
        labelValue(graphics, "Cursor Δ", Math.abs(menu.cursorB() - menu.cursorA()) + " samples / "
                + menu.cursorDeltaTicks() + "t server time", 140);
        graphics.drawString(font, "Threshold and trigger controls never bypass the server capture engine.", 16, 178, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        for (int channel = 0; channel < 4; channel++) {
            int y = 80 + channel * 25;
            graphics.drawString(font, "CH " + channelName(channel), 16, y, channelColor(channel), false);
            graphics.drawString(font,
                    "coverage=" + menu.coverage(channel) + "% duty=" + menu.duty(channel)
                            + "% transition=" + menu.transitionRate(channel) + "%",
                    54, y, TEXT, false);
            graphics.drawString(font,
                    "↑" + menu.rising(channel) + " ↓" + menu.falling(channel)
                            + "  T=" + timing(menu.periodTicks(channel))
                            + "  HIGH=" + timing(menu.pulseWidthTicks(channel))
                            + "  Δ↑=" + relativeTiming(menu.relativeRisingTicks(channel)),
                    54, y + 12, MUTED, false);
        }
        statusLine(graphics, "Network", networkIntegrity(), networkColor(), 183);
    }

    private void renderHistory(GuiGraphics graphics) {
        EngineeringChartRenderer.drawFrame(graphics, 38, 80, 260, 86, 0, 1, false);
        for (int channel = 0; channel < 4; channel++) {
            int y = 87 + channel * 18;
            graphics.drawString(font, channelName(channel), 16, y, channelColor(channel), false);
            EngineeringChartRenderer.Series series = series(channel);
            EngineeringChartRenderer.drawDigitalLane(graphics, series, 42, y, 250, 12, 1, channelColor(channel));
            EngineeringChartRenderer.drawDigitalEdgeMarkers(graphics, series, 42, y, 250, 12, 1, GOOD, WARN);
        }

        EngineeringChartRenderer.Series timeSeries = series(menu.triggerChannel());
        EngineeringChartRenderer.drawTimeMarker(
                graphics, timeSeries, menu.displayGameTime(menu.cursorA()), 42, 80, 250, 86, WARN);
        EngineeringChartRenderer.drawTimeMarker(
                graphics, timeSeries, menu.displayGameTime(menu.cursorB()), 42, 80, 250, 86, 0xFFE879F9);
        EngineeringChartRenderer.drawGameTimeAxis(graphics, font, timeSeries, 42, 168, 250);
        graphics.drawString(font, "↑/↓ markers use observed edges; dots mark invalid/missing probe samples.", 16, 180, MUTED, false);
        graphics.drawString(font,
                "Cursor Δ=" + Math.abs(menu.cursorB() - menu.cursorA()) + " samples / " + menu.cursorDeltaTicks() + "t",
                16, 192, TEXT, false);
    }

    private EngineeringChartRenderer.Series series(int channel) {
        return new EngineeringChartRenderer.Series() {
            @Override public int size() { return LogicAnalyzerBlockEntity.DISPLAY_SAMPLES; }
            @Override public int valueAt(int slot) { return menu.displayState(channel, slot); }
            @Override public long gameTimeAt(int slot) { return menu.displayGameTime(slot); }
        };
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
        if (menu.duplicateChannels() > 0) return "AMBIGUOUS • duplicate channels";
        if (menu.probeNodes() == 0) return "NO PROBES";
        return "OK • " + menu.validChannels() + "/" + menu.activeChannels() + " valid/active";
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

    private static String timing(int ticks) {
        return ticks < 0 ? "N/A" : ticks + "t";
    }

    private static String relativeTiming(int ticks) {
        if (ticks == Integer.MIN_VALUE) return "N/A";
        return (ticks > 0 ? "+" : "") + ticks + "t";
    }

    private static String channelName(int channel) {
        return SignalProbeBlock.channelName(channel);
    }

    private static String edgeName(int edge) {
        return edge == 2 ? "FALLING" : "RISING";
    }

    private static int channelColor(int channel) {
        return switch (channel) {
            case 0 -> 0xFF66C2FF;
            case 1 -> 0xFF7DDB8A;
            case 2 -> 0xFFFFC857;
            default -> 0xFFE879F9;
        };
    }
}
