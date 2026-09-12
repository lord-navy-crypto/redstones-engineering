package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated optical HMI preserving guided/free-space source, terminal, observer and processor topology. */
public final class OpticalSystemScreen extends EngineeringScreen<OpticalSystemMenu> {
    private Button primaryPrevious;
    private Button primaryNext;
    private Button secondaryPrevious;
    private Button secondaryNext;

    public OpticalSystemScreen(OpticalSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 111;
        primaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Primary"),
                b -> sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftPos + 16, y, 105, 20).build());
        primaryNext = addConfigureWidget(Button.builder(Component.literal("Primary ▶"),
                b -> sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(leftPos + 199, y, 105, 20).build());
        secondaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Secondary"),
                b -> sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_PREVIOUS)).bounds(leftPos + 16, y + 26, 105, 20).build());
        secondaryNext = addConfigureWidget(Button.builder(Component.literal("Secondary ▶"),
                b -> sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_NEXT)).bounds(leftPos + 199, y + 26, 105, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (primaryPrevious == null) return;
        boolean emitter = menu.kind() == OpticalSystemMenu.KIND_EMITTER;
        boolean filter = menu.kind() == OpticalSystemMenu.KIND_FILTER;
        boolean attenuator = menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR;
        boolean freeTx = menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_TX;
        boolean freeRx = menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_RX;
        boolean primary = emitter || filter || attenuator;
        boolean secondary = emitter || freeTx || freeRx;
        boolean configure = isConfigureSection();

        primaryPrevious.active = primary;
        primaryNext.active = primary;
        primaryPrevious.visible = configure && primary;
        primaryNext.visible = configure && primary;
        secondaryPrevious.active = secondary;
        secondaryNext.active = secondary;
        secondaryPrevious.visible = configure && secondary;
        secondaryNext.visible = configure && secondary;

        if (emitter) {
            primaryPrevious.setMessage(Component.literal("◀ I " + menu.primary()));
            primaryNext.setMessage(Component.literal("I " + menu.primary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("◀ CH " + menu.secondary()));
            secondaryNext.setMessage(Component.literal("CH " + menu.secondary() + " ▶"));
        } else if (filter) {
            primaryPrevious.setMessage(Component.literal("◀ CH " + menu.secondary()));
            primaryNext.setMessage(Component.literal("CH " + menu.secondary() + " ▶"));
        } else if (attenuator) {
            primaryPrevious.setMessage(Component.literal("◀ LOSS " + menu.secondary()));
            primaryNext.setMessage(Component.literal("LOSS " + menu.secondary() + " ▶"));
        } else if (freeTx || freeRx) {
            secondaryPrevious.setMessage(Component.literal("◀ CH " + menu.secondary()));
            secondaryNext.setMessage(Component.literal("CH " + menu.secondary() + " ▶"));
            String role = freeTx ? "free-space transmitter" : "free-space receiver";
            secondaryPrevious.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Select the " + role + " channel on the server.")));
            secondaryNext.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Select the " + role + " channel on the server.")));
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
        statusBadge(g, qualityName(), qualityColor(), 205, 80);
        metricCard(g, primaryLabel(), primaryText(), 16, 103, 88, INFO);
        metricCard(g, secondaryLabel(), secondaryText(), 111, 103, 88, GOOD);
        metricCard(g, tertiaryLabel(), tertiaryText(), 206, 103, 88, INFO);
        labelValue(g, "Topology", topologyText(), 149);
        labelValue(g, "Role", roleText(), 165);
        labelValue(g, "Evidence", qualityName(), 181);
        safeText(g, hint(), 16, 199, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "OPTICAL INTERFACES", GOOD, 16, 80);
        switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> {
                statusLine(g, "ALL 6 FACES", "OUTPUT • OPTICAL SOURCE", GOOD, 112);
                safeText(g, "Configured zero intensity remains a valid source setting; downstream fiber may be DARK.", 16, 150, MUTED);
            }
            case OpticalSystemMenu.KIND_RECEIVER -> {
                statusLine(g, "ALL 6 FACES", "INPUT • OPTICAL TERMINAL SINK", qualityColor(), 112);
                labelValue(g, "Physical inputs / drivers", menu.tertiary() + " / " + menu.auxiliary(), 142);
            }
            case OpticalSystemMenu.KIND_METER -> {
                statusLine(g, face(menu.facing()), "INPUT • OPTICAL MEASUREMENT", qualityColor(), 112);
                statusLine(g, "AUTHORITY", "OBSERVER ONLY • NO BACKDRIVE", INFO, 142);
            }
            case OpticalSystemMenu.KIND_SPLITTER -> {
                statusLine(g, face(inputFace()), "INPUT • OPTICAL", qualityColor(), 108);
                statusLine(g, face(menu.facing()), "OUTPUT A • OPTICAL", GOOD, 136);
                statusLine(g, face(leftOf(menu.facing())), "OUTPUT B • OPTICAL", GOOD, 164);
            }
            case OpticalSystemMenu.KIND_FREE_SPACE_TX -> {
                statusLine(g, face(inputFace()), "INPUT • REDSTONE PAYLOAD", qualityColor(), 112);
                statusLine(g, face(menu.facing()), "OUTPUT • FREE-SPACE OPTICAL • CH " + menu.secondary(), INFO, 142);
                safeText(g, "Line-of-sight optical launch uses the real FRONT route; channel belongs on Configure.", 16, 176, MUTED);
            }
            case OpticalSystemMenu.KIND_FREE_SPACE_RX -> {
                statusLine(g, face(inputFace()), "INPUT • FREE-SPACE OPTICAL • CH " + menu.secondary(), qualityColor(), 112);
                statusLine(g, face(menu.facing()), "OUTPUT • REDSTONE 0..15", INFO, 142);
                safeText(g, "Reception stays observational until converted onto the real FRONT redstone output.", 16, 176, MUTED);
            }
            default -> {
                statusLine(g, face(inputFace()), "INPUT • OPTICAL", qualityColor(), 112);
                statusLine(g, face(menu.facing()), "OUTPUT • OPTICAL", GOOD, 142);
                safeText(g, "Series Direction preserves INPUT → PROCESS → OUTPUT semantics.", 16, 176, MUTED);
            }
        }
    }

    private void configure(GuiGraphics g) {
        boolean configurable = menu.kind() == OpticalSystemMenu.KIND_EMITTER
                || menu.kind() == OpticalSystemMenu.KIND_FILTER
                || menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR
                || menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_TX
                || menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_RX;
        statusBadge(g, configurable ? "SERVER-SIDE BOUNDED CONTROL" : "READ-ONLY DEVICE", configurable ? INFO : MUTED, 16, 80);
        labelValue(g, "Primary control", primaryControlText(), 98);
        labelValue(g, "Secondary control", secondaryControlText(), 181);
        if (menu.directional()) {
            safeText(g, "Direction and physical interface orientation are controlled only on Route.", 16, 207, MUTED);
        } else if (menu.kind() == OpticalSystemMenu.KIND_METER) {
            safeText(g, "Measurement face is controlled only on Route.", 16, 207, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        labelValue(g, primaryLabel(), primaryText(), 106);
        labelValue(g, secondaryLabel(), secondaryText(), 124);
        labelValue(g, tertiaryLabel(), tertiaryText(), 142);
        if (menu.kind() == OpticalSystemMenu.KIND_RECEIVER) {
            labelValue(g, "Physical inputs", Integer.toString(menu.tertiary()), 160);
            labelValue(g, "Active drivers", Integer.toString(menu.auxiliary()), 178);
        } else if (menu.kind() == OpticalSystemMenu.KIND_SPLITTER) {
            labelValue(g, "Quantization loss", Integer.toString(menu.auxiliary()), 160);
        } else if (menu.kind() == OpticalSystemMenu.KIND_FILTER) {
            labelValue(g, "Input channel", Integer.toString(menu.auxiliary()), 160);
        } else if (menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR) {
            labelValue(g, "Carrier channel", Integer.toString(menu.auxiliary()), 160);
        } else if (menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_TX) {
            labelValue(g, "Input evidence", menu.tertiary() != 0 ? "VALID" : "UNAVAILABLE", 160);
            labelValue(g, "Launch direction", face(menu.facing()), 178);
        } else if (menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_RX) {
            labelValue(g, "Redstone output", menu.tertiary() + " / 15", 160);
            labelValue(g, "Receive direction", face(inputFace()), 178);
        }
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "OPTICAL EVIDENCE", INFO, 16, 80);
        safeText(g, "This HMI exposes retained/current server optical evidence only.", 16, 108, TEXT);
        safeText(g, "It does not invent client-side carrier history or optical power traces.", 16, 128, MUTED);
        if (menu.kind() == OpticalSystemMenu.KIND_RECEIVER) {
            labelValue(g, "Inputs / drivers", menu.tertiary() + " / " + menu.auxiliary(), 156);
            labelValue(g, "Topology state", qualityName(), 174);
        } else if (menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_TX || menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_RX) {
            labelValue(g, "Selected channel", Integer.toString(menu.secondary()), 156);
            labelValue(g, "Current evidence", qualityName(), 174);
        }
    }

    private String deviceName() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "OPTICAL EMITTER";
            case OpticalSystemMenu.KIND_RECEIVER -> "OPTICAL RECEIVER";
            case OpticalSystemMenu.KIND_METER -> "OPTICAL POWER METER";
            case OpticalSystemMenu.KIND_SPLITTER -> "OPTICAL 1×2 SPLITTER";
            case OpticalSystemMenu.KIND_FILTER -> "OPTICAL CHANNEL FILTER";
            case OpticalSystemMenu.KIND_ATTENUATOR -> "OPTICAL ATTENUATOR";
            case OpticalSystemMenu.KIND_FREE_SPACE_TX -> "FREE-SPACE OPTICAL TX";
            case OpticalSystemMenu.KIND_FREE_SPACE_RX -> "FREE-SPACE OPTICAL RX";
            default -> "OPTICAL DEVICE";
        };
    }

    private String roleText() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "SOURCE";
            case OpticalSystemMenu.KIND_RECEIVER -> "TERMINAL SINK";
            case OpticalSystemMenu.KIND_METER -> "OBSERVER";
            case OpticalSystemMenu.KIND_SPLITTER -> "1→2 ROUTER";
            case OpticalSystemMenu.KIND_FREE_SPACE_TX, OpticalSystemMenu.KIND_FREE_SPACE_RX -> "CONVERTER";
            default -> "SERIES PROCESSOR";
        };
    }

    private String topologyText() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "6× OUTPUT";
            case OpticalSystemMenu.KIND_RECEIVER -> "6× INPUT TERMINAL";
            case OpticalSystemMenu.KIND_METER -> "1× MEASUREMENT INPUT • " + face(menu.facing());
            case OpticalSystemMenu.KIND_SPLITTER -> face(inputFace()) + " → " + face(menu.facing()) + " + " + face(leftOf(menu.facing()));
            case OpticalSystemMenu.KIND_FREE_SPACE_TX -> face(inputFace()) + " REDSTONE → " + face(menu.facing()) + " FREE-SPACE";
            case OpticalSystemMenu.KIND_FREE_SPACE_RX -> face(inputFace()) + " FREE-SPACE → " + face(menu.facing()) + " REDSTONE";
            default -> face(inputFace()) + " → " + face(menu.facing());
        };
    }

    private String primaryLabel() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "Intensity";
            case OpticalSystemMenu.KIND_SPLITTER -> "Input";
            case OpticalSystemMenu.KIND_FREE_SPACE_TX -> "Payload in";
            case OpticalSystemMenu.KIND_FREE_SPACE_RX -> "Optical in";
            default -> "Intensity in";
        };
    }

    private String secondaryLabel() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER, OpticalSystemMenu.KIND_RECEIVER, OpticalSystemMenu.KIND_METER,
                 OpticalSystemMenu.KIND_FREE_SPACE_TX, OpticalSystemMenu.KIND_FREE_SPACE_RX -> "Channel";
            case OpticalSystemMenu.KIND_SPLITTER -> "Branch A";
            case OpticalSystemMenu.KIND_FILTER -> "Target CH";
            case OpticalSystemMenu.KIND_ATTENUATOR -> "Loss";
            default -> "Secondary";
        };
    }

    private String tertiaryLabel() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_RECEIVER -> "Inputs";
            case OpticalSystemMenu.KIND_SPLITTER -> "Branch B";
            case OpticalSystemMenu.KIND_FILTER, OpticalSystemMenu.KIND_ATTENUATOR -> "Expected out";
            case OpticalSystemMenu.KIND_FREE_SPACE_TX -> "Input valid";
            case OpticalSystemMenu.KIND_FREE_SPACE_RX -> "Redstone out";
            default -> "State";
        };
    }

    private String primaryText() { return menu.primary() + " / 15"; }
    private String secondaryText() { return Integer.toString(menu.secondary()); }
    private String tertiaryText() {
        if (menu.kind() == OpticalSystemMenu.KIND_FILTER || menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR
                || menu.kind() == OpticalSystemMenu.KIND_SPLITTER || menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_RX) {
            return menu.tertiary() + " / 15";
        }
        if (menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_TX) return menu.tertiary() != 0 ? "YES" : "NO";
        return Integer.toString(menu.tertiary());
    }

    private String primaryControlText() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "INTENSITY " + menu.primary() + "/15";
            case OpticalSystemMenu.KIND_FILTER -> "TARGET CHANNEL " + menu.secondary();
            case OpticalSystemMenu.KIND_ATTENUATOR -> "LOSS " + menu.secondary();
            default -> "READ ONLY";
        };
    }

    private String secondaryControlText() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER, OpticalSystemMenu.KIND_FREE_SPACE_TX, OpticalSystemMenu.KIND_FREE_SPACE_RX ->
                    "CHANNEL " + menu.secondary();
            default -> "NONE";
        };
    }

    private String hint() {
        if (menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR && menu.primary() > 0 && menu.tertiary() == 0 && menu.quality() == PortQuality.VALID)
            return "Full attenuation is a valid transfer result, not a fault.";
        if (menu.kind() == OpticalSystemMenu.KIND_METER) return "Observer-only meter measures one selected face without driving the network.";
        if (menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_TX) return "Redstone payload becomes a channel-selected line-of-sight optical transmission.";
        if (menu.kind() == OpticalSystemMenu.KIND_FREE_SPACE_RX) return "Channel-selected free-space evidence becomes redstone only on the declared output face.";
        return "Optical role and topology are explicit; passive fiber remains a separate no-translation medium.";
    }

    private String qualityName() { return menu.quality().name().replace('_', ' '); }
    private int qualityColor() { return menu.quality() == PortQuality.VALID ? GOOD : menu.quality() == PortQuality.NO_SIGNAL || menu.quality() == PortQuality.STALE ? WARN : BAD; }
    private Direction inputFace() { return menu.facing().getOpposite(); }
    private String face(Direction d) { return d.getName().toUpperCase(); }
    private Direction leftOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }
}
