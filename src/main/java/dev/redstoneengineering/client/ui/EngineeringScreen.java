package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import dev.redstoneengineering.ui.menu.EngineeringDeviceMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Shared RSE engineering visual language. */
public abstract class EngineeringScreen<M extends EngineeringDeviceMenu> extends AbstractContainerScreen<M> {
    protected enum Section {
        OVERVIEW("Live", "Live engineering state"),
        PORTS("Ports", "Physical I/O contract"),
        CONFIGURE("Config", "Parameters, modes and actions"),
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

    private static final int CONTENT_LEFT = 16;
    private static final int CONTENT_RIGHT = 304;
    private static final int VALUE_X = 154;
    private static final int FOOTER_TOP = 245;
    private static final int ROUTE_CONTROL_Y = 196;
    private static final int ROUTE_ENDPOINT_Y = 174;

    private Section section = Section.OVERVIEW;
    private boolean routePage;
    private boolean workbenchPage;
    private Button workbenchTab;
    private static final int TIMELINE_SAMPLES = 48;
    private final int[] evidenceTimeline = new int[TIMELINE_SAMPLES];
    private final int[] healthTimeline = new int[TIMELINE_SAMPLES];
    private int timelineCount;
    private final List<AbstractWidget> configureWidgets = new ArrayList<>();
    private final List<Button> sectionButtons = new ArrayList<>();
    private Button routeTab;
    private Button routePrevious;
    private Button routeNext;
    private Button routeInputPrevious;
    private Button routeInputNext;
    private Button routeOutputPrevious;
    private Button routeOutputNext;

    private EditBox workbenchTarget;
    private Button workbenchParameterPrevious;
    private Button workbenchParameterNext;
    private Button workbenchDecrease;
    private Button workbenchIncrease;
    private Button workbenchApply;
    private Button workbenchMin;
    private Button workbenchMax;
    private Button workbenchQuarter;
    private Button workbenchMid;
    private Button workbenchThreeQuarter;
    private Button workbenchSweep;
    private int workbenchParameterIndex;
    private boolean workbenchSweepActive;
    private int workbenchSweepDelay;
    private static final int WORKBENCH_SWEEP_POINTS = 64;
    private static final int INVALID_SWEEP_SAMPLE = Integer.MIN_VALUE;
    private final int[] workbenchSweepParameters = new int[WORKBENCH_SWEEP_POINTS];
    private final int[] workbenchSweepResponses = new int[WORKBENCH_SWEEP_POINTS];
    private int workbenchSweepPointCount;

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
        routeTab = null;
        workbenchTab = null;
        routePrevious = null;
        routeNext = null;
        routeInputPrevious = null;
        routeInputNext = null;
        routeOutputPrevious = null;
        routeOutputNext = null;
        workbenchTarget = null;
        workbenchParameterPrevious = null;
        workbenchParameterNext = null;
        workbenchDecrease = null;
        workbenchIncrease = null;
        workbenchApply = null;
        workbenchMin = null;
        workbenchMax = null;
        workbenchQuarter = null;
        workbenchMid = null;
        workbenchThreeQuarter = null;
        workbenchSweep = null;
        workbenchSweepActive = false;
        workbenchSweepDelay = 0;
        workbenchSweepPointCount = 0;

        int tabY = topPos + 31;
        int x = leftPos + 8;
        int tabWidth = 42;
        int gap = 1;

        addSectionTab(Section.OVERVIEW, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.PORTS, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.CONFIGURE, x, tabY, tabWidth); x += tabWidth + gap;
        routeTab = addRenderableWidget(Button.builder(Component.literal("Route"), button -> setRoutePage())
                .bounds(x, tabY, tabWidth, 20).build()); x += tabWidth + gap;
        EngineeringWorkbenchCatalog.UiPolicy initialPolicy = EngineeringWorkbenchCatalog.uiPolicy(menu);
        workbenchTab = addRenderableWidget(Button.builder(Component.literal(initialPolicy.pageLabel()), button -> setWorkbenchPage())
                .bounds(x, tabY, tabWidth, 20).build()); x += tabWidth + gap;
        addSectionTab(Section.DIAGNOSTICS, x, tabY, tabWidth); x += tabWidth + gap;
        addSectionTab(Section.HISTORY, x, tabY, tabWidth);

        addDeviceWidgets();
        addRouteControls();
        addWorkbenchControls();
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
        syncWorkbenchControls();
    }

    private void addSectionTab(Section target, int x, int y, int width) {
        Button tab = Button.builder(Component.literal(target.label), button -> setSection(target))
                .bounds(x, y, width, 20).build();
        sectionButtons.add(addRenderableWidget(tab));
    }

    protected void addDeviceWidgets() {}
    protected void syncDeviceWidgetLabels() {}

    protected final <T extends AbstractWidget> T addConfigureWidget(T widget) {
        configureWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    protected final void sendMenuButton(int buttonId) {
        if (buttonId < 0) return;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void addRouteControls() {
        int width = 136;
        routePrevious = addRenderableWidget(Button.builder(
                Component.literal("Direction ▲"), button -> sendMenuButton(routeActionId(false)))
                .bounds(leftPos + CONTENT_LEFT, topPos + ROUTE_CONTROL_Y, width, 20).build());
        routeNext = addRenderableWidget(Button.builder(
                Component.literal("Direction ▼"), button -> sendMenuButton(routeActionId(true)))
                .bounds(leftPos + CONTENT_RIGHT - width, topPos + ROUTE_CONTROL_Y, width, 20).build());

        int endpointWidth = 66;
        int endpointGap = 6;
        int x0 = leftPos + CONTENT_LEFT;
        routeInputPrevious = addRenderableWidget(Button.builder(
                Component.literal("RX ▲"), button -> sendMenuButton(routeInputActionId(false)))
                .bounds(x0, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
        routeInputNext = addRenderableWidget(Button.builder(
                Component.literal("RX ▼"), button -> sendMenuButton(routeInputActionId(true)))
                .bounds(x0 + endpointWidth + endpointGap, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
        routeOutputPrevious = addRenderableWidget(Button.builder(
                Component.literal("TX ▲"), button -> sendMenuButton(routeOutputActionId(false)))
                .bounds(x0 + (endpointWidth + endpointGap) * 2, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
        routeOutputNext = addRenderableWidget(Button.builder(
                Component.literal("TX ▼"), button -> sendMenuButton(routeOutputActionId(true)))
                .bounds(x0 + (endpointWidth + endpointGap) * 3, topPos + ROUTE_ENDPOINT_Y, endpointWidth, 20).build());
    }

    private void addWorkbenchControls() {
        int y = topPos + 198;
        workbenchParameterPrevious = addRenderableWidget(Button.builder(
                Component.literal("◀ P"), button -> selectWorkbenchParameter(-1))
                .bounds(leftPos + 16, y, 34, 20).build());
        workbenchDecrease = addRenderableWidget(Button.builder(
                Component.literal("−1"), button -> stepWorkbenchParameter(false))
                .bounds(leftPos + 54, y, 38, 20).build());
        workbenchTarget = addRenderableWidget(new EditBox(
                font, leftPos + 96, y, 56, 20, Component.literal("Parameter target")));
        workbenchTarget.setMaxLength(5);
        workbenchTarget.setFilter(EngineeringScreen::numericTargetText);
        workbenchApply = addRenderableWidget(Button.builder(
                Component.literal("Apply"), button -> applyWorkbenchTarget())
                .bounds(leftPos + 156, y, 48, 20).build());
        workbenchIncrease = addRenderableWidget(Button.builder(
                Component.literal("+1"), button -> stepWorkbenchParameter(true))
                .bounds(leftPos + 208, y, 38, 20).build());
        workbenchParameterNext = addRenderableWidget(Button.builder(
                Component.literal("P ▶"), button -> selectWorkbenchParameter(1))
                .bounds(leftPos + 250, y, 54, 20).build());

        int presetY = topPos + 221;
        workbenchMin = addRenderableWidget(Button.builder(
                Component.literal("Min"), button -> applyWorkbenchBound(false))
                .bounds(leftPos + 16, presetY, 42, 18).build());
        workbenchQuarter = addRenderableWidget(Button.builder(
                Component.literal("25%"), button -> applyWorkbenchFraction(0.25))
                .bounds(leftPos + 61, presetY, 42, 18).build());
        workbenchMid = addRenderableWidget(Button.builder(
                Component.literal("50%"), button -> applyWorkbenchFraction(0.50))
                .bounds(leftPos + 106, presetY, 42, 18).build());
        workbenchThreeQuarter = addRenderableWidget(Button.builder(
                Component.literal("75%"), button -> applyWorkbenchFraction(0.75))
                .bounds(leftPos + 151, presetY, 42, 18).build());
        workbenchMax = addRenderableWidget(Button.builder(
                Component.literal("Max"), button -> applyWorkbenchBound(true))
                .bounds(leftPos + 196, presetY, 42, 18).build());
        workbenchSweep = addRenderableWidget(Button.builder(
                Component.literal("Sweep ↑"), button -> toggleWorkbenchSweep())
                .bounds(leftPos + 241, presetY, 63, 18).build());
    }

    private static boolean numericTargetText(String value) {
        if (value == null || value.isEmpty()) return true;
        if (value.length() > 5) return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == 1 && value.length() == 1) return true;
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private List<EngineeringWorkbenchCatalog.ParameterSpec> workbenchParameters() {
        return EngineeringWorkbenchCatalog.parameters(menu);
    }

    private EngineeringWorkbenchCatalog.ParameterSpec activeWorkbenchParameter() {
        List<EngineeringWorkbenchCatalog.ParameterSpec> specs = workbenchParameters();
        if (specs.isEmpty()) return null;
        workbenchParameterIndex = Math.floorMod(workbenchParameterIndex, specs.size());
        return specs.get(workbenchParameterIndex);
    }

    private void selectWorkbenchParameter(int delta) {
        List<EngineeringWorkbenchCatalog.ParameterSpec> specs = workbenchParameters();
        if (specs.isEmpty()) return;
        workbenchSweepActive = false;
        workbenchSweepPointCount = 0;
        workbenchParameterIndex = Math.floorMod(workbenchParameterIndex + delta, specs.size());
        if (workbenchTarget != null) workbenchTarget.setFocused(false);
        syncWorkbenchControls();
    }

    private void stepWorkbenchParameter(boolean increase) {
        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        if (spec == null) return;
        sendMenuButton(increase ? spec.incrementButton() : spec.decrementButton());
    }

    private void applyWorkbenchBound(boolean maximum) {
        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        if (spec == null) return;
        int target = maximum ? spec.maximum() : spec.minimum();
        if (workbenchTarget != null) workbenchTarget.setValue(Integer.toString(target));
        applyWorkbenchTargetValue(spec, target);
    }

    private void applyWorkbenchFraction(double fraction) {
        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        if (spec == null || !spec.fractionPresets()) return;
        int span = spec.maximum() - spec.minimum();
        int target = spec.minimum() + (int) Math.round(span * Math.max(0.0, Math.min(1.0, fraction)));
        if (workbenchTarget != null) workbenchTarget.setValue(Integer.toString(target));
        applyWorkbenchTargetValue(spec, target);
    }

    private int workbenchSweepDwellTicks() {
        return EngineeringWorkbenchCatalog.recommendedSweepDwellTicks(menu);
    }

    private void toggleWorkbenchSweep() {
        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        EngineeringWorkbenchCatalog.UiPolicy policy = EngineeringWorkbenchCatalog.uiPolicy(menu);
        EngineeringWorkbenchCatalog.ResponseSpec response = EngineeringWorkbenchCatalog.response(menu);
        if (spec == null || !policy.experimental() || !spec.sweepMeaningful()
                || response == null || spec.maximum() <= spec.minimum()) {
            workbenchSweepActive = false;
            return;
        }
        if (workbenchSweepActive) {
            workbenchSweepActive = false;
            return;
        }
        workbenchSweepActive = true;
        workbenchSweepPointCount = 0;
        workbenchSweepDelay = workbenchSweepDwellTicks();
        applyWorkbenchTargetValue(spec, spec.minimum());
    }

    private void tickWorkbenchSweep() {
        if (!workbenchSweepActive || !workbenchPage) return;
        EngineeringWorkbenchCatalog.UiPolicy policy = EngineeringWorkbenchCatalog.uiPolicy(menu);
        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        if (!policy.experimental() || spec == null || !spec.sweepMeaningful()) {
            workbenchSweepActive = false;
            return;
        }
        if (workbenchSweepDelay > 0) {
            workbenchSweepDelay--;
            return;
        }

        captureWorkbenchSweepPoint(spec);
        if (spec.current() >= spec.maximum()) {
            workbenchSweepActive = false;
            return;
        }

        sendMenuButton(spec.incrementButton());
        workbenchSweepDelay = workbenchSweepDwellTicks();
    }

    private void captureWorkbenchSweepPoint(EngineeringWorkbenchCatalog.ParameterSpec spec) {
        EngineeringWorkbenchCatalog.ResponseSpec response = EngineeringWorkbenchCatalog.response(menu);
        if (response == null) return;

        int parameter = spec.current();
        int measured = response.usable() ? response.value() : INVALID_SWEEP_SAMPLE;

        if (workbenchSweepPointCount > 0
                && workbenchSweepParameters[workbenchSweepPointCount - 1] == parameter) {
            workbenchSweepResponses[workbenchSweepPointCount - 1] = measured;
            return;
        }

        if (workbenchSweepPointCount >= WORKBENCH_SWEEP_POINTS) {
            System.arraycopy(workbenchSweepParameters, 1, workbenchSweepParameters, 0, WORKBENCH_SWEEP_POINTS - 1);
            System.arraycopy(workbenchSweepResponses, 1, workbenchSweepResponses, 0, WORKBENCH_SWEEP_POINTS - 1);
            workbenchSweepPointCount = WORKBENCH_SWEEP_POINTS - 1;
        }
        workbenchSweepParameters[workbenchSweepPointCount] = parameter;
        workbenchSweepResponses[workbenchSweepPointCount] = measured;
        workbenchSweepPointCount++;
    }

    private void applyWorkbenchTarget() {
        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        if (spec == null || workbenchTarget == null) return;
        try {
            int target = Integer.parseInt(workbenchTarget.getValue());
            if (target < spec.minimum() || target > spec.maximum()) {
                workbenchTarget.setTextColor(BAD);
                return;
            }
            workbenchTarget.setTextColor(TEXT);
            applyWorkbenchTargetValue(spec, target);
        } catch (NumberFormatException ignored) {
            workbenchTarget.setTextColor(BAD);
        }
    }

    private void applyWorkbenchTargetValue(EngineeringWorkbenchCatalog.ParameterSpec spec, int target) {
        int delta = target - spec.current();
        int steps = Math.min(1024, Math.abs(delta));
        int button = delta >= 0 ? spec.incrementButton() : spec.decrementButton();
        for (int i = 0; i < steps; i++) sendMenuButton(button);
    }

    private void syncWorkbenchControls() {
        EngineeringWorkbenchCatalog.UiPolicy policy = EngineeringWorkbenchCatalog.uiPolicy(menu);
        List<EngineeringWorkbenchCatalog.ParameterSpec> specs = workbenchParameters();
        boolean showEditor = workbenchPage && policy.configurable() && !specs.isEmpty();
        if (!specs.isEmpty()) workbenchParameterIndex = Math.floorMod(workbenchParameterIndex, specs.size());
        EngineeringWorkbenchCatalog.ParameterSpec spec = specs.isEmpty() ? null : specs.get(workbenchParameterIndex);
        EngineeringWorkbenchCatalog.ResponseSpec response = EngineeringWorkbenchCatalog.response(menu);

        if (workbenchTab != null) workbenchTab.setMessage(Component.literal(policy.pageLabel()));

        boolean choice = showEditor && spec != null
                && spec.control() == EngineeringWorkbenchCatalog.ParameterControl.CHOICE;
        boolean numeric = showEditor && spec != null && !choice;
        boolean showFractionPresets = numeric && spec.fractionPresets();
        boolean showSweep = numeric && spec != null && policy.experimental()
                && spec.control() == EngineeringWorkbenchCatalog.ParameterControl.EXPERIMENT
                && spec.sweepMeaningful() && response != null;

        if (workbenchParameterPrevious != null) workbenchParameterPrevious.visible = showEditor;
        if (workbenchParameterNext != null) workbenchParameterNext.visible = showEditor;
        if (workbenchDecrease != null) workbenchDecrease.visible = showEditor;
        if (workbenchIncrease != null) workbenchIncrease.visible = showEditor;
        if (workbenchTarget != null) workbenchTarget.visible = numeric;
        if (workbenchApply != null) workbenchApply.visible = numeric;
        if (workbenchMin != null) workbenchMin.visible = numeric;
        if (workbenchMax != null) workbenchMax.visible = numeric;
        if (workbenchQuarter != null) workbenchQuarter.visible = showFractionPresets;
        if (workbenchMid != null) workbenchMid.visible = showFractionPresets;
        if (workbenchThreeQuarter != null) workbenchThreeQuarter.visible = showFractionPresets;
        if (workbenchSweep != null) workbenchSweep.visible = showSweep;

        if (!showSweep) {
            workbenchSweepActive = false;
            workbenchSweepDelay = 0;
        }
        if (!showEditor || spec == null) return;

        boolean multiple = specs.size() > 1;
        workbenchParameterPrevious.active = multiple;
        workbenchParameterNext.active = multiple;
        workbenchParameterPrevious.setMessage(Component.literal("◀ P" + (workbenchParameterIndex + 1)));
        workbenchParameterNext.setMessage(Component.literal("P" + (workbenchParameterIndex + 1) + " ▶"));
        workbenchParameterPrevious.setTooltip(Tooltip.create(Component.literal("Previous block-owned parameter")));
        workbenchParameterNext.setTooltip(Tooltip.create(Component.literal("Next block-owned parameter")));
        String symbol = EngineeringWorkbenchCatalog.parameterSymbol(menu, spec);
        int equationIndex = EngineeringWorkbenchCatalog.parameterEquationIndex(menu, spec);
        if (choice) {
            if ("P".equals(symbol)) {
                workbenchDecrease.setMessage(Component.literal("◀ Prev"));
                workbenchIncrease.setMessage(Component.literal("Next ▶"));
            } else {
                workbenchDecrease.setMessage(Component.literal("◀" + symbol));
                workbenchIncrease.setMessage(Component.literal(symbol + "▶"));
            }
            workbenchDecrease.setTooltip(Tooltip.create(Component.literal(
                    "Previous " + spec.label() + " • changes " + symbol + " in Eq." + equationIndex)));
            workbenchIncrease.setTooltip(Tooltip.create(Component.literal(
                    "Next " + spec.label() + " • changes " + symbol + " in Eq." + equationIndex)));
        } else {
            workbenchDecrease.setMessage(Component.literal("−" + symbol));
            workbenchIncrease.setMessage(Component.literal("+" + symbol));
            workbenchDecrease.setTooltip(Tooltip.create(Component.literal(
                    spec.label() + " fine -1 • changes " + symbol + " in Eq." + equationIndex)));
            workbenchIncrease.setTooltip(Tooltip.create(Component.literal(
                    spec.label() + " fine +1 • changes " + symbol + " in Eq." + equationIndex)));
        }
        if (numeric) {
            workbenchApply.setTooltip(Tooltip.create(Component.literal(
                    "Apply exact bounded target " + spec.minimum() + ".." + spec.maximum()
                            + " using the existing server-authoritative block action.")));
            workbenchMin.setMessage(Component.literal("Min"));
            workbenchMax.setMessage(Component.literal("Max"));
        }

        if (showFractionPresets) {
            workbenchQuarter.setTooltip(Tooltip.create(Component.literal("Apply 25% of this numeric range.")));
            workbenchMid.setTooltip(Tooltip.create(Component.literal("Apply midpoint of this numeric range.")));
            workbenchThreeQuarter.setTooltip(Tooltip.create(Component.literal("Apply 75% of this numeric range.")));
        }
        if (showSweep) {
            workbenchSweep.setMessage(Component.literal(workbenchSweepActive ? "Stop" : "Sweep ↑"));
            workbenchSweep.setTooltip(Tooltip.create(Component.literal(
                    "Measure a real parameter-response sweep with " + workbenchSweepDwellTicks()
                            + " ticks dwell per point for this experiment type. Categorical modes/channels never receive this control.")));
        }

        if (workbenchTarget != null && !workbenchTarget.isFocused()) {
            workbenchTarget.setValue(Integer.toString(spec.current()));
            workbenchTarget.setTextColor(TEXT);
        }
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
        if (menu instanceof PidControllerMenu) return clockwise ? PidControllerMenu.BUTTON_INPUT_NEXT : PidControllerMenu.BUTTON_INPUT_PREVIOUS;
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
        if (menu instanceof PidControllerMenu) return clockwise ? PidControllerMenu.BUTTON_OUTPUT_NEXT : PidControllerMenu.BUTTON_OUTPUT_PREVIOUS;
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
        if (menu instanceof PidControllerMenu) return true;
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
        if (menu instanceof PidControllerMenu) return true;
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
        if (menu instanceof PidControllerMenu) return true;
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
        this.workbenchPage = false;
        this.workbenchSweepActive = false;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
        syncWorkbenchControls();
    }

    private void setRoutePage() {
        routePage = true;
        workbenchPage = false;
        workbenchSweepActive = false;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
        syncWorkbenchControls();
    }

    private void setWorkbenchPage() {
        routePage = false;
        workbenchPage = true;
        updateWidgetVisibility();
        syncDeviceWidgetLabels();
        syncRouteControls();
        syncWorkbenchControls();
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
        for (int i = 0; i < sectionButtons.size(); i++) {
            sectionButtons.get(i).active = routePage || workbenchPage || tabSections[i] != section;
        }
        if (routeTab != null) routeTab.active = !routePage;
        if (workbenchTab != null) workbenchTab.active = !workbenchPage;
        syncWorkbenchControls();
    }

    protected final boolean isConfigureSection() { return !routePage && !workbenchPage && section == Section.CONFIGURE; }
    protected final boolean isWorkbenchPage() { return workbenchPage; }
    public final boolean showsPortVisualization() { return routePage || section == Section.PORTS; }

    @Override
    protected void containerTick() {
        super.containerTick();
        recordSharedTimeline();
        tickWorkbenchSweep();
        syncDeviceWidgetLabels();
        syncRouteControls();
        syncWorkbenchControls();
    }

    private void recordSharedTimeline() {
        if (EngineeringWorkbenchCatalog.uiPolicy(menu).tier() == EngineeringWorkbenchCatalog.UiTier.BLOCK) return;
        int evidence = menu.evidenceState() == EngineeringDeviceMenu.EVIDENCE_VALID ? 1 : 0;
        int health = menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_NOMINAL
                || menu.operationalHealth() == EngineeringDeviceMenu.HEALTH_ACTIVE ? 1 : 0;
        if (timelineCount < TIMELINE_SAMPLES) {
            evidenceTimeline[timelineCount] = evidence;
            healthTimeline[timelineCount] = health;
            timelineCount++;
            return;
        }
        System.arraycopy(evidenceTimeline, 1, evidenceTimeline, 0, TIMELINE_SAMPLES - 1);
        System.arraycopy(healthTimeline, 1, healthTimeline, 0, TIMELINE_SAMPLES - 1);
        evidenceTimeline[TIMELINE_SAMPLES - 1] = evidence;
        healthTimeline[TIMELINE_SAMPLES - 1] = health;
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

        if (routePage) {
            graphics.drawString(font, "ROUTE", 13, 62, TEXT, false);
            graphics.drawString(font, "Direct RX / TX direction control", 92, 62, MUTED, false);
            renderRoutePage(graphics);
        } else if (workbenchPage) {
            EngineeringWorkbenchCatalog.UiPolicy policy = EngineeringWorkbenchCatalog.uiPolicy(menu);
            graphics.drawString(font, policy.pageLabel(), 13, 62, TEXT, false);
            String subtitle = switch (policy.tier()) {
                case BLOCK -> "Role, route and current evidence";
                case DEVICE -> "Model, settings and evidence boundary";
                case LAB -> "Model, tuning and measured response";
            };
            graphics.drawString(font, fitForWidth(subtitle, 210), 92, 62, MUTED, false);
            renderWorkbenchPage(graphics);
        } else {
            graphics.drawString(font, section.label.toUpperCase(), 13, 62, TEXT, false);
            graphics.drawString(font, fitForWidth(section.subtitle, 210), 92, 62, MUTED, false);
            renderSection(graphics, section);
        }

        String evidence = fitForWidth("EVIDENCE • " + menu.evidenceStateLabel(), 150);
        graphics.drawString(font, evidence, 13, imageHeight - 20, evidenceStateColor(), false);
        String position = fitForWidth("@ " + menu.blockPos().getX() + ", " + menu.blockPos().getY() + ", " + menu.blockPos().getZ(), 142);
        graphics.drawString(font, position, imageWidth - 13 - font.width(position), imageHeight - 20, MUTED, false);
    }

    protected void renderWorkbenchPage(GuiGraphics graphics) {
        EngineeringWorkbenchCatalog.UiPolicy policy = EngineeringWorkbenchCatalog.uiPolicy(menu);
        EngineeringWorkbenchCatalog.ModelCard model = EngineeringWorkbenchCatalog.describe(menu);
        List<EngineeringWorkbenchCatalog.ParameterSpec> specs = workbenchParameters();

        statusBadge(graphics, policy.tier().name() + " • " + model.family(), INFO, 16, 80);
        statusBadge(graphics, menu.evidenceStateLabel(), evidenceStateColor(), 222, 80);

        if (policy.tier() == EngineeringWorkbenchCatalog.UiTier.BLOCK) {
            labelValue(graphics, "Block role", menu.topologyRoleLabel(), 108);
            labelValue(graphics, "Physical route", menu.portRouteLabel(), 128);
            labelValue(graphics, "Evidence", menu.evidenceStateLabel(), 148);
            labelValue(graphics, "Health", menu.operationalHealthLabel(), 168);
            sectionRule(graphics, 187);
            safeWrappedText(graphics, policy.rationale(), 16, 196, MUTED, 3);
            safeText(graphics, "No sweep, no desktop-style experiment workflow: inspect the world wiring first.",
                    16, 228, INFO);
            return;
        }

        if (policy.tier() == EngineeringWorkbenchCatalog.UiTier.LAB) {
            renderLabWorkbench(graphics, model, specs);
            return;
        }

        List<String> equations = EngineeringWorkbenchCatalog.physicsEquations(menu);
        graphics.drawString(font, "PHYSICS • Formula / relation", 16, 103, INFO, false);
        if (!equations.isEmpty()) {
            graphics.drawString(font, "Eq.1", 16, 115, MUTED, false);
            safeText(graphics, equations.get(0), 48, 115, TEXT);
        }
        if (equations.size() > 1) {
            graphics.drawString(font, "Eq.2", 16, 127, MUTED, false);
            safeText(graphics, equations.get(1), 48, 127, TEXT);
        }
        graphics.drawString(font, "Process", 16, 141, MUTED, false);
        safeText(graphics, model.process(), 67, 141, TEXT);
        sectionRule(graphics, 154);
        safeText(graphics, "TERMS • " + model.parameters(), 16, 160, MUTED);

        if (!specs.isEmpty()) {
            EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
            if (spec != null) {
                String index = "PARAM " + (workbenchParameterIndex + 1) + "/" + specs.size();
                graphics.drawString(font, index, 16, 173, INFO, false);
                String controlTag = switch (spec.control()) {
                    case CHOICE -> "CHOICE";
                    case RANGE -> "RANGE";
                    case EXPERIMENT -> "EXPERIMENT";
                };
                String value = controlTag + " • " + spec.label() + " = " + spec.current()
                        + (spec.unit().isBlank() ? "" : " " + spec.unit())
                        + " [" + spec.minimum() + ".." + spec.maximum() + "]";
                graphics.drawString(font, fitForWidth(value, 222), 82, 173, TEXT, false);

                String symbol = EngineeringWorkbenchCatalog.parameterSymbol(menu, spec);
                int equationIndex = EngineeringWorkbenchCatalog.parameterEquationIndex(menu, spec);
                String binding = "CONTROL → " + symbol + " in Eq." + equationIndex
                        + " • buttons below change this server-owned term";
                safeText(graphics, binding, 16, 184, GOOD);

                int barX = 16, barY = 193, barW = 288, barH = 3;
                graphics.fill(barX, barY, barX + barW, barY + barH, PANEL_3);
                int span = Math.max(1, spec.maximum() - spec.minimum());
                int clamped = Math.max(spec.minimum(), Math.min(spec.maximum(), spec.current()));
                int filled = (int) Math.round((clamped - spec.minimum()) * barW / (double) span);
                graphics.fill(barX, barY, barX + filled, barY + barH, INFO);
                int markerX = barX + Math.max(0, Math.min(barW - 1, filled));
                graphics.fill(markerX, barY - 1, markerX + 1, barY + barH + 1, TEXT);

                if (!spec.detail().isBlank() && workbenchTarget != null) {
                    workbenchTarget.setTooltip(Tooltip.create(Component.literal(
                            spec.detail() + " • Changes " + symbol + " in Eq." + equationIndex + ".")));
                }
            }
        } else {
            labelValue(graphics, "Block-owned parameters", "NONE / READ ONLY", 177);
            safeText(graphics, model.boundary(), 16, 189, MUTED);
        }
    }

    private void renderLabWorkbench(
            GuiGraphics graphics,
            EngineeringWorkbenchCatalog.ModelCard model,
            List<EngineeringWorkbenchCatalog.ParameterSpec> specs
    ) {
        if (renderSignalShowcaseLab(graphics)) return;
        EngineeringWorkbenchCatalog.LabProfile lab = EngineeringWorkbenchCatalog.labProfile(menu);
        List<String> equations = EngineeringWorkbenchCatalog.physicsEquations(menu);
        graphics.drawString(font, "PHYSICS • Formula / relation", 16, 103, INFO, false);
        if (!equations.isEmpty()) {
            graphics.drawString(font, "Eq.1", 16, 115, MUTED, false);
            safeText(graphics, equations.get(0), 48, 115, TEXT);
        }
        if (equations.size() > 1) {
            graphics.drawString(font, "Eq.2", 16, 127, MUTED, false);
            safeText(graphics, equations.get(1), 48, 127, TEXT);
        }

        safeText(graphics, "TERMS • " + model.parameters(), 16, 139, MUTED);

        if (lab == null) {
            safeText(graphics, "LAB profile unavailable • use Live / Observe for current server evidence.", 16, 151, WARN);
            return;
        }

        String experimentLabel = "Experiment • " + EngineeringWorkbenchCatalog.experimentKind(menu).name().replace('_', ' ');
        graphics.drawString(font, fitForWidth(experimentLabel, 108), 16, 150, INFO, false);
        safeText(graphics, lab.question(), 128, 150, TEXT);

        EngineeringWorkbenchCatalog.ParameterSpec spec = activeWorkbenchParameter();
        EngineeringWorkbenchCatalog.ResponseSpec response = EngineeringWorkbenchCatalog.response(menu);
        if (spec != null) {
            String symbol = EngineeringWorkbenchCatalog.parameterSymbol(menu, spec);
            int equationIndex = EngineeringWorkbenchCatalog.parameterEquationIndex(menu, spec);
            safeText(graphics, "CONTROL → " + spec.label() + " = " + symbol + " in Eq." + equationIndex
                    + " • X=" + lab.independentVariable() + " • Y=" + lab.dependentVariable(), 16, 160, GOOD);
        } else {
            safeText(graphics, "X • " + lab.independentVariable() + "    Y • " + lab.dependentVariable(), 16, 160, MUTED);
        }
        boolean showSweepPlot = spec != null && spec.sweepMeaningful() && response != null
                && (workbenchSweepActive || workbenchSweepPointCount > 0);

        if (showSweepPlot) {
            int plotX = 16, plotY = 170, plotW = 288, plotH = 22;
            EngineeringPlot.analogFrame(graphics, plotX, plotY, plotW, plotH);
            EngineeringPlot.xyTrace(graphics, workbenchSweepPointCount,
                    i -> workbenchSweepParameters[i],
                    i -> workbenchSweepResponses[i],
                    spec.minimum(), spec.maximum(),
                    response.minimum(), response.maximum(),
                    plotX + 3, plotY + 3, plotW - 6, plotH - 6, GOOD);
            String caption = "measured " + spec.label() + " → " + response.label()
                    + " • " + workbenchSweepPointCount + " pts";
            graphics.drawString(font, fitForWidth(caption, 270), 22, 171,
                    workbenchSweepActive ? INFO : GOOD, false);
        } else {
            List<EngineeringWorkbenchCatalog.LabMetric> metrics = lab.metrics();
            int[] xs = {16, 111, 206};
            for (int i = 0; i < Math.min(3, metrics.size()); i++) {
                EngineeringWorkbenchCatalog.LabMetric metric = metrics.get(i);
                metricCard(graphics, metric.label(), metric.value(), xs[i], 166, 88, i == 1 ? GOOD : INFO);
            }
        }

        if (spec != null && workbenchTarget != null) {
            String detail = spec.detail();
            if (!lab.note().isBlank()) detail = detail + " • " + lab.note();
            workbenchTarget.setTooltip(Tooltip.create(Component.literal(detail)));
        }
    }

    private boolean renderSignalShowcaseLab(GuiGraphics graphics) {
        if (!(menu instanceof UniversalFieldDeviceMenu universal)) return false;
        int kind = universal.configKind();
        boolean lpf = kind == UniversalFieldDeviceMenu.CONFIG_LAPIS_LOW_PASS;
        boolean clock = kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_LAB_OSCILLATOR;
        boolean sampler = kind == UniversalFieldDeviceMenu.CONFIG_QUARTZ_LAPIS_SAMPLER;
        if (!lpf && !clock && !sampler) return false;

        EngineeringWorkbenchCatalog.LabProfile lab = EngineeringWorkbenchCatalog.labProfile(menu);
        List<String> equations = EngineeringWorkbenchCatalog.physicsEquations(menu);

        String title = lpf ? "LOW-PASS FILTER"
                : clock ? "QUARTZ CLOCK"
                : "SAMPLE & HOLD";
        statusBadge(graphics, title, INFO, 16, 103);

        // Showcase rule: one complete equation, never ellipsized.
        if (!equations.isEmpty()) {
            graphics.drawString(font, "MODEL", 16, 126, MUTED, false);
            safeWrappedText(graphics, equations.get(0), 58, 126, TEXT, 2);
        }

        if (lab != null) {
            List<EngineeringWorkbenchCatalog.LabMetric> metrics = lab.metrics();
            int[] xs = {16, 111, 206};
            for (int i = 0; i < Math.min(3, metrics.size()); i++) {
                EngineeringWorkbenchCatalog.LabMetric metric = metrics.get(i);
                metricCard(graphics, metric.label(), metric.value(), xs[i], 157, 88, i == 1 ? GOOD : INFO);
            }

            if (lpf) {
                safeText(graphics, "Meaning: each update removes alpha of the current input-output error.", 16, 202, INFO);
            } else if (clock) {
                safeText(graphics, "Meaning: period sets cadence; jitter shifts the next real edge within its bound.", 16, 202, INFO);
            } else {
                safeText(graphics, "Meaning: only a genuine rising edge may replace the held sample.", 16, 202, INFO);
            }
            safeWrappedText(graphics, lab.note(), 16, 218, MUTED, 2);
        }
        return true;
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
        statusBadge(graphics, healthy ? "HEALTH • " + state : "ATTENTION • " + state, healthy ? GOOD : WARN, x, y);
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

    protected final void safeWrappedText(GuiGraphics graphics, String text, int x, int y, int color, int maxLines) {
        int width = Math.max(0, CONTENT_RIGHT - x);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(text == null ? "" : text), width);
        int count = Math.min(Math.max(0, maxLines), lines.size());
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, false);
        }
    }

    protected abstract void renderSection(GuiGraphics graphics, Section section);
}
