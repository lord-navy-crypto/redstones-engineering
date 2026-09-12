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
 * <p>The shell is deliberately conservative: pages own their full content area, long text is
 * pixel-clamped, and configuration controls live in reserved lanes. The six-face physical route
 * visualization is delegated to the read-only I/O Compass on Ports so the main panel never has two
 * independent diagrams competing for the same pixels.</p>
 */
public abstract class EngineeringScreen<M extends EngineeringDeviceMenu> extends AbstractContainerScreen<M> {
    protected enum Section {
        OVERVIEW("Overview", "Live engineering state"),
        PORTS("Ports", "Physical I/O contract"),
        CONFIGURE("Configure", "Bounded server-side controls"),
        DIAGNOSTICS("Observe", "Observer-neutral signals, topology and health"),
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

    private static final int CONTENT_LEFT = 16;
    private static final int CONTENT_RIGHT = 304;
    private static final int VALUE_X = 154;
    private static final int FOOTER_TOP = 245;
    private static final int ROUTE_CONTROL_Y = 218;

    private Section section = Section.OVERVIEW;
    private final List<AbstractWidget> configureWidgets = new ArrayList<>();
    private final List<Button> sectionButtons = new ArrayList<>();
    private Button sharedRouteCycle;

    protected EngineeringScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 320;
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
        sharedRouteCycle = null;

        int tabY = topPos + 31;
        int x = leftPos + 8;
        for (Section candidate : Section.values()) {
            Section target = candidate;
            Button tab = Button.builder(Component.literal(candidate.label), button -> setSection(target))
                    .bounds(x, tabY, 59, 20).build();
            sectionButtons.add(addRenderableWidget(tab));
            x += 61;
        }

        addDeviceWidgets();
        addSharedRouteControl();
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncSharedRouteControl();
    }

    protected void addDeviceWidgets() {
    }

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

    /** Single route control in a reserved bottom lane; no other shared control may occupy this band. */
    private void addSharedRouteControl() {
        if (!(menu instanceof FieldDeviceMenu)) return;
        int y = topPos + ROUTE_CONTROL_Y;
        sharedRouteCycle = addConfigureWidget(Button.builder(
                Component.literal("Direction • —"),
                button -> sendMenuButton(FieldDeviceMenu.BUTTON_ROTATE_CW)
        ).bounds(leftPos + CONTENT_LEFT, y, CONTENT_RIGHT - CONTENT_LEFT, 20).build());
    }

    private void syncSharedRouteControl() {
        if (!(menu instanceof FieldDeviceMenu fieldMenu) || sharedRouteCycle == null) return;
        boolean enabled = fieldMenu.seriesConfigurable();
        String route = fieldMenu.portRouteLabel();
        if (route == null || route.isBlank()) route = "NO ROTATABLE ROUTE";
        String label = fitForWidth("Direction • " + route, CONTENT_RIGHT - CONTENT_LEFT - 16);
        sharedRouteCycle.setMessage(Component.literal(label));
        sharedRouteCycle.active = enabled;
        sharedRouteCycle.visible = section == Section.CONFIGURE && enabled;
        sharedRouteCycle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Cycle the declared RX/TX route clockwise on the server.")));
    }

    private void setSection(Section section) {
        this.section = section;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncSharedRouteControl();
    }

    private boolean isLegacyRouteWidget(AbstractWidget widget) {
        if (!(widget instanceof Button button)) return false;
        String message = button.getMessage().getString();
        return message.contains("Rotate I/O") || message.contains("Rotate route");
    }

    private void updateWidgetVisibility() {
        boolean visible = section == Section.CONFIGURE;
        for (AbstractWidget widget : configureWidgets) {
            widget.visible = visible && !isLegacyRouteWidget(widget);
        }
        for (int i = 0; i < sectionButtons.size(); i++) {
            sectionButtons.get(i).active = Section.values()[i] != section;
        }
    }

    /** The sidecar I/O compass is the only dense route diagram and only appears on Ports. */
    public final boolean showsPortVisualization() {
        return section == Section.PORTS;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncDeviceWidgetLabels();
        syncSharedRouteControl();
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
        graphics.fill(leftPos + 8, topPos + 27, leftPos + imageWidth - 8, topPos + 29, ACCENT);

        // Every page owns the full content panel. No duplicated route schematic consumes the lower third.
        graphics.fill(leftPos + 8, topPos + 58, leftPos + imageWidth - 8, topPos + FOOTER_TOP - 4, PANEL_2);
        graphics.fill(leftPos + 8, topPos + FOOTER_TOP, leftPos + imageWidth - 8, topPos + imageHeight - 9, PANEL_3);
        graphics.fill(leftPos + 8, topPos + 58, leftPos + 11, topPos + FOOTER_TOP - 8, WHITE_SIGN);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String live = "● LIVE / SERVER";
        graphics.drawString(font, fitForWidth(title.getString(), 170), 12, 9, TEXT, false);
        graphics.drawString(font, live, imageWidth - 12 - font.width(live), 9, GOOD, false);

        String role = fitForWidth("ROLE • " + menu.topologyRoleLabel(), 145);
        String health = fitForWidth("HEALTH • " + menu.operationalHealthLabel(), 145);
        graphics.drawString(font, role, 12, 19, INFO, false);
        graphics.drawString(font, health, imageWidth - 12 - font.width(health), 19, operationalHealthColor(), false);

        graphics.drawString(font, section.label.toUpperCase(), 13, 62, TEXT, false);
        graphics.drawString(font, fitForWidth(section.subtitle, 210), 92, 62, MUTED, false);
        renderSection(graphics, section);

        String evidence = fitForWidth("EVIDENCE • " + menu.evidenceStateLabel(), 150);
        graphics.drawString(font, evidence, 13, imageHeight - 20, evidenceStateColor(), false);
        String position = fitForWidth("@ " + menu.blockPos().getX() + ", " + menu.blockPos().getY() + ", " + menu.blockPos().getZ(), 142);
        graphics.drawString(font, position, imageWidth - 13 - font.width(position), imageHeight - 20, MUTED, false);
    }

    /** Pixel-based truncation used by all shared widgets and available to every device screen. */
    protected final String fitForWidth(String text, int maxWidth) {
        if (text == null || text.isBlank()) return "—";
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String compact = text;
        while (compact.length() > 1 && font.width(compact + "…") > maxWidth) {
            compact = compact.substring(0, compact.length() - 1);
        }
        return compact + "…";
    }

    /** Safe one-line text helper for long explanatory strings in concrete screens. */
    protected final void safeText(GuiGraphics graphics, String text, int x, int y, int color) {
        int width = Math.max(0, CONTENT_RIGHT - x);
        graphics.drawString(font, fitForWidth(text, width), x, y, color, false);
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
            case "Safety state" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE ? "TIMED OUT" : "HEALTHY";
            case "Actuator" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE ? "BRAKED" : "ENABLED";
            case "Voting health" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_DEGRADED ? "DEGRADED" : "OK";
            case "Safety memory" -> menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_FAULT ? "FAULT LATCHED" : "CLEAR";
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
            return menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE ? "VENTING" : "RELIEF ARMED";
        }
        return value;
    }

    private record PresentationLine(String label, String value) {
    }

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
        graphics.drawString(font, fitForWidth(normalized.label(), 130), CONTENT_LEFT, y, MUTED, false);
        graphics.drawString(font, fitForWidth(normalized.value(), CONTENT_RIGHT - VALUE_X), VALUE_X, y, TEXT, false);
    }

    protected final void statusLine(GuiGraphics graphics, String label, String value, int color, int y) {
        if (isOperationalHealthLine(label)) {
            value = authoritativeHealthValue(label, value);
            color = operationalHealthColor();
        }
        PresentationLine normalized = normalizeLegacyPresentation(label, value);
        graphics.drawString(font, fitForWidth(normalized.label(), 130), CONTENT_LEFT, y, MUTED, false);
        graphics.drawString(font, fitForWidth(normalized.value(), CONTENT_RIGHT - VALUE_X), VALUE_X, y, color, false);
    }

    protected final void statusBadge(GuiGraphics graphics, String value, int color, int x, int y) {
        if (isOperationalHealthBadge(value)) {
            value = authoritativeHealthBadge(value);
            color = operationalHealthColor();
        }
        int available = Math.max(24, CONTENT_RIGHT - x);
        String compact = fitForWidth(value, Math.max(8, available - 12));
        int width = Math.min(available, font.width(compact) + 12);
        graphics.fill(x, y, x + width, y + 14, PANEL_3);
        graphics.fill(x, y, x + 3, y + 14, color);
        graphics.drawString(font, compact, x + 7, y + 3, color, false);
    }

    protected final void metricCard(GuiGraphics graphics, String label, String value, int x, int y, int width, int color) {
        int safeWidth = Math.max(24, Math.min(width, CONTENT_RIGHT - x));
        graphics.fill(x, y, x + safeWidth, y + 31, PANEL_3);
        graphics.fill(x, y, x + 2, y + 31, color);
        graphics.drawString(font, fitForWidth(label.toUpperCase(), safeWidth - 14), x + 7, y + 5, MUTED, false);
        graphics.drawString(font, fitForWidth(value, safeWidth - 14), x + 7, y + 17, TEXT, false);
    }

    protected final void healthBadge(GuiGraphics graphics, String state, boolean healthy, int x, int y) {
        statusBadge(graphics, healthy ? "HEALTH • " + state : "ATTENTION • " + state,
                healthy ? GOOD : WARN, x, y);
    }

    protected final void sectionRule(GuiGraphics graphics, int y) {
        graphics.fill(CONTENT_LEFT, y, CONTENT_RIGHT, y + 1, 0xFF3A4650);
    }

    protected final void signalBar(GuiGraphics graphics, int value, int y) {
        int bounded = Math.max(0, Math.min(15, value));
        int x0 = CONTENT_LEFT;
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
