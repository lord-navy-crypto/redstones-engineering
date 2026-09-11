package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated magnetic HMI separating source, actuator, converter and observer responsibilities. */
public final class MagneticSystemScreen extends EngineeringScreen<MagneticSystemMenu> {
    private Button primaryPrevious, primaryNext, rotateLeft, rotateRight;

    public MagneticSystemScreen(MagneticSystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 116;
        primaryPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"), b -> sendMenuButton(MagneticSystemMenu.BUTTON_PRIMARY_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        primaryNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"), b -> sendMenuButton(MagneticSystemMenu.BUTTON_PRIMARY_NEXT)).bounds(leftPos+199,y,105,20).build());
        rotateLeft = addConfigureWidget(Button.builder(Component.literal("↺ Axis"), b -> sendMenuButton(MagneticSystemMenu.BUTTON_ROTATE_LEFT)).bounds(leftPos+70,y+30,80,20).build());
        rotateRight = addConfigureWidget(Button.builder(Component.literal("Axis ↻"), b -> sendMenuButton(MagneticSystemMenu.BUTTON_ROTATE_RIGHT)).bounds(leftPos+170,y+30,80,20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (primaryPrevious == null) return;
        boolean permanent = menu.kind() == MagneticSystemMenu.KIND_PERMANENT;
        boolean coil = menu.kind() == MagneticSystemMenu.KIND_COIL;
        primaryPrevious.active = permanent || coil;
        primaryNext.active = permanent || coil;
        rotateLeft.active = permanent || coil;
        rotateRight.active = permanent || coil;
        if (permanent) {
            primaryPrevious.setMessage(Component.literal("◀ B " + menu.primary()));
            primaryNext.setMessage(Component.literal("B " + menu.primary() + " ▶"));
            rotateLeft.setMessage(Component.literal("↺ N marker"));
            rotateRight.setMessage(Component.literal("N marker ↻"));
        } else if (coil) {
            primaryPrevious.setMessage(Component.literal("◀ N×" + menu.tertiary()));
            primaryNext.setMessage(Component.literal("N×" + menu.tertiary() + " ▶"));
            rotateLeft.setMessage(Component.literal("↺ I/O"));
            rotateRight.setMessage(Component.literal("I/O ↻"));
        } else {
            primaryPrevious.setMessage(Component.literal("Read only"));
            primaryNext.setMessage(Component.literal("Read only"));
            rotateLeft.setMessage(Component.literal("No axis control"));
            rotateRight.setMessage(Component.literal("No axis control"));
        }
    }

    @Override protected void renderSection(GuiGraphics g, Section section) {
        switch(section){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceName(), GOOD, 16, 80);
        statusBadge(g, qualityName(), qualityColor(), 205, 80);
        switch (menu.kind()) {
            case MagneticSystemMenu.KIND_ELECTROMAGNET -> {
                metricCard(g,"Field",menu.primary()+" / 15",16,103,88,GOOD);
                metricCard(g,"Copper V",menu.secondary()+" / 15",111,103,88,INFO);
                metricCard(g,"Feeds",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Role","COPPER-POWERED ACTUATOR",149);
                labelValue(g,"Topology","6× COPPER INPUT • FREE-SPACE FIELD",165);
                labelValue(g,"Evidence",qualityName(),181);
            }
            case MagneticSystemMenu.KIND_PERMANENT -> {
                metricCard(g,"Field source",menu.primary()+" / 15",16,103,88,GOOD);
                metricCard(g,"N marker",face(menu.facing()),111,103,88,INFO);
                metricCard(g,"Wired","NO",206,103,88,INFO);
                labelValue(g,"Role","STATIC FREE-SPACE SOURCE",149);
                labelValue(g,"Model","SCALAR FIELD",165);
                labelValue(g,"Evidence","VALID CONFIGURATION",181);
            }
            case MagneticSystemMenu.KIND_COIL -> {
                metricCard(g,"Field",menu.primary()+" / 15",16,103,88,INFO);
                metricCard(g,"EMF",menu.secondary()+" / 15",111,103,88,GOOD);
                metricCard(g,"Turns idx",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Contract","IRON_MAGNETIC → COPPER",149);
                labelValue(g,"Series path",face(menu.facing().getOpposite())+" → "+face(menu.facing()),165);
                labelValue(g,"Baseline",menu.complete()?"VALID":"STALE COVERAGE",181);
            }
            case MagneticSystemMenu.KIND_FIELD_SENSOR -> {
                metricCard(g,"Field",menu.primary()+" / 15",16,103,88,GOOD);
                metricCard(g,"Scanned",Integer.toString(menu.secondary()),111,103,88,INFO);
                metricCard(g,"Expected",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Role","OBSERVER ONLY",149);
                labelValue(g,"Coverage",menu.complete()?"COMPLETE":"INCOMPLETE",165);
                labelValue(g,"Evidence",qualityName(),181);
            }
            default -> {
                metricCard(g,"ΔBx",Integer.toString(menu.primary()),16,103,88,INFO);
                metricCard(g,"ΔBy",Integer.toString(menu.secondary()),111,103,88,INFO);
                metricCard(g,"ΔBz",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Local B",menu.auxiliary()+" / 15",149);
                labelValue(g,"Coverage cells",Integer.toString(menu.extra()),165);
                labelValue(g,"Role","DIFFERENTIAL OBSERVER",181);
            }
        }
        g.drawString(font, "A measured zero is VALID whenever magnetic coverage/evidence is valid.", 16, 199, menu.complete()?GOOD:MUTED, false);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g,"MAGNETIC INTERFACES",GOOD,16,80);
        switch(menu.kind()) {
            case MagneticSystemMenu.KIND_ELECTROMAGNET -> {
                statusLine(g,"ALL 6 FACES","INPUT • COPPER COIL VOLTAGE",qualityColor(),112);
                statusLine(g,"FREE SPACE","PHYSICAL MAGNETIC FIELD • NOT A WIRED PORT",INFO,142);
            }
            case MagneticSystemMenu.KIND_PERMANENT -> {
                statusLine(g,"ALL 6 FACES","OUTPUT EVIDENCE • FREE-SPACE MAGNETIC FIELD",GOOD,112);
                statusLine(g,"WIRED","NONE",INFO,142);
            }
            case MagneticSystemMenu.KIND_COIL -> {
                statusLine(g,face(menu.facing().getOpposite()),"INPUT • MAGNETIC SENSE",qualityColor(),112);
                statusLine(g,face(menu.facing()),"OUTPUT • INDUCED COPPER VOLTAGE",qualityColor(),142);
            }
            case MagneticSystemMenu.KIND_FIELD_SENSOR -> {
                statusLine(g,"ALL 6 APERTURES","MEASUREMENT INPUT • MAGNETIC FIELD",qualityColor(),112);
                statusLine(g,"AUTHORITY","OBSERVER ONLY • NO DRIVER",INFO,142);
            }
            default -> {
                statusLine(g,"±X / ±Y / ±Z","MEASUREMENT INPUT • FIELD GRADIENT",qualityColor(),112);
                statusLine(g,"AUTHORITY","OBSERVER ONLY • NO DRIVER",INFO,142);
            }
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g,"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);
        if(menu.kind()==MagneticSystemMenu.KIND_PERMANENT){labelValue(g,"Strength",menu.primary()+" / 15",101);labelValue(g,"N marker",face(menu.facing()),171);}
        else if(menu.kind()==MagneticSystemMenu.KIND_COIL){labelValue(g,"Turns index",Integer.toString(menu.tertiary()),101);labelValue(g,"I/O axis",face(menu.facing().getOpposite())+" → "+face(menu.facing()),171);}
        else {labelValue(g,"Configuration","READ ONLY / PHYSICS-DRIVEN",101);labelValue(g,"Network authority",observerOrActuator(),171);}
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g,qualityName(),qualityColor(),16,80);
        labelValue(g,"Device role",role(),106);
        if(menu.kind()==MagneticSystemMenu.KIND_GRADIENT){labelValue(g,"Gradient X / Y / Z",menu.primary()+" / "+menu.secondary()+" / "+menu.tertiary(),126);labelValue(g,"Local field",Integer.toString(menu.auxiliary()),146);labelValue(g,"Coverage",menu.complete()?"COMPLETE":"INCOMPLETE",166);}
        else if(menu.kind()==MagneticSystemMenu.KIND_FIELD_SENSOR){labelValue(g,"Field",Integer.toString(menu.primary()),126);labelValue(g,"Coverage",menu.secondary()+" / "+menu.tertiary(),146);labelValue(g,"Validity",menu.complete()?"VALID":"STALE",166);}
        else if(menu.kind()==MagneticSystemMenu.KIND_COIL){labelValue(g,"Field / EMF",menu.primary()+" / "+menu.secondary(),126);labelValue(g,"Turns",Integer.toString(menu.tertiary()),146);labelValue(g,"Output validity",qualityName(),166);}
        else {labelValue(g,"Primary field",Integer.toString(menu.primary()),126);labelValue(g,"Evidence",qualityName(),146);}
        statusLine(g,"Authority","SERVER SYNCHRONIZED",GOOD,198);
    }

    private void history(GuiGraphics g) {
        statusBadge(g,"MAGNETIC EVIDENCE",INFO,16,80);
        g.drawString(font,"This HMI exposes current/retained server observations; it does not fabricate field history.",16,108,TEXT,false);
        if(menu.kind()==MagneticSystemMenu.KIND_COIL){labelValue(g,"Current induced EMF",menu.secondary()+" / 15",136);labelValue(g,"Derivative baseline",menu.complete()?"VALID":"STALE / RE-ARM",156);}
        else if(menu.kind()==MagneticSystemMenu.KIND_FIELD_SENSOR){labelValue(g,"Coverage",menu.secondary()+" / "+menu.tertiary(),136);}
    }

    private String deviceName(){return switch(menu.kind()){case MagneticSystemMenu.KIND_ELECTROMAGNET->"ELECTROMAGNET";case MagneticSystemMenu.KIND_PERMANENT->"PERMANENT MAGNET";case MagneticSystemMenu.KIND_COIL->"INDUCTION COIL";case MagneticSystemMenu.KIND_FIELD_SENSOR->"MAGNETIC FIELD SENSOR";default->"MAGNETIC GRADIENT METER";};}
    private String role(){return switch(menu.kind()){case MagneticSystemMenu.KIND_ELECTROMAGNET->"ACTUATOR";case MagneticSystemMenu.KIND_PERMANENT->"SOURCE";case MagneticSystemMenu.KIND_COIL->"CONVERTER";default->"OBSERVER";};}
    private String observerOrActuator(){return (menu.kind()==MagneticSystemMenu.KIND_FIELD_SENSOR||menu.kind()==MagneticSystemMenu.KIND_GRADIENT)?"OBSERVE ONLY":"EXTERNAL PHYSICS INPUT";}
    private String qualityName(){return menu.quality().name().replace('_',' ');} private int qualityColor(){return menu.quality()==PortQuality.VALID?GOOD:menu.quality()==PortQuality.STALE||menu.quality()==PortQuality.NO_SIGNAL?WARN:BAD;}
    private String face(Direction d){return d.getName().toUpperCase();}
}
