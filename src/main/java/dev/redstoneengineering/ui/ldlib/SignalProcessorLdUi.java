package dev.redstoneengineering.ui.ldlib;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaFlexDirection;

public final class SignalProcessorLdUi {
    private SignalProcessorLdUi(){}

    public static ModularUI create(SignalProcessorMenu menu, Player player){
        var root=new UIElement().addClass("panel_bg");
        root.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).gapAll(6));
        root.addChildren(
                RseLdUiComponents.title("PIONEER PATTERN • SIGNAL PROCESSOR MODEL"),
                RseLdUiComponents.tabbedWorkspace(
                        630, 400, 850,
                        new String[]{"Overview", "Configure", "Diagnostics", "Evidence", "More"},
                        new UIElement[]{
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.formulaCard(()->processorEquation(menu.kind())),
                                        RseLdUiComponents.liveRow("MEASURED","x[n]",()->menu.input()+" / 15"),
                                        RseLdUiComponents.liveRow("OUTPUT","y[n]",()->menu.output()+" / 15"),
                                        RseLdUiComponents.liveRow("ADJUSTABLE",symbol(menu.kind()),()->parameter(menu)),
                                        runtime(menu)
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("ADJUSTABLE",symbol(menu.kind()),()->parameter(menu)),
                                        control(menu)
                                ),
                                RseLdUiComponents.workspacePage(
                                        runtime(menu),
                                        RseLdUiComponents.fixedRow("evidence authority",()->"SERVER RETAINED",
                                                "The HMI reads response, edge history or pulse state; it cannot manufacture input quality")
                                ),
                                RseLdUiComponents.workspacePage(
                                        RseLdUiComponents.liveRow("I/O","route",()->menu.inputDirection().getName().toUpperCase()+" → "+menu.outputDirection().getName().toUpperCase()),
                                        new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                        RseLdUiComponents.serverAction("Cycle RX ▶",menu::cycleInputForward),
                        RseLdUiComponents.serverAction("Cycle TX ▶",menu::cycleOutputForward)
                )
                                ),
                                RseLdUiComponents.workspacePage(
                                        new Label().setText(evidenceContract(menu.kind())),
                                        RseLdUiComponents.authorityFooter()
                                )
                        }
                )
        );
        return RseLdUiComponents.responsiveUi(root, player, 650, 440);
    }

    private static UIElement control(SignalProcessorMenu menu){
        if(menu.kind()!=SignalProcessorMenu.KIND_EDGE
                && menu.kind()!=SignalProcessorMenu.KIND_FILTER
                && menu.kind()!=SignalProcessorMenu.KIND_PULSE){
            return RseLdUiComponents.fixedRow("control", () -> "UNAVAILABLE",
                    "No server-side adjustable parameter exists for this device");
        }
        if(menu.kind()==SignalProcessorMenu.KIND_EDGE){
            return RseLdUiComponents.serverAction("Cycle edge mode ▶",menu::cycleParameterForward);
        }
        int max=menu.kind()==SignalProcessorMenu.KIND_FILTER?4:8;
        var field=new TextField().setNumbersOnlyInt(1,max); field.layout(l->l.width(90));
        field.bind(DataBindingBuilder.string(
                ()->Integer.toString(menu.parameter()),
                v->{try{menu.setParameterFromUi(Integer.parseInt(v));}catch(NumberFormatException ignored){}}
        ).build());
        return new UIElement().layout(l->l.flexDirection(YogaFlexDirection.ROW).gapAll(6)).addChildren(
                new Label().setText("DIRECT ENTRY"),
                field,
                new Label().setText(menu.kind()==SignalProcessorMenu.KIND_FILTER?"r ∈ 1..4":"W ∈ 1..8 ticks")
        );
    }

    private static UIElement runtime(SignalProcessorMenu m){
        var p=new UIElement().addClass("panel_bg"); p.layout(l->l.paddingAll(5).gapAll(3));
        if(m.kind()==SignalProcessorMenu.KIND_FILTER){
            p.addChildren(
                    RseLdUiComponents.liveRow("DERIVED","|x-y|",()->Integer.toString(m.runtimeA())),
                    RseLdUiComponents.liveRow("EVIDENCE","response",()->m.runtimeB()==1
                            ? "ZERO NUMERICAL LAG • input presence unverified"
                            : "NONZERO LAG • response in progress"),
                    RseLdUiComponents.note("Equality x=y is not evidence that a sensor or input source is connected.")
            );
        }else if(m.kind()==SignalProcessorMenu.KIND_EDGE){
            p.addChildren(
                    RseLdUiComponents.liveRow("RUNTIME","pulse",()->m.runtimeA()+"t remaining"),
                    RseLdUiComponents.liveRow("EVIDENCE","initialized",()->m.initialized()?"YES • baseline sampled":"NO • await first sample"),
                    RseLdUiComponents.liveRow("EVIDENCE","edges",()->m.initialized()
                            ? m.runtimeB()+" • last age "+(m.runtimeC()<0?"NONE":m.runtimeC()+"t")
                            : "NOT READY • no input baseline")
            );
        }else{
            p.addChildren(
                    RseLdUiComponents.liveRow("RUNTIME","pulse",()->m.runtimeA()+"t remaining"),
                    RseLdUiComponents.liveRow("EVIDENCE","initialized",()->m.initialized()?"YES • last input "+m.runtimeB():"NO"),
                RseLdUiComponents.liveRow("MODEL","configured pulse width",()->m.parameter()+" ticks")
            );
        }
        return p;
    }

    private static String processorEquation(int k){return switch(k){
        case SignalProcessorMenu.KIND_EDGE -> "e[n] = edge_mode(x[n-1], x[n]); e[n] ⇒ y=15 for 2 ticks";
        case SignalProcessorMenu.KIND_PULSE -> "rising edge(x) ⇒ y=15 for W ticks; otherwise y=0";
        default -> "y[n+1] = y[n] + clamp(x[n]-y[n], -r, +r)";
    };}
    private static String symbol(int k){return k==SignalProcessorMenu.KIND_EDGE?"mode":k==SignalProcessorMenu.KIND_PULSE?"W":"r";}
    private static String parameter(SignalProcessorMenu m){
        if(m.kind()==SignalProcessorMenu.KIND_EDGE) return switch(m.parameter()){case 0->"RISING";case 1->"FALLING";default->"BOTH";};
        return m.kind()==SignalProcessorMenu.KIND_PULSE?m.parameter()+" ticks":m.parameter()+" level/tick";
    }
    private static String evidenceContract(int k){return switch(k){
        case SignalProcessorMenu.KIND_EDGE -> "Edge chronology is retained by server runtime; opening diagnostics never initializes the detector or creates a false edge.";
        case SignalProcessorMenu.KIND_PULSE -> "Observer readback never initializes or retriggers runtime state.";
        default -> "The filter owns response speed, not gain or offset; temporary lag is expected until the server-authoritative response settles.";
    };}
}
