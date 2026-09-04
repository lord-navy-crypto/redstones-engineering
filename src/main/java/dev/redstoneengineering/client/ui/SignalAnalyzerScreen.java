package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalAnalyzerBlock;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Metrology-focused analyzer panel with rolling history and explicit TAP/INLINE semantics. */
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
        statusBadge(graphics, modeName(), menu.mode() == SignalAnalyzerBlock.TAP ? INFO : GOOD, 16, 80);
        labelValue(graphics, "Raw measurement", menu.raw() + " / 15", 101);
        labelValue(graphics, "Calibrated display", menu.calibrated() + " / 15", 116);
        labelValue(graphics, "Calibration", signed(menu.calibrationOffset()), 131);
        labelValue(graphics, "World output", menu.mode() == SignalAnalyzerBlock.INLINE ? menu.output() + " / 15 RAW" : "DISCONNECTED", 146);
        graphics.drawString(font, "ROLLING WINDOW", 16, 164, MUTED, false);
        analogTrace(graphics, 98, 160, 200, 22, INFO);
        graphics.drawString(font, "avg=" + decimal100(menu.average100()) + "  p2p=" + menu.peakToPeak(), 16, 187, TEXT, false);
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
        labelValue(graphics, "Mode", modeName(), 80);
        labelValue(graphics, "Calibration offset", signed(menu.calibrationOffset()), 95);
        graphics.drawString(font,
                menu.mode() == SignalAnalyzerBlock.TAP
                        ? "TAP observes the TEST side without creating a redstone electrical path."
                        : "INLINE reads TEST and reproduces the RAW sample on the opposite face.",
                16, 162, TEXT, false);
        graphics.drawString(font, "Calibration changes only the displayed engineering reading.", 16, 177, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        labelValue(graphics, "Lifetime min / max", menu.lifeMin() + " / " + menu.lifeMax(), 82);
        labelValue(graphics, "Changes", Integer.toString(menu.changes()), 97);
        labelValue(graphics, "Edges", "↑" + menu.rising() + " ↓" + menu.falling(), 112);
        labelValue(graphics, "Last / max Δ", menu.lastDelta() + " / " + menu.maxDelta(), 127);
        labelValue(graphics, "Stable for", menu.stableAgeTicks() + "t", 142);
        labelValue(graphics, "Sample age", menu.sampleAgeTicks() < 0 ? "N/A" : menu.sampleAgeTicks() + "t", 157);
        statusLine(graphics, "Variation", stabilityClass(), stabilityColor(), 177);
    }

    private void renderHistory(GuiGraphics graphics) {
        graphics.drawString(font, "15", 13, 84, MUTED, false);
        graphics.drawString(font, "0", 18, 162, MUTED, false);
        graphics.fill(38, 82, 298, 166, 0xFF10141A);
        analogTrace(graphics, 42, 86, 250, 76, INFO);
        graphics.drawString(font,
                "window=" + menu.windowCount() + "/16  avg=" + decimal100(menu.average100())
                        + "  p2p=" + menu.peakToPeak() + "  meanStep=" + decimal100(menu.meanStep100()),
                16, 175, TEXT, false);
        graphics.drawString(font,
                "samples=" + menu.totalSamples() + "  mode switches=" + menu.modeSwitches()
                        + "  calibration switches=" + menu.calibrationSwitches(),
                16, 188, MUTED, false);
    }

    private void analogTrace(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int previousX = -1;
        int previousY = -1;
        for (int slot = 0; slot < SignalAnalyzerBlock.DISPLAY_SAMPLES; slot++) {
            int sample = menu.sample(slot);
            if (sample < 0) {
                previousX = -1;
                previousY = -1;
                continue;
            }
            int px = x + Math.round(slot * width / 15.0f);
            int py = y + height - Math.round(sample * height / 15.0f);
            if (previousX >= 0) {
                graphics.fill(Math.min(previousX, px), previousY, Math.max(previousX, px) + 1, previousY + 1, color);
                graphics.fill(px, Math.min(previousY, py), px + 1, Math.max(previousY, py) + 1, color);
            }
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
            previousX = px;
            previousY = py;
        }
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

    private int stabilityColor() {
        String state = stabilityClass();
        return switch (state) {
            case "STEADY", "STABLE" -> GOOD;
            case "DYNAMIC" -> INFO;
            case "HIGH VARIATION" -> WARN;
            default -> MUTED;
        };
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String decimal100(int value) {
        int abs = Math.abs(value);
        return (value < 0 ? "-" : "") + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}
