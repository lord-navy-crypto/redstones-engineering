package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalDomainSourceBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzLabOscillatorBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzPhaseDelayBlock;
import dev.redstoneengineering.block.QuartzStabilityMonitorBlock;
import dev.redstoneengineering.block.QuartzTimingLineBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
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

/** Server-authoritative HMI model for Quartz source, processing, and metrology devices. */
public final class QuartzTimingMenu extends EngineeringDeviceMenu {
    public static final int KIND_OSCILLATOR = 0;
    public static final int KIND_DIVIDER = 1;
    public static final int KIND_STABILITY = 2;
    public static final int KIND_DELAY = 3;
    public static final int KIND_LAB_OSCILLATOR = 4;

    public static final int BUTTON_PARAMETER_PREVIOUS = 0;
    public static final int BUTTON_PARAMETER_NEXT = 1;
    /** Legacy whole-route actions retained for compatibility. */
    public static final int BUTTON_ROTATE_LEFT = 2;
    public static final int BUTTON_ROTATE_RIGHT = 3;
    public static final int BUTTON_RESET_MEASUREMENT = 4;
    public static final int BUTTON_INPUT_LEFT = 5;
    public static final int BUTTON_INPUT_RIGHT = 6;
    public static final int BUTTON_OUTPUT_LEFT = 7;
    public static final int BUTTON_OUTPUT_RIGHT = 8;
    public static final int BUTTON_PARAMETER_COARSE_PREVIOUS = 9;
    public static final int BUTTON_PARAMETER_COARSE_NEXT = 10;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot runtimeA = trackedInt();
    private final DataSlot runtimeB = trackedInt();
    private final DataSlot runtimeC = trackedInt();
    private final DataSlot runtimeD = trackedInt();
    private final DataSlot runtimeE = trackedInt();
    private final DataSlot runtimeF = trackedInt();
    private final DataSlot runtimeG = trackedInt();
    private final DataSlot runtimeH = trackedInt();
    private final DataSlot runtimeI = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();
    private final DataSlot quality = trackedInt();

    public QuartzTimingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public QuartzTimingMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.QUARTZ_TIMING.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        primary.set(0);
        secondary.set(0);
        tertiary.set(0);
        runtimeA.set(0);
        runtimeB.set(0);
        runtimeC.set(0);
        runtimeD.set(0);
        runtimeE.set(0);
        runtimeF.set(0);
        runtimeG.set(0);
        runtimeH.set(0);
        runtimeI.set(0);
        inputFacing.set(-1);
        outputFacing.set(-1);
        quality.set(PortQuality.NO_SIGNAL.ordinal());

        if (block instanceof QuartzLabOscillatorBlock) {
            kind.set(KIND_LAB_OSCILLATOR);
            QuartzLabOscillatorBlock.TimingEvidence evidence =
                    QuartzLabOscillatorBlock.timingEvidence(level, blockPos, state);
            primary.set(state.getValue(QuartzLabOscillatorBlock.ACTIVE) ? 1 : 0);
            secondary.set(QuartzLabOscillatorBlock.configuredPeriodTicks(state));
            tertiary.set(QuartzLabOscillatorBlock.configuredJitterTicks(state));
            runtimeA.set(QuartzLabOscillatorBlock.effectivePeriodTicks(level, blockPos, state));
            runtimeB.set(QuartzLabOscillatorBlock.effectiveJitter(level, blockPos, state));
            runtimeC.set(QuartzLabOscillatorBlock.configurationPending(level, blockPos, state) ? 1 : 0);
            runtimeD.set(evidence.available() ? 1 : 0);
            runtimeE.set(evidence.nominalPeriod());
            runtimeF.set(evidence.lastHalfInterval());
            runtimeG.set(evidence.lastJitterOffset());
            outputFacing.set(DirectionalDomainSourceBlock.outputSide(state).ordinal());
            quality.set(PortQuality.VALID.ordinal());
            return;
        }

        if (block instanceof QuartzOscillatorBlock) {
            kind.set(KIND_OSCILLATOR);
            primary.set(state.getValue(QuartzOscillatorBlock.ACTIVE) ? 1 : 0);
            secondary.set(QuartzOscillatorBlock.configuredPeriodTicks(level, blockPos, state));
            tertiary.set(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX));
            runtimeA.set(QuartzOscillatorBlock.effectivePeriodTicks(level, blockPos, state));
            runtimeB.set(QuartzOscillatorBlock.periodChangePending(level, blockPos, state) ? 1 : 0);
            runtimeC.set(QuartzOscillatorBlock.edgeCount(level, blockPos));
            outputFacing.set(DirectionalDomainSourceBlock.outputSide(state).ordinal());
            quality.set(PortQuality.VALID.ordinal());
            return;
        }

        if (block instanceof QuartzClockDividerBlock) {
            kind.set(KIND_DIVIDER);
            Direction in = DirectionalDomainBlock.seriesInputSide(state);
            Direction out = DirectionalDomainBlock.seriesOutputSide(state);
            inputFacing.set(in.ordinal());
            outputFacing.set(out.ordinal());
            DomainNetwork.QuartzSample inputSample = DomainNetwork.sampleQuartz(level, blockPos.relative(in));
            DomainNetwork.QuartzSample result = DomainNetwork.sampleQuartz(level, blockPos.relative(out));
            primary.set(inputSample.periodTicks());
            secondary.set(result.periodTicks());
            tertiary.set(QuartzClockDividerBlock.configuredDivision(level, blockPos, state));
            runtimeA.set(QuartzClockDividerBlock.countedEdges(level, blockPos));
            runtimeB.set(QuartzClockDividerBlock.initialized(level, blockPos) ? 1 : 0);
            runtimeC.set(QuartzClockDividerBlock.phaseStarted(level, blockPos) ? 1 : 0);
            if (block instanceof EngineeringPortProvider provider) {
                quality.set(provider.engineeringSnapshot(level, blockPos, state, out)
                        .map(snapshot -> snapshot.quality().ordinal()).orElse(PortQuality.NO_SIGNAL.ordinal()));
            }
            return;
        }

        if (block instanceof QuartzPhaseDelayBlock delay) {
            kind.set(KIND_DELAY);
            Direction in = DirectionalDomainBlock.seriesInputSide(state);
            Direction out = DirectionalDomainBlock.seriesOutputSide(state);
            inputFacing.set(in.ordinal());
            outputFacing.set(out.ordinal());
            primary.set(QuartzPhaseDelayBlock.configuredDelayTicks(level, blockPos, state));
            secondary.set(QuartzPhaseDelayBlock.queuedEdges(level, blockPos));
            tertiary.set(QuartzPhaseDelayBlock.pendingTicks(level, blockPos));
            runtimeA.set(QuartzPhaseDelayBlock.droppedEdges(level, blockPos));
            runtimeB.set(QuartzPhaseDelayBlock.initialized(level, blockPos) ? 1 : 0);
            if (delay instanceof EngineeringPortProvider provider) {
                quality.set(provider.engineeringSnapshot(level, blockPos, state, out)
                        .map(snapshot -> snapshot.quality().ordinal()).orElse(PortQuality.NO_SIGNAL.ordinal()));
            }
            return;
        }

        if (block instanceof QuartzStabilityMonitorBlock monitor) {
            kind.set(KIND_STABILITY);
            Direction inputSide = DirectionalDomainBlock.seriesInputSide(state);
            inputFacing.set(inputSide.ordinal());
            QuartzStabilityMonitorBlock.TimingMeasurement measurement = QuartzStabilityMonitorBlock.measurement(level, blockPos);
            DomainNetwork.QuartzSample upstream = DomainNetwork.sampleQuartz(level, blockPos.relative(inputSide));
            primary.set(measurement.period());
            secondary.set(measurement.nominalError());
            tertiary.set(upstream.periodTicks());
            runtimeA.set(measurement.initialized() ? 1 : 0);
            runtimeB.set(measurement.referenceEdgeSeen() ? 1 : 0);
            runtimeC.set(measurement.currentMeasurement() ? 1 : 0);
            runtimeD.set(measurement.sampleCount());
            runtimeE.set(measurement.minPeriod());
            runtimeF.set(measurement.maxPeriod());
            runtimeG.set(measurement.meanPeriodX100());
            runtimeH.set(measurement.jitter());
            runtimeI.set(measurement.maxNominalError());
            if (monitor instanceof EngineeringPortProvider provider) {
                quality.set(provider.engineeringSnapshot(level, blockPos, state, inputSide)
                        .map(snapshot -> snapshot.quality().ordinal()).orElse(PortQuality.NO_SIGNAL.ordinal()));
            }
            return;
        }

        kind.set(-1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof QuartzLabOscillatorBlock) {
            if (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT) {
                changed = QuartzLabOscillatorBlock.stepPeriodIndex(
                        level, blockPos, id == BUTTON_PARAMETER_NEXT);
            } else if (id == BUTTON_PARAMETER_COARSE_PREVIOUS || id == BUTTON_PARAMETER_COARSE_NEXT) {
                changed = QuartzLabOscillatorBlock.stepJitter(
                        level, blockPos, id == BUTTON_PARAMETER_COARSE_NEXT);
            } else if (id == BUTTON_RESET_MEASUREMENT) {
                changed = QuartzLabOscillatorBlock.resetTimingConfiguration(level, blockPos);
            } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT
                    || id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
                boolean clockwise = id == BUTTON_OUTPUT_RIGHT || id == BUTTON_ROTATE_RIGHT;
                changed = DirectionalDomainSourceBlock.rotateOutput(level, blockPos, clockwise);
                if (changed && level instanceof ServerLevel server) {
                    DomainNetwork.recomputeQuartzAround(server, blockPos);
                }
            } else return false;
        } else         if (block instanceof QuartzOscillatorBlock) {
            if (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT
                    || id == BUTTON_PARAMETER_COARSE_PREVIOUS || id == BUTTON_PARAMETER_COARSE_NEXT) {
                if (!(level instanceof ServerLevel server)) return false;
                int delta = switch (id) {
                    case BUTTON_PARAMETER_PREVIOUS -> -QuartzOscillatorBlock.FINE_STEP_TICKS;
                    case BUTTON_PARAMETER_NEXT -> QuartzOscillatorBlock.FINE_STEP_TICKS;
                    case BUTTON_PARAMETER_COARSE_PREVIOUS -> -QuartzOscillatorBlock.COARSE_STEP_TICKS;
                    default -> QuartzOscillatorBlock.COARSE_STEP_TICKS;
                };
                changed = QuartzOscillatorBlock.adjustConfiguredPeriodTicks(server, blockPos, delta);
            } else if (id == BUTTON_RESET_MEASUREMENT) {
                if (!(level instanceof ServerLevel server)) return false;
                changed = QuartzOscillatorBlock.resetConfiguredPeriodTicks(server, blockPos);
            } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT
                    || id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
                boolean clockwise = id == BUTTON_OUTPUT_RIGHT || id == BUTTON_ROTATE_RIGHT;
                changed = DirectionalDomainSourceBlock.rotateOutput(level, blockPos, clockwise);
                if (changed && level instanceof ServerLevel server) {
                    DomainNetwork.recomputeQuartzAround(server, blockPos);
                }
            } else return false;
        } else if (block instanceof QuartzClockDividerBlock || block instanceof QuartzStabilityMonitorBlock || block instanceof QuartzPhaseDelayBlock) {
            if (block instanceof QuartzStabilityMonitorBlock monitor && id == BUTTON_RESET_MEASUREMENT) {
                RuntimeIntStore.remove(level, "quartz_stability", blockPos);
                level.scheduleTick(blockPos, monitor, 1);
                changed = true;
            } else if (block instanceof QuartzClockDividerBlock && (id == BUTTON_PARAMETER_NEXT || id == BUTTON_PARAMETER_PREVIOUS)) {
                if (!(level instanceof ServerLevel server)) return false;
                int current = QuartzClockDividerBlock.configuredDivision(level, blockPos, state);
                changed = QuartzClockDividerBlock.setConfiguredDivision(
                        server, blockPos, current + (id == BUTTON_PARAMETER_NEXT
                                ? QuartzClockDividerBlock.PARAMETER_STEP
                                : -QuartzClockDividerBlock.PARAMETER_STEP));
            } else if (block instanceof QuartzPhaseDelayBlock && (id == BUTTON_PARAMETER_NEXT || id == BUTTON_PARAMETER_PREVIOUS)) {
                if (!(level instanceof ServerLevel server)) return false;
                changed = QuartzPhaseDelayBlock.setConfiguredDelayTicks(
                        server, blockPos, primary.get() + (id == BUTTON_PARAMETER_NEXT
                                ? QuartzPhaseDelayBlock.PARAMETER_STEP_TICKS
                                : -QuartzPhaseDelayBlock.PARAMETER_STEP_TICKS));
            } else if (block instanceof QuartzStabilityMonitorBlock
                    && (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT
                    || id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT)) {
                boolean clockwise = id == BUTTON_INPUT_RIGHT || id == BUTTON_ROTATE_RIGHT;
                changed = DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise);
            } else if ((block instanceof QuartzClockDividerBlock || block instanceof QuartzPhaseDelayBlock)
                    && (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT
                    || id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT
                    || id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT)) {
                // Divider and phase delay are physically straight-through two-port timing devices.
                // Legacy RX/TX button IDs are retained, but all of them rotate one rigid opposite-face axis.
                boolean clockwise = id == BUTTON_INPUT_RIGHT || id == BUTTON_OUTPUT_RIGHT || id == BUTTON_ROTATE_RIGHT;
                changed = DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, clockwise);
            } else return false;
        } else return false;

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int runtimeA() { return runtimeA.get(); }
    public int runtimeB() { return runtimeB.get(); }
    public int runtimeC() { return runtimeC.get(); }
    public int runtimeD() { return runtimeD.get(); }
    public int runtimeE() { return runtimeE.get(); }
    public int runtimeF() { return runtimeF.get(); }
    public int runtimeG() { return runtimeG.get(); }
    public int runtimeH() { return runtimeH.get(); }
    public int runtimeI() { return runtimeI.get(); }
    public int facingOrdinal() { return outputFacing.get(); }
    public boolean hasInputEndpoint() { return inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return outputFacing.get() >= 0; }

    public PortQuality quality() {
        int ordinal = quality.get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public Direction inputDirection() {
        int ordinal = inputFacing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }

    public Direction outputDirection() {
        int ordinal = outputFacing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }

    public Direction logicalFacing() {
        if (hasOutputEndpoint()) return outputDirection();
        return hasInputEndpoint() ? inputDirection().getOpposite() : Direction.NORTH;
    }
}
