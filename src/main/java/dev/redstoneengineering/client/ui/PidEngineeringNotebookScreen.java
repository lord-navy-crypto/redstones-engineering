package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.PneumaticClosedLoopWitness;
import dev.redstoneengineering.diagnostics.acceptance.AcceptanceEvidenceTrend;
import dev.redstoneengineering.diagnostics.acceptance.EngineeringAcceptanceStatus;
import dev.redstoneengineering.ui.menu.PidControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Full-page engineering notebook for PID tuning, response and evidence. */
public final class PidEngineeringNotebookScreen extends AbstractContainerScreen<PidControllerMenu> {
    private static final int BG = 0xFFF2E9D8;
    private static final int PAGE = 0xFFFFF8E8;
    private static final int INK = 0xFF2C2925;
    private static final int MUTED = 0xFF6E675E;
    private static final int RULE = 0xFFB9A98F;
    private static final int ACCENT = 0xFF8A4A35;
    private static final int GOOD = 0xFF2F7D4A;
    private static final int WARN = 0xFF9A6A19;
    private static final int BAD = 0xFFA43838;
    private static final int SP_COLOR = WARN;
    private static final int PV_COLOR = GOOD;
    private static final int OUT_COLOR = ACCENT;
    private static final int EVIDENCE_ACTION_VIRTUAL_Y = CONTENT_TOP + 500;

    private enum Page {
        OPERATE("Operate"),
        PARAMETERS("Parameters"),
        MODEL("Model"),
        RESPONSE("Response"),
        ROUTING("Routing"),
        EVIDENCE("Evidence");

        final String label;
        Page(String label) { this.label = label; }
    }

    private Page page = Page.PARAMETERS;
    private final List<Button> parameterWidgets = new ArrayList<>();
    private final List<Button> routingWidgets = new ArrayList<>();
    private final List<Button> evidenceWidgets = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 84;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    public PidEngineeringNotebookScreen(PidControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 520;
        imageHeight = 300;
        titleLabelX = 18;
        titleLabelY = 12;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(380, width - VIEW_MARGIN * 2);
        imageHeight = Math.max(250, height - VIEW_MARGIN * 2);
        super.init();
        parameterWidgets.clear();
        routingWidgets.clear();
        evidenceWidgets.clear();
        scrollOffset = 0;

        int gap = imageWidth < 480 ? 4 : 6;
        int tabWidth = Math.max(54, (imageWidth - 48 - gap * (Page.values().length - 1)) / Page.values().length);
        int x = leftPos + 24;
        int tabY = topPos + 38;
        for (Page value : Page.values()) {
            addRenderableWidget(Button.builder(Component.literal(pageTabLabel(value)), b -> {
                page = value;
                scrollOffset = 0;
                updateVisibility();
            }).bounds(x, tabY, tabWidth, 22).build());
            x += tabWidth + gap;
        }

        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("◀ Preset"),
                b -> send(PidControllerMenu.BUTTON_TUNING_PREVIOUS))
                .bounds(leftPos + imageWidth - 194, topPos + CONTENT_TOP + 10, 78, 22).build()));
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("Preset ▶"),
                b -> send(PidControllerMenu.BUTTON_TUNING_NEXT))
                .bounds(leftPos + imageWidth - 108, topPos + CONTENT_TOP + 10, 78, 22).build()));

        addParameterRow(0, "Kp", PidControllerMenu.BUTTON_KP_MINUS, PidControllerMenu.BUTTON_KP_PLUS);
        addParameterRow(1, "Ki divisor", PidControllerMenu.BUTTON_KI_DIV_MINUS, PidControllerMenu.BUTTON_KI_DIV_PLUS);
        addParameterRow(2, "Kd", PidControllerMenu.BUTTON_KD_MINUS, PidControllerMenu.BUTTON_KD_PLUS);
        addParameterRow(3, "D smoothing", PidControllerMenu.BUTTON_DSMOOTH_MINUS, PidControllerMenu.BUTTON_DSMOOTH_PLUS);
        addParameterRow(4, "Rise limit", PidControllerMenu.BUTTON_RISE_MINUS, PidControllerMenu.BUTTON_RISE_PLUS);
        addParameterRow(5, "Fall limit", PidControllerMenu.BUTTON_FALL_MINUS, PidControllerMenu.BUTTON_FALL_PLUS);

        int routeY = topPos + CONTENT_TOP + 92;
        int routeWidth = routeButtonWidth();
        int routeGap = 8;
        int routeX = routeButtonStartX();
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("RX ◀"),
                b -> send(PidControllerMenu.BUTTON_INPUT_PREVIOUS))
                .bounds(routeX, routeY, routeWidth, 22).build()));
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("RX ▶"),
                b -> send(PidControllerMenu.BUTTON_INPUT_NEXT))
                .bounds(routeX + routeWidth + routeGap, routeY, routeWidth, 22).build()));
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("TX ◀"),
                b -> send(PidControllerMenu.BUTTON_OUTPUT_PREVIOUS))
                .bounds(routeX + (routeWidth + routeGap) * 2, routeY, routeWidth, 22).build()));
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("TX ▶"),
                b -> send(PidControllerMenu.BUTTON_OUTPUT_NEXT))
                .bounds(routeX + (routeWidth + routeGap) * 3, routeY, routeWidth, 22).build()));

        int evidenceWidth = evidenceButtonWidth();
        int evidenceGap = 12;
        int evidenceX = evidenceButtonStartX();
        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Capture acceptance"),
                b -> send(PidControllerMenu.BUTTON_CAPTURE_ACCEPTANCE))
                .bounds(evidenceX, topPos + EVIDENCE_ACTION_VIRTUAL_Y, evidenceWidth, 22).build()));
        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Reset runtime + trend"),
                b -> send(PidControllerMenu.BUTTON_RESET_RUNTIME_TREND))
                .bounds(evidenceX + evidenceWidth + evidenceGap, topPos + EVIDENCE_ACTION_VIRTUAL_Y, evidenceWidth, 22).build()));

        updateVisibility();
    }

    private String pageTabLabel(Page value) {
        if (imageWidth >= 480) return value.label;
        return switch (value) {
            case OPERATE -> "Run";
            case PARAMETERS -> "Params";
            case MODEL -> "Model";
            case RESPONSE -> "Resp";
            case ROUTING -> "Route";
            case EVIDENCE -> "Evidence";
        };
    }

    private int routeButtonWidth() {
        return Math.max(60, Math.min(88, (imageWidth - 48 - 8 * 3) / 4));
    }

    private int routeButtonStartX() {
        int total = routeButtonWidth() * 4 + 8 * 3;
        return leftPos + Math.max(24, (imageWidth - total) / 2);
    }

    private int evidenceButtonWidth() {
        return Math.max(120, Math.min(170, (imageWidth - 48 - 12) / 2));
    }

    private int evidenceButtonStartX() {
        int total = evidenceButtonWidth() * 2 + 12;
        return leftPos + Math.max(24, (imageWidth - total) / 2);
    }

    private void addParameterRow(int row, String label, int minusId, int plusId) {
        int y = topPos + CONTENT_TOP + 60 + row * 44 - scrollOffset;
        Button minus = addRenderableWidget(Button.builder(Component.literal("−"),
                b -> send(minusId)).bounds(leftPos + imageWidth - 164, y, 42, 22).build());
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> send(plusId)).bounds(leftPos + imageWidth - 78, y, 42, 22).build());
        minus.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Decrease " + label)));
        plus.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Increase " + label)));
        parameterWidgets.add(minus);
        parameterWidgets.add(plus);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateVisibility() {
        for (int i = 0; i < parameterWidgets.size(); i++) {
            Button b = parameterWidgets.get(i);
            if (i < 2) {
                b.setX(i == 0 ? leftPos + imageWidth - 194 : leftPos + imageWidth - 108);
                b.setY(topPos + CONTENT_TOP + 10 - scrollOffset);
            } else {
                int row = (i - 2) / 2;
                b.setX(((i - 2) % 2 == 0) ? leftPos + imageWidth - 164 : leftPos + imageWidth - 78);
                b.setY(topPos + CONTENT_TOP + 60 + row * 44 - scrollOffset);
            }
            b.visible = page == Page.PARAMETERS
                    && b.getY() >= topPos + CONTENT_TOP
                    && b.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - 22;
        }
        int routeWidth = routeButtonWidth();
        int routeGap = 8;
        int routeX = routeButtonStartX();
        for (int i = 0; i < routingWidgets.size(); i++) {
            Button b = routingWidgets.get(i);
            b.setX(routeX + (i % 4) * (routeWidth + routeGap));
            b.setY(topPos + CONTENT_TOP + 92 - scrollOffset);
            b.visible = page == Page.ROUTING
                    && b.getY() >= topPos + CONTENT_TOP
                    && b.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - 22;
        }
        int evidenceWidth = evidenceButtonWidth();
        int evidenceGap = 12;
        int evidenceX = evidenceButtonStartX();
        for (int i = 0; i < evidenceWidgets.size(); i++) {
            Button b = evidenceWidgets.get(i);
            b.setX(evidenceX + i * (evidenceWidth + evidenceGap));
            b.setY(topPos + EVIDENCE_ACTION_VIRTUAL_Y - scrollOffset);
            b.visible = page == Page.EVIDENCE
                    && b.getY() >= topPos + CONTENT_TOP
                    && b.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - 22;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + 18 && mouseX <= leftPos + imageWidth - 18
                && mouseY >= topPos + CONTENT_TOP && mouseY <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int)Math.round(scrollY * 24.0)));
            updateVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int contentHeight() {
        return switch (page) {
            case OPERATE -> 460;
            case PARAMETERS -> 620;
            case MODEL -> 820;
            case RESPONSE -> 520;
            case ROUTING -> 500;
            case EVIDENCE -> 760;
        };
    }

    private int maxScroll() {
        int visible = Math.max(90, imageHeight - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
        return Math.max(0, contentHeight() - visible);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
        g.fill(leftPos + 5, topPos + 5, leftPos + imageWidth - 5, topPos + imageHeight - 5, PAGE);
        g.fill(leftPos + 18, topPos + 29, leftPos + imageWidth - 18, topPos + 30, RULE);
        g.fill(leftPos + 18, topPos + 66, leftPos + imageWidth - 18, topPos + 67, RULE);
        g.fill(leftPos + 18, topPos + imageHeight - CONTENT_BOTTOM_MARGIN,
                leftPos + imageWidth - 18, topPos + imageHeight - CONTENT_BOTTOM_MARGIN + 1, RULE);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 18, 12, INK, false);
        String live = "SERVER CONTROL MODEL";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, page.label.toUpperCase(), 24, 72, ACCENT, false);

        g.enableScissor(leftPos + 18, topPos + CONTENT_TOP, leftPos + imageWidth - 18,
                topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);
        switch (page) {
            case OPERATE -> operate(g);
            case PARAMETERS -> parameters(g);
            case MODEL -> model(g);
            case RESPONSE -> response(g);
            case ROUTING -> routing(g);
            case EVIDENCE -> evidence(g);
        }
        g.pose().popPose();
        g.disableScissor();

        if (maxScroll() > 0) {
            String scroll = "SCROLL " + scrollOffset + " / " + maxScroll();
            g.drawString(font, scroll, imageWidth - 24 - font.width(scroll), 72, MUTED, false);
        }

        String footer = "Discrete PID • 2-tick control cycle • derivative on measured PV • bounded output 0..15";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void operate(GuiGraphics g) {
        pair(g, "Mode", menu.inhibited() ? "INHIBITED" : menu.manualMode() ? "MANUAL" : "AUTO", CONTENT_TOP + 34);
        pair(g, "Setpoint SP", menu.setpoint() + " / 15", CONTENT_TOP + 76);
        pair(g, "Process value PV", menu.processValue() + " / 15", CONTENT_TOP + 118);
        pair(g, "Error e = SP − PV", signed(menu.error()), CONTENT_TOP + 160);
        pair(g, "Control output u", menu.controlOutput() + " / 15", CONTENT_TOP + 202);
        pair(g, "Actuator target", menu.actuatorTarget() + " / 15", CONTENT_TOP + 244);
        pair(g, "Slew limiting", menu.slewActive() ? "ACTIVE" : "CLEAR", CONTENT_TOP + 286);
    }

    private void parameters(GuiGraphics g) {
        g.drawString(font, "Preset template", 42, CONTENT_TOP + 18, MUTED, false);
        g.drawString(font, tuningName(menu.tuning()), 190, CONTENT_TOP + 18, INK, false);

        parameterLine(g, "Kp", Integer.toString(menu.kp()), CONTENT_TOP + 64);
        parameterLine(g, "Ki divisor", menu.kiDivisor() == 0 ? "0  (integral disabled)" : Integer.toString(menu.kiDivisor()), CONTENT_TOP + 108);
        parameterLine(g, "Kd", Integer.toString(menu.kd()), CONTENT_TOP + 152);
        parameterLine(g, "Derivative smoothing", Integer.toString(menu.derivativeSmoothing()), CONTENT_TOP + 196);
        parameterLine(g, "Rise limit", menu.riseLimit() + " level / cycle", CONTENT_TOP + 240);
        parameterLine(g, "Fall limit", menu.fallLimit() + " level / cycle", CONTENT_TOP + 284);

        g.drawString(font, "u* = bias + Kp·e + I/Ki − Kd·d(PV)", 42, CONTENT_TOP + 350, INK, false);
        g.drawString(font, fit("Parameter controls stay aligned with this scrollable engineering sheet instead of being squeezed into a fixed 300 px panel.", Math.max(300,imageWidth-96)),
                42, CONTENT_TOP + 410, MUTED, false);
    }

    private void model(GuiGraphics g) {
        int w = Math.max(300, imageWidth - 96);
        g.drawString(font, "DISCRETE PID CONTROL MODEL", 42, CONTENT_TOP + 22, MUTED, false);
        int y = CONTENT_TOP + 58;
        y = drawWrapped(g, "Control cycle = 2 ticks. Engineering boundary: setpoint, process value and controller output are bounded to the Redstone 0..15 range.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Raw error: e_raw[k] = SP[k] − PV[k]. With deadband = 1, e[k] = 0 when |e_raw| ≤ 1; otherwise e[k] = e_raw[k].", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Derivative is taken on the measured process value, not on setpoint: d_raw = PV[k] − PV[k−1]. Filtered derivative d[k] = d[k−1] + (d_raw − d[k−1]) / max(1, D_smoothing).", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Candidate integral: I* = clamp(I[k] + e[k], −180, 180). P = Kp·e. Iterm = 0 when Ki divisor = 0, otherwise I*/Ki. D = −Kd·d[k].", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Unsaturated command: u_unsat = bias + P + Iterm + D. Actuator target u_target = clamp(u_unsat, 0, 15).", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Actuator command has asymmetric slew: rising output advances by at most the configured rise limit per control cycle; falling output uses the independent fall limit.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Anti-windup is conditional integration: the candidate integral is committed only when the controller is not saturated against the error direction and the actuator slew limit is not blocking correction in that same direction.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Manual → AUTO transfer is bumpless: derivative state is reset and bias is recomputed from the current output so the automatic law starts from the existing command instead of jumping.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Fail-safe rule: stale safety/mode evidence, or missing required AUTO setpoint/process evidence, drives output to 0 without treating the missing observation as a fabricated numeric zero sample.", 42, y, w, MUTED) + 18;
        drawWrapped(g, "The HMI displays synchronized parameters, response metrics and retained evidence only; this page does not execute a second PID solver.", 42, y, w, MUTED);
    }

    private void response(GuiGraphics g) {
        pair(g, "Rise to 90%", metric(menu.rise90Ticks()), CONTENT_TOP + 34);
        pair(g, "Settling time", metric(menu.settlingTicks()), CONTENT_TOP + 76);
        pair(g, "Overshoot", Integer.toString(menu.overshoot()), CONTENT_TOP + 118);
        pair(g, "Saturation events", Integer.toString(menu.saturationEvents()), CONTENT_TOP + 160);
        pair(g, "Slew-limit episodes", Integer.toString(menu.slewEvents()), CONTENT_TOP + 202);
        pair(g, "Controller status", menu.controllerStatus().name() + " • " + menu.controllerScore() + "/100", CONTENT_TOP + 244);
        String note = "Change one tuning parameter, create the same setpoint step, then compare rise/settling/overshoot instead of relying on preset labels.";
        g.drawString(font, fit(note, Math.max(300,imageWidth-96)), 42, CONTENT_TOP + 310, MUTED, false);
    }

    private void routing(GuiGraphics g) {
        pair(g, "Setpoint input RX", menu.inputFacing().getName().toUpperCase(), CONTENT_TOP + 34);
        pair(g, "Control output TX", menu.outputFacing().getName().toUpperCase(), CONTENT_TOP + 76);
        pair(g, "Process value", menu.outputFacing().getCounterClockWise().getName().toUpperCase(), CONTENT_TOP + 150);
        pair(g, "Inhibit", menu.outputFacing().getClockWise().getName().toUpperCase(), CONTENT_TOP + 192);
        pair(g, "Mode select", "UP", CONTENT_TOP + 234);
        pair(g, "Manual output", "DOWN", CONTENT_TOP + 276);
    }

    private void evidence(GuiGraphics g) {
        pair(g, "Commissioning", menu.status().name() + " • " + menu.score() + "/100", CONTENT_TOP + 28);
        pair(g, "Retained captures", menu.historyCount() + " / 8", CONTENT_TOP + 68);

        if (menu.historyCount() == 0) {
            pair(g, "Latest capture", "NONE", CONTENT_TOP + 108);
            drawWrapped(g, "No retained acceptance capture yet. Use Capture acceptance after a representative run; reset clears live runtime/trend but preserves retained acceptance history.",
                    42, CONTENT_TOP + 148, Math.max(300, imageWidth - 96), MUTED);
        } else {
            String latest = "#" + menu.latestSequence() + " • " + menu.latestAcceptanceStatus().name()
                    + " • score " + menu.latestAcceptanceScore();
            g.drawString(font, "Latest capture", 42, CONTENT_TOP + 108, MUTED, false);
            g.drawString(font, fit(latest, Math.max(180, imageWidth - Math.min(320, imageWidth / 2) - 56)),
                    Math.min(320, imageWidth / 2), CONTENT_TOP + 108, acceptanceColor(menu.latestAcceptanceStatus()), false);
            AcceptanceEvidenceTrend trend = menu.comparisonTrend();
            String comparison = trend == null
                    ? "Baseline capture established; capture again after a change to compare."
                    : "Compared with previous: " + trend.name()
                        + " • Δscore " + signed(menu.scoreDelta())
                        + " • Δissues " + signed(menu.topologyIssueDelta());
            drawWrapped(g, comparison, 42, CONTENT_TOP + 148, Math.max(300, imageWidth - 96),
                    trend == null ? MUTED : comparisonColor(trend));
        }

        int plotX = 58;
        int plotY = CONTENT_TOP + 214;
        int plotWidth = Math.max(240, imageWidth - 116);
        int plotHeight = 96;
        EngineeringPlot.analogFrame(g, plotX, plotY, plotWidth, plotHeight);
        EngineeringPlot.analogTrace(g, PidControllerMenu.TREND_SAMPLES, menu::trendSetpoint,
                0, 15, plotX, plotY, plotWidth, plotHeight, SP_COLOR);
        EngineeringPlot.analogTrace(g, PidControllerMenu.TREND_SAMPLES, menu::trendProcessValue,
                0, 15, plotX, plotY, plotWidth, plotHeight, PV_COLOR);
        EngineeringPlot.analogTrace(g, PidControllerMenu.TREND_SAMPLES, menu::trendControlOutput,
                0, 15, plotX, plotY, plotWidth, plotHeight, OUT_COLOR);
        g.drawString(font, "15", 42, plotY - 3, MUTED, false);
        g.drawString(font, "0", 48, plotY + plotHeight - 4, MUTED, false);
        g.drawString(font, "SP", 58, CONTENT_TOP + 320, SP_COLOR, false);
        g.drawString(font, "PV", 92, CONTENT_TOP + 320, PV_COLOR, false);
        g.drawString(font, "OUT", 126, CONTENT_TOP + 320, OUT_COLOR, false);
        String newest = "newest →";
        g.drawString(font, newest, Math.max(170, imageWidth - 58 - font.width(newest)), CONTENT_TOP + 320, MUTED, false);
        g.drawString(font, menu.trendCount() + "/32 authoritative samples • 2t/sample • transient",
                58, CONTENT_TOP + 340, MUTED, false);

        if (menu.plantDetected()) {
            pair(g, "Plant witness", menu.plantStatus().name() + " • " + diagnosis(menu.plantDiagnosis()), CONTENT_TOP + 390);
            pair(g, "Plant position / target", menu.plantPosition() + " / " + menu.plantTarget(), CONTENT_TOP + 430);
            pair(g, "Actuator / supply pressure", menu.plantPressure() + " / " + menu.plantSupply(), CONTENT_TOP + 470);
        } else {
            g.drawString(font, "No explicit pneumatic plant witness detected.", 42, CONTENT_TOP + 390, MUTED, false);
        }
    }

    private void parameterLine(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        g.drawString(font, value, Math.min(300, imageWidth / 2), y, INK, false);
    }

    private void pair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        int x = Math.min(320, imageWidth / 2);
        g.drawString(font, fit(value, Math.max(180, imageWidth - x - 56)), x, y, INK, false);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, color, false);
            y += 14;
        }
        return y;
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String metric(int ticks) { return ticks > 0 ? ticks + " ticks" : "—"; }
    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    private static String tuningName(int tuning) {
        return switch (tuning) {
            case 0 -> "P-GENTLE";
            case 1 -> "PI";
            case 2 -> "PID-BALANCED";
            case 3 -> "PID-AGGRESSIVE";
            default -> "CUSTOM";
        };
    }

    private static int acceptanceColor(EngineeringAcceptanceStatus status) {
        return switch (status) {
            case PASS -> GOOD;
            case MARGINAL -> WARN;
            case FAIL -> BAD;
            case NOT_READY -> MUTED;
        };
    }

    private static int comparisonColor(AcceptanceEvidenceTrend trend) {
        return switch (trend) {
            case IMPROVED -> GOOD;
            case SAME -> ACCENT;
            case REGRESSED -> BAD;
            case INCOMPARABLE -> WARN;
        };
    }

    private static String diagnosis(PneumaticClosedLoopWitness.Diagnosis diagnosis) {
        return switch (diagnosis) {
            case NO_WITNESS -> "NO WITNESS";
            case COLLECTING_EVIDENCE -> "COLLECTING";
            case NOMINAL -> "NOMINAL";
            case NO_SUPPLY -> "NO SUPPLY";
            case RESTRICTION -> "RESTRICTION";
            case LOW_ACTUATOR_PRESSURE -> "LOW PRESSURE";
            case STALLED -> "STALLED";
        };
    }
}
