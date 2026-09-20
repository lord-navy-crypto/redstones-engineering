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

    public QuartzTimingScreen(QuartzTimingMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos + 16, y, 110, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos + 194, y, 110, 20).build());
        reset = addConfigureWidget(Button.builder(Component.literal("Reset measurement"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_RESET_MEASUREMENT)).bounds(leftPos + 70, y, 180, 20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
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
        } else reset.setMessage(Component.literal("Reset measurement"));
    }

    @Override protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) { case OVERVIEW -> overview(graphics); case PORTS -> ports(graphics); case CONFIGURE -> configure(graphics); case DIAGNOSTICS -> diagnostics(graphics); case HISTORY -> history(graphics); }
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
        safeText(g, "MODEL • " + timingEquation(), 16, 195, GOOD);
        safeText(g, topologyHint(), 16, 211, MUTED);
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
        safeText(g, "TRANSFER • " + timingEquation(), 16, 190, GOOD);
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        safeText(g, "EQUATION • " + timingEquation(), 16, 96, GOOD);
        safeText(g, "CONTROL MAP • " + timingControlMap(), 16, 114, INFO);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            labelValue(g, "Period", menu.secondary() + " ticks", 138);
            labelValue(g, "Topology", "FOUR-WAY SOURCE", 172);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Division", "÷" + menu.tertiary(), 138);
            labelValue(g, "I/O axis", inputFace() + " → " + outputFace(), 172);
            safeText(g, "Physical I/O direction is controlled only on Route.", 16, 199, MUTED);
        } else {
            labelValue(g, "Measurement", menu.primary() + " ticks", 138);
            labelValue(g, "Input face", inputFace(), 172);
            safeText(g, "Measurement face is controlled only on Route.", 16, 199, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        safeText(g, "CHECK • " + timingDiagnosticRelation(), 16, 96, GOOD);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Initialized", yesNo(menu.runtimeA()), 116);
            labelValue(g, "Reference edge", yesNo(menu.runtimeB()), 122);
            labelValue(g, "Current measurement", yesNo(menu.runtimeC()), 140);
            labelValue(g, "Measured / error", menu.primary() + " / " + menu.secondary() + " ticks", 158);
            labelValue(g, "Input face", inputFace(), 176);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Input period", menu.primary() + " ticks", 116);
            labelValue(g, "Output period", menu.secondary() + " ticks", 122);
            labelValue(g, "Counted edges", Integer.toString(menu.runtimeA()), 140);
            labelValue(g, "Initialized", yesNo(menu.runtimeB()), 158);
            labelValue(g, "Path", inputFace() + " → " + outputFace(), 176);
        } else {
            labelValue(g, "Oscillator state", menu.primary() == 1 ? "HIGH" : "LOW", 116);
            labelValue(g, "Period", menu.secondary() + " ticks", 122);
            labelValue(g, "Evidence", "VALID SOURCE CONFIGURATION", 140);
        }
        statusLine(g, "Diagnosis", diagnosis(), diagnosisColor(), 194);
        safeText(g, nextAction(), 16, 214, diagnosisColor());
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "TIMING EVIDENCE", INFO, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Retained period", menu.primary() + " ticks", 110);
            labelValue(g, "Nominal error", menu.secondary() + " ticks", 130);
            labelValue(g, "Evidence age class", menu.runtimeC() == 1 ? "CURRENT" : menu.primary() > 0 ? "STALE" : "NONE", 150);
            sectionRule(g, 170);
            safeText(g, diagnosis(), 16, 184, diagnosisColor());
            safeText(g, "Two genuine rising edges are required; UI inspection never fabricates timing evidence.", 16, 200, MUTED);
        } else {
            safeText(g, "This device exposes current timing state; it does not fabricate client-side waveform history.", 16, 112, MUTED);
            safeText(g, diagnosis(), 16, 136, diagnosisColor());
        }
    }

    private String timingEquation() {
        return switch (menu.kind()) {
            case QuartzTimingMenu.KIND_OSCILLATOR -> "f=1/T; output toggles each half-cycle from server clock";
            case QuartzTimingMenu.KIND_DIVIDER -> "T_out = N*T_in; f_out = f_in/N";
            default -> "error = T_measured - T_reference; measurement requires genuine edge intervals";
        };
    }

    private String timingControlMap() {
        return switch (menu.kind()) {
            case QuartzTimingMenu.KIND_OSCILLATOR -> "T=" + menu.secondary() + " ticks • period index=" + menu.tertiary();
            case QuartzTimingMenu.KIND_DIVIDER -> "N=" + menu.tertiary() + " • current Tin=" + menu.primary() + "t";
            default -> "observer/reset only • no fabricated measurement target";
        };
    }

    private String timingDiagnosticRelation() {
        return switch (menu.kind()) {
            case QuartzTimingMenu.KIND_OSCILLATOR -> "T=" + menu.secondary() + "t • phase=" + (menu.primary() == 1 ? "HIGH" : "LOW");
            case QuartzTimingMenu.KIND_DIVIDER -> "Tout expected=" + (Math.max(0, menu.primary()) * Math.max(1, menu.tertiary()))
                    + "t • measured=" + menu.secondary() + "t";
            default -> "Tmeas=" + menu.primary() + "t • error=" + menu.secondary() + "t • Tref=" + menu.tertiary() + "t";
        };
    }

    private String diagnosis() {
        if (menu.quality().name().equals("TOPOLOGY_ERROR")) return "CLOCK SOURCE / TOPOLOGY CONFLICT";
        if (menu.quality().name().equals("NO_SIGNAL")) return "NO TIMING EVIDENCE";
        if (menu.quality().name().equals("STALE")) return "STALE TIMING EVIDENCE";
        if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            if (menu.runtimeB() == 0) return "DIVIDER NOT INITIALIZED";
            int expected = Math.max(0, menu.primary()) * Math.max(1, menu.tertiary());
            if (menu.primary() > 0 && menu.secondary() != expected) return "DIVISION PERIOD MISMATCH";
            return "DIVIDED CLOCK COHERENT";
        }
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            if (menu.runtimeA() == 0 || menu.runtimeB() == 0) return "WAITING FOR TWO EDGE REFERENCES";
            if (menu.runtimeC() == 0) return menu.primary() > 0 ? "RETAINED PERIOD • NOT CURRENT" : "NO CURRENT PERIOD";
            if (Math.abs(menu.secondary()) >= Math.max(2, Math.max(1, menu.tertiary()) / 4)) return "TIMING ERROR ELEVATED";
            return "TIMING STABILITY NOMINAL";
        }
        return menu.secondary() > 0 ? "CLOCK SOURCE CONFIGURED" : "INVALID ZERO PERIOD";
    }

    private String nextAction() {
        String d = diagnosis();
        if (d.contains("CONFLICT")) return "NEXT • isolate competing timing sources before measuring period.";
        if (d.contains("STALE") || d.contains("NO TIMING") || d.contains("NOT CURRENT")) return "NEXT • restore current edge evidence before accepting timing quality.";
        if (d.contains("NOT INITIALIZED") || d.contains("WAITING")) return "NEXT • allow genuine source edges to initialize the timing state.";
        if (d.contains("MISMATCH")) return "NEXT • verify divider ratio and upstream period before changing downstream logic.";
        if (d.contains("ELEVATED")) return "NEXT • compare measured period against upstream/reference timing and inspect clock integrity.";
        return "NEXT • timing evidence is coherent; retain this state as the commissioning reference.";
    }

    private int diagnosisColor() { String d = diagnosis(); return d.contains("COHERENT") || d.contains("NOMINAL") || d.contains("CONFIGURED") ? GOOD : WARN; }
    private String deviceName() { return switch (menu.kind()) { case QuartzTimingMenu.KIND_DIVIDER -> "QUARTZ CLOCK DIVIDER"; case QuartzTimingMenu.KIND_STABILITY -> "QUARTZ STABILITY MONITOR"; default -> "QUARTZ OSCILLATOR"; }; }
    private String qualityName() { return menu.quality().name().replace('_', ' '); }
    private int qualityColor() { return switch (menu.quality()) { case VALID -> GOOD; case STALE, NO_SIGNAL -> WARN; default -> BAD; }; }
    private String inputFace() { return menu.logicalFacing().getOpposite().getName().toUpperCase(); }
    private String outputFace() { return menu.logicalFacing().getName().toUpperCase(); }
    private String yesNo(int value) { return value == 1 ? "YES" : "NO"; }
    private String topologyHint() { return switch (menu.kind()) { case QuartzTimingMenu.KIND_DIVIDER -> "Divider is strict series timing processing; Direction changes the whole I/O axis."; case QuartzTimingMenu.KIND_STABILITY -> "Monitor is observer-only; Direction selects the measurement face, not an output."; default -> "Oscillator keeps its real four-way source topology instead of being forced into series."; }; }
}
