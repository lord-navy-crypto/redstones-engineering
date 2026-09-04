package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Context-sensitive inspector for probes, small processors, terminals and cable topology. */
public final class FieldDeviceScreen extends EngineeringScreen<FieldDeviceMenu> {
    private Button decrease;
    private Button increase;
    private Button toggle;
    private Button preset0;
    private Button preset5;
    private Button preset10;
    private Button preset15;

    public FieldDeviceScreen(FieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int x = leftPos + 16;
        int y = topPos + 108;
        int w = 88;
        int gap = 6;
        decrease = addConfigureWidget(Button.builder(Component.literal("−"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_DECREASE)).bounds(x, y, w, 20).build());
        increase = addConfigureWidget(Button.builder(Component.literal("+"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_INCREASE)).bounds(x + w + gap, y, w, 20).build());
        toggle = addConfigureWidget(Button.builder(Component.literal("Toggle mode"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_TOGGLE)).bounds(x + (w + gap) * 2, y, w, 20).build());
        preset0 = addConfigureWidget(Button.builder(Component.literal("Preset 0"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_0)).bounds(x, y + 26, 64, 20).build());
        preset5 = addConfigureWidget(Button.builder(Component.literal("Preset 5"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_5)).bounds(x + 70, y + 26, 64, 20).build());
        preset10 = addConfigureWidget(Button.builder(Component.literal("Preset 10"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_10)).bounds(x + 140, y + 26, 64, 20).build());
        preset15 = addConfigureWidget(Button.builder(Component.literal("Preset 15"),
                b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_15)).bounds(x + 210, y + 26, 64, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (decrease == null) return;
        boolean primaryAdjust = menu.kind() == FieldDeviceMenu.KIND_PROBE
                || menu.kind() == FieldDeviceMenu.KIND_FILTER
                || menu.kind() == FieldDeviceMenu.KIND_REFERENCE;
        decrease.active = primaryAdjust;
        increase.active = primaryAdjust;
        toggle.active = menu.kind() == FieldDeviceMenu.KIND_TERMINAL;
        boolean presets = menu.kind() == FieldDeviceMenu.KIND_REFERENCE;
        preset0.active = presets;
        preset5.active = presets;
        preset10.active = presets;
        preset15.active = presets;

        switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> {
                decrease.setMessage(Component.literal("◀ Channel"));
                increase.setMessage(Component.literal("Channel ▶"));
            }
            case FieldDeviceMenu.KIND_FILTER -> {
                decrease.setMessage(Component.literal("− Slew"));
                increase.setMessage(Component.literal("Slew +"));
            }
            case FieldDeviceMenu.KIND_REFERENCE -> {
                decrease.setMessage(Component.literal("− Output"));
                increase.setMessage(Component.literal("Output +"));
            }
            default -> {
                decrease.setMessage(Component.literal("−"));
                increase.setMessage(Component.literal("+"));
            }
        }
        toggle.setMessage(Component.literal(menu.tertiary() == 1 ? "Cable → Vanilla" : "Vanilla → Cable"));
    }

    @Override
    protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) {
            case OVERVIEW -> renderOverview(graphics);
            case PORTS -> renderPorts(graphics);
            case CONFIGURE -> renderConfigure(graphics);
            case DIAGNOSTICS -> renderDiagnostics(graphics);
            case HISTORY -> renderHistory(graphics);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> {
                statusBadge(graphics, "NON-INVASIVE PROBE", GOOD, 16, 80);
                labelValue(graphics, "Channel", SignalProbeBlock.channelName(menu.secondary()), 104);
                labelValue(graphics, "Measured signal", menu.primary() + " / 15", 120);
                labelValue(graphics, "TEST face", directionName(menu.facingOrdinal()), 136);
                labelValue(graphics, "BUS face", oppositeDirectionName(menu.facingOrdinal()), 152);
                signalBar(graphics, menu.primary(), 170);
            }
            case FieldDeviceMenu.KIND_FILTER -> {
                statusBadge(graphics, "SLEW-LIMIT FILTER", INFO, 16, 80);
                labelValue(graphics, "Input", menu.primary() + " / 15", 104);
                labelValue(graphics, "Output", menu.secondary() + " / 15", 120);
                labelValue(graphics, "Slew rate", menu.tertiary() + " signal-step/tick", 136);
                labelValue(graphics, "FRONT", directionName(menu.facingOrdinal()), 152);
                signalBar(graphics, menu.secondary(), 170);
            }
            case FieldDeviceMenu.KIND_REFERENCE -> {
                statusBadge(graphics, "LAB REFERENCE", GOOD, 16, 80);
                labelValue(graphics, "Output", menu.primary() + " / 15", 105);
                labelValue(graphics, "FRONT OUT", directionName(menu.facingOrdinal()), 121);
                labelValue(graphics, "Engineering ports", Integer.toString(menu.portCount()), 137);
                signalBar(graphics, menu.primary(), 160);
            }
            case FieldDeviceMenu.KIND_TERMINAL -> {
                statusBadge(graphics, menu.tertiary() == 1 ? "CABLE → VANILLA" : "VANILLA → CABLE", INFO, 16, 80);
                labelValue(graphics, "Internal cable signal", menu.primary() + " / 15", 105);
                labelValue(graphics, "External vanilla input", menu.secondary() + " / 15", 121);
                labelValue(graphics, "Vanilla face", directionName(menu.facingOrdinal()), 137);
                labelValue(graphics, "Cable face", oppositeDirectionName(menu.facingOrdinal()), 153);
                signalBar(graphics, menu.primary(), 170);
            }
            default -> renderCableOverview(graphics);
        }
    }

    private void renderCableOverview(GuiGraphics graphics) {
        statusBadge(graphics, menu.topologyValid() ? "TOPOLOGY VALID" : "TOPOLOGY ERROR", menu.topologyValid() ? GOOD : BAD, 16, 80);
        labelValue(graphics, "Connections", menu.connectionCount() + " / 6", 105);
        labelValue(graphics, "Engineering ports", Integer.toString(menu.portCount()), 121);
        labelValue(graphics, "Connected faces", connectedFaces(), 137);
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_CABLE || menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            labelValue(graphics, "Signal", menu.primary() + " / 15", 153);
            signalBar(graphics, menu.primary(), 170);
        } else {
            statusLine(graphics, "Medium", "INSTRUMENT BUS • measurement channels", INFO, 157);
        }
    }

    private void renderPorts(GuiGraphics graphics) {
        labelValue(graphics, "Engineering ports", Integer.toString(menu.portCount()), 82);
        if (isCable()) {
            labelValue(graphics, "Connected faces", connectedFaces(), 102);
            statusLine(graphics, "Topology", menu.topologyValid() ? "VALID" : "INVALID", menu.topologyValid() ? GOOD : BAD, 122);
            graphics.drawString(font, "Cable ports are physical graph edges; absent faces are not virtual ports.", 16, 146, MUTED, false);
            graphics.drawString(font, "Junctions intentionally allow branching; plain signal cable remains two-ended.", 16, 164, INFO, false);
        } else {
            switch (menu.kind()) {
                case FieldDeviceMenu.KIND_PROBE -> {
                    statusLine(graphics, directionName(menu.facingOrdinal()), "TEST • REDSTONE MEASUREMENT INPUT", GOOD, 105);
                    statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), "INSTRUMENT BUS OUTPUT", INFO, 125);
                }
                case FieldDeviceMenu.KIND_FILTER -> {
                    statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), "BACK • REDSTONE INPUT", GOOD, 105);
                    statusLine(graphics, directionName(menu.facingOrdinal()), "FRONT • REDSTONE OUTPUT", GOOD, 125);
                }
                case FieldDeviceMenu.KIND_REFERENCE -> statusLine(graphics, directionName(menu.facingOrdinal()), "REFERENCE OUT • REDSTONE 0..15", GOOD, 105);
                case FieldDeviceMenu.KIND_TERMINAL -> {
                    statusLine(graphics, directionName(menu.facingOrdinal()), menu.tertiary() == 1 ? "VANILLA OUT" : "VANILLA IN", GOOD, 105);
                    statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), menu.tertiary() == 1 ? "CABLE IN" : "CABLE OUT", INFO, 125);
                }
                default -> { }
            }
        }
    }

    private void renderConfigure(GuiGraphics graphics) {
        switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> {
                labelValue(graphics, "Selected channel", SignalProbeBlock.channelName(menu.secondary()), 80);
                graphics.drawString(font, "Changing channel changes only the instrument-bus label, never the measured node.", 16, 163, MUTED, false);
            }
            case FieldDeviceMenu.KIND_FILTER -> {
                labelValue(graphics, "Slew rate", menu.tertiary() + " step/tick", 80);
                graphics.drawString(font, "Rate is bounded 1..4; output still converges to the authoritative BACK input.", 16, 163, MUTED, false);
            }
            case FieldDeviceMenu.KIND_REFERENCE -> {
                labelValue(graphics, "Reference output", menu.primary() + " / 15", 80);
                graphics.drawString(font, "Use ±1 adjustment or the four lab presets for repeatable tests.", 16, 163, MUTED, false);
            }
            case FieldDeviceMenu.KIND_TERMINAL -> {
                labelValue(graphics, "Boundary direction", menu.tertiary() == 1 ? "CABLE → VANILLA" : "VANILLA → CABLE", 80);
                graphics.drawString(font, "Toggle reverses the explicit conversion boundary and recomputes the cable network.", 16, 163, MUTED, false);
            }
            default -> {
                statusLine(graphics, "Configuration", "READ-ONLY TOPOLOGY DEVICE", MUTED, 82);
                graphics.drawString(font, "Cable shape is derived from actual neighboring engineering ports.", 16, 163, MUTED, false);
            }
        }
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Device", deviceName(), INFO, 82);
        labelValue(graphics, "Port count", Integer.toString(menu.portCount()), 102);
        if (isCable()) {
            labelValue(graphics, "Connection count", Integer.toString(menu.connectionCount()), 122);
            statusLine(graphics, "Topology", menu.topologyValid() ? "PASS" : "FAIL", menu.topologyValid() ? GOOD : BAD, 142);
            labelValue(graphics, "Faces", connectedFaces(), 162);
        } else {
            statusLine(graphics, "Server snapshot", "VALID • synchronized", GOOD, 122);
            labelValue(graphics, "Orientation", directionName(menu.facingOrdinal()), 142);
            graphics.drawString(font, "Runtime values stay out of high-cardinality BlockState where practical.", 16, 166, MUTED, false);
        }
    }

    private void renderHistory(GuiGraphics graphics) {
        graphics.drawString(font, "This field device does not retain a local time-series history.", 16, 84, TEXT, false);
        graphics.drawString(font, "Use Signal Analyzer, Oscilloscope, or Logic Analyzer for historical evidence.", 16, 103, INFO, false);
        sectionRule(graphics, 126);
        graphics.drawString(font, "The inspector intentionally stays lightweight: inspect, configure, verify topology.", 16, 140, MUTED, false);
    }

    private boolean isCable() {
        return menu.kind() == FieldDeviceMenu.KIND_REDSTONE_CABLE
                || menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION
                || menu.kind() == FieldDeviceMenu.KIND_INSTRUMENT_CABLE;
    }

    private String deviceName() {
        return switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> "SIGNAL PROBE";
            case FieldDeviceMenu.KIND_FILTER -> "PRECISION FILTER";
            case FieldDeviceMenu.KIND_REFERENCE -> "REFERENCE SOURCE";
            case FieldDeviceMenu.KIND_TERMINAL -> "CABLE TERMINAL";
            case FieldDeviceMenu.KIND_REDSTONE_CABLE -> "INSULATED REDSTONE CABLE";
            case FieldDeviceMenu.KIND_REDSTONE_JUNCTION -> "REDSTONE CABLE JUNCTION";
            case FieldDeviceMenu.KIND_INSTRUMENT_CABLE -> "INSTRUMENT BUS CABLE";
            default -> "UNKNOWN";
        };
    }

    private String connectedFaces() {
        StringBuilder text = new StringBuilder();
        for (Direction direction : Direction.values()) {
            if ((menu.connectionMask() & (1 << direction.ordinal())) == 0) continue;
            if (text.isEmpty()) text.append(direction.getName().toUpperCase());
            else text.append(" · ").append(direction.getName().toUpperCase());
        }
        return text.isEmpty() ? "NONE" : text.toString();
    }

    private static String directionName(int ordinal) {
        if (ordinal < 0 || ordinal >= Direction.values().length) return "N/A";
        return Direction.values()[ordinal].getName().toUpperCase();
    }

    private static String oppositeDirectionName(int ordinal) {
        if (ordinal < 0 || ordinal >= Direction.values().length) return "N/A";
        return Direction.values()[ordinal].getOpposite().getName().toUpperCase();
    }
}
