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
    private static final int MIN_WIDTH = 420;
    private static final int MAX_WIDTH = 720;
    private static final int MIN_HEIGHT = 300;
    private static final int MAX_HEIGHT = 460;
    private int page;
    private int scrollY;
    private Button newerButton;
    private Button olderButton;

    public DiagnosticTabletScreen(DiagnosticTabletMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 620;
        imageHeight = 390;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width - 20));
        imageHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height - 20));
        super.init();
        scrollY = 0;
        addRenderableWidget(Button.builder(Component.literal("Diagnostics"), button ->
                        Minecraft.getInstance().setScreen(new RseDiagnosticsScreen(this)))
                .bounds(leftPos + 12, topPos + imageHeight - 28, 96, 20).build());
        newerButton = addRenderableWidget(Button.builder(Component.literal("Newer"), button -> {
                    page = Math.max(0, page - 1);
                    scrollY = 0;
                    refreshHistoryButtons();
                })
                .bounds(leftPos + imageWidth - 132, topPos + imageHeight - 28, 56, 20).build());
        olderButton = addRenderableWidget(Button.builder(Component.literal("Older"), button -> {
                    page = Math.min(Math.max(0, menu.history().size() - 1), page + 1);
                    scrollY = 0;
                    refreshHistoryButtons();
                })
                .bounds(leftPos + imageWidth - 70, topPos + imageHeight - 28, 56, 20).build());
        refreshHistoryButtons();
    }

    private void refreshHistoryButtons() {
        int lastPage = Math.max(0, menu.history().size() - 1);
        page = Math.max(0, Math.min(page, lastPage));
        if (newerButton != null) newerButton.active = !menu.history().isEmpty() && page > 0;
        if (olderButton != null) olderButton.active = !menu.history().isEmpty() && page < lastPage;
        clampScroll();
    }

    private int contentViewportHeight() {
        return Math.max(1, imageHeight - 126);
    }

    private int maxScrollY() {
        if (menu.history().isEmpty()) return 0;
        String[] lines = menu.history().get(page).split("\\n");
        int height = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("STATUS:") || lines[i].startsWith("MODE:")) continue;
            int wrapped = Math.max(1, font.split(Component.literal(lines[i]), imageWidth - 44).size());
            height += wrapped * 11;
        }
        return Math.max(0, height - contentViewportHeight());
    }

    private void clampScroll() {
        scrollY = Math.max(0, Math.min(scrollY, maxScrollY()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXDelta, double scrollYDelta) {
        int x0 = leftPos + 10;
        int y0 = topPos + 64;
        int x1 = leftPos + imageWidth - 10;
        int y1 = topPos + imageHeight - 52;
        if (mouseX < x0 || mouseX >= x1 || mouseY < y0 || mouseY >= y1) {
            return super.mouseScrolled(mouseX, mouseY, scrollXDelta, scrollYDelta);
        }
        scrollY -= (int) Math.round(scrollYDelta * 24.0);
        clampScroll();
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, PANEL);
        graphics.fill(leftPos + 10, topPos + 42, leftPos + imageWidth - 10, topPos + imageHeight - 36, PANEL_2);
        graphics.fill(leftPos + 10, topPos + 35, leftPos + imageWidth - 10, topPos + 37, ACCENT);
        graphics.fill(leftPos + 10, topPos + imageHeight - 48, leftPos + imageWidth - 10, topPos + imageHeight - 46, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "ENGINEERING DIAGNOSTIC TABLET", 14, 13, TEXT, false);
        graphics.drawString(font, "OBSERVER ONLY • retained block + AMR evidence", 14, 25, GOOD, false);
        List<String> history = menu.history();
        refreshHistoryButtons();
        if (history.isEmpty()) {
            graphics.drawString(font, "No retained snapshots.", 18, 53, MUTED, false);
            drawWrapped(graphics,
                    "Right-click an RSE/vanilla block or AMR with the tablet to capture observer evidence. Shift+right-click a finished AMR mission to capture an explicit Baseline/Candidate commissioning trial. Right-click air reopens history.",
                    18, 70, imageWidth - 36, INFO, 11);
            return;
        }

        String[] lines = history.get(page).split("\\n");
        String status = findLine(lines, "STATUS:");
        drawStatusBadge(graphics, status);

        graphics.enableScissor(
                leftPos + 10,
                topPos + 64,
                leftPos + imageWidth - 10,
                topPos + imageHeight - 52
        );
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, -scrollY, 0.0F);
        int y = 68;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("STATUS:") || lines[i].startsWith("MODE:")) continue;
            y = drawWrapped(graphics, lines[i], 18, y, imageWidth - 44, lineColor(lines[i], i), 11);
        }
        graphics.pose().popPose();
        graphics.disableScissor();

        String footer = "Snapshot " + (page + 1) + " / " + history.size()
                + " • " + chronologyCue(history)
                + " • scroll " + scrollY + "/" + maxScrollY();
        graphics.drawString(font, footer, 120, imageHeight - 43, MUTED, false);
        String compare = comparisonCue(history);
        graphics.drawString(font, compare, 120, imageHeight - 31, comparisonColor(history), false);
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
        String newest = history.get(0);
        String explicitTrial = lineValue(newest, "TRIAL COMPARE:");
        if (page == 0 && !explicitTrial.isBlank()) return explicitTrial;
        if (page == 0) return "BASELINE • newest retained evidence";

        String selected = history.get(page);
        SnapshotContext newestContext = snapshotContext(newest);
        SnapshotContext selectedContext = snapshotContext(selected);

        String newestEntity = lineValue(newest, "ENTITY:");
        String selectedEntity = lineValue(selected, "ENTITY:");
        boolean sameEntity = newestContext != null
                && selectedContext != null
                && newestContext.dimension().equals(selectedContext.dimension())
                && !newestEntity.isBlank()
                && newestEntity.equals(selectedEntity);

        String newestId = lineValue(newest, "ID:");
        String selectedId = lineValue(selected, "ID:");
        String newestPos = lineValue(newest, "POS:");
        String selectedPos = lineValue(selected, "POS:");
        boolean sameBlock = newestContext != null
                && selectedContext != null
                && newestContext.dimension().equals(selectedContext.dimension())
                && !newestId.isBlank()
                && newestId.equals(selectedId)
                && !newestPos.isBlank()
                && newestPos.equals(selectedPos);

        if (!sameEntity && !sameBlock) return "OTHER TARGET • independent evidence";

        String newestStatus = lineValue(newest, "STATUS:");
        String selectedStatus = lineValue(selected, "STATUS:");
        if (newestStatus.isBlank() || selectedStatus.isBlank()) return "SAME TARGET • status unknown";
        if (!newestStatus.equals(selectedStatus)) return "SAME TARGET • STATUS CHANGED";

        String newestTopology = lineValue(newest, "TOPOLOGY:");
        String selectedTopology = lineValue(selected, "TOPOLOGY:");
        if (!newestTopology.isBlank() && !selectedTopology.isBlank() && !newestTopology.equals(selectedTopology)) {
            return "SAME TARGET • TOPOLOGY CHANGED";
        }
        return sameEntity ? "SAME AMR • status unchanged" : "SAME TARGET • status unchanged";
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
        String normalized = status == null ? "" : status;
        int color;
        if (normalized.contains("FAILED") || normalized.contains("FAULT") || normalized.contains("REGRESSED")) color = BAD;
        else if (normalized.contains("CHECK") || normalized.contains("SAFE_STOP")
                || normalized.contains("INCOMPARABLE") || normalized.contains("STALE")) color = WARN;
        else if (normalized.contains("COMPLETE") || normalized.contains("IMPROVED")
                || normalized.contains("NOMINAL") || normalized.contains("SAME")) color = GOOD;
        else color = INFO;
        String label = normalized.isBlank() ? "STATUS • UNKNOWN" : normalized.replace("STATUS:", "STATUS •").trim();
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
        if (line.startsWith("TARGET FACE:") || line.startsWith("TOPOLOGY:")
                || line.startsWith("REDSTONE IN:") || line.startsWith("PATH:")
                || line.startsWith("TRIAL ROLE:") || line.startsWith("TRIAL SEQUENCE:")) return INFO;
        if (line.startsWith("CONTEXT:") || line.startsWith("ID:") || line.startsWith("ENTITY:")
                || line.startsWith("POS:") || line.startsWith("SOURCE:") || line.startsWith("STATE:")
                || line.startsWith("TYPE:")) return MUTED;
        if (line.contains("REGRESSED") || line.contains("MISSION FAILED")
                || line.contains("q=FAULT") || line.contains("q=DOMAIN_MISMATCH")
                || line.contains("q=TOPOLOGY_ERROR") || line.contains("→ DOMAIN_MISMATCH")
                || line.contains("→ DIRECTION_MISMATCH")) return BAD;
        if (line.contains("SAFE_STOP") || line.contains("INCOMPARABLE")
                || line.contains("q=NO_SIGNAL") || line.contains("q=SATURATED")
                || line.contains("q=STALE") || line.contains("→ OPEN")
                || line.contains("→ UNLOADED")) return WARN;
        if (line.contains("IMPROVED") || line.contains("MISSION COMPLETE")
                || line.contains("q=VALID") || line.contains("→ CONNECTED")) return GOOD;
        if (line.startsWith("SAFETY:") || line.startsWith("LOCALIZATION:")
                || line.startsWith("TRIAL COMPARE:")) return INFO;
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
