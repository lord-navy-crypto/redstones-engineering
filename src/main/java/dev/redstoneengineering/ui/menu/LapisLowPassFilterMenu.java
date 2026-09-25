package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative engineering notebook model for the Lapis low-pass filter. */
public final class LapisLowPassFilterMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_ALPHA_MINUS_5 = 0;
    public static final int BUTTON_ALPHA_MINUS_1 = 1;
    public static final int BUTTON_ALPHA_PLUS_1 = 2;
    public static final int BUTTON_ALPHA_PLUS_5 = 3;
    public static final int BUTTON_ALPHA_RESET = 4;
    public static final int BUTTON_INPUT_PREVIOUS = 20;
    public static final int BUTTON_INPUT_NEXT = 21;
    public static final int BUTTON_OUTPUT_PREVIOUS = 22;
    public static final int BUTTON_OUTPUT_NEXT = 23;
    public static final int BUTTON_ROTATE_LEFT = 24;
    public static final int BUTTON_ROTATE_RIGHT = 25;

    private final DataSlot alphaPercent = trackedInt();
    private final DataSlot input = trackedInt();
    private final DataSlot output = trackedInt();
    private final DataSlot valid = trackedInt();
    private final DataSlot history = trackedInt();
    private final DataSlot inputQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();

    public LapisLowPassFilterMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public LapisLowPassFilterMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.LAPIS_LOW_PASS_FILTER.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock)) {
            alphaPercent.set(25);
            input.set(0);
            output.set(0);
            valid.set(0);
            history.set(0);
            inputQuality.set(PortQuality.NO_SIGNAL.ordinal());
            outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
            inputFacing.set(-1);
            outputFacing.set(-1);
            return;
        }

        alphaPercent.set(LapisLowPassFilterBlock.alphaPercent(level, blockPos, state));
        LapisLowPassFilterBlock.FilterState filter = LapisLowPassFilterBlock.filterState(level, blockPos);
        output.set(filter.output());
        valid.set(filter.valid() ? 1 : 0);
        history.set(LapisLowPassFilterBlock.retainedHistory(level, blockPos) ? 1 : 0);
        outputQuality.set(filter.quality().ordinal());

        int inputValue = 0;
        PortQuality observedInputQuality = PortQuality.NO_SIGNAL;
        if (state.getBlock() instanceof EngineeringPortProvider provider) {
            Direction in = DirectionalDomainBlock.seriesInputSide(state);
            var snapshot = provider.engineeringSnapshot(level, blockPos, state, in);
            inputValue = snapshot.map(port -> (int) Math.round(port.value())).orElse(0);
            observedInputQuality = snapshot.map(port -> port.quality()).orElse(PortQuality.NO_SIGNAL);
        }
        input.set(Math.max(0, Math.min(100, inputValue)));
        inputQuality.set(observedInputQuality.ordinal());
        inputFacing.set(DirectionalDomainBlock.seriesInputSide(state).ordinal());
        outputFacing.set(DirectionalDomainBlock.seriesOutputSide(state).ordinal());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player) || !(level instanceof ServerLevel serverLevel)) return false;

        boolean changed;
        if (id == BUTTON_ROTATE_LEFT || id == BUTTON_INPUT_PREVIOUS || id == BUTTON_OUTPUT_PREVIOUS) {
            // Straight-through filter: RX is permanently opposite TX. Legacy endpoint IDs rotate
            // the full axis too, preventing old clients from creating a bent filter topology.
            changed = DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, false);
        } else if (id == BUTTON_ROTATE_RIGHT || id == BUTTON_INPUT_NEXT || id == BUTTON_OUTPUT_NEXT) {
            changed = DirectionalDomainBlock.rotateRigidSeriesAxis(level, blockPos, true);
        } else {
            changed = switch (id) {
                case BUTTON_ALPHA_MINUS_5 -> LapisLowPassFilterBlock.adjustAlpha(serverLevel, blockPos, -5);
                case BUTTON_ALPHA_MINUS_1 -> LapisLowPassFilterBlock.adjustAlpha(serverLevel, blockPos, -1);
                case BUTTON_ALPHA_PLUS_1 -> LapisLowPassFilterBlock.adjustAlpha(serverLevel, blockPos, 1);
                case BUTTON_ALPHA_PLUS_5 -> LapisLowPassFilterBlock.adjustAlpha(serverLevel, blockPos, 5);
                case BUTTON_ALPHA_RESET -> LapisLowPassFilterBlock.resetAlpha(serverLevel, blockPos);
                default -> false;
            };
        }
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int alphaPercent() { return alphaPercent.get(); }
    public double alpha() { return alphaPercent() / 100.0; }
    public int input() { return input.get(); }
    public int output() { return output.get(); }
    public boolean valid() { return valid.get() != 0; }
    public boolean historyPresent() { return history.get() != 0; }
    public PortQuality inputQuality() { return decodeQuality(inputQuality.get()); }
    public PortQuality outputQuality() { return decodeQuality(outputQuality.get()); }
    public boolean hasInputEndpoint() { return inputFacing.get() >= 0; }
    public boolean hasOutputEndpoint() { return outputFacing.get() >= 0; }
    public Direction inputDirection() { return decodeDirection(inputFacing.get(), Direction.SOUTH); }
    public Direction outputDirection() { return decodeDirection(outputFacing.get(), Direction.NORTH); }

    private static PortQuality decodeQuality(int ordinal) {
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    private static Direction decodeDirection(int ordinal, Direction fallback) {
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? fallback : all[ordinal];
    }

    /** Discrete-time e-folding constant in filter samples. */
    public double tauSamples() {
        double a = alpha();
        return a >= 1.0 ? 0.0 : -1.0 / Math.log(1.0 - a);
    }

    public int sampleTicks() {
        return LapisLowPassFilterBlock.FILTER_SAMPLE_TICKS;
    }

    public double samplePeriodSeconds() {
        return sampleTicks() / 20.0;
    }

    /** Discrete pole governing error decay once a trustworthy filter history exists. */
    public double pole() {
        return 1.0 - alpha();
    }

    /** Filter e-folding constant converted from samples into authoritative game ticks. */
    public double tauTicks() {
        return tauSamples() * sampleTicks();
    }

    /**
     * Samples required for an already-initialized step error to decay to 10% or less:
     * (1-alpha)^n <= 0.1.
     */
    public int step90Samples() {
        double p = pole();
        if (p <= 0.0) return 1;
        return Math.max(1, (int) Math.ceil(Math.log(0.10) / Math.log(p)));
    }

    public int step90Ticks() {
        return step90Samples() * sampleTicks();
    }

    /** Equivalent continuous-time cutoff derived from synchronized alpha and sample period. */
    public double equivalentCutoffHz() {
        double a = alpha();
        return a >= 1.0 ? Double.POSITIVE_INFINITY
                : -Math.log(1.0 - a) / (2.0 * Math.PI * samplePeriodSeconds());
    }

    public int trackingError() {
        return input() - output();
    }
}
