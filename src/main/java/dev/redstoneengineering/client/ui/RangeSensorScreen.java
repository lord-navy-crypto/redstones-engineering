package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated HMI for Range Sensor commissioning, live evidence, and I/O orientation. */
public final class RangeSensorScreen extends EngineeringScreen<RangeSensorMenu> {
    private Button modePrevious;
    private Button modeNext;
    private Button rangePrevious;
    private Button rangeNext;
    private Button responsePrevious;
    private Button responseNext;

    public RangeSensorScreen(RangeSensorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + imageHeight - 116;
        modePrevious = addConfigureWidget(Button.builder(Component.literal("◀ Detect"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_MODE_PREVIOUS)).bounds(leftPos + 16, y, 70, 20).build());
        modeNext = addConfigureWidget(Button.builder(Component.literal("Detect ▶"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_MODE_NEXT)).bounds(leftPos + 234, y, 70, 20).build());

        rangePrevious = addConfigureWidget(Button.builder(Component.literal("◀ Range"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_RANGE_PREVIOUS)).bounds(leftPos + 16, y + 25, 70, 20).build());
        rangeNext = addConfigureWidget(Button.builder(Component.literal("Range ▶"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_RANGE_NEXT)).bounds(leftPos + 234, y + 25, 70, 20).build());

        responsePrevious = addConfigureWidget(Button.builder(Component.literal("◀ Response"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_RESPONSE_PREVIOUS)).bounds(leftPos + 16, y + 50, 82, 20).build());
        responseNext = addConfigureWidget(Button.builder(Component.literal("Response ▶"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_RESPONSE_NEXT)).bounds(leftPos + 222, y + 50, 82, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (modePrevious == null) return;
        modePrevious.setMessage(Component.literal("◀ " + detectModeName()));
        modeNext.setMessage(Component.literal(detectModeName() + " ▶"));
        rangePrevious.setMessage(Component.literal("◀ " + menu.configuredRange()));
        rangeNext.setMessage(Component.literal(menu.configuredRange() + " ▶"));
        responsePrevious.setMessage(Component.literal("◀ " + responseName()));
        responseNext.setMessage(Component.literal(responseName() + " ▶"));
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
        int evidenceColor = menu.evidenceValid() ? GOOD : WARN;
        statusBadge(g, menu.evidenceValid() ? "VALID SCAN" : "SCAN NOT COMPLETE", evidenceColor, 16, 80);
        statusBadge(g, scanStatusName(), evidenceColor, 205, 80);

        metricCard(g, "Distance", Integer.toString(menu.distance()), 16, 103, 88, INFO);
        metricCard(g, "Output", menu.output() + " / 15", 111, 103, 88, GOOD);
        metricCard(g, "Range", Integer.toString(menu.configuredRange()), 206, 103, 88, INFO);

        labelValue(g, "Detect mode", detectModeName(), 145);
        labelValue(g, "Response", responseName(), 161);
        labelValue(g, "Scan progress", menu.scannedCells() + " / " + menu.configuredRange(), 177);
        safeText(g,
                menu.distance() == 0 && menu.evidenceValid()
                        ? "0 is VALID evidence: the completed scan found no target."
                        : "Distance and output are server-authoritative retained scan evidence.",
                16, 198, menu.distance() == 0 && menu.evidenceValid() ? GOOD : MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "PHYSICAL / LOGICAL INTERFACES", GOOD, 16, 80);
        labelValue(g, "Sensing face", menu.sensingDirection().getName().toUpperCase(), 106);
        labelValue(g, "Interface", "FREE-SPACE RANGE APERTURE", 124);
        labelValue(g, "Output face", menu.outputDirection().getName().toUpperCase(), 150);
        labelValue(g, "Interface", "REDSTONE • 0..15 OUTPUT", 168);
        safeText(g, "The sensing aperture observes only; the opposite face is the electrical output.", 16, 196, INFO);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "FORMULA-FIRST SENSOR RESPONSE", INFO, 16, 80);
        formulaCard(g, responseEquation(), 105);
        variableRole(g, "MEASURED", "d", menu.distance() + "", "blocks", 134);
        variableRole(g, "ADJUSTABLE", "R", menu.configuredRange() + "", "blocks", 152);
        variableRole(g, "ADJUSTABLE", "mode", responseName(), "", 170);
        variableRole(g, "DERIVED", "y", menu.output() + " / 15", "Redstone", 188);
        variableRole(g, "EVIDENCE", "scan", scanStatusName() + " • " + menu.scannedCells() + "/" + menu.configuredRange(), "", 206);
        wrappedText(g, "A complete CLEAR scan with d=0 is valid evidence. Physical sensing/output direction remains owned by Route.", 16, 232, workspaceWidth() - 24, MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        int color = menu.evidenceValid() ? GOOD : WARN;
        statusBadge(g, scanStatusName(), color, 16, 80);
        labelValue(g, "Evidence", menu.evidenceValid() ? "VALID • SERVER RETAINED" : "INCOMPLETE / UNINITIALIZED", 108);
        labelValue(g, "Distance", Integer.toString(menu.distance()), 126);
        labelValue(g, "Cells scanned", menu.scannedCells() + " / " + menu.configuredRange(), 144);
        labelValue(g, "Redstone output", menu.output() + " / 15", 162);
        labelValue(g, "Direction", menu.sensingDirection().getName().toUpperCase() + " → "
                + menu.outputDirection().getName().toUpperCase(), 180);
        safeText(g, "Validity comes from ScanResult.complete(), never from distance > 0.", 16, 201, GOOD);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "RETAINED SCAN EVIDENCE", INFO, 16, 80);
        labelValue(g, "Latest status", scanStatusName(), 108);
        labelValue(g, "Latest distance", Integer.toString(menu.distance()), 126);
        labelValue(g, "Latest progress", menu.scannedCells() + " / " + menu.configuredRange(), 144);
        sectionRule(g, 166);
        safeText(g, "The sensor retains its latest authoritative scan, not a fabricated client history.", 16, 180, MUTED);
    }

    private String responseEquation() {
        int range = Math.max(1, menu.configuredRange());
        return switch (menu.responseMode()) {
            case 0 -> "y = (d ≤ 0) ? 0 : round(15 · (R - d + 1) / R)";
            case 1 -> "y = (d ≤ 0) ? 0 : round(15 · d / R)";
            case 2 -> "y = (d > 0 ∧ d ≤ max(1, floor(R/2))) ? 15 : 0";
            case 3 -> "y = (max(1,floor(R/3)) ≤ d ≤ max(low,floor(2R/3))) ? 15 : 0";
            default -> "y = 0";
        };
    }

    private String scanStatusName() {
        return switch (menu.scanStatusOrdinal()) {
            case 1 -> "TARGET";
            case 2 -> "CLEAR";
            case 3 -> "INCOMPLETE / UNLOADED";
            default -> "UNINITIALIZED";
        };
    }

    private String detectModeName() {
        return switch (menu.detectMode()) {
            case 0 -> "BLOCK";
            case 1 -> "ENTITY";
            default -> "ANY";
        };
    }

    private String responseName() {
        return switch (menu.responseMode()) {
            case 0 -> "PROXIMITY";
            case 1 -> "DISTANCE";
            case 2 -> "THRESHOLD";
            case 3 -> "WINDOW";
            default -> "PROXIMITY";
        };
    }
}
