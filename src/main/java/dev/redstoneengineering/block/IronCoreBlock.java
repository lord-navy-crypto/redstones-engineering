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

/** Soft iron core with intentionally persistent remanence until manually demagnetized. */
public class IronCoreBlock extends DomainBlock implements EngineeringPortProvider {
    public static final BooleanProperty MAGNETIZED = BooleanProperty.create("magnetized");
    private static final int MAGNETIZE_THRESHOLD = 8;
    private static final int APPLIED_FIELD_RADIUS = 2;

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
                port, state.getValue(MAGNETIZED) ? 1.0 : 0.0, 0.0, 1.0, PortQuality.VALID));
    }

    public static MagneticPhysics.FieldSample appliedFieldSample(Level level, BlockPos pos) {
        return MagneticPhysics.appliedFieldSample(level, pos, APPLIED_FIELD_RADIUS);
    }

    /** Numeric readback stays on the historical bounded applied-field API. */
    public static int appliedField(Level level, BlockPos pos) {
        return MagneticPhysics.appliedFieldAt(level, pos, APPLIED_FIELD_RADIUS);
    }

    /**
     * Apply the soft-core hysteresis rule at a concrete lifecycle boundary. Strong external
     * field magnetizes immediately only when the complete radius-2 applied-field scan is
     * authoritative; remanence then persists until the explicit demagnetize action. This
     * prevents placement/neighbor ordering or partial chunk coverage from deciding whether
     * a core ever enters a persistent magnetized state.
     */
    private void refreshMagnetization(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(MAGNETIZED)) return;
        MagneticPhysics.FieldSample applied = appliedFieldSample(level, pos);
        if (applied.complete() && applied.field() >= MAGNETIZE_THRESHOLD) {
            level.setBlock(pos, state.setValue(MAGNETIZED, true), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel serverLevel) {
            refreshMagnetization(serverLevel, pos, state);
            serverLevel.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            refreshMagnetization(serverLevel, pos, state);
            serverLevel.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refreshMagnetization(level, pos, state);
        level.scheduleTick(pos, this, 5);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = state;
            if (player.isShiftKeyDown() && state.getValue(MAGNETIZED)) {
                next = state.setValue(MAGNETIZED, false);
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 1);
            }
            player.displayClientMessage(Component.literal(
                    "Iron core | free-space magnetic material | "
                            + (next.getValue(MAGNETIZED) ? "remanent magnetized state" : "soft magnetic core")
                            + " | applied-field=" + appliedField(level, pos) + "/15"
                            + " | threshold=" + MAGNETIZE_THRESHOLD
                            + (player.isShiftKeyDown() ? " | demagnetize" : "")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
