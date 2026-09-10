package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.CopperFuseBlock;
import dev.redstoneengineering.block.CopperResistiveLoadBlock;
import dev.redstoneengineering.block.CopperSeriesResistorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lifecycle regressions for Copper processor authority across configuration epochs. */
public final class RseCopperConfigurationLifecycleSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCopperConfigurationLifecycleSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void resistorConfigurationChangeInvalidatesOldDerivedVoltageUntilNextRealTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos inputWire = new BlockPos(1, 1, 2);
        BlockPos resistor = new BlockPos(2, 1, 2);
        BlockPos outputWire = new BlockPos(3, 1, 2);
        BlockPos load = new BlockPos(4, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(resistor, RedstoneEngineering.COPPER_SERIES_RESISTOR.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperSeriesResistorBlock.RESISTANCE, 4));
        helper.setBlock(outputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 4));

        helper.runAfterDelay(14, () -> {
            BlockPos resistorWorld = helper.absolutePos(resistor);
            BlockPos outputWorld = helper.absolutePos(outputWire);
            int beforeResistance = helper.getBlockState(resistor).getValue(CopperSeriesResistorBlock.RESISTANCE);
            int beforeOutput = CopperSeriesResistorBlock.outputVoltage(level, resistorWorld);
            PortQuality beforeProcessorQuality = CopperSeriesResistorBlock.outputQuality(level, resistorWorld);
            int beforeWire = DomainNetwork.sampleCopperVoltage(level, outputWorld);

            if (beforeResistance != 4
                    || beforeProcessorQuality != PortQuality.VALID
                    || !CopperSeriesResistorBlock.outputInitialized(level, resistorWorld)
                    || beforeOutput <= 0
                    || beforeWire != beforeOutput) {
                helper.fail("Precondition failed: resistor did not establish one trusted derived output"
                        + " | R=" + beforeResistance
                        + " processorQuality=" + beforeProcessorQuality
                        + " processorV=" + beforeOutput
                        + " wireV=" + beforeWire, resistor);
                return;
            }

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(resistorWorld), Direction.UP, resistorWorld, false);
            level.getBlockState(resistorWorld).useWithoutItem(level, player, hit);

            int afterResistance = level.getBlockState(resistorWorld).getValue(CopperSeriesResistorBlock.RESISTANCE);
            if (afterResistance == beforeResistance) {
                helper.fail("Precondition failed: real resistor configuration interaction did not change resistance", resistor);
                return;
            }

            DomainNetwork.recomputeCopper(level, outputWorld);
            PortQuality immediateProcessorQuality = CopperSeriesResistorBlock.outputQuality(level, resistorWorld);
            boolean immediateInitialized = CopperSeriesResistorBlock.outputInitialized(level, resistorWorld);
            int immediateProcessorVoltage = CopperSeriesResistorBlock.outputVoltage(level, resistorWorld);
            int immediateWireVoltage = DomainNetwork.sampleCopperVoltage(level, outputWorld);

            if (immediateProcessorQuality == PortQuality.VALID
                    || immediateInitialized
                    || immediateProcessorVoltage != 0
                    || immediateWireVoltage != 0) {
                helper.fail("Resistor configuration change left old derived Copper voltage authoritative"
                        + " | R=" + beforeResistance + "->" + afterResistance
                        + " oldV=" + beforeOutput
                        + " processorQuality=" + immediateProcessorQuality
                        + " initialized=" + immediateInitialized
                        + " processorV=" + immediateProcessorVoltage
                        + " wireV=" + immediateWireVoltage, resistor);
                return;
            }

            helper.runAfterDelay(4, () -> {
                PortQuality recoveredProcessorQuality = CopperSeriesResistorBlock.outputQuality(level, resistorWorld);
                boolean recoveredInitialized = CopperSeriesResistorBlock.outputInitialized(level, resistorWorld);
                int recoveredProcessorVoltage = CopperSeriesResistorBlock.outputVoltage(level, resistorWorld);
                int recoveredWireVoltage = DomainNetwork.sampleCopperVoltage(level, outputWorld);

                if (recoveredProcessorQuality != PortQuality.VALID
                        || !recoveredInitialized
                        || recoveredProcessorVoltage <= 0
                        || recoveredProcessorVoltage == beforeOutput
                        || recoveredWireVoltage != recoveredProcessorVoltage) {
                    helper.fail("Resistor did not publish fresh derived Copper voltage after the new configuration was evaluated"
                            + " | oldV=" + beforeOutput
                            + " newV=" + recoveredProcessorVoltage
                            + " wireV=" + recoveredWireVoltage
                            + " quality=" + recoveredProcessorQuality
                            + " initialized=" + recoveredInitialized, resistor);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void fuseRatingReductionInvalidatesOldSafeAuthorityUntilProtectionReevaluates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = new BlockPos(0, 1, 2);
        BlockPos inputWire = new BlockPos(1, 1, 2);
        BlockPos fuse = new BlockPos(2, 1, 2);
        BlockPos outputWire = new BlockPos(3, 1, 2);
        BlockPos load = new BlockPos(4, 1, 2);

        helper.setBlock(source, RedstoneEngineering.COPPER_VOLTAGE_SOURCE.get().defaultBlockState());
        helper.setBlock(inputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(fuse, RedstoneEngineering.COPPER_FUSE.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(CopperFuseBlock.RATING, 15)
                .setValue(CopperFuseBlock.TRIPPED, false));
        helper.setBlock(outputWire, RedstoneEngineering.COPPER_WIRE.get().defaultBlockState());
        helper.setBlock(load, RedstoneEngineering.COPPER_RESISTIVE_LOAD.get().defaultBlockState()
                .setValue(CopperResistiveLoadBlock.RESISTANCE, 4));

        helper.runAfterDelay(14, () -> {
            BlockPos fuseWorld = helper.absolutePos(fuse);
            BlockPos outputWorld = helper.absolutePos(outputWire);
            int beforeRating = helper.getBlockState(fuse).getValue(CopperFuseBlock.RATING);
            int beforeOutput = CopperFuseBlock.outputVoltage(level, fuseWorld);
            PortQuality beforeQuality = CopperFuseBlock.outputQuality(level, fuseWorld, helper.getBlockState(fuse));
            int beforeWire = DomainNetwork.sampleCopperVoltage(level, outputWorld);

            if (beforeRating != 15
                    || helper.getBlockState(fuse).getValue(CopperFuseBlock.TRIPPED)
                    || beforeQuality != PortQuality.VALID
                    || !CopperFuseBlock.protectionInitialized(level, fuseWorld)
                    || beforeOutput <= 0
                    || beforeWire != beforeOutput) {
                helper.fail("Precondition failed: high-rated fuse did not establish one verified-safe protected output"
                        + " | rating=" + beforeRating
                        + " tripped=" + helper.getBlockState(fuse).getValue(CopperFuseBlock.TRIPPED)
                        + " quality=" + beforeQuality
                        + " initialized=" + CopperFuseBlock.protectionInitialized(level, fuseWorld)
                        + " fuseV=" + beforeOutput
                        + " wireV=" + beforeWire, fuse);
                return;
            }

            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(fuseWorld), Direction.UP, fuseWorld, false);
            level.getBlockState(fuseWorld).useWithoutItem(level, player, hit);

            int afterRating = level.getBlockState(fuseWorld).getValue(CopperFuseBlock.RATING);
            if (afterRating != 1) {
                helper.fail("Precondition failed: real fuse configuration interaction did not reduce rating 15->1"
                        + " | rating=" + afterRating, fuse);
                return;
            }

            DomainNetwork.recomputeCopper(level, outputWorld);
            PortQuality immediateQuality = CopperFuseBlock.outputQuality(level, fuseWorld, level.getBlockState(fuseWorld));
            boolean immediateInitialized = CopperFuseBlock.protectionInitialized(level, fuseWorld);
            int immediateFuseVoltage = CopperFuseBlock.outputVoltage(level, fuseWorld);
            int immediateWireVoltage = DomainNetwork.sampleCopperVoltage(level, outputWorld);

            if (immediateQuality == PortQuality.VALID
                    || immediateInitialized
                    || immediateFuseVoltage != 0
                    || immediateWireVoltage != 0) {
                helper.fail("Fuse rating reduction left old verified-safe Copper authority live before protection re-evaluation"
                        + " | rating=" + beforeRating + "->" + afterRating
                        + " oldV=" + beforeOutput
                        + " quality=" + immediateQuality
                        + " initialized=" + immediateInitialized
                        + " fuseV=" + immediateFuseVoltage
                        + " wireV=" + immediateWireVoltage, fuse);
                return;
            }

            helper.runAfterDelay(4, () -> {
                var reevaluatedState = level.getBlockState(fuseWorld);
                PortQuality reevaluatedQuality = CopperFuseBlock.outputQuality(level, fuseWorld, reevaluatedState);
                int reevaluatedFuseVoltage = CopperFuseBlock.outputVoltage(level, fuseWorld);
                int reevaluatedWireVoltage = DomainNetwork.sampleCopperVoltage(level, outputWorld);

                if (!reevaluatedState.getValue(CopperFuseBlock.TRIPPED)
                        || reevaluatedQuality != PortQuality.FAULT
                        || reevaluatedFuseVoltage != 0
                        || reevaluatedWireVoltage != 0) {
                    helper.fail("Fuse did not perform a fresh fail-safe protection decision after rating reduction"
                            + " | tripped=" + reevaluatedState.getValue(CopperFuseBlock.TRIPPED)
                            + " quality=" + reevaluatedQuality
                            + " fuseV=" + reevaluatedFuseVoltage
                            + " wireV=" + reevaluatedWireVoltage, fuse);
                    return;
                }
                helper.succeed();
            });
        });
    }
}
