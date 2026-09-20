package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.domain.EngineeringDomain;
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

    public DigitalCommunicationScreen(DigitalCommunicationMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Threshold"), b -> sendMenuButton(DigitalCommunicationMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos + 16, y, 105, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Threshold ▶"), b -> sendMenuButton(DigitalCommunicationMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos + 199, y, 105, 20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean regenerator = menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR;
        boolean configure = isConfigureSection();
        parameterPrevious.active = regenerator;
        parameterNext.active = regenerator;
        parameterPrevious.visible = configure && regenerator;
        parameterNext.visible = configure && regenerator;
        if (regenerator) {
            int threshold = thresholdPercent();
            parameterPrevious.setMessage(Component.literal("◀ " + threshold + "%"));
            parameterNext.setMessage(Component.literal(threshold + "% ▶"));
        }
    }

    @Override protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) { case OVERVIEW -> overview(graphics); case PORTS -> ports(graphics); case CONFIGURE -> configure(graphics); case DIAGNOSTICS -> diagnostics(graphics); case HISTORY -> history(graphics); }
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceName(), GOOD, 16, 80);
        statusBadge(g, outputQualityName(), qualityColor(menu.outputQuality()), 205, 80);
        metricCard(g, "Input", valueText(menu.inputValue(), menu.inputDomain()), 16, 103, 88, INFO);
        metricCard(g, "Output", valueText(menu.outputValue(), menu.outputDomain()), 111, 103, 88, GOOD);
        metricCard(g, "Link Q", mediumQualityText(), 206, 103, 88, mediumColor());
        labelValue(g, "Contract", contract(), 149);
        labelValue(g, "Series path", face(menu.inputDirection()) + " → " + face(menu.outputDirection()), 165);
        labelValue(g, "Media evidence", mediumHeadline(), 181);
        safeText(g, "MODEL • " + digitalEquation(), 16, 195, GOOD);
        safeText(g, mediaIdentity(), 16, 211, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, roleName(), GOOD, 16, 80);
        statusLine(g, face(menu.inputDirection()), "INPUT • " + menu.inputDomain().label(), qualityColor(menu.inputQuality()), 112);
        statusLine(g, "PROCESS", processName(), INFO, 140);
        statusLine(g, face(menu.outputDirection()), "OUTPUT • " + menu.outputDomain().label(), qualityColor(menu.outputQuality()), 168);
        safeText(g, "TRANSFER • " + digitalEquation(), 16, 190, GOOD);
        safeText(g, "Input and output are an explicit two-face path; no hidden side port is implied.", 16, 206, MUTED);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        safeText(g, "EQUATION • " + digitalEquation(), 16, 98, GOOD);
        safeText(g, "CONTROL MAP • " + digitalControlMap(), 16, 116, INFO);
        if (menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR) labelValue(g, "Min input quality", thresholdPercent() + "%", 140);
        else labelValue(g, "Device parameter", digitalParameterText(), 140);
        labelValue(g, "Input face", face(menu.inputDirection()), 171);
        labelValue(g, "Output face", face(menu.outputDirection()), 187);
        safeText(g, "Physical input/output direction is controlled only on Route.", 16, 207, MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, outputQualityName(), qualityColor(menu.outputQuality()), 16, 80);
        safeText(g, "CHECK • " + digitalDiagnosticRelation(), 16, 98, GOOD);
        statusLine(g, mediumName(), mediumHeadline(), mediumColor(), 116);
        labelValue(g, "Input / output quality", menu.inputQuality().name() + " → " + menu.outputQuality().name(), 138);
        labelValue(g, "Link quality / age", mediumQualityText() + " / " + mediumAgeText(), 156);
        labelValue(g, "Link drivers", Integer.toString(menu.mediumDriverCount()), 174);
        labelValue(g, mediumMetricLabel(), mediumMetricValue(), 192);
        statusLine(g, "Diagnosis", diagnosis(), diagnosisColor(), 210);
        safeText(g, nextAction(), 16, 226, diagnosisColor());
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "CURRENT LINK EVIDENCE", INFO, 16, 80);
        safeText(g, "This directional communication HMI exposes authoritative current evidence.", 16, 106, TEXT);
        safeText(g, "It does not synthesize packet history that the server does not retain.", 16, 124, MUTED);
        labelValue(g, "Medium", mediumName(), 148);
        labelValue(g, "Evidence", mediumHeadline(), 166);
        labelValue(g, "Quality / age", mediumQualityText() + " / " + mediumAgeText(), 184);
        labelValue(g, mediumMetricLabel(), mediumMetricValue(), 202);
        safeText(g, mediumTradeoff(), 16, 222, MUTED);
    }

    private String digitalEquation() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_ENCODER ->
                    "byte = DIRECT ? clamp(x,0,15) : 17*clamp(x,0,15)";
            case DigitalCommunicationMenu.KIND_DECODER ->
                    "y = CLAMP ? min(15,byte) : round(byte/17)";
            case DigitalCommunicationMenu.KIND_SERIALIZER ->
                    "serial.value=byte; word period T∈{4,8,16} ticks";
            case DigitalCommunicationMenu.KIND_REGENERATOR ->
                    "accept = inputValid AND q_in >= q_min; output keeps value/period only when accepted";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER ->
                    "bit = (redstone >= Vth) ? 1 : 0; Vth∈{1,4,8,12}";
            case DigitalCommunicationMenu.KIND_DIFF_RECEIVER ->
                    "redstone output = decoded differential bit mapped to 0/15";
            default -> "explicit domain conversion with evidence preserved";
        };
    }

    private String digitalControlMap() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_ENCODER -> "mode selects DIRECT or FULL_SCALE byte mapping";
            case DigitalCommunicationMenu.KIND_DECODER -> "mode selects CLAMP or FULL_SCALE decode mapping";
            case DigitalCommunicationMenu.KIND_SERIALIZER -> "T profile selects 4/8/16 ticks per word";
            case DigitalCommunicationMenu.KIND_REGENERATOR -> "q_min=" + thresholdPercent() + "%";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> "threshold profile selects Vth";
            default -> "no extra player-owned scalar beyond declared route";
        };
    }

    private String digitalParameterText() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_ENCODER -> processName();
            case DigitalCommunicationMenu.KIND_DECODER -> processName();
            case DigitalCommunicationMenu.KIND_SERIALIZER -> "word period profile • " + mediumMetricValue();
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> processName();
            default -> "FIXED FUNCTION";
        };
    }

    private String digitalDiagnosticRelation() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_ENCODER, DigitalCommunicationMenu.KIND_DECODER ->
                    "input=" + valueText(menu.inputValue(), menu.inputDomain()) + " • output="
                            + valueText(menu.outputValue(), menu.outputDomain());
            case DigitalCommunicationMenu.KIND_SERIALIZER ->
                    "byte=" + menu.outputValue() + " • period=" + Math.max(1, menu.mediumMetricA()) + "t"
                            + " • util=" + menu.mediumMetricB() + "%";
            case DigitalCommunicationMenu.KIND_REGENERATOR ->
                    "q_in=" + menu.auxiliary() + "% • q_min=" + thresholdPercent() + "% • margin="
                            + signed(menu.auxiliary() - thresholdPercent()) + "%";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER ->
                    "input=" + menu.inputValue() + "/15 • output bit=" + (menu.outputValue() & 1);
            default -> "link quality=" + mediumQualityText() + " • drivers=" + menu.mediumDriverCount();
        };
    }

    private String diagnosis() {
        if (menu.inputQuality() == PortQuality.TOPOLOGY_ERROR || menu.outputQuality() == PortQuality.TOPOLOGY_ERROR) return "LINK TOPOLOGY / DRIVER CONFLICT";
        if (menu.inputQuality() == PortQuality.NO_SIGNAL) return "NO INPUT LINK EVIDENCE";
        if (menu.inputQuality() == PortQuality.STALE) return "STALE INPUT LINK EVIDENCE";
        if (menu.outputQuality() == PortQuality.NO_SIGNAL) return "NO VALID OUTPUT AFTER TRANSFORM";
        if (menu.outputQuality() == PortQuality.STALE) return "STALE OUTPUT LINK EVIDENCE";

        if (menu.mediumDomain() == EngineeringDomain.DATA_BUS_8) {
            if (menu.mediumMetricC() > 0) return "8-BIT BUS DRIVER CONFLICT OBSERVED";
            if (menu.mediumMetricB() > 0) return "8-BIT BUS CONTENTION CONSUMING MARGIN";
            if (menu.mediumQualityPercent() < 70) return "8-BIT BUS LOADING MARGIN LOW";
            return "8-BIT PARALLEL BUS HEALTHY";
        }
        if (menu.mediumDomain() == EngineeringDomain.SERIAL_DATA) {
            if (menu.mediumDriverCount() > 1) return "SERIAL MULTI-DRIVER CONFLICT";
            if (menu.mediumMetricB() >= 90) return "SERIAL LINK NEAR UTILIZATION LIMIT";
            if (menu.mediumQualityPercent() < 70) return "SERIAL LINK QUALITY MARGINAL";
            if (menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR && menu.auxiliary() < thresholdPercent()) return "SERIAL QUALITY BELOW REGENERATION THRESHOLD";
            return "SERIAL FRAME TIMING PRESENT";
        }
        if (menu.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) {
            if (menu.mediumDriverCount() > 1) return "DIFFERENTIAL MULTI-DRIVER CONFLICT";
            if (menu.mediumQualityPercent() < 70) return "DIFFERENTIAL LINK MARGIN EXHAUSTED";
            return "DIFFERENTIAL HIGH-INTEGRITY LINK VALID";
        }
        return "DOMAIN CONVERSION VALID";
    }

    private String nextAction() {
        String d = diagnosis();
        if (d.contains("CONFLICT")) return "NEXT • isolate multiple drivers or invalid link topology before decoding data.";
        if (d.contains("NO INPUT") || d.contains("STALE INPUT")) return "NEXT • restore current upstream link evidence before troubleshooting conversion.";
        if (d.contains("CONTENTION")) return "NEXT • reduce redundant bus driving; same-value multi-drive still consumes parallel-bus margin.";
        if (d.contains("LOADING")) return "NEXT • shorten or segment the 8-bit bus instead of treating parallel wiring as a free long-distance link.";
        if (d.contains("UTILIZATION")) return "NEXT • reduce frame demand or move the payload to a wider/local bus where parallel wiring is acceptable.";
        if (d.contains("SERIAL LINK QUALITY")) return "NEXT • shorten/regenerate the serial path before increasing traffic.";
        if (d.contains("BELOW")) return "NEXT • improve serial link quality or lower the accepted threshold only with commissioning evidence.";
        if (d.contains("DIFFERENTIAL LINK MARGIN")) return "NEXT • shorten the link or remove topology faults; differential margin is finite even with slower quality decay.";
        if (d.contains("NO VALID OUTPUT") || d.contains("STALE OUTPUT")) return "NEXT • verify the transform contract and downstream medium after confirming valid input.";
        return "NEXT • link evidence is coherent; choose the medium by payload width, wiring cost, timing and integrity needs.";
    }

    private int diagnosisColor() {
        String d = diagnosis();
        return d.contains("HEALTHY") || d.contains("PRESENT") || d.contains("VALID") ? GOOD : WARN;
    }

    private String mediumHeadline() {
        if (menu.mediumDomain() == EngineeringDomain.DATA_BUS_8) {
            return "8-bit parallel • nodes=" + menu.mediumMetricA() + " • drivers=" + menu.mediumDriverCount();
        }
        if (menu.mediumDomain() == EngineeringDomain.SERIAL_DATA) {
            return "serial • period=" + Math.max(1, menu.mediumMetricA()) + "t • util=" + menu.mediumMetricB() + "%";
        }
        if (menu.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) {
            return "1-bit high-integrity • drivers=" + menu.mediumDriverCount();
        }
        return "NO COMMUNICATION MEDIUM";
    }

    private String mediumMetricLabel() {
        if (menu.mediumDomain() == EngineeringDomain.DATA_BUS_8) return "Contention / conflicts";
        if (menu.mediumDomain() == EngineeringDomain.SERIAL_DATA) return "Period / utilization / nodes";
        if (menu.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) return "Payload width";
        return "Medium metric";
    }

    private String mediumMetricValue() {
        if (menu.mediumDomain() == EngineeringDomain.DATA_BUS_8) return menu.mediumMetricB() + " / " + menu.mediumMetricC() + " frames";
        if (menu.mediumDomain() == EngineeringDomain.SERIAL_DATA) return Math.max(1, menu.mediumMetricA()) + "t / " + menu.mediumMetricB() + "% / " + menu.mediumMetricC();
        if (menu.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) return menu.mediumMetricA() + " bit";
        return "N/A";
    }

    private String mediumTradeoff() {
        if (menu.mediumDomain() == EngineeringDomain.DATA_BUS_8) return "TRADE-OFF • highest local payload width; loading and driver coordination cost margin.";
        if (menu.mediumDomain() == EngineeringDomain.SERIAL_DATA) return "TRADE-OFF • fewer conductors; frame timing and utilization become the engineering limit.";
        if (menu.mediumDomain() == EngineeringDomain.DIFFERENTIAL_DATA) return "TRADE-OFF • one-bit payload density; stronger link margin suits discrete control and protection state.";
        return "TRADE-OFF • no universal communication medium is implied.";
    }

    private String mediaIdentity() {
        return switch (menu.kind()) {
            case DigitalCommunicationMenu.KIND_SERIALIZER, DigitalCommunicationMenu.KIND_DESERIALIZER, DigitalCommunicationMenu.KIND_REGENERATOR -> "Serial media emphasizes ordered timing, utilization and link-quality evidence.";
            case DigitalCommunicationMenu.KIND_DIFF_DRIVER, DigitalCommunicationMenu.KIND_DIFF_RECEIVER -> "Differential media trades payload density for stronger binary link integrity.";
            case DigitalCommunicationMenu.KIND_ENCODER, DigitalCommunicationMenu.KIND_DECODER -> "8-bit bus media preserves parallel byte identity but pays local loading/contention cost.";
            default -> "Converters cross declared domains; no hidden universal medium is assumed.";
        };
    }

    private String mediumName() {
        return switch (menu.mediumDomain()) {
            case DATA_BUS_8 -> "8-BIT DATA BUS";
            case SERIAL_DATA -> "SERIAL DATA";
            case DIFFERENTIAL_DATA -> "DIFFERENTIAL DATA";
            default -> "DIGITAL LINK";
        };
    }

    private String mediumQualityText() { return menu.mediumAgeTicks() < 0 ? "N/A" : menu.mediumQualityPercent() + "%"; }
    private String mediumAgeText() { return menu.mediumAgeTicks() < 0 ? "NO SAMPLE" : menu.mediumAgeTicks() + "t"; }
    private int mediumColor() { if (menu.mediumAgeTicks() < 0) return MUTED; if (menu.mediumQualityPercent() >= 85) return GOOD; if (menu.mediumQualityPercent() >= 60) return INFO; return WARN; }
    private String deviceName() { return switch (menu.kind()) { case DigitalCommunicationMenu.KIND_ENCODER -> "REDSTONE BYTE ENCODER"; case DigitalCommunicationMenu.KIND_DECODER -> "BYTE TO REDSTONE DECODER"; case DigitalCommunicationMenu.KIND_SERIALIZER -> "SERIALIZER"; case DigitalCommunicationMenu.KIND_DESERIALIZER -> "DESERIALIZER"; case DigitalCommunicationMenu.KIND_REGENERATOR -> "DIGITAL REGENERATOR"; case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> "DIFFERENTIAL DRIVER"; case DigitalCommunicationMenu.KIND_DIFF_RECEIVER -> "DIFFERENTIAL RECEIVER"; default -> "DIGITAL COMMUNICATION"; }; }
    private String roleName() { return menu.kind() == DigitalCommunicationMenu.KIND_REGENERATOR ? "SERIAL PROCESSOR" : "DOMAIN CONVERTER"; }
    private String contract() { return menu.inputDomain().label() + " → " + menu.outputDomain().label(); }
    private String processName() { return switch (menu.kind()) { case DigitalCommunicationMenu.KIND_ENCODER -> "ENCODE 0..15 → BYTE"; case DigitalCommunicationMenu.KIND_DECODER -> "DECODE BYTE → 0..15"; case DigitalCommunicationMenu.KIND_SERIALIZER -> "BYTE → SERIAL FRAME"; case DigitalCommunicationMenu.KIND_DESERIALIZER -> "SERIAL FRAME → BYTE"; case DigitalCommunicationMenu.KIND_REGENERATOR -> "QUALITY GATE + REGENERATION"; case DigitalCommunicationMenu.KIND_DIFF_DRIVER -> "LOGIC → DIFFERENTIAL"; case DigitalCommunicationMenu.KIND_DIFF_RECEIVER -> "DIFFERENTIAL → REDSTONE"; default -> "DECLARED TRANSFORM"; }; }
    private String valueText(int value, EngineeringDomain domain) { return switch (domain) { case DATA_BUS_8, SERIAL_DATA -> String.format("0x%02X", value & 0xFF); case DIFFERENTIAL_DATA -> Integer.toString(value & 1); default -> Integer.toString(value); }; }
    private static String signed(int value) { return value >= 0 ? "+" + value : Integer.toString(value); }
    private String outputQualityName() { return menu.outputQuality().name().replace('_', ' '); }
    private int thresholdPercent() { return switch (menu.parameter()) { case 0 -> 20; case 1 -> 40; default -> 60; }; }
    private String face(net.minecraft.core.Direction direction) { return direction.getName().toUpperCase(); }
    private int qualityColor(PortQuality quality) { return switch (quality) { case VALID -> GOOD; case NO_SIGNAL, STALE -> WARN; default -> BAD; }; }
}
