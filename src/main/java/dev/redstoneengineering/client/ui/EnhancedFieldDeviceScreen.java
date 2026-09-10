package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dense, reusable engineering dashboard for lightweight RSE devices.
 * It renders only synchronized menu data and client-synchronized BlockState metadata.
 */
public final class EnhancedFieldDeviceScreen extends EngineeringScreen<FieldDeviceMenu> {
    private Button minus;
    private Button plus;
    private Button toggle;
    private Button p0;
    private Button p5;
    private Button p10;
    private Button p15;

    public EnhancedFieldDeviceScreen(FieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 111;
        minus = addConfigureWidget(Button.builder(Component.literal("−"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_DECREASE))
                .bounds(leftPos + 16, y, 82, 20).build());
        plus = addConfigureWidget(Button.builder(Component.literal("+"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_INCREASE))
                .bounds(leftPos + 104, y, 82, 20).build());
        toggle = addConfigureWidget(Button.builder(Component.literal("Toggle"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_TOGGLE))
                .bounds(leftPos + 192, y, 110, 20).build());
        p0 = preset(0, leftPos + 16, y + 27, FieldDeviceMenu.BUTTON_PRESET_0);
        p5 = preset(5, leftPos + 88, y + 27, FieldDeviceMenu.BUTTON_PRESET_5);
        p10 = preset(10, leftPos + 160, y + 27, FieldDeviceMenu.BUTTON_PRESET_10);
        p15 = preset(15, leftPos + 232, y + 27, FieldDeviceMenu.BUTTON_PRESET_15);
    }

    private Button preset(int value, int x, int y, int id) {
        return addConfigureWidget(Button.builder(Component.literal("Preset " + value), b -> sendMenuButton(id))
                .bounds(x, y, 66, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (minus == null) return;
        boolean adjustable = switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE,
                 FieldDeviceMenu.KIND_FILTER,
                 FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_DIGITAL_REGENERATOR,
                 FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE,
                 FieldDeviceMenu.KIND_PERMANENT_MAGNET,
                 FieldDeviceMenu.KIND_INDUCTION_COIL,
                 FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER,
                 FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> true;
            default -> false;
        };
        minus.active = adjustable;
        plus.active = adjustable;
        toggle.active = menu.kind() == FieldDeviceMenu.KIND_TERMINAL
                || menu.kind() == FieldDeviceMenu.KIND_PNEUMATIC_VALVE;
        boolean presets = menu.kind() == FieldDeviceMenu.KIND_REFERENCE || menu.kind() == FieldDeviceMenu.KIND_OPTICAL_EMITTER;
        p0.active = presets;
        p5.active = presets;
        p10.active = presets;
        p15.active = presets;
        String axis = adjustmentLabel();
        minus.setMessage(Component.literal("− " + axis));
        plus.setMessage(Component.literal(axis + " +"));
        toggle.setMessage(Component.literal(menu.kind() == FieldDeviceMenu.KIND_PNEUMATIC_VALVE
                ? (menu.tertiary() == 1 ? "Close valve" : "Open valve")
                : (menu.tertiary() == 1 ? "Cable → Vanilla" : "Vanilla → Cable")));
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
        int health = !menu.topologyValid() ? BAD : menu.dataValid() ? GOOD : WARN;
        statusBadge(g, deviceName(), health, 16, 80);
        statusBadge(g, family(), INFO, 200, 80);

        metricCard(g, 16, 103, "PRIMARY", valueText(menu.primary()));
        metricCard(g, 111, 103, "SECONDARY", valueText(menu.secondary()));
        metricCard(g, 206, 103, "AUX", valueText(menu.tertiary()));

        labelValue(g, "Quality", menu.qualityPercent() + "%", 149);
        labelValue(g, "Runtime", menu.dataValid() ? "VALID / ACTIVE EVIDENCE" : "NO SIGNAL / INVALID", 165);
        labelValue(g, "Topology", menu.topologyValid() ? "PASS" : "FAIL-CLOSED", 181);

        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            junctionSummary(g, 149);
        }
    }

    private void metricCard(GuiGraphics g, int x, int y, String label, String value) {
        g.fill(x, y, x + 88, y + 38, 0xFF0C1014);
        g.fill(x, y, x + 3, y + 38, INFO);
        g.drawString(font, label, x + 8, y + 6, MUTED, false);
        g.drawString(font, value, x + 8, y + 21, TEXT, false);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "PHYSICAL I/O MAP", menu.topologyValid() ? GOOD : BAD, 16, 80);
        labelValue(g, "Declared ports", Integer.toString(menu.portCount()), 103);
        labelValue(g, "Physical links", Integer.toString(menu.connectionCount()), 119);
        labelValue(g, "Connected faces", faces(), 135);

        int y = 156;
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            faceLine(g, Direction.UP, y);
            faceLine(g, Direction.DOWN, y + 18);
            g.drawString(font, "VERTICAL RISER ONLY • SAME MEDIUM • NO CONVERSION", 16, 194, INFO, false);
        } else {
            faceLine(g, Direction.NORTH, y);
            faceLine(g, Direction.EAST, y + 18);
            faceLine(g, Direction.SOUTH, y + 36);
            faceLine(g, Direction.WEST, y + 54);
        }
    }

    private void faceLine(GuiGraphics g, Direction direction, int y) {
        boolean linked = (menu.connectionMask() & (1 << direction.ordinal())) != 0;
        statusLine(g, direction.getName().toUpperCase(), linked ? "CONNECTED" : "OPEN", linked ? GOOD : MUTED, y);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, adjustable() ? "SERVER-SIDE BOUNDED CONTROL" : "READ-ONLY DEVICE", adjustable() ? INFO : MUTED, 16, 80);
        labelValue(g, "Control axis", adjustmentLabel(), 102);
        labelValue(g, "Current value", Integer.toString(controlValue()), 158);
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            g.drawString(font, "Junction Point has no conversion mode and no routing-mode toggle.", 16, 178, WARN, false);
            g.drawString(font, "Its medium is inferred from UP/DOWN physical cables.", 16, 194, MUTED, false);
        } else {
            g.drawString(font, "Buttons send intent to the server; this client never solves device physics.", 16, 184, MUTED, false);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, menu.topologyValid() ? "BOUNDARY CHECK PASS" : "BOUNDARY CHECK FAIL", menu.topologyValid() ? GOOD : BAD, 16, 80);
        labelValue(g, "Quality", menu.qualityPercent() + "%", 104);
        labelValue(g, "Data evidence", menu.dataValid() ? "VALID" : "INVALID / NO SIGNAL", 120);
        labelValue(g, "Drivers / sources", Integer.toString(menu.driverCount()), 136);
        labelValue(g, "Ports / links", menu.portCount() + " / " + menu.connectionCount(), 152);
        labelValue(g, "Connection mask", "0x" + Integer.toHexString(menu.connectionMask()).toUpperCase(), 168);
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 188);
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            junctionSummary(g, 120);
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "EVIDENCE POLICY", INFO, 16, 80);
        g.drawString(font, "This lightweight device view does not invent local time-series state.", 16, 108, TEXT, false);
        g.drawString(font, "Use Signal Analyzer / Oscilloscope / Logic Analyzer for waveform history.", 16, 126, INFO, false);
        g.drawString(font, "Diagnostics here expose current authoritative quality, topology and source state.", 16, 144, MUTED, false);
        sectionRule(g, 166);
        g.drawString(font, "Design rule: measurement UI observes; it does not become a hidden network driver.", 16, 180, GOOD, false);
    }

    private void junctionSummary(GuiGraphics g, int y) {
        if (minecraft == null || minecraft.level == null) return;
        BlockState state = minecraft.level.getBlockState(menu.blockPos());
        if (!(state.getBlock() instanceof RedstoneCableJunctionBlock)) return;
        TransmissionTopology.SignalMedium medium = state.getValue(RedstoneCableJunctionBlock.MEDIUM);
        int color = medium == TransmissionTopology.SignalMedium.MISMATCH ? BAD
                : medium == TransmissionTopology.SignalMedium.NONE ? WARN : GOOD;
        statusLine(g, "Detected medium", medium.getSerializedName().toUpperCase(), color, y);
        statusLine(g, "Routing contract", medium == TransmissionTopology.SignalMedium.MISMATCH
                ? "BLOCKED • MIXED MEDIA" : "PASS-THROUGH • NO CONVERSION", color, y + 16);
    }

    private boolean adjustable() {
        return minus != null && minus.active;
    }

    private int controlValue() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> menu.secondary();
            case FieldDeviceMenu.KIND_FILTER -> menu.tertiary();
            case FieldDeviceMenu.KIND_REFERENCE -> menu.primary();
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> menu.tertiary();
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> menu.secondary();
            default -> menu.tertiary() != 0 ? menu.tertiary() : menu.primary();
        };
    }

    private String adjustmentLabel() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> "Channel";
            case FieldDeviceMenu.KIND_FILTER -> "Slew";
            case FieldDeviceMenu.KIND_REFERENCE -> "Output";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "Threshold";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "Setpoint";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "Relief";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "Strength";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "Turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "Intensity";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "Channel";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "Loss";
            default -> "Readback";
        };
    }

    private String family() {
        int k = menu.kind();
        if (k == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) return "ROUTING";
        if (k >= FieldDeviceMenu.KIND_AIR_COMPRESSOR && k <= FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER) return "PNEUMATIC";
        if (k >= FieldDeviceMenu.KIND_ELECTROMAGNET && k <= FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER) return "MAGNETIC";
        if (k >= FieldDeviceMenu.KIND_OPTICAL_FIBER && k <= FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION) return "OPTICAL";
        if (k >= FieldDeviceMenu.KIND_DATA_BUS_8 && k <= FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER) return "COMMUNICATIONS";
        if (k >= FieldDeviceMenu.KIND_WATCHDOG && k <= FieldDeviceMenu.KIND_OPERATIONS_MONITOR) return "CPS / RELIABILITY";
        return "ENGINEERING DEVICE";
    }

    private String deviceName() {
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) return "JUNCTION POINT";
        return title.getString().toUpperCase();
    }

    private String valueText(int value) {
        return Integer.toString(value);
    }

    private String faces() {
        StringBuilder out = new StringBuilder();
        for (Direction d : Direction.values()) {
            if ((menu.connectionMask() & (1 << d.ordinal())) == 0) continue;
            if (!out.isEmpty()) out.append(" · ");
            out.append(d.getName().toUpperCase());
        }
        return out.isEmpty() ? "NONE" : out.toString();
    }
}
