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
import dev.redstoneengineering.physics.DomainNetwork;
import dev.redstoneengineering.physics.EngineeringMath;
import dev.redstoneengineering.physics.MagneticPhysics;
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.ui.FieldDeviceUi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.List;
import java.util.Optional;

public class InductionCoilBlock extends DirectionalDomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty TURNS = IntegerProperty.create("turns", 1, 4);
    private static final String KEY = "induction_coil";
    private static final int RADIUS = 6;
    private static final int PREVIOUS_FLUX = 0;
    private static final int EMF = 1;
    private static final int BASELINE_VALID = 2;
    private static final int RUNTIME_SIZE = 3;

    public InductionCoilBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(TURNS, 2));
    }

    @Override public MapCodec<InductionCoilBlock> codec() { return RedstoneEngineering.INDUCTION_COIL_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(TURNS); }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("MAGNETIC SENSE", inputSide(state), EngineeringDomain.IRON_MAGNETIC,
                        PortKind.MEASUREMENT, PortDirection.INPUT, false, "field"),
                new EngineeringPort("INDUCED COPPER OUT", outputSide(state), EngineeringDomain.COPPER,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "voltage"));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> descriptor = engineeringPort(state, side);
        if (descriptor.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            MagneticPhysics.FieldSample sample = MagneticPhysics.fieldSample(level, pos, RADIUS);
            return Optional.of(new EngineeringPortSnapshot(
                    descriptor.get(), sample.field(), 0.0, 15.0,
                    sample.complete() ? PortQuality.VALID : PortQuality.STALE));
        }
        return Optional.of(new EngineeringPortSnapshot(
                descriptor.get(), outputVoltage(level, pos), 0.0, 15.0, outputQuality(level, pos)));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) {
            MagneticPhysics.FieldSample sample = MagneticPhysics.fieldSample(level, pos, RADIUS);
            int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
            runtime[PREVIOUS_FLUX] = sample.field();
            runtime[EMF] = 0;
            runtime[BASELINE_VALID] = sample.complete() ? 1 : 0;
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState nextState, boolean moved) {
        if (!state.is(nextState.getBlock())) {
            if (level instanceof ServerLevel server) {
                DomainNetwork.driveCopper(server, outputPos(pos, state), pos, 0, false);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, nextState, moved);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        MagneticPhysics.FieldSample sample = MagneticPhysics.fieldSample(level, pos, RADIUS);
        int emf = 0;

        if (sample.complete()) {
            if (runtime[BASELINE_VALID] != 0) {
                int delta = Math.abs(sample.field() - runtime[PREVIOUS_FLUX]);
                emf = EngineeringMath.clamp(delta * state.getValue(TURNS), 0, 15);
            }
            runtime[PREVIOUS_FLUX] = sample.field();
            runtime[BASELINE_VALID] = 1;
        } else {
            // Coverage changes are not physics. Drop the derivative baseline so
            // a later chunk reload cannot masquerade as a magnetic transient.
            runtime[BASELINE_VALID] = 0;
        }

        runtime[EMF] = emf;
        // A complete magnetic observation establishes a real converter output even
        // when ΔΦ=0 and the induced voltage is exactly 0 V. Incomplete coverage is
        // unknown evidence, so release the Copper driver instead of publishing 0 V.
        DomainNetwork.driveCopper(level, outputPos(pos, state), pos, emf, runtime[BASELINE_VALID] != 0);
        level.scheduleTick(pos, this, 2);
    }

    public static int outputVoltage(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length <= EMF ? 0 : runtime[EMF];
    }

    public static PortQuality outputQuality(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length >= RUNTIME_SIZE && runtime[BASELINE_VALID] != 0
                ? PortQuality.VALID
                : PortQuality.STALE;
    }

    /** Server-authoritative turns transition used by the real configuration interaction. */
    public static int cycleTurns(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof InductionCoilBlock)) return 0;
        int turns = state.getValue(TURNS);
        turns = turns >= 4 ? 1 : turns + 1;
        level.setBlock(pos, state.setValue(TURNS, turns), Block.UPDATE_CLIENTS);
        return turns;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            int turns = cycleTurns((ServerLevel) level, pos);
            int emf = outputVoltage(level, pos);
            boolean valid = outputQuality(level, pos) == PortQuality.VALID;
            player.displayClientMessage(Component.literal(
                    "Induction coil | turns-index=" + turns
                            + " | |emf| ∝ N·|ΔΦ/Δt|"
                            + " | current emf=" + emf + "/15"
                            + " | " + (valid ? "VALID" : "STALE COVERAGE")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
