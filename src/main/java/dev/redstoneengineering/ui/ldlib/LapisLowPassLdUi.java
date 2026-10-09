package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.LapisLowPassMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

import java.util.Locale;

/** Formula-first LDLib2 HMI for the sampled Lapis low-pass filter. */
public final class LapisLowPassLdUi {
    private LapisLowPassLdUi() {}

    public static ModularUI create(LapisLowPassMenu m, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("LAPIS PRECISION • FIRST-ORDER SAMPLED LOW-PASS"),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        modelPanel(m),
                                        livePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        configurePanel(m),
                                        routePanel(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        evidencePanel(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }

    private static UIElement modelPanel(LapisLowPassMenu m) {
        double alpha = LapisLowPassMenu.alphaForIndex(m.alphaIndex());
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("GOVERNING EQUATION"),
                RseLdUiComponents.formulaCard(() -> "y[n] = y[n-1] + α · (x[n] - y[n-1])"),
                RseLdUiComponents.liveRow("MEASURED", "x[n]", () -> m.inputQuality()==dev.redstoneengineering.core.port.PortQuality.VALID
                        ? m.inputValue()+" precision units" : "NOT READY • input unverified"),
                RseLdUiComponents.liveRow("SOLVER", "y[n-1]", () -> m.runtimePresent()
                        ? m.previousOutput()+" precision units" : "NOT READY • no retained state"),
                RseLdUiComponents.liveRow("ADJUSTABLE", "α", () -> formatAlpha(m.alphaIndex())),
                RseLdUiComponents.fixedRow("profile", () -> LapisLowPassMenu.profileId(),
                        "central EngineeringParameterProfile provenance"),
                RseLdUiComponents.fixedRow("Δt", () -> LapisLowPassMenu.samplePeriodTicks() + " ticks",
                        "server sample period"),
                RseLdUiComponents.liveRow("DERIVED", "τ", () ->
                        String.format(Locale.ROOT, "%.3f ticks", LapisLowPassMenu.timeConstantTicksForIndex(m.alphaIndex()))),
                RseLdUiComponents.liveRow("DERIVED", "f_c nominal", () ->
                        String.format(Locale.ROOT, "%.3f Hz", LapisLowPassMenu.cutoffHzForIndex(m.alphaIndex()))),
                new Label().setText("LIVE SUBSTITUTION"),
                RseLdUiComponents.liveRow("MODEL", "substitute", () ->
                        predictionReady(m)
                                ? m.previousOutput() + " + " + formatAlpha(m.alphaIndex()) + "·(" + m.inputValue()
                                        + " - " + m.previousOutput() + ") → predicted " + m.predictedOutput()
                                : "NOT READY • valid RX and initialized filter required")
        );
    }

    private static UIElement livePanel(LapisLowPassMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("LIVE FILTER STATE"),
                RseLdUiComponents.liveRow("RX", "input", () -> m.inputQuality()==dev.redstoneengineering.core.port.PortQuality.VALID
                        ? m.inputValue()+" • VALID" : "NOT READY • "+m.inputQuality().name()),
                RseLdUiComponents.liveRow("STATE", "previous", () -> m.runtimePresent()?Integer.toString(m.previousOutput()):"NOT READY • uninitialized"),
                RseLdUiComponents.liveRow("MODEL", "predicted", () -> predictionReady(m)?Integer.toString(m.predictedOutput()):"NOT READY • no usable model input"),
                RseLdUiComponents.liveRow("TX", "output", () -> m.outputQuality()==dev.redstoneengineering.core.port.PortQuality.VALID
                        ? m.outputValue()+" • VALID" : "NOT READY • "+m.outputQuality().name()),
                RseLdUiComponents.liveRow("STATE", "runtime", () -> m.runtimePresent() ? "INITIALIZED" : "NOT_READY")
        );
    }

    private static UIElement configurePanel(LapisLowPassMenu m) {
        var alpha = new TextField();
        alpha.layout(l -> l.width(120));
        alpha.bind(DataBindingBuilder.string(
                () -> formatAlpha(m.alphaIndex()),
                value -> m.applyAlphaVisibleValue(value)
        ).build());

        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(4)).addChildren(
                new Label().setText("FORMULA PARAMETER • α EXACT ENTRY"),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        new Label().setText("α").layout(l -> l.width(40)),
                        alpha,
                        new Label().setText(alphaSet()).layout(l -> l.flex(1))
                ),
                RseLdUiComponents.serverAction("Restore default α", m::restoreDefaultAlpha),
                new Label().setText("PROFILE RESPONSE TABLE • exact supported α values only; invalid off-grid entry is rejected")
        );
    }

    private static UIElement routePanel(LapisLowPassMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(4)).addChildren(
                new Label().setText("MECHANISM FLOW • RX → FILTER MODEL → SOLVER STATE → TX"),
                RseLdUiComponents.liveRow("RX", "face", () -> m.inputDirection().getName().toUpperCase(Locale.ROOT)),
                RseLdUiComponents.liveRow("TX", "face", () -> m.outputDirection().getName().toUpperCase(Locale.ROOT)),
                new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle RX ▶", m::cycleInputForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶", m::cycleOutputForward)
                )
        );
    }

    private static UIElement evidencePanel(LapisLowPassMenu m) {
        return new UIElement().addClass("panel_bg").layout(l -> l.paddingAll(5).gapAll(3)).addChildren(
                new Label().setText("MEASUREMENT / MODEL EVIDENCE"),
                RseLdUiComponents.liveRow("INPUT", "quality", () -> m.inputQuality().name()),
                RseLdUiComponents.liveRow("OUTPUT", "quality", () -> m.outputQuality().name()),
                RseLdUiComponents.liveRow("LIVE STATE", "HEALTH", m::operationalHealthLabel),
                RseLdUiComponents.liveRow("I/O", "route", m::portRouteLabel),
                new Label().setText("observer-neutral evidence • client never mutates filter runtime or recomputes world physics")
        );
    }

    /** The retained y[n-1] and actual server input are independent prerequisites. */
    private static boolean predictionReady(LapisLowPassMenu m) {
        return m.runtimePresent()
                && m.inputQuality()==dev.redstoneengineering.core.port.PortQuality.VALID;
    }

    private static String formatAlpha(int index) {
        return String.format(Locale.ROOT, "%.2f", LapisLowPassMenu.alphaForIndex(index));
    }

    private static String alphaSet() {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < LapisLowPassMenu.alphaSteps(); i++) {
            if (i > 0) out.append(", ");
            out.append(String.format(Locale.ROOT, "%.2f", LapisLowPassMenu.alphaForIndex(i)));
        }
        return out.append("}").toString();
    }
}
