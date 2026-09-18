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
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.IronCoreLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Soft iron core with induced magnetization and low, decaying remanence.
 *
 * MAGNETIZED remains a coarse visual/backward-compatible state flag. The authoritative magnetic
 * contribution is a 0..15 runtime magnetization that follows complete applied-field evidence
 * with finite response and relaxes after the field is removed. Shift-use performs explicit degauss.
 */
public class IronCoreBlock extends DomainBlock implements EngineeringPortProvider {
    public static final BooleanProperty MAGNETIZED = BooleanProperty.create("magnetized");
    public static final int APPLIED_FIELD_RADIUS = 2;

    private static final String RUNTIME_KEY = "iron_core";
    private static final int MAGNETIZATION = 0;
    private static final int SCANNED = 1;
    private static final int EXPECTED = 2;
    private static final int INITIALIZED = 3;
    private static final int RUNTIME_SIZE = 4;

    public IronCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MAGNETIZED, false));
    }

    @Override public MapCodec<IronCoreBlock> codec() { return RedstoneEngineering.IRON_CORE_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MAGNETIZED); }

    /** Free-space magnetic coupling metadata; these are not wired cable edges. */
    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return Arrays.stream(Direction.values())
                .map(side -> new EngineeringPort(
                        "MAGNETIC COUPLING " + side.getName().toUpperCase(), side,
                        EngineeringDomain.IRON_MAGNETIC, PortKind.AUXILIARY,
                        PortDirection.BIDIRECTIONAL, false, "remanence"))
                .toList();
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, remanentField(level, pos), 0.0, 15.0,
                coverageComplete(level, pos) ? PortQuality.VALID : PortQuality.STALE));
    }

    public static MagneticPhysics.FieldSample appliedFieldSample(Level level, BlockPos pos) {
        return MagneticPhysics.appliedFieldSample(level, pos, APPLIED_FIELD_RADIUS);
    }

    public static int appliedField(Level level, BlockPos pos) {
        return appliedFieldSample(level, pos).field();
    }

    private static int[] snapshot(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, RUNTIME_KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE ? runtime : null;
    }

    /** Authoritative variable remanence consumed by MagneticPhysics. */
    public static int remanentField(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        if (runtime != null && runtime[INITIALIZED] != 0) {
            return Math.max(0, Math.min(15, runtime[MAGNETIZATION]));
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof IronCoreBlock && state.getValue(MAGNETIZED) ? 2 : 0;
    }

    public static int magnetization(Level level, BlockPos pos) {
        return remanentField(level, pos);
    }

    public static boolean coverageComplete(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime != null && runtime[INITIALIZED] != 0
                && runtime[SCANNED] == runtime[EXPECTED];
    }

    public static int scannedCells(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[SCANNED]);
    }

    public static int expectedCells(Level level, BlockPos pos) {
        int[] runtime = snapshot(level, pos);
        return runtime == null ? 0 : Math.max(0, runtime[EXPECTED]);
    }

    /** Explicit operator degauss: immediately clears retained soft-core magnetization. */
    public static boolean degauss(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof IronCoreBlock core)) return false;
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);
        runtime[MAGNETIZATION] = 0;
        runtime[INITIALIZED] = 1;
        if (state.getValue(MAGNETIZED)) {
            level.setBlock(pos, state.setValue(MAGNETIZED, false), Block.UPDATE_CLIENTS);
        }
        if (level instanceof ServerLevel server) server.scheduleTick(pos, core, 1);
        return true;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (level instanceof ServerLevel serverLevel) serverLevel.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MagneticPhysics.FieldSample applied = appliedFieldSample(level, pos);
        int[] runtime = RuntimeIntStore.get(level, RUNTIME_KEY, pos, RUNTIME_SIZE);

        if (runtime[INITIALIZED] == 0) {
            runtime[MAGNETIZATION] = state.getValue(MAGNETIZED) ? 2 : 0;
            runtime[INITIALIZED] = 1;
        }

        int current = runtime[MAGNETIZATION];
        int next = IronCoreLogic.nextMagnetization(current, applied.field(), applied.complete());
        runtime[MAGNETIZATION] = next;
        runtime[SCANNED] = applied.scannedCells();
        runtime[EXPECTED] = applied.expectedCells();

        boolean coarseMagnetized = next > 0;
        if (state.getValue(MAGNETIZED) != coarseMagnetized) {
            level.setBlock(pos, state.setValue(MAGNETIZED, coarseMagnetized), Block.UPDATE_CLIENTS);
        }

        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) RuntimeIntStore.remove(level, RUNTIME_KEY, pos);
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) degauss(level, pos);
            MagneticPhysics.FieldSample applied = appliedFieldSample(level, pos);
            player.displayClientMessage(Component.literal(
                    "Iron core | soft magnetic material"
                            + " | applied=" + applied.field() + "/15"
                            + " | magnetization=" + remanentField(level, pos) + "/15"
                            + " | coverage=" + applied.scannedCells() + "/" + applied.expectedCells()
                            + " " + (applied.complete() ? "VALID" : "INCOMPLETE")
                            + (player.isShiftKeyDown() ? " | DEGAUSS" : "")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
