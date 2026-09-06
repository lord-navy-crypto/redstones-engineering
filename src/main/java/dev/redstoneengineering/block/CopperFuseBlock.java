package dev.redstoneengineering.block;

import com.mojang.serialization.MapCodec;
import dev.redstoneengineering.RedstoneEngineering;
import dev.redstoneengineering.diagnostics.events.SystemEventKind;
import dev.redstoneengineering.diagnostics.events.SystemEventTimeline;
import dev.redstoneengineering.physics.CircuitPhysics;
import dev.redstoneengineering.physics.CopperNetworkSupport;
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

import java.util.Locale;

/** Axial copper safety element: BACK input, FRONT protected output. */
public class CopperFuseBlock extends DirectionalCopperProcessorBlock {
    private static final String KEY = "copper_fuse";
    private static final int RUNTIME_SIZE = 3;
    private static final int OUTPUT_VOLTAGE = 0;
    private static final int LAST_EVALUATED_TRIP = 1;
    private static final int PROTECTION_STATE_INITIALIZED = 2;
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
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        if (runtime[PROTECTION_STATE_INITIALIZED] == 0) {
            // A persisted TRIPPED block is historical state, not evidence that a new incident occurred now.
            runtime[LAST_EVALUATED_TRIP] = state.getValue(TRIPPED) ? 1 : 0;
            runtime[PROTECTION_STATE_INITIALIZED] = 1;
        }

        int inputVoltage = DomainNetwork.sampleCopperVoltage(level, inputPos(pos, state));
        double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, state), 128);
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

    public static int outputVoltage(Level level, BlockPos pos) {
        return RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE)[OUTPUT_VOLTAGE];
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

            int inputVoltage = DomainNetwork.sampleCopperVoltage(level, inputPos(pos, next));
            double loadResistance = CircuitPhysics.equivalentLoadResistance(level, outputPos(pos, next), 128);
            double current = CircuitPhysics.current(inputVoltage, loadResistance);
            player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                    "Copper fuse | BACK input -> FRONT protected output | rating=%d | V=%d | Req=%.3f | I≈%.3f | %s%s",
                    next.getValue(RATING),
                    inputVoltage,
                    loadResistance,
                    current,
                    next.getValue(TRIPPED) ? "TRIPPED" : "armed",
                    player.isShiftKeyDown()
                            ? " | reset requested; protection re-evaluates next tick; READY only after a safe server re-evaluation"
                            : ""
            )), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
