package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated HMI for byte/serial/differential converters and serial regeneration. */
public final class DigitalCommunicationScreen extends EngineeringScreen<DigitalCommunicationMenu> {
    private Button parameterPrevious;
    private Button parameterNext;

    public DigitalCommunicationScreen(DigitalCommunicationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Threshold"),
                b -> sendMenuButton(DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS))
                .bounds(leftPos + 16, y, 105, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Threshold ▶"),
                b -> sendMenuButton(DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT))
                .bounds(leftPos + 199, y, 105, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean regenerator = menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR;
        boolean configure = isConfigureSection();
        parameterPrevious.active = regenerator;
        parameterNext.active = regenerator;
        parameterPrevious.visible = configure && regenerator;
        parameterNext.visible = configure && regenerator;
        if (regenerator) {
            int threshold = switch (menu.parameter()) {
                case 0 -> 20;
                case 1 -> 40;
                default -> 60;
            };
            parameterPrevious.setMessage(Component.literal("◀ " + threshold + "%"));
            parameterNext.setMessage(Component.literal(threshold + "% ▶"));
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
        statusBadge(g, outputQualityName(), qualityColor(menu.outputQuality()), 205, 80);
        metricCard(g, "Input", valueText(menu.inputValue(), menu.inputDomain()), 16, 103, 88, INFO);
        metricCard(g, "Output", valueText(menu.outputValue(), menu.outputDomain()), 111, 103, 88, GOOD);
        metricCard(g, "Quality", qualityMetric(), 206, 103, 88, INFO);
        labelValue(g, "Contract", contract(), 149);
        labelValue(g, "Series path", face(menu.inputDirection()) + " → " + face(menu.outputDirection()), 165);
        if (menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR) {
            labelValue(g, "Threshold / input Q", thresholdPercent() + "% / " + menu.auxiliary() + "%", 181);
        } else if (menu.kind() == DigitalCommunicationMenu.KIND_SERIALIZER
                || menu.kind() == DigitalCommunicationMenu.KIND_DESERIALIZER) {
            labelValue(g, "Serial timing", Math.max(1, menu.auxiliary()) + " ticks", 181);
        } else {
            labelValue(g, "Authority", "SERVER SYNCHRONIZED", 181);
        }
        safeText(g, "Converters cross declared domains; regenerator stays SERIAL_DATA → SERIAL_DATA.", 16, 199, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, roleName(), GOOD, 16, 80);
        statusLine(g, face(menu.inputDirection()), "INPUT • " + menu.inputDomain().label(), qualityColor(menu.inputQuality()), 112);
        statusLine(g, "PROCESS", processName(), INFO, 140);
        statusLine(g, face(menu.outputDirection()), "OUTPUT • " + menu.outputDomain().label(), qualityColor(menu.outputQuality()), 168);
        safeText(g, "Input and output are an explicit two-face path; no hidden side port is implied.", 16, 198, MUTED);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        if (menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR) {
            labelValue(g, "Min input quality", thresholdPercent() + "%", 101);
        } else {
            labelValue(g, "Device parameter", "FIXED FUNCTION", 101);
        }
        labelValue(g, "Input face", face(menu.inputDirection()), 171);
        labelValue(g, "Output face", face(menu.outputDirection()), 187);
        safeText(g, "Physical input/output direction is controlled only on Route.", 16, 207, MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, outputQualityName(), qualityColor(menu.outputQuality()), 16, 80);
        labelValue(g, "Input quality", menu.inputQuality().name(), 108);
        labelValue(g, "Output quality", menu.outputQuality().name(), 126);
        labelValue(g, "Input", valueText(menu.inputValue(), menu.inputDomain()), 144);
        labelValue(g, "Output", valueText(menu.outputValue(), menu.outputDomain()), 162);
        labelValue(g, "Path", face(menu.inputDirection()) + " → " + face(menu.outputDirection()), 180);
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "CURRENT LINK EVIDENCE", INFO, 16, 80);
        safeText(g, "This directional communication HMI exposes authoritative current evidence.", 16, 108, TEXT);
        safeText(g, "It does not synthesize packet history that the server does not retain.", 16, 128, MUTED);
        if (menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR) {
            labelValue(g, "Input quality", menu.auxiliary() + "%", 154);
            labelValue(g, "Decision threshold", thresholdPercent() + "%", 172);
        }
    }

    private String deviceName() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_ENCODER -> "REDSTONE BYTE ENCODER";
            case DigitalCommunicationMenu.KIND_DECODER -> "BYTE TO REDSTONE DECODER";
            case DigitalCommunicationMenu.KIND_SERIALIZER -> "SERIALIZER";
            case DigitalCommunicationMenu.KIND_DESERIALIZER -> "DESERIALIZER";
            case DigitalCommunicationMenu.KIND_REGENERATOR -> "DIGITAL REGENERATOR";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> "DIFFERENTIAL DRIVER";
            case DigitalCommunicationMenu.KIND_DIFF_RECEIVER -> "DIFFERENTIAL RECEIVER";
            default -> "DIGITAL COMMUNICATION";
        };
    }

    private String roleName() {
        return menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR ? "SERIAL PROCESSOR" : "DOMAIN CONVERTER";
    }

    private String contract() {
        return menu.inputDomain().label() + " → " + menu.outputDomain().label();
    }

    private String processName() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_ENCODER -> "ENCODE 0..15 → BYTE";
            case DigitalCommunicationMenu.KIND_DECODER -> "DECODE BYTE → 0..15";
            case DigitalCommunicationMenu.KIND_SERIALIZER -> "BYTE → SERIAL FRAME";
            case DigitalCommunicationMenu.KIND_DESERIALIZER -> "SERIAL FRAME → BYTE";
            case DigitalCommunicationMenu.KIND_REGENERATOR -> "QUALITY GATE + REGENERATION";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> "LOGIC → DIFFERENTIAL";
            case DigitalCommunicationMenu.KIND_DIFF_RECEIVER -> "DIFFERENTIAL → REDSTONE";
            default -> "DECLARED TRANSFORM";
        };
    }

    private String valueText(int value, dev.redstoneengineering.core.domain.EngineeringDomain domain) {
        return switch (domain) {
            case DATA_BUS_8, SERIAL_DATA -> String.format("0x%02X", value & 0xFF);
            case DIFFERENTIAL_DATA -> Integer.toString(value & 1);
            default -> Integer.toString(value);
        };
    }

    private String qualityMetric() { return menu.inputQuality().name() + " → " + menu.outputQuality().name(); }
    private String outputQualityName() { return menu.outputQuality().name().replace('_', ' '); }
    private int thresholdPercent() { return switch (menu.parameter()) { case 0 -> 20; case 1 -> 40; default -> 60; }; }
    private String face(net.minecraft.core.Direction direction) { return direction.getName().toUpperCase(); }
    private int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> GOOD;
            case NO_SIGNAL, STALE -> WARN;
            default -> BAD;
        };
    }
}
