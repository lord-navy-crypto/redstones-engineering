package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated HMI for amethyst source, exact filtering, tuned response, and spectrum observation. */
public final class AmethystSystemScreen extends EngineeringScreen<AmethystSystemMenu> {
    private Button primaryPrevious, primaryNext, secondaryPrevious, secondaryNext, pulse;
    private EditBox primaryInput, secondaryInput;
    private Button primaryApply, secondaryApply;

    public AmethystSystemScreen(AmethystSystemMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y = topPos + imageHeight - 94;
        primaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Primary"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        primaryNext = addConfigureWidget(Button.builder(Component.literal("Primary ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(leftPos+199,y,105,20).build());
        secondaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Secondary"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_PREVIOUS)).bounds(leftPos+16,y+26,105,20).build());
        secondaryNext = addConfigureWidget(Button.builder(Component.literal("Secondary ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_NEXT)).bounds(leftPos+199,y+26,105,20).build());
        pulse = addConfigureWidget(Button.builder(Component.literal("Pulse"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PULSE)).bounds(leftPos+70,y+52,180,20).build());
        primaryInput = addConfigureWidget(new EditBox(this.font,leftPos+16,y,105,20,Component.literal("Exact primary value")));
        primaryInput.setMaxLength(2);
        primaryInput.setFilter(v->v.isEmpty()||v.chars().allMatch(Character::isDigit));
        primaryApply = addConfigureWidget(Button.builder(Component.literal("Apply primary"),b->submitPrimary()).bounds(leftPos+199,y,105,20).build());
        secondaryInput = addConfigureWidget(new EditBox(this.font,leftPos+16,y+26,105,20,Component.literal("Exact secondary value")));
        secondaryInput.setMaxLength(2);
        secondaryInput.setFilter(v->v.isEmpty()||v.chars().allMatch(Character::isDigit));
        secondaryApply = addConfigureWidget(Button.builder(Component.literal("Apply secondary"),b->submitSecondary()).bounds(leftPos+199,y+26,105,20).build());
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
        primaryPrevious.visible = false;
        primaryNext.visible = false;
        secondaryPrevious.active = secondary;
        secondaryNext.active = secondary;
        secondaryPrevious.visible = false;
        secondaryNext.visible = false;
        primaryInput.visible=primaryInput.active=configure&&primary;
        primaryApply.visible=configure&&primary;
        primaryApply.active=configure&&primary&&primaryInputValid();
        secondaryInput.visible=secondaryInput.active=configure&&secondary;
        secondaryApply.visible=configure&&secondary;
        secondaryApply.active=configure&&secondary&&secondaryInputValid();
        if(configure&&primary&&!primaryInput.isFocused()){
            String expected=Integer.toString(primaryVisibleValue());
            if(!expected.equals(primaryInput.getValue()))primaryInput.setValue(expected);
        }
        if(configure&&secondary&&!secondaryInput.isFocused()){
            String expected=Integer.toString(secondaryVisibleValue());
            if(!expected.equals(secondaryInput.getValue()))secondaryInput.setValue(expected);
        }
        primaryApply.setMessage(Component.literal("Apply "+primarySymbol()));
        secondaryApply.setMessage(Component.literal("Apply "+secondarySymbol()));
        pulse.active = source;
        pulse.visible = configure && source;
        if (source) {
            primaryPrevious.setMessage(Component.literal("◀ FREQ " + menu.primary())); primaryNext.setMessage(Component.literal("FREQ " + menu.primary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("◀ AMP " + menu.secondary())); secondaryNext.setMessage(Component.literal("AMP " + menu.secondary() + " ▶"));
            pulse.setMessage(Component.literal(menu.stateFlag()==1?"Pulse • ACTIVE":"Pulse"));
        } else if (filter) {
            primaryPrevious.setMessage(Component.literal("◀ TARGET " + menu.tertiary())); primaryNext.setMessage(Component.literal("TARGET " + menu.tertiary() + " ▶"));
        } else if (tuned) {
            primaryPrevious.setMessage(Component.literal("◀ F0 " + menu.tertiary())); primaryNext.setMessage(Component.literal("F0 " + menu.tertiary() + " ▶"));
            secondaryPrevious.setMessage(Component.literal("◀ Q " + menu.auxiliary())); secondaryNext.setMessage(Component.literal("Q " + menu.auxiliary() + " ▶"));
        }
    }

    private int primaryVisibleValue(){
        return switch(menu.kind()){
            case AmethystSystemMenu.KIND_SOURCE -> menu.primary();
            case AmethystSystemMenu.KIND_FILTER, AmethystSystemMenu.KIND_TUNED -> menu.tertiary();
            default -> 0;
        };
    }

    private int secondaryVisibleValue(){
        return menu.kind()==AmethystSystemMenu.KIND_SOURCE?menu.secondary():menu.auxiliary();
    }

    private String primarySymbol(){
        return switch(menu.kind()){
            case AmethystSystemMenu.KIND_SOURCE -> "f_idx";
            case AmethystSystemMenu.KIND_FILTER -> "f_target";
            case AmethystSystemMenu.KIND_TUNED -> "f0";
            default -> "value";
        };
    }

    private String secondarySymbol(){
        return menu.kind()==AmethystSystemMenu.KIND_SOURCE?"A":"Q_idx";
    }

    private boolean primaryInputValid(){
        if(primaryInput==null||primaryInput.getValue().isEmpty())return false;
        try{int v=Integer.parseInt(primaryInput.getValue());return v>=1&&v<=15;}catch(NumberFormatException ignored){return false;}
    }

    private boolean secondaryInputValid(){
        if(secondaryInput==null||secondaryInput.getValue().isEmpty())return false;
        try{
            int v=Integer.parseInt(secondaryInput.getValue());
            return menu.kind()==AmethystSystemMenu.KIND_SOURCE?v>=1&&v<=15:menu.kind()==AmethystSystemMenu.KIND_TUNED&&v>=1&&v<=4;
        }catch(NumberFormatException ignored){return false;}
    }

    private void submitPrimary(){
        if(!primaryInputValid())return;
        sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_DIRECT_BASE+Integer.parseInt(primaryInput.getValue()));
        primaryInput.setFocused(false);
    }

    private void submitSecondary(){
        if(!secondaryInputValid())return;
        sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_DIRECT_BASE+Integer.parseInt(secondaryInput.getValue()));
        secondaryInput.setFocused(false);
    }

    @Override protected void renderSection(GuiGraphics g, Section section) {
        switch(section){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceName(), GOOD, 16,80); statusBadge(g, qualityName(), qualityColor(),205,80);
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){
            metricCard(g,"Freq index",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Amplitude",menu.secondary()+" / 15",111,103,88,GOOD); metricCard(g,"Pulse",menu.stateFlag()==1?"ACTIVE":"IDLE",206,103,88,INFO);
            labelValue(g,"Topology","FOUR-WAY SOURCE",149); labelValue(g,"Role","BASE RESONANCE SOURCE",165); labelValue(g,"Evidence",qualityName(),181);
        } else if(menu.kind()==AmethystSystemMenu.KIND_FILTER){
            metricCard(g,"Input F idx",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Input amp",menu.secondary()+" / 15",111,103,88,GOOD); metricCard(g,"Target idx",Integer.toString(menu.tertiary()),206,103,88,INFO);
            labelValue(g,"Expected out",menu.auxiliary()+" / 15",149); labelValue(g,"Decision",menu.stateFlag()==1?"PASS":"REJECT",165); labelValue(g,"Series path",path(),181);
        } else if(menu.kind()==AmethystSystemMenu.KIND_TUNED){
            metricCard(g,"Input F idx",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Natural idx",Integer.toString(menu.tertiary()),111,103,88,GOOD); metricCard(g,"Q index",Integer.toString(menu.auxiliary()),206,103,88,INFO);
            labelValue(g,"Bandwidth index","±"+menu.extraA(),149); labelValue(g,"Expected output",menu.extraB()+" / 15",165); labelValue(g,"State",menu.stateFlag()==2?"SATURATED":menu.stateFlag()==1?"RESPONDING":"IDLE",181);
        } else {
            metricCard(g,"Dominant idx",Integer.toString(menu.primary()),16,103,88,INFO); metricCard(g,"Energy",Integer.toString(menu.secondary()),111,103,88,GOOD); metricCard(g,"Active bands",Integer.toString(menu.tertiary()),206,103,88,INFO);
            labelValue(g,"Samples / conflicts",menu.auxiliary()+" / "+menu.extraA(),149); labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),165); labelValue(g,"Authority","OBSERVER ONLY",181);
        }
        safeText(g,"Frequency values are discrete model indices, not fabricated Hz units.",16,199,MUTED);
    }

    private void ports(GuiGraphics g){
        statusBadge(g,"AMETHYST INTERFACES",GOOD,16,80);
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){statusLine(g,"N/E/S/W","OUTPUT • AMETHYST RESONANCE",GOOD,112);}
        else if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){statusLine(g,"UP / LOCAL VOLUME","MEASUREMENT • OBSERVER ONLY",INFO,112);}
        else {statusLine(g,face(menu.facing().getOpposite()),"INPUT • AMETHYST RESONANCE",qualityColor(),112);statusLine(g,"PROCESS",menu.kind()==AmethystSystemMenu.KIND_FILTER?"EXACT FREQUENCY SELECTION":"TUNED RESONANT RESPONSE",INFO,140);statusLine(g,face(menu.facing()),"OUTPUT • AMETHYST RESONANCE",GOOD,168);}
    }

    private void configure(GuiGraphics g){
        statusBadge(g,"PIONEER PATTERN • RESONANCE MODEL",INFO,16,80);
        formulaCard(g,resonanceEquation(),105);
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){
            variableRole(g,"ADJUSTABLE","f_idx",Integer.toString(menu.primary()),"1..15 discrete index • direct entry",134);
            variableRole(g,"ADJUSTABLE","A",Integer.toString(menu.secondary()),"1..15 amplitude • direct entry",152);
            variableRole(g,"ACTION","pulse",menu.stateFlag()==1?"ACTIVE":"IDLE","",170);
            variableRole(g,"EVIDENCE","quality",qualityName(),"",188);
        }else if(menu.kind()==AmethystSystemMenu.KIND_FILTER){
            variableRole(g,"MEASURED","f_in",Integer.toString(menu.primary()),"discrete index",134);
            variableRole(g,"MEASURED","A_in",Integer.toString(menu.secondary()),"0..15",152);
            variableRole(g,"ADJUSTABLE","f_target",Integer.toString(menu.tertiary()),"1..15 discrete index • direct entry",170);
            variableRole(g,"DERIVED","A_out",Integer.toString(menu.auxiliary()),"0..15",188);
            variableRole(g,"EVIDENCE","decision",menu.stateFlag()==1?"PASS":"REJECT","",206);
        }else if(menu.kind()==AmethystSystemMenu.KIND_TUNED){
            variableRole(g,"MEASURED","f_in",Integer.toString(menu.primary()),"discrete index",134);
            variableRole(g,"ADJUSTABLE","f0",Integer.toString(menu.tertiary()),"1..15 natural index • direct entry",152);
            variableRole(g,"ADJUSTABLE","Q_idx",Integer.toString(menu.auxiliary()),"1..4 • direct entry",170);
            variableRole(g,"DERIVED","BW",Integer.toString(menu.extraA()),"± index",188);
            variableRole(g,"DERIVED","A_out",Integer.toString(menu.extraB()),"0..15",206);
        }else{
            variableRole(g,"MEASURED","f_dom",Integer.toString(menu.primary()),"dominant index",134);
            variableRole(g,"MEASURED","E",Integer.toString(menu.secondary()),"spectrum energy",152);
            variableRole(g,"MEASURED","bands",Integer.toString(menu.tertiary()),"active",170);
            variableRole(g,"EVIDENCE","coverage",menu.extraB()+" / "+menu.stateFlag(),"cells",188);
        }
        wrappedText(g,"Frequency values are deliberate model indices, not fabricated Hz. Physical resonance direction stays on Route; spectrum state is server-observed.",16,232,workspaceWidth()-24,MUTED);
    }

    private void diagnostics(GuiGraphics g){
        statusBadge(g,qualityName(),qualityColor(),16,80);
        labelValue(g,"Device",deviceName(),104);
        labelValue(g,"Primary",primaryDiagnostic(),122);
        labelValue(g,"Secondary",secondaryDiagnostic(),140);
        if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            labelValue(g,"Conflicts",Integer.toString(menu.extraA()),158);
            labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),176);
        } else if(menu.directional()){
            labelValue(g,"Path",path(),158);
            labelValue(g,"Output evidence",menu.kind()==AmethystSystemMenu.KIND_TUNED?(menu.stateFlag()==2?"SATURATED":"BOUNDED"):(menu.stateFlag()==1?"PASS":"REJECT"),176);
        }
        statusLine(g,"Diagnosis",diagnosis(),diagnosisColor(),194);
        safeText(g,nextAction(),16,214,diagnosisColor());
    }

    private void history(GuiGraphics g){
        statusBadge(g,"RESONANCE EVIDENCE",INFO,16,80);
        if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){
            labelValue(g,"Samples",Integer.toString(menu.auxiliary()),110);
            labelValue(g,"Conflicts",Integer.toString(menu.extraA()),130);
            labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),150);
            labelValue(g,"Dominant / active",menu.primary()+" / "+menu.tertiary(),170);
            safeText(g,diagnosis(),16,190,diagnosisColor());
        }else{
            safeText(g,"Current server resonance evidence is shown; no client-side spectrum/history is invented.",16,112,MUTED);
            safeText(g,diagnosis(),16,136,diagnosisColor());
        }
    }

    private String resonanceEquation(){
        if(menu.kind()==AmethystSystemMenu.KIND_FILTER){
            return "A_out = (f_in = f_target) ? max(0, A_in - 1) : 0";
        }
        if(menu.kind()==AmethystSystemMenu.KIND_TUNED){
            return "BW = 5 - Q ; Δf = |f_in - f0| ; response depends on Δf within BW";
        }
        if(menu.kind()==AmethystSystemMenu.KIND_SOURCE){
            return "carrier = (f_idx, A) on the Amethyst resonance domain";
        }
        return "spectrum = dominant index + energy + active-band evidence";
    }

    private String diagnosis(){
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
    private String secondaryDiagnostic(){return menu.kind()==AmethystSystemMenu.KIND_TUNED?"Aout="+menu.extraB()+"/15":"value="+menu.secondary();}
    private String path(){return face(menu.facing().getOpposite())+" → "+face(menu.facing());}
    private String qualityName(){return menu.quality().name().replace('_',' ');} private int qualityColor(){return menu.quality()==PortQuality.VALID?GOOD:menu.quality()==PortQuality.NO_SIGNAL||menu.quality()==PortQuality.STALE?WARN:BAD;}
    private String face(Direction d){return d.getName().toUpperCase();}
}
