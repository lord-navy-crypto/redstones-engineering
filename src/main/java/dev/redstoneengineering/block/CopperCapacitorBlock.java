package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.CopperObservationSupport;
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

/** Axial copper RC storage element: BACK input, FRONT output. */
public class CopperCapacitorBlock extends DirectionalCopperProcessorBlock {
    public static final IntegerProperty C_INDEX = IntegerProperty.create("capacitance", 0, 3);
    private static final String KEY = "copper_capacitor";
    private static final int CHARGE_SLOT = 0;
    private static final int INITIALIZED_SLOT = 1;
    private static final int INPUT_QUALITY_SLOT = 2;
    private static final int RUNTIME_SIZE = 3;

    public CopperCapacitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(C_INDEX, 1));
    }

    @Override public MapCodec<CopperCapacitorBlock> codec() { return RedstoneEngineering.COPPER_CAPACITOR_CODEC.value(); }

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
        int targetCharge = (int) Math.round(inputVoltage / 15.0 * 100.0);
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int delta = targetCharge - runtime[CHARGE_SLOT];
        int step = delta == 0
                ? 0
                : (int) Math.copySign(Math.max(1, Math.abs(delta) / tau(state.getValue(C_INDEX))), delta);

        runtime[CHARGE_SLOT] = EngineeringMath.clamp(runtime[CHARGE_SLOT] + step, 0, 100);
        runtime[INITIALIZED_SLOT] = 1;
        runtime[INPUT_QUALITY_SLOT] = input.quality().ordinal();
        DomainNetwork.driveCopper(level, outputPos(pos, state), pos, outputVoltageFromCharge(runtime[CHARGE_SLOT]));
        level.scheduleTick(pos, this, 2);
    }

    private static int outputVoltageFromCharge(int charge) {
        return EngineeringMath.clamp((int) Math.round(charge / 100.0 * 15.0), 0, 15);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    public static int chargePercent(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : EngineeringMath.clamp(runtime[CHARGE_SLOT], 0, 100);
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        return outputVoltageFromCharge(chargePercent(level, pos));
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        if (runtime == null || runtime[INITIALIZED_SLOT] == 0) return PortQuality.STALE;
        int index = Math.max(0, Math.min(PortQuality.values().length - 1, runtime[INPUT_QUALITY_SLOT]));
        PortQuality inputQuality = PortQuality.values()[index];
        if (inputQuality == PortQuality.FAULT
                || inputQuality == PortQuality.TOPOLOGY_ERROR
                || inputQuality == PortQuality.DOMAIN_MISMATCH) {
            return inputQuality;
        }
        // Stored charge is a legitimate local energy source while it decays after input removal.
        if (runtime[CHARGE_SLOT] > 0 || inputQuality == PortQuality.VALID) return PortQuality.VALID;
        return inputQuality == PortQuality.STALE ? PortQuality.STALE : PortQuality.NO_SIGNAL;
    }

    public static boolean outputInitialized(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[INITIALIZED_SLOT] == 1;
    }

    @Override protected int observedOutputVoltage(Level level, BlockPos pos, BlockState state) { return outputVoltage(level, pos); }
    @Override protected PortQuality observedOutputQuality(Level level, BlockPos pos, BlockState state) { return outputQuality(level, pos); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int capacitanceIndex = (state.getValue(C_INDEX) + 1) % 4;
            BlockState next = state.setValue(C_INDEX, capacitanceIndex);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 1);
            player.displayClientMessage(Component.literal(
                    "Copper capacitor | BACK input -> FRONT output | C-index=" + (capacitanceIndex + 1)
                            + " | RC time-constant proxy=" + tau(capacitanceIndex) + " ticks"
                            + " | charge=" + chargePercent(level, pos) + "%"
                            + " | Vout=" + outputVoltage(level, pos)
                            + " | quality=" + outputQuality(level, pos)
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
