package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.LapisToRedstoneQuantizerBlock;
import dev.redstoneengineering.block.RedstoneToLapisScalerBlock;
import dev.redstoneengineering.core.diagnostic.CommissioningStatus;
import dev.redstoneengineering.core.diagnostic.CoreMediaDiagnostics;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative read-only HMI for the explicit Redstone ↔ Lapis representation boundary. */
public final class MediaConversionMenu extends EngineeringDeviceMenu {
    public static final int MODE_UNKNOWN = 0;
    public static final int MODE_REDSTONE_TO_LAPIS = 1;
    public static final int MODE_LAPIS_TO_REDSTONE = 2;

    private final DataSlot mode = trackedInt();
    private final DataSlot inputFace = trackedInt();
    private final DataSlot outputFace = trackedInt();
    private final DataSlot inputValue = trackedInt();
    private final DataSlot outputValue = trackedInt();
    private final DataSlot inputQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
    private final DataSlot sourceSpacing = trackedInt();
    private final DataSlot reconstructedLapis = trackedInt();
    private final DataSlot quantizationLoss = trackedInt();
    private final DataSlot commissioningStatus = trackedInt();

    public MediaConversionMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public MediaConversionMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.MEDIA_CONVERSION.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        int conversionMode = block instanceof RedstoneToLapisScalerBlock ? MODE_REDSTONE_TO_LAPIS
                : block instanceof LapisToRedstoneQuantizerBlock ? MODE_LAPIS_TO_REDSTONE : MODE_UNKNOWN;
        mode.set(conversionMode);
        inputFace.set(-1);
        outputFace.set(-1);
        inputValue.set(0);
        outputValue.set(0);
        inputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        sourceSpacing.set(-1);
        reconstructedLapis.set(-1);
        quantizationLoss.set(-1);
        commissioningStatus.set(CommissioningStatus.NOT_READY.code());

        if (!(block instanceof EngineeringPortProvider provider) || conversionMode == MODE_UNKNOWN) return;

        PortQuality inQuality = PortQuality.NO_SIGNAL;
        PortQuality outQuality = PortQuality.NO_SIGNAL;
        int inValue = 0;
        int outValue = 0;
        for (Direction side : Direction.values()) {
            var descriptor = provider.engineeringPort(state, side);
            if (descriptor.isEmpty()) continue;
            var snapshot = provider.engineeringSnapshot(level, blockPos, state, side);
            if (descriptor.get().direction() == PortDirection.INPUT) {
                inputFace.set(side.ordinal());
                if (snapshot.isPresent()) {
                    inQuality = snapshot.get().quality();
                    inValue = conversionMode == MODE_REDSTONE_TO_LAPIS
                            ? (int) Math.round(snapshot.get().value())
                            : (int) Math.round(snapshot.get().value() * 100.0);
                }
            } else if (descriptor.get().direction() == PortDirection.OUTPUT) {
                outputFace.set(side.ordinal());
                if (snapshot.isPresent()) {
                    outQuality = snapshot.get().quality();
                    outValue = conversionMode == MODE_REDSTONE_TO_LAPIS
                            ? (int) Math.round(snapshot.get().value() * 100.0)
                            : (int) Math.round(snapshot.get().value());
                }
            }
        }

        inputValue.set(inValue);
        outputValue.set(outValue);
        inputQuality.set(inQuality.ordinal());
        outputQuality.set(outQuality.ordinal());
        if (conversionMode == MODE_REDSTONE_TO_LAPIS && inQuality == PortQuality.VALID) {
            sourceSpacing.set(CoreMediaDiagnostics.sourceCodeSpacing(inValue));
        } else if (conversionMode == MODE_LAPIS_TO_REDSTONE && inQuality == PortQuality.VALID) {
            reconstructedLapis.set(CoreMediaDiagnostics.lapisReconstructedFromRedstone(outValue));
            quantizationLoss.set(CoreMediaDiagnostics.quantizationError(inValue));
        }
        commissioningStatus.set(commissioning(inQuality, outQuality).code());
    }

    private static CommissioningStatus commissioning(PortQuality input, PortQuality output) {
        if (isFailure(input) || isFailure(output)) return CommissioningStatus.FAIL;
        if (input == PortQuality.STALE || input == PortQuality.NO_SIGNAL
                || output == PortQuality.STALE || output == PortQuality.NO_SIGNAL) return CommissioningStatus.NOT_READY;
        if (input == PortQuality.SATURATED || output == PortQuality.SATURATED) return CommissioningStatus.MARGINAL;
        return CommissioningStatus.PASS;
    }

    private static boolean isFailure(PortQuality quality) {
        return quality == PortQuality.FAULT || quality == PortQuality.DOMAIN_MISMATCH
                || quality == PortQuality.TOPOLOGY_ERROR;
    }

    public int mode() { return mode.get(); }
    public boolean redstoneToLapis() { return mode() == MODE_REDSTONE_TO_LAPIS; }
    public boolean lapisToRedstone() { return mode() == MODE_LAPIS_TO_REDSTONE; }
    public Direction inputFace() { return direction(inputFace.get()); }
    public Direction outputFace() { return direction(outputFace.get()); }
    public int inputValue() { return inputValue.get(); }
    public int outputValue() { return outputValue.get(); }
    public int sourceSpacing() { return sourceSpacing.get(); }
    public int reconstructedLapis() { return reconstructedLapis.get(); }
    public int quantizationLoss() { return quantizationLoss.get(); }
    public PortQuality inputQuality() { return quality(inputQuality.get()); }
    public PortQuality outputQuality() { return quality(outputQuality.get()); }
    public CommissioningStatus commissioningStatus() { return CommissioningStatus.fromCode(commissioningStatus.get()); }

    private static Direction direction(int ordinal) {
        Direction[] values = Direction.values();
        return ordinal < 0 || ordinal >= values.length ? Direction.NORTH : values[ordinal];
    }
    private static PortQuality quality(int ordinal) {
        PortQuality[] values = PortQuality.values();
        return ordinal < 0 || ordinal >= values.length ? PortQuality.NO_SIGNAL : values[ordinal];
    }
}
