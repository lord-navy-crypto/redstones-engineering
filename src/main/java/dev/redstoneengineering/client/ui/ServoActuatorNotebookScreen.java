package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.ServoActuatorBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
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
        MODEL("Model"),
        RESPONSE("Response"),
        ROUTING("Routing"),
        EVIDENCE("Evidence");

        final String label;
        PageTab(String label) { this.label = label; }
    }

    private PageTab page = PageTab.PARAMETERS;
    private final List<Button> parameterWidgets = new ArrayList<>();
    private final List<Button> routeWidgets = new ArrayList<>();
    private final List<Button> evidenceWidgets = new ArrayList<>();
    private int scrollOffset = 0;
    private int horizontalOffset = 0;
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
        routeWidgets.clear();
        evidenceWidgets.clear();
        scrollOffset = 0;
        horizontalOffset = 0;

        int gap = imageWidth < 440 ? 4 : 7;
        int tabWidth = Math.max(48, (imageWidth - 48 - gap * (PageTab.values().length - 1)) / PageTab.values().length);
        int x = leftPos + 24;
        for (PageTab value : PageTab.values()) {
            addRenderableWidget(Button.builder(Component.literal(pageTabLabel(value)), b -> {
                page = value;
                scrollOffset = 0;
                horizontalOffset = 0;
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

        int routeWidth = routeButtonWidth();
        int routeX = routeButtonStartX();
        routeWidgets.add(addRenderableWidget(Button.builder(Component.literal("Rotate layout ◀"),
                b -> send(ServoActuatorMenu.BUTTON_ROTATE_LEFT))
                .bounds(routeX, topPos + CONTENT_TOP + 210, routeWidth, 22).build()));
        routeWidgets.add(addRenderableWidget(Button.builder(Component.literal("Rotate layout ▶"),
                b -> send(ServoActuatorMenu.BUTTON_ROTATE_RIGHT))
                .bounds(routeX + routeWidth + 8, topPos + CONTENT_TOP + 210, routeWidth, 22).build()));

        evidenceWidgets.add(addRenderableWidget(Button.builder(Component.literal("Home / reset trajectory"),
                b -> send(ServoActuatorMenu.BUTTON_HOME_RESET))
                .bounds(leftPos + imageWidth / 2 - 90, topPos + CONTENT_TOP + 240, 180, 22).build()));

        updateVisibility();
    }

    private String pageTabLabel(PageTab value) {
        if (imageWidth >= 460) return value.label;
        return switch (value) {
            case OPERATE -> "Run";
            case PARAMETERS -> "Params";
            case MODEL -> "Model";
            case RESPONSE -> "Resp";
            case ROUTING -> "Route";
            case EVIDENCE -> "Evid";
        };
    }

    private int routeButtonWidth() {
        return Math.max(96, Math.min(140, (imageWidth - 72) / 2));
    }

    private int routeButtonStartX() {
        int total = routeButtonWidth() * 2 + 8;
        return leftPos + Math.max(24, (imageWidth - total) / 2);
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
                b.setX((i == 0 ? leftPos + imageWidth - 194 : leftPos + imageWidth - 108) - horizontalOffset);
                b.setY(topPos + CONTENT_TOP + 10 - scrollOffset);
            } else {
                int row = (i - 2) / 2;
                b.setX((((i - 2) % 2 == 0) ? leftPos + imageWidth - 164 : leftPos + imageWidth - 78) - horizontalOffset);
                b.setY(topPos + CONTENT_TOP + 72 + row * 56 - scrollOffset);
            }
            b.visible = page == PageTab.PARAMETERS && inViewport(b);
        }
        int routeWidth = routeButtonWidth();
        int routeX = routeButtonStartX();
        for (int i = 0; i < routeWidgets.size(); i++) {
            Button b = routeWidgets.get(i);
            b.setX(routeX + i * (routeWidth + 8) - horizontalOffset);
            b.setY(topPos + CONTENT_TOP + 210 - scrollOffset);
            b.visible = page == PageTab.ROUTING && inViewport(b);
        }
        for (Button b : evidenceWidgets) {
            b.setX(leftPos + imageWidth / 2 - 90 - horizontalOffset);
            b.setY(topPos + CONTENT_TOP + 240 - scrollOffset);
            b.visible = page == PageTab.EVIDENCE && inViewport(b);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + 18 && mouseX <= leftPos + imageWidth - 18
                && mouseY >= topPos + CONTENT_TOP && mouseY <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN) {
            double horizontalDelta = Math.abs(scrollX) > 0.01 ? scrollX : (hasShiftDown() ? scrollY : 0.0);
            if (Math.abs(horizontalDelta) > 0.01 && maxHorizontalScroll() > 0) {
                horizontalOffset = Math.max(0, Math.min(maxHorizontalScroll(),
                        horizontalOffset - (int)Math.round(horizontalDelta * 32.0)));
            } else {
                scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int)Math.round(scrollY * 24.0)));
            }
            updateVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int contentHeight() {
        return switch (page) {
            case OPERATE -> 520;
            case PARAMETERS -> 700;
            case MODEL -> 760;
            case RESPONSE -> 560;
            case ROUTING -> 620;
            case EVIDENCE -> 650;
        };
    }

    private int maxScroll() {
        int visible = Math.max(80, imageHeight - CONTENT_TOP - CONTENT_BOTTOM_MARGIN);
        return Math.max(0, contentHeight() - visible);
    }

    private int virtualContentWidth() { return Math.max(imageWidth - 36, 1180); }
    private int maxHorizontalScroll() {
        int visible = Math.max(240, imageWidth - 36);
        return Math.max(0, virtualContentWidth() - visible);
    }
    private boolean inViewport(Button b) {
        return b.getY() >= topPos + CONTENT_TOP
                && b.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - b.getHeight()
                && b.getX() + b.getWidth() >= leftPos + 18
                && b.getX() <= leftPos + imageWidth - 18;
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
        g.pose().translate(-horizontalOffset, -scrollOffset, 0);
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

        if (maxScroll() > 0 || maxHorizontalScroll() > 0) {
            String raw = "SCROLL Y " + scrollOffset + "/" + maxScroll()
                    + " • X " + horizontalOffset + "/" + maxHorizontalScroll()
                    + " • Shift+wheel / trackpad";
            String compact = fit(raw, Math.max(170, imageWidth - 220));
            g.drawString(font, compact, imageWidth - 24 - font.width(compact), 72, MUTED, false);
        }

        String footer = "Servo • rigid layout rotation • server-owned motion • Shift+wheel = horizontal";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void operate(GuiGraphics g) {
        pair(g, "Mode", menu.velocityMode() ? "VELOCITY" : "POSITION", CONTENT_TOP + 34);
        pair(g, "Command", menu.command() + " / 15", CONTENT_TOP + 76);
        pair(g, "Position", menu.position() + " / 15", CONTENT_TOP + 118);
        pair(g, "Applied velocity", signed(menu.velocity()), CONTENT_TOP + 160);
        pair(g, menu.velocityMode() ? "Velocity-command error" : "Position error", signed(menu.error()), CONTENT_TOP + 202);
        pair(g, "Brake", menu.braking() ? "ACTIVE" : "RELEASED", CONTENT_TOP + 244);
        pair(g, "Command quality", qualityName(menu.commandQuality()), CONTENT_TOP + 286);
        pair(g, "Output quality", qualityName(menu.outputQuality()), CONTENT_TOP + 328);
        pair(g, "Mechanical preset", presetName(menu.preset()), CONTENT_TOP + 370);
    }

    private void parameters(GuiGraphics g) {
        g.drawString(font, "Mechanical preset", 42, CONTENT_TOP + 18, MUTED, false);
        g.drawString(font, presetName(menu.preset()), 200, CONTENT_TOP + 18, INK, false);

        parameter(g, "Maximum speed",
                menu.maxSpeed() + " position units / control cycle • allowed "
                        + EngineeringDeviceParameters.ServoParameters.MIN_SPEED_LIMIT + ".."
                        + EngineeringDeviceParameters.ServoParameters.MAX_SPEED_LIMIT, CONTENT_TOP + 76);
        parameter(g, "Acceleration period",
                menu.accelerationPeriod() + " cycles • allowed "
                        + EngineeringDeviceParameters.ServoParameters.MIN_ACCELERATION_PERIOD + ".."
                        + EngineeringDeviceParameters.ServoParameters.MAX_ACCELERATION_PERIOD, CONTENT_TOP + 132);
        parameter(g, "Acceleration step",
                menu.accelerationStep() + " velocity units / update • allowed "
                        + EngineeringDeviceParameters.ServoParameters.MIN_ACCELERATION_STEP + ".."
                        + EngineeringDeviceParameters.ServoParameters.MAX_ACCELERATION_STEP, CONTENT_TOP + 188);

        parameter(g, "Control cycle",
                ServoActuatorBlock.CONTROL_CYCLE_TICKS + " game ticks", CONTENT_TOP + 250);
        parameter(g, "Acceleration update interval",
                menu.accelerationPeriod() + " × " + ServoActuatorBlock.CONTROL_CYCLE_TICKS
                        + " = " + accelerationIntervalTicks() + " ticks", CONTENT_TOP + 292);
        parameter(g, "0 → max-speed ramp",
                "ceil(" + menu.maxSpeed() + " / " + menu.accelerationStep() + ") × "
                        + accelerationIntervalTicks() + " = " + theoreticalRampTicks() + " ticks",
                CONTENT_TOP + 334);

        drawWrapped(g, "The ramp estimate assumes a command that continuously demands maximum speed, no brake, and no soft-limit collision. It is a derived timing bound from synchronized server parameters, not a second motion simulation.",
                42, CONTENT_TOP + 390, Math.max(300, imageWidth - 96), MUTED);
        drawWrapped(g, "Preset loads are starting points only. After a preset is loaded, max speed, acceleration period and acceleration step remain independently configurable server-owned parameters.",
                42, CONTENT_TOP + 470, Math.max(300, imageWidth - 96), MUTED);
    }

    private void model(GuiGraphics g) {
        int w = Math.max(300, imageWidth - 96);
        g.drawString(font, "MECHATRONIC SERVO MODEL", 42, CONTENT_TOP + 22, MUTED, false);
        int y = CONTENT_TOP + 58;
        y = drawWrapped(g, "Server control cycle = " + ServoActuatorBlock.CONTROL_CYCLE_TICKS + " ticks. The servo has two command interpretations selected by the physical MODE input.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "POSITION mode: desiredVelocity = clamp(command − position, −maxSpeed, +maxSpeed). The applied velocity is also limited so it cannot step past the remaining position error.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "VELOCITY mode: velocityCommand = command − 7, so command 7 = stop, 0..6 = reverse and 8..15 = forward. desiredVelocity = clamp(velocityCommand, −maxSpeed, +maxSpeed).", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Acceleration is discrete: every accelerationPeriod control cycles (" + accelerationIntervalTicks() + " game ticks with the current setting), appliedVelocity approaches desiredVelocity by at most accelerationStep. v[k+1] = toward(v[k], v_des, Δv) only on those acceleration updates.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Brake is authoritative: missing command evidence or an asserted brake input forces appliedVelocity = 0 and resets the acceleration phase.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Position update: position[k+1] = clamp(position[k] + appliedVelocity, 0, 15). A boundary clamp increments retained soft-limit-hit evidence and zeroes applied velocity.", 42, y, w, INK) + 18;
        y = drawWrapped(g, "Current theoretical 0→max-speed ramp bound = " + theoreticalRampTicks() + " ticks = ceil(maxSpeed / accelerationStep) × accelerationPeriod × controlCycleTicks, before brake/soft-limit interruptions.", 42, y, w, INFO) + 18;
        y = drawWrapped(g, "Trajectory evidence records total travel, motion samples, reversals, maximum observed velocity, settling time and load-delay ticks. These are server-owned response measurements, not client estimates.", 42, y, w, MUTED) + 18;
        drawWrapped(g, "The notebook renders synchronized state only. It does not advance acceleration phase, integrate position, infer missing control evidence, or run a second servo solver.", 42, y, w, MUTED);
    }

    private void response(GuiGraphics g) {
        pair(g, "Current mode", menu.velocityMode() ? "VELOCITY" : "POSITION", CONTENT_TOP + 34);
        pair(g, "Velocity command", signed(menu.velocityCommand()), CONTENT_TOP + 76);
        pair(g, "Applied velocity", signed(menu.velocity()), CONTENT_TOP + 118);
        pair(g, "Current error", signed(menu.error()), CONTENT_TOP + 160);
        pair(g, "Maximum observed velocity", Integer.toString(menu.maxObservedVelocity()), CONTENT_TOP + 202);
        pair(g, "Settling time", menu.settleTicks() > 0 ? menu.settleTicks() + " ticks" : "—", CONTENT_TOP + 244);
        pair(g, "Total travel", Integer.toString(menu.travel()), CONTENT_TOP + 286);
        pair(g, "Load-delay ticks", Integer.toString(menu.loadDelayTicks()), CONTENT_TOP + 328);
        pair(g, "Motion samples", Integer.toString(menu.motionSamples()), CONTENT_TOP + 370);
        pair(g, "Reversals", Integer.toString(menu.reversals()), CONTENT_TOP + 412);
    }

    private void routing(GuiGraphics g) {
        int w = Math.max(300, imageWidth - 96);
        g.drawString(font, "RIGID SERVO PORT LAYOUT", 42, CONTENT_TOP + 22, MUTED, false);
        pair(g, "FRONT • POSITION OUT", menu.positionOutputDirection().getName().toUpperCase(), CONTENT_TOP + 58);
        pair(g, "BACK • COMMAND IN", menu.commandDirection().getName().toUpperCase(), CONTENT_TOP + 94);
        pair(g, "RIGHT • BRAKE", menu.brakeDirection().getName().toUpperCase(), CONTENT_TOP + 130);
        pair(g, "UP • MODE SELECT", menu.modeDirection().getName().toUpperCase(), CONTENT_TOP + 166);

        int y = CONTENT_TOP + 264;
        y = drawWrapped(g,
                "The servo rotates as one rigid horizontal assembly. COMMAND stays opposite FRONT, BRAKE stays to the servo's right, MODE stays on UP, and POSITION OUT stays on FRONT.",
                42, y, w, INK) + 18;
        y = drawWrapped(g,
                "Rotation changes only physical orientation. It does not independently reroute command, brake or position ports and cannot create an impossible connector layout.",
                42, y, w, MUTED) + 18;
        y = drawWrapped(g,
                "Position, load configuration and retained trajectory evidence survive the rotation. The next scheduled server tick reacquires COMMAND, MODE and BRAKE evidence from the new physical faces.",
                42, y, w, MUTED) + 18;
        drawWrapped(g,
                "Use Evidence after rotation to confirm command/mode/brake PortQuality before interpreting motion response.",
                42, y, w, MUTED);
    }

    private void evidence(GuiGraphics g) {
        pair(g, "Command input quality", qualityName(menu.commandQuality()), CONTENT_TOP + 34);
        pair(g, "Mode input quality", qualityName(menu.modeQuality()), CONTENT_TOP + 76);
        pair(g, "Brake input quality", qualityName(menu.brakeQuality()), CONTENT_TOP + 118);
        pair(g, "Position output quality", qualityName(menu.outputQuality()), CONTENT_TOP + 160);
        pair(g, "Soft-limit hits", Integer.toString(menu.softLimitHits()), CONTENT_TOP + 220);
        pair(g, "Load-delay ticks", Integer.toString(menu.loadDelayTicks()), CONTENT_TOP + 262);
        pair(g, "Motion samples", Integer.toString(menu.motionSamples()), CONTENT_TOP + 304);
        pair(g, "Reversals", Integer.toString(menu.reversals()), CONTENT_TOP + 346);
        pair(g, "Settling time", menu.settleTicks() > 0 ? menu.settleTicks() + " ticks" : "—", CONTENT_TOP + 388);
        pair(g, "Total travel", Integer.toString(menu.travel()), CONTENT_TOP + 430);
        drawWrapped(g, "Home/reset clears transient trajectory evidence but does not erase the configured mechanical parameters.",
                42, CONTENT_TOP + 500, Math.max(300, imageWidth - 96), MUTED);
    }

    private int drawWrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(text), width)) {
            g.drawString(font, line, x, y, color, false);
            y += 14;
        }
        return y;
    }

    private int accelerationIntervalTicks() {
        return menu.accelerationPeriod() * ServoActuatorBlock.CONTROL_CYCLE_TICKS;
    }

    private int theoreticalRampTicks() {
        int updates = (menu.maxSpeed() + menu.accelerationStep() - 1) / menu.accelerationStep();
        return updates * accelerationIntervalTicks();
    }

    private static String qualityName(PortQuality quality) {
        return quality.name().replace('_', ' ');
    }

    private void parameter(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        int x = Math.min(300, imageWidth / 2);
        g.drawString(font, value, x, y, INK, false);
    }

    private void pair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        int x = Math.min(320, imageWidth / 2);
        g.drawString(font, value, x, y, INK, false);
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
