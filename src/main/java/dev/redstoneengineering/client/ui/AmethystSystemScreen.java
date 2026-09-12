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
    private Button primaryPrevious, primaryNext, secondaryPrevious, secondaryNext, directionCycle, pulse;

    public AmethystSystemScreen(AmethystSystemMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 111;
        primaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Primary"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        primaryNext = addConfigureWidget(Button.builder(Component.literal("Primary ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(leftPos+199,y,105,20).build());
        secondaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Secondary"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_PREVIOUS)).bounds(leftPos+16,y+26,105,20).build());
        secondaryNext = addConfigureWidget(Button.builder(Component.literal("Secondary ▶"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_SECONDARY_NEXT)).bounds(leftPos+199,y+26,105,20).build());
        pulse = addConfigureWidget(Button.builder(Component.literal("Pulse"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_PULSE)).bounds(leftPos+70,y+52,180,20).build());
        directionCycle = addConfigureWidget(Button.builder(Component.literal("Direction • —"), b -> sendMenuButton(AmethystSystemMenu.BUTTON_ROTATE_RIGHT)).bounds(leftPos+70,y+52,180,20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (primaryPrevious == null) return;
        boolean source = menu.kind()==AmethystSystemMenu.KIND_SOURCE;
        boolean filter = menu.kind()==AmethystSystemMenu.KIND_FILTER;
        boolean tuned = menu.kind()==AmethystSystemMenu.KIND_TUNED;
        boolean spectrum = menu.kind()==AmethystSystemMenu.KIND_SPECTRUM;
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
        pulse.active = source;
        pulse.visible = configure && source;
        directionCycle.active = menu.directional();
        directionCycle.visible = configure && menu.directional();

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
        if (!spectrum && menu.directional()) {
            directionCycle.setMessage(Component.literal("Direction • " + face(menu.facing())));
            directionCycle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    "Cycle the complete amethyst input/output axis clockwise on the server.")));
        }
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

    private void configure(GuiGraphics g){statusBadge(g,"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);labelValue(g,"Primary",primaryControl(),98);labelValue(g,"Secondary",secondaryControl(),181);if(menu.directional())labelValue(g,"I/O axis",path(),197);}
    private void diagnostics(GuiGraphics g){statusBadge(g,qualityName(),qualityColor(),16,80);labelValue(g,"Device",deviceName(),108);labelValue(g,"Primary",primaryDiagnostic(),126);labelValue(g,"Secondary",secondaryDiagnostic(),144);if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){labelValue(g,"Conflicts",Integer.toString(menu.extraA()),162);labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),180);}else if(menu.directional()){labelValue(g,"Path",path(),162);labelValue(g,"Output evidence",menu.kind()==AmethystSystemMenu.KIND_TUNED?(menu.stateFlag()==2?"SATURATED":"BOUNDED"):(menu.stateFlag()==1?"PASS":"REJECT"),180);}statusLine(g,"Authority","SERVER SYNCHRONIZED",GOOD,200);}
    private void history(GuiGraphics g){statusBadge(g,"RESONANCE EVIDENCE",INFO,16,80);if(menu.kind()==AmethystSystemMenu.KIND_SPECTRUM){labelValue(g,"Samples",Integer.toString(menu.auxiliary()),110);labelValue(g,"Conflicts",Integer.toString(menu.extraA()),130);labelValue(g,"Coverage",menu.extraB()+" / "+menu.stateFlag(),150);}else{safeText(g,"Current server resonance evidence is shown; no client-side spectrum/history is invented.",16,112,MUTED);}}

    private String deviceName(){return switch(menu.kind()){case AmethystSystemMenu.KIND_SOURCE->"AMETHYST RESONATOR";case AmethystSystemMenu.KIND_FILTER->"AMETHYST FREQUENCY FILTER";case AmethystSystemMenu.KIND_TUNED->"TUNED AMETHYST RESONATOR";case AmethystSystemMenu.KIND_SPECTRUM->"AMETHYST SPECTRUM ANALYZER";default->"AMETHYST DEVICE";};}
    private String primaryControl(){return switch(menu.kind()){case AmethystSystemMenu.KIND_SOURCE->"FREQUENCY INDEX "+menu.primary();case AmethystSystemMenu.KIND_FILTER->"TARGET INDEX "+menu.tertiary();case AmethystSystemMenu.KIND_TUNED->"NATURAL INDEX "+menu.tertiary();default->"READ ONLY";};}
    private String secondaryControl(){return switch(menu.kind()){case AmethystSystemMenu.KIND_SOURCE->"AMPLITUDE "+menu.secondary()+"/15";case AmethystSystemMenu.KIND_TUNED->"Q INDEX "+menu.auxiliary();default->"NONE";};}
    private String primaryDiagnostic(){return menu.kind()==AmethystSystemMenu.KIND_SPECTRUM?"dominant index="+menu.primary():"input/source index="+menu.primary();}
    private String secondaryDiagnostic(){return menu.kind()==AmethystSystemMenu.KIND_TUNED?"Aout="+menu.extraB()+"/15":"value="+menu.secondary();}
    private String path(){return face(menu.facing().getOpposite())+" → "+face(menu.facing());}
    private String qualityName(){return menu.quality().name().replace('_',' ');} private int qualityColor(){return menu.quality()==PortQuality.VALID?GOOD:menu.quality()==PortQuality.NO_SIGNAL||menu.quality()==PortQuality.STALE?WARN:BAD;}
    private String face(Direction d){return d.getName().toUpperCase();}
}
