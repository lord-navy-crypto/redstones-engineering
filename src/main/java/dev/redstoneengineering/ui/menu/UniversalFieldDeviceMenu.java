package dev.redstoneengineering.ui.menu;

import dev.redstoneengineering.block.*;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.SensorModel;
import dev.redstoneengineering.physics.SoulFluxNetwork;
import dev.redstoneengineering.physics.ThermalPhysics;
import dev.redstoneengineering.ui.EngineeringUiRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
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
    /** Encodes an explicit primary numeric target as BASE + value through the vanilla container-button channel. */
    public static final int BUTTON_CONFIG_PRIMARY_DIRECT_BASE = 1000;
    public static final int BUTTON_CONFIG_PRIMARY_DIRECT_MAX = 1063;
    /** Encodes an explicit secondary numeric target as BASE + value through the same server-authoritative channel. */
    public static final int BUTTON_CONFIG_SECONDARY_DIRECT_BASE = 1100;
    public static final int BUTTON_CONFIG_SECONDARY_DIRECT_MAX = 1163;

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
    public static final int CONFIG_COPPER_VOLTAGE_SOURCE = 12;
    public static final int CONFIG_COPPER_LOAD = 13;
    public static final int CONFIG_COPPER_SERIES_RESISTOR = 14;
    public static final int CONFIG_COPPER_CAPACITOR = 15;
    public static final int CONFIG_COPPER_FUSE = 16;
    public static final int CONFIG_LAPIS_NOISE = 17;
    public static final int CONFIG_QUARTZ_OSCILLATOR = 18;
    public static final int CONFIG_QUARTZ_PHASE_DELAY = 19;
    public static final int CONFIG_IRON_CORE = 20;
    public static final int CONFIG_THERMAL_MASS = 21;
    public static final int CONFIG_THERMAL_HEATER = 22;
    public static final int CONFIG_THERMAL_RADIATOR = 23;

    public static final int PIONEER_MEASUREMENT_NONE = 0;
    public static final int PIONEER_MEASUREMENT_TEMPERATURE = 1;
    public static final int PIONEER_MEASUREMENT_LIGHT = 2;
    public static final int PIONEER_MEASUREMENT_TANK = 3;
    public static final int PIONEER_MEASUREMENT_ENTITY_DENSITY = 4;
    public static final int PIONEER_MEASUREMENT_LAPIS_METER = 5;
    public static final int PIONEER_MEASUREMENT_LAPIS_RANGE = 6;
    public static final int PIONEER_MEASUREMENT_ANALOG_INDICATOR = 7;

    public static final int PIONEER_PROCESS_NONE = 0;
    public static final int PIONEER_PROCESS_CALIBRATION = 1;
    public static final int PIONEER_PROCESS_SAMPLE_HOLD = 2;
    public static final int PIONEER_PROCESS_PWM = 3;
    public static final int PIONEER_PROCESS_LAPIS_TEMPERATURE = 4;
    public static final int PIONEER_PROCESS_LAPIS_MAGNETIC = 5;
    public static final int PIONEER_PROCESS_LAPIS_OPTICAL = 6;
    public static final int PIONEER_PROCESS_LAPIS_VOLTAGE = 7;
    public static final int PIONEER_PROCESS_COPPER_WIRE = 8;
    public static final int PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE = 9;
    public static final int PIONEER_PROCESS_COPPER_LOAD = 10;
    public static final int PIONEER_PROCESS_COPPER_SERIES_RESISTOR = 11;
    public static final int PIONEER_PROCESS_COPPER_CAPACITOR = 12;
    public static final int PIONEER_PROCESS_COPPER_FUSE = 13;
    public static final int PIONEER_PROCESS_COPPER_JUNCTION = 14;
    public static final int PIONEER_PROCESS_LAPIS_NOISE = 15;
    public static final int PIONEER_PROCESS_QUARTZ_OSCILLATOR = 16;
    public static final int PIONEER_PROCESS_QUARTZ_PHASE_DELAY = 17;
    public static final int PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER = 18;
    public static final int PIONEER_PROCESS_SOUL_INJECTOR = 19;
    public static final int PIONEER_PROCESS_SOUL_METER = 20;
    public static final int PIONEER_PROCESS_MOLECULAR_RECEIVER = 21;
    public static final int PIONEER_PROCESS_IRON_CORE = 22;
    public static final int PIONEER_PROCESS_THERMAL_MASS = 23;
    public static final int PIONEER_PROCESS_THERMAL_HEATER = 24;
    public static final int PIONEER_PROCESS_THERMAL_RADIATOR = 25;
    public static final int PIONEER_PROCESS_THERMAL_CALORIMETER = 26;
    public static final int PIONEER_PROCESS_SOUL_CONDUIT = 27;
    public static final int PIONEER_PROCESS_SOUL_RESERVOIR = 28;

    private final DataSlot facing = trackedInt();
    private final DataSlot routeKind = trackedInt();
    private final DataSlot configKind = trackedInt();
    private final DataSlot configPrimary = trackedInt();
    private final DataSlot configSecondary = trackedInt();
    private final DataSlot pioneerMeasurementKind = trackedInt();
    private final DataSlot pioneerPrimary = trackedInt();
    private final DataSlot pioneerSecondary = trackedInt();
    private final DataSlot pioneerTertiary = trackedInt();
    private final DataSlot pioneerQuaternary = trackedInt();
    private final DataSlot pioneerEvidenceQuality = trackedInt();
    private final DataSlot pioneerProcessKind = trackedInt();
    private final DataSlot pioneerProcessPrimary = trackedInt();
    private final DataSlot pioneerProcessSecondary = trackedInt();
    private final DataSlot pioneerProcessTertiary = trackedInt();
    private final DataSlot pioneerProcessQuaternary = trackedInt();
    private final DataSlot pioneerProcessQuinary = trackedInt();
    private final DataSlot pioneerProcessSenary = trackedInt();
    private final DataSlot pioneerProcessEvidenceQuality = trackedInt();
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
        pioneerMeasurementKind.set(PIONEER_MEASUREMENT_NONE);
        pioneerPrimary.set(0);
        pioneerSecondary.set(0);
        pioneerTertiary.set(0);
        pioneerQuaternary.set(0);
        pioneerEvidenceQuality.set(-1);
        pioneerProcessKind.set(PIONEER_PROCESS_NONE);
        pioneerProcessPrimary.set(0);
        pioneerProcessSecondary.set(0);
        pioneerProcessTertiary.set(0);
        pioneerProcessQuaternary.set(0);
        pioneerProcessQuinary.set(0);
        pioneerProcessSenary.set(0);
        pioneerProcessEvidenceQuality.set(-1);
        fillPioneerMeasurementSnapshot(block, state);
        fillPioneerProcessSnapshot(block, state);

        if (block instanceof LapisPrecisionRangeSensorBlock) {
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
        } else if (block instanceof SampleHoldBlock) {
            configKind.set(CONFIG_SAMPLE_HOLD);
            configPrimary.set(state.getValue(SampleHoldBlock.TRIGGER_MODE));
            configSecondary.set(SampleHoldBlock.captureCount(level, blockPos));
        } else if (block instanceof CalibrationModuleBlock) {
            configKind.set(CONFIG_CALIBRATION);
            configPrimary.set(state.getValue(CalibrationModuleBlock.PROFILE));
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
        } else if (block instanceof SafetyInterlockBlock) {
            configKind.set(CONFIG_SAFETY_INTERLOCK);
            configPrimary.set(SafetyInterlockBlock.failedMask(level, blockPos));
            configSecondary.set(state.getValue(DirectionalSignalBlock.OUTPUT) > 0 ? 1 : 0);
        } else if (block instanceof TopologyDebuggerBlock) {
            configKind.set(CONFIG_TOPOLOGY_DEBUGGER);
            configPrimary.set(TopologyDebuggerBlock.scanCount(level, blockPos));
            configSecondary.set(TopologyDebuggerBlock.targetsVanillaRedstone(level, blockPos, state) ? 1 : 0);
        } else if (block instanceof CopperVoltageSourceBlock) {
            configKind.set(CONFIG_COPPER_VOLTAGE_SOURCE);
            configPrimary.set(state.getValue(CopperVoltageSourceBlock.VOLTAGE));
        } else if (block instanceof CopperResistiveLoadBlock) {
            configKind.set(CONFIG_COPPER_LOAD);
            configPrimary.set(state.getValue(CopperResistiveLoadBlock.RESISTANCE));
        } else if (block instanceof CopperSeriesResistorBlock) {
            configKind.set(CONFIG_COPPER_SERIES_RESISTOR);
            configPrimary.set(state.getValue(CopperSeriesResistorBlock.RESISTANCE));
        } else if (block instanceof CopperCapacitorBlock) {
            configKind.set(CONFIG_COPPER_CAPACITOR);
            configPrimary.set(state.getValue(CopperCapacitorBlock.C_INDEX));
        } else if (block instanceof CopperFuseBlock) {
            configKind.set(CONFIG_COPPER_FUSE);
            configPrimary.set(state.getValue(CopperFuseBlock.RATING));
            configSecondary.set(state.getValue(CopperFuseBlock.TRIPPED) ? 1 : 0);
        } else if (block instanceof LapisNoiseSourceBlock) {
            configKind.set(CONFIG_LAPIS_NOISE);
            configPrimary.set(state.getValue(LapisNoiseSourceBlock.BASELINE));
            configSecondary.set(state.getValue(LapisNoiseSourceBlock.NOISE));
        } else if (block instanceof QuartzLabOscillatorBlock) {
            configKind.set(CONFIG_QUARTZ_OSCILLATOR);
            configPrimary.set(state.getValue(QuartzLabOscillatorBlock.PERIOD_INDEX));
            configSecondary.set(state.getValue(QuartzLabOscillatorBlock.JITTER));
        } else if (block instanceof QuartzPhaseDelayBlock) {
            configKind.set(CONFIG_QUARTZ_PHASE_DELAY);
            configPrimary.set(state.getValue(QuartzPhaseDelayBlock.DELAY));
        } else if (block instanceof IronCoreBlock) {
            configKind.set(CONFIG_IRON_CORE);
            configPrimary.set(state.getValue(IronCoreBlock.MAGNETIZED) ? 1 : 0);
        } else if (block instanceof ThermalMassBlock) {
            configKind.set(CONFIG_THERMAL_MASS);
            configPrimary.set(state.getValue(ThermalMassBlock.HEAT_CAPACITY));
        } else if (block instanceof ThermalHeaterBlock) {
            configKind.set(CONFIG_THERMAL_HEATER);
            configPrimary.set(state.getValue(ThermalHeaterBlock.RESISTANCE_INDEX));
        } else if (block instanceof ThermalRadiatorBlock) {
            configKind.set(CONFIG_THERMAL_RADIATOR);
            configPrimary.set(state.getValue(ThermalRadiatorBlock.COOLING));
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

    private void fillPioneerMeasurementSnapshot(Block block, BlockState state) {
        if (block instanceof TemperatureSensorBlock) {
            TemperatureSensorBlock.ThermalObservation observation = TemperatureSensorBlock.observe(level, blockPos);
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_TEMPERATURE);
            pioneerPrimary.set(state.getValue(TemperatureSensorBlock.TEMPERATURE));
            pioneerSecondary.set(observation.targetTemperature());
            pioneerTertiary.set(observation.thermalBodies());
            pioneerQuaternary.set(observation.loadedFaces());
            pioneerEvidenceQuality.set((observation.complete() ? PortQuality.VALID : PortQuality.STALE).ordinal());
            return;
        }
        if (block instanceof EngineeringLightSensorBlock sensor) {
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_LIGHT);
            pioneerPrimary.set(level.getMaxLocalRawBrightness(blockPos.above()));
            pioneerSecondary.set(state.getValue(DirectionalRedstoneSensorBlock.POWER));
            pioneerTertiary.set(1); // BALANCED profile
            pioneerQuaternary.set(10); // scheduled sample period
            pioneerEvidenceQuality.set(sensor.engineeringSnapshot(level, blockPos, state, Direction.UP)
                    .map(snapshot -> snapshot.quality().ordinal()).orElse(PortQuality.NO_SIGNAL.ordinal()));
            return;
        }
        if (block instanceof TankLevelSensorBlock) {
            TankLevelSensorBlock.ColumnSample sample = TankLevelSensorBlock.columnSample(level, blockPos);
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_TANK);
            pioneerPrimary.set(sample.fluidBlocks());
            pioneerSecondary.set(sample.scannedCells());
            pioneerTertiary.set(sample.expectedCells());
            pioneerQuaternary.set(state.getValue(DirectionalRedstoneSensorBlock.POWER));
            pioneerEvidenceQuality.set(sample.quality().ordinal());
            return;
        }
        if (block instanceof EntityDensitySensorBlock) {
            EntityDensitySensorBlock.DensitySample sample = EntityDensitySensorBlock.densitySample(level, blockPos);
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_ENTITY_DENSITY);
            pioneerPrimary.set(sample.physicalCount());
            pioneerSecondary.set(sample.complete() ? 1 : 0);
            pioneerTertiary.set(state.getValue(DirectionalRedstoneSensorBlock.POWER));
            pioneerQuaternary.set(4); // horizontal aperture radius
            pioneerEvidenceQuality.set(sample.quality().ordinal());
            return;
        }
        if (block instanceof LapisPrecisionMeterBlock) {
            LapisPrecisionMeterBlock.MeterReading reading = LapisPrecisionMeterBlock.reading(level, blockPos, state);
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_LAPIS_METER);
            pioneerPrimary.set(reading.value());
            pioneerSecondary.set(state.getValue(LapisPrecisionMeterBlock.FACING).ordinal());
            pioneerTertiary.set(0);
            pioneerQuaternary.set(100);
            pioneerEvidenceQuality.set(reading.quality().ordinal());
            return;
        }
        if (block instanceof LapisPrecisionRangeSensorBlock range && level instanceof ServerLevel server) {
            LapisPrecisionRangeSensorBlock.RangeSample sample = LapisPrecisionRangeSensorBlock.rangeSample(server, blockPos, state);
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_LAPIS_RANGE);
            pioneerPrimary.set(sample.distance());
            pioneerSecondary.set(sample.maxRange());
            pioneerTertiary.set(range.output(level, blockPos));
            pioneerQuaternary.set(state.getValue(AbstractLapisTransducerBlock.PROFILE));
            pioneerEvidenceQuality.set(sample.quality().ordinal());
            return;
        }
        if (block instanceof AnalogIndicatorBlock indicator) {
            AnalogIndicatorBlock.InputObservation observation = indicator.inputObservation(level, blockPos, state);
            pioneerMeasurementKind.set(PIONEER_MEASUREMENT_ANALOG_INDICATOR);
            pioneerPrimary.set(observation.value());
            pioneerSecondary.set(state.getValue(AnalogIndicatorBlock.LEVEL));
            pioneerTertiary.set(0);
            pioneerQuaternary.set(15);
            pioneerEvidenceQuality.set(observation.quality().ordinal());
        }
    }


    private void fillPioneerProcessSnapshot(Block block, BlockState state) {
        if (block instanceof CalibrationModuleBlock calibration) {
            pioneerProcessKind.set(PIONEER_PROCESS_CALIBRATION);
            EngineeringPortSnapshot observed = snapshotByLabel(calibration, state, "OBSERVED");
            EngineeringPortSnapshot reference = snapshotByLabel(calibration, state, "REFERENCE");
            EngineeringPortSnapshot output = snapshotByLabel(calibration, state, "CALIBRATED");
            var measurement = CalibrationModuleBlock.measurement(level, blockPos);
            pioneerProcessPrimary.set(observed == null ? 0 : syncNumber(observed.value()));
            pioneerProcessSecondary.set(reference == null ? 0 : syncNumber(reference.value()));
            pioneerProcessTertiary.set(output == null ? state.getValue(DirectionalSignalBlock.OUTPUT) : syncNumber(output.value()));
            pioneerProcessQuaternary.set(state.getValue(CalibrationModuleBlock.PROFILE));
            pioneerProcessQuinary.set(measurement.hasSample() ? syncNumber(measurement.bias()) : 0);
            pioneerProcessSenary.set(measurement.sampleCount());
            pioneerProcessEvidenceQuality.set(
                    dev.redstoneengineering.metrology.MetrologySupport.portQuality(measurement).ordinal());
            return;
        }

        if (block instanceof SampleHoldBlock sampleHold) {
            pioneerProcessKind.set(PIONEER_PROCESS_SAMPLE_HOLD);
            pioneerProcessPrimary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            pioneerProcessSecondary.set(SampleHoldBlock.captureCount(level, blockPos));
            pioneerProcessTertiary.set(SampleHoldBlock.sampleAgeTicks(level, blockPos));
            pioneerProcessQuaternary.set(state.getValue(SampleHoldBlock.TRIGGER_MODE));
            EngineeringPortSnapshot trigger = snapshotByLabel(sampleHold, state, "TRIGGER");
            EngineeringPortSnapshot reset = snapshotByLabel(sampleHold, state, "RESET");
            pioneerProcessQuinary.set(trigger == null ? 0 : syncNumber(trigger.value()));
            pioneerProcessSenary.set(reset == null ? 0 : syncNumber(reset.value()));
            pioneerProcessEvidenceQuality.set(SampleHoldBlock.outputQuality(level, blockPos).ordinal());
            return;
        }

        if (block instanceof PwmControllerBlock pwm) {
            pioneerProcessKind.set(PIONEER_PROCESS_PWM);
            PwmControllerBlock.PwmAssessment assessment = pwm.assessment(level, blockPos, state);
            pioneerProcessPrimary.set(assessment.command());
            pioneerProcessSecondary.set(assessment.periodTicks());
            pioneerProcessTertiary.set(assessment.onTicks());
            pioneerProcessQuaternary.set(assessment.effectiveDutyPermille());
            pioneerProcessQuinary.set(assessment.quantizationErrorPermille());
            pioneerProcessSenary.set(assessment.phase());
            pioneerProcessEvidenceQuality.set(PortQuality.VALID.ordinal());
            return;
        }

        if (block instanceof AbstractLapisTransducerBlock transducer) {
            if (block instanceof LapisTemperatureTransducerBlock) {
                pioneerProcessKind.set(PIONEER_PROCESS_LAPIS_TEMPERATURE);
            } else if (block instanceof LapisMagneticTransducerBlock) {
                pioneerProcessKind.set(PIONEER_PROCESS_LAPIS_MAGNETIC);
            } else if (block instanceof LapisOpticalTransducerBlock) {
                pioneerProcessKind.set(PIONEER_PROCESS_LAPIS_OPTICAL);
            } else if (block instanceof LapisVoltageTransducerBlock) {
                pioneerProcessKind.set(PIONEER_PROCESS_LAPIS_VOLTAGE);
            } else {
                return;
            }

            EngineeringPortSnapshot input = snapshotByDirection(transducer, state, PortDirection.INPUT);
            EngineeringPortSnapshot output = snapshotByDirection(transducer, state, PortDirection.OUTPUT);
            int profile = state.getValue(AbstractLapisTransducerBlock.PROFILE);
            pioneerProcessPrimary.set(input == null ? 0 : syncNumber(input.value() * 100.0));
            pioneerProcessSecondary.set(output == null ? 0 : syncNumber(output.value() * 100.0));
            pioneerProcessTertiary.set(SensorModel.samplePeriod(profile));
            pioneerProcessQuaternary.set(SensorModel.resolutionStep(profile));
            pioneerProcessQuinary.set(SensorModel.noiseAmplitude(profile));
            pioneerProcessSenary.set(SensorModel.latencySamples(profile));
            pioneerProcessEvidenceQuality.set(
                    (output == null ? transducer.outputQuality(level, blockPos) : output.quality()).ordinal());
            return;
        }

        if (block instanceof CopperWireBlock wire) {
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_WIRE);
            pioneerProcessPrimary.set(CopperWireBlock.voltage(level, blockPos));
            pioneerProcessSecondary.set(CopperWireBlock.driverCount(level, blockPos));
            pioneerProcessTertiary.set(wire.engineeringPorts(state).size());
            pioneerProcessEvidenceQuality.set(CopperWireBlock.quality(level, blockPos, state).ordinal());
            return;
        }

        if (block instanceof CopperVoltageSourceBlock source) {
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_VOLTAGE_SOURCE);
            pioneerProcessPrimary.set(state.getValue(CopperVoltageSourceBlock.VOLTAGE));
            pioneerProcessSecondary.set(source.engineeringPorts(state).size());
            pioneerProcessEvidenceQuality.set(PortQuality.VALID.ordinal());
            return;
        }

        if (block instanceof CopperResistiveLoadBlock load) {
            CopperNetworkSupport.TerminalInput terminal = CopperNetworkSupport.terminalInput(level, blockPos);
            double voltage = terminal.voltage();
            double resistance = state.getValue(CopperResistiveLoadBlock.RESISTANCE);
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_LOAD);
            pioneerProcessPrimary.set((int) Math.round(voltage));
            pioneerProcessSecondary.set((int) Math.round(resistance));
            pioneerProcessTertiary.set(syncNumber(CircuitPhysics.current(voltage, resistance) * 1000.0));
            pioneerProcessQuaternary.set(syncNumber(CircuitPhysics.power(voltage, resistance) * 1000.0));
            pioneerProcessQuinary.set(terminal.connectedFeeds());
            pioneerProcessEvidenceQuality.set(terminal.quality().ordinal());
            return;
        }

        if (block instanceof CopperSeriesResistorBlock resistor) {
            EngineeringPortSnapshot input = snapshotByDirection(resistor, state, PortDirection.INPUT);
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_SERIES_RESISTOR);
            pioneerProcessPrimary.set(input == null ? 0 : syncNumber(input.value()));
            pioneerProcessSecondary.set(state.getValue(CopperSeriesResistorBlock.RESISTANCE));
            pioneerProcessTertiary.set(CopperSeriesResistorBlock.loadResistanceMilli(level, blockPos));
            pioneerProcessQuaternary.set(CopperSeriesResistorBlock.outputVoltage(level, blockPos));
            pioneerProcessQuinary.set(CopperSeriesResistorBlock.currentMilli(level, blockPos));
            pioneerProcessSenary.set(CopperSeriesResistorBlock.outputInitialized(level, blockPos) ? 1 : 0);
            pioneerProcessEvidenceQuality.set(CopperSeriesResistorBlock.outputQuality(level, blockPos).ordinal());
            return;
        }

        if (block instanceof CopperCapacitorBlock capacitor) {
            EngineeringPortSnapshot input = snapshotByDirection(capacitor, state, PortDirection.INPUT);
            int index = state.getValue(CopperCapacitorBlock.C_INDEX);
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_CAPACITOR);
            pioneerProcessPrimary.set(input == null ? 0 : syncNumber(input.value()));
            pioneerProcessSecondary.set(index + 1);
            pioneerProcessTertiary.set(CopperCapacitorBlock.tauTicks(index));
            pioneerProcessQuaternary.set(CopperCapacitorBlock.chargePercent(level, blockPos));
            pioneerProcessQuinary.set(CopperCapacitorBlock.outputVoltage(level, blockPos));
            pioneerProcessSenary.set(CopperCapacitorBlock.outputInitialized(level, blockPos) ? 1 : 0);
            pioneerProcessEvidenceQuality.set(CopperCapacitorBlock.outputQuality(level, blockPos).ordinal());
            return;
        }

        if (block instanceof CopperFuseBlock fuse) {
            EngineeringPortSnapshot input = snapshotByDirection(fuse, state, PortDirection.INPUT);
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_FUSE);
            pioneerProcessPrimary.set(input == null ? 0 : syncNumber(input.value()));
            pioneerProcessSecondary.set(state.getValue(CopperFuseBlock.RATING));
            pioneerProcessTertiary.set(CopperFuseBlock.loadResistanceMilli(level, blockPos));
            pioneerProcessQuaternary.set(CopperFuseBlock.currentMilli(level, blockPos));
            pioneerProcessQuinary.set(CopperFuseBlock.outputVoltage(level, blockPos));
            pioneerProcessSenary.set(state.getValue(CopperFuseBlock.TRIPPED) ? 1 : 0);
            pioneerProcessEvidenceQuality.set(CopperFuseBlock.outputQuality(level, blockPos, state).ordinal());
            return;
        }

        if (block instanceof CopperCableJunctionBlock junction) {
            pioneerProcessKind.set(PIONEER_PROCESS_COPPER_JUNCTION);
            pioneerProcessPrimary.set(CopperCableJunctionBlock.voltage(level, blockPos));
            pioneerProcessSecondary.set(CopperCableJunctionBlock.driverCount(level, blockPos));
            pioneerProcessTertiary.set(junction.engineeringPorts(state).size());
            pioneerProcessEvidenceQuality.set(CopperCableJunctionBlock.quality(level, blockPos, state).ordinal());
            return;
        }

        if (block instanceof LapisNoiseSourceBlock noise) {
            pioneerProcessKind.set(PIONEER_PROCESS_LAPIS_NOISE);
            pioneerProcessPrimary.set(state.getValue(LapisNoiseSourceBlock.BASELINE) * 5);
            pioneerProcessSecondary.set(state.getValue(LapisNoiseSourceBlock.NOISE) * 2);
            pioneerProcessTertiary.set(LapisNoiseSourceBlock.currentValue(level, blockPos, state));
            pioneerProcessQuaternary.set(4);
            pioneerProcessQuinary.set(LapisNoiseSourceBlock.sampleInitialized(level, blockPos) ? 1 : 0);
            pioneerProcessEvidenceQuality.set((LapisNoiseSourceBlock.sampleInitialized(level, blockPos)
                    ? PortQuality.VALID : PortQuality.NOT_READY).ordinal());
            return;
        }

        if (block instanceof QuartzLabOscillatorBlock oscillator) {
            QuartzLabOscillatorBlock.TimingEvidence timing = QuartzLabOscillatorBlock.timingEvidence(level, blockPos, state);
            pioneerProcessKind.set(PIONEER_PROCESS_QUARTZ_OSCILLATOR);
            pioneerProcessPrimary.set(timing.nominalPeriod());
            pioneerProcessSecondary.set(state.getValue(QuartzLabOscillatorBlock.JITTER));
            pioneerProcessTertiary.set(timing.lastHalfInterval());
            pioneerProcessQuaternary.set(timing.lastJitterOffset());
            pioneerProcessQuinary.set(state.getValue(QuartzLabOscillatorBlock.ACTIVE) ? 1 : 0);
            pioneerProcessSenary.set(timing.available() ? 1 : 0);
            pioneerProcessEvidenceQuality.set((timing.available() ? PortQuality.VALID : PortQuality.NOT_READY).ordinal());
            return;
        }

        if (block instanceof QuartzPhaseDelayBlock delay) {
            EngineeringPortSnapshot input = snapshotByDirection(delay, state, PortDirection.INPUT);
            pioneerProcessKind.set(PIONEER_PROCESS_QUARTZ_PHASE_DELAY);
            pioneerProcessPrimary.set(input == null ? 0 : syncNumber(input.value()));
            pioneerProcessSecondary.set(state.getValue(QuartzPhaseDelayBlock.DELAY));
            pioneerProcessTertiary.set(QuartzPhaseDelayBlock.pendingTicks(level, blockPos));
            pioneerProcessQuaternary.set(QuartzPhaseDelayBlock.outputPulse(level, blockPos) ? 1 : 0);
            pioneerProcessQuinary.set(QuartzPhaseDelayBlock.initialized(level, blockPos) ? 1 : 0);
            pioneerProcessEvidenceQuality.set((input == null ? PortQuality.NO_SIGNAL : input.quality()).ordinal());
            return;
        }

        if (block instanceof QuartzTriggeredLapisSamplerBlock sampler) {
            EngineeringPortSnapshot input = snapshotByLabel(sampler, state, "LAPIS INPUT");
            EngineeringPortSnapshot trigger = snapshotByLabel(sampler, state, "QUARTZ TRIGGER");
            pioneerProcessKind.set(PIONEER_PROCESS_QUARTZ_LAPIS_SAMPLER);
            pioneerProcessPrimary.set(input == null ? 0 : syncNumber(input.value() * 100.0));
            pioneerProcessSecondary.set(trigger == null ? 0 : syncNumber(trigger.value()));
            pioneerProcessTertiary.set(QuartzTriggeredLapisSamplerBlock.heldValue(level, blockPos));
            pioneerProcessQuaternary.set(QuartzTriggeredLapisSamplerBlock.heldQuality(level, blockPos).ordinal());
            pioneerProcessEvidenceQuality.set(QuartzTriggeredLapisSamplerBlock.heldQuality(level, blockPos).ordinal());
            return;
        }

        if (block instanceof SoulFluxInjectorBlock injector) {
            var command = SoulFluxInjectorBlock.commandObservation(level, blockPos);
            pioneerProcessKind.set(PIONEER_PROCESS_SOUL_INJECTOR);
            pioneerProcessPrimary.set(command.value());
            pioneerProcessSecondary.set(command.valid() ? command.value() * 4 : 0);
            pioneerProcessTertiary.set(SoulFluxInjectorBlock.attachedNodeCount(level, blockPos));
            pioneerProcessQuaternary.set(5);
            pioneerProcessEvidenceQuality.set(command.quality().ordinal());
            return;
        }

        if (block instanceof SoulFluxMeterBlock meter) {
            SoulFluxMeterBlock.ChargeObservation observation = SoulFluxMeterBlock.inputObservation(level, blockPos, state);
            pioneerProcessKind.set(PIONEER_PROCESS_SOUL_METER);
            pioneerProcessPrimary.set(observation.value());
            pioneerProcessSecondary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            pioneerProcessTertiary.set(Math.min(15, (observation.value() * 15) / 100));
            pioneerProcessEvidenceQuality.set(observation.quality().ordinal());
            return;
        }

        if (block instanceof MolecularCloudReceiverBlock molecular) {
            MolecularCloudReceiverBlock.CloudSample sample = MolecularCloudReceiverBlock.sample(level, blockPos, state);
            EngineeringPortSnapshot output = snapshotByDirection(molecular, state, PortDirection.OUTPUT);
            int sensitivity = state.getValue(MolecularCloudReceiverBlock.SENSITIVITY);
            pioneerProcessKind.set(PIONEER_PROCESS_MOLECULAR_RECEIVER);
            pioneerProcessPrimary.set(sample.value());
            pioneerProcessSecondary.set(MolecularCloudReceiverBlock.filtered(level, blockPos));
            pioneerProcessTertiary.set(MolecularCloudReceiverBlock.peak(level, blockPos));
            pioneerProcessQuaternary.set(sensitivity);
            pioneerProcessQuinary.set(MolecularCloudReceiverBlock.gainFor(sensitivity));
            pioneerProcessSenary.set(state.getValue(DirectionalSignalBlock.OUTPUT));
            pioneerProcessEvidenceQuality.set((output == null ? PortQuality.NO_SIGNAL : output.quality()).ordinal());
            return;
        }

        if (block instanceof IronCoreBlock) {
            var applied = IronCoreBlock.appliedFieldSample(level, blockPos);
            pioneerProcessKind.set(PIONEER_PROCESS_IRON_CORE);
            pioneerProcessPrimary.set(applied.field());
            pioneerProcessSecondary.set(IronCoreBlock.magnetizeThreshold());
            pioneerProcessTertiary.set(IronCoreBlock.appliedFieldRadius());
            pioneerProcessQuaternary.set(state.getValue(IronCoreBlock.MAGNETIZED) ? 1 : 0);
            pioneerProcessQuinary.set(applied.complete() ? 1 : 0);
            pioneerProcessEvidenceQuality.set((applied.complete() ? PortQuality.VALID : PortQuality.STALE).ordinal());
            return;
        }

        if (block instanceof ThermalMassBlock) {
            ThermalMassBlock.ThermalState thermal = ThermalMassBlock.thermalState(level, blockPos, state);
            pioneerProcessKind.set(PIONEER_PROCESS_THERMAL_MASS);
            pioneerProcessPrimary.set(thermal.current());
            pioneerProcessSecondary.set(thermal.environment());
            pioneerProcessTertiary.set(thermal.neighborAverage());
            pioneerProcessQuaternary.set(thermal.target());
            pioneerProcessQuinary.set(thermal.capacity());
            pioneerProcessSenary.set(thermal.maxStep());
            pioneerProcessEvidenceQuality.set(PortQuality.VALID.ordinal());
            return;
        }

        if (block instanceof ThermalHeaterBlock) {
            CopperNetworkSupport.TerminalInput input = CopperNetworkSupport.terminalInput(level, blockPos);
            int voltage = input.quality() == PortQuality.VALID ? input.voltage() : 0;
            int resistance = ThermalHeaterBlock.resistance(state);
            pioneerProcessKind.set(PIONEER_PROCESS_THERMAL_HEATER);
            pioneerProcessPrimary.set(voltage);
            pioneerProcessSecondary.set(resistance);
            pioneerProcessTertiary.set(syncNumber(CircuitPhysics.current(voltage, resistance) * 1000.0));
            pioneerProcessQuaternary.set(syncNumber(CircuitPhysics.power(voltage, resistance) * 1000.0));
            pioneerProcessQuinary.set(state.getValue(ThermalHeaterBlock.TEMPERATURE));
            pioneerProcessSenary.set(ThermalHeaterBlock.targetTemperature(voltage, resistance));
            pioneerProcessEvidenceQuality.set(input.quality().ordinal());
            return;
        }

        if (block instanceof ThermalRadiatorBlock) {
            ThermalRadiatorBlock.CoolingObservation observation = ThermalRadiatorBlock.observation(level, blockPos);
            pioneerProcessKind.set(PIONEER_PROCESS_THERMAL_RADIATOR);
            pioneerProcessPrimary.set(state.getValue(ThermalRadiatorBlock.COOLING));
            pioneerProcessSecondary.set(observation.adjacentMasses());
            pioneerProcessTertiary.set(observation.averageTemperature());
            pioneerProcessQuaternary.set(observation.hottestTemperature());
            pioneerProcessQuinary.set(ThermalPhysics.AMBIENT);
            pioneerProcessSenary.set(10);
            pioneerProcessEvidenceQuality.set(
                    (observation.adjacentMasses() > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL).ordinal());
            return;
        }

        if (block instanceof ThermalCalorimeterBlock) {
            ThermalCalorimeterBlock.Sample sample = ThermalCalorimeterBlock.sample(level, blockPos);
            ThermalCalorimeterBlock.History history = ThermalCalorimeterBlock.history(level, blockPos);
            pioneerProcessKind.set(PIONEER_PROCESS_THERMAL_CALORIMETER);
            pioneerProcessPrimary.set(sample.temperature());
            pioneerProcessSecondary.set(history.deltaTemperature());
            pioneerProcessTertiary.set(sample.heatCapacity());
            pioneerProcessQuaternary.set(history.deltaTemperature() * sample.heatCapacity());
            pioneerProcessQuinary.set(sample.bodyCount());
            pioneerProcessSenary.set(history.initialized() ? 1 : 0);
            pioneerProcessEvidenceQuality.set(
                    (sample.bodyCount() == 0 ? PortQuality.NO_SIGNAL
                            : history.initialized() ? PortQuality.VALID : PortQuality.NOT_READY).ordinal());
            return;
        }

        if (block instanceof SoulSoilConduitBlock conduit) {
            var flux = SoulFluxNetwork.chargeSnapshot(level, blockPos);
            int charge = Math.max(0, Math.min(100, flux.value()));
            pioneerProcessKind.set(PIONEER_PROCESS_SOUL_CONDUIT);
            pioneerProcessPrimary.set(charge);
            pioneerProcessSecondary.set(flux.ageTicks());
            pioneerProcessTertiary.set(flux.qualityPercent());
            pioneerProcessQuaternary.set(SoulSoilConduitBlock.decayPeriodTicks());
            pioneerProcessQuinary.set(conduit.engineeringPorts(state).size());
            pioneerProcessEvidenceQuality.set(
                    (flux.valid() && flux.qualityPercent() > 0 && charge > 0
                            ? PortQuality.VALID : PortQuality.NO_SIGNAL).ordinal());
            return;
        }

        if (block instanceof SoulSandReservoirBlock reservoir) {
            var stored = SoulFluxNetwork.chargeSnapshot(level, blockPos);
            int charge = Math.max(0, Math.min(100, stored.value()));
            pioneerProcessKind.set(PIONEER_PROCESS_SOUL_RESERVOIR);
            pioneerProcessPrimary.set(charge);
            pioneerProcessSecondary.set(stored.ageTicks());
            pioneerProcessTertiary.set(stored.qualityPercent());
            pioneerProcessQuaternary.set(SoulSandReservoirBlock.decayPeriodTicks());
            pioneerProcessQuinary.set(reservoir.engineeringPorts(state).size());
            pioneerProcessEvidenceQuality.set(
                    (stored.ageTicks() < 0 ? PortQuality.STALE
                            : stored.valid() ? PortQuality.VALID : PortQuality.FAULT).ordinal());
        }
    }

    private EngineeringPortSnapshot snapshotByLabel(
            EngineeringPortProvider provider, BlockState state, String label
    ) {
        for (var port : provider.engineeringPorts(state)) {
            if (!label.equals(port.label())) continue;
            return provider.engineeringSnapshot(level, blockPos, state, port.side()).orElse(null);
        }
        return null;
    }

    private EngineeringPortSnapshot snapshotByDirection(
            EngineeringPortProvider provider, BlockState state, PortDirection direction
    ) {
        for (var port : provider.engineeringPorts(state)) {
            if (port.direction() != direction) continue;
            return provider.engineeringSnapshot(level, blockPos, state, port.side()).orElse(null);
        }
        return null;
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
                || block instanceof QuartzTriggeredLapisSamplerBlock
                || block instanceof SoulFluxInjectorBlock) return ROUTE_MULTI_PORT_LAYOUT;
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
        if (id >= BUTTON_CONFIG_PRIMARY_DIRECT_BASE && id <= BUTTON_CONFIG_PRIMARY_DIRECT_MAX) {
            boolean changed = applyDirectPrimaryTarget(id - BUTTON_CONFIG_PRIMARY_DIRECT_BASE);
            if (changed) {
                refreshAuthoritativeSnapshot();
                broadcastChanges();
            }
            return changed;
        }
        if (id >= BUTTON_CONFIG_SECONDARY_DIRECT_BASE && id <= BUTTON_CONFIG_SECONDARY_DIRECT_MAX) {
            boolean changed = applyDirectSecondaryTarget(id - BUTTON_CONFIG_SECONDARY_DIRECT_BASE);
            if (changed) {
                refreshAuthoritativeSnapshot();
                broadcastChanges();
            }
            return changed;
        }
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

    /**
     * Direct numeric entry remains server-authoritative: the client only sends a bounded integer intent.
     * We deliberately reuse each block's existing adjustment method so all invalidation, recomputation,
     * scheduling and evidence semantics remain in one place.
     */
    private boolean applyDirectPrimaryTarget(int target) {
        Block block = level.getBlockState(blockPos).getBlock();
        int min;
        int max;
        java.util.function.IntSupplier current;
        java.util.function.IntPredicate stepForward;

        if (block instanceof CopperVoltageSourceBlock source) {
            min = 0; max = 15;
            current = () -> level.getBlockState(blockPos).getValue(CopperVoltageSourceBlock.VOLTAGE);
            stepForward = ignored -> source.adjustVoltage(level, blockPos, 1);
        } else if (block instanceof CopperResistiveLoadBlock load) {
            min = 1; max = 15;
            current = () -> level.getBlockState(blockPos).getValue(CopperResistiveLoadBlock.RESISTANCE);
            stepForward = ignored -> load.adjustResistance(level, blockPos, 1);
        } else if (block instanceof CopperSeriesResistorBlock resistor) {
            min = 1; max = 15;
            current = () -> level.getBlockState(blockPos).getValue(CopperSeriesResistorBlock.RESISTANCE);
            stepForward = ignored -> resistor.adjustResistance(level, blockPos, 1);
        } else if (block instanceof CopperFuseBlock fuse) {
            min = 1; max = 15;
            current = () -> level.getBlockState(blockPos).getValue(CopperFuseBlock.RATING);
            stepForward = ignored -> fuse.adjustRating(level, blockPos, 1);
        } else if (block instanceof LapisNoiseSourceBlock noise) {
            min = 0; max = 20;
            current = () -> level.getBlockState(blockPos).getValue(LapisNoiseSourceBlock.BASELINE);
            stepForward = ignored -> noise.adjustBaseline(level, blockPos, 1);
        } else if (block instanceof QuartzPhaseDelayBlock delay) {
            min = 1; max = 16;
            current = () -> level.getBlockState(blockPos).getValue(QuartzPhaseDelayBlock.DELAY);
            stepForward = ignored -> delay.adjustDelay(level, blockPos, 1);
        } else if (block instanceof ThermalMassBlock mass) {
            min = 1; max = 4;
            current = () -> level.getBlockState(blockPos).getValue(ThermalMassBlock.HEAT_CAPACITY);
            stepForward = ignored -> mass.adjustHeatCapacity(level, blockPos, 1);
        } else if (block instanceof ThermalRadiatorBlock radiator) {
            min = 1; max = 4;
            current = () -> level.getBlockState(blockPos).getValue(ThermalRadiatorBlock.COOLING);
            stepForward = ignored -> radiator.adjustCooling(level, blockPos, 1);
        } else {
            return false;
        }

        if (target < min || target > max) return false;
        int guard = max - min + 2;
        boolean changed = false;
        while (current.getAsInt() != target && guard-- > 0) {
            if (!stepForward.test(1)) return changed;
            changed = true;
        }
        return changed || current.getAsInt() == target;
    }

    private boolean applyDirectSecondaryTarget(int target) {
        Block block = level.getBlockState(blockPos).getBlock();
        int min;
        int max;
        java.util.function.IntSupplier current;
        java.util.function.IntPredicate stepForward;

        if (block instanceof LapisNoiseSourceBlock noise) {
            min = 0; max = 10;
            current = () -> level.getBlockState(blockPos).getValue(LapisNoiseSourceBlock.NOISE);
            stepForward = ignored -> noise.adjustNoise(level, blockPos, 1);
        } else if (block instanceof QuartzLabOscillatorBlock oscillator) {
            min = 0; max = 3;
            current = () -> level.getBlockState(blockPos).getValue(QuartzLabOscillatorBlock.JITTER);
            stepForward = ignored -> oscillator.adjustJitter(level, blockPos, 1);
        } else {
            return false;
        }

        if (target < min || target > max) return false;
        int guard = max - min + 2;
        boolean changed = false;
        while (current.getAsInt() != target && guard-- > 0) {
            if (!stepForward.test(1)) return changed;
            changed = true;
        }
        return changed || current.getAsInt() == target;
    }

    private boolean adjustPrimary(int delta) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof AbstractLapisTransducerBlock transducer) return transducer.adjustProfile(level, blockPos, delta);
        if (block instanceof MolecularCloudReceiverBlock receiver) return receiver.adjustSensitivity(level, blockPos, delta);
        if (block instanceof AlarmProcessorBlock alarm) return alarm.adjustSeverity(level, blockPos, delta);
        if (block instanceof SampleHoldBlock sampleHold) return sampleHold.adjustTriggerMode(level, blockPos, delta);
        if (block instanceof CalibrationModuleBlock calibration) return calibration.adjustProfile(level, blockPos, delta);
        if (block instanceof PwmControllerBlock pwm) return pwm.adjustPeriodMode(level, blockPos, delta);
        if (block instanceof FaultInjectorBlock faultInjector) return faultInjector.adjustMode(level, blockPos, delta);
        if (block instanceof CopperVoltageSourceBlock source) return source.adjustVoltage(level, blockPos, delta);
        if (block instanceof CopperResistiveLoadBlock load) return load.adjustResistance(level, blockPos, delta);
        if (block instanceof CopperSeriesResistorBlock resistor) return resistor.adjustResistance(level, blockPos, delta);
        if (block instanceof CopperCapacitorBlock capacitor) return capacitor.adjustCapacitance(level, blockPos, delta);
        if (block instanceof CopperFuseBlock fuse) return fuse.adjustRating(level, blockPos, delta);
        if (block instanceof LapisNoiseSourceBlock noise) return noise.adjustBaseline(level, blockPos, delta);
        if (block instanceof QuartzLabOscillatorBlock oscillator) return oscillator.adjustPeriod(level, blockPos, delta);
        if (block instanceof QuartzPhaseDelayBlock delay) return delay.adjustDelay(level, blockPos, delta);
        if (block instanceof ThermalMassBlock mass) return mass.adjustHeatCapacity(level, blockPos, delta);
        if (block instanceof ThermalHeaterBlock heater) return heater.adjustResistance(level, blockPos, delta);
        if (block instanceof ThermalRadiatorBlock radiator) return radiator.adjustCooling(level, blockPos, delta);
        return false;
    }

    private boolean adjustSecondary(int delta) {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof LapisPrecisionRangeSensorBlock range) return range.adjustRange(level, blockPos, delta);
        if (block instanceof LapisNoiseSourceBlock noise) return noise.adjustNoise(level, blockPos, delta);
        if (block instanceof QuartzLabOscillatorBlock oscillator) return oscillator.adjustJitter(level, blockPos, delta);
        return false;
    }

    private boolean runAction() {
        Block block = level.getBlockState(blockPos).getBlock();
        if (block instanceof MolecularCloudReceiverBlock receiver) return receiver.resetHistory(level, blockPos);
        if (block instanceof AlarmProcessorBlock alarm) return alarm.acknowledge(level, blockPos);
        if (block instanceof SampleHoldBlock sampleHold) return sampleHold.clearHeldValue(level, blockPos);
        if (block instanceof FaultInjectorBlock faultInjector) return faultInjector.resetDiagnostics(level, blockPos);
        if (block instanceof SequenceControllerBlock sequence) return sequence.operatorReset(level, blockPos);
        if (block instanceof SafetyInterlockBlock interlock) return interlock.resetDiagnostics(level, blockPos);
        if (block instanceof TopologyDebuggerBlock debugger) return debugger.resetDiagnostics(level, blockPos);
        if (block instanceof CopperFuseBlock fuse) return fuse.resetTrip(level, blockPos);
        if (block instanceof IronCoreBlock core) return core.demagnetize(level, blockPos);
        return false;
    }

    private boolean toggleConfig() {
        Block block = level.getBlockState(blockPos).getBlock();
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
    public int pioneerMeasurementKind() { return pioneerMeasurementKind.get(); }
    public int pioneerProcessKind() { return pioneerProcessKind.get(); }
    public int pioneerProcessPrimary() { return pioneerProcessPrimary.get(); }
    public int pioneerProcessSecondary() { return pioneerProcessSecondary.get(); }
    public int pioneerProcessTertiary() { return pioneerProcessTertiary.get(); }
    public int pioneerProcessQuaternary() { return pioneerProcessQuaternary.get(); }
    public int pioneerProcessQuinary() { return pioneerProcessQuinary.get(); }
    public int pioneerProcessSenary() { return pioneerProcessSenary.get(); }
    public PortQuality pioneerProcessEvidenceQuality() {
        int ordinal = pioneerProcessEvidenceQuality.get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }
    public int pioneerPrimary() { return pioneerPrimary.get(); }
    public int pioneerSecondary() { return pioneerSecondary.get(); }
    public int pioneerTertiary() { return pioneerTertiary.get(); }
    public int pioneerQuaternary() { return pioneerQuaternary.get(); }
    public PortQuality pioneerEvidenceQuality() {
        int ordinal = pioneerEvidenceQuality.get();
        PortQuality[] all = PortQuality.values();
        return ordinal < 0 || ordinal >= all.length ? PortQuality.NO_SIGNAL : all[ordinal];
    }
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
