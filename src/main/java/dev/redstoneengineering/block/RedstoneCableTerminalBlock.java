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
import dev.redstoneengineering.physics.RedstoneCableNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Explicit Vanilla-redstone ↔ insulated-cable boundary. */
public class RedstoneCableTerminalBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OUTPUT_MODE = BooleanProperty.create("output_mode");
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    public RedstoneCableTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OUTPUT_MODE, false)
                .setValue(POWER, 0));
    }

    @Override
    public MapCodec<RedstoneCableTerminalBlock> codec() {
        return RedstoneEngineering.REDSTONE_CABLE_TERMINAL_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OUTPUT_MODE, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public Direction vanillaSide(BlockState state) {
        return state.getValue(FACING);
    }

    public Direction cableSide(BlockState state) {
        return vanillaSide(state).getOpposite();
    }

    public int externalInput(Level level, BlockPos pos, BlockState state) {
        Direction direction = vanillaSide(state);
        return Math.max(0, Math.min(15, level.getSignal(pos.relative(direction), direction)));
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        boolean cableToVanilla = state.getValue(OUTPUT_MODE);
        EngineeringPort vanilla = new EngineeringPort(
                cableToVanilla ? "VANILLA OUT" : "VANILLA IN",
                vanillaSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.CONVERTER,
                cableToVanilla ? PortDirection.OUTPUT : PortDirection.INPUT,
                true,
                "signal"
        );
        EngineeringPort cable = new EngineeringPort(
                cableToVanilla ? "CABLE IN" : "CABLE OUT",
                cableSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.CONVERTER,
                cableToVanilla ? PortDirection.INPUT : PortDirection.OUTPUT,
                false,
                "signal"
        );
        return List.of(vanilla, cable);
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        Optional<EngineeringPort> resolved = engineeringPort(state, side);
        if (resolved.isEmpty()) return Optional.empty();

        int signal;
        if (side == vanillaSide(state) && !state.getValue(OUTPUT_MODE)) {
            signal = externalInput(level, pos, state);
        } else {
            signal = state.getValue(POWER);
        }
        return Optional.of(EngineeringPortSnapshot.redstone(resolved.get(), signal, PortQuality.VALID));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction == vanillaSide(state).getOpposite();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(OUTPUT_MODE);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(OUTPUT_MODE) && direction == vanillaSide(state).getOpposite()
                ? state.getValue(POWER)
                : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) RedstoneCableNetwork.recompute(server, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel server) RedstoneCableNetwork.recompute(server, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = state.setValue(OUTPUT_MODE, !state.getValue(OUTPUT_MODE));
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) RedstoneCableNetwork.recompute(server, pos);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(vanillaSide(next)), this);
            player.displayClientMessage(Component.literal(
                    "Redstone Cable Terminal | " + PortDiagnostics.terminal(next, this)
                            + " | signal=" + next.getValue(POWER) + "/15"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(vanillaSide(state)), this);
            if (level instanceof ServerLevel server) {
                RedstoneCableNetwork.recompute(server, pos.relative(cableSide(state)));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
