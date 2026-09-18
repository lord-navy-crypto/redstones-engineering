package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
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

/** Server-authoritative HMI projection for the pneumatic subsystem. */
public final class PneumaticSystemMenu extends EngineeringDeviceMenu {
    public static final int KIND_COMPRESSOR = 0;
    public static final int KIND_PIPE = 1;
    public static final int KIND_RESERVOIR = 2;
    public static final int KIND_REGULATOR = 3;
    public static final int KIND_RECEIVER = 4;
    public static final int KIND_VALVE = 5;
    public static final int KIND_CHECK_VALVE = 6;
    public static final int KIND_FLOW_METER = 7;
    public static final int KIND_PROPORTIONAL = 8;
    public static final int KIND_RELIEF = 9;
    public static final int KIND_CYLINDER = 10;

    public static final int BUTTON_PARAMETER_PREVIOUS = 0;
    public static final int BUTTON_PARAMETER_NEXT = 1;
    public static final int BUTTON_TOGGLE = 2;
    public static final int BUTTON_ROTATE_LEFT = 3;
    public static final int BUTTON_ROTATE_RIGHT = 4;
    public static final int BUTTON_INPUT_LEFT = 5;
    public static final int BUTTON_INPUT_RIGHT = 6;
    public static final int BUTTON_OUTPUT_LEFT = 7;
    public static final int BUTTON_OUTPUT_RIGHT = 8;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot stateFlag = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot inputQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
    private final DataSlot upstreamPressure = trackedInt();
    private final DataSlot downstreamPressure = trackedInt();
    private final DataSlot upstreamQuality = trackedInt();
    private final DataSlot downstreamQuality = trackedInt();
    private final DataSlot commissioning = trackedInt();

    private final DataSlot cylinderSupply = trackedInt();
    private final DataSlot cylinderPathEdges = trackedInt();
    private final DataSlot cylinderObservedLoss = trackedInt();
    private final DataSlot cylinderLineLoss = trackedInt();
    private final DataSlot cylinderRestrictionLoss = trackedInt();
    private final DataSlot cylinderResponsePeriod = trackedInt();
    private final DataSlot cylinderRemainingTicks = trackedInt();
    private final DataSlot cylinderVelocity = trackedInt();
    private final DataSlot cylinderError = trackedInt();
    private final DataSlot cylinderStallTicks = trackedInt();
    private final DataSlot cylinderReversals = trackedInt();
    private final DataSlot cylinderSamples = trackedInt();
    private final DataSlot compressorTrackingError = trackedInt();
    private final DataSlot compressorRunTicks = trackedInt();

    public PneumaticSystemMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public PneumaticSystemMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.PNEUMATIC_SYSTEM.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0); stateFlag.set(0); facing.set(-1); inputFacing.set(-1);
        inputQuality.set(PortQuality.NO_SIGNAL.ordinal()); outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        upstreamPressure.set(0); downstreamPressure.set(0); upstreamQuality.set(PortQuality.NO_SIGNAL.ordinal()); downstreamQuality.set(PortQuality.NO_SIGNAL.ordinal());
        commissioning.set(CommissioningStatus.NOT_READY.code());
        cylinderSupply.set(0); cylinderPathEdges.set(0); cylinderObservedLoss.set(0); cylinderLineLoss.set(0); cylinderRestrictionLoss.set(0);
        cylinderResponsePeriod.set(0); cylinderRemainingTicks.set(0); cylinderVelocity.set(0); cylinderError.set(0); cylinderStallTicks.set(0); cylinderReversals.set(0); cylinderSamples.set(0);
        compressorTrackingError.set(0); compressorRunTicks.set(0);

        if (block instanceof AirCompressorBlock) {
            kind.set(KIND_COMPRESSOR);
            primary.set(AirCompressorBlock.commandSignal(level, blockPos));
            secondary.set(AirCompressorBlock.commandedPressure(level, blockPos));
            tertiary.set(AirCompressorBlock.actualPressure(level, blockPos));
            auxiliary.set(AirCompressorBlock.startCount(level, blockPos));
            stateFlag.set(state.getValue(AirCompressorBlock.RESPONSE_MODE));
            compressorTrackingError.set(AirCompressorBlock.trackingError(level, blockPos));
            compressorRunTicks.set(AirCompressorBlock.runTicks(level, blockPos));
            setNodeQuality();
        } else if (block instanceof PneumaticPipeBlock) {
            kind.set(KIND_PIPE); primary.set(PneumaticNetwork.pressure(level, blockPos)); setNodeQuality();
        } else if (block instanceof AirReservoirBlock) {
            kind.set(KIND_RESERVOIR); primary.set(AirReservoirBlock.storedPressure(level, blockPos)); secondary.set(PneumaticNetwork.pressure(level, blockPos)); setNodeQuality();
        } else if (block instanceof PressureRegulatorBlock) {
            kind.set(KIND_REGULATOR); primary.set(PneumaticNetwork.pressure(level, blockPos)); secondary.set(PressureRegulatorBlock.setpointPressure(state)); tertiary.set(state.getValue(PressureRegulatorBlock.SETPOINT)); facing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal()); inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal()); setNodeQuality();
        } else if (block instanceof PneumaticReceiverBlock receiver) {
            kind.set(KIND_RECEIVER); directionalSnapshots(state, receiver); secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
        } else if (block instanceof PneumaticValveBlock valve) {
            kind.set(KIND_VALVE); directionalSnapshots(state, valve); stateFlag.set(state.getValue(PneumaticValveBlock.OPEN) ? 1 : 0);
        } else if (block instanceof PneumaticCheckValveBlock valve) {
            kind.set(KIND_CHECK_VALVE); directionalSnapshots(state, valve);
        } else if (block instanceof PneumaticFlowMeterBlock meter) {
            kind.set(KIND_FLOW_METER); directionalSnapshots(state, meter);
            primary.set(PneumaticFlowMeterBlock.flowProxy(level, blockPos)); secondary.set(PneumaticFlowMeterBlock.pressureDrop(level, blockPos));
            tertiary.set(PneumaticFlowMeterBlock.inletPressure(level, blockPos)); auxiliary.set(PneumaticFlowMeterBlock.outletPressure(level, blockPos));
            long samples = PneumaticFlowMeterBlock.measurement(level, blockPos).sampleCount(); stateFlag.set((int) Math.min(Integer.MAX_VALUE, samples));
            Direction in = DirectionalDomainBlock.seriesInputSide(state), out = DirectionalDomainBlock.seriesOutputSide(state);
            PneumaticObservationSupport.Observation upstream = PneumaticObservationSupport.observe(level, blockPos.relative(in, 2));
            PneumaticObservationSupport.Observation downstream = PneumaticObservationSupport.observe(level, blockPos.relative(out, 2));
            upstreamPressure.set(upstream.pressure()); downstreamPressure.set(downstream.pressure()); upstreamQuality.set(upstream.quality().ordinal()); downstreamQuality.set(downstream.quality().ordinal());
            commissioning.set(flowCommissioning(samples, inputQuality(), outputQuality(), upstream.quality(), downstream.quality(), secondary.get()).code());
        } else if (block instanceof PneumaticProportionalValveBlock valve) {
            kind.set(KIND_PROPORTIONAL); directionalSnapshots(state, valve); tertiary.set(PneumaticProportionalValveBlock.opening(level, blockPos)); auxiliary.set(PneumaticNetwork.pressure(level, blockPos));
        } else if (block instanceof PneumaticReliefValveBlock valve) {
            kind.set(KIND_RELIEF); directionalSnapshots(state, valve); tertiary.set(state.getValue(PneumaticReliefValveBlock.SETPOINT) * 25); auxiliary.set(PneumaticReliefValveBlock.ventEvents(level, blockPos)); stateFlag.set(PneumaticReliefValveBlock.venting(level, blockPos) ? 1 : 0);
        } else if (block instanceof PneumaticCylinderBlock cylinder) {
            kind.set(KIND_CYLINDER); directionalSnapshots(state, cylinder);
            primary.set(PneumaticCylinderBlock.pressure(level, blockPos)); secondary.set(PneumaticCylinderBlock.position(level, blockPos)); tertiary.set(PneumaticCylinderBlock.target(level, blockPos)); auxiliary.set(PneumaticCylinderBlock.travel(level, blockPos));
            PneumaticNetwork.ActuatorPathEvidence path = PneumaticNetwork.actuatorPathEvidence(level, blockPos);
            cylinderSupply.set(path.supplyPressure()); cylinderPathEdges.set(path.pathEdges()); cylinderObservedLoss.set(path.observedLoss()); cylinderLineLoss.set(path.lineLoss()); cylinderRestrictionLoss.set(path.restrictionLoss());
            cylinderResponsePeriod.set(PneumaticCylinderBlock.responsePeriodTicks(primary.get())); cylinderRemainingTicks.set(PneumaticCylinderBlock.estimatedRemainingTicks(level, blockPos));
            cylinderVelocity.set(PneumaticCylinderBlock.velocity(level, blockPos)); cylinderError.set(PneumaticCylinderBlock.error(level, blockPos)); cylinderStallTicks.set(PneumaticCylinderBlock.stallTicks(level, blockPos)); cylinderReversals.set(PneumaticCylinderBlock.reversals(level, blockPos)); cylinderSamples.set(PneumaticCylinderBlock.samples(level, blockPos));
        } else kind.set(-1);
    }

    private static CommissioningStatus flowCommissioning(long samples, PortQuality in, PortQuality out, PortQuality upstream, PortQuality downstream, int meterDrop) {
        if (samples < 4 || in == PortQuality.NO_SIGNAL || out == PortQuality.NO_SIGNAL) return CommissioningStatus.NOT_READY;
        if (hardFault(in) || hardFault(out)) return CommissioningStatus.FAIL;
        if (in != PortQuality.VALID || out != PortQuality.VALID) return CommissioningStatus.MARGINAL;
        boolean completeWitness = upstream == PortQuality.VALID && downstream == PortQuality.VALID;
        if (!completeWitness) return CommissioningStatus.MARGINAL;
        return meterDrop >= 50 ? CommissioningStatus.FAIL : meterDrop >= 25 ? CommissioningStatus.MARGINAL : CommissioningStatus.PASS;
    }

    private static boolean hardFault(PortQuality q) {
        return q == PortQuality.FAULT || q == PortQuality.DOMAIN_MISMATCH || q == PortQuality.TOPOLOGY_ERROR;
    }

    private void setNodeQuality() {
        PneumaticObservationSupport.Observation observation = PneumaticObservationSupport.observe(level, blockPos);
        inputQuality.set(observation.quality().ordinal());
        outputQuality.set(observation.quality().ordinal());
    }

    private void directionalSnapshots(BlockState state, EngineeringPortProvider provider) {
        Direction out;
        Direction in;
        if (state.hasProperty(DirectionalDomainBlock.FACING)) {
            out = DirectionalDomainBlock.seriesOutputSide(state);
            in = DirectionalDomainBlock.seriesInputSide(state);
        } else {
            out = DirectionalSignalBlock.seriesOutputSide(state);
            in = DirectionalSignalBlock.seriesInputSide(state);
        }
        facing.set(out.ordinal());
        inputFacing.set(in.ordinal());
        provider.engineeringSnapshot(level, blockPos, state, in).ifPresent(snapshot -> {
            primary.set((int) Math.round(snapshot.value()));
            inputQuality.set(snapshot.quality().ordinal());
        });
        provider.engineeringSnapshot(level, blockPos, state, out).ifPresent(snapshot -> {
            secondary.set((int) Math.round(snapshot.value()));
            outputQuality.set(snapshot.quality().ordinal());
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof AirCompressorBlock) {
            if (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT) {
                changed = AirCompressorBlock.stepResponseMode(
                        level, blockPos, id == BUTTON_PARAMETER_NEXT);
            }
        } else if (block instanceof PressureRegulatorBlock) {
            if (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT) {
                int value = state.getValue(PressureRegulatorBlock.SETPOINT);
                value = id == BUTTON_PARAMETER_NEXT ? value % 4 + 1 : value <= 1 ? 4 : value - 1;
                level.setBlock(blockPos, state.setValue(PressureRegulatorBlock.SETPOINT, value), Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, blockPos);
                changed = true;
            } else {
                changed = rotateDirectional(block, id);
            }
        } else if (block instanceof PneumaticValveBlock) {
            if (id == BUTTON_TOGGLE) {
                level.setBlock(blockPos, state.setValue(PneumaticValveBlock.OPEN, !state.getValue(PneumaticValveBlock.OPEN)), Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, blockPos);
                changed = true;
            } else {
                changed = rotateDirectional(block, id);
            }
        } else if (block instanceof PneumaticReliefValveBlock) {
            if (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT) {
                int value = state.getValue(PneumaticReliefValveBlock.SETPOINT);
                value = id == BUTTON_PARAMETER_NEXT ? value % 4 + 1 : value <= 1 ? 4 : value - 1;
                level.setBlock(blockPos, state.setValue(PneumaticReliefValveBlock.SETPOINT, value), Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, blockPos);
                changed = true;
            } else {
                changed = rotateDirectional(block, id);
            }
        } else if (block instanceof PneumaticReceiverBlock
                || block instanceof PneumaticCheckValveBlock
                || block instanceof PneumaticFlowMeterBlock
                || block instanceof PneumaticProportionalValveBlock
                || block instanceof PneumaticCylinderBlock) {
            changed = rotateDirectional(block, id);
        } else {
            return false;
        }

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private boolean rotateDirectional(Block block, int id) {
        boolean clockwise;
        boolean changed;
        if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            clockwise = id == BUTTON_ROTATE_RIGHT;
            if (block instanceof DirectionalDomainBlock) {
                changed = DirectionalDomainBlock.rotateSeriesAxis(level, blockPos, clockwise);
            } else if (block instanceof DirectionalSignalBlock) {
                changed = DirectionalSignalBlock.rotateSeriesAxis(level, blockPos, clockwise);
            } else {
                return false;
            }
        } else if (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT) {
            clockwise = id == BUTTON_INPUT_RIGHT;
            if (block instanceof DirectionalDomainBlock) {
                changed = DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise);
            } else if (block instanceof DirectionalSignalBlock) {
                changed = DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise);
            } else {
                return false;
            }
        } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT) {
            clockwise = id == BUTTON_OUTPUT_RIGHT;
            if (block instanceof DirectionalDomainBlock) {
                changed = DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise);
            } else if (block instanceof DirectionalSignalBlock) {
                changed = DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise);
            } else {
                return false;
            }
        } else {
            return false;
        }
        if (changed && level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, blockPos);
        return changed;
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int auxiliary() { return auxiliary.get(); }
    public int stateFlag() { return stateFlag.get(); }
    public PortQuality inputQuality() { return quality(inputQuality.get()); }
    public PortQuality outputQuality() { return quality(outputQuality.get()); }
    public int upstreamPressure() { return upstreamPressure.get(); }
    public int downstreamPressure() { return downstreamPressure.get(); }
    public PortQuality upstreamQuality() { return quality(upstreamQuality.get()); }
    public PortQuality downstreamQuality() { return quality(downstreamQuality.get()); }
    public int cylinderSupply() { return cylinderSupply.get(); }
    public int cylinderPathEdges() { return cylinderPathEdges.get(); }
    public int cylinderObservedLoss() { return cylinderObservedLoss.get(); }
    public int cylinderLineLoss() { return cylinderLineLoss.get(); }
    public int cylinderRestrictionLoss() { return cylinderRestrictionLoss.get(); }
    public int cylinderResponsePeriod() { return cylinderResponsePeriod.get(); }
    public int cylinderRemainingTicks() { return cylinderRemainingTicks.get(); }
    public int cylinderVelocity() { return cylinderVelocity.get(); }
    public int cylinderError() { return cylinderError.get(); }
    public int cylinderStallTicks() { return cylinderStallTicks.get(); }
    public int cylinderReversals() { return cylinderReversals.get(); }
    public int cylinderSamples() { return cylinderSamples.get(); }
    public int compressorTrackingError() { return compressorTrackingError.get(); }
    public int compressorRunTicks() { return compressorRunTicks.get(); }
    public CommissioningStatus commissioningStatus() { return CommissioningStatus.fromCode(commissioning.get()); }

    private static PortQuality quality(int ordinal) {
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public Direction outputDirection() {
        int ordinal = facing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }

    public Direction inputDirection() {
        int ordinal = inputFacing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? outputDirection().getOpposite() : all[ordinal];
    }

    public boolean directional() { return facing.get() >= 0; }
}
