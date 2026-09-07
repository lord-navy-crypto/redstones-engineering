package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.physics.DataBusNetwork;
import dev.redstoneengineering.physics.DifferentialNetwork;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.SerialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime contracts proving that RSE communication media make different engineering trade-offs. */
public final class RseCommunicationIdentityGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseCommunicationIdentityGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void informationFreshnessIsIndependentFromQuality(GameTestHelper helper) {
        BlockPos probe = helper.absolutePos(new BlockPos(2, 1, 2));
        String medium = "identity_freshness";

        InformationRuntime.write(helper.getLevel(), medium, probe, 77, 3, true, 61);
        InformationRuntime.Snapshot first = InformationRuntime.snapshot(helper.getLevel(), medium, probe);
        if (!first.valid()
                || first.value() != 77
                || first.selector() != 3
                || first.qualityPercent() != 61
                || first.ageTicks() != 0) {
            helper.fail("Information envelope did not preserve independent payload/selector/quality/freshness semantics",
                    new BlockPos(2, 1, 2));
            return;
        }

        helper.runAfterDelay(5, () -> {
            InformationRuntime.Snapshot aged = InformationRuntime.snapshot(helper.getLevel(), medium, probe);
            if (aged.qualityPercent() != 61 || aged.ageTicks() < 5) {
                helper.fail("Information quality changed merely because the payload aged, or freshness did not advance",
                        new BlockPos(2, 1, 2));
                return;
            }

            InformationRuntime.write(helper.getLevel(), medium, probe, 77, 3, true, 61);
            InformationRuntime.Snapshot refreshed = InformationRuntime.snapshot(helper.getLevel(), medium, probe);
            if (refreshed.qualityPercent() != 61 || refreshed.ageTicks() > 1) {
                helper.fail("Fresh update did not reset age independently from quality",
                        new BlockPos(2, 1, 2));
                return;
            }
            InformationRuntime.clear(helper.getLevel(), medium, probe);
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void differentialTradesPayloadDensityForLinkMargin(GameTestHelper helper) {
        BlockPos serialStart = new BlockPos(0, 1, 1);
        BlockPos differentialStart = new BlockPos(0, 1, 3);
        for (int x = 0; x < 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), RedstoneEngineering.SERIAL_DATA_LINE.get().defaultBlockState());
            helper.setBlock(new BlockPos(x, 1, 3), RedstoneEngineering.DIFFERENTIAL_DATA_PAIR.get().defaultBlockState());
        }

        BlockPos serialWorld = helper.absolutePos(serialStart);
        BlockPos differentialWorld = helper.absolutePos(differentialStart);
        SerialNetwork.drive(helper.getLevel(), serialWorld, 0xA5, 8, true, 100);
        DifferentialNetwork.drive(helper.getLevel(), differentialWorld, 1);

        BlockPos serialEnd = helper.absolutePos(new BlockPos(3, 1, 1));
        BlockPos differentialEnd = helper.absolutePos(new BlockPos(3, 1, 3));
        int serialQuality = InformationRuntime.quality(helper.getLevel(), "serial", serialEnd);
        int differentialQuality = InformationRuntime.quality(helper.getLevel(), "diff", differentialEnd);

        if ((InformationRuntime.value(helper.getLevel(), "serial", serialEnd) & 0xFF) != 0xA5
                || (InformationRuntime.value(helper.getLevel(), "diff", differentialEnd) & 1) != 1) {
            helper.fail("Serial/differential payload identities were not preserved", new BlockPos(3, 1, 2));
            return;
        }
        if (differentialQuality <= serialQuality) {
            helper.fail("One-bit differential link did not receive stronger RSE link margin than equal-length serial",
                    new BlockPos(3, 1, 3));
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 30)
    public static void dataBusSameValueContentionConsumesMarginAndConflictInvalidates(GameTestHelper helper) {
        BlockPos busPos = new BlockPos(2, 1, 2);
        BlockPos driverA = new BlockPos(1, 1, 2);
        BlockPos driverB = new BlockPos(3, 1, 2);
        helper.setBlock(busPos, RedstoneEngineering.EIGHT_BIT_DATA_BUS.get().defaultBlockState());
        helper.setBlock(driverA, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST));
        helper.setBlock(driverB, RedstoneEngineering.REDSTONE_BYTE_ENCODER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.WEST));

        BlockPos busWorld = helper.absolutePos(busPos);
        BlockPos driverAWorld = helper.absolutePos(driverA);
        BlockPos driverBWorld = helper.absolutePos(driverB);
        InformationRuntime.write(helper.getLevel(), "bus8_out", driverAWorld, 42, 0, true, 100);
        InformationRuntime.write(helper.getLevel(), "bus8_out", driverBWorld, 42, 0, true, 100);
        DataBusNetwork.resolve(helper.getLevel(), DataBusNetwork.collect(helper.getLevel(), busWorld));

        DataBusNetwork.Diagnostics contended = DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld);
        int contendedQuality = InformationRuntime.quality(helper.getLevel(), "bus8", busWorld);
        if (!DataBusNetwork.valid(helper.getLevel(), busWorld)
                || DataBusNetwork.sample(helper.getLevel(), busWorld) != 42
                || contended.driverCount() != 2
                || contended.distinctValues() != 1
                || contended.sameValueMultiDriverFrames() < 1
                || contendedQuality >= 100) {
            helper.fail("Same-value multi-driver bus must remain usable while consuming bus margin", busPos);
            return;
        }

        InformationRuntime.write(helper.getLevel(), "bus8_out", driverBWorld, 43, 0, true, 100);
        DataBusNetwork.resolve(helper.getLevel(), DataBusNetwork.collect(helper.getLevel(), busWorld));
        DataBusNetwork.Diagnostics conflicted = DataBusNetwork.getDiagnostics(helper.getLevel(), busWorld);
        if (DataBusNetwork.valid(helper.getLevel(), busWorld)
                || InformationRuntime.quality(helper.getLevel(), "bus8", busWorld) != 0
                || conflicted.distinctValues() != 2
                || conflicted.conflictFrames() < 1) {
            helper.fail("Different-value multi-driver bus must become a hard conflict instead of random corruption", busPos);
            return;
        }
        helper.succeed();
    }
}
