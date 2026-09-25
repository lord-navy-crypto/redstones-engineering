package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.physics.VibrationNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Device-aware notebook menu for process/control parameter batch three. */
public final class ProcessParameterMenu extends EngineeringDeviceMenu {
    public static final int KIND_CONDITIONER = 0;
    public static final int KIND_PWM = 1;
    public static final int KIND_COPPER_DRIVER = 2;
    public static final int KIND_CAPACITOR = 3;
    public static final int KIND_FUSE = 4;
    public static final int KIND_COMPRESSOR = 5;
    public static final int KIND_DAMPER = 6;
    public static final int KIND_EXCITER = 7;
    public static final int KIND_LAPIS_SOURCE = 8;
    public static final int KIND_COPPER_SOURCE = 9;

    public static final int BUTTON_P0_MINUS = 0;
    public static final int BUTTON_P0_PLUS = 1;
    public static final int BUTTON_P1_MINUS = 2;
    public static final int BUTTON_P1_PLUS = 3;
    public static final int BUTTON_P2_MINUS = 4;
    public static final int BUTTON_P2_PLUS = 5;
    public static final int BUTTON_P3_MINUS = 6;
    public static final int BUTTON_P3_PLUS = 7;
    public static final int BUTTON_ACTION = 8;
    public static final int BUTTON_INPUT_PREVIOUS = 20;
    public static final int BUTTON_INPUT_NEXT = 21;
    public static final int BUTTON_OUTPUT_PREVIOUS = 22;
    public static final int BUTTON_OUTPUT_NEXT = 23;

    private final DataSlot kind = trackedInt();
    private final DataSlot p0 = trackedInt();
    private final DataSlot p1 = trackedInt();
    private final DataSlot p2 = trackedInt();
    private final DataSlot p3 = trackedInt();
    private final DataSlot liveA = trackedInt();
    private final DataSlot liveB = trackedInt();
    private final DataSlot liveC = trackedInt();
    private final DataSlot liveD = trackedInt();
    private final DataSlot liveE = trackedInt();
    private final DataSlot liveF = trackedInt();
    private final DataSlot liveG = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();

    public ProcessParameterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public ProcessParameterMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.PROCESS_PARAMETER.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        p0.set(0); p1.set(0); p2.set(0); p3.set(0);
        liveA.set(0); liveB.set(0); liveC.set(0); liveD.set(0); liveE.set(0); liveF.set(PortQuality.NO_SIGNAL.ordinal()); liveG.set(PortQuality.NO_SIGNAL.ordinal());
        inputFacing.set(-1); outputFacing.set(-1);

        if (block instanceof SignalConditionerBlock) {
            kind.set(KIND_CONDITIONER);
            p0.set(state.getValue(SignalConditionerBlock.MODE));
            p1.set(state.getValue(SignalConditionerBlock.PARAM));
            liveA.set(SignalConditionerBlock.inspectInput(level, blockPos, state));
            liveB.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            liveC.set(SignalConditionerBlock.limitingEpisodes(level, blockPos));
            liveD.set(SignalConditionerBlock.limitingActive(level, blockPos, state) ? 1 : 0);
            liveE.set(SignalConditionerBlock.inspectInputQuality(level, blockPos, state).ordinal());
            liveF.set(SignalConditionerBlock.inspectOutputQuality(level, blockPos, state).ordinal());
            liveG.set(SignalConditionerBlock.lastLimitingAgeTicks(level, blockPos));
            inputFacing.set(DirectionalSignalBlock.seriesInputSide(state).ordinal());
            outputFacing.set(DirectionalSignalBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof PwmControllerBlock pwm) {
            kind.set(KIND_PWM);
            p0.set(PwmControllerBlock.configuredPeriod(level, blockPos, state));
            p1.set(state.getValue(PwmControllerBlock.INVERT) ? 1 : 0);
            var a = pwm.assessment(level, blockPos, state);
            liveA.set(a.command()); liveB.set(a.appliedCommand());
            liveC.set(a.effectiveDutyPermille()); liveD.set(a.completedCycles());
            liveE.set(PwmControllerBlock.commandQuality(level, blockPos, state).ordinal());
            liveF.set(PwmControllerBlock.inhibitQuality(level, blockPos, state).ordinal());
            liveG.set(PwmControllerBlock.outputQuality(level, blockPos, state).ordinal());
            inputFacing.set(DirectionalSignalBlock.seriesInputSide(state).ordinal());
            outputFacing.set(DirectionalSignalBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof RedstoneCopperDriverBlock) {
            kind.set(KIND_COPPER_DRIVER);
            p0.set(RedstoneCopperDriverBlock.configuredRiseSlew(level, blockPos, state));
            p1.set(RedstoneCopperDriverBlock.configuredFallSlew(level, blockPos, state));
            PortQuality inputQuality = RedstoneCopperDriverBlock.inputQuality(level, blockPos);
            liveA.set(RedstoneCopperDriverBlock.targetVoltage(level, blockPos));
            liveB.set(RedstoneCopperDriverBlock.actualVoltage(level, blockPos));
            liveC.set(inputQuality.ordinal());
            liveD.set(Math.abs(RedstoneCopperDriverBlock.targetVoltage(level, blockPos)
                    - RedstoneCopperDriverBlock.actualVoltage(level, blockPos)));
            liveE.set(inputQuality == PortQuality.VALID ? 1 : 0);
            inputFacing.set(RedstoneCopperDriverBlock.inputSide(state).ordinal());
            outputFacing.set(RedstoneCopperDriverBlock.outputSide(state).ordinal());
        } else if (block instanceof CopperCapacitorBlock) {
            kind.set(KIND_CAPACITOR);
            p0.set(CopperCapacitorBlock.configuredBaseTau(level, blockPos, state));
            p1.set(CopperCapacitorBlock.configuredLeakageFactor(level, blockPos, state));
            liveA.set(CopperCapacitorBlock.chargePercent(level, blockPos));
            liveB.set(CopperCapacitorBlock.outputVoltage(level, blockPos));
            liveC.set(CopperCapacitorBlock.effectiveTau(level, blockPos));
            double load = CopperCapacitorBlock.observedLoadResistance(level, blockPos);
            liveD.set(Double.isInfinite(load) ? -1 : (int)Math.round(Math.min(32.0, load) * 10.0));
            liveE.set(CopperCapacitorBlock.loadTruncated(level, blockPos) ? 1 : 0);
            liveF.set(CopperCapacitorBlock.inputQuality(level, blockPos).ordinal());
            liveG.set(CopperCapacitorBlock.outputQuality(level, blockPos).ordinal());
            inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal());
            outputFacing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof CopperFuseBlock) {
            kind.set(KIND_FUSE);
            p0.set(state.getValue(CopperFuseBlock.RATING));
            p1.set(CopperFuseBlock.configuredTimeCurrentClass(level, blockPos));
            liveA.set(CopperFuseBlock.thermalExposure(level, blockPos));
            liveB.set(CopperFuseBlock.tripProgressPermille(level, blockPos));
            liveC.set(state.getValue(CopperFuseBlock.TRIPPED) ? 1 : 0);
            double current = CopperFuseBlock.lastEvaluatedCurrent(level, blockPos);
            liveD.set((int)Math.round(current * 100.0));
            liveE.set((int)Math.round(current / Math.max(1, state.getValue(CopperFuseBlock.RATING)) * 1000.0));
            liveF.set(CopperFuseBlock.inputQuality(level, blockPos).ordinal());
            liveG.set(CopperFuseBlock.outputQuality(level, blockPos, state).ordinal());
            inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal());
            outputFacing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof AirCompressorBlock) {
            kind.set(KIND_COMPRESSOR);
            var r = AirCompressorBlock.configuredResponse(level, blockPos, state);
            p0.set(r.a()); p1.set(r.b());
            liveA.set(AirCompressorBlock.commandedPressure(level, blockPos));
            liveB.set(AirCompressorBlock.actualPressure(level, blockPos));
            liveC.set(AirCompressorBlock.trackingError(level, blockPos));
            liveD.set(AirCompressorBlock.startCount(level, blockPos));
        } else if (block instanceof HoneyVibrationDamperBlock) {
            kind.set(KIND_DAMPER);
            p0.set(HoneyVibrationDamperBlock.configuredAttenuation(level, blockPos));
            var wave = VibrationNetwork.sample(level, blockPos);
            liveA.set(wave.amplitude()); liveB.set(wave.frequency());
            liveC.set(wave.valid() ? 1 : 0);
            liveD.set(HoneyVibrationDamperBlock.localEnvelopeQuality(level, blockPos));
            liveE.set(HoneyVibrationDamperBlock.localEnvelopeAgeTicks(level, blockPos));
        } else if (block instanceof MechanicalExciterBlock) {
            kind.set(KIND_EXCITER);
            p0.set(state.getValue(MechanicalExciterBlock.FREQUENCY));
            var d = MechanicalExciterBlock.configuredDynamics(level, blockPos);
            p1.set(d.a()); p2.set(d.b()); p3.set(d.c());
            liveA.set(MechanicalExciterBlock.targetAmplitude(level, blockPos));
            liveB.set(MechanicalExciterBlock.actualAmplitude(level, blockPos));
            liveC.set(MechanicalExciterBlock.actualFrequency(level, blockPos));
            liveD.set(MechanicalExciterBlock.startCount(level, blockPos));
            liveE.set(MechanicalExciterBlock.runTicks(level, blockPos));
            liveF.set(MechanicalExciterBlock.driveObservation(level, blockPos).quality().ordinal());
            liveG.set(MechanicalExciterBlock.outputQuality(level, blockPos).ordinal());
        } else if (block instanceof LapisPrecisionSourceBlock) {
            kind.set(KIND_LAPIS_SOURCE);
            p0.set(state.getValue(LapisPrecisionSourceBlock.VALUE));
            liveA.set(state.getValue(LapisPrecisionSourceBlock.VALUE));
            outputFacing.set(DirectionalDomainSourceBlock.outputSide(state).ordinal());
        } else if (block instanceof CopperVoltageSourceBlock) {
            kind.set(KIND_COPPER_SOURCE);
            p0.set(state.getValue(CopperVoltageSourceBlock.VOLTAGE));
            liveA.set(state.getValue(CopperVoltageSourceBlock.VOLTAGE));
            liveB.set(PortQuality.VALID.ordinal());
            liveC.set(1);
        } else {
            kind.set(-1);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player) || !(level instanceof ServerLevel server)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();

        int delta = switch (id) {
            case BUTTON_P0_MINUS, BUTTON_P1_MINUS, BUTTON_P2_MINUS, BUTTON_P3_MINUS -> -1;
            case BUTTON_P0_PLUS, BUTTON_P1_PLUS, BUTTON_P2_PLUS, BUTTON_P3_PLUS -> 1;
            default -> 0;
        };
        int slot = switch (id) {
            case BUTTON_P0_MINUS, BUTTON_P0_PLUS -> 0;
            case BUTTON_P1_MINUS, BUTTON_P1_PLUS -> 1;
            case BUTTON_P2_MINUS, BUTTON_P2_PLUS -> 2;
            case BUTTON_P3_MINUS, BUTTON_P3_PLUS -> 3;
            default -> -1;
        };
        boolean changed = false;

        if (id == BUTTON_INPUT_PREVIOUS || id == BUTTON_INPUT_NEXT
                || id == BUTTON_OUTPUT_PREVIOUS || id == BUTTON_OUTPUT_NEXT) {
            boolean clockwise = id == BUTTON_INPUT_NEXT || id == BUTTON_OUTPUT_NEXT;
            boolean inputRoute = id == BUTTON_INPUT_PREVIOUS || id == BUTTON_INPUT_NEXT;
            if (block instanceof SignalConditionerBlock || block instanceof PwmControllerBlock) {
                changed = inputRoute
                        ? DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise)
                        : DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise);
            } else if (block instanceof RedstoneCopperDriverBlock) {
                changed = inputRoute
                        ? RedstoneCopperDriverBlock.rotateInput(level, blockPos, clockwise)
                        : RedstoneCopperDriverBlock.rotateOutput(level, blockPos, clockwise);
            } else if (block instanceof CopperCapacitorBlock || block instanceof CopperFuseBlock) {
                // Both subclasses are DirectionalCopperProcessorBlock axial devices: BACK input
                // and FRONT output stay exactly opposite. Legacy RX/TX buttons rotate one rigid axis.
                changed = DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, clockwise);
            } else if (block instanceof LapisPrecisionSourceBlock && !inputRoute) {
                changed = DirectionalDomainSourceBlock.rotateOutput(level, blockPos, clockwise);
            }
        } else if (block instanceof SignalConditionerBlock) {
            if (slot == 0) changed = SignalConditionerBlock.applyConfigurationAction(
                    level, blockPos, delta > 0 ? SignalConditionerMenu.BUTTON_MODE_NEXT : SignalConditionerMenu.BUTTON_MODE_PREVIOUS);
            else if (slot == 1) changed = SignalConditionerBlock.applyConfigurationAction(
                    level, blockPos, delta > 0 ? SignalConditionerMenu.BUTTON_PARAM_INCREASE : SignalConditionerMenu.BUTTON_PARAM_DECREASE);
        } else if (block instanceof PwmControllerBlock pwm) {
            if (slot == 0) changed = PwmControllerBlock.setConfiguredPeriod(server, blockPos, p0.get() + delta);
            else if (slot == 1 || id == BUTTON_ACTION) changed = pwm.toggleInvert(level, blockPos);
        } else if (block instanceof RedstoneCopperDriverBlock && (slot == 0 || slot == 1)) {
            int rise = p0.get() + (slot == 0 ? delta : 0);
            int fall = p1.get() + (slot == 1 ? delta : 0);
            changed = RedstoneCopperDriverBlock.setEngineeringSlewRates(server, blockPos, rise, fall);
        } else if (block instanceof CopperCapacitorBlock && (slot == 0 || slot == 1)) {
            int tau = p0.get() + (slot == 0 ? delta : 0);
            int leakage = p1.get() + (slot == 1 ? delta : 0);
            changed = CopperCapacitorBlock.setEngineeringParameters(server, blockPos, tau, leakage);
        } else if (block instanceof CopperFuseBlock fuse) {
            if (slot == 0) {
                changed = CopperFuseBlock.setRating(server, blockPos, p0.get() + delta);
            } else if (slot == 1) {
                changed = CopperFuseBlock.setTimeCurrentClass(server, blockPos, p1.get() + delta);
            } else if (id == BUTTON_ACTION) {
                changed = CopperFuseBlock.tryReset(server, blockPos);
            }
        } else if (block instanceof AirCompressorBlock && (slot == 0 || slot == 1)) {
            changed = AirCompressorBlock.setResponseRates(server, blockPos,
                    p0.get() + (slot == 0 ? delta : 0),
                    p1.get() + (slot == 1 ? delta : 0));
        } else if (block instanceof HoneyVibrationDamperBlock && slot == 0) {
            changed = HoneyVibrationDamperBlock.setConfiguredAttenuation(server, blockPos, p0.get() + delta);
        } else if (block instanceof MechanicalExciterBlock exciter) {
            if (slot == 0) {
                changed = MechanicalExciterBlock.setConfiguredFrequency(
                        server, blockPos, p0.get() + delta);
            } else if (slot >= 1 && slot <= 3) {
                changed = MechanicalExciterBlock.setConfiguredDynamics(server, blockPos,
                        p1.get() + (slot == 1 ? delta : 0),
                        p2.get() + (slot == 2 ? delta : 0),
                        p3.get() + (slot == 3 ? delta : 0));
            }
        } else if (block instanceof LapisPrecisionSourceBlock && slot == 0) {
            changed = LapisPrecisionSourceBlock.stepValue(level, blockPos, delta);
        } else if (block instanceof CopperVoltageSourceBlock && slot == 0) {
            int next = Math.max(0, Math.min(15, p0.get() + delta));
            if (next != p0.get()) {
                level.setBlock(blockPos, state.setValue(CopperVoltageSourceBlock.VOLTAGE, next), Block.UPDATE_CLIENTS);
                dev.redstoneengineering.physics.DomainNetwork.recomputeCopper(server, blockPos);
                changed = true;
            }
        }

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int kind(){return kind.get();}
    public int p0(){return p0.get();}
    public int p1(){return p1.get();}
    public int p2(){return p2.get();}
    public int p3(){return p3.get();}
    public int liveA(){return liveA.get();}
    public int liveB(){return liveB.get();}
    public int liveC(){return liveC.get();}
    public int liveD(){return liveD.get();}
    public int liveE(){return liveE.get();}
    public int liveF(){return liveF.get();}
    public int liveG(){return liveG.get();}
    public boolean hasInputEndpoint(){return inputFacing.get()>=0;}
    public boolean hasOutputEndpoint(){return outputFacing.get()>=0;}
    public boolean canRouteInput(){
        return kind.get()==KIND_CONDITIONER || kind.get()==KIND_PWM || kind.get()==KIND_COPPER_DRIVER
                || kind.get()==KIND_CAPACITOR || kind.get()==KIND_FUSE;
    }
    public boolean rigidSeriesRoute(){
        return kind.get()==KIND_CAPACITOR || kind.get()==KIND_FUSE;
    }
    public boolean canRouteOutput(){
        return canRouteInput() || kind.get()==KIND_LAPIS_SOURCE;
    }
    public Direction inputDirection(){return direction(inputFacing.get(),Direction.SOUTH);}
    public Direction outputDirection(){return direction(outputFacing.get(),Direction.NORTH);}
    private static Direction direction(int ordinal,Direction fallback){
        Direction[] values=Direction.values();
        return ordinal>=0&&ordinal<values.length?values[ordinal]:fallback;
    }
}
