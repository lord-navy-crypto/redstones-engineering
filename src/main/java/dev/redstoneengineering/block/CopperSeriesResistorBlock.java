package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.CopperObservationSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.RuntimeIntStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import dev.redstoneengineering.ui.FieldDeviceUi;

/** Axial copper resistor: BACK input, FRONT output. */
public class CopperSeriesResistorBlock extends DirectionalCopperProcessorBlock {
    public static final IntegerProperty RESISTANCE = IntegerProperty.create("resistance", 1, 15);
    private static final String KEY = "copper_series_resistor";
    private static final int OUTPUT_VOLTAGE_SLOT = 0;
    private static final int INITIALIZED_SLOT = 1;
    private static final int INPUT_QUALITY_SLOT = 2;
    private static final int LOAD_RESISTANCE_MILLI_SLOT = 3;
    private static final int CURRENT_MILLI_SLOT = 4;
    private static final int RUNTIME_SIZE = 5;

    public CopperSeriesResistorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RESISTANCE, 4));
    }

    @Override public MapCodec<CopperSeriesResistorBlock> codec() { return RedstoneEngineering.COPPER_SERIES_RESISTOR_CODEC.value(); }

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
            if (level instanceof ServerLevel serverLevel) DomainNetwork.driveCopper(serverLevel, outputPos(pos, state), pos, 0);
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CopperNetworkSupport.recomputeAround(serverLevel, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CopperObservationSupport.Observation input = CopperObservationSupport.observe(level, inputPos(pos, state), pos);
        int inputVoltage = input.quality() == PortQuality.VALID ? input.voltage() : 0;
        double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, state), 128);
        int outputVoltage = input.quality() == PortQuality.VALID
                ? CircuitPhysics.divider(inputVoltage, state.getValue(RESISTANCE), loadResistance)
                : 0;

        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[OUTPUT_VOLTAGE_SLOT] = outputVoltage;
        runtime[INITIALIZED_SLOT] = 1;
        runtime[INPUT_QUALITY_SLOT] = input.quality().ordinal();
        runtime[LOAD_RESISTANCE_MILLI_SLOT] = (int) Math.round(loadResistance * 1000.0);
        runtime[CURRENT_MILLI_SLOT] = (int) Math.round(
                CircuitPhysics.current(inputVoltage, state.getValue(RESISTANCE) + loadResistance) * 1000.0);
        DomainNetwork.driveCopper(
                level,
                outputPos(pos, state),
                pos,
                outputVoltage,
                input.quality() == PortQuality.VALID
        );
        level.scheduleTick(pos, this, 2);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : runtime[OUTPUT_VOLTAGE_SLOT];
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        if (runtime == null || runtime[INITIALIZED_SLOT] == 0) return PortQuality.STALE;
        int index = Math.max(0, Math.min(PortQuality.values().length - 1, runtime[INPUT_QUALITY_SLOT]));
        return PortQuality.values()[index];
    }

    public static boolean outputInitialized(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[INITIALIZED_SLOT] == 1;
    }

    public static int loadResistanceMilli(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[LOAD_RESISTANCE_MILLI_SLOT]);
    }

    public static int currentMilli(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[CURRENT_MILLI_SLOT]);
    }

    public boolean adjustResistance(Level level, BlockPos pos, int delta) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return false;
        int resistance = state.getValue(RESISTANCE);
        int nextResistance = Math.floorMod((resistance - 1) + delta, 15) + 1;
        BlockState next = state.setValue(RESISTANCE, nextResistance);

        RuntimeIntStore.remove(level, KEY, pos);
        if (level instanceof ServerLevel serverLevel) {
            DomainNetwork.driveCopper(serverLevel, outputPos(pos, state), pos, 0, false);
        }
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 1);
        return true;
    }

    @Override protected int observedOutputVoltage(Level level, BlockPos pos, BlockState state) { return outputVoltage(level, pos); }
    @Override protected PortQuality observedOutputQuality(Level level, BlockPos pos, BlockState state) { return outputQuality(level, pos); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown() && hit.getDirection().getAxis().isVertical()
                    && player instanceof ServerPlayer serverPlayer) {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
                return InteractionResult.sidedSuccess(false);
            }
            adjustResistance(level, pos, 1);
            int resistance = level.getBlockState(pos).getValue(RESISTANCE);
            player.displayClientMessage(Component.literal(String.format(
                    "Copper series resistor | BACK input -> FRONT output | Rs=%d | retained Rload=%.3f | retained I≈%.3f | Vout=%d | quality=%s | sneak+top/bottom = Pioneer HMI",
                    resistance, loadResistanceMilli(level, pos) / 1000.0, currentMilli(level, pos) / 1000.0,
                    outputVoltage(level, pos), outputQuality(level, pos)
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
