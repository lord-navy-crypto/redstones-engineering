package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact optical HMI with topology-aware commissioning evidence and acceptance. */
public final class OpticalSystemScreen extends EngineeringScreen<OpticalSystemMenu> {
    private Button p0,p1,s0,s1;
    public OpticalSystemScreen(OpticalSystemMenu m, Inventory i, Component t){super(m,i,t);}

    @Override protected void addDeviceWidgets(){
        int y=topPos+111;
        p0=addConfigureWidget(Button.builder(Component.literal("◀ Primary"),b->sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        p1=addConfigureWidget(Button.builder(Component.literal("Primary ▶"),b->sendMenuButton(OpticalSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(leftPos+199,y,105,20).build());
        s0=addConfigureWidget(Button.builder(Component.literal("◀ Secondary"),b->sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_PREVIOUS)).bounds(leftPos+16,y+26,105,20).build());
        s1=addConfigureWidget(Button.builder(Component.literal("Secondary ▶"),b->sendMenuButton(OpticalSystemMenu.BUTTON_SECONDARY_NEXT)).bounds(leftPos+199,y+26,105,20).build());
    }

    @Override protected void syncDeviceWidgetLabels(){
        if(p0==null)return;
        boolean e=menu.kind()==OpticalSystemMenu.KIND_EMITTER,f=menu.kind()==OpticalSystemMenu.KIND_FILTER,a=menu.kind()==OpticalSystemMenu.KIND_ATTENUATOR;
        boolean tx=menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_TX,rx=menu.kind()==OpticalSystemMenu.KIND_FREE_SPACE_RX,c=isConfigureSection();
        p0.visible=p1.visible=c&&(e||f||a); s0.visible=s1.visible=c&&(e||tx||rx);
        if(e){p0.setMessage(Component.literal("◀ I "+menu.primary()));p1.setMessage(Component.literal("I "+menu.primary()+" ▶"));s0.setMessage(Component.literal("◀ CH "+menu.secondary()));s1.setMessage(Component.literal("CHANNEL " + menu.secondary()));}
        else if(f){p0.setMessage(Component.literal("◀ CH "+menu.secondary()));p1.setMessage(Component.literal("CHANNEL " + menu.secondary()));}
        else if(a){p0.setMessage(Component.literal("◀ LOSS "+menu.secondary()));p1.setMessage(Component.literal("LOSS "+menu.secondary()+" ▶"));}
        else if(tx||rx){s0.setMessage(Component.literal("◀ CH "+menu.secondary()));s1.setMessage(Component.literal("CHANNEL " + menu.secondary()));}
    }

    @Override protected void renderSection(GuiGraphics g,Section s){switch(s){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}}

    private void overview(GuiGraphics g){
        statusBadge(g,name(),GOOD,16,80);
        statusBadge(g,menu.kind()==OpticalSystemMenu.KIND_METER?menu.commissioningStatus().name().replace('_',' '):qName(),menu.kind()==OpticalSystemMenu.KIND_METER?acceptanceColor():qColor(),205,80);
        metricCard(g,"Input",menu.primary()+" / 15",16,103,88,INFO);metricCard(g,"Channel",Integer.toString(menu.secondary()),111,103,88,GOOD);metricCard(g,"Aux",Integer.toString(menu.tertiary()),206,103,88,INFO);
        labelValue(g,"Topology",topology(),149);labelValue(g,"Role",role(),169);safeText(g,menu.kind()==OpticalSystemMenu.KIND_METER?acceptanceSummary():diagnosis(),16,195,menu.kind()==OpticalSystemMenu.KIND_METER?acceptanceColor():dColor());
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
        statusBadge(g,menu.kind()==OpticalSystemMenu.KIND_METER?"READ-ONLY DEVICE":"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);
        labelValue(g,"Primary",primaryControl(),104);labelValue(g,"Secondary",secondaryControl(),180);
        if(menu.directional())safeText(g,"Direction and physical interface orientation are controlled only on Route.",16,207,MUTED);
        else if(menu.kind()==OpticalSystemMenu.KIND_METER)safeText(g,"Measurement face is controlled only on Route.",16,207,MUTED);
    }

    private void diagnostics(GuiGraphics g){
        statusBadge(g,menu.kind()==OpticalSystemMenu.KIND_METER?"COMMISSIONING "+menu.commissioningStatus().name().replace('_',' '):qName(),menu.kind()==OpticalSystemMenu.KIND_METER?acceptanceColor():qColor(),16,80);
        if(menu.kind()==OpticalSystemMenu.KIND_METER){
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
        if(menu.kind()==OpticalSystemMenu.KIND_METER){labelValue(g,"Acceptance",menu.commissioningStatus().name(),110);labelValue(g,"One-hop connected",Integer.toString(menu.meterConnectedNeighbors()),132);labelValue(g,"Same / mismatch",menu.meterSameChannelNeighbors()+" / "+menu.meterChannelMismatches(),154);labelValue(g,"Strong / weak / spread",menu.meterStrongestNeighbor()+" / "+menu.meterWeakestNeighbor()+" / "+spread(),176);safeText(g,"Server-evaluated one-hop acceptance only; no hidden path or continuous dB history is invented.",16,202,MUTED);}
        else{labelValue(g,"Budget evidence",budget(),112);safeText(g,"Current server optical evidence only; no client-side carrier history is fabricated.",16,154,MUTED);}
    }

    private String acceptanceSummary(){return switch(menu.commissioningStatus()){case NOT_READY->"NOT READY • establish carrier and a connected comparison point.";case PASS->"PASS • same-channel one-hop budget is locally coherent.";case MARGINAL->"MARGINAL • resolve mismatch, incomplete comparison, or elevated local variation.";case FAIL->"FAIL • hard optical fault or severe local attenuation step.";};}
    private int acceptanceColor(){return switch(menu.commissioningStatus()){case PASS->GOOD;case NOT_READY->INFO;case MARGINAL->WARN;case FAIL->BAD;};}

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
    private String role(){return menu.kind()==OpticalSystemMenu.KIND_METER?"OBSERVER / COMMISSIONING":menu.kind()==OpticalSystemMenu.KIND_EMITTER?"SOURCE":menu.kind()==OpticalSystemMenu.KIND_RECEIVER?"TERMINAL SINK":"PROCESSOR / CONVERTER";}
    private String topology(){return menu.kind()==OpticalSystemMenu.KIND_METER?"MEASURE "+face(menu.facing()):menu.directional()?face(inputFace())+" → "+face(menu.facing()):"NETWORK / SOURCE";}
    private String primaryControl(){return switch(menu.kind()){case OpticalSystemMenu.KIND_EMITTER->"INTENSITY "+menu.primary()+"/15";case OpticalSystemMenu.KIND_FILTER->"TARGET CHANNEL "+menu.secondary();case OpticalSystemMenu.KIND_ATTENUATOR->"LOSS "+menu.secondary();default->"READ ONLY";};}
    private String secondaryControl(){return switch(menu.kind()){case OpticalSystemMenu.KIND_EMITTER,OpticalSystemMenu.KIND_FREE_SPACE_TX,OpticalSystemMenu.KIND_FREE_SPACE_RX->"CHANNEL " + menu.secondary();default->"NONE";};}
    private String qName(){return menu.quality().name().replace('_',' ');}private int qColor(){return menu.quality()==PortQuality.VALID?GOOD:menu.quality()==PortQuality.NO_SIGNAL||menu.quality()==PortQuality.STALE?WARN:BAD;}
    private Direction inputFace(){return menu.facing().getOpposite();}private String face(Direction d){return d.getName().toUpperCase();}
    private Direction leftOf(Direction f){return switch(f){case NORTH->Direction.WEST;case WEST->Direction.SOUTH;case SOUTH->Direction.EAST;case EAST->Direction.NORTH;default->Direction.WEST;};}
}
