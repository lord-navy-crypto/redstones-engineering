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
import dev.redstoneengineering.physics.RuntimeIntStore;
import dev.redstoneengineering.signal.AmethystRingdownLogic;
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

/**
 * Pulse activity is transient runtime data; frequency/amplitude remain persistent source configuration.
 * Engineering responsibility contract: role=BASE SOURCE. Filtering and tuned resonance remain separate blocks.
 */
public class AmethystResonatorBlock extends DomainBlock implements EngineeringPortProvider {
    public static final IntegerProperty FREQUENCY = IntegerProperty.create("frequency", 1, 15);
    public static final IntegerProperty AMPLITUDE = IntegerProperty.create("amplitude", 1, 15);
    private static final String KEY = "amethyst_resonator";
    private static final int CURRENT_AMPLITUDE = 0;
    private static final int EXCITATION_COUNT = 1;
    private static final int RUNTIME_SIZE = 2;

    public AmethystResonatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FREQUENCY, 1).setValue(AMPLITUDE, 12));
    }
    @Override public MapCodec<AmethystResonatorBlock> codec() { return RedstoneEngineering.AMETHYST_RESONATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FREQUENCY, AMPLITUDE); }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(sourcePort(Direction.NORTH), sourcePort(Direction.SOUTH), sourcePort(Direction.WEST), sourcePort(Direction.EAST));
    }
    private static EngineeringPort sourcePort(Direction side) {
        return new EngineeringPort("RESONANCE OUT", side, EngineeringDomain.AMETHYST,
                PortKind.BUS, PortDirection.OUTPUT, false, "amplitude");
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        int amplitude = currentAmplitude(level, pos);
        return Optional.of(new EngineeringPortSnapshot(port.get(), amplitude, 0.0, 15.0,
                amplitude > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL));
    }

    public static int currentAmplitude(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE
                ? 0 : Math.max(0, Math.min(15, runtime[CURRENT_AMPLITUDE]));
    }

    public static boolean isActive(Level level, BlockPos pos) {
        return AmethystRingdownLogic.active(currentAmplitude(level, pos));
    }

    public static int excitationCount(Level level, BlockPos pos) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length < RUNTIME_SIZE
                ? 0 : Math.max(0, runtime[EXCITATION_COUNT]);
    }

    public static void excite(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        runtime[CURRENT_AMPLITUDE] = AmethystRingdownLogic.excite(state.getValue(AMPLITUDE));
        if (runtime[EXCITATION_COUNT] < Integer.MAX_VALUE) runtime[EXCITATION_COUNT]++;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeAmethyst(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        int previous = runtime[CURRENT_AMPLITUDE];
        int next = AmethystRingdownLogic.decay(previous);
        runtime[CURRENT_AMPLITUDE] = next;

        if (next != previous) DomainNetwork.recomputeAmethyst(level, pos);
        if (next > 0) level.scheduleTick(pos, this, 2);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            excite(level, pos, state);
            level.scheduleTick(pos, this, 2);
            if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeAmethyst(serverLevel, pos);
            player.displayClientMessage(Component.literal(
                    "Amethyst resonator impulse | frequency index=" + state.getValue(FREQUENCY)
                            + " | peak amplitude=" + state.getValue(AMPLITUDE)
                            + " | current amplitude=" + currentAmplitude(level, pos)
                            + " | ring-down=1 level / 2t"
                            + " | excitations=" + excitationCount(level, pos)
                            + " | normal right-click opens Engineering UI"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
