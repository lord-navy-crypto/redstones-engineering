package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.DiagnosticTabletMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Read-only tablet surface for bounded retained block snapshots plus the existing session console. */
public final class DiagnosticTabletScreen extends AbstractContainerScreen<DiagnosticTabletMenu> {
    private static final int PANEL = 0xF0141A20;
    private static final int PANEL_2 = 0xF01D2730;
    private static final int BORDER = 0xFF657481;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF99A7B4;
    private static final int INFO = 0xFF8EC5FF;
    private static final int GOOD = 0xFF70D49B;
    private static final int WARN = 0xFFFFB45C;
    private static final int BAD = 0xFFFF7373;
    private static final int ACCENT = 0xFFE25757;
    private int page;
    private Button newerButton;
    private Button olderButton;

    public DiagnosticTabletScreen(DiagnosticTabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 316;
        imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Diagnostics"), button ->
                        Minecraft.getInstance().setScreen(new RseDiagnosticsScreen(this)))
                .bounds(leftPos + 12, topPos + imageHeight - 28, 88, 20).build());
        newerButton = addRenderableWidget(Button.builder(Component.literal("Newer"), button -> {
                    page = Math.max(0, page - 1);
                    refreshHistoryButtons();
                })
                .bounds(leftPos + imageWidth - 122, topPos + imageHeight - 28, 52, 20).build());
        olderButton = addRenderableWidget(Button.builder(Component.literal("Older"), button -> {
                    page = Math.min(Math.max(0, menu.history().size() - 1), page + 1);
                    refreshHistoryButtons();
                })
                .bounds(leftPos + imageWidth - 64, topPos + imageHeight - 28, 52, 20).build());
        refreshHistoryButtons();
    }

    private void refreshHistoryButtons() {
        int lastPage = Math.max(0, menu.history().size() - 1);
        page = Math.max(0, Math.min(page, lastPage));
        if (newerButton != null) newerButton.active = !menu.history().isEmpty() && page > 0;
        if (olderButton != null) olderButton.active = !menu.history().isEmpty() && page < lastPage;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, PANEL);
        graphics.fill(leftPos + 10, topPos + 42, leftPos + imageWidth - 10, topPos + imageHeight - 36, PANEL_2);
        graphics.fill(leftPos + 10, topPos + 35, leftPos + imageWidth - 10, topPos + 37, ACCENT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "ENGINEERING DIAGNOSTIC TABLET", 14, 13, TEXT, false);
        graphics.drawString(font, "OBSERVER ONLY • retained snapshots", 14, 25, GOOD, false);
        List<String> history = menu.history();
        refreshHistoryButtons();
        if (history.isEmpty()) {
            graphics.drawString(font, "No retained snapshots.", 18, 53, MUTED, false);
            drawWrapped(graphics, "Right-click an RSE or vanilla block with the tablet to capture its current identity and EngineeringPort topology evidence. Shift-right-click air to return here.", 18, 70, imageWidth - 36, INFO, 11);
            return;
        }

        String[] lines = history.get(page).split("\\n");
        String status = findLine(lines, "STATUS:");
        drawStatusBadge(graphics, status);

        int y = 68;
        for (int i = 0; i < lines.length && y < imageHeight - 52; i++) {
            if (lines[i].startsWith("STATUS:") || lines[i].startsWith("MODE:")) continue;
            y = drawWrapped(graphics, lines[i], 18, y, imageWidth - 36, lineColor(lines[i], i), 10);
        }
        String footer = "Snapshot " + (page + 1) + " / " + history.size() + " • newest = 1";
        graphics.drawString(font, footer, 108, imageHeight - 40, MUTED, false);
    }

    private void drawStatusBadge(GuiGraphics graphics, String status) {
        boolean issue = status.contains("CHECK TOPOLOGY");
        int color = issue ? BAD : GOOD;
        String label = status.isBlank() ? "STATUS • UNKNOWN" : status.replace("STATUS:", "STATUS •").trim();
        int width = Math.min(imageWidth - 36, font.width(label) + 12);
        graphics.fill(16, 47, 16 + width, 61, 0xAA000000 | (color & 0x00FFFFFF));
        graphics.drawString(font, label, 22, 50, PANEL, false);
    }

    private static String findLine(String[] lines, String prefix) {
        for (String line : lines) if (line.startsWith(prefix)) return line;
        return "";
    }

    private int lineColor(String line, int index) {
        if (index == 0) return INFO;
        if (line.startsWith("TOPOLOGY:") || line.startsWith("REDSTONE IN:")) return INFO;
        if (line.startsWith("CONTEXT:") || line.startsWith("ID:") || line.startsWith("POS:") || line.startsWith("SOURCE:") || line.startsWith("STATE:")) return MUTED;
        if (line.contains(" q=DEGRADED") || line.contains(" q=MISSING") || line.contains("issue")) return WARN;
        return TEXT;
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color, int step) {
        for (FormattedCharSequence line : font.split(Component.literal(text), width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += step;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
