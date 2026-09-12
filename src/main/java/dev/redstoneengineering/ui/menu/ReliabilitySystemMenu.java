package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative HMI model for reliability, voting, latching and servo feedback devices. */
public final class ReliabilitySystemMenu extends EngineeringDeviceMenu {
    public static final int KIND_WATCHDOG = 0;
    public static final int KIND_SERVO = 1;
    public static final int KIND_POSITION_SENSOR = 2;
    public static final int KIND_VOTER = 3;
    public static final int KIND_FAULT_LATCH = 4;

    public static final int BUTTON_PARAMETER_PREVIOUS = 0;
    public static final int BUTTON_PARAMETER_NEXT = 1;
    public static final int BUTTON_ROTATE_LEFT = 2;
    public static final int BUTTON_ROTATE_RIGHT = 3;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot extraA = trackedInt();
    private final DataSlot extraB = trackedInt();
    private final DataSlot extraC = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot quality = trackedInt();

    public ReliabilitySystemMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public ReliabilitySystemMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.RELIABILITY_SYSTEM.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0);
        extraA.set(0); extraB.set(0); extraC.set(0); facing.set(-1);
        quality.set(PortQuality.NO_SIGNAL.ordinal());

        if (block instanceof WatchdogBlock watchdog) {
            kind.set(KIND_WATCHDOG);
            Direction out = DirectionalSignalBlock.seriesOutputSide(state);
            Direction in = DirectionalSignalBlock.seriesInputSide(state);
            facing.set(out.ordinal());
            primary.set(WatchdogBlock.ageTicks(level, blockPos));
            secondary.set(WatchdogBlock.timeoutTicks(state.getValue(WatchdogBlock.TIMEOUT)));
            tertiary.set(WatchdogBlock.timeoutCount(level, blockPos));
            auxiliary.set(WatchdogBlock.transitionCount(level, blockPos));
            extraA.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            quality.set(snapshotQuality(watchdog, state, in).ordinal());
        } else if (block instanceof ServoActuatorBlock servo) {
            kind.set(KIND_SERVO);
            Direction front = state.getValue(ServoActuatorBlock.FACING);
            facing.set(front.ordinal());
            primary.set(ServoActuatorBlock.position(level, blockPos));
            secondary.set(ServoActuatorBlock.command(level, blockPos));
            tertiary.set(ServoActuatorBlock.velocity(level, blockPos));
            auxiliary.set(ServoActuatorBlock.error(level, blockPos));
            extraA.set(ServoActuatorBlock.braking(level, blockPos) ? 1 : 0);
            extraB.set(ServoActuatorBlock.softLimitHits(level, blockPos));
            extraC.set(ServoActuatorBlock.slewStep(state.getValue(ServoActuatorBlock.SLEW)));
            quality.set(snapshotQuality(servo, state, front.getOpposite()).ordinal());
        } else if (block instanceof ServoPositionSensorBlock sensor) {
            kind.set(KIND_POSITION_SENSOR);
            Direction out = DirectionalSignalBlock.seriesOutputSide(state);
            Direction in = DirectionalSignalBlock.seriesInputSide(state);
            facing.set(out.ordinal());
            primary.set(snapshotValue(sensor, state, in));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set((int) Math.min(Integer.MAX_VALUE, ServoPositionSensorBlock.measurement(level, blockPos).sampleCount()));
            PortQuality source = ServoPositionSensorBlock.sourceQuality(level, blockPos, state);
            quality.set(source.ordinal());
        } else if (block instanceof RedundantVoterBlock voter) {
            kind.set(KIND_VOTER);
            Direction out = DirectionalSignalBlock.seriesOutputSide(state);
            facing.set(out.ordinal());
            RedundantVoterBlock.Vote vote = voter.vote(level, blockPos, state);
            primary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            secondary.set(vote.validInputs());
            tertiary.set(vote.spread());
            auxiliary.set(RedundantVoterBlock.toleranceValue(state.getValue(RedundantVoterBlock.TOLERANCE)));
            extraA.set(RedundantVoterBlock.maxSpread(level, blockPos));
            extraB.set(RedundantVoterBlock.disagreementCount(level, blockPos));
            extraC.set(RedundantVoterBlock.degraded(level, blockPos) ? 1 : 0);
            quality.set(vote.quality().ordinal());
        } else if (block instanceof FaultLatchBlock latch) {
            kind.set(KIND_FAULT_LATCH);
            Direction out = DirectionalSignalBlock.seriesOutputSide(state);
            facing.set(out.ordinal());
            primary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            secondary.set(FaultLatchBlock.thresholdValue(state.getValue(FaultLatchBlock.THRESHOLD)));
            tertiary.set(FaultLatchBlock.tripCount(level, blockPos));
            auxiliary.set(FaultLatchBlock.resetCount(level, blockPos));
            extraA.set(FaultLatchBlock.latched(level, blockPos) ? 1 : 0);
            extraB.set(FaultLatchBlock.resetActive(level, blockPos) ? 1 : 0);
            quality.set(snapshotQuality(latch, state, out).ordinal());
        } else kind.set(-1);
    }

    private int snapshotValue(EngineeringPortProvider provider, BlockState state, Direction side) {
        return provider.engineeringSnapshot(level, blockPos, state, side)
                .map(snapshot -> (int) Math.round(snapshot.value())).orElse(0);
    }

    private PortQuality snapshotQuality(EngineeringPortProvider provider, BlockState state, Direction side) {
        return provider.engineeringSnapshot(level, blockPos, state, side)
                .map(snapshot -> snapshot.quality()).orElse(PortQuality.NO_SIGNAL);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (SeriesRouteActions.isEndpointAction(id)) return SeriesRouteActions.handle(this, player, id);
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            boolean clockwise = id == BUTTON_ROTATE_RIGHT;
            if (block instanceof DirectionalSignalBlock) {
                changed = DirectionalSignalBlock.rotateWholeRoute(level, blockPos, clockwise);
            } else if (block instanceof ServoActuatorBlock servo) {
                Direction current = state.getValue(ServoActuatorBlock.FACING);
                Direction next = clockwise ? current.getClockWise() : current.getCounterClockWise();
                level.setBlock(blockPos, state.setValue(ServoActuatorBlock.FACING, next), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, servo, 1);
                changed = true;
            } else return false;
        } else if (block instanceof WatchdogBlock watchdog) {
            int index = state.getValue(WatchdogBlock.TIMEOUT);
            if (id == BUTTON_PARAMETER_NEXT) index = (index + 1) % 4;
            else if (id == BUTTON_PARAMETER_PREVIOUS) index = Math.floorMod(index - 1, 4);
            else return false;
            level.setBlock(blockPos, state.setValue(WatchdogBlock.TIMEOUT, index), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, watchdog, 1);
            changed = true;
        } else if (block instanceof ServoActuatorBlock servo) {
            int index = state.getValue(ServoActuatorBlock.SLEW);
            if (id == BUTTON_PARAMETER_NEXT) index = (index + 1) % 3;
            else if (id == BUTTON_PARAMETER_PREVIOUS) index = Math.floorMod(index - 1, 3);
            else return false;
            level.setBlock(blockPos, state.setValue(ServoActuatorBlock.SLEW, index), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, servo, 1);
            changed = true;
        } else if (block instanceof RedundantVoterBlock voter) {
            int index = state.getValue(RedundantVoterBlock.TOLERANCE);
            if (id == BUTTON_PARAMETER_NEXT) index = (index + 1) % 4;
            else if (id == BUTTON_PARAMETER_PREVIOUS) index = Math.floorMod(index - 1, 4);
            else return false;
            level.setBlock(blockPos, state.setValue(RedundantVoterBlock.TOLERANCE, index), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, voter, 1);
            changed = true;
        } else if (block instanceof FaultLatchBlock latch) {
            int index = state.getValue(FaultLatchBlock.THRESHOLD);
            if (id == BUTTON_PARAMETER_NEXT) index = (index + 1) % 4;
            else if (id == BUTTON_PARAMETER_PREVIOUS) index = Math.floorMod(index - 1, 4);
            else return false;
            level.setBlock(blockPos, state.setValue(FaultLatchBlock.THRESHOLD, index), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, latch, 1);
            changed = true;
        } else return false;

        if (changed) broadcastChanges();
        return changed;
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int auxiliary() { return auxiliary.get(); }
    public int extraA() { return extraA.get(); }
    public int extraB() { return extraB.get(); }
    public int extraC() { return extraC.get(); }
    public PortQuality quality() {
        int ordinal = quality.get(); PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }
    public Direction facing() {
        int ordinal = facing.get(); Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }
}
