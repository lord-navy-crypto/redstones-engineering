package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.block.FaultLatchBlock;
import dev.redstoneengineering.block.RedundantVoterBlock;
import dev.redstoneengineering.block.WatchdogBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Dedicated reliability HMI for watchdog, servo, feedback sensor, voter and fault latch. */
public final class ReliabilitySystemScreen extends EngineeringScreen<ReliabilitySystemMenu> {
    private Button parameterPrevious, parameterNext, maintenanceAction;

    public ReliabilitySystemScreen(ReliabilitySystemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void addDeviceWidgets() {
        int y = topPos + 116;
        parameterPrevious = addConfigureWidget(Button.builder(Component.literal("◀ Parameter"), b -> sendMenuButton(ReliabilitySystemMenu.BUTTON_PARAMETER_PREVIOUS)).bounds(leftPos+16,y,105,20).build());
        parameterNext = addConfigureWidget(Button.builder(Component.literal("Parameter ▶"), b -> sendMenuButton(ReliabilitySystemMenu.BUTTON_PARAMETER_NEXT)).bounds(leftPos+199,y,105,20).build());
        maintenanceAction = addConfigureWidget(Button.builder(Component.literal("Maintenance action"), b -> sendMenuButton(ReliabilitySystemMenu.BUTTON_ACTION)).bounds(leftPos+38,topPos+147,244,20).build());
    }

    @Override protected void syncDeviceWidgetLabels() {
        if (parameterPrevious == null || maintenanceAction == null) return;
        boolean adjustable = menu.kind() == ReliabilitySystemMenu.KIND_WATCHDOG
                || menu.kind() == ReliabilitySystemMenu.KIND_SERVO
                || menu.kind() == ReliabilitySystemMenu.KIND_VOTER
                || menu.kind() == ReliabilitySystemMenu.KIND_FAULT_LATCH;
        boolean configure = isConfigureSection();
        parameterPrevious.active = adjustable;
        parameterNext.active = adjustable;
        parameterPrevious.visible = configure && adjustable;
        parameterNext.visible = configure && adjustable;
        maintenanceAction.visible = configure;
        maintenanceAction.active = menu.kind() != ReliabilitySystemMenu.KIND_FAULT_LATCH || menu.extraC() != 0;
        maintenanceAction.setMessage(Component.literal(fitForWidth(maintenanceActionText(), 228)));
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
        int hintY = 199;
        switch(menu.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> {
                metricCard(g,"Age",menu.primary()+" t",16,103,88,INFO);
                metricCard(g,"Timeout",menu.secondary()+" t",111,103,88,GOOD);
                metricCard(g,"Alarms",Integer.toString(menu.tertiary()),206,103,88,WARN);
                labelValue(g,"Transitions",Integer.toString(menu.auxiliary()),149);
                labelValue(g,"Alarm output",menu.extraA()+" / 15",165);
                labelValue(g,"Source acquired",menu.extraB()==1?"YES":"NO",181);
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
                labelValue(g,"Latch state",menu.extraA()==1?"LATCHED":"CLEAR",167);
                labelValue(g,"Fault input evidence",menu.faultInputQuality().name(),185);
                labelValue(g,"Reset input evidence",menu.resetInputQuality().name(),203);
                labelValue(g,"Reset state / permissive",
                        (menu.extraB()==1?"ACTIVE":"LOW")+" / "+(menu.extraC()==1?"YES":"BLOCKED"),221);
                hintY = 247;
            }
        }
        wrappedText(g, hint(),16,hintY,620,MUTED);
    }

    private void ports(GuiGraphics g) {
        statusBadge(g,"FUNCTIONAL INTERFACES",GOOD,16,80);
        Direction front = menu.facing();
        switch(menu.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> {statusLine(g,face(front.getOpposite()),"INPUT • HEARTBEAT",qualityColor(),108);statusLine(g,face(front),"OUTPUT • TIMEOUT ALARM",GOOD,140);}
            case ReliabilitySystemMenu.KIND_SERVO -> {statusLine(g,face(front.getOpposite()),"INPUT • COMMAND",qualityColor(),102);statusLine(g,"UP","INPUT • POSITION/VELOCITY MODE",INFO,126);statusLine(g,face(rightOf(front)),"INPUT • BRAKE",WARN,150);statusLine(g,face(front),"OUTPUT • MECHATRONIC POSITION",GOOD,174);}
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> {statusLine(g,face(front.getOpposite()),"INPUT • SERVO POSITION",qualityColor(),112);statusLine(g,face(front),"OUTPUT • REDSTONE FEEDBACK",GOOD,144);}
            case ReliabilitySystemMenu.KIND_VOTER -> {statusLine(g,face(front.getOpposite()),"INPUT A • REDSTONE",INFO,102);statusLine(g,face(leftOf(front)),"INPUT B • REDSTONE",INFO,126);statusLine(g,face(rightOf(front)),"INPUT C • REDSTONE",INFO,150);statusLine(g,face(front),"OUTPUT • 2oo3 VOTED",qualityColor(),174);}
            default -> {statusLine(g,face(front.getOpposite()),"INPUT • FAULT",qualityColor(menu.faultInputQuality()),108);statusLine(g,face(rightOf(front)),"INPUT • RESET",qualityColor(menu.resetInputQuality()),136);statusLine(g,face(front),"OUTPUT • LATCHED ALARM • AUTHORITATIVE",GOOD,164);}
        }
    }

    private void configure(GuiGraphics g) {
        statusBadge(g,"SERVER-SIDE BOUNDED CONTROL",INFO,16,80);
        labelValue(g,"Parameter",parameterText(),101);
        boolean detailedSafety = menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG
                || menu.kind()==ReliabilitySystemMenu.KIND_VOTER
                || menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH;
        if(menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG){
            labelValue(g,"Allowed timeouts",WatchdogBlock.timeoutChoicesText(),121);
            labelValue(g,"Sample cadence",WatchdogBlock.SAMPLE_TICKS+" ticks",141);
            labelValue(g,"Heartbeat rule","only a VALID observed transition resets age",159);
        }else if(menu.kind()==ReliabilitySystemMenu.KIND_VOTER){
            labelValue(g,"Allowed tolerance",RedundantVoterBlock.toleranceChoicesText(),121);
            labelValue(g,"Vote quorum","numeric vote requires ≥"+RedundantVoterBlock.MIN_VALID_INPUTS+" valid inputs",141);
            labelValue(g,"Healthy rule",RedundantVoterBlock.NOMINAL_INPUTS+" valid inputs + spread ≤ tolerance",159);
        }else if(menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH){
            labelValue(g,"Allowed trip levels",FaultLatchBlock.thresholdChoicesText(),121);
            labelValue(g,"Trip rule","FAULT evidence missing/invalid OR value ≥ T → LATCH",141);
            labelValue(g,"Reset rule","rising RESET + VALID fault value < T → CLEAR",159);
        }
        labelValue(g,"Maintenance",maintenanceActionText(),detailedSafety?187:179);
        labelValue(g,"Front / primary output",face(menu.facing()),detailedSafety?205:195);
        wrappedText(g,
                menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH
                        ? "NO_SIGNAL on FAULT IN is missing evidence, not a measured zero: the latch fails safe and reset remains blocked. RESET evidence recovery only reacquires the electrical level; it never fabricates a reset edge. FRONT alarm output remains authoritative VALID state."
                        : menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG
                        ? "Missing/invalid heartbeat evidence never masquerades as a LOW transition. Age keeps increasing; when a valid source reappears, its first sample establishes a baseline and does not fabricate a heartbeat edge."
                        : menu.kind()==ReliabilitySystemMenu.KIND_VOTER
                        ? "With two valid channels the voter can compute a degraded numeric 2oo3 result, but quality remains FAULT. NOMINAL requires all three valid channels and spread within the configured tolerance."
                        : "Routing stays on Route; maintenance actions use the same server methods as Shift-right-click.",
                16,detailedSafety?229:213,620,MUTED);
    }

    private void diagnostics(GuiGraphics g) {
        statusBadge(g,stateName(),stateColor(),16,80);
        labelValue(g,"Role",roleName(),106);
        labelValue(g,menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH?"Operational evidence":"Quality",menu.quality().name(),124);
        switch(menu.kind()) {
            case ReliabilitySystemMenu.KIND_WATCHDOG -> {labelValue(g,"Age / timeout",menu.primary()+" / "+menu.secondary()+" t",144);labelValue(g,"Transitions / timeouts",menu.auxiliary()+" / "+menu.tertiary(),162);}
            case ReliabilitySystemMenu.KIND_SERVO -> {labelValue(g,"Position / command",menu.primary()+" / "+menu.secondary(),144);labelValue(g,"Velocity / error",menu.tertiary()+" / "+menu.auxiliary(),162);labelValue(g,"Brake / soft limits",menu.extraA()+" / "+menu.extraB(),180);}
            case ReliabilitySystemMenu.KIND_POSITION_SENSOR -> {labelValue(g,"Input / output",menu.primary()+" / "+menu.secondary(),144);labelValue(g,"Samples",Integer.toString(menu.tertiary()),162);}
            case ReliabilitySystemMenu.KIND_VOTER -> {labelValue(g,"Valid / spread",menu.secondary()+" / "+menu.tertiary(),144);labelValue(g,"Tolerance",Integer.toString(menu.auxiliary()),162);labelValue(g,"Disagreements",Integer.toString(menu.extraB()),180);}
            default -> {
                labelValue(g,"Threshold / trips",menu.secondary()+" / "+menu.tertiary(),144);
                labelValue(g,"Fault input quality",menu.faultInputQuality().name(),162);
                labelValue(g,"Reset input quality",menu.resetInputQuality().name(),180);
                labelValue(g,"Latched / reset permissive",
                        (menu.extraA()==1?"YES":"NO")+" / "+(menu.extraC()==1?"YES":"BLOCKED"),198);
                labelValue(g,"Reset events",Integer.toString(menu.auxiliary()),216);
            }
        }
        statusLine(g,"Authority","SERVER SYNCHRONIZED • FRONT ALARM OUTPUT VALID",GOOD,
                menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH?238:200);
    }

    private void history(GuiGraphics g) {
        statusBadge(g,"RELIABILITY EVIDENCE",INFO,16,80);
        if(menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG){labelValue(g,"Heartbeat transitions",Integer.toString(menu.auxiliary()),110);labelValue(g,"Timeout events",Integer.toString(menu.tertiary()),130);}
        else if(menu.kind()==ReliabilitySystemMenu.KIND_VOTER){labelValue(g,"Max observed spread",Integer.toString(menu.extraA()),110);labelValue(g,"Disagreement events",Integer.toString(menu.extraB()),130);}
        else if(menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH){labelValue(g,"Trip events",Integer.toString(menu.tertiary()),110);labelValue(g,"Reset events",Integer.toString(menu.auxiliary()),130);}
        else if(menu.kind()==ReliabilitySystemMenu.KIND_SERVO){labelValue(g,"Soft-limit hits",Integer.toString(menu.extraB()),110);labelValue(g,"Current error",Integer.toString(menu.auxiliary()),130);}
        else {labelValue(g,"Measurement samples",Integer.toString(menu.tertiary()),110);}
        sectionRule(g,154);wrappedText(g,"Counters are retained server evidence; opening the HMI never manufactures events.",16,170,620,MUTED);
    }

    private String parameterText(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"TIMEOUT "+menu.secondary()+"t";case ReliabilitySystemMenu.KIND_SERVO->"SLEW STEP "+menu.extraC();case ReliabilitySystemMenu.KIND_VOTER->"TOLERANCE "+menu.auxiliary();case ReliabilitySystemMenu.KIND_FAULT_LATCH->"THRESHOLD "+menu.secondary();default->"READ ONLY";};}
    private String maintenanceActionText(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"Reset watchdog diagnostics";case ReliabilitySystemMenu.KIND_SERVO->"Home / reset trajectory";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->"Reset position metrology";case ReliabilitySystemMenu.KIND_VOTER->"Reset voter diagnostics";default->menu.extraC()!=0?"Manual reset latch":"Reset blocked • fault not clear";};}
    private String deviceName(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"WATCHDOG";case ReliabilitySystemMenu.KIND_SERVO->"SERVO ACTUATOR";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->"SERVO POSITION SENSOR";case ReliabilitySystemMenu.KIND_VOTER->"REDUNDANT VOTER";default->"FAULT LATCH";};}
    private String roleName(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->"SAFETY PROCESSOR";case ReliabilitySystemMenu.KIND_SERVO->"MECHATRONIC ACTUATOR";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->"FEEDBACK SENSOR";case ReliabilitySystemMenu.KIND_VOTER->"2oo3 SAFETY VOTER";default->"PERSISTENT FAULT MEMORY";};}
    private String stateName(){return switch(menu.kind()){case ReliabilitySystemMenu.KIND_WATCHDOG->menu.extraA()>0?"TIMEOUT":menu.extraB()==0?"NO VALID SOURCE":"MONITORING";case ReliabilitySystemMenu.KIND_SERVO->menu.extraA()==1?"BRAKING":menu.auxiliary()==0?"AT COMMAND":"MOVING / ERROR";case ReliabilitySystemMenu.KIND_POSITION_SENSOR->menu.quality()==PortQuality.VALID?"VALID FEEDBACK":"SOURCE ISSUE";case ReliabilitySystemMenu.KIND_VOTER->menu.extraC()==1?"DEGRADED":"NOMINAL";default->menu.extraA()==1?"LATCHED":"CLEAR";};}
    private int stateColor(){if(menu.kind()==ReliabilitySystemMenu.KIND_FAULT_LATCH&&menu.extraA()==1)return BAD;if(menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG&&menu.extraA()>0)return BAD;if(menu.kind()==ReliabilitySystemMenu.KIND_WATCHDOG&&menu.extraB()==0)return WARN;if(menu.kind()==ReliabilitySystemMenu.KIND_VOTER&&menu.extraC()==1)return WARN;if(menu.quality()==PortQuality.FAULT)return BAD;return GOOD;}
    private String seriesPath(){return face(menu.facing().getOpposite())+" → "+face(menu.facing());}
    private String hint(){return menu.kind()==ReliabilitySystemMenu.KIND_SERVO?"Control validity, brake state, motion and soft-limit evidence stay separate.":"Safety state and source validity are synchronized independently; numerical zero is not absence.";}
    private int qualityColor(){return qualityColor(menu.quality());}
    private int qualityColor(PortQuality quality){
        return quality==PortQuality.VALID?GOOD:quality==PortQuality.NO_SIGNAL||quality==PortQuality.STALE?WARN:BAD;
    }
    private String face(Direction d){return d.getName().toUpperCase();}
    private Direction leftOf(Direction f){return switch(f){case NORTH->Direction.WEST;case WEST->Direction.SOUTH;case SOUTH->Direction.EAST;case EAST->Direction.NORTH;default->Direction.WEST;};}
    private Direction rightOf(Direction f){return leftOf(f).getOpposite();}
}
