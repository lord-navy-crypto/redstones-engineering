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
import dev.redstoneengineering.ui.FieldDeviceUi;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

/** Laboratory quartz source with bounded timing jitter and one configurable output face. */
public class QuartzLabOscillatorBlock extends DirectionalDomainSourceBlock implements EngineeringPortProvider {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final IntegerProperty PERIOD_INDEX = IntegerProperty.create("period", 0, 4);
    public static final IntegerProperty JITTER = IntegerProperty.create("jitter", 0, 3);
    private static final String KEY = "quartz_lab_oscillator";
    private static final int LAST_HALF_INTERVAL_SLOT = 0;
    private static final int LAST_JITTER_OFFSET_SLOT = 1;
    private static final int EVIDENCE_VALID_SLOT = 2;
    private static final int EVIDENCE_PERIOD_INDEX_SLOT = 3;
    private static final int EVIDENCE_JITTER_SLOT = 4;
    private static final int RUNTIME_SIZE = 5;

    public record TimingEvidence(int nominalPeriod, int lastHalfInterval, int lastJitterOffset, boolean available) {}

    public QuartzLabOscillatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(PERIOD_INDEX, 2).setValue(JITTER, 1));
    }

    @Override public MapCodec<QuartzLabOscillatorBlock> codec() { return RedstoneEngineering.QUARTZ_LAB_OSCILLATOR_CODEC.value(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE, PERIOD_INDEX, JITTER);
    }

    private static EngineeringPort port(Direction side) {
        return new EngineeringPort(
                "QUARTZ LAB CLOCK OUT", side, EngineeringDomain.QUARTZ,
                PortKind.TRIGGER, PortDirection.OUTPUT, false, "ticks");
    }

    @Override
    public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(port(outputSide(state)));
    }

    public static TimingEvidence timingEvidence(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        if (runtime == null || runtime.length != RUNTIME_SIZE || runtime[EVIDENCE_VALID_SLOT] != 1) {
            return new TimingEvidence(
                    QuartzTimingLineBlock.periodTicks(state.getValue(PERIOD_INDEX)), 0, 0, false);
        }
        int nominal = QuartzTimingLineBlock.periodTicks(runtime[EVIDENCE_PERIOD_INDEX_SLOT]);
        return new TimingEvidence(
                nominal,
                runtime[LAST_HALF_INTERVAL_SLOT],
                runtime[LAST_JITTER_OFFSET_SLOT],
                true);
    }

    public static int effectivePeriodTicks(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        int index = runtime == null || runtime.length != RUNTIME_SIZE || runtime[EVIDENCE_VALID_SLOT] != 1
                ? state.getValue(PERIOD_INDEX)
                : runtime[EVIDENCE_PERIOD_INDEX_SLOT];
        return QuartzTimingLineBlock.periodTicks(index);
    }

    public static int effectiveJitter(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime == null || runtime.length != RUNTIME_SIZE || runtime[EVIDENCE_VALID_SLOT] != 1
                ? state.getValue(JITTER)
                : runtime[EVIDENCE_JITTER_SLOT];
    }

    public static boolean configurationPending(Level level, BlockPos pos, BlockState state) {
        int[] runtime = RuntimeIntStore.peek(level, KEY, pos);
        return runtime != null && runtime.length == RUNTIME_SIZE && runtime[EVIDENCE_VALID_SLOT] == 1
                && (runtime[EVIDENCE_PERIOD_INDEX_SLOT] != state.getValue(PERIOD_INDEX)
                || runtime[EVIDENCE_JITTER_SLOT] != state.getValue(JITTER));
    }

    @Override
    public Optional<EngineeringPortSnapshot> engineeringSnapshot(Level level, BlockPos pos, BlockState state, Direction side) {
        return engineeringPort(state, side).map(port -> new EngineeringPortSnapshot(
                port, effectivePeriodTicks(level, pos, state), 1.0, 4096.0, PortQuality.VALID));
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(state.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            RuntimeIntStore.remove(level, KEY, pos);
            DomainNetwork.recomputeQuartzAround(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = state.setValue(ACTIVE, !state.getValue(ACTIVE));
        int[] runtime = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);

        // Period and jitter are shadow configuration. They become effective only at this real
        // waveform transition; editing the controls never inserts an early clock edge.
        int periodIndex = next.getValue(PERIOD_INDEX);
        int jitter = next.getValue(JITTER);
        runtime[EVIDENCE_PERIOD_INDEX_SLOT] = periodIndex;
        runtime[EVIDENCE_JITTER_SLOT] = jitter;
        runtime[EVIDENCE_VALID_SLOT] = 1;

        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        DomainNetwork.recomputeQuartz(level, pos);

        int half = Math.max(1, effectivePeriodTicks(level, pos, next) / 2);
        int effectiveJitter = effectiveJitter(level, pos, next);
        int offset = effectiveJitter == 0
                ? 0
                : random.nextInt(effectiveJitter * 2 + 1) - effectiveJitter;
        int realized = Math.max(1, half + offset);
        runtime[LAST_HALF_INTERVAL_SLOT] = realized;
        runtime[LAST_JITTER_OFFSET_SLOT] = realized - half;
        level.scheduleTick(pos, this, realized);
    }

    /** Configuration changes are shadow state and latch only on the next genuine waveform edge. */
    public static boolean adjustPeriod(Level level, BlockPos pos, int delta) {
        if (level.isClientSide || delta == 0) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) return false;
        int next = Math.floorMod(state.getValue(PERIOD_INDEX) + (delta > 0 ? 1 : -1), 5);
        level.setBlock(pos, state.setValue(PERIOD_INDEX, next), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Adjusts bounded timing jitter without inserting an early edge or rewriting realized evidence. */
    public static boolean adjustJitter(Level level, BlockPos pos, int delta) {
        if (level.isClientSide || delta == 0) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuartzLabOscillatorBlock)) return false;
        int next = Math.floorMod(state.getValue(JITTER) + (delta > 0 ? 1 : -1), 4);
        level.setBlock(pos, state.setValue(JITTER, next), Block.UPDATE_CLIENTS);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                FieldDeviceUi.openUniversal(serverPlayer, pos);
                return InteractionResult.CONSUME;
            }
            BlockState next = state;
            if (player.isShiftKeyDown() && hit.getDirection().getAxis().isHorizontal()) {
                if (rotateOutput(level, pos, true)) {
                    next = level.getBlockState(pos);
                    if (level instanceof ServerLevel serverLevel) DomainNetwork.recomputeQuartzAround(serverLevel, pos);
                }
            } else if (player.isShiftKeyDown()) {
                adjustJitter(level, pos, 1);
                next = level.getBlockState(pos);
            }
            // Route changes alter topology immediately; timing configuration waits for the next
            // already-scheduled physical edge and therefore never creates an artificial transition.
            TimingEvidence evidence = timingEvidence(level, pos, next);
            player.displayClientMessage(Component.literal(
                    "Quartz lab oscillator | OUT=" + outputSide(next).getName().toUpperCase()
                            + " | configured=" + QuartzTimingLineBlock.periodTicks(next.getValue(PERIOD_INDEX))
                            + "t ±" + next.getValue(JITTER) + "t"
                            + " | effective=" + effectivePeriodTicks(level, pos, next)
                            + "t ±" + effectiveJitter(level, pos, next) + "t"
                            + (configurationPending(level, pos, next) ? " (LATCHES NEXT EDGE)" : "")
                            + (evidence.available() ? " | last-half=" + evidence.lastHalfInterval()
                            + "t offset=" + evidence.lastJitterOffset() + "t" : " | no realized interval yet")
                            + " | shift-side=route, shift-vertical=jitter"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
