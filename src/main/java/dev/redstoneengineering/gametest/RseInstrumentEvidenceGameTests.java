package dev.redstoneengineering.gametest;

import dev.redstoneengineering.EngineeringSystemsModule;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalSignalBlock;
import dev.redstoneengineering.block.FaultInjectorBlock;
import dev.redstoneengineering.block.SignalProbeBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** End-to-end provenance tests for source -> probe -> instrument-bus evidence. */
public final class RseInstrumentEvidenceGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseInstrumentEvidenceGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void instrumentBusPreservesProbeFaultQuality(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 2);
        BlockPos injector = new BlockPos(2, 1, 2);
        BlockPos arm = new BlockPos(2, 1, 3);
        BlockPos probe = new BlockPos(3, 1, 2);
        BlockPos cable = new BlockPos(4, 1, 2);

        helper.setBlock(source, Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.setBlock(injector, EngineeringSystemsModule.FAULT_INJECTOR.get().defaultBlockState()
                .setValue(DirectionalSignalBlock.FACING, Direction.EAST)
                .setValue(DirectionalSignalBlock.INPUT_FACING, Direction.WEST)
                .setValue(FaultInjectorBlock.MODE, 0));
        helper.setBlock(probe, RedstoneEngineering.SIGNAL_PROBE.get().defaultBlockState()
                .setValue(SignalProbeBlock.FACING, Direction.WEST)
                .setValue(SignalProbeBlock.CHANNEL, 0));
        helper.setBlock(cable, RedstoneEngineering.INSTRUMENT_CABLE.get().defaultBlockState());

        helper.runAfterDelay(5, () -> {
            BlockPos probeWorld = helper.absolutePos(probe);
            BlockPos cableWorld = helper.absolutePos(cable);
            var baselineProbe = SignalProbeBlock.measurementObservation(
                    helper.getLevel(), probeWorld, helper.getBlockState(probe));
            InstrumentNetwork.ProbeSnapshot baselineBus =
                    InstrumentNetwork.scan(helper.getLevel(), cableWorld);

            if (baselineProbe.value() != 15
                    || baselineProbe.quality() != PortQuality.VALID
                    || baselineBus.quality(0) != PortQuality.VALID
                    || baselineBus.qualityForMask(1) != PortQuality.VALID
                    || !baselineBus.valid(0)
                    || baselineBus.valueOr(0, -1) != 15) {
                helper.fail("Instrument chain did not establish a valid probe baseline", probe);
                return;
            }

            helper.setBlock(arm, Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                var faultProbe = SignalProbeBlock.measurementObservation(
                        helper.getLevel(), probeWorld, helper.getBlockState(probe));
                InstrumentNetwork.ProbeSnapshot faultBus =
                        InstrumentNetwork.scan(helper.getLevel(), cableWorld);

                if (faultProbe.quality() != PortQuality.FAULT
                        || faultBus.quality(0) != PortQuality.FAULT
                        || faultBus.qualityForMask(1) != PortQuality.FAULT
                        || faultBus.valid(0)) {
                    helper.fail("FAULT-quality source evidence was washed to VALID on the instrument bus", probe);
                    return;
                }

                helper.setBlock(arm, Blocks.AIR.defaultBlockState());
                helper.runAfterDelay(4, () -> {
                    var recoveredProbe = SignalProbeBlock.measurementObservation(
                            helper.getLevel(), probeWorld, helper.getBlockState(probe));
                    InstrumentNetwork.ProbeSnapshot recoveredBus =
                            InstrumentNetwork.scan(helper.getLevel(), cableWorld);
                    if (recoveredProbe.quality() != PortQuality.VALID
                            || recoveredBus.quality(0) != PortQuality.VALID
                            || recoveredBus.qualityForMask(1) != PortQuality.VALID
                            || !recoveredBus.valid(0)
                            || recoveredBus.valueOr(0, -1) != 15) {
                        helper.fail("Instrument evidence did not recover after source quality returned", probe);
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }
}
