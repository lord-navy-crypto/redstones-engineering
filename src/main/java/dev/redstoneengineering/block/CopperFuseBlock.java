package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Axial copper safety element: BACK input, FRONT protected output. */
public class CopperFuseBlock extends DirectionalCopperProcessorBlock {
    private static final String KEY = "copper_fuse";
    public static final IntegerProperty RATING = IntegerProperty.create("rating", 1, 15);
    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");

    public CopperFuseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RATING, 4).setValue(TRIPPED, false));
    }

    @Override
    public MapCodec<CopperFuseBlock> codec() {
        return RedstoneEngineering.COPPER_FUSE_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RATING, TRIPPED);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int inputVoltage = DomainNetwork.sampleCopperVoltage(level, inputPos(pos, state));
        double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, state), 128);
        double current = CircuitPhysics.current(inputVoltage, loadResistance);
        boolean tripped = state.getValue(TRIPPED) || current > state.getValue(RATING);
        BlockState next = state.setValue(TRIPPED, tripped);

        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        int outputVoltage = tripped ? 0 : inputVoltage;
        RuntimeIntStore.get(level, KEY, pos, 1)[0] = outputVoltage;
        DomainNetwork.driveCopper(level, outputPos(pos, next), pos, outputVoltage);
        level.scheduleTick(pos, this, 2);
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, 1)[0];
    }

    @Override
    protected int observedOutputVoltage(Level level, BlockPos pos, BlockState state) {
        return outputVoltage(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                DomainNetwork.driveCopper(serverLevel, outputPos(pos, state), pos, 0);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = state;
            if (player.isShiftKeyDown()) {
                next = state.setValue(TRIPPED, false);
            } else {
                int rating = state.getValue(RATING);
                next = state.setValue(RATING, rating >= 15 ? 1 : rating + 1);
            }
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            player.displayClientMessage(Component.literal(
                    "Copper fuse | BACK input -> FRONT protected output | current rating=" + next.getValue(RATING)
                            + " | " + (next.getValue(TRIPPED) ? "TRIPPED" : "armed")
                            + (player.isShiftKeyDown() ? " | reset" : "")
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
