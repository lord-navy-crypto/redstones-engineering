package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.CopperCircuitMeterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated electrical HMI for the observer-only Copper Circuit Meter. */
public final class CopperCircuitMeterScreen extends EngineeringScreen<CopperCircuitMeterMenu> {
    public CopperCircuitMeterScreen(CopperCircuitMeterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        // Physical measurement-face routing lives exclusively on the shared Route page.
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
        statusBadge(g, "COPPER POWER / LOAD NETWORK", qualityColor(), 16, 80);
        statusBadge(g, commissioningLabel(), commissioningColor(), 211, 80);
        metricCard(g, "Voltage", String.format("%d V-eq", menu.voltage()), 16, 103, 88, qualityColor());
        metricCard(g, "Req", String.format("%.2f Ω-eq", menu.resistance()), 111, 103, 88, INFO);
        metricCard(g, "Current", String.format("%.3f I-eq", menu.current()), 206, 103, 88, INFO);
        labelValue(g, "Estimated power", String.format("%.2f P-eq", menu.power()), 149);
        labelValue(g, "Measurement face", menu.facing().getName().toUpperCase(), 167);
        labelValue(g, "Electrical state", stateLabel(), 185);
        labelValue(g, "Meter samples / age", menu.sampleCount() + " / " + menu.sampleAgeTicks() + " t", 203);
        safeText(g, "Copper is modeled as an electrical load network, not as a control-signal medium.", 16, 225, INFO);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "MEASUREMENT APERTURE", INFO, 16, 80);
        statusLine(g, menu.facing().getName().toUpperCase(), "INPUT • COPPER MEASUREMENT • OBSERVER ONLY", qualityColor(), 112);
        statusLine(g, "OTHER FACES", "NO DECLARED ELECTRICAL DRIVER PORT", MUTED, 142);
        safeText(g, "The meter samples the selected adjacent copper node without becoming a load or source.", 16, 176, TEXT);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "OBSERVER-ONLY INSTRUMENT", INFO, 16, 80);
        labelValue(g, "Current face", menu.facing().getName().toUpperCase(), 101);
        labelValue(g, "Electrical setpoint", "NONE", 121);
        safeText(g, "Physical measurement-face routing is controlled only on Route.", 16, 158, TEXT);
        safeText(g, "Configure is intentionally read-only: the meter never drives or loads the Copper network.", 16, 180, MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        statusBadge(g, commissioningLabel(), commissioningColor(), 211, 80);
        labelValue(g, "Electrical state", stateLabel(), 104);
        labelValue(g, "V / Req", String.format("%d / %.2f", menu.voltage(), menu.resistance()), 124);
        labelValue(g, "I / P", String.format("%.3f / %.2f", menu.current(), menu.power()), 144);
        labelValue(g, "Meter quality", measurementQualityName(), 164);
        labelValue(g, "Reading / uncertainty", meterReadingSummary(), 184);
        labelValue(g, "Samples / age", menu.sampleCount() + " / " + menu.sampleAgeTicks() + " t", 204);
        labelValue(g, "Authority", "SERVER-SYNCHRONIZED OBSERVER", 224);
        safeText(g, diagnosis(), 16, 248, diagnosisColor());
        safeText(g, nextAction(), 16, 268, diagnosisColor());
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "METROLOGY + COMMISSIONING EVIDENCE", commissioningColor(), 16, 80);
        safeText(g, "V, Req, I and P come from the server electrical model; the statistics below come from the retained server metrology tracker.", 16, 108, TEXT);
        labelValue(g, "Measurement quality", measurementQualityName(), 142);
        labelValue(g, "Conditioned reading", menu.sampleCount() > 0 ? String.format("%.2f V-eq", menu.meterReading()) : "NO DATA", 162);
        labelValue(g, "Repeatability", menu.sampleCount() > 0 ? String.format("±%.2f", menu.repeatability()) : "N/A", 182);
        labelValue(g, "Bias / drift", menu.sampleCount() > 0 ? String.format("%+.2f / %+.2f", menu.bias(), menu.drift()) : "N/A", 202);
        labelValue(g, "Noise", menu.sampleCount() > 0 ? String.format("%.2f", menu.noise()) : "N/A", 222);
        labelValue(g, "Uncertainty proxy", menu.sampleCount() > 0 ? String.format("±%.2f", menu.uncertaintyProxy()) : "N/A", 242);
        labelValue(g, "Sample count / age", menu.sampleCount() + " / " + menu.sampleAgeTicks() + " t", 262);
        labelValue(g, "Saturation", menu.saturated() ? "YES" : "NO", 282);
        labelValue(g, "Medium identity", "COPPER • POWER / LOAD", 302);
        labelValue(g, "Commissioning", commissioningLabel(), 322);
        safeText(g, commissioningMeaning(), 16, 344, commissioningColor());
        safeText(g, "Repeatability, bias, drift, noise and uncertainty are diagnostic metrology proxies; they are not client-side circuit calculations.", 16, 366, MUTED);
    }

    private String qualityName() { return menu.quality().name().replace('_', ' '); }
    private String measurementQualityName() { return menu.measurementQuality().name().replace('_', ' '); }
    private String meterReadingSummary() {
        if (menu.sampleCount() <= 0) return "NO DATA";
        return String.format("%.2f / ±%.2f V-eq", menu.meterReading(), menu.uncertaintyProxy());
    }
    private int qualityColor() {
        return switch (menu.quality()) {
            case VALID -> GOOD;
            case NO_SIGNAL, STALE, SATURATED -> WARN;
            default -> BAD;
        };
    }
    private String stateLabel() {
        if (menu.quality() != PortQuality.VALID) return "EVIDENCE INVALID";
        return menu.energized() ? "ENERGIZED" : "DE-ENERGIZED";
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
            case PASS -> "PASS • valid energized electrical evidence is available for load-network commissioning.";
            case MARGINAL -> "MARGINAL • evidence exists, but the network is de-energized or the measurement is saturated.";
            case FAIL -> "FAIL • topology/domain/fault evidence prevents trustworthy electrical commissioning.";
            case NOT_READY -> "NOT READY • acquire a fresh connected copper measurement before commissioning.";
        };
    }
    private String diagnosis() {
        return switch (menu.quality()) {
            case VALID -> menu.energized()
                    ? (menu.resistance() <= 2.0 ? "DIAGNOSIS • HEAVY LOAD / HIGH CURRENT DEMAND" : "DIAGNOSIS • ENERGIZED LOAD NETWORK")
                    : "DIAGNOSIS • VALID DE-ENERGIZED NETWORK";
            case TOPOLOGY_ERROR -> "DIAGNOSIS • COPPER TOPOLOGY INVALID";
            case STALE -> "DIAGNOSIS • ELECTRICAL EVIDENCE STALE";
            case NO_SIGNAL -> "DIAGNOSIS • NO COPPER SOURCE EVIDENCE";
            case DOMAIN_MISMATCH -> "DIAGNOSIS • WRONG DOMAIN AT MEASUREMENT FACE";
            default -> "DIAGNOSIS • ELECTRICAL EVIDENCE NOT TRUSTWORTHY";
        };
    }
    private String nextAction() {
        return switch (menu.quality()) {
            case VALID -> menu.energized()
                    ? "NEXT • compare V, Req, I and P before changing source or load configuration."
                    : "NEXT • verify source availability before diagnosing downstream load behavior.";
            case TOPOLOGY_ERROR -> "NEXT • repair copper cable/junction topology, then reacquire the measurement.";
            case STALE -> "NEXT • restore the measured node and wait for a fresh server sample.";
            case NO_SIGNAL -> "NEXT • connect the measurement face to an energized copper network.";
            case DOMAIN_MISMATCH -> "NEXT • point the meter at a copper-domain node.";
            default -> "NEXT • restore valid electrical evidence before using V/I/P for decisions.";
        };
    }
    private int diagnosisColor() { return menu.quality() == PortQuality.VALID ? INFO : qualityColor(); }
}
