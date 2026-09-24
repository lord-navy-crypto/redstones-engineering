package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RedstoneObservationSupport;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Device-aware notebook for the second ten-block engineering parameter batch. */
public final class AdvancedParameterMenu extends EngineeringDeviceMenu {
    public static final int KIND_PRECISION_FILTER = 0;
    public static final int KIND_PULSE_SHAPER = 1;
    public static final int KIND_EDGE_DETECTOR = 2;
    public static final int KIND_SIGNAL_AMPLIFIER = 3;
    public static final int KIND_ELECTROMAGNET = 4;
    public static final int KIND_INDUCTION_COIL = 5;
    public static final int KIND_RELIEF_VALVE = 6;
    public static final int KIND_LAPIS_NOISE = 7;
    public static final int KIND_LAPIS_RANGE = 8;
    public static final int KIND_OPTICAL_EMITTER = 9;

    public static final int BUTTON_P0_MINUS = 0;
    public static final int BUTTON_P0_PLUS = 1;
    public static final int BUTTON_P1_MINUS = 2;
    public static final int BUTTON_P1_PLUS = 3;
    public static final int BUTTON_P2_MINUS = 4;
    public static final int BUTTON_P2_PLUS = 5;
    public static final int BUTTON_P3_TOGGLE = 6;

    private final DataSlot kind = trackedInt();
    private final DataSlot p0 = trackedInt();
    private final DataSlot p1 = trackedInt();
    private final DataSlot p2 = trackedInt();
    private final DataSlot p3 = trackedInt();
    private final DataSlot liveA = trackedInt();
    private final DataSlot liveB = trackedInt();
    private final DataSlot liveC = trackedInt();
    private final DataSlot liveD = trackedInt();

    public AdvancedParameterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public AdvancedParameterMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.ADVANCED_PARAMETER.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        p0.set(0); p1.set(0); p2.set(0); p3.set(0);
        liveA.set(0); liveB.set(0); liveC.set(0); liveD.set(0);

        if (block instanceof PrecisionFilterBlock) {
            kind.set(KIND_PRECISION_FILTER);
            p0.set(PrecisionFilterBlock.riseRate(level, blockPos, state));
            p1.set(PrecisionFilterBlock.fallRate(level, blockPos, state));
            liveA.set(PrecisionFilterBlock.input(level, blockPos, state));
            liveB.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            liveC.set(PrecisionFilterBlock.trackingError(level, blockPos, state));
            liveD.set(PrecisionFilterBlock.settleTicks(level, blockPos, state));
        } else if (block instanceof PulseShaperBlock) {
            kind.set(KIND_PULSE_SHAPER);
            p0.set(PulseShaperBlock.configuredWidth(level, blockPos, state));
            p1.set(PulseShaperBlock.threshold(level, blockPos));
            p2.set(PulseShaperBlock.hysteresis(level, blockPos));
            p3.set(state.getValue(PulseShaperBlock.RETRIGGERABLE) ? 1 : 0);
            liveA.set(PulseShaperBlock.pulseRemaining(level, blockPos));
            liveB.set(PulseShaperBlock.triggerCount(level, blockPos));
            liveC.set(PulseShaperBlock.suppressedTriggerCount(level, blockPos));
            liveD.set(PulseShaperBlock.rearmThreshold(level, blockPos));
        } else if (block instanceof EdgeDetectorBlock) {
            kind.set(KIND_EDGE_DETECTOR);
            p0.set(state.getValue(EdgeDetectorBlock.MODE));
            p1.set(EdgeDetectorBlock.configuredPulseWidth(level, blockPos, state));
            liveA.set(EdgeDetectorBlock.edgeCount(level, blockPos));
            liveB.set(EdgeDetectorBlock.pulseRemaining(level, blockPos));
            liveC.set(EdgeDetectorBlock.rejectedInputEpisodes(level, blockPos));
            liveD.set(EdgeDetectorBlock.lastEdgeAgeTicks(level, blockPos));
        } else if (block instanceof SignalAmplifierBlock amplifier) {
            kind.set(KIND_SIGNAL_AMPLIFIER);
            p0.set(SignalAmplifierBlock.configuredGain(level, blockPos, state));
            var input = RedstoneObservationSupport.observe(level, blockPos, DirectionalSignalBlock.seriesInputSide(state));
            liveA.set(input.value());
            liveB.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            liveC.set(SignalAmplifierBlock.clippingEpisodes(level, blockPos));
            liveD.set(SignalAmplifierBlock.maxRawOutput(level, blockPos));
        } else if (block instanceof ElectromagnetBlock) {
            kind.set(KIND_ELECTROMAGNET);
            var response = ElectromagnetBlock.configuredResponse(level, blockPos);
            p0.set(response.a()); p1.set(response.b()); p2.set(response.c());
            liveA.set(ElectromagnetBlock.input(level, blockPos).voltage());
            liveB.set(state.getValue(ElectromagnetBlock.FIELD));
            liveC.set(ElectromagnetBlock.targetField(level, blockPos));
            liveD.set(ElectromagnetBlock.thermalLoad(level, blockPos));
        } else if (block instanceof InductionCoilBlock) {
            kind.set(KIND_INDUCTION_COIL);
            p0.set(InductionCoilBlock.configuredTurns(level, blockPos, state));
            liveA.set(MagneticPhysics.fieldAt(level, blockPos, 6));
            liveB.set(InductionCoilBlock.outputVoltage(level, blockPos));
            liveC.set(InductionCoilBlock.outputQuality(level, blockPos).ordinal());
        } else if (block instanceof PneumaticReliefValveBlock) {
            kind.set(KIND_RELIEF_VALVE);
            p0.set(PneumaticReliefValveBlock.setpointPressure(level, blockPos, state));
            p1.set(PneumaticReliefValveBlock.configuredBlowdown(level, blockPos, state));
            liveA.set(PneumaticNetwork.pressure(level, blockPos));
            liveB.set(PneumaticReliefValveBlock.reseatPressure(level, blockPos, state));
            liveC.set(PneumaticReliefValveBlock.ventEvents(level, blockPos));
            liveD.set(PneumaticReliefValveBlock.lastExcess(level, blockPos));
        } else if (block instanceof LapisNoiseSourceBlock) {
            kind.set(KIND_LAPIS_NOISE);
            var parameters = LapisNoiseSourceBlock.configuredParameters(level, blockPos, state);
            p0.set(parameters.a()); p1.set(parameters.b()); p2.set(parameters.c());
            liveA.set(LapisNoiseSourceBlock.currentValue(level, blockPos, state));
            liveB.set(LapisNoiseSourceBlock.sampleInitialized(level, blockPos) ? 1 : 0);
        } else if (block instanceof LapisPrecisionRangeSensorBlock && level instanceof ServerLevel server) {
            kind.set(KIND_LAPIS_RANGE);
            p0.set(LapisPrecisionRangeSensorBlock.configuredRange(level, blockPos, state));
            var sample = LapisPrecisionRangeSensorBlock.rangeSample(server, blockPos, state);
            liveA.set(sample.distance()); liveB.set(sample.maxRange()); liveC.set(sample.complete() ? 1 : 0);
        } else if (block instanceof OpticalEmitterBlock) {
            kind.set(KIND_OPTICAL_EMITTER);
            p0.set(state.getValue(OpticalEmitterBlock.INTENSITY));
            p1.set(state.getValue(OpticalEmitterBlock.CHANNEL));
            liveA.set(state.getValue(OpticalEmitterBlock.INTENSITY));
            liveB.set(state.getValue(OpticalEmitterBlock.CHANNEL));
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
        boolean changed = false;

        if (block instanceof PrecisionFilterBlock filter && (slot == 0 || slot == 1)) {
            if (slot == 0) changed = PrecisionFilterBlock.setRiseRate(server, blockPos, p0.get() + delta);
            else {
                var entity = level.getBlockEntity(blockPos);
                if (entity instanceof dev.redstoneengineering.blockentity.PrecisionFilterBlockEntity precision) {
                    precision.setFallRate(p1.get() + delta);
                    server.scheduleTick(blockPos, filter, 1);
                    changed = true;
                }
            }
        } else if (block instanceof PulseShaperBlock) {
            if (slot == 0) changed = PulseShaperBlock.setConfiguredWidth(server, blockPos, p0.get() + delta);
            else if (slot == 1) changed = PulseShaperBlock.stepThreshold(level, blockPos, delta > 0);
            else if (slot == 2) changed = PulseShaperBlock.stepHysteresis(level, blockPos, delta > 0);
            else if (id == BUTTON_P3_TOGGLE) changed = PulseShaperBlock.toggleRetriggerable(level, blockPos);
        } else if (block instanceof EdgeDetectorBlock) {
            if (slot == 0) changed = EdgeDetectorBlock.stepMode(level, blockPos, delta > 0);
            else if (slot == 1) changed = EdgeDetectorBlock.setConfiguredPulseWidth(server, blockPos, p1.get() + delta);
        } else if (block instanceof SignalAmplifierBlock && slot == 0) {
            changed = SignalAmplifierBlock.setConfiguredGain(server, blockPos, p0.get() + delta);
        } else if (block instanceof ElectromagnetBlock && slot >= 0 && slot <= 2) {
            int rise = p0.get() + (slot == 0 ? delta : 0);
            int fall = p1.get() + (slot == 1 ? delta : 0);
            int cooling = p2.get() + (slot == 2 ? delta : 0);
            changed = ElectromagnetBlock.setEngineeringParameters(server, blockPos, rise, fall, cooling);
        } else if (block instanceof InductionCoilBlock && slot == 0) {
            changed = InductionCoilBlock.setConfiguredTurns(server, blockPos, p0.get() + delta);
        } else if (block instanceof PneumaticReliefValveBlock && (slot == 0 || slot == 1)) {
            int setpoint = p0.get() + (slot == 0 ? delta : 0);
            int blowdown = p1.get() + (slot == 1 ? delta : 0);
            changed = PneumaticReliefValveBlock.setEngineeringParameters(server, blockPos, setpoint, blowdown);
        } else if (block instanceof LapisNoiseSourceBlock && slot >= 0 && slot <= 2) {
            int baseline = p0.get() + (slot == 0 ? delta : 0);
            int noise = p1.get() + (slot == 1 ? delta : 0);
            int period = p2.get() + (slot == 2 ? delta : 0);
            changed = LapisNoiseSourceBlock.setEngineeringParameters(server, blockPos, baseline, noise, period);
        } else if (block instanceof LapisPrecisionRangeSensorBlock && slot == 0) {
            changed = LapisPrecisionRangeSensorBlock.setConfiguredRange(server, blockPos, p0.get() + delta);
        } else if (block instanceof OpticalEmitterBlock && (slot == 0 || slot == 1)) {
            int intensity = p0.get();
            int channel = p1.get();
            if (slot == 0) intensity = Math.max(0, Math.min(15, intensity + delta));
            else channel = Math.floorMod(channel + delta, 16);
            BlockState next = state.setValue(OpticalEmitterBlock.INTENSITY, intensity)
                    .setValue(OpticalEmitterBlock.CHANNEL, channel);
            if (!next.equals(state)) {
                level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
                dev.redstoneengineering.physics.DomainNetwork.recomputeOptical(server, blockPos);
                changed = true;
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
    public int p3() { return p3.get(); }
    public int liveA() { return liveA.get(); }
    public int liveB() { return liveB.get(); }
    public int liveC() { return liveC.get(); }
    public int liveD() { return liveD.get(); }
}
