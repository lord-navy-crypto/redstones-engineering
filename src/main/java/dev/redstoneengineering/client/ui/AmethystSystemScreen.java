package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated HMI for amethyst source, exact filtering, tuned response, and spectrum observation. */
public final class AmethystSystemScreen extends EngineeringScreen<AmethystSystemMenu> {
    private Button primaryPrevious, primaryNext, secondaryPrevious, secondaryNext;
    private Button couplingPrevious, couplingNext, decayPrevious, decayNext, pulse;

    public AmethystSystemScreen(AmethystSystemMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int controlWidth = Math.max(100, Math.min(160, (imageWidth - 72) / 2));
        int leftX = leftPos + 24;
        int rightX = leftPos + imageWidth - 24 - controlWidth;
        int y = topPos + 120;
        primaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Primary"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftX,y,controlWidth,22).build());
        primaryNext = addConfigureWidget(Button.builder(Component.literal("Primary ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(rightX,y,controlWidth,22).build());
        secondaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Secondary"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_PREVIOUS)).bounds(leftX,y+46,controlWidth,22).build());
        secondaryNext = addConfigureWidget(Button.builder(Component.literal("Secondary ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_NEXT)).bounds(rightX,y+46,controlWidth,22).build());
        couplingPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Coupling"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_COUPLING_PREVIOUS)).bounds(leftX,y+92,controlWidth,22).build());
        couplingNext = addConfigureWidget(Button.builder(Component.literal("Coupling ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_COUPLING_NEXT)).bounds(rightX,y+92,controlWidth,22).build());
        decayPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Decay"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_DECAY_PREVIOUS)).bounds(leftX,y+138,controlWidth,22).build());
        decayNext = addConfigureWidget(Button.builder(Component.literal("Decay ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_DECAY_NEXT)).bounds(rightX,y+138,controlWidth,22).build());
        pulse = addConfigureWidget(Button.builder(Component.literal("Pulse / excite"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PULSE)).bounds(leftX,y+92,Math.max(180, imageWidth-48),22).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (primaryPrevious == null) return;
        boolean source = menu.kind()==AmethystSystemMenu.KIND_SOURCE;
        boolean filter = menu.kind()==AmethystSystemMenu.KIND_FILTER;
        boolean tuned = menu.kind()==AmethystSystemMenu.KIND_TUNED;
        boolean configure = isConfigureSection();
        boolean primary = source || filter || tuned;
        boolean secondary = source || tuned;
        primaryPrevious.active = primary;
        primaryNext.active = primary;
        primaryPrevious.visible = configure && primary;
        primaryNext.visible = configure && primary;
        secondaryPrevious.active = secondary;
        secondaryNext.active = secondary;
        secondaryPrevious.visible = configure && secondary;
        secondaryNext.visible = configure && secondary;
        couplingPrevious.active = tuned;
        couplingNext.active = tuned;
        decayPrevious.active = tuned;
        decayNext.active = tuned;
        couplingPrevious.visible = configure && tuned;
        couplingNext.visible = configure && tuned;
        decayPrevious.visible = configure && tuned;
        decayNext.visible = configure && tuned;
        pulse.active = source;
        pulse.visible = configure && source;
        if (source) {
            primaryPrevious.setMessage(Component.literal("◀ FREQ " + menu.primary())); primaryNext.setMessage(Component.literal("FREQ " + menu.primary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("◀ AMP " + menu.secondary())); secondaryNext.setMessage(Component.literal("AMP " + menu.secondary() + " ▶"));
            pulse.setMessage(Component.literal(menu.stateFlag()==1?"Pulse • ACTIVE":"Pulse"));
        } else if (filter) {
            primaryPrevious.setMessage(Component.literal("◀ TARGET " + menu.tertiary())); primaryNext.setMessage(Component.literal("TARGET " + menu.tertiary() + " ▶"));
        } else if (tuned) {
            primaryPrevious.setMessage(Component.literal("◀ f0 " + menu.tertiary())); primaryNext.setMessage(Component.literal("f0 " + menu.tertiary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("◀ Q " + menu.auxiliary())); secondaryNext.setMessage(Component.literal("Q " + menu.auxiliary() + " ▶"));
            couplingPrevious.setMessage(Component.literal("◀ C " + menu.extraG())); couplingNext.setMessage(Component.literal("C " + menu.extraG() + " ▶"));
            decayPrevious.setMessage(Component.literal("◀ D " + menu.extraH())); decayNext.setMessage(Component.literal("D " + menu.extraH() + " ▶"));
        }
    }

    @Override protected void renderSection(GuiGraphics g, Section section) {
        switch(section){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceName(), GOOD, 16,80); statusBadge(g, qualityName(), qualityColor(),205,80);
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){
            metricCard(g,"Freq index",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Peak A",menu.secondary()+" / 15",111,103,88,GOOD); metricCard(g,"Current A",menu.tertiary()+" / 15",206,103,88,INFO);
            labelValue(g,"Pulse state",menu.stateFlag()==1?"RINGING":"IDLE",149); labelValue(g,"Excitations",Integer.toString(menu.auxiliary()),165); labelValue(g,"Topology","FOUR-WAY SOURCE",181);
        } else if(menu.kind()==AmethystSystemMenu.KIND_FILTER){
            metricCard(g,"Input F idx",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Input amp",menu.secondary()+" / 15",111,103,88,GOOD); metricCard(g,"Target idx",Integer.toString(menu.tertiary()),206,103,88,INFO);
            labelValue(g,"Expected out",menu.auxiliary()+" / 15",149); labelValue(g,"Decision",menu.stateFlag()==1?"PASS":"REJECT",165); labelValue(g,"Series path",path(),181);
        } else if(menu.kind()==AmethystSystemMenu.KIND_TUNED){
            metricCard(g,"Input F idx",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Natural idx",Integer.toString(menu.tertiary()),111,103,88,GOOD); metricCard(g,"Actual A",menu.extraC()+" / 15",206,103,88,INFO);
            labelValue(g,"Q / bandwidth",menu.auxiliary()+" / ±"+menu.extraA(),149);
            labelValue(g,"Coupling / decay",menu.extraG()+" / "+menu.extraH()+" per 2t",165);
            labelValue(g,"Target / output F",menu.extraB()+" / "+menu.extraD(),181);
            labelValue(g,"State",tunedState(),197);
        } else {
            metricCard(g,"Dominant idx",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Energy",Integer.toString(menu.secondary()),111,103,88,GOOD); metricCard(g,"Active bands",Integer.toString(menu.tertiary()),206,103,88,INFO);
            labelValue(g,"Samples / conflicts",menu.auxiliary()+" / "+menu.extraA(),149); labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),165); labelValue(g,"Authority","OBSERVER ONLY",181);
        }
        safeText(g,"Frequency values are discrete model indices, not fabricated Hz units.",16,199,MUTED);
    }

    private void ports(GuiGraphics g){
        statusBadge(g,"AMETHYST INTERFACES",GOOD,16,80);
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){statusLine(g,"N/E/S/W","OUTPUT • AMETHYST RESONANCE",outputQualityColor(),112);}
        else if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){statusLine(g,"UP / LOCAL VOLUME","MEASUREMENT • OBSERVER ONLY",INFO,112);}
        else {statusLine(g,face(menu.facing().getOpposite()),"INPUT • AMETHYST RESONANCE",qualityColor(),112);statusLine(g,"PROCESS",menu.kind()==AmethystSystemMenu.KIND_FILTER?"EXACT FREQUENCY SELECTION":"TUNED RESONANT RESPONSE",INFO,140);statusLine(g,face(menu.facing()),"OUTPUT • AMETHYST RESONANCE • "+outputQualityName(),outputQualityColor(),168);}
    }

    private void configure(GuiGraphics g){
        statusBadge(g,"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);
        if(menu.kind()==AmethystSystemMenu.KIND_TUNED){
            labelValue(g,"Natural frequency index f0",Integer.toString(menu.tertiary()),100);
            labelValue(g,"Quality index Q",menu.auxiliary()+"  → bandwidth ±"+menu.extraA(),146);
            labelValue(g,"Drive coupling C",menu.extraG()+"  → resonant boost Q·C = "+(menu.auxiliary()*menu.extraG()),192);
            labelValue(g,"Free decay D",menu.extraH()+" amplitude / 2 ticks",238);
            sectionRule(g,286);
            int y=wrappedText(g,
                    "MODEL 1 • Δf = |fin - f0| ; B = 5 - Q. Q controls selectivity and driven response speed, while C controls drive coupling.",
                    16,302,760,TEXT)+12;
            y=wrappedText(g,
                    "MODEL 2 • Δf=0: Atarget = clamp(Ain + Q·C, 0..15). In-band: Atarget = clamp(Ain - max(1, Δf·Q) + (C-2), 0..15).",
                    16,y,760,TEXT)+12;
            y=wrappedText(g,
                    "MODEL 3 • Driven amplitude approaches Atarget by "+menu.extraF()+" per 2t. When trustworthy drive disappears, free ring-down approaches zero by D="+menu.extraH()+" per 2t.",
                    16,y,760,TEXT)+12;
            labelValue(g,"Rigid physical axis",path()+"  • RX fixed opposite TX",y);
            wrappedText(g,
                    "Route rotates the complete block axis only. This keeps the wiring legible in-world while the Parameters page carries the model complexity.",
                    16,y+24,760,MUTED);
        } else {
            labelValue(g,"Primary",primaryControl(),100);
            labelValue(g,"Secondary",secondaryControl(),146);
            if(menu.directional()) {
                labelValue(g,"Rigid I/O axis",path()+"  • RX opposite TX",192);
                wrappedText(g,"Use Route to rotate the complete block. The two physical ports stay opposite.",16,216,700,MUTED);
            }
        }
    }

    private void diagnostics(GuiGraphics g){
        statusBadge(g,qualityName(),qualityColor(),16,80);
        labelValue(g,"Device",deviceName(),104);
        labelValue(g,"Primary",primaryDiagnostic(),122);
        labelValue(g,"Secondary",secondaryDiagnostic(),140);
        if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            labelValue(g,"Conflicts",Integer.toString(menu.extraA()),158);
            labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),176);
        } else if(menu.kind()==AmethystSystemMenu.KIND_TUNED){
            labelValue(g,"Detune / bandwidth",menu.extraE()+" / ±"+menu.extraA(),158);
            labelValue(g,"Target / actual A",menu.extraB()+" / "+menu.extraC(),176);
            labelValue(g,"Output F / driven step",menu.extraD()+" / "+menu.extraF()+" per 2t",194);
            labelValue(g,"Coupling / free decay",menu.extraG()+" / "+menu.extraH()+" per 2t",212);
            labelValue(g,"Input / output quality",qualityName()+" / "+outputQualityName(),230);
            labelValue(g,"Path",path(),248);
        } else if(menu.kind()==AmethystSystemMenu.KIND_FILTER){
            labelValue(g,"Path",path(),158);
            labelValue(g,"Decision / Aout",(menu.stateFlag()==1?"PASS":"REJECT")+" / "+menu.auxiliary(),176);
            labelValue(g,"Input / output quality",qualityName()+" / "+outputQualityName(),194);
        } else {
            labelValue(g,"Current / peak A",menu.tertiary()+" / "+menu.secondary(),158);
            labelValue(g,"Excitations",Integer.toString(menu.auxiliary()),176);
        }
        statusLine(g,"Diagnosis",diagnosis(),diagnosisColor(),252);
        safeText(g,nextAction(),16,272,diagnosisColor());
    }

    private void history(GuiGraphics g){
        statusBadge(g,"RESONANCE EVIDENCE",INFO,16,80);
        if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            labelValue(g,"Samples",Integer.toString(menu.auxiliary()),110);
            labelValue(g,"Conflicts",Integer.toString(menu.extraA()),130);
            labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),150);
            labelValue(g,"Dominant / active",menu.primary()+" / "+menu.tertiary(),170);
            safeText(g,"Only a complete aperture scan replaces the retained numerical spectrum; incomplete coverage is STALE rather than invented zero data.",16,194,MUTED);
        } else if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){
            labelValue(g,"Configured frequency",Integer.toString(menu.primary()),110);
            labelValue(g,"Configured peak A",menu.secondary()+" / 15",130);
            labelValue(g,"Current ring-down A",menu.tertiary()+" / 15",150);
            labelValue(g,"Excitation count",Integer.toString(menu.auxiliary()),170);
            labelValue(g,"Decay model","A[k+1] = max(0, A[k] - 1)",190);
            labelValue(g,"Update interval","2 ticks",210);
        } else if(menu.kind()==AmethystSystemMenu.KIND_FILTER){
            labelValue(g,"Exact selector","pass only when fin = ftarget",110);
            labelValue(g,"Pass amplitude","Aout = max(0, Ain - 1)",130);
            labelValue(g,"Reject amplitude","Aout = 0",150);
            labelValue(g,"Current input / target",menu.primary()+" / "+menu.tertiary(),170);
            labelValue(g,"Current expected Aout",Integer.toString(menu.auxiliary()),190);
        } else {
            labelValue(g,"Detune","Δf = |fin - f0| = "+menu.extraE(),110);
            labelValue(g,"Bandwidth","B = 5 - Q = "+menu.extraA(),130);
            labelValue(g,"Coupling","C = "+menu.extraG()+" ; nominal C=2 preserves baseline transfer",150);
            labelValue(g,"On resonance","Atarget = clamp(Ain + Q·C, 0..15)",170);
            labelValue(g,"In band","Atarget = clamp(Ain - max(1, Δf·Q) + (C-2), 0..15)",190);
            labelValue(g,"Out of band","Atarget = 0",210);
            labelValue(g,"Driven dynamics","A approaches target by "+menu.extraF()+" every 2t",230);
            labelValue(g,"Free ring-down","A[k+1] = max(0, A[k] - D), D="+menu.extraH(),250);
            labelValue(g,"Target / actual",menu.extraB()+" / "+menu.extraC(),270);
            labelValue(g,"Output frequency",Integer.toString(menu.extraD()),290);
            wrappedText(g,
                    "Known drive removal rings freely at f0. STALE/FAULT/TOPOLOGY_ERROR evidence freezes retained state and withholds the network driver instead of inventing a removal event.",
                    16,316,780,MUTED);
        }
    }

    private String diagnosis(){
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE) return menu.stateFlag()==1?"SOURCE RING-DOWN ACTIVE":"SOURCE IDLE • READY FOR EXCITATION";
        if(menu.kind()==AmethystSystemMenu.KIND_TUNED && menu.stateFlag()==3) return "FREE RING-DOWN • STORED RESONANT ENERGY";
        if(menu.quality()==PortQuality.TOPOLOGY_ERROR) return "SOURCE CONFLICT / resonance topology ambiguous";
        if(menu.quality()==PortQuality.NO_SIGNAL) return "NO RESONANCE EVIDENCE";
        if(menu.quality()==PortQuality.STALE) return "STALE RESONANCE EVIDENCE";
        if(menu.kind()==AmethystSystemMenu.KIND_FILTER) {
            if(menu.primary()!=menu.tertiary()) return "FREQUENCY REJECT • input does not match selected band";
            return menu.auxiliary()>0 ? "FREQUENCY PASS • selected band present" : "MATCHED BAND • zero amplitude";
        }
        if(menu.kind()==AmethystSystemMenu.KIND_TUNED) {
            int detune=Math.abs(menu.primary()-menu.tertiary());
            if(menu.stateFlag()==2) return "RESONANT RESPONSE SATURATED";
            if(detune<=menu.extraA()) return "IN-BAND RESONANT RESPONSE";
            return "OUT-OF-BAND / DETUNED";
        }
        if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM) {
            if(menu.extraA()>0) return "SPECTRUM CONFLICT • overlapping source evidence";
            if(menu.tertiary()==0) return "QUIET SPECTRUM";
            if(menu.tertiary()==1) return "SINGLE-BAND RESONANCE";
            return "MULTI-BAND RESONANCE";
        }
        return menu.secondary()==0 ? "SOURCE CONFIGURED • zero amplitude" : "SOURCE ACTIVE";
    }

    private String nextAction(){
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE) return menu.stateFlag()==1
                ?"NEXT • observe the server ring-down or re-excite to the configured peak amplitude."
                :"NEXT • pulse the source when you want a real resonance excitation.";
        if(menu.kind()==AmethystSystemMenu.KIND_TUNED && menu.stateFlag()==3) return "NEXT • observe free decay at the natural index before applying another drive.";
        if(menu.quality()==PortQuality.TOPOLOGY_ERROR) return "NEXT • isolate competing resonance sources before interpreting frequency.";
        if(menu.quality()==PortQuality.NO_SIGNAL||menu.quality()==PortQuality.STALE) return "NEXT • restore current resonance evidence before tuning the device.";
        if(menu.kind()==AmethystSystemMenu.KIND_FILTER && menu.primary()!=menu.tertiary()) return "NEXT • align target index with the carrier or intentionally keep this rejection band.";
        if(menu.kind()==AmethystSystemMenu.KIND_TUNED && Math.abs(menu.primary()-menu.tertiary())>menu.extraA()) return "NEXT • retune natural index or widen the modeled response band via Q.";
        if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM && menu.extraA()>0) return "NEXT • separate conflicting sources, then rescan the spectrum.";
        return "NEXT • resonance evidence is coherent; compare amplitude/response before changing topology.";
    }

    private int diagnosisColor(){String d=diagnosis();return d.contains("CONFLICT")||d.contains("STALE")||d.contains("NO ")||d.contains("REJECT")||d.contains("OUT-OF-BAND")||d.contains("SATURATED")?WARN:GOOD;}
    private String deviceName(){return switch(menu.kind()){case AmethystSystemMenu.KIND_SOURCE->"AMETHYST RESONATOR";case AmethystSystemMenu.KIND_FILTER->"AMETHYST FREQUENCY FILTER";case AmethystSystemMenu.KIND_TUNED->"TUNED AMETHYST RESONATOR";case AmethystSystemMenu.KIND_SPECTRUM->"AMETHYST SPECTRUM ANALYZER";default->"AMETHYST DEVICE";};}
    private String primaryControl(){return switch(menu.kind()){case AmethystSystemMenu.KIND_SOURCE->"FREQUENCY INDEX "+menu.primary();case AmethystSystemMenu.KIND_FILTER->"TARGET INDEX "+menu.tertiary();case AmethystSystemMenu.KIND_TUNED->"NATURAL INDEX "+menu.tertiary();default->"READ ONLY";};}
    private String secondaryControl(){return switch(menu.kind()){case AmethystSystemMenu.KIND_SOURCE->"AMPLITUDE "+menu.secondary()+"/15";case AmethystSystemMenu.KIND_TUNED->"Q INDEX "+menu.auxiliary();default->"NONE";};}
    private String primaryDiagnostic(){return menu.kind()==AmethystSystemMenu.KIND_SPECTRUM?"dominant index="+menu.primary():"input/source index="+menu.primary();}
    private String secondaryDiagnostic(){return menu.kind()==AmethystSystemMenu.KIND_TUNED?"Atarget/actual="+menu.extraB()+"/"+menu.extraC():menu.kind()==AmethystSystemMenu.KIND_SOURCE?"peak/current="+menu.secondary()+"/"+menu.tertiary():"value="+menu.secondary();}
    private String path(){return face(menu.facing().getOpposite())+" → "+face(menu.facing());}
    private String qualityName(){return menu.quality().name().replace('_',' ');}
    private int qualityColor(){return portQualityColor(menu.quality());}
    private String outputQualityName(){return menu.outputQuality().name().replace('_',' ');}
    private int outputQualityColor(){return portQualityColor(menu.outputQuality());}
    private int portQualityColor(PortQuality quality){return quality==PortQuality.VALID?GOOD:quality==PortQuality.NO_SIGNAL||quality==PortQuality.STALE||quality==PortQuality.SATURATED?WARN:BAD;}
    private String tunedState(){return switch(menu.stateFlag()){case 1->"DRIVEN";case 2->"TARGET SATURATED";case 3->"FREE RING-DOWN";default->"IDLE";};}
    private String face(Direction d){return d.getName().toUpperCase();}
}
