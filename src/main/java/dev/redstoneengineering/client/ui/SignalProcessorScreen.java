package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated series-processor HMI for Precision Filter, Edge Detector, and Pulse Shaper. */
public final class SignalProcessorScreen extends EngineeringScreen<SignalProcessorMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private Button rotateLeft;
    private Button rotateRight;

    public SignalProcessorScreen(SignalProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_PARAMETER_PREVIOUS))
                .bounds(leftPos + 16, y, 110, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_PARAMETER_NEXT))
                .bounds(leftPos + 194, y, 110, 20).build());
        rotateLeft = addConfigureWidget(Button.builder(Component.literal("↺ I/O"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_ROTATE_LEFT))
                .bounds(leftPos + 70, y + 30, 80, 20).build());
        rotateRight = addConfigureWidget(Button.builder(Component.literal("I/O ↻"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_ROTATE_RIGHT))
                .bounds(leftPos + 170, y + 30, 80, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        String parameter = parameterName() + " " + parameterValue();
        parameterPrevious.setMessage(Component.literal("◀ " + parameter));
        parameterNext.setMessage(Component.literal(parameter + " ▶"));
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
        g.drawString(font, "All values are synchronized server evidence; processing stays inside the block tick.",
                16, 199, MUTED, false);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "STRICT SERIES I/O", GOOD, 16, 80);
        statusLine(g, menu.inputDirection().getName().toUpperCase(), "INPUT • REDSTONE 0..15", GOOD, 112);
        statusLine(g, "PROCESS", processDescription(), INFO, 140);
        statusLine(g, menu.outputDirection().getName().toUpperCase(), "OUTPUT • REDSTONE 0..15", GOOD, 168);
        g.drawString(font, "Rotating I/O rotates the complete INPUT → PROCESS → OUTPUT axis.", 16, 198, MUTED, false);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        labelValue(g, parameterName(), parameterValue(), 101);
        labelValue(g, "Input face", menu.inputDirection().getName().toUpperCase(), 171);
        labelValue(g, "Output face", menu.outputDirection().getName().toUpperCase(), 187);
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
            g.drawString(font, "Edge chronology is retained by server runtime; opening this UI never creates an edge.", 16, 184, MUTED, false);
        } else if (menu.kind() == SignalProcessorMenu.KIND_PULSE) {
            labelValue(g, "Last input", Integer.toString(menu.runtimeB()), 110);
            labelValue(g, "Pulse remaining", menu.runtimeA() + " ticks", 130);
            labelValue(g, "Initialized", menu.initialized() ? "YES" : "NO", 150);
            sectionRule(g, 170);
            g.drawString(font, "Readback is observer-neutral and never initializes the one-shot runtime.", 16, 184, MUTED, false);
        } else {
            labelValue(g, "Current lag", menu.runtimeA() + " levels", 110);
            labelValue(g, "Response", menu.runtimeB() == 1 ? "SETTLED" : "SETTLING", 130);
            sectionRule(g, 154);
            g.drawString(font, "Slew lag is live state; no artificial time-series is created by the client.", 16, 170, MUTED, false);
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
