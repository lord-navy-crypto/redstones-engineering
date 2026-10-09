package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class QuartzTimingLdUi {
    private QuartzTimingLdUi() {}

    public static ModularUI create(QuartzTimingMenu menu, Player player) {
        var root = new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(deviceName(menu)),
                RseLdUiComponents.tabbedWorkspace(
                        580, 350, 750,
                        new String[]{"Timing", "Configure", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        new Label().setText("FORMULA-FIRST TIMING MODEL"),
                                        RseLdUiComponents.formulaCard(() -> equation(menu)),
                                        overview(menu)),
                                RseLdUiComponents.workspacePage(controls(menu)),
                                RseLdUiComponents.workspacePage(evidence(menu), RseLdUiComponents.authorityFooter())
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 600, 390);
    }

    private static UIElement overview(QuartzTimingMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        if (m.kind() == QuartzTimingMenu.KIND_OSCILLATOR) {
            p.addChildren(
                    RseLdUiComponents.liveRow("STATE","clock",()->m.primary()==1?"HIGH":"LOW"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","T",()->m.secondary()+" ticks"),
                    RseLdUiComponents.liveRow("DERIVED","f_nom",()->String.format(java.util.Locale.ROOT,"%.3f Hz",20.0/Math.max(1,m.secondary()))),
                    RseLdUiComponents.fixedRow("topology",()->"N/E/S/W outputs","source • no fake series path")
            );
        } else if (m.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","T_in",()->m.primary()+" ticks"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","N",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("DERIVED","T_out",()->m.secondary()+" ticks"),
                    RseLdUiComponents.liveRow("EVIDENCE","expected",()->expectedDividerPeriod(m)+" ticks"),
                    RseLdUiComponents.liveRow("EVIDENCE", "period limit", () -> dividerSaturated(m) ? "SATURATED @4096" : "IN RANGE")
            );
        } else {
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","T_meas",()->m.primary()+" ticks"),
                    RseLdUiComponents.liveRow("MEASURED","T_upstream",()->m.tertiary()+" ticks"),
                    RseLdUiComponents.liveRow("DERIVED","|e_T|",()->m.secondary()+" ticks"),
                    RseLdUiComponents.liveRow("EVIDENCE","current",()->m.runtimeC()==1?"YES":"NO"),
                    RseLdUiComponents.liveRow("EVIDENCE","initialized / first edge",()->m.runtimeA()+ " / "+m.runtimeB())
            );
        }
        return p;
    }

    private static UIElement controls(QuartzTimingMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(4));
        if (m.kind() == QuartzTimingMenu.KIND_OSCILLATOR || m.kind() == QuartzTimingMenu.KIND_DIVIDER) {
            var input = new TextField().setNumbersOnlyInt(2, 32);
            input.layout(l -> l.width(110));
            input.bind(DataBindingBuilder.string(
                    () -> Integer.toString(m.kind()==QuartzTimingMenu.KIND_OSCILLATOR?m.secondary():m.tertiary()),
                    value -> { try { m.setTimingParameterFromUi(Integer.parseInt(value)); } catch (NumberFormatException ignored) {} }
            ).build());
            p.addChildren(
                    new Label().setText(m.kind()==QuartzTimingMenu.KIND_OSCILLATOR?"DIRECT T • {2,4,8,16,32}":"DIRECT N • {2,4,8,16}"),
                    input
            );
        } else {
            p.addChildren(
                    RseLdUiComponents.fixedRow("measurement",()->"observer-only","two genuine rising edges required"),
                    RseLdUiComponents.serverAction("Reset measurement", m::resetMeasurement)
            );
        }
        if (m.kind()!=QuartzTimingMenu.KIND_OSCILLATOR) {
            p.addChild(new UIElement().layout(l -> l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                    RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                    m.kind()==QuartzTimingMenu.KIND_DIVIDER
                            ? RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
                            : RseLdUiComponents.serverAction("Cycle measurement face ▶",m::cycleWholeRouteForward)
            ));
        }
        return p;
    }

    /**
     * The three runtime slots have different semantics for each clock family.
     * Expose their actual server-defined meanings, not misleading A/B/C labels.
     */
    private static UIElement evidence(QuartzTimingMenu m) {
        var p = new UIElement().addClass("panel_bg");
        p.layout(l -> l.paddingAll(5).gapAll(3));
        p.addChild(RseLdUiComponents.liveRow("EVIDENCE","port quality",()->m.quality().name()));
        switch (m.kind()) {
            case QuartzTimingMenu.KIND_OSCILLATOR -> p.addChildren(
                    RseLdUiComponents.liveRow("STATE", "oscillator output", () -> m.primary()==1?"HIGH":"LOW"),
                    RseLdUiComponents.liveRow("MODEL", "period index", () -> Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("TIMING", "nominal period", () -> m.secondary()+" ticks"),
                    RseLdUiComponents.fixedRow("history",()->"NOT RETAINED",
                            "This oscillator has no historical edge or jitter trace in the menu snapshot"));
            case QuartzTimingMenu.KIND_DIVIDER -> p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED", "counted rising edges", () -> Integer.toString(m.runtimeA())),
                    RseLdUiComponents.liveRow("EVIDENCE", "divider initialized", () -> m.runtimeB()==1?"YES":"NO"),
                    RseLdUiComponents.liveRow("MODEL", "ideal bounded period", () -> expectedDividerPeriod(m)+" ticks"),
                    RseLdUiComponents.liveRow("LIMIT", "4096 tick saturation", () -> dividerSaturated(m)?"SATURATED":"IN RANGE"));
            case QuartzTimingMenu.KIND_STABILITY -> p.addChildren(
                    RseLdUiComponents.liveRow("EVIDENCE", "measurement initialized", () -> m.runtimeA()==1?"YES":"NO"),
                    RseLdUiComponents.liveRow("EVIDENCE", "reference edge seen", () -> m.runtimeB()==1?"YES":"NO"),
                    RseLdUiComponents.liveRow("EVIDENCE", "current measurement", () -> m.runtimeC()==1?"YES":"NO"),
                    RseLdUiComponents.liveRow("MEASURED", "T_meas / T_upstream", () -> m.primary()+" / "+m.tertiary()+" ticks"),
                    RseLdUiComponents.liveRow("DERIVED", "absolute period error", () -> m.secondary()+" ticks"));
            default -> p.addChild(RseLdUiComponents.fixedRow("timing",()->"UNKNOWN",
                    "No runtime semantics inferred for an unidentified device"));
        }
        return p;
    }

    private static String deviceName(QuartzTimingMenu m){return switch(m.kind()){case QuartzTimingMenu.KIND_DIVIDER->"QUARTZ CLOCK DIVIDER";case QuartzTimingMenu.KIND_STABILITY->"QUARTZ STABILITY MONITOR";default->"QUARTZ OSCILLATOR";};}
    private static int expectedDividerPeriod(QuartzTimingMenu m){
        if(m.primary()<=0) return 0;
        return Math.min(4096,Math.max(1,m.primary())*Math.max(1,m.tertiary()));
    }
    private static boolean dividerSaturated(QuartzTimingMenu m){
        return m.primary()>0 && (long)Math.max(1,m.primary())*Math.max(1,m.tertiary())>4096L;
    }
    private static String equation(QuartzTimingMenu m){return switch(m.kind()){case QuartzTimingMenu.KIND_DIVIDER->"valid input ⇒ T_out = min(4096, N · max(1,T_in)) ticks";case QuartzTimingMenu.KIND_STABILITY->"|e_T| = |T_meas - T_upstream|";default->"f_nom = 20 / T Hz";};}
}
