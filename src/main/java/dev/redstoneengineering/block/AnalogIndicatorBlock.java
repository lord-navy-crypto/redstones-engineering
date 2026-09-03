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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Front-facing process display with a single BACK redstone input port. */
public class AnalogIndicatorBlock extends DirectionalRedstoneEndpointBlock implements EngineeringPortProvider {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

    public AnalogIndicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, 0));
    }

    @Override
    public MapCodec<AnalogIndicatorBlock> codec() {
        return RedstoneEngineering.ANALOG_INDICATOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "SIGNAL IN",
                backSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.ACTUATOR,
                PortDirection.INPUT,
                true,
                "signal"
        ));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side
    ) {
        return engineeringPort(state, side)
                .map(port -> EngineeringPortSnapshot.redstone(
                        port,
                        state.getValue(LEVEL),
                        PortQuality.VALID
                ));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return direction != null && connectionMatches(direction, backSide(state));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) update(level, pos, state);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean moved
    ) {
        if (!level.isClientSide) update(level, pos, state);
    }

    private void update(Level level, BlockPos pos, BlockState state) {
        int value = readBackInput(level, pos, state);
        if (value != state.getValue(LEVEL)) {
            level.setBlock(pos, state.setValue(LEVEL, value), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Analog Process Indicator = " + state.getValue(LEVEL) + "/15"
                            + " | FRONT display=" + frontSide(state).getName()
                            + " BACK IN=" + backSide(state).getName()
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
