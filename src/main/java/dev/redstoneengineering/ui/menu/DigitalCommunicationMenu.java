package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.ByteToRedstoneDecoderBlock;
import dev.redstoneengineering.block.DeserializerBlock;
import dev.redstoneengineering.block.DifferentialDriverBlock;
import dev.redstoneengineering.block.DifferentialReceiverBlock;
import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.RedstoneByteEncoderBlock;
import dev.redstoneengineering.block.SerializerBlock;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-authoritative HMI model for directional digital converters and regenerators. */
public final class DigitalCommunicationMenu extends EngineeringDeviceMenu {
    public static final int KIND_ENCODER = 0;
    public static final int KIND_DECODER = 1;
    public static final int KIND_SERIALIZER = 2;
    public static final int KIND_DESERIALIZER = 3;
    public static final int KIND_REGENERATOR = 4;
    public static final int KIND_DIFF_DRIVER = 5;
    public static final int KIND_DIFF_RECEIVER = 6;

    public static final int BUTTON_PARAMETER_PREVIOUS = 0;
    public static final int BUTTON_PARAMETER_NEXT = 1;
    public static final int BUTTON_ROTATE_LEFT = 2;
    public static final int BUTTON_ROTATE_RIGHT = 3;
    public static final int BUTTON_RX_LEFT = 4;
    public static final int BUTTON_RX_RIGHT = 5;
    public static final int BUTTON_TX_LEFT = 6;
    public static final int BUTTON_TX_RIGHT = 7;
    public static final int BUTTON_INPUT_LEFT = BUTTON_RX_LEFT;
    public static final int BUTTON_INPUT_RIGHT = BUTTON_RX_RIGHT;
    public static final int BUTTON_OUTPUT_LEFT = BUTTON_TX_LEFT;
    public static final int BUTTON_OUTPUT_RIGHT = BUTTON_TX_RIGHT;

    private final DataSlot kind = trackedInt();
    private final DataSlot inputValue = trackedInt();
    private final DataSlot outputValue = trackedInt();
    private final DataSlot inputQuality = trackedInt();
    private final DataSlot outputQuality = trackedInt();
    private final DataSlot inputDomain = trackedInt();
    private final DataSlot outputDomain = trackedInt();
    private final DataSlot parameter = trackedInt();
    private final DataSlot auxiliary = trackedInt();
    private final DataSlot inputFacing = trackedInt();
    private final DataSlot outputFacing = trackedInt();

    /** Medium-level evidence for the actual communication side of this converter. */
    private final DataSlot mediumDomain = trackedInt();
    private final DataSlot mediumQualityPercent = trackedInt();
    private final DataSlot mediumAgeTicks = trackedInt();
    private final DataSlot mediumDriverCount = trackedInt();
    private final DataSlot mediumMetricA = trackedInt();
    private final DataSlot mediumMetricB = trackedInt();
    private final DataSlot mediumMetricC = trackedInt();

    public DigitalCommunicationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public DigitalCommunicationMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.DIGITAL_COMMUNICATION.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        inputValue.set(0);
        outputValue.set(0);
        inputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        outputQuality.set(PortQuality.NO_SIGNAL.ordinal());
        inputDomain.set(EngineeringDomain.GENERIC.ordinal());
        outputDomain.set(EngineeringDomain.GENERIC.ordinal());
        parameter.set(0);
        auxiliary.set(0);
        inputFacing.set(-1);
        outputFacing.set(-1);
        clearMediumTelemetry();

        int deviceKind = kindOf(block);
        kind.set(deviceKind);
        if (deviceKind < 0 || !(block instanceof EngineeringPortProvider provider)) return;

        Direction outputSide = directionalOutput(state);
        Direction inputSide = directionalInput(state);
        if (outputSide == null || inputSide == null) return;
        outputFacing.set(outputSide.ordinal());
        inputFacing.set(inputSide.ordinal());

        provider.engineeringPort(state, inputSide).ifPresent(port -> inputDomain.set(port.domain().ordinal()));
        provider.engineeringPort(state, outputSide).ifPresent(port -> outputDomain.set(port.domain().ordinal()));
        provider.engineeringSnapshot(level, blockPos, state, inputSide).ifPresent(snapshot -> {
            inputValue.set((int) Math.round(snapshot.value()));
            inputQuality.set(snapshot.quality().ordinal());
        });
        provider.engineeringSnapshot(level, blockPos, state, outputSide).ifPresent(snapshot -> {
            outputValue.set((int) Math.round(snapshot.value()));
            outputQuality.set(snapshot.quality().ordinal());
        });

        if (block instanceof DigitalRegeneratorBlock) {
            int threshold = state.getValue(DigitalRegeneratorBlock.THRESHOLD);
            parameter.set(threshold);
            auxiliary.set(InformationRuntime.snapshot(level, "serial", blockPos.relative(inputSide)).qualityPercent());
        } else if (block instanceof SerializerBlock) {
            auxiliary.set(InformationRuntime.snapshot(level, "serial", blockPos).selector());
        } else if (block instanceof DeserializerBlock) {
            auxiliary.set(InformationRuntime.snapshot(level, "serial", blockPos.relative(inputSide)).selector());
        }

        refreshMediumTelemetry(deviceKind, inputSide, outputSide);
    }

    private void clearMediumTelemetry() {
        mediumDomain.set(EngineeringDomain.GENERIC.ordinal());
        mediumQualityPercent.set(0);
        mediumAgeTicks.set(-1);
        mediumDriverCount.set(0);
        mediumMetricA.set(0);
        mediumMetricB.set(0);
        mediumMetricC.set(0);
    }

    /**
     * Synchronizes existing runtime diagnostics only. This method never resolves a graph, drives a
     * medium, or creates synthetic packet history. Metric meanings are domain-specific:
     * DATA_BUS_8: A=nodes, B=contention frames, C=conflict frames;
     * SERIAL_DATA: A=period ticks, B=utilization %, C=nodes;
     * DIFFERENTIAL_DATA: A=payload bits (1), B=reserved, C=reserved.
     */
    private void refreshMediumTelemetry(int deviceKind, Direction inputSide, Direction outputSide) {
        EngineeringDomain domain;
        BlockPos mediumPos;
        if (deviceKind == KIND_ENCODER) {
            domain = EngineeringDomain.DATA_BUS_8;
            mediumPos = blockPos.relative(outputSide);
        } else if (deviceKind == KIND_DECODER || deviceKind == KIND_SERIALIZER) {
            domain = deviceKind == KIND_DECODER ? EngineeringDomain.DATA_BUS_8 : EngineeringDomain.SERIAL_DATA;
            mediumPos = blockPos.relative(deviceKind == KIND_DECODER ? inputSide : outputSide);
        } else if (deviceKind == KIND_DESERIALIZER || deviceKind == KIND_REGENERATOR) {
            domain = EngineeringDomain.SERIAL_DATA;
            mediumPos = blockPos.relative(inputSide);
        } else if (deviceKind == KIND_DIFF_DRIVER) {
            domain = EngineeringDomain.DIFFERENTIAL_DATA;
            mediumPos = blockPos.relative(outputSide);
        } else if (deviceKind == KIND_DIFF_RECEIVER) {
            domain = EngineeringDomain.DIFFERENTIAL_DATA;
            mediumPos = blockPos.relative(inputSide);
        } else {
            return;
        }

        mediumDomain.set(domain.ordinal());
        if (domain == EngineeringDomain.DATA_BUS_8) {
            DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(level, mediumPos);
            InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "bus8", mediumPos);
            mediumQualityPercent.set(snapshot.qualityPercent());
            mediumAgeTicks.set(snapshot.ageTicks());
            mediumDriverCount.set(diagnostics.driverCount());
            mediumMetricA.set(diagnostics.nodes());
            mediumMetricB.set(diagnostics.contentionFrames());
            mediumMetricC.set(diagnostics.conflictFrames());
        } else if (domain == EngineeringDomain.SERIAL_DATA) {
            SerialNetwork.Diagnostics diagnostics = SerialNetwork.getDiagnostics(level, mediumPos);
            InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "serial", mediumPos);
            mediumQualityPercent.set(snapshot.qualityPercent());
            mediumAgeTicks.set(snapshot.ageTicks());
            mediumDriverCount.set(diagnostics.driverCount());
            mediumMetricA.set(diagnostics.periodTicks());
            mediumMetricB.set(diagnostics.utilizationPercent());
            mediumMetricC.set(diagnostics.nodes());
        } else if (domain == EngineeringDomain.DIFFERENTIAL_DATA) {
            InformationRuntime.Snapshot snapshot = InformationRuntime.snapshot(level, "diff", mediumPos);
            mediumQualityPercent.set(snapshot.qualityPercent());
            mediumAgeTicks.set(snapshot.ageTicks());
            mediumDriverCount.set(DifferentialNetwork.driverCount(level, mediumPos));
            mediumMetricA.set(1);
        }
    }

    private static int kindOf(Block block) {
        if (block instanceof RedstoneByteEncoderBlock) return KIND_ENCODER;
        if (block instanceof ByteToRedstoneDecoderBlock) return KIND_DECODER;
        if (block instanceof SerializerBlock) return KIND_SERIALIZER;
        if (block instanceof DeserializerBlock) return KIND_DESERIALIZER;
        if (block instanceof DigitalRegeneratorBlock) return KIND_REGENERATOR;
        if (block instanceof DifferentialDriverBlock) return KIND_DIFF_DRIVER;
        if (block instanceof DifferentialReceiverBlock) return KIND_DIFF_RECEIVER;
        return -1;
    }

    private static Direction directionalOutput(BlockState state) {
        if (state.hasProperty(DirectionalDomainBlock.FACING)) return DirectionalDomainBlock.seriesOutputSide(state);
        if (state.hasProperty(DirectionalSignalBlock.FACING)) return DirectionalSignalBlock.seriesOutputSide(state);
        return null;
    }

    private static Direction directionalInput(BlockState state) {
        if (state.hasProperty(DirectionalDomainBlock.INPUT_FACING)) return DirectionalDomainBlock.seriesInputSide(state);
        if (state.hasProperty(DirectionalSignalBlock.INPUT_FACING)) return DirectionalSignalBlock.seriesInputSide(state);
        Direction output = directionalOutput(state);
        return output == null ? null : output.getOpposite();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed;

        if (id == BUTTON_ROTATE_LEFT || id == BUTTON_ROTATE_RIGHT) {
            boolean clockwise = id == BUTTON_ROTATE_RIGHT;
            if (block instanceof DirectionalDomainBlock) changed = DirectionalDomainBlock.rotateWholeRoute(level, blockPos, clockwise);
            else if (block instanceof DirectionalSignalBlock) changed = DirectionalSignalBlock.rotateWholeRoute(level, blockPos, clockwise);
            else return false;
            if (changed) level.scheduleTick(blockPos, block, 1);
        } else if (id == BUTTON_RX_LEFT || id == BUTTON_RX_RIGHT) {
            boolean clockwise = id == BUTTON_RX_RIGHT;
            if (block instanceof DirectionalDomainBlock) changed = DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise);
            else if (block instanceof DirectionalSignalBlock) changed = DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise);
            else return false;
            if (changed) level.scheduleTick(blockPos, block, 1);
        } else if (id == BUTTON_TX_LEFT || id == BUTTON_TX_RIGHT) {
            boolean clockwise = id == BUTTON_TX_RIGHT;
            if (block instanceof DirectionalDomainBlock) changed = DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise);
            else if (block instanceof DirectionalSignalBlock) changed = DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise);
            else return false;
            if (changed) level.scheduleTick(blockPos, block, 1);
        } else if (block instanceof DigitalRegeneratorBlock regenerator
                && (id == BUTTON_PARAMETER_PREVIOUS || id == BUTTON_PARAMETER_NEXT)) {
            int threshold = state.getValue(DigitalRegeneratorBlock.THRESHOLD);
            threshold = id == BUTTON_PARAMETER_NEXT ? (threshold + 1) % 3 : Math.floorMod(threshold - 1, 3);
            level.setBlock(blockPos, state.setValue(DigitalRegeneratorBlock.THRESHOLD, threshold), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, regenerator, 1);
            changed = true;
        } else return false;

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    public int kind() { return kind.get(); }
    public int inputValue() { return inputValue.get(); }
    public int outputValue() { return outputValue.get(); }
    public int parameter() { return parameter.get(); }
    public int auxiliary() { return auxiliary.get(); }
    public int mediumQualityPercent() { return mediumQualityPercent.get(); }
    public int mediumAgeTicks() { return mediumAgeTicks.get(); }
    public int mediumDriverCount() { return mediumDriverCount.get(); }
    public int mediumMetricA() { return mediumMetricA.get(); }
    public int mediumMetricB() { return mediumMetricB.get(); }
    public int mediumMetricC() { return mediumMetricC.get(); }

    public PortQuality inputQuality() { return quality(inputQuality.get()); }
    public PortQuality outputQuality() { return quality(outputQuality.get()); }
    public EngineeringDomain inputDomain() { return domain(inputDomain.get()); }
    public EngineeringDomain outputDomain() { return domain(outputDomain.get()); }
    public EngineeringDomain mediumDomain() { return domain(mediumDomain.get()); }

    private static PortQuality quality(int ordinal) {
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    private static EngineeringDomain domain(int ordinal) {
        EngineeringDomain[] all = EngineeringDomain.values();
        return ordinal < 0 || ordinal >= all.length ? EngineeringDomain.GENERIC : all[ordinal];
    }

    public Direction outputDirection() {
        int ordinal = outputFacing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.NORTH : all[ordinal];
    }

    public Direction inputDirection() {
        int ordinal = inputFacing.get();
        Direction[] all = Direction.values();
        return ordinal < 0 || ordinal >= all.length ? Direction.SOUTH : all[ordinal];
    }
}
