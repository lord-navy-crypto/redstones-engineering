package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated pneumatic HMI spanning sources, valves, metrology, safety, and actuation. */
public final class PneumaticSystemScreen extends EngineeringScreen<PneumaticSystemMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private Button toggle;
    private Button directionCycle;

    public PneumaticSystemScreen(PneumaticSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Setpoint"),
                b -> sendMenuButton(PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS))
                .bounds(leftPos + 16, y, 95, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Setpoint ▶"),
                b -> sendMenuButton(PneumaticSystemMenu.BUTTON_PARAMETER_NEXT))
                .bounds(leftPos + 209, y, 95, 20).build());
        toggle = addConfigureWidget(Button.builder(Component.literal("Toggle valve"),
                b -> sendMenuButton(PneumaticSystemMenu.BUTTON_TOGGLE))
                .bounds(leftPos + 100, y, 120, 20).build());
        directionCycle = addConfigureWidget(Button.builder(Component.literal("Direction • —"),
                b -> sendMenuButton(PneumaticSystemMenu.BUTTON_ROTATE_RIGHT))
                .bounds(leftPos + 70, y + 30, 180, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean setpoint = menu.kind() == PneumaticSystemMenu.KIND_REGULATOR
                || menu.kind() == PneumaticSystemMenu.KIND_RELIEF;
        boolean manualValve = menu.kind() == PneumaticSystemMenu.KIND_VALVE;
        boolean configure = isConfigureSection();

        parameterPrevious.active = setpoint;
        parameterNext.active = setpoint;
        parameterPrevious.visible = configure && setpoint;
        parameterNext.visible = configure && setpoint;
        toggle.active = manualValve;
        toggle.visible = configure && manualValve;
        directionCycle.active = menu.directional();
        directionCycle.visible = configure && menu.directional();

        if (setpoint) {
            String text = menu.kind() == PneumaticSystemMenu.KIND_REGULATOR
                    ? menu.secondary() + "/100" : menu.tertiary() + "/100";
            parameterPrevious.setMessage(Component.literal("◀ " + text));
            parameterNext.setMessage(Component.literal(text + " ▶"));
        }
        if (manualValve) {
            toggle.setMessage(Component.literal(menu.stateFlag() == 1 ? "Close valve" : "Open valve"));
        }
        if (menu.directional()) {
            directionCycle.setMessage(Component.literal("Direction • " + face(menu.outputDirection())));
            directionCycle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Cycle the declared pneumatic input/output path clockwise on the server.")));
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
        statusBadge(g, deviceName(), GOOD, 16, 80);
        statusBadge(g, stateName(), stateColor(), 205, 80);
        metricCard(g, primaryLabel(), primaryValue(), 16, 103, 88, INFO);
        metricCard(g, secondaryLabel(), secondaryValue(), 111, 103, 88, GOOD);
        metricCard(g, tertiaryLabel(), tertiaryValue(), 206, 103, 88, INFO);
        labelValue(g, "Topology", topologyText(), 149);
        labelValue(g, "Input evidence", menu.inputQuality().name(), 165);
        labelValue(g, "Output evidence", menu.outputQuality().name(), 181);
        safeText(g, hint(), 16, 199, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "PNEUMATIC INTERFACES", GOOD, 16, 80);
        if (!menu.directional()) {
            statusLine(g, "NETWORK", nondirectionalPortText(), INFO, 112);
            safeText(g, topologyText(), 16, 146, MUTED);
            return;
        }
        statusLine(g, face(menu.inputDirection()), inputPortText(), qualityColor(menu.inputQuality()), 112);
        statusLine(g, face(menu.outputDirection()), outputPortText(), qualityColor(menu.outputQuality()), 142);
        if (menu.kind() == PneumaticSystemMenu.KIND_PROPORTIONAL) {
            statusLine(g, "UP", "INPUT • REDSTONE OPENING COMMAND", INFO, 170);
        } else {
            safeText(g, topologyText(), 16, 176, MUTED);
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        labelValue(g, "Control", controlText(), 101);
        labelValue(g, "Topology", topologyText(), 171);
        if (menu.directional()) labelValue(g, "Axis", face(menu.inputDirection()) + " → " + face(menu.outputDirection()), 187);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, stateName(), stateColor(), 16, 80);
        labelValue(g, primaryLabel(), primaryValue(), 106);
        labelValue(g, secondaryLabel(), secondaryValue(), 124);
        labelValue(g, tertiaryLabel(), tertiaryValue(), 142);
        if (menu.kind() == PneumaticSystemMenu.KIND_FLOW_METER) {
            labelValue(g, "Outlet pressure", menu.auxiliary() + " / 100", 160);
            labelValue(g, "Samples", Integer.toString(menu.stateFlag()), 178);
        } else if (menu.kind() == PneumaticSystemMenu.KIND_RELIEF) {
            labelValue(g, "Vent events", Integer.toString(menu.auxiliary()), 160);
            labelValue(g, "Operating state", menu.stateFlag() == 1 ? "VENTING • VALID STATE" : "ARMED", 178);
        } else if (menu.kind() == PneumaticSystemMenu.KIND_CYLINDER) {
            labelValue(g, "Travel", Integer.toString(menu.auxiliary()), 160);
        } else if (menu.kind() == PneumaticSystemMenu.KIND_PROPORTIONAL) {
            labelValue(g, "Network pressure", menu.auxiliary() + " / 100", 160);
        }
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "PNEUMATIC EVIDENCE", INFO, 16, 80);
        if (menu.kind() == PneumaticSystemMenu.KIND_RELIEF) {
            labelValue(g, "Vent events", Integer.toString(menu.auxiliary()), 110);
            labelValue(g, "Current state", menu.stateFlag() == 1 ? "VENTING" : "ARMED", 130);
            sectionRule(g, 154);
            safeText(g, "VENTING is an operating event, not missing measurement evidence.", 16, 170, GOOD);
        } else if (menu.kind() == PneumaticSystemMenu.KIND_FLOW_METER) {
            labelValue(g, "Measurement samples", Integer.toString(menu.stateFlag()), 110);
            labelValue(g, "Current flow proxy", Integer.toString(menu.primary()), 130);
            sectionRule(g, 154);
            safeText(g, "Flow metrology history is server-retained; the HMI does not fabricate samples.", 16, 170, MUTED);
        } else {
            safeText(g, "This device exposes live server state; no artificial client-side history is created.", 16, 112, MUTED);
        }
    }

    private String deviceName() {
        return switch (menu.kind()) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> "AIR COMPRESSOR";
            case PneumaticSystemMenu.KIND_PIPE -> "PNEUMATIC PIPE";
            case PneumaticSystemMenu.KIND_RESERVOIR -> "AIR RESERVOIR";
            case PneumaticSystemMenu.KIND_REGULATOR -> "PRESSURE REGULATOR";
            case PneumaticSystemMenu.KIND_RECEIVER -> "PNEUMATIC RECEIVER";
            case PneumaticSystemMenu.KIND_VALVE -> "PNEUMATIC VALVE";
            case PneumaticSystemMenu.KIND_CHECK_VALVE -> "CHECK VALVE";
            case PneumaticSystemMenu.KIND_FLOW_METER -> "FLOW METER";
            case PneumaticSystemMenu.KIND_PROPORTIONAL -> "PROPORTIONAL VALVE";
            case PneumaticSystemMenu.KIND_RELIEF -> "RELIEF VALVE";
            case PneumaticSystemMenu.KIND_CYLINDER -> "PNEUMATIC CYLINDER";
            default -> "PNEUMATIC DEVICE";
        };
    }

    private String primaryLabel() {
        return switch (menu.kind()) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> "Command";
            case PneumaticSystemMenu.KIND_RESERVOIR -> "Stored";
            case PneumaticSystemMenu.KIND_FLOW_METER -> "Flow";
            case PneumaticSystemMenu.KIND_CYLINDER -> "Pressure";
            default -> menu.directional() ? "Inlet" : "Pressure";
        };
    }
    private String secondaryLabel() {
        return switch (menu.kind()) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> "Set pressure";
            case PneumaticSystemMenu.KIND_RESERVOIR -> "Network";
            case PneumaticSystemMenu.KIND_REGULATOR -> "Setpoint";
            case PneumaticSystemMenu.KIND_FLOW_METER -> "Δ pressure";
            case PneumaticSystemMenu.KIND_CYLINDER -> "Position";
            case PneumaticSystemMenu.KIND_RECEIVER -> "Redstone out";
            default -> menu.directional() ? "Outlet" : "Aux";
        };
    }
    private String tertiaryLabel() {
        return switch (menu.kind()) {
            case PneumaticSystemMenu.KIND_COMPRESSOR -> "Actual";
            case PneumaticSystemMenu.KIND_FLOW_METER -> "Inlet P";
            case PneumaticSystemMenu.KIND_PROPORTIONAL -> "Opening";
            case PneumaticSystemMenu.KIND_RELIEF -> "Relief";
            case PneumaticSystemMenu.KIND_CYLINDER -> "Target";
            default -> "State";
        };
    }
    private String primaryValue() { return value(menu.primary(), primaryLabel()); }
    private String secondaryValue() { return value(menu.secondary(), secondaryLabel()); }
    private String tertiaryValue() { return value(menu.tertiary(), tertiaryLabel()); }
    private String value(int v, String label) {
        return label.contains("Command") || label.contains("Redstone") || label.contains("Opening") ? v + " / 15" : v + " / 100";
    }
    private String topologyText() {
        if (!menu.directional()) {
            return menu.kind() == PneumaticSystemMenu.KIND_REGULATOR ? "SIX-WAY BIDIRECTIONAL REGULATED NODE" : "NETWORK NODE";
        }
        return face(menu.inputDirection()) + " → " + face(menu.outputDirection());
    }
    private String nondirectionalPortText() {
        return menu.kind() == PneumaticSystemMenu.KIND_REGULATOR ? "6× BIDIRECTIONAL PNEUMATIC" : "PNEUMATIC NETWORK";
    }
    private String inputPortText() { return "INPUT • PNEUMATIC"; }
    private String outputPortText() {
        return menu.kind() == PneumaticSystemMenu.KIND_RECEIVER ? "OUTPUT • REDSTONE 0..15" : "OUTPUT • PNEUMATIC";
    }
    private String controlText() {
        return switch (menu.kind()) {
            case PneumaticSystemMenu.KIND_REGULATOR -> "SETPOINT " + menu.secondary() + "/100";
            case PneumaticSystemMenu.KIND_RELIEF -> "RELIEF " + menu.tertiary() + "/100";
            case PneumaticSystemMenu.KIND_VALVE -> menu.stateFlag() == 1 ? "OPEN" : "CLOSED";
            case PneumaticSystemMenu.KIND_PROPORTIONAL -> "EXTERNAL UP COMMAND";
            default -> "NO MANUAL PROCESS PARAMETER";
        };
    }
    private String stateName() {
        if (menu.kind() == PneumaticSystemMenu.KIND_RELIEF) return menu.stateFlag() == 1 ? "VENTING" : "ARMED";
        if (menu.kind() == PneumaticSystemMenu.KIND_VALVE) return menu.stateFlag() == 1 ? "OPEN" : "CLOSED";
        if (menu.kind() == PneumaticSystemMenu.KIND_FLOW_METER) return menu.stateFlag() > 0 ? "MEASURING" : "NO SAMPLES";
        return menu.inputQuality() == PortQuality.VALID || menu.outputQuality() == PortQuality.VALID ? "NOMINAL" : "IDLE / NO SIGNAL";
    }
    private int stateColor() {
        if (menu.kind() == PneumaticSystemMenu.KIND_RELIEF && menu.stateFlag() == 1) return WARN;
        return menu.inputQuality() == PortQuality.FAULT || menu.outputQuality() == PortQuality.FAULT ? BAD : GOOD;
    }
    private String hint() {
        return menu.kind() == PneumaticSystemMenu.KIND_RELIEF
                ? "Safety state is separate from evidence validity: venting does not mean missing data."
                : "Pressure zero can be a legitimate state when the observation itself is valid.";
    }
    private String face(net.minecraft.core.Direction d) { return d.getName().toUpperCase(); }
    private int qualityColor(PortQuality q) { return q == PortQuality.VALID ? GOOD : q == PortQuality.NO_SIGNAL || q == PortQuality.STALE ? WARN : BAD; }
}
