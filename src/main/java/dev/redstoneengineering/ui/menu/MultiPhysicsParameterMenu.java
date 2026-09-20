package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Device-aware parameter notebook model for ten multi-physics blocks.
 *
 * <p>The transport is shared; parameter semantics are not. Each kind maps its own physical
 * parameters, bounds, live response and authoritative server action.</p>
 */
public final class MultiPhysicsParameterMenu extends EngineeringDeviceMenu {
    public static final int KIND_QUARTZ_OSCILLATOR = 0;
    public static final int KIND_QUARTZ_DIVIDER = 1;
    public static final int KIND_QUARTZ_DELAY = 2;
    public static final int KIND_AMETHYST_FILTER = 3;
    public static final int KIND_AMETHYST_RESONATOR = 4;
    public static final int KIND_PRESSURE_REGULATOR = 5;
    public static final int KIND_PROPORTIONAL_VALVE = 6;
    public static final int KIND_OPTICAL_ATTENUATOR = 7;
    public static final int KIND_OPTICAL_CHANNEL_FILTER = 8;
    public static final int KIND_RANGE_SENSOR = 9;

    public static final int BUTTON_P0_MINUS = 0;
    public static final int BUTTON_P0_PLUS = 1;
    public static final int BUTTON_P1_MINUS = 2;
    public static final int BUTTON_P1_PLUS = 3;
    public static final int BUTTON_P2_MINUS = 4;
    public static final int BUTTON_P2_PLUS = 5;

    private final DataSlot kind = trackedInt();
    private final DataSlot p0 = trackedInt();
    private final DataSlot p1 = trackedInt();
    private final DataSlot p2 = trackedInt();
    private final DataSlot liveA = trackedInt();
    private final DataSlot liveB = trackedInt();
    private final DataSlot liveC = trackedInt();
    private final DataSlot liveD = trackedInt();

    public MultiPhysicsParameterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public MultiPhysicsParameterMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.MULTI_PHYSICS_PARAMETER.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        p0.set(0); p1.set(0); p2.set(0); liveA.set(0); liveB.set(0); liveC.set(0); liveD.set(0);

        if (block instanceof QuartzOscillatorBlock) {
            kind.set(KIND_QUARTZ_OSCILLATOR);
            p0.set(QuartzOscillatorBlock.configuredPeriodTicks(level, blockPos, state));
            liveA.set(QuartzOscillatorBlock.effectivePeriodTicks(level, blockPos, state));
            liveB.set(QuartzOscillatorBlock.edgeCount(level, blockPos));
            liveC.set(QuartzOscillatorBlock.periodChangePending(level, blockPos, state) ? 1 : 0);
            liveD.set(state.getValue(QuartzOscillatorBlock.ACTIVE) ? 1 : 0);
        } else if (block instanceof QuartzClockDividerBlock) {
            kind.set(KIND_QUARTZ_DIVIDER);
            p0.set(QuartzClockDividerBlock.configuredDivision(level, blockPos, state));
            liveA.set(QuartzClockDividerBlock.countedEdges(level, blockPos));
            liveB.set(QuartzClockDividerBlock.phaseStarted(level, blockPos) ? 1 : 0);
            liveC.set(QuartzClockDividerBlock.initialized(level, blockPos) ? 1 : 0);
        } else if (block instanceof QuartzPhaseDelayBlock) {
            kind.set(KIND_QUARTZ_DELAY);
            p0.set(QuartzPhaseDelayBlock.configuredDelayTicks(level, blockPos, state));
            liveA.set(QuartzPhaseDelayBlock.queuedEdges(level, blockPos));
            liveB.set(QuartzPhaseDelayBlock.pendingTicks(level, blockPos));
            liveC.set(QuartzPhaseDelayBlock.droppedEdges(level, blockPos));
            liveD.set(QuartzPhaseDelayBlock.initialized(level, blockPos) ? 1 : 0);
        } else if (block instanceof AmethystFrequencyFilterBlock) {
            kind.set(KIND_AMETHYST_FILTER);
            p0.set(state.getValue(AmethystFrequencyFilterBlock.TARGET));
            var e = AmethystFrequencyFilterBlock.evidence(level, blockPos, state);
            liveA.set(e.inputFrequency()); liveB.set(e.inputAmplitude());
            liveC.set(e.expectedOutputAmplitude()); liveD.set(e.matched() ? 1 : 0);
        } else if (block instanceof AmethystTunedResonatorBlock) {
            kind.set(KIND_AMETHYST_RESONATOR);
            p0.set(state.getValue(AmethystTunedResonatorBlock.NATURAL));
            p1.set(state.getValue(AmethystTunedResonatorBlock.Q_INDEX));
            var e = AmethystTunedResonatorBlock.response(level, blockPos, state);
            liveA.set(e.bandwidth()); liveB.set(e.targetAmplitude());
            liveC.set(e.actualAmplitude()); liveD.set(e.ringDown() ? 2 : e.responding() ? 1 : 0);
        } else if (block instanceof PressureRegulatorBlock) {
            kind.set(KIND_PRESSURE_REGULATOR);
            p0.set(PressureRegulatorBlock.setpointPressure(level, blockPos, state));
            p1.set(PressureRegulatorBlock.responseRate(level, blockPos, state));
            liveA.set(PressureRegulatorBlock.inletPressure(level, blockPos, state));
            liveB.set(PressureRegulatorBlock.actualRegulatedPressure(level, blockPos));
            liveC.set(PressureRegulatorBlock.trackingError(level, blockPos, state));
        } else if (block instanceof PneumaticProportionalValveBlock) {
            kind.set(KIND_PROPORTIONAL_VALVE);
            p0.set(PneumaticProportionalValveBlock.configuredResponseRate(level, blockPos, state));
            liveA.set(PneumaticProportionalValveBlock.commandedOpening(level, blockPos));
            liveB.set(PneumaticProportionalValveBlock.actualOpening(level, blockPos));
            liveC.set(PneumaticProportionalValveBlock.trackingError(level, blockPos));
            liveD.set(PneumaticProportionalValveBlock.reversals(level, blockPos));
        } else if (block instanceof OpticalAttenuatorBlock) {
            kind.set(KIND_OPTICAL_ATTENUATOR);
            p0.set(OpticalAttenuatorBlock.configuredLoss(level, blockPos, state));
            var e = OpticalAttenuatorBlock.evidence(level, blockPos, state);
            liveA.set(e.inputIntensity()); liveB.set(e.expectedOutputIntensity());
            liveC.set(e.channel()); liveD.set(e.fullyAttenuated() ? 1 : 0);
        } else if (block instanceof OpticalChannelFilterBlock) {
            kind.set(KIND_OPTICAL_CHANNEL_FILTER);
            p0.set(state.getValue(OpticalChannelFilterBlock.TARGET));
            var e = OpticalChannelFilterBlock.evidence(level, blockPos, state);
            liveA.set(e.inputChannel()); liveB.set(e.inputIntensity());
            liveC.set(e.expectedOutputIntensity()); liveD.set(e.matched() ? 1 : 0);
        } else if (block instanceof RangeSensorBlock) {
            kind.set(KIND_RANGE_SENSOR);
            p0.set(RangeSensorBlock.configuredRange(level, blockPos, state));
            p1.set(state.getValue(RangeSensorBlock.MODE));
            p2.set(state.getValue(RangeSensorBlock.RESPONSE));
            var scan = RangeSensorBlock.lastScan(level, blockPos, state);
            liveA.set(scan.distance()); liveB.set(scan.scannedCells());
            liveC.set(state.getValue(RangeSensorBlock.OUTPUT)); liveD.set(scan.status().ordinal());
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
        int delta = (id == BUTTON_P0_MINUS || id == BUTTON_P1_MINUS || id == BUTTON_P2_MINUS) ? -1 : 1;
        int slot = (id == BUTTON_P0_MINUS || id == BUTTON_P0_PLUS) ? 0
                : (id == BUTTON_P1_MINUS || id == BUTTON_P1_PLUS) ? 1
                : (id == BUTTON_P2_MINUS || id == BUTTON_P2_PLUS) ? 2 : -1;
        if (slot < 0) return false;

        boolean changed = false;
        if (block instanceof QuartzOscillatorBlock && slot == 0) {
            changed = QuartzOscillatorBlock.setConfiguredPeriodTicks(server, blockPos, p0.get() + delta);
        } else if (block instanceof QuartzClockDividerBlock && slot == 0) {
            changed = QuartzClockDividerBlock.setConfiguredDivision(server, blockPos, p0.get() + delta);
        } else if (block instanceof QuartzPhaseDelayBlock && slot == 0) {
            changed = QuartzPhaseDelayBlock.setConfiguredDelayTicks(server, blockPos, p0.get() + delta);
        } else if (block instanceof AmethystFrequencyFilterBlock filter && slot == 0) {
            int next = Math.max(1, Math.min(15, p0.get() + delta));
            if (next != p0.get()) {
                level.setBlock(blockPos, state.setValue(AmethystFrequencyFilterBlock.TARGET, next), Block.UPDATE_CLIENTS);
                server.scheduleTick(blockPos, filter, 1); changed = true;
            }
        } else if (block instanceof AmethystTunedResonatorBlock resonator) {
            if (slot == 0) {
                int next = Math.max(1, Math.min(15, p0.get() + delta));
                if (next != p0.get()) { level.setBlock(blockPos, state.setValue(AmethystTunedResonatorBlock.NATURAL, next), Block.UPDATE_CLIENTS); changed = true; }
            } else if (slot == 1) {
                int next = Math.max(1, Math.min(4, p1.get() + delta));
                if (next != p1.get()) { level.setBlock(blockPos, state.setValue(AmethystTunedResonatorBlock.Q_INDEX, next), Block.UPDATE_CLIENTS); changed = true; }
            }
            if (changed) server.scheduleTick(blockPos, resonator, 1);
        } else if (block instanceof PressureRegulatorBlock && (slot == 0 || slot == 1)) {
            int setpoint = p0.get() + (slot == 0 ? delta : 0);
            int rate = p1.get() + (slot == 1 ? delta : 0);
            changed = PressureRegulatorBlock.setEngineeringParameters(server, blockPos, setpoint, rate);
        } else if (block instanceof PneumaticProportionalValveBlock && slot == 0) {
            changed = PneumaticProportionalValveBlock.setConfiguredResponseRate(server, blockPos, p0.get() + delta);
        } else if (block instanceof OpticalAttenuatorBlock && slot == 0) {
            changed = OpticalAttenuatorBlock.setConfiguredLoss(server, blockPos, p0.get() + delta);
        } else if (block instanceof OpticalChannelFilterBlock && slot == 0) {
            int next = Math.max(0, Math.min(15, p0.get() + delta));
            if (next != p0.get()) {
                BlockState nextState = state.setValue(OpticalChannelFilterBlock.TARGET, next);
                level.setBlock(blockPos, nextState, Block.UPDATE_CLIENTS);
                OpticalChannelFilterBlock.configurationChanged(server, blockPos, nextState); changed = true;
            }
        } else if (block instanceof RangeSensorBlock sensor) {
            if (slot == 0) {
                changed = RangeSensorBlock.setConfiguredRange(server, blockPos, p0.get() + delta);
            } else if (slot == 1) {
                int next = Math.floorMod(p1.get() + delta, 3);
                level.setBlock(blockPos, state.setValue(RangeSensorBlock.MODE, next), Block.UPDATE_CLIENTS);
                server.scheduleTick(blockPos, sensor, 1); changed = true;
            } else if (slot == 2) {
                int next = Math.floorMod(p2.get() + delta, 4);
                level.setBlock(blockPos, state.setValue(RangeSensorBlock.RESPONSE, next), Block.UPDATE_CLIENTS);
                server.scheduleTick(blockPos, sensor, 1); changed = true;
            }
        }

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int kind() { return kind.get(); }
    public int p0() { return p0.get(); }
    public int p1() { return p1.get(); }
    public int p2() { return p2.get(); }
    public int liveA() { return liveA.get(); }
    public int liveB() { return liveB.get(); }
    public int liveC() { return liveC.get(); }
    public int liveD() { return liveD.get(); }
}
