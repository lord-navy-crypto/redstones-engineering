package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Full engineering panel for the series signal conditioner. */
public final class SignalConditionerScreen extends EngineeringScreen<SignalConditionerMenu> {
    private EditBox parameterInput;
    private Button parameterApply;

    public SignalConditionerScreen(SignalConditionerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + imageHeight - 90;
        addConfigureWidget(Button.builder(Component.literal("◀ Mode"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_PREVIOUS))
                .bounds(leftPos + 18, y, 86, 20).build());
        addConfigureWidget(Button.builder(Component.literal("Mode ▶"),
                button -> sendMenuButton(SignalConditionerMenu.BUTTON_MODE_NEXT))
                .bounds(leftPos + 108, y, 86, 20).build());
        parameterInput = addConfigureWidget(new EditBox(this.font, leftPos + 18, y + 25, 86, 20,
                Component.literal("Formula parameter")));
        parameterInput.setMaxLength(3);
        parameterInput.setFilter(value -> value.isEmpty() || value.equals("-") || value.matches("-?\\d+"));
        parameterApply = addConfigureWidget(Button.builder(Component.literal("Apply parameter"),
                button -> submitParameter())
                .bounds(leftPos + 108, y + 25, 86, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterInput == null || parameterApply == null) return;
        int visibleValue = visibleFormulaParameter(menu.mode(), menu.parameter());
        if (!parameterInput.isFocused()) {
            String expected = Integer.toString(visibleValue);
            if (!expected.equals(parameterInput.getValue())) parameterInput.setValue(expected);
        }
        parameterApply.setMessage(Component.literal("Apply " + parameterSymbol(menu.mode())));
        parameterApply.active = parameterInputValid();
    }

    private static int visibleFormulaParameter(int mode, int rawParam) {
        return mode == 1 ? Math.min(10, rawParam) - 5 : rawParam;
    }

    private boolean parameterInputValid() {
        if (parameterInput == null || parameterInput.getValue().isEmpty() || parameterInput.getValue().equals("-")) return false;
        try {
            int value = Integer.parseInt(parameterInput.getValue());
            return switch (menu.mode()) {
                case 0, 4 -> value >= 1 && value <= 4;
                case 1 -> value >= -5 && value <= 5;
                case 2, 3 -> value >= 1 && value <= 15;
                default -> false;
            };
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void submitParameter() {
        if (!parameterInputValid()) return;
        int value = Integer.parseInt(parameterInput.getValue());
        sendMenuButton(SignalConditionerMenu.BUTTON_PARAM_DIRECT_BASE + value + 16);
        parameterInput.setFocused(false);
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
        safeText(graphics, "Input and output remain opposite ends of one rotatable series path.", 16, 186, MUTED);
        safeText(graphics, "Physical direction is controlled only on Route; side faces remain non-driving.", 16, 202, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "FORMULA-FIRST SERVER CONTROL", INFO, 16, 80);
        formulaCard(graphics, governingEquation(), 105);
        variableRole(graphics, "MEASURED", "x", menu.input() + " / 15", "redstone", 134);
        variableRole(graphics, "ADJUSTABLE", parameterSymbol(menu.mode()), parameterText(menu.mode(), menu.parameter()), parameterRange(menu.mode()), 152);
        variableRole(graphics, "CONTROL", "direct entry", "enter " + parameterSymbol(menu.mode()) + " exactly", parameterRange(menu.mode()), 170);
        variableRole(graphics, "DERIVED", "y", menu.output() + " / 15", "redstone", 188);
        variableRole(graphics, "EVIDENCE", "boundary", menu.limiting() ? "SATURATED" : "IN RANGE", "", 206);
        wrappedText(graphics, behaviorLine(menu.mode()), 16, 228, workspaceWidth() - 24, TEXT);
        wrappedText(graphics, "Direct entry submits the formula value to the server; Route owns physical RX/TX direction.", 16, 258, workspaceWidth() - 24, MUTED);
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
        safeText(graphics, "The conditioner exposes the complete current transfer state above.", 16, 108, TEXT);
        safeText(graphics, "For time history, place Probe / Analyzer / Oscilloscope on the series path.", 16, 127, INFO);
        sectionRule(graphics, 149);
        safeText(graphics, "Current state = input + mode + parameter + output + I/O direction + saturation.", 16, 162, MUTED);
        safeText(graphics, "A valid zero is data; it is never treated as a fault by this screen.", 16, 180, GOOD);
    }

    private String governingEquation() {
        return switch (menu.mode()) {
            case 0 -> "y = clamp₀..₁₅(g · x)";
            case 1 -> "y = clamp₀..₁₅(x + b)";
            case 2 -> "y = min(x, c)";
            case 3 -> "y = (x ≥ T) ? x : 0";
            case 4 -> "y = (|x - y_prev| ≥ B) ? x : y_prev";
            default -> "y = x";
        };
    }

    private static String parameterSymbol(int mode) {
        return switch (mode) {
            case 0 -> "g";
            case 1 -> "b";
            case 2 -> "c";
            case 3 -> "T";
            case 4 -> "B";
            default -> "p";
        };
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
