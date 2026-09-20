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
        RESPONSE("Response");

        final String label;
        Page(String label) { this.label = label; }
    }

    private Page page = Page.PARAMETERS;
    private final List<Button> parameterButtons = new ArrayList<>();

    public LapisLowPassFilterScreen(LapisLowPassFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 440;
        imageHeight = 252;
        titleLabelX = 18;
        titleLabelY = 12;
        inventoryLabelY = 1000;
    }

    @Override
    protected void init() {
        super.init();
        parameterButtons.clear();

        int tabY = topPos + 34;
        int tabW = 112;
        int start = leftPos + 45;
        for (Page value : Page.values()) {
            addRenderableWidget(Button.builder(Component.literal(value.label), b -> {
                page = value;
                updateVisibility();
            }).bounds(start, tabY, tabW, 20).build());
            start += tabW + 6;
        }

        int y = topPos + 164;
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("-0.05"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_MINUS_5))
                .bounds(leftPos + 62, y, 64, 20).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("-0.01"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_MINUS_1))
                .bounds(leftPos + 132, y, 64, 20).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("+0.01"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_PLUS_1))
                .bounds(leftPos + 244, y, 64, 20).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("+0.05"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_PLUS_5))
                .bounds(leftPos + 314, y, 64, 20).build()));
        parameterButtons.add(addRenderableWidget(Button.builder(Component.literal("Reset α"),
                b -> send(LapisLowPassFilterMenu.BUTTON_ALPHA_RESET))
                .bounds(leftPos + 174, topPos + 194, 92, 20).build()));

        updateVisibility();
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updateVisibility() {
        boolean visible = page == Page.PARAMETERS;
        for (Button button : parameterButtons) button.visible = visible;
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
        String live = "SERVER MODEL";
        g.drawString(font, live, imageWidth - 18 - font.width(live), 12, GOOD, false);
        g.drawString(font, page.label.toUpperCase(), 18, 69, ACCENT, false);

        switch (page) {
            case OPERATE -> renderOperate(g);
            case PARAMETERS -> renderParameters(g);
            case RESPONSE -> renderResponse(g);
        }

        String footer = "Lapis precision domain • BACK input → FRONT output • parameter changes affect the authoritative filter";
        g.drawString(font, fit(footer, imageWidth - 36), 18, imageHeight - 20, MUTED, false);
    }

    private void renderOperate(GuiGraphics g) {
        drawPair(g, "Input x[k]", menu.input() + " / 100", 94);
        drawPair(g, "Output y[k]", menu.output() + " / 100", 116);
        drawPair(g, "Evidence", menu.valid() ? "VALID" : "NO VALID INPUT", 138);
        drawPair(g, "Internal history", menu.historyPresent() ? "RETAINED" : "EMPTY", 160);
        drawPair(g, "Current α", String.format("%.2f", menu.alpha()), 182);
    }

    private void renderParameters(GuiGraphics g) {
        g.drawString(font, "FILTER STRENGTH / RESPONSE COEFFICIENT", 28, 94, MUTED, false);
        String alpha = String.format("α = %.2f", menu.alpha());
        g.drawString(font, alpha, 28, 111, INK, false);

        int x0 = 112;
        int x1 = 328;
        int y = 137;
        g.fill(x0, y, x1, y + 8, 0xFFD8CCB7);
        int fill = (int) Math.round((menu.alphaPercent() - 1) / 98.0 * (x1 - x0 - 2));
        if (fill > 0) g.fill(x0 + 1, y + 1, x0 + 1 + fill, y + 7, ACCENT);
        g.drawString(font, "0.01", x0 - 6, y + 12, MUTED, false);
        g.drawString(font, "0.99", x1 - 18, y + 12, MUTED, false);

        String equation = "y[k+1] = y[k] + α · (x[k] − y[k])";
        g.drawString(font, equation, (imageWidth - font.width(equation)) / 2, 219, INK, false);
    }

    private void renderResponse(GuiGraphics g) {
        drawPair(g, "Current α", String.format("%.2f", menu.alpha()), 94);
        drawPair(g, "Equivalent e-fold time", String.format("%.2f samples", menu.tauSamples()), 116);
        drawPair(g, "At 2 ticks/sample", String.format("%.2f ticks", menu.tauTicks()), 138);
        drawPair(g, "Current input", menu.input() + " / 100", 160);
        drawPair(g, "Current output", menu.output() + " / 100", 182);
        g.drawString(font, fit("Smaller α = stronger smoothing / slower response. Larger α = weaker smoothing / faster response.", 390),
                28, 210, MUTED, false);
    }

    private void drawPair(GuiGraphics g, String label, String value, int y) {
        g.drawString(font, label, 34, y, MUTED, false);
        g.drawString(font, value, 238, y, INK, false);
    }

    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        String s = text;
        while (s.length() > 1 && font.width(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
