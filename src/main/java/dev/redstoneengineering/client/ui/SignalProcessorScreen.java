package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.signal.PrecisionFilterLogic;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated series-processor HMI for Precision Filter, Edge Detector, and Pulse Shaper. */
public final class SignalProcessorScreen extends EngineeringScreen<SignalProcessorMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private Button thresholdPrevious;
    private Button thresholdNext;
    private Button hysteresisPrevious;
    private Button hysteresisNext;
    private Button retriggerToggle;
    private Button fallRatePrevious;
    private Button fallRateNext;
    private Button edgeWidthPrevious;
    private Button edgeWidthNext;

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

        thresholdPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Trigger threshold"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_THRESHOLD_PREVIOUS))
                .bounds(leftPos + 16, topPos + 140, 110, 20).build());
        thresholdNext = addConfigureWidget(Button.builder(Component.literal("Trigger threshold ▶"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_THRESHOLD_NEXT))
                .bounds(leftPos + 194, topPos + 140, 110, 20).build());
        hysteresisPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Hysteresis"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_PULSE_HYSTERESIS_PREVIOUS))
                .bounds(leftPos + 16, topPos + 164, 110, 20).build());
        hysteresisNext = addConfigureWidget(Button.builder(Component.literal("Hysteresis ▶"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_PULSE_HYSTERESIS_NEXT))
                .bounds(leftPos + 194, topPos + 164, 110, 20).build());
        retriggerToggle = addConfigureWidget(Button.builder(Component.literal("Retrigger"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_TOGGLE_RETRIGGER))
                .bounds(leftPos + 102, topPos + 188, 116, 20).build());

        fallRatePrevious = addConfigureWidget(Button.builder(Component.literal("◀ Fall rate"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_FILTER_FALL_PREVIOUS))
                .bounds(leftPos + 16, topPos + 140, 110, 20).build());
        fallRateNext = addConfigureWidget(Button.builder(Component.literal("Fall rate ▶"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_FILTER_FALL_NEXT))
                .bounds(leftPos + 194, topPos + 140, 110, 20).build());

        edgeWidthPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Pulse width"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_EDGE_WIDTH_PREVIOUS))
                .bounds(leftPos + 16, topPos + 140, 110, 20).build());
        edgeWidthNext = addConfigureWidget(Button.builder(Component.literal("Pulse width ▶"),
                b -> sendMenuButton(SignalProcessorMenu.BUTTON_EDGE_WIDTH_NEXT))
                .bounds(leftPos + 194, topPos + 140, 110, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        String parameter = parameterName() + " " + parameterValue();
        parameterPrevious.setMessage(Component.literal(fitForWidth("◀ " + parameter, 94)));
        parameterNext.setMessage(Component.literal(fitForWidth(parameter + " ▶", 94)));

        boolean pulse = menu.kind() == SignalProcessorMenu.KIND_PULSE;
        boolean filter = menu.kind() == SignalProcessorMenu.KIND_FILTER;
        boolean edge = menu.kind() == SignalProcessorMenu.KIND_EDGE;
        thresholdPrevious.visible = pulse;
        thresholdNext.visible = pulse;
        hysteresisPrevious.visible = pulse;
        hysteresisNext.visible = pulse;
        retriggerToggle.visible = pulse;
        thresholdPrevious.active = pulse;
        thresholdNext.active = pulse;
        hysteresisPrevious.active = pulse;
        hysteresisNext.active = pulse;
        retriggerToggle.active = pulse;
        fallRatePrevious.visible = filter;
        fallRateNext.visible = filter;
        fallRatePrevious.active = filter;
        fallRateNext.active = filter;
        edgeWidthPrevious.visible = edge;
        edgeWidthNext.visible = edge;
        edgeWidthPrevious.active = edge;
        edgeWidthNext.active = edge;
        if (pulse) {
            String threshold = "Trigger threshold " + menu.secondaryParameter() + "/15";
            thresholdPrevious.setMessage(Component.literal(fitForWidth("◀ " + threshold, 94)));
            thresholdNext.setMessage(Component.literal(fitForWidth(threshold + " ▶", 94)));
            String hysteresis = "Hysteresis " + menu.tertiaryParameter();
            hysteresisPrevious.setMessage(Component.literal(fitForWidth("◀ " + hysteresis, 94)));
            hysteresisNext.setMessage(Component.literal(fitForWidth(hysteresis + " ▶", 94)));
            retriggerToggle.setMessage(Component.literal("Retrigger: " + (menu.modeFlag() ? "YES" : "NO")));
        }
        if (filter) {
            String fall = "Fall rate " + menu.secondaryParameter();
            fallRatePrevious.setMessage(Component.literal(fitForWidth("◀ " + fall, 94)));
            fallRateNext.setMessage(Component.literal(fitForWidth(fall + " ▶", 94)));
        }
        if (edge) {
            String width = "Pulse width " + menu.secondaryParameter() + "t";
            edgeWidthPrevious.setMessage(Component.literal(fitForWidth("◀ " + width, 94)));
            edgeWidthNext.setMessage(Component.literal(fitForWidth(width + " ▶", 94)));
        }
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
        if (menu.kind() == SignalProcessorMenu.KIND_PULSE) {
            wrappedText(g, "Trigger " + menu.secondaryParameter() + "/15 • re-arm ≤" + pulseRearmThreshold()
                    + "/15 • hysteresis " + menu.tertiaryParameter()
                    + " • retrigger " + (menu.modeFlag() ? "enabled" : "blocked while busy"), 16, 188, 620, MUTED);
        } else if (menu.kind() == SignalProcessorMenu.KIND_FILTER) {
            wrappedText(g, "Rise " + menu.parameter() + " • fall " + menu.secondaryParameter()
                    + " level/tick • settle ETA " + menu.runtimeC() + "t", 16, 188, 620, MUTED);
        } else {
            wrappedText(g, "All values are synchronized server evidence; processing stays inside the block tick.", 16, 199, 620, MUTED);
        }
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "STRICT SERIES I/O", GOOD, 16, 80);
        statusLine(g, menu.inputDirection().getName().toUpperCase(), "INPUT • REDSTONE 0..15", GOOD, 112);
        statusLine(g, "PROCESS", processDescription(), INFO, 140);
        statusLine(g, menu.outputDirection().getName().toUpperCase(), "OUTPUT • REDSTONE 0..15", GOOD, 168);
        wrappedText(g, "Route rotates one rigid INPUT → PROCESS → OUTPUT axis; INPUT remains exactly opposite OUTPUT.", 16, 198, 620, MUTED);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        labelValue(g, parameterName(), parameterValue(), 101);
        if (menu.kind() == SignalProcessorMenu.KIND_PULSE) {
            wrappedText(g, "Trigger threshold fires the one-shot; hysteresis sets the lower re-arm level so noisy or bouncing inputs cannot chatter.", 16, 214, 620, MUTED);
        } else if (menu.kind() == SignalProcessorMenu.KIND_FILTER) {
            labelValue(g, "Fall rate Rfall", menu.secondaryParameter() + " level/tick", 181);
            labelValue(g, "Allowed rates",
                    PrecisionFilterLogic.MIN_RATE + ".." + PrecisionFilterLogic.MAX_RATE + " level/tick", 201);
            labelValue(g, "Response law",
                    "rise: y[k+1]=min(x,y+Rrise) • fall: y[k+1]=max(x,y-Rfall)", 221);
            labelValue(g, "ETA model",
                    "ceil(|x-y| / Rdir) = " + (menu.runtimeC() < 0 ? "UNAVAILABLE" : menu.runtimeC() + " ticks"), 241);
            wrappedText(g,
                    "Rrise and Rfall are exact server-owned slew limits. Legacy over-range stored values are interpreted at the effective 1..4 model boundary; invalid input evidence retains the last physical output instead of becoming numerical zero.",
                    16, 265, 620, MUTED);
        } else {
            labelValue(g, "Pulse width", menu.secondaryParameter() + " ticks", 181);
            labelValue(g, "Input face", menu.inputDirection().getName().toUpperCase(), 201);
            labelValue(g, "Output face", menu.outputDirection().getName().toUpperCase(), 219);
            wrappedText(g, "Physical direction is controlled only on Route; the series endpoints remain exactly opposite.", 16, 239, 620, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, stateName(), stateColor(), 16, 80);
        labelValue(g, "Input", menu.input() + " / 15", 108);
        labelValue(g, "Output", menu.output() + " / 15", 126);
        labelValue(g, parameterName(), parameterValue(), 144);
        runtimeSummary(g, 162);
        if (menu.kind() == SignalProcessorMenu.KIND_PULSE) {
            labelValue(g, "Trigger / re-arm", menu.secondaryParameter() + " / " + pulseRearmThreshold(), 180);
            labelValue(g, "Hysteresis", menu.tertiaryParameter() + " levels", 198);
            labelValue(g, "Retrigger", menu.modeFlag() ? "ENABLED" : "BLOCK WHILE BUSY", 216);
        } else if (menu.kind() == SignalProcessorMenu.KIND_FILTER) {
            labelValue(g, "Fall rate", menu.secondaryParameter() + " level/tick", 180);
            labelValue(g, "Settle ETA", menu.runtimeC() + " ticks", 198);
        } else {
            labelValue(g, "Pulse width", menu.secondaryParameter() + " ticks", 180);
            labelValue(g, "Rejected evidence", Integer.toString(menu.runtimeD()), 198);
            statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 216);
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "DEVICE EVIDENCE", INFO, 16, 80);
        if (menu.kind() == SignalProcessorMenu.KIND_EDGE) {
            labelValue(g, "Detected edges", Integer.toString(menu.runtimeB()), 110);
            labelValue(g, "Last edge age", menu.runtimeC() < 0 ? "NONE" : menu.runtimeC() + " ticks", 130);
            labelValue(g, "Pulse remaining", menu.runtimeA() + " ticks", 150);
            labelValue(g, "Pulse width", menu.secondaryParameter() + " ticks", 170);
            labelValue(g, "Rejected evidence episodes", Integer.toString(menu.runtimeD()), 190);
            sectionRule(g, 210);
            wrappedText(g, "Bad or stale evidence resets the baseline without manufacturing an edge; rejected episodes remain explicit server evidence.", 16, 224, 620, MUTED);
        } else if (menu.kind() == SignalProcessorMenu.KIND_PULSE) {
            labelValue(g, "Accepted triggers", Integer.toString(menu.runtimeB()), 106);
            labelValue(g, "Suppressed triggers", Integer.toString(menu.runtimeC()), 126);
            labelValue(g, "Last trigger age", menu.runtimeD() < 0 ? "NONE" : menu.runtimeD() + " ticks", 146);
            labelValue(g, "Pulse remaining", menu.runtimeA() + " ticks", 166);
            sectionRule(g, 184);
            wrappedText(g, "Suppressed triggers are threshold crossings rejected only because non-retriggerable mode was busy.", 16, 196, 620, MUTED);
        } else {
            labelValue(g, "Current lag", menu.runtimeA() + " levels", 104);
            labelValue(g, "Tracking error", Integer.toString(menu.input() - menu.output()), 124);
            labelValue(g, "Response", filterDirection(), 144);
            labelValue(g, "Settle ETA", menu.runtimeC() + " ticks", 164);
            sectionRule(g, 182);
            wrappedText(g, "Rise/fall slew limits are physical response settings; error and ETA are live server evidence.", 16, 194, 620, MUTED);
        }
    }

    private void runtimeSummary(GuiGraphics g, int y) {
        if (menu.kind() == SignalProcessorMenu.KIND_FILTER) {
            labelValue(g, "Lag / state", menu.runtimeA() + " • " + (menu.runtimeB() == 1 ? "SETTLED" : "SETTLING"), y);
        } else if (menu.kind() == SignalProcessorMenu.KIND_EDGE) {
            labelValue(g, "Pulse / edges", menu.runtimeA() + "t • " + menu.runtimeB(), y);
        } else {
            labelValue(g, "Pulse / accepted", menu.runtimeA() + "t • " + menu.runtimeB(), y);
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
            default -> "Rise rate";
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
            case SignalProcessorMenu.KIND_EDGE -> "EDGE DETECTION • " + parameterValue()
                    + " • pulse=" + menu.secondaryParameter() + "t";
            case SignalProcessorMenu.KIND_PULSE -> "SCHMITT MONOSTABLE • width=" + parameterValue()
                    + " • trigger=" + menu.secondaryParameter() + "/15"
                    + " • rearm≤" + pulseRearmThreshold() + "/15"
                    + " • retrigger=" + (menu.modeFlag() ? "YES" : "NO");
            default -> "SLEW LIMIT • rise=" + parameterValue()
                    + " • fall=" + menu.secondaryParameter() + " level/tick";
        };
    }

    private int pulseRearmThreshold() {
        return Math.max(0, menu.secondaryParameter() - Math.max(1, menu.tertiaryParameter()));
    }

    private String stateName() {
        if (!menu.initialized()) return "UNINITIALIZED";
        if (menu.kind() == SignalProcessorMenu.KIND_FILTER) return filterDirection();
        if (menu.runtimeA() > 0 || menu.output() > 0) return "PULSE ACTIVE";
        return "READY";
    }

    private String filterDirection() {
        return menu.runtimeD() > 0 ? "RISING" : menu.runtimeD() < 0 ? "FALLING" : "SETTLED";
    }

    private int stateColor() {
        if (!menu.initialized()) return WARN;
        if (menu.kind() == SignalProcessorMenu.KIND_FILTER) return menu.runtimeD() == 0 ? GOOD : INFO;
        return (menu.runtimeA() > 0 || menu.output() > 0) ? INFO : GOOD;
    }
}
