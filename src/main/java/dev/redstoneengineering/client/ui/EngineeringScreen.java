package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
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
import java.util.List;

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

    protected static final int CONTENT_LEFT = 24;
    private static final int CONTENT_TOP = 82;
    private static final int FOOTER_HEIGHT = 66;
    private static final int MIN_WORKSPACE_WIDTH = 440;
    private static final int MAX_WORKSPACE_WIDTH = 780;
    private static final int MIN_WORKSPACE_HEIGHT = 320;
    private static final int MAX_WORKSPACE_HEIGHT = 520;
    private static final int DEFAULT_CANVAS_WIDTH = 1020;
    private static final int DEFAULT_CANVAS_HEIGHT = 980;
    private static final int CONFIGURE_CONTROL_COLUMNS = 3;
    private static final int CONFIGURE_CONTROL_GAP_X = 10;
    private static final int CONFIGURE_CONTROL_GAP_Y = 8;
    private static final int CONFIGURE_CONTROL_HEIGHT = 20;
    private static final int CONFIGURE_CONTROL_TOP = 88;

    private Section section = Section.OVERVIEW;
    private boolean routePage;
    private final List<AbstractWidget> configureWidgets = new ArrayList<>();
    private final List<Button> sectionButtons = new ArrayList<>();
    private Button routeTab;
    private Button routePrevious;
    private Button routeNext;
    private Button routeInputPrevious;
    private Button routeInputNext;
    private Button routeOutputPrevious;
    private Button routeOutputNext;
    private int scrollX;
    private int scrollY;
    private boolean draggingHorizontalScroll;
    private boolean draggingVerticalScroll;
    private double horizontalDragOffset;
    private double verticalDragOffset;

    protected EngineeringScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 620;
        this.imageHeight = 390;
        this.titleLabelX = 12;
        this.titleLabelY = 10;
        this.inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(MIN_WORKSPACE_WIDTH, Math.min(MAX_WORKSPACE_WIDTH, width - 20));
        imageHeight = Math.max(MIN_WORKSPACE_HEIGHT, Math.min(MAX_WORKSPACE_HEIGHT, height - 20));
        super.init();
        scrollX = 0;
        scrollY = 0;
        draggingHorizontalScroll = false;
        draggingVerticalScroll = false;
        horizontalDragOffset = 0.0;
        verticalDragOffset = 0.0;
        configureWidgets.clear();
        sectionButtons.clear();
        routeTab = null;
        routePrevious = null;
        routeNext = null;
        routeInputPrevious = null;
        routeInputNext = null;
        routeOutputPrevious = null;
        routeOutputNext = null;

        int tabY = topPos + 31;
        int x = leftPos + 8;
        int gap = 2;
        int tabWidth = Math.max(52, (imageWidth - 16 - gap * 5) / 6);

        addSectionTab(Section.OVERVIEW, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.PORTS, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.CONFIGURE, x, tabY, tabWidth); x += tabWidth + gap;
        routeTab = addRenderableWidget(Button.builder(Component.literal("Route"), button -> setRoutePage())
                .bounds(x, tabY, tabWidth, 20).build()); x += tabWidth + gap;
        addSectionTab(Section.DIAGNOSTICS, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.HISTORY, x, tabY, tabWidth);

        addDeviceWidgets();
        layoutConfigureWidgets();
        addRouteControls();
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
        clampScroll();
    }

    private void addSectionTab(Section target, int x, int y, int width) {
        Button tab = Button.builder(Component.literal(target.label), button -> setSection(target))
                .bounds(x, y, width, 20).build();
        sectionButtons.add(addRenderableWidget(tab));
    }

    protected void addDeviceWidgets() {}
    protected void syncDeviceWidgetLabels() {}

    /** Shared all-family Configure rail metadata for UI verifiers and human inspection. */
    protected final int configureControlCount() { return configureWidgets.size(); }

    protected final <T extends AbstractWidget> T addConfigureWidget(T widget) {
        configureWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    /**
     * Global Configure control rail.
     *
     * Device screens may still declare controls in their natural semantic order, but final
     * position and width are owned here so no family can regress to cramped hand-written
     * coordinates or cover the scrollable engineering content.
     */
    private void layoutConfigureWidgets() {
        if (configureWidgets.isEmpty()) return;

        int controlCount = configureWidgets.size();
        int rows = configureControlRows();
        int availableWidth = imageWidth - (CONTENT_LEFT * 2);
        int controlWidth = Math.max(92,
                (availableWidth - CONFIGURE_CONTROL_GAP_X * (CONFIGURE_CONTROL_COLUMNS - 1))
                        / CONFIGURE_CONTROL_COLUMNS);

        for (int index = 0; index < controlCount; index++) {
            int row = index / CONFIGURE_CONTROL_COLUMNS;
            int column = index % CONFIGURE_CONTROL_COLUMNS;
            int rowStart = row * CONFIGURE_CONTROL_COLUMNS;
            int rowCount = Math.min(CONFIGURE_CONTROL_COLUMNS, controlCount - rowStart);
            int rowWidth = rowCount * controlWidth + (rowCount - 1) * CONFIGURE_CONTROL_GAP_X;
            int rowX = leftPos + (imageWidth - rowWidth) / 2;
            AbstractWidget widget = configureWidgets.get(index);
            widget.setX(rowX + column * (controlWidth + CONFIGURE_CONTROL_GAP_X));
            widget.setY(topPos + CONFIGURE_CONTROL_TOP + row * (CONFIGURE_CONTROL_HEIGHT + CONFIGURE_CONTROL_GAP_Y));
            widget.setWidth(controlWidth);
            widget.setHeight(CONFIGURE_CONTROL_HEIGHT);
        }
    }

    private int configureControlRows() {
        return Math.max(1,
                (configureWidgets.size() + CONFIGURE_CONTROL_COLUMNS - 1) / CONFIGURE_CONTROL_COLUMNS);
    }

    private int configureContentOffset() {
        if (configureWidgets.isEmpty()) return 0;
        return 28 + configureControlRows() * (CONFIGURE_CONTROL_HEIGHT + CONFIGURE_CONTROL_GAP_Y);
    }

    protected final void sendMenuButton(int buttonId) {
        if (buttonId < 0) return;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private int contentRight() { return imageWidth - 16; }
    private int valueX() { return Math.max(154, Math.min(contentRight() - 160, (imageWidth * 43) / 100)); }
    private int footerTop() { return imageHeight - FOOTER_HEIGHT; }
    private int routeControlY() { return footerTop() - 38; }
    private int routeEndpointY() { return footerTop() - 62; }
    private int viewportWidth() { return Math.max(1, imageWidth - 28); }
    private int viewportHeight() { return Math.max(1, footerTop() - CONTENT_TOP - 8); }

    /**
     * Every engineering page gets a real virtual canvas rather than being forced into the visible viewport.
     * Subclasses can still return a larger value for exceptionally wide formula tables or long evidence pages.
     */
    protected int virtualContentWidth(Section section) {
        return Math.max(viewportWidth(), DEFAULT_CANVAS_WIDTH);
    }

    /** Give every engineering page real vertical headroom for detailed model/evidence presentation. */
    protected int virtualContentHeight(Section section) {
        return Math.max(viewportHeight(), DEFAULT_CANVAS_HEIGHT);
    }

    private int activeVirtualWidth() {
        return routePage ? viewportWidth()
                : Math.max(Math.max(viewportWidth(), DEFAULT_CANVAS_WIDTH), virtualContentWidth(section));
    }

    private int activeVirtualHeight() {
        return routePage ? viewportHeight()
                : Math.max(Math.max(viewportHeight(), DEFAULT_CANVAS_HEIGHT), virtualContentHeight(section));
    }

    /** Right edge of the scrollable engineering canvas, distinct from the physical window edge. */
    private int canvasRight() {
        return Math.max(contentRight(), activeVirtualWidth() - 24);
    }

    /** Keep the primary value column initially visible while giving long values room to extend into X-scroll space. */
    private int canvasValueX() {
        return Math.max(190, valueX() + 24);
    }

    private void clampScroll() {
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, activeVirtualWidth() - viewportWidth())));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, activeVirtualHeight() - viewportHeight())));
    }

    private void addRouteControls() {
        int width = Math.min(180, Math.max(112, (imageWidth - 48) / 2));
        routePrevious = addRenderableWidget(Button.builder(
                Component.literal("Direction ▲"), button -> sendMenuButton(routeActionId(false)))
                .bounds(leftPos + CONTENT_LEFT, topPos + routeControlY(), width, 20).build());
        routeNext = addRenderableWidget(Button.builder(
                Component.literal("Direction ▼"), button -> sendMenuButton(routeActionId(true)))
                .bounds(leftPos + contentRight() - width, topPos + routeControlY(), width, 20).build());

        int endpointGap = 8;
        int endpointWidth = Math.min(100, Math.max(66, (imageWidth - 56) / 4));
        int x0 = leftPos + CONTENT_LEFT;
        routeInputPrevious = addRenderableWidget(Button.builder(
                Component.literal("RX ▲"), button -> sendMenuButton(routeInputActionId(false)))
                .bounds(x0, topPos + routeEndpointY(), endpointWidth, 20).build());
        routeInputNext = addRenderableWidget(Button.builder(
                Component.literal("RX ▼"), button -> sendMenuButton(routeInputActionId(true)))
                .bounds(x0 + endpointWidth + endpointGap, topPos + routeEndpointY(), endpointWidth, 20).build());
        routeOutputPrevious = addRenderableWidget(Button.builder(
                Component.literal("TX ▲"), button -> sendMenuButton(routeOutputActionId(false)))
                .bounds(x0 + (endpointWidth + endpointGap) * 2, topPos + routeEndpointY(), endpointWidth, 20).build());
        routeOutputNext = addRenderableWidget(Button.builder(
                Component.literal("TX ▼"), button -> sendMenuButton(routeOutputActionId(true)))
                .bounds(x0 + (endpointWidth + endpointGap) * 3, topPos + routeEndpointY(), endpointWidth, 20).build());
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
        if (menu instanceof QuartzTimingMenu quartz) return quartz.kind() == QuartzTimingMenu.KIND_DIVIDER || quartz.kind() == QuartzTimingMenu.KIND_STABILITY;
        if (menu instanceof RadioLinkMenu radio) return radio.kind() == RadioLinkMenu.KIND_RECEIVER;
        if (menu instanceof DigitalCommunicationMenu) return true;
        if (menu instanceof PneumaticSystemMenu pneumatic) return pneumatic.directional();
        if (menu instanceof OpticalSystemMenu optical) return optical.directional() || optical.kind() == OpticalSystemMenu.KIND_METER;
        if (menu instanceof AmethystSystemMenu amethyst) return amethyst.directional();
        if (menu instanceof MagneticSystemMenu magnetic) return magnetic.kind() == MagneticSystemMenu.KIND_PERMANENT || magnetic.kind() == MagneticSystemMenu.KIND_COIL;
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
    }

    private void setSection(Section target) {
        this.section = target;
        this.routePage = false;
        this.scrollX = 0;
        this.scrollY = 0;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
    }

    private void setRoutePage() {
        routePage = true;
        scrollX = 0;
        scrollY = 0;
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
        for (AbstractWidget widget : configureWidgets) widget.visible = controlsVisible && !isLegacyRouteWidget(widget);
        Section[] tabSections = {Section.OVERVIEW, Section.PORTS, Section.CONFIGURE, Section.DIAGNOSTICS, Section.HISTORY};
        for (int i = 0; i < sectionButtons.size(); i++) sectionButtons.get(i).active = routePage || tabSections[i] != section;
        if (routeTab != null) routeTab.active = !routePage;
    }

    protected final boolean isConfigureSection() { return !routePage && section == Section.CONFIGURE; }
    public final boolean showsPortVisualization() { return routePage || section == Section.PORTS; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginScrollbarDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && (draggingHorizontalScroll || draggingVerticalScroll)) {
            dragScrollbarTo(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = draggingHorizontalScroll || draggingVerticalScroll;
        draggingHorizontalScroll = false;
        draggingVerticalScroll = false;
        if (handled && button == 0) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean beginScrollbarDrag(double mouseX, double mouseY) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;

        int maxX = Math.max(0, activeVirtualWidth() - viewportWidth());
        if (maxX > 0) {
            int thumbX = horizontalThumbX();
            int thumbWidth = horizontalThumbWidth();
            int trackY = horizontalTrackY();
            if (localX >= horizontalTrackX0() && localX <= horizontalTrackX1()
                    && localY >= trackY - 4 && localY <= trackY + 7) {
                if (localX < thumbX || localX > thumbX + thumbWidth) {
                    horizontalDragOffset = thumbWidth / 2.0;
                    draggingHorizontalScroll = true;
                    dragScrollbarTo(mouseX, mouseY);
                } else {
                    horizontalDragOffset = localX - thumbX;
                    draggingHorizontalScroll = true;
                }
                return true;
            }
        }

        int maxY = Math.max(0, activeVirtualHeight() - viewportHeight());
        if (maxY > 0) {
            int thumbY = verticalThumbY();
            int thumbHeight = verticalThumbHeight();
            int trackX = verticalTrackX();
            if (localX >= trackX - 5 && localX <= trackX + 8
                    && localY >= verticalTrackY0() && localY <= verticalTrackY1()) {
                if (localY < thumbY || localY > thumbY + thumbHeight) {
                    verticalDragOffset = thumbHeight / 2.0;
                    draggingVerticalScroll = true;
                    dragScrollbarTo(mouseX, mouseY);
                } else {
                    verticalDragOffset = localY - thumbY;
                    draggingVerticalScroll = true;
                }
                return true;
            }
        }
        return false;
    }

    private void dragScrollbarTo(double mouseX, double mouseY) {
        if (draggingHorizontalScroll) {
            int maxX = Math.max(0, activeVirtualWidth() - viewportWidth());
            int thumbWidth = horizontalThumbWidth();
            int travel = Math.max(1, horizontalTrackX1() - horizontalTrackX0() - thumbWidth);
            double localX = mouseX - leftPos;
            double thumbX = Math.max(horizontalTrackX0(),
                    Math.min(horizontalTrackX1() - thumbWidth, localX - horizontalDragOffset));
            scrollX = (int) Math.round(((thumbX - horizontalTrackX0()) / travel) * maxX);
        }
        if (draggingVerticalScroll) {
            int maxY = Math.max(0, activeVirtualHeight() - viewportHeight());
            int thumbHeight = verticalThumbHeight();
            int travel = Math.max(1, verticalTrackY1() - verticalTrackY0() - thumbHeight);
            double localY = mouseY - topPos;
            double thumbY = Math.max(verticalTrackY0(),
                    Math.min(verticalTrackY1() - thumbHeight, localY - verticalDragOffset));
            scrollY = (int) Math.round(((thumbY - verticalTrackY0()) / travel) * maxY);
        }
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXDelta, double scrollYDelta) {
        int x0 = leftPos + 12;
        int y0 = topPos + CONTENT_TOP;
        int x1 = leftPos + imageWidth - 12;
        int y1 = topPos + footerTop() - 8;
        if (mouseX < x0 || mouseX >= x1 || mouseY < y0 || mouseY >= y1) {
            return super.mouseScrolled(mouseX, mouseY, scrollXDelta, scrollYDelta);
        }
        if (hasShiftDown() || Math.abs(scrollXDelta) > Math.abs(scrollYDelta)) {
            double delta = scrollXDelta != 0.0 ? scrollXDelta : scrollYDelta;
            scrollX -= (int) Math.round(delta * 28.0);
        } else {
            scrollY -= (int) Math.round(scrollYDelta * 24.0);
        }
        clampScroll();
        return true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        syncDeviceWidgetLabels();
        syncRouteControls();
        clampScroll();
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
        graphics.fill(leftPos + 8, topPos + 58, leftPos + imageWidth - 8, topPos + footerTop() - 4, PANEL_2);
        graphics.fill(leftPos + 8, topPos + footerTop(), leftPos + imageWidth - 8, topPos + imageHeight - 9, PANEL_3);
        graphics.fill(leftPos + 8, topPos + 58, leftPos + 11, topPos + footerTop() - 8, WHITE_SIGN);
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
        } else {
            graphics.drawString(font, section.label.toUpperCase(), 13, 62, TEXT, false);
            graphics.drawString(font, fitForWidth(section.subtitle, Math.max(210, imageWidth - 120)), 92, 62, MUTED, false);
        }

        graphics.enableScissor(leftPos + 12, topPos + CONTENT_TOP, leftPos + imageWidth - 12, topPos + footerTop() - 8);
        graphics.pose().pushPose();
        graphics.pose().translate(-scrollX, -scrollY, 0.0F);
        if (routePage) {
            renderRoutePage(graphics);
        } else {
            if (section == Section.CONFIGURE && !configureWidgets.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, configureContentOffset(), 0.0F);
                renderSection(graphics, section);
                graphics.pose().popPose();
            } else {
                renderSection(graphics, section);
            }
            renderFunctionSurface(graphics);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        renderScrollIndicators(graphics);

        renderPersistentLiveStateStrip(graphics);

        String scroll = "X " + scrollX + "/" + Math.max(0, activeVirtualWidth() - viewportWidth())
                + "  •  Y " + scrollY + "/" + Math.max(0, activeVirtualHeight() - viewportHeight())
                + "  •  drag bars  •  wheel=Y  shift+wheel=X";
        String compactScroll = fitForWidth(scroll, imageWidth - 32);
        graphics.drawString(font, compactScroll, (imageWidth - font.width(compactScroll)) / 2, imageHeight - 14, MUTED, false);
    }

    private void renderPersistentLiveStateStrip(GuiGraphics graphics) {
        String state = "LIVE STATE • HEALTH " + menu.operationalHealthLabel()
                + " • ROLE " + menu.topologyRoleLabel()
                + " • EVIDENCE " + menu.evidenceStateLabel();
        String compactState = fitForWidth(state, imageWidth - 32);
        graphics.drawString(font, compactState, 16, imageHeight - 49, evidenceStateColor(), false);

        String io = "I/O • " + menu.portRouteLabel()
                + " • CONTROLS " + configureControlCount()
                + " • ROUTE " + (routeSupported() ? "ADJUSTABLE" : "FIXED");
        String compactIo = fitForWidth(io, imageWidth - 32);
        graphics.drawString(font, compactIo, 16, imageHeight - 33, routeSupported() ? INFO : MUTED, false);

        String position = fitForWidth("@ " + menu.blockPos().getX() + ", " + menu.blockPos().getY() + ", " + menu.blockPos().getZ(), 190);
        graphics.drawString(font, position, imageWidth - 16 - font.width(position), imageHeight - 49, MUTED, false);
    }

    private int horizontalTrackX0() { return 24; }
    private int horizontalTrackX1() { return imageWidth - 24; }
    private int horizontalTrackY() { return footerTop() - 8; }
    private int horizontalThumbWidth() {
        int trackWidth = Math.max(1, horizontalTrackX1() - horizontalTrackX0());
        return Math.max(28, (int) Math.round(trackWidth * (viewportWidth() / (double) activeVirtualWidth())));
    }
    private int horizontalThumbX() {
        int maxX = Math.max(0, activeVirtualWidth() - viewportWidth());
        int travel = Math.max(0, horizontalTrackX1() - horizontalTrackX0() - horizontalThumbWidth());
        return horizontalTrackX0() + (maxX == 0 ? 0 : (int) Math.round(travel * (scrollX / (double) maxX)));
    }
    private int verticalTrackX() { return imageWidth - 11; }
    private int verticalTrackY0() { return CONTENT_TOP; }
    private int verticalTrackY1() { return footerTop() - 12; }
    private int verticalThumbHeight() {
        int trackHeight = Math.max(1, verticalTrackY1() - verticalTrackY0());
        return Math.max(24, (int) Math.round(trackHeight * (viewportHeight() / (double) activeVirtualHeight())));
    }
    private int verticalThumbY() {
        int maxY = Math.max(0, activeVirtualHeight() - viewportHeight());
        int travel = Math.max(0, verticalTrackY1() - verticalTrackY0() - verticalThumbHeight());
        return verticalTrackY0() + (maxY == 0 ? 0 : (int) Math.round(travel * (scrollY / (double) maxY)));
    }

    private void renderScrollIndicators(GuiGraphics graphics) {
        int maxX = Math.max(0, activeVirtualWidth() - viewportWidth());
        int maxY = Math.max(0, activeVirtualHeight() - viewportHeight());

        if (maxX > 0) {
            int thumbX = horizontalThumbX();
            int thumbWidth = horizontalThumbWidth();
            graphics.fill(horizontalTrackX0(), horizontalTrackY(), horizontalTrackX1(), horizontalTrackY() + 3, PANEL_3);
            graphics.fill(thumbX, horizontalTrackY(), Math.min(horizontalTrackX1(), thumbX + thumbWidth),
                    horizontalTrackY() + 3, draggingHorizontalScroll ? GOOD : INFO);
        }

        if (maxY > 0) {
            int thumbY = verticalThumbY();
            int thumbHeight = verticalThumbHeight();
            graphics.fill(verticalTrackX(), verticalTrackY0(), verticalTrackX() + 3, verticalTrackY1(), PANEL_3);
            graphics.fill(verticalTrackX(), thumbY, verticalTrackX() + 3,
                    Math.min(verticalTrackY1(), thumbY + thumbHeight), draggingVerticalScroll ? GOOD : INFO);
        }
    }

    /**
     * Global all-block rollout surface.
     *
     * Every EngineeringScreen family gets the same capability inventory automatically, so
     * server-backed controls and read-only/operator boundaries cannot remain hidden in code.
     * The panel lives below the device-specific page and is reached by ordinary Y scrolling.
     */
    private void renderFunctionSurface(GuiGraphics graphics) {
        int x = CONTENT_LEFT;
        int y = section == Section.CONFIGURE ? 720 : 620;
        int width = Math.max(420, Math.min(920, canvasRight() - x - 24));
        int height = section == Section.CONFIGURE ? 168 : 118;

        graphics.fill(x, y, x + width, y + height, PANEL_3);
        graphics.fill(x, y, x + 4, y + height, INFO);
        graphics.drawString(font, "FUNCTION SURFACE • ALL-BLOCK UI CONTRACT", x + 12, y + 10, INFO, false);

        int controlCount = 0;
        List<AbstractWidget> operatorControls = new ArrayList<>();
        for (AbstractWidget widget : configureWidgets) {
            if (isLegacyRouteWidget(widget)) continue;
            controlCount++;
            operatorControls.add(widget);
        }

        if (section == Section.CONFIGURE) {
            String summary = controlCount == 0
                    ? "OPERATOR CONTROLS • READ-ONLY / OBSERVER DEVICE"
                    : "OPERATOR CONTROLS • " + controlCount + " server-routed actions";
            graphics.drawString(font, summary, x + 12, y + 29, controlCount == 0 ? MUTED : GOOD, false);

            if (operatorControls.isEmpty()) {
                wrappedText(graphics,
                        "No adjustable parameter is fabricated for this device. Model, ports, evidence and retained state remain inspectable through the other pages.",
                        x + 12, y + 49, width - 24, MUTED);
            } else {
                int rowY = y + 49;
                int columnWidth = Math.max(190, (width - 36) / 2);
                int shown = Math.min(operatorControls.size(), 12);
                for (int i = 0; i < shown; i++) {
                    AbstractWidget widget = operatorControls.get(i);
                    int column = i >= 6 ? 1 : 0;
                    int row = i % 6;
                    int xx = x + 12 + column * (columnWidth + 12);
                    int yy = rowY + row * 15;
                    String state = widget.active ? "ACTIVE" : "LOCKED";
                    int color = widget.active ? TEXT : MUTED;
                    String label = widget.getMessage().getString();
                    graphics.drawString(font,
                            fitForWidth("[" + state + "] " + label, columnWidth),
                            xx, yy, color, false);
                }
                if (operatorControls.size() > shown) {
                    graphics.drawString(font, "+" + (operatorControls.size() - shown)
                                    + " additional controls remain visible in the fixed control rail.",
                            x + 12, y + 139, MUTED, false);
                }
            }
            return;
        }

        graphics.drawString(font,
                "Configure • " + controlCount + (controlCount == 1 ? " server action" : " server actions")
                        + " • shared control rail " + configureControlRows() + " row" + (configureControlRows() == 1 ? "" : "s"),
                x + 12, y + 31, controlCount == 0 ? MUTED : GOOD, false);
        graphics.drawString(font,
                "Route • " + (routeSupported() ? "SERVER-ROUTED / ADJUSTABLE" : "FIXED PHYSICAL INTERFACE"),
                x + 12, y + 48, routeSupported() ? INFO : MUTED, false);
        graphics.drawString(font, "Ports • explicit engineering I/O contract", x + 12, y + 65, TEXT, false);
        graphics.drawString(font, "Observe • synchronized signals / topology / health", x + 12, y + 82, TEXT, false);
        graphics.drawString(font, "Log • retained evidence where the backend actually owns history", x + 12, y + 99, TEXT, false);
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
        int width = Math.max(0, canvasRight() - x);
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
            case EngineeringDeviceMenu.EVIDENCE_SATURATED, EngineeringDeviceMenu.EVIDENCE_STALE, EngineeringDeviceMenu.EVIDENCE_NOT_READY -> WARN;
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
        graphics.drawString(font, fitForWidth(normalized.label(), 150), CONTENT_LEFT, y, MUTED, false);
        graphics.drawString(font, fitForWidth(normalized.value(), canvasRight() - canvasValueX()), canvasValueX(), y, TEXT, false);
    }

    protected final void statusLine(GuiGraphics graphics, String label, String value, int color, int y) {
        if (isOperationalHealthLine(label)) {
            value = authoritativeHealthValue(label, value);
            color = operationalHealthColor();
        }
        PresentationLine normalized = normalizeLegacyPresentation(label, value);
        graphics.drawString(font, fitForWidth(normalized.label(), 150), CONTENT_LEFT, y, MUTED, false);
        graphics.drawString(font, fitForWidth(normalized.value(), canvasRight() - canvasValueX()), canvasValueX(), y, color, false);
    }

    protected final void statusBadge(GuiGraphics graphics, String value, int color, int x, int y) {
        if (isOperationalHealthBadge(value)) {
            value = authoritativeHealthBadge(value);
            color = operationalHealthColor();
        }
        int available = Math.max(24, canvasRight() - x);
        String compact = fitForWidth(value, Math.max(8, available - 12));
        int width = Math.min(available, font.width(compact) + 12);
        graphics.fill(x, y, x + width, y + 14, PANEL_3);
        graphics.fill(x, y, x + 3, y + 14, color);
        graphics.drawString(font, compact, x + 7, y + 3, color, false);
    }

    protected final void metricCard(GuiGraphics graphics, String label, String value, int x, int y, int width, int color) {
        int safeWidth = Math.max(24, Math.min(width, canvasRight() - x));
        graphics.fill(x, y, x + safeWidth, y + 31, PANEL_3);
        graphics.fill(x, y, x + 2, y + 31, color);
        graphics.drawString(font, fitForWidth(label.toUpperCase(), safeWidth - 14), x + 7, y + 5, MUTED, false);
        graphics.drawString(font, fitForWidth(value, safeWidth - 14), x + 7, y + 17, TEXT, false);
    }

    protected final void healthBadge(GuiGraphics graphics, String state, boolean healthy, int x, int y) {
        statusBadge(graphics, healthy ? "HEALTH • " + state : "ATTENTION • " + state, healthy ? GOOD : WARN, x, y);
    }

    protected final void sectionRule(GuiGraphics graphics, int y) {
        graphics.fill(CONTENT_LEFT, y, canvasRight(), y + 1, 0xFF3A4650);
    }

    protected final void signalBar(GuiGraphics graphics, int value, int y) {
        int bounded = Math.max(0, Math.min(15, value));
        int x0 = CONTENT_LEFT;
        int x1 = Math.max(286, Math.min(canvasRight() - 24, CONTENT_LEFT + 700));
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
        graphics.drawString(font, bounded + " / 15", x1 - 42, y - 10, TEXT, false);
    }

    /**
     * Rollout primitive: render a governing equation without truncation.
     * Long equations intentionally expand the virtual page instead of being collapsed into prose.
     */
    protected final void formulaCard(GuiGraphics graphics, String equation, int y) {
        int width = Math.max(320, Math.min(activeVirtualWidth() - CONTENT_LEFT - 24, font.width(equation) + 32));
        graphics.fill(CONTENT_LEFT, y - 4, CONTENT_LEFT + width, y + 15, PANEL_3);
        graphics.fill(CONTENT_LEFT, y - 4, CONTENT_LEFT + 3, y + 15, ACCENT);
        graphics.drawString(font, equation, CONTENT_LEFT + 10, y, TEXT, false);
    }

    /** Rollout primitive for explicit variable ownership and engineering units. */
    protected final void variableRole(
            GuiGraphics graphics, String role, String symbol, String value, String units, int y
    ) {
        String name = "[" + role + "] " + symbol;
        String rendered = (value == null || value.isBlank() ? "—" : value)
                + (units == null || units.isBlank() ? "" : " " + units);
        graphics.drawString(font, name, CONTENT_LEFT, y, MUTED, false);
        graphics.drawString(font, rendered, canvasValueX(), y, TEXT, false);
    }

    /** Rollout primitive for baseline/candidate or expected/actual evidence comparisons. */
    protected final void evidenceRow(
            GuiGraphics graphics, String label, String leftValue, String rightValue, String note, int y
    ) {
        int x1 = canvasValueX();
        int available = Math.max(360, canvasRight() - x1);
        int x2 = x1 + Math.max(120, available / 3);
        int x3 = x2 + Math.max(120, available / 3);
        graphics.drawString(font, label, CONTENT_LEFT, y, MUTED, false);
        graphics.drawString(font, leftValue, x1, y, TEXT, false);
        graphics.drawString(font, rightValue, x2, y, TEXT, false);
        graphics.drawString(font, note, x3, y, INFO, false);
    }

    /** Multi-line text for model/evidence pages; unlike legacy safeText it never inserts ellipses. */
    protected final int wrappedText(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        int yy = y;
        for (var line : font.split(Component.literal(text), Math.max(80, width))) {
            graphics.drawString(font, line, x, yy, color, false);
            yy += 11;
        }
        return yy;
    }

    protected final int workspaceRight() { return canvasRight(); }
    protected final int workspaceWidth() { return canvasRight() - CONTENT_LEFT; }

    protected abstract void renderSection(GuiGraphics graphics, Section section);
}
