package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
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

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot stateFlag = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot inputQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();

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
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0); stateFlag.set(0); facing.set(-1);
        inputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        outputQuality.set(PortQuality.NO_SIGNAL.ordinal());

        if (block instanceof AirCompressorBlock) {
            kind.set(KIND_COMPRESSOR);
            primary.set(AirCompressorBlock.commandSignal(level, blockPos));
            secondary.set(AirCompressorBlock.commandedPressure(level, blockPos));
            tertiary.set(PneumaticNetwork.pressure(level, blockPos));
            setNodeQuality();
        } else if (block instanceof PneumaticPipeBlock) {
            kind.set(KIND_PIPE);
            primary.set(PneumaticNetwork.pressure(level, blockPos));
            setNodeQuality();
        } else if (block instanceof AirReservoirBlock) {
            kind.set(KIND_RESERVOIR);
            primary.set(AirReservoirBlock.storedPressure(level, blockPos));
            secondary.set(PneumaticNetwork.pressure(level, blockPos));
            setNodeQuality();
        } else if (block instanceof PressureRegulatorBlock) {
            kind.set(KIND_REGULATOR);
            primary.set(PneumaticNetwork.pressure(level, blockPos));
            secondary.set(PressureRegulatorBlock.setpointPressure(state));
            tertiary.set(state.getValue(PressureRegulatorBlock.SETPOINT));
            setNodeQuality();
        } else if (block instanceof PneumaticReceiverBlock receiver) {
            kind.set(KIND_RECEIVER);
            directionalSnapshots(state, receiver);
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
        } else if (block instanceof PneumaticValveBlock valve) {
            kind.set(KIND_VALVE);
            directionalSnapshots(state, valve);
            stateFlag.set(state.getValue(PneumaticValveBlock.OPEN) ? 1 : 0);
        } else if (block instanceof PneumaticCheckValveBlock valve) {
            kind.set(KIND_CHECK_VALVE);
            directionalSnapshots(state, valve);
        } else if (block instanceof PneumaticFlowMeterBlock meter) {
            kind.set(KIND_FLOW_METER);
            directionalSnapshots(state, meter);
            primary.set(PneumaticFlowMeterBlock.flowProxy(level, blockPos));
            secondary.set(PneumaticFlowMeterBlock.pressureDrop(level, blockPos));
            tertiary.set(PneumaticFlowMeterBlock.inletPressure(level, blockPos));
            auxiliary.set(PneumaticFlowMeterBlock.outletPressure(level, blockPos));
            stateFlag.set((int) Math.min(Integer.MAX_VALUE, PneumaticFlowMeterBlock.measurement(level, blockPos).sampleCount()));
        } else if (block instanceof PneumaticProportionalValveBlock valve) {
            kind.set(KIND_PROPORTIONAL);
            directionalSnapshots(state, valve);
            tertiary.set(PneumaticProportionalValveBlock.opening(level, blockPos));
            auxiliary.set(PneumaticNetwork.pressure(level, blockPos));
        } else if (block instanceof PneumaticReliefValveBlock valve) {
            kind.set(KIND_RELIEF);
            directionalSnapshots(state, valve);
            tertiary.set(state.getValue(PneumaticReliefValveBlock.SETPOINT) * 25);
            auxiliary.set(PneumaticReliefValveBlock.ventEvents(level, blockPos));
            stateFlag.set(PneumaticReliefValveBlock.venting(level, blockPos) ? 1 : 0);
        } else if (block instanceof PneumaticCylinderBlock cylinder) {
            kind.set(KIND_CYLINDER);
            directionalSnapshots(state, cylinder);
            primary.set(PneumaticCylinderBlock.pressure(level, blockPos));
            secondary.set(PneumaticCylinderBlock.position(level, blockPos));
            tertiary.set(PneumaticCylinderBlock.target(level, blockPos));
            auxiliary.set(PneumaticCylinderBlock.travel(level, blockPos));
        } else {
            kind.set(-1);
        }
    }

    private void setNodeQuality() {
        PneumaticObservationSupport.Observation observation = PneumaticObservationSupport.observe(level, blockPos);
        inputQuality.set(observation.quality().ordinal());
        outputQuality.set(observation.quality().ordinal());
    }

    private void directionalSnapshots(BlockState state, EngineeringPortProvider provider) {
        Direction out = state.hasProperty(DirectionalDomainBlock.FACING)
                ? state.getValue(DirectionalDomainBlock.FACING)
                : state.getValue(DirectionalSignalBlock.FACING);
        Direction in = out.getOpposite();
        facing.set(out.ordinal());
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

        if (block instanceof PressureRegulatorBlock) {
            if (id != BUTTON_PARAMETER_PREVIOUS && id != BUTTON_PARAMETER_NEXT) return false;
            int value = state.getValue(PressureRegulatorBlock.SETPOINT);
            value = id == BUTTON_PARAMETER_NEXT ? value % 4 + 1 : value <= 1 ? 4 : value - 1;
            level.setBlock(blockPos, state.setValue(PressureRegulatorBlock.SETPOINT, value), Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) PneumaticNetwork.recompute(server, blockPos);
            changed = true;
        } else if (block instanceof PneumaticValveBlock) {
            if (id == BUTTON_TOGGLE) {
                level.setBlock(blockPos, state.setValue(PneumaticValveBlock.OPEN, !state.getValue(PneumaticValveBlock.OPEN)), Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, blockPos);
                changed = true;
            } else changed = rotateDirectional(block, id);
        } else if (block instanceof PneumaticReliefValveBlock) {
            if (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT) {
                int value = state.getValue(PneumaticReliefValveBlock.SETPOINT);
                value = id == BUTTON_PARAMETER_NEXT ? value % 4 + 1 : value <= 1 ? 4 : value - 1;
                level.setBlock(blockPos, state.setValue(PneumaticReliefValveBlock.SETPOINT, value), Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) PneumaticNetwork.recomputeAround(server, blockPos);
                changed = true;
            } else changed = rotateDirectional(block, id);
        } else if (block instanceof PneumaticReceiverBlock
                || block instanceof PneumaticCheckValveBlock
                || block instanceof PneumaticFlowMeterBlock
                || block instanceof PneumaticProportionalValveBlock
                || block instanceof PneumaticCylinderBlock) {
            changed = rotateDirectional(block, id);
        } else return false;

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private boolean rotateDirectional(Block block, int id) {
        if (id != BUTTON_ROTATE_LEFT && id != BUTTON_ROTATE_RIGHT) return false;
        boolean clockwise = id == BUTTON_ROTATE_RIGHT;
        boolean changed;
        if (block instanceof DirectionalDomainBlock) changed = DirectionalDomainBlock.rotateSeriesAxis(level, blockPos, clockwise);
        else if (block instanceof DirectionalSignalBlock) changed = DirectionalSignalBlock.rotateSeriesAxis(level, blockPos, clockwise);
        else return false;
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

    private static PortQuality quality(int ordinal) {
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public Direction outputDirection() {
        int ordinal = facing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }
    public Direction inputDirection() { return outputDirection().getOpposite(); }
    public boolean directional() { return facing.get() >= 0; }
}
