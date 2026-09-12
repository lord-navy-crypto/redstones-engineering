package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Read-only companion visualization for the dedicated EngineeringScreen Ports page.
 *
 * <p>The overlay consumes only synchronized EngineeringDeviceMenu state. It never samples the world,
 * solves topology, mutates block state, or invents ports. Keeping it on Ports prevents physical-I/O
 * detail from crowding Overview, Configure, Observe, and Log.</p>
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
    private static final int BAD = 0xFFF06A6A;

    private EngineeringIoCompassOverlay() {
    }

    public static void render(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof EngineeringScreen<?> engineeringScreen)) return;
        if (!engineeringScreen.showsPortVisualization()) return;
        if (!(engineeringScreen instanceof AbstractContainerScreen<?> containerScreen)) return;
        if (!(containerScreen.getMenu() instanceof EngineeringDeviceMenu menu)) return;

        int panelWidth = 112;
        int panelHeight = 176;
        int gap = 6;
        int margin = 4;
        int rightX = containerScreen.getGuiLeft() + containerScreen.getXSize() + gap;
        int leftX = containerScreen.getGuiLeft() - panelWidth - gap;
        boolean rightFits = rightX + panelWidth <= screen.width - margin;
        boolean leftFits = leftX >= margin;

        // Fail safe on narrow GUI scales: never cover the authoritative main Engineering panel.
        if (!rightFits && !leftFits) return;
        int x = rightFits ? rightX : leftX;
        int y = Math.max(margin, containerScreen.getGuiTop() + 58);
        if (y + panelHeight > screen.height - margin) {
            y = Math.max(margin, screen.height - margin - panelHeight);
        }

        GuiGraphics g = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        g.fill(x, y, x + panelWidth, y + panelHeight, BORDER);
        g.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, PANEL);
        g.fill(x + 5, y + 5, x + panelWidth - 5, y + 23, PANEL_2);
        g.drawString(font, "I/O COMPASS", x + 9, y + 10, TEXT, false);

        String topology = topologyHint(menu);
        g.drawString(font, fit(font, topology, panelWidth - 14), x + 7, y + 29, topologyColor(menu), false);
        g.drawString(font, fit(font, compactRole(menu.topologyRoleLabel()), panelWidth - 14), x + 7, y + 40, MUTED, false);

        String rxMedium = mediumLabel(menu, true);
        String txMedium = mediumLabel(menu, false);
        int linkedMask = connectionMask(menu);
        boolean linkEvidenceKnown = linkedMask >= 0;
        int effectiveLinkedMask = Math.max(0, linkedMask);
        drawCompass(g, font, x + 8, y + 56, "RX", menu.receivePortMask(), effectiveLinkedMask,
                RX, isFreeSpace(rxMedium), linkEvidenceKnown);
        drawCompass(g, font, x + 60, y + 56, "TX", menu.transmitPortMask(), effectiveLinkedMask,
                TX, isFreeSpace(txMedium), linkEvidenceKnown);

        drawMediumRow(g, font, x + 7, y + 119, "RX", rxMedium, menu.receivePortMask(),
                effectiveLinkedMask, linkEvidenceKnown);
        drawMediumRow(g, font, x + 7, y + 131, "TX", txMedium, menu.transmitPortMask(),
                effectiveLinkedMask, linkEvidenceKnown);

        g.fill(x + 7, y + 145, x + panelWidth - 7, y + 146, BORDER);
        g.drawString(font, "EVIDENCE", x + 7, y + 151, MUTED, false);
        String evidence = fit(font, menu.evidenceStateLabel(), 50);
        g.drawString(font, evidence, x + panelWidth - 7 - font.width(evidence), y + 151,
                evidenceColor(menu), false);
        g.drawString(font, "HEALTH", x + 7, y + 162, MUTED, false);
        String health = fit(font, menu.operationalHealthLabel(), 54);
        g.drawString(font, health, x + panelWidth - 7 - font.width(health), y + 162,
                healthColor(menu), false);
    }

    private static void drawCompass(GuiGraphics g, Font font, int x, int y,
                                    String heading, int declaredMask, int linkedMask,
                                    int activeColor, boolean propagationInterface,
                                    boolean linkEvidenceKnown) {
        g.drawString(font, heading, x + 12, y, activeColor, false);
        faceCell(g, font, x + 14, y + 12, Direction.NORTH, "N", declaredMask, linkedMask,
                activeColor, propagationInterface, linkEvidenceKnown);
        faceCell(g, font, x + 14, y + 36, Direction.SOUTH, "S", declaredMask, linkedMask,
                activeColor, propagationInterface, linkEvidenceKnown);
        faceCell(g, font, x + 2, y + 24, Direction.WEST, "W", declaredMask, linkedMask,
                activeColor, propagationInterface, linkEvidenceKnown);
        faceCell(g, font, x + 26, y + 24, Direction.EAST, "E", declaredMask, linkedMask,
                activeColor, propagationInterface, linkEvidenceKnown);
        faceCell(g, font, x + 2, y + 48, Direction.UP, "U", declaredMask, linkedMask,
                activeColor, propagationInterface, linkEvidenceKnown);
        faceCell(g, font, x + 26, y + 48, Direction.DOWN, "D", declaredMask, linkedMask,
                activeColor, propagationInterface, linkEvidenceKnown);

        int cx = x + 18;
        int cy = y + 28;
        g.fill(cx, cy, cx + 8, cy + 8, PANEL_2);
        g.fill(cx + 3, cy + 3, cx + 5, cy + 5, activeColor);
    }

    private static void faceCell(GuiGraphics g, Font font, int x, int y,
                                 Direction direction, String label, int declaredMask, int linkedMask,
                                 int activeColor, boolean propagationInterface,
                                 boolean linkEvidenceKnown) {
        int bit = 1 << direction.ordinal();
        boolean declared = (declaredMask & bit) != 0;
        boolean linked = (linkedMask & bit) != 0;
        int color = !declared ? BORDER
                : propagationInterface || !linkEvidenceKnown || linked ? activeColor : MUTED;
        g.fill(x, y, x + 10, y + 10, PANEL_2);
        g.fill(x, y + 8, x + 10, y + 10, color);
        if (declared && linked && !propagationInterface && linkEvidenceKnown) {
            g.fill(x + 1, y + 1, x + 3, y + 3, TX);
        }
        g.drawString(font, label, x + 2, y + 1, color, false);
    }

    private static void drawMediumRow(GuiGraphics g, Font font, int x, int y,
                                      String endpoint, String medium, int declaredMask, int linkedMask,
                                      boolean linkEvidenceKnown) {
        g.drawString(font, endpoint, x, y, MUTED, false);
        String mediumText = medium.isEmpty() ? "DOMAIN" : medium;
        int mediumColor = isFreeSpace(mediumText) ? RX : TEXT;
        g.drawString(font, mediumText, x + 18, y, mediumColor, false);

        String state = interfaceState(mediumText, declaredMask, linkedMask, linkEvidenceKnown);
        int stateColor = switch (state) {
            case "LINKED", "AIR PATH", "LOS PATH" -> TX;
            case "OPEN" -> WARN;
            case "NONE", "DECLARED" -> MUTED;
            default -> TEXT;
        };
        g.drawString(font, fit(font, state, 46), x + 58, y, stateColor, false);
    }

    private static String interfaceState(String medium, int declaredMask, int linkedMask,
                                         boolean linkEvidenceKnown) {
        if (declaredMask == 0) return "NONE";
        if ("RF".equals(medium)) return "AIR PATH";
        if ("LOS".equals(medium)) return "LOS PATH";
        if (!linkEvidenceKnown) return "DECLARED";
        return (declaredMask & linkedMask) != 0 ? "LINKED" : "OPEN";
    }

    private static int connectionMask(EngineeringDeviceMenu menu) {
        return menu instanceof FieldDeviceMenu fieldMenu ? fieldMenu.connectionMask() : -1;
    }

    private static String mediumLabel(EngineeringDeviceMenu menu, boolean receiving) {
        if (!(menu instanceof FieldDeviceMenu fieldMenu)) return "";
        return switch (fieldMenu.kind()) {
            case FieldDeviceMenu.KIND_RADIO_TRANSMITTER -> receiving ? "WIRE" : "RF";
            case FieldDeviceMenu.KIND_RADIO_RECEIVER -> receiving ? "RF" : "WIRE";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_TRANSMITTER -> receiving ? "WIRE" : "LOS";
            case FieldDeviceMenu.KIND_FREE_OPTICAL_RECEIVER -> receiving ? "LOS" : "WIRE";
            case FieldDeviceMenu.KIND_OPTICAL_FIBER,
                 FieldDeviceMenu.KIND_OPTICAL_EMITTER,
                 FieldDeviceMenu.KIND_OPTICAL_RECEIVER,
                 FieldDeviceMenu.KIND_OPTICAL_POWER_METER,
                 FieldDeviceMenu.KIND_OPTICAL_SPLITTER,
                 FieldDeviceMenu.KIND_OPTICAL_CHANNEL_FILTER,
                 FieldDeviceMenu.KIND_OPTICAL_ATTENUATOR,
                 FieldDeviceMenu.KIND_OPTICAL_FIBER_JUNCTION -> "FIBER";
            default -> "WIRE";
        };
    }

    private static boolean isFreeSpace(String medium) {
        return "RF".equals(medium) || "LOS".equals(medium);
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
                    EngineeringDeviceMenu.EVIDENCE_TOPOLOGY_ERROR -> BAD;
            default -> MUTED;
        };
    }

    private static int healthColor(EngineeringDeviceMenu menu) {
        return switch (menu.operationalHealth()) {
            case EngineeringDeviceMenu.HEALTH_FAULT -> BAD;
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
