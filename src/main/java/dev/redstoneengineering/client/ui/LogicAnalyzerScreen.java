package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.ui.menu.LogicAnalyzerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Four-channel digital timing UI with real capture, edge counts and probe integrity. */
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
            EngineeringPlot.digitalTrace(
                    graphics,
                    LogicAnalyzerBlockEntity.DISPLAY_SAMPLES,
                    slot -> menu.displayState(channel, slot),
                    38,
                    y - 1,
                    260,
                    9,
                    channelColor(channel)
            );
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
                + Math.abs(menu.cursorB() - menu.cursorA()) * LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS + "t", 140);
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
                    "edges ↑" + menu.rising(channel) + " ↓" + menu.falling(channel),
                    54, y + 12, MUTED, false);
        }
        statusLine(graphics, "Network", networkIntegrity(), networkColor(), 183);
    }

    private void renderHistory(GuiGraphics graphics) {
        int x = 38;
        int y = 80;
        int width = 260;
        int height = 86;
        int samples = LogicAnalyzerBlockEntity.DISPLAY_SAMPLES;

        EngineeringPlot.analogFrame(graphics, x, y, width, height);
        for (int channel = 0; channel < 4; channel++) {
            int laneY = 88 + channel * 19;
            graphics.drawString(font, channelName(channel), 16, laneY, channelColor(channel), false);
            EngineeringPlot.digitalTrace(
                    graphics,
                    samples,
                    slot -> menu.displayState(channel, slot),
                    42,
                    laneY,
                    250,
                    12,
                    channelColor(channel)
            );
        }
        EngineeringPlot.verticalMarker(graphics, menu.cursorA(), samples, 42, y, 250, height, WARN);
        EngineeringPlot.verticalMarker(graphics, menu.cursorB(), samples, 42, y, 250, height, 0xFFE879F9);
        graphics.drawString(font, "A", 271, 82, WARN, false);
        graphics.drawString(font, "B", 282, 82, 0xFFE879F9, false);
        graphics.drawString(font, "HIGH/LOW timing • gaps mark invalid or missing probe samples", 16, 175, MUTED, false);
        graphics.drawString(font,
                "Cursor Δ=" + Math.abs(menu.cursorB() - menu.cursorA()) + " samples / "
                        + Math.abs(menu.cursorB() - menu.cursorA()) * LogicAnalyzerBlockEntity.SAMPLE_PERIOD_TICKS + "t",
                16, 188, TEXT, false);
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
