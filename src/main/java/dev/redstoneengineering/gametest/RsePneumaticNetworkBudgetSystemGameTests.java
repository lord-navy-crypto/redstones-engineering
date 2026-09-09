package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.PneumaticNetwork;
import dev.redstoneengineering.physics.PneumaticObservationSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Strict lifecycle proof that pneumatic scan-budget truncation fails closed without losing valid-zero semantics. */
public final class RsePneumaticNetworkBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int SHORT_COMPONENT_NODES = 16;
    private static final int FIXTURE_Y_OFFSET = 64;

    private RsePneumaticNetworkBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 180)
    public static void truncatedPneumaticMustBecomeStaleThenRecoverValidZero(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        List<BlockPos> path = planarSnake(anchor);
        BlockPos firstPipe = path.get(0);

        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: pneumatic budget path crossed unloaded terrain at " + pos);
                return;
            }
        }

        placeRange(level, path, 0, SHORT_COMPONENT_NODES);
        PneumaticNetwork.recompute(level, firstPipe);

        helper.runAfterDelay(5, () -> {
            PneumaticObservationSupport.Observation baseline = PneumaticObservationSupport.observe(level, firstPipe);
            InformationRuntime.Snapshot baselineRuntime = InformationRuntime.snapshot(level, "pneumatic", firstPipe);
            if (baseline.quality() != PortQuality.VALID
                    || baseline.pressure() != 0
                    || !baselineRuntime.valid()
                    || baselineRuntime.value() != 0
                    || PneumaticNetwork.truncated(level, firstPipe)) {
                cleanup(level, path);
                helper.fail("Precondition failed: short pneumatic component did not establish solved VALID zero pressure");
                return;
            }

            placeRange(level, path, SHORT_COMPONENT_NODES, path.size());
            PneumaticNetwork.recompute(level, firstPipe);

            helper.runAfterDelay(5, () -> {
                int auditedNodes = PneumaticNetwork.collect(level, firstPipe).size();
                NetworkKernel.ScanStats stats = NetworkKernel.stats(level, "pneumatic");
                PneumaticObservationSupport.Observation fault = PneumaticObservationSupport.observe(level, firstPipe);
                InformationRuntime.Snapshot faultRuntime = InformationRuntime.snapshot(level, "pneumatic", firstPipe);
                boolean nodeTruncated = PneumaticNetwork.truncated(level, firstPipe);

                if (auditedNodes != NetworkKernel.MAX_NODES
                        || stats.lastNodes() != NetworkKernel.MAX_NODES
                        || !stats.lastTruncated()
                        || !nodeTruncated
                        || fault.quality() != PortQuality.STALE
                        || fault.pressure() != 0
                        || faultRuntime.valid()
                        || faultRuntime.value() != 0) {
                    cleanup(level, path);
                    helper.fail("Budget-truncated pneumatic component published a partial solved state"
                            + " | auditedNodes=" + auditedNodes
                            + " statsNodes=" + stats.lastNodes()
                            + " statsTruncated=" + stats.lastTruncated()
                            + " nodeTruncated=" + nodeTruncated
                            + " quality=" + fault.quality()
                            + " pressure=" + fault.pressure()
                            + " runtimeValid=" + faultRuntime.valid()
                            + " runtimePressure=" + faultRuntime.value());
                    return;
                }

                removeRange(level, path, SHORT_COMPONENT_NODES, path.size());
                PneumaticNetwork.recompute(level, firstPipe);

                helper.runAfterDelay(5, () -> {
                    PneumaticObservationSupport.Observation recovered = PneumaticObservationSupport.observe(level, firstPipe);
                    InformationRuntime.Snapshot recoveredRuntime = InformationRuntime.snapshot(level, "pneumatic", firstPipe);
                    boolean recoveredTruncated = PneumaticNetwork.truncated(level, firstPipe);
                    if (recovered.quality() != PortQuality.VALID
                            || recovered.pressure() != 0
                            || !recoveredRuntime.valid()
                            || recoveredRuntime.value() != 0
                            || recoveredTruncated) {
                        cleanup(level, path);
                        helper.fail("Pneumatic component did not recover exact solved VALID zero below budget"
                                + " | quality=" + recovered.quality()
                                + " pressure=" + recovered.pressure()
                                + " runtimeValid=" + recoveredRuntime.valid()
                                + " runtimePressure=" + recoveredRuntime.value()
                                + " truncated=" + recoveredTruncated);
                        return;
                    }
                    cleanup(level, path);
                    helper.succeed();
                });
            });
        });
    }

    private static List<BlockPos> planarSnake(BlockPos anchor) {
        int minX = anchor.getX() & ~15;
        int minZ = anchor.getZ() & ~15;
        int y = anchor.getY() + FIXTURE_Y_OFFSET;
        List<BlockPos> path = new ArrayList<>(135);
        for (int row = 0; row < 11 && path.size() < 135; row++) {
            int z = minZ + row;
            if ((row & 1) == 0) {
                for (int x = minX + 2; x <= minX + 14 && path.size() < 135; x++) path.add(new BlockPos(x, y, z));
            } else {
                for (int x = minX + 14; x >= minX + 2 && path.size() < 135; x--) path.add(new BlockPos(x, y, z));
            }
        }
        return path;
    }

    private static void placeRange(ServerLevel level, List<BlockPos> path, int from, int to) {
        for (int i = from; i < to; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.PNEUMATIC_PIPE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void removeRange(ServerLevel level, List<BlockPos> path, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void cleanup(ServerLevel level, List<BlockPos> path) {
        removeRange(level, path, 0, path.size());
    }
}
