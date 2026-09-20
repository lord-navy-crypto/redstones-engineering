package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.CopperObservationSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.NetworkKernel;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.CopperCapacitorLogic;
import dev.redstoneengineering.ui.FieldDeviceUi;
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

import java.util.Locale;

/**
 * Axial copper RC storage element: BACK input, FRONT output.
 *
 * Source-present charging follows the selected capacitance time constant. When the source
 * disappears, downstream equivalent resistance controls discharge; open circuit retains charge
 * longest through leakage only. Bounded/truncated load scans are surfaced as STALE evidence
 * instead of being treated as authoritative RC data.
 */
public class CopperCapacitorBlock extends DirectionalCopperProcessorBlock {
    public static final IntegerProperty C_INDEX = IntegerProperty.create("capacitance", 0, 3);
    private static final String KEY = "copper_capacitor";
    private static final int CHARGE_SLOT = 0;
    private static final int INITIALIZED_SLOT = 1;
    private static final int INPUT_QUALITY_SLOT = 2;
    private static final int LOAD_RESISTANCE_X100_SLOT = 3;
    private static final int EFFECTIVE_TAU_SLOT = 4;
    private static final int LOAD_TRUNCATED_SLOT = 5;
    private static final int RUNTIME_SIZE = 6;
    private static final int OPEN_CIRCUIT_SENTINEL = Integer.MAX_VALUE;

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

    public static int configuredBaseTau(Level level, BlockPos pos, BlockState state) {
        int fallback = CopperCapacitorLogic.chargeTau(state.getValue(C_INDEX));
        if (level instanceof ServerLevel serverLevel) {
            return Math.max(1, Math.min(64, EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(fallback, 0, 0, 0)).a()));
        }
        return fallback;
    }

    public static boolean setConfiguredBaseTau(ServerLevel level, BlockPos pos, int tau) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CopperCapacitorBlock capacitor)) return false;
        int bounded = Math.max(1, Math.min(64, tau));
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(bounded, 0, 0, 0));
        if (changed) level.scheduleTick(pos, capacitor, 1);
        return changed;
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
                DomainNetwork.driveCopper(serverLevel, outputPos(pos, state), pos, 0, false);
            }
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CopperNetworkSupport.recomputeAround(serverLevel, pos);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CopperObservationSupport.Observation input = CopperObservationSupport.observe(level, inputPos(pos, state), pos);
        boolean sourceDriven = input.quality() == PortQuality.VALID;
        boolean sourceAbsent = input.quality() == PortQuality.NO_SIGNAL;

        double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, state), 128);
        boolean loadTruncated = NetworkKernel.stats(level, "copper_load").lastTruncated();

        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (!loadTruncated && (sourceDriven || sourceAbsent)) {
            int baseTau = configuredBaseTau(level, pos, state);
            runtime[CHARGE_SLOT] = CopperCapacitorLogic.stepChargeBaseTau(
                    runtime[CHARGE_SLOT],
                    sourceDriven ? input.voltage() : 0,
                    sourceDriven,
                    baseTau,
                    loadResistance
            );
            runtime[EFFECTIVE_TAU_SLOT] = sourceDriven
                    ? baseTau
                    : CopperCapacitorLogic.dischargeTauBase(baseTau, loadResistance);
        }
        // STALE/FAULT/DOMAIN/TOPOLOGY input evidence cannot tell us whether the source is
        // charging or absent. Freeze the stored-energy integration rather than inventing discharge.

        runtime[INITIALIZED_SLOT] = 1;
        runtime[INPUT_QUALITY_SLOT] = input.quality().ordinal();
        runtime[LOAD_RESISTANCE_X100_SLOT] = Double.isInfinite(loadResistance)
                ? OPEN_CIRCUIT_SENTINEL
                : (int) Math.min(Integer.MAX_VALUE - 1L, Math.round(Math.max(0.0, loadResistance) * 100.0));
        runtime[LOAD_TRUNCATED_SLOT] = loadTruncated ? 1 : 0;

        int outputVoltage = outputVoltageFromCharge(runtime[CHARGE_SLOT]);
        boolean outputValid = !loadTruncated && outputQuality(level, pos) == PortQuality.VALID;
        DomainNetwork.driveCopper(level, outputPos(pos, state), pos, outputVoltage, outputValid);
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

    public static double observedLoadResistance(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        if (runtime == null) return Double.POSITIVE_INFINITY;
        int encoded = runtime[LOAD_RESISTANCE_X100_SLOT];
        return encoded == OPEN_CIRCUIT_SENTINEL ? Double.POSITIVE_INFINITY : Math.max(0.0, encoded / 100.0);
    }

    public static int effectiveTau(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[EFFECTIVE_TAU_SLOT]);
    }

    public static boolean loadTruncated(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[LOAD_TRUNCATED_SLOT] != 0;
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
        if (runtime[LOAD_TRUNCATED_SLOT] != 0 || inputQuality == PortQuality.STALE) {
            return PortQuality.STALE;
        }

        // A verified absent source does not erase energy already stored locally in the capacitor.
        if (runtime[CHARGE_SLOT] > 0 || inputQuality == PortQuality.VALID) return PortQuality.VALID;
        return PortQuality.NO_SIGNAL;
    }

    public static boolean outputInitialized(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[INITIALIZED_SLOT] == 1;
    }

    @Override protected int observedOutputVoltage(Level level, BlockPos pos, BlockState state) { return outputVoltage(level, pos); }
    @Override protected PortQuality observedOutputQuality(Level level, BlockPos pos, BlockState state) { return outputQuality(level, pos); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            int capacitanceIndex = (state.getValue(C_INDEX) + 1) % 4;
            BlockState next = state.setValue(C_INDEX, capacitanceIndex);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).setExtendedParameters(
                        serverLevel, pos,
                        new EngineeringDeviceParameters.ExtendedParameters(
                                CopperCapacitorLogic.chargeTau(capacitanceIndex), 0, 0, 0));
            }
            level.scheduleTick(pos, this, 1);

            double load = observedLoadResistance(level, pos);
            String loadText = Double.isInfinite(load) ? "OPEN" : String.format(Locale.ROOT, "%.2f", load);
            player.displayClientMessage(Component.literal(
                    "Copper capacitor | BACK input -> FRONT output | C-index=" + (capacitanceIndex + 1)
                            + " | baseTau=" + CopperCapacitorLogic.chargeTau(capacitanceIndex) + "t"
                            + " | effectiveTau=" + effectiveTau(level, pos) + "t"
                            + " | Rload=" + loadText
                            + " | loadScan=" + (loadTruncated(level, pos) ? "TRUNCATED" : "COMPLETE")
                            + " | charge=" + chargePercent(level, pos) + "%"
                            + " | Vout=" + outputVoltage(level, pos)
                            + " | quality=" + outputQuality(level, pos)
            ), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
