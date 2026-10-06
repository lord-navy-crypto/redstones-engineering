package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.RedstoneCableJunctionBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dense, reusable engineering dashboard for lightweight RSE devices.
 * It renders only synchronized menu data and client-synchronized BlockState metadata.
 */
public final class EnhancedFieldDeviceScreen extends EngineeringScreen<FieldDeviceMenu> {
    private EditBox directInput;
    private Button directApply;
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
    protected int virtualContentWidth(Section section) {
        return section == Section.PORTS ? 940 : 1000;
    }

    @Override
    protected int virtualContentHeight(Section section) {
        return section == Section.CONFIGURE ? 660 : 540;
    }

    @Override
    protected void addDeviceWidgets() {
        int gap = 10;
        int controlWidth = Math.min(150, Math.max(104, (imageWidth - 72 - gap * 2) / 3));
        int total = controlWidth * 3 + gap * 2;
        int startX = leftPos + (imageWidth - total) / 2;
        int y = topPos + 100;
        directInput = addConfigureWidget(new EditBox(this.font, startX, y, controlWidth, 20,
                Component.literal("Exact engineering value")));
        directInput.setMaxLength(3);
        directInput.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        directApply = addConfigureWidget(Button.builder(Component.literal("Apply exact value"),
                b -> submitDirectValue()).bounds(startX + controlWidth + gap, y, controlWidth, 20).build());

        minus = addConfigureWidget(Button.builder(Component.literal("−"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_DECREASE))
                .bounds(startX, y, controlWidth, 20).build());
        plus = addConfigureWidget(Button.builder(Component.literal("+"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_INCREASE))
                .bounds(startX + controlWidth + gap, y, controlWidth, 20).build());
        toggle = addConfigureWidget(Button.builder(Component.literal("Toggle"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_TOGGLE))
                .bounds(startX + (controlWidth + gap) * 2, y, controlWidth, 20).build());

        int presetGap = 8;
        int presetWidth = Math.min(84, Math.max(62, (imageWidth - 80 - presetGap * 3) / 4));
        int presetTotal = presetWidth * 4 + presetGap * 3;
        int presetX = leftPos + (imageWidth - presetTotal) / 2;
        int presetY = y + 34;
        p0 = preset(0, presetX, presetY, presetWidth, FieldDeviceMenu.BUTTON_PRESET_0);
        p5 = preset(5, presetX + presetWidth + presetGap, presetY, presetWidth, FieldDeviceMenu.BUTTON_PRESET_5);
        p10 = preset(10, presetX + (presetWidth + presetGap) * 2, presetY, presetWidth, FieldDeviceMenu.BUTTON_PRESET_10);
        p15 = preset(15, presetX + (presetWidth + presetGap) * 3, presetY, presetWidth, FieldDeviceMenu.BUTTON_PRESET_15);
    }

    private Button preset(int value, int x, int y, int width, int id) {
        return addConfigureWidget(Button.builder(Component.literal("Preset " + value), b -> sendMenuButton(id))
                .bounds(x, y, width, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (minus == null) return;
        boolean adjustable = switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE,
                 FieldDeviceMenu.KIND_FILTER,
                 FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_DIGITAL_REGENERATOR,
                 FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE,
                 FieldDeviceMenu.KIND_PERMANENT_MAGNET,
                 FieldDeviceMenu.KIND_INDUCTION_COIL,
                 FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER,
                 FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR,
                 FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> true;
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
        boolean direct = directEntryKind();
        directInput.visible = direct;
        directInput.active = direct;
        if (direct && !directInput.isFocused()) {
            String expected = Integer.toString(controlValue());
            if (!expected.equals(directInput.getValue())) directInput.setValue(expected);
        }
        directApply.visible = direct;
        directApply.active = direct && directInputValid();
        directApply.setMessage(Component.literal("Apply " + formulaSymbol()));
        minus.visible = adjustable && !direct;
        plus.visible = adjustable && !direct;
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
        } else if (menu.kind() == FieldDeviceMenu.KIND_MECHANICAL_EXCITER) {
            statusLine(g, "DOWN", "INPUT • REDSTONE DRIVE", GOOD, y);
            statusLine(g, "UP + HORIZONTAL", "OUTPUT • MECHANICAL_VIBRATION PACKET", INFO, y + 20);
            safeText(g, "Source amplitude comes from valid Redstone drive; configured frequency is an RSE model index.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_HYDRO_EXCITER) {
            statusLine(g, "DOWN", "INPUT • REDSTONE DRIVE", GOOD, y);
            statusLine(g, "UP + HORIZONTAL", "OUTPUT • HYDROACOUSTIC PACKET", INFO, y + 20);
            safeText(g, "The source launches the discrete RSE pressure-wave packet model, not a continuous fluid-acoustics solver.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_THERMAL_ENCODER) {
            statusLine(g, "DOWN", "INPUT • REDSTONE DRIVE", GOOD, y);
            statusLine(g, "UP + HORIZONTAL", "OUTPUT • PHONON_THERMAL EVENT PACKET", INFO, y + 20);
            safeText(g, "Encoder output is an event packet with finite retention, not a continuously driven temperature field.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_MECHANICAL_RECEIVER) {
            statusLine(g, oppositeFacingName(), "INPUT • MECHANICAL_VIBRATION", GOOD, y);
            statusLine(g, facingName(), "OUTPUT • REDSTONE 0..15", INFO, y + 20);
            safeText(g, "Receiver maps valid packet amplitude onto the declared Redstone output face.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_HYDRO_RECEIVER) {
            statusLine(g, oppositeFacingName(), "INPUT • HYDROACOUSTIC", GOOD, y);
            statusLine(g, facingName(), "OUTPUT • REDSTONE 0..15", INFO, y + 20);
            safeText(g, "Receiver maps valid pressure-packet amplitude onto the declared Redstone output face.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_THERMAL_RECEIVER) {
            statusLine(g, oppositeFacingName(), "INPUT • PHONON_THERMAL", GOOD, y);
            statusLine(g, facingName(), "OUTPUT • REDSTONE 0..15", INFO, y + 20);
            safeText(g, "Low-bandwidth receiver exposes packet amplitude; invalid/no packet is distinct from valid zero.", 16, 198, MUTED);
        } else if (menu.kind() == FieldDeviceMenu.KIND_SCULK_INTERFACE) {
            statusLine(g, oppositeFacingName(), "INPUT • SCULK / CALIBRATED-SENSOR EVENT CODE", GOOD, y);
            statusLine(g, facingName(), "OUTPUT • RETAINED EVENT-CODE REDSTONE", INFO, y + 20);
            safeText(g, "Opening the HMI reads retained counters only; it never creates a Sculk event or transition.", 16, 198, MUTED);
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
        if (isSourceMediumIntegrityDevice()) {
            statusBadge(g, "PIONEER PATTERN • SOURCE / MEDIUM INTEGRITY", INFO, 16, 80);
            formulaCard(g, sourceMediumContract(), 105);
            variableRole(g, "ROLE", "device", deviceRole(), sourceMediumDomain(), 134);
            variableRole(g, adjustable() ? "ADJUSTABLE" : "MEASURED",
                    directEntryKind() ? formulaSymbol() : sourceMediumPrimarySymbol(),
                    sourceMediumPrimaryValue(), sourceMediumPrimaryMeaning(), 152);
            if (directEntryKind()) {
                variableRole(g, "CONTROL", "direct entry", directRangeLabel(), "exact engineering value", 170);
            }
            variableRole(g, "EVIDENCE", "PortQuality", menu.evidenceQuality().name(),
                    "quality=" + menu.qualityPercent() + "%", directEntryKind() ? 188 : 170);
            variableRole(g, "EVIDENCE", "sources / drivers", Integer.toString(menu.driverCount()),
                    sourceOwnershipMeaning(), directEntryKind() ? 206 : 188);
            variableRole(g, "TOPOLOGY", "ports / links", menu.portCount() + " / " + menu.connectionCount(),
                    menu.topologyValid() ? "PASS" : "FAIL-CLOSED", directEntryKind() ? 224 : 206);
            variableRole(g, "AUTHORITY", "policy", sourceMediumAuthority(), "server synchronized", directEntryKind() ? 242 : 224);
            wrappedText(g, sourceMediumExplanation(), 16, directEntryKind() ? 268 : 250, workspaceWidth() - 24,
                    menu.evidenceQuality().name().equals("VALID") ? MUTED : WARN);
            return;
        }

        if (isDiscreteTransportDevice()) {
            statusBadge(g, "PIONEER PATTERN • RSE DISCRETE TRANSPORT MODEL", INFO, 16, 80);
            formulaCard(g, discreteTransportEquation(), 105);
            variableRole(g, "ROLE", "device", deviceRole(), discreteTransportDomain(), 134);
            variableRole(g, "MEASURED", discretePrimarySymbol(), discretePrimaryValue(), "server packet/event evidence", 152);
            variableRole(g, "MEASURED", discreteSecondarySymbol(), discreteSecondaryValue(), discreteSecondaryMeaning(), 170);
            variableRole(g, "EVIDENCE", "quality", menu.qualityPercent() + "%", evidenceState(), 188);
            variableRole(g, "TOPOLOGY", "ports / links", menu.portCount() + " / " + menu.connectionCount(),
                    menu.topologyValid() ? "PASS" : "FAIL-CLOSED", 206);
            variableRole(g, "AUTHORITY", "model", "SERVER ONLY", "client presents synchronized evidence", 224);
            wrappedText(g, discreteTransportExplanation(), 16, 250, workspaceWidth() - 24,
                    menu.topologyValid() ? MUTED : BAD);
            return;
        }

        statusBadge(g, "PIONEER PATTERN • SHARED FIELD DEVICE", adjustable() ? INFO : MUTED, 16, 80);
        formulaCard(g, pioneerContract(), 105);
        variableRole(g, "ROLE", "device", deviceRole(), family(), 134);
        if (adjustable()) {
            variableRole(g, "ADJUSTABLE", formulaSymbol(), controlValueText(), "server bounded", 152);
            if (directEntryKind()) {
                variableRole(g, "CONTROL", "direct entry", directRangeLabel(), "exact engineering value", 170);
            }
        } else {
            variableRole(g, "MEASURED", metricLabel(0), metricValue(0, menu.primary()), "server snapshot", 152);
        }
        variableRole(g, "EVIDENCE", "quality", menu.qualityPercent() + "%", evidenceState(), directEntryKind() ? 188 : 170);
        variableRole(g, "TOPOLOGY", "ports / links", menu.portCount() + " / " + menu.connectionCount(),
                menu.topologyValid() ? "PASS" : "FAIL-CLOSED", directEntryKind() ? 206 : 188);
        variableRole(g, "AUTHORITY", "policy", policy, "client presentation only", directEntryKind() ? 224 : 206);
        wrappedText(g, sharedPioneerExplanation(), 16, directEntryKind() ? 250 : 232, workspaceWidth() - 24,
                menu.topologyValid() ? MUTED : BAD);
    }

    private boolean isSourceMediumIntegrityDevice() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_TERMINAL,
                 FieldDeviceMenu.KIND_REDSTONE_CABLE,
                 FieldDeviceMenu.KIND_REDSTONE_JUNCTION,
                 FieldDeviceMenu.KIND_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_DATA_BUS_8,
                 FieldDeviceMenu.KIND_SERIAL_LINE,
                 FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR,
                 FieldDeviceMenu.KIND_LAPIS_LINE,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE,
                 FieldDeviceMenu.KIND_QUARTZ_LINE,
                 FieldDeviceMenu.KIND_AMETHYST_DUST,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> true;
            default -> false;
        };
    }

    private String sourceMediumContract() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE ->
                    "SOURCE: y_R = POWER ∈ [0,15]; configured zero remains VALID evidence";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE ->
                    "SOURCE: y_L = VALUE ∈ [0,100] on one configured horizontal LAPIS output";
            case FieldDeviceMenu.KIND_TERMINAL ->
                    "BOUNDARY: mode selects VANILLA→CABLE or CABLE→VANILLA; valid zero ≠ no source";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION ->
                    "NETWORK: strongest attached source wins; cable hop P_next=max(0,P-1); source count retained";
            case FieldDeviceMenu.KIND_LAPIS_LINE ->
                    "NETWORK: one LAPIS source → value 0..100; multi-source=TOPOLOGY_ERROR; truncated scan=STALE";
            case FieldDeviceMenu.KIND_QUARTZ_LINE ->
                    "NETWORK: one clock source → active+period; multi-source=TOPOLOGY_ERROR; truncated scan=STALE";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE ->
                    "BUS: 4 measurement channels; duplicate channel or truncated scan invalidates trustworthy evidence";
            case FieldDeviceMenu.KIND_DATA_BUS_8 ->
                    "BUS: payload is meaningful only with authoritative driver count + valid topology";
            case FieldDeviceMenu.KIND_SERIAL_LINE ->
                    "LINK: byte payload + frame timing + quality must be interpreted together";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR ->
                    "LINK: logic payload is meaningful only with valid balanced-pair quality evidence";
            case FieldDeviceMenu.KIND_AMETHYST_DUST ->
                    "NETWORK: resonance amplitude/frequency require ACTIVE evidence; conflict/stale are not zero";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER, FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION ->
                    "OPTICAL: intensity/channel require source + topology evidence; service-open means hard isolation";
            default -> "SOURCE / MEDIUM INTEGRITY";
        };
    }

    private String sourceMediumDomain() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_TERMINAL,
                 FieldDeviceMenu.KIND_REDSTONE_CABLE,
                 FieldDeviceMenu.KIND_REDSTONE_JUNCTION -> "REDSTONE / INSULATED_REDSTONE";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE,
                 FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "INSTRUMENT_BUS";
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "DATA_BUS_8";
            case FieldDeviceMenu.KIND_SERIAL_LINE -> "SERIAL_DATA";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> "DIFFERENTIAL_DATA";
            case FieldDeviceMenu.KIND_LAPIS_LINE, FieldDeviceMenu.KIND_LAPIS_SOURCE -> "LAPIS_PRECISION";
            case FieldDeviceMenu.KIND_QUARTZ_LINE -> "QUARTZ_TIMING";
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> "AMETHYST_RESONANCE";
            default -> "OPTICAL";
        };
    }

    private String sourceMediumPrimarySymbol() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE -> "POWER";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "VALUE";
            case FieldDeviceMenu.KIND_TERMINAL -> "signal";
            case FieldDeviceMenu.KIND_QUARTZ_LINE -> "clock";
            case FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "shield coverage";
            default -> metricLabel(0);
        };
    }

    private String sourceMediumPrimaryValue() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE -> menu.primary() + " / 15";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> menu.primary() + " / 100";
            case FieldDeviceMenu.KIND_TERMINAL -> menu.primary() + " / 15";
            case FieldDeviceMenu.KIND_QUARTZ_LINE -> (menu.primary() != 0 ? "HIGH" : "LOW") + " • " + menu.secondary() + "t";
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> String.format("0x%02X", menu.primary() & 0xFF);
            default -> metricValue(0, menu.primary());
        };
    }

    private String sourceMediumPrimaryMeaning() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE -> "Redstone reference output";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "precision source; ± changes by 5";
            case FieldDeviceMenu.KIND_TERMINAL -> menu.tertiary() == 1 ? "cable-derived output" : "Vanilla-derived input";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION -> "strongest-source propagated signal";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "measurement-bus evidence";
            default -> "authoritative server readback";
        };
    }

    private String sourceOwnershipMeaning() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION,
                 FieldDeviceMenu.KIND_LAPIS_LINE, FieldDeviceMenu.KIND_QUARTZ_LINE ->
                    "source ownership evidence";
            case FieldDeviceMenu.KIND_TERMINAL ->
                    menu.tertiary() == 1 ? "cable source count" : "attached Vanilla source present";
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "active drivers";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE -> "active channels";
            case FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE -> "cable nodes in shielding audit";
            default -> "not a source-counted medium";
        };
    }

    private String sourceMediumAuthority() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_REFERENCE, FieldDeviceMenu.KIND_LAPIS_SOURCE -> "SERVER SOURCE CONFIG";
            case FieldDeviceMenu.KIND_TERMINAL -> "SERVER BOUNDARY MODE";
            default -> "OBSERVER • NETWORK SOLVER OWNS STATE";
        };
    }

    private String sourceMediumExplanation() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_TERMINAL ->
                    "Terminal source presence is explicit: an attached legitimate zero-valued Vanilla source remains different from an empty terminal. Switching direction invalidates cached power before recomputation.";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE, FieldDeviceMenu.KIND_REDSTONE_JUNCTION ->
                    "Redstone cable keeps sourceCount as evidence. A truncated bounded traversal clears partial power/source evidence and reports STALE rather than publishing an incomplete answer.";
            case FieldDeviceMenu.KIND_LAPIS_LINE, FieldDeviceMenu.KIND_QUARTZ_LINE ->
                    "Source ownership and quality are synchronized from the server network. TOPOLOGY_ERROR means competing sources; STALE means the bounded scan could not prove a complete result.";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE, FieldDeviceMenu.KIND_SHIELDED_INSTRUMENT_CABLE ->
                    "Instrument quality comes from the authoritative probe scan. Duplicate channels or truncated traversal are topology evidence, while shielding/interference confidence stays a separate deterministic metric.";
            case FieldDeviceMenu.KIND_REFERENCE, FieldDeviceMenu.KIND_LAPIS_SOURCE ->
                    "Configured zero is still a real source value. Source orientation and numeric value are server configuration, not inferred from neighboring signal.";
            default ->
                    "Interpret payload/value only together with synchronized PortQuality, source/driver evidence and physical topology.";
        };
    }

    private boolean isDiscreteTransportDevice() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION,
                 FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HONEY_DAMPER,
                 FieldDeviceMenu.KIND_SCULK_INTERFACE,
                 FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER,
                 FieldDeviceMenu.KIND_PHONON_CONDUIT,
                 FieldDeviceMenu.KIND_THERMAL_ENCODER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER -> true;
            default -> false;
        };
    }

    private String discreteTransportEquation() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER ->
                    "SOURCE: A_emit = A_drive(valid), f = configured index";
            case FieldDeviceMenu.KIND_SLIME_VIBRATION ->
                    "HOP: A_next=max(0,A-1); RETAIN @4t: A←max(0,A-2), Q←max(0,Q-10)";
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER ->
                    "OUT: y_R=valid?min(15,A):0; RETAIN @4t: A←max(0,A-2), Q←max(0,Q-5)";
            case FieldDeviceMenu.KIND_HONEY_DAMPER ->
                    "HOP: A_next=max(0,A-4), node Q=80; RETAIN @4t: A←max(0,A-4), Q←max(0,Q-20)";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE ->
                    "EVENT: code_out=valid?clamp(code_in,0,15):0; transitions count every code change";
            case FieldDeviceMenu.KIND_HYDRO_TUBE ->
                    "HOP: A_next=max(0,A-Lm), Lm={water:1,milk-model:2,lava:3}; RETAIN @4t: A←max(0,A-2), Q←max(0,Q-10)";
            case FieldDeviceMenu.KIND_HYDRO_EXCITER ->
                    "SOURCE: A_emit = A_drive(valid), f = configured index";
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER ->
                    "OUT: y_R=valid?min(15,A):0; RETAIN @4t: A←max(0,A-1), Q←max(0,Q-5)";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT ->
                    "HOP: A_next=max(0,A-1); RETAIN @8t: A←max(0,A-2), Q←max(0,Q-10)";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER ->
                    "EVENT: valid Redstone drive → one bounded PHONON_THERMAL packet; source clears after 1t";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER ->
                    "OUT: y_R=valid?min(15,A):0; RETAIN @8t: A←max(0,A-1), Q←max(0,Q-5)";
            default -> "RSE discrete transport model";
        };
    }

    private String discreteTransportDomain() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION,
                 FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HONEY_DAMPER -> "MECHANICAL_VIBRATION";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> "SCULK EVENT-CODE BRIDGE";
            case FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER -> "HYDROACOUSTIC";
            default -> "PHONON_THERMAL";
        };
    }

    private String discretePrimarySymbol() {
        return menu.kind() == FieldDeviceMenu.KIND_SCULK_INTERFACE ? "code_now" : "A";
    }

    private String discretePrimaryValue() {
        return menu.kind() == FieldDeviceMenu.KIND_SCULK_INTERFACE
                ? menu.primary() + " / 15"
                : menu.primary() + " / 15";
    }

    private String discreteSecondarySymbol() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> "eventCount";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> "packet";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "y_R";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "packet_aux";
            default -> "f_idx";
        };
    }

    private String discreteSecondaryValue() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> Integer.toString(menu.secondary());
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> "event packet";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> Integer.toString(menu.secondary());
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> Integer.toString(menu.secondary());
            default -> Integer.toString(menu.secondary());
        };
    }

    private String discreteSecondaryMeaning() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_SCULK_INTERFACE ->
                    "last code " + menu.tertiary() + " • transitions " + menu.driverCount();
            case FieldDeviceMenu.KIND_HYDRO_TUBE ->
                    "frequency index • medium=" + hydroMedium(menu.tertiary());
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER ->
                    "frequency index • Redstone out " + menu.tertiary() + "/15";
            case FieldDeviceMenu.KIND_HONEY_DAMPER -> "frequency index • hop loss 4";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "Redstone output /15";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "packet selector";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> "source packet is event-like, not a level";
            default -> "RSE model index, not Hz";
        };
    }

    private String discreteTransportExplanation() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER ->
                    "RSE DISCRETE MODEL: this is a bounded game-domain pressure-packet network. It intentionally does not claim real continuous hydroacoustic spreading, absorption, bathymetry, temperature or salinity physics.";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER,
                 FieldDeviceMenu.KIND_PHONON_CONDUIT ->
                    "RSE DISCRETE MODEL: PHONON_THERMAL is a finite-bandwidth event-packet abstraction, not a Fourier heat-transfer or continuously driven temperature-field solver.";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE ->
                    "Event counters are retained server evidence. Reading this page never creates an event, transition or network drive.";
            default ->
                    "RSE DISCRETE MODEL: amplitude/frequency are bounded packet variables. Hop attenuation and local retained-packet decay are shown separately so propagation loss is not confused with time decay.";
        };
    }

    private String pioneerContract() {
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            return "TOPOLOGY: UP ↔ DOWN only; same medium; conversion = NONE";
        }
        if (isPassiveMedium()) {
            return "TOPOLOGY: connected faces = physical graph edges; medium identity is preserved";
        }
        if (isObserver()) {
            return "OBSERVE: physical/process state → synchronized evidence; network drive = NONE";
        }
        if (isDirectionalConverter()) {
            return "BOUNDARY: " + converterInput() + " → " + converterOutput();
        }
        if (isDirectionalProcessor()) {
            return "PROCESS: " + processorInput() + " → [" + processorFunction() + "] → " + processorOutput();
        }
        if (isCommunicationDevice()) {
            return "LINK: payload is meaningful only with route + quality + source evidence";
        }
        if (family().equals("CPS / RELIABILITY")) {
            return "STATE: safety/process state is server-authoritative; invalid evidence fails closed";
        }
        if (family().equals("PNEUMATIC")) {
            return "PROCESS: command / inlet → server pneumatic solve → realized state";
        }
        if (family().equals("MAGNETIC")) {
            return "FIELD: source / configuration → server field evidence; client does not solve B";
        }
        if (family().equals("OPTICAL")) {
            return "OPTICAL: channel + intensity + topology → authoritative optical state";
        }
        return "CONTRACT: server state → synchronized HMI evidence; no hidden client physics";
    }

    private String sharedPioneerExplanation() {
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            return "Junction Point remains a same-medium vertical riser: no conversion mode, no routing-mode toggle, and mixed media fail closed.";
        }
        if (isObserver()) {
            return "Observer controls can select what to measure, but never create network-drive evidence. A valid measured zero remains distinct from missing or invalid evidence.";
        }
        if (isPassiveMedium()) {
            return "Passive media expose continuity, medium identity, links and quality. Open faces are not virtual ports and the HMI never translates one medium into another.";
        }
        if (isDirectionalProcessor()) {
            return "The processing parameter is configuration; realized input/output and quality are evidence. Physical direction remains an explicit route, never an implied UI shortcut.";
        }
        if (isDirectionalConverter()) {
            return "The conversion boundary is explicit: input and output domains stay distinct and the client only presents the server-authoritative result.";
        }
        return "Buttons express operator intent to the server. Runtime state, topology, quality and physical behavior remain authoritative outside the client screen.";
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

    private boolean directEntryKind() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE,
                 FieldDeviceMenu.KIND_FILTER,
                 FieldDeviceMenu.KIND_REFERENCE,
                 FieldDeviceMenu.KIND_LAPIS_SOURCE,
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
    }

    private boolean directInputValid() {
        if (!directEntryKind() || directInput == null || directInput.getValue().isEmpty()) return false;
        try {
            int value = Integer.parseInt(directInput.getValue());
            return switch (menu.kind()) {
                case FieldDeviceMenu.KIND_PROBE -> value >= 0 && value <= 3;
                case FieldDeviceMenu.KIND_FILTER -> value >= 1 && value <= 4;
                case FieldDeviceMenu.KIND_REFERENCE -> value >= 0 && value <= 15;
                case FieldDeviceMenu.KIND_LAPIS_SOURCE -> value >= 0 && value <= 100 && value % 5 == 0;
                case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> value >= 0 && value <= 2;
                case FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                     FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> value >= 25 && value <= 100 && value % 25 == 0;
                case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> value >= 1 && value <= 15;
                case FieldDeviceMenu.KIND_INDUCTION_COIL -> value >= 1 && value <= 4;
                case FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                     FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> value >= 0 && value <= 15;
                case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> value >= 0 && value <= 8;
                case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                     FieldDeviceMenu.KIND_HYDRO_EXCITER -> value >= 1 && value <= 15;
                default -> false;
            };
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void submitDirectValue() {
        if (!directInputValid()) return;
        int value = Integer.parseInt(directInput.getValue());
        sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_DIRECT_BASE + value);
        directInput.setFocused(false);
    }

    private String formulaSymbol() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> "channel";
            case FieldDeviceMenu.KIND_FILTER -> "Δmax";
            case FieldDeviceMenu.KIND_REFERENCE -> "y_R";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "y_L";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "threshold";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "P_set";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "P_relief";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "B_src";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "N_turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "I_emit";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "channel";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "loss";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> "f";
            default -> adjustmentLabel();
        };
    }

    private String directRangeLabel() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> "0..3";
            case FieldDeviceMenu.KIND_FILTER -> "1..4 /sample";
            case FieldDeviceMenu.KIND_REFERENCE -> "0..15 redstone";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "0..100 Lapis • step 5";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "0..2 threshold profile";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR,
                 FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "{25, 50, 75, 100} pressure";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "1..15 B-index";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "1..4 turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "0..15 intensity";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "channel 0..15";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "loss 0..8";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> "1..15 frequency index";
            default -> "";
        };
    }

    private boolean adjustable() {
        return directEntryKind() || (minus != null && minus.active);
    }

    private int controlValue() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> menu.secondary();
            case FieldDeviceMenu.KIND_FILTER -> menu.tertiary();
            case FieldDeviceMenu.KIND_REFERENCE -> menu.primary();
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> menu.tertiary();
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> menu.secondary();
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> menu.secondary();
            default -> menu.tertiary() != 0 ? menu.tertiary() : menu.primary();
        };
    }

    private String adjustmentLabel() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> "Channel";
            case FieldDeviceMenu.KIND_FILTER -> "Slew";
            case FieldDeviceMenu.KIND_REFERENCE -> "Output";
            case FieldDeviceMenu.KIND_LAPIS_SOURCE -> "Value";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "Threshold";
            case FieldDeviceMenu.KIND_PRESSURE_REGULATOR -> "Setpoint";
            case FieldDeviceMenu.KIND_PNEUMATIC_RELIEF_VALVE -> "Relief";
            case FieldDeviceMenu.KIND_PERMANENT_MAGNET -> "Strength";
            case FieldDeviceMenu.KIND_INDUCTION_COIL -> "Turns";
            case FieldDeviceMenu.KIND_OPTICAL_EMITTER -> "Intensity";
            case FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER -> "Channel";
            case FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR -> "Loss";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER -> "Frequency";
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
        if (menu.kind() == FieldDeviceMenu.KIND_TERMINAL) return "BOUNDARY";
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
                 FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER,
                 FieldDeviceMenu.KIND_MECHANICAL_EXCITER,
                 FieldDeviceMenu.KIND_HYDRO_EXCITER,
                 FieldDeviceMenu.KIND_THERMAL_ENCODER -> "SOURCE";
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
        if (k == FieldDeviceMenu.KIND_LAPIS_SOURCE) return switch (slot) { case 0 -> "LAPIS VALUE"; case 1 -> "OUTPUT FACE"; default -> "QUALITY"; };
        if (k == FieldDeviceMenu.KIND_TERMINAL) return switch (slot) { case 0 -> "BOUNDARY SIGNAL"; case 1 -> "VANILLA INPUT"; default -> "MODE"; };
        if (k == FieldDeviceMenu.KIND_FILTER) return switch (slot) { case 0 -> "INPUT"; case 1 -> "OUTPUT"; default -> "SLEW"; };
        if (k == FieldDeviceMenu.KIND_PROBE) return switch (slot) { case 0 -> "SIGNAL"; case 1 -> "CHANNEL"; default -> "FACING"; };
        if (k >= FieldDeviceMenu.KIND_WATCHDOG && k <= FieldDeviceMenu.KIND_OPERATIONS_MONITOR) return switch (slot) { case 0 -> "PROCESS"; case 1 -> "STATUS"; default -> "SAFETY"; };
        if (k >= FieldDeviceMenu.KIND_AMETHYST_RESONATOR && k <= FieldDeviceMenu.KIND_THERMAL_RECEIVER) return switch (slot) { case 0 -> "AMPLITUDE"; case 1 -> "FREQUENCY"; default -> "STATE"; };
        return switch (slot) { case 0 -> "PROCESS"; case 1 -> "READBACK"; default -> "CONFIG"; };
    }

    private String metricValue(int slot, int value) {
        int k = menu.kind();
        if (k == FieldDeviceMenu.KIND_REFERENCE) {
            if (slot == 0) return menu.primary() + " / 15";
            if (slot == 1) return menu.evidenceQuality().name();
            return facingName();
        }
        if (k == FieldDeviceMenu.KIND_LAPIS_SOURCE) {
            if (slot == 0) return menu.primary() + " / 100";
            if (slot == 1) return facingName();
            return menu.evidenceQuality().name();
        }
        if (k == FieldDeviceMenu.KIND_TERMINAL) {
            if (slot == 0) return menu.primary() + " / 15";
            if (slot == 1) return menu.secondary() + " / 15";
            return menu.tertiary() == 1 ? "CABLE → VANILLA" : "VANILLA → CABLE";
        }
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
        if (menu.kind() == FieldDeviceMenu.KIND_LAPIS_SOURCE) return controlValue() + " / 100";
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
                 FieldDeviceMenu.KIND_OPTICAL_FIBER,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION,
                 FieldDeviceMenu.KIND_SLIME_VIBRATION,
                 FieldDeviceMenu.KIND_HONEY_DAMPER,
                 FieldDeviceMenu.KIND_HYDRO_TUBE,
                 FieldDeviceMenu.KIND_PHONON_CONDUIT -> true;
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
                 FieldDeviceMenu.KIND_INDUCTION_COIL,
                 FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER -> true;
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
            case FieldDeviceMenu.KIND_SLIME_VIBRATION, FieldDeviceMenu.KIND_HONEY_DAMPER -> "MECHANICAL_VIBRATION";
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> "HYDROACOUSTIC";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "PHONON_THERMAL";
            default -> "DEVICE I/O";
        };
    }

    private String routingContract() {
        if (menu.kind() == FieldDeviceMenu.KIND_SLIME_VIBRATION) return "MECHANICAL_VIBRATION • SIX-WAY • LOW-LOSS PACKET";
        if (menu.kind() == FieldDeviceMenu.KIND_HONEY_DAMPER) return "MECHANICAL_VIBRATION • SIX-WAY • HIGH-DAMPING PACKET";
        if (menu.kind() == FieldDeviceMenu.KIND_HYDRO_TUBE) return "HYDROACOUSTIC • SIX-WAY • MEDIUM-DEPENDENT LOSS";
        if (menu.kind() == FieldDeviceMenu.KIND_PHONON_CONDUIT) return "PHONON_THERMAL • SIX-WAY • FINITE-BANDWIDTH PACKET";
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
        return switch (menu.evidenceQuality()) {
            case VALID -> "VALID • SERVER READBACK";
            case NO_SIGNAL -> "NO SIGNAL / INVALID";
            case STALE -> "STALE • SERVER EVIDENCE INCOMPLETE";
            case TOPOLOGY_ERROR -> "TOPOLOGY ERROR • CONFLICT / INVALID PATH";
            case SATURATED -> "SATURATED • SERVER READBACK";
            case NOT_READY -> "NOT READY • EVIDENCE PENDING";
            case FAULT -> "FAULT • SERVER EVIDENCE";
            case DOMAIN_MISMATCH -> "DOMAIN MISMATCH • FAIL-CLOSED";
        };
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
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> "MECHANICAL_VIBRATION";
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER -> "HYDROACOUSTIC";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "PHONON_THERMAL";
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
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER,
                 FieldDeviceMenu.KIND_HYDRO_RECEIVER,
                 FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "REDSTONE • 0..15";
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

    private static String hydroMedium(int medium) {
        return switch (medium) {
            case 0 -> "water";
            case 1 -> "milk-model";
            case 2 -> "lava";
            default -> "unknown";
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
