package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated Quartz source/processor/metrology HMI with topology-correct controls. */
public final class QuartzTimingScreen extends EngineeringScreen<QuartzTimingMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private Button reset;

    public QuartzTimingScreen(QuartzTimingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"),
                b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_PREVIOUS))
                .bounds(leftPos + 16, y, 110, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"),
                b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_NEXT))
                .bounds(leftPos + 194, y, 110, 20).build());
        reset = addConfigureWidget(Button.builder(Component.literal("Reset measurement"),
                b -> sendMenuButton(QuartzTimingMenu.BUTTON_RESET_MEASUREMENT))
                .bounds(leftPos + 70, y, 180, 20).build());
    }

    @Override
    protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean oscillator = menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR;
        boolean divider = menu.kind() == QuartzTimingMenu.KIND_DIVIDER;
        boolean stability = menu.kind() == QuartzTimingMenu.KIND_STABILITY;
        boolean configure = isConfigureSection();

        parameterPrevious.active = oscillator || divider;
        parameterNext.active = oscillator || divider;
        parameterPrevious.visible = configure && (oscillator || divider);
        parameterNext.visible = configure && (oscillator || divider);
        reset.active = stability;
        reset.visible = configure && stability;

        if (oscillator) {
            parameterPrevious.setMessage(Component.literal("◀ " + menu.secondary() + "t"));
            parameterNext.setMessage(Component.literal(menu.secondary() + "t ▶"));
        } else if (divider) {
            parameterPrevious.setMessage(Component.literal("◀ ÷" + menu.tertiary()));
            parameterNext.setMessage(Component.literal("÷" + menu.tertiary() + " ▶"));
        } else {
            reset.setMessage(Component.literal("Reset measurement"));
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
        statusBadge(g, deviceName(), GOOD, 16, 80);
        statusBadge(g, qualityName(), qualityColor(), 200, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            metricCard(g, "State", menu.primary() == 1 ? "HIGH" : "LOW", 16, 103, 88, INFO);
            metricCard(g, "Period", menu.secondary() + " t", 111, 103, 88, GOOD);
            metricCard(g, "Index", Integer.toString(menu.tertiary()), 206, 103, 88, INFO);
            labelValue(g, "Topology", "SOURCE • FOUR HORIZONTAL OUTPUTS", 153);
            labelValue(g, "Authority", "SERVER CLOCK", 171);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            metricCard(g, "Period in", menu.primary() + " t", 16, 103, 88, INFO);
            metricCard(g, "Period out", menu.secondary() + " t", 111, 103, 88, GOOD);
            metricCard(g, "Division", "÷" + menu.tertiary(), 206, 103, 88, INFO);
            labelValue(g, "Series path", inputFace() + " → " + outputFace(), 153);
            labelValue(g, "Count / initialized", menu.runtimeA() + " / " + (menu.runtimeB() == 1 ? "YES" : "NO"), 171);
        } else {
            metricCard(g, "Measured", menu.primary() + " t", 16, 103, 88, GOOD);
            metricCard(g, "Error", menu.secondary() + " t", 111, 103, 88, INFO);
            metricCard(g, "Upstream", menu.tertiary() + " t", 206, 103, 88, INFO);
            labelValue(g, "Measurement face", inputFace(), 153);
            labelValue(g, "Current evidence", menu.runtimeC() == 1 ? "CURRENT" : menu.primary() > 0 ? "STALE/RETAINED" : "NONE", 171);
        }
        safeText(g, topologyHint(), 16, 199, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "QUARTZ INTERFACES", GOOD, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            statusLine(g, "NORTH / EAST", "OUTPUT • QUARTZ TIMING", GOOD, 112);
            statusLine(g, "SOUTH / WEST", "OUTPUT • QUARTZ TIMING", GOOD, 136);
            safeText(g, "Oscillator is a source; it is intentionally not forced into a fake series topology.", 16, 174, INFO);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            statusLine(g, inputFace(), "INPUT • QUARTZ CLOCK", GOOD, 112);
            statusLine(g, "PROCESS", "CLOCK DIVISION • ÷" + menu.tertiary(), INFO, 140);
            statusLine(g, outputFace(), "OUTPUT • DIVIDED QUARTZ CLOCK", GOOD, 168);
        } else {
            statusLine(g, inputFace(), "INPUT • QUARTZ TIMING MEASUREMENT", GOOD, 118);
            statusLine(g, "NETWORK AUTHORITY", "OBSERVE ONLY • NO OUTPUT DRIVER", INFO, 146);
            safeText(g, "Stability Monitor has one measurement input; the opposite face is not an output.", 16, 176, MUTED);
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            labelValue(g, "Period", menu.secondary() + " ticks", 101);
            labelValue(g, "Topology", "FOUR-WAY SOURCE", 172);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Division", "÷" + menu.tertiary(), 101);
            labelValue(g, "I/O axis", inputFace() + " → " + outputFace(), 172);
            safeText(g, "Physical I/O direction is controlled only on Route.", 16, 199, MUTED);
        } else {
            labelValue(g, "Measurement", menu.primary() + " ticks", 101);
            labelValue(g, "Input face", inputFace(), 172);
            safeText(g, "Measurement face is controlled only on Route.", 16, 199, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Initialized", yesNo(menu.runtimeA()), 108);
            labelValue(g, "Reference edge", yesNo(menu.runtimeB()), 126);
            labelValue(g, "Current measurement", yesNo(menu.runtimeC()), 144);
            labelValue(g, "Measured / error", menu.primary() + " / " + menu.secondary() + " ticks", 162);
            labelValue(g, "Input face", inputFace(), 180);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Input period", menu.primary() + " ticks", 108);
            labelValue(g, "Output period", menu.secondary() + " ticks", 126);
            labelValue(g, "Counted edges", Integer.toString(menu.runtimeA()), 144);
            labelValue(g, "Initialized", yesNo(menu.runtimeB()), 162);
            labelValue(g, "Path", inputFace() + " → " + outputFace(), 180);
        } else {
            labelValue(g, "Oscillator state", menu.primary() == 1 ? "HIGH" : "LOW", 108);
            labelValue(g, "Period", menu.secondary() + " ticks", 126);
            labelValue(g, "Evidence", "VALID SOURCE CONFIGURATION", 144);
        }
        statusLine(g, "Authority", "SERVER SYNCHRONIZED", GOOD, 200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "TIMING EVIDENCE", INFO, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Retained period", menu.primary() + " ticks", 110);
            labelValue(g, "Nominal error", menu.secondary() + " ticks", 130);
            labelValue(g, "Evidence age class", menu.runtimeC() == 1 ? "CURRENT" : menu.primary() > 0 ? "STALE" : "NONE", 150);
            sectionRule(g, 170);
            safeText(g, "Two genuine rising edges are required; UI inspection never fabricates timing evidence.", 16, 184, MUTED);
        } else {
            safeText(g, "This device exposes current timing state; it does not fabricate client-side waveform history.", 16, 112, MUTED);
            safeText(g, "Use Oscilloscope / Signal Analyzer when time-series capture is required.", 16, 134, INFO);
        }
    }

    private String deviceName() {
        return switch (menu.kind()) {
            case QuartzTimingMenu.KIND_DIVIDER -> "QUARTZ CLOCK DIVIDER";
            case QuartzTimingMenu.KIND_STABILITY -> "QUARTZ STABILITY MONITOR";
            default -> "QUARTZ OSCILLATOR";
        };
    }

    private String qualityName() { return menu.quality().name().replace('_', ' '); }
    private int qualityColor() {
        return switch (menu.quality()) {
            case VALID -> GOOD;
            case STALE, NO_SIGNAL -> WARN;
            default -> BAD;
        };
    }
    private String inputFace() { return menu.logicalFacing().getOpposite().getName().toUpperCase(); }
    private String outputFace() { return menu.logicalFacing().getName().toUpperCase(); }
    private String yesNo(int value) { return value == 1 ? "YES" : "NO"; }

    private String topologyHint() {
        return switch (menu.kind()) {
            case QuartzTimingMenu.KIND_DIVIDER -> "Divider is strict series timing processing; Direction changes the whole I/O axis.";
            case QuartzTimingMenu.KIND_STABILITY -> "Monitor is observer-only; Direction selects the measurement face, not an output.";
            default -> "Oscillator keeps its real four-way source topology instead of being forced into series.";
        };
    }
}
