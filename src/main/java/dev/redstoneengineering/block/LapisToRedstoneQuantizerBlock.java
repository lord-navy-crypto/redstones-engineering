package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.DomainNetwork;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Explicit Lapis continuous-like -> vanilla Redstone 0..15 quantizer. */
public class LapisToRedstoneQuantizerBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    public LapisToRedstoneQuantizerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWER, 0));
    }

    @Override
    public MapCodec<LapisToRedstoneQuantizerBlock> codec() {
        return RedstoneEngineering.LAPIS_TO_REDSTONE_QUANTIZER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private Direction outputSide(BlockState state) {
        return state.getValue(FACING);
    }

    private Direction inputSide(BlockState state) {
        return outputSide(state).getOpposite();
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort(
                        "LAPIS INPUT",
                        inputSide(state),
                        EngineeringDomain.LAPIS,
                        PortKind.CONVERTER,
                        PortDirection.INPUT,
                        false,
                        "normalized"
                ),
                new EngineeringPort(
                        "REDSTONE OUTPUT",
                        outputSide(state),
                        EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER,
                        PortDirection.OUTPUT,
                        true,
                        "signal"
                )
        );
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();

        if (side == inputSide(state)) {
            var sample = DomainNetwork.sampleLapis(level, pos.relative(inputSide(state)));
            PortQuality quality = sample.valid() ? PortQuality.VALID : PortQuality.NO_SIGNAL;
            return Optional.of(new EngineeringPortSnapshot(
                    port.get(),
                    sample.valid() ? sample.value() / 100.0 : 0.0,
                    0.0,
                    1.0,
                    quality
            ));
        }
        return Optional.of(EngineeringPortSnapshot.redstone(
                port.get(),
                state.getValue(POWER),
                PortQuality.VALID
        ));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction == outputSide(state).getOpposite();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == outputSide(state).getOpposite() ? state.getValue(POWER) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var sample = DomainNetwork.sampleLapis(level, pos.relative(inputSide(state)));
        int power = sample.valid() ? Math.round(sample.value() * 15.0f / 100.0f) : 0;
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(outputSide(state)), this);
        }
        level.scheduleTick(pos, this, 2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            var sample = DomainNetwork.sampleLapis(level, pos.relative(inputSide(state)));
            player.displayClientMessage(Component.literal(
                    "Lapis → Redstone Quantizer | input="
                            + (sample.valid() ? String.format("%.2f", sample.value() / 100.0) : "INVALID")
                            + " | output=" + state.getValue(POWER) + "/15"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
