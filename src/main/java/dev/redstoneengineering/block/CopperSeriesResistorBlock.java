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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Axial copper resistor: BACK input, FRONT output. */
public class CopperSeriesResistorBlock extends DirectionalCopperProcessorBlock {
    public static final IntegerProperty RESISTANCE = IntegerProperty.create("resistance", 1, 15);
    private static final String KEY = "copper_series_resistor";

    public CopperSeriesResistorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RESISTANCE, 4));
    }

    @Override
    public MapCodec<CopperSeriesResistorBlock> codec() {
        return RedstoneEngineering.COPPER_SERIES_RESISTOR_CODEC.value();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RESISTANCE);
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
        double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, state), 128);
        int outputVoltage = CircuitPhysics.divider(inputVoltage, state.getValue(RESISTANCE), loadResistance);

        RuntimeIntStore.get(level, KEY, pos, 1)[0] = outputVoltage;
        DomainNetwork.driveCopper(level, outputPos(pos, state), pos, outputVoltage);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int resistance = state.getValue(RESISTANCE);
            resistance = resistance >= 15 ? 1 : resistance + 1;
            BlockState next = state.setValue(RESISTANCE, resistance);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);

            double load = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, next), 128);
            int output = outputVoltage(level, pos);
            player.displayClientMessage(Component.literal(String.format(
                    "Copper series resistor | BACK input -> FRONT output | Rs=%d | estimated Rload=%.2f | Vout=%d",
                    resistance,
                    load,
                    output
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
