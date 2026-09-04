package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact Level-1 engineering panel for signal conditioning. */
public final class SignalConditionerScreen extends EngineeringScreen<SignalConditionerMenu> {
    private Button parameterDecrease;
    private Button parameterIncrease;

    public SignalConditionerScreen(SignalConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 108;
        addConfigureWidget(Button.builder(
                Component.literal("◀ Mode"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_PREVIOUS)
        ).bounds(leftPos + 18, y, 86, 20).build());
        addConfigureWidget(Button.builder(
                Component.literal("Mode ▶"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_NEXT)
        ).bounds(leftPos + 108, y, 86, 20).build());
        parameterDecrease = addConfigureWidget(Button.builder(
                Component.literal("− Parameter"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_DECREASE)
        ).bounds(leftPos + 18, y + 25, 86, 20).build());
        parameterIncrease = addConfigureWidget(Button.builder(
                Component.literal("Parameter +"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_INCREASE)
        ).bounds(leftPos + 108, y + 25, 86, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterDecrease == null || parameterIncrease == null) return;
        String shortName = parameterShortName(menu.mode());
        parameterDecrease.setMessage(Component.literal("− " + shortName));
        parameterIncrease.setMessage(Component.literal(shortName + " +"));
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
        labelValue(graphics, "Input", menu.input() + " / 15", 82);
        labelValue(graphics, "Output", menu.output() + " / 15", 97);
        labelValue(graphics, "Mode", modeName(menu.mode()), 112);
        labelValue(graphics, parameterName(menu.mode()), parameterText(menu.mode(), menu.parameter()), 127);
        statusBadge(graphics, boundaryState(), boundaryColor(), 16, 143);
        graphics.drawString(font, "OUTPUT SIGNAL", 16, 162, MUTED, false);
        signalBar(graphics, menu.output(), 174);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusLine(graphics, "BACK", "INPUT • REDSTONE 0..15", GOOD, 84);
        statusLine(graphics, "FRONT", "OUTPUT • REDSTONE 0..15", GOOD, 102);
        sectionRule(graphics, 119);
        statusLine(graphics, "LEFT / RIGHT", "NO ENGINEERING PORT", MUTED, 128);
        statusLine(graphics, "UP / DOWN", "NO ENGINEERING PORT", MUTED, 146);
        graphics.drawString(font, "Orientation matters: BACK reads, FRONT drives.", 16, 167, INFO, false);
    }

    private void renderConfigure(GuiGraphics graphics) {
        labelValue(graphics, "Current mode", modeName(menu.mode()), 80);
        labelValue(graphics, parameterName(menu.mode()), parameterText(menu.mode(), menu.parameter()), 95);
        graphics.drawString(font, "Allowed: " + parameterRange(menu.mode()), 208, 95, MUTED, false);
        graphics.drawString(font, behaviorLine(menu.mode()), 16, 161, TEXT, false);
        graphics.drawString(font, "Buttons send intent; the logical server validates and applies it.", 16, 176, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        labelValue(graphics, "Live input", menu.input() + " / 15", 82);
        labelValue(graphics, "Live output", menu.output() + " / 15", 97);
        statusLine(graphics, "World boundary", "0..15 • CLAMPED", GOOD, 115);
        statusLine(graphics, "Input direction", "BACK ONLY", GOOD, 133);
        statusLine(graphics, "Output direction", "FRONT ONLY", GOOD, 151);
        statusLine(graphics, "Output state", boundaryState(), boundaryColor(), 169);
    }

    private void renderHistory(GuiGraphics graphics) {
        graphics.drawString(font, "No internal history buffer is stored in this compact processor.", 16, 84, TEXT, false);
        graphics.drawString(font, "Place an analyzer or oscilloscope downstream for time history.", 16, 103, INFO, false);
        sectionRule(graphics, 124);
        graphics.drawString(font, "Reason: conditioning stays small, deterministic, and world-facing.", 16, 136, MUTED, false);
        graphics.drawString(font, "The authoritative live readback remains available in Overview.", 16, 154, MUTED, false);
    }

    private String boundaryState() {
        if (menu.output() <= 0) return "LOW LIMIT";
        if (menu.output() >= 15) return "HIGH LIMIT";
        return "IN RANGE";
    }

    private int boundaryColor() {
        return menu.output() <= 0 || menu.output() >= 15 ? WARN : GOOD;
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "GAIN";
            case 1 -> "OFFSET";
            case 2 -> "CLAMP";
            case 3 -> "THRESHOLD";
            case 4 -> "DEADBAND";
            default -> "UNKNOWN";
        };
    }

    private static String parameterName(int mode) {
        return switch (mode) {
            case 0 -> "Gain factor";
            case 1 -> "Offset";
            case 2 -> "Clamp ceiling";
            case 3 -> "Trip level";
            case 4 -> "Deadband width";
            default -> "Parameter";
        };
    }

    private static String parameterShortName(int mode) {
        return switch (mode) {
            case 0 -> "Gain";
            case 1 -> "Offset";
            case 2 -> "Ceiling";
            case 3 -> "Trip";
            case 4 -> "Band";
            default -> "Param";
        };
    }

    private static String parameterRange(int mode) {
        return switch (mode) {
            case 0 -> "×1 .. ×4";
            case 1 -> "−5 .. +5";
            case 2, 3 -> "1 .. 15";
            case 4 -> "1 .. 4";
            default -> "—";
        };
    }

    private static String behaviorLine(int mode) {
        return switch (mode) {
            case 0 -> "GAIN: scale input; the world-facing output remains bounded to 0..15.";
            case 1 -> "OFFSET: add a signed correction, then clamp to the 0..15 boundary.";
            case 2 -> "CLAMP: pass the input up to the selected maximum ceiling.";
            case 3 -> "THRESHOLD: pass input at/above the trip level; otherwise output 0.";
            case 4 -> "DEADBAND: accept only changes large enough to cross the selected band.";
            default -> "Unknown conditioning mode.";
        };
    }

    private static String parameterText(int mode, int param) {
        return switch (mode) {
            case 0 -> "×" + Math.max(1, Math.min(4, param));
            case 1 -> (Math.min(10, param) - 5 >= 0 ? "+" : "") + (Math.min(10, param) - 5);
            case 2 -> "MAX " + Math.max(1, param);
            case 3 -> "TRIP ≥ " + Math.max(1, param);
            case 4 -> "BAND " + Math.max(1, Math.min(4, param));
            default -> "—";
        };
    }
}
