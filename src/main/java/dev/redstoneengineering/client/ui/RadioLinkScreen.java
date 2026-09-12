package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated radio HMI for channel control, wireless evidence, and receiver diagnostics. */
public final class RadioLinkScreen extends EngineeringScreen<RadioLinkMenu> {
    private Button channelPrevious;
    private Button channelNext;
    private Button directionCycle;

    public RadioLinkScreen(RadioLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 116;
        channelPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Channel"),
                b -> sendMenuButton(RadioLinkMenu.BUTTON_CHANNEL_PREVIOUS))
                .bounds(leftPos + 16, y, 105, 20).build());
        channelNext = addConfigureWidget(Button.builder(Component.literal("Channel ▶"),
                b -> sendMenuButton(RadioLinkMenu.BUTTON_CHANNEL_NEXT))
                .bounds(leftPos + 199, y, 105, 20).build());
        directionCycle = addConfigureWidget(Button.builder(Component.literal("Direction • —"),
                b -> sendMenuButton(RadioLinkMenu.BUTTON_OUTPUT_RIGHT))
                .bounds(leftPos + 70, y + 30, 180, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (channelPrevious == null) return;
        channelPrevious.setMessage(Component.literal("◀ CH " + menu.channel()));
        channelNext.setMessage(Component.literal("CH " + menu.channel() + " ▶"));
        boolean receiver = menu.kind() == RadioLinkMenu.KIND_RECEIVER;
        if (directionCycle != null) {
            directionCycle.active = receiver;
            directionCycle.visible = isConfigureSection() && receiver;
            directionCycle.setMessage(Component.literal("Direction • " + menu.outputDirection().getName().toUpperCase()));
            directionCycle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Cycle the receiver wired output face clockwise; the antenna remains UP.")));
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
        statusBadge(g, menu.kind() == RadioLinkMenu.KIND_TRANSMITTER ? "RADIO TRANSMITTER" : "RADIO RECEIVER", GOOD, 16, 80);
        statusBadge(g, qualityName(), qualityColor(), 200, 80);
        metricCard(g, "Payload", menu.payload() + " / 15", 16, 103, 88, INFO);
        metricCard(g, "Channel", Integer.toString(menu.channel()), 111, 103, 88, GOOD);
        metricCard(g, menu.kind() == RadioLinkMenu.KIND_RECEIVER ? "Drivers" : "Range",
                menu.kind() == RadioLinkMenu.KIND_RECEIVER ? Integer.toString(menu.drivers()) : "RADIO", 206, 103, 88, INFO);
        if (menu.kind() == RadioLinkMenu.KIND_RECEIVER) {
            labelValue(g, "Latency", menu.latency() + " ticks", 149);
            labelValue(g, "Coverage", menu.coverageComplete() ? "COMPLETE" : "INCOMPLETE / STALE", 165);
            labelValue(g, "Output", menu.output() + " / 15 • " + menu.outputDirection().getName().toUpperCase(), 181);
        } else {
            labelValue(g, "Input topology", "5× REDSTONE INPUT", 149);
            labelValue(g, "Wireless interface", "UP • RADIO ANTENNA", 165);
            labelValue(g, "Frame evidence", menu.quality() == PortQuality.VALID ? "VALID" : qualityName(), 181);
        }
        safeText(g, menu.payload() == 0 && menu.quality() == PortQuality.VALID
                ? "Payload 0 is a valid frame when source evidence is VALID."
                : "Radio validity comes from evidence quality, never payload > 0.", 16, 199, GOOD);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "RADIO INTERFACES", GOOD, 16, 80);
        if (menu.kind() == RadioLinkMenu.KIND_TRANSMITTER) {
            statusLine(g, "DOWN / N / S / E / W", "INPUT • REDSTONE PAYLOAD", GOOD, 112);
            statusLine(g, "UP", "OUTPUT • FREE-SPACE RADIO ANTENNA", INFO, 142);
            safeText(g, "Transmitter is intentionally multi-input; it is not forced into a fake series axis.", 16, 176, MUTED);
        } else {
            statusLine(g, "UP", "INPUT • FREE-SPACE RADIO ANTENNA", INFO, 112);
            statusLine(g, menu.outputDirection().getName().toUpperCase(), "OUTPUT • REDSTONE 0..15", GOOD, 142);
            safeText(g, "Only the wired output face rotates; the antenna remains the physical UP interface.", 16, 176, MUTED);
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        labelValue(g, "Channel", Integer.toString(menu.channel()), 101);
        if (menu.kind() == RadioLinkMenu.KIND_RECEIVER) {
            labelValue(g, "Output face", menu.outputDirection().getName().toUpperCase(), 171);
        } else {
            labelValue(g, "Antenna", "UP • FIXED", 171);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        labelValue(g, "Payload", menu.payload() + " / 15", 106);
        labelValue(g, "Drivers", Integer.toString(menu.drivers()), 124);
        if (menu.kind() == RadioLinkMenu.KIND_RECEIVER) {
            labelValue(g, "Latency / noise", menu.latency() + "t / " + menu.noise() + "%", 142);
            labelValue(g, "Coverage", menu.coverageComplete() ? "COMPLETE" : "TRUNCATED / STALE", 160);
            labelValue(g, "Collision", menu.collision() ? "YES" : "NO", 178);
        } else {
            labelValue(g, "Channel", Integer.toString(menu.channel()), 142);
            labelValue(g, "Source evidence", qualityName(), 160);
        }
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "RADIO LINK COUNTERS", INFO, 16, 80);
        if (menu.kind() == RadioLinkMenu.KIND_RECEIVER) {
            labelValue(g, "Samples / valid", menu.samples() + " / " + menu.validSamples(), 106);
            labelValue(g, "Undecodable", Integer.toString(menu.undecodableSamples()), 124);
            labelValue(g, "Collisions", Integer.toString(menu.collisions()), 142);
            labelValue(g, "Dropouts", Integer.toString(menu.dropouts()), 160);
            labelValue(g, "Channel handoffs", Integer.toString(menu.handoffs()), 178);
        } else {
            safeText(g, "Transmitter currently exposes live frame evidence; it does not invent client-side history.", 16, 112, MUTED);
            safeText(g, "Receiver counters provide the authoritative link chronology for a radio path.", 16, 134, INFO);
        }
    }

    private String qualityName() { return menu.quality().name().replace('_', ' '); }
    private int qualityColor() {
        return switch (menu.quality()) {
            case VALID -> GOOD;
            case NO_SIGNAL, STALE -> WARN;
            default -> BAD;
        };
    }
}
