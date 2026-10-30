package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class AmethystSystemLdUi {
    private AmethystSystemLdUi() {}

    public static ModularUI create(AmethystSystemMenu m, Player player){
        var root=new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title(deviceName(m)),
                RseLdUiComponents.tabbedWorkspace(
                        600, 400, 850,
                        new String[]{"Overview", "Details", "Evidence"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.title("PIONEER PATTERN • RESONANCE MODEL"),
                                        RseLdUiComponents.formulaCard(()->equation(m)),
                                        overview(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        controls(m),
                                        resonanceMechanism(m)
                                ),
                                RseLdUiComponents.workspacePage(
                                        diagnostics(m),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 620, 440);
    }

    /** Configuration remains visible even when input/output resonance evidence is absent. */
    private static boolean reliableResonance(AmethystSystemMenu m) {
        return m.quality() == PortQuality.VALID;
    }

    private static UIElement overview(AmethystSystemMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(3));
        if(m.kind()==AmethystSystemMenu.KIND_SOURCE){
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","f_idx",()->Integer.toString(m.primary())),
                    RseLdUiComponents.liveRow("ADJUSTABLE","A",()->m.secondary()+" / 15"),
                    RseLdUiComponents.liveRow("STATE","source",()->m.stateFlag()==1?"ACTIVE":"IDLE")
            );
        } else if(m.kind()==AmethystSystemMenu.KIND_FILTER){
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","f_in/A_in",()->reliableResonance(m)?m.primary()+" / "+m.secondary():"NOT READY • resonance input unverified"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","f_target",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("DERIVED","A_out",()->reliableResonance(m)?Integer.toString(m.auxiliary()):"UNVERIFIED • no valid resonance input")
            );
        } else if(m.kind()==AmethystSystemMenu.KIND_TUNED){
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","f_in/A_in",()->reliableResonance(m)?m.primary()+" / "+m.secondary():"NOT READY • resonance input unverified"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","f0",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("ADJUSTABLE","Q_idx",()->Integer.toString(m.auxiliary())),
                    RseLdUiComponents.liveRow("DERIVED","BW",()->Integer.toString(m.extraA())),
                    RseLdUiComponents.liveRow("DERIVED","A_out",()->reliableResonance(m)?Integer.toString(m.extraB()):"UNVERIFIED • no valid resonance input")
            );
        } else {
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","dominant/energy",()->reliableResonance(m)?m.primary()+" / "+m.secondary():"NOT READY • spectrum evidence incomplete"),
                    RseLdUiComponents.liveRow("MEASURED","active bands",()->reliableResonance(m)?Integer.toString(m.tertiary()):"NOT READY • spectrum evidence incomplete"),
                    RseLdUiComponents.liveRow("EVIDENCE","samples/conflicts",()->m.auxiliary()+" / "+m.extraA()),
                    RseLdUiComponents.liveRow("EVIDENCE","coverage",()->m.extraB()+" / "+m.stateFlag())
            );
        }
        p.addChild(RseLdUiComponents.fixedRow("frequency units",()->"MODEL INDEX 1..15",
                "Frequency values are deliberate model indices, not fabricated Hz"));
        return p;
    }

    /** Explain live resonance mechanisms using only menu-synchronized values. */
    private static UIElement resonanceMechanism(AmethystSystemMenu m){
        var p=new UIElement().addClass("panel_bg");
        p.layout(l->l.paddingAll(5).gapAll(4));
        p.addChild(RseLdUiComponents.title("RESONANCE • MODEL INDICES / OBSERVED RESPONSE"));
        if(m.kind()==AmethystSystemMenu.KIND_SOURCE){
            p.addChildren(
                    RseLdUiComponents.liveRow("ADJUSTABLE","frequency index",()->m.primary()+" / 15"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","source amplitude",()->m.secondary()+" / 15"),
                    RseLdUiComponents.liveRow("STATE","transmission",()->m.stateFlag()==1?"PULSE ACTIVE":"IDLE / NO EMISSION"));
        }else if(m.kind()==AmethystSystemMenu.KIND_FILTER){
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","carrier index",()->reliableResonance(m)?Integer.toString(m.primary()):"NOT READY"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","target index",()->Integer.toString(m.tertiary())),
                    RseLdUiComponents.liveRow("EVIDENCE","server band match",()->reliableResonance(m)?(m.stateFlag()==1?"MATCH":"REJECT"):"NOT READY • input unverified"),
                    RseLdUiComponents.liveRow("OUTPUT","amplitude after filter",()->reliableResonance(m)?Integer.toString(m.auxiliary()):"NOT READY"));
        }else if(m.kind()==AmethystSystemMenu.KIND_TUNED){
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","incoming / natural index",()->reliableResonance(m)?m.primary()+" / "+m.tertiary():"NOT READY • input unverified"),
                    RseLdUiComponents.liveRow("DERIVED","absolute detuning |Δf_idx|",()->reliableResonance(m)?Integer.toString(Math.abs(m.primary()-m.tertiary())):"NOT READY • input unverified"),
                    RseLdUiComponents.liveRow("ADJUSTABLE","quality-factor index",()->Integer.toString(m.auxiliary())),
                    RseLdUiComponents.liveRow("MODEL","server bandwidth index",()->Integer.toString(m.extraA())),
                    RseLdUiComponents.liveRow("STATE","server response",()->reliableResonance(m)?(m.stateFlag()==2?"SATURATED":m.stateFlag()==1?"RESPONDING":"NO RESPONSE"):"UNVERIFIED • input evidence"),
                    RseLdUiComponents.liveRow("OUTPUT","resonance amplitude",()->reliableResonance(m)?Integer.toString(m.extraB()):"NOT READY"));
        }else if(m.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            p.addChildren(
                    RseLdUiComponents.liveRow("MEASURED","dominant band index",()->reliableResonance(m)?Integer.toString(m.primary()):"NOT READY"),
                    RseLdUiComponents.liveRow("MEASURED","aggregate energy",()->reliableResonance(m)?Integer.toString(m.secondary()):"NOT READY"),
                    RseLdUiComponents.liveRow("MEASURED","number of active bands",()->reliableResonance(m)?Integer.toString(m.tertiary()):"NOT READY"),
                    RseLdUiComponents.liveRow("EVIDENCE","samples",()->Integer.toString(m.auxiliary())),
                    RseLdUiComponents.liveRow("EVIDENCE","source conflicts",()->Integer.toString(m.extraA())),
                    RseLdUiComponents.liveRow("COVERAGE","scanned / expected cells",()->m.extraB()+" / "+m.stateFlag()));
        }
        p.addChild(RseLdUiComponents.fixedRow("frequency units",()->"INDEX, NOT HERTZ",
                "The RSE resonance model uses discrete carrier bands, not mapped physical SI frequencies"));
        return p;
    }

    private static UIElement controls(AmethystSystemMenu m){
        var p=new UIElement().addClass("panel_bg");p.layout(l->l.paddingAll(5).gapAll(4));
        if(m.kind()!=AmethystSystemMenu.KIND_SPECTRUM){
            var primary=new TextField().setNumbersOnlyInt(1,15); primary.layout(l->l.width(110));
            primary.bind(DataBindingBuilder.string(
                    ()->Integer.toString(m.kind()==AmethystSystemMenu.KIND_SOURCE?m.primary():m.tertiary()),
                    v->{try{m.setPrimaryFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}
            ).build());
            p.addChild(primary);
        }
        if(m.kind()==AmethystSystemMenu.KIND_SOURCE || m.kind()==AmethystSystemMenu.KIND_TUNED){
            int max=m.kind()==AmethystSystemMenu.KIND_SOURCE?15:4;
            var secondary=new TextField().setNumbersOnlyInt(1,max);secondary.layout(l->l.width(110));
            secondary.bind(DataBindingBuilder.string(
                    ()->Integer.toString(m.kind()==AmethystSystemMenu.KIND_SOURCE?m.secondary():m.auxiliary()),
                    v->{try{m.setSecondaryFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}
            ).build());
            p.addChild(secondary);
        }
        if(m.kind()==AmethystSystemMenu.KIND_SOURCE){
            p.addChild(RseLdUiComponents.serverAction("Pulse",m::pulse));
        }
        if(m.directional()){
            p.addChild(new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                    RseLdUiComponents.serverAction("Cycle direction ▶",m::cycleWholeRouteForward),
                    RseLdUiComponents.serverAction("Cycle RX ▶",m::cycleInputForward),
                    RseLdUiComponents.serverAction("Cycle TX ▶",m::cycleOutputForward)
            ));
        }
        if(m.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            p.addChild(RseLdUiComponents.fixedRow("control",()->"READ ONLY","spectrum analyzer is observer-only"));
        }
        return p;
    }

    private static UIElement diagnostics(AmethystSystemMenu m){
        return new UIElement().addClass("panel_bg").layout(l->l.paddingAll(5).gapAll(3)).addChildren(
                RseLdUiComponents.liveRow("EVIDENCE","quality",()->m.quality().name()),
                RseLdUiComponents.liveRow("DIAGNOSIS","state",()->diagnosis(m)),
                RseLdUiComponents.liveRow("NEXT","action",()->nextAction(m)),
                RseLdUiComponents.fixedRow("history",()->"SERVER RESONANCE EVIDENCE","no client-side spectrum/history is invented")
        );
    }

    private static String equation(AmethystSystemMenu m){
        if(m.kind()==AmethystSystemMenu.KIND_FILTER)return "A_out = (f_in = f_target) ? max(0, A_in - 1) : 0";
        if(m.kind()==AmethystSystemMenu.KIND_TUNED)return "BW = 5 - Q ; Δf = |f_in - f0| ; response depends on Δf within BW";
        if(m.kind()==AmethystSystemMenu.KIND_SOURCE)return "carrier = (f_idx, A) on the Amethyst resonance domain";
        return "spectrum = dominant index + energy + active-band evidence";
    }

    private static String diagnosis(AmethystSystemMenu m){
        if(m.quality()==PortQuality.TOPOLOGY_ERROR)return "SOURCE CONFLICT / resonance topology ambiguous";
        if(m.quality()==PortQuality.FAULT || m.quality()==PortQuality.DOMAIN_MISMATCH)
            return "RESONANCE INPUT FAULT • "+m.quality().name();
        if(m.quality()==PortQuality.NO_SIGNAL)return "NO RESONANCE EVIDENCE";
        if(m.quality()==PortQuality.STALE || m.quality()==PortQuality.NOT_READY)
            return "UNVERIFIED RESONANCE EVIDENCE • "+m.quality().name();
        if(m.quality()!=PortQuality.VALID)return "NON-VALID RESONANCE INPUT • "+m.quality().name();
        if(m.kind()==AmethystSystemMenu.KIND_FILTER){
            if(m.primary()!=m.tertiary())return "FREQUENCY REJECT • input does not match selected band";
            return m.auxiliary()>0?"FREQUENCY PASS • selected band present":"MATCHED BAND • zero amplitude";
        }
        if(m.kind()==AmethystSystemMenu.KIND_TUNED){
            int detune=Math.abs(m.primary()-m.tertiary());
            if(m.stateFlag()==2)return "RESONANT RESPONSE SATURATED";
            if(detune<=m.extraA())return "IN-BAND RESONANT RESPONSE";
            return "OUT-OF-BAND / DETUNED";
        }
        if(m.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            if(m.extraA()>0)return "SPECTRUM CONFLICT • overlapping source evidence";
            if(m.tertiary()==0)return "QUIET SPECTRUM";
            if(m.tertiary()==1)return "SINGLE-BAND RESONANCE";
            return "MULTI-BAND RESONANCE";
        }
        return m.stateFlag()==1?"SOURCE ACTIVE":"SOURCE IDLE / NO TRANSMISSION";
    }

    private static String nextAction(AmethystSystemMenu m){
        if(m.quality()==PortQuality.TOPOLOGY_ERROR)return "NEXT • isolate competing resonance sources before interpreting frequency.";
        if(m.quality()!=PortQuality.VALID)return "NEXT • resolve resonance source, domain and coverage quality before accepting the response.";
        if(m.kind()==AmethystSystemMenu.KIND_FILTER&&m.primary()!=m.tertiary())return "NEXT • align target index with the carrier or intentionally keep this rejection band.";
        if(m.kind()==AmethystSystemMenu.KIND_TUNED&&Math.abs(m.primary()-m.tertiary())>m.extraA())return "NEXT • retune natural index or widen the modeled response band via Q.";
        if(m.kind()==AmethystSystemMenu.KIND_SPECTRUM&&m.extraA()>0)return "NEXT • separate conflicting sources, then rescan the spectrum.";
        return "NEXT • resonance evidence is coherent; compare amplitude/response before changing topology.";
    }

    private static String deviceName(AmethystSystemMenu m){return switch(m.kind()){case AmethystSystemMenu.KIND_SOURCE->"AMETHYST RESONATOR";case AmethystSystemMenu.KIND_FILTER->"AMETHYST FREQUENCY FILTER";case AmethystSystemMenu.KIND_TUNED->"TUNED AMETHYST RESONATOR";case AmethystSystemMenu.KIND_SPECTRUM->"AMETHYST SPECTRUM ANALYZER";default->"AMETHYST DEVICE";};}
}
