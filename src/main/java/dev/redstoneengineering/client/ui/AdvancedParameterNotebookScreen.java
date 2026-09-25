package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.signal.LapisNoiseSourceLogic;
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
    private static final int BG=0xFFF2E9D8, PAGE=0xFFFFF8E8, INK=0xFF2C2925, MUTED=0xFF6E675E, RULE=0xFFB9A98F, ACCENT=0xFF6B4E3D, GOOD=0xFF2F7D4A, WARN=0xFF9A6A19, BAD=0xFFA43838;
    private enum Tab { OPERATE("Operate"), PARAMETERS("Parameters"), MODEL("Model"), ROUTING("Routing"), DIAGNOSTICS("Diagnostics"); final String label; Tab(String s){label=s;} }
    private Tab tab=Tab.PARAMETERS;
    private final List<Button> controls=new ArrayList<>();
    private final List<Button> routeControls=new ArrayList<>();
    private Button toggle;
    private int scrollOffset=0;
    private int horizontalOffset=0;
    private static final int VIEW_MARGIN=8, CONTENT_TOP=84, CONTENT_BOTTOM_MARGIN=34;

    public AdvancedParameterNotebookScreen(AdvancedParameterMenu menu, Inventory inventory, Component title){
        super(menu,inventory,title); imageWidth=520; imageHeight=292; titleLabelX=18; titleLabelY=12; inventoryLabelY=1000;
    }

    @Override protected void init(){
        imageWidth=Math.max(360,width-VIEW_MARGIN*2);
        imageHeight=Math.max(240,height-VIEW_MARGIN*2);
        super.init(); controls.clear(); routeControls.clear(); scrollOffset=0; horizontalOffset=0;
        int count=Tab.values().length, gap=imageWidth<460?4:7;
        int tabWidth=Math.max(48,(imageWidth-48-gap*(count-1))/count);
        int x=leftPos+24;
        for(Tab t:Tab.values()){
            addRenderableWidget(Button.builder(Component.literal(tabLabel(t)),b->{tab=t;scrollOffset=0;horizontalOffset=0;syncVisibility();})
                    .bounds(x,topPos+38,tabWidth,22).build());
            x+=tabWidth+gap;
        }
        addRow(0,CONTENT_TOP+50); addRow(1,CONTENT_TOP+106); addRow(2,CONTENT_TOP+162);
        toggle=addRenderableWidget(Button.builder(Component.literal("Toggle"),b->send(AdvancedParameterMenu.BUTTON_P3_TOGGLE))
                .bounds(leftPos+imageWidth-184,topPos+CONTENT_TOP+224,146,22).build());

        int routeWidth=routeButtonWidth(), routeGap=8, routeX=routeButtonStartX(), routeY=topPos+CONTENT_TOP+112;
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("RX ◀"),b->send(AdvancedParameterMenu.BUTTON_INPUT_PREVIOUS))
                .bounds(routeX,routeY,routeWidth,22).build()));
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("RX ▶"),b->send(AdvancedParameterMenu.BUTTON_INPUT_NEXT))
                .bounds(routeX+routeWidth+routeGap,routeY,routeWidth,22).build()));
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("TX ◀"),b->send(AdvancedParameterMenu.BUTTON_OUTPUT_PREVIOUS))
                .bounds(routeX+(routeWidth+routeGap)*2,routeY,routeWidth,22).build()));
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("TX ▶"),b->send(AdvancedParameterMenu.BUTTON_OUTPUT_NEXT))
                .bounds(routeX+(routeWidth+routeGap)*3,routeY,routeWidth,22).build()));
        syncVisibility();
    }

    private String tabLabel(Tab value){
        if(imageWidth>=500)return value.label;
        return switch(value){
            case OPERATE->"Run";
            case PARAMETERS->"Params";
            case MODEL->"Model";
            case ROUTING->"Route";
            case DIAGNOSTICS->"Diag";
        };
    }

    private int routeButtonWidth(){
        return Math.max(54,Math.min(82,(imageWidth-48-8*3)/4));
    }

    private int routeButtonStartX(){
        int total=routeButtonWidth()*4+8*3;
        return leftPos+Math.max(24,(imageWidth-total)/2);
    }

    private void addRow(int row,int virtualY){
        int minus=row==0?AdvancedParameterMenu.BUTTON_P0_MINUS:row==1?AdvancedParameterMenu.BUTTON_P1_MINUS:AdvancedParameterMenu.BUTTON_P2_MINUS;
        int plus=row==0?AdvancedParameterMenu.BUTTON_P0_PLUS:row==1?AdvancedParameterMenu.BUTTON_P1_PLUS:AdvancedParameterMenu.BUTTON_P2_PLUS;
        int y=topPos+virtualY-scrollOffset;
        controls.add(addRenderableWidget(Button.builder(Component.literal("−"),b->send(minus)).bounds(leftPos+imageWidth-164,y,42,22).build()));
        controls.add(addRenderableWidget(Button.builder(Component.literal("+"),b->send(plus)).bounds(leftPos+imageWidth-78,y,42,22).build()));
    }

    private void send(int id){ if(minecraft!=null&&minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }

    @Override protected void containerTick(){ super.containerTick(); syncVisibility(); }

    @Override
    public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){
        if(mouseX>=leftPos+18&&mouseX<=leftPos+imageWidth-18
                &&mouseY>=topPos+CONTENT_TOP&&mouseY<=topPos+imageHeight-CONTENT_BOTTOM_MARGIN){
            double horizontalDelta=Math.abs(scrollX)>0.01?scrollX:(hasShiftDown()?scrollY:0.0);
            if(Math.abs(horizontalDelta)>0.01&&maxHorizontalScroll()>0){
                horizontalOffset=Math.max(0,Math.min(maxHorizontalScroll(),horizontalOffset-(int)Math.round(horizontalDelta*32.0)));
            }else{
                scrollOffset=Math.max(0,Math.min(maxScroll(),scrollOffset-(int)Math.round(scrollY*24.0)));
            }
            syncVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }

    private int contentHeight(){
        return switch(tab){case OPERATE->430;case PARAMETERS->500;case MODEL->760;case ROUTING->520;case DIAGNOSTICS->620;};
    }
    private int maxScroll(){
        int visible=Math.max(80,imageHeight-CONTENT_TOP-CONTENT_BOTTOM_MARGIN);
        return Math.max(0,contentHeight()-visible);
    }

    private int virtualContentWidth(){ return Math.max(imageWidth-36,1100); }
    private int maxHorizontalScroll(){
        int visible=Math.max(240,imageWidth-36);
        return Math.max(0,virtualContentWidth()-visible);
    }
    private boolean inViewport(Button b){
        return b.getY()>=topPos+CONTENT_TOP
                &&b.getY()<=topPos+imageHeight-CONTENT_BOTTOM_MARGIN-b.getHeight()
                &&b.getX()+b.getWidth()>=leftPos+18
                &&b.getX()<=leftPos+imageWidth-18;
    }

    private void syncVisibility(){
        int n=parameterCount();
        for(int i=0;i<controls.size();i++){
            int row=i/2; Button b=controls.get(i);
            int virtualY=CONTENT_TOP+50+row*56;
            b.setX(((i%2==0)?leftPos+imageWidth-164:leftPos+imageWidth-78)-horizontalOffset);
            b.setY(topPos+virtualY-scrollOffset);
            b.visible=tab==Tab.PARAMETERS&&row<n&&inViewport(b);
        }
        int routeWidth=routeButtonWidth(), routeGap=8, routeX=routeButtonStartX();
        for(int i=0;i<routeControls.size();i++){
            Button b=routeControls.get(i);
            b.setX(routeX+i*(routeWidth+routeGap)-horizontalOffset);
            b.setY(topPos+CONTENT_TOP+112-scrollOffset);
            boolean input=i<2;
            boolean routable=input?menu.canRouteInput():menu.canRouteOutput();
            b.visible=tab==Tab.ROUTING&&routable&&inViewport(b);
        }
        if(toggle!=null){
            toggle.setX(leftPos+imageWidth-184-horizontalOffset);
            toggle.setY(topPos+CONTENT_TOP+224-scrollOffset);
            toggle.visible=tab==Tab.PARAMETERS&&menu.kind()==AdvancedParameterMenu.KIND_PULSE_SHAPER&&inViewport(toggle);
        }
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
        g.fill(leftPos+18,topPos+66,leftPos+imageWidth-18,topPos+67,RULE);
        g.fill(leftPos+18,topPos+imageHeight-CONTENT_BOTTOM_MARGIN,leftPos+imageWidth-18,topPos+imageHeight-CONTENT_BOTTOM_MARGIN+1,RULE);
    }

    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,18,12,INK,false);
        String live="SERVER PHYSICS"; g.drawString(font,live,imageWidth-18-font.width(live),12,GOOD,false);
        g.drawString(font,tab.label.toUpperCase(),24,72,ACCENT,false);
        g.enableScissor(leftPos+18,topPos+CONTENT_TOP,leftPos+imageWidth-18,topPos+imageHeight-CONTENT_BOTTOM_MARGIN);
        g.pose().pushPose(); g.pose().translate(-horizontalOffset,-scrollOffset,0);
        switch(tab){case OPERATE->operate(g);case PARAMETERS->parameters(g);case MODEL->model(g);case ROUTING->routing(g);case DIAGNOSTICS->diagnostics(g);}
        g.pose().popPose(); g.disableScissor();
        if(maxScroll()>0||maxHorizontalScroll()>0){
            String raw="SCROLL Y "+scrollOffset+"/"+maxScroll()+" • X "+horizontalOffset+"/"+maxHorizontalScroll()+" • Shift+wheel / trackpad";
            String compact=fit(raw,Math.max(170,imageWidth-220));
            g.drawString(font,compact,imageWidth-24-font.width(compact),72,MUTED,false);
        }
        g.drawString(font,fit(footer(),imageWidth-36),18,imageHeight-20,MUTED,false);
    }

    private void parameters(GuiGraphics g){
        String[] labels=parameterLabels(); int[] values={menu.p0(),menu.p1(),menu.p2()};
        for(int i=0;i<labels.length;i++){
            int y=CONTENT_TOP+54+i*56;
            g.drawString(font,labels[i],42,y,MUTED,false);
            g.drawString(font,paramValue(i,values[i]),Math.min(280,imageWidth/2),y,INK,false);
        }
        if(menu.kind()==AdvancedParameterMenu.KIND_PULSE_SHAPER){
            int y=CONTENT_TOP+226;
            g.drawString(font,"Retriggerable",42,y,MUTED,false);
            g.drawString(font,menu.p3()!=0?"YES":"NO",Math.min(280,imageWidth/2),y,INK,false);
        }
    }

    private void operate(GuiGraphics g){
        String[] labels=liveLabels();
        int[] values={menu.liveA(),menu.liveB(),menu.liveC(),menu.liveD(),menu.liveE(),menu.liveF()};
        for(int i=0;i<labels.length;i++) pair(g,labels[i],liveValue(i,values[i]),CONTENT_TOP+38+i*42);
    }

    private void model(GuiGraphics g){
        int w=Math.max(280,imageWidth-96);
        g.drawString(font,"ENGINEERING MODEL",42,CONTENT_TOP+28,MUTED,false);
        int y=CONTENT_TOP+66;
        y=drawWrapped(g,model1(),42,y,w,INK)+18;
        y=drawWrapped(g,model2(),42,y,w,INK)+22;
        y=drawWrapped(g,model3(),42,y,w,MUTED)+22;
        y=drawWrapped(g,"Only configuration variables are editable; measured state, thermal load, evidence and topology stay solver/world-owned.",42,y,w,MUTED)+22;
        drawWrapped(g,"Additional equations, assumptions, response diagnostics and validation notes extend vertically. Scroll instead of compressing or truncating them.",42,y,w,MUTED);
    }

    private void routing(GuiGraphics g){
        int w=Math.max(280,imageWidth-96);
        g.drawString(font,"PHYSICAL ROUTING",42,CONTENT_TOP+28,MUTED,false);
        pair(g,"Input / sensing endpoint",menu.hasInputEndpoint()?menu.inputDirection().getName().toUpperCase():"NONE",CONTENT_TOP+62);
        pair(g,"Output endpoint",menu.hasOutputEndpoint()?menu.outputDirection().getName().toUpperCase():"NONE",CONTENT_TOP+96);
        int y=CONTENT_TOP+170;
        y=drawWrapped(g,routingContract(),42,y,w,INK)+18;
        y=drawWrapped(g,"Route controls mutate only declared server-owned endpoint properties. They do not rotate measurements in the client or manufacture connectivity.",
                42,y,w,MUTED)+18;
        drawWrapped(g,"After rerouting, Operate and Diagnostics remain the authority for live value and PortQuality evidence.",42,y,w,MUTED);
    }

    private void diagnostics(GuiGraphics g){
        int w=Math.max(280,imageWidth-96);
        g.drawString(font,"EVIDENCE DIAGNOSTICS",42,CONTENT_TOP+28,MUTED,false);
        g.drawString(font,diagnosticStatus(),42,CONTENT_TOP+58,diagnosticColor(),false);
        int y=CONTENT_TOP+96;
        for(String line:diagnosticLines()){
            y=drawWrapped(g,line,42,y,w,INK)+18;
        }
        y=drawWrapped(g,"Diagnostics use synchronized server evidence only. The client does not rescan the world, generate a new sample, or run a second device solver.",
                42,y,w,MUTED)+18;
        drawWrapped(g,diagnosticNextAction(),42,y,w,diagnosticColor());
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
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> slot==0
                    ? v+"/100 • allowed "+LapisNoiseSourceLogic.MIN_BASELINE+".."+LapisNoiseSourceLogic.MAX_BASELINE
                    : slot==1
                    ? "±"+v+"/100 • allowed ±"+LapisNoiseSourceLogic.MIN_NOISE_AMPLITUDE+".."+LapisNoiseSourceLogic.MAX_NOISE_AMPLITUDE
                    : v+" ticks • allowed "+LapisNoiseSourceLogic.MIN_SAMPLE_PERIOD_TICKS+".."+LapisNoiseSourceLogic.MAX_SAMPLE_PERIOD_TICKS;
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
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> new String[]{"Input","Output","Clip episodes","Max raw","Input quality","Output quality"};
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> new String[]{"Copper voltage","Actual field","Target field","Thermal load"};
            case AdvancedParameterMenu.KIND_INDUCTION_COIL -> new String[]{"Magnetic field","Induced output","Output quality"};
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> new String[]{"Network pressure","Reseat pressure","Vent events","Last excess"};
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> new String[]{"Current sample","Initialized","Output quality"};
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> new String[]{"Measured distance","Configured range","Coverage complete","Measurement quality"};
            case AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> new String[]{"Emission intensity","Channel"};
            default -> new String[]{"Live"};
        };
    }

    private String liveValue(int i,int v){
        if(menu.kind()==AdvancedParameterMenu.KIND_PRECISION_FILTER&&i==3) return v<0?"UNAVAILABLE":v+" ticks";
        if(menu.kind()==AdvancedParameterMenu.KIND_EDGE_DETECTOR&&i==3) return v<0?"NONE":v+" ticks";
        if(menu.kind()==AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER&&(i==4||i==5)) return qualityName(v);
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_NOISE&&i==1) return v!=0?"YES":"NO";
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_NOISE&&i==2) return qualityName(v);
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_RANGE&&i==0){
            PortQuality q=quality(menu.liveD());
            if(q==PortQuality.NO_SIGNAL) return "NO TARGET";
            if(q==PortQuality.STALE) return "UNKNOWN";
            return v+" blocks";
        }
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_RANGE&&i==1) return v+" blocks";
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_RANGE&&i==2) return v!=0?"YES":"NO";
        if(menu.kind()==AdvancedParameterMenu.KIND_LAPIS_RANGE&&i==3) return qualityName(v);
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
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> "delta = deterministic mix(gameTime XOR blockPos seed) mapped into [−A,+A]; sample = clamp(baseline + delta, 0,100).";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> "Scan i=1..R along the physical sensing aperture; first non-air/fluid cell is distance d. Output = round(clamp(d,0,R) × 100 / R).";
            case AdvancedParameterMenu.KIND_OPTICAL_EMITTER -> "Emitter launches selected intensity on one discrete optical channel.";
            default -> "";
        };
    }

    private String model2(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_PULSE_SHAPER -> "Rearm threshold = trigger threshold − hysteresis.";
            case AdvancedParameterMenu.KIND_ELECTROMAGNET -> "H[k+1] = clamp(H + heating(V) − cooling, 0,1000); cooling is an operator design parameter.";
            case AdvancedParameterMenu.KIND_RELIEF_VALVE -> "Setpoint and blowdown are independent safety parameters; blowdown prevents rapid open/close chatter.";
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> "Clipping is explicit SATURATED output quality; it does not rewrite a valid input into missing evidence.";
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> "Baseline, amplitude and cadence are independent experiment variables. Exact server ranges are "
                    +LapisNoiseSourceLogic.MIN_BASELINE+".."+LapisNoiseSourceLogic.MAX_BASELINE+", ±"
                    +LapisNoiseSourceLogic.MIN_NOISE_AMPLITUDE+".."+LapisNoiseSourceLogic.MAX_NOISE_AMPLITUDE
                    +", and "+LapisNoiseSourceLogic.MIN_SAMPLE_PERIOD_TICKS+".."+LapisNoiseSourceLogic.MAX_SAMPLE_PERIOD_TICKS
                    +" ticks. Legacy quick presets map into these same exact parameters; a generated sample of 0 remains VALID evidence.";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> "If any scanned cell lies in an unavailable chunk, coverage stops and quality is STALE. Complete clear coverage with no target is NO_SIGNAL; only a found target is VALID.";
            default -> "Configuration affects the authoritative device solver, not a client-only visualization.";
        };
    }

    private String model3(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> "Operate separates input quality from output quality, so headroom saturation stays visible even when the numerical output is clamped to 15.";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> "Raw distance and measurement quality are synchronized separately; the UI never converts NO_SIGNAL or STALE into a fabricated distance.";
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> "Rotating the LAPIS output changes topology only; it never advances, resets or fabricates the deterministic sample. Legacy BlockState presets synchronize the same exact server parameter authority.";
            default -> "All values shown on Operate are synchronized evidence from the real Minecraft world state.";
        };
    }

    private String diagnosticStatus(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> {
                PortQuality in=quality(menu.liveE()), out=quality(menu.liveF());
                if(out==PortQuality.SATURATED) yield "OUTPUT SATURATED • HEADROOM LIMIT";
                if(in!=PortQuality.VALID) yield "INPUT EVIDENCE • "+qualityName(menu.liveE());
                yield "AMPLIFIER EVIDENCE COHERENT";
            }
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> menu.liveB()==0
                    ?"NOISE SOURCE INITIALIZING"
                    :"NOISE SOURCE ACTIVE • VALID OUTPUT";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> {
                PortQuality q=quality(menu.liveD());
                if(q==PortQuality.STALE) yield "SCAN COVERAGE INCOMPLETE";
                if(q==PortQuality.NO_SIGNAL) yield "COMPLETE SCAN • NO TARGET";
                if(q==PortQuality.VALID) yield "TARGET MEASURED";
                yield "RANGE EVIDENCE • "+q.name().replace('_',' ');
            }
            default -> "COMPATIBILITY DEVICE • USE DEVICE-SPECIFIC HMI WHEN AVAILABLE";
        };
    }

    private int diagnosticColor(){
        String s=diagnosticStatus();
        if(s.contains("FAULT")||s.contains("TOPOLOGY")) return BAD;
        if(s.contains("SATURATED")||s.contains("STALE")||s.contains("INCOMPLETE")||s.contains("INITIALIZING")) return WARN;
        return GOOD;
    }

    private String[] diagnosticLines(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> new String[]{
                    "Gain = ×"+menu.p0()+" • input/output = "+menu.liveA()+" / "+menu.liveB()+".",
                    "Input/output evidence = "+qualityName(menu.liveE())+" / "+qualityName(menu.liveF())+".",
                    "Clipping episodes = "+menu.liveC()+" • maximum retained raw output = "+menu.liveD()+".",
                    "A numerical output of 15 is not by itself a fault; SATURATED quality is the explicit headroom evidence."
            };
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> new String[]{
                    "Baseline / amplitude / sample period = "+menu.p0()+" / ±"+menu.p1()+" / "+menu.p2()+" ticks.",
                    "Current server sample = "+menu.liveA()+" • initialized = "+(menu.liveB()!=0?"YES":"NO")+".",
                    "Output evidence = "+qualityName(menu.liveC())+". A generated sample of 0 remains a valid numerical result.",
                    "Opening this page never advances the deterministic noise sequence or fabricates a replacement sample."
            };
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> new String[]{
                    "Configured maximum range = "+menu.p0()+" blocks • synchronized scan range = "+menu.liveB()+" blocks.",
                    "Measurement = "+rangeMeasurementLabel()+" • coverage complete = "+(menu.liveC()!=0?"YES":"NO")+".",
                    "Measurement evidence = "+qualityName(menu.liveD())+".",
                    "NO SIGNAL means a complete clear scan with no target; STALE means the requested coverage was not fully available."
            };
            default -> new String[]{
                    "This parameter menu remains registered for compatibility, but normal gameplay dispatch uses a richer device-specific HMI for this kind.",
                    "No additional client-side diagnosis is invented here; use Operate/Model or the dedicated HMI for authoritative evidence."
            };
        };
    }

    private String rangeMeasurementLabel(){
        PortQuality q=quality(menu.liveD());
        if(q==PortQuality.NO_SIGNAL) return "NO TARGET";
        if(q==PortQuality.STALE) return "UNKNOWN • INCOMPLETE COVERAGE";
        return menu.liveA()+" blocks";
    }

    private String diagnosticNextAction(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> {
                PortQuality in=quality(menu.liveE()), out=quality(menu.liveF());
                if(in!=PortQuality.VALID) yield "NEXT • restore trustworthy upstream Redstone evidence before changing gain.";
                if(out==PortQuality.SATURATED) yield "NEXT • decide whether clipping is intended; if not, reduce gain or upstream level and compare retained clip episodes.";
                yield "NEXT • compare input × gain against output and retained max-raw evidence under the same stimulus.";
            }
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> menu.liveB()==0
                    ?"NEXT • allow the authoritative source to produce its first sample before judging amplitude or cadence."
                    :"NEXT • change one experiment variable at a time; do not treat a valid zero sample as missing evidence.";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> {
                PortQuality q=quality(menu.liveD());
                if(q==PortQuality.STALE) yield "NEXT • restore complete world/chunk coverage before interpreting distance; increasing range does not repair missing evidence.";
                if(q==PortQuality.NO_SIGNAL) yield "NEXT • treat this as a real clear-scan result; change range only if the experiment intentionally needs a wider search volume.";
                yield "NEXT • compare measured distance against the same configured range before changing sensor reach.";
            }
            default -> "NEXT • use the dedicated HMI for device-specific response, routing and evidence diagnostics.";
        };
    }

    private String routingContract(){
        return switch(menu.kind()){
            case AdvancedParameterMenu.KIND_SIGNAL_AMPLIFIER -> "REDSTONE analog input RX → gain model → REDSTONE output TX. RX and TX are independently routable physical endpoints.";
            case AdvancedParameterMenu.KIND_LAPIS_NOISE -> "Output-only LAPIS precision source. TX may rotate; there is no synthetic input endpoint.";
            case AdvancedParameterMenu.KIND_LAPIS_RANGE -> "RX is the physical measurement aperture used by the server range model; TX is the normalized LAPIS output. The sensing aperture is not a wired source input.";
            default -> "Compatibility kind: normal gameplay dispatch uses its dedicated HMI for physical routing and device-specific evidence.";
        };
    }

    private static PortQuality quality(int ordinal){
        PortQuality[] all=PortQuality.values();
        return ordinal<0||ordinal>=all.length?PortQuality.NO_SIGNAL:all[ordinal];
    }

    private static String qualityName(int ordinal){
        return quality(ordinal).name().replace('_',' ');
    }

    private String footer(){ return "Engineering Notebook • precise parameters where physics supports them • discrete variables remain discrete"; }

    private int drawWrapped(GuiGraphics g,String text,int x,int y,int width,int color){
        for(var line:font.split(Component.literal(text),width)){
            g.drawString(font,line,x,y,color,false);
            y+=14;
        }
        return y;
    }

    private void pair(GuiGraphics g,String label,String value,int y){
        g.drawString(font,label,42,y,MUTED,false);
        int x=Math.min(300,imageWidth/2);
        g.drawString(font,value,x,y,INK,false);
    }
    private String fit(String s,int width){ if(font.width(s)<=width)return s; String x=s; while(x.length()>1&&font.width(x+"…")>width)x=x.substring(0,x.length()-1); return x+"…"; }
}
