package dev.redstoneengineering.block;

import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Configurable one-input/one-output topology for non-redstone RSE domain processors.
 *
 * <p>Placement defaults to a straight series path. INPUT and OUTPUT remain explicit single ports,
 * but OUTPUT may be routed independently to a different horizontal face so a processor can make a
 * deliberate turn without becoming an implicit junction or multi-input device.</p>
 */
public abstract class DirectionalDomainBlock extends DomainBlock {
    /** OUTPUT face; retained as FACING for model/backward compatibility. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Dedicated INPUT face. It is never allowed to equal {@link #FACING}. */
    public static final DirectionProperty INPUT_FACING = DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);

    protected DirectionalDomainBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(INPUT_FACING, Direction.SOUTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState()
                .setValue(FACING, output)
                .setValue(INPUT_FACING, output.getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INPUT_FACING);
    }

    protected Direction outputSide(BlockState state) { return state.getValue(FACING); }
    protected Direction inputSide(BlockState state) { return state.getValue(INPUT_FACING); }
    protected BlockPos inputPos(BlockPos pos, BlockState state) { return pos.relative(inputSide(state)); }
    protected BlockPos outputPos(BlockPos pos, BlockState state) { return pos.relative(outputSide(state)); }

    public static Direction seriesOutputSide(BlockState state) { return state.getValue(FACING); }
    public static Direction seriesInputSide(BlockState state) { return state.getValue(INPUT_FACING); }

    public static Direction leftOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    protected static Direction rightOf(Direction facing) {
        return leftOf(facing).getOpposite();
    }

    /** Server-authoritative 90-degree rotation of the complete configured route. */
    public static boolean rotateSeriesAxis(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalDomainBlock block)) return false;

        Direction oldOutput = seriesOutputSide(state);
        Direction oldInput = seriesInputSide(state);
        Direction newOutput = rotateHorizontal(oldOutput, clockwise);
        Direction newInput = rotateHorizontal(oldInput, clockwise);
        BlockState next = state
                .setValue(FACING, newOutput)
                .setValue(INPUT_FACING, newInput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        notifyNeighbors(level, pos, block, oldInput, oldOutput, newInput, newOutput);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    /**
     * Routes only OUTPUT while keeping INPUT fixed. INPUT=OUTPUT is rejected by skipping that face,
     * so ordinary domain processors remain explicit 1-in/1-out devices even when turning a corner.
     */
    public static boolean rotateSeriesOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalDomainBlock block)) return false;

        Direction input = seriesInputSide(state);
        Direction oldOutput = seriesOutputSide(state);
        Direction newOutput = rotateHorizontal(oldOutput, clockwise);
        for (int i = 0; i < 3 && newOutput == input; i++) {
            newOutput = rotateHorizontal(newOutput, clockwise);
        }
        if (newOutput == input || newOutput == oldOutput) return false;

        BlockState next = state.setValue(FACING, newOutput);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        notifyNeighbors(level, pos, block, input, oldOutput, newOutput);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    private static Direction rotateHorizontal(Direction direction, boolean clockwise) {
        return clockwise ? direction.getClockWise() : direction.getCounterClockWise();
    }

    private static void notifyNeighbors(Level level, BlockPos pos, Block block, Direction... sides) {
        level.updateNeighborsAt(pos, block);
        for (Direction side : sides) {
            level.updateNeighborsAt(pos.relative(side), block);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                rotateSeriesOutput(level, pos, true);
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        "Series route | IN=" + seriesInputSide(next).getName().toUpperCase()
                                + " → OUT=" + seriesOutputSide(next).getName().toUpperCase()
                                + " | normal right-click opens Engineering UI"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
