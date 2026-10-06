package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated series-processor HMI for Precision Filter, Edge Detector, and Pulse Shaper. */
public final class SignalProcessorScreen extends EngineeringScreen<SignalProcessorMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private EditBox parameterInput;
    private Button parameterApply;

    public SignalProcessorScreen(SignalProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + imageHeight - 66;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS))
                .bounds(leftPos + 16, y, 82, 20).build());
        parameterInput = addConfigureWidget(new EditBox(this.font, leftPos + 104, y, 82, 20,
                Component.literal("Exact parameter")));
        parameterInput.setMaxLength(2);
        parameterInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        parameterApply = addConfigureWidget(Button.builder(Component.literal("Apply"),
                b -> submitParameter()).bounds(leftPos + 192, y, 54, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_PARAMETER_NEXT))
                .bounds(leftPos + 252, y, 82, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null || parameterNext == null) return;
        String parameter = parameterName() + " " + parameterValue();
        boolean direct = menu.kind() != SignalProcessorMenu.KIND_EDGE;
        parameterPrevious.visible = !direct;
        parameterNext.visible = !direct;
        if (!direct) {
            parameterPrevious.setMessage(Component.literal(fitForWidth("◀ " + parameter, 66)));
            parameterNext.setMessage(Component.literal(fitForWidth(parameter + " ▶", 66)));
        }
        if (parameterInput != null) {
            parameterInput.visible = direct;
            parameterInput.active = direct;
            if (direct && !parameterInput.isFocused()) {
                String expected = Integer.toString(menu.parameter());
                if (!expected.equals(parameterInput.getValue())) parameterInput.setValue(expected);
            }
        }
        if (parameterApply != null) {
            parameterApply.visible = direct;
            parameterApply.active = direct && parameterInputValid();
            parameterApply.setMessage(Component.literal("Apply " + parameterSymbol()));
        }
    }

    private boolean parameterInputValid() {
        if (parameterInput == null || parameterInput.getValue().isEmpty()) return false;
        try {
            int value = Integer.parseInt(parameterInput.getValue());
            return menu.kind() == SignalProcessorMenu.KIND_FILTER
                    ? value >= 1 && value <= 4
                    : menu.kind() == SignalProcessorMenu.KIND_PULSE && value >= 1 && value <= 8;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void submitParameter() {
        if (!parameterInputValid()) return;
        int value = Integer.parseInt(parameterInput.getValue());
        sendMenuButton(SignalProcessorMenu.BUTTON_PARAMETER_DIRECT_BASE + value);
        parameterInput.setFocused(false);
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> overview(graphics);
            case PORTS -> ports(graphics);
            case CONFIGURE -> configure(graphics);
            case DIAGNOSTICS -> diagnostics(graphics);
            case HISTORY -> history(graphics);
        }
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceType(), GOOD, 16, 80);
        statusBadge(g, stateName(), stateColor(), 190, 80);
        metricCard(g, "Input", menu.input() + " / 15", 16, 103, 88, INFO);
        metricCard(g, "Output", menu.output() + " / 15", 111, 103, 88, GOOD);
        metricCard(g, parameterName(), parameterValue(), 206, 103, 88, INFO);
        labelValue(g, "Series path", menu.inputDirection().getName().toUpperCase() + " → "
                + menu.outputDirection().getName().toUpperCase(), 148);
        runtimeSummary(g, 166);
        safeText(g, "All values are synchronized server evidence; processing stays inside the block tick.", 16, 199, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "STRICT SERIES I/O", GOOD, 16, 80);
        statusLine(g, menu.inputDirection().getName().toUpperCase(), "INPUT • REDSTONE 0..15", GOOD, 112);
        statusLine(g, "PROCESS", processDescription(), INFO, 140);
        statusLine(g, menu.outputDirection().getName().toUpperCase(), "OUTPUT • REDSTONE 0..15", GOOD, 168);
        safeText(g, "Direction rotates the complete INPUT → PROCESS → OUTPUT axis.", 16, 198, MUTED);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "PIONEER PATTERN • SIGNAL PROCESSOR MODEL", INFO, 16, 80);
        formulaCard(g, processorEquation(), 105);
        variableRole(g, "MEASURED", "x[n]", Integer.toString(menu.input()), "Redstone 0..15", 134);
        variableRole(g, "DERIVED", "y[n]", Integer.toString(menu.output()), "Redstone 0..15", 152);
        variableRole(g, "ADJUSTABLE", parameterSymbol(), parameterValue(), parameterMeaning(), 170);
        if (menu.kind() != SignalProcessorMenu.KIND_EDGE) {
            variableRole(g, "CONTROL", "direct entry",
                    menu.kind() == SignalProcessorMenu.KIND_FILTER ? "r ∈ 1..4" : "W ∈ 1..8 ticks",
                    "exact server-backed value", 188);
        }
        if (menu.kind() == SignalProcessorMenu.KIND_FILTER) {
            variableRole(g, "DERIVED", "|x-y|", Integer.toString(menu.runtimeA()), "lag levels", 188);
            variableRole(g, "EVIDENCE", "response", menu.runtimeB() == 1 ? "SETTLED" : "SETTLING", "", 206);
        } else if (menu.kind() == SignalProcessorMenu.KIND_EDGE) {
            variableRole(g, "RUNTIME", "pulse", menu.runtimeA() + "t remaining", "", 188);
            variableRole(g, "EVIDENCE", "edges", Integer.toString(menu.runtimeB()),
                    menu.runtimeC() < 0 ? "no retained edge" : "last age " + menu.runtimeC() + "t", 206);
        } else {
            variableRole(g, "RUNTIME", "pulse", menu.runtimeA() + "t remaining", "", 188);
            variableRole(g, "EVIDENCE", "initialized", menu.initialized() ? "YES" : "NO",
                    "last input " + menu.runtimeB(), 206);
        }
        evidenceRow(g, "Route", menu.inputDirection().getName().toUpperCase(),
                menu.outputDirection().getName().toUpperCase(), "physical direction lives on Route", 230);
        wrappedText(g, processorEvidenceContract(), 16, 256, workspaceWidth() - 24, MUTED);
    }

    private String processorEquation() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE ->
                    "e[n] = edge_mode(x[n-1], x[n]); e[n] ⇒ y=15 for 2 ticks";
            case SignalProcessorMenu.KIND_PULSE ->
                    "rising edge(x) ⇒ y=15 for W ticks; otherwise y=0";
            default ->
                    "y[n+1] = y[n] + clamp(x[n]-y[n], -r, +r)";
        };
    }

    private String parameterSymbol() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE -> "mode";
            case SignalProcessorMenu.KIND_PULSE -> "W";
            default -> "r";
        };
    }

    private String parameterMeaning() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE -> "RISING / FALLING / BOTH";
            case SignalProcessorMenu.KIND_PULSE -> "one-shot width in ticks";
            default -> "maximum signal change per tick";
        };
    }

    private String processorEvidenceContract() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE ->
                    "Edge chronology is retained by server runtime; opening diagnostics never initializes the detector or creates a false edge.";
            case SignalProcessorMenu.KIND_PULSE ->
                    "The one-shot triggers only on a real LOW→HIGH transition. Observer readback never initializes or retriggers runtime state.";
            default ->
                    "The filter owns response speed, not gain or offset: temporary input/output lag is expected until the server-authoritative slew response settles.";
        };
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, stateName(), stateColor(), 16, 80);
        labelValue(g, "Input", menu.input() + " / 15", 108);
        labelValue(g, "Output", menu.output() + " / 15", 126);
        labelValue(g, parameterName(), parameterValue(), 144);
        runtimeSummary(g, 162);
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 198);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "DEVICE EVIDENCE", INFO, 16, 80);
        if (menu.kind() == SignalProcessorMenu.KIND_EDGE) {
            labelValue(g, "Detected edges", Integer.toString(menu.runtimeB()), 110);
            labelValue(g, "Last edge age", menu.runtimeC() < 0 ? "NONE" : menu.runtimeC() + " ticks", 130);
            labelValue(g, "Pulse remaining", menu.runtimeA() + " ticks", 150);
            sectionRule(g, 170);
            safeText(g, "Edge chronology is retained by server runtime; opening this UI never creates an edge.", 16, 184, MUTED);
        } else if (menu.kind() == SignalProcessorMenu.KIND_PULSE) {
            labelValue(g, "Last input", Integer.toString(menu.runtimeB()), 110);
            labelValue(g, "Pulse remaining", menu.runtimeA() + " ticks", 130);
            labelValue(g, "Initialized", menu.initialized() ? "YES" : "NO", 150);
            sectionRule(g, 170);
            safeText(g, "Readback is observer-neutral and never initializes the one-shot runtime.", 16, 184, MUTED);
        } else {
            labelValue(g, "Current lag", menu.runtimeA() + " levels", 110);
            labelValue(g, "Response", menu.runtimeB() == 1 ? "SETTLED" : "SETTLING", 130);
            sectionRule(g, 154);
            safeText(g, "Slew lag is live state; no artificial time-series is created by the client.", 16, 170, MUTED);
        }
    }

    private void runtimeSummary(GuiGraphics g, int y) {
        if (menu.kind() == SignalProcessorMenu.KIND_FILTER) {
            labelValue(g, "Lag / state", menu.runtimeA() + " • " + (menu.runtimeB() == 1 ? "SETTLED" : "SETTLING"), y);
        } else if (menu.kind() == SignalProcessorMenu.KIND_EDGE) {
            labelValue(g, "Pulse / edges", menu.runtimeA() + "t • " + menu.runtimeB(), y);
        } else {
            labelValue(g, "Pulse remaining", menu.runtimeA() + " ticks", y);
        }
    }

    private String deviceType() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE -> "EDGE DETECTOR";
            case SignalProcessorMenu.KIND_PULSE -> "PULSE SHAPER";
            default -> "PRECISION FILTER";
        };
    }

    private String parameterName() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE -> "Edge mode";
            case SignalProcessorMenu.KIND_PULSE -> "Pulse width";
            default -> "Slew rate";
        };
    }

    private String parameterValue() {
        if (menu.kind() == SignalProcessorMenu.KIND_EDGE) {
            return switch (menu.parameter()) {
                case 0 -> "RISING";
                case 1 -> "FALLING";
                default -> "BOTH";
            };
        }
        return menu.kind() == SignalProcessorMenu.KIND_PULSE
                ? menu.parameter() + " ticks"
                : menu.parameter() + " level/tick";
    }

    private String processDescription() {
        return switch (menu.kind()) {
            case SignalProcessorMenu.KIND_EDGE -> "EDGE DETECTION • " + parameterValue();
            case SignalProcessorMenu.KIND_PULSE -> "ONE-SHOT PULSE • " + parameterValue();
            default -> "SLEW LIMIT • " + parameterValue();
        };
    }

    private String stateName() {
        if (!menu.initialized()) return "UNINITIALIZED";
        if (menu.kind() == SignalProcessorMenu.KIND_FILTER) return menu.runtimeB() == 1 ? "SETTLED" : "SETTLING";
        if (menu.runtimeA() > 0) return "PULSE ACTIVE";
        return "READY";
    }

    private int stateColor() {
        return !menu.initialized() ? WARN : menu.runtimeA() > 0 ? INFO : GOOD;
    }
}
