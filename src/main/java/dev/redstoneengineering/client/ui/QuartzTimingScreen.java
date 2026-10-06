package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated Quartz source/processor/metrology HMI with topology-correct controls. */
public final class QuartzTimingScreen extends EngineeringScreen<QuartzTimingMenu> {
    private Button parameterPrevious;
    private Button parameterNext;
    private Button reset;
    private EditBox parameterInput;
    private Button parameterApply;

    public QuartzTimingScreen(QuartzTimingMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y = topPos + imageHeight - 66;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos + 16, y, 110, 20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos + 194, y, 110, 20).build());
        reset = addConfigureWidget(Button.builder(Component.literal("Reset measurement"), b -> sendMenuButton(QuartzTimingMenu.BUTTON_RESET_MEASUREMENT)).bounds(leftPos + 70, y, 180, 20).build());
        parameterInput = addConfigureWidget(new EditBox(this.font,leftPos+16,y,110,20,Component.literal("Exact timing value")));
        parameterInput.setMaxLength(2);
        parameterInput.setFilter(v->v.isEmpty()||v.chars().allMatch(Character::isDigit));
        parameterApply = addConfigureWidget(Button.builder(Component.literal("Apply"),b->submitParameter()).bounds(leftPos+194,y,110,20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean oscillator = menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR;
        boolean divider = menu.kind() == QuartzTimingMenu.KIND_DIVIDER;
        boolean stability = menu.kind() == QuartzTimingMenu.KIND_STABILITY;
        boolean configure = isConfigureSection();
        parameterPrevious.active = oscillator || divider;
        parameterNext.active = oscillator || divider;
        parameterPrevious.visible = false;
        parameterNext.visible = false;
        boolean direct = configure && (oscillator || divider);
        if(parameterInput!=null){
            parameterInput.visible=direct;
            parameterInput.active=direct;
            if(direct&&!parameterInput.isFocused()){
                int current=oscillator?menu.secondary():menu.tertiary();
                String expected=Integer.toString(current);
                if(!expected.equals(parameterInput.getValue()))parameterInput.setValue(expected);
            }
        }
        if(parameterApply!=null){
            parameterApply.visible=direct;
            parameterApply.active=direct&&parameterValid();
            parameterApply.setMessage(Component.literal(oscillator?"Apply T":"Apply N"));
        }
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

    private boolean parameterValid(){
        if(parameterInput==null||parameterInput.getValue().isEmpty())return false;
        try{
            int value=Integer.parseInt(parameterInput.getValue());
            if(menu.kind()==QuartzTimingMenu.KIND_OSCILLATOR)
                return value==2||value==4||value==8||value==16||value==32;
            return menu.kind()==QuartzTimingMenu.KIND_DIVIDER&&(value==2||value==4||value==8||value==16);
        }catch(NumberFormatException ignored){return false;}
    }

    private void submitParameter(){
        if(!parameterValid())return;
        sendMenuButton(QuartzTimingMenu.BUTTON_PARAMETER_DIRECT_BASE+Integer.parseInt(parameterInput.getValue()));
        parameterInput.setFocused(false);
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
        statusBadge(g, "FORMULA-FIRST TIMING MODEL", INFO, 16, 80);
        formulaCard(g, timingEquation(), 105);
        if (menu.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            variableRole(g, "ADJUSTABLE", "T", menu.secondary() + "", "ticks", 134);
            variableRole(g, "CONTROL", "direct entry", "T={2,4,8,16,32}", "ticks", 152);
            variableRole(g, "DERIVED", "f_nom", String.format(java.util.Locale.ROOT, "%.3f", 20.0 / Math.max(1, menu.secondary())), "Hz @20TPS", 170);
            variableRole(g, "SOLVER", "state", menu.primary() == 1 ? "HIGH" : "LOW", "", 188);
            variableRole(g, "TOPOLOGY", "OUT", "N/E/S/W", "Quartz source", 206);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            variableRole(g, "MEASURED", "T_in", menu.primary() + "", "ticks", 134);
            variableRole(g, "ADJUSTABLE", "N", Integer.toString(menu.tertiary()), "division", 152);
            variableRole(g, "CONTROL", "direct entry", "N={2,4,8,16}", "exact divisor", 170);
            variableRole(g, "DERIVED", "T_out", menu.secondary() + "", "ticks", 188);
            variableRole(g, "EVIDENCE", "expected", expectedDividerPeriod() + "", "ticks", 206);
            variableRole(g, "EVIDENCE", "period limit", dividerSaturated() ? "SATURATED @4096" : "IN RANGE", "server clamp", 224);
            wrappedText(g, "Route owns the physical RX/TX axis; changing N re-arms divider phase evidence. Output period is bounded by the server timing domain.", 16, 250, workspaceWidth() - 24, MUTED);
        } else {
            variableRole(g, "MEASURED", "T_meas", menu.primary() + "", "ticks", 134);
            variableRole(g, "MEASURED", "T_upstream", menu.tertiary() + "", "ticks", 152);
            variableRole(g, "DERIVED", "|e_T|", menu.secondary() + "", "ticks", 170);
            variableRole(g, "EVIDENCE", "current", menu.runtimeC() == 1 ? "YES" : "NO", "", 188);
            wrappedText(g, "The monitor needs two genuine rising edges; opening the HMI never fabricates timing evidence.", 16, 214, workspaceWidth() - 24, MUTED);
        }
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g, qualityName(), qualityColor(), 16, 80);
        if (menu.kind() == QuartzTimingMenu.KIND_STABILITY) {
            labelValue(g, "Initialized", yesNo(menu.runtimeA()), 104);
            labelValue(g, "Reference edge", yesNo(menu.runtimeB()), 122);
            labelValue(g, "Current measurement", yesNo(menu.runtimeC()), 140);
            labelValue(g, "Measured / error", menu.primary() + " / " + menu.secondary() + " ticks", 158);
            labelValue(g, "Input face", inputFace(), 176);
        } else if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            labelValue(g, "Input period", menu.primary() + " ticks", 104);
            labelValue(g, "Output period", menu.secondary() + " ticks", 122);
            labelValue(g, "Counted edges", Integer.toString(menu.runtimeA()), 140);
            labelValue(g, "Initialized", yesNo(menu.runtimeB()), 158);
            labelValue(g, "Path", inputFace() + " → " + outputFace(), 176);
        } else {
            labelValue(g, "Oscillator state", menu.primary() == 1 ? "HIGH" : "LOW", 104);
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
            case QuartzTimingMenu.KIND_DIVIDER -> "valid input ⇒ T_out = min(4096, N · max(1,T_in)) ticks";
            case QuartzTimingMenu.KIND_STABILITY -> "|e_T| = |T_meas - T_upstream|";
            default -> "f_nom = 20 / T  Hz";
        };
    }

    private String diagnosis() {
        if (menu.quality().name().equals("TOPOLOGY_ERROR")) return "CLOCK SOURCE / TOPOLOGY CONFLICT";
        if (menu.quality().name().equals("NO_SIGNAL")) return "NO TIMING EVIDENCE";
        if (menu.quality().name().equals("STALE")) return "STALE TIMING EVIDENCE";
        if (menu.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            if (menu.runtimeB() == 0) return "DIVIDER NOT INITIALIZED";
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
        return menu.secondary() > 0 ? "CLOCK SOURCE CONFIGURED" : "INVALID ZERO PERIOD";
    }

    private int expectedDividerPeriod() {
        if (menu.primary() <= 0) return 0;
        return Math.min(4096, Math.max(1, menu.primary()) * Math.max(1, menu.tertiary()));
    }

    private boolean dividerSaturated() {
        if (menu.primary() <= 0) return false;
        return (long) Math.max(1, menu.primary()) * Math.max(1, menu.tertiary()) > 4096L;
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
