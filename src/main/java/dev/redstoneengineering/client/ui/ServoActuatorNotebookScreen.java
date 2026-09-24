package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.ServoActuatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Full-page engineering notebook for servo mechanical parameters and motion evidence. */
public final class ServoActuatorNotebookScreen extends AbstractContainerScreen<ServoActuatorMenu> {
    private static final int BG = 0xFFF2E9D8;
    private static final int PAGE = 0xFFFFF8E8;
    private static final int INK = 0xFF2C2925;
    private static final int MUTED = 0xFF6E675E;
    private static final int RULE = 0xFFB9A98F;
    private static final int ACCENT = 0xFF4D6C50;
    private static final int GOOD = 0xFF2F7D4A;
    private static final int WARN = 0xFF9A6A19;

    private enum PageTab {
        OPERATE("Operate"),
        PARAMETERS("Parameters"),
        RESPONSE("Response"),
        EVIDENCE("Evidence");

        final String label;
        PageTab(String label) { this.label = label; }
    }

    private PageTab page = PageTab.PARAMETERS;
    private final List<Button> parameterWidgets = new ArrayList<>();
    private final List<Button> evidenceWidgets = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 84;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    public ServoActuatorNotebookScreen(ServoActuatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 500;
        imageHeight = 286;
        titleLabelX = 18;
        titleLabelY = 12;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(360, width - VIEW_MARGIN * 2);
        imageHeight = Math.max(240, height - VIEW_MARGIN * 2);
        super.init();
        parameterWidgets.clear();
        evidenceWidgets.clear();
        scrollOffset = 0;

        int gap = imageWidth < 440 ? 5 : 7;
        int tabWidth = Math.max(64, (imageWidth - 48 - gap * (PageTab.values().length - 1)) / PageTab.values().length);
        int x = leftPos + 24;
        for (PageTab value : PageTab.values()) {
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> {
                page = value;
                scrollOffset = 0;
                updateVisibility();
            }).bounds(x, topPos + 38, tabWidth, 22).build());
            x += tabWidth + gap;
        }

        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("◀ Preset"),
                b -> send(ServoActuatorMenu.BUTTON_PRESET_PREVIOUS))
                .bounds(leftPos + imageWidth - 194, topPos + CONTENT_TOP + 10, 78, 22).build()));
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("Preset ▶"),
                b -> send(ServoActuatorMenu.BUTTON_PRESET_NEXT))
                .bounds(leftPos + imageWidth - 108, topPos + CONTENT_TOP + 10, 78, 22).build()));

        addParameterControl(0, ServoActuatorMenu.BUTTON_MAX_SPEED_MINUS, ServoActuatorMenu.BUTTON_MAX_SPEED_PLUS);
        addParameterControl(1, ServoActuatorMenu.BUTTON_ACCEL_PERIOD_MINUS, ServoActuatorMenu.BUTTON_ACCEL_PERIOD_PLUS);
        addParameterControl(2, ServoActuatorMenu.BUTTON_ACCEL_STEP_MINUS, ServoActuatorMenu.BUTTON_ACCEL_STEP_PLUS);

        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Home / reset trajectory"),
                b -> send(ServoActuatorMenu.BUTTON_HOME_RESET))
                .bounds(leftPos + imageWidth / 2 - 90, topPos + CONTENT_TOP + 240, 180, 22).build()));

        updateVisibility();
    }

    private void addParameterControl(int row, int minusId, int plusId) {
        int y = topPos + CONTENT_TOP + 72 + row * 56 - scrollOffset;
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("−"),
                b -> send(minusId)).bounds(leftPos + imageWidth - 164, y, 42, 22).build()));
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"),
                b -> send(plusId)).bounds(leftPos + imageWidth - 78, y, 42, 22).build()));
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
                b.setY(topPos + CONTENT_TOP + 72 + row * 56 - scrollOffset);
            }
            b.visible = page == PageTab.PARAMETERS
                    && b.getY() >= topPos + CONTENT_TOP
                    && b.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - 22;
        }
        for (Button b : evidenceWidgets) {
            b.setX(leftPos + imageWidth / 2 - 90);
            b.setY(topPos + CONTENT_TOP + 240 - scrollOffset);
            b.visible = page == PageTab.EVIDENCE
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
            case OPERATE -> 450;
            case PARAMETERS -> 540;
            case RESPONSE -> 470;
            case EVIDENCE -> 520;
        };
    }

    private int maxScroll() {
        int visible = Math.max(80, imageHeight - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
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
        String live = "SERVER MECHANICAL MODEL";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, page.label.toUpperCase(), 24, 72, ACCENT, false);

        g.enableScissor(leftPos + 18, topPos + CONTENT_TOP, leftPos + imageWidth - 18,
                topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);
        switch (page) {
            case OPERATE -> operate(g);
            case PARAMETERS -> parameters(g);
            case RESPONSE -> response(g);
            case EVIDENCE -> evidence(g);
        }
        g.pose().popPose();
        g.disableScissor();

        if (maxScroll() > 0) {
            String scroll = "SCROLL " + scrollOffset + " / " + maxScroll();
            g.drawString(font, scroll, imageWidth - 24 - font.width(scroll), 72, MUTED, false);
        }

        String footer = "Servo position 0..15 • command/mode/brake are physical ports • parameters modify authoritative motion";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void operate(GuiGraphics g) {
        pair(g, "Position", menu.position() + " / 15", CONTENT_TOP + 34);
        pair(g, "Command", menu.command() + " / 15", CONTENT_TOP + 76);
        pair(g, "Velocity", signed(menu.velocity()), CONTENT_TOP + 118);
        pair(g, "Position / velocity error", signed(menu.error()), CONTENT_TOP + 160);
        pair(g, "Brake", menu.braking() ? "ACTIVE" : "RELEASED", CONTENT_TOP + 202);
        pair(g, "Mechanical preset", presetName(menu.preset()), CONTENT_TOP + 244);
    }

    private void parameters(GuiGraphics g) {
        g.drawString(font, "Mechanical preset", 42, CONTENT_TOP + 18, MUTED, false);
        g.drawString(font, presetName(menu.preset()), 200, CONTENT_TOP + 18, INK, false);

        parameter(g, "Maximum speed", menu.maxSpeed() + " position units / 2t", CONTENT_TOP + 76);
        parameter(g, "Acceleration period", menu.accelerationPeriod() + " control cycles", CONTENT_TOP + 132);
        parameter(g, "Acceleration step", menu.accelerationStep() + " velocity units / update", CONTENT_TOP + 188);

        g.drawString(font, fit("Preset loads are starting points only. These three parameters independently define the actual motion model.", Math.max(300,imageWidth-96)),
                42, CONTENT_TOP + 270, MUTED, false);
        g.drawString(font, fit("More mechanical assumptions, load response and trajectory diagnostics can extend below without forcing the page into a fixed-height panel.", Math.max(300,imageWidth-96)),
                42, CONTENT_TOP + 350, MUTED, false);
    }

    private void response(GuiGraphics g) {
        pair(g, "Current position", Integer.toString(menu.position()), CONTENT_TOP + 34);
        pair(g, "Current velocity", signed(menu.velocity()), CONTENT_TOP + 76);
        pair(g, "Current error", signed(menu.error()), CONTENT_TOP + 118);
        pair(g, "Load-delay ticks", Integer.toString(menu.loadDelayTicks()), CONTENT_TOP + 160);
        pair(g, "Motion samples", Integer.toString(menu.motionSamples()), CONTENT_TOP + 202);
        pair(g, "Reversals", Integer.toString(menu.reversals()), CONTENT_TOP + 244);
    }

    private void evidence(GuiGraphics g) {
        pair(g, "Soft-limit hits", Integer.toString(menu.softLimitHits()), CONTENT_TOP + 40);
        pair(g, "Load-delay ticks", Integer.toString(menu.loadDelayTicks()), CONTENT_TOP + 88);
        pair(g, "Motion samples", Integer.toString(menu.motionSamples()), CONTENT_TOP + 136);
        pair(g, "Reversals", Integer.toString(menu.reversals()), CONTENT_TOP + 184);
        g.drawString(font, fit("Home/reset clears transient trajectory evidence but does not erase your mechanical parameter configuration.", Math.max(300,imageWidth-96)),
                42, CONTENT_TOP + 300, MUTED, false);
    }

    private void parameter(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        int x = Math.min(300, imageWidth / 2);
        g.drawString(font, fit(value, Math.max(180,imageWidth-x-56)), x, y, INK, false);
    }

    private void pair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        int x = Math.min(320, imageWidth / 2);
        g.drawString(font, fit(value, Math.max(180,imageWidth-x-56)), x, y, INK, false);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

    private static String presetName(int preset) {
        return switch (preset) {
            case 0 -> "UNLOADED";
            case 1 -> "LIGHT";
            case 2 -> "MEDIUM";
            default -> "HEAVY";
        };
    }
}
