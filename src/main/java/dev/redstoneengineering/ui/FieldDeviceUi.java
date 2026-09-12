package dev.redstoneengineering.ui;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.ui.menu.AmethystSystemMenu;
import dev.redstoneengineering.ui.menu.DigitalCommunicationMenu;
import dev.redstoneengineering.ui.menu.FieldDeviceMenu;
import dev.redstoneengineering.ui.menu.MagneticSystemMenu;
import dev.redstoneengineering.ui.menu.OpticalSystemMenu;
import dev.redstoneengineering.ui.menu.PneumaticSystemMenu;
import dev.redstoneengineering.ui.menu.QuartzTimingMenu;
import dev.redstoneengineering.ui.menu.RadioLinkMenu;
import dev.redstoneengineering.ui.menu.RangeSensorMenu;
import dev.redstoneengineering.ui.menu.ReliabilitySystemMenu;
import dev.redstoneengineering.ui.menu.SignalProcessorMenu;
import dev.redstoneengineering.ui.menu.UniversalFieldDeviceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Common server-side openers for rich known-device UI and universal engineering fallback UI. */
public final class FieldDeviceUi {
    private FieldDeviceUi() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        var state = player.level().getBlockState(pos);
        var block = state.getBlock();
        var title = block.getName();
        if (block instanceof RangeSensorBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new RangeSensorMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof PrecisionFilterBlock || block instanceof EdgeDetectorBlock || block instanceof PulseShaperBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new SignalProcessorMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof QuartzOscillatorBlock || block instanceof QuartzClockDividerBlock || block instanceof QuartzStabilityMonitorBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new QuartzTimingMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof RadioTransmitterBlock || block instanceof RadioReceiverBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new RadioLinkMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof RedstoneByteEncoderBlock || block instanceof ByteToRedstoneDecoderBlock || block instanceof SerializerBlock
                || block instanceof DeserializerBlock || block instanceof DigitalRegeneratorBlock || block instanceof DifferentialDriverBlock
                || block instanceof DifferentialReceiverBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new DigitalCommunicationMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof AirCompressorBlock || block instanceof PneumaticPipeBlock || block instanceof AirReservoirBlock
                || block instanceof PressureRegulatorBlock || block instanceof PneumaticReceiverBlock || block instanceof PneumaticValveBlock
                || block instanceof PneumaticCheckValveBlock || block instanceof PneumaticFlowMeterBlock || block instanceof PneumaticProportionalValveBlock
                || block instanceof PneumaticReliefValveBlock || block instanceof PneumaticCylinderBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new PneumaticSystemMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof OpticalEmitterBlock || block instanceof OpticalReceiverBlock || block instanceof OpticalPowerMeterBlock
                || block instanceof OpticalSplitterBlock || block instanceof OpticalChannelFilterBlock || block instanceof OpticalAttenuatorBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new OpticalSystemMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof AmethystResonatorBlock || block instanceof AmethystFrequencyFilterBlock
                || block instanceof AmethystTunedResonatorBlock || block instanceof AmethystSpectrumAnalyzerBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new AmethystSystemMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof ElectromagnetBlock || block instanceof PermanentMagnetBlock || block instanceof InductionCoilBlock
                || block instanceof MagneticFieldSensorBlock || block instanceof MagneticGradientMeterBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new MagneticSystemMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        if (block instanceof WatchdogBlock || block instanceof ServoActuatorBlock || block instanceof ServoPositionSensorBlock
                || block instanceof RedundantVoterBlock || block instanceof FaultLatchBlock) {
            player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new ReliabilitySystemMenu(id, inv, pos), title), data -> data.writeBlockPos(pos)); return;
        }
        // These migrated endpoints expose their complete engineering state through formal ports,
        // so the universal HMI is more accurate than forcing them through a device-kind table.
        if (block instanceof EngineeringLightSensorBlock || block instanceof TankLevelSensorBlock
                || block instanceof EntityDensitySensorBlock || block instanceof AnalogIndicatorBlock) {
            openUniversal(player, pos); return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new FieldDeviceMenu(id, inv, pos), title), data -> data.writeBlockPos(pos));
    }

    public static void openUniversal(ServerPlayer player, BlockPos pos) {
        var title = player.level().getBlockState(pos).getBlock().getName();
        player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new UniversalFieldDeviceMenu(id, inv, pos), title), data -> data.writeBlockPos(pos));
    }
}
