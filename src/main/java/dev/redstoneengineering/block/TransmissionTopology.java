package dev.redstoneengineering.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central port/topology rules for RSE transmission media.
 * Visual connections and graph connections must agree with these rules.
 */
public final class TransmissionTopology {
    private TransmissionTopology() {}

    private static Direction deviceToMedium(Direction mediumToDevice) {
        return mediumToDevice.getOpposite();
    }
    private static boolean onFrontBack(BlockState s, Direction mediumToDevice) {
        if (!(s.getBlock() instanceof DirectionalDomainBlock)) return true;
        Direction facing = s.getValue(DirectionalDomainBlock.FACING);
        Direction d = deviceToMedium(mediumToDevice);
        return d == facing || d == facing.getOpposite();
    }
    private static boolean onFront(BlockState s, Direction mediumToDevice) {
        Direction facing = s.getValue(DirectionalDomainBlock.FACING);
        return deviceToMedium(mediumToDevice) == facing;
    }
    private static boolean onBack(BlockState s, Direction mediumToDevice) {
        Direction facing = s.getValue(DirectionalDomainBlock.FACING);
        return deviceToMedium(mediumToDevice) == facing.getOpposite();
    }

    public static boolean lapisPort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof LapisSignalLineBlock || b instanceof LapisPrecisionSourceBlock || b instanceof LapisNoiseSourceBlock) return true;
        if (b instanceof LapisLowPassFilterBlock || b instanceof QuartzTriggeredLapisSamplerBlock) return onFrontBack(s, mediumToDevice);
        if (b instanceof AbstractLapisTransducerBlock || b instanceof RedstoneToLapisScalerBlock) return onFront(s, mediumToDevice);
        if (b instanceof LapisToRedstoneQuantizerBlock) {
            Direction facing = s.getValue(LapisToRedstoneQuantizerBlock.FACING);
            return deviceToMedium(mediumToDevice) == facing.getOpposite();
        }
        if (b instanceof LapisPrecisionMeterBlock) return s.getValue(LapisPrecisionMeterBlock.FACING) == mediumToDevice.getOpposite();
        return false;
    }

    public static boolean quartzPort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof QuartzTimingLineBlock || b instanceof QuartzOscillatorBlock || b instanceof QuartzLabOscillatorBlock) return true;
        if (b instanceof QuartzClockDividerBlock || b instanceof QuartzPhaseDelayBlock || b instanceof QuartzStabilityMonitorBlock)
            return onFrontBack(s, mediumToDevice);
        if (b instanceof QuartzTriggeredLapisSamplerBlock) {
            Direction facing=s.getValue(DirectionalDomainBlock.FACING);
            return deviceToMedium(mediumToDevice)==DirectionalDomainBlock.leftOf(facing);
        }
        return false;
    }

    public static boolean amethystPort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof AmethystResonanceDustBlock || b instanceof AmethystResonatorBlock) return true;
        if (b instanceof AmethystFrequencyFilterBlock || b instanceof AmethystTunedResonatorBlock)
            return onFrontBack(s, mediumToDevice);
        return false;
    }

    public static boolean redstoneCablePort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof RedstoneSignalCableBlock || b instanceof RedstoneCableJunctionBlock) return true;
        if (b instanceof RedstoneCableTerminalBlock t) return t.cableSide(s) == mediumToDevice.getOpposite();
        return false;
    }

    public static boolean instrumentPort(BlockState s, Direction mediumToDevice) {
        var b = s.getBlock();
        if (b instanceof InstrumentCableBlock || b instanceof OscilloscopeBlock || b instanceof LogicAnalyzerBlock) return true;
        if (b instanceof SignalProbeBlock) {
            Direction probeToCable = deviceToMedium(mediumToDevice);
            return probeToCable == s.getValue(SignalProbeBlock.FACING).getOpposite();
        }
        return false;
    }

    public static boolean copperPort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof CopperWireBlock || b instanceof CopperCableJunctionBlock || b instanceof CopperVoltageSourceBlock
                || b instanceof CopperResistiveLoadBlock || b instanceof ElectromagnetBlock || b instanceof ThermalHeaterBlock) return true;
        if (b instanceof CopperSeriesResistorBlock || b instanceof CopperCapacitorBlock || b instanceof CopperFuseBlock)
            return onFrontBack(s, mediumToDevice);
        if (b instanceof LapisVoltageTransducerBlock) return onBack(s, mediumToDevice);
        if (b instanceof InductionCoilBlock) return onFront(s, mediumToDevice); // Copper output only
        if (b instanceof CopperCircuitMeterBlock) return s.getValue(CopperCircuitMeterBlock.FACING) == mediumToDevice.getOpposite();
        return false;
    }

    public static boolean opticalPort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof OpticalFiberJunctionBlock) return !s.getValue(OpticalFiberJunctionBlock.SERVICE_OPEN);
        if (b instanceof OpticalFiberBlock || b instanceof OpticalEmitterBlock || b instanceof OpticalReceiverBlock) return true;
        if (b instanceof OpticalChannelFilterBlock || b instanceof OpticalAttenuatorBlock) return onFrontBack(s, mediumToDevice);
        if (b instanceof LapisOpticalTransducerBlock) return onBack(s, mediumToDevice);
        if (b instanceof OpticalSplitterBlock) {
            Direction facing=s.getValue(DirectionalDomainBlock.FACING);
            Direction d=deviceToMedium(mediumToDevice);
            return d==facing || d==facing.getOpposite() || d==DirectionalDomainBlock.leftOf(facing);
        }
        if (b instanceof OpticalPowerMeterBlock) return s.getValue(OpticalPowerMeterBlock.FACING) == mediumToDevice.getOpposite();
        return false;
    }
}
