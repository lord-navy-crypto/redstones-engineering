package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import dev.redstoneengineering.ui.menu.CopperCircuitMeterMenu;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import dev.redstoneengineering.ui.menu.SignalAnalyzerMenu;
import dev.redstoneengineering.ui.menu.SignalConditionerMenu;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Shared RSE engineering visual language. */
public abstract class EngineeringScreen<M extends EngineeringDeviceMenu> extends AbstractContainerScreen<M> {
    protected enum Section {
        OVERVIEW("Overview", "Live engineering state"),
        PORTS("Ports", "Physical I/O contract"),
        CONFIGURE("Configure", "Parameters, modes and actions"),
        DIAGNOSTICS("Observe", "Signals, topology and health"),
        HISTORY("Log", "Evidence and retained events");

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

    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_LEFT = 24;
    private static final int HEADER_BOTTOM = 72;
    private static final int FOOTER_HEIGHT = 34;
    private static final int ROUTE_CONTROL_Y = 236;
    private static final int ROUTE_ENDPOINT_Y = 198;

    private Section section = Section.OVERVIEW;
    private boolean routePage;
    private final List<AbstractWidget> configureWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> configureVirtualY = new IdentityHashMap<>();
    private final Map<AbstractWidget, Integer> configureVirtualX = new IdentityHashMap<>();
    private final List<AbstractWidget> routeWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> routeVirtualY = new IdentityHashMap<>();
    private final Map<AbstractWidget, Integer> routeVirtualX = new IdentityHashMap<>();
    private final List<Button> sectionButtons = new ArrayList<>();
    private int scrollOffset;
    private int horizontalOffset;
    private Button routeTab;
    private Button routePrevious;
    private Button routeNext;
    private Button routeInputPrevious;
    private Button routeInputNext;
    private Button routeOutputPrevious;
    private Button routeOutputNext;

    protected EngineeringScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 520;
        this.imageHeight = 300;
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(360, width - VIEW_MARGIN * 2);
        imageHeight = Math.max(240, height - VIEW_MARGIN * 2);
        super.init();
        configureWidgets.clear();
        configureVirtualY.clear();
        configureVirtualX.clear();
        routeWidgets.clear();
        routeVirtualY.clear();
        routeVirtualX.clear();
        sectionButtons.clear();
        scrollOffset = 0;
        horizontalOffset = 0;
        routeTab = null;
        routePrevious = null;
        routeNext = null;
        routeInputPrevious = null;
        routeInputNext = null;
        routeOutputPrevious = null;
        routeOutputNext = null;

        int tabY = topPos + 36;
        int gap = imageWidth < 420 ? 4 : 6;
        int tabCount = 6;
        int tabWidth = Math.max(36, (imageWidth - 48 - gap * (tabCount - 1)) / tabCount);
        int x = leftPos + 24;

        addSectionTab(Section.OVERVIEW, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.PORTS, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.CONFIGURE, x, tabY, tabWidth); x += tabWidth + gap;
        routeTab = addRenderableWidget(Button.builder(Component.literal("Route"), button -> setRoutePage())
                .bounds(x, tabY, tabWidth, 22).build()); x += tabWidth + gap;
        addSectionTab(Section.DIAGNOSTICS, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.HISTORY, x, tabY, tabWidth);

        addDeviceWidgets();
        addRouteControls();
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
    }

    private void addSectionTab(Section target, int x, int y, int width) {
        Button tab = Button.builder(Component.literal(sectionTabLabel(target)), button -> setSection(target))
                .bounds(x, y, width, 22).build();
        sectionButtons.add(addRenderableWidget(tab));
    }

    private String sectionTabLabel(Section target) {
        if (imageWidth >= 420) return target.label;
        return switch (target) {
            case OVERVIEW -> "State";
            case PORTS -> "Ports";
            case CONFIGURE -> "Config";
            case DIAGNOSTICS -> "Diag";
            case HISTORY -> "Log";
        };
    }

    protected void addDeviceWidgets() {}
    protected void syncDeviceWidgetLabels() {}

    protected final <T extends AbstractWidget> T addConfigureWidget(T widget) {
        configureWidgets.add(widget);
        configureVirtualY.put(widget, widget.getY() - topPos + scrollOffset);
        configureVirtualX.put(widget, widget.getX() - leftPos + horizontalOffset);
        return addRenderableWidget(widget);
    }

    private <T extends AbstractWidget> T addRouteWidget(T widget) {
        routeWidgets.add(widget);
        routeVirtualY.put(widget, widget.getY() - topPos + scrollOffset);
        routeVirtualX.put(widget, widget.getX() - leftPos + horizontalOffset);
        return addRenderableWidget(widget);
    }

    protected final void sendMenuButton(int buttonId) {
        if (buttonId < 0) return;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void addRouteControls() {
        int width = Math.max(136, Math.min(220, (contentWidth() - 24) / 2));
        routePrevious = addRouteWidget(Button.builder(
                Component.literal("Direction ▲"), button -> sendMenuButton(routeActionId(false)))
                .bounds(leftPos + CONTENT_LEFT, topPos + ROUTE_CONTROL_Y, width, 22).build());
        routeNext = addRouteWidget(Button.builder(
                Component.literal("Direction ▼"), button -> sendMenuButton(routeActionId(true)))
                .bounds(leftPos + contentRight() - width, topPos + ROUTE_CONTROL_Y, width, 22).build());

        int endpointGap = 8;
        int endpointWidth = Math.max(72, (contentWidth() - endpointGap * 3) / 4);
        int x0 = leftPos + CONTENT_LEFT;
        routeInputPrevious = addRouteWidget(Button.builder(
                Component.literal("RX ▲"), button -> sendMenuButton(routeInputActionId(false)))
                .bounds(x0, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
        routeInputNext = addRouteWidget(Button.builder(
                Component.literal("RX ▼"), button -> sendMenuButton(routeInputActionId(true)))
                .bounds(x0 + endpointWidth + endpointGap, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
        routeOutputPrevious = addRouteWidget(Button.builder(
                Component.literal("TX ▲"), button -> sendMenuButton(routeOutputActionId(false)))
                .bounds(x0 + (endpointWidth + endpointGap) * 2, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
        routeOutputNext = addRouteWidget(Button.builder(
                Component.literal("TX ▼"), button -> sendMenuButton(routeOutputActionId(true)))
                .bounds(x0 + (endpointWidth + endpointGap) * 3, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
    }

    private int routeActionId(boolean clockwise) {
        if (menu instanceof FieldDeviceMenu) return clockwise ? FieldDeviceMenu.BUTTON_ROTATE_CW : FieldDeviceMenu.BUTTON_ROTATE_CCW;
        if (menu instanceof UniversalFieldDeviceMenu) return clockwise ? UniversalFieldDeviceMenu.BUTTON_ROTATE_RIGHT : UniversalFieldDeviceMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof RangeSensorMenu) return clockwise ? RangeSensorMenu.BUTTON_ROTATE_RIGHT : RangeSensorMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof SignalAnalyzerMenu) return clockwise ? SignalAnalyzerMenu.BUTTON_ROTATE_RIGHT : SignalAnalyzerMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof SignalProcessorMenu) return clockwise ? SignalProcessorMenu.BUTTON_ROTATE_RIGHT : SignalProcessorMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof SignalConditionerMenu) return clockwise ? SignalConditionerMenu.BUTTON_ROTATE_RIGHT : SignalConditionerMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof QuartzTimingMenu) return clockwise ? QuartzTimingMenu.BUTTON_ROTATE_RIGHT : QuartzTimingMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof RadioLinkMenu) return clockwise ? RadioLinkMenu.BUTTON_OUTPUT_RIGHT : RadioLinkMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof DigitalCommunicationMenu) return clockwise ? DigitalCommunicationMenu.BUTTON_ROTATE_RIGHT : DigitalCommunicationMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof PneumaticSystemMenu) return clockwise ? PneumaticSystemMenu.BUTTON_ROTATE_RIGHT : PneumaticSystemMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof OpticalSystemMenu) return clockwise ? OpticalSystemMenu.BUTTON_ROTATE_RIGHT : OpticalSystemMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof AmethystSystemMenu) return clockwise ? AmethystSystemMenu.BUTTON_ROTATE_RIGHT : AmethystSystemMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof MagneticSystemMenu) return clockwise ? MagneticSystemMenu.BUTTON_ROTATE_RIGHT : MagneticSystemMenu.BUTTON_ROTATE_LEFT;
        if (menu instanceof ReliabilitySystemMenu) return clockwise ? ReliabilitySystemMenu.BUTTON_ROTATE_RIGHT : ReliabilitySystemMenu.BUTTON_ROTATE_LEFT;
        return -1;
    }

    private int routeInputActionId(boolean clockwise) {
        if (menu instanceof FieldDeviceMenu) return clockwise ? FieldDeviceMenu.BUTTON_INPUT_NEXT : FieldDeviceMenu.BUTTON_INPUT_PREVIOUS;
        if (menu instanceof UniversalFieldDeviceMenu) return clockwise ? UniversalFieldDeviceMenu.BUTTON_INPUT_RIGHT : UniversalFieldDeviceMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof DigitalCommunicationMenu) return clockwise ? DigitalCommunicationMenu.BUTTON_INPUT_RIGHT : DigitalCommunicationMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof PneumaticSystemMenu) return clockwise ? PneumaticSystemMenu.BUTTON_INPUT_RIGHT : PneumaticSystemMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof SignalProcessorMenu) return clockwise ? SignalProcessorMenu.BUTTON_INPUT_RIGHT : SignalProcessorMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof SignalConditionerMenu) return clockwise ? SignalConditionerMenu.BUTTON_INPUT_RIGHT : SignalConditionerMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof QuartzTimingMenu) return clockwise ? QuartzTimingMenu.BUTTON_INPUT_RIGHT : QuartzTimingMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof AmethystSystemMenu) return clockwise ? AmethystSystemMenu.BUTTON_INPUT_RIGHT : AmethystSystemMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof OpticalSystemMenu) return clockwise ? OpticalSystemMenu.BUTTON_INPUT_RIGHT : OpticalSystemMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof MagneticSystemMenu) return clockwise ? MagneticSystemMenu.BUTTON_INPUT_RIGHT : MagneticSystemMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof ReliabilitySystemMenu) return clockwise ? ReliabilitySystemMenu.BUTTON_INPUT_RIGHT : ReliabilitySystemMenu.BUTTON_INPUT_LEFT;
        if (menu instanceof CopperCircuitMeterMenu) return clockwise ? CopperCircuitMeterMenu.BUTTON_FACE_NEXT : CopperCircuitMeterMenu.BUTTON_FACE_PREVIOUS;
        return -1;
    }

    private int routeOutputActionId(boolean clockwise) {
        if (menu instanceof FieldDeviceMenu) return clockwise ? FieldDeviceMenu.BUTTON_OUTPUT_NEXT : FieldDeviceMenu.BUTTON_OUTPUT_PREVIOUS;
        if (menu instanceof UniversalFieldDeviceMenu) return clockwise ? UniversalFieldDeviceMenu.BUTTON_OUTPUT_RIGHT : UniversalFieldDeviceMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof DigitalCommunicationMenu) return clockwise ? DigitalCommunicationMenu.BUTTON_OUTPUT_RIGHT : DigitalCommunicationMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof PneumaticSystemMenu) return clockwise ? PneumaticSystemMenu.BUTTON_OUTPUT_RIGHT : PneumaticSystemMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof SignalProcessorMenu) return clockwise ? SignalProcessorMenu.BUTTON_OUTPUT_RIGHT : SignalProcessorMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof SignalConditionerMenu) return clockwise ? SignalConditionerMenu.BUTTON_OUTPUT_RIGHT : SignalConditionerMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof QuartzTimingMenu) return clockwise ? QuartzTimingMenu.BUTTON_OUTPUT_RIGHT : QuartzTimingMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof AmethystSystemMenu) return clockwise ? AmethystSystemMenu.BUTTON_OUTPUT_RIGHT : AmethystSystemMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof OpticalSystemMenu) return clockwise ? OpticalSystemMenu.BUTTON_OUTPUT_RIGHT : OpticalSystemMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof MagneticSystemMenu) return clockwise ? MagneticSystemMenu.BUTTON_OUTPUT_RIGHT : MagneticSystemMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof ReliabilitySystemMenu) return clockwise ? ReliabilitySystemMenu.BUTTON_OUTPUT_RIGHT : ReliabilitySystemMenu.BUTTON_OUTPUT_LEFT;
        if (menu instanceof RadioLinkMenu) return clockwise ? RadioLinkMenu.BUTTON_OUTPUT_RIGHT : RadioLinkMenu.BUTTON_OUTPUT_LEFT;
        return -1;
    }

    private boolean hasRouteInputEndpoint() {
        if (menu instanceof FieldDeviceMenu field) return field.hasInputEndpoint();
        if (menu instanceof UniversalFieldDeviceMenu universal) return universal.hasInputEndpoint();
        if (menu instanceof DigitalCommunicationMenu) return true;
        if (menu instanceof SignalProcessorMenu processor) return processor.hasInputEndpoint();
        if (menu instanceof SignalConditionerMenu conditioner) return conditioner.hasInputEndpoint();
        if (menu instanceof QuartzTimingMenu quartz) return quartz.hasInputEndpoint();
        if (menu instanceof AmethystSystemMenu amethyst) return amethyst.hasInputEndpoint();
        if (menu instanceof OpticalSystemMenu optical) return optical.hasInputEndpoint();
        if (menu instanceof MagneticSystemMenu magnetic) return magnetic.hasInputEndpoint();
        if (menu instanceof ReliabilitySystemMenu reliability) return reliability.hasInputEndpoint();
        if (menu instanceof CopperCircuitMeterMenu) return true;
        return menu instanceof PneumaticSystemMenu pneumatic && pneumatic.directional();
    }

    private boolean hasRouteOutputEndpoint() {
        if (menu instanceof FieldDeviceMenu field) return field.hasOutputEndpoint();
        if (menu instanceof UniversalFieldDeviceMenu universal) return universal.hasOutputEndpoint();
        if (menu instanceof DigitalCommunicationMenu) return true;
        if (menu instanceof SignalProcessorMenu processor) return processor.hasOutputEndpoint();
        if (menu instanceof SignalConditionerMenu conditioner) return conditioner.hasOutputEndpoint();
        if (menu instanceof QuartzTimingMenu quartz) return quartz.hasOutputEndpoint();
        if (menu instanceof AmethystSystemMenu amethyst) return amethyst.hasOutputEndpoint();
        if (menu instanceof OpticalSystemMenu optical) return optical.hasOutputEndpoint();
        if (menu instanceof MagneticSystemMenu magnetic) return magnetic.hasOutputEndpoint();
        if (menu instanceof ReliabilitySystemMenu reliability) return reliability.hasOutputEndpoint();
        if (menu instanceof RadioLinkMenu radio) return radio.kind() == RadioLinkMenu.KIND_RECEIVER;
        return menu instanceof PneumaticSystemMenu pneumatic && pneumatic.directional();
    }

    private boolean routeSupported() {
        if (menu instanceof FieldDeviceMenu field) return field.seriesConfigurable();
        if (menu instanceof UniversalFieldDeviceMenu universal) return universal.rotatableSeriesAxis();
        if (menu instanceof RangeSensorMenu) return true;
        if (menu instanceof SignalAnalyzerMenu) return true;
        if (menu instanceof SignalProcessorMenu) return true;
        if (menu instanceof SignalConditionerMenu) return true;
        if (menu instanceof QuartzTimingMenu quartz) return quartz.hasInputEndpoint() || quartz.hasOutputEndpoint();
        if (menu instanceof RadioLinkMenu radio) return radio.kind() == RadioLinkMenu.KIND_RECEIVER;
        if (menu instanceof DigitalCommunicationMenu) return true;
        if (menu instanceof PneumaticSystemMenu pneumatic) return pneumatic.directional();
        if (menu instanceof OpticalSystemMenu optical) return optical.directional() || optical.kind() == OpticalSystemMenu.KIND_METER;
        if (menu instanceof AmethystSystemMenu amethyst) return amethyst.directional();
        if (menu instanceof MagneticSystemMenu magnetic) return magnetic.kind() == MagneticSystemMenu.KIND_PERMANENT || magnetic.kind() == MagneticSystemMenu.KIND_COIL;
        if (menu instanceof CopperCircuitMeterMenu) return true;
        return menu instanceof ReliabilitySystemMenu;
    }

    private void syncRouteControls() {
        if (routePrevious == null || routeNext == null) return;
        boolean enabled = routeSupported();
        boolean rx = enabled && hasRouteInputEndpoint();
        boolean tx = enabled && hasRouteOutputEndpoint();
        boolean endpoints = rx || tx;

        routePrevious.active = enabled && !endpoints;
        routeNext.active = enabled && !endpoints;
        routePrevious.visible = routePage && enabled && !endpoints;
        routeNext.visible = routePage && enabled && !endpoints;
        routePrevious.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Cycle the device orientation to the previous valid direction.")));
        routeNext.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Cycle the device orientation to the next valid direction.")));

        if (routeInputPrevious != null && routeInputNext != null && routeOutputPrevious != null && routeOutputNext != null) {
            routeInputPrevious.active = rx;
            routeInputNext.active = rx;
            routeOutputPrevious.active = tx;
            routeOutputNext.active = tx;
            routeInputPrevious.visible = routePage && rx;
            routeInputNext.visible = routePage && rx;
            routeOutputPrevious.visible = routePage && tx;
            routeOutputNext.visible = routePage && tx;
            routeInputPrevious.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cycle RX / INPUT to the previous valid direction.")));
            routeInputNext.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cycle RX / INPUT to the next valid direction.")));
            routeOutputPrevious.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cycle TX / OUTPUT to the previous valid direction.")));
            routeOutputNext.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Cycle TX / OUTPUT to the next valid direction.")));
        }

        for (AbstractWidget widget : routeWidgets) syncRouteWidgetViewport(widget);
    }

    private void syncRouteWidgetViewport(AbstractWidget widget) {
        boolean requestedVisible = widget.visible;
        boolean requestedActive = widget.active;
        int virtualY = routeVirtualY.getOrDefault(widget, widget.getY() - topPos + scrollOffset);
        int virtualX = routeVirtualX.getOrDefault(widget, widget.getX() - leftPos + horizontalOffset);
        widget.setY(topPos + virtualY - scrollOffset);
        widget.setX(leftPos + virtualX - horizontalOffset);
        int viewportBottom = topPos + imageHeight - FOOTER_HEIGHT;
        int viewportLeft = leftPos + CONTENT_LEFT;
        int viewportRight = leftPos + contentRight();
        boolean inViewport = widget.getY() >= topPos + HEADER_BOTTOM
                && widget.getY() <= viewportBottom - widget.getHeight()
                && widget.getX() + widget.getWidth() >= viewportLeft
                && widget.getX() <= viewportRight;
        widget.visible = requestedVisible && inViewport;
        widget.active = requestedActive && inViewport;
    }

    private void setSection(Section target) {
        this.section = target;
        this.routePage = false;
        this.scrollOffset = 0;
        this.horizontalOffset = 0;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
    }

    private void setRoutePage() {
        routePage = true;
        scrollOffset = 0;
        horizontalOffset = 0;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
    }

    private boolean isLegacyRouteWidget(AbstractWidget widget) {
        if (!(widget instanceof Button button)) return false;
        String message = button.getMessage().getString();
        return message.contains("Rotate I/O") || message.contains("Rotate route");
    }

    private void updateWidgetVisibility() {
        boolean controlsVisible = isConfigureSection();
        int viewportBottom = topPos + imageHeight - FOOTER_HEIGHT;
        int viewportLeft = leftPos + CONTENT_LEFT;
        int viewportRight = leftPos + contentRight();
        for (AbstractWidget widget : configureWidgets) {
            int virtualY = configureVirtualY.getOrDefault(widget, widget.getY() - topPos + scrollOffset);
            int virtualX = configureVirtualX.getOrDefault(widget, widget.getX() - leftPos + horizontalOffset);
            widget.setY(topPos + virtualY - scrollOffset);
            widget.setX(leftPos + virtualX - horizontalOffset);
            widget.visible = controlsVisible && !isLegacyRouteWidget(widget)
                    && widget.getY() >= topPos + HEADER_BOTTOM
                    && widget.getY() <= viewportBottom - widget.getHeight()
                    && widget.getX() + widget.getWidth() >= viewportLeft
                    && widget.getX() <= viewportRight;
        }
        Section[] tabSections = {Section.OVERVIEW, Section.PORTS, Section.CONFIGURE, Section.DIAGNOSTICS, Section.HISTORY};
        for (int i = 0; i < sectionButtons.size(); i++) sectionButtons.get(i).active = routePage || tabSections[i] != section;
        if (routeTab != null) routeTab.active = !routePage;
    }

    protected final boolean isConfigureSection() { return !routePage && section == Section.CONFIGURE; }
    public final boolean showsPortVisualization() { return routePage || section == Section.PORTS; }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncDeviceWidgetLabels();
        syncRouteControls();
        updateWidgetVisibility();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + CONTENT_LEFT && mouseX <= leftPos + contentRight()
                && mouseY >= topPos + HEADER_BOTTOM && mouseY <= topPos + imageHeight - FOOTER_HEIGHT) {
            double horizontalDelta = Math.abs(scrollX) > 0.01 ? scrollX : (hasShiftDown() ? scrollY : 0.0);
            if (Math.abs(horizontalDelta) > 0.01 && maxHorizontalScroll() > 0) {
                horizontalOffset = Math.max(0, Math.min(maxHorizontalScroll(),
                        horizontalOffset - (int)Math.round(horizontalDelta * 32.0)));
            } else {
                scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int)Math.round(scrollY * 24.0)));
            }
            updateWidgetVisibility();
            syncRouteControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    protected int virtualContentHeight() {
        if (routePage) return 430;
        return switch (section) {
            case OVERVIEW -> 430;
            case PORTS -> 520;
            case CONFIGURE -> 560;
            case DIAGNOSTICS -> 620;
            case HISTORY -> 620;
        };
    }

    private int maxScroll() {
        int visible = Math.max(80, imageHeight - HEADER_BOTTOM - FOOTER_HEIGHT);
        return Math.max(0, virtualContentHeight() - visible);
    }

    protected int virtualContentWidth() {
        return Math.max(contentWidth(), 1100);
    }

    private int maxHorizontalScroll() {
        int visible = Math.max(240, contentWidth());
        return Math.max(0, virtualContentWidth() - visible);
    }

    private int contentRight() { return imageWidth - CONTENT_LEFT; }
    private int contentWidth() { return Math.max(240, contentRight() - CONTENT_LEFT); }
    private int valueX() { return Math.max(154, Math.min(imageWidth / 3, contentRight() - 180)); }

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
        int footerTop = topPos + imageHeight - FOOTER_HEIGHT;
        graphics.fill(leftPos + 8, topPos + HEADER_BOTTOM, leftPos + imageWidth - 8, footerTop - 4, PANEL_2);
        graphics.fill(leftPos + 8, footerTop, leftPos + imageWidth - 8, topPos + imageHeight - 9, PANEL_3);
        graphics.fill(leftPos + 8, topPos + HEADER_BOTTOM, leftPos + 11, footerTop - 8, WHITE_SIGN);
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

        if (routePage) {
            graphics.drawString(font, "ROUTE", 13, 62, TEXT, false);
            graphics.drawString(font, "Direct RX / TX direction control", 92, 62, MUTED, false);
            graphics.enableScissor(leftPos + CONTENT_LEFT, topPos + HEADER_BOTTOM,
                    leftPos + contentRight(), topPos + imageHeight - FOOTER_HEIGHT);
            graphics.pose().pushPose();
            graphics.pose().translate(-horizontalOffset, -scrollOffset, 0);
            renderRoutePage(graphics);
            graphics.pose().popPose();
            graphics.disableScissor();
        } else {
            graphics.drawString(font, section.label.toUpperCase(), 13, 62, TEXT, false);
            graphics.drawString(font, fitForWidth(section.subtitle, Math.max(210, imageWidth - 190)), 92, 62, MUTED, false);
            graphics.enableScissor(leftPos + CONTENT_LEFT, topPos + HEADER_BOTTOM,
                    leftPos + contentRight(), topPos + imageHeight - FOOTER_HEIGHT);
            graphics.pose().pushPose();
            graphics.pose().translate(-horizontalOffset, -scrollOffset, 0);
            renderSection(graphics, section);
            graphics.pose().popPose();
            graphics.disableScissor();
        }

        if (maxScroll() > 0 || maxHorizontalScroll() > 0) {
            String scroll = "SCROLL Y " + scrollOffset + " / " + maxScroll()
                    + "  •  X " + horizontalOffset + " / " + maxHorizontalScroll()
                    + "  •  trackpad X or Shift+wheel";
            graphics.drawString(font, fitForWidth(scroll, Math.max(180, imageWidth - 220)),
                    imageWidth - 14 - font.width(fitForWidth(scroll, Math.max(180, imageWidth - 220))), 62, MUTED, false);
        }

        String evidence = fitForWidth("EVIDENCE • " + menu.evidenceStateLabel(), Math.max(150, imageWidth / 3));
        graphics.drawString(font, evidence, 13, imageHeight - 20, evidenceStateColor(), false);
        String position = fitForWidth("@ " + menu.blockPos().getX() + ", " + menu.blockPos().getY() + ", " + menu.blockPos().getZ(), 142);
        graphics.drawString(font, position, imageWidth - 13 - font.width(position), imageHeight - 20, MUTED, false);
    }

    private void renderRoutePage(GuiGraphics graphics) {
        boolean enabled = routeSupported();
        boolean rx = enabled && hasRouteInputEndpoint();
        boolean tx = enabled && hasRouteOutputEndpoint();
        boolean endpoints = rx || tx;
        statusBadge(graphics, enabled ? "ROUTING ENABLED" : "FIXED INTERFACE", enabled ? INFO : MUTED, 16, 84);
        labelValue(graphics, "Topology role", menu.topologyRoleLabel(), 112);
        labelValue(graphics, "Current route", menu.portRouteLabel(), 132);
        labelValue(graphics, "Control authority", enabled ? "SERVER-SIDE" : "READ ONLY", 152);
        if (enabled && endpoints) {
            String controls = rx && tx ? "Use RX and TX ▲ / ▼ below to change endpoint direction."
                    : rx ? "Use RX ▲ / ▼ below to change the input direction."
                    : "Use TX ▲ / ▼ below to change the output direction.";
            safeText(graphics, controls, 16, 196, TEXT);
        } else if (enabled) {
            safeText(graphics, "Use Direction ▲ / ▼ below to change the physical interface direction.", 16, 174, TEXT);
        } else {
            safeText(graphics, "This device has a fixed physical port contract. Its remaining controls, if any, are on Configure.", 16, 174, MUTED);
        }
    }

    protected final String fitForWidth(String text, int maxWidth) {
        if (text == null || text.isBlank()) return "—";
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String compact = text;
        while (compact.length() > 1 && font.width(compact + "…") > maxWidth) compact = compact.substring(0, compact.length() - 1);
        return compact + "…";
    }

    protected final void safeText(GuiGraphics graphics, String text, int x, int y, int color) {
        String visible = text == null || text.isBlank() ? "—" : text;
        graphics.drawString(font, visible, x, y, color, false);
    }

    protected final int wrappedText(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        int cursor = y;
        String visible = text == null ? "" : text;
        for (var line : font.split(Component.literal(visible), Math.max(120, width))) {
            graphics.drawString(font, line, x, cursor, color, false);
            cursor += 12;
        }
        return cursor;
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
            case EngineeringDeviceMenu.EVIDENCE_FAULT, EngineeringDeviceMenu.EVIDENCE_DOMAIN_MISMATCH, EngineeringDeviceMenu.EVIDENCE_TOPOLOGY_ERROR -> BAD;
            default -> MUTED;
        };
    }

    private boolean isOperationalHealthLine(String label) {
        return "Safety state".equals(label) || "Actuator".equals(label) || "Voting health".equals(label)
                || "Safety memory".equals(label) || "System state".equals(label);
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
        return "HEARTBEAT WATCHDOG".equals(value) || "SERVO ACTUATOR".equals(value)
                || "2oo3 REDUNDANT VOTER".equals(value) || "FAULT LATCH".equals(value)
                || "OPERATIONS MONITOR • OBSERVER".equals(value) || "RELIEF ARMED".equals(value) || "VENTING".equals(value);
    }

    private String authoritativeHealthBadge(String value) {
        if ("RELIEF ARMED".equals(value) || "VENTING".equals(value)) {
            return menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_PROTECTIVE ? "VENTING" : "RELIEF ARMED";
        }
        return value;
    }

    private record PresentationLine(String label, String value) {}

    private PresentationLine normalizeLegacyPresentation(String label, String value) {
        if ("PNEUMATIC • SIX-WAY REGULATED MANIFOLD".equals(value)) return new PresentationLine(label, "PNEUMATIC • " + menu.portRouteLabel());
        if ("OTHER FIVE FACES".equals(label) && "REDSTONE PAYLOAD INPUT".equals(value)) return new PresentationLine("DOWN", "REDSTONE PAYLOAD INPUT");
        if ("Topology role".equals(label) && "FIXED / SOURCE / SINK / OBSERVER / PASSIVE".equals(value)) return new PresentationLine(label, menu.topologyRoleLabel());
        return new PresentationLine(label, value);
    }

    protected final void labelValue(GuiGraphics graphics, String label, String value, int y) {
        PresentationLine normalized = normalizeLegacyPresentation(label, value);
        int vx = valueX();
        graphics.drawString(font, fitForWidth(normalized.label(), Math.max(130, vx - CONTENT_LEFT - 20)), CONTENT_LEFT, y, MUTED, false);
        String valueText = normalized.value() == null || normalized.value().isBlank() ? "—" : normalized.value();
        graphics.drawString(font, valueText, vx, y, TEXT, false);
    }

    protected final void statusLine(GuiGraphics graphics, String label, String value, int color, int y) {
        if (isOperationalHealthLine(label)) {
            value = authoritativeHealthValue(label, value);
            color = operationalHealthColor();
        }
        PresentationLine normalized = normalizeLegacyPresentation(label, value);
        int vx = valueX();
        graphics.drawString(font, fitForWidth(normalized.label(), Math.max(130, vx - CONTENT_LEFT - 20)), CONTENT_LEFT, y, MUTED, false);
        String valueText = normalized.value() == null || normalized.value().isBlank() ? "—" : normalized.value();
        graphics.drawString(font, valueText, vx, y, color, false);
    }

    protected final void statusBadge(GuiGraphics graphics, String value, int color, int x, int y) {
        if (isOperationalHealthBadge(value)) {
            value = authoritativeHealthBadge(value);
            color = operationalHealthColor();
        }
        int available = Math.max(24, contentRight() - x);
        String compact = fitForWidth(value, Math.max(8, available - 12));
        int width = Math.min(available, font.width(compact) + 12);
        graphics.fill(x, y, x + width, y + 14, PANEL_3);
        graphics.fill(x, y, x + 3, y + 14, color);
        graphics.drawString(font, compact, x + 7, y + 3, color, false);
    }

    protected final void metricCard(GuiGraphics graphics, String label, String value, int x, int y, int width, int color) {
        int safeWidth = Math.max(24, Math.min(width, contentRight() - x));
        graphics.fill(x, y, x + safeWidth, y + 31, PANEL_3);
        graphics.fill(x, y, x + 2, y + 31, color);
        graphics.drawString(font, fitForWidth(label.toUpperCase(), safeWidth - 14), x + 7, y + 5, MUTED, false);
        graphics.drawString(font, fitForWidth(value, safeWidth - 14), x + 7, y + 17, TEXT, false);
    }

    protected final void healthBadge(GuiGraphics graphics, String state, boolean healthy, int x, int y) {
        statusBadge(graphics, healthy ? "HEALTH • " + state : "ATTENTION • " + state, healthy ? GOOD : WARN, x, y);
    }

    protected final void sectionRule(GuiGraphics graphics, int y) {
        graphics.fill(CONTENT_LEFT, y, contentRight(), y + 1, 0xFF3A4650);
    }

    protected final void signalBar(GuiGraphics graphics, int value, int y) {
        int bounded = Math.max(0, Math.min(15, value));
        int x0 = CONTENT_LEFT;
        int x1 = Math.max(286, contentRight() - 18);
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
        String scaleValue = bounded + " / 15";
        graphics.drawString(font, scaleValue, x1 - font.width(scaleValue), y - 10, TEXT, false);
    }

    protected abstract void renderSection(GuiGraphics graphics, Section section);
}
