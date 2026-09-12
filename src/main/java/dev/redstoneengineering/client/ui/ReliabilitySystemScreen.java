package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated reliability HMI for watchdog, servo, feedback sensor, voter and fault latch. */
public final class ReliabilitySystemScreen extends EngineeringScreen<ReliabilitySystemMenu> {
    private Button parameterPrevious, parameterNext;

    public ReliabilitySystemScreen(ReliabilitySystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"), b -> sendMenuButton(ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"), b -> sendMenuButton(ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos+199,y,105,20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null) return;
        boolean adjustable = menu.kind() == ReliabilitySystemMenu.KIND_WATCHDOG
                || menu.kind() == ReliabilitySystemMenu.KIND_SERVO
                || menu.kind() == ReliabilitySystemMenu.KIND_VOTER
                || menu.kind() == ReliabilitySystemMenu.KIND_FAULT_LATCH;
        boolean configure = isConfigureSection();
        parameterPrevious.active = adjustable;
        parameterNext.active = adjustable;
        parameterPrevious.visible = configure && adjustable;
        parameterNext.visible = configure && adjustable;
        String parameter = parameterText();
        if (adjustable) {
            parameterPrevious.setMessage(Component.literal(fitForWidth("◀ " + parameter, 89)));
            parameterNext.setMessage(Component.literal(fitForWidth(parameter + " ▶", 89)));
        }
    }

    @Override protected void renderSection(GuiGraphics g, Section section) {
        switch(section){case OVERVIEW->overview(g);case PORTS->ports(g);case CONFIGURE->configure(g);case DIAGNOSTICS->diagnostics(g);case HISTORY->history(g);}
    }

    private void overview(GuiGraphics g) {
        statusBadge(g, deviceName(), GOOD, 16,80);
        statusBadge(g, stateName(), stateColor(),205,80);
        switch(menu.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> {
                metricCard(g,"Age",menu.primary()+" t",16,103,88,INFO);
                metricCard(g,"Timeout",menu.secondary()+" t",111,103,88,GOOD);
                metricCard(g,"Alarms",Integer.toString(menu.tertiary()),206,103,88,WARN);
                labelValue(g,"Transitions",Integer.toString(menu.auxiliary()),149);
                labelValue(g,"Alarm output",menu.extraA()+" / 15",165);
                labelValue(g,"Path",seriesPath(),181);
            }
            case ReliabilitySystemMenu.KIND_SERVO -> {
                metricCard(g,"Position",menu.primary()+" / 15",16,103,88,GOOD);
                metricCard(g,"Command",menu.secondary()+" / 15",111,103,88,INFO);
                metricCard(g,"Velocity",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Error",Integer.toString(menu.auxiliary()),149);
                labelValue(g,"Brake / slew",(menu.extraA()==1?"ON":"OFF")+" / "+menu.extraC(),165);
                labelValue(g,"Soft-limit hits",Integer.toString(menu.extraB()),181);
            }
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> {
                metricCard(g,"Mech in",menu.primary()+" / 15",16,103,88,INFO);
                metricCard(g,"Redstone out",menu.secondary()+" / 15",111,103,88,GOOD);
                metricCard(g,"Samples",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Role","MECHANICAL → REDSTONE FEEDBACK",149);
                labelValue(g,"Source quality",menu.quality().name(),165);
                labelValue(g,"Path",seriesPath(),181);
            }
            case ReliabilitySystemMenu.KIND_VOTER -> {
                metricCard(g,"Voted out",menu.primary()+" / 15",16,103,88,GOOD);
                metricCard(g,"Valid inputs",menu.secondary()+" / 3",111,103,88,INFO);
                metricCard(g,"Spread",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Tolerance",Integer.toString(menu.auxiliary()),149);
                labelValue(g,"Max spread / events",menu.extraA()+" / "+menu.extraB(),165);
                labelValue(g,"Health",menu.extraC()==1?"DEGRADED":"NOMINAL",181);
            }
            default -> {
                metricCard(g,"Alarm out",menu.primary()+" / 15",16,103,88,WARN);
                metricCard(g,"Threshold",Integer.toString(menu.secondary()),111,103,88,INFO);
                metricCard(g,"Trips",Integer.toString(menu.tertiary()),206,103,88,INFO);
                labelValue(g,"Resets",Integer.toString(menu.auxiliary()),149);
                labelValue(g,"Latch state",menu.extraA()==1?"LATCHED":"CLEAR",165);
                labelValue(g,"Reset input",menu.extraB()==1?"ACTIVE":"LOW",181);
            }
        }
        safeText(g, hint(),16,199,MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g,"FUNCTIONAL INTERFACES",GOOD,16,80);
        Direction front = menu.facing();
        switch(menu.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> {statusLine(g,face(front.getOpposite()),"INPUT • HEARTBEAT",qualityColor(),108);statusLine(g,face(front),"OUTPUT • TIMEOUT ALARM",GOOD,140);}
            case ReliabilitySystemMenu.KIND_SERVO -> {statusLine(g,face(front.getOpposite()),"INPUT • COMMAND",qualityColor(),102);statusLine(g,"UP","INPUT • POSITION/VELOCITY MODE",INFO,126);statusLine(g,face(rightOf(front)),"INPUT • BRAKE",WARN,150);statusLine(g,face(front),"OUTPUT • MECHATRONIC POSITION",GOOD,174);}
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> {statusLine(g,face(front.getOpposite()),"INPUT • SERVO POSITION",qualityColor(),112);statusLine(g,face(front),"OUTPUT • REDSTONE FEEDBACK",GOOD,144);}
            case ReliabilitySystemMenu.KIND_VOTER -> {statusLine(g,face(front.getOpposite()),"INPUT A • REDSTONE",INFO,102);statusLine(g,face(leftOf(front)),"INPUT B • REDSTONE",INFO,126);statusLine(g,face(rightOf(front)),"INPUT C • REDSTONE",INFO,150);statusLine(g,face(front),"OUTPUT • 2oo3 VOTED",qualityColor(),174);}
            default -> {statusLine(g,face(front.getOpposite()),"INPUT • FAULT",INFO,108);statusLine(g,face(rightOf(front)),"INPUT • RESET",INFO,136);statusLine(g,face(front),"OUTPUT • LATCHED ALARM",qualityColor(),164);}
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g,"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);
        labelValue(g,"Parameter",parameterText(),101);
        labelValue(g,"Front / primary output",face(menu.facing()),171);
        labelValue(g,"Orientation contract",orientationText(),187);
        safeText(g,"Physical orientation is controlled only on Route.",16,207,MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g,stateName(),stateColor(),16,80);
        labelValue(g,"Role",roleName(),106);
        labelValue(g,"Quality",menu.quality().name(),124);
        switch(menu.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> {labelValue(g,"Age / timeout",menu.primary()+" / "+menu.secondary()+" t",144);labelValue(g,"Transitions / timeouts",menu.auxiliary()+" / "+menu.tertiary(),162);}
            case ReliabilitySystemMenu.KIND_SERVO -> {labelValue(g,"Position / command",menu.primary()+" / "+menu.secondary(),144);labelValue(g,"Velocity / error",menu.tertiary()+" / "+menu.auxiliary(),162);labelValue(g,"Brake / soft limits",menu.extraA()+" / "+menu.extraB(),180);}
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> {labelValue(g,"Input / output",menu.primary()+" / "+menu.secondary(),144);labelValue(g,"Samples",Integer.toString(menu.tertiary()),162);}
            case ReliabilitySystemMenu.KIND_VOTER -> {labelValue(g,"Valid / spread",menu.secondary()+" / "+menu.tertiary(),144);labelValue(g,"Tolerance",Integer.toString(menu.auxiliary()),162);labelValue(g,"Disagreements",Integer.toString(menu.extraB()),180);}
            default -> {labelValue(g,"Threshold / trips",menu.secondary()+" / "+menu.tertiary(),144);labelValue(g,"Resets",Integer.toString(menu.auxiliary()),162);labelValue(g,"Latched",menu.extraA()==1?"YES":"NO",180);}
        }
        statusLine(g,"Authority","SERVER SYNCHRONIZED",GOOD,200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g,"RELIABILITY EVIDENCE",INFO,16,80);
        if(menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG){labelValue(g,"Heartbeat transitions",Integer.toString(menu.auxiliary()),110);labelValue(g,"Timeout events",Integer.toString(menu.tertiary()),130);}
        else if(menu.kind()==ReliabilitySystemMenu.KIND_VOTER){labelValue(g,"Max observed spread",Integer.toString(menu.extraA()),110);labelValue(g,"Disagreement events",Integer.toString(menu.extraB()),130);}
        else if(menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH){labelValue(g,"Trip events",Integer.toString(menu.tertiary()),110);labelValue(g,"Reset events",Integer.toString(menu.auxiliary()),130);}
        else if(menu.kind()==ReliabilitySystemMenu.KIND_SERVO){labelValue(g,"Soft-limit hits",Integer.toString(menu.extraB()),110);labelValue(g,"Current error",Integer.toString(menu.auxiliary()),130);}
        else {labelValue(g,"Measurement samples",Integer.toString(menu.tertiary()),110);}
        sectionRule(g,154);safeText(g,"Counters are retained server evidence; opening the HMI never manufactures events.",16,170,MUTED);
    }

    private String parameterText(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"TIMEOUT "+menu.secondary()+"t";case ReliabilitySystemMenu.KIND_SERVO->"SLEW STEP "+menu.extraC();case ReliabilitySystemMenu.KIND_VOTER->"TOLERANCE "+menu.auxiliary();case ReliabilitySystemMenu.KIND_FAULT_LATCH->"THRESHOLD "+menu.secondary();default->"READ ONLY";};}
    private String deviceName(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"WATCHDOG";case ReliabilitySystemMenu.KIND_SERVO->"SERVO ACTUATOR";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->"SERVO POSITION SENSOR";case ReliabilitySystemMenu.KIND_VOTER->"REDUNDANT VOTER";default->"FAULT LATCH";};}
    private String roleName(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"SAFETY PROCESSOR";case ReliabilitySystemMenu.KIND_SERVO->"MECHATRONIC ACTUATOR";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->"FEEDBACK SENSOR";case ReliabilitySystemMenu.KIND_VOTER->"2oo3 SAFETY VOTER";default->"PERSISTENT FAULT MEMORY";};}
    private String stateName(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->menu.extraA()>0?"TIMEOUT":"HEALTHY";case ReliabilitySystemMenu.KIND_SERVO->menu.extraA()==1?"BRAKING":menu.auxiliary()==0?"AT COMMAND":"MOVING / ERROR";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->menu.quality()==PortQuality.VALID?"VALID FEEDBACK":"SOURCE ISSUE";case ReliabilitySystemMenu.KIND_VOTER->menu.extraC()==1?"DEGRADED":"NOMINAL";default->menu.extraA()==1?"LATCHED":"CLEAR";};}
    private int stateColor(){if(menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH&&menu.extraA()==1)return BAD;if(menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG&&menu.extraA()>0)return BAD;if(menu.kind()==ReliabilitySystemMenu.KIND_VOTER&&menu.extraC()==1)return WARN;if(menu.quality()==PortQuality.FAULT)return BAD;return GOOD;}
    private String seriesPath(){return face(menu.facing().getOpposite())+" → "+face(menu.facing());}
    private String orientationText(){return menu.kind()==ReliabilitySystemMenu.KIND_SERVO?"BACK COMMAND • UP MODE • RIGHT BRAKE • FRONT POSITION":menu.kind()==ReliabilitySystemMenu.KIND_VOTER?"BACK/LEFT/RIGHT INPUTS • FRONT OUTPUT":menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH?"BACK FAULT • RIGHT RESET • FRONT OUTPUT":seriesPath();}
    private String hint(){return menu.kind()==ReliabilitySystemMenu.KIND_SERVO?"Control validity, brake state, motion and soft-limit evidence stay separate.":"Safety state and source validity are synchronized independently; numerical zero is not absence.";}
    private int qualityColor(){return menu.quality()==PortQuality.VALID?GOOD:menu.quality()==PortQuality.NO_SIGNAL||menu.quality()==PortQuality.STALE?WARN:BAD;}
    private String face(Direction d){return d.getName().toUpperCase();}
    private Direction leftOf(Direction f){return switch(f){case NORTH->Direction.WEST;case WEST->Direction.SOUTH;case SOUTH->Direction.EAST;case EAST->Direction.NORTH;default->Direction.WEST;};}
    private Direction rightOf(Direction f){return leftOf(f).getOpposite();}
}
