package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.ThermalMassBlock;
import dev.redstoneengineering.physics.ThermalPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Coverage-completeness regression for thermal-state evolution. */
public final class RseThermalCoverageSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";

    private RseThermalCoverageSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 100)
    public static void incompleteEnvironmentCoverageCannotAdvanceThermalMassAndCompleteCoverageRecovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ThermalFixture fixture = findFixture(level, helper.absolutePos(new BlockPos(2, 1, 2)));
        if (fixture == null) {
            helper.fail("Precondition failed: could not find a loaded thermal endpoint chunk beside unavailable adjacent coverage");
            return;
        }

        BlockPos hot = fixture.hot();
        BlockPos mass = fixture.mass();
        BlockPos unknown = fixture.unknown();

        level.setBlock(hot, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(mass, RedstoneEngineering.THERMAL_MASS.get().defaultBlockState(), Block.UPDATE_ALL);

        if (level.hasChunkAt(unknown)) {
            cleanup(level, fixture);
            helper.fail("Precondition failed: thermal unknown-side chunk became available before the incomplete-coverage stage");
            return;
        }

        var state = level.getBlockState(mass);
        ThermalMassBlock.ThermalState partial = ThermalMassBlock.thermalState(level, mass, state);
        if (state.getValue(ThermalMassBlock.TEMPERATURE) != ThermalPhysics.AMBIENT
                || partial.environment() <= ThermalPhysics.AMBIENT
                || partial.target() <= partial.current()) {
            cleanup(level, fixture);
            helper.fail("Precondition failed: loaded thermal evidence did not request positive temperature evolution"
                    + " | current=" + partial.current()
                    + " env=" + partial.environment()
                    + " target=" + partial.target());
            return;
        }

        level.scheduleTick(mass, RedstoneEngineering.THERMAL_MASS.get(), 1);
        helper.runAfterDelay(3, () -> {
            if (level.hasChunkAt(unknown)) {
                cleanup(level, fixture);
                helper.fail("Fixture failure: unknown thermal coverage became loaded before the incomplete-stage assertion");
                return;
            }

            int afterIncomplete = level.getBlockState(mass).getValue(ThermalMassBlock.TEMPERATURE);
            if (afterIncomplete != ThermalPhysics.AMBIENT) {
                cleanup(level, fixture);
                helper.fail("Incomplete thermal coverage produced definitive physical temperature evolution"
                        + " | before=" + ThermalPhysics.AMBIENT
                        + " after=" + afterIncomplete
                        + " env=" + partial.environment()
                        + " target=" + partial.target()
                        + " unknown=" + unknown);
                return;
            }

            // Restore the missing adjacent evidence. The same thermal body and same hot boundary
            // must then execute a normal production tick and advance toward the resolved target.
            level.getChunkAt(unknown);
            level.scheduleTick(mass, RedstoneEngineering.THERMAL_MASS.get(), 1);
            helper.runAfterDelay(3, () -> {
                int recovered = level.getBlockState(mass).getValue(ThermalMassBlock.TEMPERATURE);
                cleanup(level, fixture);
                if (recovered <= ThermalPhysics.AMBIENT) {
                    helper.fail("Complete thermal coverage did not recover normal Thermal Mass evolution");
                    return;
                }
                helper.succeed();
            });
        });
    }

    private static ThermalFixture findFixture(ServerLevel level, BlockPos origin) {
        for (int chunks = 24; chunks <= 96; chunks += 8) {
            BlockPos candidate = origin.offset(chunks * 16, 0, 0);
            int chunkX = candidate.getX() >> 4;
            int chunkZ = candidate.getZ() >> 4;
            int chunkMinX = chunkX << 4;
            int chunkMinZ = chunkZ << 4;
            BlockPos mass = new BlockPos(chunkMinX + 15, origin.getY(), chunkMinZ + 8);
            BlockPos hot = mass.west();
            BlockPos unknown = mass.east();

            if (level.hasChunkAt(mass) || level.hasChunkAt(unknown)) continue;
            level.getChunkAt(mass);
            if (!level.hasChunkAt(unknown)) return new ThermalFixture(hot, mass, unknown);
        }
        return null;
    }

    private static void cleanup(ServerLevel level, ThermalFixture fixture) {
        if (level.hasChunkAt(fixture.hot())) level.setBlock(fixture.hot(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.hasChunkAt(fixture.mass())) level.setBlock(fixture.mass(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private record ThermalFixture(BlockPos hot, BlockPos mass, BlockPos unknown) {}
}
