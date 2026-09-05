package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortCompatibility;
import dev.redstoneengineering.instrument.InstrumentShieldingAudit;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.RadioKernel;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import dev.redstoneengineering.physics.VibrationNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lightweight Engineering UI for field devices that need inspection and a few bounded controls,
 * but do not justify a dedicated heavy five-tab instrument implementation.
 */
public final class FieldDeviceMenu extends EngineeringDeviceMenu {
    public static final int KIND_UNKNOWN = 0;
    public static final int KIND_PROBE = 1;
    public static final int KIND_FILTER = 2;
    public static final int KIND_REFERENCE = 3;
    public static final int KIND_TERMINAL = 4;
    public static final int KIND_REDSTONE_CABLE = 5;
    public static final int KIND_REDSTONE_JUNCTION = 6;
    public static final int KIND_INSTRUMENT_CABLE = 7;
    public static final int KIND_DATA_BUS_8 = 8;
    public static final int KIND_ENCODER = 9;
    public static final int KIND_DECODER = 10;
    public static final int KIND_SERIAL_LINE = 11;
    public static final int KIND_SERIALIZER = 12;
    public static final int KIND_DESERIALIZER = 13;
    public static final int KIND_DIFFERENTIAL_PAIR = 14;
    public static final int KIND_DIGITAL_REGENERATOR = 15;
    public static final int KIND_DIFFERENTIAL_DRIVER = 16;
    public static final int KIND_DIFFERENTIAL_RECEIVER = 17;
    public static final int KIND_RADIO_TRANSMITTER = 18;
    public static final int KIND_RADIO_RECEIVER = 19;
    public static final int KIND_FREE_OPTICAL_TRANSMITTER = 20;
    public static final int KIND_FREE_OPTICAL_RECEIVER = 21;
    public static final int KIND_QUARTZ_DIVIDER = 22;
    public static final int KIND_QUARTZ_STABILITY = 23;
    public static final int KIND_AMETHYST_RESONATOR = 24;
    public static final int KIND_AMETHYST_DUST = 25;
    public static final int KIND_AMETHYST_FILTER = 26;
    public static final int KIND_AMETHYST_TUNED = 27;
    public static final int KIND_AMETHYST_SPECTRUM = 28;
    public static final int KIND_MECHANICAL_EXCITER = 29;
    public static final int KIND_SLIME_VIBRATION = 30;
    public static final int KIND_MECHANICAL_RECEIVER = 31;
    public static final int KIND_HONEY_DAMPER = 32;
    public static final int KIND_SCULK_INTERFACE = 33;
    public static final int KIND_HYDRO_TUBE = 34;
    public static final int KIND_HYDRO_EXCITER = 35;
    public static final int KIND_HYDRO_RECEIVER = 36;
    public static final int KIND_PHONON_CONDUIT = 37;
    public static final int KIND_THERMAL_ENCODER = 38;
    public static final int KIND_THERMAL_RECEIVER = 39;
    public static final int KIND_SHIELDED_INSTRUMENT_CABLE = 40;
    public static final int KIND_WATCHDOG = 41;
    public static final int KIND_SERVO_ACTUATOR = 42;
    public static final int KIND_SERVO_POSITION_SENSOR = 43;
    public static final int KIND_REDUNDANT_VOTER = 44;
    public static final int KIND_FAULT_LATCH = 45;
    public static final int KIND_OPERATIONS_MONITOR = 46;
    public static final int KIND_AIR_COMPRESSOR = 47;
    public static final int KIND_PNEUMATIC_PIPE = 48;
    public static final int KIND_AIR_RESERVOIR = 49;
    public static final int KIND_PRESSURE_REGULATOR = 50;
    public static final int KIND_PNEUMATIC_RECEIVER = 51;
    public static final int KIND_PNEUMATIC_VALVE = 52;
    public static final int KIND_PNEUMATIC_CHECK_VALVE = 53;
    public static final int KIND_PNEUMATIC_FLOW_METER = 54;
    public static final int KIND_EDGE_DETECTOR = 55;
    public static final int KIND_PULSE_SHAPER = 56;
    public static final int KIND_SIGNAL_TAP = 57;
    public static final int KIND_RANGE_SENSOR = 58;
    public static final int KIND_LAPIS_LINE = 59;
    public static final int KIND_LAPIS_SOURCE = 60;
    public static final int KIND_QUARTZ_LINE = 61;
    public static final int KIND_QUARTZ_OSCILLATOR = 62;
    public static final int KIND_PNEUMATIC_PROPORTIONAL_VALVE = 63;
    public static final int KIND_PNEUMATIC_RELIEF_VALVE = 64;
    public static final int KIND_PNEUMATIC_CYLINDER = 65;
    public static final int KIND_ELECTROMAGNET = 66;
    public static final int KIND_PERMANENT_MAGNET = 67;
    public static final int KIND_INDUCTION_COIL = 68;
    public static final int KIND_MAGNETIC_FIELD_SENSOR = 69;
    public static final int KIND_MAGNETIC_GRADIENT_METER = 70;
    public static final int KIND_OPTICAL_FIBER = 71;
    public static final int KIND_OPTICAL_EMITTER = 72;
    public static final int KIND_OPTICAL_RECEIVER = 73;
    public static final int KIND_OPTICAL_POWER_METER = 74;
    public static final int KIND_OPTICAL_SPLITTER = 75;
    public static final int KIND_OPTICAL_CHANNEL_FILTER = 76;
    public static final int KIND_OPTICAL_ATTENUATOR = 77;
    public static final int KIND_OPTICAL_FIBER_JUNCTION = 78;

    public static final int BUTTON_PRIMARY_DECREASE = 0;
    public static final int BUTTON_PRIMARY_INCREASE = 1;
    public static final int BUTTON_TOGGLE = 2;
    public static final int BUTTON_PRESET_0 = 3;
    public static final int BUTTON_PRESET_5 = 4;
    public static final int BUTTON_PRESET_10 = 5;
    public static final int BUTTON_PRESET_15 = 6;

    private final DataSlot kind = trackedInt();
    private final DataSlot primary = trackedInt();
    private final DataSlot secondary = trackedInt();
    private final DataSlot tertiary = trackedInt();
    private final DataSlot facing = trackedInt();
    private final DataSlot portCount = trackedInt();
    private final DataSlot connectionCount = trackedInt();
    private final DataSlot connectionMask = trackedInt();
    private final DataSlot topologyValid = trackedInt();
    private final DataSlot dataValid = trackedInt();
    private final DataSlot quality = trackedInt();
    private final DataSlot driverCount = trackedInt();

    public FieldDeviceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public FieldDeviceMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.FIELD_DEVICE.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        kind.set(kindOf(block));
        primary.set(0);
        secondary.set(0);
        tertiary.set(0);
        facing.set(-1);
        connectionCount.set(0);
        connectionMask.set(0);
        topologyValid.set(1);
        dataValid.set(1);
        quality.set(100);
        driverCount.set(0);
        portCount.set(block instanceof EngineeringPortProvider provider ? provider.engineeringPorts(state).size() : 0);

        if (block instanceof AirCompressorBlock compressor) {
            primary.set(AirCompressorBlock.commandSignal(level, blockPos));
            secondary.set(AirCompressorBlock.commandedPressure(level, blockPos));
            tertiary.set(PneumaticNetwork.pressure(level, blockPos));
            fillCompatibleTopology(state, compressor);
        } else if (block instanceof PneumaticPipeBlock pipe) {
            primary.set(PneumaticNetwork.pressure(level, blockPos));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, pipe);
        } else if (block instanceof AirReservoirBlock reservoir) {
            primary.set(AirReservoirBlock.storedPressure(level, blockPos));
            secondary.set(PneumaticNetwork.pressure(level, blockPos));
            fillCompatibleTopology(state, reservoir);
        } else if (block instanceof PressureRegulatorBlock regulator) {
            primary.set(PneumaticNetwork.pressure(level, blockPos));
            secondary.set(PressureRegulatorBlock.setpointPressure(state));
            tertiary.set(state.getValue(PressureRegulatorBlock.SETPOINT));
            fillCompatibleTopology(state, regulator);
        } else if (block instanceof PneumaticReceiverBlock receiver) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(receiver, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(output.ordinal());
            fillCompatibleTopology(state, receiver);
        } else if (block instanceof PneumaticValveBlock valve) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(valve, state, output.getOpposite()));
            secondary.set(providerValue(valve, state, output));
            tertiary.set(state.getValue(PneumaticValveBlock.OPEN) ? 1 : 0);
            facing.set(output.ordinal());
            fillCompatibleTopology(state, valve);
        } else if (block instanceof PneumaticCheckValveBlock valve) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(valve, state, output.getOpposite()));
            secondary.set(providerValue(valve, state, output));
            facing.set(output.ordinal());
            fillCompatibleTopology(state, valve);
        } else if (block instanceof PneumaticFlowMeterBlock meter) {
            primary.set(PneumaticFlowMeterBlock.flowProxy(level, blockPos));
            secondary.set(PneumaticFlowMeterBlock.pressureDrop(level, blockPos));
            tertiary.set(PneumaticFlowMeterBlock.inletPressure(level, blockPos));
            driverCount.set(PneumaticFlowMeterBlock.outletPressure(level, blockPos));
            var measurement = PneumaticFlowMeterBlock.measurement(level, blockPos);
            dataValid.set(measurement.sampleCount() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalDomainBlock.FACING).ordinal());
            fillCompatibleTopology(state, meter);
        } else if (block instanceof PneumaticProportionalValveBlock valve) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(valve, state, output.getOpposite()));
            secondary.set(providerValue(valve, state, output));
            tertiary.set(PneumaticProportionalValveBlock.opening(level, blockPos));
            driverCount.set(PneumaticNetwork.pressure(level, blockPos));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(output.ordinal());
            fillCompatibleTopology(state, valve);
        } else if (block instanceof PneumaticReliefValveBlock valve) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(valve, state, output.getOpposite()));
            secondary.set(providerValue(valve, state, output));
            tertiary.set(state.getValue(PneumaticReliefValveBlock.SETPOINT) * 25);
            driverCount.set(PneumaticReliefValveBlock.ventEvents(level, blockPos));
            dataValid.set(PneumaticReliefValveBlock.venting(level, blockPos) ? 0 : 1);
            quality.set(dataValid.get() != 0 ? 100 : 60);
            facing.set(output.ordinal());
            fillCompatibleTopology(state, valve);
        } else if (block instanceof PneumaticCylinderBlock cylinder) {
            primary.set(PneumaticCylinderBlock.pressure(level, blockPos));
            secondary.set(PneumaticCylinderBlock.position(level, blockPos));
            tertiary.set(PneumaticCylinderBlock.target(level, blockPos));
            driverCount.set(PneumaticCylinderBlock.travel(level, blockPos));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalDomainBlock.FACING).ordinal());
            fillCompatibleTopology(state, cylinder);
        } else if (block instanceof ElectromagnetBlock magnet) {
            primary.set(state.getValue(ElectromagnetBlock.FIELD));
            secondary.set(dev.redstoneengineering.physics.MagneticPhysics.adjacentCopperLevel(level, blockPos));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, magnet);
        } else if (block instanceof PermanentMagnetBlock) {
            primary.set(state.getValue(PermanentMagnetBlock.STRENGTH));
            facing.set(state.getValue(PermanentMagnetBlock.FACING).ordinal());
        } else if (block instanceof InductionCoilBlock coil) {
            primary.set(dev.redstoneengineering.physics.MagneticPhysics.fieldAt(level, blockPos, 6));
            secondary.set(InductionCoilBlock.outputVoltage(level, blockPos));
            tertiary.set(state.getValue(InductionCoilBlock.TURNS));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalDomainBlock.FACING).ordinal());
            fillCompatibleTopology(state, coil);
        } else if (block instanceof MagneticFieldSensorBlock) {
            primary.set(state.getValue(MagneticFieldSensorBlock.FIELD));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
        } else if (block instanceof MagneticGradientMeterBlock) {
            primary.set(dev.redstoneengineering.physics.MagneticPhysics.fieldAt(level, blockPos, 6));
            secondary.set(MagneticGradientMeterBlock.gradientX(level, blockPos));
            tertiary.set(MagneticGradientMeterBlock.gradientY(level, blockPos));
            driverCount.set(MagneticGradientMeterBlock.gradientZ(level, blockPos));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
        } else if (block instanceof OpticalFiberBlock fiber) {
            primary.set(OpticalFiberBlock.intensity(level, blockPos));
            secondary.set(OpticalFiberBlock.channel(level, blockPos));
            dataValid.set(OpticalFiberBlock.valid(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCableTopology(state, fiber);
        } else if (block instanceof OpticalEmitterBlock emitter) {
            primary.set(state.getValue(OpticalEmitterBlock.INTENSITY));
            secondary.set(state.getValue(OpticalEmitterBlock.CHANNEL));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, emitter);
        } else if (block instanceof OpticalReceiverBlock receiver) {
            primary.set(OpticalReceiverBlock.intensity(level, blockPos));
            secondary.set(OpticalReceiverBlock.channel(level, blockPos));
            dataValid.set(OpticalReceiverBlock.valid(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, receiver);
        } else if (block instanceof OpticalPowerMeterBlock meter) {
            var sample = DomainNetwork.sampleOptical(level, blockPos.relative(state.getValue(OpticalPowerMeterBlock.FACING)));
            primary.set(sample.intensity());
            secondary.set(sample.channel());
            dataValid.set(sample.valid() ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(OpticalPowerMeterBlock.FACING).ordinal());
            fillCompatibleTopology(state, meter);
        } else if (block instanceof OpticalSplitterBlock splitter) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(splitter, state, output.getOpposite()));
            secondary.set(providerValue(splitter, state, output));
            tertiary.set(providerValue(splitter, state, DirectionalDomainBlock.leftOf(output)));
            facing.set(output.ordinal());
            fillCompatibleTopology(state, splitter);
        } else if (block instanceof OpticalChannelFilterBlock filter) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(filter, state, output.getOpposite()));
            secondary.set(providerValue(filter, state, output));
            tertiary.set(state.getValue(OpticalChannelFilterBlock.TARGET));
            facing.set(output.ordinal());
            fillCompatibleTopology(state, filter);
        } else if (block instanceof OpticalAttenuatorBlock attenuator) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(attenuator, state, output.getOpposite()));
            secondary.set(providerValue(attenuator, state, output));
            tertiary.set(state.getValue(OpticalAttenuatorBlock.LOSS));
            facing.set(output.ordinal());
            fillCompatibleTopology(state, attenuator);
        } else if (block instanceof OpticalFiberJunctionBlock junction) {
            primary.set(OpticalFiberJunctionBlock.intensity(level, blockPos));
            secondary.set(OpticalFiberJunctionBlock.channel(level, blockPos));
            dataValid.set(OpticalFiberJunctionBlock.valid(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCableTopology(state, junction);
        } else if (block instanceof EdgeDetectorBlock detector) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(detector, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set(state.getValue(EdgeDetectorBlock.MODE));
            driverCount.set(EdgeDetectorBlock.pulseRemaining(level, blockPos));
            dataValid.set(EdgeDetectorBlock.initialized(level, blockPos) ? 1 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof PulseShaperBlock shaper) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(shaper, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set(state.getValue(PulseShaperBlock.WIDTH));
            driverCount.set(PulseShaperBlock.pulseRemaining(level, blockPos));
            dataValid.set(PulseShaperBlock.initialized(level, blockPos) ? 1 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof SignalTapBlock tap) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(tap, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            facing.set(output.ordinal());
            fillCompatibleTopology(state, tap);
        } else if (block instanceof RangeSensorBlock sensor) {
            primary.set(RangeSensorBlock.detectedDistance(level, blockPos, state));
            secondary.set(state.getValue(RangeSensorBlock.OUTPUT));
            tertiary.set(RangeSensorBlock.configuredRange(state));
            driverCount.set(state.getValue(RangeSensorBlock.RESPONSE));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(RangeSensorBlock.sensingSide(state).ordinal());
        } else if (block instanceof LapisSignalLineBlock line) {
            primary.set(LapisSignalLineBlock.value(level, blockPos));
            dataValid.set(LapisSignalLineBlock.valid(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, line);
        } else if (block instanceof LapisPrecisionSourceBlock source) {
            primary.set(state.getValue(LapisPrecisionSourceBlock.VALUE));
            fillCompatibleTopology(state, source);
        } else if (block instanceof QuartzTimingLineBlock line) {
            primary.set(QuartzTimingLineBlock.active(level, blockPos) ? 1 : 0);
            secondary.set(QuartzTimingLineBlock.period(level, blockPos));
            dataValid.set(QuartzTimingLineBlock.valid(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, line);
        } else if (block instanceof QuartzOscillatorBlock oscillator) {
            primary.set(state.getValue(QuartzOscillatorBlock.ACTIVE) ? 1 : 0);
            secondary.set(QuartzTimingLineBlock.periodTicks(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX)));
            tertiary.set(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX));
            fillCompatibleTopology(state, oscillator);
        } else if (block instanceof SignalProbeBlock probe) {
            primary.set(probe.sample(level, blockPos, state));
            secondary.set(state.getValue(SignalProbeBlock.CHANNEL));
            facing.set(state.getValue(SignalProbeBlock.FACING).ordinal());
        } else if (block instanceof PrecisionFilterBlock filter) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(filter, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set(state.getValue(PrecisionFilterBlock.RATE));
            facing.set(output.ordinal());
        } else if (block instanceof RedstoneReferenceSourceBlock) {
            primary.set(state.getValue(RedstoneReferenceSourceBlock.POWER));
            facing.set(state.getValue(DirectionalRedstoneEndpointBlock.FACING).ordinal());
        } else if (block instanceof RedstoneCableTerminalBlock terminal) {
            primary.set(state.getValue(RedstoneCableTerminalBlock.POWER));
            secondary.set(terminal.externalInput(level, blockPos, state));
            tertiary.set(state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE) ? 1 : 0);
            facing.set(state.getValue(RedstoneCableTerminalBlock.FACING).ordinal());
        } else if (block instanceof RedstoneSignalCableBlock cable) {
            primary.set(RedstoneSignalCableBlock.power(level, blockPos));
            fillCableTopology(state, cable);
        } else if (block instanceof RedstoneCableJunctionBlock junction) {
            primary.set(RedstoneCableJunctionBlock.power(level, blockPos));
            fillCableTopology(state, junction);
        } else if (block instanceof ShieldedInstrumentCableBlock cable) {
            fillCableTopology(state, cable);
            InstrumentShieldingAudit.ShieldingSnapshot shielding = InstrumentShieldingAudit.inspect(level, blockPos);
            primary.set(shielding.coveragePercent());
            secondary.set(shielding.shieldedNodes());
            tertiary.set(shielding.unshieldedNodes());
            dataValid.set(shielding.bounded() ? 1 : 0);
            quality.set(shielding.bounded() ? shielding.coveragePercent() : 0);
            driverCount.set(shielding.cableNodes());
        } else if (block instanceof InstrumentCableBlock cable) {
            fillCableTopology(state, cable);
        } else if (block instanceof EightBitDataBusBlock bus) {
            primary.set(DataBusNetwork.sample(level, blockPos));
            DataBusNetwork.Diagnostics diagnostics = DataBusNetwork.getDiagnostics(level, blockPos);
            dataValid.set(DataBusNetwork.valid(level, blockPos) ? 1 : 0);
            quality.set(DataBusNetwork.valid(level, blockPos) ? 100 : 0);
            driverCount.set(diagnostics.driverCount());
            fillCompatibleTopology(state, bus);
        } else if (block instanceof RedstoneByteEncoderBlock encoder) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(encoder, state, output.getOpposite()));
            secondary.set(InformationRuntime.value(level, "bus8_out", blockPos) & 0xFF);
            dataValid.set(InformationRuntime.valid(level, "bus8_out", blockPos) ? 1 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof ByteToRedstoneDecoderBlock decoder) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(decoder, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(DataBusNetwork.valid(level, blockPos.relative(output.getOpposite())) ? 1 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof SerialDataLineBlock line) {
            primary.set(InformationRuntime.value(level, "serial", blockPos) & 0xFF);
            secondary.set(Math.max(1, InformationRuntime.aux(level, "serial", blockPos)));
            dataValid.set(InformationRuntime.valid(level, "serial", blockPos) ? 1 : 0);
            quality.set(boundedQuality("serial", blockPos));
            fillCompatibleTopology(state, line);
        } else if (block instanceof SerializerBlock serializer) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(serializer, state, output.getOpposite()));
            secondary.set(InformationRuntime.value(level, "serial", blockPos) & 0xFF);
            tertiary.set(Math.max(1, InformationRuntime.aux(level, "serial", blockPos)));
            dataValid.set(InformationRuntime.valid(level, "serial", blockPos) ? 1 : 0);
            quality.set(boundedQuality("serial", blockPos));
            facing.set(output.ordinal());
        } else if (block instanceof DeserializerBlock) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            BlockPos inputPos = blockPos.relative(output.getOpposite());
            primary.set(InformationRuntime.value(level, "serial", inputPos) & 0xFF);
            secondary.set(InformationRuntime.value(level, "bus8_out", blockPos) & 0xFF);
            tertiary.set(Math.max(1, InformationRuntime.aux(level, "serial", inputPos)));
            dataValid.set(InformationRuntime.valid(level, "bus8_out", blockPos) ? 1 : 0);
            quality.set(boundedQuality("serial", inputPos));
            facing.set(output.ordinal());
        } else if (block instanceof DifferentialDataPairBlock pair) {
            primary.set(InformationRuntime.value(level, "diff", blockPos) & 1);
            dataValid.set(InformationRuntime.valid(level, "diff", blockPos) ? 1 : 0);
            quality.set(boundedQuality("diff", blockPos));
            fillCompatibleTopology(state, pair);
        } else if (block instanceof DigitalRegeneratorBlock) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            BlockPos inputPos = blockPos.relative(output.getOpposite());
            primary.set(boundedQuality("serial", inputPos));
            secondary.set(InformationRuntime.value(level, "serial", blockPos) & 0xFF);
            tertiary.set(state.getValue(DigitalRegeneratorBlock.THRESHOLD));
            dataValid.set(InformationRuntime.valid(level, "serial", blockPos) ? 1 : 0);
            quality.set(boundedQuality("serial", blockPos));
            facing.set(output.ordinal());
        } else if (block instanceof DifferentialDriverBlock driver) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(driver, state, output.getOpposite()));
            secondary.set(InformationRuntime.value(level, "diff_out", blockPos) & 1);
            dataValid.set(InformationRuntime.valid(level, "diff_out", blockPos) ? 1 : 0);
            quality.set(boundedQuality("diff_out", blockPos));
            facing.set(output.ordinal());
        } else if (block instanceof DifferentialReceiverBlock) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            BlockPos inputPos = blockPos.relative(output.getOpposite());
            primary.set(InformationRuntime.value(level, "diff", inputPos) & 1);
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(InformationRuntime.valid(level, "diff", inputPos) ? 1 : 0);
            quality.set(boundedQuality("diff", inputPos));
            facing.set(output.ordinal());
        } else if (block instanceof RadioTransmitterBlock transmitter) {
            int payload = providerValue(transmitter, state, Direction.UP);
            primary.set(payload);
            secondary.set(state.getValue(RadioTransmitterBlock.CHANNEL));
            tertiary.set(RadioKernel.RANGE);
            dataValid.set(payload > 0 ? 1 : 0);
            quality.set(payload > 0 ? 100 : 0);
            driverCount.set(payload > 0 ? 1 : 0);
        } else if (block instanceof RadioReceiverBlock) {
            var reception = RadioKernel.receivePacket(level, blockPos, state.getValue(RadioReceiverBlock.CHANNEL));
            primary.set(reception.value());
            secondary.set(state.getValue(RadioReceiverBlock.CHANNEL));
            tertiary.set(reception.latencyTicks());
            dataValid.set(reception.valid() ? 1 : 0);
            quality.set(Math.max(0, Math.min(100, reception.quality())));
            driverCount.set(reception.drivers());
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof FreeSpaceOpticalTransmitterBlock transmitter) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            primary.set(providerValue(transmitter, state, output.getOpposite()));
            secondary.set(state.getValue(FreeSpaceOpticalTransmitterBlock.CHANNEL));
            tertiary.set(providerValue(transmitter, state, output));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(primary.get() > 0 ? 100 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof FreeSpaceOpticalReceiverBlock receiver) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(receiver, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            tertiary.set(state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL));
            boolean valid = InformationRuntime.valid(level, "free_optical", blockPos)
                    && InformationRuntime.aux(level, "free_optical", blockPos) == state.getValue(FreeSpaceOpticalReceiverBlock.CHANNEL);
            dataValid.set(valid ? 1 : 0);
            quality.set(boundedQuality("free_optical", blockPos));
            facing.set(output.ordinal());
        } else if (block instanceof QuartzClockDividerBlock) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            var input = DomainNetwork.sampleQuartz(level, blockPos.relative(output.getOpposite()));
            var divided = DomainNetwork.sampleQuartz(level, blockPos.relative(output));
            primary.set(input.periodTicks());
            secondary.set(divided.periodTicks());
            tertiary.set(QuartzClockDividerBlock.division(state.getValue(QuartzClockDividerBlock.DIV_INDEX)));
            dataValid.set(divided.valid() ? 1 : 0);
            quality.set(divided.valid() ? 100 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof QuartzStabilityMonitorBlock monitor) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            var input = DomainNetwork.sampleQuartz(level, blockPos.relative(output.getOpposite()));
            primary.set(monitor.measuredPeriod(level, blockPos));
            secondary.set(monitor.nominalError(level, blockPos));
            tertiary.set(input.periodTicks());
            dataValid.set(input.valid() && primary.get() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof AmethystResonatorBlock) {
            primary.set(state.getValue(AmethystResonatorBlock.FREQUENCY));
            secondary.set(state.getValue(AmethystResonatorBlock.AMPLITUDE));
            dataValid.set(AmethystResonatorBlock.isActive(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
        } else if (block instanceof AmethystResonanceDustBlock dust) {
            primary.set(AmethystResonanceDustBlock.frequency(level, blockPos));
            secondary.set(AmethystResonanceDustBlock.amplitude(level, blockPos));
            dataValid.set(AmethystResonanceDustBlock.active(level, blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            fillCompatibleTopology(state, dust);
        } else if (block instanceof AmethystFrequencyFilterBlock) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            var input = DomainNetwork.sampleAmethyst(level, blockPos.relative(output.getOpposite()));
            var filtered = DomainNetwork.sampleAmethyst(level, blockPos.relative(output));
            primary.set(input.frequency());
            secondary.set(filtered.amplitude());
            tertiary.set(state.getValue(AmethystFrequencyFilterBlock.TARGET));
            dataValid.set(filtered.active() ? 1 : 0);
            quality.set(filtered.active() ? 100 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof AmethystTunedResonatorBlock) {
            Direction output = state.getValue(DirectionalDomainBlock.FACING);
            var resonant = DomainNetwork.sampleAmethyst(level, blockPos.relative(output));
            primary.set(state.getValue(AmethystTunedResonatorBlock.NATURAL));
            secondary.set(resonant.amplitude());
            tertiary.set(state.getValue(AmethystTunedResonatorBlock.Q_INDEX));
            dataValid.set(resonant.active() ? 1 : 0);
            quality.set(resonant.active() ? 100 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof AmethystSpectrumAnalyzerBlock) {
            var spectrum = AmethystSpectrumAnalyzerBlock.spectrum(level, blockPos);
            primary.set(spectrum.dominantFrequency());
            secondary.set(spectrum.energy());
            tertiary.set(spectrum.activeBands());
            driverCount.set(spectrum.samples());
            dataValid.set(spectrum.samples() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
        } else if (block instanceof MechanicalExciterBlock) {
            primary.set(InformationRuntime.value(level, "mech_exciter", blockPos));
            secondary.set(state.getValue(MechanicalExciterBlock.FREQUENCY));
            dataValid.set(InformationRuntime.valid(level, "mech_exciter", blockPos) ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
        } else if (block instanceof SlimeVibrationConduitBlock conduit) {
            readMechanicalWave(conduit, state);
        } else if (block instanceof MechanicalVibrationReceiverBlock) {
            VibrationNetwork.Wave wave = VibrationNetwork.sample(level, blockPos);
            primary.set(wave.amplitude());
            secondary.set(wave.frequency());
            tertiary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(wave.valid() ? 1 : 0);
            quality.set(boundedQuality("mech_wave", blockPos));
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof HoneyVibrationDamperBlock damper) {
            readMechanicalWave(damper, state);
            tertiary.set(4);
        } else if (block instanceof SculkVibrationInterfaceBlock sculk) {
            primary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            secondary.set(sculk.eventCount(level, blockPos));
            tertiary.set(sculk.lastEventCode(level, blockPos));
            driverCount.set(sculk.transitionCount(level, blockPos));
            dataValid.set(primary.get() > 0 ? 1 : 0);
            quality.set(primary.get() > 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof HydroacousticTubeBlock tube) {
            primary.set(InformationRuntime.value(level, "hydro", blockPos));
            secondary.set(InformationRuntime.aux(level, "hydro", blockPos));
            tertiary.set(state.getValue(HydroacousticTubeBlock.MEDIUM));
            dataValid.set(InformationRuntime.valid(level, "hydro", blockPos) ? 1 : 0);
            quality.set(boundedQuality("hydro", blockPos));
            fillCompatibleTopology(state, tube);
        } else if (block instanceof HydroacousticExciterBlock) {
            primary.set(InformationRuntime.value(level, "hydro_exciter", blockPos));
            secondary.set(state.getValue(HydroacousticExciterBlock.FREQUENCY));
            dataValid.set(InformationRuntime.valid(level, "hydro_exciter", blockPos) ? 1 : 0);
            quality.set(boundedQuality("hydro_exciter", blockPos));
        } else if (block instanceof HydroacousticReceiverBlock) {
            primary.set(InformationRuntime.value(level, "hydro", blockPos));
            secondary.set(InformationRuntime.aux(level, "hydro", blockPos));
            tertiary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(InformationRuntime.valid(level, "hydro", blockPos) ? 1 : 0);
            quality.set(boundedQuality("hydro", blockPos));
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof PhononConduitBlock conduit) {
            primary.set(InformationRuntime.value(level, "thermal_pulse", blockPos));
            secondary.set(InformationRuntime.aux(level, "thermal_pulse", blockPos));
            dataValid.set(InformationRuntime.valid(level, "thermal_pulse", blockPos) ? 1 : 0);
            quality.set(boundedQuality("thermal_pulse", blockPos));
            fillCompatibleTopology(state, conduit);
        } else if (block instanceof ThermalPulseEncoderBlock) {
            primary.set(InformationRuntime.value(level, "thermal_encoder", blockPos));
            dataValid.set(InformationRuntime.valid(level, "thermal_encoder", blockPos) ? 1 : 0);
            quality.set(boundedQuality("thermal_encoder", blockPos));
        } else if (block instanceof ThermalPulseReceiverBlock) {
            primary.set(InformationRuntime.value(level, "thermal_pulse", blockPos));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            dataValid.set(InformationRuntime.valid(level, "thermal_pulse", blockPos) ? 1 : 0);
            quality.set(boundedQuality("thermal_pulse", blockPos));
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof WatchdogBlock) {
            primary.set(WatchdogBlock.ageTicks(level, blockPos));
            secondary.set(WatchdogBlock.timeoutTicks(state.getValue(WatchdogBlock.TIMEOUT)));
            tertiary.set(WatchdogBlock.timeoutCount(level, blockPos));
            driverCount.set(WatchdogBlock.transitionCount(level, blockPos));
            dataValid.set(state.getValue(DirectionalSignalBlock.OUTPUT) == 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof ServoActuatorBlock) {
            primary.set(ServoActuatorBlock.position(level, blockPos));
            secondary.set(ServoActuatorBlock.command(level, blockPos));
            tertiary.set(ServoActuatorBlock.velocity(level, blockPos));
            driverCount.set(ServoActuatorBlock.softLimitHits(level, blockPos));
            dataValid.set(ServoActuatorBlock.braking(level, blockPos) ? 0 : 1);
            quality.set(ServoActuatorBlock.braking(level, blockPos) ? 50 : 100);
            facing.set(state.getValue(ServoActuatorBlock.FACING).ordinal());
        } else if (block instanceof ServoPositionSensorBlock sensor) {
            Direction output = state.getValue(DirectionalSignalBlock.FACING);
            primary.set(providerValue(sensor, state, output.getOpposite()));
            secondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            var measurement = ServoPositionSensorBlock.measurement(level, blockPos);
            tertiary.set((int) Math.min(Integer.MAX_VALUE, measurement.sampleCount()));
            dataValid.set(measurement.sampleCount() > 0 ? 1 : 0);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(output.ordinal());
        } else if (block instanceof RedundantVoterBlock) {
            primary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            secondary.set(RedundantVoterBlock.spread(level, blockPos));
            tertiary.set(RedundantVoterBlock.toleranceValue(state.getValue(RedundantVoterBlock.TOLERANCE)));
            driverCount.set(RedundantVoterBlock.disagreementCount(level, blockPos));
            dataValid.set(RedundantVoterBlock.degraded(level, blockPos) ? 0 : 1);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof FaultLatchBlock) {
            primary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            secondary.set(FaultLatchBlock.tripCount(level, blockPos));
            tertiary.set(FaultLatchBlock.resetCount(level, blockPos));
            driverCount.set(FaultLatchBlock.resetActive(level, blockPos) ? 1 : 0);
            dataValid.set(FaultLatchBlock.latched(level, blockPos) ? 0 : 1);
            quality.set(dataValid.get() != 0 ? 100 : 0);
            facing.set(state.getValue(DirectionalSignalBlock.FACING).ordinal());
        } else if (block instanceof OperationsMonitorBlock) {
            primary.set(OperationsMonitorBlock.queueNow(level, blockPos));
            secondary.set(OperationsMonitorBlock.throughputLastWindow(level, blockPos));
            tertiary.set(OperationsMonitorBlock.downtimeTicks(level, blockPos));
            driverCount.set(OperationsMonitorBlock.stateOrdinal(level, blockPos));
            dataValid.set(driverCount.get() == OperationsMonitorBlock.SystemState.FAILED.ordinal() ? 0 : 1);
            quality.set(dataValid.get() != 0 ? 100 : 0);
        }
    }

    private void readMechanicalWave(EngineeringPortProvider provider, BlockState state) {
        VibrationNetwork.Wave wave = VibrationNetwork.sample(level, blockPos);
        primary.set(wave.amplitude());
        secondary.set(wave.frequency());
        dataValid.set(wave.valid() ? 1 : 0);
        quality.set(boundedQuality("mech_wave", blockPos));
        fillCompatibleTopology(state, provider);
    }

    private int boundedQuality(String channel, BlockPos pos) {
        return Math.max(0, Math.min(100, InformationRuntime.quality(level, channel, pos)));
    }

    private int providerValue(EngineeringPortProvider provider, BlockState state, Direction side) {
        return provider.engineeringSnapshot(level, blockPos, state, side)
                .map(snapshot -> (int) Math.round(snapshot.value())).orElse(0);
    }

    private void fillCableTopology(BlockState state, ConnectedCableBlock cable) {
        connectionCount.set(ConnectedCableBlock.connectionCount(state));
        topologyValid.set(cable.topologyValid(state) ? 1 : 0);
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (ConnectedCableBlock.connected(state, direction)) mask |= 1 << direction.ordinal();
        }
        connectionMask.set(mask);
    }

    private void fillCompatibleTopology(BlockState state, EngineeringPortProvider provider) {
        int mask = 0;
        int issues = 0;
        for (Direction direction : Direction.values()) {
            var localPort = provider.engineeringPort(state, direction);
            if (localPort.isEmpty()) continue;
            BlockState neighborState = level.getBlockState(blockPos.relative(direction));
            if (!(neighborState.getBlock() instanceof EngineeringPortProvider neighborProvider)) continue;
            var neighborPort = neighborProvider.engineeringPort(neighborState, direction.getOpposite());
            if (neighborPort.isEmpty()) continue;
            PortCompatibility.Result compatibility = PortCompatibility.evaluate(localPort.get(), neighborPort.get());
            if (compatibility.compatible()) mask |= 1 << direction.ordinal();
            else issues++;
        }
        connectionMask.set(mask);
        connectionCount.set(Integer.bitCount(mask));
        topologyValid.set(issues == 0 ? 1 : 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        boolean changed = false;

        if (block instanceof SignalProbeBlock) {
            int channel = state.getValue(SignalProbeBlock.CHANNEL);
            if (id == BUTTON_PRIMARY_DECREASE) channel = Math.floorMod(channel - 1, 4);
            else if (id == BUTTON_PRIMARY_INCREASE) channel = (channel + 1) % 4;
            else return false;
            level.setBlock(blockPos, state.setValue(SignalProbeBlock.CHANNEL, channel), Block.UPDATE_CLIENTS);
            changed = true;
        } else if (block instanceof PrecisionFilterBlock filter) {
            int rate = state.getValue(PrecisionFilterBlock.RATE);
            if (id == BUTTON_PRIMARY_DECREASE) rate = rate <= 1 ? 4 : rate - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) rate = rate >= 4 ? 1 : rate + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(PrecisionFilterBlock.RATE, rate), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, filter, 1);
            changed = true;
        } else if (block instanceof RedstoneReferenceSourceBlock source) {
            int value = state.getValue(RedstoneReferenceSourceBlock.POWER);
            if (id == BUTTON_PRIMARY_DECREASE) value = value <= 0 ? 15 : value - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) value = value >= 15 ? 0 : value + 1;
            else if (id == BUTTON_PRESET_0) value = 0;
            else if (id == BUTTON_PRESET_5) value = 5;
            else if (id == BUTTON_PRESET_10) value = 10;
            else if (id == BUTTON_PRESET_15) value = 15;
            else return false;
            BlockState next = state.setValue(RedstoneReferenceSourceBlock.POWER, value);
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            Direction front = next.getValue(DirectionalRedstoneEndpointBlock.FACING);
            level.updateNeighborsAt(blockPos, source);
            level.updateNeighborsAt(blockPos.relative(front), source);
            changed = true;
        } else if (block instanceof RedstoneCableTerminalBlock terminal) {
            if (id != BUTTON_TOGGLE) return false;
            BlockState next = state.setValue(RedstoneCableTerminalBlock.OUTPUT_MODE,
                    !state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE));
            level.setBlock(blockPos, next, Block.UPDATE_CLIENTS);
            if (level instanceof net.minecraft.server.level.ServerLevel server) RedstoneCableNetwork.recompute(server, blockPos);
            level.updateNeighborsAt(blockPos, terminal);
            level.updateNeighborsAt(blockPos.relative(terminal.vanillaSide(next)), terminal);
            changed = true;
        } else if (block instanceof DigitalRegeneratorBlock regenerator) {
            int threshold = state.getValue(DigitalRegeneratorBlock.THRESHOLD);
            if (id == BUTTON_PRIMARY_DECREASE) threshold = Math.floorMod(threshold - 1, 3);
            else if (id == BUTTON_PRIMARY_INCREASE) threshold = (threshold + 1) % 3;
            else return false;
            level.setBlock(blockPos, state.setValue(DigitalRegeneratorBlock.THRESHOLD, threshold), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, regenerator, 1);
            changed = true;
        } else if (block instanceof PressureRegulatorBlock regulator) {
            int setpoint = state.getValue(PressureRegulatorBlock.SETPOINT);
            if (id == BUTTON_PRIMARY_DECREASE) setpoint = setpoint <= 1 ? 4 : setpoint - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) setpoint = setpoint >= 4 ? 1 : setpoint + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(PressureRegulatorBlock.SETPOINT, setpoint), Block.UPDATE_CLIENTS);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                PneumaticNetwork.recompute(server, blockPos);
            }
            changed = true;
        } else if (block instanceof PneumaticValveBlock valve) {
            if (id != BUTTON_TOGGLE) return false;
            level.setBlock(blockPos, state.setValue(PneumaticValveBlock.OPEN,
                    !state.getValue(PneumaticValveBlock.OPEN)), Block.UPDATE_CLIENTS);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                PneumaticNetwork.recomputeAround(server, blockPos);
            }
            changed = true;
        } else if (block instanceof PneumaticReliefValveBlock) {
            int setpoint = state.getValue(PneumaticReliefValveBlock.SETPOINT);
            if (id == BUTTON_PRIMARY_DECREASE) setpoint = setpoint <= 1 ? 4 : setpoint - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) setpoint = setpoint >= 4 ? 1 : setpoint + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(PneumaticReliefValveBlock.SETPOINT, setpoint), Block.UPDATE_CLIENTS);
            if (level instanceof net.minecraft.server.level.ServerLevel server) PneumaticNetwork.recomputeAround(server, blockPos);
            changed = true;
        } else if (block instanceof PermanentMagnetBlock) {
            int strength = state.getValue(PermanentMagnetBlock.STRENGTH);
            if (id == BUTTON_PRIMARY_DECREASE) strength = strength <= 1 ? 15 : strength - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) strength = strength >= 15 ? 1 : strength + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(PermanentMagnetBlock.STRENGTH, strength), Block.UPDATE_CLIENTS);
            changed = true;
        } else if (block instanceof InductionCoilBlock coil) {
            int turns = state.getValue(InductionCoilBlock.TURNS);
            if (id == BUTTON_PRIMARY_DECREASE) turns = turns <= 1 ? 4 : turns - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) turns = turns >= 4 ? 1 : turns + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(InductionCoilBlock.TURNS, turns), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, coil, 1);
            changed = true;
        } else if (block instanceof OpticalEmitterBlock) {
            int value = state.getValue(OpticalEmitterBlock.INTENSITY);
            if (id == BUTTON_PRIMARY_DECREASE) value = value <= 0 ? 15 : value - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) value = value >= 15 ? 0 : value + 1;
            else if (id == BUTTON_PRESET_0) value = 0;
            else if (id == BUTTON_PRESET_5) value = 5;
            else if (id == BUTTON_PRESET_10) value = 10;
            else if (id == BUTTON_PRESET_15) value = 15;
            else return false;
            level.setBlock(blockPos, state.setValue(OpticalEmitterBlock.INTENSITY, value), Block.UPDATE_CLIENTS);
            if (level instanceof net.minecraft.server.level.ServerLevel server) DomainNetwork.recomputeOptical(server, blockPos);
            changed = true;
        } else if (block instanceof OpticalChannelFilterBlock filter) {
            int target = state.getValue(OpticalChannelFilterBlock.TARGET);
            if (id == BUTTON_PRIMARY_DECREASE) target = Math.floorMod(target - 1, 16);
            else if (id == BUTTON_PRIMARY_INCREASE) target = (target + 1) % 16;
            else return false;
            level.setBlock(blockPos, state.setValue(OpticalChannelFilterBlock.TARGET, target), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, filter, 1);
            changed = true;
        } else if (block instanceof OpticalAttenuatorBlock attenuator) {
            int loss = state.getValue(OpticalAttenuatorBlock.LOSS);
            if (id == BUTTON_PRIMARY_DECREASE) loss = loss <= 0 ? 8 : loss - 1;
            else if (id == BUTTON_PRIMARY_INCREASE) loss = loss >= 8 ? 0 : loss + 1;
            else return false;
            level.setBlock(blockPos, state.setValue(OpticalAttenuatorBlock.LOSS, loss), Block.UPDATE_CLIENTS);
            level.scheduleTick(blockPos, attenuator, 1);
            changed = true;
        }

        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private static int kindOf(Block block) {
        if (block instanceof AirCompressorBlock) return KIND_AIR_COMPRESSOR;
        if (block instanceof PneumaticPipeBlock) return KIND_PNEUMATIC_PIPE;
        if (block instanceof AirReservoirBlock) return KIND_AIR_RESERVOIR;
        if (block instanceof PressureRegulatorBlock) return KIND_PRESSURE_REGULATOR;
        if (block instanceof PneumaticReceiverBlock) return KIND_PNEUMATIC_RECEIVER;
        if (block instanceof PneumaticValveBlock) return KIND_PNEUMATIC_VALVE;
        if (block instanceof PneumaticCheckValveBlock) return KIND_PNEUMATIC_CHECK_VALVE;
        if (block instanceof PneumaticFlowMeterBlock) return KIND_PNEUMATIC_FLOW_METER;
        if (block instanceof PneumaticProportionalValveBlock) return KIND_PNEUMATIC_PROPORTIONAL_VALVE;
        if (block instanceof PneumaticReliefValveBlock) return KIND_PNEUMATIC_RELIEF_VALVE;
        if (block instanceof PneumaticCylinderBlock) return KIND_PNEUMATIC_CYLINDER;
        if (block instanceof ElectromagnetBlock) return KIND_ELECTROMAGNET;
        if (block instanceof PermanentMagnetBlock) return KIND_PERMANENT_MAGNET;
        if (block instanceof InductionCoilBlock) return KIND_INDUCTION_COIL;
        if (block instanceof MagneticFieldSensorBlock) return KIND_MAGNETIC_FIELD_SENSOR;
        if (block instanceof MagneticGradientMeterBlock) return KIND_MAGNETIC_GRADIENT_METER;
        if (block instanceof OpticalFiberBlock) return KIND_OPTICAL_FIBER;
        if (block instanceof OpticalEmitterBlock) return KIND_OPTICAL_EMITTER;
        if (block instanceof OpticalReceiverBlock) return KIND_OPTICAL_RECEIVER;
        if (block instanceof OpticalPowerMeterBlock) return KIND_OPTICAL_POWER_METER;
        if (block instanceof OpticalSplitterBlock) return KIND_OPTICAL_SPLITTER;
        if (block instanceof OpticalChannelFilterBlock) return KIND_OPTICAL_CHANNEL_FILTER;
        if (block instanceof OpticalAttenuatorBlock) return KIND_OPTICAL_ATTENUATOR;
        if (block instanceof OpticalFiberJunctionBlock) return KIND_OPTICAL_FIBER_JUNCTION;
        if (block instanceof EdgeDetectorBlock) return KIND_EDGE_DETECTOR;
        if (block instanceof PulseShaperBlock) return KIND_PULSE_SHAPER;
        if (block instanceof SignalTapBlock) return KIND_SIGNAL_TAP;
        if (block instanceof RangeSensorBlock) return KIND_RANGE_SENSOR;
        if (block instanceof LapisSignalLineBlock) return KIND_LAPIS_LINE;
        if (block instanceof LapisPrecisionSourceBlock) return KIND_LAPIS_SOURCE;
        if (block instanceof QuartzTimingLineBlock) return KIND_QUARTZ_LINE;
        if (block instanceof QuartzOscillatorBlock) return KIND_QUARTZ_OSCILLATOR;
        if (block instanceof SignalProbeBlock) return KIND_PROBE;
        if (block instanceof PrecisionFilterBlock) return KIND_FILTER;
        if (block instanceof RedstoneReferenceSourceBlock) return KIND_REFERENCE;
        if (block instanceof RedstoneCableTerminalBlock) return KIND_TERMINAL;
        if (block instanceof RedstoneSignalCableBlock) return KIND_REDSTONE_CABLE;
        if (block instanceof RedstoneCableJunctionBlock) return KIND_REDSTONE_JUNCTION;
        if (block instanceof ShieldedInstrumentCableBlock) return KIND_SHIELDED_INSTRUMENT_CABLE;
        if (block instanceof InstrumentCableBlock) return KIND_INSTRUMENT_CABLE;
        if (block instanceof EightBitDataBusBlock) return KIND_DATA_BUS_8;
        if (block instanceof RedstoneByteEncoderBlock) return KIND_ENCODER;
        if (block instanceof ByteToRedstoneDecoderBlock) return KIND_DECODER;
        if (block instanceof SerialDataLineBlock) return KIND_SERIAL_LINE;
        if (block instanceof SerializerBlock) return KIND_SERIALIZER;
        if (block instanceof DeserializerBlock) return KIND_DESERIALIZER;
        if (block instanceof DifferentialDataPairBlock) return KIND_DIFFERENTIAL_PAIR;
        if (block instanceof DigitalRegeneratorBlock) return KIND_DIGITAL_REGENERATOR;
        if (block instanceof DifferentialDriverBlock) return KIND_DIFFERENTIAL_DRIVER;
        if (block instanceof DifferentialReceiverBlock) return KIND_DIFFERENTIAL_RECEIVER;
        if (block instanceof RadioTransmitterBlock) return KIND_RADIO_TRANSMITTER;
        if (block instanceof RadioReceiverBlock) return KIND_RADIO_RECEIVER;
        if (block instanceof FreeSpaceOpticalTransmitterBlock) return KIND_FREE_OPTICAL_TRANSMITTER;
        if (block instanceof FreeSpaceOpticalReceiverBlock) return KIND_FREE_OPTICAL_RECEIVER;
        if (block instanceof QuartzClockDividerBlock) return KIND_QUARTZ_DIVIDER;
        if (block instanceof QuartzStabilityMonitorBlock) return KIND_QUARTZ_STABILITY;
        if (block instanceof AmethystResonatorBlock) return KIND_AMETHYST_RESONATOR;
        if (block instanceof AmethystResonanceDustBlock) return KIND_AMETHYST_DUST;
        if (block instanceof AmethystFrequencyFilterBlock) return KIND_AMETHYST_FILTER;
        if (block instanceof AmethystTunedResonatorBlock) return KIND_AMETHYST_TUNED;
        if (block instanceof AmethystSpectrumAnalyzerBlock) return KIND_AMETHYST_SPECTRUM;
        if (block instanceof MechanicalExciterBlock) return KIND_MECHANICAL_EXCITER;
        if (block instanceof SlimeVibrationConduitBlock) return KIND_SLIME_VIBRATION;
        if (block instanceof MechanicalVibrationReceiverBlock) return KIND_MECHANICAL_RECEIVER;
        if (block instanceof HoneyVibrationDamperBlock) return KIND_HONEY_DAMPER;
        if (block instanceof SculkVibrationInterfaceBlock) return KIND_SCULK_INTERFACE;
        if (block instanceof HydroacousticTubeBlock) return KIND_HYDRO_TUBE;
        if (block instanceof HydroacousticExciterBlock) return KIND_HYDRO_EXCITER;
        if (block instanceof HydroacousticReceiverBlock) return KIND_HYDRO_RECEIVER;
        if (block instanceof PhononConduitBlock) return KIND_PHONON_CONDUIT;
        if (block instanceof ThermalPulseEncoderBlock) return KIND_THERMAL_ENCODER;
        if (block instanceof ThermalPulseReceiverBlock) return KIND_THERMAL_RECEIVER;
        if (block instanceof WatchdogBlock) return KIND_WATCHDOG;
        if (block instanceof ServoActuatorBlock) return KIND_SERVO_ACTUATOR;
        if (block instanceof ServoPositionSensorBlock) return KIND_SERVO_POSITION_SENSOR;
        if (block instanceof RedundantVoterBlock) return KIND_REDUNDANT_VOTER;
        if (block instanceof FaultLatchBlock) return KIND_FAULT_LATCH;
        if (block instanceof OperationsMonitorBlock) return KIND_OPERATIONS_MONITOR;
        return KIND_UNKNOWN;
    }

    public int kind() { return kind.get(); }
    public int primary() { return primary.get(); }
    public int secondary() { return secondary.get(); }
    public int tertiary() { return tertiary.get(); }
    public int facingOrdinal() { return facing.get(); }
    public int portCount() { return portCount.get(); }
    public int connectionCount() { return connectionCount.get(); }
    public int connectionMask() { return connectionMask.get(); }
    public boolean topologyValid() { return topologyValid.get() != 0; }
    public boolean dataValid() { return dataValid.get() != 0; }
    public int qualityPercent() { return quality.get(); }
    public int driverCount() { return driverCount.get(); }
}
