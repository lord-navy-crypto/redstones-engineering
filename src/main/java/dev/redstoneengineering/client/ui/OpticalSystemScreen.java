package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact optical HMI with topology-aware commissioning evidence and acceptance. */
public final class OpticalSystemScreen extends EngineeringScreen<OpticalSystemMenu> {
    private Button p0,p1,s0,s1;
    private EditBox primaryInput, secondaryInput;
    private Button primaryApply, secondaryApply;
    public OpticalSystemScreen(OpticalSystemMenu m, Inventory i, Component t){super(m,i,t);}

    @Override protected void addDeviceWidgets(){
        int y=topPos+111;
        p0=addConfigureWidget(Button.builder(Component.literal("◀ Primary"),b->sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        p1=addConfigureWidget(Button.builder(Component.literal("Primary ▶"),b->sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(leftPos+199,y,105,20).build());
        s0=addConfigureWidget(Button.builder(Component.literal("◀ Secondary"),b->sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_PREVIOUS)).bounds(leftPos+16,y+26,105,20).build());
        s1=addConfigureWidget(Button.builder(Component.literal("Secondary ▶"),b->sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_NEXT)).bounds(leftPos+199,y+26,105,20).build());

        primaryInput=addConfigureWidget(new EditBox(this.font,leftPos+16,y,105,20,Component.literal("Primary exact value")));
        primaryInput.setMaxLength(2);
        primaryInput.setFilter(value->value.isEmpty()||value.chars().allMatch(Character::isDigit));
        primaryApply=addConfigureWidget(Button.builder(Component.literal("Apply primary"),b->submitPrimary()).bounds(leftPos+199,y,105,20).build());

        secondaryInput=addConfigureWidget(new EditBox(this.font,leftPos+16,y+26,105,20,Component.literal("Secondary exact value")));
        secondaryInput.setMaxLength(2);
        secondaryInput.setFilter(value->value.isEmpty()||value.chars().allMatch(Character::isDigit));
        secondaryApply=addConfigureWidget(Button.builder(Component.literal("Apply secondary"),b->submitSecondary()).bounds(leftPos+199,y+26,105,20).build());
    }

    @Override protected void syncDeviceWidgetLabels(){
        if(p0==null)return;
        boolean e=menu.kind()==OpticalSystemMenu.KIND_EMITTER,f=menu.kind()==OpticalSystemMenu.KIND_FILTER,a=menu.kind()==OpticalSystemMenu.KIND_ATTENUATOR;
        boolean tx=menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_TX,rx=menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_RX,c=isConfigureSection();
        boolean directPrimary=c&&(e||f||a),directSecondary=c&&(e||tx||rx);
        p0.visible=p1.visible=false; s0.visible=s1.visible=false;

        primaryInput.visible=primaryInput.active=directPrimary;
        primaryApply.visible=directPrimary;
        primaryApply.active=directPrimary&&primaryInputValid();
        secondaryInput.visible=secondaryInput.active=directSecondary;
        secondaryApply.visible=directSecondary;
        secondaryApply.active=directSecondary&&secondaryInputValid();

        if(directPrimary&&!primaryInput.isFocused()){
            String expected=Integer.toString(primaryValue());
            if(!expected.equals(primaryInput.getValue()))primaryInput.setValue(expected);
        }
        if(directSecondary&&!secondaryInput.isFocused()){
            String expected=Integer.toString(menu.secondary());
            if(!expected.equals(secondaryInput.getValue()))secondaryInput.setValue(expected);
        }

        primaryApply.setMessage(Component.literal("Apply "+primarySymbol()));
        secondaryApply.setMessage(Component.literal("Apply CH"));
    }

    private int primaryValue(){
        return menu.kind()==OpticalSystemMenu.KIND_EMITTER?menu.primary():menu.secondary();
    }

    private String primarySymbol(){
        return switch(menu.kind()){
            case OpticalSystemMenu.KIND_EMITTER->"I_set";
            case OpticalSystemMenu.KIND_FILTER->"CH_target";
            case OpticalSystemMenu.KIND_ATTENUATOR->"L";
            default->"value";
        };
    }

    private boolean primaryInputValid(){
        if(primaryInput==null||primaryInput.getValue().isEmpty())return false;
        try{
            int value=Integer.parseInt(primaryInput.getValue());
            if(menu.kind()==OpticalSystemMenu.KIND_ATTENUATOR)return value>=0&&value<=8;
            return (menu.kind()==OpticalSystemMenu.KIND_EMITTER||menu.kind()==OpticalSystemMenu.KIND_FILTER)&&value>=0&&value<=15;
        }catch(NumberFormatException ignored){return false;}
    }

    private boolean secondaryInputValid(){
        if(secondaryInput==null||secondaryInput.getValue().isEmpty())return false;
        try{
            int value=Integer.parseInt(secondaryInput.getValue());
            if(menu.kind()==OpticalSystemMenu.KIND_EMITTER)return value>=0&&value<=15;
            return (menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_TX||menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_RX)&&value>=0&&value<=3;
        }catch(NumberFormatException ignored){return false;}
    }

    private void submitPrimary(){
        if(!primaryInputValid())return;
        sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_DIRECT_BASE+Integer.parseInt(primaryInput.getValue()));
        primaryInput.setFocused(false);
    }

    private void submitSecondary(){
        if(!secondaryInputValid())return;
        sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_DIRECT_BASE+Integer.parseInt(secondaryInput.getValue()));
        secondaryInput.setFocused(false);
    }

    @Override protected void renderSection(GuiGraphics g,Section s){switch(s){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}}

    private boolean hasAcceptance(){return menu.kind()==OpticalSystemMenu.KIND_METER||menu.kind()==OpticalSystemMenu.KIND_RECEIVER;}

    private void overview(GuiGraphics g){
        statusBadge(g,name(),GOOD,16,80);
        statusBadge(g,hasAcceptance()?menu.commissioningStatus().name().replace('_',' '):qName(),hasAcceptance()?acceptanceColor():qColor(),205,80);
        metricCard(g,"Input",menu.primary()+" / 15",16,103,88,INFO);metricCard(g,"Channel",Integer.toString(menu.secondary()),111,103,88,GOOD);metricCard(g,"Aux",Integer.toString(menu.tertiary()),206,103,88,INFO);
        labelValue(g,"Topology",topology(),149);labelValue(g,"Role",role(),169);
        safeText(g,menu.kind()==OpticalSystemMenu.KIND_RECEIVER?receiverBudgetSummary():menu.kind()==OpticalSystemMenu.KIND_METER?acceptanceSummary():diagnosis(),16,195,hasAcceptance()?acceptanceColor():dColor());
    }

    private void ports(GuiGraphics g){
        statusBadge(g,"OPTICAL INTERFACES",GOOD,16,80);
        if(menu.kind()==OpticalSystemMenu.KIND_EMITTER)statusLine(g,"ALL 6 FACES","OUTPUT • OPTICAL SOURCE",GOOD,112);
        else if(menu.kind()==OpticalSystemMenu.KIND_RECEIVER)statusLine(g,"ALL 6 FACES","INPUT • OPTICAL TERMINAL SINK",qColor(),112);
        else if(menu.kind()==OpticalSystemMenu.KIND_METER){statusLine(g,face(menu.facing()),"INPUT • OPTICAL MEASUREMENT",qColor(),112);statusLine(g,"AUTHORITY","OBSERVER ONLY • NO BACKDRIVE",INFO,142);}
        else if(menu.kind()==OpticalSystemMenu.KIND_SPLITTER){statusLine(g,face(inputFace()),"INPUT • OPTICAL",qColor(),108);statusLine(g,face(menu.facing()),"OUTPUT A • OPTICAL",GOOD,136);statusLine(g,face(leftOf(menu.facing())),"OUTPUT B • OPTICAL",GOOD,164);}
        else{statusLine(g,face(inputFace()),menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_TX?"INPUT • REDSTONE PAYLOAD":"INPUT • OPTICAL",qColor(),112);statusLine(g,face(menu.facing()),menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_RX?"OUTPUT • REDSTONE 0..15":"OUTPUT • OPTICAL",GOOD,142);}
    }

    private void configure(GuiGraphics g){
        statusBadge(g,"PIONEER PATTERN • OPTICAL MODEL",INFO,16,80);
        formulaCard(g,opticalEquation(),105);
        variableRole(g,"MEASURED","I / state",budget(),"optical evidence",134);
        variableRole(g,"ADJUSTABLE",primarySymbol(),primaryControl(),"exact server-backed value",152);
        variableRole(g,"ADJUSTABLE","channel",secondaryControl(),"exact server-backed value",170);
        variableRole(g,"EVIDENCE","quality",qName(),"",188);
        if(menu.kind()==OpticalSystemMenu.KIND_RECEIVER){
            variableRole(g,"DERIVED","L_obs",Integer.toString(menu.budgetObservedLoss()),"intensity steps",206);
            variableRole(g,"DERIVED","H_rx",Integer.toString(menu.budgetReceiverHeadroom()),"above I=1",224);
        }else if(menu.kind()==OpticalSystemMenu.KIND_SPLITTER){
            variableRole(g,"DERIVED","q_split",Integer.toString(Math.max(0,menu.auxiliary())),"integer loss",206);
        }else if(menu.kind()==OpticalSystemMenu.KIND_ATTENUATOR){
            variableRole(g,"DERIVED","L",Integer.toString(Math.max(0,menu.primary()-menu.secondary())),"intensity steps",206);
        }
        wrappedText(g,"Optical controls remain server-authoritative. The HMI shows carrier/budget evidence but never invents an unobserved path or performs a second optical propagation solve.",16,248,workspaceWidth()-24,MUTED);
        wrappedText(g,"Direction and physical interface orientation are controlled only on Route.",16,278,workspaceWidth()-24,MUTED);
    }

    private void diagnostics(GuiGraphics g){
        statusBadge(g,hasAcceptance()?"COMMISSIONING "+menu.commissioningStatus().name().replace('_',' '):qName(),hasAcceptance()?acceptanceColor():qColor(),16,80);
        if(menu.kind()==OpticalSystemMenu.KIND_RECEIVER){
            labelValue(g,"Segment TX / RX",menu.budgetSourceIntensity()+"/15 → "+menu.primary()+"/15",104);
            labelValue(g,"Observed segment loss",Integer.toString(menu.budgetObservedLoss()),126);
            labelValue(g,"Receiver headroom",menu.budgetReceiverHeadroom()+" above I=1 threshold",148);
            labelValue(g,"Passive nodes / hops",menu.budgetPassiveNodes()+" / "+menu.budgetPassiveHops(),170);
            labelValue(g,"Source / channel",menu.budgetSourceCount()+" source • CH "+menu.budgetSourceChannel()+" → CH "+menu.secondary(),192);
            safeText(g,receiverNext(),16,216,acceptanceColor());
        }else if(menu.kind()==OpticalSystemMenu.KIND_METER){
            labelValue(g,"Point",menu.primary()+"/15 • CH "+menu.secondary(),106);
            labelValue(g,"Connected / same CH",menu.meterConnectedNeighbors()+" / "+menu.meterSameChannelNeighbors(),128);
            labelValue(g,"Channel mismatches",Integer.toString(menu.meterChannelMismatches()),150);
            labelValue(g,"Neighbor strong / weak",menu.meterStrongestNeighbor()+" / "+menu.meterWeakestNeighbor(),172);
            statusLine(g,"Local diagnosis",diagnosis(),dColor(),194);safeText(g,acceptanceSummary(),16,214,acceptanceColor());
        }else{
            labelValue(g,"Transfer",budget(),108);labelValue(g,"Quality",qName(),132);
            statusLine(g,"Commissioning",diagnosis(),dColor(),194);safeText(g,next(),16,214,dColor());
        }
    }

    private void history(GuiGraphics g){
        statusBadge(g,"OPTICAL EVIDENCE",INFO,16,80);
        if(menu.kind()==OpticalSystemMenu.KIND_RECEIVER){
            labelValue(g,"Segment acceptance",menu.commissioningStatus().name(),108);
            labelValue(g,"TX / observed RX",menu.budgetSourceIntensity()+" / "+menu.primary(),130);
            labelValue(g,"Loss / headroom",menu.budgetObservedLoss()+" / "+menu.budgetReceiverHeadroom(),152);
            labelValue(g,"Source count / channel",menu.budgetSourceCount()+" / "+menu.budgetSourceChannel(),174);
            labelValue(g,"Audit bounded",menu.budgetBounded()?"YES":"NO",196);
            safeText(g,"Intensity-unit segment budget only; no continuous dB or unretained optical history is invented.",16,218,MUTED);
        }else if(menu.kind()==OpticalSystemMenu.KIND_METER){labelValue(g,"Acceptance",menu.commissioningStatus().name(),110);labelValue(g,"One-hop connected",Integer.toString(menu.meterConnectedNeighbors()),132);labelValue(g,"Same / mismatch",menu.meterSameChannelNeighbors()+" / "+menu.meterChannelMismatches(),154);labelValue(g,"Strong / weak / spread",menu.meterStrongestNeighbor()+" / "+menu.meterWeakestNeighbor()+" / "+spread(),176);safeText(g,"Server-evaluated one-hop acceptance only; no hidden path or continuous dB history is invented.",16,202,MUTED);}
        else{labelValue(g,"Budget evidence",budget(),112);safeText(g,"Current server optical evidence only; no client-side carrier history is fabricated.",16,154,MUTED);}
    }

    private String receiverBudgetSummary(){return switch(menu.commissioningStatus()){case NOT_READY->"NOT READY • guided segment needs bounded, live source-to-receiver evidence.";case PASS->"PASS • guided segment has coherent channel and useful receiver headroom.";case MARGINAL->"MARGINAL • carrier arrives with only 0–1 intensity unit of headroom above dark.";case FAIL->"FAIL • multiple sources, topology fault, or channel incoherence invalidates the segment.";};}
    private String receiverNext(){return switch(menu.commissioningStatus()){case NOT_READY->"NEXT • restore one bounded guided source and continuous passive fiber path.";case FAIL->"NEXT • isolate source/topology/channel conflict before interpreting optical loss.";case MARGINAL->"NEXT • reduce downstream path loss or upstream splitter/attenuator loss, or raise TX intensity with commissioning evidence.";case PASS->"NEXT • segment budget is coherent; inspect upstream processor budgets only if more margin is required.";};}
    private String acceptanceSummary(){return switch(menu.commissioningStatus()){case NOT_READY->"NOT READY • establish carrier and a connected comparison point.";case PASS->"PASS • same-channel one-hop budget is locally coherent.";case MARGINAL->"MARGINAL • resolve mismatch, incomplete comparison, or elevated local variation.";case FAIL->"FAIL • hard optical fault or severe local attenuation step.";};}
    private int acceptanceColor(){return switch(menu.commissioningStatus()){case PASS->GOOD;case NOT_READY->INFO;case MARGINAL->WARN;case FAIL->BAD;};}

    private String opticalEquation(){
        if(menu.kind()==OpticalSystemMenu.KIND_ATTENUATOR)return "I_out = max(0, I_in - L)";
        if(menu.kind()==OpticalSystemMenu.KIND_SPLITTER)return "I_A = floor(I_in/2), I_B = floor(I_in/2), q = I_in - I_A - I_B";
        if(menu.kind()==OpticalSystemMenu.KIND_FILTER)return "I_out = (CH_in = CH_target) ? I_in : 0";
        if(menu.kind()==OpticalSystemMenu.KIND_RECEIVER)return "L_obs = I_TX - I_RX ; H_rx = I_RX - 1";
        if(menu.kind()==OpticalSystemMenu.KIND_EMITTER)return "I_out = I_set on selected channel";
        return "I_obs = authoritative optical path evidence";
    }

    private String diagnosis(){
        if(menu.quality()==PortQuality.TOPOLOGY_ERROR||menu.quality()==PortQuality.FAULT)return"TOPOLOGY / SOURCE CONFLICT";
        if(menu.quality()==PortQuality.STALE)return"STALE OPTICAL EVIDENCE";if(menu.quality()==PortQuality.NO_SIGNAL)return"NO CARRIER / DARK PATH";
        if(menu.kind()==OpticalSystemMenu.KIND_METER){if(menu.meterChannelMismatches()>0)return"LOCAL CHANNEL MISMATCH EVIDENCE";if(menu.meterConnectedNeighbors()==0)return"ISOLATED MEASUREMENT POINT";if(menu.meterSameChannelNeighbors()==0)return"NO SAME-CHANNEL COMPARISON POINT";if(menu.meterStrongestNeighbor()-menu.primary()>=2)return"LOCAL ATTENUATION STEP EVIDENCE";if(spread()>=2)return"ONE-HOP BUDGET VARIATION";return"LOCAL OPTICAL BUDGET COHERENT";}
        if(menu.kind()==OpticalSystemMenu.KIND_RECEIVER&&menu.auxiliary()>1)return"MULTIPLE ACTIVE DRIVERS";
        if(menu.kind()==OpticalSystemMenu.KIND_FILTER&&menu.auxiliary()!=menu.secondary()&&menu.primary()>0)return"CHANNEL REJECTION • EXPECTED";
        if(menu.kind()==OpticalSystemMenu.KIND_ATTENUATOR&&menu.tertiary()!=Math.max(0,menu.primary()-menu.secondary()))return"ATTENUATION TRANSFER MISMATCH";
        if(menu.kind()==OpticalSystemMenu.KIND_SPLITTER&&menu.secondary()+menu.tertiary()+Math.max(0,menu.auxiliary())!=Math.max(0,menu.primary()))return"SPLIT BUDGET MISMATCH";
        return"OPTICAL EVIDENCE COHERENT";
    }
    private String next(){String d=diagnosis();if(d.contains("CONFLICT")||d.contains("MISMATCH"))return"NEXT • resolve source/channel/topology inconsistency first.";if(d.contains("ISOLATED")||d.contains("NO SAME"))return"NEXT • add or move a meter to a connected same-channel point.";if(d.contains("ATTENUATION")||d.contains("VARIATION"))return"NEXT • compare the strongest adjacent point to localize loss.";if(d.contains("NO CARRIER"))return"NEXT • check source intensity, channel and path continuity.";return"NEXT • local budget is coherent; continue to the next commissioning point if needed.";}
    private int dColor(){String d=diagnosis();return d.contains("CONFLICT")||d.contains("MISMATCH")||d.contains("ATTENUATION")||d.contains("VARIATION")||d.contains("NO CARRIER")?WARN:d.contains("ISOLATED")||d.contains("NO SAME")?INFO:GOOD;}
    private int spread(){return Math.max(0,menu.meterStrongestNeighbor()-menu.meterWeakestNeighbor());}
    private String budget(){return switch(menu.kind()){case OpticalSystemMenu.KIND_FILTER->"IN "+menu.primary()+" → OUT "+menu.tertiary()+" • CH "+menu.auxiliary()+"→"+menu.secondary();case OpticalSystemMenu.KIND_ATTENUATOR->"IN "+menu.primary()+" - LOSS "+menu.secondary()+" → OUT "+menu.tertiary();case OpticalSystemMenu.KIND_SPLITTER->"IN "+menu.primary()+" → A/B "+menu.secondary()+"/"+menu.tertiary();default->"CURRENT SERVER EVIDENCE";};}
    private String name(){return switch(menu.kind()){case OpticalSystemMenu.KIND_EMITTER->"OPTICAL EMITTER";case OpticalSystemMenu.KIND_RECEIVER->"OPTICAL RECEIVER";case OpticalSystemMenu.KIND_METER->"OPTICAL POWER METER";case OpticalSystemMenu.KIND_SPLITTER->"OPTICAL 1×2 SPLITTER";case OpticalSystemMenu.KIND_FILTER->"OPTICAL CHANNEL FILTER";case OpticalSystemMenu.KIND_ATTENUATOR->"OPTICAL ATTENUATOR";case OpticalSystemMenu.KIND_FREE_SPACE_TX->"FREE-SPACE OPTICAL TX";case OpticalSystemMenu.KIND_FREE_SPACE_RX->"FREE-SPACE OPTICAL RX";default->"OPTICAL DEVICE";};}
    private String role(){return menu.kind()==OpticalSystemMenu.KIND_METER?"OBSERVER / COMMISSIONING":menu.kind()==OpticalSystemMenu.KIND_EMITTER?"SOURCE":menu.kind()==OpticalSystemMenu.KIND_RECEIVER?"TERMINAL SINK / LINK BUDGET":"PROCESSOR / CONVERTER";}
    private String topology(){return menu.kind()==OpticalSystemMenu.KIND_METER?"MEASURE "+face(menu.facing()):menu.directional()?face(inputFace())+" → "+face(menu.facing()):"NETWORK / SOURCE";}
    private String primaryControl(){return switch(menu.kind()){case OpticalSystemMenu.KIND_EMITTER->"INTENSITY "+menu.primary()+"/15";case OpticalSystemMenu.KIND_FILTER->"TARGET CHANNEL "+menu.secondary();case OpticalSystemMenu.KIND_ATTENUATOR->"LOSS "+menu.secondary();default->"READ ONLY";};}
    private String secondaryControl(){return switch(menu.kind()){case OpticalSystemMenu.KIND_EMITTER,OpticalSystemMenu.KIND_FREE_SPACE_TX,OpticalSystemMenu.KIND_FREE_SPACE_RX->"CHANNEL " + menu.secondary();default->"NONE";};}
    private String qName(){return menu.quality().name().replace('_',' ');}private int qColor(){return menu.quality()==PortQuality.VALID?GOOD:menu.quality()==PortQuality.NO_SIGNAL||menu.quality()==PortQuality.STALE?WARN:BAD;}
    private Direction inputFace(){return menu.facing().getOpposite();}private String face(Direction d){return d.getName().toUpperCase();}
    private Direction leftOf(Direction f){return switch(f){case NORTH->Direction.WEST;case WEST->Direction.SOUTH;case SOUTH->Direction.EAST;case EAST->Direction.NORTH;default->Direction.WEST;};}
}
