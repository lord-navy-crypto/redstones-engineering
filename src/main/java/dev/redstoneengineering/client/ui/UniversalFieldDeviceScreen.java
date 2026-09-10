package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Universal six-face engineering HMI backed only by synchronized server snapshots. */
public final class UniversalFieldDeviceScreen extends EngineeringScreen<UniversalFieldDeviceMenu> {
    private Button rotateLeft;
    private Button rotateRight;

    public UniversalFieldDeviceScreen(UniversalFieldDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 111;
        rotateLeft = addConfigureWidget(Button.builder(
                Component.literal("↺ Rotate I/O"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_ROTATE_LEFT)
        ).bounds(leftPos + 38, y, 112, 20).build());
        rotateRight = addConfigureWidget(Button.builder(
                Component.literal("Rotate I/O ↻"),
                button -> sendMenuButton(UniversalFieldDeviceMenu.BUTTON_ROTATE_RIGHT)
        ).bounds(leftPos + 170, y, 112, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (rotateLeft == null || rotateRight == null) return;
        boolean active = menu.rotatableSeriesAxis();
        rotateLeft.active = active;
        rotateRight.active = active;
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
        int declared = Integer.bitCount(menu.declaredPortMask());
        int attention = attentionCount();
        statusBadge(g, title.getString().toUpperCase(), attention == 0 ? GOOD : WARN, 16, 80);
        statusBadge(g, "UNIVERSAL HMI", INFO, 207, 80);
        labelValue(g, "Declared interfaces", Integer.toString(declared), 106);
        labelValue(g, "Input / output path", axisText(), 124);
        labelValue(g, "Attention ports", Integer.toString(attention), 142);
        sectionRule(g, 161);
        g.drawString(font, "Every displayed value and quality is synchronized from the logical server.", 16, 174, TEXT, false);
        g.drawString(font, "Use Ports for all six physical faces; Configure rotates supported series devices.", 16, 191, MUTED, false);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "ALL SIX PHYSICAL FACES", INFO, 16, 80);
        Direction[] order = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int y = 102;
        for (Direction side : order) {
            portLine(g, side, y);
            y += 18;
        }
    }

    private void portLine(GuiGraphics g, Direction side, int y) {
        String face = side.getName().toUpperCase();
        if (!menu.hasPort(side)) {
            statusLine(g, face, "NO DECLARED ENGINEERING PORT", MUTED, y);
            return;
        }
        String role = menu.isBidirectional(side) ? "BIDIR"
                : menu.isInput(side) ? "INPUT"
                : menu.isOutput(side) ? "OUTPUT" : "PASSIVE";
        PortQuality quality = menu.quality(side);
        String value = menu.value(side) + " [" + menu.minimum(side) + ".." + menu.maximum(side) + "]";
        String text = role + " • " + menu.domain(side).label() + " • " + menu.portKind(side).name()
                + " • " + value + " • " + quality.name();
        statusLine(g, face, text, qualityColor(quality), y);
    }

    private void configure(GuiGraphics g) {
        boolean rotatable = menu.rotatableSeriesAxis();
        statusBadge(g, rotatable ? "SERIES AXIS CONTROL" : "READ-ONLY CONFIGURATION", rotatable ? INFO : MUTED, 16, 80);
        labelValue(g, "Current axis", axisText(), 101);
        labelValue(g, "I/O rule", rotatable ? "INPUT = opposite(OUTPUT)" : "DEVICE-SPECIFIC", 158);
        g.drawString(font, rotatable
                        ? "Rotation changes the complete INPUT → PROCESS → OUTPUT axis on the server."
                        : "This device has no shared rotatable series axis; dedicated controls remain device-specific.",
                16, 178, TEXT, false);
        g.drawString(font, "No client-side physics or hidden port mutation is performed.", 16, 196, MUTED, false);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, attentionCount() == 0 ? "PORT EVIDENCE NOMINAL" : "PORT EVIDENCE ATTENTION",
                attentionCount() == 0 ? GOOD : WARN, 16, 80);
        int y = 104;
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            PortQuality quality = menu.quality(side);
            statusLine(g, side.getName().toUpperCase(), quality.name() + " • " + menu.domain(side).label(), qualityColor(quality), y);
            y += 18;
        }
        if (y == 104) {
            g.drawString(font, "No EngineeringPortProvider interfaces are declared by this block.", 16, 108, WARN, false);
        }
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "LIVE SNAPSHOT POLICY", INFO, 16, 80);
        g.drawString(font, "Universal HMI intentionally stores no client-local history.", 16, 108, TEXT, false);
        g.drawString(font, "Use dedicated analyzers/monitors when retained chronology is required.", 16, 128, INFO, false);
        g.drawString(font, "This prevents opening a UI from creating measurement evidence or changing simulation state.", 16, 150, MUTED, false);
    }

    private int attentionCount() {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (!menu.hasPort(side)) continue;
            PortQuality q = menu.quality(side);
            if (q != PortQuality.VALID) count++;
        }
        return count;
    }

    private String axisText() {
        int ordinal = menu.facingOrdinal();
        if (ordinal < 0 || ordinal >= Direction.values().length) return "NOT SERIES-ROTATABLE";
        Direction output = Direction.values()[ordinal];
        return output.getOpposite().getName().toUpperCase() + " → " + output.getName().toUpperCase();
    }

    private static int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> GOOD;
            case SATURATED, STALE, NO_SIGNAL -> WARN;
            case FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> BAD;
        };
    }
}
