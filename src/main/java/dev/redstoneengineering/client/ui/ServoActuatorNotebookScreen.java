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
        super.init();
        parameterWidgets.clear();
        evidenceWidgets.clear();

        int tabWidth = 108;
        int x = leftPos + 26;
        for (PageTab value : PageTab.values()) {
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> {
                page = value;
                updateVisibility();
            }).bounds(x, topPos + 34, tabWidth, 20).build());
            x += tabWidth + 6;
        }

        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("◀ Preset"),
                b -> send(ServoActuatorMenu.BUTTON_PRESET_PREVIOUS))
                .bounds(leftPos + 318, topPos + 82, 74, 20).build()));
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("Preset ▶"),
                b -> send(ServoActuatorMenu.BUTTON_PRESET_NEXT))
                .bounds(leftPos + 398, topPos + 82, 74, 20).build()));

        addParameterControl(0, ServoActuatorMenu.BUTTON_MAX_SPEED_MINUS, ServoActuatorMenu.BUTTON_MAX_SPEED_PLUS);
        addParameterControl(1, ServoActuatorMenu.BUTTON_ACCEL_PERIOD_MINUS, ServoActuatorMenu.BUTTON_ACCEL_PERIOD_PLUS);
        addParameterControl(2, ServoActuatorMenu.BUTTON_ACCEL_STEP_MINUS, ServoActuatorMenu.BUTTON_ACCEL_STEP_PLUS);

        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Home / reset trajectory"),
                b -> send(ServoActuatorMenu.BUTTON_HOME_RESET))
                .bounds(leftPos + 166, topPos + 214, 168, 20).build()));

        updateVisibility();
    }

    private void addParameterControl(int row, int minusId, int plusId) {
        int y = topPos + 126 + row * 38;
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("−"),
                b -> send(minusId)).bounds(leftPos + 340, y, 36, 20).build()));
        parameterWidgets.add(addRenderableWidget(Button.builder(Component.literal("+"),
                b -> send(plusId)).bounds(leftPos + 430, y, 36, 20).build()));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateVisibility() {
        for (Button b : parameterWidgets) b.visible = page == PageTab.PARAMETERS;
        for (Button b : evidenceWidgets) b.visible = page == PageTab.EVIDENCE;
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
        String live = "SERVER MECHANICAL MODEL";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, page.label.toUpperCase(), 22, 70, ACCENT, false);

        switch (page) {
            case OPERATE -> operate(g);
            case PARAMETERS -> parameters(g);
            case RESPONSE -> response(g);
            case EVIDENCE -> evidence(g);
        }

        String footer = "Servo position 0..15 • command/mode/brake are physical ports • parameters modify authoritative motion";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void operate(GuiGraphics g) {
        pair(g, "Position", menu.position() + " / 15", 98);
        pair(g, "Command", menu.command() + " / 15", 124);
        pair(g, "Velocity", signed(menu.velocity()), 150);
        pair(g, "Position / velocity error", signed(menu.error()), 176);
        pair(g, "Brake", menu.braking() ? "ACTIVE" : "RELEASED", 202);
        pair(g, "Mechanical preset", presetName(menu.preset()), 228);
    }

    private void parameters(GuiGraphics g) {
        g.drawString(font, "Mechanical preset", 30, 88, MUTED, false);
        g.drawString(font, presetName(menu.preset()), 170, 88, INK, false);

        parameter(g, "Maximum speed", menu.maxSpeed() + " position units / 2t", 128);
        parameter(g, "Acceleration period", menu.accelerationPeriod() + " control cycles", 166);
        parameter(g, "Acceleration step", menu.accelerationStep() + " velocity units / update", 204);

        g.drawString(font, fit("Preset loads are starting points only. These three parameters independently define the actual motion model.", 430),
                30, 242, MUTED, false);
    }

    private void response(GuiGraphics g) {
        pair(g, "Current position", Integer.toString(menu.position()), 98);
        pair(g, "Current velocity", signed(menu.velocity()), 124);
        pair(g, "Current error", signed(menu.error()), 150);
        pair(g, "Load-delay ticks", Integer.toString(menu.loadDelayTicks()), 176);
        pair(g, "Motion samples", Integer.toString(menu.motionSamples()), 202);
        pair(g, "Reversals", Integer.toString(menu.reversals()), 228);
    }

    private void evidence(GuiGraphics g) {
        pair(g, "Soft-limit hits", Integer.toString(menu.softLimitHits()), 104);
        pair(g, "Load-delay ticks", Integer.toString(menu.loadDelayTicks()), 132);
        pair(g, "Motion samples", Integer.toString(menu.motionSamples()), 160);
        pair(g, "Reversals", Integer.toString(menu.reversals()), 188);
        g.drawString(font, fit("Home/reset clears transient trajectory evidence but does not erase your mechanical parameter configuration.", 430),
                30, 244, MUTED, false);
    }

    private void parameter(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        g.drawString(font, fit(value, 180), 190, y, INK, false);
    }

    private void pair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        g.drawString(font, fit(value, 210), 230, y, INK, false);
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
