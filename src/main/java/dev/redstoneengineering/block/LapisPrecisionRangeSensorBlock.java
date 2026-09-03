package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.EngineeringMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Directional time-of-flight-style range sensor, represented as a normalized Lapis quantity. */
public class LapisPrecisionRangeSensorBlock extends AbstractLapisTransducerBlock {
    public static final IntegerProperty RANGE_INDEX = IntegerProperty.create("range_index", 0, 3);
    private static final int[] RANGES = {8, 16, 32, 64};

    public LapisPrecisionRangeSensorBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(RANGE_INDEX, 1));
    }

    @Override public MapCodec<LapisPrecisionRangeSensorBlock> codec() { return RedstoneEngineering.LAPIS_PRECISION_RANGE_SENSOR_CODEC.value(); }
    @Override protected String runtimeKey() { return "lapis_precision_range_sensor"; }
    @Override protected String instrumentName() { return "Lapis Precision Range Sensor"; }
    @Override protected String rangeText(BlockState state) { return RANGES[state.getValue(RANGE_INDEX)] + " blocks"; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RANGE_INDEX);
    }

    @Override
    protected Measurement sense(ServerLevel level, BlockPos pos, BlockState state) {
        int max = RANGES[state.getValue(RANGE_INDEX)];
        Direction direction = inputSide(state);
        int distance = -1;
        for (int i = 1; i <= max; i++) {
            BlockPos p = pos.relative(direction, i);
            if (!level.hasChunkAt(p)) break;
            BlockState target = level.getBlockState(p);
            if (!target.isAir() || !level.getFluidState(p).isEmpty()) {
                distance = i;
                break;
            }
        }
        if (distance < 0) return new Measurement(0, false, "no target within " + max + " blocks");
        int normalized = Math.round(EngineeringMath.clamp(distance, 0, max) * 100.0f / max);
        return new Measurement(normalized, true, "distance=" + distance + "/" + max + " blocks");
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide && player.isShiftKeyDown()) {
            int next = (state.getValue(RANGE_INDEX) + 1) & 3;
            state = state.setValue(RANGE_INDEX, next);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Precision Range Sensor range = " + RANGES[next] + " blocks"), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
