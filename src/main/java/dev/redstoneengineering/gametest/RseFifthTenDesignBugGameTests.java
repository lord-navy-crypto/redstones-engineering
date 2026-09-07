package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystSpectrumAnalyzerBlock;
import dev.redstoneengineering.block.ConnectedCableBlock;
import dev.redstoneengineering.block.CopperCapacitorBlock;
import dev.redstoneengineering.block.CopperCircuitMeterBlock;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.CopperVoltageSourceBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.block.OpticalAttenuatorBlock;
import dev.redstoneengineering.block.OpticalChannelFilterBlock;
import dev.redstoneengineering.block.OpticalFiberBlock;
import dev.redstoneengineering.block.OpticalPowerMeterBlock;
import dev.redstoneengineering.block.OpticalSplitterBlock;
import dev.redstoneengineering.block.PermanentMagnetBlock;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.metrology.MetrologyStore;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fifth 10-block design/bug campaign: spectrum, guided optics, Copper R/C/protection/metrology and permanent field source. */
public final class RseFifthTenDesignBugGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseFifthTenDesignBugGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void spectrumAnalyzerInspectionIsNeutralAndConflictAware(GameTestHelper helper) {
        BlockPos dust = new BlockPos(1, 1, 2);
        BlockPos analyzer = new BlockPos(3, 1, 2);
        helper.setBlock(dust, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState());
        helper.setBlock(analyzer, RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER.get().defaultBlockState());
        BlockPos dustWorld = helper.absolutePos(dust);
        BlockPos analyzerWorld = helper.absolutePos(analyzer);
        AmethystResonanceDustBlock.setResonance(
                helper.getLevel(), dustWorld, 0, 0,
                AmethystResonanceDustBlock.ResonanceStatus.FREQUENCY_CONFLICT);
        helper.getLevel().scheduleTick(analyzerWorld, RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER.get(), 1);

        helper.runAfterDelay(2, () -> {
            AmethystSpectrumAnalyzerBlock.Spectrum measured = AmethystSpectrumAnalyzerBlock.spectrum(helper.getLevel(), analyzerWorld);
            if (measured.conflicts() < 1 || AmethystSpectrumAnalyzerBlock.quality(helper.getLevel(), analyzerWorld) != PortQuality.TOPOLOGY_ERROR) {
                helper.fail("Spectrum analyzer hid an observed amethyst frequency conflict", analyzer);
                return;
            }

            RuntimeIntStore.remove(helper.getLevel(), "amethyst_spectrum", analyzerWorld);
            int before = RuntimeIntStore.entryCount(helper.getLevel());
            AmethystSpectrumAnalyzerBlock.spectrum(helper.getLevel(), analyzerWorld);
            EngineeringPortProvider provider = RedstoneEngineering.AMETHYST_SPECTRUM_ANALYZER.get();
            provider.engineeringSnapshot(helper.getLevel(), analyzerWorld, helper.getBlockState(analyzer), Direction.UP);
            int after = RuntimeIntStore.entryCount(helper.getLevel());
            if (after != before) {
                helper.fail("Spectrum inspection created runtime state instead of remaining observer-only", analyzer);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void opticalPowerMeterPreservesSourceConflict(GameTestHelper helper) {
        BlockPos fiber = new BlockPos(2, 1, 2);
        BlockPos westEmitter = new BlockPos(1, 1, 2);
        BlockPos eastEmitter = new BlockPos(3, 1, 2);
        BlockPos meter = new BlockPos(2, 2, 2);
        helper.setBlock(fiber, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState()
                .setValue(ConnectedCableBlock.WEST, true)
                .setValue(ConnectedCableBlock.EAST, true));
        helper.setBlock(westEmitter, RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState());
        helper.setBlock(eastEmitter, RedstoneEngineering.OPTICAL_EMITTER.get().defaultBlockState());
        helper.setBlock(meter, RedstoneEngineering.OPTICAL_POWER_METER.get().defaultBlockState()
                .setValue(OpticalPowerMeterBlock.FACING, Direction.DOWN));
        DomainNetwork.recomputeOptical(helper.getLevel(), helper.absolutePos(fiber));

        helper.runAfterDelay(2, () -> {
            var snapshot = RedstoneEngineering.OPTICAL_POWER_METER.get().engineeringSnapshot(
                    helper.getLevel(), helper.absolutePos(meter), helper.getBlockState(meter), Direction.DOWN).orElse(null);
            if (snapshot == null || snapshot.quality() != PortQuality.TOPOLOGY_ERROR) {
                helper.fail("Optical power meter collapsed a multi-source conflict into DARK/no-signal", meter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void opticalSplitterReportsOddLevelQuantizationAndDrivesBothBranches(GameTestHelper helper) {
        BlockPos input = new BlockPos(1, 1, 2);
        BlockPos splitter = new BlockPos(2, 1, 2);
        BlockPos outputA = new BlockPos(3, 1, 2);
        BlockPos outputB = new BlockPos(2, 1, 1);
        helper.setBlock(input, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        helper.setBlock(splitter, RedstoneEngineering.OPTICAL_SPLITTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(outputA, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        helper.setBlock(outputB, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        OpticalFiberBlock.setOptical(helper.getLevel(), helper.absolutePos(input), 5, 3, true);
        helper.getLevel().scheduleTick(helper.absolutePos(splitter), RedstoneEngineering.OPTICAL_SPLITTER.get(), 1);

        helper.runAfterDelay(3, () -> {
            OpticalSplitterBlock.SplitEvidence evidence = RedstoneEngineering.OPTICAL_SPLITTER.get().evidence(
                    helper.getLevel(), helper.absolutePos(splitter), helper.getBlockState(splitter));
            var a = DomainNetwork.sampleOptical(helper.getLevel(), helper.absolutePos(outputA));
            var b = DomainNetwork.sampleOptical(helper.getLevel(), helper.absolutePos(outputB));
            if (evidence.branchAIntensity() != 2 || evidence.branchBIntensity() != 2 || evidence.quantizationLoss() != 1) {
                helper.fail("Optical splitter did not expose integer odd-level split quantization", splitter);
                return;
            }
            if (!a.valid() || !b.valid() || a.intensity() != 2 || b.intensity() != 2 || a.channel() != 3 || b.channel() != 3) {
                helper.fail("Optical splitter failed to maintain two independent branch claims", splitter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void opticalRetuningImmediatelyInvalidatesOldFilterAndAttenuatorOutputs(GameTestHelper helper) {
        BlockPos filter = new BlockPos(1, 1, 1);
        BlockPos filterOut = new BlockPos(2, 1, 1);
        BlockPos attenuator = new BlockPos(1, 1, 3);
        BlockPos attenuatorOut = new BlockPos(2, 1, 3);
        helper.setBlock(filter, RedstoneEngineering.OPTICAL_CHANNEL_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(filterOut, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        helper.setBlock(attenuator, RedstoneEngineering.OPTICAL_ATTENUATOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(attenuatorOut, RedstoneEngineering.OPTICAL_FIBER.get().defaultBlockState());
        DomainNetwork.driveOptical(helper.getLevel(), helper.absolutePos(filterOut), helper.absolutePos(filter), 7, 2, true);
        DomainNetwork.driveOptical(helper.getLevel(), helper.absolutePos(attenuatorOut), helper.absolutePos(attenuator), 6, 4, true);

        if (!DomainNetwork.sampleOptical(helper.getLevel(), helper.absolutePos(filterOut)).valid()
                || !DomainNetwork.sampleOptical(helper.getLevel(), helper.absolutePos(attenuatorOut)).valid()) {
            helper.fail("Test setup failed to establish old optical processor outputs", filterOut);
            return;
        }

        BlockState filterNext = helper.getBlockState(filter).setValue(OpticalChannelFilterBlock.TARGET, 5);
        BlockState attenuatorNext = helper.getBlockState(attenuator).setValue(OpticalAttenuatorBlock.LOSS, 7);
        helper.setBlock(filter, filterNext);
        helper.setBlock(attenuator, attenuatorNext);

        if (DomainNetwork.sampleOptical(helper.getLevel(), helper.absolutePos(filterOut)).valid()
                || DomainNetwork.sampleOptical(helper.getLevel(), helper.absolutePos(attenuatorOut)).valid()) {
            helper.fail("Optical processor configuration change left a stale old carrier alive for another tick", filterOut);
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void copperProcessorInspectionIsNeutralAndZeroSourceRemainsValid(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos resistor = new BlockPos(1, 1, 2);
        BlockPos capacitor = new BlockPos(2, 1, 1);
        BlockPos fuse = new BlockPos(2, 1, 3);
        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, 0));
        helper.setBlock(resistor, RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(capacitor, RedstoneEngineering.COPPER_CAPACITOR.get().defaultBlockState());
        helper.setBlock(fuse, RedstoneEngineering.COPPER_FUSE.get().defaultBlockState());

        int before = RuntimeIntStore.entryCount(helper.getLevel());
        CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(resistor));
        CopperCapacitorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(capacitor));
        CopperFuseBlock.outputVoltage(helper.getLevel(), helper.absolutePos(fuse));
        int after = RuntimeIntStore.entryCount(helper.getLevel());
        if (after != before) {
            helper.fail("Copper processor output inspection allocated runtime state", resistor);
            return;
        }

        helper.runAfterDelay(3, () -> {
            if (CopperSeriesResistorBlock.outputVoltage(helper.getLevel(), helper.absolutePos(resistor)) != 0
                    || CopperSeriesResistorBlock.outputQuality(helper.getLevel(), helper.absolutePos(resistor)) != PortQuality.VALID) {
                helper.fail("Configured zero-volt Copper source was confused with an absent source", resistor);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 70)
    public static void copperCapacitorRetainsLegitimateStoredEnergyAfterInputRemoval(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos capacitor = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, 15));
        helper.setBlock(capacitor, RedstoneEngineering.COPPER_CAPACITOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));

        helper.runAfterDelay(5, () -> {
            int charged = CopperCapacitorBlock.chargePercent(helper.getLevel(), helper.absolutePos(capacitor));
            if (charged <= 0) {
                helper.fail("Copper capacitor failed to accumulate charge from a valid input", capacitor);
                return;
            }
            helper.setBlock(source, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(3, () -> {
                if (CopperCapacitorBlock.chargePercent(helper.getLevel(), helper.absolutePos(capacitor)) <= 0
                        || CopperCapacitorBlock.outputQuality(helper.getLevel(), helper.absolutePos(capacitor)) != PortQuality.VALID) {
                    helper.fail("Copper capacitor discarded legitimate stored-energy output as soon as input disappeared", capacitor);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void copperFuseTripPublishesFaultQualityWithoutObserverMutation(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos fuse = new BlockPos(1, 1, 2);
        BlockPos load = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, 15));
        helper.setBlock(fuse, RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperFuseBlock.RATING, 4));
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(dev.redstoneengineering.block.CopperResistiveLoadBlock.RESISTANCE, 1));

        helper.runAfterDelay(3, () -> {
            BlockState state = helper.getBlockState(fuse);
            if (!state.getValue(CopperFuseBlock.TRIPPED)
                    || CopperFuseBlock.outputVoltage(helper.getLevel(), helper.absolutePos(fuse)) != 0
                    || CopperFuseBlock.outputQuality(helper.getLevel(), helper.absolutePos(fuse), state) != PortQuality.FAULT) {
                helper.fail("Tripped Copper fuse did not expose a zero-voltage FAULT output", fuse);
                return;
            }
            int before = RuntimeIntStore.entryCount(helper.getLevel());
            CopperFuseBlock.outputVoltage(helper.getLevel(), helper.absolutePos(fuse));
            CopperFuseBlock.outputQuality(helper.getLevel(), helper.absolutePos(fuse), state);
            if (RuntimeIntStore.entryCount(helper.getLevel()) != before) {
                helper.fail("Fuse diagnostics mutated runtime state", fuse);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 60)
    public static void copperMeterSnapshotDoesNotCreateTrackerButServerSampleDoes(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos meter = new BlockPos(2, 1, 2);
        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState()
                .setValue(CopperVoltageSourceBlock.VOLTAGE, 9));
        helper.setBlock(meter, RedstoneEngineering.COPPER_CIRCUIT_METER.get().defaultBlockState()
                .setValue(CopperCircuitMeterBlock.FACING, Direction.WEST));

        int before = MetrologyStore.entryCount(helper.getLevel());
        var initial = CopperCircuitMeterBlock.measurement(helper.getLevel(), helper.absolutePos(meter));
        RedstoneEngineering.COPPER_CIRCUIT_METER.get().engineeringSnapshot(
                helper.getLevel(), helper.absolutePos(meter), helper.getBlockState(meter), Direction.WEST);
        int after = MetrologyStore.entryCount(helper.getLevel());
        if (initial.sampleCount() != 0 || after != before
                || CopperCircuitMeterBlock.measurementQuality(helper.getLevel(), helper.absolutePos(meter), helper.getBlockState(meter)) != PortQuality.STALE) {
            helper.fail("Copper meter observation created tracker state or mislabeled the pre-sample condition", meter);
            return;
        }

        helper.runAfterDelay(2, () -> {
            if (CopperCircuitMeterBlock.measurement(helper.getLevel(), helper.absolutePos(meter)).sampleCount() < 1
                    || CopperCircuitMeterBlock.measurementQuality(helper.getLevel(), helper.absolutePos(meter), helper.getBlockState(meter)) == PortQuality.STALE) {
                helper.fail("Scheduled Copper meter sampling failed to become authoritative", meter);
                return;
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 50)
    public static void permanentMagnetIsFreeSpaceScalarSourceNotWiredBus(GameTestHelper helper) {
        BlockPos magnet = new BlockPos(1, 1, 2);
        helper.setBlock(magnet, RedstoneEngineering.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.STRENGTH, 12)
                .setValue(PermanentMagnetBlock.FACING, Direction.EAST));
        BlockState state = helper.getBlockState(magnet);
        PermanentMagnetBlock.SourceEvidence evidence = PermanentMagnetBlock.evidence(state);
        if (!evidence.scalarFieldModel() || evidence.wired() || evidence.northMarker() != Direction.EAST
                || RedstoneEngineering.PERMANENT_MAGNET.get().engineeringPorts(state).stream().anyMatch(p -> p.redstoneConnectable())) {
            helper.fail("Permanent magnet violated its scalar free-space/non-wired source contract", magnet);
            return;
        }

        int near = MagneticPhysics.fieldAt(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), 6);
        int far = MagneticPhysics.fieldAt(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 2)), 6);
        if (near <= far || near <= 0) {
            helper.fail("Permanent magnet field did not decay with distance in the bounded scalar model", magnet);
            return;
        }
        helper.succeed();
    }
}
