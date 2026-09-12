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
    private Button directionCycle;

    public RangeSensorScreen(RangeSensorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 104;
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

        directionCycle = addConfigureWidget(Button.builder(Component.literal("Direction • —"),
                b -> sendMenuButton(RangeSensorMenu.BUTTON_ROTATE_RIGHT)).bounds(leftPos + 104, y + 50, 112, 20).build());
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
        if (directionCycle != null) {
            directionCycle.setMessage(Component.literal(fitForWidth(
                    "Direction • " + menu.outputDirection().getName().toUpperCase(), 96)));
            directionCycle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Cycle the sensing/output axis clockwise on the server.")));
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
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        labelValue(g, "Detect", detectModeName(), 96);
        labelValue(g, "Range", Integer.toString(menu.configuredRange()), 121);
        labelValue(g, "Response", responseName(), 146);
        labelValue(g, "I/O axis", menu.sensingDirection().getName().toUpperCase() + " SENSE → "
                + menu.outputDirection().getName().toUpperCase() + " OUT", 171);
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
