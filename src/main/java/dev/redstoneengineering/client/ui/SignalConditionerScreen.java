package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.signal.SignalConditionerLogic;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Full engineering panel for the series signal conditioner. */
public final class SignalConditionerScreen extends EngineeringScreen<SignalConditionerMenu> {
    private Button parameterDecrease;
    private Button parameterIncrease;

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
        statusBadge(graphics, "SERIES SIGNAL CONDITIONER", INFO, 16, 80);
        labelValue(graphics, "Input", menu.input() + " / 15", 105);
        labelValue(graphics, "Transfer", modeName(menu.mode()) + " • " + parameterText(menu.mode(), menu.parameter()), 121);
        labelValue(graphics, "Output", menu.output() + " / 15", 137);
        labelValue(graphics, "Evidence", qualityName(menu.inputQuality()) + " → " + qualityName(menu.outputQuality()), 153);
        labelValue(graphics, "Series path", direction(menu.inputDirection().getName()) + " → " + direction(menu.outputDirection().getName()), 169);
        statusLine(graphics, "Boundary", boundaryState(), boundaryColor(), 189);
        graphics.drawString(font, "OUTPUT", 16, 210, MUTED, false);
        signalBar(graphics, menu.output(), 221);
    }

    private void renderPorts(GuiGraphics graphics) {
        statusBadge(graphics, "SERIES I/O AXIS", GOOD, 16, 80);
        statusLine(graphics, direction(menu.inputDirection().getName()), "INPUT • REDSTONE 0..15", GOOD, 108);
        statusLine(graphics, "PROCESS", modeName(menu.mode()) + " • " + parameterText(menu.mode(), menu.parameter()), INFO, 130);
        statusLine(graphics, direction(menu.outputDirection().getName()), "OUTPUT • REDSTONE 0..15", GOOD, 152);
        sectionRule(graphics, 174);
        int noteY = wrappedText(graphics, "Input and output remain opposite ends of one rigid rotatable series path.", 16, 186, 620, MUTED);
        wrappedText(graphics, "Physical direction is controlled only on Route; side faces remain non-driving.", 16, noteY + 4, 620, MUTED);
    }

    private void renderConfigure(GuiGraphics graphics) {
        statusBadge(graphics, "SERVER-AUTHORITATIVE CONTROL", INFO, 16, 80);
        // Two rows of configuration buttons occupy the 108..153 region.
        // Keep model text below them so controls and engineering explanation never overlap.
        labelValue(graphics, "Mode", modeName(menu.mode()), 164);
        labelValue(graphics, parameterName(menu.mode()), parameterText(menu.mode(), menu.parameter()), 184);
        labelValue(graphics, "Allowed", parameterRange(menu.mode()), 204);
        labelValue(graphics, "Stored → effective", parameterContract(menu.mode(), menu.parameter()), 224);
        labelValue(graphics, "State dependence",
                SignalConditionerLogic.usesPreviousOutput(menu.mode())
                        ? "STATEFUL • transfer uses previous output yprev"
                        : "MEMORYLESS • current input + parameter only", 244);
        labelValue(graphics, "Boundary evidence", boundaryContract(menu.mode(), menu.parameter()), 264);
        labelValue(graphics, "Rigid Input → Output",
                direction(menu.inputDirection().getName()) + " → " + direction(menu.outputDirection().getName()), 284);
        int noteY = wrappedText(graphics, behaviorLine(menu.mode()), 16, 308, 620, TEXT);
        noteY = wrappedText(graphics, "Buttons change configuration only; Route rotates the complete opposite-port axis.", 16, noteY + 4, 620, MUTED);
        labelValue(graphics, "Transfer model", modelLine(menu.mode(), menu.parameter()).replace("MODEL • ", ""), noteY + 10);
        wrappedText(graphics,
                "Server tick execution calls the same pure SignalConditionerLogic contract verified by the semantic harness; the client only renders synchronized configuration and evidence.",
                16, noteY + 34, 620, MUTED);
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusBadge(graphics, diagnosticTitle(), diagnosticColor(), 16, 80);
        labelValue(graphics, "Live input", menu.input() + " / 15", 105);
        labelValue(graphics, "Live output", menu.output() + " / 15", 121);
        labelValue(graphics, "Input quality", qualityName(menu.inputQuality()), 137);
        labelValue(graphics, "Output quality", qualityName(menu.outputQuality()), 153);
        labelValue(graphics, "Mode / parameter", modeName(menu.mode()) + " • " + parameterText(menu.mode(), menu.parameter()), 169);
        statusLine(graphics, "0..15 boundary", boundaryState(), boundaryColor(), 190);
        labelValue(graphics, "Limiting episodes / last",
                menu.limitingEpisodes() + " / " + (menu.lastLimitingAgeTicks() < 0 ? "never" : menu.lastLimitingAgeTicks() + "t ago"), 211);
        wrappedText(graphics, diagnosticNextAction(), 16, 235, 620, diagnosticColor());
    }

    private void renderHistory(GuiGraphics graphics) {
        statusBadge(graphics, "LIVE STATE / EXTERNAL HISTORY", INFO, 16, 80);
        int noteY = wrappedText(graphics, "The conditioner exposes the complete current transfer state above.", 16, 108, 620, TEXT);
        noteY = wrappedText(graphics, "For time history, place Probe / Analyzer / Oscilloscope on the series path.", 16, noteY + 4, 620, INFO);
        sectionRule(graphics, noteY + 8);
        noteY = wrappedText(graphics, "Current state = input + transfer + output + rigid I/O axis + explicit PortQuality.", 16, noteY + 20, 620, MUTED);
        labelValue(graphics, "Boundary limiting episodes / last",
                menu.limitingEpisodes() + " / " + (menu.lastLimitingAgeTicks() < 0 ? "never" : menu.lastLimitingAgeTicks() + "t ago"), noteY + 8);
        wrappedText(graphics, "A valid zero is data; NO_SIGNAL / STALE / topology evidence remain separate states.", 16, noteY + 32, 620, GOOD);
    }

    private String boundaryState() {
        if (menu.outputQuality() == PortQuality.SATURATED || menu.limiting()) return "SATURATED • WORLD BOUNDARY ACTIVE";
        if (menu.outputQuality() != PortQuality.VALID) return qualityName(menu.outputQuality());
        if (menu.output() == 0) return "VALID ZERO";
        if (menu.output() == 15) return "VALID FULL-SCALE";
        return "IN RANGE";
    }

    private String diagnosticTitle() {
        if (menu.inputQuality() != PortQuality.VALID) return "INPUT EVIDENCE • " + qualityName(menu.inputQuality());
        if (menu.outputQuality() == PortQuality.SATURATED || menu.limiting()) return "OUTPUT SATURATED";
        if (menu.outputQuality() != PortQuality.VALID) return "OUTPUT EVIDENCE • " + qualityName(menu.outputQuality());
        return "TRANSFER EVIDENCE COHERENT";
    }

    private int diagnosticColor() {
        PortQuality input = menu.inputQuality();
        PortQuality output = menu.outputQuality();
        if (severe(input) || severe(output)) return BAD;
        if (input != PortQuality.VALID || output != PortQuality.VALID) return WARN;
        return GOOD;
    }

    private String diagnosticNextAction() {
        if (menu.inputQuality() != PortQuality.VALID)
            return "NEXT • restore trustworthy upstream Redstone evidence before changing the transfer parameter.";
        if (menu.outputQuality() == PortQuality.SATURATED || menu.limiting())
            return "NEXT • decide whether boundary limiting is intentional; compare input, transfer mode and retained clip episodes before retuning.";
        if (menu.outputQuality() != PortQuality.VALID)
            return "NEXT • resolve output evidence quality before interpreting the numerical level.";
        return "NEXT • transfer evidence is coherent; compare the selected model against the same input stimulus before changing configuration.";
    }

    private static String qualityName(PortQuality quality) {
        return quality.name().replace('_', ' ');
    }

    private static boolean severe(PortQuality quality) {
        return quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    private int boundaryColor() {
        if (severe(menu.outputQuality())) return BAD;
        return menu.outputQuality() == PortQuality.VALID && !menu.limiting() ? GOOD : WARN;
    }

    private static String direction(String name) {
        return name.toUpperCase();
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "LEGACY SCALE";
            case 1 -> "OFFSET";
            case 2 -> "CLAMP";
            case 3 -> "THRESHOLD";
            case 4 -> "DEADBAND";
            case 5 -> "ATTENUATE";
            default -> "UNKNOWN";
        };
    }

    private static String parameterName(int mode) {
        return switch (mode) {
            case 0 -> "Legacy scale";
            case 1 -> "Offset";
            case 2 -> "Clamp ceiling";
            case 3 -> "Trip level";
            case 4 -> "Deadband width";
            case 5 -> "Attenuation ratio";
            default -> "Parameter";
        };
    }

    private static String parameterShortName(int mode) {
        return switch (mode) {
            case 0 -> "Scale";
            case 1 -> "Offset";
            case 2 -> "Ceiling";
            case 3 -> "Trip";
            case 4 -> "Band";
            case 5 -> "Divide";
            default -> "Param";
        };
    }

    private static String parameterRange(int mode) {
        int min = SignalConditionerLogic.minParameter(mode);
        int max = SignalConditionerLogic.maxParameter(mode);
        return switch (mode) {
            case 0 -> "×" + min + " .. ×" + max;
            case 1 -> (min - 5) + " .. +" + (max - 5);
            case 2, 3, 4 -> min + " .. " + max;
            case 5 -> "÷" + min + " .. ÷" + max;
            default -> "—";
        };
    }

    private static String modelLine(int mode, int param) {
        int p = SignalConditionerLogic.boundedParameter(mode, param);
        return switch (mode) {
            case 0 -> "MODEL • y = clamp(x × " + p + ", 0, 15)";
            case 1 -> {
                int offset = p - 5;
                yield "MODEL • y = clamp(x " + (offset >= 0 ? "+ " : "− ") + Math.abs(offset) + ", 0, 15)";
            }
            case 2 -> "MODEL • y = min(x, " + p + ")";
            case 3 -> "MODEL • y = x when x ≥ " + p + "; otherwise y = 0";
            case 4 -> "MODEL • if |x − yprev| ≥ " + p + ", y = x; otherwise retain yprev";
            case 5 -> "MODEL • y = round(x / " + p + ")";
            default -> "MODEL • y = x";
        };
    }

    private static String parameterContract(int mode, int stored) {
        int effective = SignalConditionerLogic.boundedParameter(mode, stored);
        return stored == effective
                ? "stored " + stored + " = effective " + effective
                : "legacy stored " + stored + " → effective " + effective;
    }

    private static String boundaryContract(int mode, int param) {
        int p = SignalConditionerLogic.boundedParameter(mode, param);
        return switch (mode) {
            case 0 -> "SATURATED only when x × " + p + " > 15";
            case 1 -> {
                int offset = p - 5;
                yield "SATURATED only when raw x " + (offset >= 0 ? "+ " : "− ")
                        + Math.abs(offset) + " leaves 0..15";
            }
            case 2 -> "SATURATED when x > ceiling " + p;
            case 3 -> "threshold LOW is valid transfer semantics, not saturation";
            case 4 -> "deadband hold is stateful transfer semantics, not saturation";
            case 5 -> "attenuation is in-range transfer semantics, not saturation";
            default -> "no boundary-limiting evidence";
        };
    }

    private static String behaviorLine(int mode) {
        return switch (mode) {
            case 0 -> "LEGACY SCALE: retained for world compatibility; use Signal Amplifier when gain itself is the engineering task.";
            case 1 -> "OFFSET: add signed correction, then enforce the vanilla 0..15 boundary.";
            case 2 -> "CLAMP: pass input until the configured ceiling is reached.";
            case 3 -> "THRESHOLD: pass values at/above trip; otherwise emit a valid zero.";
            case 4 -> "DEADBAND: hold output until the input change exceeds the selected band.";
            case 5 -> "ATTENUATE: reduce full-scale signal range by an integer divider with rounded output.";
            default -> "Unknown conditioning mode.";
        };
    }

    private static String parameterText(int mode, int param) {
        return switch (mode) {
            case 0 -> "×" + SignalConditionerLogic.boundedParameter(0, param);
            case 1 -> (SignalConditionerLogic.boundedParameter(1, param) - 5 >= 0 ? "+" : "") + (SignalConditionerLogic.boundedParameter(1, param) - 5);
            case 2 -> "MAX " + SignalConditionerLogic.boundedParameter(2, param);
            case 3 -> "TRIP ≥ " + SignalConditionerLogic.boundedParameter(3, param);
            case 4 -> "BAND " + SignalConditionerLogic.boundedParameter(4, param);
            case 5 -> "÷" + SignalConditionerLogic.boundedParameter(5, param);
            default -> "—";
        };
    }
}
