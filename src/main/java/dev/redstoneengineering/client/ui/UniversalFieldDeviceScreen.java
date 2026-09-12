package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.core.port.PortQuality;
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
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR;
        boolean range = kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE;
        boolean hasAction = kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER
                || kind == UniversalFieldDeviceMenu.CONFIG_ALARM
                || kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD;
        boolean hasToggle = kind == UniversalFieldDeviceMenu.CONFIG_PWM;

        if (primaryPrevious != null) primaryPrevious.visible = configure && primary;
        if (primaryNext != null) primaryNext.visible = configure && primary;
        if (secondaryPrevious != null) secondaryPrevious.visible = configure && range;
        if (secondaryNext != null) secondaryNext.visible = configure && range;
        if (action != null) {
            action.visible = configure && hasAction;
            if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER) action.setMessage(Component.literal("Reset measurement history"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_ALARM) action.setMessage(Component.literal("Acknowledge active alarm"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD) action.setMessage(Component.literal("Clear held value"));
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
        statusBadge(g, "UNIVERSAL HMI", INFO, 207, 80);
        labelValue(g, "Declared interfaces", Integer.toString(declared), 106);
        labelValue(g, "Physical route", routeText(), 124);
        labelValue(g, "Attention ports", Integer.toString(attention), 142);
        sectionRule(g, 161);
        safeText(g, "Every displayed value and quality is synchronized from the logical server.", 16, 174, TEXT);
        safeText(g, "Use Ports for physical faces, Configure for parameters, and Route for real orientation.", 16, 191, MUTED);
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
                statusBadge(g, "ALARM PROCESSOR", INFO, 16, 80);
                labelValue(g, "Severity", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Activations", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "Acknowledge clears the operator-attention latch; process RESET remains a physical input.", 16, 188, MUTED);
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
                statusBadge(g, "FAULT INJECTOR", WARN, 16, 80);
                labelValue(g, "Fault mode", FaultInjectorBlock.modeLabelFor(menu.configPrimary()), 101);
                labelValue(g, "Activations", Integer.toString(menu.configSecondary()), 141);
                safeText(g, "SIGNAL IN, FAULT ARM and FAULTED OUT rotate together on Route.", 16, 188, MUTED);
            }
            default -> {
                boolean rotatable = menu.rotatableSeriesAxis();
                statusBadge(g, "NO UNIVERSAL PARAMETERS", MUTED, 16, 80);
                labelValue(g, "Current route", routeText(), 106);
                safeText(g, rotatable
                                ? "This device has a real routable interface; change it on Route, not Configure."
                                : "This device has no shared configurable parameter in the universal HMI.",
                        16, 132, TEXT);
                safeText(g, "No client-side physics or hidden port mutation is performed.", 16, 152, MUTED);
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
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "LIVE SNAPSHOT POLICY", INFO, 16, 80);
        safeText(g, "Universal HMI intentionally stores no client-local history.", 16, 108, TEXT);
        safeText(g, "Use dedicated analyzers/monitors when retained chronology is required.", 16, 128, INFO);
        safeText(g, "This prevents opening a UI from creating measurement evidence or changing simulation state.", 16, 150, MUTED);
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
            default -> "No universal route action is available.";
        };
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
