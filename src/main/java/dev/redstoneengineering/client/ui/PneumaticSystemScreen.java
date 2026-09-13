package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Pneumatic HMI with server-synchronized section, actuator-path and storage diagnostics. */
public final class PneumaticSystemScreen extends EngineeringScreen<PneumaticSystemMenu> {
    private Button prev, next, toggle;

    public PneumaticSystemScreen(PneumaticSystemMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y=topPos+116;
        prev=addConfigureWidget(Button.builder(Component.literal("◀ Setpoint"),b->sendMenuButton(PneumaticSystemMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos+16,y,95,20).build());
        next=addConfigureWidget(Button.builder(Component.literal("Setpoint ▶"),b->sendMenuButton(PneumaticSystemMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos+209,y,95,20).build());
        toggle=addConfigureWidget(Button.builder(Component.literal("Toggle valve"),b->sendMenuButton(PneumaticSystemMenu.BUTTON_TOGGLE)).bounds(leftPos+100,y,120,20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if(prev==null)return;
        boolean setpoint=menu.kind()==PneumaticSystemMenu.KIND_REGULATOR||menu.kind()==PneumaticSystemMenu.KIND_RELIEF;
        boolean valve=menu.kind()==PneumaticSystemMenu.KIND_VALVE;
        prev.visible=next.visible=isConfigureSection()&&setpoint;toggle.visible=isConfigureSection()&&valve;
        if(setpoint){String v=menu.kind()==PneumaticSystemMenu.KIND_REGULATOR?menu.secondary()+"/100":menu.tertiary()+"/100";prev.setMessage(Component.literal("◀ "+v));next.setMessage(Component.literal(v+" ▶"));}
        if(valve)toggle.setMessage(Component.literal(menu.stateFlag()==1?"Close valve":"Open valve"));
    }

    @Override protected void renderSection(GuiGraphics g,Section section){switch(section){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}}

    private void overview(GuiGraphics g){
        statusBadge(g,name(),GOOD,16,80);
        statusBadge(g,isFlow()?menu.commissioningStatus().name().replace('_',' '):isCylinder()?cylinderState():isReservoir()?reservoirState():isProportional()?proportionalState():state(),isFlow()?acceptanceColor():isCylinder()?cylinderColor():isReservoir()?reservoirColor():isProportional()?proportionalColor():stateColor(),205,80);
        metricCard(g,primaryLabel(),primaryText(),16,103,88,INFO);metricCard(g,secondaryLabel(),secondaryText(),111,103,88,GOOD);metricCard(g,thirdLabel(),thirdText(),206,103,88,INFO);
        labelValue(g,"Topology",route(),149);labelValue(g,"Input / output evidence",menu.inputQuality().name()+" / "+menu.outputQuality().name(),169);
        safeText(g,isFlow()?acceptanceSummary():isCylinder()?cylinderDiagnosis():isReservoir()?reservoirDiagnosis():isProportional()?proportionalDiagnosis():"Pressure zero may be valid when observation quality is VALID.",16,196,isFlow()?acceptanceColor():isCylinder()?cylinderColor():isReservoir()?reservoirColor():isProportional()?proportionalColor():MUTED);
    }

    private void ports(GuiGraphics g){
        statusBadge(g,"PNEUMATIC INTERFACES",GOOD,16,80);
        if(!menu.directional()){statusLine(g,"NETWORK","PNEUMATIC NETWORK NODE",INFO,112);return;}
        statusLine(g,face(menu.inputDirection()),"INPUT • PNEUMATIC",qualityColor(menu.inputQuality()),112);
        statusLine(g,face(menu.outputDirection()),menu.kind()==PneumaticSystemMenu.KIND_RECEIVER||isCylinder()?"OUTPUT • REDSTONE 0..15":"OUTPUT • PNEUMATIC",qualityColor(menu.outputQuality()),142);
        if(isFlow())safeText(g,"Commissioning adds one axial witness beyond each meter port.",16,176,MUTED);
        if(isCylinder())safeText(g,"Cylinder path evidence is retained by the authoritative pneumatic solve; the HMI does not rerun flow physics.",16,176,MUTED);
        if(isProportional())safeText(g,"Valve loss is the synchronized inlet-to-outlet pressure difference at the configured opening.",16,176,MUTED);
    }

    private void configure(GuiGraphics g){
        statusBadge(g,"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);labelValue(g,"Control",controlText(),104);labelValue(g,"Physical route",route(),174);safeText(g,"Physical direction is controlled only on Route.",16,202,MUTED);
    }

    private void diagnostics(GuiGraphics g){
        if(isFlow()){
            statusBadge(g,"COMMISSIONING "+menu.commissioningStatus().name().replace('_',' '),acceptanceColor(),16,80);
            PneumaticSectionDiagnostics.Result r=section();
            labelValue(g,"Flow / meter ΔP",menu.primary()+" / "+menu.secondary(),106);labelValue(g,"Pin / Pout",menu.tertiary()+" / "+menu.auxiliary(),126);
            labelValue(g,"U / D witness",p(menu.upstreamQuality(),menu.upstreamPressure())+" / "+p(menu.downstreamQuality(),menu.downstreamPressure()),146);
            labelValue(g,"Drops U / M / D",d(r.upstreamDrop())+" / "+r.meterDrop()+" / "+d(r.downstreamDrop()),166);
            statusLine(g,"Localization",r.localization(),localColor(),188);safeText(g,acceptanceSummary(),16,210,acceptanceColor());return;
        }
        if(isCylinder()){
            statusBadge(g,cylinderState(),cylinderColor(),16,80);
            labelValue(g,"Supply / cylinder P",menu.cylinderSupply()+" / "+menu.primary(),104);
            labelValue(g,"Path loss",menu.cylinderObservedLoss()+" = line "+menu.cylinderLineLoss()+" + restriction "+menu.cylinderRestrictionLoss(),126);
            labelValue(g,"Path edges",Integer.toString(menu.cylinderPathEdges()),148);
            labelValue(g,"Position / target / error",menu.secondary()+" / "+menu.tertiary()+" / "+menu.cylinderError(),170);
            labelValue(g,"Response / remaining",menu.cylinderResponsePeriod()+"t per step / ≈"+menu.cylinderRemainingTicks()+"t",192);
            safeText(g,cylinderNext(),16,216,cylinderColor());return;
        }
        if(isReservoir()){
            statusBadge(g,reservoirState(),reservoirColor(),16,80);
            labelValue(g,"Stored pressure",menu.primary()+" / 100",106);
            labelValue(g,"Line pressure",menu.secondary()+" / 100",128);
            labelValue(g,"Charge headroom",Math.max(0,menu.secondary()-menu.primary())+" pressure units",150);
            labelValue(g,"Storage law","charge ≤5 / 10t • leak 1 / 10t",172);
            statusLine(g,"Diagnosis",reservoirDiagnosis(),reservoirColor(),194);
            safeText(g,reservoirNext(),16,216,reservoirColor());return;
        }
        if(isProportional()){
            statusBadge(g,proportionalState(),proportionalColor(),16,80);
            labelValue(g,"Inlet / outlet",menu.primary()+" / "+menu.secondary(),106);
            labelValue(g,"Opening command",menu.tertiary()+" / 15",128);
            labelValue(g,"Local ΔP",Math.max(0,menu.primary()-menu.secondary())+" / 100",150);
            labelValue(g,"Body pressure",menu.auxiliary()+" / 100",172);
            statusLine(g,"Diagnosis",proportionalDiagnosis(),proportionalColor(),194);
            safeText(g,proportionalNext(),16,216,proportionalColor());return;
        }
        statusBadge(g,state(),stateColor(),16,80);labelValue(g,primaryLabel(),primaryText(),108);labelValue(g,secondaryLabel(),secondaryText(),128);labelValue(g,thirdLabel(),thirdText(),148);statusLine(g,"Authority","SERVER SYNCHRONIZED",GOOD,192);
    }

    private void history(GuiGraphics g){
        statusBadge(g,"PNEUMATIC EVIDENCE",INFO,16,80);
        if(isFlow()){
            PneumaticSectionDiagnostics.Result r=section();labelValue(g,"Samples / acceptance",menu.stateFlag()+" / "+menu.commissioningStatus().name(),108);
            labelValue(g,"U / Pin / Pout / D",p(menu.upstreamQuality(),menu.upstreamPressure())+" / "+menu.tertiary()+" / "+menu.auxiliary()+" / "+p(menu.downstreamQuality(),menu.downstreamPressure()),130);
            labelValue(g,"Drops U / M / D",d(r.upstreamDrop())+" / "+r.meterDrop()+" / "+d(r.downstreamDrop()),152);statusLine(g,"Dominant local loss",r.localization(),localColor(),177);safeText(g,"Acceptance is server-evaluated; bends beyond witnesses are not inferred.",16,201,MUTED);
        }else if(isCylinder()){
            labelValue(g,"Samples / travel",menu.cylinderSamples()+" / "+menu.auxiliary(),108);labelValue(g,"Velocity / error",menu.cylinderVelocity()+" / "+menu.cylinderError(),130);
            labelValue(g,"Stall ticks / reversals",menu.cylinderStallTicks()+" / "+menu.cylinderReversals(),152);labelValue(g,"Supply / path loss",menu.cylinderSupply()+" / "+menu.cylinderObservedLoss(),174);
            safeText(g,"Response timing is deterministic and pressure-dependent; no continuous CFD or random leak history is fabricated.",16,202,MUTED);
        }else if(isReservoir()){
            labelValue(g,"Stored / line",menu.primary()+" / "+menu.secondary(),110);
            safeText(g,"Current recovery state is derived from the existing finite-rate reservoir law; no unretained trend history is invented.",16,148,MUTED);
        }else if(isProportional()){
            labelValue(g,"In / out / opening",menu.primary()+" / "+menu.secondary()+" / "+menu.tertiary(),110);
            labelValue(g,"Current local drop",Integer.toString(Math.max(0,menu.primary()-menu.secondary())),132);
            safeText(g,"Local restriction evidence is the current authoritative pressure transform, not an inferred flow coefficient.",16,164,MUTED);
        }else if(menu.kind()==PneumaticSystemMenu.KIND_RELIEF){labelValue(g,"Vent events",Integer.toString(menu.auxiliary()),110);safeText(g,"VENTING is an operating event, not missing measurement evidence.",16,150,GOOD);}
        else safeText(g,"Live server state only; no client-side pneumatic history is fabricated.",16,112,MUTED);
    }

    private String cylinderDiagnosis(){
        if(hard(menu.inputQuality()))return"ACTUATOR INPUT EVIDENCE FAULT";
        if(menu.secondary()==menu.tertiary())return"AT TARGET • NO RESPONSE DELAY";
        if(menu.cylinderSupply()<=0)return"NO PRESSURIZED SUPPLY PATH";
        if(menu.cylinderSupply()<25)return"LOW SUPPLY PRESSURE • SLOW RESPONSE BAND";
        if(menu.cylinderRestrictionLoss()>=Math.max(3,menu.cylinderLineLoss()))return"RESTRICTION / REGULATION LOSS DOMINANT";
        if(menu.cylinderLineLoss()>=4&&menu.cylinderLineLoss()>=menu.cylinderRestrictionLoss())return"DISTRIBUTION PATH LOSS DOMINANT";
        if(menu.primary()<25)return"DOWNSTREAM PRESSURE STARVATION";
        return"NORMAL PRESSURE-DEPENDENT FINITE-RATE RESPONSE";
    }
    private String cylinderNext(){String d=cylinderDiagnosis();if(d.contains("NO PRESSURIZED"))return"NEXT • restore a permitted compressor/reservoir path and check closed or reversed pneumatic elements.";if(d.contains("LOW SUPPLY"))return"NEXT • raise available source pressure before tuning downstream restrictions.";if(d.contains("RESTRICTION"))return"NEXT • inspect regulator setpoint and proportional/closed valve restrictions on the winning pressure path.";if(d.contains("PATH LOSS"))return"NEXT • shorten/segment the pipe run or move storage closer to the actuator.";if(d.contains("STARVATION"))return"NEXT • compare supply pressure with retained path loss before changing the cylinder.";if(d.contains("FAULT"))return"NEXT • repair topology/evidence quality before interpreting actuator response.";return"NEXT • response matches the current lumped pressure model; remaining time follows the synchronized estimate.";}
    private String cylinderState(){String d=cylinderDiagnosis();if(d.startsWith("AT TARGET"))return"AT TARGET";if(d.contains("FAULT")||d.contains("NO PRESSURIZED"))return"NOT READY";if(d.contains("DOMINANT")||d.contains("STARVATION")||d.contains("LOW SUPPLY"))return"MARGINAL";return"RESPONDING";}
    private int cylinderColor(){String s=cylinderState();return s.equals("AT TARGET")?GOOD:s.equals("RESPONDING")?INFO:s.equals("MARGINAL")?WARN:BAD;}

    private String reservoirDiagnosis(){
        if(hard(menu.inputQuality()))return"RESERVOIR EVIDENCE FAULT";
        if(menu.primary()<=0&&menu.secondary()<=0)return"EMPTY / DEPRESSURIZED";
        if(menu.secondary()>menu.primary())return"CHARGING TOWARD LINE PRESSURE";
        if(menu.primary()>menu.secondary())return"DISCHARGING / SUPPORTING LOWER-PRESSURE LINE";
        return"BUFFERED • STORED AND LINE PRESSURE ALIGNED";
    }
    private String reservoirState(){String d=reservoirDiagnosis();if(d.contains("FAULT"))return"NOT READY";if(d.startsWith("CHARGING"))return"CHARGING";if(d.startsWith("DISCHARGING"))return"DISCHARGING";if(d.startsWith("EMPTY"))return"EMPTY";return"BUFFERING";}
    private int reservoirColor(){String s=reservoirState();return s.equals("NOT READY")?BAD:s.equals("EMPTY")?WARN:s.equals("CHARGING")?INFO:GOOD;}
    private String reservoirNext(){String d=reservoirDiagnosis();if(d.contains("FAULT"))return"NEXT • restore valid pneumatic evidence before interpreting storage state.";if(d.startsWith("CHARGING"))return"NEXT • allow finite-rate recovery; storage rises by at most 5 pressure units every 10 ticks.";if(d.startsWith("DISCHARGING"))return"NEXT • inspect downstream demand/path loss if stored pressure keeps supporting a lower-pressure line.";if(d.startsWith("EMPTY"))return"NEXT • connect a pressurized source path to establish stored reserve.";return"NEXT • reserve is aligned with the current line; no storage intervention is indicated.";}

    private String proportionalDiagnosis(){
        if(hard(menu.inputQuality())||hard(menu.outputQuality()))return"VALVE EVIDENCE FAULT";
        if(menu.primary()<=0)return"NO UPSTREAM PRESSURE";
        if(menu.tertiary()<=0)return"COMMANDED CLOSED • FULL ISOLATION";
        int drop=Math.max(0,menu.primary()-menu.secondary());
        if(menu.tertiary()<5&&drop>=5)return"STRONG COMMANDED RESTRICTION";
        if(menu.tertiary()<12&&drop>=3)return"PARTIAL COMMANDED RESTRICTION";
        if(drop<=2)return"LOW LOCAL RESTRICTION";
        return"VALVE PRESSURE TRANSFORM ACTIVE";
    }
    private String proportionalState(){String d=proportionalDiagnosis();if(d.contains("FAULT")||d.contains("NO UPSTREAM"))return"NOT READY";if(d.contains("CLOSED"))return"CLOSED";if(d.contains("RESTRICTION")||d.contains("TRANSFORM"))return"THROTTLING";return"OPEN";}
    private int proportionalColor(){String s=proportionalState();return s.equals("NOT READY")?BAD:s.equals("CLOSED")?INFO:s.equals("THROTTLING")?WARN:GOOD;}
    private String proportionalNext(){String d=proportionalDiagnosis();if(d.contains("FAULT"))return"NEXT • repair endpoint/topology evidence before interpreting valve loss.";if(d.contains("NO UPSTREAM"))return"NEXT • restore supply pressure; opening changes cannot create upstream pressure.";if(d.contains("CLOSED"))return"NEXT • open the valve only if downstream pressure is required by the process.";if(d.contains("STRONG")||d.contains("PARTIAL"))return"NEXT • increase opening if this local restriction is starving the downstream actuator.";return"NEXT • local valve loss is modest; inspect pipe-path loss or source pressure if downstream pressure remains low.";}

    private boolean hard(PortQuality q){return q==PortQuality.FAULT||q==PortQuality.DOMAIN_MISMATCH||q==PortQuality.TOPOLOGY_ERROR;}
    private String acceptanceSummary(){return switch(menu.commissioningStatus()){case NOT_READY->"NOT READY • collect valid inlet/outlet evidence and at least four samples.";case PASS->"PASS • local pressure-loss evidence is complete and within the commissioning band.";case MARGINAL->"MARGINAL • inspect missing witnesses, degraded quality, or elevated local ΔP.";case FAIL->"FAIL • hard evidence fault or excessive local meter-section pressure drop.";};}
    private int acceptanceColor(){return switch(menu.commissioningStatus()){case PASS->GOOD;case NOT_READY->INFO;case MARGINAL->WARN;case FAIL->BAD;};}
    private PneumaticSectionDiagnostics.Result section(){return PneumaticSectionDiagnostics.analyze(menu.upstreamPressure(),menu.upstreamQuality(),menu.tertiary(),menu.inputQuality(),menu.auxiliary(),menu.outputQuality(),menu.downstreamPressure(),menu.downstreamQuality(),menu.secondary());}
    private boolean isFlow(){return menu.kind()==PneumaticSystemMenu.KIND_FLOW_METER;}private boolean isCylinder(){return menu.kind()==PneumaticSystemMenu.KIND_CYLINDER;}private boolean isReservoir(){return menu.kind()==PneumaticSystemMenu.KIND_RESERVOIR;}private boolean isProportional(){return menu.kind()==PneumaticSystemMenu.KIND_PROPORTIONAL;}
    private int localColor(){String s=section().localization();return s.contains("DOMINANT")||s.contains("INCOMPLETE")?WARN:s.contains("PARTIAL")?INFO:GOOD;}
    private String p(PortQuality q,int v){return q==PortQuality.VALID?Integer.toString(v):"N/A";}private String d(int v){return v<0?"N/A":Integer.toString(v);}private String face(net.minecraft.core.Direction d){return d.getName().toUpperCase();}
    private int qualityColor(PortQuality q){return q==PortQuality.VALID?GOOD:q==PortQuality.NO_SIGNAL||q==PortQuality.STALE?WARN:BAD;}private String route(){return menu.directional()?face(menu.inputDirection())+" → "+face(menu.outputDirection()):"NETWORK NODE";}
    private String name(){return switch(menu.kind()){case 0->"AIR COMPRESSOR";case 1->"PNEUMATIC PIPE";case 2->"AIR RESERVOIR";case 3->"PRESSURE REGULATOR";case 4->"PNEUMATIC RECEIVER";case 5->"PNEUMATIC VALVE";case 6->"CHECK VALVE";case 7->"FLOW METER";case 8->"PROPORTIONAL VALVE";case 9->"RELIEF VALVE";case 10->"PNEUMATIC CYLINDER";default->"PNEUMATIC DEVICE";};}
    private String state(){if(menu.kind()==9)return menu.stateFlag()==1?"VENTING":"ARMED";if(menu.kind()==5)return menu.stateFlag()==1?"OPEN":"CLOSED";if(isFlow())return menu.stateFlag()>0?"MEASURING":"NO SAMPLES";return menu.inputQuality()==PortQuality.VALID||menu.outputQuality()==PortQuality.VALID?"NOMINAL":"IDLE / NO SIGNAL";}
    private int stateColor(){return menu.inputQuality()==PortQuality.FAULT||menu.outputQuality()==PortQuality.FAULT||(menu.kind()==9&&menu.stateFlag()==1)?WARN:GOOD;}
    private String primaryLabel(){return isFlow()?"Flow":isCylinder()?"Pressure":isReservoir()?"Stored":menu.directional()?"Inlet":"Pressure";}private String secondaryLabel(){return isFlow()?"Δ pressure":isCylinder()?"Position":isReservoir()?"Line":menu.directional()?"Outlet":"Aux";}private String thirdLabel(){return isFlow()?"Inlet P":isCylinder()?"Target":isProportional()?"Opening":"State";}
    private String primaryText(){return menu.primary()+(menu.kind()==0?" / 15":" / 100");}private String secondaryText(){return menu.secondary()+(isCylinder()?" / 15":" / 100");}private String thirdText(){return menu.tertiary()+(isCylinder()||isProportional()?" / 15":" / 100");}
    private String controlText(){return switch(menu.kind()){case 3->"SETPOINT "+menu.secondary()+"/100";case 9->"RELIEF "+menu.tertiary()+"/100";case 5->menu.stateFlag()==1?"OPEN":"CLOSED";case 8->"EXTERNAL UP COMMAND";default->"NO MANUAL PROCESS PARAMETER";};}
}
