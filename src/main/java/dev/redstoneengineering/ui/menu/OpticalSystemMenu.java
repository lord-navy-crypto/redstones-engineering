package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
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

/** Server-authoritative HMI projection for guided and free-space optical devices. */
public final class OpticalSystemMenu extends EngineeringDeviceMenu {
    public static final int KIND_EMITTER = 0;
    public static final int KIND_RECEIVER = 1;
    public static final int KIND_METER = 2;
    public static final int KIND_SPLITTER = 3;
    public static final int KIND_FILTER = 4;
    public static final int KIND_ATTENUATOR = 5;
    public static final int KIND_FREE_SPACE_TX = 6;
    public static final int KIND_FREE_SPACE_RX = 7;

    public static final int BUTTON_PRIMARY_PREVIOUS = 0;
    public static final int BUTTON_PRIMARY_NEXT = 1;
    public static final int BUTTON_SECONDARY_PREVIOUS = 2;
    public static final int BUTTON_SECONDARY_NEXT = 3;
    /** Legacy whole-route / measurement-face actions retained for compatibility. */
    public static final int BUTTON_ROTATE_LEFT = 4;
    public static final int BUTTON_ROTATE_RIGHT = 5;
    public static final int BUTTON_INPUT_LEFT = 6;
    public static final int BUTTON_INPUT_RIGHT = 7;
    public static final int BUTTON_OUTPUT_LEFT = 8;
    public static final int BUTTON_OUTPUT_RIGHT = 9;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();

    public OpticalSystemMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public OpticalSystemMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.OPTICAL_SYSTEM.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0);
        facing.set(-1); inputFacing.set(-1); outputFacing.set(-1);
        quality.set(PortQuality.NO_SIGNAL.ordinal());

        if (block instanceof OpticalEmitterBlock) {
            kind.set(KIND_EMITTER);
            primary.set(state.getValue(OpticalEmitterBlock.INTENSITY));
            secondary.set(state.getValue(OpticalEmitterBlock.CHANNEL));
            quality.set(PortQuality.VALID.ordinal());
        } else if (block instanceof OpticalReceiverBlock) {
            kind.set(KIND_RECEIVER);
            primary.set(OpticalReceiverBlock.intensity(level, blockPos));
            secondary.set(OpticalReceiverBlock.channel(level, blockPos));
            tertiary.set(OpticalReceiverBlock.inputCount(level, blockPos));
            auxiliary.set(OpticalReceiverBlock.driverCount(level, blockPos));
            quality.set(OpticalReceiverBlock.quality(level, blockPos).ordinal());
        } else if (block instanceof OpticalPowerMeterBlock) {
            kind.set(KIND_METER);
            OpticalPowerMeterBlock.Measurement measurement = OpticalPowerMeterBlock.measurement(level, blockPos, state);
            primary.set(measurement.intensity());
            secondary.set(measurement.channel());
            quality.set(measurement.quality().ordinal());
            facing.set(state.getValue(OpticalPowerMeterBlock.FACING).ordinal());
        } else if (block instanceof OpticalSplitterBlock splitter) {
            kind.set(KIND_SPLITTER);
            OpticalSplitterBlock.SplitEvidence evidence = splitter.evidence(level, blockPos, state);
            primary.set(evidence.inputIntensity());
            secondary.set(evidence.branchAIntensity());
            tertiary.set(evidence.branchBIntensity());
            auxiliary.set(evidence.quantizationLoss());
            quality.set(evidence.inputQuality().ordinal());
            captureDomainEndpoints(state);
        } else if (block instanceof OpticalChannelFilterBlock) {
            kind.set(KIND_FILTER);
            OpticalChannelFilterBlock.FilterEvidence evidence = OpticalChannelFilterBlock.evidence(level, blockPos, state);
            primary.set(evidence.inputIntensity());
            secondary.set(evidence.targetChannel());
            tertiary.set(evidence.expectedOutputIntensity());
            auxiliary.set(evidence.inputChannel());
            quality.set(evidence.inputQuality().ordinal());
            captureDomainEndpoints(state);
        } else if (block instanceof OpticalAttenuatorBlock) {
            kind.set(KIND_ATTENUATOR);
            OpticalAttenuatorBlock.AttenuationEvidence evidence = OpticalAttenuatorBlock.evidence(level, blockPos, state);
            primary.set(evidence.inputIntensity());
            secondary.set(evidence.loss());
            tertiary.set(evidence.expectedOutputIntensity());
            auxiliary.set(evidence.channel());
            quality.set(evidence.inputQuality().ordinal());
            captureDomainEndpoints(state);
        } else if (block instanceof FreeSpaceOpticalTransmitterBlock transmitter) {
            kind.set(KIND_FREE_SPACE_TX);
            var observation = transmitter.inputObservation(level, blockPos, state);
            primary.set(observation.value());
            secondary.set(state.getValue(FreeSpaceOpticalTransmitterBlock.CHANNEL));
            tertiary.set(observation.valid() ? 1 : 0);
            quality.set(observation.quality().ordinal());
            captureDomainEndpoints(state);
        } else if (block instanceof FreeSpaceOpticalReceiverBlock receiver) {
            kind.set(KIND_FREE_SPACE_RX);
            Direction in = DirectionalSignalBlock.seriesInputSide(state);
            Direction out = DirectionalSignalBlock.seriesOutputSide(state);
            var snapshot = receiver.engineeringSnapshot(level, blockPos, state, in);
            primary.set(snapshot.map(s -> (int) Math.round(s.value())).orElse(0));
            secondary.set(state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL));
            tertiary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            quality.set(snapshot.map(s -> s.quality()).orElse(PortQuality.NO_SIGNAL).ordinal());
            inputFacing.set(in.ordinal());
            outputFacing.set(out.ordinal());
            facing.set(out.ordinal());
        } else kind.set(-1);
    }

    private void captureDomainEndpoints(BlockState state) {
        Direction in = DirectionalDomainBlock.seriesInputSide(state);
        Direction out = DirectionalDomainBlock.seriesOutputSide(state);
        inputFacing.set(in.ordinal());
        outputFacing.set(out.ordinal());
        facing.set(out.ordinal());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof OpticalEmitterBlock) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int value = state.getValue(OpticalEmitterBlock.INTENSITY);
                value = id == BUTTON_PRIMARY_NEXT ? (value + 1) % 16 : Math.floorMod(value - 1, 16);
                state = state.setValue(OpticalEmitterBlock.INTENSITY, value);
            } else if (id == BUTTON_SECONDARY_PREVIOUS || id == BUTTON_SECONDARY_NEXT) {
                int channel = state.getValue(OpticalEmitterBlock.CHANNEL);
                channel = id == BUTTON_SECONDARY_NEXT ? (channel + 1) % 16 : Math.floorMod(channel - 1, 16);
                state = state.setValue(OpticalEmitterBlock.CHANNEL, channel);
            } else return false;
            level.setBlock(blockPos, state, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) DomainNetwork.recomputeOptical(server, blockPos);
            changed = true;
        } else if (block instanceof OpticalChannelFilterBlock filter) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int channel = state.getValue(OpticalChannelFilterBlock.TARGET);
                channel = id == BUTTON_PRIMARY_NEXT ? (channel + 1) % 16 : Math.floorMod(channel - 1, 16);
                BlockState next = state.setValue(OpticalChannelFilterBlock.TARGET, channel);
                level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) OpticalChannelFilterBlock.configurationChanged(server, blockPos, next);
                changed = true;
            } else changed = routeDomain(id);
        } else if (block instanceof OpticalAttenuatorBlock attenuator) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int loss = state.getValue(OpticalAttenuatorBlock.LOSS);
                loss = id == BUTTON_PRIMARY_NEXT ? (loss >= 8 ? 0 : loss + 1) : (loss <= 0 ? 8 : loss - 1);
                BlockState next = state.setValue(OpticalAttenuatorBlock.LOSS, loss);
                level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) OpticalAttenuatorBlock.configurationChanged(server, blockPos, next);
                changed = true;
            } else changed = routeDomain(id);
        } else if (block instanceof OpticalSplitterBlock) {
            changed = routeSplitter(id);
        } else if (block instanceof OpticalPowerMeterBlock) {
            if (id != BUTTON_ROTATE_LEFT && id != BUTTON_ROTATE_RIGHT) return false;
            Direction current = state.getValue(OpticalPowerMeterBlock.FACING);
            Direction[] all = Direction.values();
            int delta = id == BUTTON_ROTATE_RIGHT ? 1 : -1;
            Direction nextFace = all[Math.floorMod(current.ordinal() + delta, all.length)];
            level.setBlock(blockPos, state.setValue(OpticalPowerMeterBlock.FACING, nextFace), Block.UPDATE_CLIENTS);
            changed = true;
        } else if (block instanceof FreeSpaceOpticalTransmitterBlock transmitter) {
            if (id == BUTTON_SECONDARY_PREVIOUS || id == BUTTON_SECONDARY_NEXT) {
                int channel = state.getValue(FreeSpaceOpticalTransmitterBlock.CHANNEL);
                channel = id == BUTTON_SECONDARY_NEXT ? (channel + 1) % 4 : Math.floorMod(channel - 1, 4);
                level.setBlock(blockPos, state.setValue(FreeSpaceOpticalTransmitterBlock.CHANNEL, channel), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, transmitter, 1);
                changed = true;
            } else changed = routeDomain(id);
        } else if (block instanceof FreeSpaceOpticalReceiverBlock receiver) {
            if (id == BUTTON_SECONDARY_PREVIOUS || id == BUTTON_SECONDARY_NEXT) {
                int channel = state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL);
                channel = id == BUTTON_SECONDARY_NEXT ? (channel + 1) % 4 : Math.floorMod(channel - 1, 4);
                level.setBlock(blockPos, state.setValue(FreeSpaceOpticalReceiverBlock.CHANNEL, channel), Block.UPDATE_CLIENTS);
                level.scheduleTick(blockPos, receiver, 1);
                changed = true;
            } else changed = routeSignal(id);
        } else return false;

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private boolean routeDomain(int id) {
        return switch (id) {
            case BUTTON_ROTATE_LEFT -> DirectionalDomainBlock.rotateWholeRoute(level, blockPos, false);
            case BUTTON_ROTATE_RIGHT -> DirectionalDomainBlock.rotateWholeRoute(level, blockPos, true);
            case BUTTON_INPUT_LEFT -> DirectionalDomainBlock.rotateSeriesInput(level, blockPos, false);
            case BUTTON_INPUT_RIGHT -> DirectionalDomainBlock.rotateSeriesInput(level, blockPos, true);
            case BUTTON_OUTPUT_LEFT -> DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, false);
            case BUTTON_OUTPUT_RIGHT -> DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, true);
            default -> false;
        };
    }

    private boolean routeSignal(int id) {
        return switch (id) {
            case BUTTON_ROTATE_LEFT -> DirectionalSignalBlock.rotateWholeRoute(level, blockPos, false);
            case BUTTON_ROTATE_RIGHT -> DirectionalSignalBlock.rotateWholeRoute(level, blockPos, true);
            case BUTTON_INPUT_LEFT -> DirectionalSignalBlock.rotateSeriesInput(level, blockPos, false);
            case BUTTON_INPUT_RIGHT -> DirectionalSignalBlock.rotateSeriesInput(level, blockPos, true);
            case BUTTON_OUTPUT_LEFT -> DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, false);
            case BUTTON_OUTPUT_RIGHT -> DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, true);
            default -> false;
        };
    }

    /** 1x2 splitter keeps both TX branches as one rigid output layout and skips RX/TX collisions. */
    private boolean routeSplitter(int id) {
        if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            return DirectionalDomainBlock.rotateWholeRoute(level, blockPos, id == BUTTON_ROTATE_RIGHT);
        }
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof OpticalSplitterBlock splitter)) return false;
        boolean clockwise = id == BUTTON_INPUT_RIGHT || id == BUTTON_OUTPUT_RIGHT;
        if (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT) {
            Direction output = DirectionalDomainBlock.seriesOutputSide(state);
            Direction branchB = DirectionalDomainBlock.leftOf(output);
            Direction oldInput = DirectionalDomainBlock.seriesInputSide(state);
            Direction next = rotateHorizontal(oldInput, clockwise);
            for (int i = 0; i < 4 && (next == output || next == branchB); i++) next = rotateHorizontal(next, clockwise);
            if (next == oldInput || next == output || next == branchB) return false;
            level.setBlock(blockPos, state.setValue(DirectionalDomainBlock.INPUT_FACING, next), Block.UPDATE_CLIENTS);
        } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT) {
            Direction input = DirectionalDomainBlock.seriesInputSide(state);
            Direction oldOutput = DirectionalDomainBlock.seriesOutputSide(state);
            Direction next = rotateHorizontal(oldOutput, clockwise);
            for (int i = 0; i < 4 && (next == input || DirectionalDomainBlock.leftOf(next) == input); i++) {
                next = rotateHorizontal(next, clockwise);
            }
            if (next == oldOutput || next == input || DirectionalDomainBlock.leftOf(next) == input) return false;
            level.setBlock(blockPos, state.setValue(DirectionalDomainBlock.FACING, next), Block.UPDATE_CLIENTS);
        } else return false;
        level.scheduleTick(blockPos, splitter, 1);
        if (level instanceof ServerLevel server) DomainNetwork.recomputeOpticalAround(server, blockPos);
        return true;
    }

    private static Direction rotateHorizontal(Direction direction, boolean clockwise) {
        return clockwise ? direction.getClockWise() : direction.getCounterClockWise();
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int auxiliary() { return auxiliary.get(); }
    public PortQuality quality() {
        int ordinal = quality.get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }
    public Direction facing() {
        int ordinal = facing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }
    public boolean directional() {
        return kind.get() == KIND_SPLITTER || kind.get() == KIND_FILTER || kind.get() == KIND_ATTENUATOR
                || kind.get() == KIND_FREE_SPACE_TX || kind.get() == KIND_FREE_SPACE_RX;
    }
    public boolean hasInputEndpoint() { return directional() && inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return directional() && outputFacing.get() >= 0; }
}
