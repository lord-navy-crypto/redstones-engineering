package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RadioKernel;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated radio HMI for channel control, wireless evidence, and receiver diagnostics. */
public final class RadioLinkScreen extends EngineeringScreen<RadioLinkMenu> {
    private Button channelPrevious;
    private Button channelNext;

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
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (channelPrevious == null) return;
        channelPrevious.setMessage(Component.literal("◀ CH " + menu.channel()));
        channelNext.setMessage(Component.literal("CH " + menu.channel() + " ▶"));
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
        statusBadge(g, menu.kind() == RadioLinkMenu.KIND_RECEIVER ? receiverDiagnosis() : qualityName(), diagnosisColor(), 188, 80);
        metricCard(g, "Payload", menu.payload() + " / 15", 16, 103, 88, INFO);
        metricCard(g, "Channel", Integer.toString(menu.channel()), 111, 103, 88, GOOD);
        metricCard(g, menu.kind() == RadioLinkMenu.KIND_RECEIVER ? "Link Q" : "Range",
                menu.kind() == RadioLinkMenu.KIND_RECEIVER ? menu.linkQuality() + "%" : RadioKernel.RANGE + " blk", 206, 103, 88, INFO);
        if (menu.kind() == RadioLinkMenu.KIND_RECEIVER) {
            labelValue(g, "Distance / latency", menu.distanceBlocks() + " blk / " + menu.latency() + "t", 149);
            labelValue(g, "Decode margin", signed(menu.decodeMargin()) + "% above " + RadioKernel.MIN_DECODE_QUALITY + "% threshold", 165);
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
            safeText(g, "Wired output direction is controlled only on Route; antenna remains UP.", 16, 199, MUTED);
        } else {
            labelValue(g, "Antenna", "UP • FIXED", 171);
        }
    }

    private void diagnostics(GuiGraphics g) {
        if (menu.kind() != RadioLinkMenu.KIND_RECEIVER) {
            statusBadge(g, qualityName(), qualityColor(), 16, 80);
            labelValue(g, "Payload", menu.payload() + " / 15", 106);
            labelValue(g, "Channel", Integer.toString(menu.channel()), 124);
            labelValue(g, "Source evidence", qualityName(), 142);
            statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 172);
            return;
        }

        statusBadge(g, receiverDiagnosis(), diagnosisColor(), 16, 80);
        labelValue(g, "Quality / margin", menu.linkQuality() + "% / " + signed(menu.decodeMargin()) + "%", 106);
        labelValue(g, "Distance / latency", menu.distanceBlocks() + "/" + RadioKernel.RANGE + " blk • " + menu.latency() + "t", 124);
        labelValue(g, "Obstacles / adjacent", menu.obstacleHits() + " / " + menu.adjacentAggressors(), 142);
        labelValue(g, "Drivers / collision", menu.drivers() + " / " + (menu.collision() ? "YES" : "NO"), 160);
        labelValue(g, "Coverage", menu.coverageComplete() ? "COMPLETE" : "TRUNCATED / STALE", 178);
        safeText(g, nextAction(), 16, 201, diagnosisColor());
        safeText(g, "Distance, obstacles, adjacent-channel penalty and fading come from the authoritative RadioKernel.", 16, 221, MUTED);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "RADIO LINK COUNTERS", INFO, 16, 80);
        if (menu.kind() == RadioLinkMenu.KIND_RECEIVER) {
            labelValue(g, "Samples / valid", menu.samples() + " / " + menu.validSamples() + " • " + menu.availabilityPercent() + "%", 106);
            labelValue(g, "Undecodable", Integer.toString(menu.undecodableSamples()), 124);
            labelValue(g, "Collisions / dropouts", menu.collisions() + " / " + menu.dropouts(), 142);
            labelValue(g, "Channel handoffs", Integer.toString(menu.handoffs()), 160);
            labelValue(g, "Current noise proxy", menu.noise() + "%", 178);
            safeText(g, "Counters are receiver-tick evidence; the client does not fabricate packet history.", 16, 203, MUTED);
        } else {
            safeText(g, "Transmitter currently exposes live frame evidence; it does not invent client-side history.", 16, 112, MUTED);
            safeText(g, "Receiver counters provide the authoritative link chronology for a radio path.", 16, 134, INFO);
        }
    }

    private String receiverDiagnosis() {
        if (!menu.coverageComplete()) return "STALE COVERAGE";
        if (menu.collision() || menu.drivers() > 1) return "SAME-CHANNEL COLLISION";
        if (menu.drivers() == 0) return menu.adjacentAggressors() > 0 ? "NO SOURCE • ADJACENT RF" : "NO IN-RANGE SOURCE";
        if (menu.linkQuality() < RadioKernel.MIN_DECODE_QUALITY) return "BELOW DECODE MARGIN";
        if (menu.decodeMargin() < 15) return "MARGINAL LINK";
        if (menu.adjacentAggressors() > 0) return "VALID • ADJACENT INTERFERENCE";
        if (menu.obstacleHits() > 0) return "VALID • OBSTRUCTED PATH";
        return "HEALTHY LINK";
    }

    private String nextAction() {
        if (!menu.coverageComplete()) return "NEXT • load/restore the relevant coverage before trusting radio evidence.";
        if (menu.collision() || menu.drivers() > 1) return "NEXT • move one same-channel transmitter to another channel or outside the shared coverage area.";
        if (menu.drivers() == 0 && menu.adjacentAggressors() > 0) return "NEXT • restore a same-channel source; adjacent-channel transmitters are present but cannot supply payload.";
        if (menu.drivers() == 0) return "NEXT • check transmitter channel, range and registration before receiver-side logic.";
        if (menu.decodeMargin() < 0 && menu.adjacentAggressors() > 0) return "NEXT • separate adjacent channels first, then re-check decode margin.";
        if (menu.decodeMargin() < 0 && menu.obstacleHits() > 0) return "NEXT • improve line-of-sight or shorten the path before changing downstream logic.";
        if (menu.decodeMargin() < 15) return "NEXT • increase link margin by reducing distance, obstacles or adjacent-channel exposure.";
        if (menu.adjacentAggressors() > 0) return "NEXT • link decodes, but channel separation would improve margin and robustness.";
        if (menu.obstacleHits() > 0) return "NEXT • current path is valid; remove obstructions if additional margin is required.";
        return "NEXT • link evidence is coherent; investigate downstream logic only if the payload behavior is still wrong.";
    }

    private int diagnosisColor() {
        if (menu.kind() != RadioLinkMenu.KIND_RECEIVER) return qualityColor();
        String diagnosis = receiverDiagnosis();
        if (diagnosis.startsWith("HEALTHY")) return GOOD;
        if (diagnosis.startsWith("VALID")) return INFO;
        return WARN;
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
