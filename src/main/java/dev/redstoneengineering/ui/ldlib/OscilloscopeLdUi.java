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
                        "Δt = N_ticks / 20 s   •   f_s = 20/N_ticks Hz   •   f_N = f_s/2"),
                waveformPanel(menu),
                samplingControls(menu),
                triggerControls(menu),
                experimentControls(menu),
                networkEvidence(menu),
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
                .layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6).paddingAll(5))
                .addChildren(
                        new Label().setText("SAMPLING  N_ticks ∈ {1,2,4,8}"),
                        dt,
                        RseLdUiComponents.serverAction("Cycle Δt ▶", menu::cycleSamplePeriod),
                        new Label().bind(DataBindingBuilder.componentS2C(() ->
                                Component.literal("f_s=" + formatHz(menu.sampleRateMilliHz())
                                        + " • Nyquist=" + formatHz(menu.nyquistMilliHz()))
                        ).build()).layout(l -> l.flex(1))
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
                        new Label().setText("SAMPLING EXPERIMENT").layout(l -> l.flex(1)),
                        RseLdUiComponents.serverAction("Capture baseline", menu::captureBaseline),
                        RseLdUiComponents.serverAction("Capture candidate", menu::captureCandidate),
                        RseLdUiComponents.serverAction("Clear experiment", menu::clearExperiment)
                ),
                RseLdUiComponents.liveRow("BASELINE", "Δt", () ->
                        menu.baselinePresent() ? menu.baselineSamplePeriodTicks() + " t/sample" : "NOT CAPTURED"),
                RseLdUiComponents.liveRow("CANDIDATE", "Δt", () ->
                        menu.candidatePresent() ? menu.candidateSamplePeriodTicks() + " t/sample" : "NOT CAPTURED"),
                RseLdUiComponents.liveRow("EVIDENCE", "alias", () ->
                        "baseline=" + alias(menu.baselineAliasRisk()) + " • candidate=" + alias(menu.candidateAliasRisk()))
        );
    }

    private static UIElement networkEvidence(OscilloscopeMenu menu) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                RseLdUiComponents.liveRow("NETWORK", "nodes", () ->
                        "cable=" + menu.cableNodes() + " • probes=" + menu.probeNodes()),
                RseLdUiComponents.liveRow("NETWORK", "channels", () ->
                        "valid=" + menu.validChannels() + " • active=" + menu.activeChannels()
                                + " • duplicate=" + menu.duplicateChannels()),
                RseLdUiComponents.liveRow("EVIDENCE", "shielding", () ->
                        menu.shieldingCoverage() + "% • interference exposure=" + menu.interferenceExposure() + "%")
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

    private static String alias(int code) {
        return switch (code) { case 1 -> "RISK"; case 2 -> "MARGINAL"; case 3 -> "GOOD"; default -> "NOT_READY"; };
    }
}
