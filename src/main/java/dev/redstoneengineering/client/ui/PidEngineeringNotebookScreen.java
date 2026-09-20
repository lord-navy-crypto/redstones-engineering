package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.diagnostics.PneumaticClosedLoopWitness;
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

    private enum Page {
        OPERATE("Operate"),
        PARAMETERS("Parameters"),
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
        super.init();
        parameterWidgets.clear();
        routingWidgets.clear();
        evidenceWidgets.clear();

        int tabWidth = 91;
        int gap = 5;
        int x = leftPos + 20;
        int tabY = topPos + 34;
        for (Page value : Page.values()) {
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> {
                page = value;
                updateVisibility();
            }).bounds(x, tabY, tabWidth, 20).build());
            x += tabWidth + gap;
        }

        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("◀ Preset"),
                b -> send(PidControllerMenu.BUTTON_TUNING_PREVIOUS))
                .bounds(leftPos + 330, topPos + 78, 76, 20).build()));
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("Preset ▶"),
                b -> send(PidControllerMenu.BUTTON_TUNING_NEXT))
                .bounds(leftPos + 412, topPos + 78, 76, 20).build()));

        addParameterRow(0, "Kp", PidControllerMenu.BUTTON_KP_MINUS, PidControllerMenu.BUTTON_KP_PLUS);
        addParameterRow(1, "Ki divisor", PidControllerMenu.BUTTON_KI_DIV_MINUS, PidControllerMenu.BUTTON_KI_DIV_PLUS);
        addParameterRow(2, "Kd", PidControllerMenu.BUTTON_KD_MINUS, PidControllerMenu.BUTTON_KD_PLUS);
        addParameterRow(3, "D smoothing", PidControllerMenu.BUTTON_DSMOOTH_MINUS, PidControllerMenu.BUTTON_DSMOOTH_PLUS);
        addParameterRow(4, "Rise limit", PidControllerMenu.BUTTON_RISE_MINUS, PidControllerMenu.BUTTON_RISE_PLUS);
        addParameterRow(5, "Fall limit", PidControllerMenu.BUTTON_FALL_MINUS, PidControllerMenu.BUTTON_FALL_PLUS);

        int routeY = topPos + 132;
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("RX ◀"),
                b -> send(PidControllerMenu.BUTTON_INPUT_PREVIOUS))
                .bounds(leftPos + 95, routeY, 80, 20).build()));
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("RX ▶"),
                b -> send(PidControllerMenu.BUTTON_INPUT_NEXT))
                .bounds(leftPos + 181, routeY, 80, 20).build()));
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("TX ◀"),
                b -> send(PidControllerMenu.BUTTON_OUTPUT_PREVIOUS))
                .bounds(leftPos + 267, routeY, 80, 20).build()));
        routingWidgets.add(addRenderableWidget(Button.builder(Component.literal("TX ▶"),
                b -> send(PidControllerMenu.BUTTON_OUTPUT_NEXT))
                .bounds(leftPos + 353, routeY, 80, 20).build()));

        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Capture acceptance"),
                b -> send(PidControllerMenu.BUTTON_CAPTURE_ACCEPTANCE))
                .bounds(leftPos + 92, topPos + 211, 150, 20).build()));
        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Reset runtime + trend"),
                b -> send(PidControllerMenu.BUTTON_RESET_RUNTIME_TREND))
                .bounds(leftPos + 278, topPos + 211, 150, 20).build()));

        updateVisibility();
    }

    private void addParameterRow(int row, String label, int minusId, int plusId) {
        int y = topPos + 111 + row * 25;
        Button minus = addRenderableWidget(Button.builder(Component.literal("−"),
                b -> send(minusId)).bounds(leftPos + 338, y, 34, 20).build());
        Button plus = addRenderableWidget(Button.builder(Component.literal("+"),
                b -> send(plusId)).bounds(leftPos + 444, y, 34, 20).build());
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
        for (Button b : parameterWidgets) b.visible = page == Page.PARAMETERS;
        for (Button b : routingWidgets) b.visible = page == Page.ROUTING;
        for (Button b : evidenceWidgets) b.visible = page == Page.EVIDENCE;
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
        g.fill(leftPos + 18, topPos + 62, leftPos + imageWidth - 18, topPos + 63, RULE);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 18, 12, INK, false);
        String live = "SERVER CONTROL MODEL";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, page.label.toUpperCase(), 20, 70, ACCENT, false);

        switch (page) {
            case OPERATE -> operate(g);
            case PARAMETERS -> parameters(g);
            case RESPONSE -> response(g);
            case ROUTING -> routing(g);
            case EVIDENCE -> evidence(g);
        }

        String footer = "Discrete PID • 2-tick control cycle • derivative on measured PV • bounded output 0..15";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void operate(GuiGraphics g) {
        pair(g, "Mode", menu.inhibited() ? "INHIBITED" : menu.manualMode() ? "MANUAL" : "AUTO", 96);
        pair(g, "Setpoint SP", menu.setpoint() + " / 15", 120);
        pair(g, "Process value PV", menu.processValue() + " / 15", 144);
        pair(g, "Error e = SP − PV", signed(menu.error()), 168);
        pair(g, "Control output u", menu.controlOutput() + " / 15", 192);
        pair(g, "Actuator target", menu.actuatorTarget() + " / 15", 216);
        pair(g, "Slew limiting", menu.slewActive() ? "ACTIVE" : "CLEAR", 240);
    }

    private void parameters(GuiGraphics g) {
        g.drawString(font, "Preset template", 28, 84, MUTED, false);
        g.drawString(font, tuningName(menu.tuning()), 152, 84, INK, false);

        parameterLine(g, "Kp", Integer.toString(menu.kp()), 112);
        parameterLine(g, "Ki divisor", menu.kiDivisor() == 0 ? "0  (integral disabled)" : Integer.toString(menu.kiDivisor()), 137);
        parameterLine(g, "Kd", Integer.toString(menu.kd()), 162);
        parameterLine(g, "Derivative smoothing", Integer.toString(menu.derivativeSmoothing()), 187);
        parameterLine(g, "Rise limit", menu.riseLimit() + " level / cycle", 212);
        parameterLine(g, "Fall limit", menu.fallLimit() + " level / cycle", 237);

        g.drawString(font, "u* = bias + Kp·e + I/Ki − Kd·d(PV)", 28, 263, INK, false);
    }

    private void response(GuiGraphics g) {
        pair(g, "Rise to 90%", metric(menu.rise90Ticks()), 96);
        pair(g, "Settling time", metric(menu.settlingTicks()), 120);
        pair(g, "Overshoot", Integer.toString(menu.overshoot()), 144);
        pair(g, "Saturation events", Integer.toString(menu.saturationEvents()), 168);
        pair(g, "Slew-limit episodes", Integer.toString(menu.slewEvents()), 192);
        pair(g, "Controller status", menu.controllerStatus().name() + " • " + menu.controllerScore() + "/100", 216);
        String note = "Change one tuning parameter, create the same setpoint step, then compare rise/settling/overshoot instead of relying on preset labels.";
        g.drawString(font, fit(note, 460), 28, 246, MUTED, false);
    }

    private void routing(GuiGraphics g) {
        pair(g, "Setpoint input RX", menu.inputFacing().getName().toUpperCase(), 96);
        pair(g, "Control output TX", menu.outputFacing().getName().toUpperCase(), 120);
        pair(g, "Process value", menu.outputFacing().getCounterClockWise().getName().toUpperCase(), 174);
        pair(g, "Inhibit", menu.outputFacing().getClockWise().getName().toUpperCase(), 198);
        pair(g, "Mode select", "UP", 222);
        pair(g, "Manual output", "DOWN", 246);
    }

    private void evidence(GuiGraphics g) {
        pair(g, "Commissioning", menu.status().name() + " • " + menu.score() + "/100", 96);
        pair(g, "Retained captures", menu.historyCount() + " / 8", 120);
        pair(g, "Latest capture", menu.historyCount() == 0 ? "NONE" : "#" + menu.latestSequence() + " • " + menu.latestAcceptanceStatus().name(), 144);
        pair(g, "Score delta", menu.historyCount() < 2 ? "—" : signed(menu.scoreDelta()), 168);
        pair(g, "Topology issue delta", menu.historyCount() < 2 ? "—" : signed(menu.topologyIssueDelta()), 192);

        if (menu.plantDetected()) {
            pair(g, "Plant witness", menu.plantStatus().name() + " • " + diagnosis(menu.plantDiagnosis()), 246);
        } else {
            g.drawString(font, "No explicit pneumatic plant witness detected.", 28, 246, MUTED, false);
        }
    }

    private void parameterLine(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        g.drawString(font, value, 190, y, INK, false);
    }

    private void pair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        g.drawString(font, fit(value, 245), 235, y, INK, false);
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
