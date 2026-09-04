package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.RseDiagnosticEntry;
import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseDiagnostics;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** Read-only, session-local diagnostics console opened from the player inventory. */
public final class RseDiagnosticsScreen extends Screen {
    private enum Filter {
        ALL("ALL"),
        WARNINGS("WARN+"),
        ERRORS("ERROR");

        private final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private static final int PANEL = 0xEE11161B;
    private static final int PANEL_2 = 0xEE1C242C;
    private static final int BORDER = 0xFF697681;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFF98A5B0;
    private static final int INFO = 0xFF9EC8FF;
    private static final int WARN = 0xFFF6C453;
    private static final int ERROR = 0xFFF06A6A;
    private static final int ACCENT = 0xFFE05555;

    private final Screen parent;
    private Filter filter = Filter.ALL;
    private int page;

    public RseDiagnosticsScreen(Screen parent) {
        super(Component.literal("RSE Diagnostics Console"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int bottom = height - 27;
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(12, bottom, 62, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy Report"), button -> copyReport())
                .bounds(80, bottom, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
                    RseDiagnostics.clear();
                    page = 0;
                })
                .bounds(178, bottom, 58, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Filter: " + filter.label), button -> {
                    filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
                    page = 0;
                    rebuildWidgets();
                })
                .bounds(242, bottom, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> page++)
                .bounds(width - 78, bottom, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> page = Math.max(0, page - 1))
                .bounds(width - 42, bottom, 30, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(8, 8, width - 8, height - 34, BORDER);
        graphics.fill(10, 10, width - 10, height - 36, PANEL);
        graphics.fill(16, 49, width - 16, height - 43, PANEL_2);
        graphics.fill(16, 42, width - 16, 44, ACCENT);

        graphics.drawString(font, title, 18, 17, TEXT, false);
        graphics.drawString(font, runtimeSummary(), 18, 30, MUTED, false);

        int warnCount = RseDiagnostics.count(RseDiagnosticSeverity.WARN);
        int errorCount = RseDiagnostics.count(RseDiagnosticSeverity.ERROR);
        graphics.drawString(font, "Session entries: " + RseDiagnostics.size() + "/" + RseDiagnostics.MAX_ENTRIES,
                width - 190, 17, MUTED, false);
        graphics.drawString(font, "WARN " + warnCount, width - 190, 30, WARN, false);
        graphics.drawString(font, "ERROR " + errorCount, width - 126, 30, ERROR, false);

        renderEntries(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntries(GuiGraphics graphics) {
        List<RseDiagnosticEntry> entries = filteredEntries();
        int rows = Math.max(4, (height - 105) / 12);
        int maxPage = entries.isEmpty() ? 0 : Math.max(0, (entries.size() - 1) / rows);
        page = Math.min(page, maxPage);

        int endExclusive = Math.max(0, entries.size() - page * rows);
        int start = Math.max(0, endExclusive - rows);
        int y = 57;
        if (entries.isEmpty()) {
            graphics.drawString(font, "No matching RSE diagnostics have been captured in this session.", 23, y, MUTED, false);
            return;
        }

        for (int i = start; i < endExclusive; i++) {
            RseDiagnosticEntry entry = entries.get(i);
            int color = severityColor(entry.severity());
            String source = shortSource(entry.source());
            String message = oneLine(entry.message());
            String line = "[" + RseDiagnostics.timeLabel(entry) + "] [" + entry.severity() + "] " + source + " — " + message;
            graphics.drawString(font, truncateToWidth(line, width - 52), 23, y, color, false);
            y += 12;
        }
        graphics.drawString(font, "Page " + (page + 1) + "/" + (maxPage + 1) + " • newest entries are on page 1",
                23, height - 56, MUTED, false);
    }

    private List<RseDiagnosticEntry> filteredEntries() {
        List<RseDiagnosticEntry> result = new ArrayList<>();
        for (RseDiagnosticEntry entry : RseDiagnostics.snapshot()) {
            if (filter == Filter.ERRORS && entry.severity() != RseDiagnosticSeverity.ERROR) continue;
            if (filter == Filter.WARNINGS && entry.severity() == RseDiagnosticSeverity.INFO) continue;
            result.add(entry);
        }
        return result;
    }

    private void copyReport() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(RseDiagnostics.exportReport(runtimeSummary()));
    }

    private String runtimeSummary() {
        String rseVersion = versionOf(RedstoneEngineering.MOD_ID);
        String neoForgeVersion = versionOf("neoforge");
        String minecraftVersion = SharedConstants.getCurrentVersion().getName();
        String javaVersion = System.getProperty("java.version", "unknown");
        return "RSE " + rseVersion + " • MC " + minecraftVersion + " • NeoForge " + neoForgeVersion + " • Java " + javaVersion;
    }

    private static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private int severityColor(RseDiagnosticSeverity severity) {
        return switch (severity) {
            case INFO -> INFO;
            case WARN -> WARN;
            case ERROR -> ERROR;
        };
    }

    private static String shortSource(String source) {
        int dot = source.lastIndexOf('.');
        return dot >= 0 && dot + 1 < source.length() ? source.substring(dot + 1) : source;
    }

    private static String oneLine(String value) {
        return value.replace('\n', ' ').replace('\t', ' ').trim();
    }

    private String truncateToWidth(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
