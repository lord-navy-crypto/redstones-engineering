package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.LapisLowPassFilterBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.EngineeringParameterProfile;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-authoritative HMI model for the Lapis first-order low-pass filter.
 *
 * <p>The menu transports only bounded configuration and observer-neutral evidence.
 * All physics remains in {@link LapisLowPassFilterBlock}.</p>
 */
public final class LapisLowPassMenu extends EngineeringDeviceMenu {
    public static final int BUTTON_ALPHA_PREVIOUS = 0;
    public static final int BUTTON_ALPHA_NEXT = 1;
    public static final int BUTTON_ALPHA_DEFAULT = 2;
    public static final int BUTTON_INPUT_LEFT = 3;
    public static final int BUTTON_INPUT_RIGHT = 4;
    public static final int BUTTON_OUTPUT_LEFT = 5;
    public static final int BUTTON_OUTPUT_RIGHT = 6;
    /** Exact supported α profile encoded as BASE + alpha index. */
    public static final int BUTTON_ALPHA_DIRECT_BASE = 4000;
    public static final int BUTTON_ALPHA_DIRECT_MAX = 4007;

    private final DataSlot alphaIndex = trackedInt();
    private final DataSlot inputValue = trackedInt();
    private final DataSlot outputValue = trackedInt();
    private final DataSlot previousOutput = trackedInt();
    private final DataSlot inputQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
    private final DataSlot predictedOutput = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();
    private final DataSlot runtimePresent = trackedInt();

    public LapisLowPassMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public LapisLowPassMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.LAPIS_LOW_PASS.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        if (!(state.getBlock() instanceof LapisLowPassFilterBlock filter)) {
            alphaIndex.set(0);
            inputValue.set(0);
            outputValue.set(0);
            previousOutput.set(0);
            inputQuality.set(PortQuality.NO_SIGNAL.ordinal());
            outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
            predictedOutput.set(0);
            inputFacing.set(Direction.SOUTH.ordinal());
            outputFacing.set(Direction.NORTH.ordinal());
            runtimePresent.set(0);
            return;
        }

        Direction input = DirectionalDomainBlock.seriesInputSide(state);
        Direction output = DirectionalDomainBlock.seriesOutputSide(state);
        alphaIndex.set(state.getValue(LapisLowPassFilterBlock.ALPHA));
        inputFacing.set(input.ordinal());
        outputFacing.set(output.ordinal());

        filter.engineeringSnapshot(level, blockPos, state, input).ifPresentOrElse(snapshot -> {
            inputValue.set((int) Math.round(snapshot.value()));
            inputQuality.set(snapshot.quality().ordinal());
        }, () -> {
            inputValue.set(0);
            inputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        });

        filter.engineeringSnapshot(level, blockPos, state, output).ifPresentOrElse(snapshot -> {
            outputValue.set((int) Math.round(snapshot.value()));
            outputQuality.set(snapshot.quality().ordinal());
        }, () -> {
            outputValue.set(0);
            outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        });

        int previous = LapisLowPassFilterBlock.previousOutput(level, blockPos);
        previousOutput.set(previous);
        runtimePresent.set(LapisLowPassFilterBlock.runtimePresent(level, blockPos) ? 1 : 0);
        double alpha = EngineeringParameterProfile.lapisFilterAlpha(state.getValue(LapisLowPassFilterBlock.ALPHA));
        predictedOutput.set(Math.max(0, Math.min(100,
                (int) Math.round(previous + alpha * (inputValue.get() - previous)))));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed;
        if (id >= BUTTON_ALPHA_DIRECT_BASE && id <= BUTTON_ALPHA_DIRECT_MAX) {
            changed = LapisLowPassFilterBlock.setAlphaIndex(level, blockPos, id - BUTTON_ALPHA_DIRECT_BASE);
        } else changed = switch (id) {
            case BUTTON_ALPHA_PREVIOUS -> LapisLowPassFilterBlock.adjustAlpha(level, blockPos, -1);
            case BUTTON_ALPHA_NEXT -> LapisLowPassFilterBlock.adjustAlpha(level, blockPos, 1);
            case BUTTON_ALPHA_DEFAULT -> LapisLowPassFilterBlock.resetAlpha(level, blockPos);
            case BUTTON_INPUT_LEFT -> DirectionalDomainBlock.rotateSeriesInput(level, blockPos, false);
            case BUTTON_INPUT_RIGHT -> DirectionalDomainBlock.rotateSeriesInput(level, blockPos, true);
            case BUTTON_OUTPUT_LEFT -> DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, false);
            case BUTTON_OUTPUT_RIGHT -> DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, true);
            default -> false;
        };
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int alphaIndex() { return alphaIndex.get(); }
    public int inputValue() { return inputValue.get(); }
    public int outputValue() { return outputValue.get(); }
    public int previousOutput() { return previousOutput.get(); }
    public int predictedOutput() { return predictedOutput.get(); }
    public boolean runtimePresent() { return runtimePresent.get() != 0; }


    public static int alphaSteps() { return EngineeringParameterProfile.LAPIS_FILTER_ALPHA_STEPS; }
    public static int defaultAlphaIndex() { return EngineeringParameterProfile.LAPIS_FILTER_DEFAULT_INDEX; }
    public static int samplePeriodTicks() { return EngineeringParameterProfile.LAPIS_FILTER_SAMPLE_PERIOD_TICKS; }
    public static double nominalTicksPerSecond() { return EngineeringParameterProfile.NOMINAL_TICKS_PER_SECOND; }
    public static String profileId() { return EngineeringParameterProfile.PROFILE_ID; }
    public static double alphaForIndex(int index) { return EngineeringParameterProfile.lapisFilterAlpha(index); }
    public static boolean bypassForIndex(int index) { return EngineeringParameterProfile.lapisFilterBypass(index); }
    public static double timeConstantTicksForIndex(int index) { return EngineeringParameterProfile.lapisFilterTimeConstantTicks(index); }
    public static double cutoffHzForIndex(int index) { return EngineeringParameterProfile.lapisFilterCutoffHzNominal(index); }

    public PortQuality inputQuality() {
        return quality(inputQuality.get());
    }

    public PortQuality outputQuality() {
        return quality(outputQuality.get());
    }

    public Direction inputDirection() {
        return direction(inputFacing.get(), Direction.SOUTH);
    }

    public Direction outputDirection() {
        return direction(outputFacing.get(), Direction.NORTH);
    }

    private static PortQuality quality(int ordinal) {
        PortQuality[] values = PortQuality.values();
        return ordinal < 0 || ordinal >= values.length ? PortQuality.NO_SIGNAL : values[ordinal];
    }

    private static Direction direction(int ordinal, Direction fallback) {
        Direction[] values = Direction.values();
        return ordinal < 0 || ordinal >= values.length ? fallback : values[ordinal];
    }
}
