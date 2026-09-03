package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.signal.EngineeringSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class SignalAnalyzerBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public SignalAnalyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<SignalAnalyzerBlock> codec() {
        return RedstoneEngineering.SIGNAL_ANALYZER_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The analyzer's probe face points back toward the clicked test point.
        return defaultBlockState().setValue(
                FACING,
                context.getClickedFace().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        // Non-invasive instrument: never becomes part of the redstone circuit.
        return false;
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
            if (player.isShiftKeyDown()) {
                showSixSideSurvey(level, pos, player);
            } else {
                Direction probeSide = state.getValue(FACING);
                BlockPos targetPos = pos.relative(probeSide);
                BlockState targetState = level.getBlockState(targetPos);
                int measured = measureNode(level, targetPos, targetState);
                int percent = (int) Math.round((measured / 15.0) * 100.0);
                String targetName = BuiltInRegistries.BLOCK
                        .getKey(targetState.getBlock())
                        .toString();

                player.displayClientMessage(
                        Component.literal(
                                "Analyzer | TEST=" + shortName(probeSide)
                                        + " | " + measured + "/15"
                                        + " | " + percent + "%"
                                        + " | " + targetName
                        ),
                        true
                );
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
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
                    level.getBlockState(targetPos)
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

    public static int measureNode(
            Level level,
            BlockPos targetPos,
            BlockState targetState
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

        // For an active non-wire block, prefer what that block itself emits.
        int emitted = 0;
        for (Direction direction : Direction.values()) {
            emitted = Math.max(
                    emitted,
                    targetState.getSignal(level, targetPos, direction)
            );
        }
        if (emitted > 0) {
            return EngineeringSignal.clamp(emitted);
        }

        // Passive/unknown blocks may be powered without emitting a useful direct value.
        // This fallback is intentionally last so it cannot contaminate an explicit node reading.
        return EngineeringSignal.clamp(level.getBestNeighborSignal(targetPos));
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
