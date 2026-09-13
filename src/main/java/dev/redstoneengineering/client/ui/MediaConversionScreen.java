package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.MediaConversionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated HMI for the explicit Redstone ↔ Lapis representation boundary. */
public final class MediaConversionScreen extends EngineeringScreen<MediaConversionMenu> {
    private Button rxPrevious;
    private Button rxNext;
    private Button txPrevious;
    private Button txNext;

    public MediaConversionScreen(MediaConversionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y1 = topPos + 118;
        int y2 = topPos + 144;
        rxPrevious = addConfigureWidget(Button.builder(Component.literal("RX ◀"),
                b -> sendMenuButton(MediaConversionMenu.BUTTON_RX_PREVIOUS))
                .bounds(leftPos + 52, y1, 72, 20).build());
        rxNext = addConfigureWidget(Button.builder(Component.literal("RX ▶"),
                b -> sendMenuButton(MediaConversionMenu.BUTTON_RX_NEXT))
                .bounds(leftPos + 132, y1, 72, 20).build());
        txPrevious = addConfigureWidget(Button.builder(Component.literal("TX ◀"),
                b -> sendMenuButton(MediaConversionMenu.BUTTON_TX_PREVIOUS))
                .bounds(leftPos + 52, y2, 72, 20).build());
        txNext = addConfigureWidget(Button.builder(Component.literal("TX ▶"),
                b -> sendMenuButton(MediaConversionMenu.BUTTON_TX_NEXT))
                .bounds(leftPos + 132, y2, 72, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        boolean configure = isConfigureSection();
        if (rxPrevious != null) rxPrevious.visible = configure;
        if (rxNext != null) rxNext.visible = configure;
        if (txPrevious != null) txPrevious.visible = configure;
        if (txNext != null) txNext.visible = configure;
    }

    @Override
    protected void renderSection(GuiGraphics g, Section section) {
        switch (section) {
            case OVERVIEW -> overview(g);
            case PORTS -> ports(g);
            case CONFIGURE -> configure(g);
            case DIAGNOSTICS -> diagnostics(g);
            case HISTORY -> history(g);
        }
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, modeTitle(), modeColor(), 16, 80);
        statusBadge(g, commissioningLabel(), commissioningColor(), 211, 80);
        metricCard(g, "Input", inputText(), 16, 103, 88, qualityColor(menu.inputQuality()));
        metricCard(g, "Output", outputText(), 111, 103, 88, qualityColor(menu.outputQuality()));
        metricCard(g, "Boundary", boundaryMetric(), 206, 103, 88, INFO);
        labelValue(g, "RX face", menu.inputFace().getName().toUpperCase(), 149);
        labelValue(g, "TX face", menu.outputFace().getName().toUpperCase(), 167);
        labelValue(g, "Evidence", menu.inputQuality().name() + " → " + menu.outputQuality().name(), 185);
        safeText(g, identityText(), 16, 207, INFO);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "PHYSICAL CONVERSION PORTS", INFO, 16, 80);
        statusLine(g, "RX • " + menu.inputFace().getName().toUpperCase(), inputPortText(), qualityColor(menu.inputQuality()), 108);
        statusLine(g, "TX • " + menu.outputFace().getName().toUpperCase(), outputPortText(), qualityColor(menu.outputQuality()), 136);
        safeText(g, "RX and TX are independently routed horizontal endpoints; the backend reads and drives these exact faces.", 16, 170, TEXT);
        safeText(g, "The HMI never permits RX and TX to occupy the same physical face.", 16, 192, MUTED);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "INDEPENDENT RX / TX ROUTING", INFO, 16, 80);
        labelValue(g, "Conversion", menu.redstoneToLapis() ? "0..15 → 0..100" : "0..100 → 0..15", 100);
        labelValue(g, "RX / TX", menu.inputFace().getName().toUpperCase() + " / " + menu.outputFace().getName().toUpperCase(), 184);
        safeText(g, "Route changes are server-authoritative; output relocation clears/notifies the old physical endpoint.", 16, 206, TEXT);
        safeText(g, "The conversion law itself remains fixed and deterministic.", 16, 226, MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, commissioningLabel(), commissioningColor(), 16, 80);
        statusLine(g, "Input evidence", menu.inputQuality().name(), qualityColor(menu.inputQuality()), 108);
        statusLine(g, "Output evidence", menu.outputQuality().name(), qualityColor(menu.outputQuality()), 128);
        labelValue(g, "RX → TX", menu.inputFace().getName().toUpperCase() + " → " + menu.outputFace().getName().toUpperCase(), 150);
        labelValue(g, "Input / output", inputText() + " → " + outputText(), 170);
        if (menu.redstoneToLapis()) {
            labelValue(g, "Source code spacing", formatNormalized(menu.sourceSpacing()), 190);
            safeText(g, "DIAGNOSIS • UPSCALED REPRESENTATION — NO NEW SOURCE PRECISION", 16, 212, diagnosisColor());
        } else {
            labelValue(g, "Reconstructed input", formatNormalized(menu.reconstructedLapis()), 190);
            labelValue(g, "Quantization loss", formatNormalized(menu.quantizationLoss()), 210);
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "COMMISSIONING EVIDENCE", commissioningColor(), 16, 80);
        safeText(g, "SERVER-SYNCHRONIZED OBSERVER • values come from the converter's existing EngineeringPort snapshots.", 16, 108, TEXT);
        safeText(g, "The client does not resample Redstone/Lapis networks and does not run a second conversion model.", 16, 130, TEXT);
        labelValue(g, "Medium boundary", menu.redstoneToLapis() ? "REDSTONE → LAPIS" : "LAPIS → REDSTONE", 162);
        labelValue(g, "Physical route", menu.inputFace().getName().toUpperCase() + " → " + menu.outputFace().getName().toUpperCase(), 182);
        labelValue(g, "Commissioning", commissioningLabel(), 202);
        safeText(g, commissioningMeaning(), 16, 224, commissioningColor());
    }

    private String modeTitle() {
        if (menu.redstoneToLapis()) return "REDSTONE → LAPIS SCALER";
        if (menu.lapisToRedstone()) return "LAPIS → REDSTONE QUANTIZER";
        return "MEDIA CONVERSION • UNKNOWN";
    }

    private int modeColor() { return menu.mode() == MediaConversionMenu.MODE_UNKNOWN ? BAD : INFO; }
    private String inputText() { return menu.redstoneToLapis() ? menu.inputValue() + "/15" : formatNormalized(menu.inputValue()); }
    private String outputText() { return menu.redstoneToLapis() ? formatNormalized(menu.outputValue()) : menu.outputValue() + "/15"; }
    private String boundaryMetric() {
        if (menu.redstoneToLapis()) return menu.sourceSpacing() < 0 ? "—" : "Δ≈" + formatNormalized(menu.sourceSpacing());
        return menu.quantizationLoss() < 0 ? "—" : "loss " + formatNormalized(menu.quantizationLoss());
    }
    private String inputPortText() { return menu.redstoneToLapis() ? "INPUT • REDSTONE • 0..15" : "INPUT • LAPIS • 0..100"; }
    private String outputPortText() { return menu.redstoneToLapis() ? "OUTPUT • LAPIS • 0..100" : "OUTPUT • REDSTONE • 0..15"; }
    private String identityText() {
        return menu.redstoneToLapis()
                ? "Lapis preserves a finer representation, but scaling cannot create information absent from 0..15 Redstone."
                : "Quantization intentionally compresses Lapis precision into the vanilla 0..15 Redstone boundary.";
    }
    private String commissioningLabel() { return menu.commissioningStatus().name().replace('_', ' '); }
    private int commissioningColor() {
        return switch (menu.commissioningStatus()) {
            case PASS -> GOOD;
            case MARGINAL, NOT_READY -> WARN;
            case FAIL -> BAD;
        };
    }
    private String commissioningMeaning() {
        return switch (menu.commissioningStatus()) {
            case PASS -> "PASS • both routed endpoints expose trustworthy evidence across the declared media boundary.";
            case MARGINAL -> "MARGINAL • conversion exists, but saturated evidence limits trustworthy interpretation.";
            case FAIL -> "FAIL • topology/domain/fault evidence invalidates the conversion boundary.";
            case NOT_READY -> "NOT READY • fresh connected evidence is required on both routed endpoints.";
        };
    }
    private int diagnosisColor() { return commissioningColor(); }
    private static int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> GOOD;
            case NO_SIGNAL, STALE, SATURATED -> WARN;
            default -> BAD;
        };
    }
    private static String formatNormalized(int hundredths) {
        return hundredths < 0 ? "—" : String.format(java.util.Locale.ROOT, "%.2f", hundredths / 100.0);
    }
}
