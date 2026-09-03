package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Axial copper RC element: BACK input, FRONT output. */
public class CopperCapacitorBlock extends DirectionalCopperProcessorBlock {
    public static final IntegerProperty C_INDEX = IntegerProperty.create("capacitance", 0, 3);
    private static final String KEY = "copper_capacitor";

    public CopperCapacitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(C_INDEX, 1));
    }

    @Override
    public MapCodec<CopperCapacitorBlock> codec() {
        return RedstoneEngineering.COPPER_CAPACITOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(C_INDEX);
    }

    private static int tau(int index) {
        return switch (index) {
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 8;
            default -> 16;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 2);
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
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int inputVoltage = DomainNetwork.sampleCopperVoltage(level, inputPos(pos, state));
        int targetCharge = (int) Math.round(inputVoltage / 15.0 * 100.0);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, 1);
        int delta = targetCharge - runtime[0];
        int step = delta == 0
                ? 0
                : (int) Math.copySign(Math.max(1, Math.abs(delta) / tau(state.getValue(C_INDEX))), delta);

        runtime[0] = EngineeringMath.clamp(runtime[0] + step, 0, 100);
        int outputVoltage = EngineeringMath.clamp((int) Math.round(runtime[0] / 100.0 * 15.0), 0, 15);
        DomainNetwork.driveCopper(level, outputPos(pos, state), pos, outputVoltage);
        level.scheduleTick(pos, this, 2);
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        int charge = RuntimeIntStore.get(level, KEY, pos, 1)[0];
        return EngineeringMath.clamp((int) Math.round(charge / 100.0 * 15.0), 0, 15);
    }

    @Override
    protected int observedOutputVoltage(Level level, BlockPos pos, BlockState state) {
        return outputVoltage(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int capacitanceIndex = (state.getValue(C_INDEX) + 1) % 4;
            BlockState next = state.setValue(C_INDEX, capacitanceIndex);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            int charge = RuntimeIntStore.get(level, KEY, pos, 1)[0];
            player.displayClientMessage(Component.literal(
                    "Copper capacitor | BACK input -> FRONT output | C-index=" + (capacitanceIndex + 1)
                            + " | RC time-constant proxy=" + tau(capacitanceIndex) + " ticks | charge=" + charge + "%"
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
