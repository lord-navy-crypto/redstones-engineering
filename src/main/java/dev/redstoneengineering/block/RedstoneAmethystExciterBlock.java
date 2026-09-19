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
import dev.redstoneengineering.physics.RedstoneObservationSupport;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Redstone-controlled electromechanical driver for the Amethyst resonance domain.
 *
 * <p>The input command defines target drive amplitude while FREQUENCY defines the carrier.
 * Actual resonance amplitude has finite attack/release slew; uncertain input evidence freezes
 * internal amplitude and releases the network claim rather than fabricating a zero command.</p>
 */
public class RedstoneAmethystExciterBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty INPUT_FACING =
            DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty FREQUENCY = IntegerProperty.create("frequency", 1, 15);

    private static final String KEY = "redstone_amethyst_exciter";
    private static final int ACTUAL = 0;
    private static final int TARGET = 1;
    private static final int QUALITY = 2;
    private static final int RUNTIME_SIZE = 3;
    private static final int SLEW_PER_TICK = 2;

    public RedstoneAmethystExciterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INPUT_FACING, Direction.SOUTH)
                .setValue(FREQUENCY, 8));
    }

    @Override public MapCodec<RedstoneAmethystExciterBlock> codec() {
        return RedstoneEngineering.REDSTONE_AMETHYST_EXCITER_CODEC.value();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INPUT_FACING, FREQUENCY);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, output).setValue(INPUT_FACING, output.getOpposite());
    }

    public static Direction outputSide(BlockState state) { return state.getValue(FACING); }
    public static Direction inputSide(BlockState state) { return state.getValue(INPUT_FACING); }

    public static int actualAmplitude(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length != RUNTIME_SIZE ? 0 : EngineeringMath.clamp(rt[ACTUAL], 0, 15);
    }

    public static int targetAmplitude(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length != RUNTIME_SIZE ? 0 : EngineeringMath.clamp(rt[TARGET], 0, 15);
    }

    public static PortQuality inputQuality(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        if (rt == null || rt.length != RUNTIME_SIZE || rt[QUALITY] <= 0) return PortQuality.STALE;
        int ordinal = rt[QUALITY] - 1;
        return ordinal >= 0 && ordinal < PortQuality.values().length
                ? PortQuality.values()[ordinal] : PortQuality.STALE;
    }

    private static int encodeQuality(PortQuality quality) { return quality.ordinal() + 1; }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("REDSTONE DRIVE", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.INPUT, true, "command"),
                new EngineeringPort("DRIVEN RESONANCE OUT", outputSide(state), EngineeringDomain.AMETHYST,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "amplitude")
        );
    }

    @Override public Optional<EngineeringPortSnapshot> engineeringSnapshot(
            Level level, BlockPos pos, BlockState state, Direction side
    ) {
        Optional<EngineeringPort> port = engineeringPort(state, side);
        if (port.isEmpty()) return Optional.empty();
        if (side == inputSide(state)) {
            var observation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
            return Optional.of(EngineeringPortSnapshot.redstone(port.get(), observation.value(), observation.quality()));
        }
        int actual = actualAmplitude(level, pos);
        PortQuality q = inputQuality(level, pos);
        PortQuality outputQ = (q == PortQuality.VALID || q == PortQuality.SATURATED)
                ? (actual > 0 ? PortQuality.VALID : PortQuality.NO_SIGNAL)
                : q;
        return Optional.of(new EngineeringPortSnapshot(port.get(), actual, 0.0, 15.0, outputQ));
    }

    @Override public boolean canConnectRedstone(
            BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction
    ) {
        return direction != null && direction == inputSide(state).getOpposite();
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved
    ) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        var observation = RedstoneObservationSupport.observe(level, pos, inputSide(state));
        int[] rt = RuntimeIntStore.get(level, KEY, pos, RUNTIME_SIZE);
        rt[QUALITY] = encodeQuality(observation.quality());

        if (observation.valid()) {
            rt[TARGET] = EngineeringMath.clamp(observation.value(), 0, 15);
            rt[ACTUAL] = moveToward(rt[ACTUAL], rt[TARGET], SLEW_PER_TICK);
            DomainNetwork.driveAmethyst(
                    level, pos.relative(outputSide(state)), pos,
                    rt[ACTUAL] > 0, state.getValue(FREQUENCY), rt[ACTUAL]);
        } else if (observation.quality() == PortQuality.NO_SIGNAL) {
            rt[TARGET] = 0;
            rt[ACTUAL] = moveToward(rt[ACTUAL], 0, SLEW_PER_TICK);
            DomainNetwork.driveAmethyst(
                    level, pos.relative(outputSide(state)), pos,
                    rt[ACTUAL] > 0, state.getValue(FREQUENCY), rt[ACTUAL]);
        } else {
            // Unknown command evidence is not a zero command. Preserve actuator state but fail closed downstream.
            DomainNetwork.driveAmethyst(level, pos.relative(outputSide(state)), pos, false,
                    state.getValue(FREQUENCY), 0);
        }
        level.scheduleTick(pos, this, 1);
    }

    private static int moveToward(int current, int target, int step) {
        current = EngineeringMath.clamp(current, 0, 15);
        target = EngineeringMath.clamp(target, 0, 15);
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) {
                DomainNetwork.driveAmethyst(server, pos.relative(outputSide(state)), pos, false,
                        state.getValue(FREQUENCY), 0);
            }
            RuntimeIntStore.remove(level, KEY, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.isShiftKeyDown()) {
                FieldDeviceUi.open(serverPlayer, pos);
            } else {
                int next = state.getValue(FREQUENCY) >= 15 ? 1 : state.getValue(FREQUENCY) + 1;
                level.setBlock(pos, state.setValue(FREQUENCY, next), Block.UPDATE_CLIENTS);
                player.displayClientMessage(Component.literal(
                        "Redstone-Amethyst Exciter | f=" + next
                                + " | target A=" + targetAmplitude(level, pos)
                                + " | actual A=" + actualAmplitude(level, pos)
                                + " | slew=" + SLEW_PER_TICK + "/tick"
                                + " | quality=" + inputQuality(level, pos)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
