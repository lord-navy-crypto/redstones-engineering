package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.RseDiagnosticSeverity;
import dev.redstoneengineering.diagnostics.RseDiagnostics;
import dev.redstoneengineering.diagnostics.RseLiveDiagnosticEvent;
import dev.redstoneengineering.diagnostics.RseLiveDiagnostics;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only live diagnostics hub opened from the inventory red-cross entry point. */
public final class RseDiagnosticsScreen extends Screen {
    private enum View {
        OVERVIEW("OVERVIEW"),
        FEEDBACK("FEEDBACK"),
        RUN_LOG("RUN LOG"),
        LIVE_EVENTS("LIVE EVENTS"),
        SYSTEMS("SYSTEMS"),
        MEGA_FACTORY("MEGA FACTORY"),
        EXPORT("EXPORT");

        private final String label;

        View(String label) {
            this.label = label;
        }
    }

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
    private static final int GOOD = 0xFF68D391;
    private static final int WARN = 0xFFF6C453;
    private static final int ERROR = 0xFFF06A6A;
    private static final int ACCENT = 0xFFE05555;

    private final Screen parent;
    private Filter filter = Filter.ALL;
    private View view = View.OVERVIEW;
    private int page;
    private String feedback = "";
    private int feedbackTicks;
    private long lastObservedEventSequence = -1L;

    public RseDiagnosticsScreen(Screen parent) {
        super(Component.literal("RSE Live Diagnostics Hub"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int bottom = height - 27;
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(12, bottom, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy All"), button -> copyReport())
                .bounds(62, bottom, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy Run"), button -> copyRun())
                .bounds(136, bottom, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"), button -> exportLatest())
                .bounds(210, bottom, 64, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
                    RseDiagnostics.clear();
                    RseLiveDiagnostics.clear();
                    page = 0;
                    showFeedback("SESSION BUFFERS CLEARED");
                })
                .bounds(280, bottom, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Filter: " + filter.label), button -> {
                    filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
                    page = 0;
                    rebuildWidgets();
                })
                .bounds(330, bottom, 78, 20).build());
        int navStart = width - 78;
        int viewWidth = Math.max(88, navStart - 420);
        addRenderableWidget(Button.builder(Component.literal("View: " + view.label), button -> {
                    view = View.values()[(view.ordinal() + 1) % View.values().length];
                    page = 0;
                    rebuildWidgets();
                })
                .bounds(414, bottom, viewWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), button -> page++)
                .bounds(width - 78, bottom, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> page = Math.max(0, page - 1))
                .bounds(width - 42, bottom, 30, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (feedbackTicks > 0) feedbackTicks--;
        if (feedbackTicks == 0) feedback = "";

        // Snapshot-only refresh: no world scan, no chunk loading, no simulation authority.
        List<RseLiveDiagnosticEvent> events = RseLiveDiagnostics.snapshotEvents();
        if (!events.isEmpty()) {
            long newest = events.get(events.size() - 1).sequence();
            if (newest != lastObservedEventSequence) {
                lastObservedEventSequence = newest;
                if (view == View.LIVE_EVENTS && page == 0) page = 0;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(8, 8, width - 8, height - 34, BORDER);
        graphics.fill(10, 10, width - 10, height - 36, PANEL);
        graphics.fill(16, 55, width - 16, height - 43, PANEL_2);
        graphics.fill(16, 47, width - 16, 49, ACCENT);

        graphics.drawString(font, title, 18, 16, TEXT, false);
        graphics.drawString(font, runtimeSummary(), 18, 31, MUTED, false);
        graphics.drawString(font, "OBSERVER ONLY • LIVE + BOUNDED", 18, 58, GOOD, false);
        graphics.drawString(font, view.label, 220, 58, INFO, false);
        if (!feedback.isEmpty()) {
            graphics.drawString(font, feedback, width - font.width(feedback) - 22, 58, GOOD, false);
        }

        switch (view) {
            case OVERVIEW -> renderOverview(graphics);
            case FEEDBACK -> renderFeedback(graphics);
            case RUN_LOG -> renderRunLog(graphics);
            case LIVE_EVENTS -> renderLiveEvents(graphics);
            case SYSTEMS -> renderSystems(graphics);
            case MEGA_FACTORY -> renderMegaFactory(graphics);
            case EXPORT -> renderExport(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverview(GuiGraphics graphics) {
        long tick = currentTick();
        RseLiveDiagnostics.Summary summary = RseLiveDiagnostics.summary(tick);
        int y = 78;
        graphics.drawString(font, "Active monitored RSE devices: " + summary.activeDevices(), 24, y, TEXT, false);
        y += 14;
        graphics.drawString(font, "WARN events: " + summary.warnEvents(), 24, y, WARN, false);
        graphics.drawString(font, "ERROR events: " + summary.errorEvents(), 170, y, ERROR, false);
        y += 18;
        graphics.drawString(font, "QUALITY", 24, y, INFO, false);
        y += 13;
        for (Map.Entry<String, Integer> entry : summary.qualityCounts().entrySet()) {
            int color = qualityColor(entry.getKey());
            graphics.drawString(font, entry.getKey() + " = " + entry.getValue(), 30, y, color, false);
            y += 12;
            if (y > height - 90) break;
        }
        RseLiveDiagnosticEvent abnormal = summary.latestAbnormal();
        if (abnormal != null && y < height - 82) {
            y += 8;
            graphics.drawString(font, "LATEST ROOT/ABNORMAL EVENT", 24, y, WARN, false);
            y += 13;
            graphics.drawString(font, truncateToWidth(eventLine(abnormal), width - 52), 30, y,
                    severityColor(abnormal.severity()), false);
        }
    }

    private void renderFeedback(GuiGraphics graphics) {
        RseLiveDiagnostics.ValidationRunSnapshot run = RseLiveDiagnostics.latestValidationRun();
        int y = 78;
        if (run == null) {
            graphics.drawString(font, "No integrated demo feedback published yet.", 24, y, MUTED, false);
            graphics.drawString(font, "Run /rsevalidation demo place, then open this red-cross panel.", 24, y + 16, INFO, false);
            return;
        }

        int overallColor = "FAIL".equalsIgnoreCase(run.overall()) ? ERROR
                : "PASS".equalsIgnoreCase(run.overall()) ? GOOD : WARN;
        graphics.drawString(font, "INTEGRATED DEMO FEEDBACK TABLE", 24, y, INFO, false);
        y += 14;
        graphics.drawString(font, "RUN " + run.runId() + " • OVERALL " + run.overall(), 24, y, overallColor, false);
        y += 13;
        graphics.drawString(font, "LAST COMMAND: " + run.command(), 24, y, TEXT, false);
        y += 16;

        List<String> lines = run.feedbackLines();
        int rowsPerPage = Math.max(3, (height - 126) / 24);
        int maxPage = lines.isEmpty() ? 0 : Math.max(0, (lines.size() - 1) / rowsPerPage);
        page = Math.min(page, maxPage);
        int start = page * rowsPerPage;
        int end = Math.min(lines.size(), start + rowsPerPage);
        for (int i = start; i < end && y < height - 62; i++) {
            String line = lines.get(i);
            int color = line.contains(" FAIL ") || line.startsWith("OVERALL FAIL") ? ERROR
                    : line.contains(" PASS ") || line.startsWith("OVERALL PASS") ? GOOD
                    : line.contains(" WAIT ") || line.startsWith("OVERALL WAIT") ? WARN : TEXT;
            y = drawWrappedCrisp(graphics, line, 24, y, width - 52, color, 11);
            y += 2;
        }
        graphics.drawString(font, "Page " + (page + 1) + "/" + (maxPage + 1)
                        + " • Copy Run copies this table + run log exactly",
                24, height - 56, MUTED, false);
    }

    private void renderRunLog(GuiGraphics graphics) {
        RseLiveDiagnostics.ValidationRunSnapshot run = RseLiveDiagnostics.latestValidationRun();
        int y = 78;
        if (run == null) {
            graphics.drawString(font, "No integrated demo run log published yet.", 24, y, MUTED, false);
            return;
        }
        graphics.drawString(font, "RUN LOG • CRISP 1:1 TEXT • WRAPPED, NEVER SCALED", 24, y, INFO, false);
        y += 15;
        graphics.drawString(font, "RUN " + run.runId() + " • tick=" + run.gameTick(), 24, y, TEXT, false);
        y += 16;

        List<String> lines = run.logLines();
        int entriesPerPage = Math.max(3, (height - 130) / 26);
        int maxPage = lines.isEmpty() ? 0 : Math.max(0, (lines.size() - 1) / entriesPerPage);
        page = Math.min(page, maxPage);
        int endExclusive = Math.max(0, lines.size() - page * entriesPerPage);
        int start = Math.max(0, endExclusive - entriesPerPage);
        if (lines.isEmpty()) {
            graphics.drawString(font, "Run log is empty; stage transitions will appear here.", 24, y, MUTED, false);
        } else {
            for (int i = start; i < endExclusive && y < height - 62; i++) {
                String line = lines.get(i);
                int color = line.contains(" FAIL ") ? ERROR : line.contains(" PASS ") ? GOOD
                        : line.contains("COMMAND") || line.contains("RUN START") ? INFO : TEXT;
                y = drawWrappedCrisp(graphics, line, 24, y, width - 52, color, 11);
                y += 3;
            }
        }
        graphics.drawString(font, "Page " + (page + 1) + "/" + (maxPage + 1)
                        + " • newest on page 1 • integer-pixel rendering",
                24, height - 56, MUTED, false);
    }

    private void renderLiveEvents(GuiGraphics graphics) {
        List<RseLiveDiagnosticEvent> entries = filteredLiveEvents();
        int rows = Math.max(4, (height - 122) / 12);
        int maxPage = entries.isEmpty() ? 0 : Math.max(0, (entries.size() - 1) / rows);
        page = Math.min(page, maxPage);
        int endExclusive = Math.max(0, entries.size() - page * rows);
        int start = Math.max(0, endExclusive - rows);
        int y = 78;
        if (entries.isEmpty()) {
            graphics.drawString(font, "No matching structured RSE events in this session.", 24, y, MUTED, false);
            return;
        }
        for (int i = start; i < endExclusive; i++) {
            RseLiveDiagnosticEvent event = entries.get(i);
            graphics.drawString(font, truncateToWidth(eventLine(event), width - 52), 24, y,
                    severityColor(event.severity()), false);
            y += 12;
        }
        graphics.drawString(font, "Page " + (page + 1) + "/" + (maxPage + 1) + " • newest on page 1",
                24, height - 56, MUTED, false);
    }

    private void renderSystems(GuiGraphics graphics) {
        RseLiveDiagnostics.Summary summary = RseLiveDiagnostics.summary(currentTick());
        int y = 78;
        graphics.drawString(font, "SUBSYSTEM HEALTH", 24, y, INFO, false);
        y += 16;
        for (RseLiveDiagnostics.Domain domain : RseLiveDiagnostics.Domain.values()) {
            int active = summary.domainCounts().getOrDefault(domain, 0);
            int unhealthy = summary.unhealthyByDomain().getOrDefault(domain, 0);
            int color = unhealthy > 0 ? WARN : active > 0 ? GOOD : MUTED;
            graphics.drawString(font, domain + "  active=" + active + "  unhealthy=" + unhealthy,
                    30, y, color, false);
            y += 13;
            if (y > height - 76) break;
        }
    }

    private void renderMegaFactory(GuiGraphics graphics) {
        RseLiveDiagnostics.MegaSnapshot mega = RseLiveDiagnostics.latestMegaSnapshot();
        int y = 78;
        if (mega == null) {
            graphics.drawString(font, "No Mega Factory telemetry published yet.", 24, y, MUTED, false);
            graphics.drawString(font, "Run /rsevalidation mega diagnose to publish a live snapshot.", 24, y + 16, INFO, false);
            return;
        }
        int masterColor = "FAIL".equalsIgnoreCase(mega.master()) ? ERROR
                : "WAIT".equalsIgnoreCase(mega.master()) ? WARN : GOOD;
        graphics.drawString(font, "RUN " + mega.runNumber() + " • PHASE " + mega.phase(), 24, y, TEXT, false);
        y += 15;
        graphics.drawString(font, "MASTER " + mega.master() + " • abnormalStations=" + mega.abnormalStations(),
                24, y, masterColor, false);
        y += 16;
        graphics.drawString(font, "ROOT BLOCKERS: " + mega.rootBlockers(), 24, y,
                mega.rootBlockers().isEmpty() ? GOOD : ERROR, false);
        y += 14;
        graphics.drawString(font, "CASCADE: " + mega.cascades(), 24, y,
                mega.cascades().isEmpty() ? GOOD : WARN, false);
        y += 18;
        for (Map.Entry<String, String> entry : mega.cellSummary().entrySet()) {
            String line = "CELL " + entry.getKey() + " " + entry.getValue();
            int color = line.contains("FAIL") ? ERROR : line.contains("WAIT") ? WARN : GOOD;
            graphics.drawString(font, truncateToWidth(line, width - 52), 30, y, color, false);
            y += 12;
            if (y > height - 76) break;
        }
    }

    private void renderExport(GuiGraphics graphics) {
        int y = 78;
        graphics.drawString(font, "EXPORT", 24, y, INFO, false);
        y += 16;
        graphics.drawString(font, "Copy All: copies feedback table + run log + live diagnostics.", 30, y, TEXT, false);
        y += 14;
        graphics.drawString(font, "Copy Run: copies only the latest demo feedback table + run log.", 30, y, TEXT, false);
        y += 14;
        graphics.drawString(font, "Export: writes run/rse-diagnostics/rse-live-latest.txt", 30, y, TEXT, false);
        y += 14;
        graphics.drawString(font, "Mega diagnose separately writes mega-latest.txt + mega-history.log", 30, y, TEXT, false);
        y += 20;
        graphics.drawString(font, "Legacy log buffer: " + RseDiagnostics.size() + "/" + RseDiagnostics.MAX_ENTRIES
                + " | WARN " + RseDiagnostics.count(RseDiagnosticSeverity.WARN)
                + " | ERROR " + RseDiagnostics.count(RseDiagnosticSeverity.ERROR), 30, y, MUTED, false);
    }

    private List<RseLiveDiagnosticEvent> filteredLiveEvents() {
        ArrayList<RseLiveDiagnosticEvent> result = new ArrayList<>();
        for (RseLiveDiagnosticEvent event : RseLiveDiagnostics.snapshotEvents()) {
            if (filter == Filter.ERRORS && event.severity() != RseDiagnosticSeverity.ERROR) continue;
            if (filter == Filter.WARNINGS && event.severity() == RseDiagnosticSeverity.INFO) continue;
            result.add(event);
        }
        return result;
    }

    private void copyReport() {
        Minecraft minecraft = Minecraft.getInstance();
        String report = RseLiveDiagnostics.exportReport(runtimeSummary(), currentTick());
        // Preserve the legacy console contract by including retained text diagnostics as an appendix.
        report += "\n\n" + RseDiagnostics.exportReport(runtimeSummary());
        minecraft.keyboardHandler.setClipboard(report);
        showFeedback("REPORT COPIED");
    }

    private void copyRun() {
        RseLiveDiagnostics.ValidationRunSnapshot run = RseLiveDiagnostics.latestValidationRun();
        if (run == null) {
            showFeedback("NO DEMO RUN TO COPY");
            return;
        }
        StringBuilder out = new StringBuilder(16_000);
        out.append("RSE INTEGRATED DEMO FEEDBACK\n");
        out.append("run=").append(run.runId())
                .append(" command=").append(run.command())
                .append(" overall=").append(run.overall())
                .append(" tick=").append(run.gameTick()).append('\n');
        out.append("\n===== FEEDBACK TABLE =====\n");
        for (String line : run.feedbackLines()) out.append(line).append('\n');
        out.append("\n===== RUN LOG =====\n");
        for (String line : run.logLines()) out.append(line).append('\n');
        Minecraft.getInstance().keyboardHandler.setClipboard(out.toString());
        showFeedback("RUN FEEDBACK COPIED");
    }

    private void exportLatest() {
        Path path = RseLiveDiagnostics.exportLatest(runtimeSummary(), currentTick());
        showFeedback(path == null ? "EXPORT FAILED — SEE LOG" : "EXPORTED " + path);
    }

    private long currentTick() {
        return minecraft == null || minecraft.level == null ? -1L : minecraft.level.getGameTime();
    }

    private void showFeedback(String message) {
        feedback = message;
        feedbackTicks = 80;
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

    private int qualityColor(String quality) {
        if (quality == null) return MUTED;
        return switch (quality) {
            case "VALID" -> GOOD;
            case "NO_SIGNAL", "STALE", "SATURATED" -> WARN;
            case "FAULT", "DOMAIN_MISMATCH", "TOPOLOGY_ERROR" -> ERROR;
            default -> MUTED;
        };
    }

    private String eventLine(RseLiveDiagnosticEvent event) {
        return "[" + event.severity() + "] " + event.domain() + "/" + event.source()
                + " " + event.eventType() + " " + event.reasonCode() + " — " + oneLine(event.message());
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\t', ' ').trim();
    }

    /**
     * Logs and feedback deliberately render at the native GUI text scale. No pose-stack scaling,
     * fractional coordinates or texture resampling is used; long lines wrap instead of shrinking.
     */
    private int drawWrappedCrisp(
            GuiGraphics graphics, String value, int x, int y, int maxWidth, int color, int lineStep
    ) {
        for (FormattedCharSequence line : font.split(Component.literal(value == null ? "" : value), maxWidth)) {
            graphics.drawString(font, line, x, y, color, false);
            y += lineStep;
        }
        return y;
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
