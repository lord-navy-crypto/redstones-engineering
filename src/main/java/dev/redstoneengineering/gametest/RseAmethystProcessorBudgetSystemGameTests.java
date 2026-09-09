package dev.redstoneengineering.gametest;

import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.block.AmethystFrequencyFilterBlock;
import dev.redstoneengineering.block.AmethystResonanceDustBlock;
import dev.redstoneengineering.block.AmethystResonatorBlock;
import dev.redstoneengineering.block.DirectionalDomainBlock;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Processor-level proof that bounded Amethyst faults remain fail-closed downstream and recover. */
public final class RseAmethystProcessorBudgetSystemGameTests {
    private static final String TEMPLATE = "empty5x4x5";
    private static final int SHORT_COMPONENT_NODES = 16;
    private static final int OBSERVED_INDEX = 13;

    private RseAmethystProcessorBudgetSystemGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = RedstoneEngineering.MOD_ID, template = TEMPLATE, timeoutTicks = 160)
    public static void frequencyFilterPreservesStaleEvidenceAndRecoversAfterBudgetTruncation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        List<BlockPos> path = compactSnake(anchor);
        if (!allLoaded(level, path, helper)) return;

        BlockPos source = path.get(0);
        BlockPos input = path.get(OBSERVED_INDEX);
        BlockPos filter = input.east();
        BlockPos output = filter.east();
        if (!level.hasChunkAt(filter) || !level.hasChunkAt(output)) {
            helper.fail("Precondition failed: Amethyst processor spur is not fully loaded");
            return;
        }

        placeSource(level, source);
        placeDustRange(level, path, 1, SHORT_COMPONENT_NODES);
        wireDustPath(level, path.subList(0, SHORT_COMPONENT_NODES));
        attachEastSpur(level, input);
        level.setBlock(filter, RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get().defaultBlockState()
                .setValue(DirectionalDomainBlock.FACING, Direction.EAST)
                .setValue(AmethystFrequencyFilterBlock.TARGET, 6), Block.UPDATE_CLIENTS);
        level.setBlock(output, RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState()
                .setValue(AmethystResonanceDustBlock.WEST, true), Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeAmethyst(level, input);

        helper.runAfterDelay(6, () -> {
            AmethystFrequencyFilterBlock.FilterEvidence initial = AmethystFrequencyFilterBlock.evidence(
                    level, filter, level.getBlockState(filter));
            PortQuality initialOutputQuality = RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get()
                    .engineeringSnapshot(level, filter, level.getBlockState(filter), Direction.EAST)
                    .orElseThrow().quality();
            DomainNetwork.AmethystSample initialOutput = DomainNetwork.sampleAmethyst(level, output);
            if (AmethystResonanceDustBlock.status(level, input) != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE
                    || initial.inputQuality() != PortQuality.VALID || !initial.matched()
                    || initial.expectedOutputAmplitude() <= 0
                    || !initialOutput.active() || initialOutput.frequency() != 6 || initialOutput.amplitude() <= 0
                    || initialOutputQuality != PortQuality.VALID) {
                cleanup(level, path, filter, output);
                helper.fail("Precondition failed: short Amethyst filter pipeline did not establish a trusted carrier");
                return;
            }

            placeDustRange(level, path, SHORT_COMPONENT_NODES, path.size());
            wireDustPath(level, path);
            attachEastSpur(level, input);
            DomainNetwork.recomputeAmethyst(level, input);
            NetworkKernel.ScanStats truncatedStats = NetworkKernel.stats(level, "amethyst");

            helper.runAfterDelay(6, () -> {
                AmethystFrequencyFilterBlock.FilterEvidence fault = AmethystFrequencyFilterBlock.evidence(
                        level, filter, level.getBlockState(filter));
                PortQuality faultOutputQuality = RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get()
                        .engineeringSnapshot(level, filter, level.getBlockState(filter), Direction.EAST)
                        .orElseThrow().quality();
                DomainNetwork.AmethystSample faultOutput = DomainNetwork.sampleAmethyst(level, output);

                if (!truncatedStats.lastTruncated() || truncatedStats.lastNodes() != NetworkKernel.MAX_NODES
                        || AmethystResonanceDustBlock.status(level, input) != AmethystResonanceDustBlock.ResonanceStatus.STALE
                        || fault.inputQuality() != PortQuality.STALE || fault.matched() || fault.expectedOutputAmplitude() != 0
                        || faultOutput.active() || faultOutput.amplitude() != 0
                        || faultOutputQuality != PortQuality.STALE) {
                    cleanup(level, path, filter, output);
                    helper.fail("Amethyst filter leaked or collapsed STALE evidence after upstream budget truncation"
                            + " | nodes=" + truncatedStats.lastNodes() + " truncated=" + truncatedStats.lastTruncated()
                            + " inputStatus=" + AmethystResonanceDustBlock.status(level, input)
                            + " inputQuality=" + fault.inputQuality()
                            + " outputActive=" + faultOutput.active() + " outputA=" + faultOutput.amplitude()
                            + " outputQuality=" + faultOutputQuality);
                    return;
                }

                for (int i = path.size() - 1; i >= SHORT_COMPONENT_NODES; i--) {
                    level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                wireDustPath(level, path.subList(0, SHORT_COMPONENT_NODES));
                attachEastSpur(level, input);
                DomainNetwork.recomputeAmethyst(level, input);

                helper.runAfterDelay(6, () -> {
                    AmethystFrequencyFilterBlock.FilterEvidence recovered = AmethystFrequencyFilterBlock.evidence(
                            level, filter, level.getBlockState(filter));
                    PortQuality recoveredOutputQuality = RedstoneEngineering.AMETHYST_FREQUENCY_FILTER.get()
                            .engineeringSnapshot(level, filter, level.getBlockState(filter), Direction.EAST)
                            .orElseThrow().quality();
                    DomainNetwork.AmethystSample recoveredOutput = DomainNetwork.sampleAmethyst(level, output);
                    AmethystResonanceDustBlock.ResonanceStatus recoveredInputStatus = AmethystResonanceDustBlock.status(level, input);

                    if (recoveredInputStatus != AmethystResonanceDustBlock.ResonanceStatus.ACTIVE
                            || recovered.inputQuality() != PortQuality.VALID || !recovered.matched()
                            || recovered.expectedOutputAmplitude() != initial.expectedOutputAmplitude()
                            || !recoveredOutput.active() || recoveredOutput.frequency() != 6
                            || recoveredOutput.amplitude() != initialOutput.amplitude()
                            || recoveredOutputQuality != PortQuality.VALID) {
                        cleanup(level, path, filter, output);
                        helper.fail("Amethyst filter did not recover its original carrier after the upstream graph returned below budget"
                                + " | inputStatus=" + recoveredInputStatus
                                + " inputQuality=" + recovered.inputQuality()
                                + " expectedA=" + recovered.expectedOutputAmplitude()
                                + " output=" + recoveredOutput.frequency() + "/" + recoveredOutput.amplitude()
                                + " outputQuality=" + recoveredOutputQuality);
                        return;
                    }
                    cleanup(level, path, filter, output);
                    helper.succeed();
                });
            });
        });
    }

    private static List<BlockPos> compactSnake(BlockPos anchor) {
        int minX = anchor.getX() & ~15;
        int minZ = anchor.getZ() & ~15;
        int y = Math.min(anchor.getY() + 16, anchor.getY() + 16);
        List<BlockPos> path = new ArrayList<>(135);
        for (int row = 0; row < 10 && path.size() < 135; row++) {
            int z = minZ + row;
            if ((row & 1) == 0) {
                for (int x = minX; x <= minX + 13 && path.size() < 135; x++) path.add(new BlockPos(x, y, z));
            } else {
                for (int x = minX + 13; x >= minX && path.size() < 135; x--) path.add(new BlockPos(x, y, z));
            }
        }
        return path;
    }

    private static void placeSource(ServerLevel level, BlockPos source) {
        level.setBlock(source, RedstoneEngineering.AMETHYST_RESONATOR.get().defaultBlockState()
                .setValue(AmethystResonatorBlock.FREQUENCY, 6)
                .setValue(AmethystResonatorBlock.AMPLITUDE, 15), Block.UPDATE_CLIENTS);
        RuntimeIntStore.get(level, "amethyst_resonator", source, 1)[0] = 1;
    }

    private static void placeDustRange(ServerLevel level, List<BlockPos> path, int from, int to) {
        for (int i = from; i < to; i++) {
            level.setBlock(path.get(i), RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void wireDustPath(ServerLevel level, List<BlockPos> path) {
        for (int i = 1; i < path.size(); i++) {
            BlockPos pos = path.get(i);
            BlockState state = RedstoneEngineering.AMETHYST_RESONANCE_DUST.get().defaultBlockState();
            state = setArm(state, direction(pos, path.get(i - 1)), true);
            if (i + 1 < path.size()) state = setArm(state, direction(pos, path.get(i + 1)), true);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static void attachEastSpur(ServerLevel level, BlockPos input) {
        BlockState state = level.getBlockState(input);
        if (state.getBlock() instanceof AmethystResonanceDustBlock) {
            level.setBlock(input, state.setValue(AmethystResonanceDustBlock.EAST, true), Block.UPDATE_CLIENTS);
        }
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 1 && dz == 0) return Direction.EAST;
        if (dx == -1 && dz == 0) return Direction.WEST;
        if (dx == 0 && dz == 1) return Direction.SOUTH;
        if (dx == 0 && dz == -1) return Direction.NORTH;
        throw new IllegalArgumentException("Non-adjacent Amethyst path nodes: " + from + " -> " + to);
    }

    private static BlockState setArm(BlockState state, Direction direction, boolean value) {
        return switch (direction) {
            case NORTH -> state.setValue(AmethystResonanceDustBlock.NORTH, value);
            case EAST -> state.setValue(AmethystResonanceDustBlock.EAST, value);
            case SOUTH -> state.setValue(AmethystResonanceDustBlock.SOUTH, value);
            case WEST -> state.setValue(AmethystResonanceDustBlock.WEST, value);
            default -> throw new IllegalArgumentException("Vertical direction is invalid for this planar Amethyst path");
        };
    }

    private static boolean allLoaded(ServerLevel level, List<BlockPos> path, GameTestHelper helper) {
        for (BlockPos pos : path) {
            if (!level.hasChunkAt(pos)) {
                helper.fail("Precondition failed: compact Amethyst path crossed unloaded terrain at " + pos);
                return false;
            }
        }
        return true;
    }

    private static void cleanup(ServerLevel level, List<BlockPos> path, BlockPos filter, BlockPos output) {
        level.setBlock(output, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(filter, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int i = path.size() - 1; i >= 0; i--) level.setBlock(path.get(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}
