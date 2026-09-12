package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared RSE engineering visual language.
 *
 * <p>The screen renders server-synchronized menu data and emits bounded menu-button intent only.
 * It never computes physics, samples sensors, solves topology, or mutates controller state locally.</p>
 *
 * <p>The shell deliberately looks like an engineering HMI rather than a vanilla inventory:
 * live/server ownership is always visible, navigation is separated from telemetry, and every
 * device gets a consistent coordinate/readback footer even when its device-specific panel is small.</p>
 *
 * <p>Observatory and Log are shared semantic surfaces across every engineering screen. Observatory
 * is observer-neutral live telemetry/topology health; Log is bounded retained evidence/events. The
 * enum names remain DIAGNOSTICS/HISTORY so existing device screens keep source compatibility while
 * players see one consistent engineering vocabulary.</p>
 */
public abstract class EngineeringScreen<M extends EngineeringDeviceMenu> extends AbstractContainerScreen<M> {
    protected enum Section {
        OVERVIEW("Overview", "Live engineering state"),
        PORTS("Ports", "Physical I/O contract"),
        CONFIGURE("Configure", "Bounded server-side controls"),
        DIAGNOSTICS("Observatory", "Observer-neutral signals, topology and health"),
        HISTORY("Log", "Bounded evidence and retained events");

        private final String label;
        private final String subtitle;

        Section(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }
    }

    protected static final int PANEL = 0xFF11171D;
    protected static final int PANEL_2 = 0xFF1B242C;
    protected static final int PANEL_3 = 0xFF0B1015;
    protected static final int BORDER = 0xFF5E6D78;
    protected static final int TEXT = 0xFFE8EDF2;
    protected static final int MUTED = 0xFF9BA8B3;
    protected static final int GOOD = 0xFF68D391;
    protected static final int WARN = 0xFFF6C453;
    protected static final int BAD = 0xFFF06A6A;
    protected static final int INFO = 0xFF9EC8FF;
    protected static final int ACCENT = 0xFFE05555;
    protected static final int WHITE_SIGN = 0xFFF3F5F7;

    private Section section = Section.OVERVIEW;
    private final List<AbstractWidget> configureWidgets = new ArrayList<>();
    private final List<Button> sectionButtons = new ArrayList<>();
    private Button sharedRotateCcw;
    private Button sharedRotateCw;

    protected EngineeringScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
        // Device content owns the upper 218 px. The route schematic and footer have dedicated space
        // below it, so dense Ports/Observatory pages cannot collide with the shared visualization.
        this.imageHeight = 270;
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        configureWidgets.clear();
        sectionButtons.clear();
        sharedRotateCcw = null;
        sharedRotateCw = null;

        int tabY = topPos + 31;
        int x = leftPos + 8;
        for (Section candidate : Section.values()) {
            Section target = candidate;
            Button tab = Button.builder(
                    Component.literal(candidate.label),
                    button -> setSection(target)
            ).bounds(x, tabY, 59, 20).build();
            sectionButtons.add(addRenderableWidget(tab));
            x += 61;
        }
        addDeviceWidgets();
        addSharedRouteControls();
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncSharedRouteControls();
    }

    protected void addDeviceWidgets() {
    }

    /** Refreshes client-only button labels from already synchronized menu data. */
    protected void syncDeviceWidgetLabels() {
    }

    protected final <T extends AbstractWidget> T addConfigureWidget(T widget) {
        configureWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    protected final void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void addSharedRouteControls() {
        if (!(menu instanceof FieldDeviceMenu)) return;
        int y = topPos + 198;
        sharedRotateCcw = addConfigureWidget(Button.builder(
                Component.literal("↺ Rotate route"),
                button -> sendMenuButton(FieldDeviceMenu.BUTTON_ROTATE_CCW)
        ).bounds(leftPos + 16, y, 136, 20).build());
        sharedRotateCw = addConfigureWidget(Button.builder(
                Component.literal("Rotate route ↻"),
                button -> sendMenuButton(FieldDeviceMenu.BUTTON_ROTATE_CW)
        ).bounds(leftPos + 168, y, 136, 20).build());
    }

    private void syncSharedRouteControls() {
        if (!(menu instanceof FieldDeviceMenu fieldMenu) || sharedRotateCcw == null || sharedRotateCw == null) return;
        boolean enabled = fieldMenu.seriesConfigurable();
        sharedRotateCcw.active = enabled;
        sharedRotateCw.active = enabled;
        sharedRotateCcw.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                enabled ? "Rotate the declared RX/TX route counter-clockwise on the server."
                        : "This device has no rotatable formal route.")));
        sharedRotateCw.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                enabled ? "Rotate the declared RX/TX route clockwise on the server."
                        : "This device has no rotatable formal route.")));
    }

    private void setSection(Section section) {
        this.section = section;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncSharedRouteControls();
    }

    private void updateWidgetVisibility() {
        boolean visible = section == Section.CONFIGURE;
        for (AbstractWidget widget : configureWidgets) widget.visible = visible;
        for (int i = 0; i < sectionButtons.size(); i++) {
            sectionButtons.get(i).active = Section.values()[i] != section;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncDeviceWidgetLabels();
        syncSharedRouteControls();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, PANEL);

        // Header and navigation are visually isolated from telemetry so every device reads like an HMI.
        graphics.fill(leftPos + 8, topPos + 27, leftPos + imageWidth - 8, topPos + 29, ACCENT);
        graphics.fill(leftPos + 8, topPos + 58, leftPos + imageWidth - 8, topPos + imageHeight - 52, PANEL_2);
        graphics.fill(leftPos + 8, topPos + imageHeight - 62, leftPos + imageWidth - 8, topPos + imageHeight - 29, PANEL_3);
        graphics.fill(leftPos + 8, topPos + imageHeight - 25, leftPos + imageWidth - 8, topPos + imageHeight - 9, PANEL_3);

        // Thin white equipment-identification rail: neutral across electrical/optical/data media.
        graphics.fill(leftPos + 8, topPos + 58, leftPos + 11, topPos + imageHeight - 66, WHITE_SIGN);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 9, TEXT, false);

        String live = "● LIVE / SERVER";
        graphics.drawString(font, live, imageWidth - 12 - font.width(live), 9, GOOD, false);
        String role = "ROLE • " + menu.topologyRoleLabel();
        graphics.drawString(font, role, 12, 19, INFO, false);
        String health = "HEALTH • " + menu.operationalHealthLabel();
        graphics.drawString(font, health, imageWidth - 12 - font.width(health), 19, operationalHealthColor(), false);

        graphics.drawString(font, section.label.toUpperCase(), 13, 62, TEXT, false);
        graphics.drawString(font, section.subtitle, 92, 62, MUTED, false);
        renderSection(graphics, section);

        renderPortRoute(graphics);

        String evidence = "EVIDENCE • " + menu.evidenceStateLabel();
        graphics.drawString(font, evidence, 13, imageHeight - 20, evidenceStateColor(), false);
        String position = "@ " + menu.blockPos().getX() + ", " + menu.blockPos().getY() + ", " + menu.blockPos().getZ();
        graphics.drawString(font, position, imageWidth - 13 - font.width(position), imageHeight - 20, MUTED, false);
    }

    /**
     * Shared route schematic sourced only from formal synchronized menu contracts.
     * RX and TX are physically separated, the center node states the device role, and the lower
     * rail exposes whether the route currently carries valid evidence or is open/degraded.
     */
    private void renderPortRoute(GuiGraphics graphics) {
        int top = imageHeight - 61;
        int nodeY = top + 10;
        int rxX = 16;
        int roleX = 113;
        int txX = 222;
        int nodeH = 19;

        String rxFaces = routeEndpointLabel(menu.receivePortFacesLabel(), true);
        String txFaces = routeEndpointLabel(menu.transmitPortFacesLabel(), false);
        String role = compactRole(menu.topologyRoleLabel());
        boolean rxPresent = menu.receivePortMask() != 0;
        boolean txPresent = menu.transmitPortMask() != 0;
        int routeColor = evidenceStateColor();

        graphics.drawString(font, "SIGNAL ROUTE", 16, top - 1, MUTED, false);
        String topology = routeTopologyHint(rxPresent, txPresent);
        graphics.drawString(font, topology, 91, top - 1, INFO, false);
        String state = menu.evidenceStateLabel();
        graphics.drawString(font, state, imageWidth - 16 - font.width(state), top - 1, routeColor, false);

        routeNode(graphics, rxX, nodeY, 82, nodeH, "RX", rxFaces, rxPresent ? INFO : MUTED);
        routeNode(graphics, roleX, nodeY, 94, nodeH, "ROLE", role, operationalHealthColor());
        routeNode(graphics, txX, nodeY, 82, nodeH, "TX", txFaces, txPresent ? GOOD : MUTED);

        drawRouteLink(graphics, rxX + 82, roleX, nodeY + 9, rxPresent, routeColor);
        drawRouteLink(graphics, roleX + 94, txX, nodeY + 9, txPresent, routeColor);

        if (!rxPresent && txPresent) {
            graphics.drawString(font, "SOURCE", 86, nodeY + 5, INFO, false);
        } else if (rxPresent && !txPresent) {
            graphics.drawString(font, "SINK", 208, nodeY + 5, INFO, false);
        }
    }

    private String routeEndpointLabel(String faces, boolean receiving) {
        String medium = routeMedium(receiving);
        if (medium.isEmpty() || !portPresent(faces)) return faces;
        return faces + " • " + medium;
    }

    /** Presentation-only medium tags for communication devices; no solver semantics are inferred here. */
    private String routeMedium(boolean receiving) {
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
            default -> "";
        };
    }

    private String routeTopologyHint(boolean rxPresent, boolean txPresent) {
        int txCount = Integer.bitCount(menu.transmitPortMask());
        if (!rxPresent && txPresent) return txCount > 1 ? "FAN-OUT ×" + txCount : "SINGLE TX";
        if (rxPresent && !txPresent) return "TERMINAL RX";
        if (rxPresent && txCount > 1) return "BRANCH ×" + txCount;
        if (rxPresent && txPresent) return "SERIES PATH";
        return "NO FORMAL PORT";
    }

    private void routeNode(GuiGraphics graphics, int x, int y, int width, int height,
                           String heading, String value, int color) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + 3, y + height, color);
        graphics.drawString(font, heading, x + 7, y + 3, MUTED, false);
        String compact = fitRouteText(value, width - 35);
        graphics.drawString(font, compact, x + width - 6 - font.width(compact), y + 3, color, false);
        graphics.fill(x + 7, y + height - 4, x + width - 7, y + height - 3, BORDER);
    }

    private void drawRouteLink(GuiGraphics graphics, int x0, int x1, int y, boolean present, int routeColor) {
        int color = present ? routeColor : BORDER;
        graphics.fill(x0 + 3, y, x1 - 4, y + 2, color);
        graphics.fill(x1 - 7, y - 2, x1 - 4, y + 4, color);
    }

    private boolean portPresent(String label) {
        if (label == null) return false;
        String normalized = label.trim().toUpperCase();
        return !normalized.isEmpty()
                && !normalized.equals("NONE")
                && !normalized.equals("—")
                && !normalized.equals("-")
                && !normalized.equals("N/A");
    }

    private String compactRole(String role) {
        if (role == null || role.isBlank()) return "DEVICE";
        String upper = role.toUpperCase();
        if (upper.length() <= 13) return upper;
        if (upper.contains("PROCESS")) return "PROCESSOR";
        if (upper.contains("CONVERT")) return "CONVERTER";
        if (upper.contains("SOURCE")) return "SOURCE";
        if (upper.contains("SINK")) return "SINK";
        if (upper.contains("OBSERV")) return "OBSERVER";
        if (upper.contains("PASSIVE")) return "PASSIVE";
        return upper.substring(0, 12) + "…";
    }

    private String fitRouteText(String text, int maxWidth) {
        if (text == null || text.isBlank()) return "—";
        if (font.width(text) <= maxWidth) return text;
        String compact = text;
        while (compact.length() > 1 && font.width(compact + "…") > maxWidth) {
            compact = compact.substring(0, compact.length() - 1);
        }
        return compact + "…";
    }

    protected final int operationalHealthColor() {
        return switch (menu.operationalHealth()) {
            case EngineeringDeviceMenu.HEALTH_FAULT -> BAD;
            case EngineeringDeviceMenu.HEALTH_DEGRADED, EngineeringDeviceMenu.HEALTH_PROTECTIVE -> WARN;
            case EngineeringDeviceMenu.HEALTH_ACTIVE -> INFO;
            default -> GOOD;
        };
    }

    protected final int evidenceStateColor() {
        return switch (menu.evidenceState()) {
            case EngineeringDeviceMenu.EVIDENCE_VALID -> GOOD;
            case EngineeringDeviceMenu.EVIDENCE_NO_SIGNAL, EngineeringDeviceMenu.EVIDENCE_UNOBSERVED -> MUTED;
            case EngineeringDeviceMenu.EVIDENCE_SATURATED, EngineeringDeviceMenu.EVIDENCE_STALE -> WARN;
            case EngineeringDeviceMenu.EVIDENCE_FAULT,
                    EngineeringDeviceMenu.EVIDENCE_DOMAIN_MISMATCH,
                    EngineeringDeviceMenu.EVIDENCE_TOPOLOGY_ERROR -> BAD;
            default -> MUTED;
        };
    }

    private boolean isOperationalHealthLine(String label) {
        return "Safety state".equals(label)
                || "Actuator".equals(label)
                || "Voting health".equals(label)
                || "Safety memory".equals(label)
                || "System state".equals(label);
    }

    private String authoritativeHealthValue(String label, String fallback) {
        return switch (label) {
            case "Safety state" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE
                    ? "TIMED OUT" : "HEALTHY";
            case "Actuator" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE
                    ? "BRAKED" : "ENABLED";
            case "Voting health" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_DEGRADED
                    ? "DEGRADED" : "OK";
            case "Safety memory" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_FAULT
                    ? "FAULT LATCHED" : "CLEAR";
            default -> fallback;
        };
    }

    private boolean isOperationalHealthBadge(String value) {
        return "HEARTBEAT WATCHDOG".equals(value)
                || "SERVO ACTUATOR".equals(value)
                || "2oo3 REDUNDANT VOTER".equals(value)
                || "FAULT LATCH".equals(value)
                || "OPERATIONS MONITOR • OBSERVER".equals(value)
                || "RELIEF ARMED".equals(value)
                || "VENTING".equals(value);
    }

    private String authoritativeHealthBadge(String value) {
        if ("RELIEF ARMED".equals(value) || "VENTING".equals(value)) {
            return menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE
                    ? "VENTING" : "RELIEF ARMED";
        }
        return value;
    }

    private record PresentationLine(String label, String value) {
    }

    /**
     * Keeps legacy device-specific screen strings aligned with the authoritative port contract.
     * This is presentation-only normalization; it never invents ports or mutates solver state.
     */
    private PresentationLine normalizeLegacyPresentation(String label, String value) {
        if ("PNEUMATIC • SIX-WAY REGULATED MANIFOLD".equals(value)) {
            return new PresentationLine(label, "PNEUMATIC • " + menu.portRouteLabel());
        }
        if ("OTHER FIVE FACES".equals(label) && "REDSTONE PAYLOAD INPUT".equals(value)) {
            return new PresentationLine("DOWN", "REDSTONE PAYLOAD INPUT");
        }
        if ("Topology role".equals(label) && "FIXED / SOURCE / SINK / OBSERVER / PASSIVE".equals(value)) {
            return new PresentationLine(label, menu.topologyRoleLabel());
        }
        return new PresentationLine(label, value);
    }

    protected final void labelValue(GuiGraphics graphics, String label, String value, int y) {
        PresentationLine normalized = normalizeLegacyPresentation(label, value);
        graphics.drawString(font, normalized.label(), 16, y, MUTED, false);
        graphics.drawString(font, normalized.value(), 154, y, TEXT, false);
    }

    protected final void statusLine(GuiGraphics graphics, String label, String value, int color, int y) {
        if (isOperationalHealthLine(label)) {
            value = authoritativeHealthValue(label, value);
            color = operationalHealthColor();
        }
        PresentationLine normalized = normalizeLegacyPresentation(label, value);
        graphics.drawString(font, normalized.label(), 16, y, MUTED, false);
        graphics.drawString(font, normalized.value(), 154, y, color, false);
    }

    protected final void statusBadge(GuiGraphics graphics, String value, int color, int x, int y) {
        if (isOperationalHealthBadge(value)) {
            value = authoritativeHealthBadge(value);
            color = operationalHealthColor();
        }
        int width = font.width(value) + 12;
        graphics.fill(x, y, x + width, y + 14, PANEL_3);
        graphics.fill(x, y, x + 3, y + 14, color);
        graphics.drawString(font, value, x + 7, y + 3, color, false);
    }

    /** Compact engineering metric card for richer device screens without adding client-side state. */
    protected final void metricCard(GuiGraphics graphics, String label, String value, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 31, PANEL_3);
        graphics.fill(x, y, x + 2, y + 31, color);
        graphics.drawString(font, label.toUpperCase(), x + 7, y + 5, MUTED, false);
        graphics.drawString(font, value, x + 7, y + 17, TEXT, false);
    }

    /** A shared state badge used by devices that expose validity/quality without inventing physics. */
    protected final void healthBadge(GuiGraphics graphics, String state, boolean healthy, int x, int y) {
        statusBadge(graphics, healthy ? "HEALTH • " + state : "ATTENTION • " + state,
                healthy ? GOOD : WARN, x, y);
    }

    protected final void sectionRule(GuiGraphics graphics, int y) {
        graphics.fill(16, y, imageWidth - 16, y + 1, 0xFF3A4650);
    }

    protected final void signalBar(GuiGraphics graphics, int value, int y) {
        int bounded = Math.max(0, Math.min(15, value));
        int x0 = 16;
        int x1 = 286;
        int interior = x1 - x0 - 2;
        int fillWidth = (bounded * interior) / 15;
        graphics.fill(x0, y, x1, y + 8, PANEL_3);
        if (fillWidth > 0) graphics.fill(x0 + 1, y + 1, x0 + 1 + fillWidth, y + 7, ACCENT);

        for (int tick = 0; tick <= 15; tick += 5) {
            int tickX = x0 + 1 + (tick * interior) / 15;
            graphics.fill(tickX, y + 6, tickX + 1, y + 9, BORDER);
        }
        graphics.drawString(font, "0", x0, y + 11, MUTED, false);
        graphics.drawString(font, "5", x0 + interior / 3 - 2, y + 11, MUTED, false);
        graphics.drawString(font, "10", x0 + (interior * 2) / 3 - 5, y + 11, MUTED, false);
        graphics.drawString(font, "15", x1 - 11, y + 11, MUTED, false);
        graphics.drawString(font, bounded + " / 15", 245, y - 10, TEXT, false);
    }

    protected abstract void renderSection(GuiGraphics graphics, Section section);
}
