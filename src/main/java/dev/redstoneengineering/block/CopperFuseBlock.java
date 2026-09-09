package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.core.port.PortQuality;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
import dev.redstoneengineering.physics.CopperObservationSupport;
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.NetworkKernel;
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

import java.util.Locale;

/** Axial copper safety element: BACK input, FRONT protected output. */
public class CopperFuseBlock extends DirectionalCopperProcessorBlock {
    private static final String KEY = "copper_fuse";
    private static final String QUALITY_KEY = "copper_fuse_quality";
    private static final int OUTPUT_VOLTAGE = 0;
    private static final int LAST_EVALUATED_TRIP = 1;
    private static final int PROTECTION_STATE_INITIALIZED = 2;
    // Preserve the established electrical-protection runtime layout. New diagnostic
    // quality evidence is intentionally stored under a separate runtime key.
    private static final int RUNTIME_SIZE = 3;
    private static final int INPUT_QUALITY = 0;
    private static final int QUALITY_RUNTIME_SIZE = 1;
    public static final IntegerProperty RATING = IntegerProperty.create("rating", 1, 15);
    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");

    public CopperFuseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RATING, 4).setValue(TRIPPED, false));
    }

    @Override public MapCodec<CopperFuseBlock> codec() { return RedstoneEngineering.COPPER_FUSE_CODEC.value(); }

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
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (runtime[PROTECTION_STATE_INITIALIZED] == 0) {
            // A persisted TRIPPED block is historical state, not evidence that a new incident occurred now.
            runtime[LAST_EVALUATED_TRIP] = state.getValue(TRIPPED) ? 1 : 0;
            runtime[PROTECTION_STATE_INITIALIZED] = 1;
        }

        CopperObservationSupport.Observation input = CopperObservationSupport.observe(level, inputPos(pos, state), pos);
        int[] qualityRuntime = RuntimeIntStore.get(level, QUALITY_KEY, pos, QUALITY_RUNTIME_SIZE);
        qualityRuntime[INPUT_QUALITY] = input.quality().ordinal();
        int inputVoltage = input.quality() == PortQuality.VALID ? input.voltage() : 0;
        double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, state), 128);
        boolean loadTruncated = NetworkKernel.stats(level, "copper_load").lastTruncated();

        // A bounded load scan is not authoritative protection evidence. Do not turn a partial
        // equivalent resistance into a trip/ready decision or event. Fail the protected output
        // closed, preserve an already-latched trip, and wait for a complete scan to re-evaluate.
        if (loadTruncated) {
            qualityRuntime[INPUT_QUALITY] = PortQuality.STALE.ordinal();
            runtime[OUTPUT_VOLTAGE] = 0;
            DomainNetwork.driveCopper(level, outputPos(pos, state), pos, 0);
            level.scheduleTick(pos, this, 2);
            return;
        }

        double current = CircuitPhysics.current(inputVoltage, loadResistance);
        boolean tripped = state.getValue(TRIPPED) || current > state.getValue(RATING);
        int tripState = tripped ? 1 : 0;

        if (runtime[LAST_EVALUATED_TRIP] != tripState) {
            if (tripped) {
                SystemEventTimeline.record(
                        level,
                        pos,
                        SystemEventKind.ELECTRICAL_TRIP,
                        2,
                        "COPPER_FUSE_TRIP",
                        String.format(Locale.ROOT,
                                "Copper fuse overcurrent trip; inputV=%d; Req=%.3f; I≈%.3f; rating=%d; protectedOutput=0",
                                inputVoltage, loadResistance, current, state.getValue(RATING))
                );
            } else {
                SystemEventTimeline.record(
                        level,
                        pos,
                        SystemEventKind.ELECTRICAL_READY,
                        0,
                        "COPPER_FUSE_READY",
                        String.format(Locale.ROOT,
                                "Copper fuse protection ready after verified safe re-evaluation; inputV=%d; Req=%.3f; I≈%.3f; rating=%d",
                                inputVoltage, loadResistance, current, state.getValue(RATING))
                );
            }
            runtime[LAST_EVALUATED_TRIP] = tripState;
        }

        BlockState next = state.setValue(TRIPPED, tripped);
        if (next != state) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        int outputVoltage = tripped ? 0 : inputVoltage;
        runtime[OUTPUT_VOLTAGE] = outputVoltage;
        DomainNetwork.driveCopper(level, outputPos(pos, next), pos, outputVoltage);
        level.scheduleTick(pos, this, 2);
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    private static int[] qualitySnapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, QUALITY_KEY, pos);
        return runtime != null && runtime.length == QUALITY_RUNTIME_SIZE ? runtime : null;
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : runtime[OUTPUT_VOLTAGE];
    }

    public static PortQuality outputQuality(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(TRIPPED)) return PortQuality.FAULT;
        int[] runtime = snapshot(level, pos);
        int[] quality = qualitySnapshot(level, pos);
        if (runtime == null || runtime[PROTECTION_STATE_INITIALIZED] == 0 || quality == null) return PortQuality.STALE;
        int index = Math.max(0, Math.min(PortQuality.values().length - 1, quality[INPUT_QUALITY]));
        return PortQuality.values()[index];
    }

    public static boolean protectionInitialized(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[PROTECTION_STATE_INITIALIZED] == 1;
    }

    @Override protected int observedOutputVoltage(Level level, BlockPos pos, BlockState state) { return outputVoltage(level, pos); }
    @Override protected PortQuality observedOutputQuality(Level level, BlockPos pos, BlockState state) { return outputQuality(level, pos, state); }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) DomainNetwork.driveCopper(serverLevel, outputPos(pos, state), pos, 0);
            RuntimeIntStore.remove(level, KEY, pos);
            RuntimeIntStore.remove(level, QUALITY_KEY, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CopperNetworkSupport.recomputeAround(serverLevel, pos);
        }
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
            level.scheduleTick(pos, this, 1);

            CopperObservationSupport.Observation input = CopperObservationSupport.observe(level, inputPos(pos, next), pos);
            int inputVoltage = input.quality() == PortQuality.VALID ? input.voltage() : 0;
            double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, next), 128);
            boolean loadTruncated = NetworkKernel.stats(level, "copper_load").lastTruncated();
            double current = loadTruncated ? 0.0 : CircuitPhysics.current(inputVoltage, loadResistance);
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "Copper fuse | BACK input -> FRONT protected output | rating=%d | V=%d | Req=%s | I≈%s | %s | inputQuality=%s | outputQuality=%s%s",
                    next.getValue(RATING),
                    inputVoltage,
                    loadTruncated ? "STALE" : String.format(Locale.ROOT, "%.3f", loadResistance),
                    loadTruncated ? "STALE" : String.format(Locale.ROOT, "%.3f", current),
                    next.getValue(TRIPPED) ? "TRIPPED" : "armed",
                    input.quality(),
                    outputQuality(level, pos, next),
                    player.isShiftKeyDown()
                            ? " | reset requested; protection re-evaluates next tick; READY only after a safe server re-evaluation"
                            : ""
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
