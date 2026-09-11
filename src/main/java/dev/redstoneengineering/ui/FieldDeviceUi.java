package dev.redstoneengineering.ui;

import dev.redstoneengineering.block.AirCompressorBlock;
import dev.redstoneengineering.block.AirReservoirBlock;
import dev.redstoneengineering.block.ByteToRedstoneDecoderBlock;
import dev.redstoneengineering.block.DeserializerBlock;
import dev.redstoneengineering.block.DifferentialDriverBlock;
import dev.redstoneengineering.block.DifferentialReceiverBlock;
import dev.redstoneengineering.block.DigitalRegeneratorBlock;
import dev.redstoneengineering.block.EdgeDetectorBlock;
import dev.redstoneengineering.block.PneumaticCheckValveBlock;
import dev.redstoneengineering.block.PneumaticCylinderBlock;
import dev.redstoneengineering.block.PneumaticFlowMeterBlock;
import dev.redstoneengineering.block.PneumaticPipeBlock;
import dev.redstoneengineering.block.PneumaticProportionalValveBlock;
import dev.redstoneengineering.block.PneumaticReceiverBlock;
import dev.redstoneengineering.block.PneumaticReliefValveBlock;
import dev.redstoneengineering.block.PneumaticValveBlock;
import dev.redstoneengineering.block.PrecisionFilterBlock;
import dev.redstoneengineering.block.PressureRegulatorBlock;
import dev.redstoneengineering.block.PulseShaperBlock;
import dev.redstoneengineering.block.QuartzClockDividerBlock;
import dev.redstoneengineering.block.QuartzOscillatorBlock;
import dev.redstoneengineering.block.QuartzStabilityMonitorBlock;
import dev.redstoneengineering.block.RadioReceiverBlock;
import dev.redstoneengineering.block.RadioTransmitterBlock;
import dev.redstoneengineering.block.RangeSensorBlock;
import dev.redstoneengineering.block.RedstoneByteEncoderBlock;
import dev.redstoneengineering.block.SerializerBlock;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side openers for rich known-device UI and universal engineering fallback UI. */
public final class FieldDeviceUi {
    private FieldDeviceUi() {}

    /** Opens the richest registered device-aware dashboard for the selected block. */
    public static void open(ServerPlayer player, BlockPos pos) {
        var state = player.level().getBlockState(pos);
        var title = state.getBlock().getName();
        if (state.getBlock() instanceof RangeSensorBlock) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new RangeSensorMenu(containerId, inventory, pos), title),
                    data -> data.writeBlockPos(pos));
            return;
        }
        if (state.getBlock() instanceof PrecisionFilterBlock
                || state.getBlock() instanceof EdgeDetectorBlock
                || state.getBlock() instanceof PulseShaperBlock) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new SignalProcessorMenu(containerId, inventory, pos), title),
                    data -> data.writeBlockPos(pos));
            return;
        }
        if (state.getBlock() instanceof QuartzOscillatorBlock
                || state.getBlock() instanceof QuartzClockDividerBlock
                || state.getBlock() instanceof QuartzStabilityMonitorBlock) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new QuartzTimingMenu(containerId, inventory, pos), title),
                    data -> data.writeBlockPos(pos));
            return;
        }
        if (state.getBlock() instanceof RadioTransmitterBlock || state.getBlock() instanceof RadioReceiverBlock) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new RadioLinkMenu(containerId, inventory, pos), title),
                    data -> data.writeBlockPos(pos));
            return;
        }
        if (state.getBlock() instanceof RedstoneByteEncoderBlock
                || state.getBlock() instanceof ByteToRedstoneDecoderBlock
                || state.getBlock() instanceof SerializerBlock
                || state.getBlock() instanceof DeserializerBlock
                || state.getBlock() instanceof DigitalRegeneratorBlock
                || state.getBlock() instanceof DifferentialDriverBlock
                || state.getBlock() instanceof DifferentialReceiverBlock) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new DigitalCommunicationMenu(containerId, inventory, pos), title),
                    data -> data.writeBlockPos(pos));
            return;
        }
        if (state.getBlock() instanceof AirCompressorBlock
                || state.getBlock() instanceof PneumaticPipeBlock
                || state.getBlock() instanceof AirReservoirBlock
                || state.getBlock() instanceof PressureRegulatorBlock
                || state.getBlock() instanceof PneumaticReceiverBlock
                || state.getBlock() instanceof PneumaticValveBlock
                || state.getBlock() instanceof PneumaticCheckValveBlock
                || state.getBlock() instanceof PneumaticFlowMeterBlock
                || state.getBlock() instanceof PneumaticProportionalValveBlock
                || state.getBlock() instanceof PneumaticReliefValveBlock
                || state.getBlock() instanceof PneumaticCylinderBlock) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new PneumaticSystemMenu(containerId, inventory, pos), title),
                    data -> data.writeBlockPos(pos));
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new FieldDeviceMenu(containerId, inventory, pos), title),
                data -> data.writeBlockPos(pos));
    }

    /** Opens the universal six-face EngineeringPortProvider inspector for devices without richer projection. */
    public static void openUniversal(ServerPlayer player, BlockPos pos) {
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new UniversalFieldDeviceMenu(containerId, inventory, pos), title),
                data -> data.writeBlockPos(pos));
    }
}
