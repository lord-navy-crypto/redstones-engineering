package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.OscilloscopeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

/** LDLib2 oscilloscope HMI backed entirely by the existing authoritative menu/capture engine. */
public final class OscilloscopeLdUi {
    private OscilloscopeLdUi() {}

    public static ModularUI create(OscilloscopeMenu menu, Player player) {
        var root = new UIElement()
                .addClass("panel_bg")
                .layout(l -> l.width(620).paddingAll(8).gapAll(6));

        root.addChildren(
                RseLdUiComponents.title("TWO-CHANNEL ENGINEERING OSCILLOSCOPE"),
                RseLdUiComponents.formulaCard(() ->
                        "Δt = N_ticks / 20 s   •   f_s = 1 / Δt = 20 / N_ticks Hz   •   f_N = f_s / 2"),
                waveformPanel(menu),
                samplingControls(menu),
                triggerControls(menu),
                experimentControls(menu),
                networkEvidence(menu),
                RseLdUiComponents.liveRow("LIVE STATE", "HEALTH", menu::operationalHealthLabel),
                RseLdUiComponents.liveRow("EVIDENCE", "quality", menu::evidenceStateLabel),
                RseLdUiComponents.liveRow("I/O", "route", menu::portRouteLabel),
                RseLdUiComponents.liveRow("CONTROLS", "owner", () -> "server-validated menu intents"),
                RseLdUiComponents.authorityFooter()
        );

        return ModularUI.of(
                UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP)),
                player
        );
    }

    private static UIElement waveformPanel(OscilloscopeMenu menu) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new OscilloscopePlotElement(menu),
                RseLdUiComponents.liveRow("MEASURED", "CH-A", () -> waveform(menu, 0)),
                RseLdUiComponents.liveRow("MEASURED", "CH-B", () -> waveform(menu, 1)),
                RseLdUiComponents.liveRow("LIVE", "A", () -> formatSample(menu.current(0))),
                RseLdUiComponents.liveRow("LIVE", "B", () -> formatSample(menu.current(1))),
                RseLdUiComponents.liveRow("QUALITY", "A", () ->
                        "coverage=" + menu.coverage(0) + "% • mean-step="
                                + String.format(java.util.Locale.ROOT, "%.2f", menu.meanStep100(0) / 100.0)
                                + " • period=" + menu.periodTicks(0) + "t"),
                RseLdUiComponents.liveRow("QUALITY", "B", () ->
                        "coverage=" + menu.coverage(1) + "% • mean-step="
                                + String.format(java.util.Locale.ROOT, "%.2f", menu.meanStep100(1) / 100.0)
                                + " • period=" + menu.periodTicks(1) + "t"),
                RseLdUiComponents.liveRow("EVIDENCE", "CAPTURE", () ->
                        menu.sampleCount() + " samples • " + captureState(menu.captureState())),
                RseLdUiComponents.liveRow("DERIVED", "Cursor Δt", () ->
                        Math.abs(menu.cursorB() - menu.cursorA()) * menu.samplePeriodTicks() + " ticks")
        );
    }

    private static UIElement samplingControls(OscilloscopeMenu menu) {
        var dt = new TextField().setNumbersOnlyInt(1, 8);
        dt.layout(l -> l.width(90));
        dt.bind(DataBindingBuilder.string(
                () -> Integer.toString(menu.samplePeriodTicks()),
                value -> parseAndApply(value, v -> menu.setSamplePeriodFromUi(v))
        ).build());

        return new UIElement().addClass("panel_bg")
                .layout(l -> l.paddingAll(5).gapAll(4))
                .addChildren(
                        new Label().setText("SAMPLING MODEL"),
                        RseLdUiComponents.liveRow("ADJUSTABLE", "N_ticks",
                                () -> menu.samplePeriodTicks() + " ticks/sample"),
                        new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                                new Label().setText("DIRECT ENTRY").layout(l -> l.width(92)),
                                dt,
                                new Label().setText("{1,2,4,8} ticks/sample").layout(l -> l.width(135)),
                                RseLdUiComponents.serverAction("Cycle Δt ▶", menu::cycleSamplePeriod)
                        ),
                        RseLdUiComponents.liveRow("DERIVED", "f_s",
                                () -> formatHz(menu.sampleRateMilliHz())),
                        RseLdUiComponents.liveRow("DERIVED", "f_N",
                                () -> formatHz(menu.nyquistMilliHz())),
                        RseLdUiComponents.liveRow("LIVE SUBSTITUTION", "timebase",
                                () -> String.format(java.util.Locale.ROOT,
                                        "Δt=%.3fs → f_s=%s → f_N=%s",
                                        menu.samplePeriodTicks() / 20.0,
                                        formatHz(menu.sampleRateMilliHz()),
                                        formatHz(menu.nyquistMilliHz()))),
                        RseLdUiComponents.fixedRow("TIMEBASE TABLE", () -> "{1,2,4,8} ticks/sample",
                                "server-supported exact set"),
                        RseLdUiComponents.liveRow("EVIDENCE", "alias A",
                                () -> alias(menu.aliasRisk(0))),
                        RseLdUiComponents.liveRow("EVIDENCE", "alias B",
                                () -> alias(menu.aliasRisk(1))),
                        new Label().setText("Nyquist gives a theoretical boundary, not proof that the captured source was alias-free.")
                );
    }

    private static UIElement triggerControls(OscilloscopeMenu menu) {
        var level = boundedField(1, 15, () -> menu.triggerLevel(), menu::setTriggerLevelFromUi);
        var a = boundedField(0, 15, () -> menu.cursorA(), menu::setCursorAFromUi);
        var b = boundedField(0, 15, () -> menu.cursorB(), menu::setCursorBFromUi);

        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(4)).addChildren(
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal("TRIGGER • " + triggerMode(menu.triggerMode())
                                        + " • CH-" + (menu.triggerChannel() == 0 ? "A" : "B"))
                        ).build()).layout(l -> l.flex(1)),
                        RseLdUiComponents.serverAction("Arm / Hold", menu::armOrHold),
                        RseLdUiComponents.serverAction("Cycle mode ▶", menu::cycleTriggerMode),
                        RseLdUiComponents.serverAction("Cycle source ▶", menu::cycleTriggerChannel),
                        RseLdUiComponents.serverAction("Clear capture", menu::clearCapture)
                ),
                controlRow("T", "trigger level 1..15", level),
                controlRow("cursor A", "0..15", a),
                controlRow("cursor B", "0..15", b)
        );
    }

    private static UIElement experimentControls(OscilloscopeMenu menu) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(4)).addChildren(
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("SAMPLING EXPERIMENT • FROZEN EVIDENCE COMPARISON").layout(l -> l.flex(1)),
                        RseLdUiComponents.serverAction("Capture baseline", menu::captureBaseline),
                        RseLdUiComponents.serverAction("Capture candidate", menu::captureCandidate),
                        RseLdUiComponents.serverAction("Clear experiment", menu::clearExperiment)
                ),
                RseLdUiComponents.formulaCard(() ->
                        "N_cycle = T_obs / Δt_sample = captured periodSamples"),
                RseLdUiComponents.fixedRow("acceptance bands", () ->
                        "≤2 samples/cycle → FAIL • 3–4 → MARGINAL • ≥5 → PASS",
                        "server-owned sampling experiment verdict"),
                RseLdUiComponents.liveRow("BASELINE", "Δt", () ->
                        menu.baselinePresent() ? menu.baselineSamplePeriodTicks() + " t/sample" : "NOT CAPTURED"),
                RseLdUiComponents.liveRow("BASELINE", "N_cycle", () ->
                        menu.baselinePresent() ? Integer.toString(menu.baselinePeriodSamples()) : "NOT READY"),
                RseLdUiComponents.liveRow("CANDIDATE", "Δt", () ->
                        menu.candidatePresent() ? menu.candidateSamplePeriodTicks() + " t/sample" : "NOT CAPTURED"),
                RseLdUiComponents.liveRow("CANDIDATE", "N_cycle", () ->
                        menu.candidatePresent() ? Integer.toString(menu.candidatePeriodSamples()) : "NOT READY"),
                RseLdUiComponents.liveRow("EVIDENCE", "alias", () ->
                        "baseline=" + alias(menu.baselineAliasRisk()) + " • candidate=" + alias(menu.candidateAliasRisk())),
                RseLdUiComponents.liveRow("EVIDENCE", "status", () ->
                        samplingExperimentStatus(menu.experimentStatus())),
                new Label().setText("Nyquist/observed-frequency evidence is not proof that the original source is alias-free."),
                new Label().setText("A vanilla Redstone clock is a valid source for the sampling experiment.")
        );
    }

    private static UIElement networkEvidence(OscilloscopeMenu menu) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                RseLdUiComponents.liveRow("NETWORK", "nodes", () ->
                        "cable=" + menu.cableNodes() + " • probes=" + menu.probeNodes()),
                RseLdUiComponents.liveRow("NETWORK", "channels", () ->
                        "valid=" + menu.validChannels() + " • active=" + menu.activeChannels()
                                + " • duplicate=" + menu.duplicateChannels()),
                RseLdUiComponents.liveRow("EVIDENCE", "Interference", () ->
                        "exposure=" + menu.interferenceExposure() + "% • confidence="
                                + menu.interferenceConfidence() + "% • shielding=" + menu.shieldingCoverage() + "%"),
                RseLdUiComponents.liveRow("NEXT", "mitigation", () ->
                        menu.unshieldedExposedNodes() > 0
                                ? "shield exposed instrument segments first • exposed=" + menu.unshieldedExposedNodes()
                                : "instrument segments protected or not exposed")
        );
    }

    private static UIElement controlRow(String symbol, String range, TextField field) {
        return new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                new Label().setText("ADJUSTABLE").layout(l -> l.width(82)),
                new Label().setText(symbol).layout(l -> l.width(72)),
                field,
                new Label().setText(range).layout(l -> l.flex(1))
        );
    }

    private static TextField boundedField(int min, int max, java.util.function.IntSupplier getter,
                                          java.util.function.IntPredicate setter) {
        var field = new TextField().setNumbersOnlyInt(min, max);
        field.layout(l -> l.width(90));
        field.bind(DataBindingBuilder.string(
                () -> Integer.toString(getter.getAsInt()),
                value -> parseAndApply(value, setter)
        ).build());
        return field;
    }

    private static void parseAndApply(String value, java.util.function.IntPredicate setter) {
        try {
            setter.test(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private static String waveform(OscilloscopeMenu menu, int channel) {
        String[] bars = {"▁","▂","▃","▄","▅","▆","▇","█"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int v = menu.displaySample(channel, i);
            out.append(v < 0 ? "·" : bars[Math.max(0, Math.min(7, (int)Math.round(v / 15.0 * 7.0)))]);
        }
        return out.toString();
    }

    private static String formatSample(int value) {
        return value < 0 ? "NO DATA" : value + " / 15";
    }

    private static String formatHz(int milliHz) {
        return String.format(java.util.Locale.ROOT, "%.3f Hz", milliHz / 1000.0);
    }

    private static String triggerMode(int mode) {
        return switch (mode) { case 0 -> "FREE"; case 1 -> "RISING"; case 2 -> "FALLING"; default -> "?"; };
    }

    private static String captureState(int state) {
        return switch (state) { case 1 -> "ARMED"; case 2 -> "TRIGGERED"; default -> "HOLD"; };
    }

    private static String samplingExperimentStatus(int ordinal) {
        return switch (ordinal) {
            case 1 -> "PASS";
            case 2 -> "MARGINAL";
            case 3 -> "FAIL";
            default -> "NOT_READY";
        };
    }

    private static String alias(int code) {
        return switch (code) { case 1 -> "RISK"; case 2 -> "MARGINAL"; case 3 -> "GOOD"; default -> "NOT_READY"; };
    }
}
