package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.MultiPhysicsParameterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Full-page, device-aware parameter notebook for ten multi-physics engineering blocks. */
public final class MultiPhysicsParameterNotebookScreen extends AbstractContainerScreen<MultiPhysicsParameterMenu> {
    private static final int BG = 0xFFF2E9D8;
    private static final int PAGE = 0xFFFFF8E8;
    private static final int INK = 0xFF2C2925;
    private static final int MUTED = 0xFF6E675E;
    private static final int RULE = 0xFFB9A98F;
    private static final int ACCENT = 0xFF5D557F;
    private static final int GOOD = 0xFF2F7D4A;

    private enum Tab {
        OPERATE("Operate"),
        PARAMETERS("Parameters"),
        MODEL("Model");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private Tab tab = Tab.PARAMETERS;
    private final List<Button> parameterButtons = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 84;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    public MultiPhysicsParameterNotebookScreen(MultiPhysicsParameterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 520;
        imageHeight = 292;
        titleLabelX = 18;
        titleLabelY = 12;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(360, width - VIEW_MARGIN * 2);
        imageHeight = Math.max(240, height - VIEW_MARGIN * 2);
        super.init();
        parameterButtons.clear();
        scrollOffset = 0;

        int count = Tab.values().length;
        int gap = 8;
        int tabWidth = Math.max(88, (imageWidth - 48 - gap * (count - 1)) / count);
        int x = leftPos + 24;
        for (Tab value : Tab.values()) {
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> {
                tab = value;
                scrollOffset = 0;
                updateVisibility();
            }).bounds(x, topPos + 38, tabWidth, 22).build());
            x += tabWidth + gap;
        }

        addPair(0, CONTENT_TOP + 52);
        addPair(1, CONTENT_TOP + 110);
        addPair(2, CONTENT_TOP + 168);
        updateVisibility();
    }

    private void addPair(int slot, int virtualY) {
        int minus = switch (slot) {
            case 0 -> MultiPhysicsParameterMenu.BUTTON_P0_MINUS;
            case 1 -> MultiPhysicsParameterMenu.BUTTON_P1_MINUS;
            default -> MultiPhysicsParameterMenu.BUTTON_P2_MINUS;
        };
        int plus = switch (slot) {
            case 0 -> MultiPhysicsParameterMenu.BUTTON_P0_PLUS;
            case 1 -> MultiPhysicsParameterMenu.BUTTON_P1_PLUS;
            default -> MultiPhysicsParameterMenu.BUTTON_P2_PLUS;
        };
        int y = topPos + virtualY - scrollOffset;
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("−"), b -> send(minus))
                .bounds(leftPos + imageWidth - 164, y, 42, 22).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(plus))
                .bounds(leftPos + imageWidth - 78, y, 42, 22).build()));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateVisibility();
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
        return switch (tab) {
            case OPERATE -> 430;
            case PARAMETERS -> 520;
            case MODEL -> 780;
        };
    }

    private int maxScroll() {
        int visible = Math.max(80, imageHeight - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
        return Math.max(0, contentHeight() - visible);
    }

    private void updateVisibility() {
        int count = parameterCount();
        for (int i = 0; i < parameterButtons.size(); i++) {
            int row = i / 2;
            Button button = parameterButtons.get(i);
            int virtualY = CONTENT_TOP + 52 + row * 58;
            button.setX((i % 2 == 0) ? leftPos + imageWidth - 164 : leftPos + imageWidth - 78);
            button.setY(topPos + virtualY - scrollOffset);
            button.visible = tab == Tab.PARAMETERS && row < count
                    && button.getY() >= topPos + CONTENT_TOP
                    && button.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - 22;
        }
    }

    private int parameterCount() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR,
                 MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR -> 2;
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> 3;
            default -> 1;
        };
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
        String live = "SERVER PHYSICS";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, tab.label.toUpperCase(), 24, 72, ACCENT, false);

        g.enableScissor(leftPos + 18, topPos + CONTENT_TOP, leftPos + imageWidth - 18,
                topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);
        switch (tab) {
            case OPERATE -> renderOperate(g);
            case PARAMETERS -> renderParameters(g);
            case MODEL -> renderModel(g);
        }
        g.pose().popPose();
        g.disableScissor();

        if (maxScroll() > 0) {
            String scroll = "SCROLL " + scrollOffset + " / " + maxScroll();
            g.drawString(font, scroll, imageWidth - 24 - font.width(scroll), 72, MUTED, false);
        }
        g.drawString(font, fit(deviceFooter(), imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void renderOperate(GuiGraphics g) {
        String[] labels = liveLabels();
        int[] values = {menu.liveA(), menu.liveB(), menu.liveC(), menu.liveD()};
        for (int i = 0; i < labels.length; i++) {
            pair(g, labels[i], liveValue(i, values[i]), 100 + i * 30);
        }
    }

    private void renderParameters(GuiGraphics g) {
        String[] labels = parameterLabels();
        int[] values = {menu.p0(), menu.p1(), menu.p2()};
        for (int i = 0; i < labels.length; i++) {
            int y = CONTENT_TOP + 56 + i * 58;
            g.drawString(font, labels[i], 42, y, MUTED, false);
            g.drawString(font, parameterValue(i, values[i]), Math.min(280, imageWidth / 2), y, INK, false);
        }
        g.drawString(font, fit(parameterHint(), Math.max(280, imageWidth - 96)), 42, CONTENT_TOP + 280, MUTED, false);
    }

    private void renderModel(GuiGraphics g) {
        int w = Math.max(280, imageWidth - 96);
        g.drawString(font, "MULTI-PHYSICS MODEL", 42, CONTENT_TOP + 28, MUTED, false);
        int y = CONTENT_TOP + 66;
        y = drawWrapped(g, modelLine1(), 42, y, w, INK) + 18;
        y = drawWrapped(g, modelLine2(), 42, y, w, INK) + 22;
        y = drawWrapped(g, modelLine3(), 42, y, w, MUTED) + 22;
        y = drawWrapped(g, modelLine4(), 42, y, w, MUTED) + 22;
        drawWrapped(g, "The engineering page extends vertically with assumptions, derivations, response metrics and validation evidence. Scroll instead of compressing or truncating the model.",
                42, y, w, MUTED);
    }

    private String[] parameterLabels() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR -> new String[]{"Period"};
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER -> new String[]{"Division ratio"};
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY -> new String[]{"Rising-edge delay"};
            case MultiPhysicsParameterMenu.KIND_AMETHYST_FILTER -> new String[]{"Target frequency index"};
            case MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR -> new String[]{"Natural frequency", "Q index"};
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR -> new String[]{"Pressure setpoint", "Response rate"};
            case MultiPhysicsParameterMenu.KIND_PROPORTIONAL_VALVE -> new String[]{"Spool response rate"};
            case MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR -> new String[]{"Optical loss"};
            case MultiPhysicsParameterMenu.KIND_OPTICAL_CHANNEL_FILTER -> new String[]{"Pass channel"};
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> new String[]{"Scan range", "Detection mode", "Response mapping"};
            default -> new String[]{"Parameter"};
        };
    }

    private String parameterValue(int slot, int value) {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR,
                 MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY -> value + " ticks";
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER -> "÷" + value;
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR ->
                    slot == 0 ? value + " / 100 pressure" : value + " pressure/tick";
            case MultiPhysicsParameterMenu.KIND_PROPORTIONAL_VALVE -> value + " opening/tick";
            case MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR -> value + " intensity units";
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> switch (slot) {
                case 0 -> value + " blocks";
                case 1 -> switch (value) { case 0 -> "BLOCK"; case 1 -> "ENTITY"; default -> "ANY"; };
                default -> switch (value) { case 0 -> "PROXIMITY"; case 1 -> "DISTANCE"; case 2 -> "THRESHOLD"; default -> "WINDOW"; };
            };
            default -> Integer.toString(value);
        };
    }

    private String[] liveLabels() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR -> new String[]{"Effective period", "Edge count", "Pending change", "Output level"};
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER -> new String[]{"Counted edges", "Phase started", "Input initialized"};
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY -> new String[]{"Queued edges", "Next event", "Dropped edges", "Input initialized"};
            case MultiPhysicsParameterMenu.KIND_AMETHYST_FILTER -> new String[]{"Input frequency", "Input amplitude", "Output amplitude", "Matched"};
            case MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR -> new String[]{"Bandwidth", "Target amplitude", "Actual amplitude", "State"};
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR -> new String[]{"Inlet pressure", "Actual pressure", "Tracking error"};
            case MultiPhysicsParameterMenu.KIND_PROPORTIONAL_VALVE -> new String[]{"Command opening", "Actual opening", "Tracking error", "Reversals"};
            case MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR -> new String[]{"Input intensity", "Output intensity", "Channel", "Fully attenuated"};
            case MultiPhysicsParameterMenu.KIND_OPTICAL_CHANNEL_FILTER -> new String[]{"Input channel", "Input intensity", "Output intensity", "Matched"};
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> new String[]{"Distance", "Cells scanned", "Redstone output", "Scan status"};
            default -> new String[]{"Live"};
        };
    }

    private String liveValue(int index, int value) {
        if (menu.kind() == MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR) {
            if (index == 0) return value + " ticks";
            if (index >= 2) return value != 0 ? "YES" : "NO";
        }
        if (menu.kind() == MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER && index > 0) return value != 0 ? "YES" : "NO";
        if (menu.kind() == MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY) {
            if (index == 1) return value + " ticks";
            if (index == 3) return value != 0 ? "YES" : "NO";
        }
        if ((menu.kind() == MultiPhysicsParameterMenu.KIND_AMETHYST_FILTER
                || menu.kind() == MultiPhysicsParameterMenu.KIND_OPTICAL_CHANNEL_FILTER)
                && index == 3) return value != 0 ? "PASS" : "REJECT";
        if (menu.kind() == MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR && index == 3) {
            return switch (value) { case 1 -> "DRIVEN"; case 2 -> "RING-DOWN"; default -> "IDLE"; };
        }
        if (menu.kind() == MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR && index == 3) return value != 0 ? "YES" : "NO";
        if (menu.kind() == MultiPhysicsParameterMenu.KIND_RANGE_SENSOR && index == 3) {
            return switch (value) { case 1 -> "TARGET"; case 2 -> "CLEAR"; case 3 -> "INCOMPLETE"; default -> "UNINITIALIZED"; };
        }
        return Integer.toString(value);
    }

    private String parameterHint() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR -> "Exact period is latched on the next genuine waveform transition; no early edge is fabricated.";
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER -> "Changing divisor resets phase evidence and waits for a genuine rising edge.";
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY -> "New edges use the new delay; already queued events retain their original remaining time.";
            case MultiPhysicsParameterMenu.KIND_AMETHYST_FILTER -> "Frequency index is intentionally discrete because the Amethyst carrier domain itself is indexed.";
            case MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR -> "Natural frequency and Q are independent physical parameters; Q controls gain, bandwidth and ring response.";
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR -> "Setpoint and diaphragm response rate are independent; inlet pressure still caps achievable output.";
            case MultiPhysicsParameterMenu.KIND_PROPORTIONAL_VALVE -> "Command remains a Redstone input; this parameter changes physical spool tracking rate only.";
            case MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR -> "Loss is passive attenuation; complete attenuation is a valid transfer result, not a fault.";
            case MultiPhysicsParameterMenu.KIND_OPTICAL_CHANNEL_FILTER -> "Channel is a discrete carrier identity, so continuous interpolation would be physically meaningless.";
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> "Range, detection class and output mapping are independent sensing choices.";
            default -> "";
        };
    }

    private String modelLine1() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR -> "Clock transition interval = configured period / 2.";
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER -> "f_out = f_in / N, where N is the configured divisor.";
            case MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY -> "t_emit = t_edge + delay; each captured edge owns its own queued delay.";
            case MultiPhysicsParameterMenu.KIND_AMETHYST_FILTER -> "Pass iff input frequency index = target index; output amplitude = input − 1.";
            case MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR -> "At resonance: target amplitude = input amplitude + 2Q.";
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR -> "P_target = min(P_inlet, P_setpoint).";
            case MultiPhysicsParameterMenu.KIND_PROPORTIONAL_VALVE -> "opening[k+1] approaches command by at most responseRate each tick.";
            case MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR -> "I_out = max(0, I_in − loss).";
            case MultiPhysicsParameterMenu.KIND_OPTICAL_CHANNEL_FILTER -> "Carrier passes only when input channel = selected pass channel.";
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> "Scan advances cell-by-cell to the configured range and records evidence completeness.";
            default -> "";
        };
    }

    private String modelLine2() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR -> "Bandwidth = ±(5 − Q); off-resonance response falls with |f − f0|·Q.";
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR -> "P_actual approaches P_target by responseRate per scheduled update.";
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> "PROXIMITY and DISTANCE mappings convert measured distance to Redstone 0..15.";
            default -> parameterHint();
        };
    }

    private String modelLine3() {
        return "Parameters are server-owned and persistent; opening this notebook does not manufacture measurements.";
    }

    private String modelLine4() {
        return "Topology, upstream evidence and physical network state remain world-owned rather than editable UI variables.";
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, color, false);
            y += 14;
        }
        return y;
    }

    private String deviceFooter() {
        return switch (menu.kind()) {
            case MultiPhysicsParameterMenu.KIND_QUARTZ_OSCILLATOR,
                 MultiPhysicsParameterMenu.KIND_QUARTZ_DIVIDER,
                 MultiPhysicsParameterMenu.KIND_QUARTZ_DELAY -> "Quartz timing domain • parameter changes preserve real edge semantics";
            case MultiPhysicsParameterMenu.KIND_AMETHYST_FILTER,
                 MultiPhysicsParameterMenu.KIND_AMETHYST_RESONATOR -> "Amethyst resonance domain • frequency and amplitude remain network evidence";
            case MultiPhysicsParameterMenu.KIND_PRESSURE_REGULATOR,
                 MultiPhysicsParameterMenu.KIND_PROPORTIONAL_VALVE -> "Pneumatic domain • UI changes device parameters, not network pressure";
            case MultiPhysicsParameterMenu.KIND_OPTICAL_ATTENUATOR,
                 MultiPhysicsParameterMenu.KIND_OPTICAL_CHANNEL_FILTER -> "Optical domain • carrier identity/intensity remain physical network evidence";
            case MultiPhysicsParameterMenu.KIND_RANGE_SENSOR -> "Sensor domain • scan evidence remains world-derived";
            default -> "Engineering parameter notebook";
        };
    }

    private void pair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        int x = Math.min(300, imageWidth / 2);
        g.drawString(font, fit(value, Math.max(180, imageWidth - x - 56)), x, y, INK, false);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
