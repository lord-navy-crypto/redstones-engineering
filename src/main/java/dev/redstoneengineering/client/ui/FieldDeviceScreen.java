package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Context-sensitive inspector for probes, processors, communication media, timing and wave devices. */
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
        decrease = addConfigureWidget(Button.builder(Component.literal("−"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_DECREASE)).bounds(x, y, w, 20).build());
        increase = addConfigureWidget(Button.builder(Component.literal("+"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRIMARY_INCREASE)).bounds(x + w + gap, y, w, 20).build());
        toggle = addConfigureWidget(Button.builder(Component.literal("Toggle mode"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_TOGGLE)).bounds(x + (w + gap) * 2, y, w, 20).build());
        preset0 = addConfigureWidget(Button.builder(Component.literal("Preset 0"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_0)).bounds(x, y + 26, 64, 20).build());
        preset5 = addConfigureWidget(Button.builder(Component.literal("Preset 5"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_5)).bounds(x + 70, y + 26, 64, 20).build());
        preset10 = addConfigureWidget(Button.builder(Component.literal("Preset 10"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_10)).bounds(x + 140, y + 26, 64, 20).build());
        preset15 = addConfigureWidget(Button.builder(Component.literal("Preset 15"), b -> sendMenuButton(FieldDeviceMenu.BUTTON_PRESET_15)).bounds(x + 210, y + 26, 64, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (decrease == null) return;
        boolean primaryAdjust = menu.kind() == FieldDeviceMenu.KIND_PROBE
                || menu.kind() == FieldDeviceMenu.KIND_FILTER
                || menu.kind() == FieldDeviceMenu.KIND_REFERENCE
                || menu.kind() == FieldDeviceMenu.KIND_DIGITAL_REGENERATOR;
        decrease.active = primaryAdjust;
        increase.active = primaryAdjust;
        toggle.active = menu.kind() == FieldDeviceMenu.KIND_TERMINAL;
        boolean presets = menu.kind() == FieldDeviceMenu.KIND_REFERENCE;
        preset0.active = presets;
        preset5.active = presets;
        preset10.active = presets;
        preset15.active = presets;
        switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> { decrease.setMessage(Component.literal("◀ Channel")); increase.setMessage(Component.literal("Channel ▶")); }
            case FieldDeviceMenu.KIND_FILTER -> { decrease.setMessage(Component.literal("− Slew")); increase.setMessage(Component.literal("Slew +")); }
            case FieldDeviceMenu.KIND_REFERENCE -> { decrease.setMessage(Component.literal("− Output")); increase.setMessage(Component.literal("Output +")); }
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> { decrease.setMessage(Component.literal("− Threshold")); increase.setMessage(Component.literal("Threshold +")); }
            default -> { decrease.setMessage(Component.literal("−")); increase.setMessage(Component.literal("+")); }
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
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> renderByteBusOverview(graphics);
            case FieldDeviceMenu.KIND_ENCODER -> directionalOverview(graphics, "REDSTONE → BYTE", "Redstone input", menu.primary() + " / 15", "Byte output", byteText(menu.secondary()));
            case FieldDeviceMenu.KIND_DECODER -> directionalOverview(graphics, "BYTE → REDSTONE", "Byte input", byteText(menu.primary()), "Redstone output", menu.secondary() + " / 15");
            case FieldDeviceMenu.KIND_SERIAL_LINE -> renderSerialLineOverview(graphics);
            case FieldDeviceMenu.KIND_SERIALIZER -> directionalOverview(graphics, "BYTE → SERIAL FRAME", "Bus input", byteText(menu.primary()), "Serial payload", byteText(menu.secondary()));
            case FieldDeviceMenu.KIND_DESERIALIZER -> directionalOverview(graphics, "SERIAL → BYTE", "Serial input", byteText(menu.primary()), "Bus output", byteText(menu.secondary()));
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> {
                statusBadge(graphics, "DIFFERENTIAL DATA", validityColor(), 16, 80);
                labelValue(graphics, "Bit", Integer.toString(menu.primary()), 105);
                labelValue(graphics, "Link quality", menu.qualityPercent() + "%", 121);
                labelValue(graphics, "Compatible links", Integer.toString(menu.connectionCount()), 137);
                statusLine(graphics, "Payload", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> {
                int minimum = DigitalRegeneratorBlock.minimumQuality(menu.tertiary());
                statusBadge(graphics, "DIGITAL REGENERATOR", validityColor(), 16, 80);
                labelValue(graphics, "Input quality", menu.primary() + "%", 105);
                labelValue(graphics, "Decision threshold", minimum + "%", 121);
                labelValue(graphics, "Output byte", byteText(menu.secondary()), 137);
                statusLine(graphics, "Decision", menu.dataValid() ? "ACCEPT / RE-SHAPED" : "REJECT", validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> directionalOverview(graphics, "REDSTONE → DIFFERENTIAL", "Input bit", Integer.toString(menu.primary()), "Driven bit", Integer.toString(menu.secondary()));
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> directionalOverview(graphics, "DIFFERENTIAL → REDSTONE", "Received bit", Integer.toString(menu.primary()), "Redstone output", menu.secondary() + " / 15");
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> {
                statusBadge(graphics, "RADIO TRANSMITTER", validityColor(), 16, 80);
                labelValue(graphics, "Payload", menu.primary() + " / 15", 105);
                labelValue(graphics, "Channel", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Nominal range", menu.tertiary() + " blocks", 137);
                statusLine(graphics, "UP antenna", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> {
                statusBadge(graphics, "RADIO RECEIVER", validityColor(), 16, 80);
                labelValue(graphics, "Payload", menu.primary() + " / 15", 105);
                labelValue(graphics, "Channel", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Quality / drivers", menu.qualityPercent() + "% / " + menu.driverCount(), 137);
                statusLine(graphics, "Estimated latency", menu.tertiary() + " ticks", validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> directionalOverview(graphics, "FREE-SPACE OPTICAL TX", "Redstone input", menu.primary() + " / 15", "Optical launch", menu.tertiary() + " / 15");
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> directionalOverview(graphics, "FREE-SPACE OPTICAL RX", "Optical power", menu.primary() + " / 15", "Redstone output", menu.secondary() + " / 15");
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> {
                statusBadge(graphics, "QUARTZ CLOCK DIVIDER", validityColor(), 16, 80);
                labelValue(graphics, "Input period", menu.primary() + " ticks", 105);
                labelValue(graphics, "Output period", menu.secondary() + " ticks", 121);
                labelValue(graphics, "Division", "÷" + menu.tertiary(), 137);
                statusLine(graphics, "Timing output", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> {
                statusBadge(graphics, "QUARTZ STABILITY MONITOR", validityColor(), 16, 80);
                labelValue(graphics, "Measured period", menu.primary() + " ticks", 105);
                labelValue(graphics, "Nominal period", menu.tertiary() + " ticks", 121);
                labelValue(graphics, "Absolute error", menu.secondary() + " ticks", 137);
                statusLine(graphics, "Metrology", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR -> {
                statusBadge(graphics, "AMETHYST RESONATOR", validityColor(), 16, 80);
                labelValue(graphics, "Configured frequency", Integer.toString(menu.primary()), 105);
                labelValue(graphics, "Configured amplitude", menu.secondary() + " / 15", 121);
                statusLine(graphics, "Pulse state", menu.dataValid() ? "ACTIVE" : "IDLE", validityColor(), 141);
            }
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> {
                statusBadge(graphics, "RESONANCE BUS", validityColor(), 16, 80);
                labelValue(graphics, "Frequency", Integer.toString(menu.primary()), 105);
                labelValue(graphics, "Amplitude", menu.secondary() + " / 15", 121);
                labelValue(graphics, "Compatible links", Integer.toString(menu.connectionCount()), 137);
                statusLine(graphics, "Wave", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> {
                statusBadge(graphics, "AMETHYST FREQUENCY FILTER", validityColor(), 16, 80);
                labelValue(graphics, "Input frequency", Integer.toString(menu.primary()), 105);
                labelValue(graphics, "Target frequency", Integer.toString(menu.tertiary()), 121);
                labelValue(graphics, "Output amplitude", menu.secondary() + " / 15", 137);
                statusLine(graphics, "Pass state", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> {
                statusBadge(graphics, "TUNED AMETHYST RESONATOR", validityColor(), 16, 80);
                labelValue(graphics, "Natural frequency", Integer.toString(menu.primary()), 105);
                labelValue(graphics, "Q index", Integer.toString(menu.tertiary()), 121);
                labelValue(graphics, "Output amplitude", menu.secondary() + " / 15", 137);
                statusLine(graphics, "Resonant response", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> {
                statusBadge(graphics, "SPECTRUM ANALYZER • OBSERVER", validityColor(), 16, 80);
                labelValue(graphics, "Dominant frequency", Integer.toString(menu.primary()), 105);
                labelValue(graphics, "Integrated energy", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Active bands / samples", menu.tertiary() + " / " + menu.driverCount(), 137);
                statusLine(graphics, "Snapshot", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> {
                statusBadge(graphics, "MECHANICAL EXCITER", validityColor(), 16, 80);
                labelValue(graphics, "Drive amplitude", menu.primary() + " / 15", 105);
                labelValue(graphics, "Frequency", Integer.toString(menu.secondary()), 121);
                statusLine(graphics, "Emission", validityText(), validityColor(), 141);
            }
            case FieldDeviceMenu.KIND_SLIME_VIBRATION -> waveMediumOverview(graphics, "SLIME VIBRATION CONDUIT", "Packet amplitude", "Packet frequency");
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> receiverOverview(graphics, "MECHANICAL VIBRATION RECEIVER", "Wave amplitude", "Wave frequency", menu.tertiary());
            case FieldDeviceMenu.KIND_HONEY_DAMPER -> {
                statusBadge(graphics, "HONEY VIBRATION DAMPER", validityColor(), 16, 80);
                labelValue(graphics, "Packet amplitude", menu.primary() + " / 15", 105);
                labelValue(graphics, "Packet frequency", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Loss / update", menu.tertiary() + " amplitude", 137);
                statusLine(graphics, "Damped transient", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> {
                statusBadge(graphics, "SCULK VIBRATION INTERFACE", validityColor(), 16, 80);
                labelValue(graphics, "Current event code", menu.primary() + " / 15", 105);
                labelValue(graphics, "Event count", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Last code / transitions", menu.tertiary() + " / " + menu.driverCount(), 137);
                statusLine(graphics, "Event input", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> {
                statusBadge(graphics, "HYDROACOUSTIC TUBE", validityColor(), 16, 80);
                labelValue(graphics, "Pressure amplitude", menu.primary() + " / 15", 105);
                labelValue(graphics, "Frequency", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Medium", hydroMedium(menu.tertiary()), 137);
                statusLine(graphics, "Transient wave", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_HYDRO_EXCITER -> {
                statusBadge(graphics, "HYDROACOUSTIC EXCITER", validityColor(), 16, 80);
                labelValue(graphics, "Drive amplitude", menu.primary() + " / 15", 105);
                labelValue(graphics, "Frequency", Integer.toString(menu.secondary()), 121);
                labelValue(graphics, "Quality", menu.qualityPercent() + "%", 137);
                statusLine(graphics, "Pressure emission", validityText(), validityColor(), 157);
            }
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER -> receiverOverview(graphics, "HYDROACOUSTIC RECEIVER", "Pressure amplitude", "Frequency", menu.tertiary());
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> waveMediumOverview(graphics, "PHONON CONDUIT", "Pulse amplitude", "Pulse auxiliary");
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> {
                statusBadge(graphics, "THERMAL PULSE ENCODER", validityColor(), 16, 80);
                labelValue(graphics, "Redstone drive", menu.primary() + " / 15", 105);
                labelValue(graphics, "Quality", menu.qualityPercent() + "%", 121);
                statusLine(graphics, "Pulse emission", validityText(), validityColor(), 141);
            }
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> directionalOverview(graphics, "THERMAL PULSE RECEIVER", "Pulse input", menu.primary() + " / 15", "Redstone output", menu.secondary() + " / 15");
            default -> renderCableOverview(graphics);
        }
    }

    private void waveMediumOverview(GuiGraphics graphics, String title, String primaryLabel, String secondaryLabel) {
        statusBadge(graphics, title, validityColor(), 16, 80);
        labelValue(graphics, primaryLabel, menu.primary() + " / 15", 105);
        labelValue(graphics, secondaryLabel, Integer.toString(menu.secondary()), 121);
        labelValue(graphics, "Runtime quality", menu.qualityPercent() + "%", 137);
        statusLine(graphics, "Transient packet", validityText(), validityColor(), 157);
    }

    private void receiverOverview(GuiGraphics graphics, String title, String primaryLabel, String secondaryLabel, int output) {
        statusBadge(graphics, title, validityColor(), 16, 80);
        labelValue(graphics, primaryLabel, menu.primary() + " / 15", 105);
        labelValue(graphics, secondaryLabel, Integer.toString(menu.secondary()), 121);
        labelValue(graphics, "Redstone output", output + " / 15", 137);
        statusLine(graphics, "BACK decode", validityText(), validityColor(), 157);
    }

    private void directionalOverview(GuiGraphics graphics, String title, String left, String leftValue, String right, String rightValue) {
        statusBadge(graphics, title, validityColor(), 16, 80);
        labelValue(graphics, left, leftValue, 105);
        labelValue(graphics, right, rightValue, 121);
        labelValue(graphics, "Quality", menu.qualityPercent() + "%", 137);
        statusLine(graphics, "Runtime", validityText(), validityColor(), 157);
    }

    private void renderByteBusOverview(GuiGraphics graphics) {
        String state = !menu.dataValid() ? (menu.driverCount() == 0 ? "NO VALID DRIVER" : "BUS CONFLICT") : (menu.driverCount() > 1 ? "VALID • CONTENTION" : "VALID");
        int color = !menu.dataValid() ? (menu.driverCount() == 0 ? WARN : BAD) : (menu.driverCount() > 1 ? WARN : GOOD);
        statusBadge(graphics, "8-BIT DATA BUS", color, 16, 80);
        labelValue(graphics, "Payload", byteText(menu.primary()), 105);
        labelValue(graphics, "Driver count", Integer.toString(menu.driverCount()), 121);
        labelValue(graphics, "Compatible links", Integer.toString(menu.connectionCount()), 137);
        statusLine(graphics, "Bus state", state, color, 157);
    }

    private void renderSerialLineOverview(GuiGraphics graphics) {
        statusBadge(graphics, "SERIAL DATA LINE", validityColor(), 16, 80);
        labelValue(graphics, "Payload", byteText(menu.primary()), 105);
        labelValue(graphics, "Period", menu.secondary() + " ticks", 121);
        labelValue(graphics, "Quality", menu.qualityPercent() + "%", 137);
        statusLine(graphics, "Frame", validityText(), validityColor(), 157);
    }

    private void renderCableOverview(GuiGraphics graphics) {
        statusBadge(graphics, menu.topologyValid() ? "TOPOLOGY VALID" : "TOPOLOGY ERROR", menu.topologyValid() ? GOOD : BAD, 16, 80);
        labelValue(graphics, "Connections", menu.connectionCount() + " / 6", 105);
        labelValue(graphics, "Engineering ports", Integer.toString(menu.portCount()), 121);
        labelValue(graphics, "Connected faces", connectedFaces(), 137);
        if (menu.kind() == FieldDeviceMenu.KIND_REDSTONE_CABLE || menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION) {
            labelValue(graphics, "Signal", menu.primary() + " / 15", 153);
            signalBar(graphics, menu.primary(), 170);
        } else statusLine(graphics, "Medium", "INSTRUMENT BUS • measurement channels", INFO, 157);
    }

    private void renderPorts(GuiGraphics graphics) {
        labelValue(graphics, "Engineering ports", Integer.toString(menu.portCount()), 82);
        if (isCable()) {
            labelValue(graphics, "Connected faces", connectedFaces(), 102);
            statusLine(graphics, "Topology", menu.topologyValid() ? "VALID" : "INVALID", menu.topologyValid() ? GOOD : BAD, 122);
            graphics.drawString(font, "Cable ports are physical graph edges; absent faces are not virtual ports.", 16, 146, MUTED, false);
            graphics.drawString(font, "Junctions intentionally allow branching; plain signal cable remains two-ended.", 16, 164, INFO, false);
            return;
        }
        switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> { statusLine(graphics, directionName(menu.facingOrdinal()), "TEST • REDSTONE MEASUREMENT INPUT", GOOD, 105); statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), "INSTRUMENT BUS OUTPUT", INFO, 125); }
            case FieldDeviceMenu.KIND_FILTER -> renderDirectionalPorts(graphics, "BACK • REDSTONE INPUT", "FRONT • REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_REFERENCE -> statusLine(graphics, directionName(menu.facingOrdinal()), "REFERENCE OUT • REDSTONE 0..15", GOOD, 105);
            case FieldDeviceMenu.KIND_TERMINAL -> { statusLine(graphics, directionName(menu.facingOrdinal()), menu.tertiary() == 1 ? "VANILLA OUT" : "VANILLA IN", GOOD, 105); statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), menu.tertiary() == 1 ? "CABLE IN" : "CABLE OUT", INFO, 125); }
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> renderMediumPorts(graphics, "DATA_BUS_8 • BIDIRECTIONAL BYTE BUS");
            case FieldDeviceMenu.KIND_ENCODER -> renderDirectionalPorts(graphics, "BACK • REDSTONE INPUT", "FRONT • DATA_BUS_8 OUTPUT");
            case FieldDeviceMenu.KIND_DECODER -> renderDirectionalPorts(graphics, "BACK • DATA_BUS_8 INPUT", "FRONT • REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_SERIAL_LINE -> renderMediumPorts(graphics, "SERIAL_DATA • BIDIRECTIONAL LINK");
            case FieldDeviceMenu.KIND_SERIALIZER -> renderDirectionalPorts(graphics, "BACK • DATA_BUS_8 INPUT", "FRONT • SERIAL_DATA OUTPUT");
            case FieldDeviceMenu.KIND_DESERIALIZER -> renderDirectionalPorts(graphics, "BACK • SERIAL_DATA INPUT", "FRONT • DATA_BUS_8 OUTPUT");
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> renderMediumPorts(graphics, "DIFFERENTIAL_DATA • BIDIRECTIONAL BIT LINK");
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> renderDirectionalPorts(graphics, "BACK • SERIAL_DATA INPUT", "FRONT • REGENERATED SERIAL OUTPUT");
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> renderDirectionalPorts(graphics, "BACK • REDSTONE BIT INPUT", "FRONT • DIFFERENTIAL_DATA OUTPUT");
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> renderDirectionalPorts(graphics, "BACK • DIFFERENTIAL_DATA INPUT", "FRONT • REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> { statusLine(graphics, "UP", "RADIO_DATA ANTENNA OUTPUT", INFO, 105); statusLine(graphics, "OTHER FIVE FACES", "REDSTONE PAYLOAD INPUT", GOOD, 125); }
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> { statusLine(graphics, "UP", "RADIO_DATA ANTENNA INPUT", INFO, 105); statusLine(graphics, directionName(menu.facingOrdinal()), "FRONT • REDSTONE OUTPUT", GOOD, 125); }
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> renderDirectionalPorts(graphics, "BACK • REDSTONE POWER INPUT", "FRONT • OPTICAL BEAM OUTPUT");
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> renderDirectionalPorts(graphics, "BACK • OPTICAL BEAM INPUT", "FRONT • REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> renderDirectionalPorts(graphics, "BACK • QUARTZ TIMING INPUT", "FRONT • DIVIDED QUARTZ OUTPUT");
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> { statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), "BACK • QUARTZ TIMING MEASUREMENT", GOOD, 105); graphics.drawString(font, "Monitor is observer-only: FRONT is deliberately not a timing driver.", 16, 131, MUTED, false); }
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR -> renderMediumPorts(graphics, "AMETHYST • FOUR HORIZONTAL SOURCE OUTPUTS");
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> renderMediumPorts(graphics, "AMETHYST • HORIZONTAL RESONANCE BUS");
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> renderDirectionalPorts(graphics, "BACK • AMETHYST INPUT", "FRONT • FILTERED AMETHYST OUTPUT");
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> renderDirectionalPorts(graphics, "BACK • AMETHYST INPUT", "FRONT • RESONANT AMETHYST OUTPUT");
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> { statusLine(graphics, "UP", "AMETHYST SPECTRUM MEASUREMENT APERTURE", GOOD, 105); graphics.drawString(font, "Observer-only: analyzer never drives the measured resonance network.", 16, 131, MUTED, false); }
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> { statusLine(graphics, "DOWN", "REDSTONE DRIVE INPUT", GOOD, 105); statusLine(graphics, "UP + HORIZONTAL", "MECHANICAL_VIBRATION OUTPUT", INFO, 125); }
            case FieldDeviceMenu.KIND_SLIME_VIBRATION -> renderMediumPorts(graphics, "MECHANICAL_VIBRATION • BIDIRECTIONAL TRANSIENT PATH");
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> renderDirectionalPorts(graphics, "BACK • MECHANICAL_VIBRATION INPUT", "FRONT • REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_HONEY_DAMPER -> renderMediumPorts(graphics, "MECHANICAL_VIBRATION • HIGH-LOSS BIDIRECTIONAL PATH");
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> renderDirectionalPorts(graphics, "BACK • SCULK EVENT-CODE REDSTONE INPUT", "FRONT • EVENT-CODE REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> renderMediumPorts(graphics, "HYDROACOUSTIC • BIDIRECTIONAL PRESSURE PATH");
            case FieldDeviceMenu.KIND_HYDRO_EXCITER -> { statusLine(graphics, "DOWN", "REDSTONE DRIVE INPUT", GOOD, 105); statusLine(graphics, "UP + HORIZONTAL", "HYDROACOUSTIC OUTPUT", INFO, 125); }
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER -> renderDirectionalPorts(graphics, "BACK • HYDROACOUSTIC INPUT", "FRONT • REDSTONE OUTPUT");
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> renderMediumPorts(graphics, "PHONON_THERMAL • BIDIRECTIONAL PULSE PATH");
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> { statusLine(graphics, "DOWN", "REDSTONE DRIVE INPUT", GOOD, 105); statusLine(graphics, "UP + HORIZONTAL", "PHONON_THERMAL OUTPUT", INFO, 125); }
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> renderDirectionalPorts(graphics, "BACK • PHONON_THERMAL INPUT", "FRONT • REDSTONE OUTPUT");
            default -> { }
        }
    }

    private void renderMediumPorts(GuiGraphics graphics, String medium) {
        statusLine(graphics, "Engineering medium", medium, INFO, 105);
        labelValue(graphics, "Compatible neighbors", connectedFaces(), 125);
        statusLine(graphics, "Topology", menu.topologyValid() ? "NO DOMAIN MISMATCH" : "DOMAIN/DIRECTION ISSUE", menu.topologyValid() ? GOOD : BAD, 145);
    }

    private void renderDirectionalPorts(GuiGraphics graphics, String back, String front) {
        statusLine(graphics, oppositeDirectionName(menu.facingOrdinal()), back, GOOD, 105);
        statusLine(graphics, directionName(menu.facingOrdinal()), front, INFO, 125);
        graphics.drawString(font, "Other faces expose no directional endpoint port.", 16, 151, MUTED, false);
    }

    private void renderConfigure(GuiGraphics graphics) {
        switch (menu.kind()) {
            case FieldDeviceMenu.KIND_PROBE -> { labelValue(graphics, "Selected channel", SignalProbeBlock.channelName(menu.secondary()), 80); graphics.drawString(font, "Changing channel never changes the measured node.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_FILTER -> { labelValue(graphics, "Slew rate", menu.tertiary() + " step/tick", 80); graphics.drawString(font, "Rate is bounded 1..4; server owns convergence.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_REFERENCE -> { labelValue(graphics, "Reference output", menu.primary() + " / 15", 80); graphics.drawString(font, "Use ±1 adjustment or lab presets.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_TERMINAL -> { labelValue(graphics, "Boundary direction", menu.tertiary() == 1 ? "CABLE → VANILLA" : "VANILLA → CABLE", 80); graphics.drawString(font, "Toggle recomputes the cable network on the server.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> { labelValue(graphics, "Minimum input quality", DigitalRegeneratorBlock.minimumQuality(menu.tertiary()) + "%", 80); graphics.drawString(font, "Server accepts frames only above threshold.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR -> { labelValue(graphics, "Frequency / amplitude", menu.primary() + " / " + menu.secondary(), 80); graphics.drawString(font, "Configuration remains server-side; Inspector is synchronized readback in this wave.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> { labelValue(graphics, "Target frequency", Integer.toString(menu.tertiary()), 80); graphics.drawString(font, "Target selection remains server-side; output readback is authoritative.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> { labelValue(graphics, "Natural f / Q", menu.primary() + " / " + menu.tertiary(), 80); graphics.drawString(font, "Tuning remains server-side; this screen never calculates resonance.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> { labelValue(graphics, "Excitation frequency", Integer.toString(menu.secondary()), 80); graphics.drawString(font, "DOWN drive and emitted packet are authoritative server state.", 16, 163, MUTED, false); }
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> { labelValue(graphics, "Hydroacoustic medium", hydroMedium(menu.tertiary()), 80); graphics.drawString(font, "Medium cycling remains a bounded server-side quick action.", 16, 163, MUTED, false); }
            default -> { statusLine(graphics, "Configuration", isWaveDevice() ? "READ-ONLY WAVE DEVICE" : isCommunicationDevice() ? "READ-ONLY COMMUNICATION DEVICE" : "READ-ONLY TOPOLOGY DEVICE", MUTED, 82); graphics.drawString(font, "Runtime payloads remain outside high-cardinality BlockState.", 16, 163, MUTED, false); }
        }
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        statusLine(graphics, "Device", deviceName(), INFO, 82);
        labelValue(graphics, "Port count", Integer.toString(menu.portCount()), 102);
        if (isCable()) {
            labelValue(graphics, "Connection count", Integer.toString(menu.connectionCount()), 122);
            statusLine(graphics, "Topology", menu.topologyValid() ? "PASS" : "FAIL", menu.topologyValid() ? GOOD : BAD, 142);
            labelValue(graphics, "Faces", connectedFaces(), 162);
        } else if (isCommunicationDevice()) {
            statusLine(graphics, "Runtime payload", validityText(), validityColor(), 122);
            labelValue(graphics, "Quality", menu.qualityPercent() + "%", 142);
            labelValue(graphics, "Links / drivers", menu.connectionCount() + " / " + menu.driverCount(), 162);
        } else if (isTimingDevice()) {
            statusLine(graphics, "Timing snapshot", validityText(), validityColor(), 122);
            labelValue(graphics, "Measured/input period", menu.primary() + " ticks", 142);
            labelValue(graphics, "Output/error", Integer.toString(menu.secondary()), 162);
        } else if (isWaveDevice()) {
            statusLine(graphics, "Wave snapshot", validityText(), validityColor(), 122);
            labelValue(graphics, "Primary / secondary", menu.primary() + " / " + menu.secondary(), 142);
            labelValue(graphics, "Quality / topology", menu.qualityPercent() + "% / " + (menu.topologyValid() ? "PASS" : "FAIL"), 162);
        } else {
            statusLine(graphics, "Server snapshot", "VALID • synchronized", GOOD, 122);
            labelValue(graphics, "Orientation", directionName(menu.facingOrdinal()), 142);
            graphics.drawString(font, "Runtime values stay out of high-cardinality BlockState where practical.", 16, 166, MUTED, false);
        }
    }

    private void renderHistory(GuiGraphics graphics) {
        graphics.drawString(font, "This field device does not retain a local time-series history.", 16, 84, TEXT, false);
        graphics.drawString(font, isWaveDevice() ? "Use Spectrum Analyzer or Oscilloscope for historical/frequency evidence."
                : isCommunicationDevice() ? "Network diagnostics retain bounded counters; payload remains runtime state."
                : "Use Signal Analyzer, Oscilloscope, or Logic Analyzer for historical evidence.", 16, 103, INFO, false);
        sectionRule(graphics, 126);
        graphics.drawString(font, "Inspector stays lightweight: observe authoritative state, ports and topology.", 16, 140, MUTED, false);
    }

    private boolean isCable() {
        return menu.kind() == FieldDeviceMenu.KIND_REDSTONE_CABLE || menu.kind() == FieldDeviceMenu.KIND_REDSTONE_JUNCTION || menu.kind() == FieldDeviceMenu.KIND_INSTRUMENT_CABLE;
    }

    private boolean isCommunicationDevice() {
        return menu.kind() >= FieldDeviceMenu.KIND_DATA_BUS_8 && menu.kind() <= FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER;
    }

    private boolean isTimingDevice() {
        return menu.kind() == FieldDeviceMenu.KIND_QUARTZ_DIVIDER || menu.kind() == FieldDeviceMenu.KIND_QUARTZ_STABILITY;
    }

    private boolean isWaveDevice() {
        return menu.kind() >= FieldDeviceMenu.KIND_AMETHYST_RESONATOR && menu.kind() <= FieldDeviceMenu.KIND_THERMAL_RECEIVER;
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
            case FieldDeviceMenu.KIND_DATA_BUS_8 -> "8-BIT DATA BUS";
            case FieldDeviceMenu.KIND_ENCODER -> "REDSTONE BYTE ENCODER";
            case FieldDeviceMenu.KIND_DECODER -> "BYTE TO REDSTONE DECODER";
            case FieldDeviceMenu.KIND_SERIAL_LINE -> "SERIAL DATA LINE";
            case FieldDeviceMenu.KIND_SERIALIZER -> "SERIALIZER";
            case FieldDeviceMenu.KIND_DESERIALIZER -> "DESERIALIZER";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_PAIR -> "DIFFERENTIAL DATA PAIR";
            case FieldDeviceMenu.KIND_DIGITAL_REGENERATOR -> "DIGITAL REGENERATOR";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_DRIVER -> "DIFFERENTIAL DRIVER";
            case FieldDeviceMenu.KIND_DIFFERENTIAL_RECEIVER -> "DIFFERENTIAL RECEIVER";
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> "RADIO TRANSMITTER";
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> "RADIO RECEIVER";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> "FREE-SPACE OPTICAL TRANSMITTER";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> "FREE-SPACE OPTICAL RECEIVER";
            case FieldDeviceMenu.KIND_QUARTZ_DIVIDER -> "QUARTZ CLOCK DIVIDER";
            case FieldDeviceMenu.KIND_QUARTZ_STABILITY -> "QUARTZ STABILITY MONITOR";
            case FieldDeviceMenu.KIND_AMETHYST_RESONATOR -> "AMETHYST RESONATOR";
            case FieldDeviceMenu.KIND_AMETHYST_DUST -> "AMETHYST RESONANCE DUST";
            case FieldDeviceMenu.KIND_AMETHYST_FILTER -> "AMETHYST FREQUENCY FILTER";
            case FieldDeviceMenu.KIND_AMETHYST_TUNED -> "AMETHYST TUNED RESONATOR";
            case FieldDeviceMenu.KIND_AMETHYST_SPECTRUM -> "AMETHYST SPECTRUM ANALYZER";
            case FieldDeviceMenu.KIND_MECHANICAL_EXCITER -> "MECHANICAL EXCITER";
            case FieldDeviceMenu.KIND_SLIME_VIBRATION -> "SLIME VIBRATION CONDUIT";
            case FieldDeviceMenu.KIND_MECHANICAL_RECEIVER -> "MECHANICAL VIBRATION RECEIVER";
            case FieldDeviceMenu.KIND_HONEY_DAMPER -> "HONEY VIBRATION DAMPER";
            case FieldDeviceMenu.KIND_SCULK_INTERFACE -> "SCULK VIBRATION INTERFACE";
            case FieldDeviceMenu.KIND_HYDRO_TUBE -> "HYDROACOUSTIC TUBE";
            case FieldDeviceMenu.KIND_HYDRO_EXCITER -> "HYDROACOUSTIC EXCITER";
            case FieldDeviceMenu.KIND_HYDRO_RECEIVER -> "HYDROACOUSTIC RECEIVER";
            case FieldDeviceMenu.KIND_PHONON_CONDUIT -> "PHONON CONDUIT";
            case FieldDeviceMenu.KIND_THERMAL_ENCODER -> "THERMAL PULSE ENCODER";
            case FieldDeviceMenu.KIND_THERMAL_RECEIVER -> "THERMAL PULSE RECEIVER";
            default -> "UNKNOWN";
        };
    }

    private String validityText() { return menu.dataValid() ? "VALID" : "INVALID / NO SIGNAL"; }
    private int validityColor() { return menu.dataValid() ? GOOD : WARN; }

    private String connectedFaces() {
        StringBuilder text = new StringBuilder();
        for (Direction direction : Direction.values()) {
            if ((menu.connectionMask() & (1 << direction.ordinal())) == 0) continue;
            if (text.isEmpty()) text.append(direction.getName().toUpperCase());
            else text.append(" · ").append(direction.getName().toUpperCase());
        }
        return text.isEmpty() ? "NONE" : text.toString();
    }

    private static String hydroMedium(int value) {
        return switch (value) {
            case 1 -> "MILK-MODEL";
            case 2 -> "LAVA";
            default -> "WATER";
        };
    }

    private static String byteText(int value) {
        int bounded = Math.max(0, Math.min(255, value));
        return bounded + " / 255 (0x" + String.format("%02X", bounded) + ")";
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
