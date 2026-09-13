package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.MediaConversionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated read-only HMI for the explicit Redstone ↔ Lapis representation boundary. */
public final class MediaConversionScreen extends EngineeringScreen<MediaConversionMenu> {
    public MediaConversionScreen(MediaConversionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
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
        labelValue(g, "Input face", menu.inputFace().getName().toUpperCase(), 149);
        labelValue(g, "Output face", menu.outputFace().getName().toUpperCase(), 167);
        labelValue(g, "Evidence", menu.inputQuality().name() + " → " + menu.outputQuality().name(), 185);
        safeText(g, identityText(), 16, 207, INFO);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "PHYSICAL CONVERSION PORTS", INFO, 16, 80);
        statusLine(g, menu.inputFace().getName().toUpperCase(), inputPortText(), qualityColor(menu.inputQuality()), 108);
        statusLine(g, menu.outputFace().getName().toUpperCase(), outputPortText(), qualityColor(menu.outputQuality()), 136);
        safeText(g, "The converter crosses an explicit media boundary; it does not silently reinterpret the same wire.", 16, 170, TEXT);
        safeText(g, "Current route remains the physical FRONT/BACK layout. Independent RX/TX routing is audited separately.", 16, 192, MUTED);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "FIXED CONVERSION LAW", INFO, 16, 80);
        labelValue(g, "Conversion", menu.redstoneToLapis() ? "0..15 → 0..100" : "0..100 → 0..15", 106);
        labelValue(g, "Input face", menu.inputFace().getName().toUpperCase(), 126);
        labelValue(g, "Output face", menu.outputFace().getName().toUpperCase(), 146);
        safeText(g, "No gain or hidden calibration knob is exposed: the conversion law is deterministic and server-owned.", 16, 174, TEXT);
        safeText(g, "Routing controls will be added only with the matching backend/blockstate endpoint refactor.", 16, 196, MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, commissioningLabel(), commissioningColor(), 16, 80);
        statusLine(g, "Input evidence", menu.inputQuality().name(), qualityColor(menu.inputQuality()), 108);
        statusLine(g, "Output evidence", menu.outputQuality().name(), qualityColor(menu.outputQuality()), 128);
        labelValue(g, "Input / output", inputText() + " → " + outputText(), 150);
        if (menu.redstoneToLapis()) {
            labelValue(g, "Source code spacing", formatNormalized(menu.sourceSpacing()), 170);
            safeText(g, "DIAGNOSIS • UPSCALED REPRESENTATION — NO NEW SOURCE PRECISION", 16, 194, diagnosisColor());
            safeText(g, redstoneToLapisNext(), 16, 214, diagnosisColor());
        } else {
            labelValue(g, "Reconstructed input", formatNormalized(menu.reconstructedLapis()), 170);
            labelValue(g, "Quantization loss", formatNormalized(menu.quantizationLoss()), 190);
            safeText(g, lapisToRedstoneNext(), 16, 214, diagnosisColor());
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "COMMISSIONING EVIDENCE", commissioningColor(), 16, 80);
        safeText(g, "SERVER-SYNCHRONIZED OBSERVER • values come from the converter's existing EngineeringPort snapshots.", 16, 108, TEXT);
        safeText(g, "The client does not resample Redstone/Lapis networks and does not run a second conversion model.", 16, 130, TEXT);
        labelValue(g, "Medium boundary", menu.redstoneToLapis() ? "REDSTONE → LAPIS" : "LAPIS → REDSTONE", 162);
        labelValue(g, "Commissioning", commissioningLabel(), 182);
        safeText(g, commissioningMeaning(), 16, 206, commissioningColor());
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
    private String redstoneToLapisNext() {
        if (menu.commissioningStatus() != CommissioningStatus.PASS) return genericNext();
        return "NEXT • treat the Lapis value as an upscaled code; do not infer sub-step source precision.";
    }
    private String lapisToRedstoneNext() {
        if (menu.commissioningStatus() != CommissioningStatus.PASS) return genericNext();
        return "NEXT • compare reconstructed input and quantization loss before using the Redstone output for thresholds.";
    }
    private String genericNext() {
        return switch (menu.commissioningStatus()) {
            case FAIL -> "NEXT • repair topology/domain/fault evidence before trusting the conversion boundary.";
            case NOT_READY -> "NEXT • restore fresh input/output evidence before commissioning the converter.";
            case MARGINAL -> "NEXT • resolve saturated evidence before treating the converted value as trustworthy.";
            case PASS -> "NEXT • conversion evidence is coherent.";
        };
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
            case PASS -> "PASS • both sides expose trustworthy evidence across the declared media boundary.";
            case MARGINAL -> "MARGINAL • conversion exists, but saturated evidence limits trustworthy interpretation.";
            case FAIL -> "FAIL • topology/domain/fault evidence invalidates the conversion boundary.";
            case NOT_READY -> "NOT READY • fresh connected evidence is required on both sides.";
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
