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
        statusBadge(g, deviceRole(), INFO, 200, 80);

        metricCard(g, 16, 103, metricLabel(0), metricValue(0, menu.primary()));
        metricCard(g, 111, 103, metricLabel(1), metricValue(1, menu.secondary()));
        metricCard(g, 206, 103, metricLabel(2), metricValue(2, menu.tertiary()));

        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            junctionSummary(g, 149);
            labelValue(g, "Topology", menu.topologyValid() ? "PASS" : "FAIL-CLOSED", 181);
        } else if (isPassiveMedium()) {
            labelValue(g, "Medium", mediumName(), 149);
            labelValue(g, "Link state", linkState(), 165);
            labelValue(g, "Routing contract", routingContract(), 181);
        } else if (isCommunicationDevice()) {
            labelValue(g, "Family", family(), 149);
            labelValue(g, "Evidence", evidenceState(), 165);
            labelValue(g, "Path contract", communicationContract(), 181);
        } else if (isObserver()) {
            labelValue(g, "Family", family(), 149);
            labelValue(g, "Evidence", evidenceState(), 165);
            labelValue(g, "Network authority", "OBSERVE ONLY • NO BACKDRIVE", 181);
        } else if (isDirectionalProcessor()) {
            labelValue(g, "Family", family(), 149);
            labelValue(g, "Process", processorFunction(), 165);
            labelValue(g, "Evidence", evidenceState(), 181);
        } else {
            labelValue(g, "Quality", menu.qualityPercent() + "%", 149);
            labelValue(g, "Evidence", evidenceState(), 165);
            labelValue(g, "Topology", menu.topologyValid() ? "PASS" : "FAIL-CLOSED", 181);
        }
        safeText(g, engineeringHint(), 16, 199, menu.topologyValid() ? MUTED : BAD);
    }

    private void metricCard(GuiGraphics g, int x, int y, String label, String value) {
        g.fill(x, y, x + 88, y + 38, 0xFF0C1014);
        g.fill(x, y, x + 3, y + 38, INFO);
        g.drawString(font, fitForWidth(label, 72), x + 8, y + 6, MUTED, false);
        g.drawString(font, fitForWidth(value, 72), x + 8, y + 21, TEXT, false);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "PHYSICAL / LOGICAL INTERFACES", menu.topologyValid() ? GOOD : BAD, 16, 80);
        labelValue(g, "Declared ports", Integer.toString(menu.portCount()), 103);
        labelValue(g, "Physical links", Integer.toString(menu.connectionCount()), 119);
        labelValue(g, "Connected faces", faces(), 135);

        int y = 156;
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            faceLine(g, Direction.UP, y);
            faceLine(g, Direction.DOWN, y + 18);
            safeText(g, "VERTICAL RISER ONLY • SAME MEDIUM • NO CONVERSION", 16, 194, INFO);
        } else if (menu.kind() == FieldDeviceMenu.KIND_PROBE) {
            statusLine(g, facingName(), "SENSE • REDSTONE MEASUREMENT INPUT", GOOD, y);
            statusLine(g, oppositeFacingName(), "REPORT • INSTRUMENT BUS OUTPUT", INFO, y + 20);
            safeText(g, "Observer interface: sensing never back-drives the measured redstone network.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_SIGNAL_TAP) {
            statusLine(g, oppositeFacingName(), "SENSE • REDSTONE SAMPLE INPUT", GOOD, y);
            statusLine(g, facingName(), "MIRROR • REDSTONE OUTPUT", INFO, y + 20);
            safeText(g, "Tap reports/mirrors sampled evidence without becoming a hidden input-side driver.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_RANGE_SENSOR) {
            statusLine(g, facingName(), "SENSE • FREE-SPACE RANGE SCAN", GOOD, y);
            statusLine(g, oppositeFacingName(), "OUTPUT • REDSTONE 0..15", INFO, y + 20);
            safeText(g, "Sensing face is physical observation; opposite face carries the electrical result.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_RADIO_TRANSMITTER) {
            statusLine(g, "UP", "INPUT • WIRED PAYLOAD", GOOD, y);
            statusLine(g, "FREE SPACE", "OUTPUT • RADIO CH " + menu.secondary() + " • RANGE " + menu.tertiary(), INFO, y + 20);
            safeText(g, "Radio boundary is explicit: wired payload becomes a free-space transmission.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_RADIO_RECEIVER) {
            statusLine(g, "FREE SPACE", "INPUT • RADIO CH " + menu.secondary(), GOOD, y);
            statusLine(g, facingName(), "OUTPUT • REDSTONE 0..15", INFO, y + 20);
            safeText(g, "Reception evidence is wireless; only the declared output face drives redstone.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER) {
            statusLine(g, oppositeFacingName(), "INPUT • WIRED SIGNAL", GOOD, y);
            statusLine(g, facingName(), "OUTPUT • FREE-SPACE OPTICAL • CH " + menu.secondary(), INFO, y + 20);
            safeText(g, "Optical launch direction is explicit; input and free-space interfaces are distinct.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER) {
            statusLine(g, oppositeFacingName(), "INPUT • FREE-SPACE OPTICAL • CH " + menu.tertiary(), GOOD, y);
            statusLine(g, facingName(), "OUTPUT • REDSTONE 0..15", INFO, y + 20);
            safeText(g, "Optical reception is observational until converted onto the declared wired output.", 16, 198, MUTED);
        } else if (isDirectionalConverter()) {
            statusLine(g, oppositeFacingName(), "INPUT • " + converterInput(), GOOD, y);
            statusLine(g, facingName(), "OUTPUT • " + converterOutput(), INFO, y + 20);
            safeText(g, "Converter boundary is explicit: input and output media remain distinct.", 16, 198, MUTED);
        } else if (isDirectionalProcessor()) {
            statusLine(g, oppositeFacingName(), "INPUT • " + processorInput(), GOOD, y);
            statusLine(g, "PROCESS", processorFunction() + " • " + processorParameter(), INFO, y + 20);
            statusLine(g, facingName(), "OUTPUT • " + processorOutput(), GOOD, y + 40);
            safeText(g, "Processor transforms evidence inside one declared domain path; it does not imply conversion.", 16, 216, MUTED);
        } else if (isPassiveMedium()) {
            faceLine(g, Direction.UP, y);
            faceLine(g, Direction.DOWN, y + 14);
            faceLine(g, Direction.NORTH, y + 28);
            faceLine(g, Direction.EAST, y + 42);
            faceLine(g, Direction.SOUTH, y + 56);
            faceLine(g, Direction.WEST, y + 70);
        } else {
            faceLine(g, Direction.NORTH, y);
            faceLine(g, Direction.EAST, y + 18);
            faceLine(g, Direction.SOUTH, y + 36);
            faceLine(g, Direction.WEST, y + 54);
        }
    }

    private void faceLine(GuiGraphics g, Direction direction, int y) {
        boolean linked = (menu.connectionMask() & (1 << direction.ordinal())) != 0;
        String state = linked ? "CONNECTED" : "OPEN";
        if (isPassiveMedium()) state = mediumName() + " • " + state;
        statusLine(g, direction.getName().toUpperCase(), state, linked ? GOOD : MUTED, y);
    }

    private void configure(GuiGraphics g) {
        String policy = adjustable() ? "SERVER-SIDE BOUNDED CONTROL"
                : isObserver() ? "OBSERVER • READ-ONLY"
                : isPassiveMedium() ? "PASSIVE MEDIUM • READ-ONLY"
                : "READ-ONLY DEVICE";
        statusBadge(g, policy, adjustable() ? INFO : MUTED, 16, 80);
        labelValue(g, "Control axis", adjustmentLabel(), 102);
        labelValue(g, "Current value", controlValueText(), 158);
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            safeText(g, "Junction Point has no conversion mode and no routing-mode toggle.", 16, 178, WARN);
            safeText(g, "Its medium is inferred from UP/DOWN physical cables.", 16, 194, MUTED);
        } else if (isObserver()) {
            safeText(g, "Observer controls select what to measure; they never create network drive evidence.", 16, 178, INFO);
            safeText(g, "Any sampled zero remains distinct from missing or invalid evidence.", 16, 194, MUTED);
        } else if (isDirectionalProcessor()) {
            safeText(g, "Processing parameter: " + processorParameter(), 16, 178, INFO);
            safeText(g, "Input and output stay on an explicit directional processing path.", 16, 194, MUTED);
        } else if (isPassiveMedium()) {
            safeText(g, "Passive medium: continuity only; this screen never changes routing semantics.", 16, 178, INFO);
            safeText(g, routingContract(), 16, 194, MUTED);
        } else {
            safeText(g, "Buttons send intent to the server; this client never solves device physics.", 16, 184, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, menu.topologyValid() ? "BOUNDARY CHECK PASS" : "BOUNDARY CHECK FAIL", menu.topologyValid() ? GOOD : BAD, 16, 80);
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            junctionSummary(g, 104);
            labelValue(g, "Quality", menu.qualityPercent() + "%", 140);
            labelValue(g, "Ports / links", menu.portCount() + " / " + menu.connectionCount(), 156);
            labelValue(g, "Connection mask", "0x" + Integer.toHexString(menu.connectionMask()).toUpperCase(), 172);
        } else if (isPassiveMedium()) {
            labelValue(g, "Medium", mediumName(), 104);
            labelValue(g, "Link state", linkState(), 120);
            labelValue(g, "Contract", routingContract(), 136);
            labelValue(g, "Quality / evidence", menu.qualityPercent() + "% • " + evidenceState(), 152);
            labelValue(g, "Ports / links", menu.portCount() + " / " + menu.connectionCount(), 168);
        } else if (isCommunicationDevice()) {
            labelValue(g, "Role", deviceRole(), 104);
            labelValue(g, "Path", communicationContract(), 120);
            labelValue(g, "Quality", menu.qualityPercent() + "%", 136);
            labelValue(g, "Evidence", evidenceState(), 152);
            labelValue(g, "Ports / links", menu.portCount() + " / " + menu.connectionCount(), 168);
        } else if (isObserver()) {
            labelValue(g, "Role", "OBSERVER • NON-DRIVING", 104);
            labelValue(g, "Quality", menu.qualityPercent() + "%", 120);
            labelValue(g, "Evidence", evidenceState(), 136);
            labelValue(g, "Observed interface", observedInterface(), 152);
            labelValue(g, "Ports / links", menu.portCount() + " / " + menu.connectionCount(), 168);
        } else if (isDirectionalProcessor()) {
            labelValue(g, "Role", "PROCESSOR • DIRECTIONAL", 104);
            labelValue(g, "Path", oppositeFacingName() + " → " + facingName(), 120);
            labelValue(g, "Function", processorFunction(), 136);
            labelValue(g, "Parameter", processorParameter(), 152);
            labelValue(g, "Evidence", evidenceState(), 168);
        } else {
            labelValue(g, "Quality", menu.qualityPercent() + "%", 104);
            labelValue(g, "Data evidence", menu.dataValid() ? "VALID" : "INVALID / NO SIGNAL", 120);
            labelValue(g, "Drivers / sources", Integer.toString(menu.driverCount()), 136);
            labelValue(g, "Ports / links", menu.portCount() + " / " + menu.connectionCount(), 152);
            labelValue(g, "Connection mask", "0x" + Integer.toHexString(menu.connectionMask()).toUpperCase(), 168);
        }
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 188);
        safeText(g, diagnosticHint(), 16, 207, menu.topologyValid() ? MUTED : BAD);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "EVIDENCE POLICY", INFO, 16, 80);
        safeText(g, "This lightweight device view does not invent local time-series state.", 16, 108, TEXT);
        safeText(g, "Use Signal Analyzer / Oscilloscope / Logic Analyzer for waveform history.", 16, 126, INFO);
        safeText(g, "Diagnostics here expose current authoritative quality, topology and source state.", 16, 144, MUTED);
        sectionRule(g, 166);
        safeText(g, "Design rule: measurement UI observes; it does not become a hidden network driver.", 16, 180, GOOD);
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
        if (isPassiveMedium()) return "INTERCONNECT";
        if (k >= FieldDeviceMenu.KIND_AIR_COMPRESSOR && k <= FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER
                || k >= FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE && k <= FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER) {
            return "PNEUMATIC";
        }
        if (k >= FieldDeviceMenu.KIND_ELECTROMAGNET && k <= FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER) return "MAGNETIC";
        if (k >= FieldDeviceMenu.KIND_OPTICAL_FIBER && k <= FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION) return "OPTICAL";
        if (isCommunicationDevice()) return "COMMUNICATIONS";
        if (k >= FieldDeviceMenu.KIND_AMETHYST_RESONATOR && k <= FieldDeviceMenu.KIND_THERMAL_RECEIVER) return "WAVE / METROLOGY";
        if (k >= FieldDeviceMenu.KIND_WATCHDOG && k <= FieldDeviceMenu.KIND_OPERATIONS_MONITOR) return "CPS / RELIABILITY";
        if (k >= FieldDeviceMenu.KIND_EDGE_DETECTOR && k <= FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR) return "SIGNAL / TIMING";
        return "ENGINEERING DEVICE";
    }

    private String deviceRole() {
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) return "ROUTER";
        if (isPassiveMedium()) return "PASSIVE MEDIUM";
        if (isObserver()) return "OBSERVER";
        if (isDirectionalConverter()) return "CONVERTER";
        if (isDirectionalProcessor()) return "PROCESSOR";
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR,
                 FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_PERMANENT_MAGNET,
                 FieldDeviceMenu.KIND_AIR_COMPRESSOR,
                 FieldDeviceMenu.KIND_RADIO_TRANSMITTER,
                 FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> "SOURCE";
            case FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER,
                 FieldDeviceMenu.KIND_SERVO_ACTUATOR,
                 FieldDeviceMenu.KIND_ELECTROMAGNET -> "ACTUATOR";
            default -> "PROCESSOR";
        };
    }

    private String deviceName() {
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) return "JUNCTION POINT";
        return title.getString().toUpperCase();
    }

    private String metricLabel(int slot) {
        int k = menu.kind();
        if (k == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) return switch (slot) { case 0 -> "SIGNAL"; case 1 -> "SOURCES"; default -> "MEDIUM"; };
        if (k == FieldDeviceMenu.KIND_REDSTONE_CABLE) return switch (slot) { case 0 -> "SIGNAL"; case 1 -> "LINKS"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_INSTRUMENT_CABLE) return switch (slot) { case 0 -> "LINKS"; case 1 -> "QUALITY"; default -> "STATE"; };
        if (k == FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE) return switch (slot) { case 0 -> "SHIELD"; case 1 -> "COVERED"; default -> "EXPOSED"; };
        if (k == FieldDeviceMenu.KIND_LAPIS_LINE) return switch (slot) { case 0 -> "VALUE"; case 1 -> "LINKS"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_QUARTZ_LINE) return switch (slot) { case 0 -> "ACTIVE"; case 1 -> "PERIOD"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_AMETHYST_DUST) return switch (slot) { case 0 -> "AMPLITUDE"; case 1 -> "FREQUENCY"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_ENCODER) return switch (slot) { case 0 -> "REDSTONE IN"; case 1 -> "BYTE OUT"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_DECODER) return switch (slot) { case 0 -> "BYTE IN"; case 1 -> "REDSTONE OUT"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_SERIALIZER) return switch (slot) { case 0 -> "BYTE IN"; case 1 -> "SERIAL OUT"; default -> "TIMING"; };
        if (k == FieldDeviceMenu.KIND_DESERIALIZER) return switch (slot) { case 0 -> "SERIAL IN"; case 1 -> "BYTE OUT"; default -> "TIMING"; };
        if (k == FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER) return switch (slot) { case 0 -> "LOGIC IN"; case 1 -> "DIFF OUT"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER) return switch (slot) { case 0 -> "DIFF IN"; case 1 -> "REDSTONE OUT"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_SIGNAL_TAP) return switch (slot) { case 0 -> "SAMPLED IN"; case 1 -> "MIRROR OUT"; default -> "OBSERVED"; };
        if (k == FieldDeviceMenu.KIND_EDGE_DETECTOR) return switch (slot) { case 0 -> "INPUT"; case 1 -> "PULSE OUT"; default -> "EDGE MODE"; };
        if (k == FieldDeviceMenu.KIND_PULSE_SHAPER) return switch (slot) { case 0 -> "INPUT"; case 1 -> "PULSE OUT"; default -> "WIDTH"; };
        if (k == FieldDeviceMenu.KIND_DIGITAL_REGENERATOR) return switch (slot) { case 0 -> "INPUT QUAL."; case 1 -> "SERIAL OUT"; default -> "THRESHOLD"; };
        if (k == FieldDeviceMenu.KIND_QUARTZ_DIVIDER) return switch (slot) { case 0 -> "PERIOD IN"; case 1 -> "PERIOD OUT"; default -> "DIVISION"; };
        if (k == FieldDeviceMenu.KIND_QUARTZ_STABILITY) return switch (slot) { case 0 -> "MEASURED"; case 1 -> "ERROR"; default -> "NOMINAL"; };
        if (k == FieldDeviceMenu.KIND_AMETHYST_FILTER) return switch (slot) { case 0 -> "FREQ IN"; case 1 -> "AMP OUT"; default -> "TARGET"; };
        if (k == FieldDeviceMenu.KIND_AMETHYST_TUNED) return switch (slot) { case 0 -> "NATURAL"; case 1 -> "AMP OUT"; default -> "Q INDEX"; };
        if (k == FieldDeviceMenu.KIND_PRESSURE_REGULATOR) return switch (slot) { case 0 -> "PRESSURE"; case 1 -> "SETPOINT"; default -> "SETTING"; };
        if (k == FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER) return switch (slot) { case 0 -> "FLOW"; case 1 -> "Δ PRESSURE"; default -> "INLET"; };
        if (k == FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER) return switch (slot) { case 0 -> "PRESSURE"; case 1 -> "POSITION"; default -> "TARGET"; };
        if (k == FieldDeviceMenu.KIND_AIR_COMPRESSOR) return switch (slot) { case 0 -> "COMMAND"; case 1 -> "SET PRESS."; default -> "ACTUAL"; };
        if (k >= FieldDeviceMenu.KIND_AIR_COMPRESSOR && k <= FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER || k >= FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE && k <= FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER) return switch (slot) { case 0 -> "INLET"; case 1 -> "OUTLET"; default -> "STATE"; };
        if (k == FieldDeviceMenu.KIND_INDUCTION_COIL) return switch (slot) { case 0 -> "FIELD"; case 1 -> "EMF"; default -> "TURNS"; };
        if (k == FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER) return switch (slot) { case 0 -> "FIELD"; case 1 -> "GRAD X"; default -> "GRAD Y"; };
        if (k >= FieldDeviceMenu.KIND_ELECTROMAGNET && k <= FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER) return switch (slot) { case 0 -> "FIELD"; case 1 -> "DRIVE"; default -> "CONFIG"; };
        if (k == FieldDeviceMenu.KIND_OPTICAL_EMITTER) return switch (slot) { case 0 -> "INTENSITY"; case 1 -> "CHANNEL"; default -> "CONFIG"; };
        if (k == FieldDeviceMenu.KIND_OPTICAL_RECEIVER || k == FieldDeviceMenu.KIND_OPTICAL_POWER_METER) return switch (slot) { case 0 -> "INTENSITY"; case 1 -> "CHANNEL"; default -> "AUX"; };
        if (k == FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER) return switch (slot) { case 0 -> "INPUT"; case 1 -> "CHANNEL"; default -> "OUTPUT"; };
        if (k == FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR) return switch (slot) { case 0 -> "INPUT"; case 1 -> "OUTPUT"; default -> "LOSS"; };
        if (k >= FieldDeviceMenu.KIND_OPTICAL_FIBER && k <= FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION) return switch (slot) { case 0 -> "OPTICAL"; case 1 -> "CHANNEL"; default -> "STATE"; };
        if (k == FieldDeviceMenu.KIND_DATA_BUS_8) return switch (slot) { case 0 -> "PAYLOAD"; case 1 -> "DRIVERS"; default -> "LOAD"; };
        if (k == FieldDeviceMenu.KIND_SERIAL_LINE) return switch (slot) { case 0 -> "PAYLOAD"; case 1 -> "TIMING"; default -> "LINK"; };
        if (k == FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR) return switch (slot) { case 0 -> "LOGIC"; case 1 -> "QUALITY"; default -> "LINKS"; };
        if (k == FieldDeviceMenu.KIND_RADIO_TRANSMITTER || k == FieldDeviceMenu.KIND_RADIO_RECEIVER) return switch (slot) { case 0 -> "PAYLOAD"; case 1 -> "CHANNEL"; default -> "LINK"; };
        if (isCommunicationDevice()) return switch (slot) { case 0 -> "PAYLOAD"; case 1 -> "SELECTOR"; default -> "STATE"; };
        if (k == FieldDeviceMenu.KIND_QUARTZ_OSCILLATOR) return switch (slot) { case 0 -> "ACTIVE"; case 1 -> "PERIOD"; default -> "PERIOD IDX"; };
        if (k == FieldDeviceMenu.KIND_RANGE_SENSOR) return switch (slot) { case 0 -> "RANGE"; case 1 -> "OUTPUT"; default -> "LIMIT"; };
        if (k == FieldDeviceMenu.KIND_REFERENCE) return switch (slot) { case 0 -> "OUTPUT"; case 1 -> "QUALITY"; default -> "STATE"; };
        if (k == FieldDeviceMenu.KIND_FILTER) return switch (slot) { case 0 -> "INPUT"; case 1 -> "OUTPUT"; default -> "SLEW"; };
        if (k == FieldDeviceMenu.KIND_PROBE) return switch (slot) { case 0 -> "SIGNAL"; case 1 -> "CHANNEL"; default -> "FACING"; };
        if (k >= FieldDeviceMenu.KIND_WATCHDOG && k <= FieldDeviceMenu.KIND_OPERATIONS_MONITOR) return switch (slot) { case 0 -> "PROCESS"; case 1 -> "STATUS"; default -> "SAFETY"; };
        if (k >= FieldDeviceMenu.KIND_AMETHYST_RESONATOR && k <= FieldDeviceMenu.KIND_THERMAL_RECEIVER) return switch (slot) { case 0 -> "AMPLITUDE"; case 1 -> "FREQUENCY"; default -> "STATE"; };
        return switch (slot) { case 0 -> "PROCESS"; case 1 -> "READBACK"; default -> "CONFIG"; };
    }

    private String metricValue(int slot, int value) {
        int k = menu.kind();
        if (k == FieldDeviceMenu.KIND_REDSTONE_CABLE) {
            if (slot == 0) return menu.primary() + " / 15";
            if (slot == 1) return Integer.toString(menu.connectionCount());
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_INSTRUMENT_CABLE) {
            if (slot == 0) return Integer.toString(menu.connectionCount());
            if (slot == 1) return menu.qualityPercent() + "%";
            return linkState();
        }
        if (k == FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE) {
            if (slot == 0) return menu.primary() + "%";
            return Integer.toString(value);
        }
        if (k == FieldDeviceMenu.KIND_LAPIS_LINE) {
            if (slot == 0) return Integer.toString(menu.primary());
            if (slot == 1) return Integer.toString(menu.connectionCount());
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_QUARTZ_LINE) {
            if (slot == 0) return menu.primary() != 0 ? "YES" : "NO";
            if (slot == 1) return menu.secondary() + " t";
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_AMETHYST_DUST && slot == 2) return menu.qualityPercent() + "%";
        if (k == FieldDeviceMenu.KIND_DATA_BUS_8) {
            if (slot == 0) return String.format("0x%02X", menu.primary() & 0xFF);
            if (slot == 1) return Integer.toString(menu.driverCount());
            return Integer.toString(menu.connectionCount());
        }
        if (k == FieldDeviceMenu.KIND_ENCODER) {
            if (slot == 0) return menu.primary() + " / 15";
            if (slot == 1) return String.format("0x%02X", menu.secondary() & 0xFF);
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_DECODER) {
            if (slot == 0) return String.format("0x%02X", menu.primary() & 0xFF);
            if (slot == 1) return menu.secondary() + " / 15";
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_SERIALIZER || k == FieldDeviceMenu.KIND_DESERIALIZER) {
            if (slot == 0 || slot == 1) return String.format("0x%02X", value & 0xFF);
            return menu.tertiary() + " t";
        }
        if (k == FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER) {
            if (slot == 0) return menu.primary() + " / 15";
            if (slot == 1) return Integer.toString(menu.secondary());
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER) {
            if (slot == 0) return Integer.toString(menu.primary());
            if (slot == 1) return menu.secondary() + " / 15";
            return menu.qualityPercent() + "%";
        }
        if (k == FieldDeviceMenu.KIND_SIGNAL_TAP) {
            if (slot < 2) return value + " / 15";
            return "NO BACKDRIVE";
        }
        if (k == FieldDeviceMenu.KIND_PROBE) {
            if (slot == 0) return menu.primary() + " / 15";
            if (slot == 1) return Integer.toString(menu.secondary());
            return facingName();
        }
        if (k == FieldDeviceMenu.KIND_DIGITAL_REGENERATOR) {
            if (slot == 0) return menu.primary() + "%";
            if (slot == 1) return String.format("0x%02X", menu.secondary() & 0xFF);
            return Integer.toString(menu.tertiary());
        }
        if (k == FieldDeviceMenu.KIND_QUARTZ_DIVIDER) {
            if (slot < 2) return value + " t";
            return "÷" + value;
        }
        if (k == FieldDeviceMenu.KIND_QUARTZ_STABILITY) {
            if (slot == 0 || slot == 2) return value + " t";
            return Integer.toString(value);
        }
        if (k == FieldDeviceMenu.KIND_AMETHYST_FILTER) {
            if (slot == 0 || slot == 2) return value + " Hz#";
            return value + " amp";
        }
        if (k == FieldDeviceMenu.KIND_AMETHYST_TUNED) {
            if (slot == 0) return value + " Hz#";
            if (slot == 1) return value + " amp";
            return "Q" + value;
        }
        if (k == FieldDeviceMenu.KIND_SERIAL_LINE && slot == 2) return linkState();
        if (k == FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR) {
            if (slot == 0) return Integer.toString(menu.primary());
            if (slot == 1) return menu.qualityPercent() + "%";
            return Integer.toString(menu.connectionCount());
        }
        if (k == FieldDeviceMenu.KIND_INDUCTION_COIL && slot == 2) return value + " turns";
        if (k == FieldDeviceMenu.KIND_OPTICAL_EMITTER || k == FieldDeviceMenu.KIND_OPTICAL_RECEIVER || k == FieldDeviceMenu.KIND_OPTICAL_FIBER || k == FieldDeviceMenu.KIND_OPTICAL_POWER_METER) {
            if (slot == 0) return value + " / 15";
            if (slot == 1) return "CH " + value;
        }
        if (k >= FieldDeviceMenu.KIND_AIR_COMPRESSOR && k <= FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER || k >= FieldDeviceMenu.KIND_PNEUMATIC_PROPORTIONAL_VALVE && k <= FieldDeviceMenu.KIND_PNEUMATIC_CYLINDER) return Integer.toString(value);
        if (k >= FieldDeviceMenu.KIND_ELECTROMAGNET && k <= FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER) return slot == 0 ? value + " field" : Integer.toString(value);
        return Integer.toString(value);
    }

    private String controlValueText() {
        if (menu.kind() == FieldDeviceMenu.KIND_INDUCTION_COIL) return controlValue() + " turns";
        if (menu.kind() == FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER) return "CH " + controlValue();
        if (menu.kind() == FieldDeviceMenu.KIND_OPTICAL_EMITTER) return controlValue() + " / 15";
        return Integer.toString(controlValue());
    }

    private String engineeringHint() {
        if (!menu.topologyValid()) return "Topology incomplete or incompatible: output must remain fail-closed.";
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) return "Vertical riser • same-medium continuity • no translation.";
        if (isObserver()) return "Observer semantics: sample evidence without becoming a hidden source or feedback path.";
        if (isPassiveMedium()) return "Follow medium identity, physical links and quality together; passive interconnect never translates.";
        if (isDirectionalConverter()) return "Converter semantics: inspect both boundary media and authoritative output evidence.";
        if (isDirectionalProcessor()) return "Processor semantics: follow input → operation → output and keep configuration distinct from evidence.";
        if (family().equals("PNEUMATIC")) return "Compare commanded, inlet and realized pressure before changing control settings.";
        if (family().equals("MAGNETIC")) return "Field evidence is observational; configuration changes must not fabricate a transient.";
        if (family().equals("OPTICAL")) return "Check channel identity and intensity together; valid zero is distinct from missing evidence.";
        if (family().equals("COMMUNICATIONS")) return "Read payload only with link state, route contract, quality and source evidence.";
        if (family().equals("CPS / RELIABILITY")) return "Safety state is server-authoritative; degraded evidence must not appear healthy.";
        return "Use quality + topology + readback together before treating a value as authoritative.";
    }

    private String diagnosticHint() {
        if (!menu.topologyValid()) return "ACTION: inspect physical ports / incompatible neighbor / unloaded boundary.";
        if (!menu.dataValid()) return isCommunicationDevice()
                ? "ACTION: trace source → medium → destination; NO PAYLOAD is not a valid zero."
                : "ACTION: trace source evidence; zero and NO SIGNAL are not equivalent.";
        if (menu.qualityPercent() < 100) return "ACTION: inspect degraded quality before adjusting the device.";
        if (isObserver()) return "Observer healthy: evidence is readable and no network-driving authority is implied.";
        if (isDirectionalProcessor()) return "Processor healthy: compare input evidence, processing parameter and realized output together.";
        if (isPassiveMedium() && menu.connectionCount() == 0) return "ACTION: medium is healthy but physically open; inspect adjacent endpoints.";
        return "No current boundary fault detected; readback remains server-authoritative.";
    }

    private boolean isPassiveMedium() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REDSTONE_CABLE,
                 FieldDeviceMenu.KIND_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_DATA_BUS_8,
                 FieldDeviceMenu.KIND_SERIAL_LINE,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR,
                 FieldDeviceMenu.KIND_LAPIS_LINE,
                 FieldDeviceMenu.KIND_QUARTZ_LINE,
                 FieldDeviceMenu.KIND_AMETHYST_DUST,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER -> true;
            default -> false;
        };
    }

    private boolean isObserver() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE,
                 FieldDeviceMenu.KIND_SIGNAL_TAP,
                 FieldDeviceMenu.KIND_RANGE_SENSOR,
                 FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR,
                 FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER,
                 FieldDeviceMenu.KIND_OPTICAL_POWER_METER,
                 FieldDeviceMenu.KIND_SERVO_POSITION_SENSOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER,
                 FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> true;
            default -> false;
        };
    }

    private boolean isDirectionalConverter() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_ENCODER,
                 FieldDeviceMenu.KIND_DECODER,
                 FieldDeviceMenu.KIND_SERIALIZER,
                 FieldDeviceMenu.KIND_DESERIALIZER,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER,
                 FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER,
                 FieldDeviceMenu.KIND_INDUCTION_COIL -> true;
            default -> false;
        };
    }

    private boolean isDirectionalProcessor() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_FILTER,
                 FieldDeviceMenu.KIND_DIGITAL_REGENERATOR,
                 FieldDeviceMenu.KIND_QUARTZ_DIVIDER,
                 FieldDeviceMenu.KIND_QUARTZ_STABILITY,
                 FieldDeviceMenu.KIND_AMETHYST_FILTER,
                 FieldDeviceMenu.KIND_AMETHYST_TUNED,
                 FieldDeviceMenu.KIND_EDGE_DETECTOR,
                 FieldDeviceMenu.KIND_PULSE_SHAPER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER,
                 FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> true;
            default -> false;
        };
    }

    private boolean isCommunicationDevice() {
        int k = menu.kind();
        return k >= FieldDeviceMenu.KIND_DATA_BUS_8 && k <= FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER;
    }

    private String mediumName() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REDSTONE_CABLE -> "INSULATED_REDSTONE";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "INSTRUMENT_BUS";
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "DATA_BUS_8";
            case FieldDeviceMenu.KIND_SERIAL_LINE -> "SERIAL_DATA";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> "DIFFERENTIAL";
            case FieldDeviceMenu.KIND_LAPIS_LINE -> "LAPIS_PRECISION";
            case FieldDeviceMenu.KIND_QUARTZ_LINE -> "QUARTZ_TIMING";
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> "AMETHYST_RESONANCE";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER -> "OPTICAL";
            default -> "DEVICE I/O";
        };
    }

    private String routingContract() {
        if (menu.kind() == FieldDeviceMenu.KIND_DATA_BUS_8) return "BYTE BUS • SAME MEDIUM • NO TRANSLATION";
        if (menu.kind() == FieldDeviceMenu.KIND_SERIAL_LINE) return "SERIAL LINK • SAME MEDIUM • NO TRANSLATION";
        if (menu.kind() == FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR) return "BALANCED PAIR • SAME MEDIUM • NO TRANSLATION";
        if (menu.kind() == FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE) return "INSTRUMENT BUS • SHIELDED • PASSIVE";
        return "SAME MEDIUM • PASSIVE CONTINUITY • NO TRANSLATION";
    }

    private String linkState() {
        if (!menu.topologyValid()) return "MISMATCH / BLOCKED";
        if (menu.connectionCount() == 0 && (isPassiveMedium() || isCommunicationDevice())) return "OPEN / NO PHYSICAL LINK";
        if (!menu.dataValid()) return "CONNECTED / NO VALID DATA";
        if (menu.qualityPercent() < 100) return "DEGRADED • " + menu.qualityPercent() + "%";
        return "NOMINAL";
    }

    private String evidenceState() {
        return menu.dataValid() ? "VALID • SERVER READBACK" : "NO SIGNAL / INVALID";
    }

    private String communicationContract() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "MULTI-DROP BYTE BUS";
            case FieldDeviceMenu.KIND_ENCODER -> "REDSTONE → DATA_BUS_8";
            case FieldDeviceMenu.KIND_DECODER -> "DATA_BUS_8 → REDSTONE";
            case FieldDeviceMenu.KIND_SERIAL_LINE -> "SERIAL_DATA PASS-THROUGH";
            case FieldDeviceMenu.KIND_SERIALIZER -> "DATA_BUS_8 → SERIAL_DATA";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "SERIAL_DATA → DATA_BUS_8";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> "DIFFERENTIAL PASS-THROUGH";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "SERIAL_DATA → REGENERATED SERIAL";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "LOGIC → DIFFERENTIAL";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "DIFFERENTIAL → REDSTONE";
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> "WIRED INPUT → FREE-SPACE RADIO";
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> "FREE-SPACE RADIO → WIRED OUTPUT";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> "WIRED INPUT → FREE-SPACE OPTICAL";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> "FREE-SPACE OPTICAL → WIRED OUTPUT";
            default -> "DECLARED COMMUNICATION PATH";
        };
    }

    private String converterInput() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_ENCODER -> "REDSTONE • 0..15";
            case FieldDeviceMenu.KIND_DECODER -> "DATA_BUS_8 • BYTE";
            case FieldDeviceMenu.KIND_SERIALIZER -> "DATA_BUS_8 • BYTE";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "SERIAL_DATA • FRAME";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "LOGIC";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "DIFFERENTIAL";
            case FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER -> "PNEUMATIC";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "MAGNETIC FIELD";
            default -> "DECLARED DOMAIN";
        };
    }

    private String converterOutput() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_ENCODER -> "DATA_BUS_8 • BYTE";
            case FieldDeviceMenu.KIND_DECODER -> "REDSTONE • 0..15";
            case FieldDeviceMenu.KIND_SERIALIZER -> "SERIAL_DATA • FRAME";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "DATA_BUS_8 • BYTE";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "DIFFERENTIAL";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "REDSTONE • 0..15";
            case FieldDeviceMenu.KIND_PNEUMATIC_RECEIVER -> "REDSTONE • 0..15";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "INDUCED ELECTRICAL";
            default -> "DECLARED DOMAIN";
        };
    }

    private String processorInput() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "SERIAL_DATA • DEGRADED/VALID";
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER, FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "QUARTZ TIMING";
            case FieldDeviceMenu.KIND_AMETHYST_FILTER, FieldDeviceMenu.KIND_AMETHYST_TUNED -> "AMETHYST RESONANCE";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER, FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "OPTICAL";
            default -> "REDSTONE • 0..15";
        };
    }

    private String processorOutput() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "SERIAL_DATA • REGENERATED";
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> "QUARTZ TIMING • DIVIDED";
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "TIMING DIAGNOSTIC";
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> "AMETHYST • FILTERED";
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "AMETHYST • RESONANT";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "OPTICAL • SELECTED CHANNEL";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "OPTICAL • ATTENUATED";
            case FieldDeviceMenu.KIND_EDGE_DETECTOR, FieldDeviceMenu.KIND_PULSE_SHAPER -> "REDSTONE • PULSE";
            default -> "REDSTONE • PROCESSED";
        };
    }

    private String processorFunction() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_FILTER -> "SLEW LIMIT";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "QUALITY GATE + REGENERATION";
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> "CLOCK DIVISION";
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "PERIOD STABILITY CHECK";
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> "FREQUENCY SELECTION";
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "RESONANT RESPONSE";
            case FieldDeviceMenu.KIND_EDGE_DETECTOR -> "EDGE DETECTION";
            case FieldDeviceMenu.KIND_PULSE_SHAPER -> "PULSE SHAPING";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "CHANNEL SELECTION";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "INTENSITY ATTENUATION";
            default -> "DECLARED TRANSFORM";
        };
    }

    private String processorParameter() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_FILTER -> "SLEW " + menu.tertiary();
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "THRESHOLD " + menu.tertiary();
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> "DIVIDE BY " + menu.tertiary();
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "ERROR " + menu.secondary();
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> "TARGET " + menu.tertiary();
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "Q INDEX " + menu.tertiary();
            case FieldDeviceMenu.KIND_EDGE_DETECTOR -> "MODE " + menu.tertiary();
            case FieldDeviceMenu.KIND_PULSE_SHAPER -> "WIDTH " + menu.tertiary();
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "CHANNEL " + menu.secondary();
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "LOSS " + menu.tertiary();
            default -> "SERVER CONFIG";
        };
    }

    private String observedInterface() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_SIGNAL_TAP -> oppositeFacingName() + " • REDSTONE SAMPLE";
            case FieldDeviceMenu.KIND_RANGE_SENSOR -> facingName() + " • FREE-SPACE SCAN";
            case FieldDeviceMenu.KIND_PROBE -> facingName() + " • REDSTONE TEST";
            case FieldDeviceMenu.KIND_PNEUMATIC_FLOW_METER -> oppositeFacingName() + " → " + facingName() + " • FLOW PATH";
            case FieldDeviceMenu.KIND_MAGNETIC_FIELD_SENSOR, FieldDeviceMenu.KIND_MAGNETIC_GRADIENT_METER -> "FREE SPACE • MAGNETIC FIELD";
            case FieldDeviceMenu.KIND_OPTICAL_POWER_METER -> facingName() + " • OPTICAL PROBE";
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> "LOCAL NETWORK • RESONANCE SPECTRUM";
            default -> facingName();
        };
    }

    private String facingName() {
        int ordinal = menu.facingOrdinal();
        if (ordinal < 0 || ordinal >= Direction.values().length) return "UNSPECIFIED";
        return Direction.values()[ordinal].getName().toUpperCase();
    }

    private String oppositeFacingName() {
        int ordinal = menu.facingOrdinal();
        if (ordinal < 0 || ordinal >= Direction.values().length) return "UNSPECIFIED";
        return Direction.values()[ordinal].getOpposite().getName().toUpperCase();
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
