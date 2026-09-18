package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Generic server-authoritative HMI for EngineeringPortProvider field devices. */
public final class UniversalFieldDeviceMenu extends EngineeringDeviceMenu {
    public static final int FACE_COUNT = Direction.values().length;
    public static final int BUTTON_ROTATE_LEFT = 100;
    public static final int BUTTON_ROTATE_RIGHT = 101;
    public static final int BUTTON_INPUT_LEFT = 102;
    public static final int BUTTON_INPUT_RIGHT = 103;
    public static final int BUTTON_OUTPUT_LEFT = 104;
    public static final int BUTTON_OUTPUT_RIGHT = 105;
    public static final int BUTTON_CONFIG_PRIMARY_PREVIOUS = 110;
    public static final int BUTTON_CONFIG_PRIMARY_NEXT = 111;
    public static final int BUTTON_CONFIG_SECONDARY_PREVIOUS = 112;
    public static final int BUTTON_CONFIG_SECONDARY_NEXT = 113;
    public static final int BUTTON_CONFIG_ACTION = 114;
    public static final int BUTTON_CONFIG_TOGGLE = 115;

    public static final int ROUTE_NONE = 0;
    public static final int ROUTE_SERIES_AXIS = 1;
    public static final int ROUTE_ENDPOINT_FRONT = 2;
    public static final int ROUTE_PROBE_AXIS = 3;
    public static final int ROUTE_TERMINAL_INTERFACE = 4;
    public static final int ROUTE_MEASUREMENT_FACE = 5;
    public static final int ROUTE_FIXED_APERTURE_OUTPUT_FRONT = 6;
    public static final int ROUTE_MULTI_PORT_LAYOUT = 7;

    public static final int CONFIG_NONE = 0;
    public static final int CONFIG_LAPIS_TRANSDUCER = 1;
    public static final int CONFIG_LAPIS_RANGE = 2;
    public static final int CONFIG_MOLECULAR_RECEIVER = 3;
    public static final int CONFIG_ALARM = 4;
    public static final int CONFIG_SAMPLE_HOLD = 5;
    public static final int CONFIG_CALIBRATION = 6;
    public static final int CONFIG_PWM = 7;
    public static final int CONFIG_FAULT_INJECTOR = 8;
    public static final int CONFIG_SEQUENCE_CONTROLLER = 9;
    public static final int CONFIG_SAFETY_INTERLOCK = 10;
    public static final int CONFIG_TOPOLOGY_DEBUGGER = 11;
    public static final int CONFIG_LIGHT_SENSOR = 12;
    public static final int CONFIG_TANK_LEVEL = 13;
    public static final int CONFIG_ENTITY_DENSITY = 14;
    public static final int CONFIG_MAGNETIC_FIELD = 15;
    public static final int CONFIG_ELECTROMAGNET = 16;
    public static final int CONFIG_IRON_CORE = 17;
    public static final int CONFIG_SIGNAL_PROBE = 18;
    public static final int CONFIG_REFERENCE_SOURCE = 19;
    public static final int CONFIG_REDSTONE_CABLE = 20;
    public static final int CONFIG_CABLE_TERMINAL = 21;
    public static final int CONFIG_QUARTZ_OSCILLATOR = 22;
    public static final int CONFIG_FAULT_LATCH = 23;
    public static final int CONFIG_ANALOG_INDICATOR = 24;
    public static final int CONFIG_JUNCTION = 25;
    public static final int CONFIG_BYTE_ENCODER = 26;
    public static final int CONFIG_BYTE_DECODER = 27;
    public static final int CONFIG_SERIALIZER = 28;
    public static final int CONFIG_DESERIALIZER = 29;
    public static final int CONFIG_SERIAL_LINE = 30;
    public static final int CONFIG_REGENERATOR = 31;
    public static final int CONFIG_DIFF_DRIVER = 32;
    public static final int CONFIG_DIFF_PAIR = 33;
    public static final int CONFIG_DIFF_RECEIVER = 34;
    public static final int CONFIG_DATA_BUS = 35;
    public static final int CONFIG_INSTRUMENT_BUS = 36;
    public static final int CONFIG_WATCHDOG = 37;
    public static final int CONFIG_QUARTZ_TRACE = 38;
    public static final int CONFIG_SINGLE_RELAY = 39;
    public static final int CONFIG_REDUNDANT_VOTER = 40;
    public static final int CONFIG_SIGNAL_AMPLIFIER = 41;
    public static final int CONFIG_QUARTZ_LAPIS_SAMPLER = 42;
    public static final int CONFIG_SIGNAL_TAP = 43;
    public static final int CONFIG_SIGNAL_SELECTOR = 44;
    public static final int CONFIG_ANALOG_COMPARATOR = 45;

    private final DataSlot facing = trackedInt();
    private final DataSlot routeKind = trackedInt();
    private final DataSlot configKind = trackedInt();
    private final DataSlot configPrimary = trackedInt();
    private final DataSlot configSecondary = trackedInt();
    private final DataSlot configTertiary = trackedInt();
    private final DataSlot configQuaternary = trackedInt();
    private final DataSlot declaredPortMask = trackedInt();
    private final DataSlot inputMask = trackedInt();
    private final DataSlot outputMask = trackedInt();
    private final DataSlot bidirectionalMask = trackedInt();
    private final DataSlot seriesRotatable = trackedInt();
    private final DataSlot[] domains = trackedInts(FACE_COUNT);
    private final DataSlot[] kinds = trackedInts(FACE_COUNT);
    private final DataSlot[] values = trackedInts(FACE_COUNT);
    private final DataSlot[] minimums = trackedInts(FACE_COUNT);
    private final DataSlot[] maximums = trackedInts(FACE_COUNT);
    private final DataSlot[] qualities = trackedInts(FACE_COUNT);

    public UniversalFieldDeviceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos());
    }

    public UniversalFieldDeviceMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(EngineeringUiRegistration.UNIVERSAL_FIELD_DEVICE.get(), containerId, inventory, pos,
                inventory.player.level().getBlockState(pos).getBlock());
        if (!level.isClientSide) refreshAuthoritativeSnapshot();
    }

    @Override
    protected void refreshAuthoritativeSnapshot() {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        facing.set(directionOrdinal(state));
        int route = routeKind(block);
        routeKind.set(route);
        seriesRotatable.set(isRotatable(block) ? 1 : 0);
        configKind.set(CONFIG_NONE);
        configPrimary.set(0);
        configSecondary.set(0);
        configTertiary.set(0);
        configQuaternary.set(0);

        if (block instanceof AnalogComparatorBlock) {
            configKind.set(CONFIG_ANALOG_COMPARATOR);
            configPrimary.set(state.getValue(AnalogComparatorBlock.MODE));
            configSecondary.set(state.getValue(AnalogComparatorBlock.HYSTERESIS));
            configTertiary.set(AnalogComparatorBlock.margin(level, blockPos, state));
            configQuaternary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
        } else if (block instanceof SignalSelectorBlock) {
            configKind.set(CONFIG_SIGNAL_SELECTOR);
            configPrimary.set(state.getValue(SignalSelectorBlock.INVERT_SELECT) ? 1 : 0);
            configSecondary.set(SignalSelectorBlock.selectedB(level, blockPos, state) ? 1 : 0);
            configTertiary.set(SignalSelectorBlock.switchCount(level, blockPos));
        } else if (block instanceof SignalTapBlock) {
            configKind.set(CONFIG_SIGNAL_TAP);
            configPrimary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            configSecondary.set(SignalTapBlock.seriesInputSide(state).ordinal());
            configTertiary.set(SignalTapBlock.seriesOutputSide(state).ordinal());
        } else if (block instanceof QuartzTriggeredLapisSamplerBlock) {
            configKind.set(CONFIG_QUARTZ_LAPIS_SAMPLER);
            configPrimary.set(QuartzTriggeredLapisSamplerBlock.heldValue(level, blockPos));
            configSecondary.set(QuartzTriggeredLapisSamplerBlock.acceptedCaptures(level, blockPos));
            configTertiary.set(QuartzTriggeredLapisSamplerBlock.rejectedCaptures(level, blockPos));
        } else if (block instanceof SignalAmplifierBlock) {
            configKind.set(CONFIG_SIGNAL_AMPLIFIER);
            configPrimary.set(state.getValue(SignalAmplifierBlock.GAIN_MODE));
            configSecondary.set(SignalAmplifierBlock.clipping(level, blockPos) ? 1 : 0);
            configTertiary.set(SignalAmplifierBlock.clippingEpisodes(level, blockPos));
        } else if (block instanceof RedundantVoterBlock) {
            configKind.set(CONFIG_REDUNDANT_VOTER);
            configPrimary.set(state.getValue(RedundantVoterBlock.TOLERANCE));
            configSecondary.set(RedundantVoterBlock.validInputs(level, blockPos));
            configTertiary.set(RedundantVoterBlock.spread(level, blockPos));
        } else if (block instanceof SingleRelayBlock) {
            configKind.set(CONFIG_SINGLE_RELAY);
            configPrimary.set(state.getValue(SingleRelayBlock.NORMALLY_CLOSED) ? 1 : 0);
            configSecondary.set(SingleRelayBlock.coilEnergized(level, blockPos, state) ? 1 : 0);
            configTertiary.set(SingleRelayBlock.switchCount(level, blockPos));
            configQuaternary.set(state.getValue(SingleRelayBlock.PICKUP_MODE));
        } else if (block instanceof QuartzTimingLineBlock) {
            configKind.set(CONFIG_QUARTZ_TRACE);
            configPrimary.set(QuartzTimingLineBlock.period(level, blockPos));
            configSecondary.set(QuartzTimingLineBlock.sourceCount(level, blockPos));
            configTertiary.set(QuartzTimingLineBlock.active(level, blockPos) ? 1 : 0);
        } else if (block instanceof InstrumentCableBlock) {
            configKind.set(CONFIG_INSTRUMENT_BUS);
            var bus = dev.redstoneengineering.instrument.InstrumentNetwork.scan(level, blockPos);
            configPrimary.set(bus.validChannels());
            configSecondary.set(bus.interferenceConfidencePercent());
            configTertiary.set(bus.shieldingCoveragePercent());
        } else if (block instanceof WatchdogBlock) {
            configKind.set(CONFIG_WATCHDOG);
            configPrimary.set(state.getValue(WatchdogBlock.TIMEOUT));
            configSecondary.set(WatchdogBlock.ageTicks(level, blockPos));
            configTertiary.set(WatchdogBlock.timeoutCount(level, blockPos));
        } else if (block instanceof EightBitDataBusBlock) {
            configKind.set(CONFIG_DATA_BUS);
            var diag = dev.redstoneengineering.physics.DataBusNetwork.getDiagnostics(level, blockPos);
            configPrimary.set(dev.redstoneengineering.physics.DataBusNetwork.sample(level, blockPos));
            configSecondary.set(diag.qualityPercent());
            configTertiary.set(diag.driverCount());
        } else if (block instanceof DifferentialDriverBlock) {
            configKind.set(CONFIG_DIFF_DRIVER);
            configPrimary.set(state.getValue(DifferentialDriverBlock.THRESHOLD));
            var input = dev.redstoneengineering.physics.RedstoneObservationSupport.observe(
                    level, blockPos, DirectionalDomainBlock.seriesInputSide(state));
            configSecondary.set(input.value());
            configTertiary.set(input.value() >= DifferentialDriverBlock.thresholdValue(state.getValue(DifferentialDriverBlock.THRESHOLD)) ? 1 : 0);
        } else if (block instanceof DifferentialDataPairBlock) {
            configKind.set(CONFIG_DIFF_PAIR);
            var diff = dev.redstoneengineering.physics.InformationRuntime.snapshot(level, "diff", blockPos);
            configPrimary.set(diff.value() & 1);
            configSecondary.set(diff.qualityPercent());
            configTertiary.set(dev.redstoneengineering.physics.DifferentialNetwork.driverCount(level, blockPos));
        } else if (block instanceof DifferentialReceiverBlock) {
            configKind.set(CONFIG_DIFF_RECEIVER);
            BlockPos input = blockPos.relative(DirectionalSignalBlock.seriesInputSide(state));
            var diff = dev.redstoneengineering.physics.InformationRuntime.snapshot(level, "diff", input);
            configPrimary.set(diff.value() & 1);
            configSecondary.set(diff.qualityPercent());
            configTertiary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
        } else if (block instanceof SerializerBlock) {
            configKind.set(CONFIG_SERIALIZER);
            configPrimary.set(state.getValue(SerializerBlock.PERIOD_MODE));
            var output = dev.redstoneengineering.physics.InformationRuntime.snapshot(level, "serial", blockPos);
            configSecondary.set(output.value() & 0xFF);
            configTertiary.set(SerializerBlock.wordPeriod(state));
        } else if (block instanceof DeserializerBlock) {
            configKind.set(CONFIG_DESERIALIZER);
            BlockPos input = blockPos.relative(DirectionalDomainBlock.seriesInputSide(state));
            var serial = dev.redstoneengineering.physics.InformationRuntime.snapshot(level, "serial", input);
            configPrimary.set(serial.value() & 0xFF);
            configSecondary.set(serial.selector());
            configTertiary.set(serial.qualityPercent());
        } else if (block instanceof SerialDataLineBlock) {
            configKind.set(CONFIG_SERIAL_LINE);
            var serial = dev.redstoneengineering.physics.InformationRuntime.snapshot(level, "serial", blockPos);
            var diag = dev.redstoneengineering.physics.SerialNetwork.getDiagnostics(level, blockPos);
            configPrimary.set(serial.value() & 0xFF);
            configSecondary.set(diag.qualityPercent());
            configTertiary.set(diag.utilizationPercent());
        } else if (block instanceof DigitalRegeneratorBlock) {
            configKind.set(CONFIG_REGENERATOR);
            configPrimary.set(state.getValue(DigitalRegeneratorBlock.THRESHOLD));
            configSecondary.set(DigitalRegeneratorBlock.acceptedCount(level, blockPos));
            configTertiary.set(DigitalRegeneratorBlock.rejectedCount(level, blockPos));
        } else if (block instanceof RedstoneByteEncoderBlock) {
            configKind.set(CONFIG_BYTE_ENCODER);
            configPrimary.set(state.getValue(RedstoneByteEncoderBlock.MODE));
            var input = dev.redstoneengineering.physics.RedstoneObservationSupport.observe(
                    level, blockPos, DirectionalDomainBlock.seriesInputSide(state));
            configSecondary.set(input.value());
            configTertiary.set(RedstoneByteEncoderBlock.encode(input.value(), state.getValue(RedstoneByteEncoderBlock.MODE)));
        } else if (block instanceof ByteToRedstoneDecoderBlock) {
            configKind.set(CONFIG_BYTE_DECODER);
            configPrimary.set(state.getValue(ByteToRedstoneDecoderBlock.MODE));
            BlockPos input = blockPos.relative(DirectionalSignalBlock.seriesInputSide(state));
            configSecondary.set(dev.redstoneengineering.physics.DataBusNetwork.sample(level, input));
            configTertiary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
        } else if (block instanceof RedstoneCableJunctionBlock) {
            configKind.set(CONFIG_JUNCTION);
            configPrimary.set(state.getValue(RedstoneCableJunctionBlock.MEDIUM).ordinal());
            configSecondary.set(RedstoneCableJunctionBlock.power(level, blockPos));
            configTertiary.set(RedstoneCableJunctionBlock.carrierValid(level, blockPos) ? 1 : 0);
        } else if (block instanceof AnalogIndicatorBlock) {
            configKind.set(CONFIG_ANALOG_INDICATOR);
            configPrimary.set(state.getValue(AnalogIndicatorBlock.LEVEL));
            configSecondary.set(AnalogIndicatorBlock.retainedMinimum(level, blockPos));
            configTertiary.set(AnalogIndicatorBlock.retainedMaximum(level, blockPos));
        } else if (block instanceof QuartzOscillatorBlock) {
            configKind.set(CONFIG_QUARTZ_OSCILLATOR);
            configPrimary.set(state.getValue(QuartzOscillatorBlock.PERIOD_INDEX));
            configSecondary.set(state.getValue(QuartzOscillatorBlock.ACTIVE) ? 1 : 0);
            configTertiary.set(QuartzOscillatorBlock.periodTicks(state));
        } else if (block instanceof FaultLatchBlock) {
            configKind.set(CONFIG_FAULT_LATCH);
            configPrimary.set(state.getValue(FaultLatchBlock.THRESHOLD));
            configSecondary.set(FaultLatchBlock.latched(level, blockPos) ? 1 : 0);
            configTertiary.set(FaultLatchBlock.tripCount(level, blockPos));
        } else if (block instanceof RedstoneCableTerminalBlock) {
            RedstoneCableNetwork.PathEvidence path = RedstoneCableNetwork.pathEvidence(level, blockPos);
            configKind.set(CONFIG_CABLE_TERMINAL);
            configPrimary.set(state.getValue(RedstoneCableTerminalBlock.POWER));
            configSecondary.set(state.getValue(RedstoneCableTerminalBlock.OUTPUT_MODE) ? 1 : 0);
            configTertiary.set(path.attenuationLoss());
        } else if (block instanceof RedstoneSignalCableBlock) {
            RedstoneCableNetwork.PathEvidence path = RedstoneCableNetwork.pathEvidence(level, blockPos);
            configKind.set(CONFIG_REDSTONE_CABLE);
            configPrimary.set(RedstoneSignalCableBlock.power(level, blockPos));
            configSecondary.set(path.winningSourceLevel());
            configTertiary.set(path.attenuationLoss());
        } else if (block instanceof RedstoneReferenceSourceBlock) {
            configKind.set(CONFIG_REFERENCE_SOURCE);
            configPrimary.set(RedstoneReferenceSourceBlock.configuredPower(state));
        } else if (block instanceof SignalProbeBlock) {
            configKind.set(CONFIG_SIGNAL_PROBE);
            configPrimary.set(SignalProbeBlock.configuredChannel(state));
            configSecondary.set(SignalProbeBlock.measuredValue(level, blockPos, state));
        } else if (block instanceof IronCoreBlock) {
            configKind.set(CONFIG_IRON_CORE);
            configPrimary.set(IronCoreBlock.appliedField(level, blockPos));
            configSecondary.set(IronCoreBlock.remanentField(level, blockPos));
            configTertiary.set(IronCoreBlock.coverageComplete(level, blockPos) ? 1 : 0);
        } else if (block instanceof ElectromagnetBlock) {
            configKind.set(CONFIG_ELECTROMAGNET);
            configPrimary.set(ElectromagnetBlock.targetField(level, blockPos));
            configSecondary.set(ElectromagnetBlock.thermalLoad(level, blockPos));
            configTertiary.set(state.getValue(ElectromagnetBlock.FIELD));
            configQuaternary.set(ElectromagnetBlock.trackingError(level, blockPos));
        } else if (block instanceof MagneticFieldSensorBlock) {
            configKind.set(CONFIG_MAGNETIC_FIELD);
            configPrimary.set(state.getValue(MagneticFieldSensorBlock.RADIUS_MODE));
            configSecondary.set(state.getValue(MagneticFieldSensorBlock.SAMPLE_MODE));
        } else if (block instanceof EngineeringLightSensorBlock) {
            configKind.set(CONFIG_LIGHT_SENSOR);
            configPrimary.set(state.getValue(EngineeringLightSensorBlock.PROFILE));
        } else if (block instanceof TankLevelSensorBlock) {
            configKind.set(CONFIG_TANK_LEVEL);
            configPrimary.set(state.getValue(TankLevelSensorBlock.RANGE_MODE));
        } else if (block instanceof EntityDensitySensorBlock) {
            configKind.set(CONFIG_ENTITY_DENSITY);
            configPrimary.set(state.getValue(EntityDensitySensorBlock.PROFILE));
            configSecondary.set(state.getValue(EntityDensitySensorBlock.APERTURE_MODE));
        } else if (block instanceof LapisPrecisionRangeSensorBlock) {
            configKind.set(CONFIG_LAPIS_RANGE);
            configPrimary.set(state.getValue(AbstractLapisTransducerBlock.PROFILE));
            configSecondary.set(LapisPrecisionRangeSensorBlock.rangeBlocks(state));
        } else if (block instanceof AbstractLapisTransducerBlock) {
            configKind.set(CONFIG_LAPIS_TRANSDUCER);
            configPrimary.set(state.getValue(AbstractLapisTransducerBlock.PROFILE));
        } else if (block instanceof MolecularCloudReceiverBlock) {
            configKind.set(CONFIG_MOLECULAR_RECEIVER);
            configPrimary.set(state.getValue(MolecularCloudReceiverBlock.SENSITIVITY));
            configSecondary.set(MolecularCloudReceiverBlock.peak(level, blockPos));
        } else if (block instanceof AlarmProcessorBlock) {
            configKind.set(CONFIG_ALARM);
            configPrimary.set(state.getValue(AlarmProcessorBlock.SEVERITY));
            configSecondary.set(!AlarmProcessorBlock.latched(level, blockPos) ? 0
                    : AlarmProcessorBlock.unacknowledged(level, blockPos) ? 2 : 1);
            configTertiary.set(AlarmProcessorBlock.activationCount(level, blockPos));
        } else if (block instanceof SampleHoldBlock) {
            configKind.set(CONFIG_SAMPLE_HOLD);
            configPrimary.set(state.getValue(SampleHoldBlock.TRIGGER_MODE));
            configSecondary.set(SampleHoldBlock.captureCount(level, blockPos));
            configTertiary.set(SampleHoldBlock.heldValue(level, blockPos));
        } else if (block instanceof CalibrationModuleBlock) {
            configKind.set(CONFIG_CALIBRATION);
            configPrimary.set(state.getValue(CalibrationModuleBlock.PROFILE));
            var measurement = CalibrationModuleBlock.measurement(level, blockPos);
            configSecondary.set(measurement.sampleCount() <= 0 ? 0 : (int) Math.round(measurement.bias()));
            configTertiary.set(measurement.sampleCount());
        } else if (block instanceof PwmControllerBlock) {
            configKind.set(CONFIG_PWM);
            configPrimary.set(state.getValue(PwmControllerBlock.PERIOD_MODE));
            configSecondary.set(state.getValue(PwmControllerBlock.INVERT) ? 1 : 0);
        } else if (block instanceof FaultInjectorBlock) {
            configKind.set(CONFIG_FAULT_INJECTOR);
            configPrimary.set(state.getValue(FaultInjectorBlock.MODE));
            configSecondary.set(FaultInjectorBlock.active(level, blockPos) ? 1 : 0);
        } else if (block instanceof SequenceControllerBlock) {
            configKind.set(CONFIG_SEQUENCE_CONTROLLER);
            configPrimary.set(SequenceControllerBlock.step(level, blockPos));
            configSecondary.set(SequenceControllerBlock.completedCycles(level, blockPos));
            configTertiary.set(SequenceControllerBlock.transitions(level, blockPos));
        } else if (block instanceof SafetyInterlockBlock) {
            configKind.set(CONFIG_SAFETY_INTERLOCK);
            configPrimary.set(SafetyInterlockBlock.failedMask(level, blockPos));
            configSecondary.set(state.getValue(DirectionalSignalBlock.OUTPUT) > 0 ? 1 : 0);
            configTertiary.set(SafetyInterlockBlock.transitionCount(level, blockPos));
        } else if (block instanceof TopologyDebuggerBlock) {
            configKind.set(CONFIG_TOPOLOGY_DEBUGGER);
            configPrimary.set(TopologyDebuggerBlock.scanCount(level, blockPos));
            configSecondary.set(TopologyDebuggerBlock.targetsVanillaRedstone(level, blockPos, state) ? 1 : 0);
        }

        declaredPortMask.set(0);
        inputMask.set(0);
        outputMask.set(0);
        bidirectionalMask.set(0);
        for (int i = 0; i < FACE_COUNT; i++) {
            domains[i].set(-1);
            kinds[i].set(-1);
            values[i].set(0);
            minimums[i].set(0);
            maximums[i].set(0);
            qualities[i].set(-1);
        }
        if (!(block instanceof EngineeringPortProvider provider)) return;

        int declared = 0;
        int inputs = 0;
        int outputs = 0;
        int bidirectional = 0;
        for (Direction side : Direction.values()) {
            var descriptor = provider.engineeringPort(state, side);
            if (descriptor.isEmpty()) continue;
            int index = side.ordinal();
            declared |= 1 << index;
            PortDirection direction = descriptor.get().direction();
            if (direction == PortDirection.INPUT) inputs |= 1 << index;
            if (direction == PortDirection.OUTPUT) outputs |= 1 << index;
            if (direction == PortDirection.BIDIRECTIONAL) bidirectional |= 1 << index;
            domains[index].set(descriptor.get().domain().ordinal());
            kinds[index].set(descriptor.get().kind().ordinal());
            var snapshot = provider.engineeringSnapshot(level, blockPos, state, side);
            if (snapshot.isPresent()) {
                values[index].set(syncNumber(snapshot.get().value()));
                minimums[index].set(syncNumber(snapshot.get().minimum()));
                maximums[index].set(syncNumber(snapshot.get().maximum()));
                qualities[index].set(snapshot.get().quality().ordinal());
            } else qualities[index].set(PortQuality.NO_SIGNAL.ordinal());
        }
        declaredPortMask.set(declared);
        inputMask.set(inputs);
        outputMask.set(outputs);
        bidirectionalMask.set(bidirectional);
    }

    private static int routeKind(Block block) {
        if (block instanceof LapisPrecisionMeterBlock || block instanceof CopperCircuitMeterBlock) return ROUTE_MEASUREMENT_FACE;
        if (block instanceof MolecularCloudReceiverBlock) return ROUTE_FIXED_APERTURE_OUTPUT_FRONT;
        if (block instanceof SignalProbeBlock) return ROUTE_PROBE_AXIS;
        if (block instanceof RedstoneCableTerminalBlock) return ROUTE_TERMINAL_INTERFACE;
        if (block instanceof DirectionalRedstoneEndpointBlock) return ROUTE_ENDPOINT_FRONT;
        if (block instanceof AlarmProcessorBlock
                || block instanceof SequenceControllerBlock
                || block instanceof SafetyInterlockBlock
                || block instanceof TopologyDebuggerBlock
                || block instanceof SampleHoldBlock
                || block instanceof CalibrationModuleBlock
                || block instanceof PwmControllerBlock
                || block instanceof FaultInjectorBlock
                || block instanceof QuartzTriggeredLapisSamplerBlock) return ROUTE_MULTI_PORT_LAYOUT;
        if (block instanceof DirectionalSignalBlock || block instanceof DirectionalDomainBlock) return ROUTE_SERIES_AXIS;
        return ROUTE_NONE;
    }

    private static boolean isRotatable(Block block) { return routeKind(block) != ROUTE_NONE; }

    private static int syncNumber(double value) {
        long rounded = Math.round(value);
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, rounded));
    }

    private static int directionOrdinal(BlockState state) {
        if (state.hasProperty(DirectionalSignalBlock.FACING)) return state.getValue(DirectionalSignalBlock.FACING).ordinal();
        if (state.hasProperty(DirectionalDomainBlock.FACING)) return state.getValue(DirectionalDomainBlock.FACING).ordinal();
        if (state.hasProperty(DirectionalRedstoneEndpointBlock.FACING)) return state.getValue(DirectionalRedstoneEndpointBlock.FACING).ordinal();
        if (state.hasProperty(SignalProbeBlock.FACING)) return state.getValue(SignalProbeBlock.FACING).ordinal();
        if (state.hasProperty(RedstoneCableTerminalBlock.FACING)) return state.getValue(RedstoneCableTerminalBlock.FACING).ordinal();
        if (state.hasProperty(LapisPrecisionMeterBlock.FACING)) return state.getValue(LapisPrecisionMeterBlock.FACING).ordinal();
        if (state.hasProperty(CopperCircuitMeterBlock.FACING)) return state.getValue(CopperCircuitMeterBlock.FACING).ordinal();
        return -1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return true;
        if (!stillValid(player)) return false;
        boolean changed = switch (id) {
            case BUTTON_ROTATE_LEFT -> rotate(false);
            case BUTTON_ROTATE_RIGHT -> rotate(true);
            case BUTTON_INPUT_LEFT -> rotateInput(false);
            case BUTTON_INPUT_RIGHT -> rotateInput(true);
            case BUTTON_OUTPUT_LEFT -> rotateOutput(false);
            case BUTTON_OUTPUT_RIGHT -> rotateOutput(true);
            case BUTTON_CONFIG_PRIMARY_PREVIOUS -> adjustPrimary(-1);
            case BUTTON_CONFIG_PRIMARY_NEXT -> adjustPrimary(1);
            case BUTTON_CONFIG_SECONDARY_PREVIOUS -> adjustSecondary(-1);
            case BUTTON_CONFIG_SECONDARY_NEXT -> adjustSecondary(1);
            case BUTTON_CONFIG_ACTION -> runAction();
            case BUTTON_CONFIG_TOGGLE -> toggleConfig();
            default -> false;
        };
        if (changed) {
            refreshAuthoritativeSnapshot();
            broadcastChanges();
        }
        return changed;
    }

    private boolean adjustPrimary(int delta) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof AnalogComparatorBlock) return AnalogComparatorBlock.stepHysteresis(level, blockPos, delta > 0);
        if (block instanceof SignalAmplifierBlock) return SignalAmplifierBlock.stepGain(level, blockPos, delta > 0);
        if (block instanceof RedundantVoterBlock) return RedundantVoterBlock.stepTolerance(level, blockPos, delta > 0);
        if (block instanceof WatchdogBlock) return WatchdogBlock.stepTimeout(level, blockPos, delta > 0);
        if (block instanceof DifferentialDriverBlock) return DifferentialDriverBlock.stepThreshold(level, blockPos, delta > 0);
        if (block instanceof SerializerBlock) return SerializerBlock.stepPeriod(level, blockPos, delta > 0);
        if (block instanceof DigitalRegeneratorBlock) return DigitalRegeneratorBlock.stepThreshold(level, blockPos, delta > 0);
        if (block instanceof RedstoneByteEncoderBlock) return RedstoneByteEncoderBlock.stepMode(level, blockPos, delta > 0);
        if (block instanceof ByteToRedstoneDecoderBlock) return ByteToRedstoneDecoderBlock.stepMode(level, blockPos, delta > 0);
        if (block instanceof QuartzOscillatorBlock) return QuartzOscillatorBlock.stepPeriod(level, blockPos, delta > 0);
        if (block instanceof FaultLatchBlock) return FaultLatchBlock.stepThreshold(level, blockPos, delta > 0);
        if (block instanceof RedstoneReferenceSourceBlock) return RedstoneReferenceSourceBlock.stepPower(level, blockPos, delta > 0);
        if (block instanceof SignalProbeBlock) return SignalProbeBlock.stepChannel(level, blockPos, delta > 0);
        if (block instanceof MagneticFieldSensorBlock) return MagneticFieldSensorBlock.adjustRadius(level, blockPos, delta);
        if (block instanceof EngineeringLightSensorBlock) return EngineeringLightSensorBlock.adjustProfile(level, blockPos, delta);
        if (block instanceof TankLevelSensorBlock) return TankLevelSensorBlock.adjustRange(level, blockPos, delta);
        if (block instanceof EntityDensitySensorBlock) return EntityDensitySensorBlock.adjustProfile(level, blockPos, delta);
        if (block instanceof AbstractLapisTransducerBlock transducer) return transducer.adjustProfile(level, blockPos, delta);
        if (block instanceof MolecularCloudReceiverBlock receiver) return receiver.adjustSensitivity(level, blockPos, delta);
        if (block instanceof AlarmProcessorBlock alarm) return alarm.adjustSeverity(level, blockPos, delta);
        if (block instanceof SampleHoldBlock sampleHold) return sampleHold.adjustTriggerMode(level, blockPos, delta);
        if (block instanceof CalibrationModuleBlock calibration) return calibration.adjustProfile(level, blockPos, delta);
        if (block instanceof PwmControllerBlock pwm) return pwm.adjustPeriodMode(level, blockPos, delta);
        if (block instanceof FaultInjectorBlock faultInjector) return faultInjector.adjustMode(level, blockPos, delta);
        return false;
    }

    private boolean adjustSecondary(int delta) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof SingleRelayBlock) return SingleRelayBlock.stepPickup(level, blockPos, delta > 0);
        if (block instanceof MagneticFieldSensorBlock) return MagneticFieldSensorBlock.adjustSampling(level, blockPos, delta);
        if (block instanceof LapisPrecisionRangeSensorBlock range) return range.adjustRange(level, blockPos, delta);
        if (block instanceof EntityDensitySensorBlock) return EntityDensitySensorBlock.adjustAperture(level, blockPos, delta);
        return false;
    }

    private boolean runAction() {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof SignalAmplifierBlock) return SignalAmplifierBlock.resetClipEvidence(level, blockPos);
        if (block instanceof RedundantVoterBlock voter) return voter.resetDiagnostics(level, blockPos);
        if (block instanceof WatchdogBlock watchdog) return watchdog.resetDiagnostics(level, blockPos);
        if (block instanceof AnalogIndicatorBlock) return AnalogIndicatorBlock.resetExtrema(level, blockPos);
        if (block instanceof FaultLatchBlock latch) return latch.manualReset(level, blockPos);
        if (block instanceof IronCoreBlock) return IronCoreBlock.degauss(level, blockPos);
        if (block instanceof MolecularCloudReceiverBlock receiver) return receiver.resetHistory(level, blockPos);
        if (block instanceof AlarmProcessorBlock alarm) return alarm.acknowledge(level, blockPos);
        if (block instanceof SampleHoldBlock sampleHold) return sampleHold.clearHeldValue(level, blockPos);
        if (block instanceof FaultInjectorBlock faultInjector) return faultInjector.resetDiagnostics(level, blockPos);
        if (block instanceof SequenceControllerBlock sequence) return sequence.operatorReset(level, blockPos);
        if (block instanceof SafetyInterlockBlock interlock) return interlock.resetDiagnostics(level, blockPos);
        if (block instanceof TopologyDebuggerBlock debugger) return debugger.resetDiagnostics(level, blockPos);
        return false;
    }

    private boolean toggleConfig() {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof AnalogComparatorBlock) return AnalogComparatorBlock.stepMode(level, blockPos, true);
        if (block instanceof SignalSelectorBlock) return SignalSelectorBlock.toggleInvertSelect(level, blockPos);
        if (block instanceof SingleRelayBlock) return SingleRelayBlock.toggleContactMode(level, blockPos);
        if (block instanceof RedstoneCableTerminalBlock) return RedstoneCableTerminalBlock.toggleMode(level, blockPos);
        return block instanceof PwmControllerBlock pwm && pwm.toggleInvert(level, blockPos);
    }

    private boolean rotate(boolean clockwise) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof DirectionalSignalBlock) return DirectionalSignalBlock.rotateWholeRoute(level, blockPos, clockwise);
        if (block instanceof DirectionalDomainBlock) return DirectionalDomainBlock.rotateWholeRoute(level, blockPos, clockwise);
        if (block instanceof DirectionalRedstoneEndpointBlock) return DirectionalRedstoneEndpointBlock.rotateOutput(level, blockPos, clockwise);
        if (block instanceof SignalProbeBlock) return SignalProbeBlock.rotateMeasurementAxis(level, blockPos, clockwise);
        if (block instanceof RedstoneCableTerminalBlock) return RedstoneCableTerminalBlock.rotateInterface(level, blockPos, clockwise);
        if (block instanceof LapisPrecisionMeterBlock) return LapisPrecisionMeterBlock.rotateMeasurementFace(level, blockPos, clockwise);
        if (block instanceof CopperCircuitMeterBlock) return CopperCircuitMeterBlock.rotateMeasurementFace(level, blockPos, clockwise);
        return false;
    }

    private boolean rotateInput(boolean clockwise) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (!hasInputEndpoint()) return false;
        if (routeKind(block) == ROUTE_MULTI_PORT_LAYOUT) return rotate(clockwise);
        if (block instanceof DirectionalSignalBlock) return DirectionalSignalBlock.rotateSeriesInput(level, blockPos, clockwise);
        if (block instanceof DirectionalDomainBlock) return DirectionalDomainBlock.rotateSeriesInput(level, blockPos, clockwise);
        return rotate(clockwise);
    }

    private boolean rotateOutput(boolean clockwise) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (!hasOutputEndpoint()) return false;
        if (routeKind(block) == ROUTE_MULTI_PORT_LAYOUT) return rotate(clockwise);
        if (block instanceof DirectionalSignalBlock) return DirectionalSignalBlock.rotateSeriesOutput(level, blockPos, clockwise);
        if (block instanceof DirectionalDomainBlock) return DirectionalDomainBlock.rotateSeriesOutput(level, blockPos, clockwise);
        return rotate(clockwise);
    }

    public int facingOrdinal() { return facing.get(); }
    public int routeKind() { return routeKind.get(); }
    public int configKind() { return configKind.get(); }
    public int configPrimary() { return configPrimary.get(); }
    public int configSecondary() { return configSecondary.get(); }
    public int configTertiary() { return configTertiary.get(); }
    public int configQuaternary() { return configQuaternary.get(); }
    public int declaredPortMask() { return declaredPortMask.get(); }
    public boolean hasPort(Direction side) { return (declaredPortMask.get() & (1 << side.ordinal())) != 0; }
    public boolean isInput(Direction side) { return (inputMask.get() & (1 << side.ordinal())) != 0; }
    public boolean isOutput(Direction side) { return (outputMask.get() & (1 << side.ordinal())) != 0; }
    public boolean isBidirectional(Direction side) { return (bidirectionalMask.get() & (1 << side.ordinal())) != 0; }
    public boolean hasInputEndpoint() { return (inputMask.get() | bidirectionalMask.get()) != 0; }
    public boolean hasOutputEndpoint() { return (outputMask.get() | bidirectionalMask.get()) != 0; }
    public int value(Direction side) { return values[side.ordinal()].get(); }
    public int minimum(Direction side) { return minimums[side.ordinal()].get(); }
    public int maximum(Direction side) { return maximums[side.ordinal()].get(); }

    public EngineeringDomain domain(Direction side) {
        int ordinal = domains[side.ordinal()].get();
        EngineeringDomain[] all = EngineeringDomain.values();
        return ordinal < 0 || ordinal >= all.length ? EngineeringDomain.GENERIC : all[ordinal];
    }

    public PortKind portKind(Direction side) {
        int ordinal = kinds[side.ordinal()].get();
        PortKind[] all = PortKind.values();
        return ordinal < 0 || ordinal >= all.length ? PortKind.AUXILIARY : all[ordinal];
    }

    public PortQuality quality(Direction side) {
        int ordinal = qualities[side.ordinal()].get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }

    public boolean rotatableSeriesAxis() { return seriesRotatable.get() != 0; }
    public boolean independentRouteEndpoints() { return hasInputEndpoint() || hasOutputEndpoint(); }
}
