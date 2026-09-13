package dev.redstoneengineering.block;

import dev.redstoneengineering.core.port.EngineeringPortProvider;
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

import java.util.List;

/**
 * Configurable one-input/one-output topology for non-redstone RSE domain processors.
 *
 * <p>Placement defaults to a straight series path. INPUT and OUTPUT remain explicit single ports.
 * Operators may rotate the complete route or route either endpoint independently, while invalid
 * physical-port overlap states are rejected.</p>
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

    /** Compatibility entry point used by existing HMI rotate buttons. */
    public static boolean rotateSeriesAxis(Level level, BlockPos pos, boolean clockwise) {
        return rotateWholeRoute(level, pos, clockwise);
    }

    /** Server-authoritative 90-degree rotation of the complete configured route. */
    public static boolean rotateWholeRoute(Level level, BlockPos pos, boolean clockwise) {
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
        if (!physicalPortsDoNotOverlap(block, next)) return false;
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);

        notifyNeighbors(level, pos, block, oldInput, oldOutput, newInput, newOutput);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
        return true;
    }

    /**
     * Routes only INPUT while keeping OUTPUT fixed. Every candidate is checked against the block's
     * full engineering-port map, so RX cannot collide with an auxiliary physical connector.
     */
    public static boolean rotateSeriesInput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalDomainBlock block)) return false;

        Direction output = seriesOutputSide(state);
        Direction oldInput = seriesInputSide(state);
        Direction candidate = oldInput;
        for (int i = 0; i < 3; i++) {
            candidate = rotateHorizontal(candidate, clockwise);
            if (candidate == output) continue;
            BlockState next = state.setValue(INPUT_FACING, candidate);
            if (!physicalPortsDoNotOverlap(block, next)) continue;
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            notifyNeighbors(level, pos, block, oldInput, candidate, output);
            if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
            return true;
        }
        return hasAuxiliaryPorts(block, state) && rotateWholeRoute(level, pos, clockwise);
    }

    /**
     * Routes only OUTPUT while keeping INPUT fixed. Every candidate is checked against the block's
     * complete physical port map; dense multi-port devices fall back to rigid-layout rotation.
     */
    public static boolean rotateSeriesOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DirectionalDomainBlock block)) return false;

        Direction input = seriesInputSide(state);
        Direction oldOutput = seriesOutputSide(state);
        Direction candidate = oldOutput;
        for (int i = 0; i < 3; i++) {
            candidate = rotateHorizontal(candidate, clockwise);
            if (candidate == input) continue;
            BlockState next = state.setValue(FACING, candidate);
            if (!physicalPortsDoNotOverlap(block, next)) continue;
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            notifyNeighbors(level, pos, block, oldOutput, candidate, input);
            if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, block, 1);
            return true;
        }
        return hasAuxiliaryPorts(block, state) && rotateWholeRoute(level, pos, clockwise);
    }

    /** Domain processors that expose engineering ports may not place two physical ports on one face. */
    private static boolean physicalPortsDoNotOverlap(DirectionalDomainBlock block, BlockState state) {
        if (!(block instanceof EngineeringPortProvider provider)) return seriesInputSide(state) != seriesOutputSide(state);
        List<dev.redstoneengineering.core.port.EngineeringPort> ports = provider.engineeringPorts(state);
        for (int i = 0; i < ports.size(); i++) {
            for (int j = i + 1; j < ports.size(); j++) {
                if (ports.get(i).side() == ports.get(j).side()) return false;
            }
        }
        return true;
    }

    private static boolean hasAuxiliaryPorts(DirectionalDomainBlock block, BlockState state) {
        return block instanceof EngineeringPortProvider provider && provider.engineeringPorts(state).size() > 2;
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
                Direction clicked = hitResult.getDirection();
                Direction input = seriesInputSide(state);
                Direction output = seriesOutputSide(state);
                String action;
                boolean changed;
                if (clicked == input) {
                    changed = rotateSeriesInput(level, pos, true);
                    action = "RX";
                } else if (clicked == output) {
                    changed = rotateSeriesOutput(level, pos, true);
                    action = "TX";
                } else {
                    changed = rotateWholeRoute(level, pos, true);
                    action = "ROUTE";
                }
                BlockState next = level.getBlockState(pos);
                player.displayClientMessage(Component.literal(
                        (changed ? action + " rotated" : action + " unchanged")
                                + " | RX=" + seriesInputSide(next).getName().toUpperCase()
                                + " -> TX=" + seriesOutputSide(next).getName().toUpperCase()
                                + " | Shift-click RX/TX face to rotate that endpoint; other face rotates whole route"), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
