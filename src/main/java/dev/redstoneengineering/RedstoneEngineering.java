package dev.redstoneengineering;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.block.*;
import dev.redstoneengineering.blockentity.LogicAnalyzerBlockEntity;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Mod(RedstoneEngineering.MOD_ID)
public final class RedstoneEngineering {
    public static final String MOD_ID = "redstoneengineering";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES =
            DeferredRegister.create(
                    BuiltInRegistries.BLOCK_TYPE,
                    MOD_ID
            );

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>>
            BLOCK_ENTITY_TYPES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    MOD_ID
            );

    public static final DeferredRegister<CreativeModeTab>
            CREATIVE_TABS =
            DeferredRegister.create(
                    Registries.CREATIVE_MODE_TAB,
                    MOD_ID
            );

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SignalAnalyzerBlock>>
            SIGNAL_ANALYZER_CODEC =
            codec("signal_analyzer", SignalAnalyzerBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SignalProbeBlock>>
            SIGNAL_PROBE_CODEC =
            codec("signal_probe", SignalProbeBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<InstrumentCableBlock>>
            INSTRUMENT_CABLE_CODEC =
            codec("instrument_cable", InstrumentCableBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OscilloscopeBlock>>
            OSCILLOSCOPE_CODEC =
            codec("oscilloscope", OscilloscopeBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LogicAnalyzerBlock>>
            LOGIC_ANALYZER_CODEC =
            codec("logic_analyzer", LogicAnalyzerBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SignalConditionerBlock>>
            SIGNAL_CONDITIONER_CODEC =
            codec("signal_conditioner", SignalConditionerBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CalibrationModuleBlock>>
            CALIBRATION_MODULE_CODEC =
            codec("calibration_module", CalibrationModuleBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PrecisionFilterBlock>>
            PRECISION_FILTER_CODEC =
            codec("precision_filter", PrecisionFilterBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SampleHoldBlock>>
            SAMPLE_HOLD_CODEC =
            codec("sample_hold", SampleHoldBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<EdgeDetectorBlock>>
            EDGE_DETECTOR_CODEC =
            codec("edge_detector", EdgeDetectorBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PulseShaperBlock>>
            PULSE_SHAPER_CODEC =
            codec("pulse_shaper", PulseShaperBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PwmControllerBlock>>
            PWM_CONTROLLER_CODEC =
            codec("pwm_controller", PwmControllerBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SignalTapBlock>>
            SIGNAL_TAP_CODEC =
            codec("signal_tap", SignalTapBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RangeSensorBlock>>
            RANGE_SENSOR_CODEC =
            codec("range_sensor", RangeSensorBlock::new);

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisSignalLineBlock>> LAPIS_SIGNAL_LINE_CODEC = codec("lapis_signal_line", LapisSignalLineBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisPrecisionSourceBlock>> LAPIS_PRECISION_SOURCE_CODEC = codec("lapis_precision_source", LapisPrecisionSourceBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzTimingLineBlock>> QUARTZ_TIMING_LINE_CODEC = codec("quartz_timing_line", QuartzTimingLineBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzOscillatorBlock>> QUARTZ_OSCILLATOR_CODEC = codec("quartz_oscillator", QuartzOscillatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AmethystResonanceDustBlock>> AMETHYST_RESONANCE_DUST_CODEC = codec("amethyst_resonance_dust", AmethystResonanceDustBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AmethystResonatorBlock>> AMETHYST_RESONATOR_CODEC = codec("amethyst_resonator", AmethystResonatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalFiberBlock>> OPTICAL_FIBER_CODEC = codec("optical_fiber", OpticalFiberBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalEmitterBlock>> OPTICAL_EMITTER_CODEC = codec("optical_emitter", OpticalEmitterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalReceiverBlock>> OPTICAL_RECEIVER_CODEC = codec("optical_receiver", OpticalReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperWireBlock>> COPPER_WIRE_CODEC = codec("copper_wire", CopperWireBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperVoltageSourceBlock>> COPPER_VOLTAGE_SOURCE_CODEC = codec("copper_voltage_source", CopperVoltageSourceBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperResistiveLoadBlock>> COPPER_RESISTIVE_LOAD_CODEC = codec("copper_resistive_load", CopperResistiveLoadBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<IronCoreBlock>> IRON_CORE_CODEC = codec("iron_core", IronCoreBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ElectromagnetBlock>> ELECTROMAGNET_CODEC = codec("electromagnet", ElectromagnetBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<MagneticFieldSensorBlock>> MAGNETIC_FIELD_SENSOR_CODEC = codec("magnetic_field_sensor", MagneticFieldSensorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ThermalMassBlock>> THERMAL_MASS_CODEC = codec("thermal_mass", ThermalMassBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<TemperatureSensorBlock>> TEMPERATURE_SENSOR_CODEC = codec("temperature_sensor", TemperatureSensorBlock::new);

    // alpha.6 engineering-depth codecs
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisNoiseSourceBlock>> LAPIS_NOISE_SOURCE_CODEC = codec("lapis_noise_source", LapisNoiseSourceBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisLowPassFilterBlock>> LAPIS_LOW_PASS_FILTER_CODEC = codec("lapis_low_pass_filter", LapisLowPassFilterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisPrecisionMeterBlock>> LAPIS_PRECISION_METER_CODEC = codec("lapis_precision_meter", LapisPrecisionMeterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzLabOscillatorBlock>> QUARTZ_LAB_OSCILLATOR_CODEC = codec("quartz_lab_oscillator", QuartzLabOscillatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzClockDividerBlock>> QUARTZ_CLOCK_DIVIDER_CODEC = codec("quartz_clock_divider", QuartzClockDividerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzPhaseDelayBlock>> QUARTZ_PHASE_DELAY_CODEC = codec("quartz_phase_delay", QuartzPhaseDelayBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzStabilityMonitorBlock>> QUARTZ_STABILITY_MONITOR_CODEC = codec("quartz_stability_monitor", QuartzStabilityMonitorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AmethystFrequencyFilterBlock>> AMETHYST_FREQUENCY_FILTER_CODEC = codec("amethyst_frequency_filter", AmethystFrequencyFilterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AmethystTunedResonatorBlock>> AMETHYST_TUNED_RESONATOR_CODEC = codec("amethyst_tuned_resonator", AmethystTunedResonatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AmethystSpectrumAnalyzerBlock>> AMETHYST_SPECTRUM_ANALYZER_CODEC = codec("amethyst_spectrum_analyzer", AmethystSpectrumAnalyzerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalPowerMeterBlock>> OPTICAL_POWER_METER_CODEC = codec("optical_power_meter", OpticalPowerMeterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalSplitterBlock>> OPTICAL_SPLITTER_CODEC = codec("optical_splitter", OpticalSplitterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalChannelFilterBlock>> OPTICAL_CHANNEL_FILTER_CODEC = codec("optical_channel_filter", OpticalChannelFilterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalAttenuatorBlock>> OPTICAL_ATTENUATOR_CODEC = codec("optical_attenuator", OpticalAttenuatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperSeriesResistorBlock>> COPPER_SERIES_RESISTOR_CODEC = codec("copper_series_resistor", CopperSeriesResistorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperCapacitorBlock>> COPPER_CAPACITOR_CODEC = codec("copper_capacitor", CopperCapacitorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperFuseBlock>> COPPER_FUSE_CODEC = codec("copper_fuse", CopperFuseBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperCircuitMeterBlock>> COPPER_CIRCUIT_METER_CODEC = codec("copper_circuit_meter", CopperCircuitMeterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PermanentMagnetBlock>> PERMANENT_MAGNET_CODEC = codec("permanent_magnet", PermanentMagnetBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<InductionCoilBlock>> INDUCTION_COIL_CODEC = codec("induction_coil", InductionCoilBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<MagneticGradientMeterBlock>> MAGNETIC_GRADIENT_METER_CODEC = codec("magnetic_gradient_meter", MagneticGradientMeterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ThermalHeaterBlock>> THERMAL_HEATER_CODEC = codec("thermal_heater", ThermalHeaterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ThermalRadiatorBlock>> THERMAL_RADIATOR_CODEC = codec("thermal_radiator", ThermalRadiatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ThermalCalorimeterBlock>> THERMAL_CALORIMETER_CODEC = codec("thermal_calorimeter", ThermalCalorimeterBlock::new);

    // alpha.7 transmission/redstone/sensor codecs
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedstoneSignalCableBlock>> REDSTONE_SIGNAL_CABLE_CODEC = codec("redstone_signal_cable", RedstoneSignalCableBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedstoneCableTerminalBlock>> REDSTONE_CABLE_TERMINAL_CODEC = codec("redstone_cable_terminal", RedstoneCableTerminalBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedstoneReferenceSourceBlock>> REDSTONE_REFERENCE_SOURCE_CODEC = codec("redstone_reference_source", RedstoneReferenceSourceBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<EngineeringLightSensorBlock>> ENGINEERING_LIGHT_SENSOR_CODEC = codec("engineering_light_sensor", EngineeringLightSensorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<TankLevelSensorBlock>> TANK_LEVEL_SENSOR_CODEC = codec("tank_level_sensor", TankLevelSensorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<EntityDensitySensorBlock>> ENTITY_DENSITY_SENSOR_CODEC = codec("entity_density_sensor", EntityDensitySensorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AnalogIndicatorBlock>> ANALOG_INDICATOR_CODEC = codec("analog_indicator", AnalogIndicatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedstoneCableJunctionBlock>> REDSTONE_CABLE_JUNCTION_CODEC = codec("redstone_cable_junction", RedstoneCableJunctionBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OpticalFiberJunctionBlock>> OPTICAL_FIBER_JUNCTION_CODEC = codec("optical_fiber_junction", OpticalFiberJunctionBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<CopperCableJunctionBlock>> COPPER_CABLE_JUNCTION_CODEC = codec("copper_cable_junction", CopperCableJunctionBlock::new);


    // alpha.8 sensors + transducers codecs
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisTemperatureTransducerBlock>> LAPIS_TEMPERATURE_TRANSDUCER_CODEC = codec("lapis_temperature_transducer", LapisTemperatureTransducerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisMagneticTransducerBlock>> LAPIS_MAGNETIC_TRANSDUCER_CODEC = codec("lapis_magnetic_transducer", LapisMagneticTransducerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisOpticalTransducerBlock>> LAPIS_OPTICAL_TRANSDUCER_CODEC = codec("lapis_optical_transducer", LapisOpticalTransducerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisVoltageTransducerBlock>> LAPIS_VOLTAGE_TRANSDUCER_CODEC = codec("lapis_voltage_transducer", LapisVoltageTransducerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisPrecisionRangeSensorBlock>> LAPIS_PRECISION_RANGE_SENSOR_CODEC = codec("lapis_precision_range_sensor", LapisPrecisionRangeSensorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<LapisToRedstoneQuantizerBlock>> LAPIS_TO_REDSTONE_QUANTIZER_CODEC = codec("lapis_to_redstone_quantizer", LapisToRedstoneQuantizerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedstoneToLapisScalerBlock>> REDSTONE_TO_LAPIS_SCALER_CODEC = codec("redstone_to_lapis_scaler", RedstoneToLapisScalerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<QuartzTriggeredLapisSamplerBlock>> QUARTZ_TRIGGERED_LAPIS_SAMPLER_CODEC = codec("quartz_triggered_lapis_sampler", QuartzTriggeredLapisSamplerBlock::new);


    // Alpha 1.0 — communications, control, CPS, and Minecraft-native engineering codecs
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<EightBitDataBusBlock>> EIGHT_BIT_DATA_BUS_CODEC = codec("eight_bit_data_bus", EightBitDataBusBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedstoneByteEncoderBlock>> REDSTONE_BYTE_ENCODER_CODEC = codec("redstone_byte_encoder", RedstoneByteEncoderBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ByteToRedstoneDecoderBlock>> BYTE_TO_REDSTONE_DECODER_CODEC = codec("byte_to_redstone_decoder", ByteToRedstoneDecoderBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SerialDataLineBlock>> SERIAL_DATA_LINE_CODEC = codec("serial_data_line", SerialDataLineBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SerializerBlock>> SERIALIZER_CODEC = codec("serializer", SerializerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<DeserializerBlock>> DESERIALIZER_CODEC = codec("deserializer", DeserializerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<DifferentialDataPairBlock>> DIFFERENTIAL_DATA_PAIR_CODEC = codec("differential_data_pair", DifferentialDataPairBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<DigitalRegeneratorBlock>> DIGITAL_REGENERATOR_CODEC = codec("digital_regenerator", DigitalRegeneratorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<DifferentialDriverBlock>> DIFFERENTIAL_DRIVER_CODEC = codec("differential_driver", DifferentialDriverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<DifferentialReceiverBlock>> DIFFERENTIAL_RECEIVER_CODEC = codec("differential_receiver", DifferentialReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SculkVibrationInterfaceBlock>> SCULK_VIBRATION_INTERFACE_CODEC = codec("sculk_vibration_interface", SculkVibrationInterfaceBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PidControllerBlock>> PID_CONTROLLER_CODEC = codec("pid_controller", PidControllerBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<WatchdogBlock>> WATCHDOG_CODEC = codec("watchdog", WatchdogBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AirCompressorBlock>> AIR_COMPRESSOR_CODEC = codec("air_compressor", AirCompressorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticPipeBlock>> PNEUMATIC_PIPE_CODEC = codec("pneumatic_pipe", PneumaticPipeBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AirReservoirBlock>> AIR_RESERVOIR_CODEC = codec("air_reservoir", AirReservoirBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PressureRegulatorBlock>> PRESSURE_REGULATOR_CODEC = codec("pressure_regulator", PressureRegulatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticReceiverBlock>> PNEUMATIC_RECEIVER_CODEC = codec("pneumatic_receiver", PneumaticReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticValveBlock>> PNEUMATIC_VALVE_CODEC = codec("pneumatic_valve", PneumaticValveBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticCheckValveBlock>> PNEUMATIC_CHECK_VALVE_CODEC = codec("pneumatic_check_valve", PneumaticCheckValveBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticFlowMeterBlock>> PNEUMATIC_FLOW_METER_CODEC = codec("pneumatic_flow_meter", PneumaticFlowMeterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticProportionalValveBlock>> PNEUMATIC_PROPORTIONAL_VALVE_CODEC = codec("pneumatic_proportional_valve", PneumaticProportionalValveBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticReliefValveBlock>> PNEUMATIC_RELIEF_VALVE_CODEC = codec("pneumatic_relief_valve", PneumaticReliefValveBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PneumaticCylinderBlock>> PNEUMATIC_CYLINDER_CODEC = codec("pneumatic_cylinder", PneumaticCylinderBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SlimeVibrationConduitBlock>> SLIME_VIBRATION_CONDUIT_CODEC = codec("slime_vibration_conduit", SlimeVibrationConduitBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<HoneyVibrationDamperBlock>> HONEY_VIBRATION_DAMPER_CODEC = codec("honey_vibration_damper", HoneyVibrationDamperBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<MechanicalExciterBlock>> MECHANICAL_EXCITER_CODEC = codec("mechanical_exciter", MechanicalExciterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<MechanicalVibrationReceiverBlock>> MECHANICAL_VIBRATION_RECEIVER_CODEC = codec("mechanical_vibration_receiver", MechanicalVibrationReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<HydroacousticTubeBlock>> HYDROACOUSTIC_TUBE_CODEC = codec("hydroacoustic_tube", HydroacousticTubeBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<HydroacousticExciterBlock>> HYDROACOUSTIC_EXCITER_CODEC = codec("hydroacoustic_exciter", HydroacousticExciterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<HydroacousticReceiverBlock>> HYDROACOUSTIC_RECEIVER_CODEC = codec("hydroacoustic_receiver", HydroacousticReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RadioTransmitterBlock>> RADIO_TRANSMITTER_CODEC = codec("radio_transmitter", RadioTransmitterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RadioReceiverBlock>> RADIO_RECEIVER_CODEC = codec("radio_receiver", RadioReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<FreeSpaceOpticalTransmitterBlock>> FREE_SPACE_OPTICAL_TRANSMITTER_CODEC = codec("free_space_optical_transmitter", FreeSpaceOpticalTransmitterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<FreeSpaceOpticalReceiverBlock>> FREE_SPACE_OPTICAL_RECEIVER_CODEC = codec("free_space_optical_receiver", FreeSpaceOpticalReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SoulSoilConduitBlock>> SOUL_SOIL_CONDUIT_CODEC = codec("soul_soil_conduit", SoulSoilConduitBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SoulSandReservoirBlock>> SOUL_SAND_RESERVOIR_CODEC = codec("soul_sand_reservoir", SoulSandReservoirBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SoulFluxInjectorBlock>> SOUL_FLUX_INJECTOR_CODEC = codec("soul_flux_injector", SoulFluxInjectorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<SoulFluxMeterBlock>> SOUL_FLUX_METER_CODEC = codec("soul_flux_meter", SoulFluxMeterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<MolecularCloudReceiverBlock>> MOLECULAR_CLOUD_RECEIVER_CODEC = codec("molecular_cloud_receiver", MolecularCloudReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<PhononConduitBlock>> PHONON_CONDUIT_CODEC = codec("phonon_conduit", PhononConduitBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ThermalPulseEncoderBlock>> THERMAL_PULSE_ENCODER_CODEC = codec("thermal_pulse_encoder", ThermalPulseEncoderBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ThermalPulseReceiverBlock>> THERMAL_PULSE_RECEIVER_CODEC = codec("thermal_pulse_receiver", ThermalPulseReceiverBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ShieldedInstrumentCableBlock>> SHIELDED_INSTRUMENT_CABLE_CODEC = codec("shielded_instrument_cable", ShieldedInstrumentCableBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ServoActuatorBlock>> SERVO_ACTUATOR_CODEC = codec("servo_actuator", ServoActuatorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<ServoPositionSensorBlock>> SERVO_POSITION_SENSOR_CODEC = codec("servo_position_sensor", ServoPositionSensorBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<RedundantVoterBlock>> REDUNDANT_VOTER_CODEC = codec("redundant_voter", RedundantVoterBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<FaultLatchBlock>> FAULT_LATCH_CODEC = codec("fault_latch", FaultLatchBlock::new);
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<OperationsMonitorBlock>> OPERATIONS_MONITOR_CODEC = codec("operations_monitor", OperationsMonitorBlock::new);

    public static final DeferredBlock<SignalAnalyzerBlock> SIGNAL_ANALYZER =
            BLOCKS.registerBlock(
                    "signal_analyzer",
                    SignalAnalyzerBlock::new,
                    machineProps(MapColor.STONE)
            );

    public static final DeferredBlock<SignalProbeBlock> SIGNAL_PROBE =
            BLOCKS.registerBlock(
                    "signal_probe",
                    SignalProbeBlock::new,
                    smallInstrumentProps(MapColor.COLOR_RED)
            );

    public static final DeferredBlock<InstrumentCableBlock> INSTRUMENT_CABLE =
            BLOCKS.registerBlock(
                    "instrument_cable",
                    InstrumentCableBlock::new,
                    smallInstrumentProps(MapColor.COLOR_GRAY)
            );

    public static final DeferredBlock<OscilloscopeBlock> OSCILLOSCOPE =
            BLOCKS.registerBlock(
                    "oscilloscope",
                    OscilloscopeBlock::new,
                    machineProps(MapColor.COLOR_BLACK)
            );

    public static final DeferredBlock<LogicAnalyzerBlock> LOGIC_ANALYZER =
            BLOCKS.registerBlock(
                    "logic_analyzer",
                    LogicAnalyzerBlock::new,
                    machineProps(MapColor.COLOR_BLACK)
            );

    public static final DeferredBlock<SignalConditionerBlock> SIGNAL_CONDITIONER =
            BLOCKS.registerBlock(
                    "signal_conditioner",
                    SignalConditionerBlock::new,
                    machineProps(MapColor.COLOR_ORANGE)
            );

    public static final DeferredBlock<CalibrationModuleBlock> CALIBRATION_MODULE =
            BLOCKS.registerBlock(
                    "calibration_module",
                    CalibrationModuleBlock::new,
                    machineProps(MapColor.COLOR_YELLOW)
            );

    public static final DeferredBlock<PrecisionFilterBlock> PRECISION_FILTER =
            BLOCKS.registerBlock(
                    "precision_filter",
                    PrecisionFilterBlock::new,
                    machineProps(MapColor.COLOR_CYAN)
            );

    public static final DeferredBlock<SampleHoldBlock> SAMPLE_HOLD =
            BLOCKS.registerBlock(
                    "sample_hold",
                    SampleHoldBlock::new,
                    machineProps(MapColor.COLOR_PURPLE)
            );

    public static final DeferredBlock<EdgeDetectorBlock> EDGE_DETECTOR =
            BLOCKS.registerBlock(
                    "edge_detector",
                    EdgeDetectorBlock::new,
                    machineProps(MapColor.COLOR_GRAY)
            );

    public static final DeferredBlock<PulseShaperBlock> PULSE_SHAPER =
            BLOCKS.registerBlock(
                    "pulse_shaper",
                    PulseShaperBlock::new,
                    machineProps(MapColor.COLOR_PINK)
            );

    public static final DeferredBlock<PwmControllerBlock> PWM_CONTROLLER =
            BLOCKS.registerBlock(
                    "pwm_controller",
                    PwmControllerBlock::new,
                    machineProps(MapColor.COLOR_RED)
            );

    public static final DeferredBlock<SignalTapBlock> SIGNAL_TAP =
            BLOCKS.registerBlock(
                    "signal_tap",
                    SignalTapBlock::new,
                    machineProps(MapColor.COLOR_RED)
            );

    public static final DeferredBlock<RangeSensorBlock> RANGE_SENSOR =
            BLOCKS.registerBlock(
                    "range_sensor",
                    RangeSensorBlock::new,
                    machineProps(MapColor.COLOR_BLUE)
            );

    public static final DeferredBlock<LapisSignalLineBlock> LAPIS_SIGNAL_LINE = BLOCKS.registerBlock("lapis_signal_line", LapisSignalLineBlock::new, smallInstrumentProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<LapisPrecisionSourceBlock> LAPIS_PRECISION_SOURCE = BLOCKS.registerBlock("lapis_precision_source", LapisPrecisionSourceBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<QuartzTimingLineBlock> QUARTZ_TIMING_LINE = BLOCKS.registerBlock("quartz_timing_line", QuartzTimingLineBlock::new, smallInstrumentProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<QuartzOscillatorBlock> QUARTZ_OSCILLATOR = BLOCKS.registerBlock("quartz_oscillator", QuartzOscillatorBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<AmethystResonanceDustBlock> AMETHYST_RESONANCE_DUST = BLOCKS.registerBlock("amethyst_resonance_dust", AmethystResonanceDustBlock::new, smallInstrumentProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<AmethystResonatorBlock> AMETHYST_RESONATOR = BLOCKS.registerBlock("amethyst_resonator", AmethystResonatorBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<OpticalFiberBlock> OPTICAL_FIBER = BLOCKS.registerBlock("optical_fiber", OpticalFiberBlock::new, smallInstrumentProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<OpticalEmitterBlock> OPTICAL_EMITTER = BLOCKS.registerBlock("optical_emitter", OpticalEmitterBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<OpticalReceiverBlock> OPTICAL_RECEIVER = BLOCKS.registerBlock("optical_receiver", OpticalReceiverBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<CopperWireBlock> COPPER_WIRE = BLOCKS.registerBlock("copper_wire", CopperWireBlock::new, smallInstrumentProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<CopperVoltageSourceBlock> COPPER_VOLTAGE_SOURCE = BLOCKS.registerBlock("copper_voltage_source", CopperVoltageSourceBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<CopperResistiveLoadBlock> COPPER_RESISTIVE_LOAD = BLOCKS.registerBlock("copper_resistive_load", CopperResistiveLoadBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<IronCoreBlock> IRON_CORE = BLOCKS.registerBlock("iron_core", IronCoreBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<ElectromagnetBlock> ELECTROMAGNET = BLOCKS.registerBlock("electromagnet", ElectromagnetBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<MagneticFieldSensorBlock> MAGNETIC_FIELD_SENSOR = BLOCKS.registerBlock("magnetic_field_sensor", MagneticFieldSensorBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<ThermalMassBlock> THERMAL_MASS = BLOCKS.registerBlock("thermal_mass", ThermalMassBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<TemperatureSensorBlock> TEMPERATURE_SENSOR = BLOCKS.registerBlock("temperature_sensor", TemperatureSensorBlock::new, machineProps(MapColor.COLOR_RED));

    // alpha.6 engineering-depth blocks
    public static final DeferredBlock<LapisNoiseSourceBlock> LAPIS_NOISE_SOURCE = BLOCKS.registerBlock("lapis_noise_source", LapisNoiseSourceBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<LapisLowPassFilterBlock> LAPIS_LOW_PASS_FILTER = BLOCKS.registerBlock("lapis_low_pass_filter", LapisLowPassFilterBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<LapisPrecisionMeterBlock> LAPIS_PRECISION_METER = BLOCKS.registerBlock("lapis_precision_meter", LapisPrecisionMeterBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<QuartzLabOscillatorBlock> QUARTZ_LAB_OSCILLATOR = BLOCKS.registerBlock("quartz_lab_oscillator", QuartzLabOscillatorBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<QuartzClockDividerBlock> QUARTZ_CLOCK_DIVIDER = BLOCKS.registerBlock("quartz_clock_divider", QuartzClockDividerBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<QuartzPhaseDelayBlock> QUARTZ_PHASE_DELAY = BLOCKS.registerBlock("quartz_phase_delay", QuartzPhaseDelayBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<QuartzStabilityMonitorBlock> QUARTZ_STABILITY_MONITOR = BLOCKS.registerBlock("quartz_stability_monitor", QuartzStabilityMonitorBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<AmethystFrequencyFilterBlock> AMETHYST_FREQUENCY_FILTER = BLOCKS.registerBlock("amethyst_frequency_filter", AmethystFrequencyFilterBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<AmethystTunedResonatorBlock> AMETHYST_TUNED_RESONATOR = BLOCKS.registerBlock("amethyst_tuned_resonator", AmethystTunedResonatorBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<AmethystSpectrumAnalyzerBlock> AMETHYST_SPECTRUM_ANALYZER = BLOCKS.registerBlock("amethyst_spectrum_analyzer", AmethystSpectrumAnalyzerBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<OpticalPowerMeterBlock> OPTICAL_POWER_METER = BLOCKS.registerBlock("optical_power_meter", OpticalPowerMeterBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<OpticalSplitterBlock> OPTICAL_SPLITTER = BLOCKS.registerBlock("optical_splitter", OpticalSplitterBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<OpticalChannelFilterBlock> OPTICAL_CHANNEL_FILTER = BLOCKS.registerBlock("optical_channel_filter", OpticalChannelFilterBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<OpticalAttenuatorBlock> OPTICAL_ATTENUATOR = BLOCKS.registerBlock("optical_attenuator", OpticalAttenuatorBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<CopperSeriesResistorBlock> COPPER_SERIES_RESISTOR = BLOCKS.registerBlock("copper_series_resistor", CopperSeriesResistorBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<CopperCapacitorBlock> COPPER_CAPACITOR = BLOCKS.registerBlock("copper_capacitor", CopperCapacitorBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<CopperFuseBlock> COPPER_FUSE = BLOCKS.registerBlock("copper_fuse", CopperFuseBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<CopperCircuitMeterBlock> COPPER_CIRCUIT_METER = BLOCKS.registerBlock("copper_circuit_meter", CopperCircuitMeterBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<PermanentMagnetBlock> PERMANENT_MAGNET = BLOCKS.registerBlock("permanent_magnet", PermanentMagnetBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<InductionCoilBlock> INDUCTION_COIL = BLOCKS.registerBlock("induction_coil", InductionCoilBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<MagneticGradientMeterBlock> MAGNETIC_GRADIENT_METER = BLOCKS.registerBlock("magnetic_gradient_meter", MagneticGradientMeterBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<ThermalHeaterBlock> THERMAL_HEATER = BLOCKS.registerBlock("thermal_heater", ThermalHeaterBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<ThermalRadiatorBlock> THERMAL_RADIATOR = BLOCKS.registerBlock("thermal_radiator", ThermalRadiatorBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<ThermalCalorimeterBlock> THERMAL_CALORIMETER = BLOCKS.registerBlock("thermal_calorimeter", ThermalCalorimeterBlock::new, machineProps(MapColor.COLOR_RED));

    // alpha.7 transmission + Redstone core + sensors/actuator
    public static final DeferredBlock<RedstoneSignalCableBlock> REDSTONE_SIGNAL_CABLE = BLOCKS.registerBlock("redstone_signal_cable", RedstoneSignalCableBlock::new, smallInstrumentProps(MapColor.COLOR_RED));
    public static final DeferredBlock<RedstoneCableTerminalBlock> REDSTONE_CABLE_TERMINAL = BLOCKS.registerBlock("redstone_cable_terminal", RedstoneCableTerminalBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<RedstoneReferenceSourceBlock> REDSTONE_REFERENCE_SOURCE = BLOCKS.registerBlock("redstone_reference_source", RedstoneReferenceSourceBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<EngineeringLightSensorBlock> ENGINEERING_LIGHT_SENSOR = BLOCKS.registerBlock("engineering_light_sensor", EngineeringLightSensorBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<TankLevelSensorBlock> TANK_LEVEL_SENSOR = BLOCKS.registerBlock("tank_level_sensor", TankLevelSensorBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<EntityDensitySensorBlock> ENTITY_DENSITY_SENSOR = BLOCKS.registerBlock("entity_density_sensor", EntityDensitySensorBlock::new, machineProps(MapColor.COLOR_GREEN));
    public static final DeferredBlock<AnalogIndicatorBlock> ANALOG_INDICATOR = BLOCKS.registerBlock("analog_indicator", AnalogIndicatorBlock::new, analogIndicatorProps());
    public static final DeferredBlock<RedstoneCableJunctionBlock> REDSTONE_CABLE_JUNCTION = BLOCKS.registerBlock("redstone_cable_junction", RedstoneCableJunctionBlock::new, smallInstrumentProps(MapColor.COLOR_RED));
    public static final DeferredBlock<OpticalFiberJunctionBlock> OPTICAL_FIBER_JUNCTION = BLOCKS.registerBlock("optical_fiber_junction", OpticalFiberJunctionBlock::new, smallInstrumentProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<CopperCableJunctionBlock> COPPER_CABLE_JUNCTION = BLOCKS.registerBlock("copper_cable_junction", CopperCableJunctionBlock::new, smallInstrumentProps(MapColor.COLOR_ORANGE));


    // alpha.8 sensors + transducers
    public static final DeferredBlock<LapisTemperatureTransducerBlock> LAPIS_TEMPERATURE_TRANSDUCER = BLOCKS.registerBlock("lapis_temperature_transducer", LapisTemperatureTransducerBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<LapisMagneticTransducerBlock> LAPIS_MAGNETIC_TRANSDUCER = BLOCKS.registerBlock("lapis_magnetic_transducer", LapisMagneticTransducerBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<LapisOpticalTransducerBlock> LAPIS_OPTICAL_TRANSDUCER = BLOCKS.registerBlock("lapis_optical_transducer", LapisOpticalTransducerBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<LapisVoltageTransducerBlock> LAPIS_VOLTAGE_TRANSDUCER = BLOCKS.registerBlock("lapis_voltage_transducer", LapisVoltageTransducerBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<LapisPrecisionRangeSensorBlock> LAPIS_PRECISION_RANGE_SENSOR = BLOCKS.registerBlock("lapis_precision_range_sensor", LapisPrecisionRangeSensorBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<LapisToRedstoneQuantizerBlock> LAPIS_TO_REDSTONE_QUANTIZER = BLOCKS.registerBlock("lapis_to_redstone_quantizer", LapisToRedstoneQuantizerBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<RedstoneToLapisScalerBlock> REDSTONE_TO_LAPIS_SCALER = BLOCKS.registerBlock("redstone_to_lapis_scaler", RedstoneToLapisScalerBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<QuartzTriggeredLapisSamplerBlock> QUARTZ_TRIGGERED_LAPIS_SAMPLER = BLOCKS.registerBlock("quartz_triggered_lapis_sampler", QuartzTriggeredLapisSamplerBlock::new, machineProps(MapColor.COLOR_GRAY));

    public static final Supplier<BlockEntityType<OscilloscopeBlockEntity>>
            OSCILLOSCOPE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "oscilloscope",
                    () -> BlockEntityType.Builder
                            .of(
                                    OscilloscopeBlockEntity::new,
                                    OSCILLOSCOPE.get()
                            )
                            .build(null)
            );

    public static final Supplier<BlockEntityType<LogicAnalyzerBlockEntity>>
            LOGIC_ANALYZER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "logic_analyzer",
                    () -> BlockEntityType.Builder
                            .of(
                                    LogicAnalyzerBlockEntity::new,
                                    LOGIC_ANALYZER.get()
                            )
                            .build(null)
            );


    // Alpha 1.0 blocks
    public static final DeferredBlock<EightBitDataBusBlock> EIGHT_BIT_DATA_BUS = BLOCKS.registerBlock("eight_bit_data_bus", EightBitDataBusBlock::new, smallInstrumentProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<RedstoneByteEncoderBlock> REDSTONE_BYTE_ENCODER = BLOCKS.registerBlock("redstone_byte_encoder", RedstoneByteEncoderBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<ByteToRedstoneDecoderBlock> BYTE_TO_REDSTONE_DECODER = BLOCKS.registerBlock("byte_to_redstone_decoder", ByteToRedstoneDecoderBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<SerialDataLineBlock> SERIAL_DATA_LINE = BLOCKS.registerBlock("serial_data_line", SerialDataLineBlock::new, smallInstrumentProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<SerializerBlock> SERIALIZER = BLOCKS.registerBlock("serializer", SerializerBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<DeserializerBlock> DESERIALIZER = BLOCKS.registerBlock("deserializer", DeserializerBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<DifferentialDataPairBlock> DIFFERENTIAL_DATA_PAIR = BLOCKS.registerBlock("differential_data_pair", DifferentialDataPairBlock::new, smallInstrumentProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<DigitalRegeneratorBlock> DIGITAL_REGENERATOR = BLOCKS.registerBlock("digital_regenerator", DigitalRegeneratorBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<DifferentialDriverBlock> DIFFERENTIAL_DRIVER = BLOCKS.registerBlock("differential_driver", DifferentialDriverBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<DifferentialReceiverBlock> DIFFERENTIAL_RECEIVER = BLOCKS.registerBlock("differential_receiver", DifferentialReceiverBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<SculkVibrationInterfaceBlock> SCULK_VIBRATION_INTERFACE = BLOCKS.registerBlock("sculk_vibration_interface", SculkVibrationInterfaceBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<PidControllerBlock> PID_CONTROLLER = BLOCKS.registerBlock("pid_controller", PidControllerBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<WatchdogBlock> WATCHDOG = BLOCKS.registerBlock("watchdog", WatchdogBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<AirCompressorBlock> AIR_COMPRESSOR = BLOCKS.registerBlock("air_compressor", AirCompressorBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<PneumaticPipeBlock> PNEUMATIC_PIPE = BLOCKS.registerBlock("pneumatic_pipe", PneumaticPipeBlock::new, smallInstrumentProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<AirReservoirBlock> AIR_RESERVOIR = BLOCKS.registerBlock("air_reservoir", AirReservoirBlock::new, machineProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<PressureRegulatorBlock> PRESSURE_REGULATOR = BLOCKS.registerBlock("pressure_regulator", PressureRegulatorBlock::new, machineProps(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<PneumaticReceiverBlock> PNEUMATIC_RECEIVER = BLOCKS.registerBlock("pneumatic_receiver", PneumaticReceiverBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<PneumaticValveBlock> PNEUMATIC_VALVE = BLOCKS.registerBlock("pneumatic_valve", PneumaticValveBlock::new, machineProps(MapColor.METAL));
    public static final DeferredBlock<PneumaticCheckValveBlock> PNEUMATIC_CHECK_VALVE = BLOCKS.registerBlock("pneumatic_check_valve", PneumaticCheckValveBlock::new, machineProps(MapColor.METAL));
    public static final DeferredBlock<PneumaticFlowMeterBlock> PNEUMATIC_FLOW_METER = BLOCKS.registerBlock("pneumatic_flow_meter", PneumaticFlowMeterBlock::new, smallInstrumentProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<PneumaticProportionalValveBlock> PNEUMATIC_PROPORTIONAL_VALVE = BLOCKS.registerBlock("pneumatic_proportional_valve", PneumaticProportionalValveBlock::new, machineProps(MapColor.METAL));
    public static final DeferredBlock<PneumaticReliefValveBlock> PNEUMATIC_RELIEF_VALVE = BLOCKS.registerBlock("pneumatic_relief_valve", PneumaticReliefValveBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<PneumaticCylinderBlock> PNEUMATIC_CYLINDER = BLOCKS.registerBlock("pneumatic_cylinder", PneumaticCylinderBlock::new, machineProps(MapColor.METAL));
    public static final DeferredBlock<SlimeVibrationConduitBlock> SLIME_VIBRATION_CONDUIT = BLOCKS.registerBlock("slime_vibration_conduit", SlimeVibrationConduitBlock::new, smallInstrumentProps(MapColor.COLOR_GREEN));
    public static final DeferredBlock<HoneyVibrationDamperBlock> HONEY_VIBRATION_DAMPER = BLOCKS.registerBlock("honey_vibration_damper", HoneyVibrationDamperBlock::new, smallInstrumentProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<MechanicalExciterBlock> MECHANICAL_EXCITER = BLOCKS.registerBlock("mechanical_exciter", MechanicalExciterBlock::new, machineProps(MapColor.COLOR_GREEN));
    public static final DeferredBlock<MechanicalVibrationReceiverBlock> MECHANICAL_VIBRATION_RECEIVER = BLOCKS.registerBlock("mechanical_vibration_receiver", MechanicalVibrationReceiverBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<HydroacousticTubeBlock> HYDROACOUSTIC_TUBE = BLOCKS.registerBlock("hydroacoustic_tube", HydroacousticTubeBlock::new, smallInstrumentProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<HydroacousticExciterBlock> HYDROACOUSTIC_EXCITER = BLOCKS.registerBlock("hydroacoustic_exciter", HydroacousticExciterBlock::new, machineProps(MapColor.COLOR_BLUE));
    public static final DeferredBlock<HydroacousticReceiverBlock> HYDROACOUSTIC_RECEIVER = BLOCKS.registerBlock("hydroacoustic_receiver", HydroacousticReceiverBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<RadioTransmitterBlock> RADIO_TRANSMITTER = BLOCKS.registerBlock("radio_transmitter", RadioTransmitterBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<RadioReceiverBlock> RADIO_RECEIVER = BLOCKS.registerBlock("radio_receiver", RadioReceiverBlock::new, machineProps(MapColor.COLOR_PURPLE));
    public static final DeferredBlock<FreeSpaceOpticalTransmitterBlock> FREE_SPACE_OPTICAL_TRANSMITTER = BLOCKS.registerBlock("free_space_optical_transmitter", FreeSpaceOpticalTransmitterBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<FreeSpaceOpticalReceiverBlock> FREE_SPACE_OPTICAL_RECEIVER = BLOCKS.registerBlock("free_space_optical_receiver", FreeSpaceOpticalReceiverBlock::new, machineProps(MapColor.COLOR_YELLOW));
    public static final DeferredBlock<SoulSoilConduitBlock> SOUL_SOIL_CONDUIT = BLOCKS.registerBlock("soul_soil_conduit", SoulSoilConduitBlock::new, smallInstrumentProps(MapColor.COLOR_BROWN));
    public static final DeferredBlock<SoulSandReservoirBlock> SOUL_SAND_RESERVOIR = BLOCKS.registerBlock("soul_sand_reservoir", SoulSandReservoirBlock::new, machineProps(MapColor.COLOR_BROWN));
    public static final DeferredBlock<SoulFluxInjectorBlock> SOUL_FLUX_INJECTOR = BLOCKS.registerBlock("soul_flux_injector", SoulFluxInjectorBlock::new, machineProps(MapColor.COLOR_BLACK));
    public static final DeferredBlock<SoulFluxMeterBlock> SOUL_FLUX_METER = BLOCKS.registerBlock("soul_flux_meter", SoulFluxMeterBlock::new, machineProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<MolecularCloudReceiverBlock> MOLECULAR_CLOUD_RECEIVER = BLOCKS.registerBlock("molecular_cloud_receiver", MolecularCloudReceiverBlock::new, machineProps(MapColor.COLOR_GREEN));
    public static final DeferredBlock<PhononConduitBlock> PHONON_CONDUIT = BLOCKS.registerBlock("phonon_conduit", PhononConduitBlock::new, smallInstrumentProps(MapColor.COLOR_BLACK));
    public static final DeferredBlock<ThermalPulseEncoderBlock> THERMAL_PULSE_ENCODER = BLOCKS.registerBlock("thermal_pulse_encoder", ThermalPulseEncoderBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<ThermalPulseReceiverBlock> THERMAL_PULSE_RECEIVER = BLOCKS.registerBlock("thermal_pulse_receiver", ThermalPulseReceiverBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<ShieldedInstrumentCableBlock> SHIELDED_INSTRUMENT_CABLE = BLOCKS.registerBlock("shielded_instrument_cable", ShieldedInstrumentCableBlock::new, smallInstrumentProps(MapColor.COLOR_GRAY));
    public static final DeferredBlock<ServoActuatorBlock> SERVO_ACTUATOR = BLOCKS.registerBlock("servo_actuator", ServoActuatorBlock::new, machineProps(MapColor.METAL));
    public static final DeferredBlock<ServoPositionSensorBlock> SERVO_POSITION_SENSOR = BLOCKS.registerBlock("servo_position_sensor", ServoPositionSensorBlock::new, smallInstrumentProps(MapColor.COLOR_CYAN));
    public static final DeferredBlock<RedundantVoterBlock> REDUNDANT_VOTER = BLOCKS.registerBlock("redundant_voter", RedundantVoterBlock::new, machineProps(MapColor.COLOR_LIGHT_GREEN));
    public static final DeferredBlock<FaultLatchBlock> FAULT_LATCH = BLOCKS.registerBlock("fault_latch", FaultLatchBlock::new, machineProps(MapColor.COLOR_RED));
    public static final DeferredBlock<OperationsMonitorBlock> OPERATIONS_MONITOR = BLOCKS.registerBlock("operations_monitor", OperationsMonitorBlock::new, machineProps(MapColor.COLOR_BLUE));

    public static final DeferredItem<BlockItem> SIGNAL_ANALYZER_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "signal_analyzer",
                    SIGNAL_ANALYZER
            );

    public static final DeferredItem<BlockItem> SIGNAL_PROBE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "signal_probe",
                    SIGNAL_PROBE
            );

    public static final DeferredItem<BlockItem> INSTRUMENT_CABLE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "instrument_cable",
                    INSTRUMENT_CABLE
            );

    public static final DeferredItem<BlockItem> OSCILLOSCOPE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "oscilloscope",
                    OSCILLOSCOPE
            );

    public static final DeferredItem<BlockItem> LOGIC_ANALYZER_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "logic_analyzer",
                    LOGIC_ANALYZER
            );

    public static final DeferredItem<BlockItem> SIGNAL_CONDITIONER_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "signal_conditioner",
                    SIGNAL_CONDITIONER
            );

    public static final DeferredItem<BlockItem> CALIBRATION_MODULE_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "calibration_module",
                    CALIBRATION_MODULE
            );

    public static final DeferredItem<BlockItem> PRECISION_FILTER_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "precision_filter",
                    PRECISION_FILTER
            );

    public static final DeferredItem<BlockItem> SAMPLE_HOLD_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "sample_hold",
                    SAMPLE_HOLD
            );

    public static final DeferredItem<BlockItem> EDGE_DETECTOR_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "edge_detector",
                    EDGE_DETECTOR
            );

    public static final DeferredItem<BlockItem> PULSE_SHAPER_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "pulse_shaper",
                    PULSE_SHAPER
            );

    public static final DeferredItem<BlockItem> PWM_CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "pwm_controller",
                    PWM_CONTROLLER
            );

    public static final DeferredItem<BlockItem> SIGNAL_TAP_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "signal_tap",
                    SIGNAL_TAP
            );

    public static final DeferredItem<BlockItem> RANGE_SENSOR_ITEM =
            ITEMS.registerSimpleBlockItem(
                    "range_sensor",
                    RANGE_SENSOR
            );

    public static final DeferredItem<BlockItem> LAPIS_SIGNAL_LINE_ITEM = ITEMS.registerSimpleBlockItem("lapis_signal_line", LAPIS_SIGNAL_LINE);
    public static final DeferredItem<BlockItem> LAPIS_PRECISION_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("lapis_precision_source", LAPIS_PRECISION_SOURCE);
    public static final DeferredItem<BlockItem> QUARTZ_TIMING_LINE_ITEM = ITEMS.registerSimpleBlockItem("quartz_timing_line", QUARTZ_TIMING_LINE);
    public static final DeferredItem<BlockItem> QUARTZ_OSCILLATOR_ITEM = ITEMS.registerSimpleBlockItem("quartz_oscillator", QUARTZ_OSCILLATOR);
    public static final DeferredItem<BlockItem> AMETHYST_RESONANCE_DUST_ITEM = ITEMS.registerSimpleBlockItem("amethyst_resonance_dust", AMETHYST_RESONANCE_DUST);
    public static final DeferredItem<BlockItem> AMETHYST_RESONATOR_ITEM = ITEMS.registerSimpleBlockItem("amethyst_resonator", AMETHYST_RESONATOR);
    public static final DeferredItem<BlockItem> OPTICAL_FIBER_ITEM = ITEMS.registerSimpleBlockItem("optical_fiber", OPTICAL_FIBER);
    public static final DeferredItem<BlockItem> OPTICAL_EMITTER_ITEM = ITEMS.registerSimpleBlockItem("optical_emitter", OPTICAL_EMITTER);
    public static final DeferredItem<BlockItem> OPTICAL_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("optical_receiver", OPTICAL_RECEIVER);
    public static final DeferredItem<BlockItem> COPPER_WIRE_ITEM = ITEMS.registerSimpleBlockItem("copper_wire", COPPER_WIRE);
    public static final DeferredItem<BlockItem> COPPER_VOLTAGE_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("copper_voltage_source", COPPER_VOLTAGE_SOURCE);
    public static final DeferredItem<BlockItem> COPPER_RESISTIVE_LOAD_ITEM = ITEMS.registerSimpleBlockItem("copper_resistive_load", COPPER_RESISTIVE_LOAD);
    public static final DeferredItem<BlockItem> IRON_CORE_ITEM = ITEMS.registerSimpleBlockItem("iron_core", IRON_CORE);
    public static final DeferredItem<BlockItem> ELECTROMAGNET_ITEM = ITEMS.registerSimpleBlockItem("electromagnet", ELECTROMAGNET);
    public static final DeferredItem<BlockItem> MAGNETIC_FIELD_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("magnetic_field_sensor", MAGNETIC_FIELD_SENSOR);
    public static final DeferredItem<BlockItem> THERMAL_MASS_ITEM = ITEMS.registerSimpleBlockItem("thermal_mass", THERMAL_MASS);
    public static final DeferredItem<BlockItem> TEMPERATURE_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("temperature_sensor", TEMPERATURE_SENSOR);

    // alpha.6 engineering-depth items
    public static final DeferredItem<BlockItem> LAPIS_NOISE_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("lapis_noise_source", LAPIS_NOISE_SOURCE);
    public static final DeferredItem<BlockItem> LAPIS_LOW_PASS_FILTER_ITEM = ITEMS.registerSimpleBlockItem("lapis_low_pass_filter", LAPIS_LOW_PASS_FILTER);
    public static final DeferredItem<BlockItem> LAPIS_PRECISION_METER_ITEM = ITEMS.registerSimpleBlockItem("lapis_precision_meter", LAPIS_PRECISION_METER);
    public static final DeferredItem<BlockItem> QUARTZ_LAB_OSCILLATOR_ITEM = ITEMS.registerSimpleBlockItem("quartz_lab_oscillator", QUARTZ_LAB_OSCILLATOR);
    public static final DeferredItem<BlockItem> QUARTZ_CLOCK_DIVIDER_ITEM = ITEMS.registerSimpleBlockItem("quartz_clock_divider", QUARTZ_CLOCK_DIVIDER);
    public static final DeferredItem<BlockItem> QUARTZ_PHASE_DELAY_ITEM = ITEMS.registerSimpleBlockItem("quartz_phase_delay", QUARTZ_PHASE_DELAY);
    public static final DeferredItem<BlockItem> QUARTZ_STABILITY_MONITOR_ITEM = ITEMS.registerSimpleBlockItem("quartz_stability_monitor", QUARTZ_STABILITY_MONITOR);
    public static final DeferredItem<BlockItem> AMETHYST_FREQUENCY_FILTER_ITEM = ITEMS.registerSimpleBlockItem("amethyst_frequency_filter", AMETHYST_FREQUENCY_FILTER);
    public static final DeferredItem<BlockItem> AMETHYST_TUNED_RESONATOR_ITEM = ITEMS.registerSimpleBlockItem("amethyst_tuned_resonator", AMETHYST_TUNED_RESONATOR);
    public static final DeferredItem<BlockItem> AMETHYST_SPECTRUM_ANALYZER_ITEM = ITEMS.registerSimpleBlockItem("amethyst_spectrum_analyzer", AMETHYST_SPECTRUM_ANALYZER);
    public static final DeferredItem<BlockItem> OPTICAL_POWER_METER_ITEM = ITEMS.registerSimpleBlockItem("optical_power_meter", OPTICAL_POWER_METER);
    public static final DeferredItem<BlockItem> OPTICAL_SPLITTER_ITEM = ITEMS.registerSimpleBlockItem("optical_splitter", OPTICAL_SPLITTER);
    public static final DeferredItem<BlockItem> OPTICAL_CHANNEL_FILTER_ITEM = ITEMS.registerSimpleBlockItem("optical_channel_filter", OPTICAL_CHANNEL_FILTER);
    public static final DeferredItem<BlockItem> OPTICAL_ATTENUATOR_ITEM = ITEMS.registerSimpleBlockItem("optical_attenuator", OPTICAL_ATTENUATOR);
    public static final DeferredItem<BlockItem> COPPER_SERIES_RESISTOR_ITEM = ITEMS.registerSimpleBlockItem("copper_series_resistor", COPPER_SERIES_RESISTOR);
    public static final DeferredItem<BlockItem> COPPER_CAPACITOR_ITEM = ITEMS.registerSimpleBlockItem("copper_capacitor", COPPER_CAPACITOR);
    public static final DeferredItem<BlockItem> COPPER_FUSE_ITEM = ITEMS.registerSimpleBlockItem("copper_fuse", COPPER_FUSE);
    public static final DeferredItem<BlockItem> COPPER_CIRCUIT_METER_ITEM = ITEMS.registerSimpleBlockItem("copper_circuit_meter", COPPER_CIRCUIT_METER);
    public static final DeferredItem<BlockItem> PERMANENT_MAGNET_ITEM = ITEMS.registerSimpleBlockItem("permanent_magnet", PERMANENT_MAGNET);
    public static final DeferredItem<BlockItem> INDUCTION_COIL_ITEM = ITEMS.registerSimpleBlockItem("induction_coil", INDUCTION_COIL);
    public static final DeferredItem<BlockItem> MAGNETIC_GRADIENT_METER_ITEM = ITEMS.registerSimpleBlockItem("magnetic_gradient_meter", MAGNETIC_GRADIENT_METER);
    public static final DeferredItem<BlockItem> THERMAL_HEATER_ITEM = ITEMS.registerSimpleBlockItem("thermal_heater", THERMAL_HEATER);
    public static final DeferredItem<BlockItem> THERMAL_RADIATOR_ITEM = ITEMS.registerSimpleBlockItem("thermal_radiator", THERMAL_RADIATOR);
    public static final DeferredItem<BlockItem> THERMAL_CALORIMETER_ITEM = ITEMS.registerSimpleBlockItem("thermal_calorimeter", THERMAL_CALORIMETER);

    public static final DeferredItem<BlockItem> REDSTONE_SIGNAL_CABLE_ITEM = ITEMS.registerSimpleBlockItem("redstone_signal_cable", REDSTONE_SIGNAL_CABLE);
    public static final DeferredItem<BlockItem> REDSTONE_CABLE_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem("redstone_cable_terminal", REDSTONE_CABLE_TERMINAL);
    public static final DeferredItem<BlockItem> REDSTONE_REFERENCE_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("redstone_reference_source", REDSTONE_REFERENCE_SOURCE);
    public static final DeferredItem<BlockItem> ENGINEERING_LIGHT_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("engineering_light_sensor", ENGINEERING_LIGHT_SENSOR);
    public static final DeferredItem<BlockItem> TANK_LEVEL_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("tank_level_sensor", TANK_LEVEL_SENSOR);
    public static final DeferredItem<BlockItem> ENTITY_DENSITY_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("entity_density_sensor", ENTITY_DENSITY_SENSOR);
    public static final DeferredItem<BlockItem> ANALOG_INDICATOR_ITEM = ITEMS.registerSimpleBlockItem("analog_indicator", ANALOG_INDICATOR);
    public static final DeferredItem<BlockItem> REDSTONE_CABLE_JUNCTION_ITEM = ITEMS.registerSimpleBlockItem("redstone_cable_junction", REDSTONE_CABLE_JUNCTION);
    public static final DeferredItem<BlockItem> OPTICAL_FIBER_JUNCTION_ITEM = ITEMS.registerSimpleBlockItem("optical_fiber_junction", OPTICAL_FIBER_JUNCTION);
    public static final DeferredItem<BlockItem> COPPER_CABLE_JUNCTION_ITEM = ITEMS.registerSimpleBlockItem("copper_cable_junction", COPPER_CABLE_JUNCTION);


    public static final DeferredItem<BlockItem> LAPIS_TEMPERATURE_TRANSDUCER_ITEM = ITEMS.registerSimpleBlockItem("lapis_temperature_transducer", LAPIS_TEMPERATURE_TRANSDUCER);
    public static final DeferredItem<BlockItem> LAPIS_MAGNETIC_TRANSDUCER_ITEM = ITEMS.registerSimpleBlockItem("lapis_magnetic_transducer", LAPIS_MAGNETIC_TRANSDUCER);
    public static final DeferredItem<BlockItem> LAPIS_OPTICAL_TRANSDUCER_ITEM = ITEMS.registerSimpleBlockItem("lapis_optical_transducer", LAPIS_OPTICAL_TRANSDUCER);
    public static final DeferredItem<BlockItem> LAPIS_VOLTAGE_TRANSDUCER_ITEM = ITEMS.registerSimpleBlockItem("lapis_voltage_transducer", LAPIS_VOLTAGE_TRANSDUCER);
    public static final DeferredItem<BlockItem> LAPIS_PRECISION_RANGE_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("lapis_precision_range_sensor", LAPIS_PRECISION_RANGE_SENSOR);
    public static final DeferredItem<BlockItem> LAPIS_TO_REDSTONE_QUANTIZER_ITEM = ITEMS.registerSimpleBlockItem("lapis_to_redstone_quantizer", LAPIS_TO_REDSTONE_QUANTIZER);
    public static final DeferredItem<BlockItem> REDSTONE_TO_LAPIS_SCALER_ITEM = ITEMS.registerSimpleBlockItem("redstone_to_lapis_scaler", REDSTONE_TO_LAPIS_SCALER);
    public static final DeferredItem<BlockItem> QUARTZ_TRIGGERED_LAPIS_SAMPLER_ITEM = ITEMS.registerSimpleBlockItem("quartz_triggered_lapis_sampler", QUARTZ_TRIGGERED_LAPIS_SAMPLER);


    // Alpha 1.0 material item: one diamond can be processed into 16 precision thermal inclusions.
    public static final DeferredItem<Item> DIAMOND_SHARD_ITEM = ITEMS.register("diamond_shard", () -> new Item(new Item.Properties()));

    // Alpha 1.0 block items
    public static final DeferredItem<BlockItem> EIGHT_BIT_DATA_BUS_ITEM = ITEMS.registerSimpleBlockItem("eight_bit_data_bus", EIGHT_BIT_DATA_BUS);
    public static final DeferredItem<BlockItem> REDSTONE_BYTE_ENCODER_ITEM = ITEMS.registerSimpleBlockItem("redstone_byte_encoder", REDSTONE_BYTE_ENCODER);
    public static final DeferredItem<BlockItem> BYTE_TO_REDSTONE_DECODER_ITEM = ITEMS.registerSimpleBlockItem("byte_to_redstone_decoder", BYTE_TO_REDSTONE_DECODER);
    public static final DeferredItem<BlockItem> SERIAL_DATA_LINE_ITEM = ITEMS.registerSimpleBlockItem("serial_data_line", SERIAL_DATA_LINE);
    public static final DeferredItem<BlockItem> SERIALIZER_ITEM = ITEMS.registerSimpleBlockItem("serializer", SERIALIZER);
    public static final DeferredItem<BlockItem> DESERIALIZER_ITEM = ITEMS.registerSimpleBlockItem("deserializer", DESERIALIZER);
    public static final DeferredItem<BlockItem> DIFFERENTIAL_DATA_PAIR_ITEM = ITEMS.registerSimpleBlockItem("differential_data_pair", DIFFERENTIAL_DATA_PAIR);
    public static final DeferredItem<BlockItem> DIGITAL_REGENERATOR_ITEM = ITEMS.registerSimpleBlockItem("digital_regenerator", DIGITAL_REGENERATOR);
    public static final DeferredItem<BlockItem> DIFFERENTIAL_DRIVER_ITEM = ITEMS.registerSimpleBlockItem("differential_driver", DIFFERENTIAL_DRIVER);
    public static final DeferredItem<BlockItem> DIFFERENTIAL_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("differential_receiver", DIFFERENTIAL_RECEIVER);
    public static final DeferredItem<BlockItem> SCULK_VIBRATION_INTERFACE_ITEM = ITEMS.registerSimpleBlockItem("sculk_vibration_interface", SCULK_VIBRATION_INTERFACE);
    public static final DeferredItem<BlockItem> PID_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem("pid_controller", PID_CONTROLLER);
    public static final DeferredItem<BlockItem> WATCHDOG_ITEM = ITEMS.registerSimpleBlockItem("watchdog", WATCHDOG);
    public static final DeferredItem<BlockItem> AIR_COMPRESSOR_ITEM = ITEMS.registerSimpleBlockItem("air_compressor", AIR_COMPRESSOR);
    public static final DeferredItem<BlockItem> PNEUMATIC_PIPE_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_pipe", PNEUMATIC_PIPE);
    public static final DeferredItem<BlockItem> AIR_RESERVOIR_ITEM = ITEMS.registerSimpleBlockItem("air_reservoir", AIR_RESERVOIR);
    public static final DeferredItem<BlockItem> PRESSURE_REGULATOR_ITEM = ITEMS.registerSimpleBlockItem("pressure_regulator", PRESSURE_REGULATOR);
    public static final DeferredItem<BlockItem> PNEUMATIC_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_receiver", PNEUMATIC_RECEIVER);
    public static final DeferredItem<BlockItem> PNEUMATIC_VALVE_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_valve", PNEUMATIC_VALVE);
    public static final DeferredItem<BlockItem> PNEUMATIC_CHECK_VALVE_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_check_valve", PNEUMATIC_CHECK_VALVE);
    public static final DeferredItem<BlockItem> PNEUMATIC_FLOW_METER_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_flow_meter", PNEUMATIC_FLOW_METER);
    public static final DeferredItem<BlockItem> PNEUMATIC_PROPORTIONAL_VALVE_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_proportional_valve", PNEUMATIC_PROPORTIONAL_VALVE);
    public static final DeferredItem<BlockItem> PNEUMATIC_RELIEF_VALVE_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_relief_valve", PNEUMATIC_RELIEF_VALVE);
    public static final DeferredItem<BlockItem> PNEUMATIC_CYLINDER_ITEM = ITEMS.registerSimpleBlockItem("pneumatic_cylinder", PNEUMATIC_CYLINDER);
    public static final DeferredItem<BlockItem> SLIME_VIBRATION_CONDUIT_ITEM = ITEMS.registerSimpleBlockItem("slime_vibration_conduit", SLIME_VIBRATION_CONDUIT);
    public static final DeferredItem<BlockItem> HONEY_VIBRATION_DAMPER_ITEM = ITEMS.registerSimpleBlockItem("honey_vibration_damper", HONEY_VIBRATION_DAMPER);
    public static final DeferredItem<BlockItem> MECHANICAL_EXCITER_ITEM = ITEMS.registerSimpleBlockItem("mechanical_exciter", MECHANICAL_EXCITER);
    public static final DeferredItem<BlockItem> MECHANICAL_VIBRATION_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("mechanical_vibration_receiver", MECHANICAL_VIBRATION_RECEIVER);
    public static final DeferredItem<BlockItem> HYDROACOUSTIC_TUBE_ITEM = ITEMS.registerSimpleBlockItem("hydroacoustic_tube", HYDROACOUSTIC_TUBE);
    public static final DeferredItem<BlockItem> HYDROACOUSTIC_EXCITER_ITEM = ITEMS.registerSimpleBlockItem("hydroacoustic_exciter", HYDROACOUSTIC_EXCITER);
    public static final DeferredItem<BlockItem> HYDROACOUSTIC_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("hydroacoustic_receiver", HYDROACOUSTIC_RECEIVER);
    public static final DeferredItem<BlockItem> RADIO_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem("radio_transmitter", RADIO_TRANSMITTER);
    public static final DeferredItem<BlockItem> RADIO_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("radio_receiver", RADIO_RECEIVER);
    public static final DeferredItem<BlockItem> FREE_SPACE_OPTICAL_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem("free_space_optical_transmitter", FREE_SPACE_OPTICAL_TRANSMITTER);
    public static final DeferredItem<BlockItem> FREE_SPACE_OPTICAL_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("free_space_optical_receiver", FREE_SPACE_OPTICAL_RECEIVER);
    public static final DeferredItem<BlockItem> SOUL_SOIL_CONDUIT_ITEM = ITEMS.registerSimpleBlockItem("soul_soil_conduit", SOUL_SOIL_CONDUIT);
    public static final DeferredItem<BlockItem> SOUL_SAND_RESERVOIR_ITEM = ITEMS.registerSimpleBlockItem("soul_sand_reservoir", SOUL_SAND_RESERVOIR);
    public static final DeferredItem<BlockItem> SOUL_FLUX_INJECTOR_ITEM = ITEMS.registerSimpleBlockItem("soul_flux_injector", SOUL_FLUX_INJECTOR);
    public static final DeferredItem<BlockItem> SOUL_FLUX_METER_ITEM = ITEMS.registerSimpleBlockItem("soul_flux_meter", SOUL_FLUX_METER);
    public static final DeferredItem<BlockItem> MOLECULAR_CLOUD_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("molecular_cloud_receiver", MOLECULAR_CLOUD_RECEIVER);
    public static final DeferredItem<BlockItem> PHONON_CONDUIT_ITEM = ITEMS.registerSimpleBlockItem("phonon_conduit", PHONON_CONDUIT);
    public static final DeferredItem<BlockItem> THERMAL_PULSE_ENCODER_ITEM = ITEMS.registerSimpleBlockItem("thermal_pulse_encoder", THERMAL_PULSE_ENCODER);
    public static final DeferredItem<BlockItem> THERMAL_PULSE_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("thermal_pulse_receiver", THERMAL_PULSE_RECEIVER);
    public static final DeferredItem<BlockItem> SHIELDED_INSTRUMENT_CABLE_ITEM = ITEMS.registerSimpleBlockItem("shielded_instrument_cable", SHIELDED_INSTRUMENT_CABLE);
    public static final DeferredItem<BlockItem> SERVO_ACTUATOR_ITEM = ITEMS.registerSimpleBlockItem("servo_actuator", SERVO_ACTUATOR);
    public static final DeferredItem<BlockItem> SERVO_POSITION_SENSOR_ITEM = ITEMS.registerSimpleBlockItem("servo_position_sensor", SERVO_POSITION_SENSOR);
    public static final DeferredItem<BlockItem> REDUNDANT_VOTER_ITEM = ITEMS.registerSimpleBlockItem("redundant_voter", REDUNDANT_VOTER);
    public static final DeferredItem<BlockItem> FAULT_LATCH_ITEM = ITEMS.registerSimpleBlockItem("fault_latch", FAULT_LATCH);
    public static final DeferredItem<BlockItem> OPERATIONS_MONITOR_ITEM = ITEMS.registerSimpleBlockItem("operations_monitor", OPERATIONS_MONITOR);

    public static final Supplier<CreativeModeTab> RSE_TAB =
            CREATIVE_TABS.register(
                    "rse",
                    () -> CreativeModeTab.builder()
                            .title(
                                    Component.translatable(
                                            "itemGroup.redstoneengineering.rse"
                                    )
                            )
                            .icon(
                                    () -> new ItemStack(
                                            SIGNAL_ANALYZER_ITEM.get()
                                    )
                            )
                            .displayItems(
                                    (params, output) -> {
                                        output.accept(SIGNAL_ANALYZER_ITEM.get());
                                        output.accept(SIGNAL_PROBE_ITEM.get());
                                        output.accept(INSTRUMENT_CABLE_ITEM.get());
                                        output.accept(OSCILLOSCOPE_ITEM.get());
                                        output.accept(LOGIC_ANALYZER_ITEM.get());

                                        output.accept(SIGNAL_CONDITIONER_ITEM.get());
                                        output.accept(CALIBRATION_MODULE_ITEM.get());
                                        output.accept(PRECISION_FILTER_ITEM.get());

                                        output.accept(SAMPLE_HOLD_ITEM.get());
                                        output.accept(EDGE_DETECTOR_ITEM.get());
                                        output.accept(PULSE_SHAPER_ITEM.get());
                                        output.accept(PWM_CONTROLLER_ITEM.get());

                                        output.accept(SIGNAL_TAP_ITEM.get());
                                        output.accept(RANGE_SENSOR_ITEM.get());

                                        // alpha.5 domain foundations: existing instruments above remain Redstone-only.
                                        output.accept(LAPIS_SIGNAL_LINE_ITEM.get());
                                        output.accept(LAPIS_PRECISION_SOURCE_ITEM.get());
                                        output.accept(QUARTZ_TIMING_LINE_ITEM.get());
                                        output.accept(QUARTZ_OSCILLATOR_ITEM.get());
                                        output.accept(AMETHYST_RESONANCE_DUST_ITEM.get());
                                        output.accept(AMETHYST_RESONATOR_ITEM.get());
                                        output.accept(OPTICAL_FIBER_ITEM.get());
                                        output.accept(OPTICAL_EMITTER_ITEM.get());
                                        output.accept(OPTICAL_RECEIVER_ITEM.get());
                                        output.accept(COPPER_WIRE_ITEM.get());
                                        output.accept(COPPER_VOLTAGE_SOURCE_ITEM.get());
                                        output.accept(COPPER_RESISTIVE_LOAD_ITEM.get());
                                        output.accept(IRON_CORE_ITEM.get());
                                        output.accept(ELECTROMAGNET_ITEM.get());
                                        output.accept(MAGNETIC_FIELD_SENSOR_ITEM.get());
                                        output.accept(THERMAL_MASS_ITEM.get());
                                        output.accept(TEMPERATURE_SENSOR_ITEM.get());

                                        // alpha.6: domain-specific engineering instruments and physics
                                        output.accept(LAPIS_NOISE_SOURCE_ITEM.get());
                                        output.accept(LAPIS_LOW_PASS_FILTER_ITEM.get());
                                        output.accept(LAPIS_PRECISION_METER_ITEM.get());
                                        output.accept(QUARTZ_LAB_OSCILLATOR_ITEM.get());
                                        output.accept(QUARTZ_CLOCK_DIVIDER_ITEM.get());
                                        output.accept(QUARTZ_PHASE_DELAY_ITEM.get());
                                        output.accept(QUARTZ_STABILITY_MONITOR_ITEM.get());
                                        output.accept(AMETHYST_FREQUENCY_FILTER_ITEM.get());
                                        output.accept(AMETHYST_TUNED_RESONATOR_ITEM.get());
                                        output.accept(AMETHYST_SPECTRUM_ANALYZER_ITEM.get());
                                        output.accept(OPTICAL_POWER_METER_ITEM.get());
                                        output.accept(OPTICAL_SPLITTER_ITEM.get());
                                        output.accept(OPTICAL_CHANNEL_FILTER_ITEM.get());
                                        output.accept(OPTICAL_ATTENUATOR_ITEM.get());
                                        output.accept(COPPER_SERIES_RESISTOR_ITEM.get());
                                        output.accept(COPPER_CAPACITOR_ITEM.get());
                                        output.accept(COPPER_FUSE_ITEM.get());
                                        output.accept(COPPER_CIRCUIT_METER_ITEM.get());
                                        output.accept(PERMANENT_MAGNET_ITEM.get());
                                        output.accept(INDUCTION_COIL_ITEM.get());
                                        output.accept(MAGNETIC_GRADIENT_METER_ITEM.get());
                                        output.accept(THERMAL_HEATER_ITEM.get());
                                        output.accept(THERMAL_RADIATOR_ITEM.get());
                                        output.accept(THERMAL_CALORIMETER_ITEM.get());

                                        // alpha.7: Redstone routing, measurement sources, sensors, first display actuator
                                        output.accept(REDSTONE_SIGNAL_CABLE_ITEM.get());
                                        output.accept(REDSTONE_CABLE_TERMINAL_ITEM.get());
                                        output.accept(REDSTONE_REFERENCE_SOURCE_ITEM.get());
                                        output.accept(ENGINEERING_LIGHT_SENSOR_ITEM.get());
                                        output.accept(TANK_LEVEL_SENSOR_ITEM.get());
                                        output.accept(ENTITY_DENSITY_SENSOR_ITEM.get());
                                        output.accept(ANALOG_INDICATOR_ITEM.get());
                                        output.accept(REDSTONE_CABLE_JUNCTION_ITEM.get());
                                        output.accept(OPTICAL_FIBER_JUNCTION_ITEM.get());
                                        output.accept(COPPER_CABLE_JUNCTION_ITEM.get());


                                        // alpha.8: physical sensing, transduction, conversion, sampling
                                        output.accept(LAPIS_TEMPERATURE_TRANSDUCER_ITEM.get());
                                        output.accept(LAPIS_MAGNETIC_TRANSDUCER_ITEM.get());
                                        output.accept(LAPIS_OPTICAL_TRANSDUCER_ITEM.get());
                                        output.accept(LAPIS_VOLTAGE_TRANSDUCER_ITEM.get());
                                        output.accept(LAPIS_PRECISION_RANGE_SENSOR_ITEM.get());
                                        output.accept(LAPIS_TO_REDSTONE_QUANTIZER_ITEM.get());
                                        output.accept(REDSTONE_TO_LAPIS_SCALER_ITEM.get());
                                        output.accept(QUARTZ_TRIGGERED_LAPIS_SAMPLER_ITEM.get());


                                        // Alpha 1.0: data, control, communications, physical media, reliability
                                        output.accept(DIAMOND_SHARD_ITEM.get());
                                        output.accept(EIGHT_BIT_DATA_BUS_ITEM.get());
                                        output.accept(REDSTONE_BYTE_ENCODER_ITEM.get());
                                        output.accept(BYTE_TO_REDSTONE_DECODER_ITEM.get());
                                        output.accept(SERIAL_DATA_LINE_ITEM.get());
                                        output.accept(SERIALIZER_ITEM.get());
                                        output.accept(DESERIALIZER_ITEM.get());
                                        output.accept(DIFFERENTIAL_DATA_PAIR_ITEM.get());
                                        output.accept(DIGITAL_REGENERATOR_ITEM.get());
                                        output.accept(DIFFERENTIAL_DRIVER_ITEM.get());
                                        output.accept(DIFFERENTIAL_RECEIVER_ITEM.get());
                                        output.accept(SCULK_VIBRATION_INTERFACE_ITEM.get());
                                        output.accept(PID_CONTROLLER_ITEM.get());
                                        output.accept(WATCHDOG_ITEM.get());
                                        output.accept(AIR_COMPRESSOR_ITEM.get());
                                        output.accept(PNEUMATIC_PIPE_ITEM.get());
                                        output.accept(AIR_RESERVOIR_ITEM.get());
                                        output.accept(PRESSURE_REGULATOR_ITEM.get());
                                        output.accept(PNEUMATIC_RECEIVER_ITEM.get());
                                        output.accept(PNEUMATIC_VALVE_ITEM.get());
                                        output.accept(PNEUMATIC_CHECK_VALVE_ITEM.get());
                                        output.accept(PNEUMATIC_FLOW_METER_ITEM.get());
                                        output.accept(PNEUMATIC_PROPORTIONAL_VALVE_ITEM.get());
                                        output.accept(PNEUMATIC_RELIEF_VALVE_ITEM.get());
                                        output.accept(PNEUMATIC_CYLINDER_ITEM.get());
                                        output.accept(SLIME_VIBRATION_CONDUIT_ITEM.get());
                                        output.accept(HONEY_VIBRATION_DAMPER_ITEM.get());
                                        output.accept(MECHANICAL_EXCITER_ITEM.get());
                                        output.accept(MECHANICAL_VIBRATION_RECEIVER_ITEM.get());
                                        output.accept(HYDROACOUSTIC_TUBE_ITEM.get());
                                        output.accept(HYDROACOUSTIC_EXCITER_ITEM.get());
                                        output.accept(HYDROACOUSTIC_RECEIVER_ITEM.get());
                                        output.accept(RADIO_TRANSMITTER_ITEM.get());
                                        output.accept(RADIO_RECEIVER_ITEM.get());
                                        output.accept(FREE_SPACE_OPTICAL_TRANSMITTER_ITEM.get());
                                        output.accept(FREE_SPACE_OPTICAL_RECEIVER_ITEM.get());
                                        output.accept(SOUL_SOIL_CONDUIT_ITEM.get());
                                        output.accept(SOUL_SAND_RESERVOIR_ITEM.get());
                                        output.accept(SOUL_FLUX_INJECTOR_ITEM.get());
                                        output.accept(SOUL_FLUX_METER_ITEM.get());
                                        output.accept(MOLECULAR_CLOUD_RECEIVER_ITEM.get());
                                        output.accept(PHONON_CONDUIT_ITEM.get());
                                        output.accept(THERMAL_PULSE_ENCODER_ITEM.get());
                                        output.accept(THERMAL_PULSE_RECEIVER_ITEM.get());
                                        output.accept(SHIELDED_INSTRUMENT_CABLE_ITEM.get());
                                        output.accept(SERVO_ACTUATOR_ITEM.get());
                                        output.accept(SERVO_POSITION_SENSOR_ITEM.get());
                                        output.accept(REDUNDANT_VOTER_ITEM.get());
                                        output.accept(FAULT_LATCH_ITEM.get());
                                        output.accept(OPERATIONS_MONITOR_ITEM.get());
                                    }
                            )
                            .build()
            );

    public RedstoneEngineering(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {
        BLOCK_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        LOGGER.info(
                "Redstone Systems Engineering 1.0.0-alpha.1 "
                        + "Integrated Cyber-Physical Engineering initializing."
        );
    }

    private static BlockBehaviour.Properties machineProps(
            MapColor color
    ) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.0F);
    }

    private static BlockBehaviour.Properties smallInstrumentProps(
            MapColor color
    ) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(0.8F)
                .noOcclusion();
    }

    private static BlockBehaviour.Properties analogIndicatorProps() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(1.5F)
                .lightLevel(state -> state.getValue(AnalogIndicatorBlock.LEVEL));
    }

    private static <T extends Block>
    DeferredHolder<MapCodec<? extends Block>, MapCodec<T>> codec(
            String id,
            java.util.function.Function<
                    BlockBehaviour.Properties,
                    T
            > factory
    ) {
        return BLOCK_TYPES.register(
                id,
                () -> BlockBehaviour.simpleCodec(factory)
        );
    }
}
