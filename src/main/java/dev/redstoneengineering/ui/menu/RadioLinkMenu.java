package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RadioReceiverBlock;
import dev.redstoneengineering.block.RadioTransmitterBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RadioKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative HMI model for radio transmitters and receivers. */
public final class RadioLinkMenu extends EngineeringDeviceMenu {
    public static final int KIND_TRANSMITTER = 0;
    public static final int KIND_RECEIVER = 1;

    public static final int BUTTON_CHANNEL_PREVIOUS = 0;
    public static final int BUTTON_CHANNEL_NEXT = 1;
    public static final int BUTTON_OUTPUT_LEFT = 2;
    public static final int BUTTON_OUTPUT_RIGHT = 3;

    private static final String RX_DIAG_KEY = "radio_rx_diag";
    private static final int RX_DIAG_SIZE = 10;

    private final DataSlot kind = trackedInt();
    private final DataSlot payload = trackedInt();
    private final DataSlot channel = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot drivers = trackedInt();
    private final DataSlot latency = trackedInt();
    private final DataSlot coverageComplete = trackedInt();
    private final DataSlot collision = trackedInt();
    private final DataSlot output = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot samples = trackedInt();
    private final DataSlot validSamples = trackedInt();
    private final DataSlot undecodableSamples = trackedInt();
    private final DataSlot collisions = trackedInt();
    private final DataSlot dropouts = trackedInt();
    private final DataSlot handoffs = trackedInt();
    private final DataSlot noise = trackedInt();

    public RadioLinkMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public RadioLinkMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.RADIO_LINK.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        payload.set(0);
        channel.set(0);
        quality.set(PortQuality.NO_SIGNAL.ordinal());
        drivers.set(0);
        latency.set(0);
        coverageComplete.set(1);
        collision.set(0);
        output.set(0);
        facing.set(-1);
        samples.set(0);
        validSamples.set(0);
        undecodableSamples.set(0);
        collisions.set(0);
        dropouts.set(0);
        handoffs.set(0);
        noise.set(0);

        if (block instanceof RadioTransmitterBlock) {
            kind.set(KIND_TRANSMITTER);
            RadioTransmitterBlock.PayloadObservation observation = RadioTransmitterBlock.payloadObservation(level, blockPos);
            payload.set(observation.value());
            channel.set(state.getValue(RadioTransmitterBlock.CHANNEL));
            quality.set(observation.quality().ordinal());
            drivers.set(observation.valid() ? 1 : 0);
            return;
        }

        if (block instanceof RadioReceiverBlock) {
            kind.set(KIND_RECEIVER);
            int selected = state.getValue(RadioReceiverBlock.CHANNEL);
            RadioKernel.Reception reception = RadioKernel.receivePacket(level, blockPos, selected);
            payload.set(reception.value());
            channel.set(selected);
            drivers.set(reception.drivers());
            latency.set(reception.latencyTicks());
            coverageComplete.set(reception.coverageComplete() ? 1 : 0);
            collision.set(reception.collision() ? 1 : 0);
            output.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
            quality.set(receptionQuality(reception).ordinal());

            int[] diagnostic = RuntimeIntStore.peek(level, RX_DIAG_KEY, blockPos);
            if (diagnostic != null && diagnostic.length >= RX_DIAG_SIZE) {
                samples.set(diagnostic[0]);
                validSamples.set(diagnostic[1]);
                undecodableSamples.set(diagnostic[2]);
                collisions.set(diagnostic[3]);
                dropouts.set(diagnostic[4]);
                handoffs.set(diagnostic[5]);
                noise.set(diagnostic[8]);
            } else {
                noise.set(Math.min(100, reception.interference() * 8 + reception.obstacles() * 2));
            }
            return;
        }

        kind.set(-1);
    }

    private static PortQuality receptionQuality(RadioKernel.Reception reception) {
        if (!reception.coverageComplete()) return PortQuality.STALE;
        if (reception.collision()) return PortQuality.TOPOLOGY_ERROR;
        return reception.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof RadioTransmitterBlock transmitter) {
            if (id != BUTTON_CHANNEL_PREVIOUS && id != BUTTON_CHANNEL_NEXT) return false;
            int selected = state.getValue(RadioTransmitterBlock.CHANNEL);
            selected = id == BUTTON_CHANNEL_NEXT ? (selected + 1) % 4 : Math.floorMod(selected - 1, 4);
            BlockState next = state.setValue(RadioTransmitterBlock.CHANNEL, selected);
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            RadioTransmitterBlock.PayloadObservation observation = RadioTransmitterBlock.payloadObservation(level, blockPos);
            if (observation.valid()) RadioKernel.updateTransmitter(level, blockPos, selected, observation.value());
            else RadioKernel.removeTransmitter(level, blockPos);
            changed = true;
        } else if (block instanceof RadioReceiverBlock receiver) {
            if (id == BUTTON_CHANNEL_PREVIOUS || id == BUTTON_CHANNEL_NEXT) {
                int old = state.getValue(RadioReceiverBlock.CHANNEL);
                int selected = id == BUTTON_CHANNEL_NEXT ? (old + 1) % 4 : Math.floorMod(old - 1, 4);
                RadioKernel.Reception reception = RadioKernel.receivePacket(level, blockPos, selected);
                BlockState next = state.setValue(RadioReceiverBlock.CHANNEL, selected)
                        .setValue(DirectionalSignalBlock.OUTPUT, Math.max(0, Math.min(15, reception.value())));
                level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
                level.updateNeighborsAt(blockPos, receiver);
                int[] diagnostic = RuntimeIntStore.get(level, RX_DIAG_KEY, blockPos, RX_DIAG_SIZE);
                if (selected != old) diagnostic[5]++;
                diagnostic[9] = selected;
                level.scheduleTick(blockPos, receiver, 1);
                changed = true;
            } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT) {
                changed = DirectionalSignalBlock.rotateSeriesAxis(level, blockPos, id == BUTTON_OUTPUT_RIGHT);
            } else return false;
        } else return false;

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int kind() { return kind.get(); }
    public int payload() { return payload.get(); }
    public int channel() { return channel.get(); }
    public int drivers() { return drivers.get(); }
    public int latency() { return latency.get(); }
    public boolean coverageComplete() { return coverageComplete.get() != 0; }
    public boolean collision() { return collision.get() != 0; }
    public int output() { return output.get(); }
    public int samples() { return samples.get(); }
    public int validSamples() { return validSamples.get(); }
    public int undecodableSamples() { return undecodableSamples.get(); }
    public int collisions() { return collisions.get(); }
    public int dropouts() { return dropouts.get(); }
    public int handoffs() { return handoffs.get(); }
    public int noise() { return noise.get(); }

    public PortQuality quality() {
        int ordinal = quality.get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public Direction outputDirection() {
        int ordinal = facing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }
}
