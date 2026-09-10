package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Full engineering panel for the series signal conditioner. */
public final class SignalConditionerScreen extends EngineeringScreen<SignalConditionerMenu> {
    private Button parameterDecrease;
    private Button parameterIncrease;
    private Button rotateLeft;
    private Button rotateRight;

    public SignalConditionerScreen(SignalConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 108;
        addConfigureWidget(Button.builder(Component.literal("◀ Mode"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_PREVIOUS))
                .bounds(leftPos + 18, y, 86, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Mode ▶"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_NEXT))
                .bounds(leftPos + 108, y, 86, 20).build());
        parameterDecrease = addConfigureWidget(Button.builder(Component.literal("− Parameter"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_DECREASE))
                .bounds(leftPos + 18, y + 25, 86, 20).build());
        parameterIncrease = addConfigureWidget(Button.builder(Component.literal("Parameter +"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_INCREASE))
                .bounds(leftPos + 108, y + 25, 86, 20).build());
        rotateLeft = addConfigureWidget(Button.builder(Component.literal("↺ Axis"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_ROTATE_LEFT))
                .bounds(leftPos + 198, y, 104, 20).build());
        rotateRight = addConfigureWidget(Button.builder(Component.literal("Axis ↻"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_ROTATE_RIGHT))
                .bounds(leftPos + 198, y + 25, 104, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterDecrease == null || parameterIncrease == null) return;
        String shortName = parameterShortName(menu.mode());
        parameterDecrease.setMessage(Component.literal("− " + shortName));
        parameterIncrease.setMessage(Component.literal(shortName + " +"));
        if (rotateLeft != null) rotateLeft.setMessage(Component.literal("↺ " + direction(menu.outputDirection().getName())));
        if (rotateRight != null) rotateRight.setMessage(Component.literal(direction(menu.outputDirection().getName()) + " ↻"));
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
        statusBadge(graphics, "SERIES SIGNAL CONDITIONER", INFO, 16, 80);
        labelValue(graphics, "Input", menu.input() + " / 15", 105);
        labelValue(graphics, "Transfer", modeName(menu.mode()) + " • " + parameterText(menu.mode(), menu.parameter()), 121);
        labelValue(graphics, "Output", menu.output() + " / 15", 137);
        labelValue(graphics, "Series path", direction(menu.inputDirection().getName()) + " → " + direction(menu.outputDirection().getName()), 153);
        statusLine(graphics, "Boundary", boundaryState(), boundaryColor(), 173);
        graphics.drawString(font, "OUTPUT", 16, 194, MUTED, false);
        signalBar(graphics, menu.output(), 205);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusBadge(graphics, "SERIES I/O AXIS", GOOD, 16, 80);
        statusLine(graphics, direction(menu.inputDirection().getName()), "INPUT • REDSTONE 0..15", GOOD, 108);
        statusLine(graphics, "PROCESS", modeName(menu.mode()) + " • " + parameterText(menu.mode(), menu.parameter()), INFO, 130);
        statusLine(graphics, direction(menu.outputDirection().getName()), "OUTPUT • REDSTONE 0..15", GOOD, 152);
        sectionRule(graphics, 174);
        graphics.drawString(font, "Input and output remain opposite ends of one rotatable series path.", 16, 186, MUTED, false);
        graphics.drawString(font, "Rotate the axis in Configure; side faces remain non-driving.", 16, 202, MUTED, false);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "SERVER-AUTHORITATIVE CONTROL", INFO, 16, 80);
        labelValue(graphics, "Mode", modeName(menu.mode()), 102);
        labelValue(graphics, parameterName(menu.mode()), parameterText(menu.mode(), menu.parameter()), 118);
        labelValue(graphics, "Allowed", parameterRange(menu.mode()), 134);
        labelValue(graphics, "Input → Output", direction(menu.inputDirection().getName()) + " → " + direction(menu.outputDirection().getName()), 150);
        graphics.drawString(font, behaviorLine(menu.mode()), 16, 177, TEXT, false);
        graphics.drawString(font, "Buttons change configuration only on the logical server.", 16, 194, MUTED, false);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusBadge(graphics, menu.limiting() ? "SATURATED" : "TRANSFER VALID", menu.limiting() ? WARN : GOOD, 16, 80);
        labelValue(graphics, "Live input", menu.input() + " / 15", 105);
        labelValue(graphics, "Live output", menu.output() + " / 15", 121);
        labelValue(graphics, "Mode / parameter", modeName(menu.mode()) + " • " + parameterText(menu.mode(), menu.parameter()), 137);
        labelValue(graphics, "Input face", direction(menu.inputDirection().getName()), 153);
        labelValue(graphics, "Output face", direction(menu.outputDirection().getName()), 169);
        statusLine(graphics, "0..15 boundary", menu.limiting() ? "SATURATION ACTIVE" : "VALID • INCLUDING ZERO", menu.limiting() ? WARN : GOOD, 190);
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "LIVE STATE / EXTERNAL HISTORY", INFO, 16, 80);
        graphics.drawString(font, "The conditioner exposes the complete current transfer state above.", 16, 108, TEXT, false);
        graphics.drawString(font, "For time history, place Probe / Analyzer / Oscilloscope on the series path.", 16, 127, INFO, false);
        sectionRule(graphics, 149);
        graphics.drawString(font, "Current state = input + mode + parameter + output + I/O direction + saturation.", 16, 162, MUTED, false);
        graphics.drawString(font, "A valid zero is data; it is never treated as a fault by this screen.", 16, 180, GOOD, false);
    }

    private String boundaryState() {
        if (menu.limiting()) return "SATURATED • WORLD BOUNDARY ACTIVE";
        if (menu.output() == 0) return "VALID ZERO";
        if (menu.output() == 15) return "VALID FULL-SCALE";
        return "IN RANGE";
    }

    private int boundaryColor() {
        return menu.limiting() ? WARN : GOOD;
    }

    private static String direction(String name) {
        return name.toUpperCase();
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
            case 0 -> "GAIN: multiply input; only the external redstone boundary clamps to 0..15.";
            case 1 -> "OFFSET: add signed correction, then enforce the vanilla 0..15 boundary.";
            case 2 -> "CLAMP: pass input until the configured ceiling is reached.";
            case 3 -> "THRESHOLD: pass values at/above trip; otherwise emit a valid zero.";
            case 4 -> "DEADBAND: hold output until the input change exceeds the selected band.";
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
