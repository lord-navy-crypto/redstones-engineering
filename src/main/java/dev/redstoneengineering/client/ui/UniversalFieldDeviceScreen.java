package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.EntityDensitySensorBlock;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.TankLevelSensorBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.SensorModel;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Universal six-face engineering HMI backed only by synchronized server snapshots. */
public final class UniversalFieldDeviceScreen extends EngineeringScreen<UniversalFieldDeviceMenu> {
    private Button primaryPrevious;
    private Button primaryNext;
    private Button secondaryPrevious;
    private Button secondaryNext;
    private Button action;
    private Button toggle;

    public UniversalFieldDeviceScreen(UniversalFieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        primaryPrevious = addConfigureWidget(Button.builder(
                Component.literal("◀ Previous"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_PREVIOUS)
        ).bounds(leftPos + 38, topPos + 118, 116, 20).build());
        primaryNext = addConfigureWidget(Button.builder(
                Component.literal("Next ▶"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_NEXT)
        ).bounds(leftPos + 166, topPos + 118, 116, 20).build());
        secondaryPrevious = addConfigureWidget(Button.builder(
                Component.literal("◀ Range"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_PREVIOUS)
        ).bounds(leftPos + 38, topPos + 158, 116, 20).build());
        secondaryNext = addConfigureWidget(Button.builder(
                Component.literal("Range ▶"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_NEXT)
        ).bounds(leftPos + 166, topPos + 158, 116, 20).build());
        action = addConfigureWidget(Button.builder(
                Component.literal("Action"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_ACTION)
        ).bounds(leftPos + 38, topPos + 158, 244, 20).build());
        toggle = addConfigureWidget(Button.builder(
                Component.literal("Toggle"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_TOGGLE)
        ).bounds(leftPos + 38, topPos + 158, 244, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        int kind = menu.configKind();
        boolean configure = isConfigureSection();
        boolean primary = kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER
                || kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE
                || kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER
                || kind == UniversalFieldDeviceMenu.CONFIG_ALARM
                || kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD
                || kind == UniversalFieldDeviceMenu.CONFIG_CALIBRATION
                || kind == UniversalFieldDeviceMenu.CONFIG_PWM
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_LIGHT_SENSOR
                || kind == UniversalFieldDeviceMenu.CONFIG_TANK_LEVEL
                || kind == UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY
                || kind == UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD;
        boolean range = kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE
                || kind == UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY
                || kind == UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD;
        boolean hasAction = kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER
                || kind == UniversalFieldDeviceMenu.CONFIG_ALARM
                || kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER
                || kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK
                || kind == UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER;
        boolean hasToggle = kind == UniversalFieldDeviceMenu.CONFIG_PWM;

        if (primaryPrevious != null) primaryPrevious.visible = configure && primary;
        if (primaryNext != null) primaryNext.visible = configure && primary;
        if (secondaryPrevious != null) secondaryPrevious.visible = configure && range;
        if (secondaryNext != null) secondaryNext.visible = configure && range;
        if (kind == UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY) {
            secondaryPrevious.setMessage(Component.literal("◀ Aperture"));
            secondaryNext.setMessage(Component.literal("Aperture ▶"));
        } else if (kind == UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD) {
            secondaryPrevious.setMessage(Component.literal("◀ Sample"));
            secondaryNext.setMessage(Component.literal("Sample ▶"));
        } else {
            secondaryPrevious.setMessage(Component.literal("◀ Range"));
            secondaryNext.setMessage(Component.literal("Range ▶"));
        }
        if (kind == UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY) {
            secondaryPrevious.setMessage(Component.literal("◀ Aperture"));
            secondaryNext.setMessage(Component.literal("Aperture ▶"));
        } else {
            secondaryPrevious.setMessage(Component.literal("◀ Range"));
            secondaryNext.setMessage(Component.literal("Range ▶"));
        }
        if (action != null) {
            action.visible = configure && hasAction;
            action.active = kind != UniversalFieldDeviceMenu.CONFIG_ALARM || menu.configSecondary() == 2;
            if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER) action.setMessage(Component.literal("Reset measurement history"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_ALARM) action.setMessage(Component.literal(menu.configSecondary() == 2 ? "Acknowledge active alarm" : "Alarm already clear / acknowledged"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD) action.setMessage(Component.literal("Clear held value"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR) action.setMessage(Component.literal("Reset fault statistics"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER) action.setMessage(Component.literal("Reset sequence to IDLE"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK) action.setMessage(Component.literal("Reset diagnostic counters"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER) action.setMessage(Component.literal("Reset scan counters"));
        }
        if (toggle != null) {
            toggle.visible = configure && hasToggle;
            toggle.setMessage(Component.literal("Invert output • " + (menu.configSecondary() != 0 ? "ON" : "OFF")));
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
        int declared = Integer.bitCount(menu.declaredPortMask());
        int attention = attentionCount();
        statusBadge(g, title.getString().toUpperCase(), attention == 0 ? GOOD : WARN, 16, 80);
        statusBadge(g, lapisPrecisionMeasurementPresent() ? "LAPIS PRECISION" : "UNIVERSAL HMI", INFO, 207, 80);
        labelValue(g, "Declared interfaces", Integer.toString(declared), 106);
        labelValue(g, "Physical route", routeText(), 124);
        labelValue(g, "Attention ports", Integer.toString(attention), 142);
        sectionRule(g, 161);
        safeText(g, "Every displayed value and quality is synchronized from the logical server.", 16, 174, TEXT);
        safeText(g, lapisPrecisionMeasurementPresent()
                        ? "Lapis measurement uses the 0..100 precision-information domain; valid zero remains real evidence."
                        : "Use Ports for physical faces, Configure for parameters, and Route for real orientation.",
                16, 191, lapisPrecisionMeasurementPresent() ? INFO : MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "ALL SIX PHYSICAL FACES", INFO, 16, 80);
        Direction[] order = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int y = 102;
        for (Direction side : order) {
            portLine(g, side, y);
            y += 18;
        }
    }

    private void portLine(GuiGraphics g, Direction side, int y) {
        String face = side.getName().toUpperCase();
        if (!menu.hasPort(side)) {
            statusLine(g, face, "NO DECLARED ENGINEERING PORT", MUTED, y);
            return;
        }
        String role = menu.isBidirectional(side) ? "BIDIR"
                : menu.isInput(side) ? "INPUT"
                : menu.isOutput(side) ? "OUTPUT" : "PASSIVE";
        PortQuality quality = menu.quality(side);
        String value = menu.value(side) + " [" + menu.minimum(side) + ".." + menu.maximum(side) + "]";
        String text = role + " • " + menu.domain(side).label() + " • " + menu.portKind(side).name()
                + " • " + value + " • " + quality.name();
        statusLine(g, face, text, qualityColor(quality), y);
    }

    private void configure(GuiGraphics g) {
        int kind = menu.configKind();
        switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> {
                int radiusMode = menu.configPrimary();
                int sampleMode = menu.configSecondary();
                statusBadge(g, "MAGNETIC SENSOR APERTURE", INFO, 16, 80);
                labelValue(g, "Aperture radius", MagneticFieldSensorBlock.radiusForMode(radiusMode) + " blocks", 101);
                labelValue(g, "Sample period", MagneticFieldSensorBlock.samplePeriodForMode(sampleMode) + " ticks", 123);
                safeText(g, "Radius changes the physical free-space field aperture; sampling mode changes how often evidence is reacquired.", 16, 160, TEXT);
                safeText(g, "Changing either setting clears old coverage evidence before the next sample.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY -> {
                int profile = menu.configPrimary();
                int apertureMode = menu.configSecondary();
                statusBadge(g, "ENTITY DENSITY APERTURE", INFO, 16, 80);
                labelValue(g, "Acquisition profile", SensorModel.profileName(profile), 101);
                labelValue(g, "Aperture radius", EntityDensitySensorBlock.radiusForMode(apertureMode) + " blocks", 123);
                labelValue(g, "Sampling period", SensorModel.samplePeriod(profile) + " ticks", 145);
                labelValue(g, "Noise", "±" + SensorModel.noiseAmplitude(profile) + " / 100", 167);
                labelValue(g, "Latency", SensorModel.latencySamples(profile) + " sample", 189);
                safeText(g, "Changing profile or aperture invalidates prior measurement evidence before reacquisition.", 16, 207, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_TANK_LEVEL -> {
                int mode = menu.configPrimary();
                statusBadge(g, "TANK LEVEL RANGE", INFO, 16, 80);
                labelValue(g, "Full-scale height", TankLevelSensorBlock.heightForMode(mode) + " blocks", 101);
                labelValue(g, "Output scale", "0..15 normalized", 131);
                safeText(g, "Changing full scale recalibrates block height to redstone level and invalidates the prior normalized sample.", 16, 168, TEXT);
                safeText(g, "The UP aperture still preserves incomplete-chunk coverage as STALE evidence.", 16, 192, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_LIGHT_SENSOR -> {
                int profile = menu.configPrimary();
                statusBadge(g, "LIGHT SENSOR RESPONSE", INFO, 16, 80);
                labelValue(g, "Profile", SensorModel.profileName(profile) + " (" + profile + ")", 101);
                labelValue(g, "Sampling period", SensorModel.samplePeriod(profile) + " ticks", 123);
                labelValue(g, "Resolution", SensorModel.resolutionStep(profile) + " / 100", 145);
                labelValue(g, "Noise", "±" + SensorModel.noiseAmplitude(profile) + " / 100", 167);
                labelValue(g, "Latency", SensorModel.latencySamples(profile) + " sample", 189);
                safeText(g, "Profile changes acquisition behavior; changing it invalidates prior pending measurement evidence.", 16, 207, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> {
                statusBadge(g, "MEASUREMENT CONDITIONING", INFO, 16, 80);
                labelValue(g, "Profile", lapisProfileName(menu.configPrimary()) + " (" + menu.configPrimary() + ")", 101);
                safeText(g, "Profile changes sampling period, resolution, noise and latency on the server.", 16, 148, TEXT);
                safeText(g, "Direction belongs on Route; no routing control is duplicated here.", 16, 168, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> {
                statusBadge(g, "RANGE MEASUREMENT CONDITIONING", INFO, 16, 80);
                labelValue(g, "Profile", lapisProfileName(menu.configPrimary()) + " (" + menu.configPrimary() + ")", 101);
                labelValue(g, "Maximum range", menu.configSecondary() + " blocks", 141);
                safeText(g, "Changing profile or range invalidates the old sample before resampling.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> {
                statusBadge(g, "MOLECULAR RECEIVER", INFO, 16, 80);
                labelValue(g, "Sensitivity", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Retained peak", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Reset clears filtered/peak history; the fixed UP aperture remains unchanged.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> {
                int alarmState = menu.configSecondary();
                String label = alarmState == 0 ? "ALARM • CLEAR" : alarmState == 2 ? "ALARM • ACTIVE / UNACK" : "ALARM • ACTIVE / ACK";
                int color = alarmState == 0 ? GOOD : alarmState == 2 ? BAD : WARN;
                statusBadge(g, label, color, 16, 80);
                labelValue(g, "Severity", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Operator state", alarmStateName(alarmState), 141);
                safeText(g, "ACK changes operator-attention state; process RESET / CLEAR remains a physical input.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> {
                statusBadge(g, "SAMPLE & HOLD", INFO, 16, 80);
                labelValue(g, "Trigger mode", SampleHoldBlock.modeName(menu.configPrimary()), 101);
                labelValue(g, "Captures", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Clear held value is an operator action; TRIGGER and RESET remain physical ports.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> {
                statusBadge(g, "CALIBRATION PROFILE", INFO, 16, 80);
                labelValue(g, "Transfer", CalibrationModuleBlock.profileName(menu.configPrimary()), 101);
                safeText(g, "OBSERVED, REFERENCE and CALIBRATED faces rotate together on Route.", 16, 148, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_PWM -> {
                statusBadge(g, "PWM CONTROL", INFO, 16, 80);
                labelValue(g, "Period", PwmControllerBlock.periodFor(menu.configPrimary()) + " ticks", 101);
                labelValue(g, "Invert", menu.configSecondary() != 0 ? "ON" : "OFF", 141);
                safeText(g, "COMMAND, PWM OUT and INHIBIT rotate as one physical interface layout on Route.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> {
                boolean armed = menu.configSecondary() != 0;
                statusBadge(g, armed ? "FAULT INJECTOR • ARMED" : "FAULT INJECTOR • SAFE", armed ? WARN : GOOD, 16, 80);
                labelValue(g, "Fault mode", FaultInjectorBlock.modeLabelFor(menu.configPrimary()), 101);
                labelValue(g, "ARM state", armed ? "ARMED / INJECTION ACTIVE" : "SAFE / PASS-THROUGH", 141);
                safeText(g, "Reset statistics preserves ARM state and last I/O evidence; FAULT ARM remains a physical input.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> {
                statusBadge(g, "SEQUENCE CONTROLLER", menu.configPrimary() == 0 ? MUTED : GOOD, 16, 80);
                labelValue(g, "Current state", sequenceStepName(menu.configPrimary()), 101);
                labelValue(g, "Completed cycles", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Operator reset returns runtime state to IDLE; wired RESET remains independent.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> {
                boolean evaluated = menu.configPrimary() >= 0;
                boolean permit = menu.configSecondary() != 0;
                statusBadge(g, !evaluated ? "INTERLOCK • REACQUIRING" : permit ? "INTERLOCK • PERMIT" : "INTERLOCK • BLOCKED",
                        !evaluated ? INFO : permit ? GOOD : WARN, 16, 80);
                labelValue(g, "Missing permissives", failedPermissives(menu.configPrimary()), 101);
                labelValue(g, "Permit output", permit ? "15 / ENABLED" : "0 / BLOCKED", 141);
                safeText(g, !evaluated
                                ? "Diagnostic counters were reset; permissive evidence will repopulate on the next evaluation."
                                : "Reset counters does not bypass permissives A/B/C or force the permit output.",
                        16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER -> {
                statusBadge(g, attentionCount() == 0 ? "TOPOLOGY • NOMINAL" : "TOPOLOGY • ISSUE", attentionCount() == 0 ? GOOD : WARN, 16, 80);
                labelValue(g, "Scan count", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Target mode", menu.configSecondary() != 0 ? "VANILLA REDSTONE" : "ENGINEERING PORTS", 141);
                safeText(g, "SCAN is opposite alarm TX; reset clears retained scan counters only.", 16, 188, MUTED);
            }
            default -> {
                boolean rotatable = menu.rotatableSeriesAxis();
                statusBadge(g, lapisPrecisionMeasurementPresent() ? "PRECISION OBSERVER • NO PROCESS PARAMETER" : "NO UNIVERSAL PARAMETERS",
                        lapisPrecisionMeasurementPresent() ? INFO : MUTED, 16, 80);
                labelValue(g, "Current route", routeText(), 106);
                safeText(g, lapisPrecisionMeasurementPresent()
                                ? "The selected measurement face samples precision information; it does not drive or quantize the source."
                                : rotatable
                                ? "This device has a real routable interface; change it on Route, not Configure."
                                : "This device has no shared configurable parameter in the universal HMI.",
                        16, 132, TEXT);
                safeText(g, lapisPrecisionMeasurementPresent()
                                ? "Precision identity is observational: 0..100 engineering scale with 0.01 display resolution."
                                : "No client-side physics or hidden port mutation is performed.",
                        16, 152, lapisPrecisionMeasurementPresent() ? INFO : MUTED);
            }
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, attentionCount() == 0 ? "PORT EVIDENCE NOMINAL" : "PORT EVIDENCE ATTENTION",
                attentionCount() == 0 ? GOOD : WARN, 16, 80);
        int y = 104;
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            PortQuality quality = menu.quality(side);
            statusLine(g, side.getName().toUpperCase(), quality.name() + " • " + menu.domain(side).label(), qualityColor(quality), y);
            y += 18;
        }
        if (y == 104) safeText(g, "No EngineeringPortProvider interfaces are declared by this block.", 16, 108, WARN);
        if (lapisPrecisionMeasurementPresent() && y <= 190) {
            sectionRule(g, y + 2);
            statusLine(g, "Precision identity", lapisPrecisionText(), lapisPrecisionColor(), y + 14);
            safeText(g, lapisPrecisionNextAction(), 16, y + 34, lapisPrecisionColor());
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "LIVE ONLY • NO RETAINED HISTORY", INFO, 16, 80);
        labelValue(g, "Current evidence", "SYNCHRONIZED SNAPSHOT", 108);
        safeText(g, "Retained chronology belongs in analyzers, monitors, or the Diagnostic Tablet.", 16, 132, INFO);
        if (lapisPrecisionMeasurementPresent()) {
            labelValue(g, "Precision medium", "LAPIS • 0..100 • 0.01 display", 158);
            safeText(g, "History remains external even when the current precision sample is VALID.", 16, 180, MUTED);
        }
    }

    private int attentionCount() {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            PortQuality q = menu.quality(side);
            if (q != PortQuality.VALID) count++;
        }
        return count;
    }

    private boolean lapisPrecisionMeasurementPresent() {
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            if (menu.domain(side) == EngineeringDomain.LAPIS && menu.portKind(side) == PortKind.MEASUREMENT) return true;
        }
        return false;
    }

    private Direction lapisMeasurementSide() {
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            if (menu.domain(side) == EngineeringDomain.LAPIS && menu.portKind(side) == PortKind.MEASUREMENT) return side;
        }
        return null;
    }

    private String lapisPrecisionText() {
        Direction side = lapisMeasurementSide();
        if (side == null) return "N/A";
        PortQuality quality = menu.quality(side);
        if (quality != PortQuality.VALID) return quality.name() + " • precision unavailable";
        return String.format(java.util.Locale.ROOT, "%.2f • 0.01 resolution • VALID", menu.value(side) / 100.0);
    }

    private int lapisPrecisionColor() {
        Direction side = lapisMeasurementSide();
        return side == null ? MUTED : qualityColor(menu.quality(side));
    }

    private String lapisPrecisionNextAction() {
        Direction side = lapisMeasurementSide();
        if (side == null) return "";
        return switch (menu.quality(side)) {
            case VALID -> "NEXT • use this value as precision reference evidence; compare changes before quantizing to redstone.";
            case TOPOLOGY_ERROR -> "NEXT • resolve competing Lapis sources before selecting a precision value.";
            case STALE -> "NEXT • restore the sampled aperture/topology and reacquire precision evidence.";
            case NO_SIGNAL -> "NEXT • connect a unique Lapis precision source to the selected measurement face.";
            case SATURATED -> "NEXT • inspect measurement range/profile before treating the value as precise.";
            default -> "NEXT • repair the measurement evidence before using it as a reference.";
        };
    }

    private String routeText() {
        int ordinal = menu.facingOrdinal();
        if (ordinal < 0 || ordinal >= Direction.values().length) return "FIXED / NO ROUTE";
        Direction facing = Direction.values()[ordinal];
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS ->
                    facing.getOpposite().getName().toUpperCase() + " IN → " + facing.getName().toUpperCase() + " OUT";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "FRONT = " + facing.getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS ->
                    "TEST " + facing.getName().toUpperCase() + " ↔ BUS " + facing.getOpposite().getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE ->
                    "INTERFACE AXIS " + facing.getName().toUpperCase() + " ↔ " + facing.getOpposite().getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "MEASURE = " + facing.getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT ->
                    "UP APERTURE • FRONT OUT = " + facing.getName().toUpperCase();
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT ->
                    "MULTI-PORT LAYOUT • FRONT = " + facing.getName().toUpperCase();
            default -> "FIXED / NO ROUTE";
        };
    }

    private String routeKindLabel() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "SERIES I/O ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "ENDPOINT FRONT ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "PROBE AXIS ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "TERMINAL INTERFACE ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "MEASUREMENT FACE ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "OUTPUT FRONT ROUTING";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "MULTI-PORT LAYOUT ROUTING";
            default -> "READ-ONLY CONFIGURATION";
        };
    }

    private String routeRule() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "INPUT = opposite(OUTPUT)";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "FRONT selects the endpoint-facing side";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "TEST and BUS remain opposite";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "Vanilla and cable interfaces remain opposite";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "Only the selected face is sampled";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "UP input stays fixed; FRONT output rotates";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "FRONT, BACK and side ports rotate together";
            default -> "DEVICE-SPECIFIC / FIXED";
        };
    }

    private String routeDescription() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "Route cycles the complete directional interface layout on the server.";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "Route changes the real FRONT endpoint orientation and its physical connection side.";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "Route moves the real TEST aperture and the opposite BUS interface together.";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "Route rotates the real terminal interface pair and forces topology recalculation.";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "Route changes the actual sampled face; no synthetic output axis is implied.";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "Route changes only the real FRONT redstone output. The UP sensing aperture is fixed.";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "Route rotates the complete declared physical interface layout. Use Ports for the exact face roles.";
            default -> "This device has no universal routing action.";
        };
    }

    private String routeTooltip() {
        return switch (menu.routeKind()) {
            case UniversalFieldDeviceMenu.ROUTE_SERIES_AXIS -> "Cycle the server-authoritative directional interface layout.";
            case UniversalFieldDeviceMenu.ROUTE_ENDPOINT_FRONT -> "Cycle the server-authoritative FRONT endpoint direction.";
            case UniversalFieldDeviceMenu.ROUTE_PROBE_AXIS -> "Cycle the server-authoritative probe measurement axis.";
            case UniversalFieldDeviceMenu.ROUTE_TERMINAL_INTERFACE -> "Cycle the server-authoritative terminal interface axis.";
            case UniversalFieldDeviceMenu.ROUTE_MEASUREMENT_FACE -> "Cycle the server-authoritative measurement face.";
            case UniversalFieldDeviceMenu.ROUTE_FIXED_APERTURE_OUTPUT_FRONT -> "Cycle only the server-authoritative FRONT output direction.";
            case UniversalFieldDeviceMenu.ROUTE_MULTI_PORT_LAYOUT -> "Rotate every declared directional port together on the server.";
            default -> "No universal route action is available.";
        };
    }

    private static String sequenceStepName(int step) {
        return step <= 0 ? "IDLE" : "STEP " + Math.min(4, step);
    }

    private static String alarmStateName(int state) {
        return state == 0 ? "CLEAR" : state == 2 ? "ACTIVE • UNACKNOWLEDGED" : "ACTIVE • ACKNOWLEDGED";
    }

    private static String failedPermissives(int mask) {
        if (mask < 0) return "NOT EVALUATED";
        if (mask == 0) return "NONE";
        StringBuilder missing = new StringBuilder();
        if ((mask & 1) != 0) missing.append("A");
        if ((mask & 2) != 0) missing.append(missing.isEmpty() ? "B" : ", B");
        if ((mask & 4) != 0) missing.append(missing.isEmpty() ? "C" : ", C");
        return missing.toString();
    }

    private static String lapisProfileName(int profile) {
        return switch (Math.max(0, Math.min(3, profile))) {
            case 0 -> "FAST";
            case 1 -> "BALANCED";
            case 2 -> "PRECISION";
            default -> "RUGGED";
        };
    }

    private static int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> GOOD;
            case SATURATED, STALE, NO_SIGNAL -> WARN;
            case FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> BAD;
        };
    }
}
