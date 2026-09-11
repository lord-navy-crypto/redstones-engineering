package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated optical HMI preserving source, terminal, observer, splitter and series-processor topology. */
public final class OpticalSystemScreen extends EngineeringScreen<OpticalSystemMenu> {
    private Button primaryPrevious;
    private Button primaryNext;
    private Button secondaryPrevious;
    private Button secondaryNext;
    private Button rotateLeft;
    private Button rotateRight;

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
        rotateLeft = addConfigureWidget(Button.builder(Component.literal("↺ Interface"),
                b -> sendMenuButton(OpticalSystemMenu.BUTTON_ROTATE_LEFT)).bounds(leftPos + 70, y + 52, 80, 20).build());
        rotateRight = addConfigureWidget(Button.builder(Component.literal("Interface ↻"),
                b -> sendMenuButton(OpticalSystemMenu.BUTTON_ROTATE_RIGHT)).bounds(leftPos + 170, y + 52, 80, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (primaryPrevious == null) return;
        boolean emitter = menu.kind() == OpticalSystemMenu.KIND_EMITTER;
        boolean filter = menu.kind() == OpticalSystemMenu.KIND_FILTER;
        boolean attenuator = menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR;
        boolean meter = menu.kind() == OpticalSystemMenu.KIND_METER;
        boolean rotatable = menu.directional() || meter;
        primaryPrevious.active = emitter || filter || attenuator;
        primaryNext.active = emitter || filter || attenuator;
        secondaryPrevious.active = emitter;
        secondaryNext.active = emitter;
        rotateLeft.active = rotatable;
        rotateRight.active = rotatable;

        if (emitter) {
            primaryPrevious.setMessage(Component.literal("◀ I " + menu.primary()));
            primaryNext.setMessage(Component.literal("I " + menu.primary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("◀ CH " + menu.secondary()));
            secondaryNext.setMessage(Component.literal("CH " + menu.secondary() + " ▶"));
        } else if (filter) {
            primaryPrevious.setMessage(Component.literal("◀ CH " + menu.secondary()));
            primaryNext.setMessage(Component.literal("CH " + menu.secondary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("Fixed"));
            secondaryNext.setMessage(Component.literal("Fixed"));
        } else if (attenuator) {
            primaryPrevious.setMessage(Component.literal("◀ LOSS " + menu.secondary()));
            primaryNext.setMessage(Component.literal("LOSS " + menu.secondary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("Fixed"));
            secondaryNext.setMessage(Component.literal("Fixed"));
        } else {
            primaryPrevious.setMessage(Component.literal("Read only"));
            primaryNext.setMessage(Component.literal("Read only"));
            secondaryPrevious.setMessage(Component.literal("Read only"));
            secondaryNext.setMessage(Component.literal("Read only"));
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
        g.drawString(font, hint(), 16, 199, MUTED, false);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "OPTICAL INTERFACES", GOOD, 16, 80);
        switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> {
                statusLine(g, "ALL 6 FACES", "OUTPUT • OPTICAL SOURCE", GOOD, 112);
                g.drawString(font, "Configured zero intensity remains a valid source setting; downstream fiber may be DARK.", 16, 150, MUTED, false);
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
            default -> {
                statusLine(g, face(inputFace()), "INPUT • OPTICAL", qualityColor(), 112);
                statusLine(g, face(menu.facing()), "OUTPUT • OPTICAL", GOOD, 142);
                g.drawString(font, "Series axis rotation preserves INPUT → PROCESS → OUTPUT semantics.", 16, 176, MUTED, false);
            }
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        labelValue(g, "Primary control", primaryControlText(), 98);
        labelValue(g, "Secondary control", secondaryControlText(), 181);
        if (menu.directional()) labelValue(g, "Series axis", face(inputFace()) + " → " + face(menu.facing()), 197);
        else if (menu.kind() == OpticalSystemMenu.KIND_METER) labelValue(g, "Measurement face", face(menu.facing()), 197);
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
        }
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "OPTICAL EVIDENCE", INFO, 16, 80);
        g.drawString(font, "This HMI exposes retained/current server optical evidence only.", 16, 108, TEXT, false);
        g.drawString(font, "It does not invent client-side carrier history or optical power traces.", 16, 128, MUTED, false);
        if (menu.kind() == OpticalSystemMenu.KIND_RECEIVER) {
            labelValue(g, "Inputs / drivers", menu.tertiary() + " / " + menu.auxiliary(), 156);
            labelValue(g, "Topology state", qualityName(), 174);
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
            default -> "OPTICAL DEVICE";
        };
    }
    private String roleText() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "SOURCE";
            case OpticalSystemMenu.KIND_RECEIVER -> "TERMINAL SINK";
            case OpticalSystemMenu.KIND_METER -> "OBSERVER";
            case OpticalSystemMenu.KIND_SPLITTER -> "1→2 ROUTER";
            default -> "SERIES PROCESSOR";
        };
    }
    private String topologyText() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "6× OUTPUT";
            case OpticalSystemMenu.KIND_RECEIVER -> "6× INPUT TERMINAL";
            case OpticalSystemMenu.KIND_METER -> "1× MEASUREMENT INPUT • " + face(menu.facing());
            case OpticalSystemMenu.KIND_SPLITTER -> face(inputFace()) + " → " + face(menu.facing()) + " + " + face(leftOf(menu.facing()));
            default -> face(inputFace()) + " → " + face(menu.facing());
        };
    }
    private String primaryLabel() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER -> "Intensity";
            case OpticalSystemMenu.KIND_SPLITTER -> "Input";
            default -> "Intensity in";
        };
    }
    private String secondaryLabel() {
        return switch (menu.kind()) {
            case OpticalSystemMenu.KIND_EMITTER, OpticalSystemMenu.KIND_RECEIVER, OpticalSystemMenu.KIND_METER -> "Channel";
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
            default -> "State";
        };
    }
    private String primaryText() { return menu.primary() + " / 15"; }
    private String secondaryText() {
        if (menu.kind() == OpticalSystemMenu.KIND_SPLITTER) return menu.secondary() + " / 15";
        if (menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR) return Integer.toString(menu.secondary());
        return Integer.toString(menu.secondary());
    }
    private String tertiaryText() {
        if (menu.kind() == OpticalSystemMenu.KIND_FILTER || menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR
                || menu.kind() == OpticalSystemMenu.KIND_SPLITTER) return menu.tertiary() + " / 15";
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
        return menu.kind() == OpticalSystemMenu.KIND_EMITTER ? "CHANNEL " + menu.secondary() : "NONE";
    }
    private String hint() {
        if (menu.kind() == OpticalSystemMenu.KIND_ATTENUATOR && menu.primary() > 0 && menu.tertiary() == 0 && menu.quality() == PortQuality.VALID)
            return "Full attenuation is a valid transfer result, not a fault.";
        if (menu.kind() == OpticalSystemMenu.KIND_METER) return "Observer-only meter measures one selected face without driving the network.";
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
