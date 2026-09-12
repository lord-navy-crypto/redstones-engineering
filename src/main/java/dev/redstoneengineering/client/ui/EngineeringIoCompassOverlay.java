package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Read-only companion visualization for EngineeringScreen instances.
 *
 * <p>The overlay consumes only synchronized EngineeringDeviceMenu state. It never samples the world,
 * solves topology, mutates block state, or invents ports. Its purpose is to make physical I/O shape
 * legible at a glance without consuming the main device panel.</p>
 */
public final class EngineeringIoCompassOverlay {
    private static final int PANEL = 0xEE0B1015;
    private static final int PANEL_2 = 0xEE11171D;
    private static final int BORDER = 0xFF5E6D78;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF9BA8B3;
    private static final int RX = 0xFF9EC8FF;
    private static final int TX = 0xFF68D391;
    private static final int WARN = 0xFFF6C453;

    private EngineeringIoCompassOverlay() {
    }

    public static void render(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof EngineeringScreen<?> engineeringScreen)) return;
        if (!(engineeringScreen instanceof AbstractContainerScreen<?> containerScreen)) return;
        if (!(containerScreen.getMenu() instanceof EngineeringDeviceMenu menu)) return;

        int panelWidth = 104;
        int panelHeight = 142;
        int gap = 6;
        int rightX = containerScreen.getGuiLeft() + containerScreen.getXSize() + gap;
        int x = rightX + panelWidth <= screen.width - 4
                ? rightX
                : Math.max(4, containerScreen.getGuiLeft() - panelWidth - gap);
        int y = Math.max(4, containerScreen.getGuiTop() + 58);

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        g.fill(x, y, x + panelWidth, y + panelHeight, BORDER);
        g.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, PANEL);
        g.fill(x + 5, y + 5, x + panelWidth - 5, y + 23, PANEL_2);
        g.drawString(font, "I/O COMPASS", x + 9, y + 10, TEXT, false);

        String topology = topologyHint(menu);
        g.drawString(font, topology, x + 7, y + 29, topologyColor(menu), false);
        g.drawString(font, compactRole(menu.topologyRoleLabel()), x + 7, y + 40, MUTED, false);

        drawCompass(g, font, x + 8, y + 54, "RX", menu.receivePortMask(), RX);
        drawCompass(g, font, x + 56, y + 54, "TX", menu.transmitPortMask(), TX);

        g.fill(x + 7, y + 113, x + panelWidth - 7, y + 114, BORDER);
        g.drawString(font, "EVIDENCE", x + 7, y + 119, MUTED, false);
        String evidence = fit(font, menu.evidenceStateLabel(), 48);
        g.drawString(font, evidence, x + panelWidth - 7 - font.width(evidence), y + 119,
                evidenceColor(menu), false);
        g.drawString(font, "HEALTH", x + 7, y + 130, MUTED, false);
        String health = fit(font, menu.operationalHealthLabel(), 52);
        g.drawString(font, health, x + panelWidth - 7 - font.width(health), y + 130,
                healthColor(menu), false);
    }

    private static void drawCompass(GuiGraphics g, Font font, int x, int y,
                                    String heading, int mask, int activeColor) {
        g.drawString(font, heading, x + 12, y, activeColor, false);
        faceCell(g, font, x + 14, y + 12, Direction.NORTH, "N", mask, activeColor);
        faceCell(g, font, x + 14, y + 36, Direction.SOUTH, "S", mask, activeColor);
        faceCell(g, font, x + 2, y + 24, Direction.WEST, "W", mask, activeColor);
        faceCell(g, font, x + 26, y + 24, Direction.EAST, "E", mask, activeColor);
        faceCell(g, font, x + 2, y + 48, Direction.UP, "U", mask, activeColor);
        faceCell(g, font, x + 26, y + 48, Direction.DOWN, "D", mask, activeColor);

        int cx = x + 18;
        int cy = y + 28;
        g.fill(cx, cy, cx + 8, cy + 8, PANEL_2);
        g.fill(cx + 3, cy + 3, cx + 5, cy + 5, activeColor);
    }

    private static void faceCell(GuiGraphics g, Font font, int x, int y,
                                 Direction direction, String label, int mask, int activeColor) {
        boolean active = (mask & (1 << direction.ordinal())) != 0;
        int color = active ? activeColor : BORDER;
        g.fill(x, y, x + 10, y + 10, PANEL_2);
        g.fill(x, y + 9, x + 10, y + 10, color);
        g.drawString(font, label, x + 2, y + 1, color, false);
    }

    private static String topologyHint(EngineeringDeviceMenu menu) {
        int rxCount = Integer.bitCount(menu.receivePortMask());
        int txCount = Integer.bitCount(menu.transmitPortMask());
        if (rxCount == 0 && txCount == 0) return "NO FORMAL I/O";
        if (rxCount == 0) return txCount > 1 ? "SOURCE • FAN-OUT x" + txCount : "SOURCE • SINGLE TX";
        if (txCount == 0) return rxCount > 1 ? "SINK • MULTI RX" : "SINK • SINGLE RX";
        if (txCount > 1) return "BRANCH • " + rxCount + "→" + txCount;
        if (rxCount > 1) return "MERGE • " + rxCount + "→" + txCount;
        return "SERIES • 1→1";
    }

    private static String compactRole(String role) {
        if (role == null || role.isBlank()) return "UNCLASSIFIED";
        String upper = role.toUpperCase();
        return upper.length() <= 18 ? upper : upper.substring(0, 17) + "…";
    }

    private static int topologyColor(EngineeringDeviceMenu menu) {
        return menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_TOPOLOGY_ERROR ? WARN : TEXT;
    }

    private static int evidenceColor(EngineeringDeviceMenu menu) {
        return switch (menu.evidenceState()) {
            case EngineeringDeviceMenu.EVIDENCE_VALID -> TX;
            case EngineeringDeviceMenu.EVIDENCE_SATURATED,
                    EngineeringDeviceMenu.EVIDENCE_STALE -> WARN;
            case EngineeringDeviceMenu.EVIDENCE_FAULT,
                    EngineeringDeviceMenu.EVIDENCE_DOMAIN_MISMATCH,
                    EngineeringDeviceMenu.EVIDENCE_TOPOLOGY_ERROR -> 0xFFF06A6A;
            default -> MUTED;
        };
    }

    private static int healthColor(EngineeringDeviceMenu menu) {
        return switch (menu.operationalHealth()) {
            case EngineeringDeviceMenu.HEALTH_FAULT -> 0xFFF06A6A;
            case EngineeringDeviceMenu.HEALTH_DEGRADED,
                    EngineeringDeviceMenu.HEALTH_PROTECTIVE -> WARN;
            case EngineeringDeviceMenu.HEALTH_ACTIVE -> RX;
            default -> TX;
        };
    }

    private static String fit(Font font, String text, int width) {
        if (text == null || text.isBlank()) return "—";
        if (font.width(text) <= width) return text;
        String out = text;
        while (out.length() > 1 && font.width(out + "…") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }
}
