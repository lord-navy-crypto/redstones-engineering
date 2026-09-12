package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
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

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot runtimeA = trackedInt();
    private final DataSlot runtimeB = trackedInt();
    private final DataSlot runtimeC = trackedInt();
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
        inputFacing.set(-1);
        outputFacing.set(-1);
        quality.set(PortQuality.NO_SIGNAL.ordinal());

        if (block instanceof QuartzOscillatorBlock) {
            kind.set(KIND_OSCILLATOR);
            primary.set(state.getValue(QuartzOscillatorBlock.ACTIVE) ? 1 : 0);
            secondary.set(QuartzTimingLineBlock.periodTicks(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX)));
            tertiary.set(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX));
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
            tertiary.set(QuartzClockDividerBlock.division(state.getValue(QuartzClockDividerBlock.DIV_INDEX)));
            runtimeA.set(QuartzClockDividerBlock.countedEdges(level, blockPos));
            runtimeB.set(QuartzClockDividerBlock.initialized(level, blockPos) ? 1 : 0);
            quality.set(result.valid() ? PortQuality.VALID.ordinal() : PortQuality.NO_SIGNAL.ordinal());
            return;
        }

        if (block instanceof QuartzStabilityMonitorBlock monitor) {
            kind.set(KIND_STABILITY);
            Direction inputSide = DirectionalDomainBlock.seriesInputSide(state);
            Direction outputSide = DirectionalDomainBlock.seriesOutputSide(state);
            inputFacing.set(inputSide.ordinal());
            outputFacing.set(outputSide.ordinal());
            QuartzStabilityMonitorBlock.TimingMeasurement measurement = QuartzStabilityMonitorBlock.measurement(level, blockPos);
            DomainNetwork.QuartzSample upstream = DomainNetwork.sampleQuartz(level, blockPos.relative(inputSide));
            primary.set(measurement.period());
            secondary.set(measurement.nominalError());
            tertiary.set(upstream.periodTicks());
            runtimeA.set(measurement.initialized() ? 1 : 0);
            runtimeB.set(measurement.referenceEdgeSeen() ? 1 : 0);
            runtimeC.set(measurement.currentMeasurement() ? 1 : 0);
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

        if (block instanceof QuartzOscillatorBlock oscillator) {
            if (id != BUTTON_PARAMETER_PREVIOUS && id != BUTTON_PARAMETER_NEXT) return false;
            int index = state.getValue(QuartzOscillatorBlock.PERIOD_INDEX);
            index = id == BUTTON_PARAMETER_NEXT ? (index + 1) % 5 : Math.floorMod(index - 1, 5);
            level.setBlock(blockPos, state.setValue(QuartzOscillatorBlock.PERIOD_INDEX, index), Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) DomainNetwork.recomputeQuartzAround(server, blockPos);
            level.scheduleTick(blockPos, oscillator, 1);
            changed = true;
        } else if (block instanceof QuartzClockDividerBlock || block instanceof QuartzStabilityMonitorBlock) {
            if (block instanceof QuartzStabilityMonitorBlock monitor && id == BUTTON_RESET_MEASUREMENT) {
                RuntimeIntStore.remove(level, "quartz_stability", blockPos);
                level.scheduleTick(blockPos, monitor, 1);
                changed = true;
            } else if (block instanceof QuartzClockDividerBlock && (id == BUTTON_PARAMETER_NEXT || id == BUTTON_PARAMETER_PREVIOUS)) {
                if (!(level instanceof ServerLevel server)) return false;
                if (id == BUTTON_PARAMETER_NEXT) QuartzClockDividerBlock.cycleDivision(server, blockPos);
                else for (int i = 0; i < 3; i++) QuartzClockDividerBlock.cycleDivision(server, blockPos);
                changed = true;
            } else if (id == BUTTON_INPUT_LEFT || id == BUTTON_INPUT_RIGHT) {
                changed = DirectionalDomainBlock.rotateSeriesInput(level, blockPos, id == BUTTON_INPUT_RIGHT);
            } else if (id == BUTTON_OUTPUT_LEFT || id == BUTTON_OUTPUT_RIGHT) {
                changed = DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, id == BUTTON_OUTPUT_RIGHT);
            } else if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
                changed = DirectionalDomainBlock.rotateWholeRoute(level, blockPos, id == BUTTON_ROTATE_RIGHT);
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
    public int facingOrdinal() { return outputFacing.get(); }
    public boolean hasInputEndpoint() { return inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return outputFacing.get() >= 0; }

    public PortQuality quality() {
        int ordinal = quality.get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public Direction logicalFacing() {
        int ordinal = outputFacing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }
}
