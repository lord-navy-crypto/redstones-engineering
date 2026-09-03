package dev.redstoneengineering.block;

import dev.redstoneengineering.core.domain.EngineeringDomain;
import dev.redstoneengineering.core.port.EngineeringPort;
import dev.redstoneengineering.core.port.EngineeringPortProvider;
import dev.redstoneengineering.core.port.EngineeringPortSnapshot;
import dev.redstoneengineering.core.port.PortDirection;
import dev.redstoneengineering.core.port.PortKind;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.signal.EngineeringSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/** Shared FRONT-only 0..15 redstone output contract for engineering sensors. */
public abstract class DirectionalRedstoneSensorBlock extends DirectionalRedstoneEndpointBlock implements EngineeringPortProvider {
    public static final IntegerProperty POWER = IntegerProperty.create("power", 0, 15);

    protected DirectionalRedstoneSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(new EngineeringPort(
                "SENSOR OUT",
                frontSide(state),
                EngineeringDomain.REDSTONE,
                PortKind.SENSOR,
                PortDirection.OUTPUT,
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
                        state.getValue(POWER),
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
        return direction != null && connectionMatches(direction, frontSide(state));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return isQueriedFrom(state, direction, frontSide(state)) ? state.getValue(POWER) : 0;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !state.is(oldState.getBlock())) level.scheduleTick(pos, this, 1);
    }

    protected final void updateSensorOutput(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            int measuredValue,
            int nextSampleDelay
    ) {
        int value = EngineeringSignal.clamp(measuredValue);
        BlockState current = state;
        if (value != state.getValue(POWER)) {
            current = state.setValue(POWER, value);
            level.setBlock(pos, current, Block.UPDATE_CLIENTS);
            notifyFrontOutput(level, pos, current);
        }
        level.scheduleTick(pos, this, nextSampleDelay);
    }
}
