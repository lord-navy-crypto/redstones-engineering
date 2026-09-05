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
import dev.redstoneengineering.physics.InformationRuntime;
import dev.redstoneengineering.physics.VibrationNetwork;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * Redstone-driven vibration source. DOWN is the control-power input; UP and the
 * four horizontal faces are mechanical-vibration outputs.
 */
public class MechanicalExciterBlock extends Block implements EngineeringPortProvider {
    public static final IntegerProperty FREQUENCY = IntegerProperty.create("frequency", 1, 15);
    private static final Direction[] OUTPUTS = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public MechanicalExciterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FREQUENCY, 8));
    }

    @Override
    public MapCodec<MechanicalExciterBlock> codec() {
        return RedstoneEngineering.MECHANICAL_EXCITER_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FREQUENCY);
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("DRIVE IN", Direction.DOWN, EngineeringDomain.REDSTONE,
                        PortKind.CONTROL, PortDirection.INPUT, true, "signal"),
                vibrationOutput(Direction.UP),
                vibrationOutput(Direction.NORTH),
                vibrationOutput(Direction.SOUTH),
                vibrationOutput(Direction.WEST),
                vibrationOutput(Direction.EAST)
        );
    }

    private static EngineeringPort vibrationOutput(Direction side) {
        return new EngineeringPort("VIBRATION OUT", side, EngineeringDomain.MECHANICAL_VIBRATION,
                PortKind.ACTUATOR, PortDirection.OUTPUT, false, "amplitude");
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int amplitude = inputAmplitude(level, pos);
        if (side == Direction.DOWN) {
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), amplitude, PortQuality.VALID));
        }
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), amplitude, 0.0, 15.0,
                amplitude > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction.getOpposite() == Direction.DOWN;
    }

    private static int inputAmplitude(Level level, BlockPos pos) {
        return Math.max(0, Math.min(15, level.getSignal(pos.below(), Direction.DOWN)));
    }

    private void updateExcitation(BlockState state, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        int amplitude = inputAmplitude(level, pos);
        int frequency = state.getValue(FREQUENCY);
        if (amplitude <= 0) {
            InformationRuntime.clear(level, "mech_exciter", pos);
            return;
        }
        InformationRuntime.write(level, "mech_exciter", pos, amplitude, frequency, true, 100);
        VibrationNetwork.propagate(serverLevel, pos, amplitude, frequency, OUTPUTS);
    }

    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston
    ) {
        if (neighborPos.equals(pos.below())) updateExcitation(state, level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) updateExcitation(state, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) InformationRuntime.clear(level, "mech_exciter", pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                int frequency = state.getValue(FREQUENCY) % 15 + 1;
                BlockState next = state.setValue(FREQUENCY, frequency);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                updateExcitation(next, level, pos);
                player.displayClientMessage(Component.literal(
                        "Mechanical exciter f=" + frequency + " amplitude=" + inputAmplitude(level, pos)), true);
            } else {
                FieldDeviceUi.open(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
