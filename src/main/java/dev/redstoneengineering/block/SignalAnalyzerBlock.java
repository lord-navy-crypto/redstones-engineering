package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.EngineeringSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Engineering signal analyzer with two measurement topologies.
 *
 * TAP mode: non-invasive side probe. The analyzer never connects to redstone.
 * INLINE mode: TEST/FACING is the measured input and the opposite face is a
 * lossless 0..15 pass-through output, allowing the instrument to be inserted
 * directly into a signal path without changing the engineering scale.
 *
 * The block continuously records transient min/max/change/edge diagnostics;
 * high-cardinality statistics stay out of BlockState.
 */
public class SignalAnalyzerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 1);
    public static final IntegerProperty OUTPUT = IntegerProperty.create("output", 0, 15);

    private static final int TAP = 0;
    private static final int INLINE = 1;
    private static final String KEY = "signal_analyzer";
    private static final int RUNTIME_SIZE = 12;
    private static final int SAMPLE_PERIOD_TICKS = 2;

    public SignalAnalyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(MODE, TAP)
                        .setValue(OUTPUT, 0)
        );
    }

    @Override
    public MapCodec<SignalAnalyzerBlock> codec() {
        return RedstoneEngineering.SIGNAL_ANALYZER_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // TEST/FACING points back toward the clicked test point.
        return defaultBlockState().setValue(
                FACING,
                context.getClickedFace().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING, MODE, OUTPUT);
    }

    private static Direction testSide(BlockState state) {
        return state.getValue(FACING);
    }

    private static Direction inlineOutputSide(BlockState state) {
        return testSide(state).getOpposite();
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        if (state.getValue(MODE) != INLINE || direction == null) return false;
        Direction physicalSide = direction.getOpposite();
        return physicalSide == testSide(state)
                || physicalSide == inlineOutputSide(state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(MODE) == INLINE;
    }

    @Override
    protected int getSignal(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction direction
    ) {
        if (state.getValue(MODE) != INLINE) return 0;
        return direction == inlineOutputSide(state).getOpposite()
                ? state.getValue(OUTPUT)
                : 0;
    }

    @Override
    protected void onPlace(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        int measured = sampleTarget(level, pos, state);
        recordSample(level, pos, measured);

        int requestedOutput = state.getValue(MODE) == INLINE ? measured : 0;
        if (state.getValue(OUTPUT) != requestedOutput) {
            BlockState next = state.setValue(OUTPUT, requestedOutput);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(inlineOutputSide(next)), this);
            state = next;
        }

        level.scheduleTick(pos, this, SAMPLE_PERIOD_TICKS);
    }

    private static int sampleTarget(Level level, BlockPos pos, BlockState state) {
        Direction side = testSide(state);
        BlockPos targetPos = pos.relative(side);
        return measureNode(
                level,
                targetPos,
                level.getBlockState(targetPos),
                side
        );
    }

    private static void recordSample(Level level, BlockPos pos, int measured) {
        int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        r[0]++;

        if (r[8] == 0) {
            r[1] = measured;
            r[2] = measured;
            r[3] = measured;
            r[7] = now;
            r[8] = 1;
            return;
        }

        r[2] = Math.min(r[2], measured);
        r[3] = Math.max(r[3], measured);
        int delta = measured - r[1];
        r[9] = delta;
        r[10] = Math.max(r[10], Math.abs(delta));

        if (delta != 0) {
            r[4]++;
            if (delta > 0) r[5]++;
            else r[6]++;
            r[7] = now;
        }
        r[1] = measured;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown() && hitResult.getDirection() == Direction.UP) {
                RuntimeIntStore.remove(level, KEY, pos);
                player.displayClientMessage(
                        Component.literal("Analyzer statistics reset"),
                        true
                );
            } else if (player.isShiftKeyDown()) {
                showSixSideSurvey(level, pos, player);
            } else if (hitResult.getDirection() == Direction.UP) {
                int nextMode = state.getValue(MODE) == TAP ? INLINE : TAP;
                BlockState next = state
                        .setValue(MODE, nextMode)
                        .setValue(OUTPUT, nextMode == INLINE ? state.getValue(OUTPUT) : 0);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
                r[11]++;
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.relative(inlineOutputSide(next)), this);
                level.scheduleTick(pos, this, 1);
                player.displayClientMessage(
                        Component.literal(
                                "Analyzer mode → " + modeName(nextMode)
                                        + (nextMode == TAP
                                        ? " | non-invasive side tap"
                                        : " | TEST=input OPPOSITE=0..15 pass-through")
                        ),
                        true
                );
            } else {
                showMeasurement(level, pos, state, player);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void showMeasurement(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player
    ) {
        Direction side = testSide(state);
        BlockPos targetPos = pos.relative(side);
        BlockState targetState = level.getBlockState(targetPos);
        int measured = measureNode(level, targetPos, targetState, side);
        int percent = (int) Math.round((measured / 15.0) * 100.0);
        String targetName = BuiltInRegistries.BLOCK
                .getKey(targetState.getBlock())
                .toString();
        int[] r = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int now = (int) Math.min(Integer.MAX_VALUE, level.getGameTime());
        int age = r[8] == 0 ? 0 : Math.max(0, now - r[7]);

        player.displayClientMessage(
                Component.literal(
                        "Analyzer " + modeName(state.getValue(MODE))
                                + " | TEST=" + shortName(side)
                                + " value=" + measured + "/15 (" + percent + "%)"
                                + (state.getValue(MODE) == INLINE
                                ? " OUT=" + shortName(inlineOutputSide(state)) + ":" + state.getValue(OUTPUT)
                                : " non-invasive")
                                + " | min/max=" + (r[8] == 0 ? measured : r[2]) + "/" + (r[8] == 0 ? measured : r[3])
                                + " changes=" + r[4]
                                + " rising=" + r[5]
                                + " falling=" + r[6]
                                + " lastDelta=" + r[9]
                                + " maxDelta=" + r[10]
                                + " stableFor=" + age + "t"
                                + " samples=" + r[0]
                                + " modeSwitches=" + r[11]
                                + " | " + targetName
                ),
                true
        );
    }

    private static void showSixSideSurvey(
            Level level,
            BlockPos analyzerPos,
            Player player
    ) {
        StringBuilder text = new StringBuilder("Analyzer SURVEY");
        int strongest = -1;
        Direction strongestSide = Direction.NORTH;

        for (Direction side : Direction.values()) {
            BlockPos targetPos = analyzerPos.relative(side);
            int value = measureNode(
                    level,
                    targetPos,
                    level.getBlockState(targetPos),
                    side
            );

            text.append(" | ")
                    .append(shortName(side))
                    .append("=")
                    .append(value);

            if (value > strongest) {
                strongest = value;
                strongestSide = side;
            }
        }

        text.append(" | strongest=")
                .append(shortName(strongestSide))
                .append(":")
                .append(Math.max(0, strongest));

        player.displayClientMessage(Component.literal(text.toString()), true);
    }

    /**
     * Compatibility overload for callers that do not know the instrument-facing side.
     * Direction-aware instruments should prefer the four-argument overload below.
     */
    public static int measureNode(
            Level level,
            BlockPos targetPos,
            BlockState targetState
    ) {
        return measureNode(level, targetPos, targetState, null);
    }

    /**
     * Read an explicit redstone node or the signal emitted toward a known instrument side.
     * `instrumentToTarget` is the direction from the instrument to the tested block.
     */
    public static int measureNode(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            @Nullable Direction instrumentToTarget
    ) {
        // Explicit engineering/vanilla conductors are test nodes: read the node itself,
        // not the strongest source adjacent to it. Otherwise an attenuated dust node
        // (for example POWER=14 next to a 15 source) would be falsely reported as 15.
        if (targetState.getBlock() instanceof RedstoneSignalCableBlock) {
            return EngineeringSignal.clamp(RedstoneSignalCableBlock.power(level, targetPos));
        }
        if (targetState.getBlock() instanceof RedstoneCableJunctionBlock) {
            return EngineeringSignal.clamp(RedstoneCableJunctionBlock.power(level, targetPos));
        }
        if (targetState.getBlock() instanceof RedstoneCableTerminalBlock) {
            return EngineeringSignal.clamp(targetState.getValue(RedstoneCableTerminalBlock.POWER));
        }
        if (targetState.getBlock() instanceof RedStoneWireBlock) {
            return EngineeringSignal.clamp(targetState.getValue(RedStoneWireBlock.POWER));
        }

        if (instrumentToTarget != null) {
            int directional = targetState.getSignal(
                    level,
                    targetPos,
                    instrumentToTarget
            );
            if (directional > 0 || targetState.isSignalSource()) {
                return EngineeringSignal.clamp(directional);
            }
        } else {
            // Legacy/unknown orientation: preserve strongest-emitted-side behavior.
            int emitted = 0;
            for (Direction direction : Direction.values()) {
                emitted = Math.max(
                        emitted,
                        targetState.getSignal(level, targetPos, direction)
                );
            }
            if (emitted > 0) return EngineeringSignal.clamp(emitted);
        }

        // Passive/unknown blocks may be powered without emitting a useful direct value.
        // This fallback is intentionally last so it cannot contaminate explicit node readings.
        return EngineeringSignal.clamp(level.getBestNeighborSignal(targetPos));
    }

    private static String modeName(int mode) {
        return mode == INLINE ? "INLINE" : "TAP";
    }

    private static String shortName(Direction direction) {
        return switch (direction) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            case UP -> "U";
            case DOWN -> "D";
        };
    }
}
