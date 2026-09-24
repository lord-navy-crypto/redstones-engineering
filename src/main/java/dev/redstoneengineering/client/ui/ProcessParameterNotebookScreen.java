package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
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
    private static final int BG=0xFFF2E9D8, PAGE=0xFFFFF8E8, INK=0xFF2C2925, MUTED=0xFF6E675E, RULE=0xFFB9A98F, ACCENT=0xFF4F5F7B, GOOD=0xFF2F7D4A;
    private enum Tab { OPERATE("Operate"), PARAMETERS("Parameters"), MODEL("Model"); final String label; Tab(String s){label=s;} }
    private Tab tab=Tab.PARAMETERS;
    private final List<Button> controls=new ArrayList<>();
    private Button action;
    private int scrollOffset = 0;
    private static final int VIEW_MARGIN = 8;
    private static final int CONTENT_TOP = 84;
    private static final int CONTENT_BOTTOM_MARGIN = 34;

    public ProcessParameterNotebookScreen(ProcessParameterMenu menu, Inventory inventory, Component title){
        super(menu,inventory,title); imageWidth=520; imageHeight=300; titleLabelX=18; titleLabelY=12; inventoryLabelY=1000;
    }

    @Override protected void init(){
        imageWidth=Math.max(360,width-VIEW_MARGIN*2);
        imageHeight=Math.max(240,height-VIEW_MARGIN*2);
        super.init(); controls.clear(); scrollOffset=0;

        int tabCount=Tab.values().length;
        int gap=8;
        int tabWidth=Math.max(88,(imageWidth-48-gap*(tabCount-1))/tabCount);
        int x=leftPos+24;
        for(Tab t:Tab.values()){
            addRenderableWidget(Button.builder(Component.literal(t.label),b->{tab=t;scrollOffset=0;syncVisibility();})
                    .bounds(x,topPos+38,tabWidth,22).build());
            x+=tabWidth+gap;
        }

        addRow(0,CONTENT_TOP+44); addRow(1,CONTENT_TOP+96); addRow(2,CONTENT_TOP+148); addRow(3,CONTENT_TOP+200);
        action=addRenderableWidget(Button.builder(Component.literal("Action"),b->send(ProcessParameterMenu.BUTTON_ACTION))
                .bounds(leftPos+imageWidth-188,topPos+CONTENT_TOP+250,150,22).build());
        syncVisibility();
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
            int max=maxScroll();
            scrollOffset=Math.max(0,Math.min(max,scrollOffset-(int)Math.round(scrollY*24.0)));
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
        };
    }

    private int maxScroll(){
        int visible=Math.max(80,imageHeight-CONTENT_TOP-CONTENT_BOTTOM_MARGIN);
        return Math.max(0,contentHeight()-visible);
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
            b.setY(topPos+virtualY-scrollOffset);
            b.visible=tab==Tab.PARAMETERS&&row<n
                    &&b.getY()>=topPos+CONTENT_TOP&&b.getY()<=topPos+imageHeight-CONTENT_BOTTOM_MARGIN-22;
        }
        if(action!=null){
            action.setX(leftPos+imageWidth-188);
            action.setY(topPos+CONTENT_TOP+250-scrollOffset);
            boolean hasAction=menu.kind()==ProcessParameterMenu.KIND_PWM||menu.kind()==ProcessParameterMenu.KIND_FUSE;
            action.visible=tab==Tab.PARAMETERS&&hasAction
                    &&action.getY()>=topPos+CONTENT_TOP&&action.getY()<=topPos+imageHeight-CONTENT_BOTTOM_MARGIN-22;
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
        g.pose().translate(0,-scrollOffset,0);
        switch(tab){case OPERATE->operate(g);case PARAMETERS->parameters(g);case MODEL->model(g);}
        g.pose().popPose();
        g.disableScissor();

        if(maxScroll()>0){
            String scroll="SCROLL "+scrollOffset+" / "+maxScroll();
            g.drawString(font,scroll,imageWidth-24-font.width(scroll),72,MUTED,false);
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
            case ProcessParameterMenu.KIND_CAPACITOR -> slot==0?v+" ticks":"×"+v;
            case ProcessParameterMenu.KIND_FUSE -> slot==0?v+" current units":fuseClass(v);
            case ProcessParameterMenu.KIND_COMPRESSOR -> v+" pressure/tick";
            case ProcessParameterMenu.KIND_DAMPER -> v+" amplitude/step";
            case ProcessParameterMenu.KIND_EXCITER -> slot==0?v+"/15":v+" units/tick";
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
            case ProcessParameterMenu.KIND_PWM -> "Duty request comes from command 0..15; carrier period controls time quantization.";
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> "V[k+1] = V[k] + clamp(Vtarget − V[k], −Sfall, +Srise).";
            case ProcessParameterMenu.KIND_CAPACITOR -> "q*[k] = 100·Vin/15; q[k+1] moves toward q* by max(1, |q*−q|/τ).";
            case ProcessParameterMenu.KIND_FUSE -> "r = I/Irated; for r>1, ΔH ∝ (r²−1)·Kclass; trip when H ≥ 1000.";
            case ProcessParameterMenu.KIND_COMPRESSOR -> "Pressure target derives from Redstone command; actual pressure follows asymmetric ramp rates.";
            case ProcessParameterMenu.KIND_DAMPER -> "Each decay step removes configured amplitude while preserving carrier frequency evidence; the local envelope also carries bounded quality and freshness.";
            case ProcessParameterMenu.KIND_EXCITER -> "Redstone controls target amplitude; frequency and amplitude approach their targets with finite dynamics.";
            case ProcessParameterMenu.KIND_LAPIS_SOURCE -> "Configured 0..100 precision value is a valid Lapis-domain source, including exact zero.";
            case ProcessParameterMenu.KIND_COPPER_SOURCE -> "Configured 0..15 voltage is a valid six-face Copper source.";
            default -> "";
        };
    }

    private String model2(){
        return switch(menu.kind()){
            case ProcessParameterMenu.KIND_CONDITIONER -> "Input and output quality are separate. Active limiting marks output SATURATED while preserving the upstream input quality.";
            case ProcessParameterMenu.KIND_PWM -> "Command updates latch at carrier-cycle boundaries; command, inhibit and output quality remain independent evidence channels.";
            case ProcessParameterMenu.KIND_COPPER_DRIVER -> "Rise and fall slew are independent. Internal actual voltage can decay after command loss, but the Copper source is released immediately unless input evidence is VALID.";
            case ProcessParameterMenu.KIND_CAPACITOR -> "τcharge=τbase; τdischarge=f(τbase,Rload); open circuit uses τbase×leakageFactor; incomplete load scans freeze integration.";
            case ProcessParameterMenu.KIND_FUSE -> "FAST/NORMAL/SLOW change overload heating rate; rating/class changes retain heat, and reset remains evidence-gated.";
            case ProcessParameterMenu.KIND_DAMPER -> "Envelope quality is server-owned 0..100 evidence and age is time since the last authoritative local write; neither is inferred from amplitude alone.";
            case ProcessParameterMenu.KIND_EXCITER -> "Amplitude rise/fall and frequency slew are independent configuration variables. Input and mechanical-output quality are synchronized independently from numeric amplitude.";
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
        g.drawString(font,fit(value,Math.max(180,imageWidth-valueX-56)),valueX,y,INK,false);
    }
    private String fit(String s,int width){ if(font.width(s)<=width)return s; String x=s; while(x.length()>1&&font.width(x+"…")>width)x=x.substring(0,x.length()-1); return x+"…"; }
    private static String conditionerMode(int m){ return switch(m){case 0->"SCALE";case 1->"OFFSET";case 2->"CLAMP";case 3->"THRESHOLD";case 4->"DEADBAND";case 5->"ATTENUATE";default->"UNKNOWN";}; }
    private static String fuseClass(int value){
        return switch(value){case 0->"FAST";case 2->"SLOW";default->"NORMAL";};
    }
    private static String qualityName(int ordinal){
        PortQuality[] values=PortQuality.values();
        return ordinal>=0&&ordinal<values.length?values[ordinal].name():"UNKNOWN";
    }
}
