package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact Level-1 engineering panel for signal conditioning. */
public final class SignalConditionerScreen extends EngineeringScreen<SignalConditionerMenu> {
    public SignalConditionerScreen(SignalConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 105;
        addConfigureWidget(Button.builder(
                Component.literal("◀ Mode"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_PREVIOUS)
        ).bounds(leftPos + 18, y, 86, 20).build());
        addConfigureWidget(Button.builder(
                Component.literal("Mode ▶"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_NEXT)
        ).bounds(leftPos + 108, y, 86, 20).build());
        addConfigureWidget(Button.builder(
                Component.literal("− Param"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_DECREASE)
        ).bounds(leftPos + 18, y + 25, 86, 20).build());
        addConfigureWidget(Button.builder(
                Component.literal("Param +"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_INCREASE)
        ).bounds(leftPos + 108, y + 25, 86, 20).build());
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> {
                labelValue(graphics, "Input", Integer.toString(menu.input()), 84);
                labelValue(graphics, "Output", Integer.toString(menu.output()), 99);
                labelValue(graphics, "Mode", modeName(menu.mode()), 114);
                labelValue(graphics, "Parameter", parameterText(menu.mode(), menu.parameter()), 129);
                graphics.drawString(font, "OUTPUT SIGNAL", 16, 151, MUTED, false);
                signalBar(graphics, menu.output(), 171);
            }
            case PORTS -> {
                statusLine(graphics, "BACK", "INPUT • REDSTONE 0..15", GOOD, 87);
                statusLine(graphics, "FRONT", "OUTPUT • REDSTONE 0..15", GOOD, 105);
                statusLine(graphics, "LEFT / RIGHT", "NO ENGINEERING PORT", MUTED, 123);
                statusLine(graphics, "UP / DOWN", "NO ENGINEERING PORT", MUTED, 141);
            }
            case CONFIGURE -> {
                labelValue(graphics, "Current mode", modeName(menu.mode()), 84);
                labelValue(graphics, "Current parameter", parameterText(menu.mode(), menu.parameter()), 99);
                graphics.drawString(font, "Changes are validated and applied on the logical server.", 16, 161, MUTED, false);
            }
            case DIAGNOSTICS -> {
                labelValue(graphics, "Live input", Integer.toString(menu.input()), 84);
                labelValue(graphics, "Live output", Integer.toString(menu.output()), 99);
                statusLine(graphics, "World boundary", "0..15 CLAMPED", GOOD, 114);
                statusLine(graphics, "Input direction", "BACK ONLY", GOOD, 129);
                statusLine(graphics, "Output direction", "FRONT ONLY", GOOD, 144);
            }
            case HISTORY -> {
                graphics.drawString(font, "This compact processor has no internal history buffer.", 16, 87, TEXT, false);
                graphics.drawString(font, "Use an analyzer or oscilloscope downstream for time history.", 16, 105, MUTED, false);
            }
        }
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
