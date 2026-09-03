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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Visible indicator whose brightness follows the strongest incoming vanilla redstone signal. */
public class AnalogIndicatorBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

    public AnalogIndicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
    }

    @Override public MapCodec<AnalogIndicatorBlock> codec() { return RedstoneEngineering.ANALOG_INDICATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LEVEL); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        // Legacy physical behavior is still omni-input in 1.0.10; the contract makes
        // that explicit so a later facing migration can be performed without guessing.
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort("LEGACY_INPUT", side, EngineeringDomain.REDSTONE,
                        PortKind.ACTUATOR, PortDirection.INPUT, true, "signal"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> {
            int value = Math.max(0, Math.min(15, level.getSignal(pos.relative(side), side)));
            return EngineeringPortSnapshot.redstone(port, value, PortQuality.VALID);
        });
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) update(level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide) update(level, pos, state);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    private void update(Level level, BlockPos pos, BlockState state) {
        int value = Math.max(0, Math.min(15, level.getBestNeighborSignal(pos)));
        if (value != state.getValue(LEVEL)) level.setBlock(pos, state.setValue(LEVEL, value), Block.UPDATE_CLIENTS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) player.displayClientMessage(Component.literal(
                "Analog Process Indicator = " + state.getValue(LEVEL) + "/15 | ports=LEGACY_OMNIDIRECTIONAL"
        ), true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
