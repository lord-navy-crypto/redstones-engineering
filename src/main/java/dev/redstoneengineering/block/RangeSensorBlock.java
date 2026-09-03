package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.signal.EngineeringSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class RangeSensorBlock extends Block {
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    public static final IntegerProperty OUTPUT =
            IntegerProperty.create("output", 0, 15);

    public static final IntegerProperty MODE =
            IntegerProperty.create("mode", 0, 2);

    public static final IntegerProperty RANGE_MODE =
            IntegerProperty.create("range_mode", 0, 2);

    public static final IntegerProperty RESPONSE =
            IntegerProperty.create("response", 0, 3);

    public RangeSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OUTPUT, 0)
                        .setValue(MODE, 2)
                        .setValue(RANGE_MODE, 2)
                        .setValue(RESPONSE, 0)
        );
    }

    @Override
    public MapCodec<RangeSensorBlock> codec() {
        return RedstoneEngineering.RANGE_SENSOR_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING, OUTPUT, MODE, RANGE_MODE, RESPONSE);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        if (direction == null) return false;
        return direction == state.getValue(FACING);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            Direction direction
    ) {
        Direction outputSide =
                state.getValue(FACING).getOpposite();

        return direction == outputSide.getOpposite()
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
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            level.updateNeighborsAt(pos, this);

            level.updateNeighborsAt(
                    pos.relative(
                            state.getValue(FACING).getOpposite()
                    ),
                    this
            );
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        int range = rangeForMode(state.getValue(RANGE_MODE));

        int distance = detectDistance(
                level,
                pos,
                state.getValue(FACING),
                range,
                state.getValue(MODE)
        );

        int output = responseSignal(
                distance,
                range,
                state.getValue(RESPONSE)
        );

        int oldOutput = state.getValue(OUTPUT);

        if (oldOutput != output) {
            BlockState next = state.setValue(OUTPUT, output);

            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);

            level.updateNeighborsAt(
                    pos.relative(
                            next.getValue(FACING).getOpposite()
                    ),
                    this
            );
        }

        level.scheduleTick(pos, this, 4);
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
            BlockState next = state;
            Direction sensingFace = state.getValue(FACING);

            if (hitResult.getDirection() == sensingFace) {
                next = next.setValue(
                        RESPONSE,
                        (state.getValue(RESPONSE) + 1) % 4
                );
            } else if (player.isShiftKeyDown()) {
                next = next.setValue(
                        RANGE_MODE,
                        (state.getValue(RANGE_MODE) + 1) % 3
                );
            } else {
                next = next.setValue(
                        MODE,
                        (state.getValue(MODE) + 1) % 3
                );
            }

            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);

            player.displayClientMessage(
                    Component.literal(
                            "Range Sensor | detect="
                                    + modeName(next.getValue(MODE))
                                    + " | range="
                                    + rangeForMode(
                                            next.getValue(RANGE_MODE)
                                    )
                                    + " | response="
                                    + responseName(
                                            next.getValue(RESPONSE)
                                    )
                                    + " | OUT="
                                    + next.getValue(OUTPUT)
                    ),
                    true
            );
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static int detectDistance(
            Level level,
            BlockPos pos,
            Direction facing,
            int range,
            int mode
    ) {
        for (int distance = 1; distance <= range; distance++) {
            BlockPos target = pos.relative(facing, distance);
            if (!level.hasChunkAt(target)) break;

            boolean blockDetected =
                    !level.getBlockState(target).isAir();

            AABB box = new AABB(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getX() + 1,
                    target.getY() + 1,
                    target.getZ() + 1
            );

            boolean entityDetected =
                    !level.getEntitiesOfClass(
                            LivingEntity.class,
                            box
                    ).isEmpty();

            boolean detected = switch (mode) {
                case 0 -> blockDetected;
                case 1 -> entityDetected;
                case 2 -> blockDetected || entityDetected;
                default -> false;
            };

            if (detected) return distance;
        }

        return 0;
    }

    private static int responseSignal(
            int distance,
            int range,
            int response
    ) {
        if (distance <= 0) return 0;

        return switch (response) {
            case 0 -> {
                double proximity =
                        (range - distance + 1.0) / range;

                yield EngineeringSignal.clamp(
                        (int) Math.round(proximity * 15.0)
                );
            }

            case 1 -> {
                double normalized =
                        distance / (double) range;

                yield EngineeringSignal.clamp(
                        (int) Math.round(normalized * 15.0)
                );
            }

            case 2 -> distance <= Math.max(1, range / 2)
                    ? 15
                    : 0;

            case 3 -> {
                int low = Math.max(1, range / 3);
                int high = Math.max(low, (range * 2) / 3);

                yield distance >= low && distance <= high
                        ? 15
                        : 0;
            }

            default -> 0;
        };
    }

    private static int rangeForMode(int rangeMode) {
        return switch (rangeMode) {
            case 0 -> 4;
            case 1 -> 8;
            case 2 -> 15;
            default -> 15;
        };
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "BLOCK";
            case 1 -> "ENTITY";
            case 2 -> "ANY";
            default -> "ANY";
        };
    }

    private static String responseName(int response) {
        return switch (response) {
            case 0 -> "PROXIMITY";
            case 1 -> "DISTANCE";
            case 2 -> "THRESHOLD";
            case 3 -> "WINDOW";
            default -> "PROXIMITY";
        };
    }
}
