package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.CalibrationModuleBlock;
import dev.redstoneengineering.block.EntityDensitySensorBlock;
import dev.redstoneengineering.block.EngineeringLightSensorBlock;
import dev.redstoneengineering.block.MagneticFieldSensorBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.PwmControllerBlock;
import dev.redstoneengineering.block.SampleHoldBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.block.RedstoneReferenceSourceBlock;
import dev.redstoneengineering.block.FaultLatchBlock;
import dev.redstoneengineering.block.TransmissionTopology;
import dev.redstoneengineering.block.RedstoneByteEncoderBlock;
import dev.redstoneengineering.block.ByteToRedstoneDecoderBlock;
import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.DifferentialDriverBlock;
import dev.redstoneengineering.block.WatchdogBlock;
import dev.redstoneengineering.block.RedundantVoterBlock;
import dev.redstoneengineering.block.SingleRelayBlock;
import dev.redstoneengineering.block.AnalogComparatorBlock;
import dev.redstoneengineering.block.TankLevelSensorBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.signal.ElectromagnetLogic;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
    private EditBox primaryTarget;
    private EditBox secondaryTarget;
    private Button primaryApply;
    private Button secondaryApply;
    private Button primaryMin;
    private Button primaryMax;
    private Button secondaryMin;
    private Button secondaryMax;

    private static final int PORT_HISTORY = 64;
    private static final int INVALID_SAMPLE = Integer.MIN_VALUE;
    private final int[] inputHistory = new int[PORT_HISTORY];
    private final int[] outputHistory = new int[PORT_HISTORY];
    private int portHistoryCount;

    public UniversalFieldDeviceScreen(UniversalFieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int pY = topPos + 112;
        int sY = topPos + 164;
        primaryPrevious = addConfigureWidget(Button.builder(
                Component.literal("◀ Fine"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_PREVIOUS)
        ).bounds(leftPos + 16, pY, 78, 20).build());
        primaryTarget = addConfigureWidget(new EditBox(
                font, leftPos + 100, pY, 62, 20, Component.literal("Primary target")));
        primaryTarget.setMaxLength(4);
        primaryTarget.setFilter(UniversalFieldDeviceScreen::numericEntry);
        primaryApply = addConfigureWidget(Button.builder(
                Component.literal("Apply"),
                button -> applyDirectTarget(true)
        ).bounds(leftPos + 168, pY, 52, 20).build());
        primaryNext = addConfigureWidget(Button.builder(
                Component.literal("Fine ▶"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_PRIMARY_NEXT)
        ).bounds(leftPos + 226, pY, 78, 20).build());
        primaryMin = addConfigureWidget(Button.builder(
                Component.literal("Min"), button -> applyBound(true, false)
        ).bounds(leftPos + 16, pY + 24, 54, 18).build());
        primaryMax = addConfigureWidget(Button.builder(
                Component.literal("Max"), button -> applyBound(true, true)
        ).bounds(leftPos + 250, pY + 24, 54, 18).build());

        secondaryPrevious = addConfigureWidget(Button.builder(
                Component.literal("◀ Fine"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_PREVIOUS)
        ).bounds(leftPos + 16, sY, 78, 20).build());
        secondaryTarget = addConfigureWidget(new EditBox(
                font, leftPos + 100, sY, 62, 20, Component.literal("Secondary target")));
        secondaryTarget.setMaxLength(4);
        secondaryTarget.setFilter(UniversalFieldDeviceScreen::numericEntry);
        secondaryApply = addConfigureWidget(Button.builder(
                Component.literal("Apply"),
                button -> applyDirectTarget(false)
        ).bounds(leftPos + 168, sY, 52, 20).build());
        secondaryNext = addConfigureWidget(Button.builder(
                Component.literal("Fine ▶"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_SECONDARY_NEXT)
        ).bounds(leftPos + 226, sY, 78, 20).build());
        secondaryMin = addConfigureWidget(Button.builder(
                Component.literal("Min"), button -> applyBound(false, false)
        ).bounds(leftPos + 16, sY + 24, 54, 18).build());
        secondaryMax = addConfigureWidget(Button.builder(
                Component.literal("Max"), button -> applyBound(false, true)
        ).bounds(leftPos + 250, sY + 24, 54, 18).build());

        action = addConfigureWidget(Button.builder(
                Component.literal("Action"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_ACTION)
        ).bounds(leftPos + 16, topPos + 218, 138, 20).build());
        toggle = addConfigureWidget(Button.builder(
                Component.literal("Toggle"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_CONFIG_TOGGLE)
        ).bounds(leftPos + 166, topPos + 218, 138, 20).build());
    }

    private static boolean numericEntry(String value) {
        if (value == null || value.isEmpty()) return true;
        if (value.length() > 4) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private void applyDirectTarget(boolean primary) {
        EditBox box = primary ? primaryTarget : secondaryTarget;
        if (box == null) return;
        try {
            int value = Integer.parseInt(box.getValue());
            int min = primary ? menu.editPrimaryMin() : menu.editSecondaryMin();
            int max = primary ? menu.editPrimaryMax() : menu.editSecondaryMax();
            if (value < min || value > max) {
                box.setTextColor(BAD);
                return;
            }
            box.setTextColor(TEXT);
            sendMenuButton((primary
                    ? UniversalFieldDeviceMenu.BUTTON_DIRECT_PRIMARY_BASE
                    : UniversalFieldDeviceMenu.BUTTON_DIRECT_SECONDARY_BASE) + value);
        } catch (NumberFormatException ignored) {
            box.setTextColor(BAD);
        }
    }

    private void applyBound(boolean primary, boolean maximum) {
        int value = primary
                ? (maximum ? menu.editPrimaryMax() : menu.editPrimaryMin())
                : (maximum ? menu.editSecondaryMax() : menu.editSecondaryMin());
        EditBox box = primary ? primaryTarget : secondaryTarget;
        if (box != null) box.setValue(Integer.toString(value));
        sendMenuButton((primary
                ? UniversalFieldDeviceMenu.BUTTON_DIRECT_PRIMARY_BASE
                : UniversalFieldDeviceMenu.BUTTON_DIRECT_SECONDARY_BASE) + value);
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        int kind = menu.configKind();
        boolean configure = isConfigureSection();
        boolean primary = menu.editPrimaryAvailable();
        boolean range = menu.editSecondaryAvailable();
        boolean hasAction = kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER
                || kind == UniversalFieldDeviceMenu.CONFIG_ALARM
                || kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER
                || kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK
                || kind == UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER
                || kind == UniversalFieldDeviceMenu.CONFIG_IRON_CORE
                || kind == UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH
                || kind == UniversalFieldDeviceMenu.CONFIG_ANALOG_INDICATOR
                || kind == UniversalFieldDeviceMenu.CONFIG_WATCHDOG
                || kind == UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER
                || kind == UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER;
        boolean hasToggle = kind == UniversalFieldDeviceMenu.CONFIG_PWM
                || kind == UniversalFieldDeviceMenu.CONFIG_CABLE_TERMINAL
                || kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY
                || kind == UniversalFieldDeviceMenu.CONFIG_SIGNAL_SELECTOR
                || kind == UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR;

        if (primaryPrevious != null) primaryPrevious.visible = configure && primary;
        if (primaryNext != null) primaryNext.visible = configure && primary;
        if (primaryTarget != null) {
            primaryTarget.visible = configure && primary;
            primaryTarget.active = primary;
            if (!primaryTarget.isFocused()) {
                primaryTarget.setValue(Integer.toString(menu.editPrimaryValue()));
                primaryTarget.setTextColor(TEXT);
            }
        }
        if (primaryApply != null) primaryApply.visible = configure && primary;
        if (primaryMin != null) {
            primaryMin.visible = configure && primary;
            primaryMin.setMessage(Component.literal("Min " + menu.editPrimaryMin()));
        }
        if (primaryMax != null) {
            primaryMax.visible = configure && primary;
            primaryMax.setMessage(Component.literal("Max " + menu.editPrimaryMax()));
        }

        if (secondaryPrevious != null) secondaryPrevious.visible = configure && range;
        if (secondaryNext != null) secondaryNext.visible = configure && range;
        if (secondaryTarget != null) {
            secondaryTarget.visible = configure && range;
            secondaryTarget.active = range;
            if (!secondaryTarget.isFocused()) {
                secondaryTarget.setValue(Integer.toString(menu.editSecondaryValue()));
                secondaryTarget.setTextColor(TEXT);
            }
        }
        if (secondaryApply != null) secondaryApply.visible = configure && range;
        if (secondaryMin != null) {
            secondaryMin.visible = configure && range;
            secondaryMin.setMessage(Component.literal("Min " + menu.editSecondaryMin()));
        }
        if (secondaryMax != null) {
            secondaryMax.visible = configure && range;
            secondaryMax.setMessage(Component.literal("Max " + menu.editSecondaryMax()));
        }

        String primaryName = primaryParameterName(kind);
        String secondaryName = secondaryParameterName(kind);
        if (primaryPrevious != null) {
            primaryPrevious.setMessage(kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY
                    ? Component.literal("◀ Pickup")
                    : Component.literal("◀ " + compactControlName(primaryName)));
            primaryPrevious.setTooltip(Tooltip.create(Component.literal("Fine -1 • " + primaryName)));
        }
        if (primaryNext != null) {
            primaryNext.setMessage(kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY
                    ? Component.literal("Pickup ▶")
                    : Component.literal(compactControlName(primaryName) + " ▶"));
            primaryNext.setTooltip(Tooltip.create(Component.literal("Fine +1 • " + primaryName)));
        }
        if (secondaryPrevious != null) {
            secondaryPrevious.setMessage(kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY
                    ? Component.literal("◀ Timing")
                    : Component.literal("◀ " + compactControlName(secondaryName)));
            secondaryPrevious.setTooltip(Tooltip.create(Component.literal("Fine -1 • " + secondaryName)));
        }
        if (secondaryNext != null) {
            secondaryNext.setMessage(kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY
                    ? Component.literal("Timing ▶")
                    : Component.literal(compactControlName(secondaryName) + " ▶"));
            secondaryNext.setTooltip(Tooltip.create(Component.literal("Fine +1 • " + secondaryName)));
        }
        if (action != null) {
            action.visible = configure && hasAction;
            action.active = (kind != UniversalFieldDeviceMenu.CONFIG_ALARM || menu.configSecondary() == 2)
                    && (kind != UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH || menu.configQuaternary() != 0);
            if (kind == UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER) action.setMessage(Component.literal("Reset measurement history"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_ALARM) action.setMessage(Component.literal(menu.configSecondary() == 2 ? "Acknowledge active alarm" : "Alarm already clear / acknowledged"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD) action.setMessage(Component.literal("Clear held value"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR) action.setMessage(Component.literal("Reset fault statistics"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER) action.setMessage(Component.literal("Reset to IDLE • require fresh RUN edge"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK) action.setMessage(Component.literal("Reset diagnostic counters"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_TOPOLOGY_DEBUGGER) action.setMessage(Component.literal("Reset scan counters"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_IRON_CORE) action.setMessage(Component.literal("Degauss core"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH) action.setMessage(Component.literal(
                    menu.configQuaternary() != 0 ? "Manual reset latch" : "Reset blocked • fault not clear"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_ANALOG_INDICATOR) action.setMessage(Component.literal("Reset retained min/max"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_WATCHDOG) action.setMessage(Component.literal("Reset watchdog diagnostics"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER) action.setMessage(Component.literal("Reset voter diagnostics"));
            else if (kind == UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER) action.setMessage(Component.literal("Reset clipping evidence"));
        }
        if (toggle != null) {
            toggle.visible = configure && hasToggle;
            if (kind == UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR) {
                toggle.setMessage(Component.literal("Compare • " + AnalogComparatorBlock.modeName(menu.configPrimary())));
            } else if (kind == UniversalFieldDeviceMenu.CONFIG_SIGNAL_SELECTOR) {
                toggle.setMessage(Component.literal(menu.configPrimary() != 0
                        ? "Select logic • INVERTED"
                        : "Select logic • NORMAL"));
            } else if (kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY) {
                toggle.setMessage(Component.literal(menu.configPrimary() != 0
                        ? "Contact • NC"
                        : "Contact • NO"));
            } else if (kind == UniversalFieldDeviceMenu.CONFIG_CABLE_TERMINAL) {
                toggle.setMessage(Component.literal(menu.configSecondary() != 0
                        ? "Mode • Cable → Vanilla"
                        : "Mode • Vanilla → Cable"));
            } else {
                toggle.setMessage(Component.literal("Invert output • " + (menu.configSecondary() != 0 ? "ON" : "OFF")));
            }
        }
    }

    private static String compactControlName(String name) {
        if (name == null || name.isBlank()) return "Step";
        return name.length() <= 8 ? name : name.substring(0, 8);
    }

    private static String primaryParameterName(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR -> "Hysteresis";
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER -> "Gain mode";
            case UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER -> "Tolerance";
            case UniversalFieldDeviceMenu.CONFIG_WATCHDOG -> "Timeout";
            case UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER -> "Threshold";
            case UniversalFieldDeviceMenu.CONFIG_SERIALIZER -> "Period";
            case UniversalFieldDeviceMenu.CONFIG_REGENERATOR -> "Threshold";
            case UniversalFieldDeviceMenu.CONFIG_BYTE_ENCODER, UniversalFieldDeviceMenu.CONFIG_BYTE_DECODER -> "Mode";
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> "Period idx";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH -> "Threshold";
            case UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE -> "Power";
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_PROBE -> "Channel";
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> "Radius";
            case UniversalFieldDeviceMenu.CONFIG_LIGHT_SENSOR -> "Profile";
            case UniversalFieldDeviceMenu.CONFIG_TANK_LEVEL -> "Range";
            case UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY -> "Profile";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE, UniversalFieldDeviceMenu.CONFIG_LAPIS_TRANSDUCER -> "Profile";
            case UniversalFieldDeviceMenu.CONFIG_MOLECULAR_RECEIVER -> "Sensitivity";
            case UniversalFieldDeviceMenu.CONFIG_ALARM -> "Severity";
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> "Trigger";
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> "Profile";
            case UniversalFieldDeviceMenu.CONFIG_PWM -> "Period";
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> "Fault mode";
            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> "Pickup";
            default -> "Parameter";
        };
    }

    private static String secondaryParameterName(int kind) {
        return switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_MAGNETIC_FIELD -> "Sampling";
            case UniversalFieldDeviceMenu.CONFIG_ENTITY_DENSITY -> "Aperture";
            case UniversalFieldDeviceMenu.CONFIG_LAPIS_RANGE -> "Range idx";
            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> "Timing";
            default -> "Parameter B";
        };
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

    private void renderNumericalWorkbench(GuiGraphics g, int kind) {
        statusBadge(g, kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY
                ? "RELAY PARAMETER WORKBENCH" : "NUMERICAL PARAMETER WORKBENCH", INFO, 16, 80);
        if (menu.editPrimaryAvailable()) {
            labelValue(g, primaryParameterName(kind),
                    menu.editPrimaryValue() + "   range " + menu.editPrimaryMin() + ".." + menu.editPrimaryMax(), 99);
        } else {
            labelValue(g, "Primary parameter", "READ ONLY / NONE", 99);
        }
        if (menu.editSecondaryAvailable()) {
            labelValue(g, secondaryParameterName(kind),
                    menu.editSecondaryValue() + "   range " + menu.editSecondaryMin() + ".." + menu.editSecondaryMax(), 151);
        }
        if (kind == UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY) {
            int pickupMode = menu.editPrimaryValue();
            int timingMode = menu.editSecondaryValue();
            int pickup = switch (pickupMode) { case 0 -> 1; case 1 -> 4; case 2 -> 8; default -> 12; };
            int dropout = Math.max(0, pickup - 2);
            labelValue(g, "Pickup / dropout", pickup + " / " + dropout, 183);
            labelValue(g, "Operate / release",
                    SingleRelayBlock.timingNameForMode(timingMode) + " • "
                            + SingleRelayBlock.operateDelayForMode(timingMode) + "/"
                            + SingleRelayBlock.releaseDelayForMode(timingMode) + "t", 195);
            safeText(g, "Timing • " + SingleRelayBlock.timingNameForMode(timingMode)
                    + " models finite armature travel; edit it independently from pickup/dropout.", 16, 211, INFO);
            safeText(g, "Pickup and timing are discrete relay profiles; Contact NO/NC remains the explicit toggle below.", 16, 229, MUTED);
        } else {
            safeText(g, "Fine buttons step the authoritative model; target boxes apply an exact bounded setting.", 16, 204, MUTED);
            safeText(g, "Open Model for equation/process meaning. Min/Max are server-validated presets.", 16, 214, MUTED);
        }
    }

    private void configure(GuiGraphics g) {
        int kind = menu.configKind();
        if (menu.editPrimaryAvailable() || menu.editSecondaryAvailable()) {
            renderNumericalWorkbench(g, kind);
            return;
        }
        switch (kind) {
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_COMPARATOR -> {
                int mode = menu.configPrimary();
                int hysteresis = menu.configSecondary();
                int margin = menu.configTertiary();
                int packed = menu.configQuaternary();
                boolean high = (packed & 1) != 0;
                PortQuality[] qualities = PortQuality.values();
                PortQuality processQuality = qualities[Math.min(qualities.length - 1, (packed >> 1) & 7)];
                PortQuality referenceQuality = qualities[Math.min(qualities.length - 1, (packed >> 4) & 7)];
                boolean processUsable = processQuality == PortQuality.VALID || processQuality == PortQuality.SATURATED;
                boolean referenceUsable = referenceQuality == PortQuality.VALID || referenceQuality == PortQuality.SATURATED;
                boolean evidenceUsable = processUsable && referenceUsable;
                boolean severe = processQuality == PortQuality.FAULT
                        || processQuality == PortQuality.DOMAIN_MISMATCH
                        || processQuality == PortQuality.TOPOLOGY_ERROR
                        || referenceQuality == PortQuality.FAULT
                        || referenceQuality == PortQuality.DOMAIN_MISMATCH
                        || referenceQuality == PortQuality.TOPOLOGY_ERROR;
                String badge = processQuality == PortQuality.NO_SIGNAL ? "ANALOG COMPARATOR • PROCESS NO SOURCE"
                        : referenceQuality == PortQuality.NO_SIGNAL ? "ANALOG COMPARATOR • REFERENCE NO SOURCE"
                        : !evidenceUsable ? "ANALOG COMPARATOR • EVIDENCE HOLD"
                        : "ANALOG COMPARATOR • " + AnalogComparatorBlock.modeName(mode);
                statusBadge(g, badge, severe ? BAD : evidenceUsable ? INFO : WARN, 16, 80);
                labelValue(g, "Hysteresis", "±" + hysteresis + " levels", 101);
                labelValue(g, "PROCESS evidence", processQuality.name(), 123);
                labelValue(g, "REFERENCE evidence", referenceQuality.name(), 145);
                labelValue(g, "Process-reference", evidenceUsable ? signed(margin) : "—", 167);
                labelValue(g, "Decision output", (high ? "HIGH" : "LOW") + (evidenceUsable ? "" : " • HELD"), 189);
                safeText(g, "Invalid PROCESS or REFERENCE evidence freezes the last decision; comparison resumes only when both live inputs are trustworthy again.", 16, 214, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_SELECTOR -> {
                boolean invert = menu.configPrimary() != 0;
                boolean selectedB = menu.configSecondary() != 0;
                int packed = menu.configQuaternary();
                boolean controlHold = (packed & 1) != 0;
                PortQuality[] qualities = PortQuality.values();
                PortQuality selectQuality = qualities[Math.min(qualities.length - 1, (packed >> 1) & 7)];
                PortQuality payloadQuality = qualities[Math.min(qualities.length - 1, (packed >> 4) & 7)];
                boolean selectIssue = selectQuality != PortQuality.VALID && selectQuality != PortQuality.SATURATED;
                boolean payloadIssue = payloadQuality != PortQuality.VALID && payloadQuality != PortQuality.SATURATED;
                boolean severe = selectQuality == PortQuality.FAULT
                        || selectQuality == PortQuality.DOMAIN_MISMATCH
                        || selectQuality == PortQuality.TOPOLOGY_ERROR
                        || payloadQuality == PortQuality.FAULT
                        || payloadQuality == PortQuality.DOMAIN_MISMATCH
                        || payloadQuality == PortQuality.TOPOLOGY_ERROR;
                String badge = controlHold ? "2:1 SIGNAL SELECTOR • CONTROL HOLD"
                        : selectQuality == PortQuality.NO_SIGNAL ? "2:1 SIGNAL SELECTOR • SELECT NO SOURCE"
                        : payloadIssue ? "2:1 SIGNAL SELECTOR • PAYLOAD " + payloadQuality.name()
                        : "2:1 SIGNAL SELECTOR • " + (selectedB ? "B SELECTED" : "A SELECTED");
                statusBadge(g, badge,
                        severe ? BAD : controlHold || selectIssue || payloadIssue ? WARN : INFO, 16, 80);
                labelValue(g, "Select logic", invert ? "INVERTED" : "NORMAL", 101);
                labelValue(g, "Active input", selectedB ? "B" : "A", 123);
                labelValue(g, "Control evidence", selectQuality.name() + (controlHold ? " • HOLD LAST" : ""), 145);
                labelValue(g, "Payload evidence", payloadQuality.name(), 167);
                labelValue(g, "Selection changes", Integer.toString(menu.configTertiary()), 189);
                safeText(g, "Missing SELECT may fall back to the configured default route, but OUT quality remains NO_SIGNAL so a broken control wire cannot look like an explicit LOW command.", 16, 214, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_TAP -> {
                int packed = menu.configQuaternary();
                boolean evidenceHold = (packed & 1) != 0;
                PortQuality inputQuality = syncedQuality((packed >> 1) & 7);
                int badEpisodes = Math.max(0, packed >>> 4);
                String badge = evidenceHold ? "BUFFERED SIGNAL TAP • EVIDENCE HOLD"
                        : inputQuality == PortQuality.NO_SIGNAL ? "BUFFERED SIGNAL TAP • NO SOURCE"
                        : evidenceSevere(inputQuality) ? "BUFFERED SIGNAL TAP • " + inputQuality.name()
                        : "BUFFERED SIGNAL TAP";
                statusBadge(g, badge,
                        evidenceSevere(inputQuality) ? BAD : evidenceHold || evidenceIssue(inputQuality) ? WARN : INFO, 16, 80);
                labelValue(g, "Copied level", menu.configPrimary() + " / 15", 101);
                labelValue(g, "Input evidence", inputQuality.name() + (evidenceHold ? " • HOLD LAST" : ""), 123);
                labelValue(g, "Main path", directionName(menu.configSecondary()) + " → " + directionName(menu.configTertiary()), 145);
                labelValue(g, "Bad evidence episodes", Integer.toString(badEpisodes), 167);
                safeText(g, "NO_SIGNAL de-energizes the copied outputs. STALE/FAULT/topology evidence retains the last trustworthy level instead of fabricating a new zero.", 16, 194, TEXT);
                safeText(g, "The TAP port remains one-way and never back-drives the process path.", 16, 216, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_LAPIS_SAMPLER -> {
                statusBadge(g, "QUARTZ-SYNCHRONIZED SAMPLE & HOLD", INFO, 16, 80);
                labelValue(g, "Held value", String.format(java.util.Locale.ROOT, "%.2f", menu.configPrimary() / 100.0), 101);
                labelValue(g, "Accepted captures", Integer.toString(menu.configSecondary()), 123);
                labelValue(g, "Rejected captures", Integer.toString(menu.configTertiary()), 145);
                safeText(g, "A Quartz rising edge captures the current Lapis precision sample. Invalid input evidence is rejected instead of overwriting the last good held value.", 16, 178, TEXT);
                safeText(g, "Rejected captures invalidate the output evidence until a later valid edge reacquires the sample.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_AMPLIFIER -> {
                int gain = switch (Math.max(0, Math.min(3, menu.configPrimary()))) {
                    case 0 -> 1;
                    case 1 -> 2;
                    case 2 -> 3;
                    default -> 4;
                };
                boolean clipping = menu.configSecondary() != 0;
                statusBadge(g, clipping ? "SIGNAL AMPLIFIER • CLIPPING" : "SIGNAL AMPLIFIER",
                        clipping ? WARN : GOOD, 16, 80);
                labelValue(g, "Gain", "x" + gain, 101);
                labelValue(g, "Clipping", clipping ? "ACTIVE" : "NO", 123);
                labelValue(g, "Clip episodes", Integer.toString(menu.configTertiary()), 145);
                safeText(g, "Dedicated gain stage: raises a redstone signal until the 0..15 headroom limit; excess gain clips rather than wrapping.", 16, 178, TEXT);
                safeText(g, "Use the conditioner for offset, clamp, threshold and deadband behavior; use this block when gain itself is the engineering task.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_REDUNDANT_VOTER -> {
                int tolerance = RedundantVoterBlock.toleranceValue(menu.configPrimary());
                int validInputs = menu.configSecondary();
                int spread = menu.configTertiary();
                boolean degraded = validInputs < 3 || spread > tolerance;
                statusBadge(g, degraded ? "2oo3 VOTER • DEGRADED" : "2oo3 VOTER • HEALTHY",
                        degraded ? WARN : GOOD, 16, 80);
                labelValue(g, "Tolerance", tolerance + " levels", 101);
                labelValue(g, "Valid inputs", validInputs + " / 3", 123);
                labelValue(g, "Current spread", Integer.toString(spread), 145);
                safeText(g, "The voter uses the median of three redstone measurements and marks the result degraded when redundancy or agreement is lost.", 16, 178, TEXT);
                safeText(g, "Tolerance controls acceptable disagreement; it does not fabricate a missing channel.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SINGLE_RELAY -> {
                boolean nc = menu.configPrimary() != 0;
                boolean coil = menu.configSecondary() != 0;
                boolean closed = nc ? !coil : coil;
                int packed = menu.configQuaternary();
                int pickupMode = packed & 3;
                boolean controlHold = (packed & 4) != 0;
                boolean payloadHold = (packed & 8) != 0;
                PortQuality[] qualities = PortQuality.values();
                int controlOrdinal = Math.min(qualities.length - 1, (packed >> 4) & 7);
                int payloadOrdinal = Math.min(qualities.length - 1, (packed >> 7) & 7);
                PortQuality controlQuality = qualities[controlOrdinal];
                PortQuality payloadQuality = qualities[payloadOrdinal];
                int timingMode = (packed >> 10) & 3;
                int remaining = (packed >> 12) & 15;
                boolean pendingPickup = (packed & (1 << 16)) != 0;
                boolean evidenceHold = controlHold || payloadHold;
                boolean controlIssue = controlQuality != PortQuality.VALID && controlQuality != PortQuality.SATURATED;
                boolean payloadIssue = closed
                        && payloadQuality != PortQuality.VALID
                        && payloadQuality != PortQuality.SATURATED;
                boolean severeIssue = controlQuality == PortQuality.FAULT
                        || controlQuality == PortQuality.DOMAIN_MISMATCH
                        || controlQuality == PortQuality.TOPOLOGY_ERROR
                        || (closed && (payloadQuality == PortQuality.FAULT
                        || payloadQuality == PortQuality.DOMAIN_MISMATCH
                        || payloadQuality == PortQuality.TOPOLOGY_ERROR));
                String badge = evidenceHold ? "SINGLE RELAY • EVIDENCE HOLD"
                        : remaining > 0 ? "SINGLE RELAY • " + (pendingPickup ? "OPERATING" : "RELEASING")
                        : controlQuality == PortQuality.NO_SIGNAL ? "SINGLE RELAY • CONTROL NO SOURCE"
                        : closed && payloadQuality == PortQuality.NO_SIGNAL ? "SINGLE RELAY • PAYLOAD NO SOURCE"
                        : "SINGLE RELAY • " + (closed ? "CONTACT CLOSED" : "CONTACT OPEN");
                statusBadge(g, badge,
                        severeIssue ? BAD
                                : evidenceHold || controlIssue || payloadIssue || remaining > 0 ? WARN
                                : closed ? GOOD : INFO,
                        16, 80);
                int pickup = switch (pickupMode) {
                    case 0 -> 1;
                    case 1 -> 4;
                    case 2 -> 8;
                    default -> 12;
                };
                int dropout = Math.max(0, pickup - 2);
                String controlEvidence = controlQuality.name() + (controlHold ? " HOLD" : "");
                String payloadEvidence = payloadQuality.name() + (payloadHold ? " HOLD" : "");
                String transition = remaining > 0
                        ? (pendingPickup ? "PICKUP " : "RELEASE ") + remaining + "t"
                        : "STEADY";
                labelValue(g, "Contact / coil",
                        (nc ? "NC" : "NO") + " • " + (coil ? "ENERGIZED" : "OFF"), 101);
                labelValue(g, "Pickup / dropout", pickup + " / " + dropout, 123);
                labelValue(g, "Operate / release",
                        SingleRelayBlock.timingNameForMode(timingMode) + " • "
                                + SingleRelayBlock.operateDelayForMode(timingMode) + "/"
                                + SingleRelayBlock.releaseDelayForMode(timingMode) + "t", 145);
                labelValue(g, "Mechanical state", transition + " • switches " + menu.configTertiary(), 167);
                labelValue(g, "Evidence", "CTRL=" + controlEvidence + " • PAY=" + payloadEvidence, 189);
                safeText(g, "Pickup/dropout models coil hysteresis; operate/release timing models finite armature travel. Bad control evidence freezes the actual armature and cancels an unfinished move.", 16, 214, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_TRACE -> {
                int sources = menu.configSecondary();
                statusBadge(g, sources > 1 ? "QUARTZ TRACE • CLOCK CONFLICT"
                                : sources == 0 ? "QUARTZ TRACE • NO SOURCE" : "QUARTZ TIMING TRACE",
                        sources > 1 ? WARN : sources == 0 ? INFO : GOOD, 16, 80);
                labelValue(g, "Clock period", menu.configPrimary() <= 0 ? "—" : menu.configPrimary() + " ticks", 101);
                labelValue(g, "Clock sources", Integer.toString(sources), 123);
                labelValue(g, "Current phase", menu.configTertiary() != 0 ? "HIGH" : "LOW", 145);
                safeText(g, "This trace distributes timing rather than payload. One authoritative clock source is required; multiple sources become a topology conflict.", 16, 178, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_INSTRUMENT_BUS -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "INSTRUMENTATION BUS • NO PROBES"
                        : evidence == PortQuality.STALE ? "INSTRUMENTATION BUS • STALE EVIDENCE"
                        : evidence == PortQuality.TOPOLOGY_ERROR ? "INSTRUMENTATION BUS • TOPOLOGY ERROR"
                        : evidenceIssue(evidence) ? "INSTRUMENTATION BUS • " + evidence.name()
                        : "INSTRUMENTATION BUS";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Valid channels", menu.configPrimary() + " / 4", 101);
                labelValue(g, "Evidence state", evidence.name(), 123);
                labelValue(g, "Interference confidence", menu.configSecondary() + "%", 145);
                labelValue(g, "Shielding coverage", menu.configTertiary() + "%", 167);
                safeText(g, "Channel PortQuality is propagated from each probe; shielding and interference confidence remain separate physical evidence.", 16, 194, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_WATCHDOG -> {
                int timeout = WatchdogBlock.timeoutTicks(menu.configPrimary());
                boolean timedOut = menu.configSecondary() >= timeout;
                boolean sourceSeen = menu.configQuaternary() != 0;
                String state = timedOut
                        ? "WATCHDOG • TIMEOUT"
                        : sourceSeen ? "WATCHDOG • MONITORING" : "WATCHDOG • NO VALID SOURCE";
                int color = timedOut || !sourceSeen ? WARN : GOOD;
                statusBadge(g, state, color, 16, 80);
                labelValue(g, "Timeout", timeout + " ticks", 101);
                labelValue(g, "Heartbeat age", menu.configSecondary() + " ticks", 123);
                labelValue(g, "Source acquired", sourceSeen ? "YES" : "NO", 145);
                labelValue(g, "Timeout count", Integer.toString(menu.configTertiary()), 167);
                safeText(g, "Only observed heartbeat transitions refresh supervision; a static level does not fake liveness.", 16, 194, TEXT);
                safeText(g, "Before a valid source is acquired the watchdog is in a grace window, not a healthy state. Missing or bad evidence continues aging toward TIMEOUT.", 16, 216, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_DATA_BUS -> {
                int drivers = menu.configTertiary();
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "8-BIT BUS • NO DRIVER"
                        : evidence == PortQuality.STALE ? "8-BIT BUS • STALE"
                        : evidence == PortQuality.TOPOLOGY_ERROR ? "8-BIT BUS • TOPOLOGY ERROR"
                        : evidenceIssue(evidence) ? "8-BIT BUS • EVIDENCE " + evidence.name()
                        : drivers > 1 ? "8-BIT BUS • MULTI-DRIVER" : "8-BIT DATA BUS";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD
                                : evidenceIssue(evidence) || drivers > 1 ? WARN : INFO,
                        16, 80);
                labelValue(g, "Resolved byte", menu.configPrimary() + " / 255", 101);
                labelValue(g, "Bus quality", menu.configSecondary() + "%", 123);
                labelValue(g, "Evidence state", evidence.name(), 145);
                labelValue(g, "Active drivers", Integer.toString(drivers), 167);
                safeText(g, "Quality percent describes usable bus margin; Evidence state distinguishes no driver, stale coverage, and topology conflict.", 16, 194, TEXT);
                safeText(g, "Different driven values become TOPOLOGY_ERROR; same-value multi-driving can remain valid while consuming margin.", 16, 216, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_DIFF_DRIVER -> {
                int threshold = DifferentialDriverBlock.thresholdValue(menu.configPrimary());
                PortQuality[] qualities = PortQuality.values();
                PortQuality inputQuality = qualities[Math.max(0,
                        Math.min(qualities.length - 1, menu.configQuaternary()))];
                boolean missing = inputQuality == PortQuality.NO_SIGNAL;
                boolean bad = inputQuality == PortQuality.STALE
                        || inputQuality == PortQuality.FAULT
                        || inputQuality == PortQuality.DOMAIN_MISMATCH
                        || inputQuality == PortQuality.TOPOLOGY_ERROR;
                statusBadge(g,
                        missing ? "DIFFERENTIAL DRIVER • INPUT NO SOURCE"
                                : bad ? "DIFFERENTIAL DRIVER • INPUT EVIDENCE BAD"
                                : "DIFFERENTIAL DRIVER",
                        bad ? BAD : missing ? WARN : INFO, 16, 80);
                labelValue(g, "Decision threshold", threshold + " / 15", 101);
                labelValue(g, "Redstone input", menu.configSecondary() + " / 15", 123);
                labelValue(g, "Input evidence", inputQuality.name(), 145);
                labelValue(g, "Driven bit", Integer.toString(menu.configTertiary()), 167);
                safeText(g, "The input port preserves the original 0..15 redstone measurement; only the differential output is thresholded into a digital bit.", 16, 194, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_DIFF_PAIR -> {
                int drivers = menu.configTertiary();
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "DIFFERENTIAL LINK • NO SOURCE"
                        : evidence == PortQuality.STALE ? "DIFFERENTIAL LINK • STALE"
                        : evidence == PortQuality.TOPOLOGY_ERROR ? "DIFFERENTIAL LINK • TOPOLOGY ERROR"
                        : evidenceIssue(evidence) ? "DIFFERENTIAL LINK • EVIDENCE " + evidence.name()
                        : "DIFFERENTIAL LINK";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Bit", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Link quality", menu.configSecondary() + "%", 123);
                labelValue(g, "Evidence state", evidence.name(), 145);
                labelValue(g, "Driver count", Integer.toString(drivers), 167);
                safeText(g, "Differential data trades payload density for stronger one-bit integrity; PortQuality identifies missing, stale, or conflicted authority.", 16, 194, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_DIFF_RECEIVER -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "DIFFERENTIAL RECEIVER • NO SOURCE"
                        : evidence == PortQuality.STALE ? "DIFFERENTIAL RECEIVER • STALE"
                        : evidenceIssue(evidence) ? "DIFFERENTIAL RECEIVER • INPUT " + evidence.name()
                        : "DIFFERENTIAL RECEIVER";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Received bit", Integer.toString(menu.configPrimary()), 101);
                labelValue(g, "Link quality", menu.configSecondary() + "%", 123);
                labelValue(g, "Input evidence", evidence.name(), 145);
                labelValue(g, "Redstone output", menu.configTertiary() + " / 15", 167);
                safeText(g, "Only a valid differential bit drives redstone; the discrete evidence state remains visible when fail-closed output is zero.", 16, 194, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SERIALIZER -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "SERIALIZER • BYTE SOURCE MISSING"
                        : evidence == PortQuality.STALE ? "SERIALIZER • INPUT STALE"
                        : evidenceIssue(evidence) ? "SERIALIZER • INPUT " + evidence.name()
                        : "SERIALIZER";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Word period", menu.configTertiary() + " ticks", 101);
                labelValue(g, "Current byte", menu.configSecondary() + " / 255", 123);
                labelValue(g, "Input evidence", evidence.name(), 145);
                safeText(g, "Configure selects 4/8/16 ticks per word. The serializer reports upstream bus authority separately from the retained/current byte.", 16, 178, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_DESERIALIZER -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "DESERIALIZER • SERIAL NO SOURCE"
                        : evidence == PortQuality.STALE ? "DESERIALIZER • INPUT STALE"
                        : evidenceIssue(evidence) ? "DESERIALIZER • INPUT " + evidence.name()
                        : "DESERIALIZER";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Recovered byte", menu.configPrimary() + " / 255", 101);
                labelValue(g, "Serial period", Math.max(1, menu.configSecondary()) + " ticks", 123);
                labelValue(g, "Frame quality", menu.configTertiary() + "%", 145);
                labelValue(g, "Input evidence", evidence.name(), 167);
                safeText(g, "Deserializer conversion remains fail-closed when serial authority is missing, stale, or topologically invalid.", 16, 194, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SERIAL_LINE -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "SERIAL LINK • NO SOURCE"
                        : evidence == PortQuality.STALE ? "SERIAL LINK • STALE"
                        : evidence == PortQuality.TOPOLOGY_ERROR ? "SERIAL LINK • TOPOLOGY ERROR"
                        : evidenceIssue(evidence) ? "SERIAL LINK • EVIDENCE " + evidence.name()
                        : "SERIAL LINK";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Current byte", menu.configPrimary() + " / 255", 101);
                labelValue(g, "Link quality", menu.configSecondary() + "%", 123);
                labelValue(g, "Evidence state", evidence.name(), 145);
                labelValue(g, "Utilization", menu.configTertiary() + "%", 167);
                safeText(g, "Serial quality percent describes link margin; PortQuality separately identifies source absence, stale coverage, and topology failure.", 16, 194, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_REGENERATOR -> {
                int minQuality = DigitalRegeneratorBlock.minimumQuality(menu.configPrimary());
                int packed = menu.configQuaternary();
                PortQuality inputEvidence = syncedQuality(packed & 7);
                PortQuality outputEvidence = syncedQuality((packed >> 3) & 7);
                int frameQuality = Math.max(0, Math.min(100, packed >> 6));
                String badge = inputEvidence == PortQuality.NO_SIGNAL ? "DIGITAL REGENERATOR • NO SOURCE"
                        : inputEvidence == PortQuality.STALE ? "DIGITAL REGENERATOR • INPUT STALE"
                        : evidenceSevere(inputEvidence) ? "DIGITAL REGENERATOR • INPUT " + inputEvidence.name()
                        : outputEvidence == PortQuality.FAULT ? "DIGITAL REGENERATOR • FRAME REJECTED"
                        : "DIGITAL REGENERATOR • ACCEPTING";
                int badgeColor = evidenceSevere(inputEvidence) ? BAD
                        : evidenceIssue(inputEvidence) || outputEvidence == PortQuality.FAULT ? WARN : GOOD;
                statusBadge(g, badge, badgeColor, 16, 80);
                labelValue(g, "Minimum quality", minQuality + "%", 101);
                labelValue(g, "Frame quality", frameQuality + "%", 123);
                labelValue(g, "Input evidence", inputEvidence.name(), 145);
                labelValue(g, "Output decision", outputEvidence.name(), 167);
                labelValue(g, "Accepted / rejected", menu.configSecondary() + " / " + menu.configTertiary(), 189);
                safeText(g, "A trustworthy serial source can still be rejected when frame quality falls below the configured regeneration threshold.", 16, 214, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_BYTE_ENCODER -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "REDSTONE → BYTE • NO SOURCE"
                        : evidence == PortQuality.STALE ? "REDSTONE → BYTE • INPUT STALE"
                        : evidenceIssue(evidence) ? "REDSTONE → BYTE • INPUT " + evidence.name()
                        : "REDSTONE → BYTE ENCODER";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Mapping mode", RedstoneByteEncoderBlock.modeName(menu.configPrimary()), 101);
                labelValue(g, "Redstone input", menu.configSecondary() + " / 15", 123);
                labelValue(g, "Input evidence", evidence.name(), 145);
                labelValue(g, "Byte output", menu.configTertiary() + " / 255", 167);
                safeText(g, menu.configPrimary() == RedstoneByteEncoderBlock.FULL_SCALE
                        ? "FULL_SCALE maps 0..15 to 0..255 in steps of 17; evidence state remains independent of the numeric mapping."
                        : "DIRECT maps redstone 0..15 to byte 0..15 without hiding missing or stale input authority.",
                        16, 200, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_BYTE_DECODER -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "BYTE → REDSTONE • NO BUS SOURCE"
                        : evidence == PortQuality.STALE ? "BYTE → REDSTONE • INPUT STALE"
                        : evidence == PortQuality.TOPOLOGY_ERROR ? "BYTE → REDSTONE • BUS TOPOLOGY ERROR"
                        : evidenceIssue(evidence) ? "BYTE → REDSTONE • INPUT " + evidence.name()
                        : "BYTE → REDSTONE DECODER";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Mapping mode", ByteToRedstoneDecoderBlock.modeName(menu.configPrimary()), 101);
                labelValue(g, "Byte input", menu.configSecondary() + " / 255", 123);
                labelValue(g, "Input evidence", evidence.name(), 145);
                labelValue(g, "Redstone output", menu.configTertiary() + " / 15", 167);
                safeText(g, menu.configPrimary() == ByteToRedstoneDecoderBlock.FULL_SCALE
                        ? "FULL_SCALE compresses 0..255 into redstone 0..15; invalid bus authority still fails closed."
                        : "CLAMP saturates byte values above 15, while NO_SIGNAL/STALE/TOPOLOGY_ERROR remain explicit evidence states.",
                        16, 200, TEXT);
            }
            case UniversalFieldDeviceMenu.CONFIG_JUNCTION -> {
                TransmissionTopology.SignalMedium[] media = TransmissionTopology.SignalMedium.values();
                int ordinal = Math.max(0, Math.min(media.length - 1, menu.configPrimary()));
                TransmissionTopology.SignalMedium medium = media[ordinal];
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                boolean usable = menu.configTertiary() != 0;
                boolean mismatch = medium == TransmissionTopology.SignalMedium.MISMATCH;
                String badge = mismatch ? "JUNCTION • MEDIUM MISMATCH"
                        : medium == TransmissionTopology.SignalMedium.NONE ? "JUNCTION • NO MEDIUM"
                        : evidence == PortQuality.NO_SIGNAL ? "JUNCTION • NO CARRIER"
                        : evidence == PortQuality.STALE ? "JUNCTION • CARRIER STALE"
                        : evidenceIssue(evidence) ? "JUNCTION • CARRIER " + evidence.name()
                        : "JUNCTION • ROUTING ONLY";
                statusBadge(g, badge,
                        mismatch || evidenceSevere(evidence) ? BAD
                                : evidenceIssue(evidence) ? WARN : usable ? GOOD : INFO,
                        16, 80);
                String carrier = switch (medium) {
                    case REDSTONE -> menu.configSecondary() + " / 15";
                    case INSTRUMENT -> menu.configSecondary() + " / 4 channels";
                    case DATA_BUS_8, SERIAL -> menu.configSecondary() + " / 255";
                    case DIFFERENTIAL -> "bit " + (menu.configSecondary() & 1);
                    case OPTICAL -> menu.configSecondary() + " / 15 intensity";
                    case COPPER -> menu.configSecondary() + " / 15 V-eq";
                    case NONE, MISMATCH -> "—";
                };
                labelValue(g, "Resolved medium", medium.getSerializedName().toUpperCase(), 101);
                labelValue(g, "Carrier value", carrier, 123);
                labelValue(g, "Carrier evidence", evidence.name(), 145);
                labelValue(g, "Carrier usability", usable ? "USABLE" : "NOT USABLE", 167);
                safeText(g, "The Junction Point projects the authoritative carrier snapshot for its resolved medium; it routes vertically but never converts domains.", 16, 194, TEXT);
                safeText(g, mismatch
                        ? "Different media on opposite sides become TOPOLOGY_ERROR and fail closed."
                        : "Byte, bit, channel, redstone, optical and copper payloads retain their own evidence semantics.",
                        16, 216, mismatch ? WARN : MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_ANALOG_INDICATOR -> {
                int min = menu.configSecondary();
                int max = menu.configTertiary();
                statusBadge(g, "ANALOG REDSTONE INDICATOR", INFO, 16, 80);
                labelValue(g, "Current level", menu.configPrimary() + " / 15", 101);
                labelValue(g, "Retained minimum", min < 0 ? "—" : min + " / 15", 123);
                labelValue(g, "Retained maximum", max < 0 ? "—" : max + " / 15", 145);
                safeText(g, "The indicator stays read-only but retains observed extrema so short excursions are not lost between inspections.", 16, 178, TEXT);
                safeText(g, "Reset min/max starts a new observation window without changing the redstone process.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_QUARTZ_OSCILLATOR -> {
                statusBadge(g, "QUARTZ TIMING SOURCE", menu.configSecondary() != 0 ? GOOD : INFO, 16, 80);
                labelValue(g, "Period", menu.configTertiary() + " ticks", 101);
                labelValue(g, "Half-cycle", Math.max(1, menu.configTertiary() / 2) + " ticks", 123);
                labelValue(g, "Output state", menu.configSecondary() != 0 ? "HIGH" : "LOW", 145);
                safeText(g, "Configure selects the clock period; Route selects the physical timing output face.", 16, 178, TEXT);
                safeText(g, "Use Quartz timing as the next layer beyond redstone levels: clocks, sampling, phase and sequencing.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_FAULT_LATCH -> {
                int thresholdIndex = menu.configPrimary();
                boolean resetPermitted = menu.configQuaternary() != 0;
                statusBadge(g, "FAULT LATCH", menu.configSecondary() != 0 ? WARN : GOOD, 16, 80);
                labelValue(g, "Trip threshold", FaultLatchBlock.thresholdValue(thresholdIndex) + " / 15", 101);
                labelValue(g, "Latched state", menu.configSecondary() != 0 ? "TRIPPED" : "CLEAR", 123);
                labelValue(g, "Trip count", Integer.toString(menu.configTertiary()), 145);
                labelValue(g, "Reset permissive", resetPermitted ? "YES • FAULT CLEAR" : "NO • BLOCKED", 167);
                safeText(g, "RESET is edge-triggered. Holding RESET high cannot suppress a continuing fault.", 16, 194, TEXT);
                safeText(g, "Electrical or manual reset is accepted only after FAULT evidence is clear; bad reset evidence must reacquire before another edge is accepted.", 16, 216, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_REDSTONE_CABLE -> {
                statusBadge(g, "INSULATED REDSTONE LINK", INFO, 16, 80);
                labelValue(g, "Received signal", menu.configPrimary() + " / 15", 101);
                labelValue(g, "Winning source", menu.configSecondary() + " / 15", 123);
                labelValue(g, "Attenuation loss", Integer.toString(menu.configTertiary()), 145);
                labelValue(g, "Remaining margin", Math.max(0, 15 - menu.configPrimary()) + " levels", 167);
                safeText(g, "The cable keeps Minecraft-style 0..15 attenuation, but exposes enough evidence to engineer path length and signal margin.", 16, 197, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_CABLE_TERMINAL -> {
                statusBadge(g, "REDSTONE CABLE TERMINAL", INFO, 16, 80);
                labelValue(g, "Boundary mode", menu.configSecondary() != 0 ? "CABLE → VANILLA" : "VANILLA → CABLE", 101);
                labelValue(g, "Boundary signal", menu.configPrimary() + " / 15", 123);
                labelValue(g, "Cable attenuation", Integer.toString(menu.configTertiary()), 145);
                safeText(g, "Toggle changes the active conversion direction. Route independently chooses the Vanilla/Cable physical axis.", 16, 178, TEXT);
                safeText(g, "This is the explicit boundary between vanilla redstone and the insulated engineering network.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_REFERENCE_SOURCE -> {
                statusBadge(g, "REDSTONE REFERENCE SOURCE", INFO, 16, 80);
                labelValue(g, "Output level", menu.configPrimary() + " / 15", 101);
                safeText(g, "Configure sets the authoritative laboratory reference level. Route selects the real output face independently.", 16, 150, TEXT);
                safeText(g, "Use it to inject known 0..15 values when calibrating conditioners, filters, thresholds, analyzers and control chains.", 16, 178, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SIGNAL_PROBE -> {
                PortQuality evidence = syncedQuality(menu.configQuaternary());
                String badge = evidence == PortQuality.NO_SIGNAL ? "INSTRUMENT PROBE • NO SOURCE"
                        : evidence == PortQuality.STALE ? "INSTRUMENT PROBE • STALE"
                        : evidenceIssue(evidence) ? "INSTRUMENT PROBE • " + evidence.name()
                        : "INSTRUMENT PROBE";
                statusBadge(g, badge,
                        evidenceSevere(evidence) ? BAD : evidenceIssue(evidence) ? WARN : INFO, 16, 80);
                labelValue(g, "Bus channel", SignalProbeBlock.channelName(menu.configPrimary()), 101);
                labelValue(g, "Measured redstone", menu.configSecondary() + " / 15", 123);
                labelValue(g, "Measurement evidence", evidence.name(), 145);
                safeText(g, "The probe preserves source PortQuality while placing the observed TEST value onto the selected Instrument Bus channel.", 16, 178, TEXT);
                safeText(g, "Route changes the physical TEST/BUS axis; channel selection never repairs or hides bad upstream evidence.", 16, 202, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_IRON_CORE -> {
                boolean complete = menu.configTertiary() != 0;
                statusBadge(g, "SOFT IRON CORE", complete ? INFO : WARN, 16, 80);
                labelValue(g, "Applied field", menu.configPrimary() + " / 15", 101);
                labelValue(g, "Remanent field", menu.configSecondary() + " / 15", 123);
                labelValue(g, "Coverage", complete ? "COMPLETE" : "INCOMPLETE / HOLD", 145);
                safeText(g, "Soft iron follows applied field with finite magnetization and retains only low, decaying remanence after excitation is removed.", 16, 177, TEXT);
                safeText(g, "Degauss explicitly clears retained magnetization; incomplete applied-field evidence freezes material state.", 16, 201, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_ELECTROMAGNET -> {
                int target = menu.configPrimary();
                int thermal = menu.configSecondary();
                int actual = menu.configTertiary();
                statusBadge(g, "ELECTROMAGNET COIL", thermal >= 850 ? BAD : thermal >= 700 ? WARN : INFO, 16, 80);
                labelValue(g, "Target / actual field", target + " / " + actual, 101);
                labelValue(g, "Tracking error", Integer.toString(menu.configQuaternary()), 123);
                labelValue(g, "Thermal load", thermal + " / 1000", 145);
                labelValue(g, "Thermal state", ElectromagnetLogic.thermalState(thermal), 167);
                safeText(g, "Copper excitation drives a finite inductive field response; sustained high excitation causes thermal derating until the coil cools.", 16, 197, MUTED);
            }
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
                labelValue(g, "Acquisition profile", EntityDensitySensorBlock.profileName(profile), 101);
                labelValue(g, "Aperture radius", EntityDensitySensorBlock.radiusForMode(apertureMode) + " blocks", 123);
                labelValue(g, "Sampling period", EntityDensitySensorBlock.profileSamplePeriod(profile) + " ticks", 145);
                labelValue(g, "Noise", "±" + EntityDensitySensorBlock.profileNoiseAmplitude(profile) + " / 100", 167);
                labelValue(g, "Latency", EntityDensitySensorBlock.profileLatencySamples(profile) + " sample", 189);
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
                labelValue(g, "Profile", EngineeringLightSensorBlock.profileName(profile) + " (" + profile + ")", 101);
                labelValue(g, "Sampling period", EngineeringLightSensorBlock.profileSamplePeriod(profile) + " ticks", 123);
                labelValue(g, "Resolution", EngineeringLightSensorBlock.profileResolutionStep(profile) + " / 100", 145);
                labelValue(g, "Noise", "±" + EngineeringLightSensorBlock.profileNoiseAmplitude(profile) + " / 100", 167);
                labelValue(g, "Latency", EngineeringLightSensorBlock.profileLatencySamples(profile) + " sample", 189);
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
                labelValue(g, "Operator state", alarmStateName(alarmState), 129);
                labelValue(g, "Activations", Integer.toString(menu.configTertiary()), 151);
                safeText(g, "ACK changes operator-attention state; process RESET / CLEAR remains a physical input.", 16, 188, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SAMPLE_HOLD -> {
                statusBadge(g, "SAMPLE & HOLD", INFO, 16, 80);
                labelValue(g, "Trigger mode", SampleHoldBlock.modeName(menu.configPrimary()), 101);
                labelValue(g, "Held value", menu.configTertiary() + " / 15", 123);
                labelValue(g, "Captures", Integer.toString(menu.configSecondary()), 145);
                safeText(g, "The value input is copied only on the configured trigger edge, then retained until another capture or RESET.", 16, 178, TEXT);
                safeText(g, "Clear held value is an operator action; TRIGGER and RESET remain physical ports.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_CALIBRATION -> {
                int residual = menu.configSecondary();
                int samples = menu.configTertiary();
                int packed = menu.configQuaternary();
                PortQuality observedQuality = syncedQuality(packed & 7);
                PortQuality referenceQuality = syncedQuality((packed >> 3) & 7);
                PortQuality outputQuality = syncedQuality((packed >> 6) & 7);
                String badge = observedQuality == PortQuality.NO_SIGNAL ? "CALIBRATION • OBSERVED NO SOURCE"
                        : referenceQuality == PortQuality.NO_SIGNAL ? "CALIBRATION • REFERENCE NO SOURCE"
                        : evidenceSevere(observedQuality) ? "CALIBRATION • OBSERVED " + observedQuality.name()
                        : evidenceSevere(referenceQuality) ? "CALIBRATION • REFERENCE " + referenceQuality.name()
                        : evidenceIssue(outputQuality) ? "CALIBRATION • OUTPUT " + outputQuality.name()
                        : "CALIBRATION PROFILE";
                statusBadge(g, badge,
                        evidenceSevere(outputQuality) ? BAD
                                : evidenceIssue(outputQuality) ? WARN
                                : samples <= 0 ? INFO
                                : Math.abs(residual) <= 1 ? GOOD : WARN,
                        16, 80);
                labelValue(g, "Transfer", CalibrationModuleBlock.profileName(menu.configPrimary()), 101);
                labelValue(g, "OBSERVED evidence", observedQuality.name(), 123);
                labelValue(g, "REFERENCE evidence", referenceQuality.name(), 145);
                labelValue(g, "Output evidence", outputQuality.name(), 167);
                labelValue(g, "Residual / samples", samples <= 0 ? "NO TRACEABLE DATA" : signed(residual) + " levels • n=" + samples, 189);
                safeText(g, "OBSERVED drives the numeric transfer. REFERENCE only authorizes new traceable calibration evidence; bad reference evidence never becomes a hidden control input.", 16, 214, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_PWM -> {
                int packed = menu.configQuaternary();
                int requested = packed & 15;
                int phase = (packed >> 4) & 31;
                boolean pending = (packed & (1 << 9)) != 0;
                int cycles = Math.max(0, packed >>> 10);
                int applied = menu.configTertiary();
                int period = PwmControllerBlock.periodFor(menu.configPrimary());
                statusBadge(g, pending ? "PWM • DUTY UPDATE PENDING" : "PWM CONTROL",
                        pending ? WARN : INFO, 16, 80);
                labelValue(g, "Period / phase", period + "t • " + phase + "/" + period, 101);
                labelValue(g, "Command requested / active", requested + " / " + applied, 123);
                labelValue(g, "Duty requested / active",
                        PwmControllerBlock.requestedDutyPermille(requested) / 10.0 + "% / "
                                + PwmControllerBlock.effectiveDutyPermille(applied, period) / 10.0 + "%", 145);
                labelValue(g, "Invert / completed cycles",
                        (menu.configSecondary() != 0 ? "ON" : "OFF") + " • " + cycles, 167);
                safeText(g, "Partial-duty commands are latched only at the carrier-cycle boundary, preventing a mid-cycle command change from creating a runt pulse. INHIBIT still shuts down immediately.", 16, 194, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_FAULT_INJECTOR -> {
                boolean armed = menu.configSecondary() != 0;
                int packed = menu.configQuaternary();
                PortQuality[] qualities = PortQuality.values();
                PortQuality signalQuality = qualities[Math.min(qualities.length - 1, packed & 7)];
                PortQuality armQuality = qualities[Math.min(qualities.length - 1, (packed >> 3) & 7)];
                boolean signalIssue = signalQuality != PortQuality.VALID && signalQuality != PortQuality.SATURATED;
                boolean armIssue = armQuality != PortQuality.VALID && armQuality != PortQuality.SATURATED;
                boolean severe = signalQuality == PortQuality.FAULT
                        || signalQuality == PortQuality.DOMAIN_MISMATCH
                        || signalQuality == PortQuality.TOPOLOGY_ERROR
                        || armQuality == PortQuality.FAULT
                        || armQuality == PortQuality.DOMAIN_MISMATCH
                        || armQuality == PortQuality.TOPOLOGY_ERROR;
                String badge = armQuality == PortQuality.NO_SIGNAL ? "FAULT INJECTOR • ARM NO SOURCE"
                        : armIssue ? "FAULT INJECTOR • ARM EVIDENCE BAD"
                        : signalIssue ? "FAULT INJECTOR • SIGNAL " + signalQuality.name()
                        : armed ? "FAULT INJECTOR • ARMED" : "FAULT INJECTOR • SAFE";
                statusBadge(g, badge, severe ? BAD : armIssue || signalIssue || armed ? WARN : GOOD, 16, 80);
                labelValue(g, "Fault mode", FaultInjectorBlock.modeLabelFor(menu.configPrimary()), 101);
                labelValue(g, "ARM state", armed ? "ARMED / INJECTION ACTIVE" : "SAFE / PASS-THROUGH", 123);
                labelValue(g, "SIGNAL evidence", signalQuality.name(), 145);
                labelValue(g, "ARM evidence", armQuality.name(), 167);
                labelValue(g, "Activations", Integer.toString(menu.configTertiary()), 189);
                safeText(g, "Only trustworthy HIGH ARM evidence authorizes injection. Missing or bad ARM evidence forces safe pass-through while remaining visible in OUT quality.", 16, 214, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SEQUENCE_CONTROLLER -> {
                PortQuality[] qualities = PortQuality.values();
                PortQuality runQuality = qualities[Math.max(0,
                        Math.min(qualities.length - 1, menu.configQuaternary()))];
                boolean runMissing = runQuality == PortQuality.NO_SIGNAL;
                boolean runBad = runQuality == PortQuality.STALE
                        || runQuality == PortQuality.FAULT
                        || runQuality == PortQuality.DOMAIN_MISMATCH
                        || runQuality == PortQuality.TOPOLOGY_ERROR;
                statusBadge(g,
                        runMissing ? "SEQUENCE • RUN NO SOURCE"
                                : runBad ? "SEQUENCE • RUN EVIDENCE BAD"
                                : "SEQUENCE CONTROLLER",
                        runBad ? BAD : runMissing ? WARN : menu.configPrimary() == 0 ? MUTED : GOOD,
                        16, 80);
                labelValue(g, "Current state", sequenceStepName(menu.configPrimary()), 101);
                labelValue(g, "RUN evidence", runQuality.name(), 123);
                labelValue(g, "Completed cycles", Integer.toString(menu.configSecondary()), 145);
                labelValue(g, "Transitions", Integer.toString(menu.configTertiary()), 167);
                safeText(g, "Operator reset returns runtime state to IDLE and reacquires RUN; a fresh LOW→HIGH RUN edge is required before STEP 1 can start again.", 16, 200, MUTED);
            }
            case UniversalFieldDeviceMenu.CONFIG_SAFETY_INTERLOCK -> {
                boolean evaluated = menu.configPrimary() >= 0;
                boolean permit = menu.configSecondary() != 0;
                statusBadge(g, !evaluated ? "INTERLOCK • REACQUIRING" : permit ? "INTERLOCK • PERMIT" : "INTERLOCK • BLOCKED",
                        !evaluated ? INFO : permit ? GOOD : WARN, 16, 80);
                labelValue(g, "Missing permissives", failedPermissives(menu.configPrimary()), 101);
                labelValue(g, "Permit output", permit ? "15 / ENABLED" : "0 / BLOCKED", 129);
                labelValue(g, "State transitions", Integer.toString(menu.configTertiary()), 151);
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
        EngineeringWorkbenchCatalog.UiPolicy policy = EngineeringWorkbenchCatalog.uiPolicy(menu);
        if (policy.tier() == EngineeringWorkbenchCatalog.UiTier.BLOCK) {
            statusBadge(g, "BLOCK EVIDENCE • LIVE ONLY", INFO, 16, 80);
            Direction in = firstInputSide();
            Direction out = firstOutputSide();
            labelValue(g, "Topology role", menu.topologyRoleLabel(), 106);
            labelValue(g, "Route", menu.portRouteLabel(), 126);
            labelValue(g, "Input now", in == null ? "—" : menu.value(in) + " • " + menu.quality(in).name(), 146);
            labelValue(g, "Output now", out == null ? "—" : menu.value(out) + " • " + menu.quality(out).name(), 166);
            labelValue(g, "Evidence", menu.evidenceStateLabel(), 186);
            safeText(g, "LIVE ONLY • NO RETAINED HISTORY", 16, 208, INFO);
            safeText(g, "Use an analyzer/oscilloscope when the experiment needs time-series evidence.", 16, 224, MUTED);
            return;
        }
        statusBadge(g, "UI OBSERVATION HISTORY • DISPLAY ONLY", INFO, 16, 80);
        // Compatibility and scientific-boundary contract: the device itself still owns no
        // retained server chronology. The graph below is only a client view of synchronized snapshots.
        String authoritativeHistory = "LIVE ONLY • NO RETAINED HISTORY";
        String evidenceLabel = "Current evidence";
        String evidenceValue = "SYNCHRONIZED SNAPSHOT";
        String chronologyBoundary = "Retained chronology belongs in analyzers, monitors, or the Diagnostic Tablet.";
        Direction in = firstInputSide();
        Direction out = firstOutputSide();
        if (portHistoryCount <= 1 || (in == null && out == null)) {
            labelValue(g, "Observation samples", Integer.toString(portHistoryCount), 108);
            safeText(g, "Keep the panel open to build a short client-side view of synchronized port evidence.", 16, 132, MUTED);
            safeText(g, "This display history is not authoritative device memory and never feeds simulation.", 16, 150, INFO);
            return;
        }

        int y = 112;
        if (in != null) {
            g.drawString(font, "INPUT • " + in.getName().toUpperCase(), 16, 98, MUTED, false);
            EngineeringPlot.analogFrame(g, 16, y, 132, 50);
            EngineeringPlot.analogTrace(g, portHistoryCount, i -> inputHistory[i],
                    menu.minimum(in), menu.maximum(in), 18, y + 3, 128, 44, INFO);
        }
        if (out != null) {
            g.drawString(font, "OUTPUT • " + out.getName().toUpperCase(), 160, 98, MUTED, false);
            EngineeringPlot.analogFrame(g, 160, y, 132, 50);
            EngineeringPlot.analogTrace(g, portHistoryCount, i -> outputHistory[i],
                    menu.minimum(out), menu.maximum(out), 162, y + 3, 128, 44, GOOD);
        }
        if (in != null) labelValue(g, "Input now", menu.value(in) + " • " + menu.quality(in).name(), 170);
        if (out != null) labelValue(g, "Output now", menu.value(out) + " • " + menu.quality(out).name(), 186);
        labelValue(g, evidenceLabel, evidenceValue, 202);
        safeText(g, authoritativeHistory, 16, 216, INFO);
        safeText(g, chronologyBoundary + " • source data stay server-owned", 16, 228, MUTED);
    }


    @Override
    protected void containerTick() {
        super.containerTick();
        if (EngineeringWorkbenchCatalog.uiPolicy(menu).tier() != EngineeringWorkbenchCatalog.UiTier.BLOCK) {
            recordPortHistory();
        }
    }

    private void recordPortHistory() {
        Direction in = firstInputSide();
        Direction out = firstOutputSide();
        int inValue = observedValue(in);
        int outValue = observedValue(out);
        if (portHistoryCount < PORT_HISTORY) {
            inputHistory[portHistoryCount] = inValue;
            outputHistory[portHistoryCount] = outValue;
            portHistoryCount++;
            return;
        }
        System.arraycopy(inputHistory, 1, inputHistory, 0, PORT_HISTORY - 1);
        System.arraycopy(outputHistory, 1, outputHistory, 0, PORT_HISTORY - 1);
        inputHistory[PORT_HISTORY - 1] = inValue;
        outputHistory[PORT_HISTORY - 1] = outValue;
    }

    private int observedValue(Direction side) {
        if (side == null) return INVALID_SAMPLE;
        PortQuality q = menu.quality(side);
        return q == PortQuality.VALID || q == PortQuality.SATURATED ? menu.value(side) : INVALID_SAMPLE;
    }

    private Direction firstInputSide() {
        for (Direction side : Direction.values()) {
            if (menu.hasPort(side) && (menu.isInput(side) || menu.isBidirectional(side))) return side;
        }
        return null;
    }

    private Direction firstOutputSide() {
        for (Direction side : Direction.values()) {
            if (menu.hasPort(side) && (menu.isOutput(side) || menu.isBidirectional(side))) return side;
        }
        return null;
    }

    private static PortQuality syncedQuality(int ordinal) {
        PortQuality[] qualities = PortQuality.values();
        return qualities[Math.max(0, Math.min(qualities.length - 1, ordinal))];
    }

    private static boolean evidenceIssue(PortQuality quality) {
        return quality != PortQuality.VALID && quality != PortQuality.SATURATED;
    }

    private static boolean evidenceSevere(PortQuality quality) {
        return quality == PortQuality.FAULT
                || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
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

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String directionName(int ordinal) {
        Direction[] values = Direction.values();
        int index = Math.max(0, Math.min(values.length - 1, ordinal));
        return values[index].getName().toUpperCase(java.util.Locale.ROOT);
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
