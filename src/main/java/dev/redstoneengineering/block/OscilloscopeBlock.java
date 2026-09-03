package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.blockentity.OscilloscopeBlockEntity;
import dev.redstoneengineering.instrument.InstrumentNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class OscilloscopeBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public OscilloscopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<OscilloscopeBlock> codec() {
        return RedstoneEngineering.OSCILLOSCOPE_CODEC.value();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OscilloscopeBlockEntity(pos, state);
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
            level.scheduleTick(pos, this, OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, pos);
        int a = snapshot.valid(0) ? snapshot.values()[0] : -1;
        int b = snapshot.valid(1) ? snapshot.values()[1] : -1;

        if (level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity scope) {
            scope.addSample(a, b);
        }

        level.scheduleTick(pos, this, OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof OscilloscopeBlockEntity scope) {

            if (player.isShiftKeyDown()) {
                scope.arm();
                player.displayClientMessage(
                        Component.literal("Oscilloscope | trigger armed | " + scope.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == Direction.UP) {
                scope.cycleTriggerMode();
                player.displayClientMessage(
                        Component.literal("Oscilloscope | trigger mode → " + scope.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == state.getValue(FACING).getClockWise()) {
                scope.cycleTriggerLevel();
                player.displayClientMessage(
                        Component.literal("Oscilloscope | trigger level → " + scope.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == state.getValue(FACING).getCounterClockWise()) {
                scope.cycleTriggerChannel();
                player.displayClientMessage(
                        Component.literal("Oscilloscope | trigger source → " + scope.triggerStatus()),
                        true
                );
            } else if (hitResult.getDirection() == Direction.DOWN) {
                scope.moveCursorA();
                player.displayClientMessage(
                        Component.literal(
                                "Oscilloscope | cursor A moved | Δ="
                                        + scope.cursorDeltaSamples() + " samples / "
                                        + scope.cursorDeltaTicks() + " ticks"
                        ),
                        true
                );
            } else if (hitResult.getDirection() == state.getValue(FACING)) {
                scope.moveCursorB();
                player.displayClientMessage(
                        Component.literal(
                                "Oscilloscope | cursor B moved | Δ="
                                        + scope.cursorDeltaSamples() + " samples / "
                                        + scope.cursorDeltaTicks() + " ticks"
                        ),
                        true
                );
            } else {
                InstrumentNetwork.ProbeSnapshot snapshot = InstrumentNetwork.scan(level, pos);
                player.displayClientMessage(Component.literal(
                        "Scope | " + scope.triggerStatus()
                                + " | samplePeriod=" + OscilloscopeBlockEntity.SAMPLE_PERIOD_TICKS + "t"
                                + " capture=" + scope.sampleCount() + "/32"
                                + " | " + snapshot.networkStatus()
                                + channelStatus(scope, snapshot, 0, "A")
                                + channelStatus(scope, snapshot, 1, "B")
                                + " | cursors Δ=" + scope.cursorDeltaSamples()
                                + " samples/" + scope.cursorDeltaTicks() + "t"
                ), true);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static String channelStatus(
            OscilloscopeBlockEntity scope,
            InstrumentNetwork.ProbeSnapshot snapshot,
            int channel,
            String name
    ) {
        return " | " + name + "=" + snapshot.status(channel)
                + " [" + scope.waveform(channel) + "]"
                + " coverage=" + scope.coveragePercent(channel) + "%/" + scope.captureQuality(channel)
                + " min/max/p2p=" + scope.minimum(channel) + "/" + scope.maximum(channel) + "/" + scope.peakToPeak(channel)
                + " avg=" + decimal100(scope.average100(channel))
                + " meanStep=" + decimal100(scope.meanStep100(channel))
                + " period≈" + scope.estimatedPeriodSamples(channel) + " samples/"
                + scope.estimatedPeriodTicks(channel) + "t";
    }

    private static String decimal100(int value100) {
        if (value100 < 0) return "N/A";
        return (value100 / 100) + "." + String.format("%02d", value100 % 100);
    }
}
