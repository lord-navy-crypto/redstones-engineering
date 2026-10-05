package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.LapisLowPassMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Large formula-first engineering workspace for the Lapis low-pass filter.
 *
 * <p>Normal wheel scrolls vertically. Shift+wheel (or a horizontal wheel axis)
 * scrolls wide model/table content horizontally. Physics remains server-owned.</p>
 */
public final class LapisLowPassScreen extends AbstractContainerScreen<LapisLowPassMenu> {
    private enum Page {
        MODEL("Model"),
        LIVE("Live"),
        CONFIGURE("Configure"),
        ROUTE("Route"),
        EVIDENCE("Evidence");

        private final String label;
        Page(String label) { this.label = label; }
    }

    private static final int BORDER = 0xFF5E6D78;
    private static final int PANEL = 0xFF11171D;
    private static final int PANEL_2 = 0xFF1B242C;
    private static final int PANEL_3 = 0xFF0B1015;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF9BA8B3;
    private static final int GOOD = 0xFF68D391;
    private static final int WARN = 0xFFF6C453;
    private static final int BAD = 0xFFF06A6A;
    private static final int INFO = 0xFF9EC8FF;
    private static final int ACCENT = 0xFFE05555;

    private static final int CONTENT_X = 18;
    private static final int CONTENT_Y = 72;
    private static final int FOOTER_HEIGHT = 42;
    private static final int MODEL_WIDTH = 760;

    private final List<Button> pageButtons = new ArrayList<>();
    private final List<Button> configureButtons = new ArrayList<>();
    private final List<Button> routeButtons = new ArrayList<>();
    private Page page = Page.MODEL;
    private int scrollX;
    private int scrollY;

    public LapisLowPassScreen(LapisLowPassMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 620;
        imageHeight = 360;
        inventoryLabelY = 10000;
        titleLabelY = 10000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(360, Math.min(700, width - 20));
        imageHeight = Math.max(250, Math.min(430, height - 20));
        super.init();

        pageButtons.clear();
        configureButtons.clear();
        routeButtons.clear();

        int tabCount = Page.values().length;
        int gap = 4;
        int available = imageWidth - 32 - gap * (tabCount - 1);
        int tabWidth = Math.max(54, available / tabCount);
        int x = leftPos + 16;
        int y = topPos + 34;
        for (Page target : Page.values()) {
            Button button = Button.builder(Component.literal(target.label), b -> setPage(target))
                    .bounds(x, y, tabWidth, 20).build();
            pageButtons.add(addRenderableWidget(button));
            x += tabWidth + gap;
        }

        int bottomY = topPos + imageHeight - 35;
        configureButtons.add(addRenderableWidget(Button.builder(Component.literal("α ◀"),
                b -> sendButton(LapisLowPassMenu.BUTTON_ALPHA_PREVIOUS))
                .bounds(leftPos + 18, bottomY, 70, 20).build()));
        configureButtons.add(addRenderableWidget(Button.builder(Component.literal("Default α"),
                b -> sendButton(LapisLowPassMenu.BUTTON_ALPHA_DEFAULT))
                .bounds(leftPos + 94, bottomY, 92, 20).build()));
        configureButtons.add(addRenderableWidget(Button.builder(Component.literal("α ▶"),
                b -> sendButton(LapisLowPassMenu.BUTTON_ALPHA_NEXT))
                .bounds(leftPos + 192, bottomY, 70, 20).build()));

        routeButtons.add(addRenderableWidget(Button.builder(Component.literal("RX ◀"),
                b -> sendButton(LapisLowPassMenu.BUTTON_INPUT_LEFT))
                .bounds(leftPos + 18, bottomY, 70, 20).build()));
        routeButtons.add(addRenderableWidget(Button.builder(Component.literal("RX ▶"),
                b -> sendButton(LapisLowPassMenu.BUTTON_INPUT_RIGHT))
                .bounds(leftPos + 94, bottomY, 70, 20).build()));
        routeButtons.add(addRenderableWidget(Button.builder(Component.literal("TX ◀"),
                b -> sendButton(LapisLowPassMenu.BUTTON_OUTPUT_LEFT))
                .bounds(leftPos + 176, bottomY, 70, 20).build()));
        routeButtons.add(addRenderableWidget(Button.builder(Component.literal("TX ▶"),
                b -> sendButton(LapisLowPassMenu.BUTTON_OUTPUT_RIGHT))
                .bounds(leftPos + 252, bottomY, 70, 20).build()));

        updateWidgets();
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void setPage(Page next) {
        page = next;
        scrollX = 0;
        scrollY = 0;
        updateWidgets();
    }

    private void updateWidgets() {
        for (int i = 0; i < pageButtons.size(); i++) {
            pageButtons.get(i).active = Page.values()[i] != page;
        }
        for (Button button : configureButtons) button.visible = page == Page.CONFIGURE;
        for (Button button : routeButtons) button.visible = page == Page.ROUTE;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXDelta, double scrollYDelta) {
        if (!insideViewport(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollXDelta, scrollYDelta);
        if (hasShiftDown() || Math.abs(scrollXDelta) > Math.abs(scrollYDelta)) {
            scrollX -= (int) Math.round((scrollXDelta != 0.0 ? scrollXDelta : scrollYDelta) * 28.0);
        } else {
            scrollY -= (int) Math.round(scrollYDelta * 24.0);
        }
        clampScroll();
        return true;
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        int x0 = leftPos + CONTENT_X;
        int y0 = topPos + CONTENT_Y;
        int x1 = leftPos + imageWidth - 18;
        int y1 = topPos + imageHeight - FOOTER_HEIGHT - 4;
        return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
    }

    private int viewportWidth() { return Math.max(1, imageWidth - CONTENT_X - 18); }
    private int viewportHeight() { return Math.max(1, imageHeight - CONTENT_Y - FOOTER_HEIGHT - 4); }

    private int contentWidth() {
        return page == Page.MODEL || page == Page.CONFIGURE ? Math.max(viewportWidth(), MODEL_WIDTH) : viewportWidth();
    }

    private int contentHeight() {
        return switch (page) {
            case MODEL -> 430;
            case LIVE -> 300;
            case CONFIGURE -> 420;
            case ROUTE -> 260;
            case EVIDENCE -> 330;
        };
    }

    private void clampScroll() {
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, contentWidth() - viewportWidth())));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight() - viewportHeight())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, PANEL);
        g.fill(leftPos + 12, topPos + 28, leftPos + imageWidth - 12, topPos + 30, ACCENT);
        g.fill(leftPos + 12, topPos + 62, leftPos + imageWidth - 12, topPos + imageHeight - FOOTER_HEIGHT - 2, PANEL_2);
        g.fill(leftPos + 12, topPos + imageHeight - FOOTER_HEIGHT, leftPos + imageWidth - 12, topPos + imageHeight - 8, PANEL_3);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String live = "● LIVE / SERVER";
        g.drawString(font, title.getString(), 14, 10, TEXT, false);
        g.drawString(font, live, imageWidth - 14 - font.width(live), 10, GOOD, false);
        g.drawString(font, "LAPIS PRECISION • FIRST-ORDER SAMPLED LOW-PASS", 14, 21, INFO, false);

        int x0 = CONTENT_X;
        int y0 = CONTENT_Y;
        int x1 = imageWidth - 18;
        int y1 = imageHeight - FOOTER_HEIGHT - 4;
        g.enableScissor(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1);
        g.pose().pushPose();
        g.pose().translate(-scrollX, -scrollY, 0.0F);
        switch (page) {
            case MODEL -> renderModel(g);
            case LIVE -> renderLive(g);
            case CONFIGURE -> renderConfigure(g);
            case ROUTE -> renderRoute(g);
            case EVIDENCE -> renderEvidence(g);
        }
        g.pose().popPose();
        g.disableScissor();

        String evidence = "EVIDENCE • " + menu.evidenceStateLabel();
        g.drawString(font, evidence, 18, imageHeight - 25, evidenceColor(), false);
        String scroll = "SCROLL X " + scrollX + " / " + Math.max(0, contentWidth() - viewportWidth())
                + "   Y " + scrollY + " / " + Math.max(0, contentHeight() - viewportHeight());
        g.drawString(font, scroll, imageWidth - 18 - font.width(scroll), imageHeight - 25, MUTED, false);
        g.drawString(font, "Wheel: vertical • Shift+wheel: horizontal", 18, imageHeight - 14, MUTED, false);
    }

    private void renderModel(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "GOVERNING EQUATION", y);
        y += 20;
        equation(g, "y[n] = y[n-1] + α · (x[n] - y[n-1])", y);
        y += 28;

        int index = menu.alphaIndex();
        double alpha = LapisLowPassMenu.alphaForIndex(index);
        int input = menu.inputValue();
        int previous = menu.previousOutput();
        int predicted = menu.predictedOutput();

        label(g, "[MEASURED]  x[n]", input + " precision units", y); y += 18;
        label(g, "[SOLVER]    y[n-1]", previous + " precision units", y); y += 18;
        label(g, "[ADJUSTABLE] α", String.format("%.2f  (profile step %d/%d)", alpha, index + 1,
                LapisLowPassMenu.alphaSteps()), y); y += 18;
        label(g, "[PROFILE]   Δt", LapisLowPassMenu.samplePeriodTicks()
                + " ticks  ≈ " + String.format("%.3f s nominal", LapisLowPassMenu.samplePeriodTicks()
                / LapisLowPassMenu.nominalTicksPerSecond()), y); y += 18;

        if (LapisLowPassMenu.bypassForIndex(index)) {
            label(g, "[DERIVED]   τ / fc", "BYPASS • y[n] = x[n]", y); y += 22;
        } else {
            label(g, "[DERIVED]   τ", String.format("%.3f ticks", LapisLowPassMenu.timeConstantTicksForIndex(index)), y); y += 18;
            label(g, "[DERIVED]   fc", String.format("%.4f Hz nominal @ %.0f TPS",
                    LapisLowPassMenu.cutoffHzForIndex(index),
                    LapisLowPassMenu.nominalTicksPerSecond()), y); y += 22;
        }

        rule(g, y); y += 14;
        sectionTitle(g, "LIVE SUBSTITUTION", y); y += 20;
        String substitution = LapisLowPassMenu.bypassForIndex(index)
                ? String.format("y[n] = x[n] = %d", input)
                : String.format("y[n] = %d + %.2f · (%d - %d) = %d", previous, alpha, input, previous, predicted);
        equation(g, substitution, y); y += 28;
        label(g, "Server output", menu.outputValue() + " precision units", y); y += 18;
        label(g, "Residual", menu.runtimePresent() ? (menu.outputValue() - predicted) + " units" : "NOT READY", y); y += 24;

        rule(g, y); y += 14;
        sectionTitle(g, "VARIABLE ROLES", y); y += 20;
        wrapped(g, "x[n] • measured input sample • 0..100 • comes from the Lapis input port.", CONTENT_X, y, 700, TEXT); y += 28;
        wrapped(g, "y[n-1] • solver-retained previous output • not a player knob • observer reads it without creating state.", CONTENT_X, y, 700, TEXT); y += 32;
        wrapped(g, "α • adjustable response coefficient • larger α reacts faster and passes more high-frequency change.", CONTENT_X, y, 700, TEXT); y += 32;
        wrapped(g, "Δt • profile-owned sample interval • currently fixed by rse-default-v1, not by the client GUI.", CONTENT_X, y, 700, TEXT); y += 32;
        wrapped(g, "τ and fc • derived engineering quantities • they explain the physical meaning of α rather than adding decorative controls.", CONTENT_X, y, 700, INFO);
    }

    private void renderLive(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "LIVE SIGNAL PATH", y); y += 22;
        metric(g, "INPUT x[n]", menu.inputValue() + " / 100", menu.inputQuality(), y); y += 42;
        metric(g, "PREVIOUS y[n-1]", menu.previousOutput() + " / 100", menu.runtimePresent() ? PortQuality.VALID : PortQuality.NOT_READY, y); y += 42;
        metric(g, "OUTPUT y[n]", menu.outputValue() + " / 100", menu.outputQuality(), y); y += 48;
        label(g, "RX face", menu.inputDirection().getName().toUpperCase(), y); y += 18;
        label(g, "TX face", menu.outputDirection().getName().toUpperCase(), y); y += 18;
        label(g, "Profile", LapisLowPassMenu.profileId(), y); y += 18;
        label(g, "Sample period", LapisLowPassMenu.samplePeriodTicks() + " ticks", y); y += 24;
        wrapped(g, "The screen never samples the world independently. Values shown here are synchronized evidence from the server-owned device model.",
                CONTENT_X, y, Math.min(620, contentWidth() - 20), INFO);
    }

    private void renderConfigure(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "RESPONSE CONFIGURATION", y); y += 22;
        int index = menu.alphaIndex();
        double alpha = LapisLowPassMenu.alphaForIndex(index);
        label(g, "Selected α", String.format("%.2f", alpha), y); y += 18;
        label(g, "Meaning", LapisLowPassMenu.bypassForIndex(index) ? "BYPASS" : "FIRST-ORDER RESPONSE", y); y += 18;
        label(g, "Default index", Integer.toString(LapisLowPassMenu.defaultAlphaIndex()), y); y += 28;

        sectionTitle(g, "PROFILE RESPONSE TABLE", y); y += 22;
        g.drawString(font, "INDEX", CONTENT_X, y, MUTED, false);
        g.drawString(font, "α", CONTENT_X + 80, y, MUTED, false);
        g.drawString(font, "τ (ticks)", CONTENT_X + 160, y, MUTED, false);
        g.drawString(font, "fc nominal", CONTENT_X + 280, y, MUTED, false);
        g.drawString(font, "INTERPRETATION", CONTENT_X + 420, y, MUTED, false);
        y += 16;
        for (int i = 0; i < LapisLowPassMenu.alphaSteps(); i++) {
            double a = LapisLowPassMenu.alphaForIndex(i);
            int color = i == index ? GOOD : TEXT;
            g.drawString(font, (i == index ? "▶ " : "  ") + i, CONTENT_X, y, color, false);
            g.drawString(font, String.format("%.2f", a), CONTENT_X + 80, y, color, false);
            if (LapisLowPassMenu.bypassForIndex(i)) {
                g.drawString(font, "—", CONTENT_X + 160, y, color, false);
                g.drawString(font, "—", CONTENT_X + 280, y, color, false);
                g.drawString(font, "BYPASS", CONTENT_X + 420, y, color, false);
            } else {
                g.drawString(font, String.format("%.2f", LapisLowPassMenu.timeConstantTicksForIndex(i)), CONTENT_X + 160, y, color, false);
                g.drawString(font, String.format("%.3f Hz", LapisLowPassMenu.cutoffHzForIndex(i)), CONTENT_X + 280, y, color, false);
                g.drawString(font, i < 2 ? "SLOW / HEAVY SMOOTHING" : i < 5 ? "BALANCED" : "FAST RESPONSE", CONTENT_X + 420, y, color, false);
            }
            y += 18;
        }
        y += 12;
        wrapped(g, "Use the fixed controls below the workspace to select the previous/next α or restore the declared profile default. The buttons change the server blockstate; they are not cosmetic sliders.",
                CONTENT_X, y, 700, INFO);
    }

    private void renderRoute(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "PHYSICAL SERIES ROUTE", y); y += 22;
        label(g, "RX / input", menu.inputDirection().getName().toUpperCase(), y); y += 18;
        label(g, "TX / output", menu.outputDirection().getName().toUpperCase(), y); y += 18;
        label(g, "Topology", "RX → FILTER MODEL → TX", y); y += 28;
        wrapped(g, "RX and TX are real block faces. Route controls below rotate each endpoint server-side while preventing physical port overlap.",
                CONTENT_X, y, Math.min(640, contentWidth() - 20), TEXT); y += 42;
        wrapped(g, "This page changes topology only. It does not change α, τ, fc, the current sample, or retained solver state.",
                CONTENT_X, y, Math.min(640, contentWidth() - 20), INFO);
    }

    private void renderEvidence(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "MEASUREMENT / MODEL EVIDENCE", y); y += 22;
        label(g, "Input quality", menu.inputQuality().name(), y); y += 18;
        label(g, "Output quality", menu.outputQuality().name(), y); y += 18;
        label(g, "Runtime state", menu.runtimePresent() ? "PRESENT" : "NOT READY", y); y += 18;
        label(g, "Authority", "SERVER", y); y += 18;
        label(g, "Readback", "OBSERVER-NEUTRAL", y); y += 18;
        label(g, "Profile provenance", LapisLowPassMenu.profileId(), y); y += 28;
        rule(g, y); y += 14;
        sectionTitle(g, "INTERPRETATION", y); y += 20;
        wrapped(g, "VALID means the port has usable physical evidence. NO_SIGNAL means no usable source. NOT_READY means the model has not produced retained evidence yet; it is not the same thing as a measured zero.",
                CONTENT_X, y, Math.min(650, contentWidth() - 20), TEXT); y += 52;
        wrapped(g, "The HMI is intentionally a viewer/controller of the server model. Opening, scrolling, or changing pages cannot create samples, advance the filter, or rewrite diagnostic history.",
                CONTENT_X, y, Math.min(650, contentWidth() - 20), INFO);
    }

    private void sectionTitle(GuiGraphics g, String text, int y) {
        g.drawString(font, text, CONTENT_X, y, INFO, false);
    }

    private void equation(GuiGraphics g, String text, int y) {
        int width = Math.max(260, font.width(text) + 22);
        g.fill(CONTENT_X, y - 4, CONTENT_X + width, y + 15, PANEL_3);
        g.fill(CONTENT_X, y - 4, CONTENT_X + 3, y + 15, ACCENT);
        g.drawString(font, text, CONTENT_X + 10, y, TEXT, false);
    }

    private void label(GuiGraphics g, String name, String value, int y) {
        g.drawString(font, name, CONTENT_X, y, MUTED, false);
        g.drawString(font, value, CONTENT_X + 210, y, TEXT, false);
    }

    private void metric(GuiGraphics g, String name, String value, PortQuality quality, int y) {
        int color = qualityColor(quality);
        g.fill(CONTENT_X, y, CONTENT_X + 340, y + 32, PANEL_3);
        g.fill(CONTENT_X, y, CONTENT_X + 3, y + 32, color);
        g.drawString(font, name, CONTENT_X + 10, y + 6, MUTED, false);
        g.drawString(font, value, CONTENT_X + 150, y + 6, TEXT, false);
        g.drawString(font, quality.name(), CONTENT_X + 245, y + 18, color, false);
    }

    private void wrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        int yy = y;
        for (var line : font.split(Component.literal(text), Math.max(80, width))) {
            g.drawString(font, line, x, yy, color, false);
            yy += 11;
        }
    }

    private void rule(GuiGraphics g, int y) {
        g.fill(CONTENT_X, y, Math.min(contentWidth() - 18, CONTENT_X + 700), y + 1, BORDER);
    }

    private int evidenceColor() {
        return switch (menu.evidenceState()) {
            case 1 -> GOOD;
            case 2, 0 -> MUTED;
            case 3, 4, 8 -> WARN;
            default -> BAD;
        };
    }

    private static int qualityColor(PortQuality quality) {
        return switch (quality) {
            case VALID -> GOOD;
            case NO_SIGNAL -> MUTED;
            case SATURATED, STALE, NOT_READY -> WARN;
            case FAULT, DOMAIN_MISMATCH, TOPOLOGY_ERROR -> BAD;
        };
    }

    private static int bounded(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
