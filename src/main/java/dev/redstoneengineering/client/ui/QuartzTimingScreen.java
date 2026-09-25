package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated Quartz source/processor/metrology HMI with topology-correct controls. */
public final class QuartzTimingScreen extends EngineeringScreen<QuartzTimingMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private Button parameterCoarsePrevious;
    private Button parameterCoarseNext;
    private Button reset;

    public QuartzTimingScreen(QuartzTimingMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos + 16, y, 110, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos + 194, y, 110, 20).build());
        int coarseY = y + 24;
        parameterCoarsePrevious = addConfigureWidget(Button.builder(Component.literal("−5 t"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_COARSE_PREVIOUS)).bounds(leftPos + 16, coarseY, 90, 20).build());
        reset = addConfigureWidget(Button.builder(Component.literal("Reset measurement"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_RESET_MEASUREMENT)).bounds(leftPos + 116, coarseY, 88, 20).build());
        parameterCoarseNext = addConfigureWidget(Button.builder(Component.literal("+5 t"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_COARSE_NEXT)).bounds(leftPos + 214, coarseY, 90, 20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean oscillator = menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR;
        boolean divider = menu.kind() == QuartzTimingMenu.KIND_DIVIDER;
        boolean stability = menu.kind() == QuartzTimingMenu.KIND_STABILITY;
        boolean delay = menu.kind() == QuartzTimingMenu.KIND_DELAY;
        boolean configure = isConfigureSection();
        parameterPrevious.active = oscillator || divider || delay;
        parameterNext.active = oscillator || divider || delay;
        parameterPrevious.visible = configure && (oscillator || divider || delay);
        parameterNext.visible = configure && (oscillator || divider || delay);
        parameterCoarsePrevious.active = oscillator;
        parameterCoarseNext.active = oscillator;
        parameterCoarsePrevious.visible = configure && oscillator;
        parameterCoarseNext.visible = configure && oscillator;
        reset.active = stability || oscillator;
        reset.visible = configure && (stability || oscillator);
        if (oscillator) {
            parameterPrevious.setMessage(Component.literal("−1 t"));
            parameterNext.setMessage(Component.literal("+1 t"));
            parameterCoarsePrevious.setMessage(Component.literal("−5 t"));
            parameterCoarseNext.setMessage(Component.literal("+5 t"));
            reset.setMessage(Component.literal("Default " + QuartzOscillatorBlock.DEFAULT_PERIOD_TICKS + "t"));
        } else if (divider) {
            parameterPrevious.setMessage(Component.literal("◀ ÷" + menu.tertiary()));
            parameterNext.setMessage(Component.literal("÷" + menu.tertiary() + " ▶"));
        } else if (delay) {
            parameterPrevious.setMessage(Component.literal("◀ " + menu.primary() + "t delay"));
            parameterNext.setMessage(Component.literal(menu.primary() + "t delay ▶"));
        } else if (stability) {
            reset.setMessage(Component.literal("Reset measurement"));
        }
    }

    @Override protected void renderSection(GuiGraphics graphics, Section section) {
        switch (section) { case OVERVIEW -> overview(graphics); case PORTS -> ports(graphics); case CONFIGURE -> configure(graphics); case DIAGNOSTICS -> diagnostics(graphics); case HISTORY -> history(graphics); }
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceName(), GOOD, 16, 80);
        statusBadge(g, qualityName(), qualityColor(), 200, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            metricCard(g, "State", menu.primary() == 1 ? "HIGH" : "LOW", 16, 103, 88, INFO);
            metricCard(g, "Configured", menu.secondary() + " t", 111, 103, 88, GOOD);
            metricCard(g, "Effective", menu.runtimeA() + " t", 206, 103, 88, INFO);
            labelValue(g, "Output face", outputFace(), 153);
            labelValue(g, "Period latch", menu.runtimeB() == 1 ? "PENDING • NEXT REAL EDGE" : "CURRENT", 171);
            labelValue(g, "Observed transitions", Integer.toString(menu.runtimeC()), 189);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            metricCard(g, "Period in", menu.primary() + " t", 16, 103, 88, INFO);
            metricCard(g, "Period out", menu.secondary() + " t", 111, 103, 88, GOOD);
            metricCard(g, "Division", "÷" + menu.tertiary(), 206, 103, 88, INFO);
            labelValue(g, "Series path", inputFace() + " → " + outputFace(), 153);
            labelValue(g, "Count / initialized", menu.runtimeA() + " / " + yesNo(menu.runtimeB()), 171);
            labelValue(g, "Phase started", yesNo(menu.runtimeC()), 189);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DELAY) {
            metricCard(g, "Delay", menu.primary() + " t", 16, 103, 88, INFO);
            metricCard(g, "Queued", Integer.toString(menu.secondary()), 111, 103, 88, GOOD);
            metricCard(g, "Next event", menu.tertiary() > 0 ? menu.tertiary() + " t" : "NONE", 206, 103, 88, INFO);
            labelValue(g, "Series path", inputFace() + " → " + outputFace(), 153);
            labelValue(g, "Dropped / initialized", menu.runtimeA() + " / " + yesNo(menu.runtimeB()), 171);
        } else {
            metricCard(g, "Measured", menu.primary() + " t", 16, 103, 88, GOOD);
            metricCard(g, "Error", menu.secondary() + " t", 111, 103, 88, INFO);
            metricCard(g, "Upstream", menu.tertiary() + " t", 206, 103, 88, INFO);
            labelValue(g, "Measurement face", inputFace(), 153);
            labelValue(g, "Current evidence", menu.runtimeC() == 1 ? "CURRENT" : menu.primary() > 0 ? "STALE/RETAINED" : "NONE", 171);
        }
        wrappedText(g, topologyHint(), 16, 199, 760, MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g, "QUARTZ INTERFACES", GOOD, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            statusLine(g, outputFace(), "OUTPUT • QUARTZ CLOCK", GOOD, 112);
            statusLine(g, "OTHER FACES", "NO DECLARED QUARTZ PORT", MUTED, 140);
            wrappedText(g, "This is a single-output source. Route rotates the real physical output face.", 16, 174, 760, INFO);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            statusLine(g, inputFace(), "INPUT • QUARTZ CLOCK", GOOD, 112);
            statusLine(g, "PROCESS", "CLOCK DIVISION • ÷" + menu.tertiary(), INFO, 140);
            statusLine(g, outputFace(), "OUTPUT • DIVIDED QUARTZ CLOCK", GOOD, 168);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DELAY) {
            statusLine(g, inputFace(), "INPUT • QUARTZ RISING EDGES", GOOD, 112);
            statusLine(g, "PROCESS", "BOUNDED EVENT DELAY • " + menu.primary() + "t", INFO, 140);
            statusLine(g, outputFace(), "OUTPUT • DELAYED QUARTZ EDGES", qualityColor(), 168);
        } else {
            statusLine(g, inputFace(), "INPUT • QUARTZ TIMING MEASUREMENT", GOOD, 118);
            statusLine(g, "NETWORK AUTHORITY", "OBSERVE ONLY • NO OUTPUT DRIVER", INFO, 146);
            wrappedText(g, "Stability Monitor has one measurement input; the opposite face is not an output.", 16, 176, 760, MUTED);
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g, "SERVER-SIDE BOUNDED CONTROL", INFO, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            labelValue(g, "Configured period Tcfg", menu.secondary() + " ticks", 101);
            labelValue(g, "Effective period Teff", menu.runtimeA() + " ticks", 168);
            labelValue(g, "Allowed range", QuartzOscillatorBlock.MIN_PERIOD_TICKS + ".." + QuartzOscillatorBlock.MAX_PERIOD_TICKS + " ticks", 186);
            labelValue(g, "Fine / coarse step", QuartzOscillatorBlock.FINE_STEP_TICKS + " / " + QuartzOscillatorBlock.COARSE_STEP_TICKS + " ticks", 204);
            labelValue(g, "Default", QuartzOscillatorBlock.DEFAULT_PERIOD_TICKS + " ticks", 222);
            labelValue(g, "Frequency model", "f = 20 / Tcfg = " + frequencyLabel(menu.secondary()) + " Hz", 240);
            labelValue(g, "Half-cycle schedule", "Δt = max(1, Teff / 2) = " + Math.max(1, menu.runtimeA() / 2) + " ticks", 258);
            labelValue(g, "Apply policy", menu.runtimeB() == 1 ? "LATCH AT NEXT REAL TRANSITION" : "ALREADY LATCHED", 276);
            wrappedText(g, "Fine and coarse controls change only the server-owned configured period. The running waveform keeps its already scheduled transition, then latches the new period at that genuine edge.", 16, 300, 620, MUTED);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Division", "÷" + menu.tertiary(), 101);
            labelValue(g, "I/O axis", inputFace() + " → " + outputFace(), 172);
            wrappedText(g, "Physical I/O direction is controlled only on Route. Divider input and output remain opposite because the block is a straight-through timing device.", 16, 199, 760, MUTED);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DELAY) {
            labelValue(g, "Configured edge delay", menu.primary() + " ticks", 101);
            labelValue(g, "Queued / next event", menu.secondary() + " / " + (menu.tertiary() > 0 ? menu.tertiary() + "t" : "NONE"), 172);
            wrappedText(g, "Newly captured edges use the new delay; edges already in the queue retain their original remaining time. Physical I/O is controlled only on Route, and the input/output faces remain opposite as one rigid series axis.", 16, 199, 760, MUTED);
        } else {
            labelValue(g, "Measurement", menu.primary() + " ticks", 101);
            labelValue(g, "Input face", inputFace(), 172);
            wrappedText(g, "Measurement face is controlled only on Route.", 16, 199, 760, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Initialized", yesNo(menu.runtimeA()), 104);
            labelValue(g, "Reference edge", yesNo(menu.runtimeB()), 122);
            labelValue(g, "Current measurement", yesNo(menu.runtimeC()), 140);
            labelValue(g, "Measured / error", menu.primary() + " / " + menu.secondary() + " ticks", 158);
            labelValue(g, "Window samples", menu.runtimeD() + " / 8", 176);
            labelValue(g, "Jitter / max error", menu.runtimeH() + " / " + menu.runtimeI() + " ticks", 194);
            labelValue(g, "Input face", inputFace(), 212);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Input period", menu.primary() + " ticks", 104);
            labelValue(g, "Output period", menu.secondary() + " ticks", 122);
            labelValue(g, "Counted edges", Integer.toString(menu.runtimeA()), 140);
            labelValue(g, "Initialized", yesNo(menu.runtimeB()), 158);
            labelValue(g, "Phase started", yesNo(menu.runtimeC()), 176);
            labelValue(g, "Path", inputFace() + " → " + outputFace(), 194);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DELAY) {
            labelValue(g, "Configured delay", menu.primary() + " ticks", 104);
            labelValue(g, "Queued edges", Integer.toString(menu.secondary()), 122);
            labelValue(g, "Next event", menu.tertiary() > 0 ? menu.tertiary() + " ticks" : "NONE", 140);
            labelValue(g, "Dropped edges", Integer.toString(menu.runtimeA()), 158);
            labelValue(g, "Initialized", yesNo(menu.runtimeB()), 176);
            labelValue(g, "Path", inputFace() + " → " + outputFace(), 194);
        } else {
            labelValue(g, "Oscillator state", menu.primary() == 1 ? "HIGH" : "LOW", 104);
            labelValue(g, "Configured / effective", menu.secondary() + " / " + menu.runtimeA() + " ticks", 122);
            labelValue(g, "Pending latch", yesNo(menu.runtimeB()), 140);
            labelValue(g, "Transitions", Integer.toString(menu.runtimeC()), 158);
            labelValue(g, "Output face", outputFace(), 176);
        }
        statusLine(g, "Diagnosis", diagnosis(), diagnosisColor(), 232);
        wrappedText(g, nextAction(), 16, 252, 760, diagnosisColor());
    }

    private void history(GuiGraphics g) {
        statusBadge(g, "TIMING EVIDENCE", INFO, 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Retained period", menu.primary() + " ticks", 110);
            labelValue(g, "Nominal error", menu.secondary() + " ticks", 130);
            labelValue(g, "Evidence age class", menu.runtimeC() == 1 ? "CURRENT" : menu.primary() > 0 ? "STALE" : "NONE", 150);
            labelValue(g, "Window n", menu.runtimeD() + " / 8", 170);
            labelValue(g, "Min / max", menu.runtimeE() + " / " + menu.runtimeF() + " ticks", 190);
            labelValue(g, "Mean", meanPeriodLabel(), 210);
            labelValue(g, "Jitter = max - min", menu.runtimeH() + " ticks", 230);
            labelValue(g, "Max |T - Tnom|", menu.runtimeI() + " ticks", 250);
            sectionRule(g, 272);
            wrappedText(g, "Window statistics come from up to eight complete server-observed periods; a gap makes retained evidence stale and starts the next valid window fresh.", 16, 286, 760, MUTED);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Model", "Tout = clamp(Tin × N, 1, 4096) ticks", 112);
            labelValue(g, "Configured N", Integer.toString(menu.tertiary()), 132);
            labelValue(g, "Counted edges", Integer.toString(menu.runtimeA()), 152);
            labelValue(g, "Phase started", yesNo(menu.runtimeC()), 172);
            wrappedText(g, "The first valid observation seeds level only; the next genuine rising edge starts divider phase.", 16, 196, 760, MUTED);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DELAY) {
            labelValue(g, "Configured delay", menu.primary() + " ticks", 112);
            labelValue(g, "Queued edges", Integer.toString(menu.secondary()), 132);
            labelValue(g, "Next emission", menu.tertiary() > 0 ? menu.tertiary() + " ticks" : "NONE", 152);
            labelValue(g, "Dropped edges", Integer.toString(menu.runtimeA()), 172);
            labelValue(g, "Initialized", yesNo(menu.runtimeB()), 192);
            wrappedText(g, "Every captured rising edge owns its own countdown. Queue overflow increments dropped-edge evidence; upstream loss never deletes events already inside the delay line.", 16, 216, 760, MUTED);
        } else {
            labelValue(g, "Configured period", menu.secondary() + " ticks", 112);
            labelValue(g, "Effective period", menu.runtimeA() + " ticks", 132);
            labelValue(g, "Period change pending", yesNo(menu.runtimeB()), 152);
            labelValue(g, "Real transitions", Integer.toString(menu.runtimeC()), 172);
            wrappedText(g, "A period edit changes configuration immediately but becomes effective only on a real oscillator transition.", 16, 196, 760, MUTED);
        }
    }

    private String diagnosis() {
        if (menu.quality().name().equals("TOPOLOGY_ERROR")) return "CLOCK SOURCE / TOPOLOGY CONFLICT";
        if (menu.quality().name().equals("NO_SIGNAL")) return "NO TIMING EVIDENCE";
        if (menu.quality().name().equals("STALE")) return "STALE TIMING EVIDENCE";
        if (menu.kind() == QuartzTimingMenu.KIND_DELAY) {
            if (menu.runtimeB() == 0 && menu.secondary() == 0) return "DELAY LINE NOT INITIALIZED";
            if (menu.runtimeA() > 0) return "DELAY QUEUE OVERFLOW EVIDENCE";
            if (menu.secondary() > 0) return "DELAY EVENTS IN FLIGHT";
            return "DELAY LINE READY";
        }
        if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            if (menu.runtimeB() == 0) return "DIVIDER NOT INITIALIZED";
            if (menu.runtimeC() == 0) return "WAITING FOR FIRST GENUINE RISING EDGE";
            int expected = expectedDividerPeriod();
            if (menu.primary() > 0 && menu.secondary() != expected) return "DIVISION PERIOD MISMATCH";
            return "DIVIDED CLOCK COHERENT";
        }
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            if (menu.runtimeA() == 0 || menu.runtimeB() == 0) return "WAITING FOR TWO EDGE REFERENCES";
            if (menu.runtimeC() == 0) return menu.primary() > 0 ? "RETAINED PERIOD • NOT CURRENT" : "NO CURRENT PERIOD";
            if (Math.abs(menu.secondary()) >= Math.max(2, Math.max(1, menu.tertiary()) / 4)) return "TIMING ERROR ELEVATED";
            return "TIMING STABILITY NOMINAL";
        }
        if (menu.runtimeB() == 1) return "PERIOD CHANGE PENDING REAL EDGE";
        return menu.secondary() > 0 && menu.runtimeA() > 0 ? "CLOCK SOURCE CONFIGURED" : "INVALID ZERO PERIOD";
    }

    private String nextAction() {
        String d = diagnosis();
        if (d.contains("CONFLICT")) return "NEXT • isolate competing timing sources before measuring period.";
        if (d.contains("STALE") || d.contains("NO TIMING") || d.contains("NOT CURRENT")) return "NEXT • restore current edge evidence before accepting timing quality.";
        if (d.contains("PENDING REAL EDGE")) return "NEXT • leave the oscillator running; the configured period will latch on the next genuine transition.";
        if (d.contains("DELAY LINE NOT INITIALIZED")) return "NEXT • restore valid quartz input and allow a real level baseline before judging delay behavior.";
        if (d.contains("OVERFLOW")) return "NEXT • reduce incoming edge density or shorten configured delay before accepting event-transfer integrity.";
        if (d.contains("IN FLIGHT")) return "NEXT • allow retained queued events to emit; do not clear them merely because upstream evidence changed.";
        if (d.contains("NOT INITIALIZED") || d.contains("WAITING")) return "NEXT • allow genuine source edges to initialize the timing state.";
        if (d.contains("MISMATCH")) return "NEXT • verify divider ratio and upstream period before changing downstream logic.";
        if (d.contains("ELEVATED")) return "NEXT • compare measured period against upstream/reference timing and inspect clock integrity.";
        return "NEXT • timing evidence is coherent; retain this state as the commissioning reference.";
    }

    private int expectedDividerPeriod() {
        long period = (long)Math.max(1, menu.primary()) * Math.max(2, menu.tertiary());
        return (int)Math.min(4096L, period);
    }

    private String frequencyLabel(int periodTicks) {
        return String.format(java.util.Locale.ROOT, "%.3f", 20.0 / Math.max(1, periodTicks));
    }

    private String meanPeriodLabel() {
        int scaled = Math.max(0, menu.runtimeG());
        return (scaled / 100) + "." + String.format("%02d", scaled % 100) + " ticks";
    }

    private int diagnosisColor() { String d = diagnosis(); return d.contains("COHERENT") || d.contains("NOMINAL") || d.contains("CONFIGURED") || d.contains("READY") || d.contains("IN FLIGHT") ? GOOD : WARN; }
    private String deviceName() { return switch (menu.kind()) { case QuartzTimingMenu.KIND_DIVIDER -> "QUARTZ CLOCK DIVIDER"; case QuartzTimingMenu.KIND_STABILITY -> "QUARTZ STABILITY MONITOR"; case QuartzTimingMenu.KIND_DELAY -> "QUARTZ PHASE DELAY"; default -> "QUARTZ OSCILLATOR"; }; }
    private String qualityName() { return menu.quality().name().replace('_', ' '); }
    private int qualityColor() { return switch (menu.quality()) { case VALID -> GOOD; case STALE, NO_SIGNAL -> WARN; default -> BAD; }; }
    private String inputFace() { return menu.inputDirection().getName().toUpperCase(); }
    private String outputFace() { return menu.outputDirection().getName().toUpperCase(); }
    private String yesNo(int value) { return value == 1 ? "YES" : "NO"; }
    private String topologyHint() { return switch (menu.kind()) { case QuartzTimingMenu.KIND_DIVIDER -> "Divider exposes one clock input and one divided-clock output; Route changes those real endpoints."; case QuartzTimingMenu.KIND_STABILITY -> "Monitor is observer-only; Route selects its one measurement input and exposes no output."; case QuartzTimingMenu.KIND_DELAY -> "Phase Delay exposes one Quartz edge input and one delayed-edge output; Route changes those real endpoints."; default -> "Oscillator exposes one configurable quartz-clock output; Route rotates that physical source face."; }; }
}
