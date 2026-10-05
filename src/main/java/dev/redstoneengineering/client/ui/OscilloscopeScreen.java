package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Large formula-first oscilloscope workspace.
 *
 * <p>The client presents synchronized capture evidence only. Sampling cadence, trigger state,
 * period estimation and alias-margin classification remain server-owned.</p>
 */
public final class OscilloscopeScreen extends AbstractContainerScreen<OscilloscopeMenu> {
    private enum Page {
        WAVEFORM("Waveform"),
        SAMPLING("Sampling"),
        EXPERIMENT("Experiment"),
        TRIGGER("Trigger"),
        NETWORK("Network"),
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
    private static final int FOOTER_HEIGHT = 72;
    private static final int SAMPLING_CONTENT_WIDTH = 820;

    private final List<Button> pageButtons = new ArrayList<>();
    private final List<Button> samplingButtons = new ArrayList<>();
    private final List<Button> experimentButtons = new ArrayList<>();
    private final List<Button> triggerButtons = new ArrayList<>();
    private Page page = Page.WAVEFORM;
    private int scrollX;
    private int scrollY;

    public OscilloscopeScreen(OscilloscopeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 660;
        imageHeight = 390;
        inventoryLabelY = 10000;
        titleLabelY = 10000;
    }

    @Override
    protected void init() {
        imageWidth = Math.max(390, Math.min(760, width - 20));
        imageHeight = Math.max(280, Math.min(470, height - 20));
        super.init();

        pageButtons.clear();
        samplingButtons.clear();
        experimentButtons.clear();
        triggerButtons.clear();

        int gap = 4;
        int tabs = Page.values().length;
        int available = imageWidth - 32 - gap * (tabs - 1);
        int tabWidth = Math.max(58, available / tabs);
        int x = leftPos + 16;
        int y = topPos + 34;
        for (Page target : Page.values()) {
            Button button = Button.builder(Component.literal(target.label), b -> setPage(target))
                    .bounds(x, y, tabWidth, 20).build();
            pageButtons.add(addRenderableWidget(button));
            x += tabWidth + gap;
        }

        int controlY = topPos + imageHeight - 58;
        samplingButtons.add(addRenderableWidget(Button.builder(
                Component.literal("Cycle timebase Δt"),
                b -> sendButton(OscilloscopeMenu.BUTTON_SAMPLE_PERIOD))
                .bounds(leftPos + 18, controlY, 150, 20).build()));

        experimentButtons.add(addRenderableWidget(Button.builder(
                Component.literal("Capture baseline"),
                b -> sendButton(OscilloscopeMenu.BUTTON_EXPERIMENT_BASELINE))
                .bounds(leftPos + 18, controlY, 128, 20).build()));
        experimentButtons.add(addRenderableWidget(Button.builder(
                Component.literal("Capture candidate"),
                b -> sendButton(OscilloscopeMenu.BUTTON_EXPERIMENT_CANDIDATE))
                .bounds(leftPos + 152, controlY, 136, 20).build()));
        experimentButtons.add(addRenderableWidget(Button.builder(
                Component.literal("Clear experiment"),
                b -> sendButton(OscilloscopeMenu.BUTTON_EXPERIMENT_CLEAR))
                .bounds(leftPos + 294, controlY, 126, 20).build()));

        int w = 82;
        int smallGap = 5;
        int x0 = leftPos + 18;
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Arm"),
                b -> sendButton(OscilloscopeMenu.BUTTON_ARM)).bounds(x0, controlY, w, 20).build()));
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Mode"),
                b -> sendButton(OscilloscopeMenu.BUTTON_TRIGGER_MODE)).bounds(x0 + (w + smallGap), controlY, w, 20).build()));
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Source"),
                b -> sendButton(OscilloscopeMenu.BUTTON_TRIGGER_CHANNEL)).bounds(x0 + (w + smallGap) * 2, controlY, w, 20).build()));
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Level +"),
                b -> sendButton(OscilloscopeMenu.BUTTON_TRIGGER_LEVEL)).bounds(x0 + (w + smallGap) * 3, controlY, w, 20).build()));

        int secondY = controlY + 24;
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Cursor A +"),
                b -> sendButton(OscilloscopeMenu.BUTTON_CURSOR_A)).bounds(x0, secondY, w + 18, 20).build()));
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Cursor B +"),
                b -> sendButton(OscilloscopeMenu.BUTTON_CURSOR_B)).bounds(x0 + w + 23, secondY, w + 18, 20).build()));
        triggerButtons.add(addRenderableWidget(Button.builder(Component.literal("Clear capture"),
                b -> sendButton(OscilloscopeMenu.BUTTON_CLEAR)).bounds(x0 + (w + 23) * 2, secondY, 126, 20).build()));

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
        for (int i = 0; i < pageButtons.size(); i++) pageButtons.get(i).active = Page.values()[i] != page;
        for (Button button : samplingButtons) button.visible = page == Page.SAMPLING;
        for (Button button : experimentButtons) button.visible = page == Page.EXPERIMENT;
        for (Button button : triggerButtons) button.visible = page == Page.TRIGGER;
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
        return page == Page.SAMPLING || page == Page.EXPERIMENT
                ? Math.max(viewportWidth(), SAMPLING_CONTENT_WIDTH) : viewportWidth();
    }

    private int contentHeight() {
        return switch (page) {
            case WAVEFORM -> 360;
            case SAMPLING -> 470;
            case EXPERIMENT -> 520;
            case TRIGGER -> 350;
            case NETWORK -> 360;
            case EVIDENCE -> 400;
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
        g.drawString(font, title.getString(), 14, 10, TEXT, false);
        String live = "● LIVE / SERVER";
        g.drawString(font, live, imageWidth - 14 - font.width(live), 10, GOOD, false);
        g.drawString(font, "TWO-CHANNEL INSTRUMENT BUS • SAMPLED CAPTURE", 14, 21, INFO, false);

        int x0 = CONTENT_X;
        int y0 = CONTENT_Y;
        int x1 = imageWidth - 18;
        int y1 = imageHeight - FOOTER_HEIGHT - 4;
        g.enableScissor(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1);
        g.pose().pushPose();
        g.pose().translate(-scrollX, -scrollY, 0.0F);
        switch (page) {
            case WAVEFORM -> renderWaveform(g);
            case SAMPLING -> renderSampling(g);
            case EXPERIMENT -> renderExperiment(g);
            case TRIGGER -> renderTrigger(g);
            case NETWORK -> renderNetwork(g);
            case EVIDENCE -> renderEvidence(g);
        }
        g.pose().popPose();
        g.disableScissor();

        String evidence = "EVIDENCE • " + menu.evidenceStateLabel();
        g.drawString(font, evidence, 18, imageHeight - 25, evidenceColor(), false);
        String scroll = "SCROLL X " + scrollX + "/" + Math.max(0, contentWidth() - viewportWidth())
                + "  Y " + scrollY + "/" + Math.max(0, contentHeight() - viewportHeight());
        g.drawString(font, scroll, imageWidth - 18 - font.width(scroll), imageHeight - 25, MUTED, false);
        g.drawString(font, "Wheel: vertical • Shift+wheel: horizontal", 18, imageHeight - 14, MUTED, false);
    }

    private void renderWaveform(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "CAPTURE", y);
        y += 20;
        label(g, "Capture state", captureState(), y); y += 18;
        label(g, "Samples", menu.sampleCount() + " / 32", y); y += 18;
        label(g, "Timebase Δt", menu.samplePeriodTicks() + " ticks", y); y += 22;

        int plotX = CONTENT_X + 20;
        int plotY = y + 4;
        int plotWidth = Math.max(280, Math.min(610, viewportWidth() - 45));
        int plotHeight = 118;
        EngineeringPlot.analogFrame(g, plotX, plotY, plotWidth, plotHeight);
        EngineeringPlot.horizontalMarker(g, menu.triggerLevel(), 0, 15, plotX + 3, plotY + 3,
                plotWidth - 6, plotHeight - 6, WARN);
        plotChannel(g, 0, plotX + 3, plotY + 3, plotWidth - 6, plotHeight - 6, INFO);
        plotChannel(g, 1, plotX + 3, plotY + 3, plotWidth - 6, plotHeight - 6, GOOD);
        EngineeringPlot.verticalMarker(g, menu.cursorA(), 16, plotX, plotY, plotWidth, plotHeight, WARN);
        EngineeringPlot.verticalMarker(g, menu.cursorB(), 16, plotX, plotY, plotWidth, plotHeight, 0xFFE879F9);
        y = plotY + plotHeight + 16;

        label(g, "CH A", value(menu.current(0)) + " • " + observedFrequency(menu.frequencyMilliHz(0)), y); y += 18;
        label(g, "CH B", value(menu.current(1)) + " • " + observedFrequency(menu.frequencyMilliHz(1)), y); y += 18;
        label(g, "Cursor Δt", cursorDeltaTicks() + " ticks", y); y += 18;
        label(g, "Cursor ΔV A / B", cursorDeltaValue(0) + " / " + cursorDeltaValue(1), y); y += 24;
        wrapped(g, relationshipDiagnosis(), CONTENT_X, y, Math.min(650, contentWidth() - 20), relationshipColor());
    }

    private void renderSampling(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "SAMPLING MODEL", y); y += 20;
        equation(g, "Δt = N_ticks / 20 s", y); y += 26;
        equation(g, "f_s = 1 / Δt = 20 / N_ticks Hz", y); y += 26;
        equation(g, "f_N = f_s / 2", y); y += 32;

        label(g, "[ADJUSTABLE] N_ticks", menu.samplePeriodTicks() + " ticks/sample", y); y += 18;
        label(g, "[DERIVED] Δt", String.format("%.3f s nominal", menu.samplePeriodTicks() / OscilloscopeMenu.nominalTicksPerSecond()), y); y += 18;
        label(g, "[DERIVED] f_s", hzFromMilli(menu.sampleRateMilliHz()), y); y += 18;
        label(g, "[DERIVED] f_N", hzFromMilli(menu.nyquistMilliHz()), y); y += 28;

        rule(g, y); y += 14;
        sectionTitle(g, "LIVE SUBSTITUTION", y); y += 20;
        equation(g, String.format("Δt = %d / %.0f = %.3f s", menu.samplePeriodTicks(),
                OscilloscopeMenu.nominalTicksPerSecond(),
                menu.samplePeriodTicks() / OscilloscopeMenu.nominalTicksPerSecond()), y); y += 26;
        equation(g, "f_s = " + hzFromMilli(menu.sampleRateMilliHz()) + " ; f_N = " + hzFromMilli(menu.nyquistMilliHz()), y); y += 32;

        samplingChannel(g, 0, "A", y); y += 74;
        samplingChannel(g, 1, "B", y); y += 80;

        rule(g, y); y += 14;
        sectionTitle(g, "TIMEBASE TABLE", y); y += 20;
        g.drawString(font, "Δt ticks", CONTENT_X, y, MUTED, false);
        g.drawString(font, "Δt nominal", CONTENT_X + 110, y, MUTED, false);
        g.drawString(font, "f_s", CONTENT_X + 250, y, MUTED, false);
        g.drawString(font, "Nyquist f_N", CONTENT_X + 360, y, MUTED, false);
        g.drawString(font, "Use", CONTENT_X + 510, y, MUTED, false);
        y += 16;
        for (int i = 0; i < OscilloscopeMenu.samplePeriodOptionCount(); i++) {
            int ticks = OscilloscopeMenu.samplePeriodOptionTicks(i);
            double fs = OscilloscopeMenu.nominalTicksPerSecond() / ticks;
            double nyquist = fs / 2.0;
            int color = ticks == menu.samplePeriodTicks() ? GOOD : TEXT;
            g.drawString(font, (ticks == menu.samplePeriodTicks() ? "▶ " : "  ") + ticks, CONTENT_X, y, color, false);
            g.drawString(font, String.format("%.3f s", ticks / OscilloscopeMenu.nominalTicksPerSecond()), CONTENT_X + 110, y, color, false);
            g.drawString(font, String.format("%.2f Hz", fs), CONTENT_X + 250, y, color, false);
            g.drawString(font, String.format("%.2f Hz", nyquist), CONTENT_X + 360, y, color, false);
            g.drawString(font, ticks <= 2 ? "FAST CAPTURE" : ticks <= 4 ? "BALANCED" : "LONG WINDOW", CONTENT_X + 510, y, color, false);
            y += 18;
        }
        y += 12;
        wrapped(g, "Nyquist gives a theoretical boundary, not proof that a captured waveform is alias-free. A high-frequency source can already have folded into a lower observed frequency. Increase sample rate and/or limit source bandwidth before sampling when the true source spectrum is uncertain.",
                CONTENT_X, y, 760, INFO);
    }

    private void samplingChannel(GuiGraphics g, int channel, String name, int y) {
        int samples = menu.periodSamples(channel);
        label(g, "CH " + name + " observed period", samples < 0 ? "NOT READY" : samples + " samples / " + menu.periodTicks(channel) + " ticks", y);
        label(g, "CH " + name + " observed frequency", observedFrequency(menu.frequencyMilliHz(channel)), y + 18);
        label(g, "CH " + name + " sampling margin", aliasLabel(menu.aliasRisk(channel)), y + 36);
        wrapped(g, aliasExplanation(menu.aliasRisk(channel)), CONTENT_X + 420, y, 360, aliasColor(menu.aliasRisk(channel)));
    }

    private void renderExperiment(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "SAMPLING EXPERIMENT", y); y += 20;
        equation(g, "N_cycle = T_obs / Δt_sample = captured periodSamples", y); y += 28;
        wrapped(g,
                "Procedure: acquire a stable periodic Redstone waveform, capture BASELINE, change only the oscilloscope timebase, reacquire the waveform, then capture CANDIDATE. A vanilla Redstone clock is a valid source.",
                CONTENT_X, y, 760, TEXT);
        y += 48;

        status(g, "Server verdict", experimentVerdictLabel(), experimentVerdictColor(), y); y += 20;
        label(g, "Compared channel", "CH " + (menu.experimentChannel() == 0 ? "A" : "B"), y); y += 18;
        label(g, "Frozen records", (menu.baselinePresent() ? "BASELINE ✓" : "BASELINE —")
                + "   " + (menu.candidatePresent() ? "CANDIDATE ✓" : "CANDIDATE —"), y); y += 28;

        sectionTitle(g, "FROZEN EVIDENCE COMPARISON", y); y += 20;
        g.drawString(font, "QUANTITY", CONTENT_X, y, MUTED, false);
        g.drawString(font, "BASELINE", CONTENT_X + 190, y, MUTED, false);
        g.drawString(font, "CANDIDATE", CONTENT_X + 390, y, MUTED, false);
        g.drawString(font, "Δ / NOTE", CONTENT_X + 590, y, MUTED, false);
        y += 16;

        experimentRow(g, "Δt", experimentTicks(menu.baselinePresent(), menu.baselineSamplePeriodTicks()),
                experimentTicks(menu.candidatePresent(), menu.candidateSamplePeriodTicks()),
                menu.baselinePresent() && menu.candidatePresent()
                        ? signed(menu.candidateSamplePeriodTicks() - menu.baselineSamplePeriodTicks()) + " ticks" : "—", y); y += 18;
        experimentRow(g, "f_s", experimentHz(menu.baselinePresent(), menu.baselineSampleRateMilliHz()),
                experimentHz(menu.candidatePresent(), menu.candidateSampleRateMilliHz()),
                "server-synchronized", y); y += 18;
        experimentRow(g, "Nyquist f_N", experimentHz(menu.baselinePresent(), menu.baselineNyquistMilliHz()),
                experimentHz(menu.candidatePresent(), menu.candidateNyquistMilliHz()),
                "f_s / 2", y); y += 18;
        experimentRow(g, "Coverage", experimentPercent(menu.baselinePresent(), menu.baselineCoverage()),
                experimentPercent(menu.candidatePresent(), menu.candidateCoverage()),
                ">=70% required", y); y += 18;
        experimentRow(g, "Samples / cycle", experimentInt(menu.baselinePresent(), menu.baselinePeriodSamples()),
                experimentInt(menu.candidatePresent(), menu.candidatePeriodSamples()),
                menu.baselinePresent() && menu.candidatePresent()
                        ? signed(menu.experimentSamplesDelta()) : "—", y); y += 18;
        experimentRow(g, "Observed f", experimentFrequency(menu.baselinePresent(), menu.baselineFrequencyMilliHz()),
                experimentFrequency(menu.candidatePresent(), menu.candidateFrequencyMilliHz()),
                menu.baselinePresent() && menu.candidatePresent()
                        ? signedMilliHz(menu.experimentFrequencyDeltaMilliHz()) : "—", y); y += 18;
        experimentRow(g, "Mean step", experimentDecimal(menu.baselinePresent(), menu.baselineMeanStep100()),
                experimentDecimal(menu.candidatePresent(), menu.candidateMeanStep100()),
                "waveform-change evidence", y); y += 18;
        experimentRow(g, "Sampling margin", experimentMargin(menu.baselinePresent(), menu.baselineAliasRisk()),
                experimentMargin(menu.candidatePresent(), menu.candidateAliasRisk()),
                "candidate sets verdict", y); y += 28;

        rule(g, y); y += 14;
        sectionTitle(g, "VERDICT RULE", y); y += 20;
        equation(g, "≤2 samples/cycle → FAIL   |   3–4 → MARGINAL   |   ≥5 → PASS", y); y += 30;
        wrapped(g,
                "This verdict is an RSE sampling-density acceptance rule, not proof that the original source is alias-free. Frequencies above Nyquist may already have folded into a lower observed f. For unknown source bandwidth, increase f_s and/or place a real low-pass stage before sampling.",
                CONTENT_X, y, 760, INFO);
        y += 58;

        sectionTitle(g, "EXPERIMENT CONTROL", y); y += 20;
        wrapped(g,
                "The buttons below freeze server evidence only. Capturing a record never advances the sampler or changes the waveform. Changing Δt still clears the live capture, so reacquire before capturing the candidate.",
                CONTENT_X, y, 760, MUTED);
    }

    private void experimentRow(GuiGraphics g, String quantity, String baseline, String candidate, String note, int y) {
        g.drawString(font, quantity, CONTENT_X, y, MUTED, false);
        g.drawString(font, baseline, CONTENT_X + 190, y, TEXT, false);
        g.drawString(font, candidate, CONTENT_X + 390, y, TEXT, false);
        g.drawString(font, note, CONTENT_X + 590, y, INFO, false);
    }

    private void renderTrigger(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "TRIGGER / CURSOR MODEL", y); y += 20;
        label(g, "Trigger mode", modeName(menu.triggerMode()), y); y += 18;
        label(g, "Trigger source", "CH " + (menu.triggerChannel() == 0 ? "A" : "B"), y); y += 18;
        label(g, "Trigger level", menu.triggerLevel() + " / 15", y); y += 18;
        label(g, "Capture state", captureState(), y); y += 26;

        equation(g, "Δt_cursor = |B - A| · Δt_sample", y); y += 28;
        label(g, "Cursor A / B", menu.cursorA() + " / " + menu.cursorB(), y); y += 18;
        label(g, "|B-A|", cursorDeltaSamples() + " samples", y); y += 18;
        label(g, "Δt_cursor", cursorDeltaTicks() + " ticks", y); y += 26;

        rule(g, y); y += 14;
        wrapped(g, "Trigger and cursor controls below are server-authoritative. Re-arming changes capture state; moving cursors never changes the sampled waveform.",
                CONTENT_X, y, Math.min(650, contentWidth() - 20), INFO);
    }

    private void renderNetwork(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "INSTRUMENT NETWORK", y); y += 20;
        status(g, "Network", networkIntegrity(), networkColor(), y); y += 20;
        status(g, "Interference", interferenceSummary(), interferenceColor(), y); y += 20;
        status(g, "Shielding", shieldingSummary(), shieldingColor(), y); y += 20;
        label(g, "Cable nodes", Integer.toString(menu.cableNodes()), y); y += 18;
        label(g, "Probe nodes", Integer.toString(menu.probeNodes()), y); y += 18;
        label(g, "CH A probes", Integer.toString(menu.probeCount(0)), y); y += 18;
        label(g, "CH B probes", Integer.toString(menu.probeCount(1)), y); y += 18;
        label(g, "Exposed cable", menu.exposedCableNodes() + " / " + menu.cableNodes(), y); y += 18;
        label(g, "Interference exposure", menu.interferenceExposure() + "%", y); y += 18;
        label(g, "Interference confidence", menu.interferenceConfidence() + "%", y); y += 24;
        wrapped(g, "Shielding changes deterministic evidence confidence. The oscilloscope does not inject random noise merely because a cable is exposed.",
                CONTENT_X, y, Math.min(680, contentWidth() - 20), MUTED);
    }

    private void renderEvidence(GuiGraphics g) {
        int y = CONTENT_Y;
        sectionTitle(g, "CAPTURE EVIDENCE", y); y += 20;
        label(g, "Authority", "SERVER", y); y += 18;
        label(g, "Topology role", menu.topologyRoleLabel(), y); y += 18;
        label(g, "Operational health", menu.operationalHealthLabel(), y); y += 18;
        label(g, "Evidence state", menu.evidenceStateLabel(), y); y += 18;
        label(g, "Capture confidence", evidenceConfidence() + "% • " + evidenceClass(), y); y += 28;

        channelEvidence(g, 0, "A", y); y += 104;
        channelEvidence(g, 1, "B", y); y += 110;

        rule(g, y); y += 14;
        sectionTitle(g, "NEXT ACTION", y); y += 20;
        wrapped(g, nextAction(), CONTENT_X, y, Math.min(680, contentWidth() - 20), evidenceColor());
        y += 44;
        wrapped(g, "Observed frequency is evidence from the retained capture, not ground truth about pre-sampling source bandwidth. This distinction is essential when aliasing is possible.",
                CONTENT_X, y, Math.min(680, contentWidth() - 20), INFO);
    }

    private void channelEvidence(GuiGraphics g, int channel, String name, int y) {
        label(g, "CH " + name + " coverage", menu.coverage(channel) + "%", y);
        label(g, "CH " + name + " min / max / p2p", value(menu.minimum(channel)) + " / " + value(menu.maximum(channel)) + " / " + value(menu.peakToPeak(channel)), y + 18);
        label(g, "CH " + name + " average", decimal100(menu.average100(channel)), y + 36);
        label(g, "CH " + name + " mean step", decimal100(menu.meanStep100(channel)), y + 54);
        label(g, "CH " + name + " period", tickValue(menu.periodTicks(channel)) + " • " + observedFrequency(menu.frequencyMilliHz(channel)), y + 72);
        status(g, "CH " + name + " alias margin", aliasLabel(menu.aliasRisk(channel)), aliasColor(menu.aliasRisk(channel)), y + 90);
    }

    private void plotChannel(GuiGraphics g, int channel, int x, int y, int width, int height, int color) {
        EngineeringPlot.analogTrace(g, 16, slot -> menu.displaySample(channel, slot), 0, 15, x, y, width, height, color);
    }

    private void equation(GuiGraphics g, String text, int y) {
        int width = Math.max(280, font.width(text) + 24);
        g.fill(CONTENT_X, y - 4, CONTENT_X + width, y + 15, PANEL_3);
        g.fill(CONTENT_X, y - 4, CONTENT_X + 3, y + 15, ACCENT);
        g.drawString(font, text, CONTENT_X + 10, y, TEXT, false);
    }

    private void sectionTitle(GuiGraphics g, String text, int y) {
        g.drawString(font, text, CONTENT_X, y, INFO, false);
    }

    private void label(GuiGraphics g, String name, String value, int y) {
        g.drawString(font, name, CONTENT_X, y, MUTED, false);
        g.drawString(font, value, CONTENT_X + 220, y, TEXT, false);
    }

    private void status(GuiGraphics g, String name, String value, int color, int y) {
        g.drawString(font, name, CONTENT_X, y, MUTED, false);
        g.drawString(font, value, CONTENT_X + 220, y, color, false);
    }

    private void wrapped(GuiGraphics g, String text, int x, int y, int width, int color) {
        int yy = y;
        for (var line : font.split(Component.literal(text), Math.max(80, width))) {
            g.drawString(font, line, x, yy, color, false);
            yy += 11;
        }
    }

    private void rule(GuiGraphics g, int y) {
        g.fill(CONTENT_X, y, Math.min(contentWidth() - 18, CONTENT_X + 760), y + 1, BORDER);
    }

    private int evidenceConfidence() {
        int capture = Math.min(menu.coverage(0), menu.coverage(1));
        if (!menu.bounded()) capture = Math.min(capture, 25);
        if (menu.duplicateChannels() > 0) capture = Math.min(capture, 35);
        if (menu.probeCount(0) != 1 || menu.probeCount(1) != 1) capture = Math.min(capture, 50);
        capture = Math.min(capture, menu.interferenceConfidence());
        return Math.max(0, Math.min(100, capture));
    }

    private String evidenceClass() {
        int confidence = evidenceConfidence();
        if (confidence >= 90) return "STRONG";
        if (confidence >= 70) return "USABLE";
        if (confidence >= 40) return "MARGINAL";
        return "INSUFFICIENT";
    }

    private int evidenceColor() {
        int confidence = evidenceConfidence();
        if (confidence >= 90) return GOOD;
        if (confidence >= 70) return INFO;
        return confidence >= 40 ? WARN : BAD;
    }

    private String relationshipDiagnosis() {
        if (evidenceConfidence() < 70) return "CHANNEL RELATIONSHIP • insufficient synchronized evidence";
        int avgDelta = Math.abs(menu.average100(0) - menu.average100(1));
        int p2pDelta = Math.abs(menu.peakToPeak(0) - menu.peakToPeak(1));
        int periodA = menu.periodTicks(0);
        int periodB = menu.periodTicks(1);
        if (periodA > 0 && periodB > 0 && Math.abs(periodA - periodB) <= menu.samplePeriodTicks()
                && avgDelta <= 100 && p2pDelta <= 1) return "CHANNEL RELATIONSHIP • closely tracking";
        if (periodA > 0 && periodB > 0 && Math.abs(periodA - periodB) > menu.samplePeriodTicks() * 2)
            return "CHANNEL RELATIONSHIP • timing mismatch";
        if (avgDelta >= 400) return "CHANNEL RELATIONSHIP • large level offset";
        if (p2pDelta >= 5) return "CHANNEL RELATIONSHIP • amplitude mismatch";
        return "CHANNEL RELATIONSHIP • distinct but comparable";
    }

    private int relationshipColor() {
        String diagnosis = relationshipDiagnosis();
        if (diagnosis.contains("insufficient") || diagnosis.contains("mismatch") || diagnosis.contains("large")) return WARN;
        return diagnosis.contains("closely") ? GOOD : INFO;
    }

    private String experimentVerdictLabel() {
        return switch (menu.experimentStatus()) {
            case 1 -> "PASS • observed candidate has ≥5 samples/cycle";
            case 2 -> "MARGINAL • observed candidate has 3–4 samples/cycle";
            case 3 -> "FAIL • observed candidate has ≤2 samples/cycle";
            default -> "NOT READY • freeze comparable baseline and candidate captures";
        };
    }

    private int experimentVerdictColor() {
        return switch (menu.experimentStatus()) {
            case 1 -> GOOD;
            case 2 -> WARN;
            case 3 -> BAD;
            default -> MUTED;
        };
    }

    private static String experimentTicks(boolean present, int ticks) {
        return present ? ticks + " ticks" : "—";
    }

    private static String experimentHz(boolean present, int milliHz) {
        return present ? hzFromMilli(milliHz) : "—";
    }

    private static String experimentFrequency(boolean present, int milliHz) {
        return !present || milliHz < 0 ? "—" : hzFromMilli(milliHz);
    }

    private static String experimentPercent(boolean present, int percent) {
        return present ? percent + "%" : "—";
    }

    private static String experimentInt(boolean present, int value) {
        return !present || value < 0 ? "—" : Integer.toString(value);
    }

    private static String experimentDecimal(boolean present, int value) {
        return !present || value < 0 ? "—" : decimal100(value);
    }

    private String experimentMargin(boolean present, int code) {
        return present ? aliasLabel(code) : "—";
    }

    private static String signedMilliHz(int value) {
        String prefix = value > 0 ? "+" : "";
        return prefix + String.format("%.3f Hz", value / 1000.0);
    }

    private String aliasLabel(int code) {
        return switch (code) {
            case 1 -> "ALIAS RISK • ≤2 samples/cycle";
            case 2 -> "MARGINAL • 3-4 samples/cycle";
            case 3 -> "OBSERVED MARGIN • ≥5 samples/cycle";
            default -> "NOT READY";
        };
    }

    private String aliasExplanation(int code) {
        return switch (code) {
            case 1 -> "Captured cycle is at/below the Nyquist sampling density. Increase f_s before trusting frequency or shape.";
            case 2 -> "Above the theoretical boundary but with limited waveform detail. Faster sampling is preferred.";
            case 3 -> "Observed cycle has practical sample density, but hidden out-of-band content can still alias.";
            default -> "No stable captured period is available yet; alias margin cannot be assessed.";
        };
    }

    private int aliasColor(int code) {
        return switch (code) {
            case 1 -> BAD;
            case 2 -> WARN;
            case 3 -> GOOD;
            default -> MUTED;
        };
    }

    private String shieldingSummary() {
        if (!menu.bounded()) return "UNKNOWN • scan truncated";
        if (menu.cableNodes() == 0) return "DIRECT / NO CABLE";
        if (menu.unshieldedCableNodes() == 0) return "FULL • 100%";
        if (menu.shieldedCableNodes() == 0) return "NONE • 0%";
        return "MIXED • " + menu.shieldingCoverage() + "%";
    }

    private String interferenceSummary() {
        if (!menu.bounded()) return "UNKNOWN • scan truncated";
        if (menu.cableNodes() == 0) return "DIRECT • no cable exposure";
        if (menu.exposedCableNodes() == 0) return "CLEAR • confidence 100%";
        if (menu.unshieldedExposedNodes() == 0) return "EXPOSED / SHIELDED • confidence " + menu.interferenceConfidence() + "%";
        if (menu.shieldedExposedNodes() == 0) return "EXPOSED / UNSHIELDED • confidence " + menu.interferenceConfidence() + "%";
        return "EXPOSED / MIXED • confidence " + menu.interferenceConfidence() + "%";
    }

    private int shieldingColor() {
        if (!menu.bounded()) return WARN;
        if (menu.cableNodes() == 0 || menu.shieldingCoverage() >= 90) return GOOD;
        if (menu.shieldingCoverage() >= 60) return INFO;
        return WARN;
    }

    private int interferenceColor() {
        if (!menu.bounded()) return WARN;
        if (menu.interferenceConfidence() >= 90) return GOOD;
        if (menu.interferenceConfidence() >= 70) return INFO;
        return WARN;
    }

    private String networkIntegrity() {
        if (!menu.bounded()) return "TRUNCATED";
        if (menu.duplicateChannels() > 0) return "AMBIGUOUS • duplicate channel";
        if (menu.probeNodes() == 0) return "NO PROBES";
        return "OK • bounded scan";
    }

    private int networkColor() {
        if (!menu.bounded() || menu.duplicateChannels() > 0) return WARN;
        return menu.probeNodes() == 0 ? MUTED : GOOD;
    }

    private String nextAction() {
        if (!menu.bounded()) return "Reduce or segment the instrument network before trusting capture timing.";
        if (menu.duplicateChannels() > 0) return "Resolve duplicate probe channel ownership before waveform comparison.";
        if (menu.probeCount(0) != 1 || menu.probeCount(1) != 1) return "Connect exactly one probe to each compared channel.";
        if (menu.aliasRisk(0) == 1 || menu.aliasRisk(1) == 1) return "Increase sample rate before trusting observed frequency; then compare with a bandwidth-limited/pre-filtered source if available.";
        if (menu.aliasRisk(0) == 2 || menu.aliasRisk(1) == 2) return "Use a faster timebase for more samples per cycle before interpreting waveform shape.";
        if (menu.unshieldedExposedNodes() > 0) return "NEXT • shield exposed instrument segments or separate them from energized routing.";
        if (evidenceConfidence() < 70) return "Acquire a longer valid capture before interpreting waveform differences.";
        return "Evidence is coherent; use cursors to quantify time and amplitude differences.";
    }

    private int cursorDeltaSamples() { return Math.abs(menu.cursorB() - menu.cursorA()); }
    private int cursorDeltaTicks() { return cursorDeltaSamples() * menu.samplePeriodTicks(); }

    private String cursorDeltaValue(int channel) {
        int a = menu.displaySample(channel, menu.cursorA());
        int b = menu.displaySample(channel, menu.cursorB());
        return a < 0 || b < 0 ? "N/A" : signed(b - a);
    }

    private String captureState() {
        return switch (menu.captureState()) {
            case 1 -> "ARMED";
            case 2 -> "TRIGGERED";
            default -> "HOLD";
        };
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "FREE";
            case 1 -> "RISING";
            case 2 -> "FALLING";
            default -> "?";
        };
    }

    private static String hzFromMilli(int milliHz) {
        if (milliHz < 0) return "N/A";
        return String.format("%.3f Hz", milliHz / 1000.0);
    }

    private static String observedFrequency(int milliHz) {
        return milliHz < 0 ? "frequency NOT READY" : "f_obs≈" + hzFromMilli(milliHz);
    }

    private static String value(int value) { return value < 0 ? "N/A" : Integer.toString(value); }
    private static String tickValue(int value) { return value < 0 ? "N/A" : value + "t"; }

    private static String decimal100(int value) {
        if (value < 0) return "N/A";
        return (value / 100) + "." + String.format("%02d", value % 100);
    }

    private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
}
