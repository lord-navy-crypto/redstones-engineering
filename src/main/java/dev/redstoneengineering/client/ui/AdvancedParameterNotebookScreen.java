package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.ui.menu.AdvancedParameterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Full-page notebook for the second ten-block parameter batch. */
public final class AdvancedParameterNotebookScreen extends AbstractContainerScreen<AdvancedParameterMenu> {
    private static final int BG=0xFFF2E9D8, PAGE=0xFFFFF8E8, INK=0xFF2C2925, MUTED=0xFF6E675E, RULE=0xFFB9A98F, ACCENT=0xFF6B4E3D, GOOD=0xFF2F7D4A;
    private enum Tab { OPERATE("Operate"), PARAMETERS("Parameters"), MODEL("Model"); final String label; Tab(String s){label=s;} }
    private Tab tab=Tab.PARAMETERS;
    private final List<Button> controls=new ArrayList<>();
    private Button toggle;

    public AdvancedParameterNotebookScreen(AdvancedParameterMenu menu, Inventory inventory, Component title){
        super(menu,inventory,title); imageWidth=520; imageHeight=292; titleLabelX=18; titleLabelY=12; inventoryLabelY=1000;
    }

    @Override protected void init(){
        super.init(); controls.clear();
        int x=leftPos+83;
        for(Tab t:Tab.values()){
            addRenderableWidget(Button.builder(Component.literal(t.label),b->{tab=t;syncVisibility();}).bounds(x,topPos+34,112,20).build());
            x+=118;
        }
        addRow(0,topPos+124); addRow(1,topPos+164); addRow(2,topPos+204);
        toggle=addRenderableWidget(Button.builder(Component.literal("Toggle"),b->send(AdvancedParameterMenu.BUTTON_P3_TOGGLE)).bounds(leftPos+354,topPos+244,126,20).build());
        syncVisibility();
    }

    private void addRow(int row,int y){
        int minus=row==0?AdvancedParameterMenu.BUTTON_P0_MINUS:row==1?AdvancedParameterMenu.BUTTON_P1_MINUS:AdvancedParameterMenu.BUTTON_P2_MINUS;
        int plus=row==0?AdvancedParameterMenu.BUTTON_P0_PLUS:row==1?AdvancedParameterMenu.BUTTON_P1_PLUS:AdvancedParameterMenu.BUTTON_P2_PLUS;
        controls.add(addRenderableWidget(Button.builder(Component.literal("−"),b->send(minus)).bounds(leftPos+356,y,38,20).build()));
        controls.add(addRenderableWidget(Button.builder(Component.literal("+"),b->send(plus)).bounds(leftPos+442,y,38,20).build()));
    }

    private void send(int id){ if(minecraft!=null&&minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }

    @Override protected void containerTick(){ super.containerTick(); syncVisibility(); }

    private void syncVisibility(){
        int n=parameterCount();
        for(int i=0;i<controls.size();i++) controls.get(i).visible=tab==Tab.PARAMETERS&&(i/2)<n;
        if(toggle!=null) toggle.visible=tab==Tab.PARAMETERS&&menu.kind()==AdvancedParameterMenu.KIND_PULSE_SHAPER;
    }

    private int parameterCount(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PULSE_SHAPER, AdvancedParameterMenu.KIND_LAPIS_NOISE,
                 AdvancedParameterMenu.KIND_ELECTROMAGNET -> 3;
            case AdvancedParameterMenu.KIND_PRECISION_FILTER, AdvancedParameterMenu.KIND_EDGE_DETECTOR,
                 AdvancedParameterMenu.KIND_RELIEF_VALVE, AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> 2;
            default -> 1;
        };
    }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){ renderBackground(g,mx,my,pt); super.render(g,mx,my,pt); renderTooltip(g,mx,my); }

    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,BG);
        g.fill(leftPos+5,topPos+5,leftPos+imageWidth-5,topPos+imageHeight-5,PAGE);
        g.fill(leftPos+18,topPos+29,leftPos+imageWidth-18,topPos+30,RULE);
        g.fill(leftPos+18,topPos+62,leftPos+imageWidth-18,topPos+63,RULE);
    }

    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,18,12,INK,false);
        String live="SERVER PHYSICS"; g.drawString(font,live,imageWidth-18-font.width(live),12,GOOD,false);
        g.drawString(font,tab.label.toUpperCase(),22,70,ACCENT,false);
        switch(tab){case OPERATE->operate(g);case PARAMETERS->parameters(g);case MODEL->model(g);}
        g.drawString(font,fit(footer(),imageWidth-36),18,imageHeight-20,MUTED,false);
    }

    private void parameters(GuiGraphics g){
        String[] labels=parameterLabels(); int[] values={menu.p0(),menu.p1(),menu.p2()};
        for(int i=0;i<labels.length;i++){
            g.drawString(font,labels[i],38,128+i*40,MUTED,false);
            g.drawString(font,paramValue(i,values[i]),200,128+i*40,INK,false);
        }
        if(menu.kind()==AdvancedParameterMenu.KIND_PULSE_SHAPER){
            g.drawString(font,"Retriggerable",38,248,MUTED,false);
            g.drawString(font,menu.p3()!=0?"YES":"NO",200,248,INK,false);
        }
    }

    private void operate(GuiGraphics g){
        String[] labels=liveLabels(); int[] values={menu.liveA(),menu.liveB(),menu.liveC(),menu.liveD()};
        for(int i=0;i<labels.length;i++) pair(g,labels[i],liveValue(i,values[i]),100+i*30);
    }

    private void model(GuiGraphics g){
        g.drawString(font,fit(model1(),455),34,108,INK,false);
        g.drawString(font,fit(model2(),455),34,142,INK,false);
        g.drawString(font,fit(model3(),455),34,180,MUTED,false);
        g.drawString(font,fit("Only configuration variables are editable; measured state, thermal load, evidence and topology stay solver/world-owned.",455),34,224,MUTED,false);
    }

    private String[] parameterLabels(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PRECISION_FILTER -> new String[]{"Rise slew","Fall slew"};
            case AdvancedParameterMenu.KIND_PULSE_SHAPER -> new String[]{"Pulse width","Trigger threshold","Hysteresis"};
            case AdvancedParameterMenu.KIND_EDGE_DETECTOR -> new String[]{"Edge mode","Pulse width"};
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> new String[]{"Gain"};
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> new String[]{"Field rise rate","Field fall rate","Cooling rate"};
            case AdvancedParameterMenu.KIND_INDUCTION_COIL -> new String[]{"Coil turns"};
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> new String[]{"Relief setpoint","Blowdown band"};
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> new String[]{"Baseline","Noise amplitude","Sample period"};
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> new String[]{"Maximum range"};
            case AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> new String[]{"Intensity","Channel"};
            default -> new String[]{"Parameter"};
        };
    }

    private String paramValue(int slot,int v){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PRECISION_FILTER -> v+" level/tick";
            case AdvancedParameterMenu.KIND_PULSE_SHAPER -> slot==0?v+" ticks":v+"/15";
            case AdvancedParameterMenu.KIND_EDGE_DETECTOR -> slot==0?(v==0?"RISING":v==1?"FALLING":"BOTH"):v+" ticks";
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> "×"+v;
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> slot<2?v+" field/tick":v+" thermal/tick";
            case AdvancedParameterMenu.KIND_INDUCTION_COIL -> Integer.toString(v);
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> v+"/100";
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> slot==0?v+"/100":slot==1?"±"+v+"/100":v+" ticks";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> v+" blocks";
            case AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> Integer.toString(v);
            default -> Integer.toString(v);
        };
    }

    private String[] liveLabels(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PRECISION_FILTER -> new String[]{"Input","Output","Tracking error","Settle ETA"};
            case AdvancedParameterMenu.KIND_PULSE_SHAPER -> new String[]{"Pulse remaining","Accepted triggers","Suppressed triggers","Rearm threshold"};
            case AdvancedParameterMenu.KIND_EDGE_DETECTOR -> new String[]{"Edge count","Pulse remaining","Rejected evidence","Last edge age"};
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> new String[]{"Input","Output","Clip episodes","Max raw"};
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> new String[]{"Copper voltage","Actual field","Target field","Thermal load"};
            case AdvancedParameterMenu.KIND_INDUCTION_COIL -> new String[]{"Magnetic field","Induced output","Output quality"};
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> new String[]{"Network pressure","Reseat pressure","Vent events","Last excess"};
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> new String[]{"Current sample","Initialized"};
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> new String[]{"Measured distance","Configured range","Coverage complete"};
            case AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> new String[]{"Emission intensity","Channel"};
            default -> new String[]{"Live"};
        };
    }

    private String liveValue(int i,int v){
        if(menu.kind()==AdvancedParameterMenu.KIND_PRECISION_FILTER&&i==3) return v<0?"UNAVAILABLE":v+" ticks";
        if(menu.kind()==AdvancedParameterMenu.KIND_EDGE_DETECTOR&&i==3) return v<0?"NONE":v+" ticks";
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_NOISE&&i==1) return v!=0?"YES":"NO";
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_RANGE&&i==2) return v!=0?"YES":"NO";
        return Integer.toString(v);
    }

    private String model1(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PRECISION_FILTER -> "y[k+1] approaches x[k] with independent rise and fall slew limits.";
            case AdvancedParameterMenu.KIND_PULSE_SHAPER -> "Accepted threshold crossing starts a monostable pulse of configured width.";
            case AdvancedParameterMenu.KIND_EDGE_DETECTOR -> "Selected level transition emits a bounded pulse instead of forwarding a steady level.";
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> "raw = input × gain; output = min(15, raw).";
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> "B[k+1] approaches Btarget with independent rise/fall slew; Btarget is thermally derated.";
            case AdvancedParameterMenu.KIND_INDUCTION_COIL -> "|emf| ∝ N × |ΔΦ/Δt|, then clamps to Copper 0..15.";
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> "Open if P>Pset; while venting, stay open until P≤Pset−ΔPblowdown.";
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> "sample = baseline + deterministic bounded noise.";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> "Normalized output derives from measured distance / configured maximum range.";
            case AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> "Emitter launches selected intensity on one discrete optical channel.";
            default -> "";
        };
    }

    private String model2(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PULSE_SHAPER -> "Rearm threshold = trigger threshold − hysteresis.";
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> "H[k+1] = clamp(H + heating(V) − cooling, 0,1000); cooling is an operator design parameter.";
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> "Setpoint and blowdown are independent safety parameters; blowdown prevents rapid open/close chatter.";
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> "Baseline, amplitude and sampling cadence are independent experiment variables.";
            default -> "Configuration affects the authoritative device solver, not a client-only visualization.";
        };
    }

    private String model3(){ return "All values shown on Operate are synchronized evidence from the real Minecraft world state."; }

    private String footer(){ return "Engineering Notebook • precise parameters where physics supports them • discrete variables remain discrete"; }

    private void pair(GuiGraphics g,String label,String value,int y){ g.drawString(font,label,42,y,MUTED,false); g.drawString(font,fit(value,230),245,y,INK,false); }
    private String fit(String s,int width){ if(font.width(s)<=width)return s; String x=s; while(x.length()>1&&font.width(x+"…")>width)x=x.substring(0,x.length()-1); return x+"…"; }
}
