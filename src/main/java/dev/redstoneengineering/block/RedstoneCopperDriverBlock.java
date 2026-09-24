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
import dev.redstoneengineering.physics.EngineeringDeviceParameters;
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
 * Redstone command -> controlled Copper-domain voltage source.
 *
 * <p>This is a Minecraft-scale DAC/power-stage abstraction. A valid Redstone zero remains a real
 * 0 V source command, while missing or uncertain command evidence is source absence. The physical
 * output has a configurable finite slew limit rather than teleporting instantly to its target.</p>
 */
public class RedstoneCopperDriverBlock extends Block implements EngineeringPortProvider {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final DirectionProperty INPUT_FACING =
            DirectionProperty.create("input_facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty SLEW = IntegerProperty.create("slew", 0, 2);

    private static final int[] SLEW_STEPS = {1, 2, 4};
    private static final String KEY = "redstone_copper_driver";
    private static final int ACTUAL = 0;
    private static final int TARGET = 1;
    private static final int QUALITY = 2;
    private static final int RUNTIME_SIZE = 3;

    public RedstoneCopperDriverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(INPUT_FACING, Direction.SOUTH)
                .setValue(SLEW, 1));
    }

    @Override public MapCodec<RedstoneCopperDriverBlock> codec() {
        return RedstoneEngineering.REDSTONE_COPPER_DRIVER_CODEC.value();
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INPUT_FACING, SLEW);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction output = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, output).setValue(INPUT_FACING, output.getOpposite());
    }

    public static Direction outputSide(BlockState state) { return state.getValue(FACING); }
    public static Direction inputSide(BlockState state) { return state.getValue(INPUT_FACING); }

    public static boolean rotateInput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneCopperDriverBlock block)) return false;
        Direction output = outputSide(state);
        Direction oldInput = inputSide(state);
        Direction nextInput = nextFreeHorizontal(oldInput, output, clockwise);
        if (nextInput == oldInput) return false;
        level.setBlock(pos, state.setValue(INPUT_FACING, nextInput), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, block, 1);
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(oldInput), block);
        level.updateNeighborsAt(pos.relative(nextInput), block);
        return true;
    }

    public static boolean rotateOutput(Level level, BlockPos pos, boolean clockwise) {
        if (level.isClientSide) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneCopperDriverBlock block)) return false;
        Direction input = inputSide(state);
        Direction oldOutput = outputSide(state);
        Direction nextOutput = nextFreeHorizontal(oldOutput, input, clockwise);
        if (nextOutput == oldOutput) return false;
        if (level instanceof ServerLevel server) {
            DomainNetwork.driveCopper(server, pos.relative(oldOutput), pos, 0, false);
        }
        level.setBlock(pos, state.setValue(FACING, nextOutput), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) server.scheduleTick(pos, block, 1);
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(oldOutput), block);
        level.updateNeighborsAt(pos.relative(nextOutput), block);
        return true;
    }

    private static Direction nextFreeHorizontal(Direction current, Direction forbidden, boolean clockwise) {
        Direction candidate = current;
        for (int i = 0; i < 3; i++) {
            candidate = clockwise ? candidate.getClockWise() : candidate.getCounterClockWise();
            if (candidate != forbidden) return candidate;
        }
        return current;
    }

    public static int actualVoltage(Level level, BlockPos pos) {
        int[] rt = RuntimeIntStore.peek(level, KEY, pos);
        return rt == null || rt.length != RUNTIME_SIZE ? 0 : EngineeringMath.clamp(rt[ACTUAL], 0, 15);
    }

    public static int targetVoltage(Level level, BlockPos pos) {
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

    public static int slewStep(BlockState state) {
        return SLEW_STEPS[state.getValue(SLEW)];
    }

    private static EngineeringDeviceParameters.ExtendedParameters configuredDynamics(
            Level level, BlockPos pos, BlockState state
    ) {
        int fallback = slewStep(state);
        if (level instanceof ServerLevel serverLevel) {
            EngineeringDeviceParameters.ExtendedParameters raw = EngineeringDeviceParameters.get(serverLevel)
                    .extendedParameters(serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(fallback, fallback, 0, 0));
            int rise = EngineeringMath.clamp(raw.a(), 1, 15);
            // Backward compatibility with Alpha 1.0.21 saves, where slot B was unused and stored as zero.
            int fall = raw.b() <= 0 ? rise : EngineeringMath.clamp(raw.b(), 1, 15);
            return new EngineeringDeviceParameters.ExtendedParameters(rise, fall, 0, 0);
        }
        return new EngineeringDeviceParameters.ExtendedParameters(fallback, fallback, 0, 0);
    }

    public static int configuredRiseSlew(Level level, BlockPos pos, BlockState state) {
        return configuredDynamics(level, pos, state).a();
    }

    public static int configuredFallSlew(Level level, BlockPos pos, BlockState state) {
        return configuredDynamics(level, pos, state).b();
    }

    /** Compatibility accessor retained for older tests and callers. */
    public static int configuredSlew(Level level, BlockPos pos, BlockState state) {
        return configuredRiseSlew(level, pos, state);
    }

    public static boolean setEngineeringSlewRates(ServerLevel level, BlockPos pos, int riseSlew, int fallSlew) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneCopperDriverBlock driver)) return false;
        int rise = EngineeringMath.clamp(riseSlew, 1, 15);
        int fall = EngineeringMath.clamp(fallSlew, 1, 15);
        boolean changed = EngineeringDeviceParameters.get(level).setExtendedParameters(
                level, pos, new EngineeringDeviceParameters.ExtendedParameters(rise, fall, 0, 0));
        if (changed) level.scheduleTick(pos, driver, 1);
        return changed;
    }

    /** Compatibility mutator: changing the legacy single slew updates both directions. */
    public static boolean setConfiguredSlew(ServerLevel level, BlockPos pos, int slew) {
        return setEngineeringSlewRates(level, pos, slew, slew);
    }

    private static int encodeQuality(PortQuality quality) { return quality.ordinal() + 1; }

    @Override public List<EngineeringPort> engineeringPorts(BlockState state) {
        return List.of(
                new EngineeringPort("REDSTONE COMMAND", inputSide(state), EngineeringDomain.REDSTONE,
                        PortKind.CONVERTER, PortDirection.INPUT, true, "command"),
                new EngineeringPort("CONTROLLED COPPER OUT", outputSide(state), EngineeringDomain.COPPER,
                        PortKind.CONVERTER, PortDirection.OUTPUT, false, "V-eq")
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
        return Optional.of(new EngineeringPortSnapshot(
                port.get(), actualVoltage(level, pos), 0.0, 15.0, inputQuality(level, pos)));
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
            rt[ACTUAL] = moveToward(
                    rt[ACTUAL], rt[TARGET],
                    configuredRiseSlew(level, pos, state),
                    configuredFallSlew(level, pos, state));
            // sourcePresent=true even for actual 0 V: a valid zero command is a real electrical state.
            DomainNetwork.driveCopper(level, pos.relative(outputSide(state)), pos, rt[ACTUAL], true);
        } else if (observation.quality() == PortQuality.NO_SIGNAL) {
            rt[TARGET] = 0;
            rt[ACTUAL] = moveToward(
                    rt[ACTUAL], 0,
                    configuredRiseSlew(level, pos, state),
                    configuredFallSlew(level, pos, state));
            DomainNetwork.driveCopper(level, pos.relative(outputSide(state)), pos, 0, false);
        } else {
            // Unknown command evidence is not a new numeric command.
            DomainNetwork.driveCopper(level, pos.relative(outputSide(state)), pos, 0, false);
        }
        level.scheduleTick(pos, this, 1);
    }

    private static int moveToward(int current, int target, int riseStep, int fallStep) {
        current = EngineeringMath.clamp(current, 0, 15);
        target = EngineeringMath.clamp(target, 0, 15);
        int rise = EngineeringMath.clamp(riseStep, 1, 15);
        int fall = EngineeringMath.clamp(fallStep, 1, 15);
        if (current < target) return Math.min(target, current + rise);
        if (current > target) return Math.max(target, current - fall);
        return current;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel server) {
                DomainNetwork.driveCopper(server, pos.relative(outputSide(state)), pos, 0, false);
            }
            RuntimeIntStore.remove(level, KEY, pos);
            if (level instanceof ServerLevel serverLevel) {
                EngineeringDeviceParameters.get(serverLevel).removeExtendedParameters(serverLevel, pos);
            }
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
                int next = (state.getValue(SLEW) + 1) % 3;
                BlockState updated = state.setValue(SLEW, next);
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
                if (level instanceof ServerLevel serverLevel) {
                    EngineeringDeviceParameters.get(serverLevel).setExtendedParameters(
                            serverLevel, pos,
                            new EngineeringDeviceParameters.ExtendedParameters(
                                    slewStep(updated), slewStep(updated), 0, 0));
                }
                player.displayClientMessage(Component.literal(
                        "Redstone-Copper Driver | target=" + targetVoltage(level, pos)
                                + " V-level | actual=" + actualVoltage(level, pos)
                                + " | riseSlew=" + slewStep(updated) + " V-level/tick"
                                + " | fallSlew=" + slewStep(updated) + " V-level/tick"
                                + " | quality=" + inputQuality(level, pos)
                                + " | valid 0 V != missing command"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
