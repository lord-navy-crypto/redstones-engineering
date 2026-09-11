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

/** Server-authoritative HMI projection for guided optical sources, sinks, processors, splitters and meters. */
public final class OpticalSystemMenu extends EngineeringDeviceMenu {
    public static final int KIND_EMITTER = 0;
    public static final int KIND_RECEIVER = 1;
    public static final int KIND_METER = 2;
    public static final int KIND_SPLITTER = 3;
    public static final int KIND_FILTER = 4;
    public static final int KIND_ATTENUATOR = 5;

    public static final int BUTTON_PRIMARY_PREVIOUS = 0;
    public static final int BUTTON_PRIMARY_NEXT = 1;
    public static final int BUTTON_SECONDARY_PREVIOUS = 2;
    public static final int BUTTON_SECONDARY_NEXT = 3;
    public static final int BUTTON_ROTATE_LEFT = 4;
    public static final int BUTTON_ROTATE_RIGHT = 5;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot facing = trackedInt();

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
        primary.set(0); secondary.set(0); tertiary.set(0); auxiliary.set(0); facing.set(-1);
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
            facing.set(state.getValue(DirectionalDomainBlock.FACING).ordinal());
        } else if (block instanceof OpticalChannelFilterBlock) {
            kind.set(KIND_FILTER);
            OpticalChannelFilterBlock.FilterEvidence evidence = OpticalChannelFilterBlock.evidence(level, blockPos, state);
            primary.set(evidence.inputIntensity());
            secondary.set(evidence.targetChannel());
            tertiary.set(evidence.expectedOutputIntensity());
            auxiliary.set(evidence.inputChannel());
            quality.set(evidence.inputQuality().ordinal());
            facing.set(state.getValue(DirectionalDomainBlock.FACING).ordinal());
        } else if (block instanceof OpticalAttenuatorBlock) {
            kind.set(KIND_ATTENUATOR);
            OpticalAttenuatorBlock.AttenuationEvidence evidence = OpticalAttenuatorBlock.evidence(level, blockPos, state);
            primary.set(evidence.inputIntensity());
            secondary.set(evidence.loss());
            tertiary.set(evidence.expectedOutputIntensity());
            auxiliary.set(evidence.channel());
            quality.set(evidence.inputQuality().ordinal());
            facing.set(state.getValue(DirectionalDomainBlock.FACING).ordinal());
        } else kind.set(-1);
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
            } else changed = rotate(id);
        } else if (block instanceof OpticalAttenuatorBlock attenuator) {
            if (id == BUTTON_PRIMARY_PREVIOUS || id == BUTTON_PRIMARY_NEXT) {
                int loss = state.getValue(OpticalAttenuatorBlock.LOSS);
                loss = id == BUTTON_PRIMARY_NEXT ? (loss >= 8 ? 0 : loss + 1) : (loss <= 0 ? 8 : loss - 1);
                BlockState next = state.setValue(OpticalAttenuatorBlock.LOSS, loss);
                level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel server) OpticalAttenuatorBlock.configurationChanged(server, blockPos, next);
                changed = true;
            } else changed = rotate(id);
        } else if (block instanceof OpticalSplitterBlock) {
            changed = rotate(id);
        } else if (block instanceof OpticalPowerMeterBlock) {
            if (id != BUTTON_ROTATE_LEFT && id != BUTTON_ROTATE_RIGHT) return false;
            Direction current = state.getValue(OpticalPowerMeterBlock.FACING);
            Direction[] all = Direction.values();
            int delta = id == BUTTON_ROTATE_RIGHT ? 1 : -1;
            Direction nextFace = all[Math.floorMod(current.ordinal() + delta, all.length)];
            level.setBlock(blockPos, state.setValue(OpticalPowerMeterBlock.FACING, nextFace), Block.UPDATE_CLIENTS);
            changed = true;
        } else return false;

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private boolean rotate(int id) {
        if (id != BUTTON_ROTATE_LEFT && id != BUTTON_ROTATE_RIGHT) return false;
        return DirectionalDomainBlock.rotateSeriesAxis(level, blockPos, id == BUTTON_ROTATE_RIGHT);
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
    public boolean directional() { return kind.get() == KIND_SPLITTER || kind.get() == KIND_FILTER || kind.get() == KIND_ATTENUATOR; }
}
