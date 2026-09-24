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
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 70;
    private static final int CONTENT_BOTTOM_MARGIN = 62;
    private int page;
    private int scrollOffset;
    private Button newerButton;
    private Button olderButton;

    public DiagnosticTabletScreen(DiagnosticTabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 520;
        imageHeight = 300;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(360, width - VIEW_MARGIN * 2);
        imageHeight = Math.max(240, height - VIEW_MARGIN * 2);
        super.init();
        scrollOffset = 0;
        addRenderableWidget(Button.builder(Component.literal("Diagnostics"), button ->
                        Minecraft.getInstance().setScreen(new RseDiagnosticsScreen(this)))
                .bounds(leftPos + 12, topPos + imageHeight - 28, 88, 20).build());
        newerButton = addRenderableWidget(Button.builder(Component.literal("Newer"), button -> {
                    page = Math.max(0, page - 1);
                    scrollOffset = 0;
                    refreshHistoryButtons();
                })
                .bounds(leftPos + imageWidth - 122, topPos + imageHeight - 28, 52, 20).build());
        olderButton = addRenderableWidget(Button.builder(Component.literal("Older"), button -> {
                    page = Math.min(Math.max(0, menu.history().size() - 1), page + 1);
                    scrollOffset = 0;
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
        graphics.fill(leftPos + 10, topPos + CONTENT_TOP - 8, leftPos + imageWidth - 10,
                topPos + imageHeight - CONTENT_BOTTOM_MARGIN, PANEL_2);
        graphics.fill(leftPos + 10, topPos + 43, leftPos + imageWidth - 10, topPos + 45, ACCENT);
        graphics.fill(leftPos + 10, topPos + imageHeight - CONTENT_BOTTOM_MARGIN,
                leftPos + imageWidth - 10, topPos + imageHeight - CONTENT_BOTTOM_MARGIN + 1, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "ENGINEERING DIAGNOSTIC TABLET", 16, 14, TEXT, false);
        graphics.drawString(font, "OBSERVER ONLY • retained snapshots", 16, 29, GOOD, false);
        List<String> history = menu.history();
        refreshHistoryButtons();

        if (history.isEmpty()) {
            graphics.enableScissor(leftPos + 12, topPos + CONTENT_TOP - 6,
                    leftPos + imageWidth - 12, topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -scrollOffset, 0);
            graphics.drawString(font, "No retained snapshots.", 22, CONTENT_TOP, MUTED, false);
            drawWrapped(graphics,
                    "Right-click an RSE or vanilla block with the tablet to capture and open its current identity and EngineeringPort topology evidence. Right-click air to reopen retained history.",
                    22, CONTENT_TOP + 22, imageWidth - 52, INFO, 12);
            graphics.pose().popPose();
            graphics.disableScissor();
            return;
        }

        String[] lines = history.get(page).split("\\n");
        String status = findLine(lines, "STATUS:");
        drawStatusBadge(graphics, status);

        graphics.enableScissor(leftPos + 12, topPos + CONTENT_TOP - 6,
                leftPos + imageWidth - 12, topPos + imageHeight - CONTENT_BOTTOM_MARGIN);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        int y = CONTENT_TOP + 12;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("STATUS:") || lines[i].startsWith("MODE:")) continue;
            y = drawWrapped(graphics, lines[i], 22, y, imageWidth - 52, lineColor(lines[i], i), 12);
            y += 2;
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        String footer = "Snapshot " + (page + 1) + " / " + history.size() + " • " + chronologyCue(history);
        graphics.drawString(font, footer, 112, imageHeight - 50, MUTED, false);
        graphics.drawString(font, comparisonCue(history), 112, imageHeight - 36, comparisonColor(history), false);
        if (maxScroll(lines) > 0) {
            String scroll = "SCROLL " + scrollOffset + " / " + maxScroll(lines);
            graphics.drawString(font, scroll, imageWidth - font.width(scroll) - 18, 50, MUTED, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<String> history = menu.history();
        if (!history.isEmpty()
                && mouseX >= leftPos + 12 && mouseX <= leftPos + imageWidth - 12
                && mouseY >= topPos + CONTENT_TOP - 6
                && mouseY <= topPos + imageHeight - CONTENT_BOTTOM_MARGIN) {
            String[] lines = history.get(Math.max(0, Math.min(page, history.size() - 1))).split("\\n");
            scrollOffset = Math.max(0, Math.min(maxScroll(lines), scrollOffset - (int)Math.round(scrollY * 24.0)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int virtualContentHeight(String[] lines) {
        int height = CONTENT_TOP + 12;
        for (String line : lines) {
            if (line.startsWith("STATUS:") || line.startsWith("MODE:")) continue;
            int wrapped = Math.max(1, font.split(Component.literal(line), imageWidth - 52).size());
            height += wrapped * 12 + 2;
        }
        return height + 24;
    }

    private int maxScroll(String[] lines) {
        int visible = Math.max(80, imageHeight - CONTENT_TOP - CONTENT_BOTTOM_MARGIN + 6);
        return Math.max(0, virtualContentHeight(lines) - CONTENT_TOP - visible);
    }

    private String chronologyCue(List<String> history) {
        if (page == 0) return "NEWEST";
        SnapshotContext newest = snapshotContext(history.get(0));
        SnapshotContext selected = snapshotContext(history.get(page));
        if (newest == null || selected == null) return "RETAINED";
        if (!newest.dimension().equals(selected.dimension())) return "CROSS-DIMENSION";
        long delta = newest.tick() - selected.tick();
        return delta >= 0 ? "Δt=" + delta + " ticks" : "RETAINED";
    }

    private String comparisonCue(List<String> history) {
        if (page == 0) return "BASELINE • newest retained evidence";
        String newest = history.get(0);
        String selected = history.get(page);
        SnapshotContext newestContext = snapshotContext(newest);
        SnapshotContext selectedContext = snapshotContext(selected);
        String newestId = lineValue(newest, "ID:");
        String selectedId = lineValue(selected, "ID:");
        String newestPos = lineValue(newest, "POS:");
        String selectedPos = lineValue(selected, "POS:");
        boolean sameTarget = newestContext != null
                && selectedContext != null
                && newestContext.dimension().equals(selectedContext.dimension())
                && !newestId.isBlank()
                && newestId.equals(selectedId)
                && !newestPos.isBlank()
                && newestPos.equals(selectedPos);
        if (!sameTarget) return "OTHER TARGET • independent evidence";

        String newestStatus = lineValue(newest, "STATUS:");
        String selectedStatus = lineValue(selected, "STATUS:");
        if (newestStatus.isBlank() || selectedStatus.isBlank()) return "SAME TARGET • status unknown";
        if (!newestStatus.equals(selectedStatus)) return "SAME TARGET • STATUS CHANGED";

        String newestTopology = lineValue(newest, "TOPOLOGY:");
        String selectedTopology = lineValue(selected, "TOPOLOGY:");
        if (!newestTopology.isBlank() && !selectedTopology.isBlank() && !newestTopology.equals(selectedTopology)) {
            return "SAME TARGET • TOPOLOGY CHANGED";
        }
        return "SAME TARGET • status unchanged";
    }

    private int comparisonColor(List<String> history) {
        String cue = comparisonCue(history);
        if (cue.contains("STATUS CHANGED") || cue.contains("TOPOLOGY CHANGED")) return WARN;
        if (cue.startsWith("SAME TARGET") || cue.startsWith("BASELINE")) return GOOD;
        return MUTED;
    }

    private static String lineValue(String snapshot, String prefix) {
        String line = findLine(snapshot.split("\\n"), prefix);
        return line.startsWith(prefix) ? line.substring(prefix.length()).trim() : "";
    }

    private static SnapshotContext snapshotContext(String snapshot) {
        String context = findLine(snapshot.split("\\n"), "CONTEXT:");
        String dimensionPrefix = "dimension=";
        String tickSeparator = " • tick=";
        int dimensionStart = context.indexOf(dimensionPrefix);
        int tickStart = context.indexOf(tickSeparator);
        if (dimensionStart < 0 || tickStart < dimensionStart) return null;
        String dimension = context.substring(dimensionStart + dimensionPrefix.length(), tickStart).trim();
        String tickText = context.substring(tickStart + tickSeparator.length()).trim();
        if (dimension.isBlank() || tickText.isBlank()) return null;
        try {
            return new SnapshotContext(dimension, Long.parseLong(tickText));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record SnapshotContext(String dimension, long tick) {}

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
        if (line.startsWith("TARGET FACE:") || line.startsWith("TOPOLOGY:") || line.startsWith("REDSTONE IN:")) return INFO;
        if (line.startsWith("CONTEXT:") || line.startsWith("ID:") || line.startsWith("POS:") || line.startsWith("SOURCE:") || line.startsWith("STATE:")) return MUTED;
        if (line.contains("q=FAULT")
                || line.contains("q=DOMAIN_MISMATCH")
                || line.contains("q=TOPOLOGY_ERROR")
                || line.contains("→ DOMAIN_MISMATCH")
                || line.contains("→ QUANTITY_MISMATCH")
                || line.contains("→ UNIT_MISMATCH")
                || line.contains("→ DIRECTION_MISMATCH")) return BAD;
        if (line.contains("q=NO_SIGNAL")
                || line.contains("q=SATURATED")
                || line.contains("q=STALE")
                || line.contains("→ OPEN")
                || line.contains("→ UNLOADED")) return WARN;
        if (line.contains("q=VALID") || line.contains("→ CONNECTED")) return GOOD;
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
