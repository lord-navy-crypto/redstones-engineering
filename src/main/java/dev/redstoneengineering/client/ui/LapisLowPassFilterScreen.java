package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.LapisLowPassFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * First-generation full engineering notebook page.
 *
 * <p>This screen intentionally gives model parameters enough space instead of compressing
 * equations, controls and live evidence into the legacy compact HMI.</p>
 */
public final class LapisLowPassFilterScreen extends AbstractContainerScreen<LapisLowPassFilterMenu> {
    private static final int BG = 0xFFF2E9D8;
    private static final int PAGE = 0xFFFFF8E8;
    private static final int INK = 0xFF2C2925;
    private static final int MUTED = 0xFF6E675E;
    private static final int RULE = 0xFFB9A98F;
    private static final int ACCENT = 0xFF355C8A;
    private static final int GOOD = 0xFF2F7D4A;

    private enum Page {
        OPERATE("Operate"),
        PARAMETERS("Parameters"),
        RESPONSE("Response"),
        MODEL("Model");

        final String label;
        Page(String label) { this.label = label; }
    }

    private Page page = Page.PARAMETERS;
    private final List<Button> parameterButtons = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 84;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    public LapisLowPassFilterScreen(LapisLowPassFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 520;
        imageHeight = 300;
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

        int tabY = topPos + 38;
        int gap = imageWidth < 440 ? 5 : 7;
        int tabW = Math.max(64, (imageWidth - 48 - gap * (Page.values().length - 1)) / Page.values().length);
        int start = leftPos + 24;
        for (Page value : Page.values()) {
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> {
                page = value;
                scrollOffset = 0;
                updateVisibility();
            }).bounds(start, tabY, tabW, 22).build());
            start += tabW + gap;
        }

        int y = topPos + CONTENT_TOP + 118;
        int stepWidth = alphaStepButtonWidth();
        int stepGap = 8;
        int stepX = alphaStepStartX();
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("-0.05"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_MINUS_5))
                .bounds(stepX, y, stepWidth, 22).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("-0.01"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_MINUS_1))
                .bounds(stepX + stepWidth + stepGap, y, stepWidth, 22).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("+0.01"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_PLUS_1))
                .bounds(stepX + (stepWidth + stepGap) * 2, y, stepWidth, 22).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("+0.05"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_PLUS_5))
                .bounds(stepX + (stepWidth + stepGap) * 3, y, stepWidth, 22).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("Reset α"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_RESET))
                .bounds(leftPos + imageWidth / 2 - 50, topPos + CONTENT_TOP + 170, 100, 22).build()));

        updateVisibility();
    }

    private int alphaStepButtonWidth() {
        return Math.max(56, Math.min(68, (imageWidth - 48 - 8 * 3) / 4));
    }

    private int alphaStepStartX() {
        int total = alphaStepButtonWidth() * 4 + 8 * 3;
        return leftPos + Math.max(24, (imageWidth - total) / 2);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateVisibility() {
        boolean visible = page == Page.PARAMETERS;
        int y = topPos + CONTENT_TOP + 118 - scrollOffset;
        int stepWidth = alphaStepButtonWidth();
        int stepGap = 8;
        int stepX = alphaStepStartX();
        for (int i = 0; i < parameterButtons.size(); i++) {
            Button button = parameterButtons.get(i);
            if (i < 4) button.setX(stepX + i * (stepWidth + stepGap));
            else button.setX(leftPos + imageWidth / 2 - 50);
            button.setY(i < 4 ? y : topPos + CONTENT_TOP + 170 - scrollOffset);
            button.visible = visible
                    && button.getY() >= topPos + CONTENT_TOP
                    && button.getY() <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN - 22;
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
            case OPERATE -> 440;
            case PARAMETERS -> 500;
            case RESPONSE -> 520;
            case MODEL -> 650;
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
        String live = "SERVER MODEL";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, page.label.toUpperCase(), 24, 72, ACCENT, false);

        g.enableScissor(leftPos + 18, topPos + CONTENT_TOP, leftPos + imageWidth - 18,
                topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
        g.pose().pushPose();
        g.pose().translate(0, -scrollOffset, 0);
        switch (page) {
            case OPERATE -> renderOperate(g);
            case PARAMETERS -> renderParameters(g);
            case RESPONSE -> renderResponse(g);
            case MODEL -> renderModel(g);
        }
        g.pose().popPose();
        g.disableScissor();

        if (maxScroll() > 0) {
            String scroll = "SCROLL " + scrollOffset + " / " + maxScroll();
            g.drawString(font, scroll, imageWidth - 24 - font.width(scroll), 72, MUTED, false);
        }

        String footer = "Lapis precision domain • BACK input → FRONT output • parameter changes affect the authoritative filter";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void renderOperate(GuiGraphics g) {
        drawPair(g, "Input x[k]", menu.input() + " / 100", CONTENT_TOP + 34);
        drawPair(g, "Output y[k]", menu.output() + " / 100", CONTENT_TOP + 76);
        drawPair(g, "Evidence", menu.valid() ? "VALID" : "NO VALID INPUT", CONTENT_TOP + 118);
        drawPair(g, "Internal history", menu.historyPresent() ? "RETAINED" : "EMPTY", CONTENT_TOP + 160);
        drawPair(g, "Current α", String.format("%.2f", menu.alpha()), CONTENT_TOP + 202);
        drawPair(g, "Tracking error x−y", signed(menu.trackingError()), CONTENT_TOP + 244);
    }

    private void renderParameters(GuiGraphics g) {
        g.drawString(font, "FILTER STRENGTH / RESPONSE COEFFICIENT", 42, CONTENT_TOP + 24, MUTED, false);
        String alpha = String.format("α = %.2f", menu.alpha());
        g.drawString(font, alpha, 42, CONTENT_TOP + 48, INK, false);

        int x0 = 142;
        int x1 = Math.max(300, imageWidth - 142);
        int y = CONTENT_TOP + 82;
        g.fill(x0, y, x1, y + 8, 0xFFD8CCB7);
        int fill = (int) Math.round((menu.alphaPercent() - 1) / 98.0 * (x1 - x0 - 2));
        if (fill > 0) g.fill(x0 + 1, y + 1, x0 + 1 + fill, y + 7, ACCENT);
        g.drawString(font, "0.01", x0 - 6, y + 12, MUTED, false);
        g.drawString(font, "0.99", x1 - 18, y + 12, MUTED, false);

        g.drawString(font, fit("Fine adjustment ±0.01 • coarse adjustment ±0.05 • reset restores device preset", Math.max(280,imageWidth-96)),
                42, CONTENT_TOP + 242, MUTED, false);
    }

    private void renderResponse(GuiGraphics g) {
        drawPair(g, "Current α", String.format("%.2f", menu.alpha()), CONTENT_TOP + 34);
        drawPair(g, "Equivalent e-fold time", String.format("%.2f samples", menu.tauSamples()), CONTENT_TOP + 76);
        drawPair(g, "At 2 ticks/sample", String.format("%.2f ticks", menu.tauTicks()), CONTENT_TOP + 118);
        drawPair(g, "Equivalent cutoff", String.format("%.3f Hz", menu.equivalentCutoffHz()), CONTENT_TOP + 160);
        drawPair(g, "Current input", menu.input() + " / 100", CONTENT_TOP + 202);
        drawPair(g, "Current output", menu.output() + " / 100", CONTENT_TOP + 244);
        drawPair(g, "Tracking error", signed(menu.trackingError()), CONTENT_TOP + 286);
        g.drawString(font, fit("Smaller α = stronger smoothing / slower response. Larger α = weaker smoothing / faster response.", Math.max(300,imageWidth-96)),
                42, CONTENT_TOP + 350, MUTED, false);
    }

    private void renderModel(GuiGraphics g) {
        int w = Math.max(300, imageWidth - 96);
        g.drawString(font, "DISCRETE FIRST-ORDER LOW-PASS MODEL", 42, CONTENT_TOP + 24, MUTED, false);
        g.drawString(font, "y[k+1] = y[k] + α · (x[k] − y[k])", 42, CONTENT_TOP + 66, INK, false);
        g.drawString(font, "H(z) = α / (1 − (1−α)z⁻¹)", 42, CONTENT_TOP + 112, INK, false);
        g.drawString(font, "τsamples = −1 / ln(1−α)", 42, CONTENT_TOP + 158, INK, false);
        g.drawString(font, "fc ≈ −ln(1−α) / (2πTs),  Ts = 0.1 s", 42, CONTENT_TOP + 204, INK, false);
        g.drawString(font, fit("Input quality is authoritative world evidence. Missing input invalidates output but retained filter history is not fabricated as zero.", w),
                42, CONTENT_TOP + 276, MUTED, false);
        g.drawString(font, fit("This model page intentionally grows vertically as derivation, assumptions, sample-rate effects and validation notes are added; use scrolling instead of shrinking the content.", w),
                42, CONTENT_TOP + 380, MUTED, false);
    }

    private void drawPair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 42, y, MUTED, false);
        g.drawString(font, value, Math.min(300, imageWidth / 2), y, INK, false);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
