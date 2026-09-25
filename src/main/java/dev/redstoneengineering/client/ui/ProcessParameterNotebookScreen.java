package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.signal.CopperCapacitorLogic;
import dev.redstoneengineering.signal.CopperFuseLogic;
import dev.redstoneengineering.signal.MechanicalExciterLogic;
import dev.redstoneengineering.ui.menu.ProcessParameterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Full-page notebook for process/control parameter batch three. */
public final class ProcessParameterNotebookScreen extends AbstractContainerScreen<ProcessParameterMenu> {
    private static final int BG=0xFFF2E9D8, PAGE=0xFFFFF8E8, INK=0xFF2C2925, MUTED=0xFF6E675E, RULE=0xFFB9A98F, ACCENT=0xFF4F5F7B, GOOD=0xFF2F7D4A, WARN=0xFF9A6A19, BAD=0xFFA43838;
    private enum Tab { OPERATE("Operate"), PARAMETERS("Parameters"), MODEL("Model"), ROUTING("Routing"), DIAGNOSTICS("Diagnostics"); final String label; Tab(String s){label=s;} }
    private Tab tab=Tab.PARAMETERS;
    private final List<Button> controls=new ArrayList<>();
    private final List<Button> routeControls=new ArrayList<>();
    private Button action;
    private int scrollOffset = 0;
    private int horizontalOffset = 0;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 84;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    public ProcessParameterNotebookScreen(ProcessParameterMenu menu, Inventory inventory, Component title){
        super(menu,inventory,title); imageWidth=520; imageHeight=300; titleLabelX=18; titleLabelY=12; inventoryLabelY=1000;
    }

    @Override protected void init(){
        imageWidth=Math.max(360,width-VIEW_MARGIN*2);
        imageHeight=Math.max(240,height-VIEW_MARGIN*2);
        super.init(); controls.clear(); routeControls.clear(); scrollOffset=0; horizontalOffset=0;

        int tabCount=Tab.values().length;
        int gap=imageWidth<460?4:7;
        int tabWidth=Math.max(48,(imageWidth-48-gap*(tabCount-1))/tabCount);
        int x=leftPos+24;
        for(Tab t:Tab.values()){
            addRenderableWidget(Button.builder(Component.literal(tabLabel(t)),b->{tab=t;scrollOffset=0;horizontalOffset=0;syncVisibility();})
                    .bounds(x,topPos+38,tabWidth,22).build());
            x+=tabWidth+gap;
        }

        addRow(0,CONTENT_TOP+44); addRow(1,CONTENT_TOP+96); addRow(2,CONTENT_TOP+148); addRow(3,CONTENT_TOP+200);
        action=addRenderableWidget(Button.builder(Component.literal("Action"),b->send(ProcessParameterMenu.BUTTON_ACTION))
                .bounds(leftPos+imageWidth-188,topPos+CONTENT_TOP+250,150,22).build());

        int routeWidth=routeButtonWidth(), routeGap=8, routeX=routeButtonStartX(), routeY=topPos+CONTENT_TOP+112;
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("RX ◀"),b->send(ProcessParameterMenu.BUTTON_INPUT_PREVIOUS))
                .bounds(routeX,routeY,routeWidth,22).build()));
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("RX ▶"),b->send(ProcessParameterMenu.BUTTON_INPUT_NEXT))
                .bounds(routeX+routeWidth+routeGap,routeY,routeWidth,22).build()));
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("TX ◀"),b->send(ProcessParameterMenu.BUTTON_OUTPUT_PREVIOUS))
                .bounds(routeX+(routeWidth+routeGap)*2,routeY,routeWidth,22).build()));
        routeControls.add(addRenderableWidget(Button.builder(Component.literal("TX ▶"),b->send(ProcessParameterMenu.BUTTON_OUTPUT_NEXT))
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
        int minus=switch(row){
            case 0->ProcessParameterMenu.BUTTON_P0_MINUS;
            case 1->ProcessParameterMenu.BUTTON_P1_MINUS;
            case 2->ProcessParameterMenu.BUTTON_P2_MINUS;
            default->ProcessParameterMenu.BUTTON_P3_MINUS;
        };
        int plus=switch(row){
            case 0->ProcessParameterMenu.BUTTON_P0_PLUS;
            case 1->ProcessParameterMenu.BUTTON_P1_PLUS;
            case 2->ProcessParameterMenu.BUTTON_P2_PLUS;
            default->ProcessParameterMenu.BUTTON_P3_PLUS;
        };
        int y=topPos+virtualY-scrollOffset;
        controls.add(addRenderableWidget(Button.builder(Component.literal("−"),b->send(minus))
                .bounds(leftPos+imageWidth-164,y,42,22).build()));
        controls.add(addRenderableWidget(Button.builder(Component.literal("+"),b->send(plus))
                .bounds(leftPos+imageWidth-78,y,42,22).build()));
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
                int max=maxScroll();
                scrollOffset=Math.max(0,Math.min(max,scrollOffset-(int)Math.round(scrollY*24.0)));
            }
            syncVisibility();
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }

    private int contentHeight(){
        return switch(tab){
            case OPERATE -> 420;
            case PARAMETERS -> 470;
            case MODEL -> 760;
            case ROUTING -> 520;
            case DIAGNOSTICS -> 650;
        };
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

    private int parameterCount(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER,
                 ProcessParameterMenu.KIND_PWM,
                 ProcessParameterMenu.KIND_COPPER_DRIVER,
                 ProcessParameterMenu.KIND_CAPACITOR,
                 ProcessParameterMenu.KIND_FUSE,
                 ProcessParameterMenu.KIND_COMPRESSOR -> 2;
            case ProcessParameterMenu.KIND_EXCITER -> 4;
            default -> 1;
        };
    }

    private void syncVisibility(){
        int n=parameterCount();
        for(int i=0;i<controls.size();i++){
            int row=i/2;
            Button b=controls.get(i);
            int virtualY=CONTENT_TOP+44+row*52;
            b.setX(((i%2)==0?leftPos+imageWidth-164:leftPos+imageWidth-78)-horizontalOffset);
            b.setY(topPos+virtualY-scrollOffset);
            b.visible=tab==Tab.PARAMETERS&&row<n&&inViewport(b);
        }
        int routeWidth=routeButtonWidth(), routeGap=8, routeX=routeButtonStartX();
        boolean rigidSeries=menu.rigidSeriesRoute();
        for(int i=0;i<routeControls.size();i++){
            Button b=routeControls.get(i);
            if(rigidSeries && i<2){
                int rigidX=leftPos+Math.max(24,(imageWidth-(routeWidth*2+routeGap))/2);
                b.setX(rigidX+i*(routeWidth+routeGap)-horizontalOffset);
                b.setMessage(Component.literal(i==0?"Rotate block ◀":"Rotate block ▶"));
            }else{
                b.setX(routeX+i*(routeWidth+routeGap)-horizontalOffset);
                b.setMessage(Component.literal(switch(i){
                    case 0->"RX ◀"; case 1->"RX ▶"; case 2->"TX ◀"; default->"TX ▶";
                }));
            }
            b.setY(topPos+CONTENT_TOP+112-scrollOffset);
            boolean input=i<2;
            boolean routable=input?menu.canRouteInput():menu.canRouteOutput();
            if(rigidSeries) routable=i<2;
            b.visible=tab==Tab.ROUTING&&routable&&inViewport(b);
        }
        if(action!=null){
            action.setX(leftPos+imageWidth-188-horizontalOffset);
            action.setY(topPos+CONTENT_TOP+250-scrollOffset);
            boolean hasAction=menu.kind()==ProcessParameterMenu.KIND_PWM||menu.kind()==ProcessParameterMenu.KIND_FUSE;
            action.visible=tab==Tab.PARAMETERS&&hasAction&&inViewport(action);
            action.setMessage(Component.literal(menu.kind()==ProcessParameterMenu.KIND_FUSE?"Attempt reset":"Toggle invert"));
        }
    }

    @Override public void render(GuiGraphics g,int mx,int my,float pt){ renderBackground(g,mx,my,pt); super.render(g,mx,my,pt); renderTooltip(g,mx,my); }

    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,BG);
        g.fill(leftPos+5,topPos+5,leftPos+imageWidth-5,topPos+imageHeight-5,PAGE);
        g.fill(leftPos+18,topPos+29,leftPos+imageWidth-18,topPos+30,RULE);
        g.fill(leftPos+18,topPos+66,leftPos+imageWidth-18,topPos+67,RULE);
        g.fill(leftPos+18,topPos+imageHeight-CONTENT_BOTTOM_MARGIN,leftPos+imageWidth-18,
                topPos+imageHeight-CONTENT_BOTTOM_MARGIN+1,RULE);
    }

    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,title,18,12,INK,false);
        String live="SERVER ENGINEERING MODEL";
        g.drawString(font,live,imageWidth-18-font.width(live),12,GOOD,false);
        g.drawString(font,tab.label.toUpperCase(),24,72,ACCENT,false);

        g.enableScissor(leftPos+18,topPos+CONTENT_TOP,leftPos+imageWidth-18,
                topPos+imageHeight-CONTENT_BOTTOM_MARGIN);
        g.pose().pushPose();
        g.pose().translate(-horizontalOffset,-scrollOffset,0);
        switch(tab){case OPERATE->operate(g);case PARAMETERS->parameters(g);case MODEL->model(g);case ROUTING->routing(g);case DIAGNOSTICS->diagnostics(g);}
        g.pose().popPose();
        g.disableScissor();

        if(maxScroll()>0||maxHorizontalScroll()>0){
            String scroll="SCROLL Y "+scrollOffset+"/"+maxScroll()+" • X "+horizontalOffset+"/"+maxHorizontalScroll()+" • Shift+wheel / trackpad";
            String compact=fit(scroll,Math.max(170,imageWidth-220));
            g.drawString(font,compact,imageWidth-24-font.width(compact),72,MUTED,false);
        }
        g.drawString(font,fit(footer(),imageWidth-36),18,imageHeight-20,MUTED,false);
    }

    private void parameters(GuiGraphics g){
        String[] labels=parameterLabels(); int[] vals={menu.p0(),menu.p1(),menu.p2(),menu.p3()};
        for(int i=0;i<labels.length;i++){
            int y=CONTENT_TOP+48+i*52;
            g.drawString(font,labels[i],42,y,MUTED,false);
            g.drawString(font,paramValue(i,vals[i]),Math.min(260,imageWidth/2),y,INK,false);
        }
    }

    private void operate(GuiGraphics g){
        String[] labels=liveLabels(); int[] vals={menu.liveA(),menu.liveB(),menu.liveC(),menu.liveD(),menu.liveE(),menu.liveF(),menu.liveG()};
        for(int i=0;i<labels.length;i++) pair(g,labels[i],liveValue(i,vals[i]),CONTENT_TOP+34+i*40);
    }

    private void model(GuiGraphics g){
        int textWidth=Math.max(280,imageWidth-96);
        g.drawString(font,"DEVICE MODEL",42,CONTENT_TOP+28,MUTED,false);
        int y=CONTENT_TOP+62;
        y=drawWrapped(g,model1(),42,y,textWidth,INK)+18;
        y=drawWrapped(g,model2(),42,y,textWidth,INK)+22;
        y=drawWrapped(g,model3(),42,y,textWidth,MUTED)+22;
        y=drawWrapped(g,"Measured state, stored energy, trip exposure, network evidence and topology stay world/solver-owned.",
                42,y,textWidth,MUTED)+22;
        drawWrapped(g,"This page grows vertically as equations, assumptions, response metrics and diagnostic evidence are added. Scroll instead of shrinking or truncating the engineering model.",
                42,y,textWidth,MUTED);
    }

    private void routing(GuiGraphics g){
        int textWidth=Math.max(280,imageWidth-96);
        g.drawString(font,"PHYSICAL ROUTING",42,CONTENT_TOP+28,MUTED,false);
        pair(g,"Input endpoint",menu.hasInputEndpoint()?menu.inputDirection().getName().toUpperCase():"NONE / FIXED",CONTENT_TOP+62);
        pair(g,"Output endpoint",menu.hasOutputEndpoint()?menu.outputDirection().getName().toUpperCase():"MULTI-FACE / FIXED",CONTENT_TOP+96);
        int y=CONTENT_TOP+170;
        y=drawWrapped(g,routingContract(),42,y,textWidth,INK)+18;
        if(menu.canRouteInput()||menu.canRouteOutput()){
            y=drawWrapped(g,"RX/TX buttons mutate only declared server-owned physical endpoint properties. Every candidate route is checked by the block routing model; the client does not invent connectivity.",
                    42,y,textWidth,MUTED)+18;
        }else{
            y=drawWrapped(g,"This device has no legal generic RX/TX rotation in this notebook. Its fixed or multi-face port contract is shown rather than replaced with fake direction controls.",
                    42,y,textWidth,MUTED)+18;
        }
        drawWrapped(g,"Topology and evidence remain world-owned after rerouting; use Operate/Diagnostics to validate the new physical path.",42,y,textWidth,MUTED);
    }

    private void diagnostics(GuiGraphics g){
        int textWidth=Math.max(280,imageWidth-96);
        g.drawString(font,"SERVER-BACKED DIAGNOSTICS",42,CONTENT_TOP+28,MUTED,false);
        g.drawString(font,diagnosticStatus(),42,CONTENT_TOP+58,diagnosticColor(),false);
        int y=CONTENT_TOP+96;
        for(String line:diagnosticLines()){
            y=drawWrapped(g,line,42,y,textWidth,INK)+18;
        }
        y=drawWrapped(g,"Diagnostic text interprets synchronized device evidence only. It does not run a second solver or repair missing topology/evidence on the client.",
                42,y,textWidth,MUTED)+18;
        drawWrapped(g,diagnosticNextAction(),42,y,textWidth,diagnosticColor());
    }

    private String[] parameterLabels(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> new String[]{"Transfer mode","Mode parameter"};
            case ProcessParameterMenu.KIND_PWM -> new String[]{"Carrier period","Invert"};
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> new String[]{"Rise slew limit","Fall slew limit"};
            case ProcessParameterMenu.KIND_CAPACITOR -> new String[]{"Base time constant","Open-circuit leakage factor"};
            case ProcessParameterMenu.KIND_FUSE -> new String[]{"Current rating","Time-current class"};
            case ProcessParameterMenu.KIND_COMPRESSOR -> new String[]{"Ramp-up rate","Ramp-down rate"};
            case ProcessParameterMenu.KIND_DAMPER -> new String[]{"Amplitude attenuation"};
            case ProcessParameterMenu.KIND_EXCITER -> new String[]{"Target frequency","Amplitude rise","Amplitude fall","Frequency slew"};
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> new String[]{"Precision value"};
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> new String[]{"Voltage level"};
            default -> new String[]{"Parameter"};
        };
    }

    private String paramValue(int slot,int v){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> slot==0?conditionerMode(v):Integer.toString(v);
            case ProcessParameterMenu.KIND_PWM -> slot==0?v+" ticks":(v!=0?"INVERTED":"NORMAL");
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> v+" V-level/tick";
            case ProcessParameterMenu.KIND_CAPACITOR -> slot==0
                    ? v+" ticks • allowed "+CopperCapacitorLogic.MIN_BASE_TAU+".."+CopperCapacitorLogic.MAX_BASE_TAU
                    : "×"+v+" • allowed ×"+CopperCapacitorLogic.MIN_LEAKAGE_FACTOR+"..×"+CopperCapacitorLogic.MAX_LEAKAGE_FACTOR;
            case ProcessParameterMenu.KIND_FUSE -> slot==0
                    ? v+" current units • allowed "+CopperFuseLogic.MIN_RATING+".."+CopperFuseLogic.MAX_RATING
                    : fuseClass(v)+" • class "+CopperFuseLogic.MIN_TIME_CURRENT_CLASS+".."+CopperFuseLogic.MAX_TIME_CURRENT_CLASS;
            case ProcessParameterMenu.KIND_COMPRESSOR -> v+" pressure/tick";
            case ProcessParameterMenu.KIND_DAMPER -> v+" amplitude/step";
            case ProcessParameterMenu.KIND_EXCITER -> slot==0
                    ? v+" • allowed "+MechanicalExciterLogic.MIN_CONFIGURED_FREQUENCY+".."+MechanicalExciterLogic.MAX_CONFIGURED_FREQUENCY
                    : v+" units/tick • allowed "+MechanicalExciterLogic.MIN_RATE+".."+MechanicalExciterLogic.MAX_RATE;
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> String.format("%.2f",v/100.0);
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> v+"/15";
            default -> Integer.toString(v);
        };
    }

    private String[] liveLabels(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> new String[]{"Input","Output","Limit episodes","Limiting now","Input quality","Output quality","Last limit age"};
            case ProcessParameterMenu.KIND_PWM -> new String[]{"Command","Applied command","Effective duty","Completed cycles","Command quality","Inhibit quality","Output quality"};
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> new String[]{"Target voltage","Internal actual voltage","Input quality","Tracking error","Copper source present"};
            case ProcessParameterMenu.KIND_CAPACITOR -> new String[]{"Stored charge","Output voltage","Effective tau","Observed load R","Load scan truncated","Input quality","Output quality"};
            case ProcessParameterMenu.KIND_FUSE -> new String[]{"Thermal exposure","Trip progress","Trip state","Last current ×100","Current / rating","Input quality","Output quality"};
            case ProcessParameterMenu.KIND_COMPRESSOR -> new String[]{"Target pressure","Actual pressure","Tracking error","Start count"};
            case ProcessParameterMenu.KIND_DAMPER -> new String[]{"Wave amplitude","Wave frequency","Valid wave","Envelope quality","Envelope age"};
            case ProcessParameterMenu.KIND_EXCITER -> new String[]{"Target amplitude","Actual amplitude","Actual frequency","Start count","Run ticks","Input quality","Output quality"};
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> new String[]{"Output value"};
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> new String[]{"Output voltage","Output quality","Copper source present"};
            default -> new String[]{"Live"};
        };
    }

    private String liveValue(int i,int v){
        if(menu.kind()==ProcessParameterMenu.KIND_CONDITIONER){
            if(i==3) return v!=0?"YES":"NO";
            if(i==4||i==5) return qualityName(v);
            if(i==6) return v<0?"NONE":v+" ticks";
        }
        if(menu.kind()==ProcessParameterMenu.KIND_COPPER_DRIVER){
            if(i==2) return qualityName(v);
            if(i==4) return v!=0?"YES • ACTIVE DRIVER":"NO • RELEASED";
        }
        if(menu.kind()==ProcessParameterMenu.KIND_COPPER_SOURCE){
            if(i==1) return qualityName(v);
            if(i==2) return v!=0?"YES":"NO";
        }
        if(menu.kind()==ProcessParameterMenu.KIND_PWM){
            if(i==2) return String.format("%.1f%%",v/10.0);
            if(i==4||i==5||i==6) return qualityName(v);
        }
        if(menu.kind()==ProcessParameterMenu.KIND_CAPACITOR){
            if(i==0) return v+"%";
            if(i==2) return v+" ticks";
            if(i==3) return v<0?"OPEN":String.format("%.1f R-eq",v/10.0);
            if(i==4) return v!=0?"YES":"NO";
            if(i==5||i==6) return qualityName(v);
        }
        if(menu.kind()==ProcessParameterMenu.KIND_FUSE){
            if(i==1) return String.format("%.1f%%",v/10.0);
            if(i==2) return v!=0?"TRIPPED":"ARMED";
            if(i==4) return String.format("%.2f × Irated",v/1000.0);
            if(i==5||i==6) return qualityName(v);
        }
        if(menu.kind()==ProcessParameterMenu.KIND_DAMPER){
            if(i==2) return v!=0?"YES":"NO";
            if(i==3) return v+"%";
            if(i==4) return v<0?"NEVER WRITTEN":v+" ticks";
        }
        if(menu.kind()==ProcessParameterMenu.KIND_EXCITER){
            if(i==4) return v+" ticks";
            if(i==5||i==6) return qualityName(v);
        }
        return Integer.toString(v);
    }

    private String model1(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> "Mode-specific transfer function maps Redstone input to bounded 0..15 output.";
            case ProcessParameterMenu.KIND_PWM -> "onTicks = round((command / 15) × period); output is HIGH while carrier phase < onTicks.";
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> "V[k+1] = V[k] + clamp(Vtarget − V[k], −Sfall, +Srise).";
            case ProcessParameterMenu.KIND_CAPACITOR -> "q*=100·Vin/15; q[k+1] moves toward q* by max(1, |q*−q|/τ). Current τbase="+menu.p0()+" ticks.";
            case ProcessParameterMenu.KIND_FUSE -> "r=I/Irated; for r>1, ΔH=ceil((r²−1)·50·Kclass); Htrip="+CopperFuseLogic.TRIP_THRESHOLD+". Current Kclass="+String.format(java.util.Locale.ROOT,"%.2f",CopperFuseLogic.timeCurrentClassFactor(menu.p1()))+".";
            case ProcessParameterMenu.KIND_COMPRESSOR -> "Pressure target derives from Redstone command; actual pressure follows asymmetric ramp rates.";
            case ProcessParameterMenu.KIND_DAMPER -> "A[k+1] = max(0, A[k] − attenuation); each decay step preserves carrier frequency while reducing the local envelope.";
            case ProcessParameterMenu.KIND_EXCITER -> "A[k+1]=toward(Acmd, Rrise/Rfall); F[k+1]=toward(Ftarget, Fslew). Control cadence="+MechanicalExciterLogic.CONTROL_TICK_TICKS+" tick.";
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> "Configured 0..100 precision value is a valid Lapis-domain source, including exact zero.";
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> "Configured 0..15 voltage is a valid six-face Copper source.";
            default -> "";
        };
    }

    private String model2(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> "Input and output quality are separate. Active limiting marks output SATURATED while preserving the upstream input quality.";
            case ProcessParameterMenu.KIND_PWM -> "Partial-duty commands latch only at carrier-cycle boundaries; 0% and 100% endpoint commands apply immediately. Command, inhibit and output quality remain independent evidence channels.";
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> "Rise and fall slew are independent. Internal actual voltage can decay after command loss, but the Copper source is released immediately unless input evidence is VALID.";
            case ProcessParameterMenu.KIND_CAPACITOR -> "τcharge=τbase; loaded discharge uses bounded Rload; open circuit uses τopen=τbase×leakage="+menu.p0()+"×"+menu.p1()+"="+(menu.p0()*menu.p1())+" ticks. Incomplete load scans freeze integration.";
            case ProcessParameterMenu.KIND_FUSE -> "FAST/NORMAL/SLOW factors are 1.50/1.00/0.65. Rating/class changes retain thermal exposure; reset is allowed only under complete, safe electrical evidence.";
            case ProcessParameterMenu.KIND_DAMPER -> "Each surviving decay step reduces envelope quality by 20 and schedules the next decay after the fixed 4-tick packet TTL. Those are model assumptions, not editable parameters.";
            case ProcessParameterMenu.KIND_EXCITER -> "Full-scale amplitude ramp: rise≈"+MechanicalExciterLogic.fullScaleRampTicks(menu.p1())+" ticks, fall≈"+MechanicalExciterLogic.fullScaleRampTicks(menu.p2())+" ticks. Frequency slew is independently bounded; while coasting with A>0 the carrier never collapses to 0 before mechanical energy reaches rest.";
            default -> "The parameter changes the authoritative server model, not a client-only display.";
        };
    }

    private String model3(){
        if(menu.kind()==ProcessParameterMenu.KIND_CAPACITOR||menu.kind()==ProcessParameterMenu.KIND_FUSE)
            return "Operate separates retained physical state from input/output evidence quality, so stored energy or heat is never mistaken for fresh source evidence.";
        if(menu.kind()==ProcessParameterMenu.KIND_CONDITIONER)
            return "A clipped/limited numerical output is not missing evidence: SATURATED is an explicit output-quality state, and the last limiting episode age remains retained evidence.";
        if(menu.kind()==ProcessParameterMenu.KIND_PWM)
            return "PWM fail-safe behavior uses command and inhibit quality separately; a bad inhibit path can invalidate output evidence even when the command number itself looks ordinary.";
        if(menu.kind()==ProcessParameterMenu.KIND_COPPER_DRIVER)
            return "A valid 0 command is a real 0 V Copper source. NO_SIGNAL/STALE/FAULT are evidence states, not numerical zero; non-valid command evidence releases the network driver.";
        if(menu.kind()==ProcessParameterMenu.KIND_COPPER_SOURCE)
            return "The configured source is always a real Copper driver. 0 V is VALID/PRESENT electrical evidence, not an absent source.";
        if(menu.kind()==ProcessParameterMenu.KIND_DAMPER)
            return "A missing wave is distinct from a low-quality retained envelope; Operate shows amplitude, validity, quality percentage and freshness separately.";
        if(menu.kind()==ProcessParameterMenu.KIND_EXCITER)
            return "Actual amplitude may coast toward zero after command loss; input quality and output quality remain explicit evidence rather than being inferred from the numeric state.";
        return "Operate values are synchronized readback from the real device and connected world.";
    }
    private String diagnosticStatus(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> {
                String out=qualityName(menu.liveF());
                if(menu.liveD()!=0||"SATURATED".equals(out)) yield "LIMITING ACTIVE • OUTPUT SATURATED";
                if(!"VALID".equals(qualityName(menu.liveE()))) yield "INPUT EVIDENCE • "+qualityName(menu.liveE());
                yield "TRANSFER EVIDENCE COHERENT";
            }
            case ProcessParameterMenu.KIND_PWM -> {
                if(!"VALID".equals(qualityName(menu.liveG()))) yield "PWM OUTPUT EVIDENCE • "+qualityName(menu.liveG());
                if(menu.liveA()!=menu.liveB()) yield "COMMAND / APPLIED DIFFER • CHECK LATCH OR INHIBIT";
                yield "PWM EVIDENCE COHERENT";
            }
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> {
                String q=qualityName(menu.liveC());
                if(!"VALID".equals(q)) yield q+" INPUT • COPPER DRIVER "+(menu.liveE()!=0?"PRESENT":"RELEASED");
                if(menu.liveD()>0) yield "COPPER SLEW TRANSIENT";
                yield "COPPER TRACKING COMMAND";
            }
            case ProcessParameterMenu.KIND_CAPACITOR -> {
                if(menu.liveE()!=0) yield "LOAD EVIDENCE INCOMPLETE";
                String q=qualityName(menu.liveG());
                if(!"VALID".equals(q)) yield "CAPACITOR OUTPUT • "+q;
                yield "CAPACITOR EVIDENCE COHERENT";
            }
            case ProcessParameterMenu.KIND_FUSE -> {
                if(menu.liveC()!=0) yield "FUSE TRIPPED";
                if(menu.liveE()>1000) yield "OVERLOAD HEATING ACTIVE";
                String q=qualityName(menu.liveG());
                if(!"VALID".equals(q)) yield "FUSE OUTPUT • "+q;
                yield "FUSE ARMED";
            }
            case ProcessParameterMenu.KIND_COMPRESSOR -> menu.liveC()>0?"PRESSURE RESPONSE TRANSIENT":"PRESSURE TRACKING TARGET";
            case ProcessParameterMenu.KIND_DAMPER -> menu.liveC()==0?"NO ACTIVE MECHANICAL WAVE":menu.liveD()<100?"DEGRADED WAVE ENVELOPE":"WAVE EVIDENCE CURRENT";
            case ProcessParameterMenu.KIND_EXCITER -> {
                String in=qualityName(menu.liveF());
                if(!"VALID".equals(in)&&menu.liveB()>0) yield "COAST-DOWN • INPUT "+in;
                if(menu.liveA()==0&&menu.liveB()==0) yield "EXCITER IDLE";
                if(menu.liveA()!=menu.liveB()||menu.p0()!=menu.liveC()) yield "EXCITER RESPONSE TRANSIENT";
                yield "EXCITER TRACKING COMMAND";
            }
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> "VALID LAPIS PRECISION SOURCE";
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> "VALID COPPER VOLTAGE SOURCE";
            default -> "DEVICE EVIDENCE AVAILABLE";
        };
    }

    private int diagnosticColor(){
        String s=diagnosticStatus();
        if(s.contains("TRIPPED")||s.contains("FAULT")||s.contains("TOPOLOGY")) return BAD;
        if(s.contains("SATURATED")||s.contains("INCOMPLETE")||s.contains("TRANSIENT")
                ||s.contains("DEGRADED")||s.contains("NO ACTIVE")||s.contains("CHECK")
                ||s.contains("NO_SIGNAL")||s.contains("STALE")) return WARN;
        return GOOD;
    }

    private String[] diagnosticLines(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> new String[]{
                    "Transfer = "+conditionerMode(menu.p0())+" • parameter="+menu.p1()+" • input/output="+menu.liveA()+" / "+menu.liveB()+".",
                    "Evidence = "+qualityName(menu.liveE())+" input → "+qualityName(menu.liveF())+" output.",
                    "Limiting = "+(menu.liveD()!=0?"ACTIVE":"CLEAR")+" • episodes="+menu.liveC()+" • last limiting age="+(menu.liveG()<0?"NONE":menu.liveG()+" ticks")+"."
            };
            case ProcessParameterMenu.KIND_PWM -> new String[]{
                    "Command / applied = "+menu.liveA()+" / "+menu.liveB()+" • effective duty="+String.format("%.1f%%",menu.liveC()/10.0)+".",
                    "Evidence = command "+qualityName(menu.liveE())+" • inhibit "+qualityName(menu.liveF())+" • output "+qualityName(menu.liveG())+".",
                    "Completed carrier cycles = "+menu.liveD()+"; command/applied differences are retained state, not client interpolation."
            };
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> new String[]{
                    "Target / actual / error = "+menu.liveA()+" / "+menu.liveB()+" / "+menu.liveD()+" V-level.",
                    "Input evidence = "+qualityName(menu.liveC())+" • network driver = "+(menu.liveE()!=0?"PRESENT":"RELEASED")+".",
                    "Configured rise/fall slew = "+menu.p0()+" / "+menu.p1()+" V-level per tick."
            };
            case ProcessParameterMenu.KIND_CAPACITOR -> new String[]{
                    "Stored charge / output = "+menu.liveA()+"% / "+menu.liveB()+" • effective τ="+menu.liveC()+" ticks.",
                    "Input/output evidence = "+qualityName(menu.liveF())+" / "+qualityName(menu.liveG())+".",
                    "Observed load = "+(menu.liveD()<0?"OPEN":String.format("%.1f R-eq",menu.liveD()/10.0))+" • scan truncated="+(menu.liveE()!=0?"YES":"NO")+"."
            };
            case ProcessParameterMenu.KIND_FUSE -> new String[]{
                    "Thermal exposure / trip progress = "+menu.liveA()+" / "+String.format("%.1f%%",menu.liveB()/10.0)+".",
                    "Current ratio = "+String.format("%.2f × Irated",menu.liveE()/1000.0)+" • state="+(menu.liveC()!=0?"TRIPPED":"ARMED")+".",
                    "Input/output evidence = "+qualityName(menu.liveF())+" / "+qualityName(menu.liveG())+"."
            };
            case ProcessParameterMenu.KIND_COMPRESSOR -> new String[]{
                    "Target / actual / tracking error = "+menu.liveA()+" / "+menu.liveB()+" / "+menu.liveC()+".",
                    "Configured ramp-up / ramp-down = "+menu.p0()+" / "+menu.p1()+" pressure per tick.",
                    "Start count = "+menu.liveD()+"; this is retained runtime evidence from the server compressor model."
            };
            case ProcessParameterMenu.KIND_DAMPER -> new String[]{
                    "Wave amplitude / frequency = "+menu.liveA()+" / "+menu.liveB()+".",
                    "Envelope validity / quality = "+(menu.liveC()!=0?"VALID":"NO ACTIVE WAVE")+" / "+menu.liveD()+"%.",
                    "Envelope age = "+(menu.liveE()<0?"NEVER WRITTEN":menu.liveE()+" ticks")+" • attenuation="+menu.p0()+" amplitude per step."
            };
            case ProcessParameterMenu.KIND_EXCITER -> new String[]{
                    "Target / actual amplitude = "+menu.liveA()+" / "+menu.liveB()+" • actual frequency="+menu.liveC()+".",
                    "Configured target frequency="+menu.p0()+" • rise/fall/frequency slew="+menu.p1()+" / "+menu.p2()+" / "+menu.p3()+" • effective bounds="+MechanicalExciterLogic.MIN_RATE+".."+MechanicalExciterLogic.MAX_RATE+".",
                    "Input/output evidence = "+qualityName(menu.liveF())+" / "+qualityName(menu.liveG())+" • starts/run ticks="+menu.liveD()+" / "+menu.liveE()+"."
            };
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> new String[]{
                    "Configured output = "+String.format("%.2f",menu.liveA()/100.0)+" on the Lapis domain.",
                    "Exact numerical zero remains a valid source state; this device does not infer absence from value 0."
            };
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> new String[]{
                    "Configured output = "+menu.liveA()+" / 15 • quality="+qualityName(menu.liveB())+".",
                    "Driver present = "+(menu.liveC()!=0?"YES":"NO")+"; a valid 0 V source remains present electrical evidence."
            };
            default -> new String[]{"No device-specific diagnostic interpretation is registered for this compatibility kind."};
        };
    }

    private String diagnosticNextAction(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> menu.liveD()!=0
                    ?"NEXT • inspect whether limiting is intended for this transfer mode before changing the parameter; keep upstream evidence separate."
                    :"NEXT • if the transfer is wrong, compare input/output with the selected mode before retuning.";
            case ProcessParameterMenu.KIND_PWM -> !"VALID".equals(qualityName(menu.liveG()))
                    ?"NEXT • inspect command and inhibit evidence first; do not retune carrier period to mask an evidence fault."
                    :"NEXT • compare requested behavior against completed carrier cycles before changing period or inversion.";
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> !"VALID".equals(qualityName(menu.liveC()))
                    ?"NEXT • restore trustworthy Redstone command evidence before judging Copper slew response."
                    :"NEXT • compare tracking error against configured asymmetric rise/fall slew.";
            case ProcessParameterMenu.KIND_CAPACITOR -> menu.liveE()!=0
                    ?"NEXT • complete the load scan before treating discharge τ as authoritative."
                    :"NEXT • compare stored charge/output and effective τ under the same load before changing leakage.";
            case ProcessParameterMenu.KIND_FUSE -> menu.liveC()!=0
                    ?"NEXT • inspect overload evidence and reset conditions; do not erase retained exposure by changing rating/class."
                    :"NEXT • compare current ratio and thermal exposure before changing protection parameters.";
            case ProcessParameterMenu.KIND_COMPRESSOR -> "NEXT • compare tracking error under the same pressure command before changing ramp rates.";
            case ProcessParameterMenu.KIND_DAMPER -> "NEXT • compare amplitude, quality and age together; a weak or stale envelope is not the same as a clean zero.";
            case ProcessParameterMenu.KIND_EXCITER -> "NEXT • distinguish command evidence loss from physical coast-down before changing amplitude/frequency slew.";
            case ProcessParameterMenu.KIND_LAPIS_SOURCE, ProcessParameterMenu.KIND_COPPER_SOURCE -> "NEXT • use downstream measurement evidence to validate the configured source; the source value itself is configuration, not a load test.";
            default -> "NEXT • inspect synchronized world evidence before changing configuration.";
        };
    }

    private String routingContract(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> "Compatibility route: REDSTONE analog input → conditioned REDSTONE output. Normal gameplay opens the dedicated Signal Conditioner HMI.";
            case ProcessParameterMenu.KIND_PWM -> "REDSTONE command RX → binary PWM TX. The inhibit input is an auxiliary physical safety port managed by the block's route model.";
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> "REDSTONE command RX → COPPER voltage TX. RX and TX rotate independently; moving TX releases the old Copper driver claim before the server publishes the new path.";
            case ProcessParameterMenu.KIND_CAPACITOR -> "Rigid axial COPPER path: BACK input → stored-energy model → FRONT output. Rotate block turns the complete INPUT/OUTPUT axis; endpoints cannot be bent independently.";
            case ProcessParameterMenu.KIND_FUSE -> "Rigid axial COPPER path: BACK input → protection element → FRONT output. Rotate block turns the complete INPUT/OUTPUT axis; endpoints cannot be bent independently.";
            case ProcessParameterMenu.KIND_COMPRESSOR -> "Compatibility view only; normal gameplay routes compressor operation through the dedicated Pneumatic HMI.";
            case ProcessParameterMenu.KIND_DAMPER -> "MECHANICAL vibration envelope is a multi-face bidirectional physical interaction; no synthetic single RX/TX pair is created.";
            case ProcessParameterMenu.KIND_EXCITER -> "Fixed REDSTONE drive enters on DOWN; mechanical vibration is emitted through the declared multi-face actuator ports.";
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> "Output-only LAPIS precision source. TX may rotate; no synthetic input endpoint exists.";
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> "Six-face COPPER voltage source. Every face is a real source boundary; there is no single routable TX face.";
            default -> "No generic routing contract is available for this compatibility kind.";
        };
    }

    private String footer(){ return "Engineering Notebook • configuration editable • state/evidence/topology authoritative"; }

    private int drawWrapped(GuiGraphics g,String text,int x,int y,int width,int color){
        for(var line:font.split(Component.literal(text),width)){
            g.drawString(font,line,x,y,color,false);
            y+=14;
        }
        return y;
    }

    private void pair(GuiGraphics g,String label,String value,int y){
        g.drawString(font,label,42,y,MUTED,false);
        int valueX=Math.min(300,imageWidth/2);
        g.drawString(font,value,valueX,y,INK,false);
    }
    private String fit(String s,int width){ if(font.width(s)<=width)return s; String x=s; while(x.length()>1&&font.width(x+"…")>width)x=x.substring(0,x.length()-1); return x+"…"; }
    private static String conditionerMode(int m){ return switch(m){case 0->"SCALE";case 1->"OFFSET";case 2->"CLAMP";case 3->"THRESHOLD";case 4->"DEADBAND";case 5->"ATTENUATE";default->"UNKNOWN";}; }
    private static String fuseClass(int value){
        return switch(CopperFuseLogic.boundedTimeCurrentClass(value)){
            case CopperFuseLogic.FAST_CLASS->"FAST";
            case CopperFuseLogic.SLOW_CLASS->"SLOW";
            default->"NORMAL";
        };
    }
    private static String qualityName(int ordinal){
        PortQuality[] values=PortQuality.values();
        return ordinal>=0&&ordinal<values.length?values[ordinal].name():"UNKNOWN";
    }
}
