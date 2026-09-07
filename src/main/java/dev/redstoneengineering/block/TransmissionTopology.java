package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Central port/topology rules for RSE transmission media.
 * Visual connections and graph connections must agree with these rules.
 *
 * <p>Cable-like information media share one routing grammar: direct cable-to-cable
 * continuity is planar (N/E/S/W). A vertical edge is legal only when the neighbor
 * is the unified Signal Junction Point and that junction has resolved to exactly
 * one matching medium. A junction never converts domains.</p>
 */
public final class TransmissionTopology {
    private TransmissionTopology() {}

    public enum SignalMedium implements StringRepresentable {
        NONE("none"),
        REDSTONE("redstone"),
        INSTRUMENT("instrument"),
        DATA_BUS_8("bus8"),
        SERIAL("serial"),
        DIFFERENTIAL("differential"),
        MISMATCH("mismatch");

        private final String serializedName;

        SignalMedium(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public boolean routable() {
            return this != NONE && this != MISMATCH;
        }
    }

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

    private static EngineeringDomain domain(SignalMedium medium) {
        return switch (medium) {
            case REDSTONE -> EngineeringDomain.REDSTONE;
            case INSTRUMENT -> EngineeringDomain.INSTRUMENT_BUS;
            case DATA_BUS_8 -> EngineeringDomain.DATA_BUS_8;
            case SERIAL -> EngineeringDomain.SERIAL_DATA;
            case DIFFERENTIAL -> EngineeringDomain.DIFFERENTIAL_DATA;
            case NONE, MISMATCH -> null;
        };
    }

    private static boolean declaredDomainPort(BlockState state, Direction mediumToDevice, SignalMedium medium) {
        EngineeringDomain expected = domain(medium);
        if (expected == null || !(state.getBlock() instanceof EngineeringPortProvider provider)) return false;
        return provider.engineeringPort(state, deviceToMedium(mediumToDevice))
                .map(port -> port.domain() == expected)
                .orElse(false);
    }

    /** Returns the physical cable medium represented by a line block, never a converter. */
    public static SignalMedium lineMedium(BlockState state) {
        var block = state.getBlock();
        if (block instanceof InstrumentCableBlock) return SignalMedium.INSTRUMENT;
        if (block instanceof RedstoneSignalCableBlock) return SignalMedium.REDSTONE;
        if (block instanceof EightBitDataBusBlock) return SignalMedium.DATA_BUS_8;
        if (block instanceof SerialDataLineBlock) return SignalMedium.SERIAL;
        if (block instanceof DifferentialDataPairBlock) return SignalMedium.DIFFERENTIAL;
        return SignalMedium.NONE;
    }

    /**
     * Resolve a unified junction from adjacent cable identities. One medium is accepted;
     * mixed-media adjacency is a hard topology mismatch rather than an implicit converter.
     */
    public static SignalMedium inferJunctionMedium(BlockGetter level, BlockPos junctionPos) {
        SignalMedium found = SignalMedium.NONE;
        for (Direction direction : Direction.values()) {
            SignalMedium candidate = lineMedium(level.getBlockState(junctionPos.relative(direction)));
            if (!candidate.routable()) continue;
            if (found == SignalMedium.NONE) found = candidate;
            else if (found != candidate) return SignalMedium.MISMATCH;
        }
        return found;
    }

    public static boolean junctionAccepts(BlockGetter level, BlockPos junctionPos, SignalMedium medium) {
        return medium.routable() && inferJunctionMedium(level, junctionPos) == medium;
    }

    /** Used by the junction itself after it has resolved a single medium. */
    public static boolean junctionNeighborMatches(
            BlockGetter level,
            BlockPos junctionPos,
            Direction junctionToNeighbor,
            BlockState neighbor,
            SignalMedium medium
    ) {
        if (!medium.routable()) return false;
        SignalMedium direct = lineMedium(neighbor);
        if (direct.routable()) return direct == medium;
        if (neighbor.getBlock() instanceof RedstoneCableJunctionBlock) return false;
        return declaredDomainPort(neighbor, junctionToNeighbor, medium);
    }

    private static boolean planarCablePort(
            BlockGetter level,
            BlockPos cablePos,
            Direction cableToNeighbor,
            BlockState neighbor,
            SignalMedium medium
    ) {
        BlockPos neighborPos = cablePos.relative(cableToNeighbor);
        if (neighbor.getBlock() instanceof RedstoneCableJunctionBlock) {
            return junctionAccepts(level, neighborPos, medium);
        }
        if (cableToNeighbor.getAxis() == Direction.Axis.Y) return false;
        SignalMedium direct = lineMedium(neighbor);
        if (direct.routable()) return direct == medium;
        return declaredDomainPort(neighbor, cableToNeighbor, medium);
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

    /** Legacy state-only query used by diagnostics. Runtime cable placement uses the overload below. */
    public static boolean redstoneCablePort(BlockState s, Direction mediumToDevice) {
        var b=s.getBlock();
        if (b instanceof RedstoneSignalCableBlock) return true;
        if (b instanceof RedstoneCableJunctionBlock && s.getValue(RedstoneCableJunctionBlock.MEDIUM) == SignalMedium.REDSTONE) return true;
        if (b instanceof RedstoneCableTerminalBlock t) return t.cableSide(s) == mediumToDevice.getOpposite();
        return false;
    }

    public static boolean redstoneCablePort(
            BlockGetter level, BlockPos cablePos, Direction cableToNeighbor, BlockState neighbor
    ) {
        if (neighbor.getBlock() instanceof RedstoneCableTerminalBlock terminal) {
            return cableToNeighbor.getAxis() != Direction.Axis.Y
                    && terminal.cableSide(neighbor) == cableToNeighbor.getOpposite();
        }
        return planarCablePort(level, cablePos, cableToNeighbor, neighbor, SignalMedium.REDSTONE);
    }

    /** Legacy state-only query used by probe/instrument diagnostics. */
    public static boolean instrumentPort(BlockState s, Direction mediumToDevice) {
        var b = s.getBlock();
        if (b instanceof InstrumentCableBlock || b instanceof OscilloscopeBlock || b instanceof LogicAnalyzerBlock) return true;
        if (b instanceof RedstoneCableJunctionBlock && s.getValue(RedstoneCableJunctionBlock.MEDIUM) == SignalMedium.INSTRUMENT) return true;
        if (b instanceof SignalProbeBlock) {
            Direction probeToCable = deviceToMedium(mediumToDevice);
            return probeToCable == s.getValue(SignalProbeBlock.FACING).getOpposite();
        }
        return false;
    }

    public static boolean instrumentCablePort(
            BlockGetter level, BlockPos cablePos, Direction cableToNeighbor, BlockState neighbor
    ) {
        if (neighbor.getBlock() instanceof SignalProbeBlock) {
            if (cableToNeighbor.getAxis() == Direction.Axis.Y) return false;
            return instrumentPort(neighbor, cableToNeighbor);
        }
        return planarCablePort(level, cablePos, cableToNeighbor, neighbor, SignalMedium.INSTRUMENT);
    }

    public static boolean dataBusPort(
            BlockGetter level, BlockPos cablePos, Direction cableToNeighbor, BlockState neighbor
    ) {
        return planarCablePort(level, cablePos, cableToNeighbor, neighbor, SignalMedium.DATA_BUS_8);
    }

    public static boolean serialPort(
            BlockGetter level, BlockPos cablePos, Direction cableToNeighbor, BlockState neighbor
    ) {
        return planarCablePort(level, cablePos, cableToNeighbor, neighbor, SignalMedium.SERIAL);
    }

    public static boolean differentialPort(
            BlockGetter level, BlockPos cablePos, Direction cableToNeighbor, BlockState neighbor
    ) {
        return planarCablePort(level, cablePos, cableToNeighbor, neighbor, SignalMedium.DIFFERENTIAL);
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
